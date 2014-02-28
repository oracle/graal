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
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code NarrowNode} converts an integer to a narrower integer.
 */
public class NarrowNode extends IntegerConvertNode implements Simplifiable {

    public NarrowNode(ValueNode input, int resultBits) {
        super(StampTool.narrowingConversion(input.stamp(), resultBits), input, resultBits);
    }

    public static long narrow(long value, int resultBits) {
        return value & IntegerStamp.defaultMask(resultBits);
    }

    @Override
    public Constant convert(Constant c) {
        return Constant.forPrimitiveInt(getResultBits(), narrow(c.asLong(), getResultBits()));
    }

    @Override
    public Constant reverse(Constant input) {
        IntegerStamp stamp = (IntegerStamp) stamp();
        long result;
        if (stamp.isUnsigned()) {
            result = ZeroExtendNode.zeroExtend(input.asLong(), getResultBits());
        } else {
            result = SignExtendNode.signExtend(input.asLong(), getResultBits());
        }
        return Constant.forPrimitiveInt(getInputBits(), result);
    }

    @Override
    public boolean isLossless() {
        return false;
    }

    private ValueNode tryCanonicalize() {
        ValueNode ret = canonicalConvert();
        if (ret != null) {
            return ret;
        }

        if (getInput() instanceof NarrowNode) {
            // zzzzzzzz yyyyxxxx -(narrow)-> yyyyxxxx -(narrow)-> xxxx
            // ==> zzzzzzzz yyyyxxxx -(narrow)-> xxxx
            NarrowNode other = (NarrowNode) getInput();
            return graph().unique(new NarrowNode(other.getInput(), getResultBits()));
        } else if (getInput() instanceof IntegerConvertNode) {
            // SignExtendNode or ZeroExtendNode
            IntegerConvertNode other = (IntegerConvertNode) getInput();
            if (getResultBits() == other.getInputBits()) {
                // xxxx -(extend)-> yyyy xxxx -(narrow)-> xxxx
                // ==> no-op
                return other.getInput();
            } else if (getResultBits() < other.getInputBits()) {
                // yyyyxxxx -(extend)-> zzzzzzzz yyyyxxxx -(narrow)-> xxxx
                // ==> yyyyxxxx -(narrow)-> xxxx
                return graph().unique(new NarrowNode(other.getInput(), getResultBits()));
            } else {
                if (other instanceof SignExtendNode) {
                    // sxxx -(sign-extend)-> ssssssss sssssxxx -(narrow)-> sssssxxx
                    // ==> sxxx -(sign-extend)-> sssssxxx
                    return graph().unique(new SignExtendNode(other.getInput(), getResultBits()));
                } else if (other instanceof ZeroExtendNode) {
                    // xxxx -(zero-extend)-> 00000000 00000xxx -(narrow)-> 0000xxxx
                    // ==> xxxx -(zero-extend)-> 0000xxxx
                    return graph().unique(new ZeroExtendNode(other.getInput(), getResultBits()));
                }
            }
        }

        return null;
    }

    private boolean tryNarrow(SimplifierTool tool, Stamp stamp, ValueNode node) {
        boolean canNarrow = node instanceof NarrowableArithmeticNode && node.usages().count() == 1;

        if (canNarrow) {
            for (Node inputNode : node.inputs().snapshot()) {
                ValueNode input = (ValueNode) inputNode;
                if (!tryNarrow(tool, stamp, input)) {
                    ValueNode narrow = graph().unique(new NarrowNode(input, getResultBits()));
                    node.replaceFirstInput(input, narrow);
                    tool.addToWorkList(narrow);
                }
            }
            node.setStamp(stamp);
        }

        return canNarrow;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        ValueNode ret = tryCanonicalize();
        if (ret != null) {
            graph().replaceFloating(this, ret);
        } else if (tryNarrow(tool, stamp().unrestricted(), getInput())) {
            graph().replaceFloating(this, getInput());
        }
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(StampTool.narrowingConversion(getInput().stamp(), getResultBits()));
    }

    @Override
    public void generate(ArithmeticLIRGenerator gen) {
        gen.setResult(this, gen.emitNarrow(gen.operand(getInput()), getResultBits()));
    }
}
