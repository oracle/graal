/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.lir.constopt;

import static org.graalvm.compiler.lir.LIRValueUtil.asVariable;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;
import static org.graalvm.compiler.lir.phases.LIRPhase.Options.LIROptimization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.CastValue;
import org.graalvm.compiler.lir.InstructionValueConsumer;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInsertionBuffer;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.ValueConsumer;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.constopt.ConstantTree.Flags;
import org.graalvm.compiler.lir.constopt.ConstantTree.NodeCost;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.phases.PreAllocationOptimizationPhase;
import org.graalvm.compiler.options.NestedBooleanOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * This optimization tries to improve the handling of constants by replacing a single definition of
 * a constant, which is potentially scheduled into a block with high frequency, with one or more
 * definitions in blocks with a lower frequency.
 */
public final class ConstantLoadOptimization extends PreAllocationOptimizationPhase {

    public static class Options {
        // @formatter:off
        @Option(help = "Enable constant load optimization.", type = OptionType.Debug)
        public static final NestedBooleanOptionKey LIROptConstantLoadOptimization = new NestedBooleanOptionKey(LIROptimization, true);
        // @formatter:on
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PreAllocationOptimizationContext context) {
        LIRGeneratorTool lirGen = context.lirGen;
        new Optimization(lirGenRes.getLIR(), lirGen).apply();
    }

    private static final CounterKey constantsTotal = DebugContext.counter("ConstantLoadOptimization[total]");
    private static final CounterKey phiConstantsSkipped = DebugContext.counter("ConstantLoadOptimization[PhisSkipped]");
    private static final CounterKey singleUsageConstantsSkipped = DebugContext.counter("ConstantLoadOptimization[SingleUsageSkipped]");
    private static final CounterKey usageAtDefinitionSkipped = DebugContext.counter("ConstantLoadOptimization[UsageAtDefinitionSkipped]");
    private static final CounterKey basePointerUsagesSkipped = DebugContext.counter("ConstantLoadOptimization[BasePointerUsagesSkipped]");
    private static final CounterKey materializeAtDefinitionSkipped = DebugContext.counter("ConstantLoadOptimization[MaterializeAtDefinitionSkipped]");
    private static final CounterKey constantsOptimized = DebugContext.counter("ConstantLoadOptimization[optimized]");

    private static final class Optimization {
        private final LIR lir;
        private final LIRGeneratorTool lirGen;
        private final VariableMap<DefUseTree> map;
        private final BitSet phiConstants;
        private final BitSet defined;
        private final BlockMap<List<UseEntry>> blockMap;
        private final BlockMap<LIRInsertionBuffer> insertionBuffers;
        private final DebugContext debug;

        private Optimization(LIR lir, LIRGeneratorTool lirGen) {
            this.lir = lir;
            this.debug = lir.getDebug();
            this.lirGen = lirGen;
            this.map = new VariableMap<>();
            this.phiConstants = new BitSet();
            this.defined = new BitSet();
            this.insertionBuffers = new BlockMap<>(lir.getControlFlowGraph());
            this.blockMap = new BlockMap<>(lir.getControlFlowGraph());
        }

        @SuppressWarnings("try")
        private void apply() {
            try (Indent indent = debug.logAndIndent("ConstantLoadOptimization")) {
                try (DebugContext.Scope s = debug.scope("BuildDefUseTree")) {
                    // build DefUseTree
                    for (AbstractBlockBase<?> b : lir.getControlFlowGraph().getBlocks()) {
                        this.analyzeBlock(b);
                    }
                    // remove all with only one use
                    map.filter(t -> {
                        if (t.usageCount() > 1) {
                            return true;
                        } else {
                            singleUsageConstantsSkipped.increment(debug);
                            return false;
                        }
                    });
                    // collect block map
                    map.forEach(tree -> tree.forEach(this::addUsageToBlockMap));
                } catch (Throwable e) {
                    throw debug.handle(e);
                }

                try (DebugContext.Scope s = debug.scope("BuildConstantTree")) {
                    // create ConstantTree
                    map.forEach(this::createConstantTree);

                    // insert moves, delete null instructions and reset instruction ids
                    for (AbstractBlockBase<?> b : lir.getControlFlowGraph().getBlocks()) {
                        this.rewriteBlock(b);
                    }

                    assert verifyStates();
                } catch (Throwable e) {
                    throw debug.handle(e);
                }
            }
        }

        private boolean verifyStates() {
            map.forEach(this::verifyStateUsage);
            return true;
        }

