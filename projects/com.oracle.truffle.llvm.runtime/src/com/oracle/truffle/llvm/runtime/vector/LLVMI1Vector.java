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
public final class LLVMI1Vector {

    private static final int I1_SIZE = 1;
    private final boolean[] vector;

    public static LLVMI1Vector create(boolean[] vector) {
        return new LLVMI1Vector(vector);
    }

    private LLVMI1Vector(boolean[] vector) {
        this.vector = vector;
    }

    public static LLVMI1Vector readVectorFromMemory(LLVMAddress address, int size) {
        boolean[] vector = new boolean[size];
        long currentPtr = address.getVal();
        for (int i = 0; i < size; i++) {
            vector[i] = LLVMMemory.getI1(currentPtr);
            currentPtr += I1_SIZE;
        }
        return create(vector);
    }

    public static void writeVectorToMemory(LLVMAddress address, LLVMI1Vector vector) {
        long currentPtr = address.getVal();
        for (int i = 0; i < vector.getLength(); i++) {
            LLVMMemory.putI1(currentPtr, vector.getValue(i));
            currentPtr += I1_SIZE;
        }
    }

    // We do not want to use lambdas because of bad startup
    private interface Operation {
        boolean eval(boolean a, boolean b);
    }

    private static LLVMI1Vector doOperation(LLVMI1Vector lhs, LLVMI1Vector rhs, Operation op) {
        boolean[] left = lhs.vector;
        boolean[] right = rhs.vector;

        // not sure if this assert is true for llvm ir in general
        // this implementation however assumes it
        assert left.length == right.length;

        boolean[] result = new boolean[left.length];

        for (int i = 0; i < left.length; i++) {
            result[i] = op.eval(left[i], right[i]);
        }
        return create(result);
    }

    public LLVMI1Vector and(LLVMI1Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public boolean eval(boolean a, boolean b) {
                return a & b;
            }
        });
    }

    public LLVMI1Vector or(LLVMI1Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public boolean eval(boolean a, boolean b) {
                return a | b;
            }
        });
    }

    public LLVMI1Vector xor(LLVMI1Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public boolean eval(boolean a, boolean b) {
                return a ^ b;
            }
        });
    }

    public boolean[] getValues() {
        return vector;
    }

    public boolean getValue(int index) {
        return vector[index];
    }

    public LLVMI1Vector insert(boolean element, int index) {
        boolean[] copyOf = Arrays.copyOf(vector, vector.length);
        copyOf[index] = element;
        return create(copyOf);
    }

    public int getLength() {
        return vector.length;
    }

}
