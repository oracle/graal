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
package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodes.calc.BinaryArithmeticNode.getArithmeticOpTable;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ArithmeticOperation;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.StampInverter;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * An {@code IntegerConvert} converts an integer to an integer of different width.
 */
@NodeInfo
public abstract class IntegerConvertNode<OP> extends UnaryNode implements ArithmeticOperation, ConvertNode, ArithmeticLIRLowerable, StampInverter {
    @SuppressWarnings("rawtypes") public static final NodeClass<IntegerConvertNode> TYPE = NodeClass.create(IntegerConvertNode.class);

    protected final int inputBits;
    protected final int resultBits;

    protected IntegerConvertNode(NodeClass<? extends IntegerConvertNode<OP>> c, IntegerConvertOp<OP> opForStampComputation, int inputBits, int resultBits, ValueNode input) {
        super(c, opForStampComputation.foldStamp(inputBits, resultBits, input.stamp(NodeView.DEFAULT)), input);
        this.inputBits = inputBits;
        this.resultBits = resultBits;
        assert PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT)) == 0 || PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT)) == inputBits;
    }

    public int getInputBits() {
        return inputBits;
    }

    public int getResultBits() {
        return resultBits;
    }

    protected abstract IntegerConvertOp<OP> getOp(ArithmeticOpTable table);

    protected abstract IntegerConvertOp<?> getReverseOp(ArithmeticOpTable table);

    @Override
    public final IntegerConvertOp<OP> getArithmeticOp() {
        return getOp(getArithmeticOpTable(getValue()));
    }

    @Override
    public Constant convert(Constant c, ConstantReflectionProvider constantReflection) {
        return getArithmeticOp().foldConstant(getInputBits(), getResultBits(), c);
    }

    @Override
    public Constant reverse(Constant c, ConstantReflectionProvider constantReflection) {
        IntegerConvertOp<?> reverse = getReverseOp(ArithmeticOpTable.forStamp(stamp(NodeView.DEFAULT)));
        return reverse.foldConstant(getResultBits(), getInputBits(), c);
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        assert newStamp.isCompatible(getValue().stamp(NodeView.DEFAULT));
        return getArithmeticOp().foldStamp(inputBits, resultBits, newStamp);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode synonym = findSynonym(getOp(getArithmeticOpTable(forValue)), forValue, inputBits, resultBits, stamp(NodeView.DEFAULT));
        if (synonym != null) {
            return synonym;
        }
        return this;
    }

    protected static <T> ValueNode findSynonym(IntegerConvertOp<T> operation, ValueNode value, int inputBits, int resultBits, Stamp stamp) {
        if (inputBits == resultBits) {
            return value;
        } else if (value.isConstant()) {
            return ConstantNode.forPrimitive(stamp, operation.foldConstant(inputBits, resultBits, value.asConstant()));
        }
        return null;
    }

    public static ValueNode convert(ValueNode input, Stamp stamp, NodeView view) {
        return convert(input, stamp, false, view);
    }

    public static ValueNode convert(ValueNode input, Stamp stamp, StructuredGraph graph, NodeView view) {
        return convert(input, stamp, false, graph, view, true);
    }

    public static ValueNode convert(ValueNode input, Stamp stamp, boolean zeroExtend, StructuredGraph graph, NodeView view) {
        return convert(input, stamp, zeroExtend, graph, view, true);
    }

    public static ValueNode convert(ValueNode input, Stamp stamp, boolean zeroExtend, StructuredGraph graph, NodeView view, boolean gvn) {
        ValueNode convert = convert(input, stamp, zeroExtend, view, gvn);
        if (gvn) {
            if (!convert.isAlive()) {
                assert !convert.isDeleted();
                convert = graph.addOrUniqueWithInputs(convert);
            }
        } else {
            GraalError.guarantee(!convert.isAlive(), "Convert must not GVN");
            GraalError.guarantee(!convert.isDeleted(), "Convert must not GVN");
            convert = graph.addWithoutUniqueWithInputs(convert);
        }
        return convert;
    }

    public static ValueNode convertUnsigned(ValueNode input, Stamp stamp, NodeView view) {
        return convert(input, stamp, true, view);
    }

    public static ValueNode convertUnsigned(ValueNode input, Stamp stamp, StructuredGraph graph, NodeView view) {
        ValueNode convert = convert(input, stamp, true, view);
        if (!convert.isAlive()) {
            assert !convert.isDeleted();
            convert = graph.addOrUniqueWithInputs(convert);
        }
        return convert;
    }

    public static ValueNode convert(ValueNode input, Stamp stamp, boolean zeroExtend, NodeView view) {
        return convert(input, stamp, zeroExtend, view, true);
    }

    private static ValueNode convert(ValueNode input, Stamp stamp, boolean zeroExtend, NodeView view, boolean gvn) {
        IntegerStamp fromStamp = (IntegerStamp) input.stamp(view);
        IntegerStamp toStamp = (IntegerStamp) stamp;

        ValueNode result;
        if (gvn && toStamp.getBits() == fromStamp.getBits()) {
            result = input;
        } else if (toStamp.getBits() < fromStamp.getBits()) {
            result = new NarrowNode(input, fromStamp.getBits(), toStamp.getBits());
        } else if (zeroExtend) {
            // toStamp.getBits() > fromStamp.getBits()
            if (gvn) {
                result = ZeroExtendNode.create(input, toStamp.getBits(), view);
            } else {
                result = new ZeroExtendNode(input, toStamp.getBits());
            }
        } else {
            // toStamp.getBits() > fromStamp.getBits()
            if (gvn) {
                result = SignExtendNode.create(input, toStamp.getBits(), view);
            } else {
                result = new SignExtendNode(input, toStamp.getBits());
            }
        }

        IntegerStamp resultStamp = (IntegerStamp) result.stamp(view);
        assert toStamp.getBits() == resultStamp.getBits();
        return result;
    }

    @Override
    public Stamp invertStamp(Stamp outStamp) {
        return getArithmeticOp().invertStamp(inputBits, resultBits, outStamp);
    }
}
