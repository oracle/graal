/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMX86_ConversionNode {

    // Convert one float/double to i32 with x86 semantics: on NaN or out-of-range the hardware
    // yields the "integer indefinite" value 0x80000000; otherwise truncate toward zero (cvtt*)
    // or round to nearest-even (cvt*, the default MXCSR mode Sulong models).
    static int floatToInt(float value, boolean truncate) {
        if (Float.isNaN(value) || value >= 2147483648.0f || value < -2147483648.0f) {
            return Integer.MIN_VALUE;
        }
        return truncate ? (int) value : (int) Math.rint(value);
    }

    static int doubleToInt(double value, boolean truncate) {
        if (Double.isNaN(value) || value >= 2147483648.0 || value < -2147483648.0) {
            return Integer.MIN_VALUE;
        }
        return truncate ? (int) value : (int) Math.rint(value);
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_ConversionFloatToIntNode extends LLVMBuiltin { // implements
                                                                                        // cvtss2si

        @Specialization
        protected int doIntrinsic(LLVMFloatVector vector) {
            if (vector.getLength() != 4) {
                throw CompilerDirectives.shouldNotReachHere("cvtss2si requires a float[4] as parameter");
            }

            return Math.round(vector.getValue(0));
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_ConversionDoubleToIntNode extends LLVMBuiltin { // implements
                                                                                         // cvtsd2si

        @Specialization
        protected int doIntrinsic(LLVMDoubleVector vector) {
            if (vector.getLength() != 2) {
                throw CompilerDirectives.shouldNotReachHere("cvtsd2si requires a double[2] as parameter");
            }

            // returns an int instead of a long,
            // causes an exception in one OpenCV test application when returning a long
            return Math.toIntExact(Math.round(vector.getValue(0)));
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_ConversionDoubleToFloatNode extends LLVMBuiltin { // cvtpd2ps
        @Specialization
        protected LLVMFloatVector doIntrinsic(LLVMDoubleVector vector) {
            // sse2 cvtpd2ps: convert <2 x double> to the low two lanes of a <4 x float>, zeroing
            // the upper two lanes. The avx cvt.pd2.ps.256 form converts <4 x double> to a full
            // <4 x float> with no padding. Java's (float) cast rounds to nearest-even, matching
            // the default MXCSR rounding mode Sulong models.
            int len = vector.getLength();
            float[] result = new float[len == 2 ? 4 : len];
            for (int i = 0; i < len; i++) {
                result[i] = (float) vector.getValue(i);
            }
            return LLVMFloatVector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "truncate", type = boolean.class)
    public abstract static class LLVMX86_ConversionFloatToIntVectorNode extends LLVMBuiltin { // cvt(t)ps2dq
        protected abstract boolean isTruncate();

        @Specialization
        protected LLVMI32Vector doConvert(LLVMFloatVector vector) {
            // 128-bit form: <4 x float> -> <4 x i32>; avx 256-bit form: <8 x float> -> <8 x i32>.
            int len = vector.getLength();
            int[] result = new int[len];
            for (int i = 0; i < len; i++) {
                result[i] = floatToInt(vector.getValue(i), isTruncate());
            }
            return LLVMI32Vector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "truncate", type = boolean.class)
    public abstract static class LLVMX86_ConversionDoubleToIntVectorNode extends LLVMBuiltin { // cvt(t)pd2dq
        protected abstract boolean isTruncate();

        @Specialization
        protected LLVMI32Vector doConvert(LLVMDoubleVector vector) {
            // sse2 form: <2 x double> -> low two lanes of a <4 x i32>, upper two zeroed; avx
            // 256-bit form: <4 x double> -> <4 x i32> with no padding.
            int len = vector.getLength();
            int[] result = new int[len == 2 ? 4 : len];
            for (int i = 0; i < len; i++) {
                result[i] = doubleToInt(vector.getValue(i), isTruncate());
            }
            return LLVMI32Vector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_Pmovmskb128 extends LLVMBuiltin {

        private static final int VECTOR_LENGTH = 16;

        @Specialization
        @ExplodeLoop
        protected int doIntrinsic128(LLVMI8Vector vector) {
            if (vector.getLength() != VECTOR_LENGTH) {
                throw CompilerDirectives.shouldNotReachHere("expected a <16 x i8> vector");
            }
            int result = 0;
            for (int i = 0; i < VECTOR_LENGTH; i++) {
                int currentByte = vector.getValue(i);
                int mostSignificantBit = (currentByte & 0xff) >> (Byte.SIZE - 1);
                result |= mostSignificantBit << i;
            }

            return result;
        }

        @Specialization
        @ExplodeLoop
        protected int doIntrinsic32(float vector) {
            int result = 0;
            int v = Float.floatToRawIntBits(vector);
            for (int i = 0; i < 4; i++) {
                int currentByte = (v >> (i * Byte.SIZE)) & 0xff;
                int mostSignificantBit = currentByte >> (Byte.SIZE - 1);
                result |= mostSignificantBit << i;
            }

            return result;
        }

        @Specialization
        @ExplodeLoop
        protected int doIntrinsic64(double vector) {
            int result = 0;
            long v = Double.doubleToRawLongBits(vector);
            for (int i = 0; i < 8; i++) {
                int currentByte = (int) (v >> (i * Byte.SIZE)) & 0xff;
                int mostSignificantBit = currentByte >> (Byte.SIZE - 1);
                result |= mostSignificantBit << i;
            }

            return result;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMX86_Movmskpd extends LLVMBuiltin {

        @Specialization
        protected int doIntrinsic(LLVMDoubleVector vector) {
            if (vector.getLength() != 2) {
                throw CompilerDirectives.shouldNotReachHere("expected a <2 x double> vector");
            }
            return ((vector.getValue(1) < 0 ? 1 : 0) << 1) | (vector.getValue(0) < 0 ? 1 : 0);
        }
    }
}
