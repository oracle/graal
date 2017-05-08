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
public final class LLVMI32Vector {

    private static final int I32_SIZE = 4;
    private final int[] vector;

    public static LLVMI32Vector create(int[] vector) {
        return new LLVMI32Vector(vector);
    }

    public static LLVMI32Vector readVectorFromMemory(LLVMAddress address, int size) {
        int[] vector = new int[size];
        long currentPtr = address.getVal();
        for (int i = 0; i < size; i++) {
            vector[i] = LLVMMemory.getI32(currentPtr);
            currentPtr += I32_SIZE;
        }
        return create(vector);
    }

    public static void writeVectorToMemory(LLVMAddress address, LLVMI32Vector vector) {
        long currentPtr = address.getVal();
        for (int i = 0; i < vector.getLength(); i++) {
            LLVMMemory.putI32(currentPtr, vector.getValue(i));
            currentPtr += I32_SIZE;
        }
    }

    private LLVMI32Vector(int[] vector) {
        this.vector = vector;
    }

    // We do not want to use lambdas because of bad startup
    private interface Operation {
        int eval(int a, int b);
    }

    private static LLVMI32Vector doOperation(LLVMI32Vector lhs, LLVMI32Vector rhs, Operation op) {
        int[] left = lhs.vector;
        int[] right = rhs.vector;

        // not sure if this assert is true for llvm ir in general
        // this implementation however assumes it
        assert left.length == right.length;

        int[] result = new int[left.length];

        for (int i = 0; i < left.length; i++) {
            result[i] = op.eval(left[i], right[i]);
        }
        return create(result);
    }

    public LLVMI32Vector add(LLVMI32Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public int eval(int a, int b) {
                return a + b;
            }
        });
    }

    public LLVMI32Vector mul(LLVMI32Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public int eval(int a, int b) {
                return a * b;
            }
        });
    }

    public LLVMI32Vector sub(LLVMI32Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public int eval(int a, int b) {
                return a - b;
            }
        });
    }

    public LLVMI32Vector div(LLVMI32Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public int eval(int a, int b) {
                return a / b;
            }
        });
    }

    public LLVMI32Vector divUnsigned(LLVMI32Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public int eval(int a, int b) {
                return Integer.divideUnsigned(a, b);
            }
        });
    }

    public LLVMI32Vector rem(LLVMI32Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public int eval(int a, int b) {
                return a % b;
            }
        });
    }

    public LLVMI32Vector remUnsigned(LLVMI32Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public int eval(int a, int b) {
                return Integer.remainderUnsigned(a, b);
            }
        });
    }

    public LLVMI32Vector and(LLVMI32Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public int eval(int a, int b) {
                return a & b;
            }
        });
    }

    public LLVMI32Vector or(LLVMI32Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public int eval(int a, int b) {
                return a | b;
            }
        });
    }

    public LLVMI32Vector leftShift(LLVMI32Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public int eval(int a, int b) {
                return a << b;
            }
        });
    }

    public LLVMI32Vector logicalRightShift(LLVMI32Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public int eval(int a, int b) {
                return a >>> b;
            }
        });
    }

    public LLVMI32Vector arithmeticRightShift(LLVMI32Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public int eval(int a, int b) {
                return a >> b;
            }
        });
    }

    public LLVMI32Vector xor(LLVMI32Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public int eval(int a, int b) {
                return a ^ b;
            }
        });
    }

    public int[] getValues() {
        return vector;
    }

    public int getValue(int index) {
        return vector[index];
    }

    public LLVMI32Vector insert(int element, int index) {
        int[] copyOf = Arrays.copyOf(vector, vector.length);
        copyOf[index] = element;
        return create(copyOf);
    }

    public int getLength() {
        return vector.length;
    }

}
