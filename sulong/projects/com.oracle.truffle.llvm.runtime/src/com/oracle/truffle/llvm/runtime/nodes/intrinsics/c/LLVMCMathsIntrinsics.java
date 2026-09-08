/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.c;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.UnaryOperation;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.floating.LLVMLongDoubleNode;
import com.oracle.truffle.llvm.runtime.floating.LLVMLongDoubleNode.LongDoubleKinds;
import com.oracle.truffle.llvm.runtime.interop.LLVMNegatedForeignObject;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMAbsNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMAbsVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMCeilNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMCopySignNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMCopySignVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMCosNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMExp2NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMExpNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFAbsNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFAbsVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFloorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFmaNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFmaVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMMaxnumVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLog10NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLog2NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLogNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMMaxnumNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMMaximumNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMMaximumVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMMinimumNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMMinimumVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMMinnumNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMMinnumVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMRintNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMRoundEvenNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMRoundNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMTruncNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSinNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSqrtNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSqrtVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMUnaryNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMVectorUnaryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin.TypedBuiltinFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMDoubleStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMFloatStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.op.ToComparableValue;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

/**
 * Implements the C functions from math.h.
 */
public abstract class LLVMCMathsIntrinsics {

    public static TypedBuiltinFactory getSqrtFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.vector1(LLVMSqrtNodeGen::create, LLVMSqrtVectorNodeGen::create);
            case X86_FP80:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("sqrt", args[1], LongDoubleKinds.FP80));
            case F128:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("sqrt", args[1], LongDoubleKinds.FP128));
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getLogFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.simple1(LLVMLogNodeGen::create);
            case X86_FP80:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("log", args[1], LongDoubleKinds.FP80));
            case F128:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("log", args[1], LongDoubleKinds.FP128));
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getLog2Factory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.simple1(LLVMLog2NodeGen::create);
            case X86_FP80:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("log2", args[1], LongDoubleKinds.FP80));
            case F128:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("log2", args[1], LongDoubleKinds.FP128));
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getLog10Factory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.simple1(LLVMLog10NodeGen::create);
            case X86_FP80:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("log10", args[1], LongDoubleKinds.FP80));
            case F128:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("log10", args[1], LongDoubleKinds.FP128));
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getRintFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.vector1(LLVMRintNodeGen::create, (vectorSize, arg) -> LLVMVectorUnaryNodeGen.create(vectorSize, LLVMRintNodeGen.create(null), arg));
            case X86_FP80:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("rint", args[1], LongDoubleKinds.FP80));
            case F128:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("rint", args[1], LongDoubleKinds.FP128));
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getCeilFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.vector1(LLVMCeilNodeGen::create, (vectorSize, arg) -> LLVMVectorUnaryNodeGen.create(vectorSize, LLVMCeilNodeGen.create(null), arg));
            case X86_FP80:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("ceil", args[1], LongDoubleKinds.FP80));
            case F128:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("ceil", args[1], LongDoubleKinds.FP128));
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getFloorFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.vector1(LLVMFloorNodeGen::create, (vectorSize, arg) -> LLVMVectorUnaryNodeGen.create(vectorSize, LLVMFloorNodeGen.create(null), arg));
            case X86_FP80:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("floor", args[1], LongDoubleKinds.FP80));
            case F128:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("floor", args[1], LongDoubleKinds.FP128));
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getExpFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.simple1(LLVMExpNodeGen::create);
            case X86_FP80:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("exp", args[1], LongDoubleKinds.FP80));
            case F128:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("exp", args[1], LongDoubleKinds.FP128));
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getExp2Factory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.simple1(LLVMExp2NodeGen::create);
            case X86_FP80:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("exp2", args[1], LongDoubleKinds.FP80));
            case F128:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("exp2", args[1], LongDoubleKinds.FP128));
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getSinFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.simple1(LLVMSinNodeGen::create);
            case X86_FP80:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("sin", args[1], LongDoubleKinds.FP80));
            case F128:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("sin", args[1], LongDoubleKinds.FP128));
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getCosFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.simple1(LLVMCosNodeGen::create);
            case X86_FP80:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("cos", args[1], LongDoubleKinds.FP80));
            case F128:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("cos", args[1], LongDoubleKinds.FP128));
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getRoundFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.vector1(LLVMRoundNodeGen::create, (vectorSize, arg) -> LLVMVectorUnaryNodeGen.create(vectorSize, LLVMRoundNodeGen.create(null), arg));
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getTruncFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.vector1(LLVMTruncNodeGen::create, (vectorSize, arg) -> LLVMVectorUnaryNodeGen.create(vectorSize, LLVMTruncNodeGen.create(null), arg));
            case X86_FP80:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("trunc", args[1], LongDoubleKinds.FP80));
            case F128:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("trunc", args[1], LongDoubleKinds.FP128));
            default:
                return null;
        }
    }

    /**
     * {@code llvm.nearbyint} rounds to an integer honoring the current rounding mode but never
     * raising the inexact exception. Sulong does not track FP exception flags, so the result is
     * identical to {@code llvm.rint}; reuse its factory verbatim.
     */
    public static TypedBuiltinFactory getNearbyintFactory(PrimitiveKind type) {
        return getRintFactory(type);
    }

    public static TypedBuiltinFactory getRoundEvenFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.vector1(LLVMRoundEvenNodeGen::create, (vectorSize, arg) -> LLVMVectorUnaryNodeGen.create(vectorSize, LLVMRoundEvenNodeGen.create(null), arg));
            case X86_FP80:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("roundeven", args[1], LongDoubleKinds.FP80));
            case F128:
                return TypedBuiltinFactory.simple((args) -> LLVMLongDoubleNode.createUnary("roundeven", args[1], LongDoubleKinds.FP128));
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getMinnumFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.vector2(LLVMMinnumNodeGen::create, LLVMMinnumVectorNodeGen::create);
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getMaxnumFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.vector2(LLVMMaxnumNodeGen::create, LLVMMaxnumVectorNodeGen::create);
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getMinimumFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.vector2(LLVMMinimumNodeGen::create, LLVMMinimumVectorNodeGen::create);
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getMaximumFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.vector2(LLVMMaximumNodeGen::create, LLVMMaximumVectorNodeGen::create);
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getCopySignFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.vector2(LLVMCopySignNodeGen::create, LLVMCopySignVectorNodeGen::create);
            case X86_FP80:
                return TypedBuiltinFactory.simple2(LLVMCopySignNodeGen::create);
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getAbsFactory(PrimitiveKind type) {
        switch (type) {
            case I8:
            case I16:
            case I32:
            case I64:
                return TypedBuiltinFactory.vector1(LLVMAbsNodeGen::create, LLVMAbsVectorNodeGen::create);
            default:
                return null;
        }
    }

    public static TypedBuiltinFactory getFAbsFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.vector1(LLVMFAbsNodeGen::create, LLVMFAbsVectorNodeGen::create);
            case X86_FP80:
                return TypedBuiltinFactory.simple1(LLVMFAbsNodeGen::create);
            default:
                return null;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMSqrt extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.sqrt(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.sqrt(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMSqrtVectorNode extends LLVMBuiltin {

        private final int vectorLength;

        LLVMSqrtVectorNode(int vectorLength) {
            this.vectorLength = vectorLength;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doVector(LLVMDoubleVector value) {
            assert value.getLength() == vectorLength;
            double[] result = new double[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Math.sqrt(value.getValue(i));
            }
            return LLVMDoubleVector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doVector(LLVMFloatVector value) {
            assert value.getLength() == vectorLength;
            float[] result = new float[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = (float) Math.sqrt(value.getValue(i));
            }
            return LLVMFloatVector.create(result);
        }
    }

    public static TypedBuiltinFactory getFmaFactory(PrimitiveKind type) {
        switch (type) {
            case FLOAT:
            case DOUBLE:
                return TypedBuiltinFactory.vector3(LLVMFmaNodeGen::create, LLVMFmaVectorNodeGen::create);
            default:
                return null;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMFma extends LLVMBuiltin {

        /*
         * Unlike llvm.fmuladd, llvm.fma requires the fused single-rounding result; Math.fma is
         * correctly rounded, a decomposed multiply+add is not.
         */
        @Specialization
        protected float doIntrinsic(float a, float b, float c) {
            return Math.fma(a, b, c);
        }

        @Specialization
        protected double doIntrinsic(double a, double b, double c) {
            return Math.fma(a, b, c);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMFmaVectorNode extends LLVMBuiltin {

        private final int vectorLength;

        LLVMFmaVectorNode(int vectorLength) {
            this.vectorLength = vectorLength;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doVector(LLVMDoubleVector a, LLVMDoubleVector b, LLVMDoubleVector c) {
            assert a.getLength() == vectorLength && b.getLength() == vectorLength && c.getLength() == vectorLength;
            double[] result = new double[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Math.fma(a.getValue(i), b.getValue(i), c.getValue(i));
            }
            return LLVMDoubleVector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doVector(LLVMFloatVector a, LLVMFloatVector b, LLVMFloatVector c) {
            assert a.getLength() == vectorLength && b.getLength() == vectorLength && c.getLength() == vectorLength;
            float[] result = new float[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Math.fma(a.getValue(i), b.getValue(i), c.getValue(i));
            }
            return LLVMFloatVector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLog extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.log(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.log(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLog2 extends LLVMBuiltin {

        private static final double LOG_2 = Math.log(2);

        /*
         * The naive log2(x) = log(x) / log(2) is up to ~1 ulp off the correctly-rounded result
         * (e.g. log2(100) came out 6.6438561897747253 vs the correct 6.6438561897747244), which
         * broke Ceres' Jet.Log2 finite-difference derivative check. Decompose a finite normal
         * positive x as m * 2^e with m in [0.5, 1) (frexp) so that log2(x) = e + log(m)/log(2):
         * e is exact and log(m) has small magnitude, recovering the correctly-rounded value that
         * matches the native libm. Zero, negatives, infinities, NaN and subnormals keep the plain
         * formula (their results need no extra precision).
         */
        @Specialization
        protected float doIntrinsic(float value) {
            if (value >= Float.MIN_NORMAL && value < Float.POSITIVE_INFINITY) {
                int e = Math.getExponent(value) + 1;
                float m = Math.scalb(value, -e);
                return (float) (e + Math.log(m) / LOG_2);
            }
            return (float) (Math.log(value) / LOG_2);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            if (value >= Double.MIN_NORMAL && value < Double.POSITIVE_INFINITY) {
                int e = Math.getExponent(value) + 1;
                double m = Math.scalb(value, -e);
                return e + Math.log(m) / LOG_2;
            }
            return Math.log(value) / LOG_2;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLog10 extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.log10(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.log10(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLog1p extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.log1p(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.log1p(value);
        }
    }

    public abstract static class LLVMRint extends LLVMUnaryNode {

        protected LLVMRint() {
            super(UnaryOperation.NEG);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            switch (getLanguage().getRoundingMode()) {
                case LLVMLanguage.ROUNDING_MODE_TOWARD_POSITIVE:
                    return (float) Math.ceil(value);
                case LLVMLanguage.ROUNDING_MODE_TOWARD_NEGATIVE:
                    return (float) Math.floor(value);
                case LLVMLanguage.ROUNDING_MODE_TOWARD_ZERO:
                    return value >= 0 ? (float) Math.floor(value) : (float) Math.ceil(value);
                case LLVMLanguage.ROUNDING_MODE_NEAREST_TIES_AWAY:
                    float nearest = (float) Math.rint(value);
                    return Math.abs(value - nearest) == 0.5f ? Math.copySign((float) Math.ceil(Math.abs(value)), value) : nearest;
                default:
                    return (float) Math.rint(value);
            }
        }

        @Specialization
        protected double doIntrinsic(double value) {
            switch (getLanguage().getRoundingMode()) {
                case LLVMLanguage.ROUNDING_MODE_TOWARD_POSITIVE:
                    return Math.ceil(value);
                case LLVMLanguage.ROUNDING_MODE_TOWARD_NEGATIVE:
                    return Math.floor(value);
                case LLVMLanguage.ROUNDING_MODE_TOWARD_ZERO:
                    return value >= 0 ? Math.floor(value) : Math.ceil(value);
                case LLVMLanguage.ROUNDING_MODE_NEAREST_TIES_AWAY:
                    double nearest = Math.rint(value);
                    return Math.abs(value - nearest) == 0.5 ? Math.copySign(Math.ceil(Math.abs(value)), value) : nearest;
                default:
                    return Math.rint(value);
            }
        }
    }

    public abstract static class LLVMCeil extends LLVMUnaryNode {

        protected LLVMCeil() {
            super(UnaryOperation.NEG);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.ceil(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.ceil(value);
        }
    }

    public abstract static class LLVMFloor extends LLVMUnaryNode {

        protected LLVMFloor() {
            super(UnaryOperation.NEG);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.floor(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.floor(value);
        }
    }

    public abstract static class LLVMRound extends LLVMUnaryNode {

        protected LLVMRound() {
            super(UnaryOperation.NEG);
        }

        /*
         * llvm.round is round-half-away-from-zero, independent of the current rounding mode.
         * Math.round is round-half-up (round(-2.5) == -2), which diverges from native for
         * negative ties, so compute ties-away explicitly: rint() gives the nearest integer
         * (ties to even); on an exact half we override with ceil(|x|) carrying x's sign.
         */
        @Specialization
        protected float doIntrinsic(float value) {
            float nearest = (float) Math.rint(value);
            return Math.abs(value - nearest) == 0.5f ? Math.copySign((float) Math.ceil(Math.abs(value)), value) : nearest;
        }

        @Specialization
        protected double doIntrinsic(double value) {
            double nearest = Math.rint(value);
            return Math.abs(value - nearest) == 0.5 ? Math.copySign(Math.ceil(Math.abs(value)), value) : nearest;
        }
    }

    public abstract static class LLVMTrunc extends LLVMUnaryNode {

        protected LLVMTrunc() {
            super(UnaryOperation.NEG);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return value < 0 ? (float) Math.ceil(value) : (float) Math.floor(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return value < 0 ? Math.ceil(value) : Math.floor(value);
        }
    }

    public abstract static class LLVMRoundEven extends LLVMUnaryNode {

        protected LLVMRoundEven() {
            super(UnaryOperation.NEG);
        }

        // llvm.roundeven is round-half-to-even, independent of the rounding mode: exactly Math.rint.
        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.rint(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.rint(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMAbs extends LLVMIntrinsic {

        @Specialization
        protected byte doByte(byte value) {
            return (byte) Math.abs(value);
        }

        @Specialization
        protected short doShort(short value) {
            return (short) Math.abs(value);
        }

        @Specialization
        protected int doInt(int value) {
            return Math.abs(value);
        }

        @Specialization
        protected long doLong(long value) {
            return Math.abs(value);
        }

        @Specialization
        protected LLVMNativePointer doNative(LLVMNativePointer value) {
            return LLVMNativePointer.create(doLong(value.asNative()));
        }

        @Specialization
        protected LLVMManagedPointer doManaged(LLVMManagedPointer value,
                        @Cached ConditionProfile negated) {
            if (negated.profile(value.getObject() instanceof LLVMNegatedForeignObject)) {
                LLVMNegatedForeignObject obj = (LLVMNegatedForeignObject) value.getObject();
                assert !(obj.getForeign() instanceof LLVMNegatedForeignObject);
                return LLVMManagedPointer.create(obj.getForeign(), -value.getOffset());
            } else {
                // valid pointers are always positive
                return value;
            }
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMAbsVectorNode extends LLVMBuiltin {

        private final int vectorLength;

        LLVMAbsVectorNode(int vectorLength) {
            this.vectorLength = vectorLength;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doVector(LLVMI8Vector value) {
            assert value.getLength() == vectorLength;
            byte[] result = new byte[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = (byte) Math.abs(value.getValue(i));
            }
            return LLVMI8Vector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doVector(LLVMI16Vector value) {
            assert value.getLength() == vectorLength;
            short[] result = new short[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = (short) Math.abs(value.getValue(i));
            }
            return LLVMI16Vector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doVector(LLVMI32Vector value) {
            assert value.getLength() == vectorLength;
            int[] result = new int[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Math.abs(value.getValue(i));
            }
            return LLVMI32Vector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doVector(LLVMI64Vector value) {
            assert value.getLength() == vectorLength;
            long[] result = new long[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Math.abs(value.getValue(i));
            }
            return LLVMI64Vector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMFAbs extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return Math.abs(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.abs(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            return value.abs();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMFAbsVectorNode extends LLVMBuiltin {

        private final int vectorLength;

        LLVMFAbsVectorNode(int vectorLength) {
            this.vectorLength = vectorLength;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doVector(LLVMDoubleVector value) {
            assert value.getLength() == vectorLength;
            double[] result = new double[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Math.abs(value.getValue(i));
            }
            return LLVMDoubleVector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doVector(LLVMFloatVector value) {
            assert value.getLength() == vectorLength;
            float[] result = new float[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Math.abs(value.getValue(i));
            }
            return LLVMFloatVector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMMinnum extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value1, float value2) {
            return minnum(value1, value2);
        }

        @Specialization
        protected double doIntrinsic(double value1, double value2) {
            return minnum(value1, value2);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMMinnumVector extends LLVMBuiltin {

        private final int vectorLength;

        protected LLVMMinnumVector(int vectorLength) {
            this.vectorLength = vectorLength;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doFloatVector(LLVMFloatVector value1, LLVMFloatVector value2) {
            assert value1.getLength() == vectorLength;
            assert value2.getLength() == vectorLength;
            float[] result = new float[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = minnum(value1.getValue(i), value2.getValue(i));
            }
            return LLVMFloatVector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doDoubleVector(LLVMDoubleVector value1, LLVMDoubleVector value2) {
            assert value1.getLength() == vectorLength;
            assert value2.getLength() == vectorLength;
            double[] result = new double[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = minnum(value1.getValue(i), value2.getValue(i));
            }
            return LLVMDoubleVector.create(result);
        }
    }

    private static float minnum(float value1, float value2) {
        if (Float.isNaN(value1)) {
            return value2;
        }
        if (Float.isNaN(value2)) {
            return value1;
        }
        return Math.min(value1, value2);
    }

    private static double minnum(double value1, double value2) {
        if (Double.isNaN(value1)) {
            return value2;
        }
        if (Double.isNaN(value2)) {
            return value1;
        }
        return Math.min(value1, value2);
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMMaxnum extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value1, float value2) {
            return maxnum(value1, value2);
        }

        @Specialization
        protected double doIntrinsic(double value1, double value2) {
            return maxnum(value1, value2);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMMaxnumVector extends LLVMBuiltin {

        private final int vectorLength;

        protected LLVMMaxnumVector(int vectorLength) {
            this.vectorLength = vectorLength;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doFloatVector(LLVMFloatVector value1, LLVMFloatVector value2) {
            assert value1.getLength() == vectorLength;
            assert value2.getLength() == vectorLength;
            float[] result = new float[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = maxnum(value1.getValue(i), value2.getValue(i));
            }
            return LLVMFloatVector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doDoubleVector(LLVMDoubleVector value1, LLVMDoubleVector value2) {
            assert value1.getLength() == vectorLength;
            assert value2.getLength() == vectorLength;
            double[] result = new double[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = maxnum(value1.getValue(i), value2.getValue(i));
            }
            return LLVMDoubleVector.create(result);
        }
    }

    private static float maxnum(float value1, float value2) {
        if (Float.isNaN(value1)) {
            return value2;
        }
        if (Float.isNaN(value2)) {
            return value1;
        }
        return Math.max(value1, value2);
    }

    private static double maxnum(double value1, double value2) {
        if (Double.isNaN(value1)) {
            return value2;
        }
        if (Double.isNaN(value2)) {
            return value1;
        }
        return Math.max(value1, value2);
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMExp extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.exp(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.exp(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMExpm1 extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.expm1(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.expm1(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMExp2 extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.pow(2, value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.pow(2, value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLdexp extends LLVMIntrinsic {

        @Specialization
        protected float doIntrinsic(float value, int exp) {
            // Math.scalb is exact IEEE ldexp/scalbn: it adjusts the exponent field, so
            // scalb(0, n) == 0 and scalb(x, huge) saturates to +/-inf. The old
            // `value * Math.pow(2, exp)` produced NaN for value==0 with a large exp
            // (2^exp overflows to inf, 0 * inf == NaN).
            return Math.scalb(value, exp);
        }

        @Specialization
        protected double doIntrinsic(double value, int exp) {
            return Math.scalb(value, exp);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMModf extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value, LLVMPointer integralAddr,
                        @Cached LLVMDoubleStoreNode store) {
            double fractional = value % 1;
            double integral = value - fractional;
            store.executeWithTarget(integralAddr, integral);
            return fractional;
        }

        @Specialization
        protected float doIntrinsic(float value, LLVMPointer integralAddr,
                        @Cached LLVMFloatStoreNode store) {
            float fractional = value % 1;
            float integral = value - fractional;
            store.executeWithTarget(integralAddr, integral);
            return fractional;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMFmod extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double numer, double denom) {
            return numer % denom;
        }

        @Specialization
        protected float doIntrinsic(float numer, float denom) {
            return numer % denom;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPow extends LLVMBuiltin {

        @Specialization
        protected float doFloat(float val, int pow) {
            return (float) Math.pow(val, pow);
        }

        @Specialization
        protected float doFloat(float val, float pow) {
            return (float) Math.pow(val, pow);
        }

        @Specialization
        protected double doDouble(double a, int b) {
            return Math.pow(a, b);
        }

        @Specialization
        protected double doDouble(double a, double b) {
            return Math.pow(a, b);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMSin extends LLVMBuiltin {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.sin(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.sin(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMSinh extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.sinh(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.sinh(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMASin extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.asin(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.asin(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMCos extends LLVMBuiltin {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.cos(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.cos(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMCosh extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.cosh(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.cosh(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMACos extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.acos(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.acos(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMTan extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.tan(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.tan(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMTanh extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.tanh(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.tanh(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMATan extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.atan(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.atan(value);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMATan2 extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value1, double value2) {
            return Math.atan2(value1, value2);
        }

        @Specialization
        protected float doIntrinsic(float value1, float value2) {
            return (float) Math.atan2(value1, value2);
        }
    }

    @NodeChild(value = "magnitude", type = LLVMExpressionNode.class)
    @NodeChild(value = "sign", type = LLVMExpressionNode.class)
    public abstract static class LLVMCopySign extends LLVMBuiltin {

        @Specialization
        protected float doFloat(float magnitude, float sign) {
            return Math.copySign(magnitude, sign);
        }

        @Specialization
        protected double doDouble(double magnitude, double sign) {
            return Math.copySign(magnitude, sign);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloat(LLVM80BitFloat magnitude, LLVM80BitFloat sign) {
            if (magnitude.getSign() != sign.getSign()) {
                return magnitude.negate();
            } else {
                return magnitude;
            }
        }
    }

    @NodeChild(value = "magnitude", type = LLVMExpressionNode.class)
    @NodeChild(value = "sign", type = LLVMExpressionNode.class)
    public abstract static class LLVMCopySignVector extends LLVMBuiltin {

        private final int vectorLength;

        protected LLVMCopySignVector(int vectorLength) {
            this.vectorLength = vectorLength;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doFloatVector(LLVMFloatVector magnitude, LLVMFloatVector sign) {
            assert magnitude.getLength() == vectorLength;
            assert sign.getLength() == vectorLength;
            float[] result = new float[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Math.copySign(magnitude.getValue(i), sign.getValue(i));
            }
            return LLVMFloatVector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doDoubleVector(LLVMDoubleVector magnitude, LLVMDoubleVector sign) {
            assert magnitude.getLength() == vectorLength;
            assert sign.getLength() == vectorLength;
            double[] result = new double[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Math.copySign(magnitude.getValue(i), sign.getValue(i));
            }
            return LLVMDoubleVector.create(result);
        }
    }

    /*
     * llvm.minimum / llvm.maximum are the IEEE-754-2019 minimum/maximum: they propagate NaN (if
     * either operand is NaN the result is NaN) and treat -0.0 as strictly less than +0.0. Java's
     * Math.min / Math.max match both of these behaviors exactly, so they map bit-for-bit onto the
     * hardware minsd/maxsd-based lowering clang emits for these intrinsics.
     */
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMMinimum extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value1, float value2) {
            return Math.min(value1, value2);
        }

        @Specialization
        protected double doIntrinsic(double value1, double value2) {
            return Math.min(value1, value2);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMMinimumVector extends LLVMBuiltin {

        private final int vectorLength;

        protected LLVMMinimumVector(int vectorLength) {
            this.vectorLength = vectorLength;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doFloatVector(LLVMFloatVector value1, LLVMFloatVector value2) {
            assert value1.getLength() == vectorLength;
            assert value2.getLength() == vectorLength;
            float[] result = new float[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Math.min(value1.getValue(i), value2.getValue(i));
            }
            return LLVMFloatVector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doDoubleVector(LLVMDoubleVector value1, LLVMDoubleVector value2) {
            assert value1.getLength() == vectorLength;
            assert value2.getLength() == vectorLength;
            double[] result = new double[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Math.min(value1.getValue(i), value2.getValue(i));
            }
            return LLVMDoubleVector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMMaximum extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value1, float value2) {
            return Math.max(value1, value2);
        }

        @Specialization
        protected double doIntrinsic(double value1, double value2) {
            return Math.max(value1, value2);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMMaximumVector extends LLVMBuiltin {

        private final int vectorLength;

        protected LLVMMaximumVector(int vectorLength) {
            this.vectorLength = vectorLength;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doFloatVector(LLVMFloatVector value1, LLVMFloatVector value2) {
            assert value1.getLength() == vectorLength;
            assert value2.getLength() == vectorLength;
            float[] result = new float[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Math.max(value1.getValue(i), value2.getValue(i));
            }
            return LLVMFloatVector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doDoubleVector(LLVMDoubleVector value1, LLVMDoubleVector value2) {
            assert value1.getLength() == vectorLength;
            assert value2.getLength() == vectorLength;
            double[] result = new double[vectorLength];
            for (int i = 0; i < vectorLength; i++) {
                result[i] = Math.max(value1.getValue(i), value2.getValue(i));
            }
            return LLVMDoubleVector.create(result);
        }
    }

    abstract static class LLVMMinMaxOperator {
        protected abstract boolean compare(boolean a, boolean b);

        protected abstract int compare(int a, int b);

        protected abstract long compare(long a, long b);

        protected abstract float compare(float a, float b);

        protected abstract double compare(double a, double b);

        protected abstract LLVMPointer compare(LLVMPointer a, long aCmp, LLVMPointer b, long bCmp);
    }

    public static final class LLVMUmaxOperator extends LLVMMinMaxOperator {
        public static final LLVMUmaxOperator INSTANCE = new LLVMUmaxOperator();

        @Override
        protected boolean compare(boolean a, boolean b) {
            return a || b;
        }

        @Override
        protected int compare(int a, int b) {
            return Integer.compareUnsigned(a, b) >= 0 ? a : b;
        }

        @Override
        protected long compare(long a, long b) {
            return Long.compareUnsigned(a, b) >= 0 ? a : b;
        }

        @Override
        protected float compare(float a, float b) {
            return Math.max(a, b);
        }

        @Override
        protected double compare(double a, double b) {
            return Math.max(a, b);
        }

        @Override
        protected LLVMPointer compare(LLVMPointer a, long aCmp, LLVMPointer b, long bCmp) {
            return Long.compareUnsigned(aCmp, bCmp) >= 0 ? a : b;
        }
    }

    public static final class LLVMUminOperator extends LLVMMinMaxOperator {
        public static final LLVMUminOperator INSTANCE = new LLVMUminOperator();

        @Override
        protected boolean compare(boolean a, boolean b) {
            return a && b;
        }

        @Override
        protected int compare(int a, int b) {
            return Integer.compareUnsigned(a, b) <= 0 ? a : b;
        }

        @Override
        protected long compare(long a, long b) {
            return Long.compareUnsigned(a, b) <= 0 ? a : b;
        }

        @Override
        protected float compare(float a, float b) {
            return Math.min(a, b);
        }

        @Override
        protected double compare(double a, double b) {
            return Math.min(a, b);
        }

        @Override
        protected LLVMPointer compare(LLVMPointer a, long aCmp, LLVMPointer b, long bCmp) {
            return Long.compareUnsigned(aCmp, bCmp) <= 0 ? a : b;
        }
    }

    public static final class LLVMSmaxOperator extends LLVMMinMaxOperator {
        public static final LLVMSmaxOperator INSTANCE = new LLVMSmaxOperator();

        @Override
        protected boolean compare(boolean a, boolean b) {
            return a || b;
        }

        @Override
        protected int compare(int a, int b) {
            return Math.max(a, b);
        }

        @Override
        protected long compare(long a, long b) {
            return Math.max(a, b);
        }

        @Override
        protected float compare(float a, float b) {
            return Math.max(a, b);
        }

        @Override
        protected double compare(double a, double b) {
            return Math.max(a, b);
        }

        @Override
        protected LLVMPointer compare(LLVMPointer a, long aCmp, LLVMPointer b, long bCmp) {
            return aCmp >= bCmp ? a : b;
        }
    }

    public static final class LLVMSminOperator extends LLVMMinMaxOperator {
        public static final LLVMSminOperator INSTANCE = new LLVMSminOperator();

        @Override
        protected boolean compare(boolean a, boolean b) {
            return a && b;
        }

        @Override
        protected int compare(int a, int b) {
            return Math.min(a, b);
        }

        @Override
        protected long compare(long a, long b) {
            return Math.min(a, b);
        }

        @Override
        protected float compare(float a, float b) {
            return Math.min(a, b);
        }

        @Override
        protected double compare(double a, double b) {
            return Math.min(a, b);
        }

        @Override
        protected LLVMPointer compare(LLVMPointer a, long aCmp, LLVMPointer b, long bCmp) {
            return aCmp <= bCmp ? a : b;
        }
    }

    public abstract static class LLVMAbstractMinMaxNode extends LLVMBuiltin {
        protected abstract LLVMMinMaxOperator getOperator();

        protected byte compare(byte a, byte b) {
            return (byte) getOperator().compare(a, b);
        }

        protected short compare(short a, short b) {
            return (short) getOperator().compare(a, b);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "operator", type = LLVMMinMaxOperator.class)
    public abstract static class LLVMScalarMinMaxNode extends LLVMAbstractMinMaxNode {
        @Specialization
        protected boolean doI1Scalar(boolean a, boolean b) {
            return getOperator().compare(a, b);
        }

        @Specialization
        protected byte doI8Scalar(byte a, byte b) {
            return compare(a, b);
        }

        @Specialization
        protected short doI16Vector(short a, short b) {
            return compare(a, b);
        }

        @Specialization
        protected int doI32Vector(int a, int b) {
            return getOperator().compare(a, b);
        }

        @Specialization
        protected long doI64Vector(long a, long b) {
            return getOperator().compare(a, b);
        }

        @Specialization
        protected float doFloatVector(float a, float b) {
            return getOperator().compare(a, b);
        }

        @Specialization
        protected double doDoubleVector(double a, double b) {
            return getOperator().compare(a, b);
        }

        @Specialization
        protected LLVMPointer doPointer(LLVMPointer a, LLVMPointer b,
                        @Cached ToComparableValue aComp,
                        @Cached ToComparableValue bComp) {
            return getOperator().compare(a, aComp.executeWithTarget(a), b, bComp.executeWithTarget(b));
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    @NodeField(name = "operator", type = LLVMMinMaxOperator.class)
    public abstract static class LLVMVectorMinMaxNode extends LLVMAbstractMinMaxNode {
        protected abstract int getVectorLength();

        @Specialization
        protected boolean doI1Scalar(boolean a, boolean b) {
            return getOperator().compare(a, b);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doI1Vector(LLVMI1Vector a, LLVMI1Vector b) {
            assert a.getLength() == getVectorLength();
            assert b.getLength() == getVectorLength();
            boolean[] result = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = getOperator().compare(a.getValue(i), b.getValue(i));
            }
            return LLVMI1Vector.create(result);
        }

        @Specialization
        protected byte doI8Scalar(byte a, byte b) {
            return compare(a, b);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doI8Vector(LLVMI8Vector a, LLVMI8Vector b) {
            assert a.getLength() == getVectorLength();
            assert b.getLength() == getVectorLength();
            byte[] result = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                byte aValue = a.getValue(i);
                byte bValue = b.getValue(i);
                result[i] = compare(aValue, bValue);
            }
            return LLVMI8Vector.create(result);
        }

        @Specialization
        protected short doI16Vector(short a, short b) {
            return compare(a, b);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI16Vector(LLVMI16Vector a, LLVMI16Vector b) {
            assert a.getLength() == getVectorLength();
            assert b.getLength() == getVectorLength();
            short[] result = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                short aValue = a.getValue(i);
                short bValue = b.getValue(i);
                result[i] = compare(aValue, bValue);
            }
            return LLVMI16Vector.create(result);
        }

        @Specialization
        protected int doI32Vector(int a, int b) {
            return getOperator().compare(a, b);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI32Vector(LLVMI32Vector a, LLVMI32Vector b) {
            assert a.getLength() == getVectorLength();
            assert b.getLength() == getVectorLength();
            int[] result = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                int aValue = a.getValue(i);
                int bValue = b.getValue(i);
                result[i] = getOperator().compare(aValue, bValue);
            }
            return LLVMI32Vector.create(result);
        }

        @Specialization
        protected long doI64Vector(long a, long b) {
            return getOperator().compare(a, b);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI64Vector(LLVMI64Vector a, LLVMI64Vector b) {
            assert a.getLength() == getVectorLength();
            assert b.getLength() == getVectorLength();
            long[] result = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                long aValue = a.getValue(i);
                long bValue = b.getValue(i);
                result[i] = getOperator().compare(aValue, bValue) >= 0 ? aValue : bValue;
            }
            return LLVMI64Vector.create(result);
        }

        @Specialization
        protected float doFloatVector(float a, float b) {
            return getOperator().compare(a, b);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doFloatVector(LLVMFloatVector a, LLVMFloatVector b) {
            assert a.getLength() == getVectorLength();
            assert b.getLength() == getVectorLength();
            float[] result = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = getOperator().compare(a.getValue(i), b.getValue(i));
            }
            return LLVMFloatVector.create(result);
        }

        @Specialization
        protected double doDoubleVector(double a, double b) {
            return getOperator().compare(a, b);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doDoubleVector(LLVMDoubleVector a, LLVMDoubleVector b) {
            assert a.getLength() == getVectorLength();
            assert b.getLength() == getVectorLength();
            double[] result = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = getOperator().compare(a.getValue(i), b.getValue(i));
            }
            return LLVMDoubleVector.create(result);
        }
    }
}
