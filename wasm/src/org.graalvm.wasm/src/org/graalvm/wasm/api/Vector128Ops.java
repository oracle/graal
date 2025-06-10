/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm.api;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.Vector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorSpecies;
import org.graalvm.wasm.constants.Bytecode;

import static org.graalvm.wasm.api.Vector128.BYTES;

public class Vector128Ops {

    public interface Shape<E> {

        Vector<E> reinterpret(ByteVector bytes);

        VectorSpecies<E> species();

        default Vector<E> zero() {
            return species().zero();
        }

        default Vector<E> broadcast(long e) {
            return species().broadcast(e);
        }
    }

    public static final Shape<Byte> I8X16 = new Shape<>() {
        @Override
        public Vector<Byte> reinterpret(ByteVector bytes) {
            return bytes;
        }

        @Override
        public VectorSpecies<Byte> species() {
            return ByteVector.SPECIES_128;
        }
    };
    public static final Shape<Short> I16X8 = new Shape<>() {
        @Override
        public Vector<Short> reinterpret(ByteVector bytes) {
            return bytes.reinterpretAsShorts();
        }

        @Override
        public VectorSpecies<Short> species() {
            return ShortVector.SPECIES_128;
        }
    };
    public static final Shape<Integer> I32X4 = new Shape<>() {
        @Override
        public Vector<Integer> reinterpret(ByteVector bytes) {
            return bytes.reinterpretAsInts();
        }

        @Override
        public VectorSpecies<Integer> species() {
            return IntVector.SPECIES_128;
        }
    };
    public static final Shape<Long> I64X2 = new Shape<>() {
        @Override
        public Vector<Long> reinterpret(ByteVector bytes) {
            return bytes.reinterpretAsLongs();
        }

        @Override
        public VectorSpecies<Long> species() {
            return LongVector.SPECIES_128;
        }
    };
    public static final Shape<Float> F32X4 = new Shape<>() {
        @Override
        public Vector<Float> reinterpret(ByteVector bytes) {
            return bytes.reinterpretAsFloats();
        }

        @Override
        public VectorSpecies<Float> species() {
            return FloatVector.SPECIES_128;
        }
    };
    public static final Shape<Double> F64X2 = new Shape<>() {
        @Override
        public Vector<Double> reinterpret(ByteVector bytes) {
            return bytes.reinterpretAsDoubles();
        }

        @Override
        public VectorSpecies<Double> species() {
            return DoubleVector.SPECIES_128;
        }
    };

    @FunctionalInterface
    private interface UnaryScalarOp<F> {
        F apply(F operand);
    }

    @FunctionalInterface
    private interface BinaryVectorOp<F> {
        Vector<F> apply(Vector<F> leftOperand, Vector<F> rightOperand);
    }

