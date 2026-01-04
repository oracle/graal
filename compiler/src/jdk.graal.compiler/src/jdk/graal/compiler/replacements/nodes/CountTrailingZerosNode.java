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
package jdk.graal.compiler.replacements.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Count the number of trailing zeros using the hardware instructions where possible.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class CountTrailingZerosNode extends UnaryNode implements ArithmeticLIRLowerable, Lowerable {
    public static final NodeClass<CountTrailingZerosNode> TYPE = NodeClass.create(CountTrailingZerosNode.class);

    protected CountTrailingZerosNode(ValueNode value) {
        super(TYPE, computeStamp(value.stamp(NodeView.DEFAULT), value), value);
        assert value.getStackKind() == JavaKind.Int || value.getStackKind() == JavaKind.Long : Assertions.errorMessage(value);
    }

    public static ValueNode create(ValueNode value) {
        ValueNode folded = tryFold(value);
        if (folded != null) {
            return folded;
        }
        return new CountTrailingZerosNode(value);
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        return computeStamp(newStamp, getValue());
    }

    static Stamp computeStamp(Stamp newStamp, ValueNode value) {
        assert newStamp.isCompatible(value.stamp(NodeView.DEFAULT));
        IntegerStamp valueStamp = (IntegerStamp) newStamp;
        return StampTool.stampForTrailingZeros(valueStamp);
    }

    public static ValueNode tryFold(ValueNode value) {
        if (value.isConstant()) {
            JavaConstant c = value.asJavaConstant();
            if (value.getStackKind() == JavaKind.Int) {
                return ConstantNode.forInt(Integer.numberOfTrailingZeros(c.asInt()));
            } else {
                return ConstantNode.forInt(Long.numberOfTrailingZeros(c.asLong()));
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
        builder.setResult(this, gen.emitCountTrailingZeros(builder.operand(getValue())));
    }

    @NodeIntrinsic
    public static native int countLongTrailingZeros(long value);
}
