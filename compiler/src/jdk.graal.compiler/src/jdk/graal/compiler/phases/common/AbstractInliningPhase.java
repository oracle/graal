/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;

import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;

/**
 * Common superclass for phases that perform inlining.
 */
public abstract class AbstractInliningPhase extends BasePhase<HighTierContext> {
    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.unlessRunBefore(this, StageFlag.HIGH_TIER_LOWERING, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.FINAL_CANONICALIZATION, graphState));
    }

    @Override
    protected final void run(StructuredGraph graph, HighTierContext context) {
        Graph.Mark mark = graph.getMark();
        runInlining(graph, context);
        if (!mark.isCurrent() && graph.getSpeculationLog() != null && graph.hasLoops()) {
            /*
             * We may have inlined new loops. We must make sure that counted loops are checked for
             * overflow before we apply the next loop optimization phase. Inlining may run multiple
             * times in different versions, possibly after some loop optimizations, and even on
             * demand (i.e., without explicitly appearing in the phase plan). For such phases the
             * stage flag mechanism isn't strong enough to express the constraint that we must run
             * DisableOverflownCountedLoops before the next loop phase. Therefore, run it
             * explicitly.
             */
            new DisableOverflownCountedLoopsPhase().run(graph);
        }
    }

    protected abstract void runInlining(StructuredGraph graph, HighTierContext context);
}
