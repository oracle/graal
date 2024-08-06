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
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
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
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Determines the index of the least significant "1" bit. Note that the result is undefined if the
 * input is zero.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class BitScanForwardNode extends UnaryNode implements ArithmeticLIRLowerable {

    public static final NodeClass<BitScanForwardNode> TYPE = NodeClass.create(BitScanForwardNode.class);

    public BitScanForwardNode(ValueNode value) {
        super(TYPE, StampFactory.forInteger(JavaKind.Int, 0, ((PrimitiveStamp) value.stamp(NodeView.DEFAULT)).getBits()), value);
        assert value.getStackKind() == JavaKind.Int || value.getStackKind() == JavaKind.Long : Assertions.errorMessage(value);
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        assert newStamp.isCompatible(getValue().stamp(NodeView.DEFAULT));
        IntegerStamp valueStamp = (IntegerStamp) newStamp;
        int min;
        int max;
        long mask = CodeUtil.mask(valueStamp.getBits());
        int firstAlwaysSetBit = scan(valueStamp.mustBeSet() & mask);
        int firstMaybeSetBit = scan(valueStamp.mayBeSet() & mask);
        if (firstAlwaysSetBit == -1) {
            int lastMaybeSetBit = BitScanReverseNode.scan(valueStamp.mayBeSet() & mask);
            min = firstMaybeSetBit;
            max = lastMaybeSetBit;
        } else {
            min = firstMaybeSetBit;
            max = firstAlwaysSetBit;
        }
        return StampFactory.forInteger(JavaKind.Int, min, max);
    }

    public static ValueNode tryFold(ValueNode value) {
        if (value.isConstant()) {
            JavaConstant c = value.asJavaConstant();
            if (c.asLong() != 0) {
                return ConstantNode.forInt(value.getStackKind() == JavaKind.Int ? scan(c.asInt()) : scan(c.asLong()));
            }
        }
        return null;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode folded = tryFold(forValue);
        return folded != null ? folded : this;
    }

    /**
     * Utility method with defined return value for 0.
     *
     * @param v
     * @return number of trailing zeros or -1 if {@code v} == 0.
     */
    public static int scan(long v) {
        if (v == 0) {
            return -1;
        }
        return Long.numberOfTrailingZeros(v);
    }

    /**
     * Utility method with defined return value for 0.
     *
     * @param v
     * @return number of trailing zeros or -1 if {@code v} == 0.
     */
    public static int scan(int v) {
        return scan(0xffffffffL & v);
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, gen.emitBitScanForward(builder.operand(getValue())));
    }
}
