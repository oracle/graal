/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.literals;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVMSimpleLiteralNode extends LLVMExpressionNode {

    private LLVMSimpleLiteralNode() {
        // restrict access to constructor
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        // requires the executeGeneric function to be simple:
        return getShortString() + " value=" + executeGeneric(null);
    }

    public abstract static class LLVMIVarBitLiteralNode extends LLVMSimpleLiteralNode {

        private final LLVMIVarBit literal;

        public LLVMIVarBitLiteralNode(LLVMIVarBit literal) {
            this.literal = literal;
        }

        @Specialization
        public LLVMIVarBit doIVarBit() {
            return literal.copy();
        }
    }

    public abstract static class LLVMI1LiteralNode extends LLVMSimpleLiteralNode {

        private final boolean literal;

        public LLVMI1LiteralNode(boolean literal) {
            this.literal = literal;
        }

        @Specialization
        public boolean doI1() {
            return literal;
        }
    }

    public abstract static class LLVMI8LiteralNode extends LLVMSimpleLiteralNode {

        private final byte literal;

        public LLVMI8LiteralNode(byte literal) {
            this.literal = literal;
        }

        @Specialization
        public byte doI8() {
            return literal;
        }
    }

    public abstract static class LLVMI16LiteralNode extends LLVMSimpleLiteralNode {

        private final short literal;

        public LLVMI16LiteralNode(short literal) {
            this.literal = literal;
        }

        @Specialization
        public short doI16() {
            return literal;
        }
    }

    public abstract static class LLVMI32LiteralNode extends LLVMSimpleLiteralNode {

        private final int literal;

        public LLVMI32LiteralNode(int literal) {
            this.literal = literal;
        }

        @Specialization
        public int doI32() {
            return literal;
        }
    }

    public abstract static class LLVMI64LiteralNode extends LLVMSimpleLiteralNode {

        private final long literal;

        public LLVMI64LiteralNode(long literal) {
            this.literal = literal;
        }

        @Specialization
        public long doI64() {
            return literal;
        }
    }

    public abstract static class LLVMFloatLiteralNode extends LLVMSimpleLiteralNode {

        private final float literal;

        public LLVMFloatLiteralNode(float literal) {
            this.literal = literal;
        }

        @Specialization
        public float doFloat() {
            return literal;
        }
    }

    public abstract static class LLVMDoubleLiteralNode extends LLVMSimpleLiteralNode {

        private final double literal;

        public LLVMDoubleLiteralNode(double literal) {
            this.literal = literal;
        }

        @Specialization
        public double doDouble() {
            return literal;
        }
    }

    public abstract static class LLVM80BitFloatLiteralNode extends LLVMSimpleLiteralNode {

        private final boolean sign;
        private final int exponent;
        private final long fraction;

        public LLVM80BitFloatLiteralNode(LLVM80BitFloat literal) {
            this.sign = literal.getSign();
            this.exponent = literal.getExponent();
            this.fraction = literal.getFraction();
        }

        @Specialization
        public LLVM80BitFloat do80BitFloat() {
            return new LLVM80BitFloat(sign, exponent, fraction);
        }
    }

    public abstract static class LLVMManagedPointerLiteralNode extends LLVMSimpleLiteralNode {

        private final LLVMManagedPointer address;

        public LLVMManagedPointerLiteralNode(LLVMManagedPointer address) {
            this.address = address;
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            if (address.getObject() instanceof LLVMFunctionDescriptor) {
                LLVMFunctionDescriptor function = (LLVMFunctionDescriptor) address.getObject();
                return getShortString() + " value=" + address + " function=\"" + function.getLLVMFunction().getName() + "\"";
            }

            return getShortString() + " value=" + address;
        }

        @Specialization
        public LLVMManagedPointer doManagedPointer() {
            return address.copy();
        }
    }

    public abstract static class LLVMNativePointerLiteralNode extends LLVMSimpleLiteralNode {

        private final long address;

        public LLVMNativePointerLiteralNode(LLVMNativePointer address) {
            this.address = address.asNative();
        }

        @Specialization
        public LLVMNativePointer doNativePointer() {
            return LLVMNativePointer.create(address);
        }
    }
}
