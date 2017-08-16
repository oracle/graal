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

public abstract class LLVMAMD64ImulNode extends LLVMExpressionNode {
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

    public LLVMAMD64ImulNode(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf,
                    LLVMAMD64WriteBooleanNode of) {
        this.cf = cf;
        this.pf = pf;
        this.af = af;
        this.zf = zf;
        this.sf = sf;
        this.of = of;
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAMD64ImulbNode extends LLVMAMD64ImulNode {
        public LLVMAMD64ImulbNode(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf,
                        LLVMAMD64WriteBooleanNode of) {
            super(cf, pf, af, zf, sf, of);
        }

        @Specialization
        protected short executeI8(VirtualFrame frame, byte left, byte right) {
            short value = (short) (left * right);
            byte valueb = (byte) value;
            boolean overflow = valueb != value;
            boolean sign = valueb < 0;
            setFlags(frame, valueb, overflow, sign);
            return value;
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAMD64ImulwNode extends LLVMAMD64ImulNode {
        @Child private LLVMAMD64WriteI16RegisterNode high;

        public LLVMAMD64ImulwNode(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf,
                        LLVMAMD64WriteBooleanNode of, LLVMAMD64WriteI16RegisterNode high) {
            super(cf, pf, af, zf, sf, of);
            this.high = high;
        }

        @Specialization
        protected short executeI16(VirtualFrame frame, short left, short right) {
            int value = left * right;
            short hi = (short) (value >> LLVMExpressionNode.I16_SIZE_IN_BITS);
            short valuew = (short) value;
            boolean overflow = valuew != value;
            boolean sign = valuew < 0;
            setFlags(frame, (byte) value, overflow, sign);
            high.execute(frame, hi);
            return valuew;
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAMD64Imulw3Node extends LLVMAMD64ImulNode {

        public LLVMAMD64Imulw3Node(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf,
                        LLVMAMD64WriteBooleanNode of) {
            super(cf, pf, af, zf, sf, of);
        }

        @Specialization
        protected short executeI16(VirtualFrame frame, short left, short right) {
            int value = left * right;
            short valuew = (short) value;
            boolean overflow = valuew != value;
            boolean sign = valuew < 0;
            setFlags(frame, (byte) value, overflow, sign);
            return valuew;
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAMD64ImullNode extends LLVMAMD64ImulNode {
        @Child private LLVMAMD64WriteI32RegisterNode high;

        public LLVMAMD64ImullNode(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf,
                        LLVMAMD64WriteBooleanNode of, LLVMAMD64WriteI32RegisterNode high) {
            super(cf, pf, af, zf, sf, of);
            this.high = high;
        }

        @Specialization
        protected int executeI32(VirtualFrame frame, int left, int right) {
            long value = (long) left * (long) right;
            int hi = (int) (value >> LLVMExpressionNode.I32_SIZE_IN_BITS);
            int valuel = (int) value;
            boolean overflow = valuel != value;
            boolean sign = valuel < 0;
            setFlags(frame, (byte) value, overflow, sign);
            high.execute(frame, hi);
            return valuel;
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAMD64Imull3Node extends LLVMAMD64ImulNode {

        public LLVMAMD64Imull3Node(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf,
                        LLVMAMD64WriteBooleanNode of) {
            super(cf, pf, af, zf, sf, of);
        }

        @Specialization
        protected int executeI32(VirtualFrame frame, int left, int right) {
            long value = (long) left * (long) right;
            int valuel = (int) value;
            boolean overflow = valuel != value;
            boolean sign = valuel < 0;
            setFlags(frame, (byte) value, overflow, sign);
            return valuel;
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAMD64ImulqNode extends LLVMAMD64ImulNode {
        @Child private LLVMAMD64WriteI64RegisterNode high;

        public LLVMAMD64ImulqNode(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf,
                        LLVMAMD64WriteBooleanNode of, LLVMAMD64WriteI64RegisterNode high) {
            super(cf, pf, af, zf, sf, of);
            this.high = high;
        }

        @Specialization
        protected long executeI64(VirtualFrame frame, long left, long right) {
            long value = left * right;
            long hi = LongMultiplication.multiplyHigh(left, right);
            boolean overflow = !(value < 0 && hi == -1) && !(value > 0 && hi == 0);
            boolean sign = value < 0;
            setFlags(frame, (byte) value, overflow, sign);
            high.execute(frame, hi);
            return value;
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class)})
    public abstract static class LLVMAMD64Imulq3Node extends LLVMAMD64ImulNode {

        public LLVMAMD64Imulq3Node(LLVMAMD64WriteBooleanNode cf, LLVMAMD64WriteBooleanNode pf, LLVMAMD64WriteBooleanNode af, LLVMAMD64WriteBooleanNode zf, LLVMAMD64WriteBooleanNode sf,
                        LLVMAMD64WriteBooleanNode of) {
            super(cf, pf, af, zf, sf, of);
        }

        @Specialization
        protected long executeI64(VirtualFrame frame, long left, long right) {
            long value = left * right;
            long hi = LongMultiplication.multiplyHigh(left, right);
            boolean overflow = !(value < 0 && hi == -1) && !(value > 0 && hi == 0);
            boolean sign = value < 0;
            setFlags(frame, (byte) value, overflow, sign);
            return value;
        }
    }
}
