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
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToDoubleNode extends LLVMExpressionNode {

    @Child private ForeignToLLVM toDouble = ForeignToLLVM.create(ForeignToLLVMType.DOUBLE);

    @Specialization
    public double executeLLVMBoxedPrimitive(LLVMBoxedPrimitive from) {
        return (double) toDouble.executeWithTarget(from.getValue());
    }

    @Child private Node isNull = Message.IS_NULL.createNode();
    @Child private Node isBoxed = Message.IS_BOXED.createNode();
    @Child private Node unbox = Message.UNBOX.createNode();

    @Specialization(guards = "notLLVM(from)")
    public double executeTruffleObject(TruffleObject from) {
        if (ForeignAccess.sendIsNull(isNull, from)) {
            return 0.0;
        } else if (ForeignAccess.sendIsBoxed(isBoxed, from)) {
            try {
                return (double) toDouble.executeWithTarget(ForeignAccess.sendUnbox(unbox, from));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Not convertable");
    }

    public abstract static class LLVMToDoubleNoZeroExtNode extends LLVMToDoubleNode {

        @Specialization
        public double executeDouble(boolean from) {
            return from ? 1.0 : 0.0;
        }

        @Specialization
        public double executeDouble(byte from) {
            return from;
        }

        @Specialization
        public double executeDouble(short from) {
            return from;
        }

        @Specialization
        public double executeDouble(int from) {
            return from;
        }

        @Specialization
        public double executeDouble(long from) {
            return from;
        }

        @Specialization
        public double executeDouble(float from) {
            return from;
        }

        @Specialization
        public double executeDouble(double from) {
            return from;
        }

        @Specialization
        public double executeDouble(LLVM80BitFloat from) {
            return from.getDoubleValue();
        }
    }

    public abstract static class LLVMToDoubleZeroExtNode extends LLVMToDoubleNode {

        @Specialization
        public double executeDouble(boolean from) {
            return from ? 1 : 0;
        }

        @Specialization
        public double executeDouble(byte from) {
            return from & LLVMExpressionNode.I8_MASK;
        }

        @Specialization
        public double executeDouble(short from) {
            return from & LLVMExpressionNode.I16_MASK;
        }

        @Specialization
        public double executeInt(int from) {
            return from & LLVMExpressionNode.I32_MASK;
        }

        @Specialization
        public double executeDouble(long from) {
            return from;
        }

        @Specialization
        public double executeDouble(double from) {
            return from;
        }

    }

    public abstract static class LLVMToDoubleUnsignedNode extends LLVMToDoubleNode {

        private static final double LEADING_BIT = 0x1.0p63;

        @Specialization
        public double executeDouble(int from) {
            return from & LLVMExpressionNode.I32_MASK;
        }

        @Specialization
        public double executeDouble(long from) {
            double val = from & Long.MAX_VALUE;
            if (from < 0) {
                val += LEADING_BIT;
            }
            return val;
        }

        @Specialization
        public double executeDouble(double from) {
            return from;
        }
    }

    public abstract static class LLVMToDoubleBitNode extends LLVMToDoubleNode {

        @Specialization
        public double executeDouble(long from) {
            return Double.longBitsToDouble(from);
        }

        @Specialization
        public double executeDouble(double from) {
            return from;
        }
    }

}