        private void verifyStateUsage(DefUseTree tree) {
            Variable var = tree.getVariable();
            ValueConsumer stateConsumer = new ValueConsumer() {

                @Override
                public void visitValue(Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                    assert !operand.equals(var) : "constant usage through variable in frame state " + var;
                }
            };
            for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
                for (LIRInstruction inst : lir.getLIRforBlock(block)) {
                    // set instruction id to the index in the lir instruction list
                    inst.visitEachState(stateConsumer);
                }
            }
        }

        private static boolean isConstantLoad(LIRInstruction inst) {
            if (!LoadConstantOp.isLoadConstantOp(inst)) {
                return false;
            }
            return isVariable(LoadConstantOp.asLoadConstantOp(inst).getResult());
        }

        private void addUsageToBlockMap(UseEntry entry) {
            AbstractBlockBase<?> block = entry.getBlock();
            List<UseEntry> list = blockMap.get(block);
            if (list == null) {
                list = new ArrayList<>();
                blockMap.put(block, list);
            }
            list.add(entry);
        }

        /**
         * Collects def-use information for a {@code block}.
         */
        @SuppressWarnings("try")
        private void analyzeBlock(AbstractBlockBase<?> block) {
            try (Indent indent = debug.logAndIndent("Block: %s", block)) {

                InstructionValueConsumer loadConsumer = (instruction, value, mode, flags) -> {
                    if (isVariable(value)) {
                        Variable var = asVariable(value);
                        AllocatableValue base = getBasePointer(var);
                        if (base != null && isVariable(base)) {
                            if (map.remove(asVariable(base)) != null) {
                                // We do not want optimize constants which are used as base
                                // pointer. The reason is that it would require to update all
                                // the derived Variables (LIRKind and so on)
                                map.remove(var);
                                basePointerUsagesSkipped.increment(debug);
                                debug.log("skip optimizing %s because it is used as base pointer", base);
                            }
                        }
                        if (!phiConstants.get(var.index)) {
                            if (!defined.get(var.index)) {
                                defined.set(var.index);
                                if (isConstantLoad(instruction)) {
                                    debug.log("constant load: %s", instruction);
                                    map.put(var, new DefUseTree(instruction, block));
                                    constantsTotal.increment(debug);
                                }
                            } else {
                                // Variable is redefined, this only happens for constant loads
                                // introduced by phi resolution -> ignore.
                                DefUseTree removed = map.remove(var);
                                if (removed != null) {
                                    phiConstantsSkipped.increment(debug);
                                }
                                phiConstants.set(var.index);
                                debug.log(DebugContext.VERBOSE_LEVEL, "Removing phi variable: %s", var);
                            }
                        } else {
                            assert defined.get(var.index) : "phi but not defined? " + var;
                        }
                    }
                };

                InstructionValueConsumer useConsumer = (instruction, value, mode, flags) -> {
                    if (isVariable(value)) {
                        Variable var = asVariable(value);
                        if (!phiConstants.get(var.index)) {
                            DefUseTree tree = map.get(var);
                            if (tree != null) {
                                tree.addUsage(block, instruction, value);
                                debug.log("usage of %s : %s", var, instruction);
                            }
                        }
                    }
                };

                int opId = 0;
                for (LIRInstruction inst : lir.getLIRforBlock(block)) {
                    // set instruction id to the index in the lir instruction list
                    inst.setId(opId++);
                    inst.visitEachOutput(loadConsumer);
                    inst.visitEachInput(useConsumer);
                    inst.visitEachAlive(useConsumer);

                }
            }
        }

        private static AllocatableValue getBasePointer(Value value) {
            ValueKind<?> kind = value.getValueKind();
            if (kind instanceof LIRKind) {
                return ((LIRKind) kind).getDerivedReferenceBase();
            } else {
                return null;
            }
        }

