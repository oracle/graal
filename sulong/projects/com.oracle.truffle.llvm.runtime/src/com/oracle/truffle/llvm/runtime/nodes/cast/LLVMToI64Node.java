/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToI64Node extends LLVMExpressionNode {
    private static final float MAX_LONG_AS_FLOAT = Long.MAX_VALUE;
    private static final double MAX_LONG_AS_DOUBLE = Long.MAX_VALUE;

    public abstract Object executeWithTarget(Object o);

    @Specialization
    protected long doNative(LLVMNativePointer from) {
        return from.asNative();
    }

    @Specialization
    protected Object doManaged(LLVMManagedPointer from) {
        return from;
    }

    public abstract static class LLVMSignedCastToI64Node extends LLVMToI64Node {

        @Specialization
        protected long doI64(boolean from) {
            return from ? -1 : 0;
        }

        @Specialization
        protected long doI64(byte from) {
            return from;
        }

        @Specialization
        protected long doI64(short from) {
            return from;
        }

        @Specialization
        protected long doI64(int from) {
            return from;
        }

        @Specialization
        protected long doI64(long from) {
            return from;
        }

        @Specialization
        protected long doI64(LLVMIVarBit from) {
            return from.getLongValue();
        }

        @Specialization
        protected long doI64(float from) {
            return (long) from;
        }

        @Specialization
        protected long doI64(double from) {
            return (long) from;
        }

        @Specialization
        protected long doI64(LLVM80BitFloat from) {
            return from.getLongValue();
        }
    }

    public abstract static class LLVMUnsignedCastToI64Node extends LLVMToI64Node {
        @Specialization
        protected long doI1(boolean from) {
            return from ? 1 : 0;
        }

        @Specialization
        protected long doI8(byte from) {
            return from & LLVMExpressionNode.I8_MASK;
        }

        @Specialization
        protected long doI16(short from) {
            return from & LLVMExpressionNode.I16_MASK;
        }

        @Specialization
        protected long doI32(int from) {
            return from & LLVMExpressionNode.I32_MASK;
        }

        @Specialization
        protected long doI64(long from) {
            return from;
        }

        @Specialization
        protected long doIVarBit(LLVMIVarBit from) {
            return from.getZeroExtendedLongValue();
        }

        private static boolean fitsIntoSignedLong(float from) {
            return from < MAX_LONG_AS_FLOAT;
        }

        private static boolean fitsIntoSignedLong(double from) {
            return from < MAX_LONG_AS_DOUBLE;
        }

        @Specialization
        protected long doFloat(float from,
                        @Cached ConditionProfile profile) {
            if (profile.profile(fitsIntoSignedLong(from))) {
                return (long) from;
            } else {
                return (long) (from + Long.MIN_VALUE) - Long.MIN_VALUE;
            }
        }

        @Specialization
        protected long doDouble(double from,
                        @Cached ConditionProfile profile) {
            if (profile.profile(fitsIntoSignedLong(from))) {
                return (long) from;
            } else {
                return (long) (from + Long.MIN_VALUE) - Long.MIN_VALUE;
            }
        }

        @Specialization
        protected long do80LLVMBitFloat(LLVM80BitFloat from) {
            return from.getLongValue();
        }
    }

    public abstract static class LLVMBitcastToI64Node extends LLVMToI64Node {
        @Specialization
        protected long doI64(double from) {
            return Double.doubleToRawLongBits(from);
        }

        @Specialization
        protected long doI64(long from) {
            return from;
        }

        @Specialization
        protected long doI1Vector(LLVMI1Vector from) {
            return castI1Vector(from, Long.SIZE);
        }

        @Specialization
        protected long doI8Vector(LLVMI8Vector from) {
            return castI8Vector(from, Long.SIZE / Byte.SIZE);
        }

        @Specialization
        protected long doI16Vector(LLVMI16Vector from) {
            return castI16Vector(from, Long.SIZE / Short.SIZE);
        }

        @Specialization
        protected long doI32Vector(LLVMI32Vector from) {
            return castI32Vector(from, Long.SIZE / Integer.SIZE);
        }

        @Specialization
        protected long doFloatVector(LLVMFloatVector from) {
            return castFloatVector(from, Long.SIZE / Float.SIZE);
        }

        @Specialization
        protected long doI64Vector(LLVMI64Vector from) {
            assert from.getLength() == 1 : "invalid vector size";
            return from.getValue(0);
        }

        @Specialization
        protected long doDoubleVector(LLVMDoubleVector from) {
            assert from.getLength() == 1 : "invalid vector size";
            return Double.doubleToLongBits(from.getValue(0));
        }

        @ExplodeLoop
        protected static long castI1Vector(LLVMI1Vector from, int elem) {
            assert from.getLength() == elem : "invalid vector size";
            long res = 0;
            for (int i = 0; i < elem; i++) {
                res |= (from.getValue(i) ? 1L : 0L) << i;
            }
            return res;
        }

        @ExplodeLoop
        protected static long castI8Vector(LLVMI8Vector from, int elem) {
            assert from.getLength() == elem : "invalid vector size";
            long res = 0;
            for (int i = 0; i < elem; i++) {
                res |= ((long) (from.getValue(i) & LLVMExpressionNode.I8_MASK)) << (i * Byte.SIZE);
            }
            return res;
        }

        @ExplodeLoop
        protected static long castI16Vector(LLVMI16Vector from, int elem) {
            assert from.getLength() == elem : "invalid vector size";
            long res = 0;
            for (int i = 0; i < elem; i++) {
                res |= ((long) (from.getValue(i) & LLVMExpressionNode.I16_MASK)) << (i * Short.SIZE);
            }
            return res;
        }

        @ExplodeLoop
        protected static long castI32Vector(LLVMI32Vector from, int elem) {
            assert from.getLength() == elem : "invalid vector size";
            long res = 0;
            for (int i = 0; i < elem; i++) {
                res |= (from.getValue(i) & LLVMExpressionNode.I32_MASK) << (i * Integer.SIZE);
            }
            return res;
        }

        @ExplodeLoop
        protected static long castFloatVector(LLVMFloatVector from, int elem) {
            assert from.getLength() == elem : "invalid vector size";
            long res = 0;
            for (int i = 0; i < elem; i++) {
                res |= (Float.floatToIntBits(from.getValue(i)) & LLVMExpressionNode.I32_MASK) << (i * Integer.SIZE);
            }
            return res;
        }
    }
}
