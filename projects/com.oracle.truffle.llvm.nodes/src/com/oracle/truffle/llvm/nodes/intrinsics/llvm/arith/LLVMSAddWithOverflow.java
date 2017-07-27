/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class LLVMSAddWithOverflow {

    private static boolean signedAddWithOverflowI8(byte left, byte right, LLVMAddress addr) {
        final int res = left + right;
        final boolean overflow = (((res ^ left) & (res ^ right)) & (1 << (Byte.SIZE - 1))) != 0;

        LLVMMemory.putI8(addr, (byte) (res));
        return overflow;
    }

    private static boolean signedAddWithOverflowI16(short left, short right, LLVMAddress addr) {
        final int res = left + right;
        final boolean overflow = (((res ^ left) & (res ^ right)) & (1 << (Short.SIZE - 1))) != 0;

        LLVMMemory.putI16(addr, (short) (res));
        return overflow;
    }

    private static boolean signedAddWithOverflowI32(int left, int right, LLVMAddress addr) {
        int res;
        boolean overflow = false;
        try {
            res = Math.addExact(left, right);
        } catch (ArithmeticException e) {
            // no transferToInterpreter - we want the compiler to remove that exception
            res = left + right;
            overflow = true;
        }
        LLVMMemory.putI32(addr, res);
        return overflow;
    }

    private static boolean signedAddWithOverflowI64(long left, long right, LLVMAddress addr) {
        long res;
        boolean overflow = false;
        try {
            res = Math.addExact(left, right);
        } catch (ArithmeticException e) {
            // no transferToInterpreter - we want the compiler to remove that exception
            res = left + right;
            overflow = true;
        }
        LLVMMemory.putI64(addr, res);
        return overflow;
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class),
                    @NodeChild(value = "target", type = LLVMExpressionNode.class)})
    public abstract static class GCCAddWithOverflow extends LLVMBuiltin {

        @Specialization
        public byte executeIntrinsic(byte left, byte right, LLVMAddress addr) {
            return (byte) (signedAddWithOverflowI8(left, right, addr) ? 1 : 0);
        }

        @Specialization
        public byte executeIntrinsic(short left, short right, LLVMAddress addr) {
            return (byte) (signedAddWithOverflowI16(left, right, addr) ? 1 : 0);
        }

        @Specialization
        public byte executeIntrinsic(int left, int right, LLVMAddress addr) {
            return (byte) (signedAddWithOverflowI32(left, right, addr) ? 1 : 0);
        }

        @Specialization
        public byte executeIntrinsic(long left, long right, LLVMAddress addr) {
            return (byte) (signedAddWithOverflowI64(left, right, addr) ? 1 : 0);
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class),
                    @NodeChild(value = "target", type = LLVMExpressionNode.class)})
    public abstract static class LLVMSAddWithOverflowI8 extends LLVMBuiltin {

        private final int secondValueOffset;

        public LLVMSAddWithOverflowI8(int secondValueOffset) {
            this.secondValueOffset = secondValueOffset;
        }

        @Specialization
        public LLVMAddress executeIntrinsic(byte left, byte right, LLVMAddress addr) {
            final boolean overflow = signedAddWithOverflowI8(left, right, addr);
            LLVMMemory.putI1(addr.getVal() + secondValueOffset, overflow);
            return addr;
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class),
                    @NodeChild(value = "target", type = LLVMExpressionNode.class)})
    public abstract static class LLVMSAddWithOverflowI16 extends LLVMBuiltin {

        private final int secondValueOffset;

        public LLVMSAddWithOverflowI16(int secondValueOffset) {
            this.secondValueOffset = secondValueOffset;
        }

        @Specialization
        public LLVMAddress executeIntrinsic(short left, short right, LLVMAddress addr) {
            final boolean overflow = signedAddWithOverflowI16(left, right, addr);
            LLVMMemory.putI1(addr.getVal() + secondValueOffset, overflow);
            return addr;
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class),
                    @NodeChild(value = "target", type = LLVMExpressionNode.class)})
    public abstract static class LLVMSAddWithOverflowI32 extends LLVMBuiltin {

        private final int secondValueOffset;

        public LLVMSAddWithOverflowI32(int secondValueOffset) {
            this.secondValueOffset = secondValueOffset;
        }

        @Specialization
        public LLVMAddress executeIntrinsic(int left, int right, LLVMAddress addr) {
            final boolean overflow = signedAddWithOverflowI32(left, right, addr);
            LLVMMemory.putI1(addr.getVal() + secondValueOffset, overflow);
            return addr;
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class),
                    @NodeChild(value = "target", type = LLVMExpressionNode.class)})
    public abstract static class LLVMSAddWithOverflowI64 extends LLVMBuiltin {

        private final int secondValueOffset;

        public LLVMSAddWithOverflowI64(int secondValueOffset) {
            this.secondValueOffset = secondValueOffset;
        }

        @Specialization
        public LLVMAddress executeIntrinsic(long left, long right, LLVMAddress addr) {
            final boolean overflow = signedAddWithOverflowI64(left, right, addr);
            LLVMMemory.putI1(addr.getVal() + secondValueOffset, overflow);
            return addr;
        }
    }

}
