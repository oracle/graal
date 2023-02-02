/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_32;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_32;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Intrinsification for {@code Float.floatToFloat16(float)}.
 */
@NodeInfo(cycles = CYCLES_32, size = SIZE_32)
public final class FloatToHalfFloatNode extends UnaryNode implements LIRLowerable {

    public static final NodeClass<FloatToHalfFloatNode> TYPE = NodeClass.create(FloatToHalfFloatNode.class);

    public FloatToHalfFloatNode(ValueNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Short), value);
    }

    // Duplicate of {@code Float.floatToFloat16(float)} to avoid JDK version dependency.
    private static short floatToFloat16(float f) {
        int doppel = Float.floatToRawIntBits(f);
        short signBit = (short) ((doppel & 0x8000_0000) >> 16);

        if (Float.isNaN(f)) {
            // Preserve sign and attempt to preserve significand bits
            return (short) (signBit | 0x7c00 // max exponent + 1
            // Preserve high order bit of float NaN in the
            // binary16 result NaN (tenth bit); OR in remaining
            // bits into lower 9 bits of binary 16 significand.
                            | (doppel & 0x007f_e000) >> 13 // 10 bits
                            | (doppel & 0x0000_1ff0) >> 4  // 9 bits
                            | (doppel & 0x0000_000f));     // 4 bits
        }

        float absF = Math.abs(f);

        // The overflow threshold is binary16 MAX_VALUE + 1/2 ulp
        if (absF >= (0x1.ffcp15f + 0x0.002p15f)) {
            return (short) (signBit | 0x7c00); // Positive or negative infinity
        }

        // Smallest magnitude nonzero representable binary16 value
        // is equal to 0x1.0p-24; half-way and smaller rounds to zero.
        if (absF <= 0x1.0p-24f * 0.5f) { // Covers float zeros and subnormals.
            return signBit; // Positive or negative zero
        }

        // Dealing with finite values in exponent range of binary16
        // (when rounding is done, could still round up)
        int exp = Math.getExponent(f);
        assert -25 <= exp && exp <= 15;

        // For binary16 subnormals, beside forcing exp to -15, retain
        // the difference expdelta = E_min - exp. This is the excess
        // shift value, in addition to 13, to be used in the
        // computations below. Further the (hidden) msb with value 1
        // in f must be involved as well.
        int expdelta = 0;
        int msb = 0x0000_0000;
        if (exp < -14) {
            expdelta = -14 - exp;
            exp = -15;
            msb = 0x0080_0000;
        }
        int fSignifBits = doppel & 0x007f_ffff | msb;

        // Significand bits as if using rounding to zero (truncation).
        short signifBits = (short) (fSignifBits >> (13 + expdelta));

        // For round to nearest even, determining whether or not to
        // round up (in magnitude) is a function of the least
        // significant bit (LSB), the next bit position (the round
        // position), and the sticky bit (whether there are any
        // nonzero bits in the exact result to the right of the round
        // digit). An increment occurs in three cases:
        //
        // LSB Round Sticky
        // 0 1 1
        // 1 1 0
        // 1 1 1
        // See "Computer Arithmetic Algorithms," Koren, Table 4.9

        int lsb = fSignifBits & (1 << 13 + expdelta);
        int round = fSignifBits & (1 << 12 + expdelta);
        int sticky = fSignifBits & ((1 << 12 + expdelta) - 1);

        if (round != 0 && ((lsb | sticky) != 0)) {
            signifBits++;
        }

        // No bits set in significand beyond the *first* exponent bit,
        // not just the sigificand; quantity is added to the exponent
        // to implement a carry out from rounding the significand.
        return (short) (signBit | (((exp + 15) << 10) + signifBits));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue instanceof ConstantNode) {
            float f = forValue.asJavaConstant().asFloat();
            return ConstantNode.forPrimitive(JavaConstant.forShort(floatToFloat16(f)));
        } else if (forValue instanceof HalfFloatToFloatNode) {
            return ((HalfFloatToFloatNode) forValue).getValue();
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().getArithmetic().emitFloatToHalfFloat(gen.operand(getValue()));
        gen.setResult(this, result);
    }
}
