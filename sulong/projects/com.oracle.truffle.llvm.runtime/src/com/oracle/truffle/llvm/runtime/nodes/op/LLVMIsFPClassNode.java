/*
 * Copyright (c) 2024, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.nodes.op;

import java.util.function.DoublePredicate;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin.TypedBuiltinFactory;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMIsFPClassNodeFactory.IsFPClassF32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMIsFPClassNodeFactory.IsFPClassF32VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMIsFPClassNodeFactory.IsFPClassF64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMIsFPClassNodeFactory.IsFPClassF64VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMIsFPClassNodeFactory.IsFPClassF80NodeGen;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;

@NodeChild("op")
@NodeChild("test")
public abstract class LLVMIsFPClassNode extends LLVMExpressionNode {

    public static TypedBuiltinFactory getIsFPClassFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
                return TypedBuiltinFactory.vector2(IsFPClassF32NodeGen::create, IsFPClassF32VectorNodeGen::create);
            case DOUBLE:
                return TypedBuiltinFactory.vector2(IsFPClassF64NodeGen::create, IsFPClassF64VectorNodeGen::create);
            case X86_FP80:
                return TypedBuiltinFactory.simple2(IsFPClassF80NodeGen::create);
            default:
                return null;
        }
    }

    private static final class FPClassBits {

        // https://llvm.org/docs/LangRef.html#llvm-is-fpclass
        private static final int SNAN = 1 << 0; // Signaling NaN
        private static final int QNAN = 1 << 1; // Quiet NaN
        private static final int NINF = 1 << 2; // Negative infinity
        private static final int NNORM = 1 << 3; // Negative normal
        private static final int NSUBN = 1 << 4; // Negative subnormal
        private static final int NZERO = 1 << 5; // Negative zero
        private static final int PZERO = 1 << 6; // Positive zero
        private static final int PSUBN = 1 << 7; // Positive subnormal
        private static final int PNORM = 1 << 8; // Positive normal
        private static final int PINF = 1 << 9; // Positive infinity

        private static final int F32_SNAN_MASK = 1 << 22;
        private static final int F32_EXP_MASK = 0xFF << 23;
        private static final long F64_SNAN_MASK = 1L << 51;
        private static final long F64_EXP_MASK = 0x7FFL << 52;
    }

    @FunctionalInterface
    interface FloatPredicate {

        boolean test(float value);
    }

    @FunctionalInterface
    interface FP80Predicate {

        boolean test(LLVM80BitFloat value);
    }

    public abstract static class IsFPClassF32 extends LLVMIsFPClassNode {

        /**
         * If all the `mask` bits are set in `test`, then check `pred`.
         */
        private static boolean checkCondition(float op, int test, int mask, FloatPredicate pred) {
            if ((test & mask) == mask) {
                return pred.test(op);
            } else {
                return false;
            }
        }

        private static boolean isSubnormal(float f) {
            return (Float.floatToIntBits(f) & FPClassBits.F32_EXP_MASK) == 0;
        }

        @Specialization
        static boolean doTest(float op, int test) {
            /*
             * According to the LLVM spec, test needs to be a compile-time constant, so no need to
             * do branch profiling or specializations here. We're doing general checks first and
             * specific checks later because some combination of bits can be checked a lot cheaper
             * than the individual bits. Given that the feature bits are compile time constant, the
             * compiler should be able to figure out redundant cases with conditional elimination.
             */
            boolean ret = false;
            ret |= checkCondition(op, test, FPClassBits.QNAN | FPClassBits.SNAN, Float::isNaN);
            ret |= checkCondition(op, test, FPClassBits.QNAN, f -> Float.isNaN(f) && (Float.floatToIntBits(f) & FPClassBits.F32_SNAN_MASK) == 0);
            ret |= checkCondition(op, test, FPClassBits.SNAN, f -> Float.isNaN(f) && (Float.floatToIntBits(f) & FPClassBits.F32_SNAN_MASK) != 0);

            ret |= checkCondition(op, test, FPClassBits.NINF | FPClassBits.NNORM | FPClassBits.NSUBN, f -> f < 0.0f);
            ret |= checkCondition(op, test, FPClassBits.PINF | FPClassBits.PNORM | FPClassBits.PSUBN, f -> f > 0.0f);

            ret |= checkCondition(op, test, FPClassBits.NNORM | FPClassBits.NSUBN | FPClassBits.NZERO | FPClassBits.PZERO | FPClassBits.PSUBN | FPClassBits.PNORM, Float::isFinite);
            ret |= checkCondition(op, test, FPClassBits.NINF | FPClassBits.PINF, Float::isInfinite);

            ret |= checkCondition(op, test, FPClassBits.NZERO | FPClassBits.PZERO, f -> f == 0.0f);

            ret |= checkCondition(op, test, FPClassBits.NINF, f -> f == Float.NEGATIVE_INFINITY);
            ret |= checkCondition(op, test, FPClassBits.NNORM, f -> f < 0 && Float.isFinite(f) && !isSubnormal(f));
            ret |= checkCondition(op, test, FPClassBits.NSUBN, f -> f < 0 && Float.isFinite(f) && isSubnormal(f));
            ret |= checkCondition(op, test, FPClassBits.NZERO, f -> Float.floatToIntBits(f) == 0x8000_0000);
            ret |= checkCondition(op, test, FPClassBits.PZERO, f -> Float.floatToIntBits(f) == 0);
            ret |= checkCondition(op, test, FPClassBits.PSUBN, f -> f > 0 && Float.isFinite(f) && isSubnormal(f));
            ret |= checkCondition(op, test, FPClassBits.PNORM, f -> f > 0 && Float.isFinite(f) && !isSubnormal(f));
            ret |= checkCondition(op, test, FPClassBits.PINF, f -> f == Float.POSITIVE_INFINITY);

            return ret;
        }
    }

    public abstract static class IsFPClassF64 extends LLVMIsFPClassNode {

        /**
         * If all the `mask` bits are set in `test`, then check `pred`.
         */
        private static boolean checkCondition(double op, int test, int mask, DoublePredicate pred) {
            if ((test & mask) == mask) {
                return pred.test(op);
            } else {
                return false;
            }
        }

        private static boolean isSubnormal(double f) {
            return (Double.doubleToLongBits(f) & FPClassBits.F64_EXP_MASK) == 0L;
        }

        @Specialization
        static boolean doTest(double op, int test) {
            /*
             * According to the LLVM spec, test needs to be a compile-time constant, so no need to
             * do branch profiling or specializations here. We're doing general checks first and
             * specific checks later because some combination of bits can be checked a lot cheaper
             * than the individual bits. Given that the feature bits are compile time constant, the
             * compiler should be able to figure out redundant cases with conditional elimination.
             */
            boolean ret = false;
            ret |= checkCondition(op, test, FPClassBits.QNAN | FPClassBits.SNAN, Double::isNaN);
            ret |= checkCondition(op, test, FPClassBits.QNAN, f -> Double.isNaN(f) && (Double.doubleToLongBits(f) & FPClassBits.F64_SNAN_MASK) == 0);
            ret |= checkCondition(op, test, FPClassBits.SNAN, f -> Double.isNaN(f) && (Double.doubleToLongBits(f) & FPClassBits.F64_SNAN_MASK) != 0);

            ret |= checkCondition(op, test, FPClassBits.NINF | FPClassBits.NNORM | FPClassBits.NSUBN, f -> f < 0.0f);
            ret |= checkCondition(op, test, FPClassBits.PINF | FPClassBits.PNORM | FPClassBits.PSUBN, f -> f > 0.0f);

            ret |= checkCondition(op, test, FPClassBits.NNORM | FPClassBits.NSUBN | FPClassBits.NZERO | FPClassBits.PZERO | FPClassBits.PSUBN | FPClassBits.PNORM, Double::isFinite);
            ret |= checkCondition(op, test, FPClassBits.NINF | FPClassBits.PINF, Double::isInfinite);

            ret |= checkCondition(op, test, FPClassBits.NZERO | FPClassBits.PZERO, f -> f == 0.0f);

            ret |= checkCondition(op, test, FPClassBits.NINF, f -> f == Double.NEGATIVE_INFINITY);
            ret |= checkCondition(op, test, FPClassBits.NNORM, f -> f < 0 && Double.isFinite(f) && !isSubnormal(f));
            ret |= checkCondition(op, test, FPClassBits.NSUBN, f -> f < 0 && Double.isFinite(f) && isSubnormal(f));
            ret |= checkCondition(op, test, FPClassBits.NZERO, f -> Double.doubleToLongBits(f) == 0x8000_0000);
            ret |= checkCondition(op, test, FPClassBits.PZERO, f -> Double.doubleToLongBits(f) == 0);
            ret |= checkCondition(op, test, FPClassBits.PSUBN, f -> f > 0 && Double.isFinite(f) && isSubnormal(f));
            ret |= checkCondition(op, test, FPClassBits.PNORM, f -> f > 0 && Double.isFinite(f) && !isSubnormal(f));
            ret |= checkCondition(op, test, FPClassBits.PINF, f -> f == Double.POSITIVE_INFINITY);

            return ret;
        }
    }

    public abstract static class IsFPClassF80 extends LLVMIsFPClassNode {

        /**
         * If all the `mask` bits are set in `test`, then check `pred`.
         */
        private static boolean checkCondition(LLVM80BitFloat op, int test, int mask, FP80Predicate pred) {
            if ((test & mask) == mask) {
                return pred.test(op);
            } else {
                return false;
            }
        }

        private static boolean isSubnormal(LLVM80BitFloat f) {
            return f.getExponent() == 0;
        }

        @Specialization
        static boolean doTest(LLVM80BitFloat op, int test) {
            /*
             * According to the LLVM spec, test needs to be a compile-time constant, so no need to
             * do branch profiling or specializations here. We're doing general checks first and
             * specific checks later because some combination of bits can be checked a lot cheaper
             * than the individual bits. Given that the feature bits are compile time constant, the
             * compiler should be able to figure out redundant cases with conditional elimination.
             */
            boolean ret = false;

            ret |= checkCondition(op, test, FPClassBits.NINF | FPClassBits.NNORM | FPClassBits.NSUBN, f -> f.getSign());
            ret |= checkCondition(op, test, FPClassBits.PINF | FPClassBits.PNORM | FPClassBits.PSUBN, f -> !f.getSign());

            ret |= checkCondition(op, test, FPClassBits.QNAN, f -> f.isSNaN());
            ret |= checkCondition(op, test, FPClassBits.SNAN, f -> f.isQNaN());
            ret |= checkCondition(op, test, FPClassBits.NINF, f -> f.isNegativeInfinity());
            ret |= checkCondition(op, test, FPClassBits.NNORM, f -> f.getSign() && !f.isNaN() && !f.isNegativeInfinity() && !isSubnormal(f));
            ret |= checkCondition(op, test, FPClassBits.NSUBN, f -> f.getSign() && !f.isNaN() && !f.isNegativeInfinity() && isSubnormal(f));
            ret |= checkCondition(op, test, FPClassBits.NZERO, f -> f.isNegativeZero());
            ret |= checkCondition(op, test, FPClassBits.PZERO, f -> f.isPositiveZero());
            ret |= checkCondition(op, test, FPClassBits.PSUBN, f -> !f.getSign() && !f.isNaN() && !f.isPositiveInfinity() && isSubnormal(f));
            ret |= checkCondition(op, test, FPClassBits.PNORM, f -> !f.getSign() && !f.isNaN() && !f.isPositiveInfinity() && !isSubnormal(f));
            ret |= checkCondition(op, test, FPClassBits.PINF, f -> f.isPositiveInfinity());

            return ret;
        }
    }

    public abstract static class IsFPClassF32Vector extends LLVMIsFPClassNode {

        private final int vectorSize;

        IsFPClassF32Vector(int vectorSize) {
            this.vectorSize = vectorSize;
        }

        @Specialization
        LLVMI1Vector doTest(LLVMFloatVector v, int test) {
            boolean[] ret = new boolean[v.getLength()];
            for (int i = 0; i < vectorSize; i++) {
                ret[i] = IsFPClassF32.doTest(v.getValue(i), test);
            }
            return LLVMI1Vector.create(ret);
        }
    }

    public abstract static class IsFPClassF64Vector extends LLVMIsFPClassNode {

        private final int vectorSize;

        IsFPClassF64Vector(int vectorSize) {
            this.vectorSize = vectorSize;
        }

        @Specialization
        LLVMI1Vector doTest(LLVMDoubleVector v, int test) {
            boolean[] ret = new boolean[v.getLength()];
            for (int i = 0; i < vectorSize; i++) {
                ret[i] = IsFPClassF64.doTest(v.getValue(i), test);
            }
            return LLVMI1Vector.create(ret);
        }
    }
}
