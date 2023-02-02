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

import static java.lang.Float.SIZE;
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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Intrinsification for {@code Float.float16ToFloat(short)}.
 */
@NodeInfo(cycles = CYCLES_32, size = SIZE_32)
public final class HalfFloatToFloatNode extends UnaryNode implements LIRLowerable {

    public static final NodeClass<HalfFloatToFloatNode> TYPE = NodeClass.create(HalfFloatToFloatNode.class);

    public HalfFloatToFloatNode(ValueNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Float), value);
    }

    private static final int SIGNIFICAND_WIDTH = 24;
    private static final int EXP_BIAS = (1 << (SIZE - SIGNIFICAND_WIDTH - 1)) - 1;
    private static final int SIGNIF_SHIFT = (SIGNIFICAND_WIDTH - 11);

    // Duplicate of {@code Float.float16ToFloat(short)} to avoid JDK version dependency.
    private static float float16ToFloat(short floatBinary16) {
        /*
         * The binary16 format has 1 sign bit, 5 exponent bits, and 10 significand bits. The
         * exponent bias is 15.
         */
        int bin16arg = floatBinary16;
        int bin16SignBit = 0x8000 & bin16arg;
        int bin16ExpBits = 0x7c00 & bin16arg;
        int bin16SignifBits = 0x03FF & bin16arg;

        // Shift left difference in the number of significand bits in
        // the float and binary16 formats

        float sign = (bin16SignBit != 0) ? -1.0f : 1.0f;

        // Extract binary16 exponent, remove its bias, add in the bias
        // of a float exponent and shift to correct bit location
        // (significand width includes the implicit bit so shift one
        // less).
        int bin16Exp = (bin16ExpBits >> 10) - 15;
        if (bin16Exp == -15) {
            // For subnormal binary16 values and 0, the numerical
            // value is 2^24 * the significand as an integer (no
            // implicit bit).
            return sign * (0x1p-24f * bin16SignifBits);
        } else if (bin16Exp == 16) {
            return (bin16SignifBits == 0) ? sign * Float.POSITIVE_INFINITY : Float.intBitsToFloat((bin16SignBit << 16) | 0x7f80_0000 | (bin16SignifBits << SIGNIF_SHIFT));
        }

        int floatExpBits = (bin16Exp + EXP_BIAS) << (SIGNIFICAND_WIDTH - 1);

        // Compute and combine result sign, exponent, and significand bits.
        return Float.intBitsToFloat((bin16SignBit << 16) | floatExpBits | (bin16SignifBits << SIGNIF_SHIFT));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue instanceof ConstantNode) {
            short s = (short) forValue.asJavaConstant().asInt();
            return ConstantNode.forFloat(float16ToFloat(s));
        } else if (forValue instanceof FloatToHalfFloatNode) {
            return ((FloatToHalfFloatNode) forValue).getValue();
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().getArithmetic().emitHalfFloatToFloat(gen.operand(getValue()));
        gen.setResult(this, result);
    }
}
