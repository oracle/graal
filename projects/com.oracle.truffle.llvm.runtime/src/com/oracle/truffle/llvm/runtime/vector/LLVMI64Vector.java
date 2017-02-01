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
package com.oracle.truffle.llvm.runtime.vector;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

import java.util.Arrays;

@ValueType
public final class LLVMI64Vector {

    private static final int I64_SIZE = 8;
    private final long[] vector;

    public static LLVMI64Vector create(long[] vector) {
        return new LLVMI64Vector(vector);
    }

    public static LLVMI64Vector readVectorFromMemory(LLVMAddress address, int size) {
        long[] vector = new long[size];
        LLVMAddress currentTarget = address;
        for (int i = 0; i < size; i++) {
            vector[i] = LLVMMemory.getI64(currentTarget);
            currentTarget = currentTarget.increment(I64_SIZE);
        }
        return create(vector);
    }

    public static void writeVectorToMemory(LLVMAddress address, LLVMI64Vector vector) {
        LLVMAddress currentTarget = address;
        for (int i = 0; i < vector.getLength(); i++) {
            LLVMMemory.putI64(currentTarget, vector.getValue(i));
            currentTarget = currentTarget.increment(I64_SIZE);
        }
    }

    private LLVMI64Vector(long[] vector) {
        this.vector = vector;
    }

    // We do not want to use lambdas because of bad startup
    private interface Operation {
        long eval(long a, long b);
    }

    private static LLVMI64Vector doOperation(LLVMI64Vector lhs, LLVMI64Vector rhs, Operation op) {
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

    public LLVMI64Vector add(LLVMI64Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a + b;
            }
        });
    }

    public LLVMI64Vector mul(LLVMI64Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a * b;
            }
        });
    }

    public LLVMI64Vector sub(LLVMI64Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a - b;
            }
        });
    }

    public LLVMI64Vector div(LLVMI64Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a / b;
            }
        });
    }

    public LLVMI64Vector divUnsigned(LLVMI64Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return Long.divideUnsigned(a, b);
            }
        });
    }

    public LLVMI64Vector rem(LLVMI64Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a % b;
            }
        });
    }

    public LLVMI64Vector remUnsigned(LLVMI64Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return Long.remainderUnsigned(a, b);
            }
        });
    }

    public LLVMI64Vector and(LLVMI64Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a & b;
            }
        });
    }

    public LLVMI64Vector or(LLVMI64Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a | b;
            }
        });
    }

    public LLVMI64Vector leftShift(LLVMI64Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a << b;
            }
        });
    }

    public LLVMI64Vector logicalRightShift(LLVMI64Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a >>> b;
            }
        });
    }

    public LLVMI64Vector arithmeticRightShift(LLVMI64Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public long eval(long a, long b) {
                return a >> b;
            }
        });
    }

    public LLVMI64Vector xor(LLVMI64Vector rightValue) {
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

    public long getValue(int index) {
        return vector[index];
    }

    public LLVMI64Vector insert(long element, int index) {
        long[] copyOf = Arrays.copyOf(vector, vector.length);
        copyOf[index] = element;
        return create(copyOf);
    }

    public int getLength() {
        return vector.length;
    }

}
