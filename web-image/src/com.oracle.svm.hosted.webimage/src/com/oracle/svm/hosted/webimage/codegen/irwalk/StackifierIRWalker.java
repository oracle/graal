/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.codegen.irwalk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

import com.oracle.svm.hosted.webimage.codegen.reconstruction.ReconstructionData;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.CatchScopeContainer;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.IfScopeContainer;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.LabeledBlock;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.LabeledBlockGeneration;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.LoopScopeContainer;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.Scope;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.StackifierData;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.StackifierScopeComputation;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.SwitchScopeContainer;
import com.oracle.svm.webimage.hightiercodegen.CodeGenTool;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.TypeSwitchNode;
import jdk.graal.compiler.replacements.nodes.BasicArrayCopyNode;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Generates code by using the Stackifier algorithm to handle control flow. See
 * {@link #lower(DebugContext)} for an example.
 */
public class StackifierIRWalker extends IRWalker {
    public static final String LABEL_PREFIX = "looplabel_";
    protected final BlockNestingVerifier blockNestingVerifier;

    protected final StackifierData stackifierData;

    public StackifierIRWalker(CodeGenTool codeGenTool, ControlFlowGraph cfg, BlockMap<List<Node>> blockToNodeMap, NodeMap<HIRBlock> nodeToBlockMap, ReconstructionData reconstructionData) {
        super(codeGenTool, cfg, blockToNodeMap, nodeToBlockMap, reconstructionData);
        this.blockNestingVerifier = new BlockNestingVerifier();
        this.stackifierData = (StackifierData) reconstructionData;
    }

    /**
     * Lower a function with the stackifier algorithm.
     *
     * Some examples to illustrate the basic version of the algorithm (for examples with the
     * optimized version, see {@link StackifierScopeComputation}):
     *
     * <pre>
     * if (condition) {
     *     A();
     * } else {
     *     B();
     * }
     * C();
     * </pre>
     *
     * will generate the following pseudocode:
     *
     * <pre>
     * block1: {
     *     block0: {
     *         if (condition) {
     *
     *         } else {
     *             break block0;
     *         }
     *         A();
     *         break block1;
     *     } // end of block 0
     *     B();
     * } // end of block1
     * C();
     * </pre>
     *
     * As one can see we use labeled blocks to jump over code that should not be executed. In the
     * implementation these blocks are implemented by {@link LabeledBlock}. One thing to note is
     * that the generated code can be optimized by putting A and B inside the then and else branch.
     * This would get rid of unnecessary blocks. This optimization is implemented in
     * {@link StackifierScopeComputation}. Also note that {@code B()} could be generated before
     * {@code A()} which would result in the {@code break} being generated in the then branch.
     *
     * Now an example with a loop:
     *
     * <pre>
     * A();
     * while (condition) {
     *     B();
     * }
     * C();
     * </pre>
     *
     * this will generate the following pseudocode:
     *
     * <pre>
     * A();
     * block0: {
     *     loop0: while (true) {
     *         if (condition) {
     *
     *         } else {
     *             break block0;
     *         }
     *         B();
     *         continue loop0;
     *         break loop0;
     *     } // end loop0
     * } // end block0
     * C();
     * </pre>
     *
     * For a loop we generate a labeled endless loop. All back edges from the IR graph are handled
     * via {@code continue} statements. As a side note, the end of a loop has an implicit back-edge.
     * Since all back-edges are handled via {@link LoopEndNode} and the corresponding
     * {@code continue} statement we place a {@code break} at the end of the loop to avoid this
     * backwards jump, see {@link #lowerLoop(HIRBlock)}.
     *
     * Now an example where we assume {@code A()} might produce an exception
     *
     * <pre>
     * try {
     *     A();
     * } catch (Exception e) {
     *     C(e);
     * }
     * B();
     * </pre>
     *
     * this will produce the following pseudocode:
     *
     * <pre>
     *  block0:{
     *      try{
     *          A();
     *      } catch(exceptionObject) {
     *          e = exceptionObject;
     *          break block0;
     *      }
     *      B();
     *  } //end of block 0
     *  C(e);
     * </pre>
     *
     * For an invoke with an exception edge we generate a try-catch block. In the catch block we
     * copy the exception object and then do a forward jump to the exception handler. This works in
     * principal like the if-then-else. Again as a side note, the order of {@code B()} and
     * {@code C()} in the {@link ControlFlowGraph} and thus in the generated code can be different.
     * The stackifier algorithm will generate blocks and {@code break} statements such that the
     * generated code is correct.
     */
    @Override
    @SuppressWarnings({"unused", "try"})
    protected void lower(DebugContext debugContext) {
        stackifierData.debugDump(debugContext);

        predeclareVariables(cfg.graph);

        lowerBlocks(stackifierData.getBlocks());
    }

    /**
     * Declare all variables that should be located at the top of the function before lowering, e.g.
     * PhiNodes.
     */
    protected void predeclareVariables(StructuredGraph graph) {
        // scan for all phi values needed in stacked scopes
        for (AbstractMergeNode n : graph.getNodes(AbstractMergeNode.TYPE)) {
            for (PhiNode phi : n.phis()) {
                if (codeGenTool.nodeLowerer().actualUsageCount(phi) > 0) {
                    codeGenTool.nodeLowerer().lower(phi);
                }
            }
        }
    }

    /**
     * Lower the given blocks. Recursively lower blocks that are inside a {@link Scope}.
     *
     * @param blocks blocks to be lowered
     */
    protected void lowerBlocks(HIRBlock[] blocks) {
        for (HIRBlock currentBlock : blocks) {
            if (blockHistory.blockVisited(currentBlock)) {
                continue;
            }

            boolean isLoopHeader = currentBlock.isLoopHeader();

            /*
             * If this block is a loop header, some labeled blocks need to start before the loop
             * header and some need to start directly after, depending on whether the blocks are
             * supposed to end outside or inside the loop.
             */
            SortedSet<LabeledBlock> labeledBlockStartsBeforeLoop = LabeledBlockGeneration.getSortedSetByLabeledBlockEnd(stackifierData);
            SortedSet<LabeledBlock> labeledBlockStartsAfterLoop = LabeledBlockGeneration.getSortedSetByLabeledBlockEnd(stackifierData);

            SortedSet<LabeledBlock> blockStarts = stackifierData.labeledBlockStarts(currentBlock);

            if (blockStarts != null) {
                for (LabeledBlock forwardBlock : blockStarts) {
                    /*
                     * A labeled block needs to start after the loop header if it ends inside the
                     * loop.
                     */
                    if (isLoopHeader && labeledBlockEndsInLoop((LoopScopeContainer) stackifierData.getScopeEntry(currentBlock.getBeginNode()), forwardBlock)) {
                        labeledBlockStartsAfterLoop.add(forwardBlock);
                    } else {
                        labeledBlockStartsBeforeLoop.add(forwardBlock);
                    }
                }
            }

            /*
             * Generate the end of the labeled block before a possible loop header as well as all
             * labeled blocks that need to start before the loop header.
             */
            if (!isInRecursiveLoopCall(isLoopHeader, currentBlock, blocks)) {
                // generate end of forward blocks
                LabeledBlock blockEnd = stackifierData.labeledBlockEnd(currentBlock);
                if (blockEnd != null) {
                    genLabeledBlockEnd(blockEnd);
                }

                // Open labeled blocks before loop header
                labeledBlockStartsBeforeLoop.forEach(this::genLabeledBlockHeader);
            }

            /*
             * Lower a loop in its own recursive call. This allows us to exactly place the end of
             * the loop. The recursive call is only executed if the loop does not start with the
             * first basic block. If it does, we are already in that recursive call.
             */
            if (newLoopStartsHere(isLoopHeader, currentBlock, blocks)) {
                lowerLoop(currentBlock);
                continue;
            }

            codeGenTool.genComment("Start of block " + currentBlock);

            // Open labeled blocks after loop header
            labeledBlockStartsAfterLoop.forEach(this::genLabeledBlockHeader);

            for (Node node : blockToNodeMap.get(currentBlock)) {
                if (node == currentBlock.getEndNode()) {
                    break;
                } else if (node instanceof LoopBeginNode) {
                    /*
                     * We should only be able to visit a LoopBeginNode if we are recursively called
                     * from lowerLoop and as such the node can be ignored since lowerLoop already
                     * emitted the loop header.
                     */
                    assert currentBlock == blocks[0] : Assertions.errorMessage(currentBlock, blocks[0]);
                } else {
                    lowerNode(node);
                }
                // for verification purposes
                verifier.visitNode(node, codeGenTool);
            }

            FixedNode lastNode = currentBlock.getEndNode();
            if (lastNode instanceof ControlSinkNode) {
                lowerNode(lastNode);
            } else if (lastNode instanceof IfNode ifNode) {
                lowerIf(currentBlock, ifNode);
            } else if (lastNode instanceof IntegerSwitchNode switchNode) {
                lowerSwitch(switchNode);
            } else if (lastNode instanceof TypeSwitchNode switchNode) {
                lowerTypeSwitch(switchNode);
            } else if (isWithExceptionNode(lastNode)) {
                lowerWithException(currentBlock, (WithExceptionNode) lastNode);
            } else if ((lastNode instanceof ControlSplitNode) && !(lastNode instanceof BasicArrayCopyNode)) {
                // BasicArrayCopyNode is also a ControlSplitNode
                assert false : "Unsupported control split node " + lastNode + " is not implemented yet";
            } else if (lastNode instanceof LoopEndNode loopEnd) {
                lowerLoopEnd(loopEnd);
            } else {
                lowerUnhandledBlockEnd(currentBlock, lastNode);
            }

            verifier.visitNode(lastNode, codeGenTool);
            blockHistory.visitBlock(currentBlock);
            codeGenTool.genComment("End of block " + currentBlock);
        }
    }

    /**
     * Whether the lowering of the given currentBlock happens inside a recursive call from
     * {@link #lowerLoop(HIRBlock)} and the current block is the start of the loop.
     *
     * If this is true, the loop header was already emitted otherwise it hasn't.
     */
    private static boolean isInRecursiveLoopCall(boolean isLoopHeader, HIRBlock currenBlock, HIRBlock[] blocks) {
        return isLoopHeader && currenBlock == blocks[0];
    }

    private static boolean newLoopStartsHere(boolean isLoopHeader, HIRBlock currenBlock, HIRBlock[] blocks) {
        return isLoopHeader && currenBlock != blocks[0];
    }

    protected boolean isWithExceptionNode(Node lastNode) {
        return lastNode instanceof WithExceptionNode;
    }

    /**
     * Generates a forward jump, i.e. a labeled {@code break}, if necessary.
     *
     * @param currentBlock block from which to jump from
     * @param successor target of the jump
     */
    private void generateForwardJump(HIRBlock currentBlock, HIRBlock successor) {
        if (LabeledBlockGeneration.isNormalLoopExit(currentBlock, successor, stackifierData)) {
            CFGLoop<HIRBlock> loop = currentBlock.getLoop();
            Scope loopScope = ((LoopScopeContainer) stackifierData.getScopeEntry(loop.getHeader().getBeginNode())).getLoopScope();
            Scope innerScope = stackifierData.getEnclosingScope().get(currentBlock);
            /*
             * We try to emit a simple break without a label. This is only possible if the
             * currentBlock is not inside a switch because otherwise the break would be associated
             * with the switch.
             */
            while (!innerScope.equals(loopScope) && !(innerScope.getStartBlock().getEndNode() instanceof IntegerSwitchNode)) {
                innerScope = innerScope.getParentScope();
            }
            if (innerScope.equals(loopScope) && !(currentBlock.getEndNode() instanceof IntegerSwitchNode)) {
                // simple break suffices
                codeGenTool.genBreak();
            } else { // a label is needed
                codeGenTool.genBreak(getLabel(loop.getHeader()));
            }
        } else if (stackifierData.getLabeledBlockGeneration().isLabeledBlockNeeded(currentBlock, successor)) {
            LabeledBlock forwardBlock = stackifierData.labeledBlockEnd(successor);
            codeGenTool.genBreak(forwardBlock.getLabel());
        }
    }

    /**
     * Lower all basic blocks that belong to a loop and the corresponding loop-header and
     * loop-footer. Example to clarify the break statement at the end of a loop - Suppose we have
     * the following Java program:
     *
     * <pre>
     * while (true) {
     *     if (condition2) {
     *         A();
     *     } else {
     *         B();
     *         break;
     *     }
     * }
     * C();
     * </pre>
     *
     * With the topological order A->B->C, the stackifier would produce:
     *
     * <pre>
     * loop1: while (true) {
     *     block2: {
     *         if (condition2) {
     *
     *         } else {
     *             break block2;
     *         }
     *         A();
     *         continue loop1;
     *     } // block2
     *     B();
     *     break loop1;
     * } // loop1
     * C();
     * </pre>
     *
     * Since C is the immediate successor of B there is no {@link LabeledBlock} or {@code break}
     * generated for the edge B->C. Without the statement {@code break loop1;}, after executing
     * {@code B()} the generated code would go back to the loop start. This would be incorrect. What
     * we want is to go to {@code C()}. Therefore a {@code break} is inserted at the loop end which
     * makes the back-edge at the end of the loop unreachable and adds a loop exit.
     *
     * @param currentBlock current block
     */
    protected void lowerLoop(HIRBlock currentBlock) {
        assert currentBlock.isLoopHeader();
        LoopScopeContainer loopScopeEntry = (LoopScopeContainer) stackifierData.getScopeEntry(currentBlock.getBeginNode());
        Scope loopScope = loopScopeEntry.getLoopScope();

        genLoopHeader(currentBlock);

        lowerBlocks(loopScope.getSortedBlocks(stackifierData));
        genLoopEnd(currentBlock);
    }

    protected void lowerLoopEnd(LoopEndNode loopEnd) {
        lowerLoopEndResolver(loopEnd);
        codeGenTool.genLoopContinue();
    }

    /**
     * Lower a WithExceptionNode. For an example how the generated code looks like, see
     * {@link #lower(DebugContext)}.
     *
     * @param currentBlock basic block that contains {@link WithExceptionNode}
     * @param lastNode the {@link WithExceptionNode}
     */
    protected void lowerWithException(HIRBlock currentBlock, WithExceptionNode lastNode) {
        HIRBlock normSucc = cfg.blockFor(lastNode.next());
        HIRBlock excpSucc = cfg.blockFor(lastNode.exceptionEdge());
        CatchScopeContainer scopeEntry = (CatchScopeContainer) stackifierData.getScopeEntry(lastNode);
        Scope catchScope = scopeEntry.getCatchScope();

        codeGenTool.genTryBlock();
        /*
         * Since the ExceptionObjectNode is needed inside the catch block already, the
         * ExceptionObjectNode is lowered here together with the InvokeWithExceptionNode and skipped
         * once it is encountered in its own basic block.
         */
        lowerNode(lastNode);
        generateForwardJump(currentBlock, normSucc);

        String caughtObjectName = codeGenTool.getExceptionObjectId(excpSucc.getBeginNode());
        ResolvedJavaType caughtObjectType = codeGenTool.getProviders().getMetaAccess().lookupJavaType(Throwable.class);
        /*
         * The exception edge does not necessarily have to be an ExceptionObjectNode. It could also,
         * for example, be an UnreachableBeginNode. To cover those instances, the default type of
         * the caught object is set to Throwable, and only changed if the exception edge indeed is
         * an ExceptionObjectNode
         */
        if (excpSucc.getBeginNode() instanceof ExceptionObjectNode excpObj) {
            caughtObjectType = excpObj.stamp(NodeView.DEFAULT).javaType(codeGenTool.getProviders().getMetaAccess());
        }
        codeGenTool.genCatchBlockPrefix(caughtObjectName, caughtObjectType);

        if (catchScope != null) {
            lowerBlocks(catchScope.getSortedBlocks(stackifierData));
        } else {
            generateForwardJump(currentBlock, excpSucc);
        }
        codeGenTool.genScopeEnd();
    }

    protected void lowerIfHeader(IfNode ifNode) {
        codeGenTool.genIfHeader(ifNode.condition());
    }

    /**
     * Lower an IfNode. If there are {@link Scope} for the then/else branch, the scopes are lowered
     * in their own recursive call.
     *
     * @param currentBlock basic block containing the {@link IfNode}
     * @param lastNode the {@link IfNode}
     */
    protected void lowerIf(HIRBlock currentBlock, IfNode lastNode) {
        IfScopeContainer ifScopeContainer = (IfScopeContainer) stackifierData.getScopeEntry(lastNode);
        Scope thenScope = ifScopeContainer.getThenScope();
        Scope elseScope = ifScopeContainer.getElseScope();
        lowerIfHeader(lastNode);
        if (thenScope != null) {
            lowerBlocks(thenScope.getSortedBlocks(stackifierData));
        } else {
            HIRBlock trueBlock = nodeToBlockMap.get(lastNode.trueSuccessor());
            generateForwardJump(currentBlock, trueBlock);
        }
        codeGenTool.genElseHeader();
        if (elseScope != null) {
            lowerBlocks(elseScope.getSortedBlocks(stackifierData));
        } else {
            HIRBlock falseBlock = nodeToBlockMap.get(lastNode.falseSuccessor());
            generateForwardJump(currentBlock, falseBlock);
        }
        codeGenTool.genScopeEnd();
    }

    /**
     * Lowers the end of an HIR block (including the last node) in case the last node is not a
     * control-flow node.
     * <p>
     * Also generates a potential forward jump to the next basic block in the control-flow-graph.
     */
    protected void lowerUnhandledBlockEnd(HIRBlock currentBlock, FixedNode lastNode) {
        if (!(lastNode instanceof LoopBeginNode)) {
            /*
             * Special case for basic blocks with only one node. LoopBeginNode need to be handled
             * separately again, as they must not be lowered by the node lowerer.
             */
            lowerNode(lastNode);
        }

        generateForwardJump(currentBlock, getSuccessorForUnhandledBlockEnd(lastNode));
    }

    /**
     * Determines successor block that should be jumped to after the given last node of the block is
     * executed.
     */
    protected HIRBlock getSuccessorForUnhandledBlockEnd(FixedNode lastNode) {
        Iterator<? extends Node> it = lastNode.cfgSuccessors().iterator();
        Node singleSuccessor = it.next();
        assert !it.hasNext() : "Node " + lastNode + " has multiple successors";
        return nodeToBlockMap.get(singleSuccessor);
    }

    /**
     * Determine whether a given {@link LabeledBlock} that starts at the same block as a loop starts
     * ends inside the loop or outside of it.
     *
     * @param loopScopeContainer loopBlock that starts at the loop header
     * @param labeledBlock labeled block that starts at the loop header
     * @return true iff the basic block ends inside the loop
     */
    public static boolean labeledBlockEndsInLoop(LoopScopeContainer loopScopeContainer, LabeledBlock labeledBlock) {
        HIRBlock labeledBlockEnd = labeledBlock.getEnd();
        return loopScopeContainer.getLoopScope().getBlocks().contains(labeledBlockEnd);
    }

    /**
     * Generates code for one case of an integer switch node. This only generates the condition
     * (`case` statement); the block successor must be generated separately.
     *
     * @param switchNode the {@link IntegerSwitchNode} that is being lowered
     * @param successor the block successor corresponding to the case in question.
     * @param keys the switch keys that have `successor` as their block successor.
     */
    protected void lowerSwitchCase(IntegerSwitchNode switchNode, AbstractBeginNode successor, int[] keys) {
        codeGenTool.genSwitchCase(keys);
    }

    /**
     * Generates code for the default case of an integer switch.
     *
     * @param switchNode the {@link IntegerSwitchNode} that is being lowered.
     */
    protected void lowerSwitchDefaultCase(IntegerSwitchNode switchNode) {
        codeGenTool.genSwitchDefaultCase();
    }

    /**
     * Lowers the given {@link IntegerSwitchNode}. If multiple keys lead to the same successor,
     * multiple {@code case} statements need to be generated for the successor.
     *
     * Example: Suppose we have the following Java program where A, B, C and D roughly correspond to
     * basic blocks (for simplicity assume that all these basic blocks end with a
     * {@link ControlSinkNode}):
     *
     * <pre>
     *  int a;
     *  ...
     *  switch(a){
     *      case 0:
     *      case 2:
     *             A();
     *             break;
     *      case 4:
     *              B();
     *      case 6:
     *              C();
     *              break;
     *      default:
     *              D();
     *  }
     * </pre>
     *
     * Witch the topological order A->D->B->C, this will generate the following pseudocode:
     *
     * <pre>
     * block3: {
     *     block2: {
     *         block1: {
     *             block0: {
     *                 switch (a) {
     *                     case 0:
     *                     case 2:
     *                         break;
     *                     case 4:
     *                         break block1;
     *                     case 6:
     *                         break block2;
     *                     default:
     *                         break block0;
     *                 } // switch
     *                 A();
     *             } // block0
     *             D();
     *         } // block1
     *         B();
     *         break block3;
     *     } // block2
     * } // block3
     * C();
     * </pre>
     *
     * Note that the {@link LabeledBlock} {@code block3} is not necessary, but will be produced
     * because the Graal IR sometimes produces a {@link HIRBlock} that only contains a
     * {@link BeginNode} and a {@link EndNode}. Since no code is emitted for such a
     * {@link HIRBlock}, there is no generated code between the ends of {@code block2} and
     * {@code block3}.
     *
     * Similar to if-then-else, an optimization could pull the code of A, B, C and D inside the case
     * blocks.
     *
     * @param switchNode node to be lowered
     */
    protected void lowerSwitch(IntegerSwitchNode switchNode) {
        codeGenTool.genSwitchHeader(switchNode.value());

        SwitchScopeContainer switchScopeEntry = (SwitchScopeContainer) stackifierData.getScopeEntry(switchNode);
        Scope[] caseScopes = switchScopeEntry.getCaseScopes();
        assert caseScopes != null;

        for (int i = 0; i < switchNode.blockSuccessorCount(); i++) {
            AbstractBeginNode succ = switchNode.blockSuccessor(i);
            if (succ.equals(switchNode.defaultSuccessor())) {
                lowerSwitchDefaultCase(switchNode);
            } else {
                ArrayList<Integer> succKeys = new ArrayList<>();
                // query all keys that have the succ as block succ
                for (int keyIndex = 0; keyIndex < switchNode.keyCount(); keyIndex++) {
                    // the key
                    int key = switchNode.intKeyAt(keyIndex);
                    AbstractBeginNode keySucc = switchNode.keySuccessor(keyIndex);
                    if (succ.equals(keySucc)) {
                        succKeys.add(key);
                    }
                }
                assert succKeys.size() > 0 : "no keys of " + switchNode + " have " + succ + " as block successor";
                int[] succk = new int[succKeys.size()];
                for (int s = 0; s < succKeys.size(); s++) {
                    succk[s] = succKeys.get(s);
                }
                lowerSwitchCase(switchNode, succ, succk);
            }
            if (caseScopes[i] != null) {
                lowerBlocks(caseScopes[i].getSortedBlocks(stackifierData));
            } else {
                generateForwardJump(cfg.blockFor(switchNode), cfg.blockFor(succ));
            }
            codeGenTool.genScopeEnd();
        }
        codeGenTool.genScopeEnd();
    }

    /**
     * Lower one case of a type switch (that is, an if or else-if with a type check condition).
     *
     * @param switchNode the {@link TypeSwitchNode} that is being lowered
     * @param succ the block successor corresponding to the case in question.
     * @param succNum the index in generation order of the case being lowered. Can be used to
     *            distinguish between if or else-if.
     * @param succKeys the allowed types for the condition of this case.
     */
    protected void lowerTypeSwitchCase(TypeSwitchNode switchNode, AbstractBeginNode succ, int succNum, List<ResolvedJavaType> succKeys) {
        GraalError.unimplementedParent();
    }

    /**
     * Lower the default case of a type switch (which should be lowered as an else statement).
     *
     * @param switchNode the {@link TypeSwitchNode} that is being lowered.
     */
    protected void lowerTypeSwitchDefaultCase(TypeSwitchNode switchNode) {
        GraalError.unimplementedParent();
    }

    /**
     * Lowers the given {@link TypeSwitchNode}. Since a type switch performs exact type checks
     * instead of instanceof ones, a pattern-matching switch cannot be used. Therefore, the node is
     * lowered as a cascade of if-else blocks, where each if checks the type of the input against a
     * given class.
     *
     * Example: Suppose we have the following Java program where A, B, C and D roughly correspond to
     * basic blocks (for simplicity assume that all these basic blocks end with a
     * {@link ControlSinkNode}). Further, assume that the following type switch performs exact type
     * checks:
     *
     * <pre>
     *  Object a;
     *  ...
     *  switch(a){
     *      case Integer:
     *      case Long:
     *             A();
     *             break;
     *      case Float:
     *              B();
     *      case String:
     *              C();
     *              break;
     *      default:
     *              D();
     *  }
     * </pre>
     *
     * Witch the topological order A->D->B->C, this will generate the following pseudocode:
     *
     * <pre>
     * block3: {
     *     block2: {
     *         block1: {
     *             block0: {
     *                 if (Integer.class.equals(a.getClass()) || Long.class.equals(a.getClass())) {
     *                 } else if (Float.class.equals(a.getClass()) {
     *                         break block1;
     *                 } else if (String.class.equals(b.getClass()) {
     *                         break block2;
     *                 } else { // default case
     *                         break block0;
     *                 } // end type switch
     *                 A();
     *             } // block0
     *             D();
     *         } // block1
     *         B();
     *         break block3;
     *     } // block2
     * } // block3
     * C();
     * </pre>
     *
     * Note that the {@link LabeledBlock} {@code block3} is not necessary, but will be produced
     * because the Graal IR sometimes produces a {@link HIRBlock} that only contains a
     * {@link BeginNode} and a {@link EndNode}. Since no code is emitted for such a
     * {@link HIRBlock}, there is no generated code between the ends of {@code block2} and
     * {@code block3}.
     *
     * Similar to if-then-else, an optimization could pull the code of A, B, C and D inside the case
     * blocks.
     *
     * @param switchNode node to be lowered
     */
    protected void lowerTypeSwitch(TypeSwitchNode switchNode) {
        for (int i = 0; i < switchNode.blockSuccessorCount(); i++) {
            AbstractBeginNode succ = switchNode.blockSuccessor(i);
            if (succ.equals(switchNode.defaultSuccessor())) {
                lowerTypeSwitchDefaultCase(switchNode);
            } else {
                ArrayList<ResolvedJavaType> succKeys = new ArrayList<>();
                // query all keys that have the succ as block succ
                for (int keyIndex = 0; keyIndex < switchNode.keyCount(); keyIndex++) {
                    ResolvedJavaType key = switchNode.typeAt(keyIndex);
                    AbstractBeginNode keySucc = switchNode.keySuccessor(keyIndex);
                    if (succ.equals(keySucc)) {
                        succKeys.add(key);
                    }
                }
                assert succKeys.size() > 0 : "no keys of " + switchNode + " have " + succ + " as block successor";
                lowerTypeSwitchCase(switchNode, succ, i, succKeys);
            }
            generateForwardJump(cfg.blockFor(switchNode), cfg.blockFor(succ));
            codeGenTool.genBlockEndBreak();
            codeGenTool.genScopeEnd();
        }
    }

    protected void genLabeledBlockHeader(LabeledBlock labeledBlock) {
        blockNestingVerifier.pushLabel(labeledBlock);
        codeGenTool.genLabeledBlockHeader(labeledBlock.getLabel());
    }

    protected void genLabeledBlockEnd(LabeledBlock blockEnd) {
        blockNestingVerifier.popLabel(blockEnd);
        codeGenTool.genScopeEnd();
        codeGenTool.genComment("End of LabeledBlock " + blockEnd.getLabel());
    }

    private void genLoopHeader(HIRBlock block) {
        assert block.isLoopHeader();
        String label = getLabel(block);
        blockNestingVerifier.pushLabel(label);
        codeGenTool.genLabel(label);
        codeGenTool.genWhileTrueHeader();
    }

    private void genLoopEnd(HIRBlock header) {
        assert header.isLoopHeader();
        String label = getLabel(header);
        blockNestingVerifier.popLabel(label);
        /*
         * A break statement is always emitted before the loop end in order to get rid of the
         * implicit back edge. Example: If the Graal IR contains a loop with 2 back edges, it has 2
         * LoopEndNodes. The generated code will have 2 back-edges because of 2 continue statements
         * for the LoopEndNodes and 1 back-edge that comes from the loop end. This back-edge needs
         * to be suppressed (i.e. made unreachable) with the break statement to guarantee that the
         * generated loop has the same semantics as the Graal IR.
         */
        codeGenTool.genBlockEndBreak();
        codeGenTool.genScopeEnd();
        codeGenTool.genComment("End of loop " + label);
    }

    private String getLabel(HIRBlock block) {
        assert block.isLoopHeader();
        return LABEL_PREFIX + stackifierData.blockOrder(block);
    }
}
