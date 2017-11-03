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
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMTo80BitFloatingNode extends LLVMExpressionNode {

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMSignedToLLVM80BitFloatNode extends LLVMTo80BitFloatingNode {

        @Specialization
        public LLVM80BitFloat execute80BitFloat(byte from) {
            return LLVM80BitFloat.fromByte(from);
        }

        @Specialization
        public LLVM80BitFloat executeLLVM80BitFloatNode(short from) {
            return LLVM80BitFloat.fromShort(from);
        }

        @Specialization
        public LLVM80BitFloat executeLLVM80BitFloatNode(int from) {
            return LLVM80BitFloat.fromInt(from);
        }

        @Specialization
        public LLVM80BitFloat executeLLVM80BitFloatNode(long from) {
            return LLVM80BitFloat.fromLong(from);
        }

        @Specialization
        public LLVM80BitFloat executeLLVM80BitFloatNode(float from) {
            return LLVM80BitFloat.fromFloat(from);
        }

        @Specialization
        public LLVM80BitFloat executeLLVM80BitFloatNode(double from) {
            return LLVM80BitFloat.fromDouble(from);
        }

        @Specialization
        public LLVM80BitFloat executeLLVM80BitFloatNode(LLVM80BitFloat from) {
            return from;
        }

        @Specialization
        public LLVM80BitFloat executeLLVM80BitFloatNode(LLVMIVarBit from) {
            return LLVM80BitFloat.fromBytesBigEndian(from.getBytes());
        }
    }

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMUnsignedToLLVM80BitFloatNode extends LLVMTo80BitFloatingNode {

        @Specialization
        public LLVM80BitFloat execute80BitFloat(byte from) {
            return LLVM80BitFloat.fromUnsignedByte(from);
        }

        @Specialization
        public LLVM80BitFloat executeLLVM80BitFloatNode(int from) {
            return LLVM80BitFloat.fromUnsignedInt(from);
        }

        @Specialization
        public LLVM80BitFloat executeLLVM80BitFloatNode(long from) {
            return LLVM80BitFloat.fromUnsignedLong(from);
        }

        @Specialization
        public LLVM80BitFloat executeLLVM80BitFloatNode(float from) {
            return LLVM80BitFloat.fromFloat(from);
        }

        @Specialization
        public LLVM80BitFloat executeLLVM80BitFloatNode(double from) {
            return LLVM80BitFloat.fromDouble(from);
        }

        @Specialization
        public LLVM80BitFloat executeLLVM80BitFloatNode(LLVM80BitFloat from) {
            return from;
        }

        @Specialization
        public LLVM80BitFloat executeLLVM80BitFloatNode(LLVMIVarBit from) {
            return LLVM80BitFloat.fromBytesBigEndian(from.getBytes());
        }
    }

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMToLLVM80BitFloatBitNode extends LLVMTo80BitFloatingNode {

        @Specialization
        public LLVM80BitFloat executeI1Vector(LLVMI1Vector from) {
            if (from.getLength() != LLVM80BitFloat.BIT_WIDTH) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            byte[] values = new byte[LLVM80BitFloat.BYTE_WIDTH];
            for (int i = 0; i < LLVM80BitFloat.BYTE_WIDTH; i++) {
                values[i] = 0;
                for (int j = 0; j < Byte.SIZE; j++) {
                    values[i] |= (from.getValue(i * Byte.SIZE + j) ? 1L : 0L) << j;
                }
            }
            return LLVM80BitFloat.fromBytes(values);
        }

        @Specialization
        public LLVM80BitFloat executeI8Vector(LLVMI8Vector from) {
            if (from.getLength() != LLVM80BitFloat.BIT_WIDTH / Byte.SIZE) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            byte[] values = new byte[LLVM80BitFloat.BYTE_WIDTH];
            for (int i = 0; i < LLVM80BitFloat.BYTE_WIDTH; i++) {
                values[i] = from.getValue(i);
            }
            return LLVM80BitFloat.fromBytes(values);
        }

        @Specialization
        public LLVM80BitFloat executeI16Vector(LLVMI16Vector from) {
            if (from.getLength() != LLVM80BitFloat.BIT_WIDTH / Short.SIZE) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            byte[] values = new byte[LLVM80BitFloat.BYTE_WIDTH];
            for (int i = 0; i < LLVM80BitFloat.BIT_WIDTH / Short.SIZE; i++) {
                values[i * 2] = (byte) (from.getValue(i) & 0xFF);
                values[i * 2 + 1] = (byte) ((from.getValue(i) >>> 8) & 0xFF);
            }
            return LLVM80BitFloat.fromBytes(values);
        }
    }

}
