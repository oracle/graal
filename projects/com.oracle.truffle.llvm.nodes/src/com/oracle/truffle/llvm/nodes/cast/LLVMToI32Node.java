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
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToI32Node extends LLVMExpressionNode {

    @Specialization
    public int executeI32(LLVMFunctionDescriptor from) {
        return (int) from.getFunctionPointer();
    }

    @Specialization
    public int executeI32(LLVMFunctionHandle from) {
        return (int) from.getFunctionPointer();
    }

    @Specialization
    public int executeLLVMAddress(LLVMGlobalVariable from, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        return (int) globalAccess.getNativeLocation(from).getVal();
    }

    @Specialization
    public int executeLLVMTruffleObject(LLVMTruffleObject from) {
        return (int) (executeTruffleObject(from.getObject()) + from.getOffset());
    }

    @Child private Node isNull = Message.IS_NULL.createNode();
    @Child private Node isBoxed = Message.IS_BOXED.createNode();
    @Child private Node unbox = Message.UNBOX.createNode();
    @Child private Node asPointer = Message.AS_POINTER.createNode();
    @Child private Node toNative = Message.TO_NATIVE.createNode();
    @Child private ForeignToLLVM convert = ForeignToLLVM.create(ForeignToLLVMType.I32);

    @Specialization(guards = "notLLVM(from)")
    public int executeTruffleObject(TruffleObject from) {
        try {
            if (ForeignAccess.sendIsNull(isNull, from)) {
                return 0;
            } else if (ForeignAccess.sendIsBoxed(isBoxed, from)) {
                return (int) convert.executeWithTarget(ForeignAccess.sendUnbox(unbox, from));
            } else {
                TruffleObject n = (TruffleObject) ForeignAccess.sendToNative(toNative, from);
                return (int) (ForeignAccess.sendAsPointer(asPointer, n));
            }
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @Specialization
    public int executeLLVMBoxedPrimitive(LLVMBoxedPrimitive from) {
        return (int) convert.executeWithTarget(from.getValue());
    }

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
        public int executeI32(int from) {
            return from;
        }

    }

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

        @Specialization
        public int executeI32(int from) {
            return from;
        }
    }

    public abstract static class LLVMToI32BitNode extends LLVMToI32Node {

        @Specialization
        public int executeI32(float from) {
            return Float.floatToIntBits(from);
        }

        @Specialization
        public int executeI32(int from) {
            return from;
        }

        @Specialization
        public int executeI1Vector(LLVMI1Vector from) {
            return (int) LLVMToI64BitNode.castI1Vector(from, Integer.SIZE);
        }

        @Specialization
        public int executeI8Vector(LLVMI8Vector from) {
            return (int) LLVMToI64BitNode.castI8Vector(from, Integer.SIZE / Byte.SIZE);
        }

        @Specialization
        public int executeI16Vector(LLVMI16Vector from) {
            return (int) LLVMToI64BitNode.castI16Vector(from, Integer.SIZE / Short.SIZE);
        }

        @Specialization
        public int executeI32Vector(LLVMI32Vector from) {
            if (from.getLength() != 1) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            return from.getValue(0);
        }

        @Specialization
        public int executeFloatVector(LLVMFloatVector from) {
            if (from.getLength() != 1) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            return Float.floatToIntBits(from.getValue(0));
        }
    }

    public abstract static class LLVMToUnsignedI32Node extends LLVMToI32Node {

        @Specialization
        public int executeI32(double from) {
            if (from > Integer.MAX_VALUE) {
                return (int) (from + Integer.MIN_VALUE) - Integer.MIN_VALUE;
            }
            return (int) from;
        }

        @Specialization
        public int executeI32(int from) {
            return from;
        }
    }
}
