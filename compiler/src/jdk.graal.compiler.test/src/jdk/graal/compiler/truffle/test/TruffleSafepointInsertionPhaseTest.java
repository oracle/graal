/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import java.util.List;

import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.truffle.nodes.TruffleSafepointNode;
import jdk.graal.compiler.truffle.phases.TruffleLoopSafepointEliminationPhase;
import jdk.graal.compiler.truffle.phases.TruffleSafepointInsertionPhase;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public class TruffleSafepointInsertionPhaseTest extends PartialEvaluationTest {

    @Test
    public void testMultiBackEdgeLoopHasOneTruffleSafepointInLoop() {
        RootNode rootNode = new MultiBackEdgeLoopRootNode(9);
        OptimizedCallTarget target = (OptimizedCallTarget) rootNode.getCallTarget();
        StructuredGraph graph = partialEval(target, new Object[0]);

        List<LoopBeginNode> loopBegins = graph.getNodes().filter(LoopBeginNode.class).snapshot();
        Assert.assertEquals(loopBegins.toString(), 1, loopBegins.size());
        LoopBeginNode loopBegin = loopBegins.getFirst();
        Assert.assertTrue("Expected multiple loop ends, got " + loopBegin.getLoopEndCount(), loopBegin.getLoopEndCount() > 1);

        graph.getGraphState().setAfterStage(GraphState.StageFlag.LOOP_OVERFLOWS_CHECKED);
        new TruffleLoopSafepointEliminationPhase(getTypes()).apply(graph, new MidTierContext(getProviders(), getTargetProvider(), OptimisticOptimizations.ALL, graph.getProfilingInfo()));
        Assert.assertTrue(loopBegin.getGuestLoopBeginSafepointState().canSafepoint());
        loopBegin.loopEnds().forEach(loopEnd -> Assert.assertFalse(loopEnd.getGuestSafepointState().canSafepoint()));

        new TruffleSafepointInsertionPhase(getTypes(), getProviders()).apply(graph);
        Assert.assertEquals(1, countSafepointsInLoop(graph));
    }

    private static long countSafepointsInLoop(StructuredGraph graph) {
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeFrequency(true).build();
        return graph.getNodes().filter(TruffleSafepointNode.class).stream().filter(safepoint -> {
            if (safepoint.predecessor() instanceof LoopBeginNode) {
                return true;
            }
            HIRBlock block = cfg.blockFor(safepoint);
            CFGLoop<HIRBlock> loop = block == null ? null : block.getLoop();
            return loop != null;
        }).count();
    }

    private static final class MultiBackEdgeLoopRootNode extends RootNode {
        private final int total;
        final int[] sideEffects = new int[2];
        final Object[] objects = new Object[1];

        MultiBackEdgeLoopRootNode(int total) {
            super(null);
            this.total = total;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int iteration = 0;
            while (iteration < total) {
                if ((iteration & 1) == 0) {
                    sideEffects[0]++;
                    objects[0] = new Object();
                    iteration++;
                    continue;
                }
                sideEffects[1]++;
                objects[0] = new Object();
                iteration++;
            }
            return sideEffects[0] + sideEffects[1];
        }
    }
}
