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
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code SignExtendNode} converts an integer to a wider integer using sign extension.
 */
public class SignExtendNode extends IntegerConvertNode {

    public SignExtendNode(ValueNode input, int resultBits) {
        super(StampTool.signExtend(input.stamp(), resultBits), input, resultBits);
    }

    public static long signExtend(long value, int inputBits) {
        if (inputBits < 64) {
            if ((value >>> (inputBits - 1) & 1) == 1) {
                return value | (-1L << inputBits);
            } else {
                return value & ~(-1L << inputBits);
            }
        } else {
            return value;
        }
    }

    @Override
    public Constant convert(Constant c) {
        return Constant.forPrimitiveInt(getResultBits(), signExtend(c.asLong(), getInputBits()));
    }

    @Override
    public Constant reverse(Constant c) {
        return Constant.forPrimitiveInt(getInputBits(), NarrowNode.narrow(c.asLong(), getInputBits()));
    }

    @Override
    public boolean isLossless() {
        return true;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ValueNode ret = canonicalConvert();
        if (ret != null) {
            return ret;
        }

        if (getValue() instanceof SignExtendNode) {
            // sxxx -(sign-extend)-> ssss sxxx -(sign-extend)-> ssssssss sssssxxx
            // ==> sxxx -(sign-extend)-> ssssssss sssssxxx
            SignExtendNode other = (SignExtendNode) getValue();
            return graph().unique(new SignExtendNode(other.getValue(), getResultBits()));
        } else if (getValue() instanceof ZeroExtendNode) {
            ZeroExtendNode other = (ZeroExtendNode) getValue();
            if (other.getResultBits() > other.getInputBits()) {
                // sxxx -(zero-extend)-> 0000 sxxx -(sign-extend)-> 00000000 0000sxxx
                // ==> sxxx -(zero-extend)-> 00000000 0000sxxx
                return graph().unique(new ZeroExtendNode(other.getValue(), getResultBits()));
            }
        }

        if (getValue().stamp() instanceof IntegerStamp) {
            IntegerStamp inputStamp = (IntegerStamp) getValue().stamp();
            if ((inputStamp.upMask() & (1L << (getInputBits() - 1))) == 0L) {
                // 0xxx -(sign-extend)-> 0000 0xxx
                // ==> 0xxx -(zero-extend)-> 0000 0xxx
                return graph().unique(new ZeroExtendNode(getValue(), getResultBits()));
            }
        }

        return this;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(StampTool.signExtend(getValue().stamp(), getResultBits()));
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        builder.setResult(this, gen.emitSignExtend(builder.operand(getValue()), getInputBits(), getResultBits()));
    }
}
