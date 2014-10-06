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

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code SignExtendNode} converts an integer to a wider integer using sign extension.
 */
@NodeInfo
public class SignExtendNode extends IntegerConvertNode {

    public static SignExtendNode create(ValueNode input, int resultBits) {
        return USE_GENERATED_NODES ? new SignExtendNodeGen(input, resultBits) : new SignExtendNode(input, resultBits);
    }

    private SignExtendNode(ArithmeticOpTable ops, ValueNode input, int resultBits) {
        super(ops.getSignExtend(), ops.getNarrow(), resultBits, input);
    }

    protected SignExtendNode(ValueNode input, int resultBits) {
        this(ArithmeticOpTable.forStamp(input.stamp()), input, resultBits);
    }

    @Override
    public boolean isLossless() {
        return true;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode ret = super.canonical(tool, forValue);
        if (ret != this) {
            return ret;
        }

        if (forValue instanceof SignExtendNode) {
            // sxxx -(sign-extend)-> ssss sxxx -(sign-extend)-> ssssssss sssssxxx
            // ==> sxxx -(sign-extend)-> ssssssss sssssxxx
            SignExtendNode other = (SignExtendNode) forValue;
            return SignExtendNode.create(other.getValue(), getResultBits());
        } else if (forValue instanceof ZeroExtendNode) {
            ZeroExtendNode other = (ZeroExtendNode) forValue;
            if (other.getResultBits() > other.getInputBits()) {
                // sxxx -(zero-extend)-> 0000 sxxx -(sign-extend)-> 00000000 0000sxxx
                // ==> sxxx -(zero-extend)-> 00000000 0000sxxx
                return ZeroExtendNode.create(other.getValue(), getResultBits());
            }
        }

        if (forValue.stamp() instanceof IntegerStamp) {
            IntegerStamp inputStamp = (IntegerStamp) forValue.stamp();
            if ((inputStamp.upMask() & (1L << (getInputBits() - 1))) == 0L) {
                // 0xxx -(sign-extend)-> 0000 0xxx
                // ==> 0xxx -(zero-extend)-> 0000 0xxx
                return ZeroExtendNode.create(forValue, getResultBits());
            }
        }

        return this;
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        builder.setResult(this, gen.emitSignExtend(builder.operand(getValue()), getInputBits(), getResultBits()));
    }
}
