/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMFloatLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMDoubleStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMFloatStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

public abstract class LLVMAdditionalIntrinsics {

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMSetRoundingNode extends LLVMBuiltin {

        @Specialization
        protected Object doSet(int roundingMode) {
            getLanguage().setRoundingMode(roundingMode);
            return null;
        }
    }

    public abstract static class LLVMGetRoundingNode extends LLVMBuiltin {

        @Specialization
        protected int doGet() {
            return getLanguage().getRoundingMode();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "signed", type = boolean.class)
    public abstract static class LLVMIntegerCompareNode extends LLVMBuiltin {

        protected abstract boolean isSigned();

        @Specialization
        protected int doI32(int left, int right) {
            return isSigned() ? Integer.compare(left, right) : Integer.compareUnsigned(left, right);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMCountTrailingElementsNode extends LLVMBuiltin {

        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected long doI1(LLVMI1Vector value, @SuppressWarnings("unused") boolean zeroIsPoison) {
            assert value.getLength() == getVectorLength();
            for (int i = 0; i < getVectorLength(); i++) {
                if (value.getValue(i)) {
                    return i;
                }
            }
            return getVectorLength();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    @NodeField(name = "bitWidth", type = int.class)
    public abstract static class LLVMSaturatingFptoSINode extends LLVMBuiltin {

        protected abstract int getVectorLength();

        protected abstract int getBitWidth();

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doDouble(LLVMDoubleVector value) {
            assert value.getLength() == getVectorLength();
            assert getBitWidth() > 1 && getBitWidth() <= Short.SIZE;
            int min = -(1 << (getBitWidth() - 1));
            int max = (1 << (getBitWidth() - 1)) - 1;
            short[] result = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                double element = value.getValue(i);
                if (Double.isNaN(element)) {
                    result[i] = 0;
                } else if (element <= min) {
                    result[i] = (short) min;
                } else if (element >= max) {
                    result[i] = (short) max;
                } else {
                    result[i] = (short) element;
                }
            }
            return LLVMI16Vector.create(result);
        }
    }

    /*
     * llvm.masked.load/store on a contiguous base pointer (as opposed to
     * gather/scatter's pointer vector). clang emits the v4f64 forms for Eigen's
     * AVX2 LLT/triangular-solve tails; masked-off lanes must not be accessed.
     */
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMMaskedLoadF64Node extends LLVMBuiltin {

        @Child private LLVMDoubleLoadNode load = LLVMDoubleLoadNode.create();

        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doLoad(LLVMPointer pointer, LLVMI1Vector mask, LLVMDoubleVector passthrough) {
            assert mask.getLength() == getVectorLength();
            assert passthrough.getLength() == getVectorLength();
            double[] result = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = mask.getValue(i) ? load.executeWithTarget(pointer.increment(i * Double.BYTES)) : passthrough.getValue(i);
            }
            return LLVMDoubleVector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMMaskedStoreF64Node extends LLVMBuiltin {

        @Child private LLVMDoubleStoreNode store = LLVMDoubleStoreNode.create();

        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected Object doStore(LLVMDoubleVector values, LLVMPointer pointer, LLVMI1Vector mask) {
            assert values.getLength() == getVectorLength();
            assert mask.getLength() == getVectorLength();
            for (int i = 0; i < getVectorLength(); i++) {
                if (mask.getValue(i)) {
                    store.executeWithTarget(pointer.increment(i * Double.BYTES), values.getValue(i));
                }
            }
            return null;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMMaskedLoadF32Node extends LLVMBuiltin {

        @Child private LLVMFloatLoadNode load = LLVMFloatLoadNode.create();

        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doLoad(LLVMPointer pointer, LLVMI1Vector mask, LLVMFloatVector passthrough) {
            assert mask.getLength() == getVectorLength();
            assert passthrough.getLength() == getVectorLength();
            float[] result = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = mask.getValue(i) ? load.executeWithTarget(pointer.increment(i * Float.BYTES)) : passthrough.getValue(i);
            }
            return LLVMFloatVector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMMaskedStoreF32Node extends LLVMBuiltin {

        @Child private LLVMFloatStoreNode store = LLVMFloatStoreNode.create();

        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected Object doStore(LLVMFloatVector values, LLVMPointer pointer, LLVMI1Vector mask) {
            assert values.getLength() == getVectorLength();
            assert mask.getLength() == getVectorLength();
            for (int i = 0; i < getVectorLength(); i++) {
                if (mask.getValue(i)) {
                    store.executeWithTarget(pointer.increment(i * Float.BYTES), values.getValue(i));
                }
            }
            return null;
        }
    }

    /*
     * Integer masked load/store (llvm.masked.{load,store}.v<N>i32.p0). clang's loop/SLP
     * vectorizer emits these for the auto-vectorized integer kernels in Eigen's AMD ordering
     * (Amd.h) and Ceres' cubic interpolation / FixedArray; masked-off lanes must not be touched.
     */
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMMaskedLoadI32Node extends LLVMBuiltin {

        @Child private LLVMI32LoadNode load = LLVMI32LoadNode.create();

        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doLoad(LLVMPointer pointer, LLVMI1Vector mask, LLVMI32Vector passthrough) {
            assert mask.getLength() == getVectorLength();
            assert passthrough.getLength() == getVectorLength();
            int[] result = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = mask.getValue(i) ? load.executeWithTarget(pointer.increment(i * Integer.BYTES)) : passthrough.getValue(i);
            }
            return LLVMI32Vector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMMaskedStoreI32Node extends LLVMBuiltin {

        @Child private LLVMI32StoreNode store = LLVMI32StoreNodeGen.create(null, null);

        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected Object doStore(LLVMI32Vector values, LLVMPointer pointer, LLVMI1Vector mask) {
            assert values.getLength() == getVectorLength();
            assert mask.getLength() == getVectorLength();
            for (int i = 0; i < getVectorLength(); i++) {
                if (mask.getValue(i)) {
                    store.executeWithTarget(pointer.increment(i * Integer.BYTES), values.getValue(i));
                }
            }
            return null;
        }
    }

    /*
     * llvm.frexp.* returns a { significand, exponent } aggregate, so (like the *.with.overflow
     * intrinsics) Sulong lowers it to sret form: args[1] is the implicit result-struct pointer,
     * args[2] is the operand. We store the significand into field 0 (offset 0) and the exponent
     * into field 1 (secondValueOffset) and return the pointer. frexp decomposes x into
     * significand * 2^exponent with 0.5 <= |significand| < 1; 0/inf/nan pass through with
     * exponent 0, matching the C library.
     */
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "secondValueOffset", type = long.class)
    public abstract static class LLVMFrexpNode extends LLVMBuiltin {

        @Child private LLVMFloatStoreNode storeF32 = LLVMFloatStoreNode.create();
        @Child private LLVMDoubleStoreNode storeF64 = LLVMDoubleStoreNode.create();
        @Child private LLVMI32StoreNode storeI32 = LLVMI32StoreNodeGen.create(null, null);

        protected abstract long getSecondValueOffset();

        private static int frexpExp(float x) {
            if (x == 0.0f || !Float.isFinite(x)) {
                return 0;
            }
            int e = Math.getExponent(x) + 1;
            float m = Math.scalb(x, -e);
            // normalize the subnormal edge cases where Math.getExponent clamps
            while (Math.abs(m) >= 1.0f) {
                m *= 0.5f;
                e++;
            }
            while (Math.abs(m) < 0.5f) {
                m *= 2.0f;
                e--;
            }
            return e;
        }

        private static int frexpExp(double x) {
            if (x == 0.0 || !Double.isFinite(x)) {
                return 0;
            }
            int e = Math.getExponent(x) + 1;
            double m = Math.scalb(x, -e);
            while (Math.abs(m) >= 1.0) {
                m *= 0.5;
                e++;
            }
            while (Math.abs(m) < 0.5) {
                m *= 2.0;
                e--;
            }
            return e;
        }

        private static float frexpMant(float x, int e) {
            return (x == 0.0f || !Float.isFinite(x)) ? x : Math.scalb(x, -e);
        }

        private static double frexpMant(double x, int e) {
            return (x == 0.0 || !Double.isFinite(x)) ? x : Math.scalb(x, -e);
        }

        @Specialization
        protected Object doFloat(LLVMPointer addr, float value) {
            int e = frexpExp(value);
            storeF32.executeWithTarget(addr, frexpMant(value, e));
            storeI32.executeWithTarget(addr.increment(getSecondValueOffset()), e);
            return addr;
        }

        @Specialization
        protected Object doDouble(LLVMPointer addr, double value) {
            int e = frexpExp(value);
            storeF64.executeWithTarget(addr, frexpMant(value, e));
            storeI32.executeWithTarget(addr.increment(getSecondValueOffset()), e);
            return addr;
        }

        @Specialization
        protected Object doFloatVector(LLVMPointer addr, LLVMFloatVector value) {
            long off = getSecondValueOffset();
            int len = value.getLength();
            for (int i = 0; i < len; i++) {
                float x = value.getValue(i);
                int e = frexpExp(x);
                storeF32.executeWithTarget(addr.increment((long) i * Float.BYTES), frexpMant(x, e));
                storeI32.executeWithTarget(addr.increment(off + (long) i * Integer.BYTES), e);
            }
            return addr;
        }

        @Specialization
        protected Object doDoubleVector(LLVMPointer addr, LLVMDoubleVector value) {
            long off = getSecondValueOffset();
            int len = value.getLength();
            for (int i = 0; i < len; i++) {
                double x = value.getValue(i);
                int e = frexpExp(x);
                storeF64.executeWithTarget(addr.increment((long) i * Double.BYTES), frexpMant(x, e));
                storeI32.executeWithTarget(addr.increment(off + (long) i * Integer.BYTES), e);
            }
            return addr;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMMaskedGatherI32Node extends LLVMBuiltin {

        @Child private LLVMI32LoadNode load = LLVMI32LoadNode.create();

        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doGather(LLVMPointerVector pointers, LLVMI1Vector mask, LLVMI32Vector passthrough) {
            assert pointers.getLength() == getVectorLength();
            assert mask.getLength() == getVectorLength();
            assert passthrough.getLength() == getVectorLength();
            int[] result = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = mask.getValue(i) ? load.executeWithTarget(pointers.getValue(i)) : passthrough.getValue(i);
            }
            return LLVMI32Vector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMMaskedScatterI32Node extends LLVMBuiltin {

        @Child private LLVMI32StoreNode store = LLVMI32StoreNodeGen.create(null, null);

        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected Object doScatter(LLVMI32Vector values, LLVMPointerVector pointers, LLVMI1Vector mask) {
            assert values.getLength() == getVectorLength();
            assert pointers.getLength() == getVectorLength();
            assert mask.getLength() == getVectorLength();
            for (int i = 0; i < getVectorLength(); i++) {
                if (mask.getValue(i)) {
                    store.executeWithTarget(pointers.getValue(i), values.getValue(i));
                }
            }
            return null;
        }
    }
}
