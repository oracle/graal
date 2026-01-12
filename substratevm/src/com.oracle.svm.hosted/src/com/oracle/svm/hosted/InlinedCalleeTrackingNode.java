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
package com.oracle.svm.hosted;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.graph.spi.NodeWithIdentity;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;

/**
 * Marker node used to track the original method in whose graph it was created. The
 * {@link com.oracle.svm.hosted.phases.DynamicAccessMarkingPhase} is responsible for assigning this
 * node a {@link NodeSourcePosition}, typically taken from the root position of the first non-start
 * node in the graph. This allows the node to be traced back to the original method during analysis,
 * even after graph transformations such as inlining.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
public final class InlinedCalleeTrackingNode extends AbstractAnalysisMetadataTrackingNode implements NodeWithIdentity {
    public static final NodeClass<InlinedCalleeTrackingNode> TYPE = NodeClass.create(InlinedCalleeTrackingNode.class);

    public InlinedCalleeTrackingNode() {
        super(TYPE);
    }
}
