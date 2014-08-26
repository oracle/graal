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
package com.oracle.graal.truffle.nodes.arithmetic;

import java.util.function.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.truffle.api.*;

@NodeInfo(shortName = "|*H|")
public class UnsignedMulHighNode extends IntegerArithmeticNode {

    public static UnsignedMulHighNode create(ValueNode x, ValueNode y) {
        return USE_GENERATED_NODES ? new UnsignedMulHighNodeGen(x, y) : new UnsignedMulHighNode(x, y);
    }

    protected UnsignedMulHighNode(ValueNode x, ValueNode y) {
        this(x.stamp().unrestricted(), x, y);
    }

    public static UnsignedMulHighNode create(Stamp stamp, ValueNode x, ValueNode y) {
        return USE_GENERATED_NODES ? new UnsignedMulHighNodeGen(stamp, x, y) : new UnsignedMulHighNode(stamp, x, y);
    }

    protected UnsignedMulHighNode(Stamp stamp, ValueNode x, ValueNode y) {
        super(stamp, x, y);
    }

    @Override
    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 2 && inputs[0].getKind() == inputs[1].getKind();
        switch (inputs[0].getKind()) {
            case Int:
                return Constant.forInt(ExactMath.multiplyHighUnsigned(inputs[0].asInt(), inputs[1].asInt()));
            case Long:
                return Constant.forLong(ExactMath.multiplyHighUnsigned(inputs[0].asLong(), inputs[1].asLong()));
            default:
                throw GraalInternalError.unimplemented();
        }
    }

    /**
     * Determines the minimum and maximum result of this node for the given inputs and returns the
     * result of the given BiFunction on the minimum and maximum values. Note that the minima and
     * maxima are calculated using signed min/max functions, while the values themselves are
     * unsigned.
     */
    private <T> T processExtremes(ValueNode forX, ValueNode forY, BiFunction<Long, Long, T> op) {
        IntegerStamp xStamp = (IntegerStamp) forX.stamp();
        IntegerStamp yStamp = (IntegerStamp) forY.stamp();

        Kind kind = getKind();
        assert kind == Kind.Int || kind == Kind.Long;
        long[] xExtremes = {xStamp.lowerBound(), xStamp.upperBound()};
        long[] yExtremes = {yStamp.lowerBound(), yStamp.upperBound()};
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long a : xExtremes) {
            for (long b : yExtremes) {
                long result = kind == Kind.Int ? ExactMath.multiplyHighUnsigned((int) a, (int) b) : ExactMath.multiplyHighUnsigned(a, b);
                min = Math.min(min, result);
                max = Math.max(max, result);
            }
        }
        return op.apply(min, max);
    }

    @SuppressWarnings("cast")
    @Override
    public boolean inferStamp() {
        // if min is negative, then the value can reach into the unsigned range
        return updateStamp(processExtremes(getX(), getY(), (min, max) -> (min == (long) max || min >= 0) ? StampFactory.forInteger(getKind(), min, max) : StampFactory.forKind(getKind())));
    }

    @SuppressWarnings("cast")
    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        return processExtremes(forX, forY, (min, max) -> min == (long) max ? ConstantNode.forIntegerKind(getKind(), min) : this);
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        Value a = builder.operand(getX());
        Value b = builder.operand(getY());
        builder.setResult(this, gen.emitUMulHigh(a, b));
    }

    @NodeIntrinsic
    public static int multiplyHighUnsigned(int a, int b) {
        return ExactMath.multiplyHighUnsigned(a, b);
    }

    @NodeIntrinsic
    public static long multiplyHighUnsigned(long a, long b) {
        return ExactMath.multiplyHighUnsigned(a, b);
    }
}
