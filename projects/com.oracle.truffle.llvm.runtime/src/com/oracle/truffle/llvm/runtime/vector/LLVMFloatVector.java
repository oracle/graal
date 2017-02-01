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
public final class LLVMFloatVector {

    private final float[] vector;
    private static final int FLOAT_SIZE = 4;

    public static LLVMFloatVector create(float[] vector) {
        return new LLVMFloatVector(vector);
    }

    private LLVMFloatVector(float[] vector) {
        this.vector = vector;
    }

    public static LLVMFloatVector readVectorFromMemory(LLVMAddress address, int size) {
        float[] vector = new float[size];
        LLVMAddress currentTarget = address;
        for (int i = 0; i < size; i++) {
            vector[i] = LLVMMemory.getFloat(currentTarget);
            currentTarget = currentTarget.increment(FLOAT_SIZE);
        }
        return create(vector);
    }

    public static void writeVectorToMemory(LLVMAddress address, LLVMFloatVector vector) {
        LLVMAddress currentTarget = address;
        for (int i = 0; i < vector.getLength(); i++) {
            LLVMMemory.putFloat(currentTarget, vector.getValue(i));
            currentTarget = currentTarget.increment(FLOAT_SIZE);
        }
    }

    // We do not want to use lambdas because of bad startup
    private interface Operation {
        float eval(float a, float b);
    }

    private static LLVMFloatVector doOperation(LLVMFloatVector lhs, LLVMFloatVector rhs, Operation op) {
        float[] left = lhs.vector;
        float[] right = rhs.vector;

        // not sure if this assert is true for llvm ir in general
        // this implementation however assumes it
        assert left.length == right.length;

        float[] result = new float[left.length];

        for (int i = 0; i < left.length; i++) {
            result[i] = op.eval(left[i], right[i]);
        }
        return create(result);
    }

    public LLVMFloatVector add(LLVMFloatVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public float eval(float a, float b) {
                return a + b;
            }
        });
    }

    public LLVMFloatVector mul(LLVMFloatVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public float eval(float a, float b) {
                return a * b;
            }
        });
    }

    public LLVMFloatVector sub(LLVMFloatVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public float eval(float a, float b) {
                return a - b;
            }
        });
    }

    public LLVMFloatVector div(LLVMFloatVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public float eval(float a, float b) {
                return a / b;
            }
        });
    }

    public LLVMFloatVector rem(LLVMFloatVector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public float eval(float a, float b) {
                return a % b;
            }
        });
    }

    public float[] getValues() {
        return vector;
    }

    public float getValue(int index) {
        return vector[index];
    }

    public LLVMFloatVector insert(float element, int index) {
        float[] copyOf = Arrays.copyOf(vector, vector.length);
        copyOf[index] = element;
        return create(copyOf);
    }

    public int getLength() {
        return vector.length;
    }

}
