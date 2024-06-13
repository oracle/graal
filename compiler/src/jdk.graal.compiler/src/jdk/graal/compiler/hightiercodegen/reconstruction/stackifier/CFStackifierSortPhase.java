/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hightiercodegen.reconstruction.stackifier;

import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hightiercodegen.reconstruction.StackifierData;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.CatchScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.IfScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.LoopScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.Scope;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.ScopeContainer;
import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes.SwitchScopeContainer;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.phases.BasePhase;

/**
 * This phase sorts the basic blocks of a {@link ControlFlowGraph} such that the basic blocks from a
 * {@link Scope} are contiguous. Contiguous in this context means that all basic blocks in a
 * topological order that do not belong to a scope either appear before the first basic block of the
 * scope or after the last basic block that still belong to the scope. For example, in a CFG with
 * the basic blocks A, B, C and D where B is a loop header and C and D belong to the loop, the
 * following topological orders are not contiguous: B->A->C->D, B->C->A->D because A does not belong
 * to the loop but appears between basic blocks that belong to the loop.
 *
 * The following orders are contiguous: A->B->C->D, B->C->D->A
 *
 * Loops (and all other {@link Scope}s) have to stay contiguous for the following reason. We place
 * the loop begin before the first basic block that belongs to the loop and the loop end after the
 * last basic block that belongs to the loop. If a basic block is between blocks that belong to the
 * same loop but itself does not belong to the loop, we have to jump into the loop which is not
 * allowed.
 *
 * Suppose we have the following Java code where A, B, C, D roughly correspond to basic blocks:
 *
 * <pre>
 * if (A()) {
 *     while (1) {
 *         B();
 *         C();
 *     }
 * } else {
 *     D();
 * }
 * </pre>
 *
 * With the topological order A->B->C->D we get the following valid pseudocode program:
 *
 * <pre>
 * block0: {
 *     if (!A()) {
 *         break block0;
 *     }
 *     loop0: while (1) {
 *         B();
 *         C();
 *     }
 * } // block0
 * D();
 * </pre>
 *
 * The topological order A->B->D->C does not have the basic blocks B and C contiguous and the
 * CF-reconstruction algorithm that is given this order will produce invalid pseudocode, as one can
 * see below in an example:
 *
 * <pre>
 * block0: {
 *     if (!A()) {
 *         break block0;
 *     }
 *     loop0: while (1) {
 *         block1: {
 *             B();
 *             break block1;
 *         } // block0
 *         D();
 *     } // block1
 *     C();
 * } // loop0
 * </pre>
 *
 * As one can see, the block block0 overlaps with the loop loop0 and the block block1, which is not
 * allowed.
 *
 * The sort algorithm uses Kahn's algorithm.
 */
