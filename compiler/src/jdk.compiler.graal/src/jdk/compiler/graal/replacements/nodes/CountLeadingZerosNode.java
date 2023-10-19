/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_1;

import jdk.compiler.graal.core.common.type.IntegerStamp;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.UnaryNode;
import jdk.compiler.graal.nodes.spi.ArithmeticLIRLowerable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodes.spi.Lowerable;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.compiler.graal.nodes.type.StampTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Count the number of leading zeros using the hardware instructions where possible.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class CountLeadingZerosNode extends UnaryNode implements ArithmeticLIRLowerable, Lowerable {
    public static final NodeClass<CountLeadingZerosNode> TYPE = NodeClass.create(CountLeadingZerosNode.class);

    protected CountLeadingZerosNode(ValueNode value) {
        super(TYPE, computeStamp(value.stamp(NodeView.DEFAULT), value), value);
        assert value.getStackKind() == JavaKind.Int || value.getStackKind() == JavaKind.Long;
    }

    public static ValueNode create(ValueNode value) {
        ValueNode folded = tryFold(value);
        if (folded != null) {
            return folded;
        }
        return new CountLeadingZerosNode(value);
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        return computeStamp(newStamp, getValue());
    }

    private static Stamp computeStamp(Stamp newStamp, ValueNode theValue) {
        assert newStamp.isCompatible(theValue.stamp(NodeView.DEFAULT));
        assert theValue.getStackKind() == JavaKind.Int || theValue.getStackKind() == JavaKind.Long;
        return StampTool.stampForLeadingZeros((IntegerStamp) newStamp);
    }

    public static ValueNode tryFold(ValueNode value) {
        if (value.isConstant()) {
            JavaConstant c = value.asJavaConstant();
            if (value.getStackKind() == JavaKind.Int) {
                return ConstantNode.forInt(Integer.numberOfLeadingZeros(c.asInt()));
            } else {
                return ConstantNode.forInt(Long.numberOfLeadingZeros(c.asLong()));
            }
        }
        return null;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode folded = tryFold(forValue);
        return folded != null ? folded : this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, gen.emitCountLeadingZeros(builder.operand(getValue())));
    }
}
