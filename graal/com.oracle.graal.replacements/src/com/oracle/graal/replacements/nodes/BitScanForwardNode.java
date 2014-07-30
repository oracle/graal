/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Determines the index of the least significant "1" bit. Note that the result is undefined if the
 * input is zero.
 */
public class BitScanForwardNode extends UnaryNode implements LIRLowerable {

    public BitScanForwardNode(ValueNode value) {
        super(StampFactory.forInteger(Kind.Int, 0, ((PrimitiveStamp) value.stamp()).getBits()), value);
        assert value.getKind() == Kind.Int || value.getKind() == Kind.Long;
    }

    @Override
    public boolean inferStamp() {
        IntegerStamp valueStamp = (IntegerStamp) getValue().stamp();
        int min;
        int max;
        long mask = IntegerStamp.defaultMask(valueStamp.getBits());
        int firstAlwaysSetBit = scan(valueStamp.downMask() & mask);
        if (firstAlwaysSetBit == -1) {
            int lastMaybeSetBit = BitScanReverseNode.scan(valueStamp.upMask() & mask);
            min = -1;
            max = lastMaybeSetBit;
        } else {
            int firstMaybeSetBit = scan(valueStamp.upMask() & mask);
            min = firstMaybeSetBit;
            max = firstAlwaysSetBit;
        }
        return updateStamp(StampFactory.forInteger(Kind.Int, min, max));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            Constant c = forValue.asConstant();
            if (c.asLong() != 0) {
                return ConstantNode.forInt(forValue.getKind() == Kind.Int ? scan(c.asInt()) : scan(c.asLong()));
            }
        }
        return this;
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

    /**
     * Raw intrinsic for bsf instruction.
     *
     * @param v
     * @return number of trailing zeros or an undefined value if {@code v} == 0.
     */
    @NodeIntrinsic
    public static native int unsafeScan(long v);

    /**
     * Raw intrinsic for bsf instruction.
     *
     * @param v
     * @return number of trailing zeros or an undefined value if {@code v} == 0.
     */
    @NodeIntrinsic
    public static native int unsafeScan(int v);

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().emitBitScanForward(gen.operand(getValue()));
        gen.setResult(this, result);
    }
}
