/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI1StoreNode.LLVMI1OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMArithmetic {

    private LLVMArithmetic() {
        // private constructor
    }

    public interface Arithmetic {
        boolean evalI8(byte left, byte right, LLVMPointer addr, LLVMI8StoreNode store);

        boolean evalI16(short left, short right, LLVMPointer addr, LLVMI16StoreNode store);

        boolean evalI32(int left, int right, LLVMPointer addr, LLVMI32StoreNode store);

        boolean evalI64(long left, long right, LLVMPointer addr, LLVMI64StoreNode store);
    }

    public interface SaturatingArithmetic {
        byte evalI8(byte left, byte right);

        short evalI16(short left, short right);

        int evalI32(int left, int right);

        long evalI64(long left, long right);
    }

    public interface CarryArithmetic {
        byte evalI8(byte left, byte right, byte cin, LLVMPointer addr, LLVMI8StoreNode store);

        short evalI16(short left, short right, short cin, LLVMPointer addr, LLVMI16StoreNode store);

        int evalI32(int left, int right, int cin, LLVMPointer addr, LLVMI32StoreNode store);

        long evalI64(long left, long right, long cin, LLVMPointer addr, LLVMI64StoreNode store);
    }

    public static final CarryArithmetic CARRY_ADD = new CarryArithmetic() {

        @Override
        public byte evalI8(byte left, byte right, byte cin, LLVMPointer addr, LLVMI8StoreNode store) {
            final int res = (left & LLVMExpressionNode.I8_MASK) + (right & LLVMExpressionNode.I8_MASK) + (cin & LLVMExpressionNode.I8_MASK);
            final boolean overflow = (res & (0xF << Byte.SIZE)) != 0;

            store.executeWithTarget(addr, (byte) (overflow ? 1 : 0));
            return (byte) res;
        }

        @Override
        public short evalI16(short left, short right, short cin, LLVMPointer addr, LLVMI16StoreNode store) {
            final int res = (left & LLVMExpressionNode.I16_MASK) + (right & LLVMExpressionNode.I16_MASK) + (cin & LLVMExpressionNode.I16_MASK);
            final boolean overflow = (res & (0xF << Short.SIZE)) != 0;

            store.executeWithTarget(addr, (short) (overflow ? 1 : 0));
            return (short) res;
        }

        @Override
        public int evalI32(int left, int right, int cin, LLVMPointer addr, LLVMI32StoreNode store) {
            final int res1 = left + right;
            final boolean overflow1 = ((~res1 & left) | (~res1 & right) | (left & right)) < 0;

            final int res2 = res1 + cin;
            final boolean overflow2 = ((~res2 & res1) | (~res2 & cin) | (res1 & cin)) < 0;

            store.executeWithTarget(addr, (overflow1 | overflow2) ? 1 : 0);
            return res2;
        }

        @Override
        public long evalI64(long left, long right, long cin, LLVMPointer addr, LLVMI64StoreNode store) {
            final long res1 = left + right;
            final boolean overflow1 = ((~res1 & left) | (~res1 & right) | (left & right)) < 0;

            final long res2 = res1 + cin;
            final boolean overflow2 = ((~res2 & res1) | (~res2 & cin) | (res1 & cin)) < 0;

            store.executeWithTarget(addr, (overflow1 | overflow2) ? 1 : 0);
            return res2;
        }
    };

    public static final CarryArithmetic CARRY_SUB = new CarryArithmetic() {

        @Override
        public byte evalI8(byte left, byte right, byte cin, LLVMPointer addr, LLVMI8StoreNode store) {
            final int res = (left & LLVMExpressionNode.I8_MASK) - (right & LLVMExpressionNode.I8_MASK) - (cin & LLVMExpressionNode.I8_MASK);
            final boolean overflow = res < 0;

            store.executeWithTarget(addr, (byte) (overflow ? 1 : 0));
            return (byte) res;
        }

        @Override
        public short evalI16(short left, short right, short cin, LLVMPointer addr, LLVMI16StoreNode store) {
            final int res = (left & LLVMExpressionNode.I16_MASK) - (right & LLVMExpressionNode.I16_MASK) - (cin & LLVMExpressionNode.I16_MASK);
            final boolean overflow = res < 0;

            store.executeWithTarget(addr, (short) (overflow ? 1 : 0));
            return (short) res;
        }

        @Override
        public int evalI32(int left, int right, int cin, LLVMPointer addr, LLVMI32StoreNode store) {
            final int res1 = left - right;
            final boolean overflow1 = Integer.compareUnsigned(left, right) < 0;

            final int res2 = res1 - cin;
            final boolean overflow2 = Integer.compareUnsigned(res1, cin) < 0;

            store.executeWithTarget(addr, (overflow1 | overflow2) ? 1 : 0);
            return res2;
        }

        @Override
        public long evalI64(long left, long right, long cin, LLVMPointer addr, LLVMI64StoreNode store) {
            final long res1 = left - right;
            final boolean overflow1 = Long.compareUnsigned(left, right) < 0;

            final long res2 = res1 - cin;
            final boolean overflow2 = Long.compareUnsigned(res1, cin) < 0;

            store.executeWithTarget(addr, (overflow1 | overflow2) ? 1 : 0);
            return res2;
        }
    };

    public static final Arithmetic SIGNED_ADD = new Arithmetic() {

        @Override
        public boolean evalI8(byte left, byte right, LLVMPointer addr, LLVMI8StoreNode store) {
            final int res = left + right;
            final boolean overflow = (((res ^ left) & (res ^ right)) & (1 << (Byte.SIZE - 1))) != 0;
            store.executeWithTarget(addr, (byte) res);
            return overflow;
        }

        @Override
        public boolean evalI16(short left, short right, LLVMPointer addr, LLVMI16StoreNode store) {
            final int res = left + right;
            final boolean overflow = (((res ^ left) & (res ^ right)) & (1 << (Short.SIZE - 1))) != 0;

            store.executeWithTarget(addr, (short) res);
            return overflow;
        }

        @Override
        public boolean evalI32(int left, int right, LLVMPointer addr, LLVMI32StoreNode store) {
            int res;
            boolean overflow = false;
            try {
                res = Math.addExact(left, right);
            } catch (ArithmeticException e) {
                // no transferToInterpreter - we want the compiler to remove that exception
                res = left + right;
                overflow = true;
            }
            store.executeWithTarget(addr, res);
            return overflow;
        }

        @Override
        public boolean evalI64(long left, long right, LLVMPointer addr, LLVMI64StoreNode store) {
            long res;
            boolean overflow = false;
            try {
                res = Math.addExact(left, right);
            } catch (ArithmeticException e) {
                // no transferToInterpreter - we want the compiler to remove that exception
                res = left + right;
                overflow = true;
            }
            store.executeWithTarget(addr, res);
            return overflow;
        }
    };

    public static final SaturatingArithmetic SIGNED_ADD_SAT = new SaturatingArithmetic() {

        @Override
        public byte evalI8(byte left, byte right) {
            final int res = left + right;
            final boolean overflow = (((res ^ left) & (res ^ right)) & (1 << (Byte.SIZE - 1))) != 0;
            if (overflow) {
                return left > 0 ? Byte.MAX_VALUE : Byte.MIN_VALUE;
            } else {
                return (byte) res;
            }
        }

        @Override
        public short evalI16(short left, short right) {
            final int res = left + right;
            final boolean overflow = (((res ^ left) & (res ^ right)) & (1 << (Short.SIZE - 1))) != 0;
            if (overflow) {
                return left > 0 ? Short.MAX_VALUE : Short.MIN_VALUE;
            } else {
                return (short) res;
            }
        }

        @Override
        public int evalI32(int left, int right) {
            int res;
            boolean overflow = false;
            try {
                res = Math.addExact(left, right);
            } catch (ArithmeticException e) {
                // no transferToInterpreter - we want the compiler to remove that exception
                res = left + right;
                overflow = true;
            }
            if (overflow) {
                return left > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            } else {
                return res;
            }
        }

        @Override
        public long evalI64(long left, long right) {
            long res;
            boolean overflow = false;
            try {
                res = Math.addExact(left, right);
            } catch (ArithmeticException e) {
                // no transferToInterpreter - we want the compiler to remove that exception
                res = left + right;
                overflow = true;
            }
            if (overflow) {
                return left > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
            } else {
                return res;
            }
        }
    };

    public static final Arithmetic UNSIGNED_ADD = new Arithmetic() {

        @Override
        public boolean evalI8(byte left, byte right, LLVMPointer addr, LLVMI8StoreNode store) {
            final int res = (left & LLVMExpressionNode.I8_MASK) + (right & LLVMExpressionNode.I8_MASK);
            final boolean overflow = (res & (1 << Byte.SIZE)) != 0;
            store.executeWithTarget(addr, (byte) res);
            return overflow;
        }

        @Override
        public boolean evalI16(short left, short right, LLVMPointer addr, LLVMI16StoreNode store) {
            final int res = (left & LLVMExpressionNode.I16_MASK) + (right & LLVMExpressionNode.I16_MASK);
            final boolean overflow = (res & (1 << Short.SIZE)) != 0;

            store.executeWithTarget(addr, (short) res);
            return overflow;
        }

        @Override
        public boolean evalI32(int left, int right, LLVMPointer addr, LLVMI32StoreNode store) {
            final int res = left + right;
            final boolean overflow = ((~res & left) | (~res & right) | (left & right)) < 0;
            store.executeWithTarget(addr, res);
            return overflow;
        }

        @Override
        public boolean evalI64(long left, long right, LLVMPointer addr, LLVMI64StoreNode store) {
            final long res = left + right;
            final boolean overflow = ((~res & left) | (~res & right) | (left & right)) < 0;
            store.executeWithTarget(addr, res);
            return overflow;
        }
    };

    public static final SaturatingArithmetic UNSIGNED_ADD_SAT = new SaturatingArithmetic() {

        @Override
        public byte evalI8(byte left, byte right) {
            final int res = (left & LLVMExpressionNode.I8_MASK) + (right & LLVMExpressionNode.I8_MASK);
            final boolean overflow = (res & (1 << Byte.SIZE)) != 0;
            return (byte) (overflow ? LLVMExpressionNode.I8_MASK : res);
        }

        @Override
        public short evalI16(short left, short right) {
            final int res = (left & LLVMExpressionNode.I16_MASK) + (right & LLVMExpressionNode.I16_MASK);
            final boolean overflow = (res & (1 << Short.SIZE)) != 0;
            return (short) (overflow ? LLVMExpressionNode.I16_MASK : res);
        }

        @Override
        public int evalI32(int left, int right) {
            final int res = left + right;
            final boolean overflow = ((~res & left) | (~res & right) | (left & right)) < 0;
            return overflow ? -1 : res;
        }

        @Override
        public long evalI64(long left, long right) {
            final long res = left + right;
            final boolean overflow = ((~res & left) | (~res & right) | (left & right)) < 0;
            return overflow ? -1 : res;
        }
    };

    public static final Arithmetic SIGNED_SUB = new Arithmetic() {

        @Override
        public boolean evalI8(byte left, byte right, LLVMPointer addr, LLVMI8StoreNode store) {
            final int res = left - right;
            final boolean overflow = (((left ^ right) & (left ^ res)) & (1 << (Byte.SIZE - 1))) != 0;
            store.executeWithTarget(addr, (byte) res);
            return overflow;
        }

        @Override
        public boolean evalI16(short left, short right, LLVMPointer addr, LLVMI16StoreNode store) {
            final int res = left - right;
            final boolean overflow = (((left ^ right) & (left ^ res)) & (1 << (Short.SIZE - 1))) != 0;

            store.executeWithTarget(addr, (short) res);
            return overflow;
        }

        @Override
        public boolean evalI32(int left, int right, LLVMPointer addr, LLVMI32StoreNode store) {
            int res;
            boolean overflow = false;
            try {
                res = Math.subtractExact(left, right);
            } catch (ArithmeticException e) {
                // no transferToInterpreter - we want the compiler to remove that exception
                res = left - right;
                overflow = true;
            }
            store.executeWithTarget(addr, res);
            return overflow;
        }

        @Override
        public boolean evalI64(long left, long right, LLVMPointer addr, LLVMI64StoreNode store) {
            long res;
            boolean overflow = false;
            try {
                res = Math.subtractExact(left, right);
            } catch (ArithmeticException e) {
                // no transferToInterpreter - we want the compiler to remove that exception
                res = left - right;
                overflow = true;
            }
            store.executeWithTarget(addr, res);
            return overflow;
        }
    };

    public static final SaturatingArithmetic SIGNED_SUB_SAT = new SaturatingArithmetic() {

        @Override
        public byte evalI8(byte left, byte right) {
            final int res = left - right;
            final boolean overflow = (((left ^ right) & (left ^ res)) & (1 << (Byte.SIZE - 1))) != 0;
            if (overflow) {
                return ((left > 0) ^ (right < 0)) ? Byte.MAX_VALUE : Byte.MIN_VALUE;
            } else {
                return (byte) res;
            }
        }

        @Override
        public short evalI16(short left, short right) {
            final int res = left - right;
            final boolean overflow = (((left ^ right) & (left ^ res)) & (1 << (Short.SIZE - 1))) != 0;
            if (overflow) {
                return ((left > 0) ^ (right < 0)) ? Short.MAX_VALUE : Short.MIN_VALUE;
            } else {
                return (short) res;
            }
        }

        @Override
        public int evalI32(int left, int right) {
            int res;
            boolean overflow = false;
            try {
                res = Math.subtractExact(left, right);
            } catch (ArithmeticException e) {
                // no transferToInterpreter - we want the compiler to remove that exception
                res = left - right;
                overflow = true;
            }
            if (overflow) {
                return ((left > 0) ^ (right < 0)) ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            } else {
                return res;
            }
        }

        @Override
        public long evalI64(long left, long right) {
            long res;
            boolean overflow = false;
            try {
                res = Math.subtractExact(left, right);
            } catch (ArithmeticException e) {
                // no transferToInterpreter - we want the compiler to remove that exception
                res = left - right;
                overflow = true;
            }
            if (overflow) {
                return ((left > 0) ^ (right < 0)) ? Long.MAX_VALUE : Long.MIN_VALUE;
            } else {
                return res;
            }
        }
    };

    public static final Arithmetic UNSIGNED_SUB = new Arithmetic() {

        @Override
        public boolean evalI8(byte left, byte right, LLVMPointer addr, LLVMI8StoreNode store) {
            final int res = (left & LLVMExpressionNode.I8_MASK) - (right & LLVMExpressionNode.I8_MASK);
            boolean overflow = res < 0;
            store.executeWithTarget(addr, (byte) res);
            return overflow;
        }

        @Override
        public boolean evalI16(short left, short right, LLVMPointer addr, LLVMI16StoreNode store) {
            final int res = (left & LLVMExpressionNode.I16_MASK) - (right & LLVMExpressionNode.I16_MASK);
            final boolean overflow = res < 0;

            store.executeWithTarget(addr, (short) res);
            return overflow;
        }

        @Override
        public boolean evalI32(int left, int right, LLVMPointer addr, LLVMI32StoreNode store) {
            final int res = left - right;
            final boolean overflow = Integer.compareUnsigned(left, right) < 0;
            store.executeWithTarget(addr, res);
            return overflow;
        }

        @Override
        public boolean evalI64(long left, long right, LLVMPointer addr, LLVMI64StoreNode store) {
            final long res = left - right;
            final boolean overflow = Long.compareUnsigned(left, right) < 0;
            store.executeWithTarget(addr, res);
            return overflow;
        }
    };

    public static final SaturatingArithmetic UNSIGNED_SUB_SAT = new SaturatingArithmetic() {

        @Override
        public byte evalI8(byte left, byte right) {
            final int res = (left & LLVMExpressionNode.I8_MASK) - (right & LLVMExpressionNode.I8_MASK);
            boolean overflow = res < 0;
            return (byte) (overflow ? 0 : res);
        }

        @Override
        public short evalI16(short left, short right) {
            final int res = (left & LLVMExpressionNode.I16_MASK) - (right & LLVMExpressionNode.I16_MASK);
            boolean overflow = res < 0;
            return (short) (overflow ? 0 : res);
        }

        @Override
        public int evalI32(int left, int right) {
            final int res = left - right;
            final boolean overflow = Integer.compareUnsigned(left, right) < 0;
            return overflow ? 0 : res;
        }

        @Override
        public long evalI64(long left, long right) {
            final long res = left - right;
            final boolean overflow = Long.compareUnsigned(left, right) < 0;
            return overflow ? 0 : res;
        }
    };

    public static final Arithmetic SIGNED_MUL = new Arithmetic() {

        @Override
        public boolean evalI8(byte left, byte right, LLVMPointer addr, LLVMI8StoreNode store) {
            final int res = left * right;
            final boolean overflow = (byte) res != res;
            store.executeWithTarget(addr, (byte) res);
            return overflow;
        }

        @Override
        public boolean evalI16(short left, short right, LLVMPointer addr, LLVMI16StoreNode store) {
            final int res = left * right;
            final boolean overflow = (short) res != res;

            store.executeWithTarget(addr, (short) res);
            return overflow;
        }

        @Override
        public boolean evalI32(int left, int right, LLVMPointer addr, LLVMI32StoreNode store) {
            int res;
            boolean overflow = false;
            try {
                res = Math.multiplyExact(left, right);
            } catch (ArithmeticException e) {
                // no transferToInterpreter - we want the compiler to remove that exception
                res = left * right;
                overflow = true;
            }
            store.executeWithTarget(addr, res);
            return overflow;
        }

        @Override
        public boolean evalI64(long left, long right, LLVMPointer addr, LLVMI64StoreNode store) {
            long res;
            boolean overflow = false;
            try {
                res = Math.multiplyExact(left, right);
            } catch (ArithmeticException e) {
                // no transferToInterpreter - we want the compiler to remove that exception
                res = left * right;
                overflow = true;
            }
            store.executeWithTarget(addr, res);
            return overflow;
        }
    };

    public static final Arithmetic UNSIGNED_MUL = new Arithmetic() {

        @Override
        public boolean evalI8(byte left, byte right, LLVMPointer addr, LLVMI8StoreNode store) {
            final int res = (left & LLVMExpressionNode.I8_MASK) * (right & LLVMExpressionNode.I8_MASK);
            final boolean overflow = (res & LLVMExpressionNode.I8_MASK) != res;
            store.executeWithTarget(addr, (byte) res);
            return overflow;
        }

        @Override
        public boolean evalI16(short left, short right, LLVMPointer addr, LLVMI16StoreNode store) {
            final int res = (left & LLVMExpressionNode.I16_MASK) * (right & LLVMExpressionNode.I16_MASK);
            final boolean overflow = (res & LLVMExpressionNode.I16_MASK) != res;

            store.executeWithTarget(addr, (short) res);
            return overflow;
        }

        @Override
        public boolean evalI32(int left, int right, LLVMPointer addr, LLVMI32StoreNode store) {
            final long res = (left & LLVMExpressionNode.I32_MASK) * (right & LLVMExpressionNode.I32_MASK);
            final boolean overflow = (res & LLVMExpressionNode.I32_MASK) != res;

            store.executeWithTarget(addr, (int) res);
            return overflow;
        }

        @Override
        public boolean evalI64(long left, long right, LLVMPointer addr, LLVMI64StoreNode store) {
            final long res = left * right;
            boolean overflow = false;
            if ((left | right) >>> 31 != 0) {
                if (right != 0 && Long.divideUnsigned(res, right) != left) {
                    overflow = true;
                }
            }

            store.executeWithTarget(addr, res);
            return overflow;
        }
    };

    @NodeChild(value = "left", type = LLVMExpressionNode.class)
    @NodeChild(value = "right", type = LLVMExpressionNode.class)
    @NodeChild(value = "target", type = LLVMExpressionNode.class)
    public abstract static class GCCArithmetic extends LLVMBuiltin {

        private final Arithmetic arithmetic;

        public GCCArithmetic(Arithmetic arithmetic) {
            this.arithmetic = arithmetic;
        }

        @Specialization
        protected byte doIntrinsic(byte left, byte right, LLVMPointer addr,
                        @Cached LLVMI8StoreNode store) {
            return (byte) (arithmetic.evalI8(left, right, addr, store) ? 1 : 0);
        }

        @Specialization
        protected short doIntrinsic(short left, short right, LLVMPointer addr,
                        @Cached LLVMI16StoreNode store) {
            return (short) (arithmetic.evalI16(left, right, addr, store) ? 1 : 0);
        }

        @Specialization
        protected int doIntrinsic(int left, int right, LLVMPointer addr,
                        @Cached LLVMI32StoreNode store) {
            return arithmetic.evalI32(left, right, addr, store) ? 1 : 0;
        }

        @Specialization
        protected long doIntrinsic(long left, long right, LLVMPointer addr,
                        @Cached LLVMI64StoreNode store) {
            return arithmetic.evalI64(left, right, addr, store) ? 1 : 0;
        }
    }

    @NodeChild(value = "left", type = LLVMExpressionNode.class)
    @NodeChild(value = "right", type = LLVMExpressionNode.class)
    public abstract static class LLVMSimpleArithmeticPrimitive extends LLVMBuiltin {
        private final SaturatingArithmetic arithmetic;

        public LLVMSimpleArithmeticPrimitive(SaturatingArithmetic arithmetic) {
            this.arithmetic = arithmetic;
        }

        @Specialization
        protected byte doIntrinsic(byte left, byte right) {
            return arithmetic.evalI8(left, right);
        }

        @Specialization
        protected short doIntrinsic(short left, short right) {
            return arithmetic.evalI16(left, right);
        }

        @Specialization
        protected int doIntrinsic(int left, int right) {
            return arithmetic.evalI32(left, right);
        }

        @Specialization
        protected long doIntrinsic(long left, long right) {
            return arithmetic.evalI64(left, right);
        }

    }

    @NodeChild(value = "left", type = LLVMExpressionNode.class)
    @NodeChild(value = "right", type = LLVMExpressionNode.class)
    @NodeChild(value = "target", type = LLVMExpressionNode.class)
    public abstract static class LLVMArithmeticWithOverflow extends LLVMBuiltin {

        private final long secondValueOffset;
        private final Arithmetic arithmetic;
        @Child private LLVMI1OffsetStoreNode storeI1 = LLVMI1OffsetStoreNode.create();

        public LLVMArithmeticWithOverflow(Arithmetic arithmetic, long secondValueOffset) {
            this.secondValueOffset = secondValueOffset;
            this.arithmetic = arithmetic;
        }

        @Specialization
        protected Object doIntrinsic(byte left, byte right, LLVMPointer addr,
                        @Cached LLVMI8StoreNode store) {
            boolean overflow = arithmetic.evalI8(left, right, addr, store);
            storeI1.executeWithTarget(addr, secondValueOffset, overflow);
            return addr;
        }

        @Specialization
        protected Object doIntrinsic(short left, short right, LLVMPointer addr,
                        @Cached LLVMI16StoreNode store) {
            boolean overflow = arithmetic.evalI16(left, right, addr, store);
            storeI1.executeWithTarget(addr, secondValueOffset, overflow);
            return addr;
        }

        @Specialization
        protected Object doIntrinsic(int left, int right, LLVMPointer addr,
                        @Cached LLVMI32StoreNode store) {
            boolean overflow = arithmetic.evalI32(left, right, addr, store);
            storeI1.executeWithTarget(addr, secondValueOffset, overflow);
            return addr;
        }

        @Specialization
        protected Object doIntrinsic(long left, long right, LLVMPointer addr,
                        @Cached LLVMI64StoreNode store) {
            boolean overflow = arithmetic.evalI64(left, right, addr, store);
            storeI1.executeWithTarget(addr, secondValueOffset, overflow);
            return addr;
        }
    }

    @NodeChild(value = "left", type = LLVMExpressionNode.class)
    @NodeChild(value = "right", type = LLVMExpressionNode.class)
    @NodeChild(value = "cin", type = LLVMExpressionNode.class)
    @NodeChild(value = "cout", type = LLVMExpressionNode.class)
    public abstract static class LLVMArithmeticWithOverflowAndCarry extends LLVMBuiltin {

        private final CarryArithmetic arithmetic;

        public LLVMArithmeticWithOverflowAndCarry(CarryArithmetic arithmetic) {
            this.arithmetic = arithmetic;
        }

        @Specialization
        protected byte doIntrinsic(byte left, byte right, byte cin, LLVMPointer addr,
                        @Cached LLVMI8StoreNode store) {
            return arithmetic.evalI8(left, right, cin, addr, store);
        }

        @Specialization
        protected short doIntrinsic(short left, short right, short cin, LLVMPointer addr,
                        @Cached LLVMI16StoreNode store) {
            return arithmetic.evalI16(left, right, cin, addr, store);
        }

        @Specialization
        protected int doIntrinsic(int left, int right, int cin, LLVMPointer addr,
                        @Cached LLVMI32StoreNode store) {
            return arithmetic.evalI32(left, right, cin, addr, store);
        }

        @Specialization
        protected long doIntrinsic(long left, long right, long cin, LLVMPointer addr,
                        @Cached LLVMI64StoreNode store) {
            return arithmetic.evalI64(left, right, cin, addr, store);
        }
    }
}
