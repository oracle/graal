/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.cast;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
@NodeField(name = "vectorLength", type = int.class)
public abstract class LLVMToVectorNode extends LLVMExpressionNode {

    protected static final int SHORTS_PER_INT = Integer.BYTES / Short.BYTES;
    protected static final int SHORTS_PER_LONG = Long.BYTES / Short.BYTES;
    protected static final int SHORTS_PER_FLOAT = Float.BYTES / Short.BYTES;
    protected static final int SHORTS_PER_DOUBLE = Double.BYTES / Short.BYTES;

    protected static final int INTS_PER_LONG = Long.BYTES / Integer.BYTES;
    protected static final int INTS_PER_DOUBLE = Double.BYTES / Integer.BYTES;

    protected static final int FLOATS_PER_LONG = Long.BYTES / Float.BYTES;
    protected static final int FLOATS_PER_DOUBLE = Double.BYTES / Float.BYTES;

    protected abstract int getVectorLength();

    public abstract static class LLVMSignedCastToI1VectorNode extends LLVMToVectorNode {

        @Specialization
        protected LLVMI1Vector doI1(LLVMI1Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doI8(LLVMI8Vector from) {
            assert from.getLength() == getVectorLength();
            final boolean[] vector = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (from.getValue(i) & 1) != 0;
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doI16(LLVMI16Vector from) {
            assert from.getLength() == getVectorLength();
            final boolean[] vector = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (from.getValue(i) & 1) != 0;
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doI32(LLVMI32Vector from) {
            assert from.getLength() == getVectorLength();
            final boolean[] vector = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (from.getValue(i) & 1) != 0;
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doI64(LLVMI64Vector from) {
            assert from.getLength() == getVectorLength();
            final boolean[] vector = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (from.getValue(i) & 1) != 0;
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doFloat(LLVMFloatVector from) {
            assert from.getLength() == getVectorLength();
            final boolean[] vector = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) != 0;
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doDouble(LLVMDoubleVector from) {
            assert from.getLength() == getVectorLength();
            final boolean[] vector = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) != 0;
            }
            return LLVMI1Vector.create(vector);
        }
    }

    public abstract static class LLVMSignedCastToI8VectorNode extends LLVMToVectorNode {

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doI1(LLVMI1Vector from) {
            assert from.getLength() == getVectorLength();
            final byte[] vector = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (byte) (from.getValue(i) ? 0xff : 0);
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doI8(LLVMI8Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doI16(LLVMI16Vector from) {
            assert from.getLength() == getVectorLength();
            final byte[] vector = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (byte) from.getValue(i);
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doI32(LLVMI32Vector from) {
            assert from.getLength() == getVectorLength();
            final byte[] vector = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (byte) from.getValue(i);
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doI64(LLVMI64Vector from) {
            assert from.getLength() == getVectorLength();
            final byte[] vector = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (byte) from.getValue(i);
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doFloat(LLVMFloatVector from) {
            assert from.getLength() == getVectorLength();
            final byte[] vector = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (byte) from.getValue(i);
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doDouble(LLVMDoubleVector from) {
            assert from.getLength() == getVectorLength();
            final byte[] vector = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (byte) from.getValue(i);
            }
            return LLVMI8Vector.create(vector);
        }
    }

    public abstract static class LLVMSignedCastToI16VectorNode extends LLVMToVectorNode {

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI1(LLVMI1Vector from) {
            assert from.getLength() == getVectorLength();
            final short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (short) (from.getValue(i) ? 1 : 0);
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI8(LLVMI8Vector from) {
            assert from.getLength() == getVectorLength();
            final short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doI16(LLVMI16Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI32(LLVMI32Vector from) {
            assert from.getLength() == getVectorLength();
            final short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (short) from.getValue(i);
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI64(LLVMI64Vector from) {
            assert from.getLength() == getVectorLength();
            final short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (short) from.getValue(i);
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doFloat(LLVMFloatVector from) {
            assert from.getLength() == getVectorLength();
            final short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (short) from.getValue(i);
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doDouble(LLVMDoubleVector from) {
            assert from.getLength() == getVectorLength();
            final short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (short) from.getValue(i);
            }
            return LLVMI16Vector.create(vector);
        }
    }

    public abstract static class LLVMSignedCastToI32VectorNode extends LLVMToVectorNode {

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI1(LLVMI1Vector from) {
            assert from.getLength() == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) ? 1 : 0;
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI8(LLVMI8Vector from) {
            assert from.getLength() == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI16(LLVMI16Vector from) {
            assert from.getLength() == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doI32(LLVMI32Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI64(LLVMI64Vector from) {
            assert from.getLength() == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (int) from.getValue(i);
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doFloat(LLVMFloatVector from) {
            assert from.getLength() == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (int) from.getValue(i);
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doDouble(LLVMDoubleVector from) {
            assert from.getLength() == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (int) from.getValue(i);
            }
            return LLVMI32Vector.create(vector);
        }
    }

    public abstract static class LLVMSignedCastToI64VectorNode extends LLVMToVectorNode {

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI1(LLVMI1Vector from) {
            assert from.getLength() == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) ? 1 : 0;
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI8(LLVMI8Vector from) {
            assert from.getLength() == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI16(LLVMI16Vector from) {
            assert from.getLength() == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI32(LLVMI32Vector from) {
            assert from.getLength() == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMI64Vector doI64(LLVMI64Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doFloat(LLVMFloatVector from) {
            assert from.getLength() == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (long) from.getValue(i);
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doDouble(LLVMDoubleVector from) {
            assert from.getLength() == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (long) from.getValue(i);
            }
            return LLVMI64Vector.create(vector);
        }
    }

    public abstract static class LLVMSignedCastToFloatVectorNode extends LLVMToVectorNode {

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doI1(LLVMI1Vector from) {
            assert from.getLength() == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) ? 1 : 0;
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doI8(LLVMI8Vector from) {
            assert from.getLength() == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doI16(LLVMI16Vector from) {
            assert from.getLength() == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doI32(LLVMI32Vector from) {
            assert from.getLength() == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doI64(LLVMI64Vector from) {
            assert from.getLength() == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doFloat(LLVMFloatVector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doDouble(LLVMDoubleVector from) {
            assert from.getLength() == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (float) from.getValue(i);
            }
            return LLVMFloatVector.create(vector);
        }
    }

    public abstract static class LLVMSignedCastToDoubleVectorNode extends LLVMToVectorNode {

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doI1(LLVMI1Vector from) {
            assert from.getLength() == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) ? 1 : 0;
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doI8(LLVMI8Vector from) {
            assert from.getLength() == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doI16(LLVMI16Vector from) {
            assert from.getLength() == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doI32(LLVMI32Vector from) {
            assert from.getLength() == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doI64(LLVMI64Vector from) {
            assert from.getLength() == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doFloat(LLVMFloatVector from) {
            assert from.getLength() == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        protected LLVMDoubleVector doDouble(LLVMDoubleVector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }
    }

    public abstract static class LLVMBitcastToI1VectorNode extends LLVMToVectorNode {

        @ExplodeLoop
        private LLVMI1Vector castFromLong(long from, int elem) {
            assert elem == getVectorLength();
            boolean[] vector = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (from & 1L << i) != 0;
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        protected LLVMI1Vector doI1(boolean from) {
            boolean[] vector = new boolean[]{from};
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        protected LLVMI1Vector doI8(byte from) {
            return castFromLong(from, Byte.SIZE);
        }

        @Specialization
        protected LLVMI1Vector doI16(short from) {
            return castFromLong(from, Short.SIZE);
        }

        @Specialization
        protected LLVMI1Vector doI32(int from) {
            return castFromLong(from, Integer.SIZE);
        }

        @Specialization
        protected LLVMI1Vector doI64(long from) {
            return castFromLong(from, Long.SIZE);
        }

        @Specialization
        protected LLVMI1Vector doFloat(float from) {
            return doI32(Float.floatToRawIntBits(from));
        }

        @Specialization
        protected LLVMI1Vector doDouble(double from) {
            return doI64(Double.doubleToRawLongBits(from));
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector do80BitFloat(LLVM80BitFloat from) {
            assert LLVM80BitFloat.BIT_WIDTH == getVectorLength();
            final byte[] vectorBytes = from.getBytes();
            final boolean[] vector = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength() / Byte.SIZE; i++) {
                for (int j = 0; j < Byte.SIZE; j++) {
                    vector[i * Byte.SIZE + j] = (vectorBytes[i] & (1 << j)) != 0;
                }
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        protected LLVMI1Vector doI1Vector(LLVMI1Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doI8Vector(LLVMI8Vector from) {
            assert from.getLength() * Byte.SIZE == getVectorLength();
            boolean[] vector = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength() / Byte.SIZE; i++) {
                for (int j = 0; j < Byte.SIZE; j++) {
                    vector[i * Byte.SIZE + j] = (from.getValue(i) & 1 << j) != 0;
                }
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doI16Vector(LLVMI16Vector from) {
            assert from.getLength() * Short.SIZE == getVectorLength();
            final boolean[] vector = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength() / Short.SIZE; i++) {
                for (int j = 0; j < Short.SIZE; j++) {
                    vector[i * Short.SIZE + j] = (from.getValue(i) & 1 << j) != 0;
                }
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doI32Vector(LLVMI32Vector from) {
            assert from.getLength() * Integer.SIZE == getVectorLength();
            final boolean[] vector = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength() / Integer.SIZE; i++) {
                for (int j = 0; j < Integer.SIZE; j++) {
                    vector[i * Integer.SIZE + j] = (from.getValue(i) & 1 << j) != 0;
                }
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doI64Vector(LLVMI64Vector from) {
            assert from.getLength() * Long.SIZE == getVectorLength();
            final boolean[] vector = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength() / Long.SIZE; i++) {
                for (int j = 0; j < Long.SIZE; j++) {
                    vector[i * Long.SIZE + j] = (from.getValue(i) & 1L << j) != 0L;
                }
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doDoubleVector(LLVMDoubleVector from) {
            assert from.getLength() * Long.SIZE == getVectorLength();
            final boolean[] vector = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength() / Long.SIZE; i++) {
                long value = Double.doubleToRawLongBits(from.getValue(i));
                for (int j = 0; j < Long.SIZE; j++) {
                    vector[i * Long.SIZE + j] = (value & 1L << j) != 0L;
                }
            }
            return LLVMI1Vector.create(vector);
        }
    }

    public abstract static class LLVMBitcastToI8VectorNode extends LLVMToVectorNode {

        @ExplodeLoop
        private LLVMI8Vector castFromLong(long from, int elem) {
            assert elem == getVectorLength();
            byte[] vector = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (byte) ((from >>> (i * Byte.SIZE)) & LLVMExpressionNode.I8_MASK);
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doI8(byte from) {
            byte[] vector = new byte[]{from};
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doI16(short from) {
            return castFromLong(from, Short.BYTES);
        }

        @Specialization
        protected LLVMI8Vector doI32(int from) {
            return castFromLong(from, Integer.BYTES);
        }

        @Specialization
        protected LLVMI8Vector doI64(long from) {
            return castFromLong(from, Long.BYTES);
        }

        @Specialization
        protected LLVMI8Vector doFloat(float from) {
            return doI32(Float.floatToRawIntBits(from));
        }

        @Specialization
        protected LLVMI8Vector doDouble(double from) {
            return doI64(Double.doubleToRawLongBits(from));
        }

        @Specialization
        protected LLVMI8Vector do80BitFloat(LLVM80BitFloat from) {
            byte[] vector = from.getBytes();
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doI1Vector(LLVMI1Vector from) {
            assert from.getLength() % Byte.SIZE == 0 : "invalid vector size";
            assert from.getLength() / Byte.SIZE == getVectorLength();
            byte[] vector = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                byte value = 0;
                for (int j = 0; j < Byte.SIZE; j++) {
                    value |= (from.getValue(i * Byte.SIZE + j) ? 1 : 0) << j;
                }
                vector[i] = value;
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doI8Vector(LLVMI8Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doI16Vector(LLVMI16Vector from) {
            assert from.getLength() * Short.BYTES == getVectorLength();
            final byte[] vector = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength() / Short.BYTES; i++) {
                for (int j = 0; j < Short.BYTES; j++) {
                    vector[i * Short.BYTES + j] = (byte) (((from.getValue(i) >>> (j * Byte.SIZE)) & LLVMExpressionNode.I8_MASK));
                }
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doI32Vector(LLVMI32Vector from) {
            assert from.getLength() * Integer.BYTES == getVectorLength();
            final byte[] vector = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength() / Integer.BYTES; i++) {
                for (int j = 0; j < Integer.BYTES; j++) {
                    vector[i * Integer.BYTES + j] = (byte) (((from.getValue(i) >>> (j * Byte.SIZE))) & LLVMExpressionNode.I8_MASK);
                }
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doI64Vector(LLVMI64Vector from) {
            assert from.getLength() * Long.BYTES == getVectorLength();
            final byte[] vector = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength() / Long.BYTES; i++) {
                for (int j = 0; j < Long.BYTES; j++) {
                    vector[i * Long.BYTES + j] = (byte) (((from.getValue(i) >>> (j * Byte.SIZE)) & LLVMExpressionNode.I8_MASK));
                }
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doDoubleVector(LLVMDoubleVector from) {
            assert from.getLength() * Long.BYTES == getVectorLength();
            final byte[] vector = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength() / Long.BYTES; i++) {
                long value = Double.doubleToRawLongBits(from.getValue(i));
                for (int j = 0; j < Long.BYTES; j++) {
                    vector[i * Long.BYTES + j] = (byte) (((value >>> (j * Byte.SIZE)) & LLVMExpressionNode.I8_MASK));
                }
            }
            return LLVMI8Vector.create(vector);
        }
    }

    public abstract static class LLVMBitcastToI16VectorNode extends LLVMToVectorNode {

        @ExplodeLoop
        private LLVMI16Vector castFromLong(long from, int elem) {
            assert elem == getVectorLength();
            short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (short) ((from >>> (i * Short.SIZE)) & LLVMExpressionNode.I16_MASK);
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doI16(short from) {
            short[] vector = new short[]{from};
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doI32(int from) {
            return castFromLong(from, SHORTS_PER_INT);
        }

        @Specialization
        protected LLVMI16Vector doI64(long from) {
            return castFromLong(from, SHORTS_PER_LONG);
        }

        @Specialization
        protected LLVMI16Vector doFloat(float from) {
            return doI32(Float.floatToRawIntBits(from));
        }

        @Specialization
        protected LLVMI16Vector doDouble(double from) {
            return doI64(Double.doubleToRawLongBits(from));
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector do80BitFloat(LLVM80BitFloat from) {
            assert LLVM80BitFloat.BIT_WIDTH / Short.SIZE == getVectorLength();
            final byte[] vectorBytes = from.getBytes();
            final short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (short) (Byte.toUnsignedInt(vectorBytes[i * 2]) | (Byte.toUnsignedInt(vectorBytes[i * 2 + 1]) << Byte.SIZE));
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI1Vector(LLVMI1Vector from) {
            assert from.getLength() % Short.SIZE == 0 : "invalid vector size";
            assert from.getLength() / Short.SIZE == getVectorLength();
            final short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                short value = 0;
                for (int j = 0; j < Short.SIZE; j++) {
                    value |= (from.getValue(i * Short.SIZE + j) ? 1 : 0) << j;
                }
                vector[i] = value;
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI8Vector(LLVMI8Vector from) {
            assert from.getLength() % (Short.BYTES) == 0 : "invalid vector size";
            assert from.getLength() / Short.BYTES == getVectorLength();
            final short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                short value = 0;
                for (int j = 0; j < Short.BYTES; j++) {
                    value |= (from.getValue(i * (Short.BYTES) + j) & LLVMExpressionNode.I8_MASK) << (j * Byte.SIZE);
                }
                vector[i] = value;
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doI16Vector(LLVMI16Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI32Vector(LLVMI32Vector from) {
            assert from.getLength() * SHORTS_PER_INT == getVectorLength();
            final short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength() / SHORTS_PER_INT; i++) {
                for (int j = 0; j < SHORTS_PER_INT; j++) {
                    vector[i * SHORTS_PER_INT + j] = (short) (((from.getValue(i) >>> (j * Short.SIZE)) & LLVMExpressionNode.I16_MASK));
                }
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI64Vector(LLVMI64Vector from) {
            assert from.getLength() * SHORTS_PER_LONG == getVectorLength();
            final short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength() / SHORTS_PER_LONG; i++) {
                for (int j = 0; j < SHORTS_PER_LONG; j++) {
                    vector[i * SHORTS_PER_LONG + j] = (short) (((from.getValue(i) >>> (j * Short.SIZE)) & LLVMExpressionNode.I16_MASK));
                }
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doDoubleVector(LLVMDoubleVector from) {
            assert from.getLength() * SHORTS_PER_LONG == getVectorLength();
            final short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength() / SHORTS_PER_LONG; i++) {
                long value = Double.doubleToRawLongBits(from.getValue(i));
                for (int j = 0; j < SHORTS_PER_LONG; j++) {
                    vector[i * SHORTS_PER_LONG + j] = (short) (((value >>> (j * Short.SIZE)) & LLVMExpressionNode.I16_MASK));
                }
            }
            return LLVMI16Vector.create(vector);
        }
    }

    public abstract static class LLVMBitcastToI32VectorNode extends LLVMToVectorNode {

        @Specialization
        protected LLVMI32Vector doI32(int from) {
            int[] vector = new int[]{from};
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doFloat(float from) {
            return doI32(Float.floatToRawIntBits(from));
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI64(long from) {
            assert INTS_PER_LONG == getVectorLength();
            int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (int) ((from >>> (i * Integer.SIZE)) & LLVMExpressionNode.I32_MASK);
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doDouble(double from) {
            return doI64(Double.doubleToRawLongBits(from));
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI1Vector(LLVMI1Vector from) {
            assert from.getLength() % Integer.SIZE == 0 : "invalid vector size";
            assert from.getLength() / Integer.SIZE == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                int value = 0;
                for (int j = 0; j < Integer.SIZE; j++) {
                    value |= (from.getValue(i * Integer.SIZE + j) ? 1 : 0) << j;
                }
                vector[i] = value;
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI8Vector(LLVMI8Vector from) {
            assert from.getLength() % Integer.BYTES == 0 : "invalid vector size";
            assert from.getLength() / Integer.BYTES == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                int value = 0;
                for (int j = 0; j < Integer.BYTES; j++) {
                    value |= (from.getValue(i * Integer.BYTES + j) & LLVMExpressionNode.I8_MASK) << (j * Byte.SIZE);
                }
                vector[i] = value;
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI16Vector(LLVMI16Vector from) {
            assert from.getLength() % SHORTS_PER_INT == 0 : "invalid vector size";
            assert from.getLength() / SHORTS_PER_INT == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                int value = 0;
                for (int j = 0; j < SHORTS_PER_INT; j++) {
                    value |= (from.getValue(i * SHORTS_PER_INT + j) & LLVMExpressionNode.I16_MASK) << (j * Short.SIZE);
                }
                vector[i] = value;
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doI32Vector(LLVMI32Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI64Vector(LLVMI64Vector from) {
            assert from.getLength() * INTS_PER_LONG == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength() / LLVMToVectorNode.INTS_PER_LONG; i++) {
                for (int j = 0; j < INTS_PER_LONG; j++) {
                    vector[i * INTS_PER_LONG + j] = (int) (((from.getValue(i) >>> (j * Integer.SIZE)) & LLVMExpressionNode.I32_MASK));
                }
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doFloatVector(LLVMFloatVector from) {
            assert from.getLength() == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                int value = Float.floatToRawIntBits(from.getValue(i));
                vector[i] = value;
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doDoubleVector(LLVMDoubleVector from) {
            assert from.getLength() * INTS_PER_LONG == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength() / LLVMToVectorNode.INTS_PER_LONG; i++) {
                long value = Double.doubleToRawLongBits(from.getValue(i));
                for (int j = 0; j < INTS_PER_LONG; j++) {
                    vector[i * INTS_PER_LONG + j] = (int) (((value >>> (j * Integer.SIZE)) & LLVMExpressionNode.I32_MASK));
                }
            }
            return LLVMI32Vector.create(vector);
        }
    }

    public abstract static class LLVMBitcastToI64VectorNode extends LLVMToVectorNode {

        @Specialization
        protected LLVMI64Vector doI64(long from) {
            long[] vector = new long[]{from};
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMI64Vector doDouble(double from) {
            return doI64(Double.doubleToRawLongBits(from));
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI1Vector(LLVMI1Vector from) {
            assert from.getLength() % Long.SIZE == 0 : "invalid vector size";
            assert from.getLength() / Long.SIZE == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                long value = 0;
                for (int j = 0; j < Long.SIZE; j++) {
                    value |= (from.getValue(i * Long.SIZE + j) ? 1L : 0L) << j;
                }
                vector[i] = value;
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI8Vector(LLVMI8Vector from) {
            assert from.getLength() % Long.BYTES == 0 : "invalid vector size";
            assert from.getLength() / Long.BYTES == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                long value = 0;
                for (int j = 0; j < Long.BYTES; j++) {
                    value |= (long) (from.getValue(i * Long.BYTES + j) & LLVMExpressionNode.I8_MASK) << (j * Byte.SIZE);
                }
                vector[i] = value;
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI16Vector(LLVMI16Vector from) {
            assert from.getLength() % SHORTS_PER_LONG == 0 : "invalid vector size";
            assert from.getLength() / SHORTS_PER_LONG == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                long value = 0;
                for (int j = 0; j < SHORTS_PER_LONG; j++) {
                    value |= (long) (from.getValue(i * SHORTS_PER_LONG + j) & LLVMExpressionNode.I16_MASK) << (j * Short.SIZE);
                }
                vector[i] = value;
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI32Vector(LLVMI32Vector from) {
            assert from.getLength() % INTS_PER_LONG == 0 : "invalid vector size";
            assert from.getLength() / INTS_PER_LONG == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                long value = 0;
                for (int j = 0; j < INTS_PER_LONG; j++) {
                    value |= (from.getValue(i * INTS_PER_LONG + j) & LLVMExpressionNode.I32_MASK) << (j * Integer.SIZE);
                }
                vector[i] = value;
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMI64Vector doI64Vector(LLVMI64Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doDoubleVector(LLVMDoubleVector from) {
            assert from.getLength() == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = Double.doubleToRawLongBits(from.getValue(i));
            }
            return LLVMI64Vector.create(vector);
        }
    }

    public abstract static class LLVMBitcastToPointerVectorNode extends LLVMToVectorNode {

        @Specialization
        @ExplodeLoop
        protected LLVMPointerVector doI64Vector(LLVMI64Vector from) {
            assert from.getLength() == getVectorLength();
            LLVMPointer[] vector = new LLVMPointer[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = LLVMNativePointer.create(from.getValue(i));
            }
            return LLVMPointerVector.create(vector);
        }
    }

    public abstract static class LLVMBitcastToFloatVectorNode extends LLVMToVectorNode {

        @Specialization
        protected LLVMFloatVector doInt(int from) {
            return doFloat(Float.intBitsToFloat(from));
        }

        @Specialization
        protected LLVMFloatVector doFloat(float from) {
            float[] vector = new float[]{from};
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doI1Vector(LLVMI1Vector from) {
            assert from.getLength() % Float.SIZE == 0 : "invalid vector size";
            assert from.getLength() / Float.SIZE == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                int value = 0;
                for (int j = 0; j < Float.SIZE; j++) {
                    value |= (from.getValue(i * Float.SIZE + j) ? 1L : 0L) << j;
                }
                vector[i] = Float.intBitsToFloat(value);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doI8Vector(LLVMI8Vector from) {
            assert from.getLength() % Float.BYTES == 0 : "invalid vector size";
            assert from.getLength() / Float.BYTES == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                int value = 0;
                for (int j = 0; j < Float.BYTES; j++) {
                    value |= (long) (from.getValue(i * (Float.BYTES) + j) & LLVMExpressionNode.I8_MASK) << (j * Byte.SIZE);
                }
                vector[i] = Float.intBitsToFloat(value);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doI16Vector(LLVMI16Vector from) {
            assert from.getLength() % SHORTS_PER_FLOAT == 0 : "invalid vector size";
            assert from.getLength() / SHORTS_PER_FLOAT == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                int value = 0;
                for (int j = 0; j < SHORTS_PER_FLOAT; j++) {
                    value |= (long) (from.getValue(i * (SHORTS_PER_FLOAT) + j) & LLVMExpressionNode.I16_MASK) << (j * Short.SIZE);
                }
                vector[i] = Float.intBitsToFloat(value);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doI32Vector(LLVMI32Vector from) {
            assert from.getLength() == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = Float.intBitsToFloat(from.getValue(i));
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doFloatVector(LLVMFloatVector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doI64Vector(LLVMI64Vector from) {
            assert from.getLength() * FLOATS_PER_LONG == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength() / FLOATS_PER_LONG; i++) {
                for (int j = 0; j < FLOATS_PER_LONG; j++) {
                    vector[i * FLOATS_PER_LONG + j] = Float.intBitsToFloat((int) (((from.getValue(i) >>> (j * Float.SIZE)) & LLVMExpressionNode.I32_MASK)));
                }
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doDoubleVector(LLVMDoubleVector from) {
            assert from.getLength() * FLOATS_PER_LONG == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength() / FLOATS_PER_LONG; i++) {
                long value = Double.doubleToRawLongBits(from.getValue(i));
                for (int j = 0; j < FLOATS_PER_LONG; j++) {
                    vector[i * FLOATS_PER_LONG + j] = Float.intBitsToFloat((int) (((value >>> (j * Float.SIZE)) & LLVMExpressionNode.I32_MASK)));
                }
            }
            return LLVMFloatVector.create(vector);
        }
    }

    public abstract static class LLVMBitcastToDoubleVectorNode extends LLVMToVectorNode {

        @Specialization
        protected LLVMDoubleVector doLong(long from) {
            return doDouble(Double.longBitsToDouble(from));
        }

        @Specialization
        protected LLVMDoubleVector doDouble(double from) {
            double[] vector = new double[]{from};
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doI1Vector(LLVMI1Vector from) {
            assert from.getLength() % Double.SIZE == 0 : "invalid vector size";
            assert from.getLength() / Double.SIZE == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                long value = 0;
                for (int j = 0; j < Double.SIZE; j++) {
                    value |= (from.getValue(i * Double.SIZE + j) ? 1L : 0L) << j;
                }
                vector[i] = Double.longBitsToDouble(value);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doI8Vector(LLVMI8Vector from) {
            assert from.getLength() % Double.BYTES == 0 : "invalid vector size";
            assert from.getLength() / Double.BYTES == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                long value = 0;
                for (int j = 0; j < Double.BYTES; j++) {
                    value |= (long) (from.getValue(i * Double.BYTES + j) & LLVMExpressionNode.I8_MASK) << (j * Byte.SIZE);
                }
                vector[i] = Double.longBitsToDouble(value);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doI16Vector(LLVMI16Vector from) {
            assert from.getLength() % SHORTS_PER_DOUBLE == 0 : "invalid vector size";
            assert from.getLength() / SHORTS_PER_DOUBLE == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                long value = 0;
                for (int j = 0; j < SHORTS_PER_DOUBLE; j++) {
                    value |= (long) (from.getValue(i * SHORTS_PER_DOUBLE + j) & LLVMExpressionNode.I16_MASK) << (j * Short.SIZE);
                }
                vector[i] = Double.longBitsToDouble(value);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doI32Vector(LLVMI32Vector from) {
            assert from.getLength() % INTS_PER_DOUBLE == 0 : "invalid vector size";
            assert from.getLength() / INTS_PER_DOUBLE == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                long value = 0;
                for (int j = 0; j < INTS_PER_DOUBLE; j++) {
                    value |= (from.getValue(i * INTS_PER_DOUBLE + j) & LLVMExpressionNode.I32_MASK) << (j * Integer.SIZE);
                }
                vector[i] = Double.longBitsToDouble(value);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doFloatVector(LLVMFloatVector from) {
            assert from.getLength() % FLOATS_PER_DOUBLE == 0 : "invalid vector size";
            assert from.getLength() / FLOATS_PER_DOUBLE == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                long value = 0;
                for (int j = 0; j < FLOATS_PER_DOUBLE; j++) {
                    value |= (Float.floatToIntBits(from.getValue(i * FLOATS_PER_DOUBLE + j)) & LLVMExpressionNode.I32_MASK) << (j * Integer.SIZE);
                }
                vector[i] = Double.longBitsToDouble(value);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doI64Vector(LLVMI64Vector from) {
            assert from.getLength() == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = Double.longBitsToDouble(from.getValue(i));
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        protected LLVMDoubleVector doDoubleVector(LLVMDoubleVector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }
    }
}
