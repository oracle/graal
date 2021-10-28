/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.util.EconomicSetNodeEventListener;

public class IterativeConditionalEliminationPhase extends BasePhase<CoreProviders> {

    private final CanonicalizerPhase canonicalizer;
    private final boolean fullSchedule;

    public IterativeConditionalEliminationPhase(CanonicalizerPhase canonicalizer, boolean fullSchedule) {
        this.canonicalizer = canonicalizer;
        this.fullSchedule = fullSchedule;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        final int maxIterations = GraalOptions.ConditionalEliminationMaxIterations.getValue(graph.getOptions());
        EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener();
        int count = 0;

        while (true) {
            count++;
            try (NodeEventScope nes = graph.trackNodeEvents(listener)) {
                new ConditionalEliminationPhase(fullSchedule).apply(graph, context);
            }
            if (listener.getNodes().isEmpty()) {
                break;
            }

            canonicalizer.applyIncremental(graph, context, listener.getNodes());
            listener.getNodes().clear();

            if (count >= maxIterations) {
                break;
            }
        }
    }

    @Override
    public float codeSizeIncrease() {
        return 2.0f;
    }
}