public class CFStackifierSortPhase extends BasePhase<StackifierData> {
    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, StackifierData stackifierData) {
        try (DebugContext.Scope scope1 = graph.getDebug().scope("Stackifier Sort Phase")) {
            Instance instance = new Instance(graph, stackifierData);
            instance.run();

        }
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return BasePhase.ALWAYS_APPLICABLE;
    }

    /**
     * Class to hold the state of the phase.
     */
    public static final class Instance {
        /**
         * Sort blocks by their id that they have from reverse post order such that original order
         * is kept as much as possible.
         */
        private final SortedSet<HIRBlock> freeBlocks = new TreeSet<>(Comparator.comparingInt(HIRBlock::getId));
        /**
         * Care needs to be taken with the {@link Equivalence} because some rely on
         * {@link HIRBlock#hashCode()}. The {@link HIRBlock} ids are updated in this phase. Since
         * {@link HIRBlock#hashCode()} relies on the id, we cannot use an {@link Equivalence} that
         * uses {@link HIRBlock#hashCode()}.
         */
        private final EconomicMap<HIRBlock, Scope> enclosingScopes;
        private final StructuredGraph.ScheduleResult scheduleResult;
        private final ControlFlowGraph cfg;
        private final HIRBlock[] sortedBlocks;
        /**
         * Stack for currently open scopes.
         */
        private final Deque<ScopeEntry> scopes = new LinkedList<>();
        private final StackifierData stackifierData;

        private Instance(StructuredGraph graph, StackifierData stackifierData) {
            this.scheduleResult = graph.getLastSchedule();
            this.cfg = scheduleResult.getCFG();
            this.stackifierData = stackifierData;
            this.sortedBlocks = new HIRBlock[cfg.getBlocks().length];
            this.enclosingScopes = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE, sortedBlocks.length);
        }

        /**
         * Get an array of successors of a block. Does not include successors that are before the
         * current block, i.e. successors that stem from back edges.
         *
         * @param current block of which we want the successors
         * @return array of successors
         */
        private static HIRBlock getSuccessorAt(HIRBlock current, int index) {
            if (current.isLoopEnd()) {
                throw GraalError.shouldNotReachHere("unexpected loop end"); // ExcludeFromJacocoGeneratedReport
            } else {
                return current.getSuccessorAt(index);
            }
        }

        private static int getSuccessorCount(HIRBlock current) {
            if (current.isLoopEnd()) {
                return 0;
            } else {
                return current.getSuccessorCount();
            }
        }

        /**
         * Sort the basic blocks using Kahn's algorithm. The algorithm is adapted such that loops
         * are contiguous.
         */
        private void sort() {
            int id = 0;
            EconomicMap<HIRBlock, Integer> remainingPredecessorsMap = EconomicMap.create();
            initializeRemainingPredecessors(remainingPredecessorsMap);

            freeBlocks.add(cfg.getStartBlock());
            while (!freeBlocks.isEmpty()) {
                assert id < sortedBlocks.length : Assertions.errorMessage(id, sortedBlocks);
                HIRBlock current = freeBlocks.first();
                freeBlocks.remove(current);
                cfg.graph.getDebug().log("Current free block %s", current);

                // Make scopes contiguous.
                if (!scopes.isEmpty() && !scopes.getFirst().scope.getBlocks().contains(current)) {
                    scopes.getFirst().addDeferredBlock(current);
                    cfg.graph.getDebug().log("Current block %s deferred by scope %s", current, scopes.getFirst().scope);
                    continue;
                }

                /*
                 * For all enclosing scopes the set with the yet unprocessed basic blocks needs to
                 * be updated too because the enclosing scopes contains the blocks of its nested
                 * scopes. (Only exception to this is if for a single ifNode both then and else
                 * scope were pushed. In that case "remove" will not change that scope)
                 */
                for (ScopeEntry scopeEntry : scopes) {
                    scopeEntry.openBlocks.remove(current);
                }

                checkForNewScopes(current);
                if (!scopes.isEmpty() && scopes.getFirst().openBlocks.isEmpty()) {
                    // All basic blocks of currentScope are processed.
                    closeScope();
                }

                cfg.graph.getDebug().log("Placing %s at %d", current, id);
                sortedBlocks[id] = current;
                id++;

                for (int i = 0; i < getSuccessorCount(current); i++) {
                    HIRBlock successor = getSuccessorAt(current, i);
                    int remainingPredecessors = remainingPredecessorsMap.get(successor) - 1;
                    remainingPredecessorsMap.put(successor, remainingPredecessors);
                    if (remainingPredecessors == 0) {
                        freeBlocks.add(successor);
                    }
                }
            }
            cfg.graph.getDebug().log("Processed [%d/%d] basic blocks", id, cfg.getBlocks().length);
            assert id == cfg.getBlocks().length : "Not all basic blocks have been reached during Stackifier sort";
        }

        /**
         * All basic block that belong to the current scope have been visited. Now the scope can be
         * closed.
         */
        private void closeScope() {
            ScopeEntry closedScopeEntry = scopes.getFirst();
            assert closedScopeEntry.openBlocks.isEmpty();

            cfg.graph.getDebug().log("Closing scope %s", closedScopeEntry.scope);
            scopes.removeFirst();

            // all basic block that were deferred by the scope can now be placed in the new sorting
            // order
            for (HIRBlock b : closedScopeEntry.deferredBlocks) {
                freeBlocks.add(b);
            }
            if (scopes.size() > 0) {
                /*
                 * Multiple scopes can end at the same basic block. Thus, if the outer scope ends
                 * too, also close it.
                 */
                if (scopes.getFirst().openBlocks.isEmpty()) {
                    closeScope();
                }
            }
        }

        /**
         * Try to push a new scope if one starts at the {@code current} block. Loop scopes have to
         * be pushed first because {@link LoopBeginNode} are the first node in a basic block whereas
         * {@link IfNode}s, etc. are the last node of a basic block.
         *
         * @param current potential start of a new scope
         */
        private void checkForNewScopes(HIRBlock current) {
            Scope parent;
            if (scopes.isEmpty()) {
                parent = null;
            } else {
                parent = scopes.getFirst().scope;
            }
            if (current.isLoopHeader()) {
                Scope loopScope = ((LoopScopeContainer) stackifierData.getScopeEntry(current.getBeginNode())).getLoopScope();
                pushNewScope(current, loopScope);
                scopes.getFirst().scope.setParentScope(parent);
                // loop can be the parent scope of the then/else scopes of the ifNode that is in the
                // same basic block
                parent = scopes.getFirst().scope;
                cfg.graph.getDebug().log("New scope %s", loopScope);
            }
            ScopeContainer scopeContainer = stackifierData.getScopeEntry(current.getEndNode());
            if (scopeContainer != null) {
                if (scopeContainer instanceof IfScopeContainer) {
                    IfScopeContainer ifScopeContainer = (IfScopeContainer) scopeContainer;
                    Scope thenScope = ifScopeContainer.getThenScope();
                    Scope elseScope = ifScopeContainer.getElseScope();
                    if (elseScope != null) {
                        pushNewScope(current, elseScope);
                        scopes.getFirst().scope.setParentScope(parent);
                        cfg.graph.getDebug().log("New scope %s", elseScope);
                    }
                    if (thenScope != null) {
                        pushNewScope(current, thenScope);
                        scopes.getFirst().scope.setParentScope(parent);
                        cfg.graph.getDebug().log("New scope %s", thenScope);
                    }
                } else if (scopeContainer instanceof CatchScopeContainer) {
                    CatchScopeContainer catchScopeContainer = (CatchScopeContainer) scopeContainer;
                    Scope catchScope = catchScopeContainer.getCatchScope();
                    if (catchScope != null) {
                        pushNewScope(current, catchScope);
                        scopes.getFirst().scope.setParentScope(parent);
                        cfg.graph.getDebug().log("New scope %s", catchScope);
                    }
                } else if (scopeContainer instanceof SwitchScopeContainer) {
                    SwitchScopeContainer switchScopeContainer = (SwitchScopeContainer) scopeContainer;
                    Scope[] caseScopes = switchScopeContainer.getCaseScopes();
                    for (int i = caseScopes.length - 1; i >= 0; i--) {
                        if (caseScopes[i] != null) {
                            pushNewScope(current, caseScopes[i]);
                            scopes.getFirst().scope.setParentScope(parent);
                            cfg.graph.getDebug().log("New scope %s", caseScopes[i]);
                        }
                    }
                }
            }
        }

        /**
         * Pushes a new scope on the stack of open scopes and updates all data structures for the
         * current stack.
         *
         * @param current basic block that opened the new scope
         * @param scope scope that gets opened
         */
        private void pushNewScope(HIRBlock current, Scope scope) {
            ScopeEntry newScopeEntry = new ScopeEntry(scope);
            // for loops the current basic block is in the scope and needs to be removed
            newScopeEntry.openBlocks.remove(current);
            scopes.addFirst(newScopeEntry);
            // set mapping basic block -> scope
            // since the innermost scope gets pushed last, the value of the outer enclosing scopes
            // gets overwritten
            for (HIRBlock b : scopes.getFirst().scope.getBlocks()) {
                enclosingScopes.put(b, scopes.getFirst().scope);
            }
        }

        /**
         * Count the number of predecessors of a block without counting back edges.
         *
         * @param remainingPredecessors map that stores for each block the number of remaining
         *            predecessors
         */
        private void initializeRemainingPredecessors(EconomicMap<HIRBlock, Integer> remainingPredecessors) {
            for (HIRBlock b : cfg.getBlocks()) {
                int predecessors;
                if (b.isLoopHeader()) {
                    predecessors = ((LoopBeginNode) b.getBeginNode()).forwardEndCount();
                } else {
                    predecessors = b.getPredecessorCount();
                }
                remainingPredecessors.put(b, predecessors);
            }
        }

        public void run() {
            sort();
            stackifierData.setSortedBlocks(sortedBlocks, cfg);
            stackifierData.setEnclosingScope(enclosingScopes);
        }
    }

    /**
     * Entry for a scope in the stack of currently open scopes.
     */
    private static class ScopeEntry {
        /**
         * Set of {@link HIRBlock}s which are deferred by the scope entry. A block that does not
         * belong to a scope needs to be deferred to keep a scope contiguous.
         */
        final EconomicSet<HIRBlock> deferredBlocks = EconomicSet.create();
        /**
         * Blocks that belong to the scope but have not been visited yet.
         */
        final EconomicSet<HIRBlock> openBlocks;
        final Scope scope;

        ScopeEntry(Scope currentScope) {
            this.scope = currentScope;
            openBlocks = EconomicSet.create(scope.getBlocks());
        }

        public void addDeferredBlock(HIRBlock current) {
            deferredBlocks.add(current);
        }
    }
}
