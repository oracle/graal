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
public final class LLVMI16Vector {

    private static final int MASK = 0xffff;
    private static final int I16_SIZE = 2;

    private final short[] vector;

    public static LLVMI16Vector create(short[] vector) {
        return new LLVMI16Vector(vector);
    }

    private LLVMI16Vector(short[] vector) {
        this.vector = vector;
    }

    public static LLVMI16Vector readVectorFromMemory(LLVMAddress address, int size) {
        short[] vector = new short[size];
        long currentPtr = address.getVal();
        for (int i = 0; i < size; i++) {
            vector[i] = LLVMMemory.getI16(currentPtr);
            currentPtr += I16_SIZE;
        }
        return create(vector);
    }

    public static void writeVectorToMemory(LLVMAddress address, LLVMI16Vector vector) {
        long currentPtr = address.getVal();
        for (int i = 0; i < vector.getLength(); i++) {
            LLVMMemory.putI16(currentPtr, vector.getValue(i));
            currentPtr += I16_SIZE;
        }
    }

    // We do not want to use lambdas because of bad startup
    private interface Operation {
        short eval(short a, short b);
    }

    private static LLVMI16Vector doOperation(LLVMI16Vector lhs, LLVMI16Vector rhs, Operation op) {
        short[] left = lhs.vector;
        short[] right = rhs.vector;

        // not sure if this assert is true for llvm ir in general
        // this implementation however assumes it
        assert left.length == right.length;

        short[] result = new short[left.length];

        for (int i = 0; i < left.length; i++) {
            result[i] = op.eval(left[i], right[i]);
        }
        return create(result);
    }

    public LLVMI16Vector add(LLVMI16Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public short eval(short a, short b) {
                return (short) (a + b);
            }
        });
    }

    public LLVMI16Vector mul(LLVMI16Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public short eval(short a, short b) {
                return (short) (a * b);
            }
        });
    }

    public LLVMI16Vector sub(LLVMI16Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public short eval(short a, short b) {
                return (short) (a - b);
            }
        });
    }

    public LLVMI16Vector div(LLVMI16Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public short eval(short a, short b) {
                return (short) (a / b);
            }
        });
    }

    public LLVMI16Vector divUnsigned(LLVMI16Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public short eval(short a, short b) {
                return (short) ((a & MASK) / (b & MASK));
            }
        });
    }

    public LLVMI16Vector rem(LLVMI16Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public short eval(short a, short b) {
                return (short) (a % b);
            }
        });
    }

    public LLVMI16Vector remUnsigned(LLVMI16Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public short eval(short a, short b) {
                return (short) ((a & MASK) % (b & MASK));
            }
        });
    }

    public LLVMI16Vector and(LLVMI16Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public short eval(short a, short b) {
                return (short) (a & b);
            }
        });
    }

    public LLVMI16Vector or(LLVMI16Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public short eval(short a, short b) {
                return (short) (a | b);
            }
        });
    }

    public LLVMI16Vector leftShift(LLVMI16Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public short eval(short a, short b) {
                return (short) (a << b);
            }
        });
    }

    public LLVMI16Vector logicalRightShift(LLVMI16Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public short eval(short a, short b) {
                return (short) (a >>> b);
            }
        });
    }

    public LLVMI16Vector arithmeticRightShift(LLVMI16Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public short eval(short a, short b) {
                return (short) (a >> b);
            }
        });
    }

    public LLVMI16Vector xor(LLVMI16Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public short eval(short a, short b) {
                return (short) (a ^ b);
            }
        });
    }

    public short[] getValues() {
        return vector;
    }

    public short getValue(int index) {
        return vector[index];
    }

    public LLVMI16Vector insert(short element, int index) {
        short[] copyOf = Arrays.copyOf(vector, vector.length);
        copyOf[index] = element;
        return create(copyOf);
    }

    public int getLength() {
        return vector.length;
    }

}