    public static ByteVector unary(ByteVector x, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_NOT -> unop(x, I8X16, VectorOperators.NOT);
            case Bytecode.VECTOR_I8X16_ABS -> unop(x, I8X16, VectorOperators.ABS);
            case Bytecode.VECTOR_I8X16_NEG -> unop(x, I8X16, VectorOperators.NEG);
            case Bytecode.VECTOR_I8X16_POPCNT -> unop(x, I8X16, VectorOperators.BIT_COUNT);
            case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_S -> extadd_pairwise(x, I8X16, VectorOperators.B2S);
            case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_U -> extadd_pairwise(x, I8X16, VectorOperators.ZERO_EXTEND_B2S);
            case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_S -> extend(x, 0, I8X16, VectorOperators.B2S);
            case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_U -> extend(x, 0, I8X16, VectorOperators.ZERO_EXTEND_B2S);
            case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_S -> extend(x, 1, I8X16, VectorOperators.B2S);
            case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_U -> extend(x, 1, I8X16, VectorOperators.ZERO_EXTEND_B2S);
            case Bytecode.VECTOR_I16X8_ABS -> unop(x, I16X8, VectorOperators.ABS);
            case Bytecode.VECTOR_I16X8_NEG -> unop(x, I16X8, VectorOperators.NEG);
            case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_S -> extadd_pairwise(x, I16X8, VectorOperators.S2I);
            case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_U -> extadd_pairwise(x, I16X8, VectorOperators.ZERO_EXTEND_S2I);
            case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_S -> extend(x, 0, I16X8, VectorOperators.S2I);
            case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_U -> extend(x, 0, I16X8, VectorOperators.ZERO_EXTEND_S2I);
            case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_S -> extend(x, 1, I16X8, VectorOperators.S2I);
            case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_U -> extend(x, 1, I16X8, VectorOperators.ZERO_EXTEND_S2I);
            case Bytecode.VECTOR_I32X4_ABS -> unop(x, I32X4, VectorOperators.ABS);
            case Bytecode.VECTOR_I32X4_NEG -> unop(x, I32X4, VectorOperators.NEG);
            case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_S -> extend(x, 0, I32X4, VectorOperators.I2L);
            case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_U -> extend(x, 0, I32X4, VectorOperators.ZERO_EXTEND_I2L);
            case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_S -> extend(x, 1, I32X4, VectorOperators.I2L);
            case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_U -> extend(x, 1, I32X4, VectorOperators.ZERO_EXTEND_I2L);
            case Bytecode.VECTOR_I64X2_ABS -> unop(x, I64X2, VectorOperators.ABS);
            case Bytecode.VECTOR_I64X2_NEG -> unop(x, I64X2, VectorOperators.NEG);
            case Bytecode.VECTOR_F32X4_ABS -> unop(x, F32X4, VectorOperators.ABS);
            case Bytecode.VECTOR_F32X4_NEG -> unop(x, F32X4, VectorOperators.NEG);
            case Bytecode.VECTOR_F32X4_SQRT -> unop(x, F32X4, VectorOperators.SQRT);
            case Bytecode.VECTOR_F32X4_CEIL -> f32x4_unop_fallback(x, f -> (float) Math.ceil(f));
            case Bytecode.VECTOR_F32X4_FLOOR -> f32x4_unop_fallback(x, f -> (float) Math.floor(f));
            case Bytecode.VECTOR_F32X4_TRUNC -> f32x4_unop_fallback(x, f -> ExactMath.truncate(f));
            case Bytecode.VECTOR_F32X4_NEAREST -> f32x4_unop_fallback(x, f -> (float) Math.rint(f));
            case Bytecode.VECTOR_F64X2_ABS -> unop(x, F64X2, VectorOperators.ABS);
            case Bytecode.VECTOR_F64X2_NEG -> unop(x, F64X2, VectorOperators.NEG);
            case Bytecode.VECTOR_F64X2_SQRT -> unop(x, F64X2, VectorOperators.SQRT);
            case Bytecode.VECTOR_F64X2_CEIL -> f64x2_floorOrCeil(x, -0.0, 1.0, 1.0);
            case Bytecode.VECTOR_F64X2_FLOOR -> f64x2_floorOrCeil(x, -1.0, 0.0, -1.0);
            case Bytecode.VECTOR_F64X2_TRUNC -> f64x2_unop_fallback(x, ExactMath::truncate);
            case Bytecode.VECTOR_F64X2_NEAREST -> f64x2_unop_fallback(x, Math::rint);
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_S, Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F32X4_S -> convert(x, F32X4, VectorOperators.F2I);
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_U, Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F32X4_U -> i32x4_trunc_sat_f32x4(x);
            case Bytecode.VECTOR_F32X4_CONVERT_I32X4_S -> convert(x, I32X4, VectorOperators.I2F);
            case Bytecode.VECTOR_F32X4_CONVERT_I32X4_U -> f32x4_convert_i32x4_u(x);
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_S_ZERO, Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F64X2_S_ZERO -> convert(x, F64X2, VectorOperators.D2I);
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_U_ZERO, Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F64X2_U_ZERO -> i32x4_trunc_sat_f64x2_zero(x);
            case Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_S -> convert(x, I32X4, VectorOperators.I2D);
            case Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_U -> f64x2_convert_low_i32x4_u(x);
            case Bytecode.VECTOR_F32X4_DEMOTE_F64X2_ZERO -> convert(x, F64X2, VectorOperators.D2F);
            case Bytecode.VECTOR_F64X2_PROMOTE_LOW_F32X4 -> convert(x, F32X4, VectorOperators.F2D);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static final int DOUBLE_SIGNIFICAND_WIDTH = Double.PRECISION;

    public static final int DOUBLE_EXP_BIAS =
            (1 << (Double.SIZE - DOUBLE_SIGNIFICAND_WIDTH - 1)) - 1; // 1023

    private static final long DOUBLE_EXP_BIT_MASK =
                    ((1L << (Double.SIZE - DOUBLE_SIGNIFICAND_WIDTH)) - 1) << (DOUBLE_SIGNIFICAND_WIDTH - 1);

    public static final long DOUBLE_SIGNIF_BIT_MASK = (1L << (DOUBLE_SIGNIFICAND_WIDTH - 1)) - 1;

    private static LongVector getExponent(DoubleVector x) {
        return x.viewAsIntegralLanes().and(DOUBLE_EXP_BIT_MASK).lanewise(VectorOperators.LSHR, DOUBLE_SIGNIFICAND_WIDTH - 1).sub(DOUBLE_EXP_BIAS);
    }

    private static ByteVector f64x2_floorOrCeil(ByteVector xBytes, double negativeBoundary, double positiveBoundary, double sign) {
        DoubleVector x = xBytes.reinterpretAsDoubles();
        LongVector exponent = getExponent(x);
        VectorMask<Double> isNegativeExponent = exponent.lt(0).cast(F64X2.species());
        VectorMask<Double> isZero = x.eq(0);
        VectorMask<Double> isNegative = x.lt(0);
        DoubleVector negativeExponentResult = DoubleVector.broadcast(F64X2.species(), positiveBoundary).blend(DoubleVector.broadcast(F64X2.species(), negativeBoundary), isNegative).blend(x, isZero);
        VectorMask<Double> isHighExponent = exponent.compare(VectorOperators.GE, 52).cast(F64X2.species());
        DoubleVector highExponentResult = x;
        LongVector doppel = x.viewAsIntegralLanes();
        Vector<Long> mask = I64X2.broadcast(DOUBLE_SIGNIF_BIT_MASK).lanewise(VectorOperators.LSHR, exponent);
        VectorMask<Double> isIntegral = doppel.and(mask).eq(0).cast(F64X2.species());
        DoubleVector integralResult = x;
        DoubleVector fractional = doppel.and(mask.neg()).viewAsFloatingLanes();
        VectorMask<Double> signMatch = x.mul(sign).compare(VectorOperators.GT, 0).cast(F64X2.species());
        DoubleVector fractionalResult = fractional.blend(fractional.add(sign), signMatch);
        DoubleVector defaultResult = fractionalResult.blend(integralResult, isIntegral);
        DoubleVector result = defaultResult.blend(highExponentResult, isHighExponent).blend(negativeExponentResult, isNegativeExponent);
        return result.reinterpretAsBytes();
    }

    public static ByteVector binary(ByteVector x, ByteVector y, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I8X16_SWIZZLE, Bytecode.VECTOR_I8X16_RELAXED_SWIZZLE -> i8x16_swizzle(x, y);
            case Bytecode.VECTOR_V128_AND -> binop(x, y, I8X16, VectorOperators.AND);
            case Bytecode.VECTOR_V128_ANDNOT -> binop(x, y, I8X16, VectorOperators.AND_NOT);
            case Bytecode.VECTOR_V128_OR -> binop(x, y, I8X16, VectorOperators.OR);
            case Bytecode.VECTOR_V128_XOR -> binop(x, y, I8X16, VectorOperators.XOR);
            case Bytecode.VECTOR_I8X16_EQ -> relop(x, y, I8X16, VectorOperators.EQ);
            case Bytecode.VECTOR_I8X16_NE -> relop(x, y, I8X16, VectorOperators.NE);
            case Bytecode.VECTOR_I8X16_LT_S -> relop(x, y, I8X16, VectorOperators.LT);
            case Bytecode.VECTOR_I8X16_LT_U -> relop(x, y, I8X16, VectorOperators.ULT);
            case Bytecode.VECTOR_I8X16_GT_S -> relop(x, y, I8X16, VectorOperators.GT);
            case Bytecode.VECTOR_I8X16_GT_U -> relop(x, y, I8X16, VectorOperators.UGT);
            case Bytecode.VECTOR_I8X16_LE_S -> relop(x, y, I8X16, VectorOperators.LE);
            case Bytecode.VECTOR_I8X16_LE_U -> relop(x, y, I8X16, VectorOperators.ULE);
            case Bytecode.VECTOR_I8X16_GE_S -> relop(x, y, I8X16, VectorOperators.GE);
            case Bytecode.VECTOR_I8X16_GE_U -> relop(x, y, I8X16, VectorOperators.UGE);
            case Bytecode.VECTOR_I16X8_EQ -> relop(x, y, I16X8, VectorOperators.EQ);
            case Bytecode.VECTOR_I16X8_NE -> relop(x, y, I16X8, VectorOperators.NE);
            case Bytecode.VECTOR_I16X8_LT_S -> relop(x, y, I16X8, VectorOperators.LT);
            case Bytecode.VECTOR_I16X8_LT_U -> relop(x, y, I16X8, VectorOperators.ULT);
            case Bytecode.VECTOR_I16X8_GT_S -> relop(x, y, I16X8, VectorOperators.GT);
            case Bytecode.VECTOR_I16X8_GT_U -> relop(x, y, I16X8, VectorOperators.UGT);
            case Bytecode.VECTOR_I16X8_LE_S -> relop(x, y, I16X8, VectorOperators.LE);
            case Bytecode.VECTOR_I16X8_LE_U -> relop(x, y, I16X8, VectorOperators.ULE);
            case Bytecode.VECTOR_I16X8_GE_S -> relop(x, y, I16X8, VectorOperators.GE);
            case Bytecode.VECTOR_I16X8_GE_U -> relop(x, y, I16X8, VectorOperators.UGE);
            case Bytecode.VECTOR_I32X4_EQ -> relop(x, y, I32X4, VectorOperators.EQ);
            case Bytecode.VECTOR_I32X4_NE -> relop(x, y, I32X4, VectorOperators.NE);
            case Bytecode.VECTOR_I32X4_LT_S -> relop(x, y, I32X4, VectorOperators.LT);
            case Bytecode.VECTOR_I32X4_LT_U -> relop(x, y, I32X4, VectorOperators.ULT);
            case Bytecode.VECTOR_I32X4_GT_S -> relop(x, y, I32X4, VectorOperators.GT);
            case Bytecode.VECTOR_I32X4_GT_U -> relop(x, y, I32X4, VectorOperators.UGT);
            case Bytecode.VECTOR_I32X4_LE_S -> relop(x, y, I32X4, VectorOperators.LE);
            case Bytecode.VECTOR_I32X4_LE_U -> relop(x, y, I32X4, VectorOperators.ULE);
            case Bytecode.VECTOR_I32X4_GE_S -> relop(x, y, I32X4, VectorOperators.GE);
            case Bytecode.VECTOR_I32X4_GE_U -> relop(x, y, I32X4, VectorOperators.UGE);
            case Bytecode.VECTOR_I64X2_EQ -> relop(x, y, I64X2, VectorOperators.EQ);
            case Bytecode.VECTOR_I64X2_NE -> relop(x, y, I64X2, VectorOperators.NE);
            case Bytecode.VECTOR_I64X2_LT_S -> relop(x, y, I64X2, VectorOperators.LT);
            case Bytecode.VECTOR_I64X2_GT_S -> relop(x, y, I64X2, VectorOperators.GT);
            case Bytecode.VECTOR_I64X2_LE_S -> relop(x, y, I64X2, VectorOperators.LE);
            case Bytecode.VECTOR_I64X2_GE_S -> relop(x, y, I64X2, VectorOperators.GE);
            case Bytecode.VECTOR_F32X4_EQ -> f32x4_relop(x, y, VectorOperators.EQ);
            case Bytecode.VECTOR_F32X4_NE -> f32x4_relop(x, y, VectorOperators.NE);
            case Bytecode.VECTOR_F32X4_LT -> f32x4_relop(x, y, VectorOperators.LT);
            case Bytecode.VECTOR_F32X4_GT -> f32x4_relop(x, y, VectorOperators.GT);
            case Bytecode.VECTOR_F32X4_LE -> f32x4_relop(x, y, VectorOperators.LE);
            case Bytecode.VECTOR_F32X4_GE -> f32x4_relop(x, y, VectorOperators.GE);
            case Bytecode.VECTOR_F64X2_EQ -> f64x2_relop(x, y, VectorOperators.EQ);
            case Bytecode.VECTOR_F64X2_NE -> f64x2_relop(x, y, VectorOperators.NE);
            case Bytecode.VECTOR_F64X2_LT -> f64x2_relop(x, y, VectorOperators.LT);
            case Bytecode.VECTOR_F64X2_GT -> f64x2_relop(x, y, VectorOperators.GT);
            case Bytecode.VECTOR_F64X2_LE -> f64x2_relop(x, y, VectorOperators.LE);
            case Bytecode.VECTOR_F64X2_GE -> f64x2_relop(x, y, VectorOperators.GE);
            case Bytecode.VECTOR_I8X16_NARROW_I16X8_S -> narrow(x, y, I16X8, VectorOperators.S2B, Byte.MIN_VALUE, Byte.MAX_VALUE);
            case Bytecode.VECTOR_I8X16_NARROW_I16X8_U -> narrow(x, y, I16X8, VectorOperators.S2B, 0, 0xff);
            case Bytecode.VECTOR_I8X16_ADD -> binop(x, y, I8X16, VectorOperators.ADD);
            case Bytecode.VECTOR_I8X16_ADD_SAT_S -> binop(x, y, I8X16, VectorOperators.SADD);
            case Bytecode.VECTOR_I8X16_ADD_SAT_U -> binop_sat_u(x, y, I8X16, VectorOperators.ZERO_EXTEND_B2S, VectorOperators.S2B, VectorOperators.ADD, 0, 0xff);
            case Bytecode.VECTOR_I8X16_SUB -> binop(x, y, I8X16, VectorOperators.SUB);
            case Bytecode.VECTOR_I8X16_SUB_SAT_S -> binop(x, y, I8X16, VectorOperators.SSUB);
            case Bytecode.VECTOR_I8X16_SUB_SAT_U -> binop_sat_u(x, y, I8X16, VectorOperators.ZERO_EXTEND_B2S, VectorOperators.S2B, VectorOperators.SUB, 0, 0xff);
            case Bytecode.VECTOR_I8X16_MIN_S -> binop(x, y, I8X16, VectorOperators.MIN);
            case Bytecode.VECTOR_I8X16_MIN_U -> binop(x, y, I8X16, VectorOperators.UMIN);
            case Bytecode.VECTOR_I8X16_MAX_S -> binop(x, y, I8X16, VectorOperators.MAX);
            case Bytecode.VECTOR_I8X16_MAX_U -> binop(x, y, I8X16, VectorOperators.UMAX);
            case Bytecode.VECTOR_I8X16_AVGR_U -> avgr(x, y, I8X16, VectorOperators.ZERO_EXTEND_B2S, VectorOperators.S2B);
            case Bytecode.VECTOR_I16X8_NARROW_I32X4_S -> narrow(x, y, I32X4, VectorOperators.I2S, Short.MIN_VALUE, Short.MAX_VALUE);
            case Bytecode.VECTOR_I16X8_NARROW_I32X4_U -> narrow(x, y, I32X4, VectorOperators.I2S, 0, 0xffff);
            case Bytecode.VECTOR_I16X8_Q15MULR_SAT_S, Bytecode.VECTOR_I16X8_RELAXED_Q15MULR_S -> i16x8_q15mulr_sat_s(x, y);
            case Bytecode.VECTOR_I16X8_ADD -> binop(x, y, I16X8, VectorOperators.ADD);
            case Bytecode.VECTOR_I16X8_ADD_SAT_S -> binop(x, y, I16X8, VectorOperators.SADD);
            case Bytecode.VECTOR_I16X8_ADD_SAT_U -> binop_sat_u(x, y, I16X8, VectorOperators.ZERO_EXTEND_S2I, VectorOperators.I2S, VectorOperators.ADD, 0, 0xffff);
            case Bytecode.VECTOR_I16X8_SUB -> binop(x, y, I16X8, VectorOperators.SUB);
            case Bytecode.VECTOR_I16X8_SUB_SAT_S -> binop(x, y, I16X8, VectorOperators.SSUB);
            case Bytecode.VECTOR_I16X8_SUB_SAT_U -> binop_sat_u(x, y, I16X8, VectorOperators.ZERO_EXTEND_S2I, VectorOperators.I2S, VectorOperators.SUB, 0, 0xffff);
            case Bytecode.VECTOR_I16X8_MUL -> binop(x, y, I16X8, VectorOperators.MUL);
            case Bytecode.VECTOR_I16X8_MIN_S -> binop(x, y, I16X8, VectorOperators.MIN);
            case Bytecode.VECTOR_I16X8_MIN_U -> binop(x, y, I16X8, VectorOperators.UMIN);
            case Bytecode.VECTOR_I16X8_MAX_S -> binop(x, y, I16X8, VectorOperators.MAX);
            case Bytecode.VECTOR_I16X8_MAX_U -> binop(x, y, I16X8, VectorOperators.UMAX);
            case Bytecode.VECTOR_I16X8_AVGR_U -> avgr(x, y, I16X8, VectorOperators.ZERO_EXTEND_S2I, VectorOperators.I2S);
            case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_S -> extmul(x, y, I8X16, VectorOperators.B2S, 0);
            case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_U -> extmul(x, y, I8X16, VectorOperators.ZERO_EXTEND_B2S, 0);
            case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_S -> extmul(x, y, I8X16, VectorOperators.B2S, 1);
            case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_U -> extmul(x, y, I8X16, VectorOperators.ZERO_EXTEND_B2S, 1);
            case Bytecode.VECTOR_I32X4_ADD -> binop(x, y, I32X4, VectorOperators.ADD);
            case Bytecode.VECTOR_I32X4_SUB -> binop(x, y, I32X4, VectorOperators.SUB);
            case Bytecode.VECTOR_I32X4_MUL -> binop(x, y, I32X4, VectorOperators.MUL);
            case Bytecode.VECTOR_I32X4_MIN_S -> binop(x, y, I32X4, VectorOperators.MIN);
            case Bytecode.VECTOR_I32X4_MIN_U -> binop(x, y, I32X4, VectorOperators.UMIN);
            case Bytecode.VECTOR_I32X4_MAX_S -> binop(x, y, I32X4, VectorOperators.MAX);
            case Bytecode.VECTOR_I32X4_MAX_U -> binop(x, y, I32X4, VectorOperators.UMAX);
            case Bytecode.VECTOR_I32X4_DOT_I16X8_S -> i32x4_dot_i16x8_s(x, y);
            case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_S -> extmul(x, y, I16X8, VectorOperators.S2I, 0);
            case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_U -> extmul(x, y, I16X8, VectorOperators.ZERO_EXTEND_S2I, 0);
            case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_S -> extmul(x, y, I16X8, VectorOperators.S2I, 1);
            case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_U -> extmul(x, y, I16X8, VectorOperators.ZERO_EXTEND_S2I, 1);
            case Bytecode.VECTOR_I64X2_ADD -> binop(x, y, I64X2, VectorOperators.ADD);
            case Bytecode.VECTOR_I64X2_SUB -> binop(x, y, I64X2, VectorOperators.SUB);
            case Bytecode.VECTOR_I64X2_MUL -> binop(x, y, I64X2, VectorOperators.MUL);
            case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_S -> extmul(x, y, I32X4, VectorOperators.I2L, 0);
            case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_U -> extmul(x, y, I32X4, VectorOperators.ZERO_EXTEND_I2L, 0);
            case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_S -> extmul(x, y, I32X4, VectorOperators.I2L, 1);
            case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_U -> extmul(x, y, I32X4, VectorOperators.ZERO_EXTEND_I2L, 1);
            case Bytecode.VECTOR_F32X4_ADD -> binop(x, y, F32X4, VectorOperators.ADD);
            case Bytecode.VECTOR_F32X4_SUB -> binop(x, y, F32X4, VectorOperators.SUB);
            case Bytecode.VECTOR_F32X4_MUL -> binop(x, y, F32X4, VectorOperators.MUL);
            case Bytecode.VECTOR_F32X4_DIV -> binop(x, y, F32X4, VectorOperators.DIV);
            case Bytecode.VECTOR_F32X4_MIN, Bytecode.VECTOR_F32X4_RELAXED_MIN -> binop(x, y, F32X4, VectorOperators.MIN);
            case Bytecode.VECTOR_F32X4_MAX, Bytecode.VECTOR_F32X4_RELAXED_MAX -> binop(x, y, F32X4, VectorOperators.MAX);
            case Bytecode.VECTOR_F32X4_PMIN -> pmin(x, y, F32X4);
            case Bytecode.VECTOR_F32X4_PMAX -> pmax(x, y, F32X4);
            case Bytecode.VECTOR_F64X2_ADD -> binop(x, y, F64X2, VectorOperators.ADD);
            case Bytecode.VECTOR_F64X2_SUB -> binop(x, y, F64X2, VectorOperators.SUB);
            case Bytecode.VECTOR_F64X2_MUL -> binop(x, y, F64X2, VectorOperators.MUL);
            case Bytecode.VECTOR_F64X2_DIV -> binop(x, y, F64X2, VectorOperators.DIV);
            case Bytecode.VECTOR_F64X2_MIN, Bytecode.VECTOR_F64X2_RELAXED_MIN -> binop(x, y, F64X2, VectorOperators.MIN);
            case Bytecode.VECTOR_F64X2_MAX, Bytecode.VECTOR_F64X2_RELAXED_MAX -> binop(x, y, F64X2, VectorOperators.MAX);
            case Bytecode.VECTOR_F64X2_PMIN -> pmin(x, y, F64X2);
            case Bytecode.VECTOR_F64X2_PMAX -> pmax(x, y, F64X2);
            case Bytecode.VECTOR_I16X8_RELAXED_DOT_I8X16_I7X16_S -> i16x8_relaxed_dot_i8x16_i7x16_s(x, y);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static ByteVector ternary(ByteVector x, ByteVector y, ByteVector z, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_BITSELECT, Bytecode.VECTOR_I8X16_RELAXED_LANESELECT, Bytecode.VECTOR_I16X8_RELAXED_LANESELECT, Bytecode.VECTOR_I32X4_RELAXED_LANESELECT,
                            Bytecode.VECTOR_I64X2_RELAXED_LANESELECT ->
                bitselect(x, y, z);
            case Bytecode.VECTOR_F32X4_RELAXED_MADD, Bytecode.VECTOR_F32X4_RELAXED_NMADD -> f32x4_ternop(x, y, z, vectorOpcode);
            case Bytecode.VECTOR_F64X2_RELAXED_MADD, Bytecode.VECTOR_F64X2_RELAXED_NMADD -> f64x2_ternop(x, y, z, vectorOpcode);
            case Bytecode.VECTOR_I32X4_RELAXED_DOT_I8X16_I7X16_ADD_S -> i32x4_relaxed_dot_i8x16_i7x16_add_s(x, y, z);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static int vectorToInt(ByteVector x, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_ANY_TRUE -> v128_any_true(x);
            case Bytecode.VECTOR_I8X16_ALL_TRUE -> all_true(x, I8X16);
            case Bytecode.VECTOR_I8X16_BITMASK -> bitmask(x, I8X16);
            case Bytecode.VECTOR_I16X8_ALL_TRUE -> all_true(x, I16X8);
            case Bytecode.VECTOR_I16X8_BITMASK -> bitmask(x, I16X8);
            case Bytecode.VECTOR_I32X4_ALL_TRUE -> all_true(x, I32X4);
            case Bytecode.VECTOR_I32X4_BITMASK -> bitmask(x, I32X4);
            case Bytecode.VECTOR_I64X2_ALL_TRUE -> all_true(x, I64X2);
            case Bytecode.VECTOR_I64X2_BITMASK -> bitmask(x, I64X2);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static ByteVector shift(ByteVector x, int shift, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I8X16_SHL -> shiftop(x, (byte) shift, I8X16, VectorOperators.LSHL);
            case Bytecode.VECTOR_I8X16_SHR_S -> shiftop(x, (byte) shift, I8X16, VectorOperators.ASHR);
            case Bytecode.VECTOR_I8X16_SHR_U -> shiftop(x, (byte) shift, I8X16, VectorOperators.LSHR);
            case Bytecode.VECTOR_I16X8_SHL -> shiftop(x, (short) shift, I16X8, VectorOperators.LSHL);
            case Bytecode.VECTOR_I16X8_SHR_S -> shiftop(x, (short) shift, I16X8, VectorOperators.ASHR);
            case Bytecode.VECTOR_I16X8_SHR_U -> shiftop(x, (short) shift, I16X8, VectorOperators.LSHR);
            case Bytecode.VECTOR_I32X4_SHL -> shiftop(x, shift, I32X4, VectorOperators.LSHL);
            case Bytecode.VECTOR_I32X4_SHR_S -> shiftop(x, shift, I32X4, VectorOperators.ASHR);
            case Bytecode.VECTOR_I32X4_SHR_U -> shiftop(x, shift, I32X4, VectorOperators.LSHR);
            case Bytecode.VECTOR_I64X2_SHL -> shiftop(x, shift, I64X2, VectorOperators.LSHL);
            case Bytecode.VECTOR_I64X2_SHR_S -> shiftop(x, shift, I64X2, VectorOperators.ASHR);
            case Bytecode.VECTOR_I64X2_SHR_U -> shiftop(x, shift, I64X2, VectorOperators.LSHR);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    // Checkstyle: stop method name check

    public static ByteVector i8x16_shuffle(ByteVector x, ByteVector y, ByteVector indices) {
        VectorShuffle<Byte> shuffle = indices.add((byte) (-2 * BYTES), indices.lt((byte) BYTES).not()).toShuffle();
        return x.rearrange(shuffle, y);
    }

    public static int i8x16_extract_lane(ByteVector vec, int laneIndex, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I8X16_EXTRACT_LANE_S -> vec.lane(laneIndex);
            case Bytecode.VECTOR_I8X16_EXTRACT_LANE_U -> Byte.toUnsignedInt(vec.lane(laneIndex));
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static ByteVector i8x16_replace_lane(ByteVector vec, int laneIndex, byte value) {
        return vec.withLane(laneIndex, value);
    }

    public static int i16x8_extract_lane(ByteVector vecBytes, int laneIndex, int vectorOpcode) {
        ShortVector vec = vecBytes.reinterpretAsShorts();
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I16X8_EXTRACT_LANE_S -> vec.lane(laneIndex);
            case Bytecode.VECTOR_I16X8_EXTRACT_LANE_U -> Short.toUnsignedInt(vec.lane(laneIndex));
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static ByteVector i16x8_replace_lane(ByteVector vecBytes, int laneIndex, short value) {
        ShortVector vec = vecBytes.reinterpretAsShorts();
        return vec.withLane(laneIndex, value).reinterpretAsBytes();
    }

    public static int i32x4_extract_lane(ByteVector vecBytes, int laneIndex) {
        IntVector vec = vecBytes.reinterpretAsInts();
        return vec.lane(laneIndex);
    }

    public static ByteVector i32x4_replace_lane(ByteVector vecBytes, int laneIndex, int value) {
        IntVector vec = vecBytes.reinterpretAsInts();
        return vec.withLane(laneIndex, value).reinterpretAsBytes();
    }

    public static long i64x2_extract_lane(ByteVector vecBytes, int laneIndex) {
        LongVector vec = vecBytes.reinterpretAsLongs();
        return vec.lane(laneIndex);
    }

    public static ByteVector i64x2_replace_lane(ByteVector vecBytes, int laneIndex, long value) {
        LongVector vec = vecBytes.reinterpretAsLongs();
        return vec.withLane(laneIndex, value).reinterpretAsBytes();
    }

    public static float f32x4_extract_lane(ByteVector vecBytes, int laneIndex) {
        FloatVector vec = vecBytes.reinterpretAsFloats();
        return vec.lane(laneIndex);
    }

    public static ByteVector f32x4_replace_lane(ByteVector vecBytes, int laneIndex, float value) {
        FloatVector vec = vecBytes.reinterpretAsFloats();
        return vec.withLane(laneIndex, value).reinterpretAsBytes();
    }

    public static double f64x2_extract_lane(ByteVector vecBytes, int laneIndex) {
        DoubleVector vec = vecBytes.reinterpretAsDoubles();
        return vec.lane(laneIndex);
    }

    public static ByteVector f64x2_replace_lane(ByteVector vecBytes, int laneIndex, double value) {
        DoubleVector vec = vecBytes.reinterpretAsDoubles();
        return vec.withLane(laneIndex, value).reinterpretAsBytes();
    }

    private static <E> ByteVector unop(ByteVector xBytes, Shape<E> shape, VectorOperators.Unary op) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> result = x.lanewise(op);
        return result.reinterpretAsBytes();
    }

    private static <E, F> ByteVector extadd_pairwise(ByteVector xBytes, Shape<E> shape, VectorOperators.Conversion<E, F> conv) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<F> evens = x.compress(evens(shape)).convert(conv, 0);
        Vector<F> odds = x.compress(odds(shape)).convert(conv, 0);
        Vector<F> result = evens.add(odds);
        return result.reinterpretAsBytes();
    }

    private static <E, F> ByteVector extend(ByteVector xBytes, int part, Shape<E> shape, VectorOperators.Conversion<E, F> conv) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<F> result = x.convert(conv, part);
        return result.reinterpretAsBytes();
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static ByteVector f32x4_unop_fallback(ByteVector xBytes, UnaryScalarOp<Float> op) {
        FloatVector x = xBytes.reinterpretAsFloats();
        float[] xArray = x.toArray();
        for (int i = 0; i < xArray.length; i++) {
            xArray[i] = op.apply(xArray[i]);
        }
        return fromArray(xArray);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static ByteVector f64x2_unop_fallback(ByteVector xBytes, UnaryScalarOp<Double> op) {
        DoubleVector x = xBytes.reinterpretAsDoubles();
        double[] xArray = x.toArray();
        for (int i = 0; i < xArray.length; i++) {
            xArray[i] = op.apply(xArray[i]);
        }
        return fromArray(xArray);
    }

    private static <E, F> ByteVector convert(ByteVector xBytes, Shape<E> shape, VectorOperators.Conversion<E, F> conv) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<F> result = x.convert(conv, 0);
        return result.reinterpretAsBytes();
    }

    private static ByteVector i32x4_trunc_sat_f32x4(ByteVector xBytes) {
        FloatVector x = xBytes.reinterpretAsFloats();
        Vector<Double> xLow = x.convert(VectorOperators.F2D, 0);
        Vector<Double> xHigh = x.convert(VectorOperators.F2D, 1);
        Vector<Integer> resultLow = truncSatU32(xLow).convert(VectorOperators.L2I, 0);
        Vector<Integer> resultHigh = truncSatU32(xHigh).convert(VectorOperators.L2I, -1);
        Vector<Integer> result = firstNonzero(resultLow, resultHigh);
        return result.reinterpretAsBytes();
    }

    private static ByteVector f32x4_convert_i32x4_u(ByteVector xBytes) {
        IntVector x = xBytes.reinterpretAsInts();
        LongVector xUnsignedLow = castLong128(x.convert(VectorOperators.ZERO_EXTEND_I2L, 0));
        LongVector xUnsignedHigh = castLong128(x.convert(VectorOperators.ZERO_EXTEND_I2L, 1));
        FloatVector resultLow = castFloat128(xUnsignedLow.convert(VectorOperators.L2F, 0));
        FloatVector resultHigh = castFloat128(xUnsignedHigh.convert(VectorOperators.L2F, -1));
        Vector<Float> result = firstNonzero(resultLow, resultHigh);
        return result.reinterpretAsBytes();
    }

    private static ByteVector i32x4_trunc_sat_f64x2_zero(ByteVector xBytes) {
        DoubleVector x = xBytes.reinterpretAsDoubles();
        Vector<Long> longResult = truncSatU32(x);
        Vector<Integer> result = longResult.convert(VectorOperators.L2I, 0);
        return result.reinterpretAsBytes();
    }

    private static ByteVector f64x2_convert_low_i32x4_u(ByteVector xBytes) {
        IntVector x = xBytes.reinterpretAsInts();
        Vector<Long> xUnsignedLow = x.convert(VectorOperators.ZERO_EXTEND_I2L, 0);
        Vector<Double> result = xUnsignedLow.convert(VectorOperators.L2D, 0);
        return result.reinterpretAsBytes();
    }

    private static ByteVector i8x16_swizzle(ByteVector valueBytes, ByteVector indexBytes) {
        ByteVector values = valueBytes;
        ByteVector indices = indexBytes;
        VectorMask<Byte> safeIndices = indices.lt((byte) 0).or(indices.lt((byte) BYTES).not()).not();
        return values.rearrange(indices.toShuffle(), safeIndices);
    }

    private static <E> ByteVector binop(ByteVector xBytes, ByteVector yBytes, Shape<E> shape, VectorOperators.Binary op) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> y = shape.reinterpret(yBytes);
        Vector<E> result = x.lanewise(op, y);
        return result.reinterpretAsBytes();
    }

    private static <E> ByteVector relop(ByteVector xBytes, ByteVector yBytes, Shape<E> shape, VectorOperators.Comparison comp) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> y = shape.reinterpret(yBytes);
        Vector<E> result = x.compare(comp, y).toVector();
        return result.reinterpretAsBytes();
    }

    private static ByteVector f32x4_relop(ByteVector xBytes, ByteVector yBytes, VectorOperators.Comparison comp) {
        FloatVector x = xBytes.reinterpretAsFloats();
        FloatVector y = yBytes.reinterpretAsFloats();
        Vector<Integer> zero = I32X4.zero();
        Vector<Integer> minusOne = I32X4.broadcast(-1);
        Vector<Integer> result = zero.blend(minusOne, x.compare(comp, y).cast(I32X4.species()));
        return result.reinterpretAsBytes();
    }

    private static ByteVector f64x2_relop(ByteVector xBytes, ByteVector yBytes, VectorOperators.Comparison comp) {
        DoubleVector x = xBytes.reinterpretAsDoubles();
        DoubleVector y = yBytes.reinterpretAsDoubles();
        Vector<Long> zero = I64X2.zero();
        Vector<Long> minusOne = I64X2.broadcast(-1);
        Vector<Long> result = zero.blend(minusOne, x.compare(comp, y).cast(I64X2.species()));
        return result.reinterpretAsBytes();
    }

    private static <E, F> ByteVector narrow(ByteVector xBytes, ByteVector yBytes, Shape<E> shape, VectorOperators.Conversion<E, F> conv, long min, long max) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> y = shape.reinterpret(yBytes);
        Vector<E> xSat = sat(x, min, max);
        Vector<E> ySat = sat(y, min, max);
        Vector<F> resultLow = xSat.convert(conv, 0);
        Vector<F> resultHigh = ySat.convert(conv, -1);
        Vector<F> result = firstNonzero(resultLow, resultHigh);
        return result.reinterpretAsBytes();
    }

    private static <E, F> ByteVector binop_sat_u(ByteVector xBytes, ByteVector yBytes, Shape<E> shape, VectorOperators.Conversion<E, F> upcast, VectorOperators.Conversion<F, E> downcast,
                    VectorOperators.Binary op, long min, long max) {
        return upcastBinopDowncast(xBytes, yBytes, shape, upcast, downcast, (x, y) -> {
            Vector<F> rawResult = x.lanewise(op, y);
            Vector<F> satResult = sat(rawResult, min, max);
            return satResult;
        });
    }

    private static <E, F> ByteVector avgr(ByteVector xBytes, ByteVector yBytes, Shape<E> shape, VectorOperators.Conversion<E, F> upcast, VectorOperators.Conversion<F, E> downcast) {
        Vector<F> one = VectorShape.S_128_BIT.withLanes(upcast.rangeType()).broadcast(1);
        Vector<F> two = VectorShape.S_128_BIT.withLanes(upcast.rangeType()).broadcast(2);
        return upcastBinopDowncast(xBytes, yBytes, shape, upcast, downcast, (x, y) -> x.add(y).add(one).div(two));
    }

    private static ByteVector i16x8_q15mulr_sat_s(ByteVector xBytes, ByteVector yBytes) {
        return upcastBinopDowncast(xBytes, yBytes, I16X8, VectorOperators.S2I, VectorOperators.I2S, (x, y) -> {
            Vector<Integer> rawResult = x.mul(y).add(I32X4.broadcast(1 << 14)).lanewise(VectorOperators.ASHR, I32X4.broadcast(15));
            Vector<Integer> satResult = sat(rawResult, Short.MIN_VALUE, Short.MAX_VALUE);
            return satResult;
        });
    }

    private static <E, F> ByteVector extmul(ByteVector xBytes, ByteVector yBytes, Shape<E> shape, VectorOperators.Conversion<E, F> extend, int part) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> y = shape.reinterpret(yBytes);
        Vector<F> xExtended = x.convert(extend, part);
        Vector<F> yExtended = y.convert(extend, part);
        Vector<F> result = xExtended.mul(yExtended);
        return result.reinterpretAsBytes();
    }

    private static ByteVector i32x4_dot_i16x8_s(ByteVector xBytes, ByteVector yBytes) {
        ShortVector x = xBytes.reinterpretAsShorts();
        ShortVector y = yBytes.reinterpretAsShorts();
        Vector<Integer> xEvens = x.compress(evens(I16X8)).convert(VectorOperators.S2I, 0);
        Vector<Integer> xOdds = x.compress(odds(I16X8)).convert(VectorOperators.S2I, 0);
        Vector<Integer> yEvens = y.compress(evens(I16X8)).convert(VectorOperators.S2I, 0);
        Vector<Integer> yOdds = y.compress(odds(I16X8)).convert(VectorOperators.S2I, 0);
        Vector<Integer> xMulYEvens = xEvens.mul(yEvens);
        Vector<Integer> xMulYOdds = xOdds.mul(yOdds);
        Vector<Integer> dot = xMulYEvens.lanewise(VectorOperators.ADD, xMulYOdds);
        return dot.reinterpretAsBytes();
    }

    private static <E> ByteVector pmin(ByteVector xBytes, ByteVector yBytes, Shape<E> shape) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> y = shape.reinterpret(yBytes);
        Vector<E> result = x.blend(y, y.compare(VectorOperators.LT, x));
        return result.reinterpretAsBytes();
    }

