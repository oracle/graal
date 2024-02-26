package org.graalvm.wasm.api;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.graalvm.wasm.constants.Bytecode;

public class Vector128Ops {

    public static byte[] v128_const(byte[] vec) {
        return vec;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static int v128_any_true(byte[] vec) {
        int result = 0;
        for (int i = 0; i < 16; i++) {
            if (vec[i] != 0) {
                result = 1;
                break;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static int i32x4_all_true(byte[] vec) {
        int[] ints = Vector128.ofBytes(vec).asInts();
        CompilerDirectives.ensureVirtualized(ints);
        int result = 1;
        for (int i = 0; i < 4; i++) {
            if (ints[i] == 0) {
                result = 0;
                break;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i32x4_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        int[] x = Vector128.ofBytes(vecX).asInts();
        int[] y = Vector128.ofBytes(vecY).asInts();
        int[] result = new int[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < 4; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_EQ -> y[i] == x[i];
                case Bytecode.VECTOR_I32X4_NE -> y[i] != x[i];
                case Bytecode.VECTOR_I32X4_LT_S -> y[i] < x[i];
                case Bytecode.VECTOR_I32X4_LT_U -> Integer.compareUnsigned(y[i], x[i]) < 0;
                case Bytecode.VECTOR_I32X4_GT_S -> y[i] > x[i];
                case Bytecode.VECTOR_I32X4_GT_U -> Integer.compareUnsigned(y[i], x[i]) > 0;
                case Bytecode.VECTOR_I32X4_LE_S -> y[i] <= x[i];
                case Bytecode.VECTOR_I32X4_LE_U -> Integer.compareUnsigned(y[i], x[i]) <= 0;
                case Bytecode.VECTOR_I32X4_GE_S -> y[i] >= x[i];
                case Bytecode.VECTOR_I32X4_GE_U -> Integer.compareUnsigned(y[i], x[i]) >= 0;
                default -> throw CompilerDirectives.shouldNotReachHere();
            } ? 0xffff_ffff : 0x0000_0000;
        }
        return Vector128.ofInts(result).asBytes();
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] f64x2_relop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        double[] x = Vector128.ofBytes(vecX).asDoubles();
        double[] y = Vector128.ofBytes(vecY).asDoubles();
        long[] result = new long[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < 2; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_F64X2_EQ -> y[i] == x[i];
                case Bytecode.VECTOR_F64X2_NE -> y[i] != x[i];
                case Bytecode.VECTOR_F64X2_LT -> y[i] < x[i];
                case Bytecode.VECTOR_F64X2_GT -> y[i] > x[i];
                case Bytecode.VECTOR_F64X2_LE -> y[i] <= x[i];
                case Bytecode.VECTOR_F64X2_GE -> y[i] >= x[i];
                default -> throw CompilerDirectives.shouldNotReachHere();
            } ? 0xffff_ffff_ffff_ffffL : 0x0000_0000_0000_0000L;
        }
        return Vector128.ofLongs(result).asBytes();
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] i32x4_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        int[] x = Vector128.ofBytes(vecX).asInts();
        int[] y = Vector128.ofBytes(vecY).asInts();
        int[] result = new int[4];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < 4; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_I32X4_ADD -> y[i] + x[i];
                case Bytecode.VECTOR_I32X4_SUB -> y[i] - x[i];
                case Bytecode.VECTOR_I32X4_MUL -> y[i] * x[i];
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.ofInts(result).asBytes();
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] f64x2_unop(byte[] vecX, int vectorOpcode) {
        double[] x = Vector128.ofBytes(vecX).asDoubles();
        double[] result = new double[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < 2; i++) {
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
        return Vector128.ofDoubles(result).asBytes();
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] f64x2_binop(byte[] vecX, byte[] vecY, int vectorOpcode) {
        double[] x = Vector128.ofBytes(vecX).asDoubles();
        double[] y = Vector128.ofBytes(vecY).asDoubles();
        double[] result = new double[2];
        CompilerDirectives.ensureVirtualized(x);
        CompilerDirectives.ensureVirtualized(y);
        CompilerDirectives.ensureVirtualized(result);
        for (int i = 0; i < 2; i++) {
            result[i] = switch (vectorOpcode) {
                case Bytecode.VECTOR_F64X2_ADD -> y[i] + x[i];
                case Bytecode.VECTOR_F64X2_SUB -> y[i] - x[i];
                case Bytecode.VECTOR_F64X2_MUL -> y[i] * x[i];
                case Bytecode.VECTOR_F64X2_DIV -> y[i] / x[i];
                case Bytecode.VECTOR_F64X2_MIN -> Math.min(y[i], x[i]);
                case Bytecode.VECTOR_F64X2_MAX -> Math.max(y[i], x[i]);
                case Bytecode.VECTOR_F64X2_PMIN -> x[i] < y[i] ? x[i] : y[i];
                case Bytecode.VECTOR_F64X2_PMAX -> y[i] < x[i] ? x[i] : y[i];
                default -> throw CompilerDirectives.shouldNotReachHere();
            };
        }
        return Vector128.ofDoubles(result).asBytes();
    }
}
