/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.tiers.LowTierContext;

/**
 * A compiler phase that removes {@link PiNode} instances from the graph to optimize the low-tier
 * compilation stage. This phase assumes all floating reads have been lowered beforehand and is not
 * applied to substitution graphs, such as stubs.
 */
public class EconomyPiRemovalPhase extends PostRunCanonicalizationPhase<LowTierContext> {

    public EconomyPiRemovalPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    public boolean shouldApply(StructuredGraph graph) {
        /*
         * We do not want to run this phase for stubs and other subsitutions.
         */
        return !graph.isSubstitution();
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, LowTierContext context) {
        removePiNodes(graph);
    }

    private static void removePiNodes(StructuredGraph graph) {
        for (PiNode piNode : graph.getNodes(PiNode.TYPE)) {
            // incompatible stamps can result from unreachable code with empty stamps missed by
            // control flow optimizations
            if (piNode.stamp(NodeView.DEFAULT).isCompatible(piNode.getOriginalNode().stamp(NodeView.DEFAULT))) {
                piNode.replaceAndDelete(piNode.getOriginalNode());
            }
        }

        assert FixReadsPhase.verifyPiRemovalInvariants(graph);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.unlessRunAfter(this, GraphState.StageFlag.FIXED_READS, graphState);
    }
}
