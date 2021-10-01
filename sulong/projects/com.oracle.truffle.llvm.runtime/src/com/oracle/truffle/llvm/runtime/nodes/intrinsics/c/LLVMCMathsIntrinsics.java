/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.c;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.interop.LLVMNegatedForeignObject;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMDoubleStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMFloatStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

/**
 * Implements the C functions from math.h.
 */
public abstract class LLVMCMathsIntrinsics {

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMSqrt extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.sqrt(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.sqrt(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMSqrtVectorNode extends LLVMBuiltin {
        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doVector(LLVMDoubleVector value) {
            assert value.getLength() == getVectorLength();
            double[] result = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = Math.sqrt(value.getValue(i));
            }
            return LLVMDoubleVector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doVector(LLVMFloatVector value) {
            assert value.getLength() == getVectorLength();
            float[] result = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = (float) Math.sqrt(value.getValue(i));
            }
            return LLVMFloatVector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLog extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.log(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.log(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLog2 extends LLVMBuiltin {

        private static final double LOG_2 = Math.log(2);

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) (Math.log(value) / LOG_2);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.log(value) / LOG_2;
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLog10 extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.log10(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.log10(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLog1p extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.log1p(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.log1p(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMRint extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.rint(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.rint(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMCeil extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.ceil(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.ceil(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMFloor extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.floor(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.floor(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMRound extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return Math.round(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.round(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMAbs extends LLVMIntrinsic {

        @Specialization
        protected byte doByte(byte value) {
            return (byte) Math.abs(value);
        }

        @Specialization
        protected short doShort(short value) {
            return (short) Math.abs(value);
        }

        @Specialization
        protected int doInt(int value) {
            return Math.abs(value);
        }

        @Specialization
        protected long doLong(long value) {
            return Math.abs(value);
        }

        @Specialization
        protected LLVMNativePointer doNative(LLVMNativePointer value) {
            return LLVMNativePointer.create(doLong(value.asNative()));
        }

        @Specialization
        protected LLVMManagedPointer doManaged(LLVMManagedPointer value,
                        @Cached ConditionProfile negated) {
            if (negated.profile(value.getObject() instanceof LLVMNegatedForeignObject)) {
                LLVMNegatedForeignObject obj = (LLVMNegatedForeignObject) value.getObject();
                assert !(obj.getForeign() instanceof LLVMNegatedForeignObject);
                return LLVMManagedPointer.create(obj.getForeign(), -value.getOffset());
            } else {
                // valid pointers are always positive
                return value;
            }
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMFAbs extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return Math.abs(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.abs(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            return value.abs();
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMFAbsVectorNode extends LLVMBuiltin {
        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doVector(LLVMDoubleVector value) {
            assert value.getLength() == getVectorLength();
            double[] result = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = Math.abs(value.getValue(i));
            }
            return LLVMDoubleVector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doVector(LLVMFloatVector value) {
            assert value.getLength() == getVectorLength();
            float[] result = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = Math.abs(value.getValue(i));
            }
            return LLVMFloatVector.create(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMMinnum extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value1, float value2) {
            if (Float.isNaN(value1)) {
                return value2;
            }
            if (Float.isNaN(value2)) {
                return value1;
            }
            return Math.min(value1, value2);
        }

        @Specialization
        protected double doIntrinsic(double value1, double value2) {
            if (Double.isNaN(value1)) {
                return value2;
            }
            if (Double.isNaN(value2)) {
                return value1;
            }
            return Math.min(value1, value2);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMMaxnum extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value1, float value2) {
            if (Float.isNaN(value1)) {
                return value2;
            }
            if (Float.isNaN(value2)) {
                return value1;
            }
            return Math.max(value1, value2);
        }

        @Specialization
        protected double doIntrinsic(double value1, double value2) {
            if (Double.isNaN(value1)) {
                return value2;
            }
            if (Double.isNaN(value2)) {
                return value1;
            }
            return Math.max(value1, value2);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMExp extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.exp(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.exp(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMExpm1 extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.expm1(value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.expm1(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMExp2 extends LLVMBuiltin {

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.pow(2, value);
        }

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.pow(2, value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLdexp extends LLVMIntrinsic {

        @Specialization
        protected float doIntrinsic(float value, int exp) {
            return value * (float) Math.pow(2, exp);
        }

        @Specialization
        protected double doIntrinsic(double value, int exp) {
            return value * Math.pow(2, exp);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value, int exp) {
            double result = doIntrinsic(value.getDoubleValue(), exp);
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMModf extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value, LLVMPointer integralAddr,
                        @Cached LLVMDoubleStoreNode store) {
            double fractional = value % 1;
            double integral = value - fractional;
            store.executeWithTarget(integralAddr, integral);
            return fractional;
        }

        @Specialization
        protected float doIntrinsic(float value, LLVMPointer integralAddr,
                        @Cached LLVMFloatStoreNode store) {
            float fractional = value % 1;
            float integral = value - fractional;
            store.executeWithTarget(integralAddr, integral);
            return fractional;
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat longDoubleValue, LLVMPointer integralAddr,
                        @Cached LLVM80BitFloatStoreNode store) {
            double value = longDoubleValue.getDoubleValue();
            double fractional = value % 1;
            double integral = value - fractional;
            store.executeWithTarget(integralAddr, LLVM80BitFloat.fromDouble(integral));
            return LLVM80BitFloat.fromDouble(fractional);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMFmod extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double numer, double denom) {
            return numer % denom;
        }

        @Specialization
        protected float doIntrinsic(float numer, float denom) {
            return numer % denom;
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value, LLVM80BitFloat denom) {
            double result = doIntrinsic(value.getDoubleValue(), denom.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPow extends LLVMBuiltin {

        @Specialization
        protected float doFloat(float val, int pow) {
            return (float) Math.pow(val, pow);
        }

        @Specialization
        protected float doFloat(float val, float pow) {
            return (float) Math.pow(val, pow);
        }

        @Specialization
        protected double doDouble(double a, int b) {
            return Math.pow(a, b);
        }

        @Specialization
        protected double doDouble(double a, double b) {
            return Math.pow(a, b);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value, int pow) {
            double result = doDouble(value.getDoubleValue(), pow);
            return LLVM80BitFloat.fromDouble(result);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat a, LLVM80BitFloat b) {
            double result = doDouble(a.getDoubleValue(), b.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMSin extends LLVMBuiltin {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.sin(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.sin(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMSinh extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.sinh(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.sinh(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMASin extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.asin(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.asin(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMCos extends LLVMBuiltin {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.cos(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.cos(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMCosh extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.cosh(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.cosh(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMACos extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.acos(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.acos(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMTan extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.tan(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.tan(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMTanh extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.tanh(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.tanh(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMATan extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value) {
            return Math.atan(value);
        }

        @Specialization
        protected float doIntrinsic(float value) {
            return (float) Math.atan(value);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value) {
            double result = doIntrinsic(value.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMATan2 extends LLVMIntrinsic {

        @Specialization
        protected double doIntrinsic(double value1, double value2) {
            return Math.atan2(value1, value2);
        }

        @Specialization
        protected float doIntrinsic(float value1, float value2) {
            return (float) Math.atan2(value1, value2);
        }

        @Specialization
        protected LLVM80BitFloat doIntrinsic(LLVM80BitFloat value1, LLVM80BitFloat value2) {
            double result = doIntrinsic(value1.getDoubleValue(), value2.getDoubleValue());
            return LLVM80BitFloat.fromDouble(result);
        }
    }

    @NodeChild(value = "magnitude", type = LLVMExpressionNode.class)
    @NodeChild(value = "sign", type = LLVMExpressionNode.class)
    public abstract static class LLVMCopySign extends LLVMBuiltin {

        @Specialization
        protected float doFloat(float magnitude, float sign) {
            return Math.copySign(magnitude, sign);
        }

        @Specialization
        protected double doDouble(double magnitude, double sign) {
            return Math.copySign(magnitude, sign);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloat(LLVM80BitFloat magnitude, LLVM80BitFloat sign) {
            if (magnitude.getSign() != sign.getSign()) {
                return magnitude.negate();
            } else {
                return magnitude;
            }
        }
    }

    abstract static class LLVMUMinMaxOperator {
        protected abstract boolean compare(boolean a, boolean b);

        protected abstract int compare(int a, int b);

        protected abstract long compare(long a, long b);

        protected abstract float compare(float a, float b);

        protected abstract double compare(double a, double b);
    }

    public static final class LLVMUmaxOperator extends LLVMUMinMaxOperator {
        public static final LLVMUmaxOperator INSTANCE = new LLVMUmaxOperator();

        @Override
        protected boolean compare(boolean a, boolean b) {
            return a || b;
        }

        @Override
        protected int compare(int a, int b) {
            return Integer.compareUnsigned(a, b) >= 0 ? a : b;
        }

        @Override
        protected long compare(long a, long b) {
            return Long.compareUnsigned(a, b) >= 0 ? a : b;
        }

        @Override
        protected float compare(float a, float b) {
            return Math.max(a, b);
        }

        @Override
        protected double compare(double a, double b) {
            return Math.max(a, b);
        }
    }

    public static final class LLVMUminOperator extends LLVMUMinMaxOperator {
        public static final LLVMUminOperator INSTANCE = new LLVMUminOperator();

        @Override
        protected boolean compare(boolean a, boolean b) {
            return a && b;
        }

        @Override
        protected int compare(int a, int b) {
            return Integer.compareUnsigned(a, b) <= 0 ? a : b;
        }

        @Override
        protected long compare(long a, long b) {
            return Long.compareUnsigned(a, b) <= 0 ? a : b;
        }

        @Override
        protected float compare(float a, float b) {
            return Math.min(a, b);
        }

        @Override
        protected double compare(double a, double b) {
            return Math.min(a, b);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    @NodeField(name = "operator", type = LLVMUMinMaxOperator.class)
    public abstract static class LLVMUnsignedVectorMinMaxNode extends LLVMBuiltin {
        protected abstract int getVectorLength();

        protected abstract LLVMUMinMaxOperator getOperator();

        private byte compare(byte a, byte b) {
            return (byte) getOperator().compare(a, b);
        }

        private short compare(short a, short b) {
            return (short) getOperator().compare(a, b);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI1Vector doI1Vector(LLVMI1Vector a, LLVMI1Vector b) {
            assert a.getLength() == getVectorLength();
            assert b.getLength() == getVectorLength();
            boolean[] result = new boolean[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = getOperator().compare(a.getValue(i), b.getValue(i));
            }
            return LLVMI1Vector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI8Vector doI8Vector(LLVMI8Vector a, LLVMI8Vector b) {
            assert a.getLength() == getVectorLength();
            assert b.getLength() == getVectorLength();
            byte[] result = new byte[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                byte aValue = a.getValue(i);
                byte bValue = b.getValue(i);
                result[i] = compare(aValue, bValue);
            }
            return LLVMI8Vector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI16Vector doI16Vector(LLVMI16Vector a, LLVMI16Vector b) {
            assert a.getLength() == getVectorLength();
            assert b.getLength() == getVectorLength();
            short[] result = new short[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                short aValue = a.getValue(i);
                short bValue = b.getValue(i);
                result[i] = compare(aValue, bValue);
            }
            return LLVMI16Vector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI32Vector doI32Vector(LLVMI32Vector a, LLVMI32Vector b) {
            assert a.getLength() == getVectorLength();
            assert b.getLength() == getVectorLength();
            int[] result = new int[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                int aValue = a.getValue(i);
                int bValue = b.getValue(i);
                result[i] = getOperator().compare(aValue, bValue);
            }
            return LLVMI32Vector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMI64Vector doI64Vector(LLVMI64Vector a, LLVMI64Vector b) {
            assert a.getLength() == getVectorLength();
            assert b.getLength() == getVectorLength();
            long[] result = new long[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                long aValue = a.getValue(i);
                long bValue = b.getValue(i);
                result[i] = getOperator().compare(aValue, bValue) >= 0 ? aValue : bValue;
            }
            return LLVMI64Vector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMFloatVector doFloatVector(LLVMFloatVector a, LLVMFloatVector b) {
            assert a.getLength() == getVectorLength();
            assert b.getLength() == getVectorLength();
            float[] result = new float[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = getOperator().compare(a.getValue(i), b.getValue(i));
            }
            return LLVMFloatVector.create(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVMDoubleVector doDoubleVector(LLVMDoubleVector a, LLVMDoubleVector b) {
            assert a.getLength() == getVectorLength();
            assert b.getLength() == getVectorLength();
            double[] result = new double[getVectorLength()];
            for (int i = 0; i < getVectorLength(); i++) {
                result[i] = getOperator().compare(a.getValue(i), b.getValue(i));
            }
            return LLVMDoubleVector.create(result);
        }
    }
}
