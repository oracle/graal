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
package com.oracle.truffle.llvm.nodes.asm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64UpdateFlagsNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteBooleanNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI16RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI32RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI64RegisterNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.asm.support.LongMultiplication;

public abstract class LLVMAMD64MulNode extends LLVMExpressionNode {
    @Child protected LLVMAMD64WriteBooleanNode cf;
    @Child protected LLVMAMD64WriteBooleanNode pf;
    @Child protected LLVMAMD64WriteBooleanNode af;
    @Child protected LLVMAMD64WriteBooleanNode zf;
    @Child protected LLVMAMD64WriteBooleanNode sf;
    @Child protected LLVMAMD64WriteBooleanNode of;

    protected void setFlags(VirtualFrame frame, byte value, boolean overflow, boolean sign) {
        cf.execute(frame, overflow);
        pf.execute(frame, LLVMAMD64UpdateFlagsNode.getParity(value));
        af.execute(frame, false);
        zf.execute(frame, false);
        sf.execute(frame, sign);
        of.execute(frame, overflow);
    }

    public LLVMAMD64MulNode(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf,
                    LLVMAMD64WriteBooleanNode of) {
        this.cf = cf;
        this.pf = pf;
        this.af = af;
        this.zf = zf;
        this.sf = sf;
        this.of = of;
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAMD64MulbNode extends LLVMAMD64MulNode {
        public LLVMAMD64MulbNode(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf,
                        LLVMAMD64WriteBooleanNode of) {
            super(cf, pf, af, zf, sf, of);
        }

        @Specialization
        protected short executeI8(VirtualFrame frame, byte left, byte right) {
            short value = (short) (Byte.toUnsignedInt(left) * Byte.toUnsignedInt(right));
            byte valueb = (byte) value;
            boolean overflow = ((value >> LLVMExpressionNode.I8_SIZE_IN_BITS) & LLVMExpressionNode.I8_MASK) != 0;
            boolean sign = valueb < 0;
            setFlags(frame, valueb, overflow, sign);
            return value;
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAMD64MulwNode extends LLVMAMD64MulNode {
        private final LLVMAMD64WriteI16RegisterNode high;

        public LLVMAMD64MulwNode(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf,
                        LLVMAMD64WriteBooleanNode of, LLVMAMD64WriteI16RegisterNode high) {
            super(cf, pf, af, zf, sf, of);
            this.high = high;
        }

        @Specialization
        protected short executeI16(VirtualFrame frame, short left, short right) {
            int value = Short.toUnsignedInt(left) * Short.toUnsignedInt(right);
            short hi = (short) (value >> LLVMExpressionNode.I16_SIZE_IN_BITS);
            setFlags(frame, (byte) value, hi != 0, (short) value < 0);
            high.execute(frame, hi);
            return (short) value;
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAMD64MullNode extends LLVMAMD64MulNode {
        private final LLVMAMD64WriteI32RegisterNode high;

        public LLVMAMD64MullNode(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf,
                        LLVMAMD64WriteBooleanNode of, LLVMAMD64WriteI32RegisterNode high) {
            super(cf, pf, af, zf, sf, of);
            this.high = high;
        }

        @Specialization
        protected int executeI32(VirtualFrame frame, int left, int right) {
            long value = Integer.toUnsignedLong(left) * Integer.toUnsignedLong(right);
            int hi = (int) (value >> LLVMExpressionNode.I32_SIZE_IN_BITS);
            setFlags(frame, (byte) value, hi != 0, (int) value < 0);
            high.execute(frame, hi);
            return (int) value;
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAMD64MulqNode extends LLVMAMD64MulNode {
        private final LLVMAMD64WriteI64RegisterNode high;

        public LLVMAMD64MulqNode(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf,
                        LLVMAMD64WriteBooleanNode of, LLVMAMD64WriteI64RegisterNode high) {
            super(cf, pf, af, zf, sf, of);
            this.high = high;
        }

        @Specialization
        protected long executeI64(VirtualFrame frame, long left, long right) {
            long value = left * right;
            long hi = LongMultiplication.multiplyHighUnsigned(left, right);
            setFlags(frame, (byte) value, hi != 0, value < 0);
            high.execute(frame, hi);
            return value;
        }
    }
}
