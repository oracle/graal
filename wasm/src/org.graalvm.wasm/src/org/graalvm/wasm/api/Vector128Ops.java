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

public class Vector128Ops {

    // Checkstyle: stop method name check

    public static byte[] v128_const(byte[] vec) {
        return vec;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] v128_not(byte[] x) {
        byte[] result = new byte[16];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) ~x[i];
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] v128_binop(byte[] x, byte[] y, int vectorOpcode) {
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
    public static byte[] v128_bitselect(byte[] x, byte[] y, byte[] mask) {
        byte[] result = new byte[16];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (x[i] & mask[i] | (y[i] & ~mask[i]));
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static int v128_any_true(byte[] vec) {
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
    public static byte[] i8x16_relop(byte[] x, byte[] y, int vectorOpcode) {
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
    public static byte[] i16x8_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        short[] x = Vector128.bytesAsShorts(vecX);
        short[] y = Vector128.bytesAsShorts(vecY);
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
        return Vector128.shortsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i32x4_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        int[] x = Vector128.bytesAsInts(vecX);
        int[] y = Vector128.bytesAsInts(vecY);
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
        return Vector128.intsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i64x2_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        long[] x = Vector128.bytesAsLongs(vecX);
        long[] y = Vector128.bytesAsLongs(vecY);
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
        return Vector128.longsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] f32x4_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        float[] x = Vector128.bytesAsFloats(vecX);
        float[] y = Vector128.bytesAsFloats(vecY);
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
        return Vector128.intsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] f64x2_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        double[] x = Vector128.bytesAsDoubles(vecX);
        double[] y = Vector128.bytesAsDoubles(vecY);
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
        return Vector128.longsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i8x16_unop(byte[] x, int vectorOpcode) {
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
    public static int i8x16_all_true(byte[] bytes) {
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
    public static int i8x16_bitmask(byte[] bytes) {
        int result = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] < 0) {
                result |= 1 << i;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i8x16_narrow_i16x8(byte[] vecX, byte[] vecY, int vectorOpcode) {
        short[] x = Vector128.bytesAsShorts(vecX);
        short[] y = Vector128.bytesAsShorts(vecY);
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
    public static byte[] i8x16_shiftop(byte[] x, int shift, int vectorOpcode) {
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
    public static byte[] i8x16_binop(byte[] x, byte[] y, int vectorOpcode) {
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
    public static byte[] i16x8_extend_i8x16(byte[] x, int vectorOpcode) {
        short[] result = new short[8];
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8x16_S -> x[2 * i] + x[2 * i + 1];
                case Bytecode.VECTOR_I16X8_EXTADD_PAIRWISE_I8x16_U -> Byte.toUnsignedInt(x[2 * i]) + Byte.toUnsignedInt(x[2 * i + 1]);
                case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8x16_S -> x[i];
                case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8x16_S -> x[i + result.length];
                case Bytecode.VECTOR_I16X8_EXTEND_LOW_I8x16_U -> Byte.toUnsignedInt(x[i]);
                case Bytecode.VECTOR_I16X8_EXTEND_HIGH_I8x16_U -> Byte.toUnsignedInt(x[i + result.length]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.shortsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i16x8_unop(byte[] vecX, int vectorOpcode) {
        short[] x = Vector128.bytesAsShorts(vecX);
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
        return Vector128.shortsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static int i16x8_all_true(byte[] vec) {
        short[] shorts = Vector128.bytesAsShorts(vec);
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
    public static int i16x8_bitmask(byte[] vec) {
        short[] shorts = Vector128.bytesAsShorts(vec);
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
    public static byte[] i16x8_narrow_i32x4(byte[] vecX, byte[] vecY, int vectorOpcode) {
        int[] x = Vector128.bytesAsInts(vecX);
        int[] y = Vector128.bytesAsInts(vecY);
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
        return Vector128.shortsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i16x8_shiftop(byte[] vecX, int shift, int vectorOpcode) {
        short[] x = Vector128.bytesAsShorts(vecX);
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
        return Vector128.shortsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i16x8_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        short[] x = Vector128.bytesAsShorts(vecX);
        short[] y = Vector128.bytesAsShorts(vecY);
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
        return Vector128.shortsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i16x8_binop_extend_i8x16(byte[] x, byte[] y, int vectorOpcode) {
        short[] result = new short[8];
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = (short) switch (vectorOpcode) {
                case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8x16_S -> x[i] * y[i];
                case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8x16_S -> x[i + result.length] * y[i + result.length];
                case Bytecode.VECTOR_I16X8_EXTMUL_LOW_I8x16_U -> Byte.toUnsignedInt(x[i]) * Byte.toUnsignedInt(y[i]);
                case Bytecode.VECTOR_I16X8_EXTMUL_HIGH_I8x16_U -> Byte.toUnsignedInt(x[i + result.length]) * Byte.toUnsignedInt(y[i + result.length]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.shortsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i32x4_extend_i16x8(byte[] vecX, int vectorOpcode) {
        short[] x = Vector128.bytesAsShorts(vecX);
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
        return Vector128.intsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i32x4_unop(byte[] vecX, int vectorOpcode) {
        int[] x = Vector128.bytesAsInts(vecX);
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
        return Vector128.intsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static int i32x4_all_true(byte[] vec) {
        int[] ints = Vector128.bytesAsInts(vec);
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
    public static int i32x4_bitmask(byte[] vec) {
        int[] ints = Vector128.bytesAsInts(vec);
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
    public static byte[] i32x4_shiftop(byte[] vecX, int shift, int vectorOpcode) {
        int[] x = Vector128.bytesAsInts(vecX);
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
        return Vector128.intsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i32x4_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        int[] x = Vector128.bytesAsInts(vecX);
        int[] y = Vector128.bytesAsInts(vecY);
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
        return Vector128.intsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i32x4_binop_extend_i16x8(byte[] vecX, byte[] vecY, int vectorOpcode) {
        short[] x = Vector128.bytesAsShorts(vecX);
        short[] y = Vector128.bytesAsShorts(vecY);
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
        return Vector128.intsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i64x2_extend_i32x4(byte[] vecX, int vectorOpcode) {
        int[] x = Vector128.bytesAsInts(vecX);
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
        return Vector128.longsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i64x2_unop(byte[] vecX, int vectorOpcode) {
        long[] x = Vector128.bytesAsLongs(vecX);
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
        return Vector128.longsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static int i64x2_all_true(byte[] vec) {
        long[] longs = Vector128.bytesAsLongs(vec);
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
    public static int i64x2_bitmask(byte[] vec) {
        long[] longs = Vector128.bytesAsLongs(vec);
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
    public static byte[] i64x2_shiftop(byte[] vecX, int shift, int vectorOpcode) {
        long[] x = Vector128.bytesAsLongs(vecX);
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
        return Vector128.longsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i64x2_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        long[] x = Vector128.bytesAsLongs(vecX);
        long[] y = Vector128.bytesAsLongs(vecY);
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
        return Vector128.longsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i64x2_binop_extend_i32x4(byte[] vecX, byte[] vecY, int vectorOpcode) {
        int[] x = Vector128.bytesAsInts(vecX);
        int[] y = Vector128.bytesAsInts(vecY);
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
        return Vector128.longsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] f32x4_unop(byte[] vecX, int vectorOpcode) {
        float[] x = Vector128.bytesAsFloats(vecX);
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
        return Vector128.floatsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] f32x4_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        float[] x = Vector128.bytesAsFloats(vecX);
        float[] y = Vector128.bytesAsFloats(vecY);
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
        return Vector128.floatsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] f64x2_unop(byte[] vecX, int vectorOpcode) {
        double[] x = Vector128.bytesAsDoubles(vecX);
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
        return Vector128.doublesAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] f64x2_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        double[] x = Vector128.bytesAsDoubles(vecX);
        double[] y = Vector128.bytesAsDoubles(vecY);
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
        return Vector128.doublesAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i32x4_trunc_sat_f32x4(byte[] vecX, int vectorOpcode) {
        float[] x = Vector128.bytesAsFloats(vecX);
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
        return Vector128.intsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] f32x4_convert_i32x4(byte[] vecX, int vectorOpcode) {
        int[] x = Vector128.bytesAsInts(vecX);
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
        return Vector128.floatsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i32x4_trunc_sat_f64x2(byte[] vecX, int vectorOpcode) {
        double[] x = Vector128.bytesAsDoubles(vecX);
        int[] result = new int[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_S_ZERO -> (int) x[i];
                case Bytecode.VECTOR_I32X4_TRUNC_SAT_F64X2_U_ZERO -> truncSatU32(x[i]);
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.intsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] f64x2_convert_low_i32x4(byte[] vecX, int vectorOpcode) {
        int[] x = Vector128.bytesAsInts(vecX);
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
        return Vector128.doublesAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] f32x4_demote_f64x2_zero(byte[] vecX) {
        double[] x = Vector128.bytesAsDoubles(vecX);
        float[] result = new float[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < x.length; i++) {
            result[i] = (float) x[i];
        }
        return Vector128.floatsAsBytes(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] f64x2_promote_low_f32x4(byte[] vecX) {
        float[] x = Vector128.bytesAsFloats(vecX);
        double[] result = new double[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = x[i];
        }
        return Vector128.doublesAsBytes(result);
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
