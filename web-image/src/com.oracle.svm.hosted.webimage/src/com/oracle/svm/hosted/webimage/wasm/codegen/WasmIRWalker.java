/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.codegen;

import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Break;
import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Const;
import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.If;
import static com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Unreachable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.graalvm.collections.Pair;

import com.oracle.svm.core.graal.nodes.ReadExceptionObjectNode;
import com.oracle.svm.hosted.webimage.codegen.irwalk.StackifierIRWalker;
import com.oracle.svm.hosted.webimage.codegen.lowerer.MoveResolver;
import com.oracle.svm.hosted.webimage.codegen.lowerer.PhiResolveLowerer;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.ReconstructionData;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.CatchScopeContainer;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.IfScopeContainer;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.LabeledBlock;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.LabeledBlockGeneration;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.LoopScopeContainer;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.Scope;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.SwitchScopeContainer;
import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.metrickeys.MethodMetricKeys;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Binary;
import com.oracle.svm.hosted.webimage.wasm.ast.id.KnownIds;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.phases.WasmSwitchPhase;
import com.oracle.svm.webimage.hightiercodegen.variables.ResolvedVar;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.java.TypeSwitchNode;
import jdk.vm.ci.meta.JavaKind;

public class WasmIRWalker extends StackifierIRWalker {

    /**
     * Encodes lowering requirements.
     * <p>
     * The default values for each field determine the default lowering requirements.
     */
    public static class Requirements implements Cloneable {

        /**
         * If {@code true}, {@link LogicNode}s need to produce either 0 (for false) or 1 (for true).
         * Otherwise, they can produce 0 for false and any other value for true.
         * <p>
         * Wasm instructions that consume a logic value (e.g. the if condition) can lower their
         * inputs with this set to false.
         */
        private boolean strictLogic = true;

        public static Requirements defaults() {
            return new Requirements();
        }

        /**
         * Returns a set of requirements for when the produced value is ignored.
         */
        public static Requirements valueIgnored() {
            return defaults().setStrictLogic(false);
        }

