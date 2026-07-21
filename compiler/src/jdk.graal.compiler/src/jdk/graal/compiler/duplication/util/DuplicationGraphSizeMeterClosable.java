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
package jdk.graal.compiler.duplication.util;

import static jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions.DuplicationBudgetFactor;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.contract.NodeCostUtil;

import jdk.graal.compiler.duplication.phases.simulation.DuplicationCostFunction;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions;
import jdk.graal.compiler.duplication.phases.simulation.NodeCostModelBasedDuplicationCostFunction;

/**
 * Class to meter graph size, node cost and duplication cost model values during compilation.
 */
public final class DuplicationGraphSizeMeterClosable implements DebugCloseable {

    private final StructuredGraph graph;
    private final int identifier;
    private final DuplicationCostFunction dupFunction;
    private int codeSizeBefore;
    private int nodeCountBefore;

    private DuplicationGraphSizeMeterClosable(StructuredGraph graph, DuplicationCostFunction dupFunction) {
        this.graph = graph;
        this.identifier = System.identityHashCode(dupFunction);
        this.dupFunction = dupFunction;

        codeSizeBefore = NodeCostUtil.computeGraphSize(graph);
        double increaseFactor = DuplicationBudgetFactor.getValue(graph.getOptions());
        int budget = (int) (codeSizeBefore * increaseFactor);
        nodeCountBefore = 0;
        DebugContext debug = graph.getDebug();

        nodeCountBefore = graph.getNodeCount();

        DebugContext.counter("NodeCosts_GraphSizeBefore_%d", identifier).add(debug, codeSizeBefore);
        DebugContext.counter("NodeCosts_Budget_%d", identifier).add(debug, budget);
        DebugContext.counter("NodeCount_Before_%d", identifier).add(debug, nodeCountBefore);
        DebugContext.counter("PhiCount_Before_%d", identifier).add(debug, graph.getNodes().filter(PhiNode.class).count());
    }

    public static DebugCloseable create(StructuredGraph graph, DuplicationCostFunction dupFunction) {
        if (!DuplicationOptions.TrackGraphSizesInDuplication.getValue(graph.getOptions())) {
            return null;
        }
        GraalError.guarantee(graph.getDebug().areCountersEnabled(), "TrackGraphSizesInDuplication can only be enabled together with -Djdk.graal.Count in the enclosing DebugContext.currentScope.");
        return new DuplicationGraphSizeMeterClosable(graph, dupFunction);
    }

    @Override
    public void close() {
        DebugContext debug = graph.getDebug();
        int sizeafter = NodeCostUtil.computeGraphSize(graph);
        int nodeCountAfter = graph.getNodeCount();
        DebugContext.counter("NodeCount_After_%d", identifier).add(debug, nodeCountAfter);
        DebugContext.counter("NodeCosts_GraphSizeAfter_%d", identifier).add(debug, sizeafter);
        if (dupFunction instanceof NodeCostModelBasedDuplicationCostFunction) {
            NodeCostModelBasedDuplicationCostFunction f = (NodeCostModelBasedDuplicationCostFunction) dupFunction;
            DebugContext.counter("SimulationBasedDuplication_Benefit_iteration_%d", identifier).add(debug, (long) f.costModel().overallBenefit());
            DebugContext.counter("NodeCosts_ModelBudgetUsed_%d", identifier).add(debug, (long) f.costModel().usedBudget());
            double percentIncreased = (((double) sizeafter / (double) codeSizeBefore) - 1) * 100;
            double nodeCountIncreased = (((double) nodeCountAfter / (double) nodeCountBefore) - 1) * 100;
            DebugContext.counter("NodeCosts_PercentIncreased_%d", identifier).add(debug, (long) percentIncreased);
            DebugContext.counter("NodeCount_PercentIncreased_%d", identifier).add(debug, (long) nodeCountIncreased);
            DebugContext.counter("PhiCount_After_%d", identifier).add(debug, graph.getNodes().filter(PhiNode.class).count());
        }
    }
}
