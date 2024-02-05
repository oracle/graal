/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.blocks;

import static jdk.graal.compiler.debug.Assertions.assertionsEnabled;

import java.util.SortedSet;
import java.util.TreeSet;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph;
import jdk.graal.compiler.core.common.cfg.Loop;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hightiercodegen.irwalk.StackifierIRWalker;
import jdk.graal.compiler.hightiercodegen.reconstruction.StackifierData;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.CatchScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.IfScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.LoopScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.Scope;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.ScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.SwitchScopeContainer;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;

/**
 * This class generates {@link LabeledBlock}s that enable us to do forward jumps and skip over basic
 * blocks.
 *
 * This class iterates over all basic blocks and checks for each forward edge if a
 * {@link LabeledBlock} is needed and generates one if that is the case. We store mappings from
 * {@link HIRBlock} to {@link LabeledBlock} in order to store where a {@link LabeledBlock} starts
 * and ends. {@link StackifierIRWalker} uses these maps to generate the corresponding code before
 * each basic block.
 *
 * See {@link StackifierIRWalker} for some examples.
 *
 */
public class LabeledBlockGeneration {

    public static final String LabeledBlockPrefix = "lb";

    /**
     * The beginnings of the LabeledBlocks need to be sorted such that the outermost block is
     * generated first and the innermost block last, if multiple labeled blocks start at the same
     * basic block.
     */
    protected final EconomicMap<HIRBlock, SortedSet<LabeledBlock>> labeledBlockStarts = EconomicMap.create();
    protected final EconomicMap<HIRBlock, LabeledBlock> labeledBlockEnds = EconomicMap.create();

    protected final LabeledBlockGenerator labeledBlockGenerator = new LabeledBlockGenerator();
    protected final ControlFlowGraph cfg;
    protected final StackifierData stackifierData;

    public LabeledBlockGeneration(StackifierData stackifierData, ControlFlowGraph cfg) {
        this.stackifierData = stackifierData;
        this.cfg = cfg;
    }

    /**
     * Checks whether a LabeledBlock needs to be generated for the edge from block to successor. No
     * LabeledBlock will be generated if the successor is reached via a back edge, i.e. the block is
     * a loop end.
     *
     * Example: Suppose we have the Java program where A, B and C roughly correspond to basic
     * blocks.
     *
     * <pre>
     * if (A()) {
     *     B();
     * }
     * C();
     * </pre>
     *
     * The only valid topological order is A->B->C. The basic blocks will be lowered in that order.
     * The basic block A has two successors, namely B and C. The code for B can be generated
     * directly after A, since B directly follows A. Therefore we do not need a {@link LabeledBlock}
     * in that case. But for the successor C we need a jump that skips B. Therefore for A and C a
     * {@link LabeledBlock} is needed. This will produce the following pseudocode.
     *
     * <pre>
     * block0: {
     *     if (A()) {
     *
     *     } else {
     *         break block0;
     *     }
     *     B();
     * } // block0
     * C();
     * </pre>
     *
     * @param block start of the edge
     * @param successor end of the edge
     * @return true iff a {@link LabeledBlock} is needed
     */
    public boolean isLabeledBlockNeeded(HIRBlock block, HIRBlock successor) {
        if (assertionsEnabled()) {
            boolean found = false;
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                HIRBlock s = block.getSuccessorAt(i);
                found = found || s == successor;
            }
            assert found;
        }
        ScopeContainer scopeContainer = stackifierData.getScopeEntry(block.getEndNode());
        if (block.isLoopEnd()) {
            assert block.getSuccessorCount() == 1 : Assertions.errorMessage(block);
            // loopEndNodes have back edges and therefore do not need a forward jump
            return false;
        }
        if (isNormalLoopExit(block, successor, stackifierData)) {
            return false;
        }

