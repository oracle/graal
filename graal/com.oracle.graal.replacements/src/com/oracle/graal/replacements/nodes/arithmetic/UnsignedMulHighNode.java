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
package com.oracle.graal.replacements.nodes.arithmetic;

import java.util.function.BiFunction;

import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.compiler.common.type.IntegerStamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.lir.gen.ArithmeticLIRGenerator;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.BinaryNode;
import com.oracle.graal.nodes.spi.ArithmeticLIRLowerable;
import com.oracle.graal.nodes.spi.NodeValueMap;

@NodeInfo(shortName = "|*H|")
public final class UnsignedMulHighNode extends BinaryNode implements ArithmeticLIRLowerable {

    public static final NodeClass<UnsignedMulHighNode> TYPE = NodeClass.create(UnsignedMulHighNode.class);

    public UnsignedMulHighNode(ValueNode x, ValueNode y) {
        this((IntegerStamp) x.stamp().unrestricted(), x, y);
    }

    public UnsignedMulHighNode(IntegerStamp stamp, ValueNode x, ValueNode y) {
        super(TYPE, stamp, x, y);
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

        JavaKind kind = getStackKind();
        assert kind == JavaKind.Int || kind == JavaKind.Long;
        long[] xExtremes = {xStamp.lowerBound(), xStamp.upperBound()};
        long[] yExtremes = {yStamp.lowerBound(), yStamp.upperBound()};
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long a : xExtremes) {
            for (long b : yExtremes) {
                long result = kind == JavaKind.Int ? multiplyHighUnsigned((int) a, (int) b) : multiplyHighUnsigned(a, b);
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
        return updateStamp(processExtremes(getX(), getY(), (min, max) -> (min == (long) max || min >= 0) ? StampFactory.forInteger(getStackKind(), min, max) : StampFactory.forKind(getStackKind())));
    }

    @SuppressWarnings("cast")
    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        return processExtremes(forX, forY, (min, max) -> min == (long) max ? ConstantNode.forIntegerKind(getStackKind(), min) : this);
    }

    @Override
    public void generate(NodeValueMap nodeValueMap, ArithmeticLIRGenerator gen) {
        Value a = nodeValueMap.operand(getX());
        Value b = nodeValueMap.operand(getY());
        nodeValueMap.setResult(this, gen.emitUMulHigh(a, b));
    }

    public static int multiplyHighUnsigned(int x, int y) {
        long xl = x & 0xFFFFFFFFL;
        long yl = y & 0xFFFFFFFFL;
        long r = xl * yl;
        return (int) (r >> 32);
    }

    public static long multiplyHighUnsigned(long x, long y) {
        // Checkstyle: stop
        long x0, y0, z0;
        long x1, y1, z1, z2, t;
        // Checkstyle: resume

        x0 = x & 0xFFFFFFFFL;
        x1 = x >>> 32;

        y0 = y & 0xFFFFFFFFL;
        y1 = y >>> 32;

        z0 = x0 * y0;
        t = x1 * y0 + (z0 >>> 32);
        z1 = t & 0xFFFFFFFFL;
        z2 = t >>> 32;
        z1 += x0 * y1;

        return x1 * y1 + z2 + (z1 >>> 32);
    }
}
