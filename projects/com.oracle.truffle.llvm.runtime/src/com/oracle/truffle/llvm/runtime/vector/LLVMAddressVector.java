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
package com.oracle.truffle.llvm.runtime.vector;

import java.util.Arrays;
import java.util.function.BiFunction;

import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

public final class LLVMAddressVector {
    private static final int ADDRESS_LENGTH = 8; // Sulong only supports 64 bit addresses
    private final long[] vector;    // no LLVMAddress stored to improve performance

    public static LLVMAddressVector create(LLVMAddress[] vector) {
        return new LLVMAddressVector(vector);
    }

    public static LLVMAddressVector create(long[] vector) {
        return new LLVMAddressVector(vector);
    }

    public static LLVMAddressVector createNullVector() {
        return new LLVMAddressVector();
    }

    public static LLVMAddressVector readVectorFromMemory(LLVMAddress address, int size) {
        LLVMAddress[] vector = new LLVMAddress[size];
        long currentPtr = address.getVal();
        for (int i = 0; i < size; i++) {
            vector[i] = LLVMMemory.getAddress(currentPtr);
            currentPtr += ADDRESS_LENGTH;
        }
        return create(vector);
    }

    public static void writeVectorToMemory(LLVMAddress address, LLVMAddressVector vector) {
        long currentPtr = address.getVal();
        for (int i = 0; i < vector.getLength(); i++) {
            LLVMMemory.putAddress(currentPtr, vector.getValue(i));
            currentPtr += ADDRESS_LENGTH;
        }
    }

    private LLVMAddressVector(LLVMAddress[] vector) {
        this.vector = new long[vector.length];
        for (int i = 0; i < vector.length; i++) {
            this.vector[i] = vector[i].getVal();
        }

    }

    private LLVMAddressVector(long[] vector) {
        this.vector = vector;
    }

    private LLVMAddressVector() {
        this.vector = null;
    }

    // We do not want to use lambdas because of bad startup
    private interface Operation {
        long eval(long a, long b);
    }

    private static LLVMAddressVector doOperation(LLVMAddressVector lhs, LLVMAddressVector rhs, Operation op) {
        long[] left = lhs.vector;
        long[] right = rhs.vector;

        // not sure if this assert is true for llvm ir in general
        // this implementation however assumes it
        assert left.length == right.length;

        long[] result = new long[left.length];

        for (int i = 0; i < left.length; i++) {
            result[i] = op.eval(left[i], right[i]);
        }
        return create(result);
    }

    public LLVMAddressVector add(LLVMAddressVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a + b;
            }
        });
    }

    public LLVMAddressVector mul(LLVMAddressVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a * b;
            }
        });
    }

    public LLVMAddressVector sub(LLVMAddressVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a - b;
            }
        });
    }

    public LLVMAddressVector div(LLVMAddressVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a / b;
            }
        });
    }

    public LLVMAddressVector divUnsigned(LLVMAddressVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return Long.divideUnsigned(a, b);
            }
        });
    }

    public LLVMAddressVector rem(LLVMAddressVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a % b;
            }
        });
    }

    public LLVMAddressVector remUnsigned(LLVMAddressVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return Long.remainderUnsigned(a, b);
            }
        });
    }

    public LLVMAddressVector and(LLVMAddressVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a & b;
            }
        });
    }

    public LLVMAddressVector or(LLVMAddressVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a | b;
            }
        });
    }

    public LLVMAddressVector leftShift(LLVMAddressVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a << b;
            }
        });
    }

    public LLVMAddressVector logicalRightShift(LLVMAddressVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a >>> b;
            }
        });
    }

    public LLVMAddressVector arithmeticRightShift(LLVMAddressVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a >> b;
            }
        });
    }

    public LLVMAddressVector xor(LLVMAddressVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a ^ b;
            }
        });
    }

    public long[] getValues() {
        return vector;
    }

    public LLVMAddress[] getAddresses() {
        LLVMAddress[] addresses = new LLVMAddress[vector.length];
        for (int i = 0; i < vector.length; i++) {
            addresses[i] = LLVMAddress.fromLong(vector[i]);
        }
        return addresses;
    }

    public long getValue(int index) {
        return vector[index];
    }

    public LLVMAddress getAddress(int index) {
        return LLVMAddress.fromLong(vector[index]);
    }

    public LLVMAddressVector insert(LLVMAddress element, int index) {
        long[] copyOf = Arrays.copyOf(vector, vector.length);
        copyOf[index] = element.getVal();
        return create(copyOf);
    }

    public LLVMAddressVector insert(long element, int index) {
        long[] copyOf = Arrays.copyOf(vector, vector.length);
        copyOf[index] = element;
        return create(copyOf);
    }

    public int getLength() {
        return vector.length;
    }

    public LLVMI1Vector doCompare(LLVMAddressVector other, BiFunction<Long, Long, Boolean> compare) {
        int length = other.getLength();
        boolean[] values = new boolean[length];

        for (int i = 0; i < length; i++) {
            values[i] = compare.apply(getValue(i), other.getValue(i));
        }

        return LLVMI1Vector.create(values);
    }
}
