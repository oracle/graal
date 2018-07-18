/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.cast;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToVectorNode extends LLVMExpressionNode {

    public abstract static class LLVMSignedCastToI1VectorNode extends LLVMToVectorNode {

        @Specialization
        protected LLVMI1Vector doI1(LLVMI1Vector from) {
            final boolean[] vector = new boolean[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        protected LLVMI1Vector doI8(LLVMI8Vector from) {
            final boolean[] vector = new boolean[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (from.getValue(i) & 1) != 0;
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        protected LLVMI1Vector doI16(LLVMI16Vector from) {
            final boolean[] vector = new boolean[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (from.getValue(i) & 1) != 0;
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        protected LLVMI1Vector doI32(LLVMI32Vector from) {
            final boolean[] vector = new boolean[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (from.getValue(i) & 1) != 0;
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        protected LLVMI1Vector doI64(LLVMI64Vector from) {
            final boolean[] vector = new boolean[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (from.getValue(i) & 1) != 0;
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        protected LLVMI1Vector doFloat(LLVMFloatVector from) {
            final boolean[] vector = new boolean[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i) != 0;
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        protected LLVMI1Vector doDouble(LLVMDoubleVector from) {
            final boolean[] vector = new boolean[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i) != 0;
            }
            return LLVMI1Vector.create(vector);
        }
    }

    public abstract static class LLVMSignedCastToI8VectorNode extends LLVMToVectorNode {

        @Specialization
        protected LLVMI8Vector doI1(LLVMI1Vector from) {
            final byte[] vector = new byte[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (byte) (from.getValue(i) ? 1 : 0);
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doI8(LLVMI8Vector from) {
            final byte[] vector = new byte[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doI16(LLVMI16Vector from) {
            final byte[] vector = new byte[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (byte) from.getValue(i);
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doI32(LLVMI32Vector from) {
            final byte[] vector = new byte[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (byte) from.getValue(i);
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doI64(LLVMI64Vector from) {
            final byte[] vector = new byte[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (byte) from.getValue(i);
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doFloat(LLVMFloatVector from) {
            final byte[] vector = new byte[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (byte) from.getValue(i);
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doDouble(LLVMDoubleVector from) {
            final byte[] vector = new byte[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (byte) from.getValue(i);
            }
            return LLVMI8Vector.create(vector);
        }
    }

    public abstract static class LLVMSignedCastToI16VectorNode extends LLVMToVectorNode {

        @Specialization
        protected LLVMI16Vector doI1(LLVMI1Vector from) {
            final short[] vector = new short[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (short) (from.getValue(i) ? 1 : 0);
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doI8(LLVMI8Vector from) {
            final short[] vector = new short[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doI16(LLVMI16Vector from) {
            final short[] vector = new short[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doI32(LLVMI32Vector from) {
            final short[] vector = new short[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (short) from.getValue(i);
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doI64(LLVMI64Vector from) {
            final short[] vector = new short[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (short) from.getValue(i);
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doFloat(LLVMFloatVector from) {
            final short[] vector = new short[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (short) from.getValue(i);
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doDouble(LLVMDoubleVector from) {
            final short[] vector = new short[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (short) from.getValue(i);
            }
            return LLVMI16Vector.create(vector);
        }
    }

    public abstract static class LLVMSignedCastToI32VectorNode extends LLVMToVectorNode {

        @Specialization
        protected LLVMI32Vector doI1(LLVMI1Vector from) {
            final int[] vector = new int[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i) ? 1 : 0;
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doI8(LLVMI8Vector from) {
            final int[] vector = new int[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doI16(LLVMI16Vector from) {
            final int[] vector = new int[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doI32(LLVMI32Vector from) {
            final int[] vector = new int[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doI64(LLVMI64Vector from) {
            final int[] vector = new int[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (int) from.getValue(i);
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doFloat(LLVMFloatVector from) {
            final int[] vector = new int[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (int) from.getValue(i);
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doDouble(LLVMDoubleVector from) {
            final int[] vector = new int[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (int) from.getValue(i);
            }
            return LLVMI32Vector.create(vector);
        }
    }

    public abstract static class LLVMSignedCastToI64VectorNode extends LLVMToVectorNode {

        @Specialization
        protected LLVMI64Vector doI1(LLVMI1Vector from) {
            final long[] vector = new long[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i) ? 1 : 0;
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMI64Vector doI8(LLVMI8Vector from) {
            final long[] vector = new long[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMI64Vector doI16(LLVMI16Vector from) {
            final long[] vector = new long[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMI64Vector doI32(LLVMI32Vector from) {
            final long[] vector = new long[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMI64Vector doI64(LLVMI64Vector from) {
            final long[] vector = new long[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMI64Vector doFloat(LLVMFloatVector from) {
            final long[] vector = new long[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (long) from.getValue(i);
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMI64Vector doDouble(LLVMDoubleVector from) {
            final long[] vector = new long[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (long) from.getValue(i);
            }
            return LLVMI64Vector.create(vector);
        }
    }

    public abstract static class LLVMSignedCastToFloatVectorNode extends LLVMToVectorNode {

        @Specialization
        protected LLVMFloatVector doI1(LLVMI1Vector from) {
            final float[] vector = new float[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i) ? 1 : 0;
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doI8(LLVMI8Vector from) {
            final float[] vector = new float[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doI16(LLVMI16Vector from) {
            final float[] vector = new float[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doI32(LLVMI32Vector from) {
            final float[] vector = new float[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doI64(LLVMI64Vector from) {
            final float[] vector = new float[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doFloat(LLVMFloatVector from) {
            final float[] vector = new float[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doDouble(LLVMDoubleVector from) {
            final float[] vector = new float[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = (float) from.getValue(i);
            }
            return LLVMFloatVector.create(vector);
        }
    }

    public abstract static class LLVMSignedCastToDoubleVectorNode extends LLVMToVectorNode {

        @Specialization
        protected LLVMDoubleVector doI1(LLVMI1Vector from) {
            final double[] vector = new double[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i) ? 1 : 0;
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        protected LLVMDoubleVector doI8(LLVMI8Vector from) {
            final double[] vector = new double[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        protected LLVMDoubleVector doI16(LLVMI16Vector from) {
            final double[] vector = new double[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        protected LLVMDoubleVector doI32(LLVMI32Vector from) {
            final double[] vector = new double[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        protected LLVMDoubleVector doI64(LLVMI64Vector from) {
            final double[] vector = new double[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        protected LLVMDoubleVector doFloat(LLVMFloatVector from) {
            final double[] vector = new double[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        protected LLVMDoubleVector doDouble(LLVMDoubleVector from) {
            final double[] vector = new double[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = from.getValue(i);
            }
            return LLVMDoubleVector.create(vector);
        }
    }

    public abstract static class LLVMBitcastToI1VectorNode extends LLVMToVectorNode {

        private static LLVMI1Vector castFromLong(long from, int elem) {
            boolean[] vector = new boolean[elem];
            for (int i = 0; i < elem; i++) {
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
        protected LLVMI1Vector do80BitFloat(LLVM80BitFloat from) {
            final byte[] vectorBytes = from.getBytes();
            final boolean[] vector = new boolean[LLVM80BitFloat.BIT_WIDTH];

            for (int i = 0; i < LLVM80BitFloat.BYTE_WIDTH; i++) {
                for (int j = 0; j < Byte.SIZE; j++) {
                    vector[i * Byte.SIZE + j] = (vectorBytes[i] & (1 << j)) != 0;
                }
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        protected LLVMI1Vector doI1Vector(LLVMI1Vector from) {
            final boolean[] vector = new boolean[from.getLength()];
            System.arraycopy(from.getValues(), 0, vector, 0, vector.length);
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        protected LLVMI1Vector doI8Vector(LLVMI8Vector from) {
            final boolean[] vector = new boolean[from.getLength() * Byte.SIZE];
            for (int i = 0; i < from.getLength(); i++) {
                for (int j = 0; j < Byte.SIZE; j++) {
                    vector[i * Byte.SIZE + j] = (from.getValue(i) & 1 << j) != 0 ? true : false;
                }
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        protected LLVMI1Vector doI16Vector(LLVMI16Vector from) {
            final boolean[] vector = new boolean[from.getLength() * Short.SIZE];
            for (int i = 0; i < from.getLength(); i++) {
                for (int j = 0; j < Short.SIZE; j++) {
                    vector[i * Short.SIZE + j] = (from.getValue(i) & 1 << j) != 0 ? true : false;
                }
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        protected LLVMI1Vector doI32Vector(LLVMI32Vector from) {
            final boolean[] vector = new boolean[from.getLength() * Integer.SIZE];
            for (int i = 0; i < from.getLength(); i++) {
                for (int j = 0; j < Integer.SIZE; j++) {
                    vector[i * Integer.SIZE + j] = (from.getValue(i) & 1 << j) != 0 ? true : false;
                }
            }
            return LLVMI1Vector.create(vector);
        }

        @Specialization
        protected LLVMI1Vector doI64Vector(LLVMI64Vector from) {
            final boolean[] vector = new boolean[from.getLength() * Long.SIZE];
            for (int i = 0; i < from.getLength(); i++) {
                for (int j = 0; j < Long.SIZE; j++) {
                    vector[i * Long.SIZE + j] = (from.getValue(i) & 1L << j) != 0L ? true : false;
                }
            }
            return LLVMI1Vector.create(vector);
        }
    }

    public abstract static class LLVMBitcastToI8VectorNode extends LLVMToVectorNode {

        private static LLVMI8Vector castFromLong(long from, int elem) {
            byte[] vector = new byte[elem];
            for (int i = 0; i < elem; i++) {
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
            return castFromLong(from, Short.SIZE / Byte.SIZE);
        }

        @Specialization
        protected LLVMI8Vector doI32(int from) {
            return castFromLong(from, Integer.SIZE / Byte.SIZE);
        }

        @Specialization
        protected LLVMI8Vector doI64(long from) {
            return castFromLong(from, Long.SIZE / Byte.SIZE);
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
            final byte[] vector = from.getBytes();
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doI1Vector(LLVMI1Vector from) {
            if (from.getLength() % Byte.SIZE != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final byte[] vector = new byte[from.getLength() / Byte.SIZE];
            for (int i = 0; i < from.getLength() / Byte.SIZE; i++) {
                vector[i] = 0;
                for (int j = 0; j < Byte.SIZE; j++) {
                    vector[i] |= (from.getValue(i * Byte.SIZE + j) ? 1 : 0) << j;
                }
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doI8Vector(LLVMI8Vector from) {
            final byte[] vector = new byte[from.getLength()];
            System.arraycopy(from.getValues(), 0, vector, 0, vector.length);
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doI16Vector(LLVMI16Vector from) {
            final byte[] vector = new byte[from.getLength() * (Short.SIZE / Byte.SIZE)];
            for (int i = 0; i < from.getLength(); i++) {
                for (int j = 0; j < Short.SIZE / Byte.SIZE; j++) {
                    vector[i * (Short.SIZE / Byte.SIZE) + j] = (byte) (((from.getValue(i) >>> (j * Byte.SIZE)) & LLVMExpressionNode.I8_MASK));
                }
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doI32Vector(LLVMI32Vector from) {
            final byte[] vector = new byte[from.getLength() * (Integer.SIZE / Byte.SIZE)];
            for (int i = 0; i < from.getLength(); i++) {
                for (int j = 0; j < Integer.SIZE / Byte.SIZE; j++) {
                    vector[i * (Integer.SIZE / Byte.SIZE) + j] = (byte) (((from.getValue(i) >>> (j * Byte.SIZE))) & LLVMExpressionNode.I8_MASK);
                }
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doI64Vector(LLVMI64Vector from) {
            final byte[] vector = new byte[from.getLength() * (Long.SIZE / Byte.SIZE)];
            for (int i = 0; i < from.getLength(); i++) {
                for (int j = 0; j < Long.SIZE / Byte.SIZE; j++) {
                    vector[i * (Long.SIZE / Byte.SIZE) + j] = (byte) (((from.getValue(i) >>> (j * Byte.SIZE)) & LLVMExpressionNode.I8_MASK));
                }
            }
            return LLVMI8Vector.create(vector);
        }
    }

    public abstract static class LLVMBitcastToI16VectorNode extends LLVMToVectorNode {

        private static LLVMI16Vector castFromLong(long from, int elem) {
            short[] vector = new short[elem];
            for (int i = 0; i < elem; i++) {
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
            return castFromLong(from, Integer.SIZE / Short.SIZE);
        }

        @Specialization
        protected LLVMI16Vector doI64(long from) {
            return castFromLong(from, Long.SIZE / Short.SIZE);
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
        protected LLVMI16Vector do80BitFloat(LLVM80BitFloat from) {
            final byte[] vectorBytes = from.getBytes();
            final short[] vector = new short[LLVM80BitFloat.BIT_WIDTH / Short.SIZE];

            for (int i = 0; i < (LLVM80BitFloat.BIT_WIDTH / Short.SIZE); i++) {
                vector[i] = (short) (Byte.toUnsignedInt(vectorBytes[i * 2]) | (Byte.toUnsignedInt(vectorBytes[i * 2 + 1]) << Byte.SIZE));
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doI1Vector(LLVMI1Vector from) {
            if (from.getLength() % Short.SIZE != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final short[] vector = new short[from.getLength() / Short.SIZE];
            for (int i = 0; i < from.getLength() / Short.SIZE; i++) {
                vector[i] = 0;
                for (int j = 0; j < Short.SIZE; j++) {
                    vector[i] |= (from.getValue(i * Short.SIZE + j) ? 1 : 0) << j;
                }
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doI8Vector(LLVMI8Vector from) {
            if (from.getLength() % (Short.SIZE / Byte.SIZE) != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final short[] vector = new short[from.getLength() / (Short.SIZE / Byte.SIZE)];
            for (int i = 0; i < from.getLength() / (Short.SIZE / Byte.SIZE); i++) {
                vector[i] = 0;
                for (int j = 0; j < Short.SIZE / Byte.SIZE; j++) {
                    vector[i] |= (from.getValue(i * (Short.SIZE / Byte.SIZE) + j) & LLVMExpressionNode.I8_MASK) << (j * Byte.SIZE);
                }
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doI16Vector(LLVMI16Vector from) {
            final short[] vector = new short[from.getLength()];
            System.arraycopy(from.getValues(), 0, vector, 0, vector.length);
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doI32Vector(LLVMI32Vector from) {
            final short[] vector = new short[from.getLength() * (Integer.SIZE / Short.SIZE)];
            for (int i = 0; i < from.getLength(); i++) {
                for (int j = 0; j < Integer.SIZE / Short.SIZE; j++) {
                    vector[i * (Integer.SIZE / Short.SIZE) + j] = (short) (((from.getValue(i) >>> (j * Short.SIZE)) & LLVMExpressionNode.I16_MASK));
                }
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doI64Vector(LLVMI64Vector from) {
            final short[] vector = new short[from.getLength() * (Long.SIZE / Short.SIZE)];
            for (int i = 0; i < from.getLength(); i++) {
                for (int j = 0; j < Long.SIZE / Short.SIZE; j++) {
                    vector[i * (Long.SIZE / Short.SIZE) + j] = (short) (((from.getValue(i) >>> (j * Short.SIZE)) & LLVMExpressionNode.I16_MASK));
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
        protected LLVMI32Vector doI64(long from) {
            int[] vector = new int[Long.SIZE / Integer.SIZE];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (int) ((from >>> (i * Integer.SIZE)) & LLVMExpressionNode.I32_MASK);
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doDouble(double from) {
            return doI64(Double.doubleToRawLongBits(from));
        }

        @Specialization
        protected LLVMI32Vector doI1Vector(LLVMI1Vector from) {
            if (from.getLength() % Integer.SIZE != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final int[] vector = new int[from.getLength() / Integer.SIZE];
            for (int i = 0; i < from.getLength() / Integer.SIZE; i++) {
                vector[i] = 0;
                for (int j = 0; j < Integer.SIZE; j++) {
                    vector[i] |= (from.getValue(i * Integer.SIZE + j) ? 1 : 0) << j;
                }
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doI8Vector(LLVMI8Vector from) {
            if (from.getLength() % (Integer.SIZE / Byte.SIZE) != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final int[] vector = new int[from.getLength() / (Integer.SIZE / Byte.SIZE)];
            for (int i = 0; i < from.getLength() / (Integer.SIZE / Byte.SIZE); i++) {
                vector[i] = 0;
                for (int j = 0; j < Integer.SIZE / Byte.SIZE; j++) {
                    vector[i] |= (from.getValue(i * (Integer.SIZE / Byte.SIZE) + j) & LLVMExpressionNode.I8_MASK) << (j * Byte.SIZE);
                }
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doI16Vector(LLVMI16Vector from) {
            if (from.getLength() % (Integer.SIZE / Short.SIZE) != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final int[] vector = new int[from.getLength() / (Integer.SIZE / Short.SIZE)];
            for (int i = 0; i < from.getLength() / (Integer.SIZE / Short.SIZE); i++) {
                vector[i] = 0;
                for (int j = 0; j < Integer.SIZE / Short.SIZE; j++) {
                    vector[i] |= (from.getValue(i * (Integer.SIZE / Short.SIZE) + j) & LLVMExpressionNode.I16_MASK) << (j * Short.SIZE);
                }
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doI32Vector(LLVMI32Vector from) {
            final int[] vector = new int[from.getLength()];
            System.arraycopy(from.getValues(), 0, vector, 0, vector.length);
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doI64Vector(LLVMI64Vector from) {
            final int[] vector = new int[from.getLength() * (Long.SIZE / Integer.SIZE)];
            for (int i = 0; i < from.getLength(); i++) {
                for (int j = 0; j < Long.SIZE / Integer.SIZE; j++) {
                    vector[i * (Long.SIZE / Integer.SIZE) + j] = (int) (((from.getValue(i) >>> (j * Integer.SIZE)) & LLVMExpressionNode.I32_MASK));
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
        protected LLVMI64Vector doI1Vector(LLVMI1Vector from) {
            if (from.getLength() % Long.SIZE != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final long[] vector = new long[from.getLength() / Long.SIZE];
            for (int i = 0; i < from.getLength() / Long.SIZE; i++) {
                vector[i] = 0;
                for (int j = 0; j < Long.SIZE; j++) {
                    vector[i] |= (from.getValue(i * Long.SIZE + j) ? 1L : 0L) << j;
                }
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMI64Vector doI8Vector(LLVMI8Vector from) {
            if (from.getLength() % (Long.SIZE / Byte.SIZE) != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final long[] vector = new long[from.getLength() / (Long.SIZE / Byte.SIZE)];
            for (int i = 0; i < from.getLength() / (Long.SIZE / Byte.SIZE); i++) {
                vector[i] = 0;
                for (int j = 0; j < Long.SIZE / Byte.SIZE; j++) {
                    vector[i] |= (long) (from.getValue(i * (Long.SIZE / Byte.SIZE) + j) & LLVMExpressionNode.I8_MASK) << (j * Byte.SIZE);
                }
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMI64Vector doI16Vector(LLVMI16Vector from) {
            if (from.getLength() % (Long.SIZE / Short.SIZE) != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final long[] vector = new long[from.getLength() / (Long.SIZE / Short.SIZE)];
            for (int i = 0; i < from.getLength() / (Long.SIZE / Short.SIZE); i++) {
                vector[i] = 0;
                for (int j = 0; j < Long.SIZE / Short.SIZE; j++) {
                    vector[i] |= (long) (from.getValue(i * (Long.SIZE / Short.SIZE) + j) & LLVMExpressionNode.I16_MASK) << (j * Short.SIZE);
                }
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMI64Vector doI32Vector(LLVMI32Vector from) {
            if (from.getLength() % (Long.SIZE / Integer.SIZE) != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final long[] vector = new long[from.getLength() / (Long.SIZE / Integer.SIZE)];
            for (int i = 0; i < from.getLength() / (Long.SIZE / Integer.SIZE); i++) {
                vector[i] = 0;
                for (int j = 0; j < Long.SIZE / Integer.SIZE; j++) {
                    vector[i] |= (from.getValue(i * (Long.SIZE / Integer.SIZE) + j) & LLVMExpressionNode.I32_MASK) << (j * Integer.SIZE);
                }
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMI64Vector doI64Vector(LLVMI64Vector from) {
            final long[] vector = new long[from.getLength()];
            System.arraycopy(from.getValues(), 0, vector, 0, vector.length);
            return LLVMI64Vector.create(vector);
        }
    }

    public abstract static class LLVMBitcastToFloatVectorNode extends LLVMToVectorNode {

        @Specialization
        protected LLVMFloatVector doLong(int from) {
            return doFloat(Float.intBitsToFloat(from));
        }

        @Specialization
        protected LLVMFloatVector doFloat(float from) {
            float[] vector = new float[]{from};
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doI1Vector(LLVMI1Vector from) {
            if (from.getLength() % Float.SIZE != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final float[] vector = new float[from.getLength() / Float.SIZE];
            for (int i = 0; i < from.getLength() / Float.SIZE; i++) {
                int vectorI = 0;
                for (int j = 0; j < Float.SIZE; j++) {
                    vectorI |= (from.getValue(i * Float.SIZE + j) ? 1L : 0L) << j;
                }
                vector[i] = Float.intBitsToFloat(vectorI);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doI8Vector(LLVMI8Vector from) {
            if (from.getLength() % (Float.SIZE / Byte.SIZE) != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final float[] vector = new float[from.getLength() / (Float.SIZE / Byte.SIZE)];
            for (int i = 0; i < from.getLength() / (Float.SIZE / Byte.SIZE); i++) {
                int vectorI = 0;
                for (int j = 0; j < Float.SIZE / Byte.SIZE; j++) {
                    vectorI |= (long) (from.getValue(i * (Float.SIZE / Byte.SIZE) + j) & LLVMExpressionNode.I8_MASK) << (j * Byte.SIZE);
                }
                vector[i] = Float.intBitsToFloat(vectorI);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doI16Vector(LLVMI16Vector from) {
            if (from.getLength() % (Float.SIZE / Short.SIZE) != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final float[] vector = new float[from.getLength() / (Float.SIZE / Short.SIZE)];
            for (int i = 0; i < from.getLength() / (Float.SIZE / Short.SIZE); i++) {
                int vectorI = 0;
                for (int j = 0; j < Float.SIZE / Short.SIZE; j++) {
                    vectorI |= (long) (from.getValue(i * (Float.SIZE / Short.SIZE) + j) & LLVMExpressionNode.I16_MASK) << (j * Short.SIZE);
                }
                vector[i] = Float.intBitsToFloat(vectorI);
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doI32Vector(LLVMI32Vector from) {
            final float[] vector = new float[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = Float.intBitsToFloat(from.getValue(i));
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doFloatVector(LLVMFloatVector from) {
            final float[] vector = new float[from.getLength()];
            System.arraycopy(from.getValues(), 0, vector, 0, vector.length);
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doI64Vector(LLVMI64Vector from) {
            final float[] vector = new float[from.getLength() * (Long.SIZE / Float.SIZE)];
            for (int i = 0; i < from.getLength(); i++) {
                for (int j = 0; j < Long.SIZE / Float.SIZE; j++) {
                    vector[i * (Long.SIZE / Float.SIZE) + j] = (int) (((from.getValue(i) >>> (j * Float.SIZE)) & LLVMExpressionNode.I32_MASK));
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
        protected LLVMDoubleVector doI1Vector(LLVMI1Vector from) {
            if (from.getLength() % Double.SIZE != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final double[] vector = new double[from.getLength() / Double.SIZE];
            for (int i = 0; i < from.getLength() / Double.SIZE; i++) {
                long vectorI = 0;
                for (int j = 0; j < Double.SIZE; j++) {
                    vectorI |= (from.getValue(i * Double.SIZE + j) ? 1L : 0L) << j;
                }
                vector[i] = Double.longBitsToDouble(vectorI);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        protected LLVMDoubleVector doI8Vector(LLVMI8Vector from) {
            if (from.getLength() % (Double.SIZE / Byte.SIZE) != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final double[] vector = new double[from.getLength() / (Double.SIZE / Byte.SIZE)];
            for (int i = 0; i < from.getLength() / (Double.SIZE / Byte.SIZE); i++) {
                long vectorI = 0;
                for (int j = 0; j < Double.SIZE / Byte.SIZE; j++) {
                    vectorI |= (long) (from.getValue(i * (Double.SIZE / Byte.SIZE) + j) & LLVMExpressionNode.I8_MASK) << (j * Byte.SIZE);
                }
                vector[i] = Double.longBitsToDouble(vectorI);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        protected LLVMDoubleVector doI16Vector(LLVMI16Vector from) {
            if (from.getLength() % (Double.SIZE / Short.SIZE) != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final double[] vector = new double[from.getLength() / (Double.SIZE / Short.SIZE)];
            for (int i = 0; i < from.getLength() / (Double.SIZE / Short.SIZE); i++) {
                long vectorI = 0;
                for (int j = 0; j < Double.SIZE / Short.SIZE; j++) {
                    vectorI |= (long) (from.getValue(i * (Double.SIZE / Short.SIZE) + j) & LLVMExpressionNode.I16_MASK) << (j * Short.SIZE);
                }
                vector[i] = Double.longBitsToDouble(vectorI);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        protected LLVMDoubleVector doI32Vector(LLVMI32Vector from) {
            if (from.getLength() % (Double.SIZE / Integer.SIZE) != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("invalid vector size!");
            }
            final double[] vector = new double[from.getLength() / (Double.SIZE / Integer.SIZE)];
            for (int i = 0; i < from.getLength() / (Double.SIZE / Integer.SIZE); i++) {
                long vectorI = 0;
                for (int j = 0; j < Double.SIZE / Integer.SIZE; j++) {
                    vectorI |= (from.getValue(i * (Double.SIZE / Integer.SIZE) + j) & LLVMExpressionNode.I32_MASK) << (j * Integer.SIZE);
                }
                vector[i] = Double.longBitsToDouble(vectorI);
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        protected LLVMDoubleVector doI64Vector(LLVMI64Vector from) {
            final double[] vector = new double[from.getLength()];
            for (int i = 0; i < from.getLength(); i++) {
                vector[i] = Double.longBitsToDouble(from.getValue(i));
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        protected LLVMDoubleVector doDoubleVector(LLVMDoubleVector from) {
            final double[] vector = new double[from.getLength()];
            System.arraycopy(from.getValues(), 0, vector, 0, vector.length);
            return LLVMDoubleVector.create(vector);
        }
    }
}
