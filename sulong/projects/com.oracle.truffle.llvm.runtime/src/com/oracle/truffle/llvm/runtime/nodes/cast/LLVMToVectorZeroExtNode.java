/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMToVectorZeroExtNode extends LLVMToVectorNode {

    public abstract static class LLVMUnsignedCastToI1VectorNode extends LLVMToVectorNode {

        @Specialization
        protected LLVMI1Vector doI1Vector(LLVMI1Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }
    }

    public abstract static class LLVMUnsignedCastToI8VectorNode extends LLVMToVectorNode {

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doI1Vector(LLVMI1Vector from) {
            assert from.getLength() == getVectorLength();
            final byte[] vector = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (byte) (from.getValue(i) ? 1 : 0);
            }
            return LLVMI8Vector.create(vector);
        }

        @Specialization
        protected LLVMI8Vector doI8Vector(LLVMI8Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }
    }

    public abstract static class LLVMUnsignedCastToI16VectorNode extends LLVMToVectorNode {

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI1Vector(LLVMI1Vector from) {
            assert from.getLength() == getVectorLength();
            final short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (short) (from.getValue(i) ? 1 : 0);
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI8Vector(LLVMI8Vector from) {
            assert from.getLength() == getVectorLength();
            final short[] vector = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = (short) (from.getValue(i) & LLVMExpressionNode.I8_MASK);
            }
            return LLVMI16Vector.create(vector);
        }

        @Specialization
        protected LLVMI16Vector doI16Vector(LLVMI16Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }
    }

    public abstract static class LLVMUnsignedCastToI32VectorNode extends LLVMToVectorNode {

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI1Vector(LLVMI1Vector from) {
            assert from.getLength() == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) ? 1 : 0;
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI8Vector(LLVMI8Vector from) {
            assert from.getLength() == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) & LLVMExpressionNode.I8_MASK;
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI16Vector(LLVMI16Vector from) {
            assert from.getLength() == getVectorLength();
            final int[] vector = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) & LLVMExpressionNode.I16_MASK;
            }
            return LLVMI32Vector.create(vector);
        }

        @Specialization
        protected LLVMI32Vector doI32Vector(LLVMI32Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }
    }

    public abstract static class LLVMUnsignedCastToI64VectorNode extends LLVMToVectorNode {

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI1Vector(LLVMI1Vector from) {
            assert from.getLength() == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) ? 1 : 0;
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI8Vector(LLVMI8Vector from) {
            assert from.getLength() == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) & LLVMExpressionNode.I8_MASK;
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI16Vector(LLVMI16Vector from) {
            assert from.getLength() == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) & LLVMExpressionNode.I16_MASK;
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI32Vector(LLVMI32Vector from) {
            assert from.getLength() == getVectorLength();
            final long[] vector = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) & LLVMExpressionNode.I32_MASK;
            }
            return LLVMI64Vector.create(vector);
        }

        @Specialization
        protected LLVMI64Vector doI64Vector(LLVMI64Vector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }
    }

    public abstract static class LLVMUnsignedCastToFloatVectorNode extends LLVMToVectorNode {

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
                vector[i] = from.getValue(i) & LLVMExpressionNode.I8_MASK;
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doI16(LLVMI16Vector from) {
            assert from.getLength() == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) & LLVMExpressionNode.I16_MASK;
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doI32(LLVMI32Vector from) {
            assert from.getLength() == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) & LLVMExpressionNode.I32_MASK;
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doI64(LLVMI64Vector from) {
            assert from.getLength() == getVectorLength();
            final float[] vector = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = LLVMToFloatNode.LLVMUnsignedCastToFloatNode.doI64(from.getValue(i));
            }
            return LLVMFloatVector.create(vector);
        }

        @Specialization
        protected LLVMFloatVector doFloat(LLVMFloatVector from) {
            assert from.getLength() == getVectorLength();
            return from;
        }
    }

    public abstract static class LLVMUnsignedCastToDoubleVectorNode extends LLVMToVectorNode {

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
                vector[i] = from.getValue(i) & LLVMExpressionNode.I8_MASK;
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doI16(LLVMI16Vector from) {
            assert from.getLength() == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) & LLVMExpressionNode.I16_MASK;
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doI32(LLVMI32Vector from) {
            assert from.getLength() == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = from.getValue(i) & LLVMExpressionNode.I32_MASK;
            }
            return LLVMDoubleVector.create(vector);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doI64(LLVMI64Vector from) {
            assert from.getLength() == getVectorLength();
            final double[] vector = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                vector[i] = LLVMToDoubleNode.LLVMUnsignedCastToDoubleNode.doI64(from.getValue(i));
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

}
