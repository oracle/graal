/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.phases;

import java.util.Optional;

import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;

/** Common state tracking and optional cleanup for architecture-specific vector legalization. */
public abstract class TargetVectorLoweringPhase extends BasePhase<LowTierContext> {
    private final boolean forceDCE;

    protected TargetVectorLoweringPhase(boolean forceDCE) {
        this.forceDCE = forceDCE;
    }

    @Override
    public boolean mustApply(GraphState graphState) {
        return graphState.requiresFutureStage(StageFlag.TARGET_VECTOR_LOWERING) || super.mustApply(graphState);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifApplied(this, StageFlag.TARGET_VECTOR_LOWERING, graphState);
    }

    @Override
    @SuppressWarnings("try")
    public final void run(StructuredGraph graph, LowTierContext context) {
        if (!forceDCE) {
            lower(graph, context);
            return;
        }
        GraphChangeListener listener = new GraphChangeListener();
        try (Graph.NodeEventScope scope = graph.trackNodeEvents(listener)) {
            lower(graph, context);
        }
        if (listener.changed) {
            new DeadCodeEliminationPhase().apply(graph);
        }
    }

    protected abstract void lower(StructuredGraph graph, LowTierContext context);

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(StageFlag.TARGET_VECTOR_LOWERING);
        graphState.removeRequirementToStage(StageFlag.TARGET_VECTOR_LOWERING);
    }

    private static final class GraphChangeListener extends Graph.NodeEventListener {
        private boolean changed;

        @Override
        public void changed(Graph.NodeEvent event, Node node) {
            changed = true;
        }
    }
}
