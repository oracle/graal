/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractDeoptimizeNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

/**
 * This phase will make sure that the branch leading towards this deopt has 0.0 probability.
 *
 */
public class PropagateDeoptimizeProbabilityPhase extends BasePhase<PhaseContext> {

    @Override
    @SuppressWarnings("try")
    protected void run(final StructuredGraph graph, PhaseContext context) {
        assert !graph.hasValueProxies() : "ConvertDeoptimizeToGuardPhase always creates proxies";
        for (AbstractDeoptimizeNode d : graph.getNodes(AbstractDeoptimizeNode.TYPE)) {
            assert d.isAlive();
            try (DebugCloseable closable = d.withNodeSourcePosition()) {
                visitDeoptBegin(AbstractBeginNode.prevBegin(d), graph);
            }
        }
    }

    private void visitDeoptBegin(AbstractBeginNode deoptBeginInput, StructuredGraph graph) {
        AbstractBeginNode deoptBegin = deoptBeginInput;
        while (deoptBegin.predecessor() instanceof AbstractBeginNode) {
            deoptBegin = (AbstractBeginNode) deoptBegin.predecessor();
        }

        if (deoptBegin instanceof AbstractMergeNode) {
            AbstractMergeNode mergeNode = (AbstractMergeNode) deoptBegin;
            Debug.log("Visiting %s", mergeNode);
            for (AbstractEndNode end : mergeNode.forwardEnds()) {
                AbstractBeginNode newBeginNode = AbstractBeginNode.prevBegin(end);
                visitDeoptBegin(newBeginNode, graph);
            }
        } else if (deoptBegin.predecessor() instanceof ControlSplitNode) {
            ControlSplitNode controlSplitNode = (ControlSplitNode) deoptBegin.predecessor();
            double probability = controlSplitNode.probability(deoptBegin);
            if (probability == 1.0) {
                visitDeoptBegin(AbstractBeginNode.prevBegin((FixedNode) controlSplitNode.predecessor()), graph);
            } else if (probability != 0.0) {
                controlSplitNode.setProbability(deoptBegin, 0.0);
            }
        } else {
            assert deoptBegin instanceof StartNode;
        }
    }
}
