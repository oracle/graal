/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.simd;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.NodeIntrinsicFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.SerializableConstant;

/**
 * Create a SIMD value filled with copies of a single value.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
@NodeIntrinsicFactory
public final class SimdBroadcastNode extends UnaryNode implements VectorLIRLowerable {
    public static final NodeClass<SimdBroadcastNode> TYPE = NodeClass.create(SimdBroadcastNode.class);

    protected final int length;

    public SimdBroadcastNode(ValueNode element, int length) {
        this(element, length, SimdStamp.broadcast(element.stamp(NodeView.DEFAULT), length));
    }

    public SimdBroadcastNode(ValueNode element, int length, SimdStamp stamp) {
        super(TYPE, stamp, element);
        this.length = length;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(SimdStamp.broadcast(getValue().stamp(NodeView.DEFAULT), length));
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue instanceof ConstantNode && forValue.asConstant() instanceof SerializableConstant) {
            return ConstantNode.forConstant(stamp, SimdConstant.broadcast(forValue.asConstant(), length), tool.getMetaAccess());
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        LIRKind kind = builder.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
        builder.setResult(this, gen.emitVectorFill(kind, builder.operand(getValue())));
    }

    public static boolean intrinsify(GraphBuilderContext b, JavaKind kind, ValueNode element, int length) {
        b.addPush(JavaKind.Object, new SimdBroadcastNode(new NarrowNode(element, kind.getBitCount()), length));
        return true;
    }
}
