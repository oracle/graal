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
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToI1Node extends LLVMExpressionNode {

    @Child private Node isNull = Message.IS_NULL.createNode();
    @Child private Node isBoxed = Message.IS_BOXED.createNode();
    @Child private Node unbox = Message.UNBOX.createNode();
    @Child private ForeignToLLVM toBool = ForeignToLLVM.create(ForeignToLLVMType.I1);

    @Specialization(guards = "notLLVM(from)")
    public boolean executeTruffleObject(TruffleObject from) {
        if (ForeignAccess.sendIsNull(isNull, from)) {
            return false;
        } else if (ForeignAccess.sendIsBoxed(isBoxed, from)) {
            try {
                return (boolean) toBool.executeWithTarget(ForeignAccess.sendUnbox(unbox, from));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Not convertable");
    }

    @Specialization
    public boolean executeLLVMBoxedPrimitive(LLVMBoxedPrimitive from) {
        return (boolean) toBool.executeWithTarget(from.getValue());
    }

    public abstract static class LLVMToI1NoZeroExtNode extends LLVMToI1Node {
        @Specialization
        public boolean executeI1(byte from) {
            return (from & 1) != 0;
        }

        @Specialization
        public boolean executeI1(short from) {
            return (from & 1) != 0;
        }

        @Specialization
        public boolean executeI1(int from) {
            return (from & 1) != 0;
        }

        @Specialization
        public boolean executeI1(long from) {
            return (from & 1) != 0;
        }

        @Specialization
        public boolean executeI1(float from) {
            return from != 0;
        }

        @Specialization
        public boolean executeI1(double from) {
            return from != 0;
        }

        @Specialization
        public boolean executeLLVMFunction(boolean from) {
            return from;
        }
    }

    public abstract static class LLVMToI1BitNode extends LLVMToI1Node {

        @Specialization
        public boolean executeI1(boolean from) {
            return from;
        }

        @Specialization
        public boolean executeI1Vector(LLVMI1Vector from) {
            if (from.getLength() != 1) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            return from.getValue(0);
        }
    }

}
