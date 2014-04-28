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
 * The {@code ZeroExtendNode} converts an integer to a wider integer using zero extension.
 */
public class ZeroExtendNode extends IntegerConvertNode {

    public ZeroExtendNode(ValueNode input, int resultBits) {
        super(StampTool.zeroExtend(input.stamp(), resultBits), input, resultBits);
    }

    public static long zeroExtend(long value, int inputBits) {
        if (inputBits < 64) {
            return value & ~(-1L << inputBits);
        } else {
            return value;
        }
    }

    @Override
    public Constant convert(Constant c) {
        return Constant.forPrimitiveInt(getResultBits(), zeroExtend(c.asLong(), getInputBits()));
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

        if (getInput() instanceof ZeroExtendNode) {
            // xxxx -(zero-extend)-> 0000 xxxx -(zero-extend)-> 00000000 0000xxxx
            // ==> xxxx -(zero-extend)-> 00000000 0000xxxx
            ZeroExtendNode other = (ZeroExtendNode) getInput();
            return graph().unique(new ZeroExtendNode(other.getInput(), getResultBits()));
        }
        if (getInput() instanceof NarrowNode) {
            NarrowNode narrow = (NarrowNode) getInput();
            Stamp inputStamp = narrow.getInput().stamp();
            if (inputStamp instanceof IntegerStamp && inputStamp.isCompatible(stamp())) {
                IntegerStamp istamp = (IntegerStamp) inputStamp;
                long mask = IntegerStamp.defaultMask(PrimitiveStamp.getBits(narrow.stamp()));
                if (((istamp.upMask() | istamp.downMask()) & ~mask) == 0) {
                    // The original value is in the range of the masked zero extended result so
                    // simply return the original input.
                    return narrow.getInput();
                }
            }
        }

        return this;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(StampTool.zeroExtend(getInput().stamp(), getResultBits()));
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        builder.setResult(this, gen.emitZeroExtend(builder.operand(getInput()), getInputBits(), getResultBits()));
    }
}
