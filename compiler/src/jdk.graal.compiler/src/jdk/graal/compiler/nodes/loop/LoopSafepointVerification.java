/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.loop;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Verification utility to ensure that all optimizations during compilation respect safepoint
 * invariants in the compiler. That is: a loop with {@link LoopBeginNode#canEndsSafepoint()}
 * {@code ==false} must never be replaced by a loop with {@code canEndsSafepoint==true}.
 */
public class LoopSafepointVerification {

    public static final boolean PRINT_SAFEPOINT_NOT_FOUND = false;

    private EconomicMap<Node, SafepointData> safepointVerificationData;

    static class SafepointData {
        /**
         * Determine if there can be a safepoint on any of the loop ends of this loop. If true, it
         * means that there was no explicit phase requesting a complete disabling of all safepoints
         * on this loop. Once safepoints have been disabled for a loop, they must not be enabled
         * again. This means any loop that is replaced by loop optimizations with other loops must
         * still retain the same safepoint rules.
         */
        boolean canHaveSafepoints;
        LoopBeginNode lb;
        NodeSourcePosition nsp;
        FrameState fs;
        int outerLoops;
        int innerLoops;

        static SafepointData fromLoopBegin(LoopBeginNode lb, int innerLoops, int outerLoops) {
            SafepointData sd = new SafepointData();
            sd.lb = lb;
            sd.canHaveSafepoints = lb.canEndsSafepoint();
            sd.fs = lb.stateAfter();
            sd.nsp = lb.getNodeSourcePosition();
            sd.innerLoops = innerLoops;
            sd.outerLoops = outerLoops;
            return sd;
        }

        /**
         * Assert that the {@code newData} is not weaker with respect to safepoint invariants than
         * {@code this} loop. For this to make "sense" {@code newData} is supposed to be a
         * new/different/optimized version of {@code this} loop. The use case is that over the
         * course of compilation {@code this} loop was replaced by {@code newData} via a loop
         * optimization for example.
         */
        boolean assertNotWeaker(SafepointData newData) {
            if (this.canHaveSafepoints) {
                // all good, other can do what it wants
            } else {
                // this cannot safepoint -> ensure other also cannot safepoint
                assert !newData.canHaveSafepoints : Assertions.errorMessage("Safepoint verification cannot become weaker", lb,
                                "previously the loop had canHaveSafepoints=false but now it has canHaveSafepoints=true", newData.lb);
            }
            return true;
        }

        public boolean sameStateOrNsp(SafepointData otherData) {
            if (otherData.fs == fs) {
                return true;
            }
            // node source position must match
            if (this.nsp != null && otherData.nsp != null && !otherData.nsp.equals(nsp)) {
                return false;
            }
            // not the same state or also not the same NSP - check if framestates represent the same
            // position
            final FrameState thisState = fs;
            final FrameState otherState = otherData.fs;
            if (thisState != null && otherState != null && !thisState.valueEquals(otherState)) {
                return false;
            }
            if (this.innerLoops != otherData.innerLoops) {
                return false;
            }
            if (this.outerLoops != otherData.outerLoops) {
                return false;
            }
            if (!optimizerRelatedLoops(this.lb, otherData.lb)) {
                return false;
            }
            return true;
        }

        /**
         * This method is a heuristic approximation for loop safepoint verification. When we remove
         * a loop from a graph and replace it by something else we try to find the replacement
         * loop(s). Which is not always trivial, thus we need to have a "best" guess as in - the new
         * loop has the same node source position, framestate based on properties and loop general
         * properties. Therefore we consider {@code lb1} the "original" loop and try to determine if
         * {@code lb2} was derived from {@code lb1} via an optimization (unrolling, strip mining,
         * etc).
         */
        @SuppressWarnings("deprecation")
        private static boolean optimizerRelatedLoops(LoopBeginNode lb1, LoopBeginNode lb2) {
            if (lb1.isCompilerInverted() != lb2.isCompilerInverted()) {
                return false;
            }
            /*
             * Only if this loop was touched by strip mining already verify the properties, else we
             * might be verifying during a replace. Note that strip mining is special in that it
             * creates a second loop with the same state and the same node source position with an
             * inner/outer mapping. Any other loop optimization is sequential in that it does only
             * copy a loop but never creates new loop nests.
             */
            final boolean isTouchedByStripMining = lb1.isAnyStripMinedInner() || lb1.isAnyStripMinedOuter();
            if (isTouchedByStripMining) {
                if (lb1.isAnyStripMinedInner() != lb2.isAnyStripMinedInner()) {
                    return false;
                }
                if (lb1.isAnyStripMinedOuter() != lb2.isAnyStripMinedOuter()) {
                    return false;
                }
            }

            final long cloneFromIdLb1 = lb1.getClonedFromNodeId();
            final long cloneFromIdLb2 = lb2.getClonedFromNodeId();

            if ((cloneFromIdLb1 != -1 || cloneFromIdLb2 != -1)) {
                assert lb2.isAlive() : Assertions.errorMessage("When verifying loops the second one must be alive always", lb1, lb2);
                if (lb1.isDeleted() && cloneFromIdLb2 != -1) {
                    /*
                     * The original loop is deleted - the new one not: if the new one is cloned from
                     * another loop determine if it was cloned from lb1.
                     */
                    long lb1IdBeforeDeletion = lb1.getIdBeforeDeletion();
                    return lb1IdBeforeDeletion == cloneFromIdLb2;
                } else {
                    if (lb1.getId() == cloneFromIdLb2) {
                        /*
                         * lb2 was cloned from lb1 - ensure they match
                         */
                        return true;
                    }
                    /*
                     * Both loops are alive and either one of them (or both) have been cloned from
                     * another loop. Assure they are cloned from the same one.
                     */
                    return cloneFromIdLb1 == cloneFromIdLb2;
                }
            }
            return true;
        }
    }

    static class LoopContext {
        EconomicSet<LoopBeginNode> innerLoopBegins = EconomicSet.create();
        LoopBeginNode lb;
        int depth;

        LoopContext(LoopBeginNode lb) {
            this.lb = lb;
        }

        static void printContexts(EconomicMap<LoopBeginNode, LoopContext> contexts) {
            var cursor = contexts.getEntries();
            while (cursor.advance()) {
                var context = cursor.getValue();
                TTY.printf("Loop %s at depth %s has inner loops %s %n", context.lb, context.depth, context.innerLoopBegins);
            }
        }

        /**
         * Process the dominated parts of the {@code graph} reachable from the given loop begin node
         * {@code lb}. This method will be called for all loops in a graph. It will incrementally
         * build a data structure presenting all loops of a graph and their nesting.
         *
         * The algorithm behind this logic works the following way:
         *
         * <ul>
         * <li>Call {@link #getLoopRelations(LoopBeginNode, StructuredGraph, EconomicMap)} for all
         * {@link LoopBeginNode} nodes of a graph. While doing so build a side data structure
         * capturing all {@code LoopContext} for each loop in the graph. This data structure is
         * given as an in/out parameter to
         * {@link #getLoopRelations(LoopBeginNode, StructuredGraph, EconomicMap)}:
         * {@code contexts}.</li>
         * <li>For each loop begin start iterating the graph backwards until the
         * {@link StructuredGraph#start()} is found.</li>
         * <li>During iteration record all loops that are found: for loop exits skip the entire loop
         * of the exit and record it as an inner loop. For loop begins visited: attribute all
         * current inner loops to the loop begin's loop inner loops. This way
         * {@link #getLoopRelations(LoopBeginNode, StructuredGraph, EconomicMap)} incrementally
         * builds a full set of inner/outer loop relations in form of the loop context map.</li>
         * <li>When a regular control flow merge is visited then pick an arbitrary (we take the
         * first one for simplicity) predecessor and continue from there. If there are other inner
         * loops in other predecessors of a merge they will be visited by other calls to
         * {@link #getLoopRelations(LoopBeginNode, StructuredGraph, EconomicMap)}</li>
         * </ul>
         */
        static void getLoopRelations(LoopBeginNode lb, StructuredGraph graph, EconomicMap<LoopBeginNode, LoopContext> contexts) {
            if (!contexts.containsKey(lb)) {
                contexts.put(lb, new LoopContext(lb));
            }

            // the set of inner loops reachable from the starting point
            EconomicSet<LoopBeginNode> innerLoops = EconomicSet.create();
            innerLoops.add(lb);

            int enterSeen = 0;
            FixedNode cur = lb.forwardEnd();
            while (cur != null) {
                if (cur instanceof LoopExitNode lex) {
                    innerLoops.add(lex.loopBegin());
                    cur = lex.loopBegin().forwardEnd();
                    continue;
                }
                if (cur instanceof LoopBeginNode cl) {
                    /*
                     * We visit a loop begin, all inner loops found can be attributed to the outer
                     * loop
                     */
                    if (!contexts.containsKey(cl)) {
                        contexts.put(cl, new LoopContext(cl));
                    }
                    contexts.get(cl).innerLoopBegins.addAll(innerLoops);
                    enterSeen++;
                }

                if (cur.predecessor() != null) {
                    cur = (FixedNode) cur.predecessor();
                    continue;
                } else {
                    /*
                     * Pick an arbitrary branch. If another branch contains an inner loop, we will
                     * collect that loop when this method is called with on that loop's begin node.
                     */
                    if (cur instanceof AbstractMergeNode am) {
                        cur = am.forwardEndAt(0);
                        continue;
                    } else if (cur == graph.start()) {
                        break;
                    }
                }
            }
            contexts.get(lb).depth = enterSeen;
        }

    }

    public boolean verifyLoopSafepoints(StructuredGraph g) {
        if (!g.hasLoops()) {
            return true;
        }

        EconomicMap<LoopBeginNode, LoopContext> contexts = EconomicMap.create();

        for (LoopBeginNode lb : g.getNodes(LoopBeginNode.TYPE)) {
            if (lb.isAlive()) {
                LoopContext.getLoopRelations(lb, g, contexts);
            }
        }

        if (safepointVerificationData == null) {
            safepointVerificationData = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        }

        EconomicSet<LoopBeginNode> loopsToVisit = EconomicSet.create();
        EconomicSet<LoopBeginNode> originalLoopsInTheGraph = EconomicSet.create();
        for (Node lb : safepointVerificationData.getKeys()) {
            loopsToVisit.add((LoopBeginNode) lb);
            originalLoopsInTheGraph.add((LoopBeginNode) lb);
        }
        for (LoopBeginNode lb : g.getNodes(LoopBeginNode.TYPE)) {
            final SafepointData newData = SafepointData.fromLoopBegin(lb, contexts.get(lb).innerLoopBegins.size(), contexts.get(lb).depth);
            if (safepointVerificationData.containsKey(lb)) {
                assert loopsToVisit.contains(lb);
                // all loops that are still in the graph just need verification, no replacement
                // verification
                loopsToVisit.remove(lb);
                assert safepointVerificationData.get(lb).assertNotWeaker(newData);
            }
            // now overwrite (or propagate new data) to the map, if it was a faulty loop it
            // would have hit the assertions above
            safepointVerificationData.put(lb, newData);
        }
        /*
         * Now we cleaned up all the loops that are in the graph. What remains is to cleanup the old
         * loops that are no longer part of the graph. They have been removed in between. This is
         * where it becomes fuzzy: if a loop optimization removed the original loop with (a) new
         * one(s) we must verify that/them. We use framestate and node source position to verify
         * them, which is a poor mans heuristic but we cannot do much better.
         */
        for (LoopBeginNode lb : loopsToVisit) {
            assert lb.isDeleted() : Assertions.errorMessage("This loop must be deleted since it was not found during iteration", lb);
            // lets remove it from the map, either we cant verify and fail or its good and we
            // verified correctly, both ways the loop should be removed from the map
            SafepointData sd = safepointVerificationData.removeKey(lb);
            if (sd.canHaveSafepoints) {
                // the loop was allowed to safepoint, if the new ones (if there are any) are allowed
                // to safepoint or not is not of real interest, thus we are good
            } else {
                // the loop was not allowed to safepoint, any replacement should also not
                // safepoint
                inner: for (Node n : safepointVerificationData.getKeys()) {
                    LoopBeginNode other = (LoopBeginNode) n;
                    if (originalLoopsInTheGraph.contains(other)) {
                        // only compare old deleted loops with newly added ones
                        continue inner;
                    }
                    SafepointData otherData = safepointVerificationData.get(other);
                    assert otherData != null : Assertions.errorMessage("Must be in map as map was propagated previously", other);
                    assert other != lb : Assertions.errorMessage("Must be different nodes since one was deleted and the other is in the graph", lb, other);
                    if (sd.sameStateOrNsp(otherData)) {
                        assert sd.assertNotWeaker(otherData);
                    } else {
                        if (PRINT_SAFEPOINT_NOT_FOUND) {
                            TTY.printf("Could not find replacement loop for %s in %s%n", sd.lb, this);
                        }
                    }
                }
            }
        }

        return true;
    }
}
