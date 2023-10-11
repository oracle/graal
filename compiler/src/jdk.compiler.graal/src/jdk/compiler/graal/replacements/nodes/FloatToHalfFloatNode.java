/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.replacements.nodes;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_32;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_32;

import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.UnaryNode;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodes.spi.LIRLowerable;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Intrinsification for {@code Float.floatToFloat16(float)}.
 */
@NodeInfo(cycles = CYCLES_32, size = SIZE_32)
public final class FloatToHalfFloatNode extends UnaryNode implements LIRLowerable {

    public static final NodeClass<FloatToHalfFloatNode> TYPE = NodeClass.create(FloatToHalfFloatNode.class);

    public FloatToHalfFloatNode(ValueNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Short), value);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue instanceof ConstantNode) {
            float f = forValue.asJavaConstant().asFloat();
            return ConstantNode.forPrimitive(JavaConstant.forShort(Float.floatToFloat16(f)));
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().getArithmetic().emitFloatToHalfFloat(gen.operand(getValue()));
        gen.setResult(this, result);
    }
}
