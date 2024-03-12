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

import java.util.ArrayList;

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

    private EconomicMap<Node, SafepointData> safepointVerifiacationData;

    static class SafepointData {
        /**
         * Determine if there can be a safepoint on any of the loop ends of this loop. If true, it
         * means that there was no explicit phase requesting a complete disabling of all safepoints
         * on this loop. Safepoints can only be removed, but not added afterwards. This means any
         * loop that is replaced by loop optimizations with other loops must still retain the same
         * safepoint rules.
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

        boolean assertNotWeaker(SafepointData other) {
            if (this.canHaveSafepoints) {
                // all good, other can do what it wants
            } else {
                // this cannot safepoint -> ensure other also cannot safepoint
                assert !other.canHaveSafepoints : Assertions.errorMessage("Safepoint verification cannot become weaker", lb,
                                "previously the loop had canHaveSafepoints=false but now it has canHaveSafepoints=true", other.lb);
            }
            return true;
        }

        public boolean sameStateOrNsp(SafepointData otherData) {
            if (otherData.fs == fs) {
                return true;
            }
            // not source position must match
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
            if (!sameLoopType(this.lb, otherData.lb)) {
                return false;
            }
            return true;
        }

        /**
         * This method is a heuristic approximation for loop safepoint verification. When we remove
         * a loop from a graph and replace it by something else we try to find the replacement
         * loop(s). Which is not always trivial, thus we need to have a "best" guess as in - the new
         * loop has the same node source position, framestate based on properties and loop general
         * properties.
         */
        private static boolean sameLoopType(LoopBeginNode lb1, LoopBeginNode lb2) {
            if (lb1.isCompilerInverted() != lb2.isCompilerInverted()) {
                return false;
            }
            // we skip loop type to verify partial unrolling

            /*
             * Only if this loop was touched by strip mining already verify the properties, else we
             * might be verifying during a replace. Note that strip mining is special in that in
             * creates a second loop with the same state and the same node source position with an
             * inner/outer mapping. Any other loop optimization is sequential in that it does only
             * copy a loop but never creates new loop nests.
             */
            boolean isTouchedByStripMining = lb1.isStripMinedInner() || lb1.isStripMinedOuter();
            if (isTouchedByStripMining) {
                if (lb1.isStripMinedInner() != lb2.isStripMinedInner()) {
                    return false;
                }
                if (lb2.isStripMinedOuter() != lb2.isStripMinedOuter()) {
                    return false;
                }
            }

            if (lb1.getClonedFrom() != -1) {
                if (lb2.getClonedFrom() != -1) {
                    if (lb1.getClonedFrom() != lb2.getClonedFrom()) {
                        // both cloned but from different loops
                        return false;
                    }
                } else {
                    // one ones cloned the other not, abort
                    return false;
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

        static void getLoopRelations(LoopBeginNode lb, StructuredGraph graph, EconomicMap<LoopBeginNode, LoopContext> contexts) {
            if (!contexts.containsKey(lb)) {
                contexts.put(lb, new LoopContext(lb));
            }

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

        if (safepointVerifiacationData == null) {
            safepointVerifiacationData = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        }

        ArrayList<LoopBeginNode> loopsToVisit = new ArrayList<>();
        ArrayList<LoopBeginNode> originalLoopsInTheGraph = new ArrayList<>();
        for (Node lb : safepointVerifiacationData.getKeys()) {
            loopsToVisit.add((LoopBeginNode) lb);
            originalLoopsInTheGraph.add((LoopBeginNode) lb);
        }
        for (LoopBeginNode lb : g.getNodes(LoopBeginNode.TYPE)) {
            final SafepointData newData = SafepointData.fromLoopBegin(lb, contexts.get(lb).innerLoopBegins.size(), contexts.get(lb).depth);
            if (safepointVerifiacationData.containsKey(lb)) {
                assert loopsToVisit.contains(lb);
                // all loops that are still in the graph just need verification, no replacement
                // verification
                loopsToVisit.remove(lb);
                assert safepointVerifiacationData.get(lb).assertNotWeaker(newData);
            }
            // now overwrite (or propagate new data) to the map, if it was a faulty loop it
            // would have hit the assertions above
            safepointVerifiacationData.put(lb, newData);
        }
        /*
         * Now we cleaned up all the loops that are in the graph. What remains is to cleanup the old
         * loops that are no longer part of the graph. They have been removed in between. This is
         * where it becomes fuzzy: if a loop optimization removed the original loop with (a) new
         * one(s) we must verify that/them. We use framestate and node source position to verify
         * them, which is a poor mans heuristic but we cannot do much better.
         */
        for (LoopBeginNode lb : loopsToVisit) {
            assert lb.isDeleted() : Assertions.errorMessage("Thus loop must be deleted since it was not found during iteration", lb);
            // lets remove it from the map, either we cant verify and fail or its good and we
            // verified correctly, both ways the loop should be removed from the map
            SafepointData sd = safepointVerifiacationData.removeKey(lb);
            if (sd.canHaveSafepoints) {
                // the loop was allowed to safepoint, if the new ones (if there are) are allowed
                // to safepoint or not are not of real interesting, thus we are good
            } else {
                // the loop was not allowed to safepoint, any replacement should also not
                // safepoint
                inner: for (Node n : safepointVerifiacationData.getKeys()) {
                    LoopBeginNode other = (LoopBeginNode) n;
                    if (originalLoopsInTheGraph.contains(other)) {
                        // only compare old deleted loops with newly added ones
                        continue inner;
                    }
                    SafepointData otherData = safepointVerifiacationData.get(other);
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
