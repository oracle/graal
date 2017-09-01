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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.debug;

import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.vector.LLVMAddressVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;

import java.math.BigInteger;

abstract class LLVMConstantVectorValueProvider extends LLVMConstantValueProvider {

    private final int elementSize;

    private final int length;

    LLVMConstantVectorValueProvider(int elementSize, int length) {
        this.elementSize = elementSize;
        this.length = length;
    }

    int getIndex(long bitOffset) {
        return (int) bitOffset / elementSize;
    }

    int getElementSize() {
        return elementSize;
    }

    @Override
    public boolean canRead(long bitOffset, int bits) {
        return bitOffset % elementSize == 0 && (bits == elementSize || bits == elementSize * length) && bitOffset + bits <= elementSize * length;
    }

    @Override
    public Object computeAddress(long bitOffset) {
        return "Vector";
    }

    static final class I1 extends LLVMConstantVectorValueProvider {

        private final LLVMI1Vector value;

        I1(LLVMI1Vector value) {
            super(1, value.getLength());
            this.value = value;
        }

        @Override
        Object getBaseValue() {
            return value;
        }

        @Override
        public boolean readBoolean(long bitOffset) {
            if (canRead(bitOffset, 1)) {
                return value.getValue(getIndex(bitOffset));
            } else {
                return super.readBoolean(bitOffset);
            }
        }
    }

    static final class I8 extends LLVMConstantVectorValueProvider {

        private final LLVMI8Vector value;

        I8(LLVMI8Vector value) {
            super(Byte.SIZE, value.getLength());
            this.value = value;
        }

        @Override
        Object getBaseValue() {
            return value;
        }

        @Override
        public BigInteger readInteger(long bitOffset, int bitSize, boolean signed) {
            if (!canRead(bitOffset, bitSize)) {
                return super.readInteger(bitOffset, bitSize, signed);

            } else if (signed) {
                return BigInteger.valueOf(value.getValue(getIndex(bitOffset)));

            } else {
                return BigInteger.valueOf(Byte.toUnsignedLong(value.getValue(getIndex(bitOffset))));
            }
        }
    }

    static final class I16 extends LLVMConstantVectorValueProvider {

        private final LLVMI16Vector value;

        I16(LLVMI16Vector value) {
            super(Short.SIZE, value.getLength());
            this.value = value;
        }

        @Override
        Object getBaseValue() {
            return value;
        }

        @Override
        public BigInteger readInteger(long bitOffset, int bitSize, boolean signed) {
            if (!canRead(bitOffset, bitSize)) {
                return super.readInteger(bitOffset, bitSize, signed);

            } else if (signed) {
                return BigInteger.valueOf(value.getValue(getIndex(bitOffset)));

            } else {
                return BigInteger.valueOf(Short.toUnsignedLong(value.getValue(getIndex(bitOffset))));
            }
        }
    }

    static final class I32 extends LLVMConstantVectorValueProvider {

        private final LLVMI32Vector value;

        I32(LLVMI32Vector value) {
            super(java.lang.Integer.SIZE, value.getLength());
            this.value = value;
        }

        @Override
        Object getBaseValue() {
            return value;
        }

        @Override
        public BigInteger readInteger(long bitOffset, int bitSize, boolean signed) {
            if (!canRead(bitOffset, bitSize)) {
                return super.readInteger(bitOffset, bitSize, signed);

            } else if (signed) {
                return BigInteger.valueOf(value.getValue(getIndex(bitOffset)));

            } else {
                return BigInteger.valueOf(java.lang.Integer.toUnsignedLong(value.getValue(getIndex(bitOffset))));
            }
        }
    }

    static final class I64 extends LLVMConstantVectorValueProvider {

        private final LLVMI64Vector value;

        I64(LLVMI64Vector value) {
            super(Long.SIZE, value.getLength());
            this.value = value;
        }

        @Override
        Object getBaseValue() {
            return value;
        }

        @Override
        public BigInteger readInteger(long bitOffset, int bitSize, boolean signed) {
            if (!canRead(bitOffset, bitSize)) {
                return super.readInteger(bitOffset, bitSize, signed);

            } else if (signed) {
                return BigInteger.valueOf(value.getValue(getIndex(bitOffset)));

            } else {
                return new BigInteger(Long.toUnsignedString(value.getValue(getIndex(bitOffset))));
            }
        }
    }

    static final class Float extends LLVMConstantVectorValueProvider {

        private final LLVMFloatVector value;

        Float(LLVMFloatVector value) {
            super(java.lang.Float.SIZE, value.getLength());
            this.value = value;
        }

        @Override
        Object getBaseValue() {
            return value;
        }

        @Override
        public Object readFloat(long bitOffset) {
            if (canRead(bitOffset, getElementSize())) {
                return value.getValue(getIndex(bitOffset));
            } else {
                return super.readFloat(bitOffset);
            }
        }
    }

    static final class Double extends LLVMConstantVectorValueProvider {

        private final LLVMDoubleVector value;

        Double(LLVMDoubleVector value) {
            super(java.lang.Double.SIZE, value.getLength());
            this.value = value;
        }

        @Override
        Object getBaseValue() {
            return value;
        }

        @Override
        public Object readDouble(long bitOffset) {
            if (canRead(bitOffset, getElementSize())) {
                return value.getValue(getIndex(bitOffset));
            } else {
                return super.readDouble(bitOffset);
            }
        }
    }

    static final class Address extends LLVMConstantVectorValueProvider {

        private final LLVMAddressVector value;

        Address(LLVMAddressVector value) {
            super(LLVMAddress.WORD_LENGTH_BIT, value.getLength());
            this.value = value;
        }

        @Override
        Object getBaseValue() {
            return value;
        }

        @Override
        public Object readAddress(long bitOffset) {
            if (canRead(bitOffset, getElementSize())) {
                return value.getValue(getIndex(bitOffset));
            } else {
                return super.readAddress(bitOffset);
            }
        }
    }
}
