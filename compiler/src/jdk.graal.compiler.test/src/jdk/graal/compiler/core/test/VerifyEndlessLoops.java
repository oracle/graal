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
package jdk.graal.compiler.core.test;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;

/**
 * Verify that no loop in Graal contains endless paths - endless paths are paths from a loop end
 * (backedge) to the loop header that makes zero progress. Progress in a loop is defined as any
 * side-effect or a backedge phi values changes. This ensures that at least statically every loop
 * should at least "advance any state" - that can be global state (side effect) or a phi itself.
 */
public class VerifyEndlessLoops extends VerifyPhase<CoreProviders> {

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        SchedulePhase.runWithoutContextOptimizations(graph);
        ScheduleResult sched = graph.getLastSchedule();
        LoopsData ld = context.getLoopsDataProvider().getLoopsData(sched.getCFG());
        for (Loop lex : ld.loops()) {
            for (LoopEndNode len : lex.loopBegin().loopEnds()) {
                boolean progress = false;
                for (PhiNode phi : lex.loopBegin().phis()) {
                    // a none self reference - some progress
                    progress = progress || phi.valueAt(len) != phi;
                }
                if (!progress) {
                    HIRBlock b = sched.getNodeToBlockMap().get(len);
                    walkBack(len, b, sched, lex.loopBegin(), new ArrayList<>());
                }
            }
        }

    }

    private static void walkBack(LoopEndNode len, HIRBlock start, ScheduleResult sched, LoopBeginNode lb, ArrayList<HIRBlock> pathSoFar) {
        HIRBlock cur = start;
        ArrayList<HIRBlock> path = new ArrayList<>(pathSoFar);
        while (cur != null) {
            assert path.indexOf(cur) == -1 : "Must not have block " + cur + " on path already";
            path.add(cur);
            List<Node> blockNodes = sched.getBlockToNodesMap().get(cur);
            for (int nodeIndex = blockNodes.size() - 1; nodeIndex >= 0; nodeIndex--) {
                Node n = blockNodes.get(nodeIndex);
                // found a side effect or an inner loop, we consider an inner loop a side effect, it
                // can propagate values outside via proxies
                if (MemoryKill.isMemoryKill(n) || (n instanceof LoopExitNode && ((LoopExitNode) n).loopBegin() != lb)) {
                    return;
                }
                if (n == lb) {
                    StringBuilder sb = new StringBuilder();
                    for (HIRBlock b : path) {
                        sb.append(b.getEndNode()).append("->");
                    }
                    sb.append(lb);
                    throw new VerificationError("Loop %s at bci %s in %s starting at loop end %s is an endless path, no side effect nor phi value update found. Path through the loop %s.", lb,
                                    lb.stateAfter().bci,
                                    lb.graph().method().format("%H.%n(%p)"), len, sb);
                }
            }
            if (cur.getPredecessorCount() == 0) {
                return;
            } else if (cur.getPredecessorCount() == 1) {
                cur = cur.getFirstPredecessor();
            } else {
                pred: for (int predIndex = 0; predIndex < cur.getPredecessorCount(); predIndex++) {
                    HIRBlock pred = cur.getPredecessorAt(predIndex);
                    if (cur.isLoopHeader() && pred.isLoopEnd() && pred.getLoop().getHeader() == cur) {
                        // break cycles on loop
                        continue pred;
                    }
                    walkBack(len, pred, sched, lb, path);
                }
                return;
            }
        }

    }

}
