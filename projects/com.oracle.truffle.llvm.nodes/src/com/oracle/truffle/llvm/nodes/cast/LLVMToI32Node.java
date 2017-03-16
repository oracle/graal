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
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMTruffleNull;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;

public abstract class LLVMToI32Node extends LLVMExpressionNode {

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMToI32NoZeroExtNode extends LLVMToI32Node {

        @Specialization
        public int executeI32(boolean from) {
            return from ? -1 : 0;
        }

        @Specialization
        public int executeI32(byte from) {
            return from;
        }

        @Specialization
        public int executeI32(short from) {
            return from;
        }

        @Specialization
        public int executeI32(LLVMAddress from) {
            return (int) from.getVal();
        }

        @Specialization
        public int executeI32(long from) {
            return (int) from;
        }

        @Specialization
        public int executeI32(LLVMIVarBit from) {
            return from.getIntValue();
        }

        @Specialization
        public int executeI32(float from) {
            return (int) from;
        }

        @Specialization
        public int executeI32(double from) {
            return (int) from;
        }

        @Specialization
        public int executeI32(LLVM80BitFloat from) {
            return from.getIntValue();
        }

        @Specialization
        public int executeI32(LLVMFunctionDescriptor from) {
            return from.getFunctionIndex();
        }

        @Specialization
        public int executeI32(LLVMFunctionHandle from) {
            return from.getFunctionIndex();
        }

        @Specialization
        public int executeLLVMTruffleNull(@SuppressWarnings("unused") LLVMTruffleNull from) {
            return 0;
        }

        @Child private Node isNull = Message.IS_NULL.createNode();
        @Child private Node isBoxed = Message.IS_BOXED.createNode();
        @Child private Node unbox = Message.UNBOX.createNode();
        @Child private ToLLVMNode convert = ToLLVMNode.createNode(int.class);

        @Specialization(guards = "notLLVM(from)")
        public int executeTruffleObject(TruffleObject from) {
            if (ForeignAccess.sendIsNull(isNull, from)) {
                return 0;
            } else if (ForeignAccess.sendIsBoxed(isBoxed, from)) {
                try {
                    return (int) convert.executeWithTarget(ForeignAccess.sendUnbox(unbox, from));
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Not convertable");
        }

        @Specialization
        public int executeUnbox(LLVMTruffleObject from) {
            try {
                int head = (int) convert.executeWithTarget(ForeignAccess.sendUnbox(unbox, from.getObject()));
                return (int) (head + from.getOffset());
            } catch (UnsupportedMessageException e) {
                throw new UnsupportedOperationException(e);
            }
        }

    }

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMToI32ZeroExtNode extends LLVMToI32Node {

        @Specialization
        public int executeI32(boolean from) {
            return from ? 1 : 0;
        }

        @Specialization
        public int executeI32(byte from) {
            return from & LLVMExpressionNode.I8_MASK;
        }

        @Specialization
        public int executeI32(short from) {
            return from & LLVMExpressionNode.I16_MASK;
        }

        @Specialization
        public int executeI32(LLVMIVarBit from) {
            return from.getZeroExtendedIntValue();
        }
    }

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMToI32BitNode extends LLVMToI32Node {

        @Specialization
        public int executeI32(float from) {
            return Float.floatToIntBits(from);
        }
    }

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMToUnsignedI32Node extends LLVMToI32Node {

        @Specialization
        public int executeI32(double from) {
            if (from > Integer.MAX_VALUE) {
                return (int) (from + Integer.MIN_VALUE) - Integer.MIN_VALUE;
            }
            return (int) from;
        }
    }
}
