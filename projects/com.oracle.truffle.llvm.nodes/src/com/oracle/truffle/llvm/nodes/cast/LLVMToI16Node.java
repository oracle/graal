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
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI64Node.LLVMToI64BitNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMToI16Node extends LLVMExpressionNode {

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMToI16NoZeroExtNode extends LLVMToI16Node {

        @Specialization
        public short executeLLVMFunction(short from) {
            return from;
        }

        @Specialization
        public short executeI16(boolean from) {
            return (short) (from ? -1 : 0);
        }

        @Specialization
        public short executeI16(byte from) {
            return from;
        }

        @Specialization
        public short executeI16(int from) {
            return (short) from;
        }

        @Specialization
        public short executeI16(long from) {
            return (short) from;
        }

        @Specialization
        public short executeI16(LLVMIVarBit from) {
            return from.getShortValue();
        }

        @Specialization
        public short executeI16(float from) {
            return (short) from;
        }

        @Specialization
        public short executeI16(double from) {
            return (short) from;
        }

        @Specialization
        public short executeLLVMAddress(LLVMAddress from) {
            return (short) from.getVal();
        }

        @Specialization
        public short executeLLVMAddress(LLVMGlobalVariable from, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            return (short) globalAccess.getNativeLocation(from).getVal();
        }

        @Child private Node isNull = Message.IS_NULL.createNode();
        @Child private Node isBoxed = Message.IS_BOXED.createNode();
        @Child private Node unbox = Message.UNBOX.createNode();
        @Child private ForeignToLLVM toShort = ForeignToLLVM.create(ForeignToLLVMType.I16);

        @Specialization
        public short executeLLVMTruffleObject(LLVMTruffleObject from) {
            TruffleObject base = from.getObject();
            if (ForeignAccess.sendIsNull(isNull, base)) {
                return (short) from.getOffset();
            } else if (ForeignAccess.sendIsBoxed(isBoxed, base)) {
                try {
                    short ptr = (short) toShort.executeWithTarget(ForeignAccess.sendUnbox(unbox, base));
                    return (short) (ptr + from.getOffset());
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new IllegalStateException(e);
                }
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Not convertable");
        }

        @Specialization
        public short executeLLVMBoxedPrimitive(LLVMBoxedPrimitive from) {
            return (short) toShort.executeWithTarget(from.getValue());
        }
    }

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMToI16ZeroExtNode extends LLVMToI16Node {

        @Specialization
        public short executeI16(boolean from) {
            return (short) (from ? 1 : 0);
        }

        @Specialization
        public short executeI16(byte from) {
            return (short) (from & LLVMExpressionNode.I8_MASK);
        }

        @Specialization
        public short executeI16(LLVMIVarBit from) {
            return from.getZeroExtendedShortValue();
        }

        @Specialization
        public short executeLLVMFunction(short from) {
            return from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMToI16BitNode extends LLVMToI16Node {

        @Specialization
        public short executeI16(short from) {
            return from;
        }

        @Specialization
        public short executeI1Vector(LLVMI1Vector from) {
            return (short) LLVMToI64BitNode.castI1Vector(from, Short.SIZE);
        }

        @Specialization
        public short executeI8Vector(LLVMI8Vector from) {
            return (short) LLVMToI64BitNode.castI8Vector(from, Short.SIZE / Byte.SIZE);
        }

        @Specialization
        public short executeI16Vector(LLVMI16Vector from) {
            if (from.getLength() != 1) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            return from.getValue(0);
        }
    }
}
