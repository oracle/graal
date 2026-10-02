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
package jdk.graal.compiler.nodes.calc;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaKind;

/**
 * Reads the low 32 raw bits of a {@code double} as a {@code float}. The result shares the input's
 * allocated floating-point location; no numeric conversion or general-purpose register is needed.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class AssumeFloatNode extends UnaryNode implements LIRLowerable {

    public static final NodeClass<AssumeFloatNode> TYPE = NodeClass.create(AssumeFloatNode.class);

    public AssumeFloatNode(ValueNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Float), value);
        assert value.getStackKind() == JavaKind.Double : value;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder) {
        LIRGeneratorTool lirTool = builder.getLIRGeneratorTool();
        LIRKind castKind = lirTool.getLIRKind(stamp(NodeView.DEFAULT));
        builder.setResult(this, new CastValue(castKind, lirTool.asAllocatable(builder.operand(getValue()))));
    }
}
