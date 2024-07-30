/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import static jdk.graal.compiler.nodes.GraphState.StageFlag.LOOP_OVERFLOWS_CHECKED;

import java.util.Optional;

import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.Phase;

/*
 * Phase that disables counted loop detection for loops that have deopted in previous compiled versions
 * because they failed the deopt guard.
 */
public class DisableOverflownCountedLoopsPhase extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    public boolean shouldApply(StructuredGraph graph) {
        return graph.hasLoops();
    }

    @Override
    protected void run(StructuredGraph graph) {
        if (graph.getSpeculationLog() != null) {
            for (LoopBeginNode lb : graph.getNodes(LoopBeginNode.TYPE)) {
                if (lb.countedLoopDisabled()) {
                    continue;
                }
                lb.checkDisableCountedBySpeculation(lb.stateAfter().bci, graph);
            }
        }
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        /*
         * This phase can run multiple times which is fine as long as we run it before all loop
         * phases.
         */
        if (graphState.isBeforeStage(LOOP_OVERFLOWS_CHECKED)) {
            graphState.setAfterStage(LOOP_OVERFLOWS_CHECKED);
        }
    }

    @Override
    public boolean mustApply(GraphState graphState) {
        return graphState.requiresFutureStage(LOOP_OVERFLOWS_CHECKED) || super.mustApply(graphState);
    }
}
