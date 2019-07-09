/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public class LLVMSimpleLiteralNode {

    public static final class LLVMIVarBitLiteralNode extends LLVMExpressionNode {

        private final LLVMIVarBit literal;

        public LLVMIVarBitLiteralNode(LLVMIVarBit literal) {
            this.literal = literal;
        }

        @Override
        public LLVMIVarBit executeLLVMIVarBit(VirtualFrame frame) {
            return literal.copy();
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeLLVMIVarBit(frame);
        }
    }

    public static final class LLVMI1LiteralNode extends LLVMExpressionNode {

        private final boolean literal;

        public LLVMI1LiteralNode(boolean literal) {
            this.literal = literal;
        }

        @Override
        public boolean executeI1(VirtualFrame frame) {
            return literal;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeI1(frame);
        }
    }

    public static final class LLVMI8LiteralNode extends LLVMExpressionNode {

        private final byte literal;

        public LLVMI8LiteralNode(byte literal) {
            this.literal = literal;
        }

        @Override
        public byte executeI8(VirtualFrame frame) {
            return literal;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeI8(frame);
        }
    }

    public static final class LLVMI16LiteralNode extends LLVMExpressionNode {

        private final short literal;

        public LLVMI16LiteralNode(short literal) {
            this.literal = literal;
        }

        @Override
        public short executeI16(VirtualFrame frame) {
            return literal;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeI16(frame);
        }
    }

    public static final class LLVMI32LiteralNode extends LLVMExpressionNode {

        private final int literal;

        public LLVMI32LiteralNode(int literal) {
            this.literal = literal;
        }

        @Override
        public int executeI32(VirtualFrame frame) {
            return literal;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeI32(frame);
        }
    }

    public static final class LLVMI64LiteralNode extends LLVMExpressionNode {

        private final long literal;

        public LLVMI64LiteralNode(long literal) {
            this.literal = literal;
        }

        @Override
        public long executeI64(VirtualFrame frame) {
            return literal;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeI64(frame);
        }
    }

    public static final class LLVMFloatLiteralNode extends LLVMExpressionNode {

        private final float literal;

        public LLVMFloatLiteralNode(float literal) {
            this.literal = literal;
        }

        @Override
        public float executeFloat(VirtualFrame frame) {
            return literal;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeFloat(frame);
        }
    }

    public static final class LLVMDoubleLiteralNode extends LLVMExpressionNode {

        private final double literal;

        public LLVMDoubleLiteralNode(double literal) {
            this.literal = literal;
        }

        @Override
        public double executeDouble(VirtualFrame frame) {
            return literal;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeDouble(frame);
        }
    }

    public static final class LLVM80BitFloatLiteralNode extends LLVMExpressionNode {

        private final boolean sign;
        private final int exponent;
        private final long fraction;

        public LLVM80BitFloatLiteralNode(LLVM80BitFloat literal) {
            this.sign = literal.getSign();
            this.exponent = literal.getExponent();
            this.fraction = literal.getFraction();
        }

        @Override
        public LLVM80BitFloat executeLLVM80BitFloat(VirtualFrame frame) {
            return new LLVM80BitFloat(sign, exponent, fraction);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeLLVM80BitFloat(frame);
        }
    }

    public static final class LLVMManagedPointerLiteralNode extends LLVMExpressionNode {

        private final LLVMManagedPointer address;

        public LLVMManagedPointerLiteralNode(LLVMManagedPointer address) {
            this.address = address;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return address.copy();
        }
    }

    public static final class LLVMNativePointerLiteralNode extends LLVMExpressionNode {

        private final long address;

        public LLVMNativePointerLiteralNode(LLVMNativePointer address) {
            this.address = address.asNative();
        }

        @Override
        public LLVMNativePointer executeLLVMNativePointer(VirtualFrame frame) {
            return LLVMNativePointer.create(address);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeLLVMNativePointer(frame);
        }
    }
}
