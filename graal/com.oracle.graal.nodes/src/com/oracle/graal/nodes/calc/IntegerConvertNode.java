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
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * An {@code IntegerConvert} converts an integer to an integer of different width.
 */
public abstract class IntegerConvertNode extends ConvertNode implements ArithmeticLIRLowerable, Canonicalizable {

    private final int resultBits;

    protected IntegerConvertNode(Stamp stamp, ValueNode input, int resultBits) {
        super(stamp, input);
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

    public static long convert(long value, int bits, boolean unsigned) {
        if (unsigned) {
            return ZeroExtendNode.zeroExtend(value, bits);
        } else {
            return SignExtendNode.signExtend(value, bits);
        }
    }

    protected ValueNode canonicalConvert() {
        if (getValue().stamp() instanceof IntegerStamp) {
            int inputBits = ((IntegerStamp) getValue().stamp()).getBits();
            if (inputBits == resultBits) {
                return getValue();
            } else if (getValue().isConstant()) {
                Constant ret = evalConst(getValue().asConstant());
                return ConstantNode.forIntegerBits(resultBits, ret.asLong(), graph());
            }
        }

        return null;
    }

    public static ValueNode convert(ValueNode input, Stamp stamp) {
        return convert(input, stamp, false);
    }

    public static ValueNode convertUnsigned(ValueNode input, Stamp stamp) {
        return convert(input, stamp, true);
    }

    public static ValueNode convert(ValueNode input, Stamp stamp, boolean zeroExtend) {
        StructuredGraph graph = input.graph();
        IntegerStamp fromStamp = (IntegerStamp) input.stamp();
        IntegerStamp toStamp = (IntegerStamp) stamp;

        ValueNode result;
        if (toStamp.getBits() == fromStamp.getBits()) {
            result = input;
        } else if (toStamp.getBits() < fromStamp.getBits()) {
            result = graph.unique(new NarrowNode(input, toStamp.getBits()));
        } else if (zeroExtend) {
            // toStamp.getBits() > fromStamp.getBits()
            result = graph.unique(new ZeroExtendNode(input, toStamp.getBits()));
        } else {
            // toStamp.getBits() > fromStamp.getBits()
            result = graph.unique(new SignExtendNode(input, toStamp.getBits()));
        }

        IntegerStamp resultStamp = (IntegerStamp) result.stamp();
        assert toStamp.getBits() == resultStamp.getBits();
        return result;
    }
}
