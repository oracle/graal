/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.*;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.IntegerConvertOp.Narrow;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.IntegerConvertOp.SignExtend;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code NarrowNode} converts an integer to a narrower integer.
 */
@NodeInfo
public final class NarrowNode extends IntegerConvertNode<Narrow, SignExtend> {

    public static final NodeClass<NarrowNode> TYPE = NodeClass.create(NarrowNode.class);

    public NarrowNode(ValueNode input, int resultBits) {
        this(input, PrimitiveStamp.getBits(input.stamp()), resultBits);
        assert 0 < resultBits && resultBits <= PrimitiveStamp.getBits(input.stamp());
    }

    public NarrowNode(ValueNode input, int inputBits, int resultBits) {
        super(TYPE, ArithmeticOpTable::getNarrow, ArithmeticOpTable::getSignExtend, inputBits, resultBits, input);
    }

    public static ValueNode create(ValueNode input, int resultBits) {
        return create(input, PrimitiveStamp.getBits(input.stamp()), resultBits);
    }

    public static ValueNode create(ValueNode input, int inputBits, int resultBits) {
        IntegerConvertOp<Narrow> signExtend = ArithmeticOpTable.forStamp(input.stamp()).getNarrow();
        ValueNode synonym = findSynonym(signExtend, input, inputBits, resultBits, signExtend.foldStamp(inputBits, resultBits, input.stamp()));
        if (synonym != null) {
            return synonym;
        } else {
            return new NarrowNode(input, inputBits, resultBits);
        }
    }

    @Override
    public boolean isLossless() {
        return false;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode ret = super.canonical(tool, forValue);
        if (ret != this) {
            return ret;
        }

        if (forValue instanceof NarrowNode) {
            // zzzzzzzz yyyyxxxx -(narrow)-> yyyyxxxx -(narrow)-> xxxx
            // ==> zzzzzzzz yyyyxxxx -(narrow)-> xxxx
            NarrowNode other = (NarrowNode) forValue;
            return new NarrowNode(other.getValue(), other.getInputBits(), getResultBits());
        } else if (forValue instanceof IntegerConvertNode) {
            // SignExtendNode or ZeroExtendNode
            IntegerConvertNode<?, ?> other = (IntegerConvertNode<?, ?>) forValue;
            if (getResultBits() == other.getInputBits()) {
                // xxxx -(extend)-> yyyy xxxx -(narrow)-> xxxx
                // ==> no-op
                return other.getValue();
            } else if (getResultBits() < other.getInputBits()) {
                // yyyyxxxx -(extend)-> zzzzzzzz yyyyxxxx -(narrow)-> xxxx
                // ==> yyyyxxxx -(narrow)-> xxxx
                return new NarrowNode(other.getValue(), other.getInputBits(), getResultBits());
            } else {
                if (other instanceof SignExtendNode) {
                    // sxxx -(sign-extend)-> ssssssss sssssxxx -(narrow)-> sssssxxx
                    // ==> sxxx -(sign-extend)-> sssssxxx
                    return new SignExtendNode(other.getValue(), other.getInputBits(), getResultBits());
                } else if (other instanceof ZeroExtendNode) {
                    // xxxx -(zero-extend)-> 00000000 00000xxx -(narrow)-> 0000xxxx
                    // ==> xxxx -(zero-extend)-> 0000xxxx
                    return new ZeroExtendNode(other.getValue(), other.getInputBits(), getResultBits());
                }
            }
        }
        return this;
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        builder.setResult(this, gen.emitNarrow(builder.operand(getValue()), getResultBits()));
    }
}
