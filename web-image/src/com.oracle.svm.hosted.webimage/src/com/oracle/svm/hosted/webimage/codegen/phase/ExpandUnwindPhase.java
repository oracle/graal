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
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;

/**
 * This phase work just like {@link ExpandReturnPhase}, just with {@link UnwindNode} instead of
 * {@link jdk.graal.compiler.nodes.ReturnNode}.
 */
public class ExpandUnwindPhase extends BasePhase<CoreProviders> {

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener();
        try (Graph.NodeEventScope nes = graph.trackNodeEvents(listener)) {
            List<UnwindNode> unwinds = graph.getNodes(UnwindNode.TYPE).snapshot();
            for (UnwindNode unwind : unwinds) {
                ExpandControlSinkUtil.distribute(unwind, graph);
            }
        }
        /*
         * Since the distribute can delete merge nodes that may have a FrameState associated with
         * them, we apply a canonicalization to remove the FrameState.
         */
        CanonicalizerPhase.create().applyIncremental(graph, context, listener.getNodes());
    }
}