        /**
         * Creates the dominator tree and searches for an solution.
         */
        @SuppressWarnings("try")
        private void createConstantTree(DefUseTree tree) {
            ConstantTree constTree = new ConstantTree(lir.getControlFlowGraph(), tree);
            constTree.set(Flags.SUBTREE, tree.getBlock());
            tree.forEach(u -> constTree.set(Flags.USAGE, u.getBlock()));

            if (constTree.get(Flags.USAGE, tree.getBlock())) {
                // usage in the definition block -> no optimization
                usageAtDefinitionSkipped.increment(debug);
                return;
            }

            constTree.markBlocks();

            NodeCost cost = ConstantTreeAnalyzer.analyze(debug, constTree, tree.getBlock());
            int usageCount = cost.getUsages().size();
            assert usageCount == tree.usageCount() : "Usage count differs: " + usageCount + " vs. " + tree.usageCount();

            if (debug.isLogEnabled()) {
                try (Indent i = debug.logAndIndent("Variable: %s, Block: %s, freq.: %f", tree.getVariable(), tree.getBlock(), tree.getBlock().getRelativeFrequency())) {
                    debug.log("Usages result: %s", cost);
                }

            }

            if (cost.getNumMaterializations() > 1 || cost.getBestCost() < tree.getBlock().getRelativeFrequency()) {
                try (DebugContext.Scope s = debug.scope("CLOmodify", constTree);
                                Indent i = debug.isLogEnabled() ? debug.logAndIndent("Replacing %s = %s", tree.getVariable(), tree.getConstant().toValueString()) : null) {
                    // mark original load for removal
                    deleteInstruction(tree);
                    constantsOptimized.increment(debug);

                    // collect result
                    createLoads(tree, constTree, tree.getBlock());

                } catch (Throwable e) {
                    throw debug.handle(e);
                }
            } else {
                // no better solution found
                materializeAtDefinitionSkipped.increment(debug);
            }
            debug.dump(DebugContext.DETAILED_LEVEL, constTree, "ConstantTree for %s", tree.getVariable());
        }

        private void createLoads(DefUseTree tree, ConstantTree constTree, AbstractBlockBase<?> startBlock) {
            Deque<AbstractBlockBase<?>> worklist = new ArrayDeque<>();
            worklist.add(startBlock);
            while (!worklist.isEmpty()) {
                AbstractBlockBase<?> block = worklist.pollLast();
                if (constTree.get(Flags.CANDIDATE, block)) {
                    constTree.set(Flags.MATERIALIZE, block);
                    // create and insert load
                    insertLoad(tree.getConstant(), tree.getVariable().getValueKind(), block, constTree.getCost(block).getUsages());
                } else {
                    AbstractBlockBase<?> dominated = block.getFirstDominated();
                    while (dominated != null) {
                        if (constTree.isMarked(dominated)) {
                            worklist.addLast(dominated);
                        }
                        dominated = dominated.getDominatedSibling();
                    }
                }
            }
        }

        private void insertLoad(Constant constant, ValueKind<?> kind, AbstractBlockBase<?> block, List<UseEntry> usages) {
            assert usages != null && usages.size() > 0 : String.format("No usages %s %s %s", constant, block, usages);
            // create variable
            Variable variable = lirGen.newVariable(kind);
            // create move
            LIRInstruction move = lirGen.getSpillMoveFactory().createLoad(variable, constant);
            // insert instruction
            getInsertionBuffer(block).append(1, move);
            debug.log("new move (%s) and inserted in block %s", move, block);
            // update usages
            for (UseEntry u : usages) {
                AllocatableValue newValue;
                if (u.getValue() instanceof CastValue) {
                    ValueKind<?> castKind = variable.getValueKind().changeType(u.getValue().getPlatformKind());
                    newValue = new CastValue(castKind, variable);
                } else {
                    newValue = variable;
                }
                u.setValue(newValue);
                debug.log("patched instruction %s", u.getInstruction());
            }
        }

        /**
         * Inserts the constant loads created in {@link #createConstantTree} and deletes the
         * original definition.
         */
        private void rewriteBlock(AbstractBlockBase<?> block) {
            // insert moves
            LIRInsertionBuffer buffer = insertionBuffers.get(block);
            if (buffer != null) {
                assert buffer.initialized() : "not initialized?";
                buffer.finish();
            }

            // delete instructions
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
            boolean hasDead = false;
            for (LIRInstruction inst : instructions) {
                if (inst == null) {
                    hasDead = true;
                } else {
                    inst.setId(-1);
                }
            }
            if (hasDead) {
                // Remove null values from the list.
                instructions.removeAll(Collections.singleton(null));
            }
        }

        private void deleteInstruction(DefUseTree tree) {
            AbstractBlockBase<?> block = tree.getBlock();
            LIRInstruction instruction = tree.getInstruction();
            debug.log("deleting instruction %s from block %s", instruction, block);
            lir.getLIRforBlock(block).set(instruction.id(), null);
        }

        private LIRInsertionBuffer getInsertionBuffer(AbstractBlockBase<?> block) {
            LIRInsertionBuffer insertionBuffer = insertionBuffers.get(block);
            if (insertionBuffer == null) {
                insertionBuffer = new LIRInsertionBuffer();
                insertionBuffers.put(block, insertionBuffer);
                assert !insertionBuffer.initialized() : "already initialized?";
                ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
                insertionBuffer.init(instructions);
            }
            return insertionBuffer;
        }
    }
}
