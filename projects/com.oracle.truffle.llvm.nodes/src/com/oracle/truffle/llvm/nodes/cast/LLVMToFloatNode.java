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
import com.oracle.truffle.llvm.nodes.cast.LLVMToI64Node.LLVMToI64BitNode;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToFloatNode extends LLVMExpressionNode {

    @Child private ForeignToLLVM toFloat = ForeignToLLVM.create(ForeignToLLVMType.FLOAT);

    @Specialization
    public float executeLLVMBoxedPrimitive(LLVMBoxedPrimitive from) {
        return (float) toFloat.executeWithTarget(from.getValue());
    }

    @Child private Node isNull = Message.IS_NULL.createNode();
    @Child private Node isBoxed = Message.IS_BOXED.createNode();
    @Child private Node unbox = Message.UNBOX.createNode();

    @Specialization
    public float executeTruffleObject(LLVMTruffleObject from) {
        TruffleObject base = from.getObject();
        if (ForeignAccess.sendIsNull(isNull, base)) {
            return from.getOffset();
        } else if (ForeignAccess.sendIsBoxed(isBoxed, base)) {
            try {
                float ptr = (float) toFloat.executeWithTarget(ForeignAccess.sendUnbox(unbox, base));
                return ptr + from.getOffset();
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Not convertable");
    }

    public abstract static class LLVMToFloatNoZeroExtNode extends LLVMToFloatNode {

        @Specialization
        public float executeDouble(byte from) {
            return from;
        }

        @Specialization
        public float executeFloat(short from) {
            return from;
        }

        @Specialization
        public float executeFloat(int from) {
            return from;
        }

        @Specialization
        public float executeFloat(long from) {
            return from;
        }

        @Specialization
        public float executeFloat(double from) {
            return (float) from;
        }

        @Specialization
        public float executeFloat(LLVM80BitFloat from) {
            return from.getFloatValue();
        }

        @Specialization
        public float executeDouble(float from) {
            return from;
        }
    }

    public abstract static class LLVMToFloatZeroExtNode extends LLVMToFloatNode {

        @Specialization
        public float executeDouble(byte from) {
            return from & LLVMExpressionNode.I8_MASK;
        }

        @Specialization
        public float executeDouble(short from) {
            return from & LLVMExpressionNode.I16_MASK;
        }

        @Specialization
        public float executeFloat(int from) {
            return from;
        }

        @Specialization
        public float executeDouble(float from) {
            return from;
        }

    }

    public abstract static class LLVMToFloatBitNode extends LLVMToFloatNode {

        @Specialization
        public float executeFloat(int from) {
            return Float.intBitsToFloat(from);
        }

        @Specialization
        public float executeDouble(float from) {
            return from;
        }

        @Specialization
        public float executeI1Vector(LLVMI1Vector from) {
            int res = (int) LLVMToI64BitNode.castI1Vector(from, Integer.SIZE);
            return Float.intBitsToFloat(res);
        }

        @Specialization
        public float executeI8Vector(LLVMI8Vector from) {
            int res = (int) LLVMToI64BitNode.castI8Vector(from, Integer.SIZE / Byte.SIZE);
            return Float.intBitsToFloat(res);
        }

        @Specialization
        public float executeI16Vector(LLVMI16Vector from) {
            int res = (int) LLVMToI64BitNode.castI16Vector(from, Integer.SIZE / Short.SIZE);
            return Float.intBitsToFloat(res);
        }

        @Specialization
        public float executeI32Vector(LLVMI32Vector from) {
            if (from.getLength() != 1) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            return Float.intBitsToFloat(from.getValue(0));
        }

        @Specialization
        public float executeFloatVector(LLVMFloatVector from) {
            if (from.getLength() != 1) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            return from.getValue(0);
        }
    }

    public abstract static class LLVMToFloatUnsignedNode extends LLVMToFloatNode {

        private static final float LEADING_BIT = 0x1.0p63f;

        @Specialization
        public float executeFloat(int from) {
            return from & LLVMExpressionNode.I32_MASK;
        }

        @Specialization
        public float executeFloat(long from) {
            float val = from & Long.MAX_VALUE;
            if (from < 0) {
                val += LEADING_BIT;
            }
            return val;
        }

        @Specialization
        public float executeDouble(float from) {
            return from;
        }
    }

}
