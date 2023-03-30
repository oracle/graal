/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hightiercodegen.irwalk;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.hightiercodegen.CodeGenTool;
import org.graalvm.compiler.hightiercodegen.reconstruction.ReconstructionData;
import org.graalvm.compiler.hightiercodegen.reconstruction.StackifierData;
import org.graalvm.compiler.hightiercodegen.reconstruction.stackifier.scopes.CatchScopeContainer;
import org.graalvm.compiler.hightiercodegen.reconstruction.stackifier.scopes.IfScopeContainer;
import org.graalvm.compiler.hightiercodegen.reconstruction.stackifier.scopes.LoopScopeContainer;
import org.graalvm.compiler.hightiercodegen.reconstruction.stackifier.scopes.Scope;
import org.graalvm.compiler.hightiercodegen.reconstruction.stackifier.scopes.SwitchScopeContainer;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.cfg.HIRBlock;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.replacements.nodes.BasicArrayCopyNode;

import org.graalvm.compiler.hightiercodegen.reconstruction.stackifier.StackifierScopeComputation;
import org.graalvm.compiler.hightiercodegen.reconstruction.stackifier.blocks.LabeledBlock;
import org.graalvm.compiler.hightiercodegen.reconstruction.stackifier.blocks.LabeledBlockGeneration;

public class StackifierIRWalker extends IRWalker {
    public static final String LABEL_PREFIX = "looplabel_";
    protected final BlockNestingVerifier blockNestingVerifier;

    public StackifierIRWalker(CodeGenTool codeGenTool, ControlFlowGraph cfg, BlockMap<List<Node>> blockToNodeMap, NodeMap<HIRBlock> nodeToBlockMap, ReconstructionData reconstructionData) {
        super(codeGenTool, cfg, blockToNodeMap, nodeToBlockMap, reconstructionData);
        this.blockNestingVerifier = new BlockNestingVerifier();
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
     * backwards jump, see {@link #lowerLoopStackifier(StackifierData, HIRBlock)}.
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
        StackifierData stackifierData = (StackifierData) reconstructionData;
        stackifierData.debugDump(debugContext);

        // * scan for all phi values needed in stacked scopes
        for (AbstractMergeNode n : cfg.graph.getNodes(AbstractMergeNode.TYPE)) {
            for (PhiNode phi : n.phis()) {
                if (codeGenTool.nodeLowerer().actualUsageCount(phi) > 0) {
                    codeGenTool.nodeLowerer().lower(phi);
                }
            }
        }
        lowerBlocks(stackifierData.getBlocks());
    }

    /**
     * Lower the given blocks. Recursively lower blocks that are inside a {@link Scope}.
     *
     * @param blocks blocks to be lowered
     */
    @SuppressWarnings("deprecated")
    private void lowerBlocks(HIRBlock[] blocks) {
        StackifierData stackifierData = (StackifierData) reconstructionData;
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
            SortedSet<LabeledBlock> labeledBlockStartsBeforeLoop = LabeledBlockGeneration.getSortedSetByLabeledBlockEnd();
            SortedSet<LabeledBlock> labeledBlockStartsAfterLoop = LabeledBlockGeneration.getSortedSetByLabeledBlockEnd();

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

            LabeledBlock blockEnd = stackifierData.labeledBlockEnd(currentBlock);

            /*
             * Generate the end of the labeled block before a possible loop header as well as all
             * labeled blocks that need to start before the loop header.
             */
            if (!isInRecursiveLoopCall(isLoopHeader, currentBlock, blocks)) {
                // generate end of forward blocks
                genLabeledBlockEnd(blockEnd);
                labeledBlockStartsBeforeLoop.forEach(this::genLabeledBlockHeader);
            }

            /*
             * Lower a loop in its own recursive call. This allows us to exactly place the end of
             * the loop. The recursive call is only executed if the loop does not start with the
             * first basic block. If it does, we are already in that recursive call.
             */
            if (newLoopStartsHere(isLoopHeader, currentBlock, blocks)) {
                lowerLoopStackifier(stackifierData, currentBlock);
                continue;
            }

            codeGenTool.genComment("Start of block " + currentBlock.getId());

            labeledBlockStartsAfterLoop.forEach(this::genLabeledBlockHeader);

            for (Node node : blockToNodeMap.get(currentBlock)) {
                if (node == currentBlock.getEndNode()) {
                    break;
                } else if (node instanceof LoopBeginNode) {
                    /*
                     * We should only be able to visit a LoopBeginNode if we are recursively called
                     * from lowerLoopStackifier and as such the node can be ignored since
                     * lowerLoopStackifier already emitted the loop header.
                     */
                    assert currentBlock == blocks[0];
                } else if (!(node instanceof LoopExitNode)) {
                    lowerNode(node);
                }
                // for verification purposes
                verifier.visitNode(node, codeGenTool);
            }

            Node lastNode = currentBlock.getEndNode();
            if (lastNode instanceof ControlSinkNode) {
                lowerNode(lastNode);
            } else if (lastNode instanceof IfNode) {
                lowerIfStackifier(currentBlock, (IfNode) lastNode);
            } else if (lastNode instanceof IntegerSwitchNode) {
                lowerSwitch((IntegerSwitchNode) lastNode, stackifierData);
            } else if (isWithExceptionNode(lastNode)) {
                lowerWithExceptionStackifier(currentBlock, (WithExceptionNode) lastNode);
            } else if ((lastNode instanceof ControlSplitNode) && !(lastNode instanceof BasicArrayCopyNode)) {
                // BasicArrayCopyNode is also a ControlSplitNode
                assert false : "Unsupported control split node " + lastNode + " is not implemented yet";
            } else if (lastNode instanceof LoopEndNode) {
                lowerLoopEndResolver((LoopEndNode) lastNode);
                codeGenTool.genLoopContinue();
            } else {
                if (!(lastNode instanceof LoopExitNode) && !(lastNode instanceof LoopBeginNode)) {
                    /*
                     * Special case for basic blocks with only one node. LoopExitNode and
                     * LoopBeginNode need to be handled separately again, as they must not be
                     * lowered by the node lowerer.
                     */
                    lowerNode(lastNode);
                }
                HIRBlock successor = nodeToBlockMap.get(lastNode.cfgSuccessors().iterator().next());
                generateForwardJump(currentBlock, successor, stackifierData);

            }

            verifier.visitNode(lastNode, codeGenTool);
            blockHistory.visitBlock(currentBlock);
            codeGenTool.genComment("End of block " + currentBlock.getId());
        }
    }

