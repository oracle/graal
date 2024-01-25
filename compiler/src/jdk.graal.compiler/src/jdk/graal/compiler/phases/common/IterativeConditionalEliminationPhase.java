/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.Optional;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;

public class IterativeConditionalEliminationPhase extends BasePhase<CoreProviders> {
    private final boolean fullSchedule;
    private final ConditionalEliminationPhase conditionalEliminationPhase;

    public IterativeConditionalEliminationPhase(CanonicalizerPhase canonicalizer, boolean fullSchedule) {
        this.fullSchedule = fullSchedule;
        this.conditionalEliminationPhase = new ConditionalEliminationPhase(canonicalizer, fullSchedule);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return conditionalEliminationPhase.notApplicableTo(graphState);
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        final int maxIterations = GraalOptions.ConditionalEliminationMaxIterations.getValue(graph.getOptions());
        EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener();
        for (int count = 0; count < maxIterations; ++count) {
            try (NodeEventScope nes = graph.trackNodeEvents(listener)) {
                conditionalEliminationPhase.apply(graph, context);
            }
            if (listener.getNodes().isEmpty()) {
                break;
            }
            listener.getNodes().clear();
        }
    }

    @Override
    public float codeSizeIncrease() {
        return 2.0f;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getClass().getName(), fullSchedule, conditionalEliminationPhase);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof IterativeConditionalEliminationPhase)) {
            return false;
        }

        IterativeConditionalEliminationPhase that = (IterativeConditionalEliminationPhase) obj;

        return this.getClass().equals(that.getClass()) &&
                        this.fullSchedule == that.fullSchedule &&
                        this.conditionalEliminationPhase.equals(that.conditionalEliminationPhase);
    }
}
