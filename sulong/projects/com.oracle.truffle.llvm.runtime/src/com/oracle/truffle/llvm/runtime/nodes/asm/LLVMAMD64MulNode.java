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
package com.oracle.truffle.llvm.runtime.nodes.asm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64UpdateFlagsNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteBooleanNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteTupelNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteValueNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LongMultiplication;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;

public abstract class LLVMAMD64MulNode extends LLVMStatementNode {
    @Child protected LLVMAMD64WriteBooleanNode writeCFNode;
    @Child protected LLVMAMD64WriteBooleanNode writePFNode;
    @Child protected LLVMAMD64WriteBooleanNode writeAFNode;
    @Child protected LLVMAMD64WriteBooleanNode writeZFNode;
    @Child protected LLVMAMD64WriteBooleanNode writeSFNode;
    @Child protected LLVMAMD64WriteBooleanNode writeOFNode;

    protected void setFlags(VirtualFrame frame, byte value, boolean overflow, boolean sign) {
        writeCFNode.execute(frame, overflow);
        writePFNode.execute(frame, LLVMAMD64UpdateFlagsNode.getParity(value));
        writeAFNode.execute(frame, false);
        writeZFNode.execute(frame, false);
        writeSFNode.execute(frame, sign);
        writeOFNode.execute(frame, overflow);
    }

    public LLVMAMD64MulNode(LLVMAMD64WriteBooleanNode writeCFNode, LLVMAMD64WriteBooleanNode writePFNode, LLVMAMD64WriteBooleanNode writeAFNode, LLVMAMD64WriteBooleanNode writeZFNode,
                    LLVMAMD64WriteBooleanNode writeSFNode, LLVMAMD64WriteBooleanNode writeOFNode) {
        this.writeCFNode = writeCFNode;
        this.writePFNode = writePFNode;
        this.writeAFNode = writeAFNode;
        this.writeZFNode = writeZFNode;
        this.writeSFNode = writeSFNode;
        this.writeOFNode = writeOFNode;
    }

    @NodeChild(value = "left", type = LLVMExpressionNode.class)
    @NodeChild(value = "right", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64MulbNode extends LLVMAMD64MulNode {
        @Child private LLVMAMD64WriteValueNode out;

        public LLVMAMD64MulbNode(LLVMAMD64WriteBooleanNode writeCFNode, LLVMAMD64WriteBooleanNode writePFNode, LLVMAMD64WriteBooleanNode writeAFNode, LLVMAMD64WriteBooleanNode writeZFNode,
                        LLVMAMD64WriteBooleanNode writeSFNode, LLVMAMD64WriteBooleanNode writeOFNode, LLVMAMD64WriteValueNode out) {
            super(writeCFNode, writePFNode, writeAFNode, writeZFNode, writeSFNode, writeOFNode);
            this.out = out;
        }

        @Specialization
        protected void doOp(VirtualFrame frame, byte left, byte right) {
            short value = (short) (Byte.toUnsignedInt(left) * Byte.toUnsignedInt(right));
            byte valueb = (byte) value;
            boolean overflow = ((value >> LLVMExpressionNode.I8_SIZE_IN_BITS) & LLVMExpressionNode.I8_MASK) != 0;
            boolean sign = valueb < 0;
            setFlags(frame, valueb, overflow, sign);
            out.execute(frame, value);
        }
    }

    @NodeChild(value = "left", type = LLVMExpressionNode.class)
    @NodeChild(value = "right", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64MulwNode extends LLVMAMD64MulNode {
        @Child private LLVMAMD64WriteTupelNode out;

        public LLVMAMD64MulwNode(LLVMAMD64WriteBooleanNode writeCFNode, LLVMAMD64WriteBooleanNode writePFNode, LLVMAMD64WriteBooleanNode writeAFNode, LLVMAMD64WriteBooleanNode writeZFNode,
                        LLVMAMD64WriteBooleanNode writeSFNode, LLVMAMD64WriteBooleanNode writeOFNode, LLVMAMD64WriteTupelNode out) {
            super(writeCFNode, writePFNode, writeAFNode, writeZFNode, writeSFNode, writeOFNode);
            this.out = out;
        }

        @Specialization
        protected void doOp(VirtualFrame frame, short left, short right) {
            int value = Short.toUnsignedInt(left) * Short.toUnsignedInt(right);
            short hi = (short) (value >> LLVMExpressionNode.I16_SIZE_IN_BITS);
            setFlags(frame, (byte) value, hi != 0, (short) value < 0);
            out.execute(frame, (short) value, hi);
        }
    }

    @NodeChild(value = "left", type = LLVMExpressionNode.class)
    @NodeChild(value = "right", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64MullNode extends LLVMAMD64MulNode {
        @Child private LLVMAMD64WriteTupelNode out;

        public LLVMAMD64MullNode(LLVMAMD64WriteBooleanNode writeCFNode, LLVMAMD64WriteBooleanNode writePFNode, LLVMAMD64WriteBooleanNode writeAFNode, LLVMAMD64WriteBooleanNode writeZFNode,
                        LLVMAMD64WriteBooleanNode writeSFNode, LLVMAMD64WriteBooleanNode writeOFNode, LLVMAMD64WriteTupelNode out) {
            super(writeCFNode, writePFNode, writeAFNode, writeZFNode, writeSFNode, writeOFNode);
            this.out = out;
        }

        @Specialization
        protected void doOp(VirtualFrame frame, int left, int right) {
            long value = Integer.toUnsignedLong(left) * Integer.toUnsignedLong(right);
            int hi = (int) (value >> LLVMExpressionNode.I32_SIZE_IN_BITS);
            setFlags(frame, (byte) value, hi != 0, (int) value < 0);
            out.execute(frame, (int) value, hi);
        }
    }

    @NodeChild(value = "left", type = LLVMExpressionNode.class)
    @NodeChild(value = "right", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64MulqNode extends LLVMAMD64MulNode {
        @Child private LLVMAMD64WriteTupelNode out;

        public LLVMAMD64MulqNode(LLVMAMD64WriteBooleanNode writeCFNode, LLVMAMD64WriteBooleanNode writePFNode, LLVMAMD64WriteBooleanNode writeAFNode, LLVMAMD64WriteBooleanNode writeZFNode,
                        LLVMAMD64WriteBooleanNode writeSFNode, LLVMAMD64WriteBooleanNode writeOFNode, LLVMAMD64WriteTupelNode out) {
            super(writeCFNode, writePFNode, writeAFNode, writeZFNode, writeSFNode, writeOFNode);
            this.out = out;
        }

        @Specialization
        protected void doOp(VirtualFrame frame, long left, long right) {
            long value = left * right;
            long hi = LongMultiplication.multiplyHighUnsigned(left, right);
            setFlags(frame, (byte) value, hi != 0, value < 0);
            out.execute(frame, value, hi);
        }
    }
}