        if (block.getEndNode() instanceof IfNode) {
            if (((IfNode) block.getEndNode()).trueSuccessor() == successor.getBeginNode()) {
                // successor is the true successor
                if (((IfScopeContainer) scopeContainer).getThenScope() != null) {
                    return false;
                }
            } else {
                if (((IfScopeContainer) scopeContainer).getElseScope() != null) {
                    return false;
                }
            }
        } else if (block.getEndNode() instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeWithExc = (InvokeWithExceptionNode) block.getEndNode();
            if (invokeWithExc.getPrimarySuccessor() == successor.getBeginNode()) {
                return !isJumpingOverCatchBlock(block, successor, stackifierData);
            } else {
                assert invokeWithExc.exceptionEdge() == successor.getBeginNode() : Assertions.errorMessage(invokeWithExc, successor);
                if (((CatchScopeContainer) scopeContainer).getCatchScope() != null) {
                    return false;
                }
            }
        } else if (block.getEndNode() instanceof IntegerSwitchNode) {
            IntegerSwitchNode switchNode = (IntegerSwitchNode) block.getEndNode();
            Scope[] caseScopes = ((SwitchScopeContainer) stackifierData.getScopeEntry(switchNode)).getCaseScopes();
            for (int i = 0; i < switchNode.getSuccessorCount(); i++) {
                if (switchNode.blockSuccessor(i) == successor.getBeginNode()) {
                    return caseScopes[i] == null;
                }
            }
            GraalError.shouldNotReachHere("successor of switchnode not found in its successor list"); // ExcludeFromJacocoGeneratedReport
        }
        if (isLastBlockInThenBranch(block, stackifierData)) {
            return !isJumpingToAfterElseBranch(block, successor, stackifierData);
        }
        if (isLastBlockInSwitchArm(block, stackifierData)) {
            /*
             * Always generate a labeled block around switch statements as a target for switch arms
             * to jump out of. This can result in less than optimal code if all switch arms jump to
             * the merge block after the switch, but determining that is tricky to do correctly
             * without having knowing the order in which blocks will be lowered.
             */
            return true;
        }
        return successor.getId() != block.getId() + 1;
    }

    /**
     * Checks if the last block in a then branch jumps to after the else scope of an {@link IfNode}.
     *
     * Example:
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
     * In the example above, we have an edge from the basic block {@code A} to {@code C}. We do not
     * need a labeled break for this edge, since we jump to {@code C} automatically from {@code A}.
     *
     * @param block last block in a then-branch of an {@link IfNode}, see
     *            {@link #isLastBlockInThenBranch}
     * @param successor target of the jump
     * @return true iff the edge from {@code block} to {@code successor} jumps over the else scope
     *         of the {@link IfNode}
     */
    private static boolean isJumpingToAfterElseBranch(HIRBlock block, HIRBlock successor, StackifierData stackifierData) {
        Scope scope = stackifierData.getEnclosingScope().get(block);
        HIRBlock startBlock = scope.getStartBlock();
        IfScopeContainer ifScopeContainer = (IfScopeContainer) stackifierData.getScopeEntry(startBlock.getEndNode());
        Scope elseScope = ifScopeContainer.getElseScope();
        if (elseScope == null) {
            return false;
        }
        HIRBlock[] blocks = stackifierData.getBlocks();
        for (int id = block.getId() + 1; id < successor.getId(); id++) {
            if (!elseScope.getBlocks().contains(blocks[id])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the given block is the last block in the then-branch of an {@link IfNode}.
     *
     * Example:
     *
     * <pre>
     * if (condition) {
     *     A();
     *     B();
     * } else {
     *     C();
     * }
     * </pre>
     *
     * For the example above this function returns true for the basic block {@code B} and false
     * otherwise.
     */
    private static boolean isLastBlockInThenBranch(HIRBlock block, StackifierData stackifierData) {
        Scope scope = stackifierData.getEnclosingScope().get(block);
        if (scope != null) {
            HIRBlock startBlock = scope.getStartBlock();
            if (startBlock.getEndNode() instanceof IfNode) {
                IfScopeContainer ifScopeContainer = (IfScopeContainer) stackifierData.getScopeEntry(startBlock.getEndNode());
                return ifScopeContainer.getThenScope() == scope && scope.getLastBlock() == block;
            }
        }
        return false;
    }

    /**
     * Checks if the given block is the last block in one of the arms of an
     * {@link IntegerSwitchNode}.
     *
     * Example:
     *
     * <pre>
     * switch (x) {
     *     case 1:
     *         A();
     *         B();
     *         break;
     * }
     * C();
     * </pre>
     *
     * For the example above this function returns true for the basic block {@code B} and false
     * otherwise.
     */
    private static boolean isLastBlockInSwitchArm(HIRBlock block, StackifierData stackifierData) {
        Scope scope = stackifierData.getEnclosingScope().get(block);
        if (scope != null) {
            HIRBlock startBlock = scope.getStartBlock();
            if (startBlock.getEndNode() instanceof IntegerSwitchNode) {
                return scope.getLastBlock() == block;
            }
        }
        return false;
    }

    /**
     * Checks if the edge from {@code block} to {@code successor} is jumping over all
     * {@link HIRBlock}s in the catch scope.
     * <p>
     * Example:
     *
     * <pre>
     *     try {
     *         A()
     *     } catch (e) {
     *         B(e);
     *     }
     *     C();
     * </pre>
     * <p>
     * In the above example, basic block {@code C} is the primary successor of {@code A}. For this
     * edge we do not need to generate a labeled break because at the end of the {@code try} block,
     * we jump to after the catch block implicitly.
     *
     * @param block block ending with a {@link InvokeWithExceptionNode}
     * @param successor primary successor of the {@link InvokeWithExceptionNode}
     */
    private static boolean isJumpingOverCatchBlock(HIRBlock block, HIRBlock successor, StackifierData stackifierData) {
        assert block.getEndNode() instanceof InvokeWithExceptionNode : Assertions.errorMessage(block, block.getEndNode());
        assert block.getFirstSuccessor() == successor : Assertions.errorMessage(block, successor);
        CatchScopeContainer catchScopeContainer = (CatchScopeContainer) stackifierData.getScopeEntry(block.getEndNode());
        Scope catchScope = catchScopeContainer.getCatchScope();

        if (catchScope == null) {
            return false;
        }

        EconomicSet<HIRBlock> blocksInCatchScope = catchScope.getBlocks();
        HIRBlock[] allBlocks = stackifierData.getBlocks();
        for (int id = block.getId() + 1; id < successor.getId(); id++) {
            if (!blocksInCatchScope.contains(allBlocks[id])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the jump from {@code block} to {@code successor} is a simple break out of a loop. In
     * that case the jump can be simply done with a {@code break looplabel}, without having to
     * generate a {@link LabeledBlock}. Not "normal" loop exits can be caused by loops with multiple
     * exits that go to different basic blocks.
     *
     * Example:
     *
     * <pre>
     * block0: {
     *     while (condition) {
     *         A();
     *         if (condition1) {
     *             break;
     *         }
     *         B();
     *         if (condition2) {
     *             break block0;
     *         }
     *         C();
     *     } // end loop
     *     D();
     * } // block0
     * E();
     * </pre>
     *
     * In the above example, the break in the first if is a "normal" jump out of the loop and
     * therefore does not need a {@link LabeledBlock}. The second break jumps out of the loop and
     * skips some code after the loop. This jump needs a {@link LabeledBlock}.
     *
     * @param block start of the jump
     * @param successor end of the jump
     * @param stackifierData data from cf reconstruction
     * @return true iff the jump from block to successor can be done with a simple break statement,
     *         i.e. jump to the end of the loop.
     */
    public static boolean isNormalLoopExit(HIRBlock block, HIRBlock successor, StackifierData stackifierData) {
        Loop<HIRBlock> l1 = block.getLoop();
        if (l1 != null) {
            HIRBlock lastLoopBlock = ((LoopScopeContainer) stackifierData.getScopeEntry(l1.getHeader().getBeginNode())).getLoopScope().getLastBlock();
            return lastLoopBlock.getId() + 1 == successor.getId();
        }
        return false;
    }

    /**
     * Creates a new sortedSet for {@link LabeledBlock}. The {@link LabeledBlock} are sorted such
     * that the block that closes last comes first, i.e. they are sorted by descending block id of
     * their ending basic blocks. This is useful for sorting {@link LabeledBlock} that start at the
     * same basic block such that the outermost {@link LabeledBlock} comes first and the innermost
     * last.
     *
     * @return sorted Set according to the ends of the {@link LabeledBlock}s
     */
    public static SortedSet<LabeledBlock> getSortedSetByLabeledBlockEnd() {
        return new TreeSet<>((b1, b2) -> b2.getEnd().getId() - b1.getEnd().getId());
    }

    private static HIRBlock commonDominatorFor(HIRBlock b, boolean pred) {
        HIRBlock commonDom = null;
        for (int i = 0; i < (pred ? b.getPredecessorCount() : b.getSuccessorCount()); i++) {
            HIRBlock block = pred ? b.getPredecessorAt(i) : b.getSuccessorAt(i);
            commonDom = (HIRBlock) AbstractControlFlowGraph.commonDominator(commonDom, block);
        }
        return commonDom;
    }

    /**
     * Iterates over all basic block and checks for each forward edge if a {@link LabeledBlock} is
     * needed. If a {@link LabeledBlock} already ends at the jump target, no new
     * {@link LabeledBlock} is generated and instead the existing one will be used.
     */
    public void generateLabeledBlocks() {
        stackifierData.setLabeledBlockStarts(labeledBlockStarts);
        stackifierData.setLabeledBlockEnd(labeledBlockEnds);

        for (HIRBlock block : stackifierData.getBlocks()) {
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                HIRBlock successor = block.getSuccessorAt(i);
                if (isLabeledBlockNeeded(block, successor)) {
                    HIRBlock labeledBlockStart = getLabeledBlockStart(block, successor);

                    if (!labeledBlockEndsBeforeBasicBlock(successor)) {
                        LabeledBlock labeledBlock = labeledBlockGenerator.generateLabeledBlock(labeledBlockStart, successor);
                        addToMaps(labeledBlock);
                    }
                }
            }
        }
    }

    /**
     * Get the position where a LabeledBlock can start. Compute common dominator of all predecessors
     * of {@code successor} and then move the start of the LabeledBlock up until all
     * {@link LabeledBlock} that end between {@code predecessor} and {@code successor} are closed.
     *
     * The end of the {@link LabeledBlock} is determined by the jump target, e.g. it has to end
     * before C in the example below. But for the start of the {@link LabeledBlock} we have more
     * freedom. We could place the start at the very beginning of the function (or if we are inside
     * a loop, right after the loop header). This would result in very deeply nested blocks. The
     * later the block start is generated the lower the nesting will be. Therefore, we try to place
     * the start as late as possible.
     *
     * For this 2 things need to be considered. If multiple basic blocks jump to the target, all
     * these basic blocks must be contained within the {@link LabeledBlock}. Secondly, since the
     * {@link LabeledBlock} cannot overlap, we have to put the start before all
     * {@link LabeledBlock}s that end between {@code predecessor} and {@code successor}.
     *
     * Example: Suppose we have the Java program:
     *
     * <pre>
     * if (A()) {
     *     B();
     * } else {
     *     C();
     * }
     * D();
     * </pre>
     *
     * If we have the topological order A->B->C->D we need a {@link LabeledBlock} from B to D in
     * order to jump over C. D has two predecessors, namely B and C, which are dominated by A. Thus
     * the {@link LabeledBlock} has to start at least before A. If there were {@link LabeledBlock}s
     * ending between {@code predecessor} and {@code successor}, we would place the start of the new
     * {@link LabeledBlock} before all of them.
     *
     * @param predecessor basic block from where the jump is done
     * @param successor target of the jump
     * @return basic block where the "blockLabel:{ ..." should be placed
     */
    private HIRBlock getLabeledBlockStart(HIRBlock predecessor, HIRBlock successor) {
        HIRBlock earliestStart = predecessor;
        if (successor.getPredecessorCount() > 1) {
            earliestStart = commonDominatorFor(successor, true);
        }
        Scope startScope = stackifierData.getEnclosingScope().get(earliestStart);
        Scope endScope = stackifierData.getEnclosingScope().get(successor);
        if (successor.isLoopHeader()) {
            /**
             * Special case when successor is a loop header: A loop scope includes the loopHeader
             * (see {@link StackifierScopeComputation#computeLoopScopes()}), whereas other scopes do
             * not include the basic block that creates a scope. For example, a basic block
             * containing an {@link IfNode} does not belong to the respective then-scope a
             * else-scope. Consequently, if successor is a loop Header, {@code endScope} is the
             * loopScope created by {@code successor}. But we want to jump to just before
             * {@code successor}. Therefore, {@code endScope} should be the scope that contains the
             * loop created by {@code successor} which is the parent scope.
             */
            endScope = endScope.getParentScope();
        }
        if (startScope != endScope) {
            while (startScope != null && startScope.getParentScope() != endScope) {
                startScope = startScope.getParentScope();
            }
            if (startScope == null) {
                assert endScope == null : cfg.graph.method().getDeclaringClass().getUnqualifiedName() + "." + cfg.graph.method().getName() + " Cannot jump from start of a method into a scope " +
                                endScope;
                earliestStart = cfg.getStartBlock();
            } else {
                earliestStart = startScope.getStartBlock();
            }
        }
        for (HIRBlock b : labeledBlockEnds.getKeys()) {
            if (predecessor.getId() < b.getId() && b.getId() < successor.getId()) {
                // a LabeledBlock ends between break and ".. }"
                LabeledBlock forwardBlock = labeledBlockEnds.get(b);
                if (forwardBlock.getStart().getId() < earliestStart.getId()) {
                    earliestStart = forwardBlock.getStart();
                }
            }
        }
        return earliestStart;
    }

    /**
     * Checks if a {@link LabeledBlock} ends before the given basic block.
     *
     * @param labeledBlockEnd basic block for which a {@link LabeledBlock} is queried
     * @return true iff a {@link LabeledBlock} ends before {@code labeledBlockEnd}
     */
    private boolean labeledBlockEndsBeforeBasicBlock(HIRBlock labeledBlockEnd) {
        return labeledBlockEnds.get(labeledBlockEnd) != null;
    }

    /**
     * Stores the given {@link LabeledBlock} in maps that map from {@link HIRBlock} to the start and
     * end of the given {@link LabeledBlock}. These maps will be used in the
     * {@link StackifierIRWalker} for generating the corresponding code before or after a basic
     * block.
     *
     * @param labeledBLock block to be stored in the maps
     */
    private void addToMaps(LabeledBlock labeledBLock) {
        if (labeledBlockStarts.get(labeledBLock.getStart()) == null) {
            labeledBlockStarts.put(labeledBLock.getStart(), getSortedSetByLabeledBlockEnd());
        }
        labeledBlockStarts.get(labeledBLock.getStart()).add(labeledBLock);
        HIRBlock endBlock = labeledBLock.getEnd();
        assert !labeledBlockEnds.containsKey(endBlock);
        labeledBlockEnds.put(endBlock, labeledBLock);
    }

    /**
     * Generates {@link LabeledBlock}s with unique labels.
     */
    private static class LabeledBlockGenerator {

        private int currentId = 0;

        public LabeledBlock generateLabeledBlock(HIRBlock start, HIRBlock end) {
            return new LabeledBlock(start, end, currentId++);
        }
    }
}
