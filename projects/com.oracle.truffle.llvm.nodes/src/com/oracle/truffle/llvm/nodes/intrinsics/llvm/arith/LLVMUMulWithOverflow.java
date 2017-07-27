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

public class LLVMUMulWithOverflow {

    private static boolean unsignedMulWithOverflowI8(byte left, byte right, LLVMAddress addr) {
        final int res = (left & LLVMExpressionNode.I8_MASK) * (right & LLVMExpressionNode.I8_MASK);
        final boolean overflow = (res & LLVMExpressionNode.I8_MASK) != res;

        LLVMMemory.putI8(addr, (byte) (res));
        return overflow;
    }

    private static boolean unsignedMulWithOverflowI16(short left, short right, LLVMAddress addr) {
        final int res = (left & LLVMExpressionNode.I16_MASK) * (right & LLVMExpressionNode.I16_MASK);
        final boolean overflow = (res & LLVMExpressionNode.I16_MASK) != res;

        LLVMMemory.putI16(addr, (short) (res));
        return overflow;
    }

    private static boolean unsignedMulWithOverflowI32(int left, int right, LLVMAddress addr) {
        final long res = (left & LLVMExpressionNode.I32_MASK) * (right & LLVMExpressionNode.I32_MASK);
        final boolean overflow = (res & LLVMExpressionNode.I32_MASK) != res;

        LLVMMemory.putI32(addr, (int) res);
        return overflow;
    }

    private static boolean unsignedMulWithOverflowI64(long left, long right, LLVMAddress addr) {
        final long res = left * right;
        boolean overflow = false;
        if ((left | right) >>> 31 != 0) {
            if (right != 0 && Long.divideUnsigned(res, right) != left) {
                overflow = true;
            }
        }

        LLVMMemory.putI64(addr, res);
        return overflow;
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class),
                    @NodeChild(value = "target", type = LLVMExpressionNode.class)})
    public abstract static class GCCUMulWithOverflow extends LLVMBuiltin {

        @Specialization
        public byte executeIntrinsic(byte left, byte right, LLVMAddress addr) {
            return (byte) (unsignedMulWithOverflowI8(left, right, addr) ? 1 : 0);
        }

        @Specialization
        public byte executeIntrinsic(short left, short right, LLVMAddress addr) {
            return (byte) (unsignedMulWithOverflowI16(left, right, addr) ? 1 : 0);
        }

        @Specialization
        public byte executeIntrinsic(int left, int right, LLVMAddress addr) {
            return (byte) (unsignedMulWithOverflowI32(left, right, addr) ? 1 : 0);
        }

        @Specialization
        public byte executeIntrinsic(long left, long right, LLVMAddress addr) {
            return (byte) (unsignedMulWithOverflowI64(left, right, addr) ? 1 : 0);
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class),
                    @NodeChild(value = "target", type = LLVMExpressionNode.class)})
    public abstract static class LLVMUMulWithOverflowI8 extends LLVMBuiltin {

        private final int secondValueOffset;

        public LLVMUMulWithOverflowI8(int secondValueOffset) {
            this.secondValueOffset = secondValueOffset;
        }

        @Specialization
        public LLVMAddress executeIntrinsic(byte left, byte right, LLVMAddress addr) {
            final boolean overflow = unsignedMulWithOverflowI8(left, right, addr);
            LLVMMemory.putI1(addr.getVal() + secondValueOffset, overflow);
            return addr;
        }

    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class),
                    @NodeChild(value = "target", type = LLVMExpressionNode.class)})
    public abstract static class LLVMUMulWithOverflowI16 extends LLVMBuiltin {

        private final int secondValueOffset;

        public LLVMUMulWithOverflowI16(int secondValueOffset) {
            this.secondValueOffset = secondValueOffset;
        }

        @Specialization
        public LLVMAddress executeIntrinsic(short left, short right, LLVMAddress addr) {
            final boolean overflow = unsignedMulWithOverflowI16(left, right, addr);
            LLVMMemory.putI1(addr.getVal() + secondValueOffset, overflow);
            return addr;
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class),
                    @NodeChild(value = "target", type = LLVMExpressionNode.class)})
    public abstract static class LLVMUMulWithOverflowI32 extends LLVMBuiltin {

        private final int secondValueOffset;

        public LLVMUMulWithOverflowI32(int secondValueOffset) {
            this.secondValueOffset = secondValueOffset;
        }

        @Specialization
        public LLVMAddress executeIntrinsic(int left, int right, LLVMAddress addr) {
            final boolean overflow = unsignedMulWithOverflowI32(left, right, addr);
            LLVMMemory.putI1(addr.getVal() + secondValueOffset, overflow);
            return addr;
        }
    }

    @NodeChildren({@NodeChild(value = "left", type = LLVMExpressionNode.class), @NodeChild(value = "right", type = LLVMExpressionNode.class),
                    @NodeChild(value = "target", type = LLVMExpressionNode.class)})
    public abstract static class LLVMUMulWithOverflowI64 extends LLVMBuiltin {

        private final int secondValueOffset;

        public LLVMUMulWithOverflowI64(int secondValueOffset) {
            this.secondValueOffset = secondValueOffset;
        }

        @Specialization
        public LLVMAddress executeIntrinsic(long left, long right, LLVMAddress addr) {
            final boolean overflow = unsignedMulWithOverflowI64(left, right, addr);
            LLVMMemory.putI1(addr.getVal() + secondValueOffset, overflow);
            return addr;
        }
    }
}
