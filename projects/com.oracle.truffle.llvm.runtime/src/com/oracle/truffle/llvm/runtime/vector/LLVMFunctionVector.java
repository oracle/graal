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
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;

public final class LLVMFunctionVector {
    private final long[] vector;    // no LLVMAddress stored to improve performance

    public static LLVMFunctionVector create(LLVMFunctionDescriptor[] vector) {
        return new LLVMFunctionVector(vector);
    }

    public static LLVMFunctionVector create(long[] vector) {
        return new LLVMFunctionVector(vector);
    }

    public static LLVMFunctionVector createNullVector() {
        return new LLVMFunctionVector();
    }

    private LLVMFunctionVector(LLVMFunctionDescriptor[] vector) {
        this.vector = new long[vector.length];
        for (int i = 0; i < vector.length; i++) {
            this.vector[i] = vector[i].toNative().asPointer();
        }

    }

    private LLVMFunctionVector(long[] vector) {
        this.vector = vector;
    }

    private LLVMFunctionVector() {
        this.vector = null;
    }

    // We do not want to use lambdas because of bad startup
    private interface Operation {
        long eval(long a, long b);
    }

    private static LLVMFunctionVector doOperation(LLVMFunctionVector lhs, LLVMFunctionVector rhs, Operation op) {
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

    public LLVMFunctionVector add(LLVMFunctionVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a + b;
            }
        });
    }

    public LLVMFunctionVector mul(LLVMFunctionVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a * b;
            }
        });
    }

    public LLVMFunctionVector sub(LLVMFunctionVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a - b;
            }
        });
    }

    public LLVMFunctionVector div(LLVMFunctionVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a / b;
            }
        });
    }

    public LLVMFunctionVector divUnsigned(LLVMFunctionVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return Long.divideUnsigned(a, b);
            }
        });
    }

    public LLVMFunctionVector rem(LLVMFunctionVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a % b;
            }
        });
    }

    public LLVMFunctionVector remUnsigned(LLVMFunctionVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return Long.remainderUnsigned(a, b);
            }
        });
    }

    public LLVMFunctionVector and(LLVMFunctionVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a & b;
            }
        });
    }

    public LLVMFunctionVector or(LLVMFunctionVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a | b;
            }
        });
    }

    public LLVMFunctionVector leftShift(LLVMFunctionVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a << b;
            }
        });
    }

    public LLVMFunctionVector logicalRightShift(LLVMFunctionVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a >>> b;
            }
        });
    }

    public LLVMFunctionVector arithmeticRightShift(LLVMFunctionVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a >> b;
            }
        });
    }

    public LLVMFunctionVector xor(LLVMFunctionVector rightValue) {
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

    public LLVMFunctionVector insert(LLVMAddress element, int index) {
        long[] copyOf = Arrays.copyOf(vector, vector.length);
        copyOf[index] = element.getVal();
        return create(copyOf);
    }

    public LLVMFunctionVector insert(long element, int index) {
        long[] copyOf = Arrays.copyOf(vector, vector.length);
        copyOf[index] = element;
        return create(copyOf);
    }

    public int getLength() {
        return vector.length;
    }

    public LLVMI1Vector doCompare(LLVMFunctionVector other, BiFunction<Long, Long, Boolean> compare) {
        int length = other.getLength();
        boolean[] values = new boolean[length];

        for (int i = 0; i < length; i++) {
            values[i] = compare.apply(getValue(i), other.getValue(i));
        }

        return LLVMI1Vector.create(values);
    }
}
