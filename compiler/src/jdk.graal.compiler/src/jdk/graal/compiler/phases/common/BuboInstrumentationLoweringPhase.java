/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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


import jdk.graal.compiler.graph.Graph.Mark;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.extended.JavaWriteNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;

/**
 * Adds CustomInstrumentation to loops.
 */
public class BuboInstrumentationLoweringPhase extends LoweringPhase  {



    public BuboInstrumentationLoweringPhase(CanonicalizerPhase canonicalizer, boolean lowerOptimizableMacroNodes) {
        super(canonicalizer, LoweringTool.StandardLoweringStage.LOW_TIER, lowerOptimizableMacroNodes, StageFlag.CUSTOMINSTRUMENTATION_TIER);
    }

    public BuboInstrumentationLoweringPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer, LoweringTool.StandardLoweringStage.LOW_TIER, StageFlag.CUSTOMINSTRUMENTATION_TIER);
    }

    private enum LoweringMode {
        LOWERING,
        VERIFY_LOWERING
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        lower(graph, context, LoweringMode.LOWERING);
        assert checkPostLowering(graph, context);

    }

    @SuppressWarnings("try")
    private void lower(StructuredGraph graph, CoreProviders context, LoweringMode mode) {
        final LoweringToolImpl loweringTool = new LoweringToolImpl(context, null, null, null, null);

            for (Node node : graph.getNodes().filter(JavaReadNode.class)) {
                JavaReadNode logNode = (JavaReadNode) node;
                context.getLowerer().lower(logNode, loweringTool);
            }

            for (Node node : graph.getNodes().filter(JavaWriteNode.class)) {
                JavaWriteNode logNode = (JavaWriteNode) node;
                logNode.lower(loweringTool);
            }


    }




    /**
     * Checks that lowering of a given node did not introduce any new {@link Lowerable} nodes that
     * could be lowered in the current {@link LoweringPhase}. Such nodes must be recursively lowered
     * as part of lowering {@code node}.
     *
     * @param node a node that was just lowered
     * @param preLoweringMark the graph mark before {@code node} was lowered
     * @param unscheduledUsages set of {@code node}'s usages that were unscheduled before it was
     *            lowered
     * @throws AssertionError if the check fails
     */
    private boolean checkPostLowering(StructuredGraph graph, CoreProviders context) {
        Mark expectedMark = graph.getMark();
        lower(graph, context, LoweringMode.VERIFY_LOWERING);
        Mark mark = graph.getMark();
        assert mark.equals(expectedMark) || graph.getNewNodes(mark).count() == 0 : graph + ": a second round in the current lowering phase introduced these new nodes: " +
                        graph.getNewNodes(expectedMark).snapshot();
        return true;
    }
}