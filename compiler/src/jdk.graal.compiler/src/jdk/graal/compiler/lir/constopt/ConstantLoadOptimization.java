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
package jdk.graal.compiler.lir.constopt;

import static jdk.graal.compiler.lir.LIRValueUtil.asVariable;
import static jdk.graal.compiler.lir.LIRValueUtil.isVariable;
import static jdk.graal.compiler.lir.phases.LIRPhase.Options.LIROptimization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.InstructionValueConsumer;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInsertionBuffer;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstruction.OperandFlag;
import jdk.graal.compiler.lir.LIRInstruction.OperandMode;
import jdk.graal.compiler.lir.StandardOp.LoadConstantOp;
import jdk.graal.compiler.lir.ValueConsumer;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.constopt.ConstantTree.Flags;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.phases.PreAllocationOptimizationPhase;
import jdk.graal.compiler.options.NestedBooleanOptionKey;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;
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
                    for (BasicBlock<?> b : lir.getControlFlowGraph().getBlocks()) {
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
                    for (BasicBlock<?> b : lir.getControlFlowGraph().getBlocks()) {
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
                    if (operand.getPlatformKind().getVectorLength() == 1) {
                        assert !operand.equals(var) : "constant usage through variable in frame state " + var;
                    }
                }
            };
            for (BasicBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
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
            BasicBlock<?> block = entry.getBlock();
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
        private void analyzeBlock(BasicBlock<?> block) {
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

                InstructionValueConsumer stateVectorUseConsumer = (instruction, value, mode, flags) -> {
                    /* States may use vector constants directly via variables. */
                    if (value.getPlatformKind().getVectorLength() > 1) {
                        useConsumer.visitValue(instruction, value, mode, flags);
                    }
                };

                int opId = 0;
                for (LIRInstruction inst : lir.getLIRforBlock(block)) {
                    // set instruction id to the index in the lir instruction list
                    inst.setId(opId++);
                    inst.visitEachOutput(loadConsumer);
                    inst.visitEachInput(useConsumer);
                    inst.visitEachAlive(useConsumer);
                    inst.visitEachState(stateVectorUseConsumer);
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
            constTree.set(ConstantTree.Flags.SUBTREE, tree.getBlock());
            tree.forEach(u -> constTree.set(ConstantTree.Flags.USAGE, u.getBlock()));

            if (constTree.get(ConstantTree.Flags.USAGE, tree.getBlock())) {
                // usage in the definition block -> no optimization
                usageAtDefinitionSkipped.increment(debug);
                return;
            }

            constTree.markBlocks();

            ConstantTree.NodeCost cost = ConstantTreeAnalyzer.analyze(debug, constTree, tree.getBlock());
            int usageCount = cost.getUsages().size();
            assert usageCount == tree.usageCount() : "Usage count differs: " + usageCount + " vs. " + tree.usageCount();

            if (debug.isLogEnabled()) {
                try (Indent i = debug.logAndIndent("Variable: %s, Block: %s, freq.: %f", tree.getVariable(), tree.getBlock(), tree.getBlock().getRelativeFrequency())) {
                    debug.log("Usages result: %s", cost);
                }

            }

            /**
             * If compiler code explicitly requests to not spill into this block we should also not
             * re-materialize constants here as that can break other live ranges due to register
             * pressure and result in all sorts of strange spill decisions.
             */
            class BConsumer implements Consumer<BasicBlock<?>> {
                boolean loadSplitDisabled = false;

                @Override
                public void accept(BasicBlock<?> block) {
                    if (constTree.get(Flags.CANDIDATE, block)) {
                        if (!block.canUseBlockAsSpillTarget()) {
                            loadSplitDisabled = true;
                        }
                    }
                }
            }
            BConsumer c = new BConsumer();
            constTree.stream(Flags.SUBTREE).forEach(c);

            if (!c.loadSplitDisabled && (cost.getNumMaterializations() > 1 || cost.getBestCost() < tree.getBlock().getRelativeFrequency())) {
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

        private void createLoads(DefUseTree tree, ConstantTree constTree, BasicBlock<?> startBlock) {
            Deque<BasicBlock<?>> worklist = new ArrayDeque<>();
            worklist.add(startBlock);
            while (!worklist.isEmpty()) {
                BasicBlock<?> block = worklist.pollLast();
                if (constTree.get(ConstantTree.Flags.CANDIDATE, block)) {
                    constTree.set(ConstantTree.Flags.MATERIALIZE, block);
                    // create and insert load
                    insertLoad(tree.getConstant(), tree.getVariable().getValueKind(), block, constTree.getCost(block).getUsages());
                } else {
                    BasicBlock<?> dominated = block.getFirstDominated();
                    while (dominated != null) {
                        if (constTree.isMarked(dominated)) {
                            worklist.addLast(dominated);
                        }
                        dominated = dominated.getDominatedSibling();
                    }
                }
            }
        }

        private void insertLoad(Constant constant, ValueKind<?> kind, BasicBlock<?> block, List<UseEntry> usages) {
            assert usages != null && usages.size() > 0 : String.format("No usages %s %s %s", constant, block, usages);
            // create variable
            Variable variable = lirGen.newVariable(kind);
            // create move
            LIRInstruction move = lirGen.getSpillMoveFactory().createLoad(variable, constant);
            // insert instruction
            int insertionIndex = lirGen.getResult().getFirstInsertPosition();
            getInsertionBuffer(block).append(insertionIndex, move);
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
        private void rewriteBlock(BasicBlock<?> block) {
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
            BasicBlock<?> block = tree.getBlock();
            LIRInstruction instruction = tree.getInstruction();
            debug.log("deleting instruction %s from block %s", instruction, block);
            lir.getLIRforBlock(block).set(instruction.id(), null);
        }

        private LIRInsertionBuffer getInsertionBuffer(BasicBlock<?> block) {
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
