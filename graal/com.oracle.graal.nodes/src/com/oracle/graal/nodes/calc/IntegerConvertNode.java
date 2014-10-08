/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.nodes.calc;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.IntegerConvertOp;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * An {@code IntegerConvert} converts an integer to an integer of different width.
 */
@NodeInfo
public abstract class IntegerConvertNode extends UnaryNode implements ConvertNode, ArithmeticLIRLowerable {

    protected IntegerConvertOp op;
    protected IntegerConvertOp reverseOp;

    protected final int resultBits;

    protected IntegerConvertNode(IntegerConvertOp op, IntegerConvertOp reverseOp, int resultBits, ValueNode input) {
        super(op.foldStamp(resultBits, input.stamp()), input);
        this.op = op;
        this.reverseOp = reverseOp;
        this.resultBits = resultBits;
    }

    public int getResultBits() {
        return resultBits;
    }

    public int getInputBits() {
        if (getValue().stamp() instanceof IntegerStamp) {
            return ((IntegerStamp) getValue().stamp()).getBits();
        } else {
            return 0;
        }
    }

    @Override
    public Constant convert(Constant c) {
        return op.foldConstant(getInputBits(), getResultBits(), c);
    }

    @Override
    public Constant reverse(Constant c) {
        return reverseOp.foldConstant(getResultBits(), getInputBits(), c);
    }

    @Override
    public boolean inferStamp() {
        op = ArithmeticOpTable.forStamp(getValue().stamp()).getIntegerConvertOp(op);
        boolean changed = updateStamp(op.foldStamp(resultBits, getValue().stamp()));
        if (changed) {
            reverseOp = ArithmeticOpTable.forStamp(stamp()).getIntegerConvertOp(reverseOp);
        }
        return changed;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (value.stamp() instanceof IntegerStamp) {
            int inputBits = ((IntegerStamp) value.stamp()).getBits();
            if (inputBits == resultBits) {
                return value;
            } else if (value.isConstant()) {
                return ConstantNode.forPrimitive(stamp(), convert(forValue.asConstant()));
            }
        }
        return this;
    }

    public static ValueNode convert(ValueNode input, Stamp stamp) {
        return convert(input, stamp, false);
    }

    public static ValueNode convert(ValueNode input, Stamp stamp, StructuredGraph graph) {
        ValueNode convert = convert(input, stamp, false);
        if (!convert.isAlive()) {
            assert !convert.isDeleted();
            convert = graph.addOrUnique(convert);
        }
        return convert;
    }

    public static ValueNode convertUnsigned(ValueNode input, Stamp stamp) {
        return convert(input, stamp, true);
    }

    public static ValueNode convert(ValueNode input, Stamp stamp, boolean zeroExtend) {
        IntegerStamp fromStamp = (IntegerStamp) input.stamp();
        IntegerStamp toStamp = (IntegerStamp) stamp;

        ValueNode result;
        if (toStamp.getBits() == fromStamp.getBits()) {
            result = input;
        } else if (toStamp.getBits() < fromStamp.getBits()) {
            result = NarrowNode.create(input, toStamp.getBits());
        } else if (zeroExtend) {
            // toStamp.getBits() > fromStamp.getBits()
            result = ZeroExtendNode.create(input, toStamp.getBits());
        } else {
            // toStamp.getBits() > fromStamp.getBits()
            result = SignExtendNode.create(input, toStamp.getBits());
        }

        IntegerStamp resultStamp = (IntegerStamp) result.stamp();
        assert toStamp.getBits() == resultStamp.getBits();
        return result;
    }
}