        @Override
        public Requirements clone() {
            try {
                return (Requirements) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean hasStrictLogic() {
            return strictLogic;
        }

        public Requirements setStrictLogic(boolean strictLogic) {
            this.strictLogic = strictLogic;
            return this;
        }
    }

    protected final WasmCodeGenTool masm;

    public WasmIRWalker(WasmCodeGenTool codeGenTool, ControlFlowGraph cfg, BlockMap<List<Node>> blockToNodeMap, NodeMap<HIRBlock> nodeToBlockMap, ReconstructionData reconstructionData) {
        super(codeGenTool, cfg, blockToNodeMap, nodeToBlockMap, reconstructionData);
        this.masm = codeGenTool;
    }

    @Override
    protected void lower(DebugContext debugContext) {
        LoggerContext.counter(MethodMetricKeys.NUM_BLOCKS).add(stackifierData.getBlocks().length);

        masm.lowerPreamble();

        super.lower(debugContext);

        if (cfg.graph.method().getSignature().getReturnKind() != JavaKind.Void) {
            /*
             * There is no implicit-return in the Graal IR and for non-void methods, validation
             * fails since it does not track reachability like that and the function end seems
             * reachable (even though it isn't).
             */
            List<Instruction> instructions = masm.getInstructions().get();
            if (!instructions.isEmpty() && !(instructions.getLast() instanceof Instruction.Return)) {
                masm.genInst(new Unreachable(), "Implicit return unreachable");
            }
        }

        masm.finish();
    }

    @Override
    protected void predeclareVariables(StructuredGraph graph) {
        /*
         * Mark all phi variables as "definition lowered" since phi nodes are never directly
         * declared through lowerNode.
         */
        for (AbstractMergeNode merge : graph.getNodes(AbstractMergeNode.TYPE)) {
            for (PhiNode phi : merge.phis()) {
                ResolvedVar phiVar = masm.getAllocatedVariable(phi);
                if (phiVar != null) {
                    phiVar.setDefinitionLowered();
                }
            }
        }
    }

    /**
     * Generates a forward jump, i.e. a labeled {@code break}, if necessary.
     *
     * @param currentBlock block from which to jump from
     * @param successor target of the jump
     */
    private void generateForwardJump(HIRBlock currentBlock, HIRBlock successor) {
        WasmId.Label id = getForwardJumpTarget(currentBlock, successor);

        if (id != null) {
            masm.genInst(new Break(id), "forward jump");
        }
    }

    private WasmId.Label getForwardJumpTarget(HIRBlock currentBlock, HIRBlock successor) {
        if (LabeledBlockGeneration.isNormalLoopExit(currentBlock, successor, stackifierData)) {
            LabeledBlock jumpTarget = stackifierData.labeledBlockEnd(successor);
            assert jumpTarget != null : "Cannot jump out of loop " + currentBlock.getLoop();
            return getLabeledBlockId(jumpTarget);
        } else if (stackifierData.getLabeledBlockGeneration().isLabeledBlockNeeded(currentBlock, successor)) {
            LabeledBlock forwardBlock = stackifierData.labeledBlockEnd(successor);
            return getLabeledBlockId(forwardBlock);
        }

        return null;
    }

    /**
     * Lower all basic blocks that belong to a loop and the corresponding loop-header and
     * loop-footer. Example to clarify the break statement at the end of a loop - Suppose we have
     * the following Java program:
     *
     * <pre>
     * {@code
     * while (true) {
     *     if (condition2) {
     *         A();
     *     } else {
     *         B();
     *         break;
     *     }
     * }
     * C();
     * }
     * </pre>
     * <p>
     * With the topological order A->B->C, the stackifier would produce:
     *
     * <pre>
     * {@code
     * (block $bl0
     *   (loop $loop0
     *     (if condition2
     *       (then
     *         A()
     *         br $loop0 ;; continue
     *       )
     *       (else
     *         B()
     *         br $bl0
     *       )
     *     )
     *   )
     * )
     * C()
     * }
     * </pre>
     * <p>
     * In WASM (as in the Graal IR), jumping to the loop header must be explicit. Reaching the end
     * of the loop block, falls out of the loop. The break instruction in the else branch is thus
     * not strictly necessary, however, the heuristics to place labeled blocks are too imprecise in
     * this case.
     *
     * @param currentBlock Loop header block
     */
    @Override
    protected void lowerLoop(HIRBlock currentBlock) {
        assert currentBlock.isLoopHeader() : currentBlock.toString(Verbosity.Name);
        LoopScopeContainer loopScopeEntry = (LoopScopeContainer) stackifierData.getScopeEntry(currentBlock.getBeginNode());
        Scope loopScope = loopScopeEntry.getLoopScope();
        Instruction.Loop loop = labeledLoop(currentBlock);
        lowerBlocks(loopScope.getSortedBlocks(stackifierData));
        masm.parentScope(loop.getLabel());
    }

    @Override
    protected void lowerLoopEnd(LoopEndNode loopEnd) {
        lowerPhiSchedule(loopEnd);
        LoopBeginNode loopBeginNode = loopEnd.loopBegin();
        LoopScopeContainer scopeEntry = (LoopScopeContainer) stackifierData.getScopeEntry(loopBeginNode);
        HIRBlock loopHeader = scopeEntry.getLoopScope().getStartBlock();

        // continue instruction
        masm.genInst(new Break(getLabeledLoopId(loopHeader)), loopEnd);
    }

    /**
     * Lower a WithExceptionNode.
     *
     * <pre>
     * {@code
     * (try
     *   (do
     *     WithExceptionNode();
     *   )
     *   (catch $exc_tag
     *     (local.set $exc_var)
     *     ExceptionEdge();
     *   )
     * )
     * }
     * </pre>
     *
     * The {@link WithExceptionNode} is wrapped in a {@code try} block and the catch block contains
     * its exception edge. In WASM, exception types are distinguished by tags,
     * {@link KnownIds#getJavaThrowableTag()} is used for thrown Java exceptions (tags can't
     * represent different Java exceptions because the tag associated with a {@code throw}
     * instruction must be defined at compile-time). In addition, in the catch block, the thrown
     * value is already on the stack, we store it in a dedicated local variable
     * ({@link WebImageWasmNodeLowerer#exceptionObjectVariable}) so that it can be read later by
     * {@link ReadExceptionObjectNode}.
     *
     * @param currentBlock basic block that ends with {@link WithExceptionNode}
     * @param lastNode the {@link WithExceptionNode}
     */
    @Override
    protected void lowerWithException(HIRBlock currentBlock, WithExceptionNode lastNode) {
        assert currentBlock.getEndNode() == lastNode : currentBlock.toString(Verbosity.Name);

        Instruction.Try tryBlock = new Instruction.Try(null);
        masm.genInst(tryBlock, lastNode);

        masm.childScope(tryBlock.instructions, tryBlock);
        lowerNode(lastNode);
        HIRBlock normSucc = cfg.blockFor(lastNode.next());
        generateForwardJump(currentBlock, normSucc);
        masm.parentScope(tryBlock);

        masm.lowerCatchBlock(tryBlock, () -> {
            CatchScopeContainer scopeEntry = (CatchScopeContainer) stackifierData.getScopeEntry(lastNode);
            Scope catchScope = scopeEntry.getCatchScope();
            if (catchScope != null) {
                lowerBlocks(catchScope.getSortedBlocks(stackifierData));
            } else {
                HIRBlock excpSucc = cfg.blockFor(lastNode.exceptionEdge());
                generateForwardJump(currentBlock, excpSucc);
            }
        });
    }

    /**
     * Lower an IfNode. If there are {@link Scope} for the then/else branch, the scopes are lowered
     * in their own recursive call.
     *
     * @param currentBlock basic block ending with {@link IfNode}
     * @param lastNode the {@link IfNode}
     */
    @Override
    protected void lowerIf(HIRBlock currentBlock, IfNode lastNode) {
        assert currentBlock.getEndNode() == lastNode : currentBlock.toString(Verbosity.Name);
        IfScopeContainer ifScopeContainer = (IfScopeContainer) stackifierData.getScopeEntry(lastNode);

        If ifBlock = new If(null, masm.lowerExpression(lastNode.condition(), Requirements.defaults().setStrictLogic(false)));
        masm.genInst(ifBlock, lastNode);

        Scope thenScope = ifScopeContainer.getThenScope();
        masm.childScope(ifBlock.thenInstructions, ifBlock);
        if (thenScope != null) {
            lowerBlocks(thenScope.getSortedBlocks(stackifierData));
        } else {
            HIRBlock trueBlock = nodeToBlockMap.get(lastNode.trueSuccessor());
            generateForwardJump(currentBlock, trueBlock);
        }
        masm.parentScope(ifBlock);
        masm.childScope(ifBlock.elseInstructions, ifBlock);
        Scope elseScope = ifScopeContainer.getElseScope();
        if (elseScope != null) {
            lowerBlocks(elseScope.getSortedBlocks(stackifierData));
        } else {
            HIRBlock falseBlock = nodeToBlockMap.get(lastNode.falseSuccessor());
            generateForwardJump(currentBlock, falseBlock);
        }
        masm.parentScope(ifBlock);
    }

    @Override
    protected void lowerUnhandledBlockEnd(HIRBlock currentBlock, FixedNode lastNode) {
        if (lastNode instanceof AbstractEndNode endNode) {
            lowerPhiSchedule(endNode);
        } else if (!(lastNode instanceof LoopExitNode) && !(lastNode instanceof LoopBeginNode)) {
            /*
             * Special case for basic blocks with only one node. LoopExitNode and LoopBeginNode need
             * to be handled separately again, as they must not be lowered by the node lowerer.
             */
            lowerNode(lastNode);
        }

        generateForwardJump(currentBlock, getSuccessorForUnhandledBlockEnd(lastNode));
    }

    /**
     * The br_table instruction is used to mimic the switch behavior. Every successor is lowered
     * after a labeled block and the br_table instruction uses that label for every key that jumps
     * to that successor.
     * <p>
     * Example: Suppose we have the following Java program where A, B, C and D roughly correspond to
     * basic blocks:
     *
     * <pre>
     * {@code
     *  int a;
     *  ...
     *  switch(a){
     *      case 0:
     *      case 1:
     *              A();
     *              break;
     *      case 2:
     *              B();
     *              break;
     *      case 3:
     *              C();
     *              break;
     *      default:
     *              D();
     *  }
     * }
     * </pre>
     * <p>
     * With the topological order A->B->C->D, this will generate the following WASM code:
     *
     * <pre>
     * {@code
     * (block $bl0
     *   (block $bl1
     *     (block $bl2
     *       (block $bl3
     *         (block $bl4
     *           (br_table $bl4 $bl4 $bl3 $bl2 $bl1 a)
     *         )
     *         A()
     *         br $bl0
     *       )
     *       B()
     *       br $bl0
     *     )
     *     C()
     *     br $bl0
     *   )
     *   D()
     * )
     * }
     * </pre>
     */
    @Override
    protected void lowerSwitch(IntegerSwitchNode switchNode) {
        if (!WasmSwitchPhase.isSimplified(switchNode)) {
            lowerDegenerateSwitch(switchNode);
            return;
        }

        assert WasmSwitchPhase.isSimplified(switchNode);
        assert switchNode.defaultSuccessor() != null;
        int numSuccessors = switchNode.blockSuccessorCount();

        Scope[] caseScopes = ((SwitchScopeContainer) stackifierData.getScopeEntry(switchNode)).getCaseScopes();
        assert caseScopes != null;
        assert caseScopes.length == numSuccessors : caseScopes.length + " != " + numSuccessors;

        // Maps each successor block indices to all keys that jump to it.
        Map<Integer, List<Integer>> successorToKeys = WasmSwitchPhase.getSuccessorToKeysMap(switchNode);

        Instruction.BreakTable brTable = new Instruction.BreakTable(masm.lowerExpression(switchNode.value()));

        /*
         * If successor index x needs a labeled block for jumping, the entry at index x is set to
         * the id for that block.
         *
         * Successors which produce only a forward jump don't need a separate block, the instruction
         * can directly target that jump.
         */
        WasmId.Label[] blockLabels = new WasmId.Label[numSuccessors];

        // Iterate over the blocks in reverse order to emit the case scopes in order
        for (int successorIndex = numSuccessors - 1; successorIndex >= 0; successorIndex--) {
            // A new labeled block is only necessary if we emit the case scope directly
            // after it.
            boolean necessary = caseScopes[successorIndex] != null;

            AbstractBeginNode successor = switchNode.blockSuccessor(successorIndex);
            boolean isDefault = successorIndex == switchNode.defaultSuccessorIndex();

            WasmId.Label id;
            if (necessary) {
                id = masm.idFactory.newSwitchLabel();
                genLabeledBlockHeader(id, "Case: " + (isDefault ? "default" : Integer.toString(successorIndex)));
                blockLabels[successorIndex] = id;
            } else {
                id = getForwardJumpTarget(cfg.blockFor(switchNode), cfg.blockFor(successor));
            }

            if (isDefault) {
                brTable.setDefaultTarget(id);
            } else {
                for (Integer key : successorToKeys.get(successorIndex)) {
                    brTable.setTarget(key, id);
                }
            }
        }

        masm.genInst(brTable, switchNode);

        // Emit the case scopes in the right order.
        for (int successorIndex = 0; successorIndex < numSuccessors; successorIndex++) {
            WasmId.Label blockLabel = blockLabels[successorIndex];
            if (blockLabel == null) {
                continue;
            }

            masm.parentScope(blockLabel);
            lowerBlocks(caseScopes[successorIndex].getSortedBlocks(stackifierData));
        }
    }

    @Override
    protected void lowerTypeSwitch(TypeSwitchNode switchNode) {
        throw GraalError.unimplementedOverride();
    }

    /**
     * The {@link WasmSwitchPhase} cannot completely remove degenerate switches because the
     * canonicalizer can reintroduce degenerate switches in some cases.
     * <p>
     * To deal with this, we lower those switches to a chain of if statements.
     */
    protected void lowerDegenerateSwitch(IntegerSwitchNode switchNode) {
        int numSuccessors = switchNode.getSuccessorCount();
        Map<Integer, List<Integer>> successorToKeys = WasmSwitchPhase.getSuccessorToKeysMap(switchNode);
        assert numSuccessors > 1 : "Degenerate switch must have more than one successor, has " + numSuccessors;

        Scope[] caseScopes = ((SwitchScopeContainer) stackifierData.getScopeEntry(switchNode)).getCaseScopes();
        assert caseScopes != null;
        assert caseScopes.length == numSuccessors : caseScopes.length + " != " + numSuccessors;

        /*
         * Opens a new if block for each successor. In the then-branch, the successor is lowered, in
         * the else-branch the if block for the next successor is lowered (or the default case).
         */
        for (int successorIndex = 0; successorIndex < numSuccessors; successorIndex++) {
            AbstractBeginNode successor = switchNode.blockSuccessor(successorIndex);

            // Skip default successor, it is lowered at the end.
            if (switchNode.defaultSuccessorIndex() == successorIndex) {
                continue;
            }

            List<Integer> keys = successorToKeys.get(successorIndex);
            assert !keys.isEmpty() : "Successor " + successor + " does not have any associated keys";

            // Equality check for each defined key
            Stream<Instruction> keyChecks = keys.stream().map(Const::forInt).map(keyInst -> Binary.Op.I32Eq.create(keyInst, masm.lowerExpression(switchNode.value())));
            // One big nested OR for all key checks.
            Instruction anyKeyCheck = keyChecks.reduce(Binary.Op.I32Or::create).get();

            If ifBlock = new If(null, anyKeyCheck);
            String comment = "Key check for " + keys;

            // The first if statement has a comment about this being a degenerate switch
            if (successorIndex == 0) {
                comment = "Degenerate switch: " + switchNode + ", " + comment;
            }

            masm.genInst(ifBlock, comment);

            // In the then branch, emit the successor for the keys.
            masm.childScope(ifBlock.thenInstructions, null);
            Scope caseScope = caseScopes[successorIndex];
            if (caseScope == null) {
                // If there is no case scope, jump to the successor which is outside of the switch
                // scope.
                WasmId.Label jumpTarget = getForwardJumpTarget(cfg.blockFor(switchNode), cfg.blockFor(successor));
                masm.genInst(new Break(jumpTarget), "forward jump out of switch");
            } else {
                lowerBlocks(caseScope.getSortedBlocks(stackifierData));
            }
            masm.parentScope(null);

            // In the else branch, open a child for the next iteration (or the default case).
            masm.childScope(ifBlock.elseInstructions, Pair.create(switchNode, successorIndex));
        }

        // Finally, lower the default successor in the innermost else branch.
        Scope defaultScope = caseScopes[switchNode.defaultSuccessorIndex()];
        if (defaultScope == null) {
            // If there is no case scope, jump to the successor which is outside of the switch
            // scope.
            WasmId.Label jumpTarget = getForwardJumpTarget(cfg.blockFor(switchNode), cfg.blockFor(switchNode.defaultSuccessor()));
            masm.genInst(new Break(jumpTarget), "forward jump out of switch to default case");
        } else {
            lowerBlocks(defaultScope.getSortedBlocks(stackifierData));
        }

        // Close all scopes again.
        for (int successorIndex = numSuccessors - 1; successorIndex >= 0; successorIndex--) {
            if (successorIndex == switchNode.defaultSuccessorIndex()) {
                continue;
            }
            masm.parentScope(Pair.create(switchNode, successorIndex));
        }
    }

    @Override
    protected void genLabeledBlockHeader(LabeledBlock labeledBlock) {
        genLabeledBlockHeader(getLabeledBlockId(labeledBlock), labeledBlock);
    }

    protected void genLabeledBlockHeader(WasmId.Label id, Object comment) {
        Instruction.Block block = new Instruction.Block(id);
        masm.genInst(block, comment);
        masm.childScope(block.instructions, id);
    }

    @Override
    protected void genLabeledBlockEnd(LabeledBlock labeledBlock) {
        masm.parentScope(getLabeledBlockId(labeledBlock));
    }

    protected WasmId.Label getLabeledBlockId(LabeledBlock labeledBlock) {
        return masm.idFactory.forBlockLabel(labeledBlock);
    }

    protected Instruction.Loop labeledLoop(HIRBlock loopHeader) {
        assert loopHeader.isLoopHeader() : loopHeader.toString(Verbosity.Name);
        Instruction.Loop loop = new Instruction.Loop(getLabeledLoopId(loopHeader));
        masm.genInst(loop, loopHeader.getBeginNode());
        masm.childScope(loop.instructions, loop.getLabel());
        return loop;
    }

    protected WasmId.Label getLabeledLoopId(HIRBlock loopHeader) {
        assert loopHeader.isLoopHeader() : loopHeader.toString(Verbosity.Name);
        return masm.idFactory.forLoopLabel(loopHeader);
    }

    protected void lowerPhiSchedule(AbstractEndNode node) {
        MoveResolver<ValueNode, ValuePhiNode>.Schedule schedule = new PhiResolveLowerer(node).scheduleMoves(masm);

        /*
         * While theoretically the moves only require a single live temporary at any given time. If
         * different types for that temporary are required, WASM will need distinct local variables
         * for each type.
         */
        Map<WasmValType, WasmId.Local> temporaries = new HashMap<>();

        for (var move : schedule.moves) {
            WasmValType type = masm.wasmProviders.util().typeForNode(move.source);

            if (move.target == null) {
                // Move into the temp variable
                assert schedule.needsTemporary() : "If a move to null exists, the schedule needs a temporary variable.";
                // For each new type a new temporary will be allocated on demand
                WasmId.Local target = temporaries.computeIfAbsent(type, masm.idFactory::newTemporaryVariable);
                Instruction value = masm.lowerExpression(move.source);
                masm.genInst(target.setter(value));
            } else {
                // Move into a phi node

                Instruction value;
                if (move.useTemporary) {
                    assert temporaries.containsKey(type) : "No temporary allocated for type " + type;
                    value = temporaries.get(type).getter();
                } else {
                    value = masm.lowerExpression(move.source);
                }

                masm.nodeLowerer().lowerVariableStore(move.target, value);
            }
        }
    }
}
