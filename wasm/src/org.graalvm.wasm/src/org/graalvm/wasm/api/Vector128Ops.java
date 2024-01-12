package org.graalvm.wasm.api;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.graalvm.wasm.constants.Bytecode;

public class Vector128Ops {

    public static Vector128 v128_const(byte[] vec) {
        return Vector128.ofBytes(vec);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static int v128_any_true(Vector128 vec) {
        byte[] bytes = vec.asBytes();
        int result = 0;
        for (int i = 0; i < 16; i++) {
            if (bytes[i] != 0) {
                result = 1;
                break;
            }
        }
        return result;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static int i32x4_all_true(Vector128 vec) {
        int[] ints = vec.asInts();
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
    public static Vector128 f64x2_relop(Vector128 vecX, Vector128 vecY, int vectorOpcode) {
        double[] x = vecX.asDoubles();
        double[] y = vecY.asDoubles();
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
        return Vector128.ofLongs(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static Vector128 i32x4_binop(Vector128 vecX, Vector128 vecY, int vectorOpcode) {
        int[] x = vecX.asInts();
        int[] y = vecY.asInts();
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
        return Vector128.ofInts(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static Vector128 f64x2_unop(Vector128 vecX, int vectorOpcode) {
        double[] x = vecX.asDoubles();
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
        return Vector128.ofDoubles(result);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static Vector128 f64x2_binop(Vector128 vecX, Vector128 vecY, int vectorOpcode) {
        double[] x = vecX.asDoubles();
        double[] y = vecY.asDoubles();
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
        return Vector128.ofDoubles(result);
    }
}
