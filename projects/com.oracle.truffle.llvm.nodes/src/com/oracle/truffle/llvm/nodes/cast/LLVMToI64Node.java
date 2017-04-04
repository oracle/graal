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
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMTruffleNull;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToI64Node extends LLVMExpressionNode {

    @Specialization
    public long executeI64(LLVMFunctionDescriptor from) {
        return from.getFunctionIndex();
    }

    @Specialization
    public long executeI64(LLVMFunctionHandle from) {
        return from.getFunctionIndex();
    }

    @Specialization
    public long executeLLVMTruffleNull(@SuppressWarnings("unused") LLVMTruffleNull from) {
        return 0;
    }

    @Specialization
    public long executeLLVMAddress(LLVMGlobalVariableDescriptor from) {
        return from.getNativeAddress().getVal();
    }

    @Specialization
    public long executeLLVMTruffleObject(LLVMTruffleObject from) {
        return (executeTruffleObject(from.getObject()) + from.getOffset());
    }

    @Child private Node isNull = Message.IS_NULL.createNode();
    @Child private Node isBoxed = Message.IS_BOXED.createNode();
    @Child private Node unbox = Message.UNBOX.createNode();
    @Child private ToLLVMNode convert = ToLLVMNode.createNode(long.class);

    @Specialization(guards = "notLLVM(from)")
    public long executeTruffleObject(TruffleObject from) {
        if (ForeignAccess.sendIsNull(isNull, from)) {
            return 0;
        } else if (ForeignAccess.sendIsBoxed(isBoxed, from)) {
            try {
                return (long) convert.executeWithTarget(ForeignAccess.sendUnbox(unbox, from));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Not convertable");
    }

    @Specialization
    public long executeLLVMBoxedPrimitive(LLVMBoxedPrimitive from) {
        return (long) convert.executeWithTarget(from.getValue());
    }

    public abstract static class LLVMToI64NoZeroExtNode extends LLVMToI64Node {

        @Specialization
        public long executeI64(boolean from) {
            return from ? -1 : 0;
        }

        @Specialization
        public long executeI64(byte from) {
            return from;
        }

        @Specialization
        public long executeI64(short from) {
            return from;
        }

        @Specialization
        public long executeI64(int from) {
            return from;
        }

        @Specialization
        public long executeI64(long from) {
            return from;
        }

        @Specialization
        public long executeI64(LLVMIVarBit from) {
            return from.getLongValue();
        }

        @Specialization
        public long executeI64(float from) {
            return (long) from;
        }

        @Specialization
        public long executeI64(double from) {
            return (long) from;
        }

        @Specialization
        public long executeI64(LLVM80BitFloat from) {
            return from.getLongValue();
        }

        @Specialization
        public long executeI64(LLVMAddress from) {
            return from.getVal();
        }

        @Specialization
        public long executeI64(LLVMFloatVector from) {
            float f1 = from.getValue(0);
            float f2 = from.getValue(1);
            long composedValue = (long) Float.floatToRawIntBits(f1) << Float.SIZE | Float.floatToRawIntBits(f2);
            return composedValue;
        }

    }

    public abstract static class LLVMToI64BitNode extends LLVMToI64Node {

        @Specialization
        public long executeI64(double from) {
            return Double.doubleToRawLongBits(from);
        }

        @Specialization
        public long executeI64(long from) {
            return from;
        }
    }

    public abstract static class LLVMToI64ZeroExtNode extends LLVMToI64Node {

        @Specialization
        public long executeI64(boolean from) {
            return from ? 1 : 0;
        }

        @Specialization
        public long executeI64(byte from) {
            return from & LLVMExpressionNode.I8_MASK;
        }

        @Specialization
        public long executeI64(short from) {
            return from & LLVMExpressionNode.I16_MASK;
        }

        @Specialization
        public long executeI64(int from) {
            return from & LLVMExpressionNode.I32_MASK;
        }

        @Specialization
        public long executeI64(LLVMIVarBit from) {
            return from.getZeroExtendedLongValue();
        }

        @Specialization
        public long executeI64(long from) {
            return from;
        }
    }

    public abstract static class LLVMToUnsignedI64Node extends LLVMToI32Node {

        @Specialization
        public long executeI64(double from) {
            if (from >= Long.MAX_VALUE) {
                return ((long) (Long.MAX_VALUE - from)) ^ 0x80000000_00000000L;
            } else {
                return (long) from;
            }
        }

        @Specialization
        public long executeI64(long from) {
            return from;
        }
    }

}
