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

import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import java.util.Arrays;
import java.util.function.BiFunction;

public final class LLVMPointerVector {
    private final long[] vector;    // no LLVMNativePointer stored to improve performance

    public static LLVMPointerVector create(LLVMNativePointer[] vector) {
        return new LLVMPointerVector(vector);
    }

    public static LLVMPointerVector create(long[] vector) {
        return new LLVMPointerVector(vector);
    }

    public static LLVMPointerVector createNullVector() {
        return new LLVMPointerVector();
    }

    private LLVMPointerVector(LLVMNativePointer[] vector) {
        this.vector = new long[vector.length];
        for (int i = 0; i < vector.length; i++) {
            this.vector[i] = vector[i].asNative();
        }
    }

    private LLVMPointerVector(long[] vector) {
        this.vector = vector;
    }

    private LLVMPointerVector() {
        this.vector = null;
    }

    // We do not want to use lambdas because of bad startup
    private interface Operation {
        long eval(long a, long b);
    }

    private static LLVMPointerVector doOperation(LLVMPointerVector lhs, LLVMPointerVector rhs, Operation op) {
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

    public LLVMPointerVector add(LLVMPointerVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a + b;
            }
        });
    }

    public LLVMPointerVector mul(LLVMPointerVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a * b;
            }
        });
    }

    public LLVMPointerVector sub(LLVMPointerVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a - b;
            }
        });
    }

    public LLVMPointerVector div(LLVMPointerVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a / b;
            }
        });
    }

    public LLVMPointerVector divUnsigned(LLVMPointerVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return Long.divideUnsigned(a, b);
            }
        });
    }

    public LLVMPointerVector rem(LLVMPointerVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a % b;
            }
        });
    }

    public LLVMPointerVector remUnsigned(LLVMPointerVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return Long.remainderUnsigned(a, b);
            }
        });
    }

    public LLVMPointerVector and(LLVMPointerVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a & b;
            }
        });
    }

    public LLVMPointerVector or(LLVMPointerVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a | b;
            }
        });
    }

    public LLVMPointerVector leftShift(LLVMPointerVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a << b;
            }
        });
    }

    public LLVMPointerVector logicalRightShift(LLVMPointerVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a >>> b;
            }
        });
    }

    public LLVMPointerVector arithmeticRightShift(LLVMPointerVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a >> b;
            }
        });
    }

    public LLVMPointerVector xor(LLVMPointerVector rightValue) {
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

    public LLVMNativePointer[] getPointers() {
        LLVMNativePointer[] addresses = new LLVMNativePointer[vector.length];
        for (int i = 0; i < vector.length; i++) {
            addresses[i] = LLVMNativePointer.create(vector[i]);
        }
        return addresses;
    }

    public long getValue(int index) {
        return vector[index];
    }

    public LLVMNativePointer getPointer(int index) {
        return LLVMNativePointer.create(vector[index]);
    }

    public LLVMPointerVector insert(LLVMNativePointer element, int index) {
        long[] copyOf = Arrays.copyOf(vector, vector.length);
        copyOf[index] = element.asNative();
        return create(copyOf);
    }

    public LLVMPointerVector insert(long element, int index) {
        long[] copyOf = Arrays.copyOf(vector, vector.length);
        copyOf[index] = element;
        return create(copyOf);
    }

    public int getLength() {
        return vector.length;
    }

    public LLVMI1Vector doCompare(LLVMPointerVector other, BiFunction<Long, Long, Boolean> compare) {
        int length = other.getLength();
        boolean[] values = new boolean[length];

        for (int i = 0; i < length; i++) {
            values[i] = compare.apply(getValue(i), other.getValue(i));
        }

        return LLVMI1Vector.create(values);
    }
}
