/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.graalvm.wasm.constants.Bytecode;

import java.util.Arrays;

public class Vector128Ops {

    private static final ByteArraySupport byteArraySupport = ByteArraySupport.littleEndian();

    public static byte[] unary(byte[] x, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_NOT -> v128_not(x);
            case Bytecode.VECTOR_I8X16_ABS, Bytecode.VECTOR_I8X16_NEG, Bytecode.VECTOR_I8X16_POPCNT -> i8x16_unop(x, vectorOpcode);
            case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_S, Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_U -> i16x8_extadd_pairwise_i8x16(x, vectorOpcode);
            case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_S, Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_U -> i16x8_extend_low_i8x16(x, vectorOpcode);
            case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_S, Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_U -> i16x8_extend_high_i8x16(x, vectorOpcode);
            case Bytecode.VECTOR_I16X8_ABS, Bytecode.VECTOR_I16X8_NEG -> i16x8_unop(x, vectorOpcode);
            case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_S, Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_U -> i32x4_extadd_pairwise_i16x8(x, vectorOpcode);
            case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_S, Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_U -> i32x4_extend_low_i16x8(x, vectorOpcode);
            case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_S, Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_U -> i32x4_extend_high_i16x8(x, vectorOpcode);
            case Bytecode.VECTOR_I32X4_ABS, Bytecode.VECTOR_I32X4_NEG -> i32x4_unop(x, vectorOpcode);
            case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_S, Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_U -> i64x2_extend_low_i32x4(x, vectorOpcode);
            case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_S, Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_U -> i64x2_extend_high_i32x4(x, vectorOpcode);
            case Bytecode.VECTOR_I64X2_ABS, Bytecode.VECTOR_I64X2_NEG -> i64x2_unop(x, vectorOpcode);
            case Bytecode.VECTOR_F32X4_ABS, Bytecode.VECTOR_F32X4_NEG, Bytecode.VECTOR_F32X4_SQRT, Bytecode.VECTOR_F32X4_CEIL, Bytecode.VECTOR_F32X4_FLOOR, Bytecode.VECTOR_F32X4_TRUNC,
                            Bytecode.VECTOR_F32X4_NEAREST ->
                f32x4_unop(x, vectorOpcode);
            case Bytecode.VECTOR_F64X2_ABS, Bytecode.VECTOR_F64X2_NEG, Bytecode.VECTOR_F64X2_SQRT, Bytecode.VECTOR_F64X2_CEIL, Bytecode.VECTOR_F64X2_FLOOR, Bytecode.VECTOR_F64X2_TRUNC,
                            Bytecode.VECTOR_F64X2_NEAREST ->
                f64x2_unop(x, vectorOpcode);
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_S, Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_U -> i32x4_trunc_sat_f32x4(x, vectorOpcode);
            case Bytecode.VECTOR_F32X4_CONVERT_I32X4_S, Bytecode.VECTOR_F32X4_CONVERT_I32X4_U -> f32x4_convert_i32x4(x, vectorOpcode);
            case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_S_ZERO, Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_U_ZERO -> i32x4_trunc_sat_f64x2_zero(x, vectorOpcode);
            case Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_S, Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_U -> f64x2_convert_low_i32x4(x, vectorOpcode);
            case Bytecode.VECTOR_F32X4_DEMOTE_F64X2_ZERO -> f32x4_demote_f64x2_zero(x);
            case Bytecode.VECTOR_F64X2_PROMOTE_LOW_F32X4 -> f64x2_promote_low_f32x4(x);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static byte[] binary(byte[] x, byte[] y, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I8X16_SWIZZLE -> i8x16_swizzle(x, y);
            case Bytecode.VECTOR_V128_AND, Bytecode.VECTOR_V128_ANDNOT, Bytecode.VECTOR_V128_OR, Bytecode.VECTOR_V128_XOR -> v128_binop(x, y, vectorOpcode);
            case Bytecode.VECTOR_I8X16_EQ, Bytecode.VECTOR_I8X16_NE, Bytecode.VECTOR_I8X16_LT_S, Bytecode.VECTOR_I8X16_LT_U, Bytecode.VECTOR_I8X16_GT_S, Bytecode.VECTOR_I8X16_GT_U,
                            Bytecode.VECTOR_I8X16_LE_S, Bytecode.VECTOR_I8X16_LE_U, Bytecode.VECTOR_I8X16_GE_S, Bytecode.VECTOR_I8X16_GE_U ->
                i8x16_relop(x, y, vectorOpcode);
            case Bytecode.VECTOR_I16X8_EQ, Bytecode.VECTOR_I16X8_NE, Bytecode.VECTOR_I16X8_LT_S, Bytecode.VECTOR_I16X8_LT_U, Bytecode.VECTOR_I16X8_GT_S, Bytecode.VECTOR_I16X8_GT_U,
                            Bytecode.VECTOR_I16X8_LE_S, Bytecode.VECTOR_I16X8_LE_U, Bytecode.VECTOR_I16X8_GE_S, Bytecode.VECTOR_I16X8_GE_U ->
                i16x8_relop(x, y, vectorOpcode);
            case Bytecode.VECTOR_I32X4_EQ, Bytecode.VECTOR_I32X4_NE, Bytecode.VECTOR_I32X4_LT_S, Bytecode.VECTOR_I32X4_LT_U, Bytecode.VECTOR_I32X4_GT_S, Bytecode.VECTOR_I32X4_GT_U,
                            Bytecode.VECTOR_I32X4_LE_S, Bytecode.VECTOR_I32X4_LE_U, Bytecode.VECTOR_I32X4_GE_S, Bytecode.VECTOR_I32X4_GE_U ->
                i32x4_relop(x, y, vectorOpcode);
            case Bytecode.VECTOR_I64X2_EQ, Bytecode.VECTOR_I64X2_NE, Bytecode.VECTOR_I64X2_LT_S, Bytecode.VECTOR_I64X2_GT_S, Bytecode.VECTOR_I64X2_LE_S, Bytecode.VECTOR_I64X2_GE_S ->
                i64x2_relop(x, y, vectorOpcode);
            case Bytecode.VECTOR_F32X4_EQ, Bytecode.VECTOR_F32X4_NE, Bytecode.VECTOR_F32X4_LT, Bytecode.VECTOR_F32X4_GT, Bytecode.VECTOR_F32X4_LE, Bytecode.VECTOR_F32X4_GE ->
                f32x4_relop(x, y, vectorOpcode);
            case Bytecode.VECTOR_F64X2_EQ, Bytecode.VECTOR_F64X2_NE, Bytecode.VECTOR_F64X2_LT, Bytecode.VECTOR_F64X2_GT, Bytecode.VECTOR_F64X2_LE, Bytecode.VECTOR_F64X2_GE ->
                f64x2_relop(x, y, vectorOpcode);
            case Bytecode.VECTOR_I8X16_NARROW_I16X8_S, Bytecode.VECTOR_I8X16_NARROW_I16X8_U -> i8x16_narrow_i16x8(x, y, vectorOpcode);
            case Bytecode.VECTOR_I8X16_ADD, Bytecode.VECTOR_I8X16_ADD_SAT_S, Bytecode.VECTOR_I8X16_ADD_SAT_U, Bytecode.VECTOR_I8X16_SUB, Bytecode.VECTOR_I8X16_SUB_SAT_S,
                            Bytecode.VECTOR_I8X16_SUB_SAT_U, Bytecode.VECTOR_I8X16_MIN_S, Bytecode.VECTOR_I8X16_MIN_U, Bytecode.VECTOR_I8X16_MAX_S, Bytecode.VECTOR_I8X16_MAX_U,
                            Bytecode.VECTOR_I8X16_AVGR_U ->
                i8x16_binop(x, y, vectorOpcode);
            case Bytecode.VECTOR_I16X8_NARROW_I32X4_S, Bytecode.VECTOR_I16X8_NARROW_I32X4_U -> i16x8_narrow_i32x4(x, y, vectorOpcode);
            case Bytecode.VECTOR_I16X8_Q15MULR_SAT_S, Bytecode.VECTOR_I16X8_ADD, Bytecode.VECTOR_I16X8_ADD_SAT_S, Bytecode.VECTOR_I16X8_ADD_SAT_U, Bytecode.VECTOR_I16X8_SUB,
                            Bytecode.VECTOR_I16X8_SUB_SAT_S, Bytecode.VECTOR_I16X8_SUB_SAT_U, Bytecode.VECTOR_I16X8_MUL, Bytecode.VECTOR_I16X8_MIN_S, Bytecode.VECTOR_I16X8_MIN_U,
                            Bytecode.VECTOR_I16X8_MAX_S, Bytecode.VECTOR_I16X8_MAX_U, Bytecode.VECTOR_I16X8_AVGR_U ->
                i16x8_binop(x, y, vectorOpcode);
            case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_S, Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_U -> i16x8_binop_extend_low_i8x16(x, y, vectorOpcode);
            case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_S, Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_U -> i16x8_binop_extend_high_i8x16(x, y, vectorOpcode);
            case Bytecode.VECTOR_I32X4_ADD, Bytecode.VECTOR_I32X4_SUB, Bytecode.VECTOR_I32X4_MUL, Bytecode.VECTOR_I32X4_MIN_S, Bytecode.VECTOR_I32X4_MIN_U, Bytecode.VECTOR_I32X4_MAX_S,
                            Bytecode.VECTOR_I32X4_MAX_U ->
                i32x4_binop(x, y, vectorOpcode);
            case Bytecode.VECTOR_I32X4_DOT_I16X8_S -> i32x4_dot_i16x8_s(x, y);
            case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_S, Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_U -> i32x4_binop_extend_low_i16x8(x, y, vectorOpcode);
            case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_S, Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_U -> i32x4_binop_extend_high_i16x8(x, y, vectorOpcode);
            case Bytecode.VECTOR_I64X2_ADD, Bytecode.VECTOR_I64X2_SUB, Bytecode.VECTOR_I64X2_MUL -> i64x2_binop(x, y, vectorOpcode);
            case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_S, Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_U -> i64x2_binop_extend_low_i32x4(x, y, vectorOpcode);
            case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_S, Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_U -> i64x2_binop_extend_high_i32x4(x, y, vectorOpcode);
            case Bytecode.VECTOR_F32X4_ADD, Bytecode.VECTOR_F32X4_SUB, Bytecode.VECTOR_F32X4_MUL, Bytecode.VECTOR_F32X4_DIV, Bytecode.VECTOR_F32X4_MIN, Bytecode.VECTOR_F32X4_MAX,
                            Bytecode.VECTOR_F32X4_PMIN, Bytecode.VECTOR_F32X4_PMAX ->
                f32x4_binop(x, y, vectorOpcode);
            case Bytecode.VECTOR_F64X2_ADD, Bytecode.VECTOR_F64X2_SUB, Bytecode.VECTOR_F64X2_MUL, Bytecode.VECTOR_F64X2_DIV, Bytecode.VECTOR_F64X2_MIN, Bytecode.VECTOR_F64X2_MAX,
                            Bytecode.VECTOR_F64X2_PMIN, Bytecode.VECTOR_F64X2_PMAX ->
                f64x2_binop(x, y, vectorOpcode);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static byte[] ternary(byte[] x, byte[] y, byte[] z, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_BITSELECT -> v128_bitselect(x, y, z);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static int vectorToInt(byte[] x, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_ANY_TRUE -> v128_any_true(x);
            case Bytecode.VECTOR_I8X16_ALL_TRUE -> i8x16_all_true(x);
            case Bytecode.VECTOR_I8X16_BITMASK -> i8x16_bitmask(x);
            case Bytecode.VECTOR_I16X8_ALL_TRUE -> i16x8_all_true(x);
            case Bytecode.VECTOR_I16X8_BITMASK -> i16x8_bitmask(x);
            case Bytecode.VECTOR_I32X4_ALL_TRUE -> i32x4_all_true(x);
            case Bytecode.VECTOR_I32X4_BITMASK -> i32x4_bitmask(x);
            case Bytecode.VECTOR_I64X2_ALL_TRUE -> i64x2_all_true(x);
            case Bytecode.VECTOR_I64X2_BITMASK -> i64x2_bitmask(x);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static byte[] shift(byte[] x, int shift, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I8X16_SHL, Bytecode.VECTOR_I8X16_SHR_S, Bytecode.VECTOR_I8X16_SHR_U -> i8x16_shiftop(x, shift, vectorOpcode);
            case Bytecode.VECTOR_I16X8_SHL, Bytecode.VECTOR_I16X8_SHR_S, Bytecode.VECTOR_I16X8_SHR_U -> i16x8_shiftop(x, shift, vectorOpcode);
            case Bytecode.VECTOR_I32X4_SHL, Bytecode.VECTOR_I32X4_SHR_S, Bytecode.VECTOR_I32X4_SHR_U -> i32x4_shiftop(x, shift, vectorOpcode);
            case Bytecode.VECTOR_I64X2_SHL, Bytecode.VECTOR_I64X2_SHR_S, Bytecode.VECTOR_I64X2_SHR_U -> i64x2_shiftop(x, shift, vectorOpcode);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    // Checkstyle: stop method name check

    public static byte[] v128_const(byte[] vec) {
        return vec;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i8x16_shuffle(byte[] x, byte[] y, byte[] indices) {
        byte[] result = new byte[16];
        for (int i = 0; i < result.length; i++) {
            result[i] = indices[i] < 16 ? x[indices[i]] : y[indices[i] - 16];
        }
        return result;
    }

    public static int i8x16_extract_lane(byte[] bytes, int laneIndex, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I8X16_EXTRACT_LANE_S -> bytes[laneIndex];
            case Bytecode.VECTOR_I8X16_EXTRACT_LANE_U -> Byte.toUnsignedInt(bytes[laneIndex]);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static byte[] i8x16_replace_lane(byte[] bytes, int laneIndex, byte value) {
        byte[] result = Arrays.copyOf(bytes, 16);
        result[laneIndex] = value;
        return result;
    }

    public static int i16x8_extract_lane(byte[] vec, int laneIndex, int vectorOpcode) {
        short x = byteArraySupport.getShort(vec, laneIndex * 2);
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I16X8_EXTRACT_LANE_S -> x;
            case Bytecode.VECTOR_I16X8_EXTRACT_LANE_U -> Short.toUnsignedInt(x);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static byte[] i16x8_replace_lane(byte[] vec, int laneIndex, short value) {
        byte[] result = Arrays.copyOf(vec, 16);
        byteArraySupport.putShort(result, laneIndex * 2, value);
        return result;
    }

    public static int i32x4_extract_lane(byte[] vec, int laneIndex) {
        return byteArraySupport.getInt(vec, laneIndex * 4);
    }

    public static byte[] i32x4_replace_lane(byte[] vec, int laneIndex, int value) {
        byte[] result = Arrays.copyOf(vec, 16);
        byteArraySupport.putInt(result, laneIndex * 4, value);
        return result;
    }

    public static long i64x2_extract_lane(byte[] vec, int laneIndex) {
        return byteArraySupport.getLong(vec, laneIndex * 8);
    }

    public static byte[] i64x2_replace_lane(byte[] vec, int laneIndex, long value) {
        byte[] result = Arrays.copyOf(vec, 16);
        byteArraySupport.putLong(result, laneIndex * 8, value);
        return result;
    }

    public static float f32x4_extract_lane(byte[] vec, int laneIndex) {
        return byteArraySupport.getFloat(vec, laneIndex * 4);
    }

    public static byte[] f32x4_replace_lane(byte[] vec, int laneIndex, float value) {
        byte[] result = Arrays.copyOf(vec, 16);
        byteArraySupport.putFloat(result, laneIndex * 4, value);
        return result;
    }

    public static double f64x2_extract_lane(byte[] vec, int laneIndex) {
        return byteArraySupport.getDouble(vec, laneIndex * 8);
    }

    public static byte[] f64x2_replace_lane(byte[] vec, int laneIndex, double value) {
        byte[] result = Arrays.copyOf(vec, 16);
        byteArraySupport.putDouble(result, laneIndex * 8, value);
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i8x16_swizzle(byte[] values, byte[] indices) {
        byte[] result = new byte[16];
        for (int i = 0; i < result.length; i++) {
            int index = Byte.toUnsignedInt(indices[i]);
            result[i] = index < 16 ? values[index] : 0;
        }
        return result;
    }

    public static byte[] i8x16_splat(byte x) {
        byte[] result = new byte[16];
        Arrays.fill(result, x);
        return result;
    }

    public static byte[] i16x8_splat(short x) {
        byte[] result = new byte[16];
        for (int i = 0; i < 8; i++) {
            byteArraySupport.putShort(result, i * 2, x);
        }
        return result;
    }

    public static byte[] i32x4_splat(int x) {
        byte[] result = new byte[16];
        for (int i = 0; i < 4; i++) {
            byteArraySupport.putInt(result, i * 4, x);
        }
        return result;
    }

    public static byte[] i64x2_splat(long x) {
        byte[] result = new byte[16];
        for (int i = 0; i < 2; i++) {
            byteArraySupport.putLong(result, i * 8, x);
        }
        return result;
    }

    public static byte[] f32x4_splat(float x) {
        byte[] result = new byte[16];
        for (int i = 0; i < 4; i++) {
            byteArraySupport.putFloat(result, i * 4, x);
        }
        return result;
    }

    public static byte[] f64x2_splat(double x) {
        byte[] result = new byte[16];
        for (int i = 0; i < 2; i++) {
            byteArraySupport.putDouble(result, i * 8, x);
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] v128_not(byte[] x) {
        byte[] result = new byte[16];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) ~x[i];
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] v128_binop(byte[] x, byte[] y, int vectorOpcode) {
        byte[] result = new byte[16];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) switch (vectorOpcode) {
                case Bytecode.VECTOR_V128_AND -> x[i] & y[i];
                case Bytecode.VECTOR_V128_ANDNOT -> x[i] & ~y[i];
                case Bytecode.VECTOR_V128_OR -> x[i] | y[i];
                case Bytecode.VECTOR_V128_XOR -> x[i] ^ y[i];
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] v128_bitselect(byte[] x, byte[] y, byte[] mask) {
        byte[] result = new byte[16];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (x[i] & mask[i] | (y[i] & ~mask[i]));
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int v128_any_true(byte[] vec) {
        int result = 0;
        for (int i = 0; i < vec.length; i++) {
            if (vec[i] != 0) {
                result = 1;
                break;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i8x16_relop(byte[] x, byte[] y, int vectorOpcode) {
        byte[] result = new byte[16];
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I8X16_EQ -> x[i] == y[i];
                case Bytecode.VECTOR_I8X16_NE -> x[i] != y[i];
                case Bytecode.VECTOR_I8X16_LT_S -> x[i] < y[i];
                case Bytecode.VECTOR_I8X16_LT_U -> Byte.compareUnsigned(x[i], y[i]) < 0;
                case Bytecode.VECTOR_I8X16_GT_S -> x[i] > y[i];
                case Bytecode.VECTOR_I8X16_GT_U -> Byte.compareUnsigned(x[i], y[i]) > 0;
                case Bytecode.VECTOR_I8X16_LE_S -> x[i] <= y[i];
                case Bytecode.VECTOR_I8X16_LE_U -> Byte.compareUnsigned(x[i], y[i]) <= 0;
                case Bytecode.VECTOR_I8X16_GE_S -> x[i] >= y[i];
                case Bytecode.VECTOR_I8X16_GE_U -> Byte.compareUnsigned(x[i], y[i]) >= 0;
                default -> throw CompilerDirectives.shouldNotReachHere();
            } ? (byte) 0xff : (byte) 0x00;
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 8; i++) {
            short x = byteArraySupport.getShort(vecX, i * 2);
            short y = byteArraySupport.getShort(vecY, i * 2);
            short result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_EQ -> x == y;
                case Bytecode.VECTOR_I16X8_NE -> x != y;
                case Bytecode.VECTOR_I16X8_LT_S -> x < y;
                case Bytecode.VECTOR_I16X8_LT_U -> Short.compareUnsigned(x, y) < 0;
                case Bytecode.VECTOR_I16X8_GT_S -> x > y;
                case Bytecode.VECTOR_I16X8_GT_U -> Short.compareUnsigned(x, y) > 0;
                case Bytecode.VECTOR_I16X8_LE_S -> x <= y;
                case Bytecode.VECTOR_I16X8_LE_U -> Short.compareUnsigned(x, y) <= 0;
                case Bytecode.VECTOR_I16X8_GE_S -> x >= y;
                case Bytecode.VECTOR_I16X8_GE_U -> Short.compareUnsigned(x, y) >= 0;
                default -> throw CompilerDirectives.shouldNotReachHere();
            } ? (short) 0xffff : (short) 0x0000;
            byteArraySupport.putShort(vecResult, i * 2, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            int x = byteArraySupport.getInt(vecX, i * 4);
            int y = byteArraySupport.getInt(vecY, i * 4);
            int result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_EQ -> x == y;
                case Bytecode.VECTOR_I32X4_NE -> x != y;
                case Bytecode.VECTOR_I32X4_LT_S -> x < y;
                case Bytecode.VECTOR_I32X4_LT_U -> Integer.compareUnsigned(x, y) < 0;
                case Bytecode.VECTOR_I32X4_GT_S -> x > y;
                case Bytecode.VECTOR_I32X4_GT_U -> Integer.compareUnsigned(x, y) > 0;
                case Bytecode.VECTOR_I32X4_LE_S -> x <= y;
                case Bytecode.VECTOR_I32X4_LE_U -> Integer.compareUnsigned(x, y) <= 0;
                case Bytecode.VECTOR_I32X4_GE_S -> x >= y;
                case Bytecode.VECTOR_I32X4_GE_U -> Integer.compareUnsigned(x, y) >= 0;
                default -> throw CompilerDirectives.shouldNotReachHere();
            } ? 0xffff_ffff : 0x0000_0000;
            byteArraySupport.putInt(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i64x2_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            long x = byteArraySupport.getLong(vecX, i * 8);
            long y = byteArraySupport.getLong(vecY, i * 8);
            long result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I64X2_EQ -> x == y;
                case Bytecode.VECTOR_I64X2_NE -> x != y;
                case Bytecode.VECTOR_I64X2_LT_S -> x < y;
                case Bytecode.VECTOR_I64X2_GT_S -> x > y;
                case Bytecode.VECTOR_I64X2_LE_S -> x <= y;
                case Bytecode.VECTOR_I64X2_GE_S -> x >= y;
                default -> throw CompilerDirectives.shouldNotReachHere();
            } ? 0xffff_ffff_ffff_ffffL : 0x0000_0000_0000_0000l;
            byteArraySupport.putLong(vecResult, i * 8, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f32x4_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            float x = byteArraySupport.getFloat(vecX, i * 4);
            float y = byteArraySupport.getFloat(vecY, i * 4);
            int result = switch (vectorOpcode) {
                case Bytecode.VECTOR_F32X4_EQ -> x == y;
                case Bytecode.VECTOR_F32X4_NE -> x != y;
                case Bytecode.VECTOR_F32X4_LT -> x < y;
                case Bytecode.VECTOR_F32X4_GT -> x > y;
                case Bytecode.VECTOR_F32X4_LE -> x <= y;
                case Bytecode.VECTOR_F32X4_GE -> x >= y;
                default -> throw CompilerDirectives.shouldNotReachHere();
            } ? 0xffff_ffff : 0x0000_0000;
            byteArraySupport.putInt(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f64x2_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            double x = byteArraySupport.getDouble(vecX, i * 8);
            double y = byteArraySupport.getDouble(vecY, i * 8);
            long result = switch (vectorOpcode) {
                case Bytecode.VECTOR_F64X2_EQ -> x == y;
                case Bytecode.VECTOR_F64X2_NE -> x != y;
                case Bytecode.VECTOR_F64X2_LT -> x < y;
                case Bytecode.VECTOR_F64X2_GT -> x > y;
                case Bytecode.VECTOR_F64X2_LE -> x <= y;
                case Bytecode.VECTOR_F64X2_GE -> x >= y;
                default -> throw CompilerDirectives.shouldNotReachHere();
            } ? 0xffff_ffff_ffff_ffffL : 0x0000_0000_0000_0000L;
            byteArraySupport.putLong(vecResult, i * 8, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i8x16_unop(byte[] x, int vectorOpcode) {
        byte[] result = new byte[16];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) switch (vectorOpcode) {
                case Bytecode.VECTOR_I8X16_ABS -> Math.abs(x[i]);
                case Bytecode.VECTOR_I8X16_NEG -> -x[i];
                case Bytecode.VECTOR_I8X16_POPCNT -> Integer.bitCount(Byte.toUnsignedInt(x[i]));
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int i8x16_all_true(byte[] bytes) {
        int result = 1;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                result = 0;
                break;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int i8x16_bitmask(byte[] bytes) {
        int result = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] < 0) {
                result |= 1 << i;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i8x16_narrow_i16x8(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] result = new byte[16];
        for (int i = 0; i < result.length; i++) {
            byte[] src = i < 8 ? vecX : vecY;
            int index = i < 8 ? i : i - 8;
            short srcValue = byteArraySupport.getShort(src, index * 2);
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I8X16_NARROW_I16X8_S -> satS8(srcValue);
                case Bytecode.VECTOR_I8X16_NARROW_I16X8_U -> satU8(srcValue);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i8x16_shiftop(byte[] x, int shift, int vectorOpcode) {
        byte[] result = new byte[16];
        int shiftMod = shift % 8;
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) switch (vectorOpcode) {
                case Bytecode.VECTOR_I8X16_SHL -> x[i] << shiftMod;
                case Bytecode.VECTOR_I8X16_SHR_S -> x[i] >> shiftMod;
                case Bytecode.VECTOR_I8X16_SHR_U -> Byte.toUnsignedInt(x[i]) >>> shiftMod;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i8x16_binop(byte[] x, byte[] y, int vectorOpcode) {
        byte[] result = new byte[16];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) switch (vectorOpcode) {
                case Bytecode.VECTOR_I8X16_ADD -> x[i] + y[i];
                case Bytecode.VECTOR_I8X16_ADD_SAT_S -> satS8(x[i] + y[i]);
                case Bytecode.VECTOR_I8X16_ADD_SAT_U -> satU8(Byte.toUnsignedInt(x[i]) + Byte.toUnsignedInt(y[i]));
                case Bytecode.VECTOR_I8X16_SUB -> x[i] - y[i];
                case Bytecode.VECTOR_I8X16_SUB_SAT_S -> satS8(x[i] - y[i]);
                case Bytecode.VECTOR_I8X16_SUB_SAT_U -> satU8(Byte.toUnsignedInt(x[i]) - Byte.toUnsignedInt(y[i]));
                case Bytecode.VECTOR_I8X16_MIN_S -> Math.min(x[i], y[i]);
                case Bytecode.VECTOR_I8X16_MIN_U -> Byte.compareUnsigned(x[i], y[i]) <= 0 ? x[i] : y[i];
                case Bytecode.VECTOR_I8X16_MAX_S -> Math.max(x[i], y[i]);
                case Bytecode.VECTOR_I8X16_MAX_U -> Byte.compareUnsigned(x[i], y[i]) >= 0 ? x[i] : y[i];
                case Bytecode.VECTOR_I8X16_AVGR_U -> (Byte.toUnsignedInt(x[i]) + Byte.toUnsignedInt(y[i]) + 1) / 2;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_extadd_pairwise_i8x16(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 8; i++) {
            byte x1 = vecX[2 * i];
            byte x2 = vecX[2 * i + 1];
            short result = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_S -> x1 + x2;
                case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_U -> Byte.toUnsignedInt(x1) + Byte.toUnsignedInt(x2);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putShort(vecResult, i * 2, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_extend_low_i8x16(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 8; i++) {
            byte x = vecX[i];
            short result = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_S -> x;
                case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_U -> Byte.toUnsignedInt(x);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putShort(vecResult, i * 2, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_extend_high_i8x16(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 8; i++) {
            byte x = vecX[i + 8];
            short result = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_S -> x;
                case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_U -> Byte.toUnsignedInt(x);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putShort(vecResult, i * 2, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_unop(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 8; i++) {
            short x = byteArraySupport.getShort(vecX, i * 2);
            short result = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_ABS -> Math.abs(x);
                case Bytecode.VECTOR_I16X8_NEG -> -x;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putShort(vecResult, i * 2, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int i16x8_all_true(byte[] vec) {
        int result = 1;
        for (int i = 0; i < 8; i++) {
            short x = byteArraySupport.getShort(vec, i * 2);
            if (x == 0) {
                result = 0;
                break;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int i16x8_bitmask(byte[] vec) {
        int result = 0;
        for (int i = 0; i < 8; i++) {
            short x = byteArraySupport.getShort(vec, i * 2);
            if (x < 0) {
                result |= 1 << i;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_narrow_i32x4(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 8; i++) {
            byte[] src = i < 4 ? vecX : vecY;
            int index = i < 4 ? i : i - 4;
            int srcValue = byteArraySupport.getInt(src, index * 4);
            short result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_NARROW_I32X4_S -> satS16(srcValue);
                case Bytecode.VECTOR_I16X8_NARROW_I32X4_U -> satU16(srcValue);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putShort(vecResult, i * 2, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_shiftop(byte[] vecX, int shift, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        int shiftMod = shift % 16;
        for (int i = 0; i < 8; i++) {
            short x = byteArraySupport.getShort(vecX, i * 2);
            short result = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_SHL -> x << shiftMod;
                case Bytecode.VECTOR_I16X8_SHR_S -> x >> shiftMod;
                case Bytecode.VECTOR_I16X8_SHR_U -> Short.toUnsignedInt(x) >>> shiftMod;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putShort(vecResult, i * 2, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 8; i++) {
            short x = byteArraySupport.getShort(vecX, i * 2);
            short y = byteArraySupport.getShort(vecY, i * 2);
            short result = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_Q15MULR_SAT_S -> satS16((x * y + (1 << 14)) >> 15);
                case Bytecode.VECTOR_I16X8_ADD -> x + y;
                case Bytecode.VECTOR_I16X8_ADD_SAT_S -> satS16(x + y);
                case Bytecode.VECTOR_I16X8_ADD_SAT_U -> satU16(Short.toUnsignedInt(x) + Short.toUnsignedInt(y));
                case Bytecode.VECTOR_I16X8_SUB -> x - y;
                case Bytecode.VECTOR_I16X8_SUB_SAT_S -> satS16(x - y);
                case Bytecode.VECTOR_I16X8_SUB_SAT_U -> satU16(Short.toUnsignedInt(x) - Short.toUnsignedInt(y));
                case Bytecode.VECTOR_I16X8_MUL -> x * y;
                case Bytecode.VECTOR_I16X8_MIN_S -> Math.min(x, y);
                case Bytecode.VECTOR_I16X8_MIN_U -> Short.compareUnsigned(x, y) <= 0 ? x : y;
                case Bytecode.VECTOR_I16X8_MAX_S -> Math.max(x, y);
                case Bytecode.VECTOR_I16X8_MAX_U -> Short.compareUnsigned(x, y) >= 0 ? x : y;
                case Bytecode.VECTOR_I16X8_AVGR_U -> (Short.toUnsignedInt(x) + Short.toUnsignedInt(y) + 1) / 2;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putShort(vecResult, i * 2, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_binop_extend_low_i8x16(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 8; i++) {
            byte x = vecX[i];
            byte y = vecY[i];
            short result = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_S -> x * y;
                case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_U -> Byte.toUnsignedInt(x) * Byte.toUnsignedInt(y);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putShort(vecResult, i * 2, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_binop_extend_high_i8x16(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 8; i++) {
            byte x = vecX[i + 8];
            byte y = vecY[i + 8];
            short result = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_S -> x * y;
                case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_U -> Byte.toUnsignedInt(x) * Byte.toUnsignedInt(y);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putShort(vecResult, i * 2, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_extadd_pairwise_i16x8(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            short x1 = byteArraySupport.getShort(vecX, (i * 2) * 2);
            short x2 = byteArraySupport.getShort(vecX, (i * 2 + 1) * 2);
            int result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_S -> x1 + x2;
                case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_U -> Short.toUnsignedInt(x1) + Short.toUnsignedInt(x2);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putInt(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_extend_low_i16x8(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            short x = byteArraySupport.getShort(vecX, i * 2);
            int result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_S -> x;
                case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_U -> Short.toUnsignedInt(x);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putInt(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_extend_high_i16x8(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            short x = byteArraySupport.getShort(vecX, (i + 4) * 2);
            int result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_S -> x;
                case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_U -> Short.toUnsignedInt(x);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putInt(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_unop(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            int x = byteArraySupport.getInt(vecX, i * 4);
            int result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_ABS -> Math.abs(x);
                case Bytecode.VECTOR_I32X4_NEG -> -x;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putInt(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int i32x4_all_true(byte[] vec) {
        int result = 1;
        for (int i = 0; i < 4; i++) {
            int x = byteArraySupport.getInt(vec, i * 4);
            if (x == 0) {
                result = 0;
                break;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int i32x4_bitmask(byte[] vec) {
        int result = 0;
        for (int i = 0; i < 4; i++) {
            int x = byteArraySupport.getInt(vec, i * 4);
            if (x < 0) {
                result |= 1 << i;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_shiftop(byte[] vecX, int shift, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            int x = byteArraySupport.getInt(vecX, i * 4);
            int result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_SHL -> x << shift;
                case Bytecode.VECTOR_I32X4_SHR_S -> x >> shift;
                case Bytecode.VECTOR_I32X4_SHR_U -> x >>> shift;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putInt(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            int x = byteArraySupport.getInt(vecX, i * 4);
            int y = byteArraySupport.getInt(vecY, i * 4);
            int result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_ADD -> x + y;
                case Bytecode.VECTOR_I32X4_SUB -> x - y;
                case Bytecode.VECTOR_I32X4_MUL -> x * y;
                case Bytecode.VECTOR_I32X4_MIN_S -> Math.min(x, y);
                case Bytecode.VECTOR_I32X4_MIN_U -> Integer.compareUnsigned(x, y) <= 0 ? x : y;
                case Bytecode.VECTOR_I32X4_MAX_S -> Math.max(x, y);
                case Bytecode.VECTOR_I32X4_MAX_U -> Integer.compareUnsigned(x, y) >= 0 ? x : y;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putInt(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_dot_i16x8_s(byte[] vecX, byte[] vecY) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            short x1 = byteArraySupport.getShort(vecX, (i * 2) * 2);
            short x2 = byteArraySupport.getShort(vecX, (i * 2 + 1) * 2);
            short y1 = byteArraySupport.getShort(vecY, (i * 2) * 2);
            short y2 = byteArraySupport.getShort(vecY, (i * 2 + 1) * 2);
            int result = x1 * y1 + x2 * y2;
            byteArraySupport.putInt(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_binop_extend_low_i16x8(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            short x = byteArraySupport.getShort(vecX, i * 2);
            short y = byteArraySupport.getShort(vecY, i * 2);
            int result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_S -> x * y;
                case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_U -> Short.toUnsignedInt(x) * Short.toUnsignedInt(y);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putInt(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_binop_extend_high_i16x8(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            short x = byteArraySupport.getShort(vecX, (i + 4) * 2);
            short y = byteArraySupport.getShort(vecY, (i + 4) * 2);
            int result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_S -> x * y;
                case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_U -> Short.toUnsignedInt(x) * Short.toUnsignedInt(y);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putInt(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i64x2_extend_low_i32x4(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            int x = byteArraySupport.getInt(vecX, i * 4);
            long result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_S -> x;
                case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_U -> Integer.toUnsignedLong(x);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putLong(vecResult, i * 8, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i64x2_extend_high_i32x4(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            int x = byteArraySupport.getInt(vecX, (i + 2) * 4);
            long result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_S -> x;
                case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_U -> Integer.toUnsignedLong(x);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putLong(vecResult, i * 8, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i64x2_unop(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            long x = byteArraySupport.getLong(vecX, i * 8);
            long result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I64X2_ABS -> Math.abs(x);
                case Bytecode.VECTOR_I64X2_NEG -> -x;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putLong(vecResult, i * 8, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int i64x2_all_true(byte[] vec) {
        int result = 1;
        for (int i = 0; i < 2; i++) {
            long x = byteArraySupport.getLong(vec, i * 8);
            if (x == 0) {
                result = 0;
                break;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int i64x2_bitmask(byte[] vec) {
        int result = 0;
        for (int i = 0; i < 2; i++) {
            long x = byteArraySupport.getLong(vec, i * 8);
            if (x < 0) {
                result |= 1 << i;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i64x2_shiftop(byte[] vecX, int shift, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            long x = byteArraySupport.getLong(vecX, i * 8);
            long result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I64X2_SHL -> x << shift;
                case Bytecode.VECTOR_I64X2_SHR_S -> x >> shift;
                case Bytecode.VECTOR_I64X2_SHR_U -> x >>> shift;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putLong(vecResult, i * 8, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i64x2_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            long x = byteArraySupport.getLong(vecX, i * 8);
            long y = byteArraySupport.getLong(vecY, i * 8);
            long result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I64X2_ADD -> x + y;
                case Bytecode.VECTOR_I64X2_SUB -> x - y;
                case Bytecode.VECTOR_I64X2_MUL -> x * y;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putLong(vecResult, i * 8, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i64x2_binop_extend_low_i32x4(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            int x = byteArraySupport.getInt(vecX, i * 4);
            int y = byteArraySupport.getInt(vecY, i * 4);
            long result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_S -> (long) x * (long) y;
                case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_U -> Integer.toUnsignedLong(x) * Integer.toUnsignedLong(y);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putLong(vecResult, i * 8, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i64x2_binop_extend_high_i32x4(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            int x = byteArraySupport.getInt(vecX, (i + 2) * 4);
            int y = byteArraySupport.getInt(vecY, (i + 2) * 4);
            long result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_S -> (long) x * (long) y;
                case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_U -> Integer.toUnsignedLong(x) * Integer.toUnsignedLong(y);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putLong(vecResult, i * 8, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f32x4_unop(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            float x = byteArraySupport.getFloat(vecX, i * 4);
            float result = switch (vectorOpcode) {
                case Bytecode.VECTOR_F32X4_ABS -> Math.abs(x);
                case Bytecode.VECTOR_F32X4_NEG -> -x;
                case Bytecode.VECTOR_F32X4_SQRT -> (float) Math.sqrt(x);
                case Bytecode.VECTOR_F32X4_CEIL -> (float) Math.ceil(x);
                case Bytecode.VECTOR_F32X4_FLOOR -> (float) Math.floor(x);
                case Bytecode.VECTOR_F32X4_TRUNC -> ExactMath.truncate(x);
                case Bytecode.VECTOR_F32X4_NEAREST -> (float) Math.rint(x);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putFloat(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f32x4_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            float x = byteArraySupport.getFloat(vecX, i * 4);
            float y = byteArraySupport.getFloat(vecY, i * 4);
            float result = switch (vectorOpcode) {
                case Bytecode.VECTOR_F32X4_ADD -> x + y;
                case Bytecode.VECTOR_F32X4_SUB -> x - y;
                case Bytecode.VECTOR_F32X4_MUL -> x * y;
                case Bytecode.VECTOR_F32X4_DIV -> x / y;
                case Bytecode.VECTOR_F32X4_MIN -> Math.min(x, y);
                case Bytecode.VECTOR_F32X4_MAX -> Math.max(x, y);
                case Bytecode.VECTOR_F32X4_PMIN -> y < x ? y : x;
                case Bytecode.VECTOR_F32X4_PMAX -> x < y ? y : x;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putFloat(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f64x2_unop(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            double x = byteArraySupport.getDouble(vecX, i * 8);
            double result = switch (vectorOpcode) {
                case Bytecode.VECTOR_F64X2_ABS -> Math.abs(x);
                case Bytecode.VECTOR_F64X2_NEG -> -x;
                case Bytecode.VECTOR_F64X2_SQRT -> Math.sqrt(x);
                case Bytecode.VECTOR_F64X2_CEIL -> Math.ceil(x);
                case Bytecode.VECTOR_F64X2_FLOOR -> Math.floor(x);
                case Bytecode.VECTOR_F64X2_TRUNC -> ExactMath.truncate(x);
                case Bytecode.VECTOR_F64X2_NEAREST -> Math.rint(x);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putDouble(vecResult, i * 8, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f64x2_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            double x = byteArraySupport.getDouble(vecX, i * 8);
            double y = byteArraySupport.getDouble(vecY, i * 8);
            double result = switch (vectorOpcode) {
                case Bytecode.VECTOR_F64X2_ADD -> x + y;
                case Bytecode.VECTOR_F64X2_SUB -> x - y;
                case Bytecode.VECTOR_F64X2_MUL -> x * y;
                case Bytecode.VECTOR_F64X2_DIV -> x / y;
                case Bytecode.VECTOR_F64X2_MIN -> Math.min(x, y);
                case Bytecode.VECTOR_F64X2_MAX -> Math.max(x, y);
                case Bytecode.VECTOR_F64X2_PMIN -> y < x ? y : x;
                case Bytecode.VECTOR_F64X2_PMAX -> x < y ? y : x;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putDouble(vecResult, i * 8, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_trunc_sat_f32x4(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            float x = byteArraySupport.getFloat(vecX, i * 4);
            int result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_S -> (int) x;
                case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_U -> truncSatU32(x);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putInt(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f32x4_convert_i32x4(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 4; i++) {
            int x = byteArraySupport.getInt(vecX, i * 4);
            float result = switch (vectorOpcode) {
                case Bytecode.VECTOR_F32X4_CONVERT_I32X4_S -> x;
                case Bytecode.VECTOR_F32X4_CONVERT_I32X4_U -> Integer.toUnsignedLong(x);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putFloat(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_trunc_sat_f64x2_zero(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            double x = byteArraySupport.getDouble(vecX, i * 8);
            int result = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_S_ZERO -> (int) x;
                case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_U_ZERO -> truncSatU32(x);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putInt(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f64x2_convert_low_i32x4(byte[] vecX, int vectorOpcode) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            int x = byteArraySupport.getInt(vecX, i * 4);
            double result = switch (vectorOpcode) {
                case Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_S -> x;
                case Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_U -> Integer.toUnsignedLong(x);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
            byteArraySupport.putDouble(vecResult, i * 8, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f32x4_demote_f64x2_zero(byte[] vecX) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            double x = byteArraySupport.getDouble(vecX, i * 8);
            float result = (float) x;
            byteArraySupport.putFloat(vecResult, i * 4, result);
        }
        return vecResult;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f64x2_promote_low_f32x4(byte[] vecX) {
        byte[] vecResult = new byte[16];
        for (int i = 0; i < 2; i++) {
            float x = byteArraySupport.getFloat(vecX, i * 4);
            double result = x;
            byteArraySupport.putDouble(vecResult, i * 8, result);
        }
        return vecResult;
    }

    // Checkstyle: resume method name check

    private static byte satS8(int x) {
        if (x > Byte.MAX_VALUE) {
            return Byte.MAX_VALUE;
        } else if (x < Byte.MIN_VALUE) {
            return Byte.MIN_VALUE;
        } else {
            return (byte) x;
        }
    }

    private static byte satU8(int x) {
        if (x > 0xff) {
            return (byte) 0xff;
        } else if (x < 0) {
            return 0;
        } else {
            return (byte) x;
        }
    }

    private static short satS16(int x) {
        if (x > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        } else if (x < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        } else {
            return (short) x;
        }
    }

    private static short satU16(int x) {
        if (x > 0xffff) {
            return (short) 0xffff;
        } else if (x < 0) {
            return 0;
        } else {
            return (short) x;
        }
    }

    private static int truncSatU32(double x) {
        if (Double.isNaN(x) || x < 0) {
            return 0;
        } else if (x > 0xffff_ffffL) {
            return 0xffff_ffff;
        } else {
            return (int) (long) ExactMath.truncate(x);
        }
    }
}