    /**
     * Whether the lowering of the given currentBlock happens inside a recursive call from
     * {@link #lowerLoopStackifier(StackifierData, HIRBlock)} and the current block is the start of
     * the loop.
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
        return lastNode instanceof InvokeWithExceptionNode;
    }

    /**
     * Generates a forward jump, i.e. a labeled {@code break}, if necessary.
     *
     * @param currentBlock block from which to jump from
     * @param successor target of the jump
     * @param stackifierData stackifier data
     */
    private void generateForwardJump(HIRBlock currentBlock, HIRBlock successor, StackifierData stackifierData) {
        if (LabeledBlockGeneration.isNormalLoopExit(currentBlock, successor, stackifierData)) {
            Loop<HIRBlock> loop = currentBlock.getLoop();
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
     * @param stackifierData data generated by the stackifier reconstruction algorithm
     * @param currentBlock current block
     */
    private void lowerLoopStackifier(StackifierData stackifierData, HIRBlock currentBlock) {
        assert currentBlock.getBeginNode() instanceof LoopBeginNode;
        LoopScopeContainer loopScopeEntry = (LoopScopeContainer) stackifierData.getScopeEntry(currentBlock.getBeginNode());
        Scope loopScope = loopScopeEntry.getLoopScope();

        genLoopHeader(currentBlock);

        lowerBlocks(loopScope.getSortedBlocks());
        genLoopEnd(currentBlock);
    }

    /**
     * Lower a WithExceptionNode. For an example how the generated code looks like, see
     * {@link #lower(DebugContext)}.
     *
     * @param currentBlock basic block that contains {@link WithExceptionNode}
     * @param lastNode the {@link WithExceptionNode}
     */
    private void lowerWithExceptionStackifier(HIRBlock currentBlock, WithExceptionNode lastNode) {
        StackifierData stackifierData = (StackifierData) reconstructionData;
        HIRBlock normSucc = cfg.blockFor(lastNode.next());
        HIRBlock excpSucc = cfg.blockFor(lastNode.exceptionEdge());
        CatchScopeContainer scopeEntry = (CatchScopeContainer) stackifierData.getScopeEntry(lastNode);
        Scope catchScope = scopeEntry.getCatchScope();
        /*
         * Since the ExceptionObjectNode is needed inside the catch block already, the
         * ExceptionObjectNode is lowered here together with the InvokeWithExceptionNode and skipped
         * once it is encountered in its own basic block.
         */
        ExceptionObjectNode excpObj = (ExceptionObjectNode) excpSucc.getBeginNode();
        codeGenTool.genTryBlock();
        lowerNode(lastNode);
        generateForwardJump(currentBlock, normSucc, stackifierData);
        codeGenTool.genCatchBlockPrefix(codeGenTool.getExceptionObjectId(excpObj));
        if (catchScope != null) {
            lowerBlocks(catchScope.getSortedBlocks());
        } else {
            generateForwardJump(currentBlock, excpSucc, stackifierData);
        }
        codeGenTool.genScopeEnd();
    }

    /**
     * Lower an IfNode. If there are {@link Scope} for the then/else branch, the scopes are lowered
     * in their own recursive call.
     *
     * @param currentBlock basic block containing the {@link IfNode}
     * @param lastNode the {@link IfNode}
     */
    private void lowerIfStackifier(HIRBlock currentBlock, IfNode lastNode) {
        StackifierData stackifierData = (StackifierData) reconstructionData;
        HIRBlock trueBlock = nodeToBlockMap.get(lastNode.trueSuccessor());
        HIRBlock falseBlock = nodeToBlockMap.get(lastNode.falseSuccessor());
        IfScopeContainer ifScopeContainer = (IfScopeContainer) stackifierData.getScopeEntry(lastNode);
        Scope thenScope = ifScopeContainer.getThenScope();
        Scope elseScope = ifScopeContainer.getElseScope();
        codeGenTool.genIfHeader(lastNode.condition());
        if (thenScope != null) {
            lowerBlocks(thenScope.getSortedBlocks());
        } else {
            generateForwardJump(currentBlock, trueBlock, stackifierData);
        }
        codeGenTool.genElseHeader();
        if (elseScope != null) {
            lowerBlocks(elseScope.getSortedBlocks());
        } else {
            generateForwardJump(currentBlock, falseBlock, stackifierData);
        }
        codeGenTool.genScopeEnd();
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
     * @param stackifierData data for forward jumps
     */
    public void lowerSwitch(IntegerSwitchNode switchNode, StackifierData stackifierData) {

        codeGenTool.genSwitchHeader(switchNode.value());

        boolean hasdefault = switchNode.defaultSuccessor() != null;

        SwitchScopeContainer switchScopeEntry = (SwitchScopeContainer) stackifierData.getScopeEntry(switchNode);
        Scope[] caseScopes = switchScopeEntry.getCaseScopes();
        assert caseScopes != null;

        for (int i = 0; i < switchNode.blockSuccessorCount(); i++) {
            // one successor
            AbstractBeginNode succ = switchNode.blockSuccessor(i);
            // the default case must be lowered at the end
            if (hasdefault) {
                if (succ.equals(switchNode.defaultSuccessor())) {
                    continue;
                }
            }

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

            assert succKeys.size() > 0;

            int[] succk = new int[succKeys.size()];
            for (int s = 0; s < succKeys.size(); s++) {
                succk[s] = succKeys.get(s);
            }

            codeGenTool.genSwitchCase(succk);

            if (caseScopes[i] != null) {
                lowerBlocks(caseScopes[i].getSortedBlocks());
            } else {
                generateForwardJump(cfg.blockFor(switchNode), cfg.blockFor(succ), stackifierData);
            }
            codeGenTool.genBreak();

            codeGenTool.genScopeEnd();
        }

        if (hasdefault) {
            codeGenTool.genSwitchDefaultCase();
            int defaultIndex = switchNode.defaultSuccessorIndex();
            if (caseScopes[defaultIndex] != null) {
                lowerBlocks(caseScopes[defaultIndex].getSortedBlocks());
            } else {
                generateForwardJump(cfg.blockFor(switchNode), cfg.blockFor(switchNode.defaultSuccessor()), stackifierData);
            }
            codeGenTool.genBreak();
            codeGenTool.genScopeEnd();
        }
        codeGenTool.genScopeEnd();
    }

    private void genLabeledBlockHeader(LabeledBlock labeledBlock) {
        blockNestingVerifier.pushLabel(labeledBlock);
        codeGenTool.genLabeledBlockHeader(labeledBlock.getLabel());
    }

    private void genLabeledBlockEnd(LabeledBlock blockEnd) {
        if (blockEnd != null) {
            blockNestingVerifier.popLabel(blockEnd);
            codeGenTool.genScopeEnd();
            codeGenTool.genComment("End of LabeledBlock " + blockEnd.getLabel());
        }
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
        codeGenTool.genBreak();
        codeGenTool.genScopeEnd();
        codeGenTool.genComment("End of loop " + label);
    }

    private static String getLabel(HIRBlock block) {
        assert block.isLoopHeader();
        return LABEL_PREFIX + (int) block.getId();
    }
}
