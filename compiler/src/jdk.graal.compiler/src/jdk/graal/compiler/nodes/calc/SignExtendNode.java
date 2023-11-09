/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp.Narrow;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp.SignExtend;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.code.CodeUtil;

/**
 * The {@code SignExtendNode} converts an integer to a wider integer using sign extension.
 *
 * On all supported architectures, sub-word (<32 bit) operations generally do not yield performance
 * improvements. They can even be slower than 32 bit operations. Thus, nodes extending <32 bit
 * values to 32 bit or more should usually not be removed.
 */
@NodeInfo(cycles = CYCLES_1)
public final class SignExtendNode extends IntegerConvertNode<SignExtend> {

    public static final NodeClass<SignExtendNode> TYPE = NodeClass.create(SignExtendNode.class);

    public SignExtendNode(ValueNode input, int resultBits) {
        this(input, PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT)), resultBits);
        assert 0 < PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT)) && PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT)) <= resultBits : Assertions.errorMessageContext("input", input,
                        "resultBits", resultBits);
    }

    public SignExtendNode(ValueNode input, int inputBits, int resultBits) {
        super(TYPE, BinaryArithmeticNode.getArithmeticOpTable(input).getSignExtend(), inputBits, resultBits, input);
    }

    public static ValueNode create(ValueNode input, int resultBits, NodeView view) {
        return create(input, PrimitiveStamp.getBits(input.stamp(view)), resultBits, view);
    }

    public static ValueNode create(ValueNode input, int inputBits, int resultBits, NodeView view) {
        IntegerConvertOp<SignExtend> signExtend = ArithmeticOpTable.forStamp(input.stamp(view)).getSignExtend();
        ValueNode synonym = findSynonym(signExtend, input, inputBits, resultBits, signExtend.foldStamp(inputBits, resultBits, input.stamp(view)));
        if (synonym != null) {
            return synonym;
        }
        return canonical(null, input, inputBits, resultBits, view);
    }

    @Override
    protected IntegerConvertOp<SignExtend> getOp(ArithmeticOpTable table) {
        return table.getSignExtend();
    }

    @Override
    protected IntegerConvertOp<Narrow> getReverseOp(ArithmeticOpTable table) {
        return table.getNarrow();
    }

    @Override
    public boolean isLossless() {
        return true;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        NodeView view = NodeView.from(tool);
        ValueNode ret = super.canonical(tool, forValue);
        if (ret != this) {
            return ret;
        }

        return canonical(this, forValue, getInputBits(), getResultBits(), view);
    }

    private static ValueNode canonical(SignExtendNode self, ValueNode forValue, int inputBits, int resultBits, NodeView view) {
        if (forValue instanceof SignExtendNode) {
            // sxxx -(sign-extend)-> ssss sxxx -(sign-extend)-> ssssssss sssssxxx
            // ==> sxxx -(sign-extend)-> ssssssss sssssxxx
            SignExtendNode other = (SignExtendNode) forValue;
            return SignExtendNode.create(other.getValue(), other.getInputBits(), resultBits, view);
        } else if (forValue instanceof ZeroExtendNode) {
            ZeroExtendNode other = (ZeroExtendNode) forValue;
            if (other.getResultBits() > other.getInputBits()) {
                // sxxx -(zero-extend)-> 0000 sxxx -(sign-extend)-> 00000000 0000sxxx
                // ==> sxxx -(zero-extend)-> 00000000 0000sxxx
                return ZeroExtendNode.create(other.getValue(), other.getInputBits(), resultBits, view, other.isInputAlwaysPositive());
            }
        }

        if (forValue.stamp(view) instanceof IntegerStamp) {
            IntegerStamp inputStamp = (IntegerStamp) forValue.stamp(view);
            if ((inputStamp.mayBeSet() & (1L << (inputBits - 1))) == 0L) {
                // 0xxx -(sign-extend)-> 0000 0xxx
                // ==> 0xxx -(zero-extend)-> 0000 0xxx
                return ZeroExtendNode.create(forValue, inputBits, resultBits, view, true);
            }
        }
        if (forValue instanceof NarrowNode) {
            NarrowNode narrow = (NarrowNode) forValue;
            Stamp inputStamp = narrow.getValue().stamp(view);
            if (inputStamp instanceof IntegerStamp) {
                IntegerStamp istamp = (IntegerStamp) inputStamp;
                long mask = CodeUtil.mask(PrimitiveStamp.getBits(narrow.stamp(view)) - 1);
                if (~mask <= istamp.lowerBound() && istamp.upperBound() <= mask) {
                    // The original value cannot change because of the narrow and sign extend.
                    if (istamp.getBits() < resultBits) {
                        // Need to keep the sign extend, skip the narrow.
                        return create(narrow.getValue(), resultBits, view);
                    } else if (istamp.getBits() > resultBits) {
                        // Need to keep the narrow, skip the sign extend.
                        return NarrowNode.create(narrow.getValue(), resultBits, view);
                    } else {
                        assert istamp.getBits() == resultBits : Assertions.errorMessageContext("self", self, "forValue", forValue, "iStamp", istamp, "resultBits", resultBits);
                        // Just return the original value.
                        return narrow.getValue();
                    }
                }
            }
        }
        return self != null ? self : new SignExtendNode(forValue, inputBits, resultBits);
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitSignExtend(nodeValueMap.operand(getValue()), getInputBits(), getResultBits()));
    }

    @Override
    public boolean mayNullCheckSkipConversion() {
        return true;
    }
}
