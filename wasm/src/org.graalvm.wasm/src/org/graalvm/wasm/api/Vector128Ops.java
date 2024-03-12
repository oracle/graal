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
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.graalvm.wasm.constants.Bytecode;

import java.util.Arrays;

public class Vector128Ops {

    public static byte[] unary(byte[] x, int vectorOpcode) {
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_V128_NOT -> v128_not(x);
            case Bytecode.VECTOR_I8X16_ABS, Bytecode.VECTOR_I8X16_NEG, Bytecode.VECTOR_I8X16_POPCNT -> i8x16_unop(x, vectorOpcode);
            case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_S, Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_U, Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_S, Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_S,
                            Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_U, Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_U ->
                i16x8_extend_i8x16(x, vectorOpcode);
            case Bytecode.VECTOR_I16X8_ABS, Bytecode.VECTOR_I16X8_NEG -> i16x8_unop(x, vectorOpcode);
            case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_S, Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_U, Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_S, Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_S,
                            Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_U, Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_U ->
                i32x4_extend_i16x8(x, vectorOpcode);
            case Bytecode.VECTOR_I32X4_ABS, Bytecode.VECTOR_I32X4_NEG -> i32x4_unop(x, vectorOpcode);
            case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_S, Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_S, Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_U, Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_U ->
                i64x2_extend_i32x4(x, vectorOpcode);
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
            case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_S, Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_S, Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_U, Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_U ->
                i16x8_binop_extend_i8x16(x, y, vectorOpcode);
            case Bytecode.VECTOR_I32X4_ADD, Bytecode.VECTOR_I32X4_SUB, Bytecode.VECTOR_I32X4_MUL, Bytecode.VECTOR_I32X4_MIN_S, Bytecode.VECTOR_I32X4_MIN_U, Bytecode.VECTOR_I32X4_MAX_S,
                            Bytecode.VECTOR_I32X4_MAX_U ->
                i32x4_binop(x, y, vectorOpcode);
            case Bytecode.VECTOR_I32X4_DOT_I16X8_S, Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_S, Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_S, Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_U,
                            Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_U ->
                i32x4_binop_extend_i16x8(x, y, vectorOpcode);
            case Bytecode.VECTOR_I64X2_ADD, Bytecode.VECTOR_I64X2_SUB, Bytecode.VECTOR_I64X2_MUL -> i64x2_binop(x, y, vectorOpcode);
            case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_S, Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_S, Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_U, Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_U ->
                i64x2_binop_extend_i32x4(x, y, vectorOpcode);
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
        short[] shorts = Vector128.fromBytesToShorts(vec);
        CompilerDirectives.ensureVirtualized(shorts);
        return switch (vectorOpcode) {
            case Bytecode.VECTOR_I16X8_EXTRACT_LANE_S -> shorts[laneIndex];
            case Bytecode.VECTOR_I16X8_EXTRACT_LANE_U -> Short.toUnsignedInt(shorts[laneIndex]);
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
    }

    public static byte[] i16x8_replace_lane(byte[] vec, int laneIndex, short value) {
        short[] result = Vector128.fromBytesToShorts(vec);
        CompilerDirectives.ensureVirtualized(result);
        result[laneIndex] = value;
        return Vector128.fromShortsToBytes(result);
    }

    public static int i32x4_extract_lane(byte[] vec, int laneIndex) {
        int[] ints = Vector128.fromBytesToInts(vec);
        CompilerDirectives.ensureVirtualized(ints);
        return ints[laneIndex];
    }

    public static byte[] i32x4_replace_lane(byte[] vec, int laneIndex, int value) {
        int[] result = Vector128.fromBytesToInts(vec);
        CompilerDirectives.ensureVirtualized(result);
        result[laneIndex] = value;
        return Vector128.fromIntsToBytes(result);
    }

    public static long i64x2_extract_lane(byte[] vec, int laneIndex) {
        long[] longs = Vector128.fromBytesToLongs(vec);
        CompilerDirectives.ensureVirtualized(longs);
        return longs[laneIndex];
    }

    public static byte[] i64x2_replace_lane(byte[] vec, int laneIndex, long value) {
        long[] result = Vector128.fromBytesToLongs(vec);
        CompilerDirectives.ensureVirtualized(result);
        result[laneIndex] = value;
        return Vector128.fromLongsToBytes(result);
    }

    public static float f32x4_extract_lane(byte[] vec, int laneIndex) {
        float[] floats = Vector128.fromBytesToFloats(vec);
        CompilerDirectives.ensureVirtualized(floats);
        return floats[laneIndex];
    }

    public static byte[] f32x4_replace_lane(byte[] vec, int laneIndex, float value) {
        float[] result = Vector128.fromBytesToFloats(vec);
        CompilerDirectives.ensureVirtualized(result);
        result[laneIndex] = value;
        return Vector128.fromFloatsToBytes(result);
    }

    public static double f64x2_extract_lane(byte[] vec, int laneIndex) {
        double[] doubles = Vector128.fromBytesToDoubles(vec);
        CompilerDirectives.ensureVirtualized(doubles);
        return doubles[laneIndex];
    }

    public static byte[] f64x2_replace_lane(byte[] vec, int laneIndex, double value) {
        double[] result = Vector128.fromBytesToDoubles(vec);
        CompilerDirectives.ensureVirtualized(result);
        result[laneIndex] = value;
        return Vector128.fromDoublesToBytes(result);
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
        short[] result = new short[8];
        CompilerDirectives.ensureVirtualized(result);
        Arrays.fill(result, x);
        return Vector128.fromShortsToBytes(result);
    }

    public static byte[] i32x4_splat(int x) {
        int[] result = new int[4];
        CompilerDirectives.ensureVirtualized(result);
        Arrays.fill(result, x);
        return Vector128.fromIntsToBytes(result);
    }

    public static byte[] i64x2_splat(long x) {
        long[] result = new long[2];
        CompilerDirectives.ensureVirtualized(result);
        Arrays.fill(result, x);
        return Vector128.fromLongsToBytes(result);
    }

    public static byte[] f32x4_splat(float x) {
        float[] result = new float[4];
        CompilerDirectives.ensureVirtualized(result);
        Arrays.fill(result, x);
        return Vector128.fromFloatsToBytes(result);
    }

    public static byte[] f64x2_splat(double x) {
        double[] result = new double[2];
        CompilerDirectives.ensureVirtualized(result);
        Arrays.fill(result, x);
        return Vector128.fromDoublesToBytes(result);
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
        short[] x = Vector128.fromBytesToShorts(vecX);
        short[] y = Vector128.fromBytesToShorts(vecY);
        short[] result = new short[8];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_EQ -> x[i] == y[i];
                case Bytecode.VECTOR_I16X8_NE -> x[i] != y[i];
                case Bytecode.VECTOR_I16X8_LT_S -> x[i] < y[i];
                case Bytecode.VECTOR_I16X8_LT_U -> Short.compareUnsigned(x[i], y[i]) < 0;
                case Bytecode.VECTOR_I16X8_GT_S -> x[i] > y[i];
                case Bytecode.VECTOR_I16X8_GT_U -> Short.compareUnsigned(x[i], y[i]) > 0;
                case Bytecode.VECTOR_I16X8_LE_S -> x[i] <= y[i];
                case Bytecode.VECTOR_I16X8_LE_U -> Short.compareUnsigned(x[i], y[i]) <= 0;
                case Bytecode.VECTOR_I16X8_GE_S -> x[i] >= y[i];
                case Bytecode.VECTOR_I16X8_GE_U -> Short.compareUnsigned(x[i], y[i]) >= 0;
                default -> throw CompilerDirectives.shouldNotReachHere();
            } ? (short) 0xffff : (short) 0x0000;
        }
        return Vector128.fromShortsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        int[] x = Vector128.fromBytesToInts(vecX);
        int[] y = Vector128.fromBytesToInts(vecY);
        int[] result = new int[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_EQ -> x[i] == y[i];
                case Bytecode.VECTOR_I32X4_NE -> x[i] != y[i];
                case Bytecode.VECTOR_I32X4_LT_S -> x[i] < y[i];
                case Bytecode.VECTOR_I32X4_LT_U -> Integer.compareUnsigned(x[i], y[i]) < 0;
                case Bytecode.VECTOR_I32X4_GT_S -> x[i] > y[i];
                case Bytecode.VECTOR_I32X4_GT_U -> Integer.compareUnsigned(x[i], y[i]) > 0;
                case Bytecode.VECTOR_I32X4_LE_S -> x[i] <= y[i];
                case Bytecode.VECTOR_I32X4_LE_U -> Integer.compareUnsigned(x[i], y[i]) <= 0;
                case Bytecode.VECTOR_I32X4_GE_S -> x[i] >= y[i];
                case Bytecode.VECTOR_I32X4_GE_U -> Integer.compareUnsigned(x[i], y[i]) >= 0;
                default -> throw CompilerDirectives.shouldNotReachHere();
            } ? 0xffff_ffff : 0x0000_0000;
        }
        return Vector128.fromIntsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i64x2_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        long[] x = Vector128.fromBytesToLongs(vecX);
        long[] y = Vector128.fromBytesToLongs(vecY);
        long[] result = new long[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I64X2_EQ -> x[i] == y[i];
                case Bytecode.VECTOR_I64X2_NE -> x[i] != y[i];
                case Bytecode.VECTOR_I64X2_LT_S -> x[i] < y[i];
                case Bytecode.VECTOR_I64X2_GT_S -> x[i] > y[i];
                case Bytecode.VECTOR_I64X2_LE_S -> x[i] <= y[i];
                case Bytecode.VECTOR_I64X2_GE_S -> x[i] >= y[i];
                default -> throw CompilerDirectives.shouldNotReachHere();
            } ? 0xffff_ffff_ffff_ffffL : 0x0000_0000_0000_0000l;
        }
        return Vector128.fromLongsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f32x4_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        float[] x = Vector128.fromBytesToFloats(vecX);
        float[] y = Vector128.fromBytesToFloats(vecY);
        int[] result = new int[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_F32X4_EQ -> x[i] == y[i];
                case Bytecode.VECTOR_F32X4_NE -> x[i] != y[i];
                case Bytecode.VECTOR_F32X4_LT -> x[i] < y[i];
                case Bytecode.VECTOR_F32X4_GT -> x[i] > y[i];
                case Bytecode.VECTOR_F32X4_LE -> x[i] <= y[i];
                case Bytecode.VECTOR_F32X4_GE -> x[i] >= y[i];
                default -> throw CompilerDirectives.shouldNotReachHere();
            } ? 0xffff_ffff : 0x0000_0000;
        }
        return Vector128.fromIntsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f64x2_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        double[] x = Vector128.fromBytesToDoubles(vecX);
        double[] y = Vector128.fromBytesToDoubles(vecY);
        long[] result = new long[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_F64X2_EQ -> x[i] == y[i];
                case Bytecode.VECTOR_F64X2_NE -> x[i] != y[i];
                case Bytecode.VECTOR_F64X2_LT -> x[i] < y[i];
                case Bytecode.VECTOR_F64X2_GT -> x[i] > y[i];
                case Bytecode.VECTOR_F64X2_LE -> x[i] <= y[i];
                case Bytecode.VECTOR_F64X2_GE -> x[i] >= y[i];
                default -> throw CompilerDirectives.shouldNotReachHere();
            } ? 0xffff_ffff_ffff_ffffL : 0x0000_0000_0000_0000L;
        }
        return Vector128.fromLongsToBytes(result);
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
        short[] x = Vector128.fromBytesToShorts(vecX);
        short[] y = Vector128.fromBytesToShorts(vecY);
        byte[] result = new byte[16];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        for (int i = 0; i < result.length; i++) {
            short[] src = i < 8 ? x : y;
            int index = i < 8 ? i : i - 8;
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I8X16_NARROW_I16X8_S -> satS8(src[index]);
                case Bytecode.VECTOR_I8X16_NARROW_I16X8_U -> satU8(src[index]);
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
    private static byte[] i16x8_extend_i8x16(byte[] x, int vectorOpcode) {
        short[] result = new short[8];
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_S -> x[2 * i] + x[2 * i + 1];
                case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8X16_U -> Byte.toUnsignedInt(x[2 * i]) + Byte.toUnsignedInt(x[2 * i + 1]);
                case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_S -> x[i];
                case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_S -> x[i + result.length];
                case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8X16_U -> Byte.toUnsignedInt(x[i]);
                case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8X16_U -> Byte.toUnsignedInt(x[i + result.length]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromShortsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_unop(byte[] vecX, int vectorOpcode) {
        short[] x = Vector128.fromBytesToShorts(vecX);
        short[] result = new short[8];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_ABS -> Math.abs(x[i]);
                case Bytecode.VECTOR_I16X8_NEG -> -x[i];
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromShortsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int i16x8_all_true(byte[] vec) {
        short[] shorts = Vector128.fromBytesToShorts(vec);
        CompilerDirectives.ensureVirtualized(shorts);
        int result = 1;
        for (int i = 0; i < shorts.length; i++) {
            if (shorts[i] == 0) {
                result = 0;
                break;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int i16x8_bitmask(byte[] vec) {
        short[] shorts = Vector128.fromBytesToShorts(vec);
        CompilerDirectives.ensureVirtualized(shorts);
        int result = 0;
        for (int i = 0; i < shorts.length; i++) {
            if (shorts[i] < 0) {
                result |= 1 << i;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_narrow_i32x4(byte[] vecX, byte[] vecY, int vectorOpcode) {
        int[] x = Vector128.fromBytesToInts(vecX);
        int[] y = Vector128.fromBytesToInts(vecY);
        short[] result = new short[8];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            int[] src = i < 4 ? x : y;
            int index = i < 4 ? i : i - 4;
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_NARROW_I32X4_S -> satS16(src[index]);
                case Bytecode.VECTOR_I16X8_NARROW_I32X4_U -> satU16(src[index]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromShortsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_shiftop(byte[] vecX, int shift, int vectorOpcode) {
        short[] x = Vector128.fromBytesToShorts(vecX);
        short[] result = new short[8];
        int shiftMod = shift % 16;
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_SHL -> x[i] << shiftMod;
                case Bytecode.VECTOR_I16X8_SHR_S -> x[i] >> shiftMod;
                case Bytecode.VECTOR_I16X8_SHR_U -> Short.toUnsignedInt(x[i]) >>> shiftMod;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromShortsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        short[] x = Vector128.fromBytesToShorts(vecX);
        short[] y = Vector128.fromBytesToShorts(vecY);
        short[] result = new short[8];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_Q15MULR_SAT_S -> satS16((x[i] * y[i] + (1 << 14)) >> 15);
                case Bytecode.VECTOR_I16X8_ADD -> x[i] + y[i];
                case Bytecode.VECTOR_I16X8_ADD_SAT_S -> satS16(x[i] + y[i]);
                case Bytecode.VECTOR_I16X8_ADD_SAT_U -> satU16(Short.toUnsignedInt(x[i]) + Short.toUnsignedInt(y[i]));
                case Bytecode.VECTOR_I16X8_SUB -> x[i] - y[i];
                case Bytecode.VECTOR_I16X8_SUB_SAT_S -> satS16(x[i] - y[i]);
                case Bytecode.VECTOR_I16X8_SUB_SAT_U -> satU16(Short.toUnsignedInt(x[i]) - Short.toUnsignedInt(y[i]));
                case Bytecode.VECTOR_I16X8_MUL -> x[i] * y[i];
                case Bytecode.VECTOR_I16X8_MIN_S -> Math.min(x[i], y[i]);
                case Bytecode.VECTOR_I16X8_MIN_U -> Short.compareUnsigned(x[i], y[i]) <= 0 ? x[i] : y[i];
                case Bytecode.VECTOR_I16X8_MAX_S -> Math.max(x[i], y[i]);
                case Bytecode.VECTOR_I16X8_MAX_U -> Short.compareUnsigned(x[i], y[i]) >= 0 ? x[i] : y[i];
                case Bytecode.VECTOR_I16X8_AVGR_U -> (Short.toUnsignedInt(x[i]) + Short.toUnsignedInt(y[i]) + 1) / 2;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromShortsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i16x8_binop_extend_i8x16(byte[] x, byte[] y, int vectorOpcode) {
        short[] result = new short[8];
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_S -> x[i] * y[i];
                case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_S -> x[i + result.length] * y[i + result.length];
                case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8X16_U -> Byte.toUnsignedInt(x[i]) * Byte.toUnsignedInt(y[i]);
                case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8X16_U -> Byte.toUnsignedInt(x[i + result.length]) * Byte.toUnsignedInt(y[i + result.length]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromShortsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_extend_i16x8(byte[] vecX, int vectorOpcode) {
        short[] x = Vector128.fromBytesToShorts(vecX);
        int[] result = new int[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_S -> x[2 * i] + x[2 * i + 1];
                case Bytecode.VECTOR_I32X4_EXTADD_PAIRWISE_I16X8_U -> Short.toUnsignedInt(x[2 * i]) + Short.toUnsignedInt(x[2 * i + 1]);
                case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_S -> x[i];
                case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_S -> x[i + result.length];
                case Bytecode.VECTOR_I32X4_EXTEND_LOW_I16X8_U -> Short.toUnsignedInt(x[i]);
                case Bytecode.VECTOR_I32X4_EXTEND_HIGH_I16X8_U -> Short.toUnsignedInt(x[i + result.length]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromIntsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_unop(byte[] vecX, int vectorOpcode) {
        int[] x = Vector128.fromBytesToInts(vecX);
        int[] result = new int[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_ABS -> Math.abs(x[i]);
                case Bytecode.VECTOR_I32X4_NEG -> -x[i];
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromIntsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int i32x4_all_true(byte[] vec) {
        int[] ints = Vector128.fromBytesToInts(vec);
        CompilerDirectives.ensureVirtualized(ints);
        int result = 1;
        for (int i = 0; i < ints.length; i++) {
            if (ints[i] == 0) {
                result = 0;
                break;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int i32x4_bitmask(byte[] vec) {
        int[] ints = Vector128.fromBytesToInts(vec);
        CompilerDirectives.ensureVirtualized(ints);
        int result = 0;
        for (int i = 0; i < ints.length; i++) {
            if (ints[i] < 0) {
                result |= 1 << i;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_shiftop(byte[] vecX, int shift, int vectorOpcode) {
        int[] x = Vector128.fromBytesToInts(vecX);
        int[] result = new int[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_SHL -> x[i] << shift;
                case Bytecode.VECTOR_I32X4_SHR_S -> x[i] >> shift;
                case Bytecode.VECTOR_I32X4_SHR_U -> x[i] >>> shift;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromIntsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        int[] x = Vector128.fromBytesToInts(vecX);
        int[] y = Vector128.fromBytesToInts(vecY);
        int[] result = new int[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_ADD -> x[i] + y[i];
                case Bytecode.VECTOR_I32X4_SUB -> x[i] - y[i];
                case Bytecode.VECTOR_I32X4_MUL -> x[i] * y[i];
                case Bytecode.VECTOR_I32X4_MIN_S -> Math.min(x[i], y[i]);
                case Bytecode.VECTOR_I32X4_MIN_U -> Integer.compareUnsigned(x[i], y[i]) <= 0 ? x[i] : y[i];
                case Bytecode.VECTOR_I32X4_MAX_S -> Math.max(x[i], y[i]);
                case Bytecode.VECTOR_I32X4_MAX_U -> Integer.compareUnsigned(x[i], y[i]) >= 0 ? x[i] : y[i];
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromIntsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_binop_extend_i16x8(byte[] vecX, byte[] vecY, int vectorOpcode) {
        short[] x = Vector128.fromBytesToShorts(vecX);
        short[] y = Vector128.fromBytesToShorts(vecY);
        int[] result = new int[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_DOT_I16X8_S -> x[2 * i] * y[2 * i] + x[2 * i + 1] * y[2 * i + 1];
                case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_S -> x[i] * y[i];
                case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_S -> x[i + result.length] * y[i + result.length];
                case Bytecode.VECTOR_I32X4_EXTMUL_LOW_I16X8_U -> Short.toUnsignedInt(x[i]) * Short.toUnsignedInt(y[i]);
                case Bytecode.VECTOR_I32X4_EXTMUL_HIGH_I16X8_U -> Short.toUnsignedInt(x[i + result.length]) * Short.toUnsignedInt(y[i + result.length]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromIntsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i64x2_extend_i32x4(byte[] vecX, int vectorOpcode) {
        int[] x = Vector128.fromBytesToInts(vecX);
        long[] result = new long[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_S -> x[i];
                case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_S -> x[i + result.length];
                case Bytecode.VECTOR_I64X2_EXTEND_LOW_I32X4_U -> Integer.toUnsignedLong(x[i]);
                case Bytecode.VECTOR_I64X2_EXTEND_HIGH_I32X4_U -> Integer.toUnsignedLong(x[i + result.length]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromLongsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i64x2_unop(byte[] vecX, int vectorOpcode) {
        long[] x = Vector128.fromBytesToLongs(vecX);
        long[] result = new long[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I64X2_ABS -> Math.abs(x[i]);
                case Bytecode.VECTOR_I64X2_NEG -> -x[i];
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromLongsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int i64x2_all_true(byte[] vec) {
        long[] longs = Vector128.fromBytesToLongs(vec);
        CompilerDirectives.ensureVirtualized(longs);
        int result = 1;
        for (int i = 0; i < longs.length; i++) {
            if (longs[i] == 0) {
                result = 0;
                break;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static int i64x2_bitmask(byte[] vec) {
        long[] longs = Vector128.fromBytesToLongs(vec);
        CompilerDirectives.ensureVirtualized(longs);
        int result = 0;
        for (int i = 0; i < longs.length; i++) {
            if (longs[i] < 0) {
                result |= 1 << i;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i64x2_shiftop(byte[] vecX, int shift, int vectorOpcode) {
        long[] x = Vector128.fromBytesToLongs(vecX);
        long[] result = new long[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I64X2_SHL -> x[i] << shift;
                case Bytecode.VECTOR_I64X2_SHR_S -> x[i] >> shift;
                case Bytecode.VECTOR_I64X2_SHR_U -> x[i] >>> shift;
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromLongsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i64x2_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        long[] x = Vector128.fromBytesToLongs(vecX);
        long[] y = Vector128.fromBytesToLongs(vecY);
        long[] result = new long[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I64X2_ADD -> x[i] + y[i];
                case Bytecode.VECTOR_I64X2_SUB -> x[i] - y[i];
                case Bytecode.VECTOR_I64X2_MUL -> x[i] * y[i];
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromLongsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i64x2_binop_extend_i32x4(byte[] vecX, byte[] vecY, int vectorOpcode) {
        int[] x = Vector128.fromBytesToInts(vecX);
        int[] y = Vector128.fromBytesToInts(vecY);
        long[] result = new long[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_S -> (long) x[i] * (long) y[i];
                case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_S -> (long) x[i + result.length] * (long) y[i + result.length];
                case Bytecode.VECTOR_I64X2_EXTMUL_LOW_I32X4_U -> Integer.toUnsignedLong(x[i]) * Integer.toUnsignedLong(y[i]);
                case Bytecode.VECTOR_I64X2_EXTMUL_HIGH_I32X4_U -> Integer.toUnsignedLong(x[i + result.length]) * Integer.toUnsignedLong(y[i + result.length]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromLongsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f32x4_unop(byte[] vecX, int vectorOpcode) {
        float[] x = Vector128.fromBytesToFloats(vecX);
        float[] result = new float[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_F32X4_ABS -> Math.abs(x[i]);
                case Bytecode.VECTOR_F32X4_NEG -> -x[i];
                case Bytecode.VECTOR_F32X4_SQRT -> (float) Math.sqrt(x[i]);
                case Bytecode.VECTOR_F32X4_CEIL -> (float) Math.ceil(x[i]);
                case Bytecode.VECTOR_F32X4_FLOOR -> (float) Math.floor(x[i]);
                case Bytecode.VECTOR_F32X4_TRUNC -> ExactMath.truncate(x[i]);
                case Bytecode.VECTOR_F32X4_NEAREST -> (float) Math.rint(x[i]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromFloatsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f32x4_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        float[] x = Vector128.fromBytesToFloats(vecX);
        float[] y = Vector128.fromBytesToFloats(vecY);
        float[] result = new float[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_F32X4_ADD -> x[i] + y[i];
                case Bytecode.VECTOR_F32X4_SUB -> x[i] - y[i];
                case Bytecode.VECTOR_F32X4_MUL -> x[i] * y[i];
                case Bytecode.VECTOR_F32X4_DIV -> x[i] / y[i];
                case Bytecode.VECTOR_F32X4_MIN -> Math.min(x[i], y[i]);
                case Bytecode.VECTOR_F32X4_MAX -> Math.max(x[i], y[i]);
                case Bytecode.VECTOR_F32X4_PMIN -> y[i] < x[i] ? y[i] : x[i];
                case Bytecode.VECTOR_F32X4_PMAX -> x[i] < y[i] ? y[i] : x[i];
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromFloatsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f64x2_unop(byte[] vecX, int vectorOpcode) {
        double[] x = Vector128.fromBytesToDoubles(vecX);
        double[] result = new double[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_F64X2_ABS -> Math.abs(x[i]);
                case Bytecode.VECTOR_F64X2_NEG -> -x[i];
                case Bytecode.VECTOR_F64X2_SQRT -> Math.sqrt(x[i]);
                case Bytecode.VECTOR_F64X2_CEIL -> Math.ceil(x[i]);
                case Bytecode.VECTOR_F64X2_FLOOR -> Math.floor(x[i]);
                case Bytecode.VECTOR_F64X2_TRUNC -> ExactMath.truncate(x[i]);
                case Bytecode.VECTOR_F64X2_NEAREST -> Math.rint(x[i]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromDoublesToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f64x2_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        double[] x = Vector128.fromBytesToDoubles(vecX);
        double[] y = Vector128.fromBytesToDoubles(vecY);
        double[] result = new double[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_F64X2_ADD -> x[i] + y[i];
                case Bytecode.VECTOR_F64X2_SUB -> x[i] - y[i];
                case Bytecode.VECTOR_F64X2_MUL -> x[i] * y[i];
                case Bytecode.VECTOR_F64X2_DIV -> x[i] / y[i];
                case Bytecode.VECTOR_F64X2_MIN -> Math.min(x[i], y[i]);
                case Bytecode.VECTOR_F64X2_MAX -> Math.max(x[i], y[i]);
                case Bytecode.VECTOR_F64X2_PMIN -> y[i] < x[i] ? y[i] : x[i];
                case Bytecode.VECTOR_F64X2_PMAX -> x[i] < y[i] ? y[i] : x[i];
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromDoublesToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_trunc_sat_f32x4(byte[] vecX, int vectorOpcode) {
        float[] x = Vector128.fromBytesToFloats(vecX);
        int[] result = new int[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_S -> (int) x[i];
                case Bytecode.VECTOR_I32X4_TRUNC_SAT_F32X4_U -> truncSatU32(x[i]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromIntsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f32x4_convert_i32x4(byte[] vecX, int vectorOpcode) {
        int[] x = Vector128.fromBytesToInts(vecX);
        float[] result = new float[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_F32X4_CONVERT_I32X4_S -> x[i];
                case Bytecode.VECTOR_F32X4_CONVERT_I32X4_U -> Integer.toUnsignedLong(x[i]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromFloatsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] i32x4_trunc_sat_f64x2_zero(byte[] vecX, int vectorOpcode) {
        double[] x = Vector128.fromBytesToDoubles(vecX);
        int[] result = new int[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < x.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_S_ZERO -> (int) x[i];
                case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_U_ZERO -> truncSatU32(x[i]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromIntsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f64x2_convert_low_i32x4(byte[] vecX, int vectorOpcode) {
        int[] x = Vector128.fromBytesToInts(vecX);
        double[] result = new double[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_S -> x[i];
                case Bytecode.VECTOR_F64X2_CONVERT_LOW_I32X4_U -> Integer.toUnsignedLong(x[i]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.fromDoublesToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f32x4_demote_f64x2_zero(byte[] vecX) {
        double[] x = Vector128.fromBytesToDoubles(vecX);
        float[] result = new float[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < x.length; i++) {
            result[i] = (float) x[i];
        }
        return Vector128.fromFloatsToBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    private static byte[] f64x2_promote_low_f32x4(byte[] vecX) {
        float[] x = Vector128.fromBytesToFloats(vecX);
        double[] result = new double[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = x[i];
        }
        return Vector128.fromDoublesToBytes(result);
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
