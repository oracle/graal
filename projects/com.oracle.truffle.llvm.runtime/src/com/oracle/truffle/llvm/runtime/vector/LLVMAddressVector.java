package com.oracle.truffle.llvm.runtime.vector;

import java.util.Arrays;

import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

public class LLVMAddressVector {
    private static final int ADDRESS_LENGTH = 8; // Sulong only supports 64 bit addresses
    private final long[] vector;    // no LLVMAddress stored to improve performance

    public static LLVMAddressVector create(LLVMAddress[] vector) {
        return new LLVMAddressVector(vector);
    }

    private static LLVMAddressVector create(long[] vector) {
        return new LLVMAddressVector(vector);
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
        for (int i = 0; i < vector.length; i++)
            this.vector[i] = vector[i].getVal();

    }

    private LLVMAddressVector(long[] vector) {
        this.vector = vector;
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
        for (int i = 0; i < vector.length; i++)
            addresses[i] = LLVMAddress.fromLong(vector[i]);
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
}
