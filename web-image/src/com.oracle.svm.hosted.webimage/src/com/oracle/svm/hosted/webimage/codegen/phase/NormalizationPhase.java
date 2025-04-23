/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.codegen.phase;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.AbstractNormalizeCompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;

/**
 * Performs the initial replacement of nodes that are not available in Web Image, cleans up
 * post-analysis graphs.
 */
public class NormalizationPhase extends BasePhase<CoreProviders> {
    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        for (AbstractNormalizeCompareNode normalize : graph.getNodes(AbstractNormalizeCompareNode.TYPE)) {
            LogicNode equalComp = graph.addOrUniqueWithInputs(normalize.createEqualComparison());
            LogicNode lessComp = graph.addOrUniqueWithInputs(normalize.createLowerComparison());
            Stamp stamp = normalize.stamp(NodeView.DEFAULT);
            ConditionalNode equalValue = graph.unique(new ConditionalNode(equalComp, ConstantNode.forIntegerStamp(stamp, 0, graph), ConstantNode.forIntegerStamp(stamp, 1, graph)));
            ConditionalNode value = graph.unique(new ConditionalNode(lessComp, ConstantNode.forIntegerStamp(stamp, -1, graph), equalValue));
            normalize.replaceAtUsagesAndDelete(value);
        }

        graph.getGraphState().setAfterStage(GraphState.StageFlag.EXPAND_LOGIC);
    }

}
