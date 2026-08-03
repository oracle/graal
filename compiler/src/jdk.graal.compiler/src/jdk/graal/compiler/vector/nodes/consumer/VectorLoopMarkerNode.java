/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.consumer;

import static jdk.graal.compiler.nodeinfo.InputType.Association;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.spi.NodeWithIdentity;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.calc.FloatingNode;

/**
 * This node marks multiple {@link VectorConsumer}s that belong to the same vector loop and should
 * be expanded together to one SIMD loop. Such groups of consumers are also marked by being inputs
 * to the same {@link VectorLoopNode}. The following invariant must hold: Iff two different vector
 * consumers have the same {@link LowerableVectorConsumer#vectorLoop()}, they have the same loop
 * marker input.
 * </p>
 *
 * The reason for having this node is to prevent illegal de-duplication of vector consumers
 * belonging to different consumer groups. De-duplication looks at nodes' inputs rather than usages,
 * therefore unique markers used as inputs are needed to prevent unwanted cases of de-duplication.
 */
// @formatter:off
@NodeInfo(allowedUsageTypes = {Association},
          cycles = CYCLES_0,
          cyclesRationale = "marker node",
          size = SIZE_0,
          sizeRationale = "marker node")
// @formatter:on
public class VectorLoopMarkerNode extends FloatingNode implements NodeWithIdentity {
    public static final NodeClass<VectorLoopMarkerNode> TYPE = NodeClass.create(VectorLoopMarkerNode.class);

    public VectorLoopMarkerNode() {
        super(TYPE, StampFactory.forVoid());
    }
}
