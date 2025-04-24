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

import java.util.List;

import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;

/**
 * This phase duplicates return nodes that are directly after a merge node.
 *
 * Example: Before this phase the IR might look like this:
 *
 * <pre>
 *     |       |
 *   [End]   [End]
 *     |    /
 *     |   /  [Value1] [Value2]
 *     |  /      |    /
 *     | /       |   /
 *  [Merge] -- [Phi]
 *     |     /
 *     |    /
 *  [Return]
 * </pre>
 *
 * This phase transforms this to:
 *
 * <pre>
 *      |  [Value1]    | [Value2]
 *      |  /           |  /
 *   [Return]        [Return]
 * </pre>
 *
 * This gets rid of a merge node and the corresponding {@link PhiNode}.
 */
public class ExpandReturnPhase extends BasePhase<CoreProviders> {

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener();
        try (Graph.NodeEventScope nes = graph.trackNodeEvents(listener)) {
            List<ReturnNode> returns = graph.getNodes(ReturnNode.TYPE).snapshot();
            for (ReturnNode returnNode : returns) {
                ExpandControlSinkUtil.distribute(returnNode, graph);
            }
        }
        /*
         * Since the distribute can delete merge nodes that may have a FrameState associated with
         * them, we apply a canonicalization to remove the FrameState.
         */
        CanonicalizerPhase.create().applyIncremental(graph, context, listener.getNodes());
    }
}
