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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

@ValueType
public final class LLVMDoubleVector {

    private final double[] vector;
    private static final int DOUBLE_SIZE = 8;

    public static LLVMDoubleVector create(double[] vector) {
        return new LLVMDoubleVector(vector);
    }

    private LLVMDoubleVector(double[] vector) {
        this.vector = vector;
    }

    public static LLVMDoubleVector readVectorFromMemory(LLVMAddress address, int size) {
        double[] vector = new double[size];
        LLVMAddress currentTarget = address;
        for (int i = 0; i < size; i++) {
            vector[i] = LLVMMemory.getDouble(currentTarget);
            currentTarget = currentTarget.increment(DOUBLE_SIZE);
        }
        return create(vector);
    }

    public static void writeVectorToMemory(LLVMAddress address, LLVMDoubleVector vector) {
        LLVMAddress currentTarget = address;
        for (int i = 0; i < vector.getLength(); i++) {
            LLVMMemory.putDouble(currentTarget, vector.getValue(i));
            currentTarget = currentTarget.increment(DOUBLE_SIZE);
        }
    }

    // We do not want to use lambdas because of bad startup
    private interface Operation {
        double eval(double a, double b);
    }

    private static LLVMDoubleVector doOperation(LLVMDoubleVector lhs, LLVMDoubleVector rhs, Operation op) {
        double[] left = lhs.vector;
        double[] right = rhs.vector;

        // not sure if this assert is true for llvm ir in general
        // this implementation however assumes it
        assert left.length == right.length;

        double[] result = new double[left.length];

        for (int i = 0; i < left.length; i++) {
            result[i] = op.eval(left[i], right[i]);
        }
        return create(result);
    }

    public LLVMDoubleVector add(LLVMDoubleVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public double eval(double a, double b) {
                return a + b;
            }
        });
    }

    public LLVMDoubleVector mul(LLVMDoubleVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public double eval(double a, double b) {
                return a * b;
            }
        });
    }

    public LLVMDoubleVector sub(LLVMDoubleVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public double eval(double a, double b) {
                return a - b;
            }
        });
    }

    public LLVMDoubleVector div(LLVMDoubleVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public double eval(double a, double b) {
                return a / b;
            }
        });
    }

    public LLVMDoubleVector rem(LLVMDoubleVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public double eval(double a, double b) {
                return a % b;
            }
        });
    }

    public double[] getValues() {
        return vector;
    }

    public double getValue(int index) {
        return vector[index];
    }

    public LLVMDoubleVector insert(double element, int index) {
        double[] copyOf = Arrays.copyOf(vector, vector.length);
        copyOf[index] = element;
        return create(copyOf);
    }

    public int getLength() {
        return vector.length;
    }

}
