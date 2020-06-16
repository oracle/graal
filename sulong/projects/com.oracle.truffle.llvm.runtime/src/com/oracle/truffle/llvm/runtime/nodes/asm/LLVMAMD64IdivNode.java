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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteTupelNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LongDivision;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;

public abstract class LLVMAMD64IdivNode extends LLVMExpressionNode {
    public static final String DIV_BY_ZERO = "division by zero";
    public static final String QUOTIENT_TOO_LARGE = "quotient too large";

    @NodeChild(value = "left", type = LLVMExpressionNode.class)
    @NodeChild(value = "right", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64IdivbNode extends LLVMExpressionNode {
        @Specialization
        protected short doOp(short left, byte right) {
            if (right == 0) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException(DIV_BY_ZERO);
            }
            byte quotient = (byte) (left / right);
            byte remainder = (byte) (left % right);
            // TODO: error on quotient too large
            return (short) ((quotient & LLVMExpressionNode.I8_MASK) | ((remainder & LLVMExpressionNode.I8_MASK) << LLVMExpressionNode.I8_SIZE_IN_BITS));
        }
    }

    @NodeChild(value = "high", type = LLVMExpressionNode.class)
    @NodeChild(value = "left", type = LLVMExpressionNode.class)
    @NodeChild(value = "right", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64IdivwNode extends LLVMStatementNode {
        @Child private LLVMAMD64WriteTupelNode out;

        public LLVMAMD64IdivwNode(LLVMAMD64WriteTupelNode out) {
            this.out = out;
        }

        @Specialization
        protected void doOp(VirtualFrame frame, short high, short left, short right) {
            if (right == 0) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException(DIV_BY_ZERO);
            }
            int value = Short.toUnsignedInt(high) << LLVMExpressionNode.I16_SIZE_IN_BITS | Short.toUnsignedInt(left);
            short quotient = (short) (value / right);
            short remainder = (short) (value % right);
            // TODO: error on quotient too large
            out.execute(frame, quotient, remainder);
        }
    }

    @NodeChild(value = "high", type = LLVMExpressionNode.class)
    @NodeChild(value = "left", type = LLVMExpressionNode.class)
    @NodeChild(value = "right", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64IdivlNode extends LLVMStatementNode {
        @Child private LLVMAMD64WriteTupelNode out;

        public LLVMAMD64IdivlNode(LLVMAMD64WriteTupelNode out) {
            this.out = out;
        }

        @Specialization
        protected void doOp(VirtualFrame frame, int high, int left, int right) {
            if (right == 0) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException(DIV_BY_ZERO);
            }
            long value = Integer.toUnsignedLong(high) << LLVMExpressionNode.I32_SIZE_IN_BITS | Integer.toUnsignedLong(left);
            int quotient = (int) (value / right);
            int remainder = (int) (value % right);
            // TODO: error on quotient too large
            out.execute(frame, quotient, remainder);
        }
    }

    @NodeChild(value = "high", type = LLVMExpressionNode.class)
    @NodeChild(value = "left", type = LLVMExpressionNode.class)
    @NodeChild(value = "right", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64IdivqNode extends LLVMStatementNode {
        @Child private LLVMAMD64WriteTupelNode out;

        public LLVMAMD64IdivqNode(LLVMAMD64WriteTupelNode out) {
            this.out = out;
        }

        @Specialization
        protected void doOp(VirtualFrame frame, long high, long left, long right) {
            if (right == 0) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException(DIV_BY_ZERO);
            }
            LongDivision.Result result = LongDivision.divs128by64(high, left, right);
            if (result.isInvalid()) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException(QUOTIENT_TOO_LARGE);
            }
            long quotient = result.quotient;
            long remainder = result.remainder;
            out.execute(frame, quotient, remainder);
        }
    }
}
