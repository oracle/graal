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
package com.oracle.svm.hosted.phases;

import com.oracle.svm.hosted.DynamicAccessDetectionSupport;
import com.oracle.svm.hosted.InlinedCalleeTrackingNode;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;

/**
 * This phase inserts a {@link InlinedCalleeTrackingNode} at the start of the compilation graph for
 * every method in the predetermined set of dynamic-access methods.
 * <p>
 * The primary purpose of these nodes is to retain the source position of the original method, so
 * that if the method is inlined during analysis, the {@link DynamicAccessDetectionPhase} can later
 * identify the original method.
 * <p>
 * This phase is enabled by the {@link com.oracle.svm.core.SubstrateOptions#TrackDynamicAccess}
 * option and runs before inlining.
 */
public class DynamicAccessMarkingPhase extends BasePhase<CoreProviders> {
    private final DynamicAccessDetectionSupport dynamicAccessDetectionSupport;

    public DynamicAccessMarkingPhase() {
        dynamicAccessDetectionSupport = DynamicAccessDetectionSupport.instance();
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        assert dynamicAccessDetectionSupport.lookupDynamicAccessMethod(graph.method()) != null;

        InlinedCalleeTrackingNode node = graph.add(new InlinedCalleeTrackingNode());
        StartNode start = graph.start();
        /*
         * We manually set the NodeSourcePosition of the node to the root NSP of the first non-start
         * node. This ensures the node points to the original method for analysis purposes.
         */
        node.setNodeSourcePosition(getRootSourcePosition(start.next().getNodeSourcePosition()));
        graph.addAfterFixed(start, node);
    }

    private static NodeSourcePosition getRootSourcePosition(NodeSourcePosition nodeSourcePosition) {
        NodeSourcePosition rootNodeSourcePosition = nodeSourcePosition;
        while (rootNodeSourcePosition != null && rootNodeSourcePosition.getCaller() != null) {
            rootNodeSourcePosition = rootNodeSourcePosition.getCaller();
        }
        return rootNodeSourcePosition;
    }
}