    private static <E> ByteVector pmax(ByteVector xBytes, ByteVector yBytes, Shape<E> shape) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> y = shape.reinterpret(yBytes);
        Vector<E> result = x.blend(y, x.compare(VectorOperators.LT, y));
        return result.reinterpretAsBytes();
    }

    private static ByteVector i16x8_relaxed_dot_i8x16_i7x16_s(ByteVector x, ByteVector y) {
        Vector<Short> xEvens = x.compress(evens(I8X16)).convert(VectorOperators.B2S, 0);
        Vector<Short> xOdds = x.compress(odds(I8X16)).convert(VectorOperators.B2S, 0);
        Vector<Short> yEvens = y.compress(evens(I8X16)).convert(VectorOperators.B2S, 0);
        Vector<Short> yOdds = y.compress(odds(I8X16)).convert(VectorOperators.B2S, 0);
        Vector<Short> xMulYEvens = xEvens.mul(yEvens);
        Vector<Short> xMulYOdds = xOdds.mul(yOdds);
        Vector<Short> dot = xMulYEvens.lanewise(VectorOperators.SADD, xMulYOdds);
        return dot.reinterpretAsBytes();
    }

    private static ByteVector bitselect(ByteVector x, ByteVector y, ByteVector mask) {
        // y.bitwiseBlend(x, mask) would work too, but it doesn't play nice with native image
        // and ends up expanding to the bottom pattern anyway
        return y.lanewise(VectorOperators.XOR, y.lanewise(VectorOperators.XOR, x).lanewise(VectorOperators.AND, mask));
    }

    private static ByteVector f32x4_ternop(ByteVector xBytes, ByteVector yBytes, ByteVector zBytes, int vectorOpcode) {
        FloatVector x = xBytes.reinterpretAsFloats();
        FloatVector y = yBytes.reinterpretAsFloats();
        FloatVector z = zBytes.reinterpretAsFloats();
        FloatVector result = switch (vectorOpcode) {
            case Bytecode.VECTOR_F32X4_RELAXED_MADD -> x.lanewise(VectorOperators.FMA, y, z);
            case Bytecode.VECTOR_F32X4_RELAXED_NMADD -> x.neg().lanewise(VectorOperators.FMA, y, z);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
        return result.reinterpretAsBytes();
    }

    private static ByteVector f64x2_ternop(ByteVector xBytes, ByteVector yBytes, ByteVector zBytes, int vectorOpcode) {
        DoubleVector x = xBytes.reinterpretAsDoubles();
        DoubleVector y = yBytes.reinterpretAsDoubles();
        DoubleVector z = zBytes.reinterpretAsDoubles();
        DoubleVector result = switch (vectorOpcode) {
            case Bytecode.VECTOR_F64X2_RELAXED_MADD -> x.lanewise(VectorOperators.FMA, y, z);
            case Bytecode.VECTOR_F64X2_RELAXED_NMADD -> x.neg().lanewise(VectorOperators.FMA, y, z);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
        return result.reinterpretAsBytes();
    }

    private static ByteVector i32x4_relaxed_dot_i8x16_i7x16_add_s(ByteVector x, ByteVector y, ByteVector zBytes) {
        IntVector z = zBytes.reinterpretAsInts();
        ShortVector xEvens = castShort128(x.compress(evens(I8X16)).convert(VectorOperators.B2S, 0));
        ShortVector xOdds = castShort128(x.compress(odds(I8X16)).convert(VectorOperators.B2S, 0));
        ShortVector yEvens = castShort128(y.compress(evens(I8X16)).convert(VectorOperators.B2S, 0));
        ShortVector yOdds = castShort128(y.compress(odds(I8X16)).convert(VectorOperators.B2S, 0));
        ShortVector xMulYEvens = xEvens.mul(yEvens);
        ShortVector xMulYOdds = xOdds.mul(yOdds);
        ShortVector dot = xMulYEvens.lanewise(VectorOperators.SADD, xMulYOdds);
        IntVector dotEvens = castInt128(dot.compress(evens(I16X8)).convert(VectorOperators.S2I, 0));
        IntVector dotOdds = castInt128(dot.compress(odds(I16X8)).convert(VectorOperators.S2I, 0));
        IntVector dots = dotEvens.add(dotOdds);
        IntVector result = dots.add(z);
        return result.reinterpretAsBytes();
    }

    private static int v128_any_true(ByteVector vec) {
        return vec.eq((byte) 0).allTrue() ? 0 : 1;
    }

    private static <E> int all_true(ByteVector vecBytes, Shape<E> shape) {
        Vector<E> vec = shape.reinterpret(vecBytes);
        return vec.eq(shape.zero()).anyTrue() ? 0 : 1;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static <E> int bitmask(ByteVector vecBytes, Shape<E> shape) {
        Vector<E> vec = shape.reinterpret(vecBytes);
        VectorMask<E> mask = vec.lt(shape.zero());
        int bitmask = 0;
        for (int i = 0; i < mask.length(); i++) {
            if (mask.laneIsSet(i)) {
                bitmask |= 1 << i;
            }
        }
        return bitmask;
    }

    private static <E> ByteVector shiftop(ByteVector xBytes, int shift, Shape<E> shape, VectorOperators.Binary shiftOp) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> result = x.lanewise(shiftOp, shift);
        return result.reinterpretAsBytes();
    }

    // Checkstyle: resume method name check

    private static final Class<? extends ByteVector> BYTE_128_CLASS = ByteVector.zero(I8X16.species()).getClass();
    private static final Class<? extends ShortVector> SHORT_128_CLASS = ShortVector.zero(I16X8.species()).getClass();
    private static final Class<? extends IntVector> INT_128_CLASS = IntVector.zero(I32X4.species()).getClass();
    private static final Class<? extends LongVector> LONG_128_CLASS = LongVector.zero(I64X2.species()).getClass();
    private static final Class<? extends FloatVector> FLOAT_128_CLASS = FloatVector.zero(F32X4.species()).getClass();
    private static final Class<? extends DoubleVector> DOUBLE_128_CLASS = DoubleVector.zero(F64X2.species()).getClass();

    public static final ByteVector castByte128(Vector<Byte> vec) {
        return BYTE_128_CLASS.cast(vec);
    }

    private static final ShortVector castShort128(Vector<Short> vec) {
        return SHORT_128_CLASS.cast(vec);
    }

    private static final IntVector castInt128(Vector<Integer> vec) {
        return INT_128_CLASS.cast(vec);
    }

    private static final LongVector castLong128(Vector<Long> vec) {
        return LONG_128_CLASS.cast(vec);
    }

    private static final FloatVector castFloat128(Vector<Float> vec) {
        return FLOAT_128_CLASS.cast(vec);
    }

    private static final DoubleVector castDouble128(Vector<Double> vec) {
        return DOUBLE_128_CLASS.cast(vec);
    }

    private static <E> Vector<E> sat(Vector<E> vec, long min, long max) {
        Vector<E> vMin = VectorShape.S_128_BIT.withLanes(vec.elementType()).broadcast(min);
        Vector<E> vMax = VectorShape.S_128_BIT.withLanes(vec.elementType()).broadcast(max);
        return vec.max(vMin).min(vMax);
    }

    private static Vector<Long> truncSatU32(Vector<Double> x) {
        VectorMask<Long> underflow = x.test(VectorOperators.IS_NAN).or(x.test(VectorOperators.IS_NEGATIVE)).cast(I64X2.species());
        VectorMask<Long> overflow = x.compare(VectorOperators.GT, 0xffff_ffffL).cast(I64X2.species());
        Vector<Long> zero = I64X2.zero();
        Vector<Long> u32max = I64X2.broadcast(0xffff_ffffL);
        Vector<Long> trunc = x.convert(VectorOperators.D2L, 0);
        return trunc.blend(u32max, overflow).blend(zero, underflow);
    }

    private static <E, F> ByteVector upcastBinopDowncast(ByteVector xBytes, ByteVector yBytes, Shape<E> shape, VectorOperators.Conversion<E, F> upcast, VectorOperators.Conversion<F, E> downcast,
                    BinaryVectorOp<F> op) {
        Vector<E> x = shape.reinterpret(xBytes);
        Vector<E> y = shape.reinterpret(yBytes);
        Vector<F> xLow = x.convert(upcast, 0);
        Vector<F> xHigh = x.convert(upcast, 1);
        Vector<F> yLow = y.convert(upcast, 0);
        Vector<F> yHigh = y.convert(upcast, 1);
        Vector<E> resultLow = op.apply(xLow, yLow).convert(downcast, 0);
        Vector<E> resultHigh = op.apply(xHigh, yHigh).convert(downcast, -1);
        Vector<E> result = firstNonzero(resultLow, resultHigh);
        return result.reinterpretAsBytes();
    }

    private static final boolean[] ALTERNATING_BITS;

    static {
        ALTERNATING_BITS = new boolean[I8X16.species().length() + 1];
        for (int i = 0; i < ALTERNATING_BITS.length; i++) {
            ALTERNATING_BITS[i] = i % 2 == 0;
        }
    }

    private static <E> VectorMask<E> evens(Shape<E> shape) {
        return VectorMask.fromArray(shape.species(), ALTERNATING_BITS, 0);
    }

    private static <E> VectorMask<E> odds(Shape<E> shape) {
        return VectorMask.fromArray(shape.species(), ALTERNATING_BITS, 1);
    }

    private static <E> Vector<E> firstNonzero(Vector<E> x, Vector<E> y) {
        // Use this definition instead of the FIRST_NONZERO operators, because the FIRST_NONZERO
        // operator is not compatible with native image
        VectorMask<?> mask = x.viewAsIntegralLanes().compare(VectorOperators.EQ, 0);
        return x.blend(y, mask.cast(x.species()));
    }

    public static ByteVector fromArray(byte[] bytes) {
        return fromArray(bytes, 0);
    }

    public static ByteVector fromArray(byte[] bytes, int offset) {
        return ByteVector.fromArray(I8X16.species(), bytes, offset);
    }

    public static ByteVector fromArray(short[] shorts) {
        return ShortVector.fromArray(I16X8.species(), shorts, 0).reinterpretAsBytes();
    }

    public static ByteVector fromArray(int[] ints) {
        return IntVector.fromArray(I32X4.species(), ints, 0).reinterpretAsBytes();
    }

    public static ByteVector fromArray(long[] longs) {
        return LongVector.fromArray(I64X2.species(), longs, 0).reinterpretAsBytes();
    }

    public static ByteVector fromArray(float[] floats) {
        return FloatVector.fromArray(F32X4.species(), floats, 0).reinterpretAsBytes();
    }

    public static ByteVector fromArray(double[] doubles) {
        return DoubleVector.fromArray(F64X2.species(), doubles, 0).reinterpretAsBytes();
    }

    public static ByteVector broadcast(byte value) {
        return ByteVector.broadcast(I8X16.species(), value);
    }

    public static ByteVector broadcast(short value) {
        return ShortVector.broadcast(I16X8.species(), value).reinterpretAsBytes();
    }

    public static ByteVector broadcast(int value) {
        return IntVector.broadcast(I32X4.species(), value).reinterpretAsBytes();
    }

    public static ByteVector broadcast(long value) {
        return LongVector.broadcast(I64X2.species(), value).reinterpretAsBytes();
    }

    public static ByteVector broadcast(float value) {
        return FloatVector.broadcast(F32X4.species(), value).reinterpretAsBytes();
    }

    public static ByteVector broadcast(double value) {
        return DoubleVector.broadcast(F64X2.species(), value).reinterpretAsBytes();
    }

    public static byte[] toArray(ByteVector vec) {
        return vec.toArray();
    }

    public static void intoArray(ByteVector vec, byte[] array, int offset) {
        vec.intoArray(array, offset);
    }
}
