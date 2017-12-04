/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.cast;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToI64Node extends LLVMExpressionNode {

    @Specialization
    protected long doI64(VirtualFrame frame, LLVMFunctionDescriptor from,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
        return toNative.executeWithTarget(frame, from).getVal();
    }

    @Specialization
    protected long doGlobal(VirtualFrame frame, LLVMGlobal from,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode access) {
        return access.executeWithTarget(frame, from).getVal();
    }

    @Child private ForeignToLLVM convert = ForeignToLLVM.create(ForeignToLLVMType.I64);

    @Specialization
    protected long doTruffleObject(VirtualFrame frame, LLVMTruffleObject from,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toAddress) {
        return toAddress.executeWithTarget(frame, from).getVal();
    }

    @Specialization
    protected long doLLVMBoxedPrimitive(VirtualFrame frame, LLVMBoxedPrimitive from) {
        return (long) convert.executeWithTarget(frame, from.getValue());
    }

    public abstract static class LLVMToI64NoZeroExtNode extends LLVMToI64Node {

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

        @Specialization
        protected long doI64(LLVMAddress from) {
            return from.getVal();
        }

        @Specialization
        protected long doI64(LLVMFloatVector from) {
            float f1 = from.getValue(0);
            float f2 = from.getValue(1);
            long composedValue = (long) Float.floatToRawIntBits(f1) << Float.SIZE | Float.floatToRawIntBits(f2);
            return composedValue;
        }
    }

    public abstract static class LLVMToI64BitNode extends LLVMToI64Node {

        protected static long castI1Vector(LLVMI1Vector from, int elem) {
            if (from.getLength() != elem) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            long res = 0;
            for (int i = 0; i < elem; i++) {
                res |= (from.getValue(i) ? 1L : 0L) << i;
            }
            return res;
        }

        protected static long castI8Vector(LLVMI8Vector from, int elem) {
            if (from.getLength() != elem) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            long res = 0;
            for (int i = 0; i < elem; i++) {
                res |= ((long) (from.getValue(i) & LLVMExpressionNode.I8_MASK)) << (i * Byte.SIZE);
            }
            return res;
        }

        protected static long castI16Vector(LLVMI16Vector from, int elem) {
            if (from.getLength() != elem) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            long res = 0;
            for (int i = 0; i < elem; i++) {
                res |= ((long) (from.getValue(i) & LLVMExpressionNode.I16_MASK)) << (i * Short.SIZE);
            }
            return res;
        }

        protected static long castI32Vector(LLVMI32Vector from, int elem) {
            if (from.getLength() != elem) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            long res = 0;
            for (int i = 0; i < elem; i++) {
                res |= (from.getValue(i) & LLVMExpressionNode.I32_MASK) << (i * Integer.SIZE);
            }
            return res;
        }

        protected static long castFloatVector(LLVMFloatVector from, int elem) {
            if (from.getLength() != elem) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            long res = 0;
            for (int i = 0; i < elem; i++) {
                res |= (Float.floatToIntBits(from.getValue(i)) & LLVMExpressionNode.I32_MASK) << (i * Integer.SIZE);
            }
            return res;
        }

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
            if (from.getLength() != 1) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            return from.getValue(0);
        }

        @Specialization
        protected long doDoubleVector(LLVMDoubleVector from) {
            if (from.getLength() != 1) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            return Double.doubleToLongBits(from.getValue(0));
        }
    }

    public abstract static class LLVMToI64ZeroExtNode extends LLVMToI64Node {

        @Specialization
        protected long doI64(boolean from) {
            return from ? 1 : 0;
        }

        @Specialization
        protected long doI64(byte from) {
            return from & LLVMExpressionNode.I8_MASK;
        }

        @Specialization
        protected long doI64(short from) {
            return from & LLVMExpressionNode.I16_MASK;
        }

        @Specialization
        protected long doI64(int from) {
            return from & LLVMExpressionNode.I32_MASK;
        }

        @Specialization
        protected long doI64(LLVMIVarBit from) {
            return from.getZeroExtendedLongValue();
        }

        @Specialization
        protected long doI64(long from) {
            return from;
        }
    }

    public abstract static class LLVMToUnsignedI64Node extends LLVMToI32Node {

        @Specialization
        protected long doI64(double from) {
            if (from >= Long.MAX_VALUE) {
                return ((long) (Long.MAX_VALUE - from)) ^ 0x80000000_00000000L;
            } else {
                return (long) from;
            }
        }

        @Specialization
        protected long doI64(long from) {
            return from;
        }
    }
}
