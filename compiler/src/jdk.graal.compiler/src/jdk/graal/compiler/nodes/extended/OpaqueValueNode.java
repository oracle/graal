/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.spi.NodeWithIdentity;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.phases.common.RemoveOpaqueValuePhase;

/**
 * This node type acts as an optimization barrier between its input node and its usages. For
 * example, a MulNode with two ConstantNodes as input will be canonicalized to a ConstantNode. This
 * optimization will be prevented if either of the two constants is wrapped by an OpaqueValueNode.
 * <p>
 * This node accepts an optional {@link StageFlag} argument. If set, the node will canonicalize away
 * after that stage has been applied to the graph.
 * <p>
 * This node is not {@link LIRLowerable}, so it should be removed from the graph before LIR
 * generation. {@link OpaqueValueNode}s that don't fold away will be removed by
 * {@link RemoveOpaqueValuePhase} at the end of low tier.r
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class OpaqueValueNode extends OpaqueNode implements NodeWithIdentity, GuardingNode, IterableNodeType, Canonicalizable {
    public static final NodeClass<OpaqueValueNode> TYPE = NodeClass.create(OpaqueValueNode.class);

    @Input(InputType.Value) private ValueNode value;
    private final StageFlag foldAfter;

    public OpaqueValueNode(ValueNode value) {
        this(value, null);
    }

    public OpaqueValueNode(ValueNode value, StageFlag foldAfter) {
        super(TYPE, value.stamp(NodeView.DEFAULT).unrestricted());
        this.foldAfter = foldAfter;
        this.value = value;
    }

    @Override
    public ValueNode getValue() {
        return value;
    }

    @Override
    public void setValue(ValueNode value) {
        this.updateUsages(this.value, value);
        this.value = value;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (foldAfter != null && this.graph() != null && this.graph().isAfterStage(foldAfter)) {
            // delete this node
            return value;
        }
        return this;
    }
}
