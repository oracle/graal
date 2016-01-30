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
package com.oracle.truffle.llvm.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMIVarBitNode;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMIVarBit;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;

public class LLVMSimpleLiteralNode {

    public static class LLVMIVarBitLiteralNode extends LLVMIVarBitNode {

        private final LLVMIVarBit literal;

        public LLVMIVarBitLiteralNode(LLVMIVarBit literal) {
            this.literal = literal;
        }

        @Override
        public LLVMIVarBit executeVarI(VirtualFrame frame) {
            return literal;
        }

    }

    public static class LLVMI1LiteralNode extends LLVMI1Node {

        private final boolean literal;

        public LLVMI1LiteralNode(boolean literal) {
            this.literal = literal;
        }

        @Override
        public boolean executeI1(VirtualFrame frame) {
            return literal;
        }

    }

    public static class LLVMI8LiteralNode extends LLVMI8Node {

        private final byte literal;

        public LLVMI8LiteralNode(byte literal) {
            this.literal = literal;
        }

        @Override
        public byte executeI8(VirtualFrame frame) {
            return literal;
        }

    }

    public static class LLVMI16LiteralNode extends LLVMI16Node {

        private final short literal;

        public LLVMI16LiteralNode(short literal) {
            this.literal = literal;
        }

        @Override
        public short executeI16(VirtualFrame frame) {
            return literal;
        }

    }

    public static class LLVMI32LiteralNode extends LLVMI32Node {

        private final int literal;

        public LLVMI32LiteralNode(int literal) {
            this.literal = literal;
        }

        @Override
        public int executeI32(VirtualFrame frame) {
            return literal;
        }

    }

    public static class LLVMI64LiteralNode extends LLVMI64Node {

        private final long literal;

        public LLVMI64LiteralNode(long literal) {
            this.literal = literal;
        }

        @Override
        public long executeI64(VirtualFrame frame) {
            return literal;
        }

    }

    public static class LLVMFloatLiteralNode extends LLVMFloatNode {

        private final float literal;

        public LLVMFloatLiteralNode(float literal) {
            this.literal = literal;
        }

        @Override
        public float executeFloat(VirtualFrame frame) {
            return literal;
        }

    }

    public static class LLVMDoubleLiteralNode extends LLVMDoubleNode {

        private final double literal;

        public LLVMDoubleLiteralNode(double literal) {
            this.literal = literal;
        }

        @Override
        public double executeDouble(VirtualFrame frame) {
            return literal;
        }

    }

    public static class LLVM80BitFloatLiteralNode extends LLVM80BitFloatNode {

        private final LLVM80BitFloat literal;

        public LLVM80BitFloatLiteralNode(LLVM80BitFloat literal) {
            this.literal = literal;
        }

        @Override
        public LLVM80BitFloat execute80BitFloat(VirtualFrame frame) {
            return literal;
        }

    }

    public static class LLVMAddressLiteralNode extends LLVMAddressNode {

        private final LLVMAddress address;

        public LLVMAddressLiteralNode(LLVMAddress address) {
            this.address = address;
        }

        @Override
        public LLVMAddress executePointee(VirtualFrame frame) {
            return address;
        }

    }

}
