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
import java.util.function.BiFunction;

@ValueType
public final class LLVMI8Vector {

    private static final int I8_SIZE = 1;
    private final byte[] vector;

    public static LLVMI8Vector create(byte[] vector) {
        return new LLVMI8Vector(vector);
    }

    private LLVMI8Vector(byte[] vector) {
        this.vector = vector;
    }

    public static LLVMI8Vector readVectorFromMemory(LLVMAddress address, int size) {
        byte[] vector = new byte[size];
        long currentPtr = address.getVal();
        for (int i = 0; i < size; i++) {
            vector[i] = LLVMMemory.getI8(currentPtr);
            currentPtr += I8_SIZE;
        }
        return create(vector);
    }

    public static void writeVectorToMemory(LLVMAddress address, LLVMI8Vector vector) {
        long currentPtr = address.getVal();
        for (int i = 0; i < vector.getLength(); i++) {
            LLVMMemory.putI8(currentPtr, vector.getValue(i));
            currentPtr += I8_SIZE;
        }
    }

    // We do not want to use lambdas because of bad startup
    private interface Operation {
        byte eval(byte a, byte b);
    }

    private static LLVMI8Vector doOperation(LLVMI8Vector lhs, LLVMI8Vector rhs, Operation op) {
        byte[] left = lhs.vector;
        byte[] right = rhs.vector;

        // not sure if this assert is true for llvm ir in general
        // this implementation however assumes it
        assert left.length == right.length;

        byte[] result = new byte[left.length];

        for (int i = 0; i < left.length; i++) {
            result[i] = op.eval(left[i], right[i]);
        }
        return create(result);
    }

    public LLVMI8Vector add(LLVMI8Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public byte eval(byte a, byte b) {
                return (byte) (a + b);
            }
        });
    }

    public LLVMI8Vector mul(LLVMI8Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public byte eval(byte a, byte b) {
                return (byte) (a * b);
            }
        });
    }

    public LLVMI8Vector sub(LLVMI8Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public byte eval(byte a, byte b) {
                return (byte) (a - b);
            }
        });
    }

    public LLVMI8Vector div(LLVMI8Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public byte eval(byte a, byte b) {
                return (byte) (a / b);
            }
        });
    }

    public LLVMI8Vector divUnsigned(LLVMI8Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public byte eval(byte a, byte b) {
                return (byte) (Byte.toUnsignedInt(a) / Byte.toUnsignedInt(b));
            }
        });
    }

    public LLVMI8Vector rem(LLVMI8Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public byte eval(byte a, byte b) {
                return (byte) (a % b);
            }
        });
    }

    public LLVMI8Vector remUnsigned(LLVMI8Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public byte eval(byte a, byte b) {
                return (byte) (Byte.toUnsignedInt(a) % Byte.toUnsignedInt(b));
            }
        });
    }

    public LLVMI8Vector and(LLVMI8Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public byte eval(byte a, byte b) {
                return (byte) (a & b);
            }
        });
    }

    public LLVMI8Vector or(LLVMI8Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public byte eval(byte a, byte b) {
                return (byte) (a | b);
            }
        });
    }

    public LLVMI8Vector leftShift(LLVMI8Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public byte eval(byte a, byte b) {
                return (byte) (a << b);
            }
        });
    }

    public LLVMI8Vector logicalRightShift(LLVMI8Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public byte eval(byte a, byte b) {
                return (byte) (a >>> b);
            }
        });
    }

    public LLVMI8Vector arithmeticRightShift(LLVMI8Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public byte eval(byte a, byte b) {
                return (byte) (a >> b);
            }
        });
    }

    public LLVMI8Vector xor(LLVMI8Vector rightValue) {
        return doOperation(this, rightValue, new Operation() {
            @Override
            public byte eval(byte a, byte b) {
                return (byte) (a ^ b);
            }
        });
    }

    public byte[] getValues() {
        return vector;
    }

    public byte getValue(int index) {
        return vector[index];
    }

    public LLVMI8Vector insert(byte element, int index) {
        byte[] copyOf = Arrays.copyOf(vector, vector.length);
        copyOf[index] = element;
        return create(copyOf);
    }

    public int getLength() {
        return vector.length;
    }

    public LLVMI1Vector doCompare(LLVMI8Vector other, BiFunction<Byte, Byte, Boolean> comparison) {
        int length = getLength();
        boolean[] values = new boolean[length];

        for (int i = 0; i < length; i++) {
            values[i] = comparison.apply(getValue(i), other.getValue(i));
        }

        return LLVMI1Vector.create(values);
    }

}
