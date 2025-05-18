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
import org.graalvm.wasm.constants.Bytecode;

import java.util.function.BiFunction;
import java.util.function.Function;

import static org.graalvm.wasm.api.Vector128.BYTES;

public class Vector128Ops {

    public static ByteVector unary(ByteVector x, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_NOT -> unop(x, byte.class, VectorOperators.NOT);
            case Bytecode.VECTOR_I8X16_ABS -> unop(x, byte.class, VectorOperators.ABS);
            case Bytecode.VECTOR_I8X16_NEG -> unop(x, byte.class, VectorOperators.NEG);
            case Bytecode.VECTOR_I8X16_POPCNT -> unop(x, byte.class, VectorOperators.BIT_COUNT);
            case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_S -> extadd_pairwise(x, byte.class, VectorOperators.B2S);
            case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_U -> extadd_pairwise(x, byte.class, VectorOperators.ZERO_EXTEND_B2S);
            case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_S -> extend(x, 0, byte.class, VectorOperators.B2S);
            case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_U -> extend(x, 0, byte.class, VectorOperators.ZERO_EXTEND_B2S);
            case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_S -> extend(x, 1, byte.class, VectorOperators.B2S);
            case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_U -> extend(x, 1, byte.class, VectorOperators.ZERO_EXTEND_B2S);
            case Bytecode.VECTOR_I16X8_ABS -> unop(x, short.class, VectorOperators.ABS);
            case Bytecode.VECTOR_I16X8_NEG -> unop(x, short.class, VectorOperators.NEG);
            case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_S -> extadd_pairwise(x, short.class, VectorOperators.S2I);
            case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_U -> extadd_pairwise(x, short.class, VectorOperators.ZERO_EXTEND_S2I);
            case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_S -> extend(x, 0, short.class, VectorOperators.S2I);
            case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_U -> extend(x, 0, short.class, VectorOperators.ZERO_EXTEND_S2I);
            case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_S -> extend(x, 1, short.class, VectorOperators.S2I);
            case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_U -> extend(x, 1, short.class, VectorOperators.ZERO_EXTEND_S2I);
            case Bytecode.VECTOR_I32X4_ABS -> unop(x, int.class, VectorOperators.ABS);
            case Bytecode.VECTOR_I32X4_NEG -> unop(x, int.class, VectorOperators.NEG);
            case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_S -> extend(x, 0, int.class, VectorOperators.I2L);
            case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_U -> extend(x, 0, int.class, VectorOperators.ZERO_EXTEND_I2L);
            case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_S -> extend(x, 1, int.class, VectorOperators.I2L);
            case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_U -> extend(x, 1, int.class, VectorOperators.ZERO_EXTEND_I2L);
            case Bytecode.VECTOR_I64X2_ABS -> unop(x, long.class, VectorOperators.ABS);
            case Bytecode.VECTOR_I64X2_NEG -> unop(x, long.class, VectorOperators.NEG);
            case Bytecode.VECTOR_F32X4_ABS -> unop(x, float.class, VectorOperators.ABS);
            case Bytecode.VECTOR_F32X4_NEG -> unop(x, float.class, VectorOperators.NEG);
            case Bytecode.VECTOR_F32X4_SQRT -> unop(x, float.class, VectorOperators.SQRT);
            case Bytecode.VECTOR_F32X4_CEIL -> f32x4_unop_fallback(x, f -> (float) Math.ceil(f));
            case Bytecode.VECTOR_F32X4_FLOOR -> f32x4_unop_fallback(x, f -> (float) Math.floor(f));
            case Bytecode.VECTOR_F32X4_TRUNC -> f32x4_unop_fallback(x, f -> ExactMath.truncate(f));
            case Bytecode.VECTOR_F32X4_NEAREST -> f32x4_unop_fallback(x, f -> (float) Math.rint(f));
            case Bytecode.VECTOR_F64X2_ABS -> unop(x, double.class, VectorOperators.ABS);
            case Bytecode.VECTOR_F64X2_NEG -> unop(x, double.class, VectorOperators.NEG);
            case Bytecode.VECTOR_F64X2_SQRT -> unop(x, double.class, VectorOperators.SQRT);
            case Bytecode.VECTOR_F64X2_CEIL -> f64x2_unop_fallback(x, Math::ceil);
            case Bytecode.VECTOR_F64X2_FLOOR -> f64x2_unop_fallback(x, Math::floor);
            case Bytecode.VECTOR_F64X2_TRUNC -> f64x2_unop_fallback(x, ExactMath::truncate);
            case Bytecode.VECTOR_F64X2_NEAREST -> f64x2_unop_fallback(x, Math::rint);
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_S, Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F32X4_S -> convert(x, float.class, VectorOperators.F2I);
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_U, Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F32X4_U -> i32x4_trunc_sat_f32x4(x);
            case Bytecode.VECTOR_F32X4_CONVERT_I32X4_S -> convert(x, int.class, VectorOperators.I2F);
            case Bytecode.VECTOR_F32X4_CONVERT_I32X4_U -> f32x4_convert_i32x4_u(x);
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_S_ZERO, Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F64X2_S_ZERO -> convert(x, double.class, VectorOperators.D2I);
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_U_ZERO, Bytecode.VECTOR_I32X4_RELAXED_TRUNC_F64X2_U_ZERO -> i32x4_trunc_sat_f64x2_zero(x);
            case Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_S -> convert(x, int.class, VectorOperators.I2D);
            case Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_U -> f64x2_convert_low_i32x4_u(x);
            case Bytecode.VECTOR_F32X4_DEMOTE_F64X2_ZERO -> convert(x, double.class, VectorOperators.D2F);
            case Bytecode.VECTOR_F64X2_PROMOTE_LOW_F32X4 -> convert(x, float.class, VectorOperators.F2D);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static ByteVector binary(ByteVector x, ByteVector y, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I8X16_SWIZZLE, Bytecode.VECTOR_I8X16_RELAXED_SWIZZLE -> i8x16_swizzle(x, y);
            case Bytecode.VECTOR_V128_AND -> binop(x, y, byte.class, VectorOperators.AND);
            case Bytecode.VECTOR_V128_ANDNOT -> binop(x, y, byte.class, VectorOperators.AND_NOT);
            case Bytecode.VECTOR_V128_OR -> binop(x, y, byte.class, VectorOperators.OR);
            case Bytecode.VECTOR_V128_XOR -> binop(x, y, byte.class, VectorOperators.XOR);
            case Bytecode.VECTOR_I8X16_EQ -> relop(x, y, byte.class, VectorOperators.EQ);
            case Bytecode.VECTOR_I8X16_NE -> relop(x, y, byte.class, VectorOperators.NE);
            case Bytecode.VECTOR_I8X16_LT_S -> relop(x, y, byte.class, VectorOperators.LT);
            case Bytecode.VECTOR_I8X16_LT_U -> relop(x, y, byte.class, VectorOperators.ULT);
            case Bytecode.VECTOR_I8X16_GT_S -> relop(x, y, byte.class, VectorOperators.GT);
            case Bytecode.VECTOR_I8X16_GT_U -> relop(x, y, byte.class, VectorOperators.UGT);
            case Bytecode.VECTOR_I8X16_LE_S -> relop(x, y, byte.class, VectorOperators.LE);
            case Bytecode.VECTOR_I8X16_LE_U -> relop(x, y, byte.class, VectorOperators.ULE);
            case Bytecode.VECTOR_I8X16_GE_S -> relop(x, y, byte.class, VectorOperators.GE);
            case Bytecode.VECTOR_I8X16_GE_U -> relop(x, y, byte.class, VectorOperators.UGE);
            case Bytecode.VECTOR_I16X8_EQ -> relop(x, y, short.class, VectorOperators.EQ);
            case Bytecode.VECTOR_I16X8_NE -> relop(x, y, short.class, VectorOperators.NE);
            case Bytecode.VECTOR_I16X8_LT_S -> relop(x, y, short.class, VectorOperators.LT);
            case Bytecode.VECTOR_I16X8_LT_U -> relop(x, y, short.class, VectorOperators.ULT);
            case Bytecode.VECTOR_I16X8_GT_S -> relop(x, y, short.class, VectorOperators.GT);
            case Bytecode.VECTOR_I16X8_GT_U -> relop(x, y, short.class, VectorOperators.UGT);
            case Bytecode.VECTOR_I16X8_LE_S -> relop(x, y, short.class, VectorOperators.LE);
            case Bytecode.VECTOR_I16X8_LE_U -> relop(x, y, short.class, VectorOperators.ULE);
            case Bytecode.VECTOR_I16X8_GE_S -> relop(x, y, short.class, VectorOperators.GE);
            case Bytecode.VECTOR_I16X8_GE_U -> relop(x, y, short.class, VectorOperators.UGE);
            case Bytecode.VECTOR_I32X4_EQ -> relop(x, y, int.class, VectorOperators.EQ);
            case Bytecode.VECTOR_I32X4_NE -> relop(x, y, int.class, VectorOperators.NE);
            case Bytecode.VECTOR_I32X4_LT_S -> relop(x, y, int.class, VectorOperators.LT);
            case Bytecode.VECTOR_I32X4_LT_U -> relop(x, y, int.class, VectorOperators.ULT);
            case Bytecode.VECTOR_I32X4_GT_S -> relop(x, y, int.class, VectorOperators.GT);
            case Bytecode.VECTOR_I32X4_GT_U -> relop(x, y, int.class, VectorOperators.UGT);
            case Bytecode.VECTOR_I32X4_LE_S -> relop(x, y, int.class, VectorOperators.LE);
            case Bytecode.VECTOR_I32X4_LE_U -> relop(x, y, int.class, VectorOperators.ULE);
            case Bytecode.VECTOR_I32X4_GE_S -> relop(x, y, int.class, VectorOperators.GE);
            case Bytecode.VECTOR_I32X4_GE_U -> relop(x, y, int.class, VectorOperators.UGE);
            case Bytecode.VECTOR_I64X2_EQ -> relop(x, y, long.class, VectorOperators.EQ);
            case Bytecode.VECTOR_I64X2_NE -> relop(x, y, long.class, VectorOperators.NE);
            case Bytecode.VECTOR_I64X2_LT_S -> relop(x, y, long.class, VectorOperators.LT);
            case Bytecode.VECTOR_I64X2_GT_S -> relop(x, y, long.class, VectorOperators.GT);
            case Bytecode.VECTOR_I64X2_LE_S -> relop(x, y, long.class, VectorOperators.LE);
            case Bytecode.VECTOR_I64X2_GE_S -> relop(x, y, long.class, VectorOperators.GE);
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
            case Bytecode.VECTOR_I8X16_NARROW_I16X8_S -> narrow(x, y, short.class, VectorOperators.S2B, Byte.MIN_VALUE, Byte.MAX_VALUE);
            case Bytecode.VECTOR_I8X16_NARROW_I16X8_U -> narrow(x, y, short.class, VectorOperators.S2B, 0, 0xff);
            case Bytecode.VECTOR_I8X16_ADD -> binop(x, y, byte.class, VectorOperators.ADD);
            case Bytecode.VECTOR_I8X16_ADD_SAT_S -> binop(x, y, byte.class, VectorOperators.SADD);
            case Bytecode.VECTOR_I8X16_ADD_SAT_U -> binop_sat_u(x, y, VectorOperators.ZERO_EXTEND_B2S, VectorOperators.S2B, VectorOperators.ADD, 0, 0xff);
            case Bytecode.VECTOR_I8X16_SUB -> binop(x, y, byte.class, VectorOperators.SUB);
            case Bytecode.VECTOR_I8X16_SUB_SAT_S -> binop(x, y, byte.class, VectorOperators.SSUB);
            case Bytecode.VECTOR_I8X16_SUB_SAT_U -> binop_sat_u(x, y, VectorOperators.ZERO_EXTEND_B2S, VectorOperators.S2B, VectorOperators.SUB, 0, 0xff);
            case Bytecode.VECTOR_I8X16_MIN_S -> binop(x, y, byte.class, VectorOperators.MIN);
            case Bytecode.VECTOR_I8X16_MIN_U -> binop(x, y, byte.class, VectorOperators.UMIN);
            case Bytecode.VECTOR_I8X16_MAX_S -> binop(x, y, byte.class, VectorOperators.MAX);
            case Bytecode.VECTOR_I8X16_MAX_U -> binop(x, y, byte.class, VectorOperators.UMAX);
            case Bytecode.VECTOR_I8X16_AVGR_U -> avgr(x, y, VectorOperators.ZERO_EXTEND_B2S, VectorOperators.S2B);
            case Bytecode.VECTOR_I16X8_NARROW_I32X4_S -> narrow(x, y, int.class, VectorOperators.I2S, Short.MIN_VALUE, Short.MAX_VALUE);
            case Bytecode.VECTOR_I16X8_NARROW_I32X4_U -> narrow(x, y, int.class, VectorOperators.I2S, 0, 0xffff);
            case Bytecode.VECTOR_I16X8_Q15MULR_SAT_S, Bytecode.VECTOR_I16X8_RELAXED_Q15MULR_S -> i16x8_q15mulr_sat_s(x, y);
            case Bytecode.VECTOR_I16X8_ADD -> binop(x, y, short.class, VectorOperators.ADD);
            case Bytecode.VECTOR_I16X8_ADD_SAT_S -> binop(x, y, short.class, VectorOperators.SADD);
            case Bytecode.VECTOR_I16X8_ADD_SAT_U -> binop_sat_u(x, y, VectorOperators.ZERO_EXTEND_S2I, VectorOperators.I2S, VectorOperators.ADD, 0, 0xffff);
            case Bytecode.VECTOR_I16X8_SUB -> binop(x, y, short.class, VectorOperators.SUB);
            case Bytecode.VECTOR_I16X8_SUB_SAT_S -> binop(x, y, short.class, VectorOperators.SSUB);
            case Bytecode.VECTOR_I16X8_SUB_SAT_U -> binop_sat_u(x, y, VectorOperators.ZERO_EXTEND_S2I, VectorOperators.I2S, VectorOperators.SUB, 0, 0xffff);
            case Bytecode.VECTOR_I16X8_MUL -> binop(x, y, short.class, VectorOperators.MUL);
            case Bytecode.VECTOR_I16X8_MIN_S -> binop(x, y, short.class, VectorOperators.MIN);
            case Bytecode.VECTOR_I16X8_MIN_U -> binop(x, y, short.class, VectorOperators.UMIN);
            case Bytecode.VECTOR_I16X8_MAX_S -> binop(x, y, short.class, VectorOperators.MAX);
            case Bytecode.VECTOR_I16X8_MAX_U -> binop(x, y, short.class, VectorOperators.UMAX);
            case Bytecode.VECTOR_I16X8_AVGR_U -> avgr(x, y, VectorOperators.ZERO_EXTEND_S2I, VectorOperators.I2S);
            case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_S -> extmul(x, y, VectorOperators.B2S, 0);
            case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_U -> extmul(x, y, VectorOperators.ZERO_EXTEND_B2S, 0);
            case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_S -> extmul(x, y, VectorOperators.B2S, 1);
            case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_U -> extmul(x, y, VectorOperators.ZERO_EXTEND_B2S, 1);
            case Bytecode.VECTOR_I32X4_ADD -> binop(x, y, int.class, VectorOperators.ADD);
            case Bytecode.VECTOR_I32X4_SUB -> binop(x, y, int.class, VectorOperators.SUB);
            case Bytecode.VECTOR_I32X4_MUL -> binop(x, y, int.class, VectorOperators.MUL);
            case Bytecode.VECTOR_I32X4_MIN_S -> binop(x, y, int.class, VectorOperators.MIN);
            case Bytecode.VECTOR_I32X4_MIN_U -> binop(x, y, int.class, VectorOperators.UMIN);
            case Bytecode.VECTOR_I32X4_MAX_S -> binop(x, y, int.class, VectorOperators.MAX);
            case Bytecode.VECTOR_I32X4_MAX_U -> binop(x, y, int.class, VectorOperators.UMAX);
            case Bytecode.VECTOR_I32X4_DOT_I16X8_S -> i32x4_dot_i16x8_s(x, y);
            case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_S -> extmul(x, y, VectorOperators.S2I, 0);
            case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_U -> extmul(x, y, VectorOperators.ZERO_EXTEND_S2I, 0);
            case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_S -> extmul(x, y, VectorOperators.S2I, 1);
            case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_U -> extmul(x, y, VectorOperators.ZERO_EXTEND_S2I, 1);
            case Bytecode.VECTOR_I64X2_ADD -> binop(x, y, long.class, VectorOperators.ADD);
            case Bytecode.VECTOR_I64X2_SUB -> binop(x, y, long.class, VectorOperators.SUB);
            case Bytecode.VECTOR_I64X2_MUL -> binop(x, y, long.class, VectorOperators.MUL);
            case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_S -> extmul(x, y, VectorOperators.I2L, 0);
            case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_U -> extmul(x, y, VectorOperators.ZERO_EXTEND_I2L, 0);
            case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_S -> extmul(x, y, VectorOperators.I2L, 1);
            case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_U -> extmul(x, y, VectorOperators.ZERO_EXTEND_I2L, 1);
            case Bytecode.VECTOR_F32X4_ADD -> binop(x, y, float.class, VectorOperators.ADD);
            case Bytecode.VECTOR_F32X4_SUB -> binop(x, y, float.class, VectorOperators.SUB);
            case Bytecode.VECTOR_F32X4_MUL -> binop(x, y, float.class, VectorOperators.MUL);
            case Bytecode.VECTOR_F32X4_DIV -> binop(x, y, float.class, VectorOperators.DIV);
            case Bytecode.VECTOR_F32X4_MIN, Bytecode.VECTOR_F32X4_RELAXED_MIN -> binop(x, y, float.class, VectorOperators.MIN);
            case Bytecode.VECTOR_F32X4_MAX, Bytecode.VECTOR_F32X4_RELAXED_MAX -> binop(x, y, float.class, VectorOperators.MAX);
            case Bytecode.VECTOR_F32X4_PMIN -> pmin(x, y, float.class);
            case Bytecode.VECTOR_F32X4_PMAX -> pmax(x, y, float.class);
            case Bytecode.VECTOR_F64X2_ADD -> binop(x, y, double.class, VectorOperators.ADD);
            case Bytecode.VECTOR_F64X2_SUB -> binop(x, y, double.class, VectorOperators.SUB);
            case Bytecode.VECTOR_F64X2_MUL -> binop(x, y, double.class, VectorOperators.MUL);
            case Bytecode.VECTOR_F64X2_DIV -> binop(x, y, double.class, VectorOperators.DIV);
            case Bytecode.VECTOR_F64X2_MIN, Bytecode.VECTOR_F64X2_RELAXED_MIN -> binop(x, y, double.class, VectorOperators.MIN);
            case Bytecode.VECTOR_F64X2_MAX, Bytecode.VECTOR_F64X2_RELAXED_MAX -> binop(x, y, double.class, VectorOperators.MAX);
            case Bytecode.VECTOR_F64X2_PMIN -> pmin(x, y, double.class);
            case Bytecode.VECTOR_F64X2_PMAX -> pmax(x, y, double.class);
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
            case Bytecode.VECTOR_I8X16_ALL_TRUE -> all_true(x, byte.class);
            case Bytecode.VECTOR_I8X16_BITMASK -> bitmask(x, byte.class);
            case Bytecode.VECTOR_I16X8_ALL_TRUE -> all_true(x, short.class);
            case Bytecode.VECTOR_I16X8_BITMASK -> bitmask(x, short.class);
            case Bytecode.VECTOR_I32X4_ALL_TRUE -> all_true(x, int.class);
            case Bytecode.VECTOR_I32X4_BITMASK -> bitmask(x, int.class);
            case Bytecode.VECTOR_I64X2_ALL_TRUE -> all_true(x, long.class);
            case Bytecode.VECTOR_I64X2_BITMASK -> bitmask(x, long.class);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static ByteVector shift(ByteVector x, int shift, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I8X16_SHL -> shiftop(x, (byte) shift, byte.class, VectorOperators.LSHL);
            case Bytecode.VECTOR_I8X16_SHR_S -> shiftop(x, (byte) shift, byte.class, VectorOperators.ASHR);
            case Bytecode.VECTOR_I8X16_SHR_U -> shiftop(x, (byte) shift, byte.class, VectorOperators.LSHR);
            case Bytecode.VECTOR_I16X8_SHL -> shiftop(x, (short) shift, short.class, VectorOperators.LSHL);
            case Bytecode.VECTOR_I16X8_SHR_S -> shiftop(x, (short) shift, short.class, VectorOperators.ASHR);
            case Bytecode.VECTOR_I16X8_SHR_U -> shiftop(x, (short) shift, short.class, VectorOperators.LSHR);
            case Bytecode.VECTOR_I32X4_SHL -> shiftop(x, shift, int.class, VectorOperators.LSHL);
            case Bytecode.VECTOR_I32X4_SHR_S -> shiftop(x, shift, int.class, VectorOperators.ASHR);
            case Bytecode.VECTOR_I32X4_SHR_U -> shiftop(x, shift, int.class, VectorOperators.LSHR);
            case Bytecode.VECTOR_I64X2_SHL -> shiftop(x, shift, long.class, VectorOperators.LSHL);
            case Bytecode.VECTOR_I64X2_SHR_S -> shiftop(x, shift, long.class, VectorOperators.ASHR);
            case Bytecode.VECTOR_I64X2_SHR_U -> shiftop(x, shift, long.class, VectorOperators.LSHR);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    // Checkstyle: stop method name check

    public static ByteVector i8x16_shuffle(ByteVector xBytes, ByteVector yBytes, ByteVector indicesBytes) {
        ByteVector x = cast(xBytes);
        ByteVector y = cast(yBytes);
        ByteVector indices = cast(indicesBytes);
        VectorShuffle<Byte> shuffle = indices.add((byte) (-2 * BYTES), indices.lt((byte) BYTES).not()).toShuffle();
        return cast(x.rearrange(shuffle, y));
    }

    public static int i8x16_extract_lane(ByteVector vecBytes, int laneIndex, int vectorOpcode) {
        ByteVector vec = cast(vecBytes);
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I8X16_EXTRACT_LANE_S -> vec.lane(laneIndex);
            case Bytecode.VECTOR_I8X16_EXTRACT_LANE_U -> Byte.toUnsignedInt(vec.lane(laneIndex));
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static ByteVector i8x16_replace_lane(ByteVector vecBytes, int laneIndex, byte value) {
        ByteVector vec = cast(vecBytes);
        return cast(vec.withLane(laneIndex, value));
    }

    public static int i16x8_extract_lane(ByteVector vecBytes, int laneIndex, int vectorOpcode) {
        ShortVector vec = cast(vecBytes).reinterpretAsShorts();
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I16X8_EXTRACT_LANE_S -> vec.lane(laneIndex);
            case Bytecode.VECTOR_I16X8_EXTRACT_LANE_U -> Short.toUnsignedInt(vec.lane(laneIndex));
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static ByteVector i16x8_replace_lane(ByteVector vecBytes, int laneIndex, short value) {
        ShortVector vec = cast(vecBytes).reinterpretAsShorts();
        return cast(vec.withLane(laneIndex, value).reinterpretAsBytes());
    }

    public static int i32x4_extract_lane(ByteVector vecBytes, int laneIndex) {
        IntVector vec = cast(vecBytes).reinterpretAsInts();
        return vec.lane(laneIndex);
    }

    public static ByteVector i32x4_replace_lane(ByteVector vecBytes, int laneIndex, int value) {
        IntVector vec = cast(vecBytes).reinterpretAsInts();
        return cast(vec.withLane(laneIndex, value).reinterpretAsBytes());
    }

    public static long i64x2_extract_lane(ByteVector vecBytes, int laneIndex) {
        LongVector vec = cast(vecBytes).reinterpretAsLongs();
        return vec.lane(laneIndex);
    }

    public static ByteVector i64x2_replace_lane(ByteVector vecBytes, int laneIndex, long value) {
        LongVector vec = cast(vecBytes).reinterpretAsLongs();
        return cast(vec.withLane(laneIndex, value).reinterpretAsBytes());
    }

    public static float f32x4_extract_lane(ByteVector vecBytes, int laneIndex) {
        FloatVector vec = cast(vecBytes).reinterpretAsFloats();
        return vec.lane(laneIndex);
    }

    public static ByteVector f32x4_replace_lane(ByteVector vecBytes, int laneIndex, float value) {
        FloatVector vec = cast(vecBytes).reinterpretAsFloats();
        return cast(vec.withLane(laneIndex, value).reinterpretAsBytes());
    }

    public static double f64x2_extract_lane(ByteVector vecBytes, int laneIndex) {
        DoubleVector vec = cast(vecBytes).reinterpretAsDoubles();
        return vec.lane(laneIndex);
    }

    public static ByteVector f64x2_replace_lane(ByteVector vecBytes, int laneIndex, double value) {
        DoubleVector vec = cast(vecBytes).reinterpretAsDoubles();
        return cast(vec.withLane(laneIndex, value).reinterpretAsBytes());
    }

    private static <E> ByteVector unop(ByteVector xBytes, Class<E> elementType, VectorOperators.Unary op) {
        Vector<E> x = in(xBytes, elementType);
        Vector<E> result = x.lanewise(op);
        return out(result);
    }

    private static <E, F> ByteVector extadd_pairwise(ByteVector xBytes, Class<E> elementType, VectorOperators.Conversion<E, F> conv) {
        Vector<E> x = in(xBytes, elementType);
        Vector<F> evens = x.compress(evens(elementType)).convert(conv, 0);
        Vector<F> odds = x.compress(odds(elementType)).convert(conv, 0);
        Vector<F> result = evens.add(odds);
        return out(result);
    }

    private static <E, F> ByteVector extend(ByteVector xBytes, int part, Class<E> elementType, VectorOperators.Conversion<E, F> conv) {
        Vector<E> x = cast(xBytes).reinterpretShape(VectorShape.S_128_BIT.withLanes(elementType), 0);
        Vector<F> result = x.convert(conv, part);
        return cast(result.reinterpretAsBytes());
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static ByteVector f32x4_unop_fallback(ByteVector xBytes, Function<Float, Float> op) {
        FloatVector x = cast(xBytes).reinterpretAsFloats();
        float[] xArray = x.toArray();
        for (int i = 0; i < xArray.length; i++) {
            xArray[i] = op.apply(xArray[i]);
        }
        return fromArray(xArray);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static ByteVector f64x2_unop_fallback(ByteVector xBytes, Function<Double, Double> op) {
        DoubleVector x = cast(xBytes).reinterpretAsDoubles();
        double[] xArray = x.toArray();
        for (int i = 0; i < xArray.length; i++) {
            xArray[i] = op.apply(xArray[i]);
        }
        return fromArray(xArray);
    }

    private static <E, F> ByteVector convert(ByteVector xBytes, Class<E> elementType, VectorOperators.Conversion<E, F> conv) {
        Vector<E> x = in(xBytes, elementType);
        Vector<F> result = x.convert(conv, 0);
        return out(result);
    }

    private static ByteVector i32x4_trunc_sat_f32x4(ByteVector xBytes) {
        FloatVector x = cast(xBytes).reinterpretAsFloats();
        Vector<Double> xLow = x.convert(VectorOperators.F2D, 0);
        Vector<Double> xHigh = x.convert(VectorOperators.F2D, 1);
        Vector<Integer> resultLow = truncSatU32(xLow).convert(VectorOperators.L2I, 0);
        Vector<Integer> resultHigh = truncSatU32(xHigh).convert(VectorOperators.L2I, -1);
        Vector<Integer> result = resultLow.lanewise(VectorOperators.FIRST_NONZERO, resultHigh);
        return out(result);
    }

    private static ByteVector f32x4_convert_i32x4_u(ByteVector xBytes) {
        Vector<Integer> x = in(xBytes, int.class);
        Vector<Long> xUnsignedLow = x.convert(VectorOperators.ZERO_EXTEND_I2L, 0);
        Vector<Long> xUnsignedHigh = x.convert(VectorOperators.ZERO_EXTEND_I2L, 1);
        Vector<Float> resultLow = xUnsignedLow.convert(VectorOperators.L2F, 0);
        Vector<Float> resultHigh = xUnsignedHigh.convert(VectorOperators.L2F, -1);
        Vector<Float> result = resultLow.lanewise(VectorOperators.FIRST_NONZERO, resultHigh);
        return out(result);
    }

    private static ByteVector i32x4_trunc_sat_f64x2_zero(ByteVector xBytes) {
        DoubleVector x = cast(xBytes).reinterpretAsDoubles();
        Vector<Long> longResult = truncSatU32(x);
        Vector<Integer> result = longResult.convert(VectorOperators.L2I, 0);
        return out(result);
    }

    private static ByteVector f64x2_convert_low_i32x4_u(ByteVector xBytes) {
        Vector<Integer> x = in(xBytes, int.class);
        Vector<Long> xUnsignedLow = x.convert(VectorOperators.ZERO_EXTEND_I2L, 0);
        Vector<Double> result = xUnsignedLow.convert(VectorOperators.L2D, 0);
        return out(result);
    }

    private static ByteVector i8x16_swizzle(ByteVector valueBytes, ByteVector indexBytes) {
        ByteVector values = cast(valueBytes);
        ByteVector indices = cast(indexBytes);
        VectorMask<Byte> safeIndices = indices.lt((byte) 0).or(indices.lt((byte) BYTES).not()).not();
        ByteVector result = values.rearrange(indices.toShuffle(), safeIndices);
        return cast(result);
    }

    private static <E> ByteVector binop(ByteVector xBytes, ByteVector yBytes, Class<E> elementType, VectorOperators.Binary op) {
        Vector<E> x = in(xBytes, elementType);
        Vector<E> y = in(yBytes, elementType);
        Vector<E> result = x.lanewise(op, y);
        return out(result);
    }

    private static <E> ByteVector relop(ByteVector xBytes, ByteVector yBytes, Class<E> elementType, VectorOperators.Comparison comp) {
        Vector<E> x = in(xBytes, elementType);
        Vector<E> y = in(yBytes, elementType);
        Vector<E> result = x.compare(comp, y).toVector();
        return out(result);
    }

    private static ByteVector f32x4_relop(ByteVector xBytes, ByteVector yBytes, VectorOperators.Comparison comp) {
        FloatVector x = cast(xBytes).reinterpretAsFloats();
        FloatVector y = cast(yBytes).reinterpretAsFloats();
        IntVector zero = IntVector.zero(IntVector.SPECIES_128);
        IntVector minusOne = IntVector.broadcast(IntVector.SPECIES_128, -1);
        IntVector result = zero.blend(minusOne, x.compare(comp, y).cast(IntVector.SPECIES_128));
        return out(result);
    }

    private static ByteVector f64x2_relop(ByteVector xBytes, ByteVector yBytes, VectorOperators.Comparison comp) {
        DoubleVector x = cast(xBytes).reinterpretAsDoubles();
        DoubleVector y = cast(yBytes).reinterpretAsDoubles();
        LongVector zero = LongVector.zero(LongVector.SPECIES_128);
        LongVector minusOne = LongVector.broadcast(LongVector.SPECIES_128, -1);
        LongVector result = zero.blend(minusOne, x.compare(comp, y).cast(LongVector.SPECIES_128));
        return out(result);
    }

    private static <E, F> ByteVector narrow(ByteVector xBytes, ByteVector yBytes, Class<E> elementType, VectorOperators.Conversion<E, F> conv, long min, long max) {
        Vector<E> x = in(xBytes, elementType);
        Vector<E> y = in(yBytes, elementType);
        Vector<E> xSat = sat(x, min, max);
        Vector<E> ySat = sat(y, min, max);
        Vector<F> resultLow = xSat.convert(conv, 0);
        Vector<F> resultHigh = ySat.convert(conv, -1);
        Vector<F> result = resultLow.lanewise(VectorOperators.FIRST_NONZERO, resultHigh);
        return out(result);
    }

    private static <E, F> ByteVector binop_sat_u(ByteVector xBytes, ByteVector yBytes, VectorOperators.Conversion<E, F> upcast, VectorOperators.Conversion<F, E> downcast, VectorOperators.Binary op,
                    long min, long max) {
        return upcastBinopDowncast(xBytes, yBytes, upcast, downcast, (x, y) -> {
            Vector<F> rawResult = x.lanewise(op, y);
            Vector<F> satResult = sat(rawResult, min, max);
            return satResult;
        });
    }

    private static <E, F> ByteVector avgr(ByteVector xBytes, ByteVector yBytes, VectorOperators.Conversion<E, F> upcast, VectorOperators.Conversion<F, E> downcast) {
        Vector<F> one = VectorShape.S_128_BIT.withLanes(upcast.rangeType()).broadcast(1);
        Vector<F> two = VectorShape.S_128_BIT.withLanes(upcast.rangeType()).broadcast(2);
        return upcastBinopDowncast(xBytes, yBytes, upcast, downcast, (x, y) -> x.add(y).add(one).div(two));
    }

    private static ByteVector i16x8_q15mulr_sat_s(ByteVector xBytes, ByteVector yBytes) {
        return upcastBinopDowncast(xBytes, yBytes, VectorOperators.S2I, VectorOperators.I2S, (x, y) -> {
            Vector<Integer> rawResult = x.mul(y).add(IntVector.broadcast(IntVector.SPECIES_128, 1 << 14)).lanewise(VectorOperators.ASHR, IntVector.broadcast(IntVector.SPECIES_128, 15));
            Vector<Integer> satResult = sat(rawResult, Short.MIN_VALUE, Short.MAX_VALUE);
            return satResult;
        });
    }

    private static <E, F> ByteVector extmul(ByteVector xBytes, ByteVector yBytes, VectorOperators.Conversion<E, F> extend, int part) {
        Vector<E> x = in(xBytes, extend.domainType());
        Vector<E> y = in(yBytes, extend.domainType());
        Vector<F> xExtended = x.convert(extend, part);
        Vector<F> yExtended = y.convert(extend, part);
        Vector<F> result = xExtended.mul(yExtended);
        return out(result);
    }

    private static ByteVector i32x4_dot_i16x8_s(ByteVector xBytes, ByteVector yBytes) {
        Vector<Short> x = in(xBytes, short.class);
        Vector<Short> y = in(yBytes, short.class);
        Vector<Integer> xEvens = x.compress(evens(short.class)).convert(VectorOperators.S2I, 0);
        Vector<Integer> xOdds = x.compress(odds(short.class)).convert(VectorOperators.S2I, 0);
        Vector<Integer> yEvens = y.compress(evens(short.class)).convert(VectorOperators.S2I, 0);
        Vector<Integer> yOdds = y.compress(odds(short.class)).convert(VectorOperators.S2I, 0);
        Vector<Integer> xMulYEvens = xEvens.mul(yEvens);
        Vector<Integer> xMulYOdds = xOdds.mul(yOdds);
        Vector<Integer> dot = xMulYEvens.lanewise(VectorOperators.ADD, xMulYOdds);
        return out(dot);
    }

    private static <E> ByteVector pmin(ByteVector xBytes, ByteVector yBytes, Class<E> elementType) {
        Vector<E> x = in(xBytes, elementType);
        Vector<E> y = in(yBytes, elementType);
        Vector<E> result = x.blend(y, y.compare(VectorOperators.LT, x));
        return out(result);
    }

    private static <E> ByteVector pmax(ByteVector xBytes, ByteVector yBytes, Class<E> elementType) {
        Vector<E> x = in(xBytes, elementType);
        Vector<E> y = in(yBytes, elementType);
        Vector<E> result = x.blend(y, x.compare(VectorOperators.LT, y));
        return out(result);
    }

    private static ByteVector i16x8_relaxed_dot_i8x16_i7x16_s(ByteVector xBytes, ByteVector yBytes) {
        ByteVector x = cast(xBytes);
        ByteVector y = cast(yBytes);
        Vector<Short> xEvens = x.compress(evens(byte.class)).convert(VectorOperators.B2S, 0);
        Vector<Short> xOdds = x.compress(odds(byte.class)).convert(VectorOperators.B2S, 0);
        Vector<Short> yEvens = y.compress(evens(byte.class)).convert(VectorOperators.B2S, 0);
        Vector<Short> yOdds = y.compress(odds(byte.class)).convert(VectorOperators.B2S, 0);
        Vector<Short> xMulYEvens = xEvens.mul(yEvens);
        Vector<Short> xMulYOdds = xOdds.mul(yOdds);
        Vector<Short> dot = xMulYEvens.lanewise(VectorOperators.SADD, xMulYOdds);
        return out(dot);
    }

    private static ByteVector bitselect(ByteVector xBytes, ByteVector yBytes, ByteVector maskBytes) {
        ByteVector x = cast(xBytes);
        ByteVector y = cast(yBytes);
        ByteVector mask = cast(maskBytes);
        ByteVector result = y.bitwiseBlend(x, mask);
        return cast(result);
    }

    private static ByteVector f32x4_ternop(ByteVector xBytes, ByteVector yBytes, ByteVector zBytes, int vectorOpcode) {
        FloatVector x = cast(xBytes).reinterpretAsFloats();
        FloatVector y = cast(yBytes).reinterpretAsFloats();
        FloatVector z = cast(zBytes).reinterpretAsFloats();
        FloatVector result = switch (vectorOpcode) {
            case Bytecode.VECTOR_F32X4_RELAXED_MADD -> x.lanewise(VectorOperators.FMA, y, z);
            case Bytecode.VECTOR_F32X4_RELAXED_NMADD -> x.neg().lanewise(VectorOperators.FMA, y, z);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
        return cast(result.reinterpretAsBytes());
    }

    private static ByteVector f64x2_ternop(ByteVector xBytes, ByteVector yBytes, ByteVector zBytes, int vectorOpcode) {
        DoubleVector x = cast(xBytes).reinterpretAsDoubles();
        DoubleVector y = cast(yBytes).reinterpretAsDoubles();
        DoubleVector z = cast(zBytes).reinterpretAsDoubles();
        DoubleVector result = switch (vectorOpcode) {
            case Bytecode.VECTOR_F64X2_RELAXED_MADD -> x.lanewise(VectorOperators.FMA, y, z);
            case Bytecode.VECTOR_F64X2_RELAXED_NMADD -> x.neg().lanewise(VectorOperators.FMA, y, z);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
        return cast(result.reinterpretAsBytes());
    }

    private static ByteVector i32x4_relaxed_dot_i8x16_i7x16_add_s(ByteVector xBytes, ByteVector yBytes, ByteVector zBytes) {
        ByteVector x = cast(xBytes);
        ByteVector y = cast(yBytes);
        IntVector z = cast(zBytes).reinterpretAsInts();
        Vector<Short> xEvens = x.compress(evens(byte.class)).convert(VectorOperators.B2S, 0);
        Vector<Short> xOdds = x.compress(odds(byte.class)).convert(VectorOperators.B2S, 0);
        Vector<Short> yEvens = y.compress(evens(byte.class)).convert(VectorOperators.B2S, 0);
        Vector<Short> yOdds = y.compress(odds(byte.class)).convert(VectorOperators.B2S, 0);
        Vector<Short> xMulYEvens = xEvens.mul(yEvens);
        Vector<Short> xMulYOdds = xOdds.mul(yOdds);
        Vector<Short> dot = xMulYEvens.lanewise(VectorOperators.SADD, xMulYOdds);
        Vector<Integer> dotEvens = dot.compress(evens(short.class)).convert(VectorOperators.S2I, 0);
        Vector<Integer> dotOdds = dot.compress(odds(short.class)).convert(VectorOperators.S2I, 0);
        Vector<Integer> dots = dotEvens.add(dotOdds);
        Vector<Integer> result = dots.add(z);
        return cast(result.reinterpretAsBytes());
    }

    private static int v128_any_true(ByteVector vec) {
        return cast(vec).eq((byte) 0).allTrue() ? 0 : 1;
    }

    private static <E> int all_true(ByteVector vecBytes, Class<E> elementType) {
        Vector<E> vec = in(vecBytes, elementType);
        Vector<E> zero = VectorShape.S_128_BIT.withLanes(elementType).zero();
        return vec.eq(zero).anyTrue() ? 0 : 1;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static <E> int bitmask(ByteVector vecBytes, Class<E> elementType) {
        Vector<E> vec = in(vecBytes, elementType);
        Vector<E> zero = VectorShape.S_128_BIT.withLanes(elementType).zero();
        VectorMask<E> mask = vec.lt(zero);
        int bitmask = 0;
        for (int i = 0; i < mask.length(); i++) {
            if (mask.laneIsSet(i)) {
                bitmask |= 1 << i;
            }
        }
        return bitmask;
    }

    private static <E> ByteVector shiftop(ByteVector xBytes, int shift, Class<E> elementType, VectorOperators.Binary shiftOp) {
        Vector<E> x = in(xBytes, elementType);
        Vector<E> result = x.lanewise(shiftOp, shift);
        return out(result);
    }

    // Checkstyle: resume method name check

    private static final Class<? extends ByteVector> BYTE_128_CLASS = ByteVector.zero(ByteVector.SPECIES_128).getClass();

    public static final ByteVector cast(ByteVector vec) {
        return BYTE_128_CLASS.cast(vec);
    }

    private static <E> Vector<E> in(ByteVector vec, Class<E> elementType) {
        return BYTE_128_CLASS.cast(vec).reinterpretShape(VectorShape.S_128_BIT.withLanes(elementType), 0);
    }

    private static <E> ByteVector out(Vector<E> vec) {
        return BYTE_128_CLASS.cast(vec.reinterpretAsBytes());
    }

    private static <E> Vector<E> sat(Vector<E> vec, long min, long max) {
        Vector<E> vMin = VectorShape.S_128_BIT.withLanes(vec.elementType()).broadcast(min);
        Vector<E> vMax = VectorShape.S_128_BIT.withLanes(vec.elementType()).broadcast(max);
        return vec.max(vMin).min(vMax);
    }

    private static Vector<Long> truncSatU32(Vector<Double> x) {
        VectorMask<Long> underflow = x.test(VectorOperators.IS_NAN).or(x.test(VectorOperators.IS_NEGATIVE)).cast(LongVector.SPECIES_128);
        VectorMask<Long> overflow = x.compare(VectorOperators.GT, 0xffff_ffffL).cast(LongVector.SPECIES_128);
        Vector<Long> zero = LongVector.SPECIES_128.zero();
        Vector<Long> u32max = LongVector.SPECIES_128.broadcast(0xffff_ffffL);
        Vector<Long> trunc = x.convert(VectorOperators.D2L, 0);
        return trunc.blend(u32max, overflow).blend(zero, underflow);
    }

    private static <E, F> ByteVector upcastBinopDowncast(ByteVector xBytes, ByteVector yBytes, VectorOperators.Conversion<E, F> upcast, VectorOperators.Conversion<F, E> downcast,
                    BiFunction<Vector<F>, Vector<F>, Vector<F>> op) {
        Vector<E> x = in(xBytes, upcast.domainType());
        Vector<E> y = in(yBytes, upcast.domainType());
        Vector<F> xLow = x.convert(upcast, 0);
        Vector<F> xHigh = x.convert(upcast, 1);
        Vector<F> yLow = y.convert(upcast, 0);
        Vector<F> yHigh = y.convert(upcast, 1);
        Vector<E> resultLow = op.apply(xLow, yLow).convert(downcast, 0);
        Vector<E> resultHigh = op.apply(xHigh, yHigh).convert(downcast, -1);
        Vector<E> result = resultLow.lanewise(VectorOperators.FIRST_NONZERO, resultHigh);
        return out(result);
    }

    private static final boolean[] ALTERNATING_BITS;

    static {
        ALTERNATING_BITS = new boolean[ByteVector.SPECIES_128.length() + 1];
        for (int i = 0; i < ALTERNATING_BITS.length; i++) {
            ALTERNATING_BITS[i] = i % 2 == 0;
        }
    }

    private static <E> VectorMask<E> evens(Class<E> elementType) {
        return VectorMask.fromArray(VectorShape.S_128_BIT.withLanes(elementType), ALTERNATING_BITS, 0);
    }

    private static <E> VectorMask<E> odds(Class<E> elementType) {
        return VectorMask.fromArray(VectorShape.S_128_BIT.withLanes(elementType), ALTERNATING_BITS, 1);
    }

    public static ByteVector fromArray(byte[] bytes) {
        return fromArray(bytes, 0);
    }

    public static ByteVector fromArray(byte[] bytes, int offset) {
        return cast(ByteVector.fromArray(ByteVector.SPECIES_128, bytes, offset));
    }

    public static ByteVector fromArray(short[] shorts) {
        return cast(ShortVector.fromArray(ShortVector.SPECIES_128, shorts, 0).reinterpretAsBytes());
    }

    public static ByteVector fromArray(int[] ints) {
        return cast(IntVector.fromArray(IntVector.SPECIES_128, ints, 0).reinterpretAsBytes());
    }

    public static ByteVector fromArray(long[] longs) {
        return cast(LongVector.fromArray(LongVector.SPECIES_128, longs, 0).reinterpretAsBytes());
    }

    public static ByteVector fromArray(float[] floats) {
        return cast(FloatVector.fromArray(FloatVector.SPECIES_128, floats, 0).reinterpretAsBytes());
    }

    public static ByteVector fromArray(double[] doubles) {
        return cast(DoubleVector.fromArray(DoubleVector.SPECIES_128, doubles, 0).reinterpretAsBytes());
    }

    public static ByteVector broadcast(byte value) {
        return cast(ByteVector.broadcast(ByteVector.SPECIES_128, value));
    }

    public static ByteVector broadcast(short value) {
        return cast(ShortVector.broadcast(ShortVector.SPECIES_128, value).reinterpretAsBytes());
    }

    public static ByteVector broadcast(int value) {
        return cast(IntVector.broadcast(IntVector.SPECIES_128, value).reinterpretAsBytes());
    }

    public static ByteVector broadcast(long value) {
        return cast(LongVector.broadcast(LongVector.SPECIES_128, value).reinterpretAsBytes());
    }

    public static ByteVector broadcast(float value) {
        return cast(FloatVector.broadcast(FloatVector.SPECIES_128, value).reinterpretAsBytes());
    }

    public static ByteVector broadcast(double value) {
        return cast(DoubleVector.broadcast(DoubleVector.SPECIES_128, value).reinterpretAsBytes());
    }

    public static byte[] toArray(ByteVector vec) {
        return cast(vec).toArray();
    }

    public static void intoArray(ByteVector vec, byte[] array, int offset) {
        cast(vec).intoArray(array, offset);
    }
}
