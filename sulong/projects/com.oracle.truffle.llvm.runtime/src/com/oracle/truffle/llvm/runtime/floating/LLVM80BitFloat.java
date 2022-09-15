/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.floating;

import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.ContextExtension;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.NativeContextExtension.WellKnownNativeFunctionNode;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloatFactory.LLVM80BitFloatNativeCallNodeGen;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeConvertNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.nfi.api.SignatureLibrary;
import com.oracle.truffle.nfi.api.SerializableLibrary;

import java.util.Arrays;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloatFactory.LLVM80BitFloatUnaryNativeCallNodeGen;

@ValueType
@ExportLibrary(value = SerializableLibrary.class, useForAOT = false)
public final class LLVM80BitFloat extends LLVMInternalTruffleObject {

    private static final int BIT_TO_HEX_FACTOR = 4;
    public static final int BIT_WIDTH = 80;
    public static final int BYTE_WIDTH = BIT_WIDTH / Byte.SIZE;

    private static final int EXPONENT_BIT_WIDTH = 15;
    private static final int FRACTION_BIT_WIDTH = 64;
    private static final int HEX_DIGITS_FRACTION = FRACTION_BIT_WIDTH / BIT_TO_HEX_FACTOR;

    @Override
    public String toString() {
        return toLLVMString(this);
    }

    @TruffleBoundary
    public String toDebugString() {
        return String.format("sign: %s\nexponent: %s\nfraction: %s %s\n", getSign(), getBinaryString(EXPONENT_BIT_WIDTH, getExponent()), getBinaryString(FRACTION_BIT_WIDTH, getFraction()),
                        getHexString(HEX_DIGITS_FRACTION, getFraction()));
    }

    @TruffleBoundary
    public static String toLLVMString(LLVM80BitFloat value) {
        if (value.isQNaN()) {
            return "QNaN";

        } else if (value.isSNaN()) {
            return "SNaN";

        } else if (value.isInfinity()) {
            return "INF";

        } else {
            short exponent = value.getExponent();
            if (value.getSign()) {
                exponent |= (1 << EXPONENT_BIT_WIDTH);
            }
            long fraction = value.getFraction();
            return String.format("0xK%4x%16x", exponent, fraction).replace(' ', '0');
        }
    }

    @TruffleBoundary
    private static String getBinaryString(int bitWidth, long number) {
        return String.format("%" + bitWidth + "s", Long.toBinaryString(number)).replace(" ", "0");
    }

    @TruffleBoundary
    private static String getHexString(int bitWidth, long number) {
        return String.format("%" + bitWidth + "x", number).replace(" ", "0");
    }

    /**
     * Casting a NaN or infinity float to int is not specified. We mimic the "standard" undefined
     * behavior and return this constant.
     *
     * @see <a href=
     *      "http://stackoverflow.com/questions/10366485/problems-casting-nan-floats-to-int">
     *      Stackoverflow Problems casting NAN floats to int</a>
     */
    private static final byte UNDEFINED_FLOAT_TO_BYTE_VALUE = 0;
    private static final short UNDEFINED_FLOAT_TO_SHORT_VALUE = 0;
    private static final int UNDEFINED_FLOAT_TO_INT_VALUE = 0x80000000;
    private static final long UNDEFINED_FLOAT_TO_LONG_VALUE = 0x8000000000000000L;

    private static final long UNDEFINED_DOUBLE_VALUE = 0x80000000_00000000L;

    public static final short EXPONENT_MASK = 0b111111111111111; // 15 bit
    public static final short SIGN_BIT = (short) (1 << 15);

    // all cached LLVM80BitFloat objects are escaping objects and must not be used directly
    private static final LLVM80BitFloat DOUBLE_MINUS_INFINITY_CONVERSION_NUMBER = LLVM80BitFloat.fromRawValues(true, EXPONENT_MASK, UNDEFINED_DOUBLE_VALUE);
    private static final LLVM80BitFloat DOUBLE_INFINITY_CONVERSION_NUMBER = LLVM80BitFloat.fromRawValues(false, EXPONENT_MASK, UNDEFINED_DOUBLE_VALUE);
    private static final LLVM80BitFloat DOUBLE_NAN_CONVERSION_NUMBER = LLVM80BitFloat.fromRawValues(false, EXPONENT_MASK, 0xc000000000000000L);

    private static final LLVM80BitFloat POSITIVE_ZERO = LLVM80BitFloat.fromRawValues(false, 0, 0);
    private static final LLVM80BitFloat NEGATIVE_ZERO = LLVM80BitFloat.fromRawValues(true, 0, 0);

    private static final LLVM80BitFloat POSITIVE_INFINITY = LLVM80BitFloat.fromRawValues(false, EXPONENT_MASK, bit(63L));
    private static final LLVM80BitFloat NEGATIVE_INFINITY = LLVM80BitFloat.fromRawValues(true, EXPONENT_MASK, bit(63L));

    private static final int EXPLICIT_LEADING_ONE_BITS = 1;
    private static final int EXPONENT_BIAS = 16383;
    private static final int FLOAT_EXPONENT_BIAS = 127;

    private final short expSign; // 1 bit sign + 15 bit biased exponent
    private final long fraction; // 64 bit

    public LLVM80BitFloat(short expSign, long fraction) {
        this.expSign = expSign;
        this.fraction = fraction;
    }

    public static LLVM80BitFloat fromRawValues(boolean sign, int exponent, long fraction) {
        assert (exponent & 0x7FFFF) == exponent;
        short expSign = (short) exponent;
        if (sign) {
            expSign |= SIGN_BIT;
        }
        return new LLVM80BitFloat(expSign, fraction);
    }

    private LLVM80BitFloat(LLVM80BitFloat value) {
        this.expSign = value.expSign;
        this.fraction = value.fraction;
    }

    private int getUnbiasedExponent() {
        return (expSign & EXPONENT_MASK) - EXPONENT_BIAS;
    }

    public static LLVM80BitFloat createPositiveZero() {
        if (CompilerDirectives.inCompiledCode()) {
            return LLVM80BitFloat.fromRawValues(false, 0, 0);
        } else {
            return POSITIVE_ZERO;
        }
    }

    public static long bit(long i) {
        return 1L << i;
    }

    public static LLVM80BitFloat fromLong(long val) {
        if (val == 0) {
            return createPositiveZero();
        }
        boolean sign = val < 0;
        return fromLong(Math.abs(val), sign);
    }

    private static LLVM80BitFloat fromLong(long val, boolean sign) {
        int leadingOnePosition = Long.SIZE - Long.numberOfLeadingZeros(val);
        int exponent = EXPONENT_BIAS + (leadingOnePosition - 1);
        long fractionMask;
        if (leadingOnePosition == Long.SIZE || leadingOnePosition == Long.SIZE - 1) {
            fractionMask = 0xffffffff;
        } else {
            fractionMask = (1L << leadingOnePosition + 1) - 1;
        }
        long maskedFractionValue = val & fractionMask;
        long fraction = maskedFractionValue << (Long.SIZE - leadingOnePosition);
        return LLVM80BitFloat.fromRawValues(sign, exponent, fraction);
    }

    public static LLVM80BitFloat fromUnsignedLong(long val) {
        if (val == 0) {
            return new LLVM80BitFloat(POSITIVE_ZERO);
        }
        return fromLong(val, false);
    }

    public static LLVM80BitFloat fromUnsignedInt(int val) {
        if (val == 0) {
            return new LLVM80BitFloat(POSITIVE_ZERO);
        }
        return fromLong(val & BinaryHelper.INT_MASK, false);
    }

    public static LLVM80BitFloat fromInt(int val) {
        if (val == 0) {
            return new LLVM80BitFloat(POSITIVE_ZERO);
        }
        boolean sign = val < 0;
        return fromInt(val, sign);
    }

    private static LLVM80BitFloat fromInt(int val, boolean sign) {
        int posVal = Math.abs(val);
        int leadingOnePosition = Integer.SIZE - Integer.numberOfLeadingZeros(posVal);
        int exponent = EXPONENT_BIAS + (leadingOnePosition - 1);
        long fractionMask = (1L << leadingOnePosition + 1) - 1;
        long maskedFractionValue = posVal & fractionMask;
        long fraction = maskedFractionValue << (Long.SIZE - leadingOnePosition);
        return LLVM80BitFloat.fromRawValues(sign, exponent, fraction);
    }

    private static boolean getBit(int position, long posVal) {
        long l = posVal >>> position;
        return (l & 1) != 0;
    }

    public boolean isZero() {
        return isPositiveZero() || isNegativeZero();
    }

    private boolean isPositiveZero() {
        return equals(POSITIVE_ZERO);
    }

    private boolean isNegativeZero() {
        return equals(NEGATIVE_ZERO);
    }

    public static LLVM80BitFloat fromFloat(float val) {
        boolean sign = val < 0;
        if (FloatHelper.isPositiveZero(val)) {
            return new LLVM80BitFloat(POSITIVE_ZERO);
        } else if (FloatHelper.isNegativeZero(val)) {
            return new LLVM80BitFloat(NEGATIVE_ZERO);
        } else if (FloatHelper.isPositiveInfinty(val)) {
            return new LLVM80BitFloat(DOUBLE_INFINITY_CONVERSION_NUMBER);
        } else if (FloatHelper.isNegativeInfinity(val)) {
            return new LLVM80BitFloat(DOUBLE_MINUS_INFINITY_CONVERSION_NUMBER);
        } else if (FloatHelper.isNaN(val)) {
            return new LLVM80BitFloat(DOUBLE_NAN_CONVERSION_NUMBER);
        } else {
            int rawValue = Float.floatToRawIntBits(val);
            int floatExponent = FloatHelper.getUnbiasedExponent(val);
            int biasedExponent = floatExponent + EXPONENT_BIAS;
            long leadingOne = (long) EXPLICIT_LEADING_ONE_BITS << (FRACTION_BIT_WIDTH - 1);
            long floatFraction = rawValue & FloatHelper.FRACTION_MASK;
            long shiftedFloatFraction = floatFraction << (FRACTION_BIT_WIDTH - FloatHelper.FLOAT_FRACTION_BIT_WIDTH - EXPLICIT_LEADING_ONE_BITS);
            long fraction = shiftedFloatFraction | leadingOne;
            return LLVM80BitFloat.fromRawValues(sign, biasedExponent, fraction);
        }
    }

    public static LLVM80BitFloat fromDouble(double val) {
        boolean sign = val < 0;
        if (DoubleHelper.isPositiveZero(val)) {
            return new LLVM80BitFloat(POSITIVE_ZERO);
        } else if (DoubleHelper.isNegativeZero(val)) {
            return new LLVM80BitFloat(NEGATIVE_ZERO);
        } else if (DoubleHelper.isPositiveInfinty(val)) {
            return new LLVM80BitFloat(DOUBLE_INFINITY_CONVERSION_NUMBER);
        } else if (DoubleHelper.isNegativeInfinity(val)) {
            return new LLVM80BitFloat(DOUBLE_MINUS_INFINITY_CONVERSION_NUMBER);
        } else if (DoubleHelper.isNaN(val)) {
            return new LLVM80BitFloat(DOUBLE_NAN_CONVERSION_NUMBER);
        } else {
            long rawValue = Double.doubleToRawLongBits(val);
            int doubleExponent = DoubleHelper.getUnbiasedExponent(val);
            int biasedExponent = doubleExponent + EXPONENT_BIAS;
            long leadingOne = (long) EXPLICIT_LEADING_ONE_BITS << (FRACTION_BIT_WIDTH - 1);
            long doubleFraction = rawValue & DoubleHelper.FRACTION_MASK;
            long shiftedDoubleFraction = doubleFraction << (FRACTION_BIT_WIDTH - DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH - EXPLICIT_LEADING_ONE_BITS);
            long fraction = shiftedDoubleFraction | leadingOne;
            return LLVM80BitFloat.fromRawValues(sign, biasedExponent, fraction);
        }
    }

    private long getFractionAsLong() {
        return fraction >>> (FRACTION_BIT_WIDTH - getUnbiasedExponent() - EXPLICIT_LEADING_ONE_BITS);
    }

    public LLVM80BitFloat abs() {
        return new LLVM80BitFloat((short) (expSign & EXPONENT_MASK), fraction);
    }

    public boolean isPositiveInfinity() {
        return POSITIVE_INFINITY.equals(this);
    }

    public boolean isNegativeInfinity() {
        return NEGATIVE_INFINITY.equals(this);
    }

    public boolean isInfinity() {
        return isPositiveInfinity() || isNegativeInfinity();
    }

    public boolean isSNaN() {
        // Checkstyle: stop magic number name check
        if (getExponent() == EXPONENT_MASK) {
            if (getBit(63, getFraction())) {
                if (!getBit(62, getFraction())) {
                    return (getFraction() & 0x3FFFFFFF_FFFFFFFFL) != 0L;
                }
            }
        }
        // Checkstyle: resume magic number name check
        return false;
    }

    public boolean isQNaN() {
        // Checkstyle: stop magic number name check
        if (getExponent() == EXPONENT_MASK) {
            if (getBit(63, getFraction())) {
                if (getBit(62, getFraction())) {
                    return true;
                }
            } else {
                return true; // Handle Pseudo NaN as quiet NaN
            }
        }
        // Checkstyle: resume magic number name check
        return false;
    }

    public boolean isNaN() {
        return isSNaN() || isQNaN();
    }

    public boolean isOrdered() {
        return !isNaN();
    }

    int compareOrdered(LLVM80BitFloat val) {
        if (isNegativeInfinity()) {
            if (val.isNegativeInfinity()) {
                return 0;
            } else {
                return -1;
            }
        }
        if (val.isNegativeInfinity()) {
            if (isNegativeInfinity()) {
                return 0;
            } else {
                return 1;
            }
        }
        if (getSign() == val.getSign()) {
            int expDifference = getExponent() - val.getExponent();
            if (expDifference == 0) {
                long fractionDifference = getFraction() - val.getFraction();
                if (fractionDifference == 0) {
                    return 0;
                } else if (fractionDifference < 0) {
                    return -1;
                } else {
                    return 1;
                }
            } else {
                return expDifference;
            }
        } else {
            if (isZero() && val.isZero()) {
                return 0;
            } else if (getSign()) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    public short getExponent() {
        return (short) (expSign & EXPONENT_MASK);
    }

    public long getFraction() {
        return fraction;
    }

    public long getFractionWithoutImplicitZero() {
        return fraction << 1;
    }

    public short getExpSign() {
        return expSign;
    }

    public boolean getSign() {
        return (expSign & SIGN_BIT) != 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LLVM80BitFloat)) {
            return false;
        }
        LLVM80BitFloat other = ((LLVM80BitFloat) obj);
        return this.expSign == other.expSign && this.fraction == other.fraction;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getBytes());
    }

    public byte[] getBytesBigEndian() {
        byte[] array = new byte[BYTE_WIDTH];
        ByteArraySupport.bigEndian().putShort(array, 0, expSign);
        ByteArraySupport.bigEndian().putLong(array, 2, fraction);
        return array;
    }

    public byte[] getBytes() {
        byte[] array = new byte[BYTE_WIDTH];
        ByteArraySupport.littleEndian().putLong(array, 0, fraction);
        ByteArraySupport.littleEndian().putShort(array, 8, expSign);
        return array;
    }

    public static LLVM80BitFloat fromBytesBigEndian(byte[] bytes) {
        assert bytes.length == BYTE_WIDTH;
        short expSign = ByteArraySupport.bigEndian().getShort(bytes, 0);
        long fraction = ByteArraySupport.bigEndian().getLong(bytes, 2);
        return new LLVM80BitFloat(expSign, fraction);
    }

    public static LLVM80BitFloat fromBytes(byte[] bytes) {
        assert bytes.length == BYTE_WIDTH;
        long fraction = ByteArraySupport.littleEndian().getLong(bytes, 0);
        short expSign = ByteArraySupport.littleEndian().getShort(bytes, 8);
        return new LLVM80BitFloat(expSign, fraction);
    }

    // get value

    public byte getByteValue() {
        if (isNaN() || isInfinity()) {
            return UNDEFINED_FLOAT_TO_BYTE_VALUE;
        } else {
            long value = getFractionAsLong();
            return (byte) (getSign() ? -value : value);
        }
    }

    public short getShortValue() {
        if (isNaN() || isInfinity()) {
            return UNDEFINED_FLOAT_TO_SHORT_VALUE;
        } else {
            long value = getFractionAsLong();
            return (short) (getSign() ? -value : value);
        }
    }

    public int getIntValue() {
        if (isNaN() || isInfinity()) {
            return UNDEFINED_FLOAT_TO_INT_VALUE;
        }
        int value = (int) getFractionAsLong();
        return getSign() ? -value : value;
    }

    public long getLongValue() {
        if (isNaN() || isInfinity()) {
            return UNDEFINED_FLOAT_TO_LONG_VALUE;
        } else {
            long value = getFractionAsLong();
            return getSign() ? -value : value;
        }
    }

    public float getFloatValue() {
        if (isPositiveZero()) {
            return FloatHelper.POSITIVE_ZERO;
        } else if (isNegativeZero()) {
            return FloatHelper.NEGATIVE_ZERO;
        } else if (isPositiveInfinity()) {
            return FloatHelper.POSITIVE_INFINITY;
        } else if (isNegativeInfinity()) {
            return FloatHelper.NEGATIVE_INFINITY;
        } else if (isNaN()) {
            return FloatHelper.NaN;
        } else {
            int floatExponent = getUnbiasedExponent() + FLOAT_EXPONENT_BIAS;
            long longFraction = (getFractionWithoutImplicitZero()) >>> (FRACTION_BIT_WIDTH - FloatHelper.FLOAT_FRACTION_BIT_WIDTH);
            int floatFraction = (int) longFraction;
            int shiftedSignBit = (getSign() ? 1 : 0) << FloatHelper.FLOAT_SIGN_POS;
            int shiftedExponent = floatExponent << FloatHelper.FLOAT_FRACTION_BIT_WIDTH;
            int rawVal = floatFraction | shiftedExponent | shiftedSignBit;
            return Float.intBitsToFloat(rawVal);
        }
    }

    public double getDoubleValue() {
        if (isPositiveZero()) {
            return DoubleHelper.POSITIVE_ZERO;
        } else if (isNegativeZero()) {
            return DoubleHelper.NEGATIVE_ZERO;
        } else if (isPositiveInfinity()) {
            return DoubleHelper.POSITIVE_INFINITY;
        } else if (isNegativeInfinity()) {
            return DoubleHelper.NEGATIVE_INFINITY;
        } else if (isNaN()) {
            return DoubleHelper.NaN;
        } else {
            int doubleExponent = getUnbiasedExponent() + DoubleHelper.DOUBLE_EXPONENT_BIAS;
            long doubleFraction = (getFractionWithoutImplicitZero()) >>> (FRACTION_BIT_WIDTH - DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH);
            long shiftedSignBit = (getSign() ? 1L : 0L) << DoubleHelper.DOUBLE_SIGN_POS;
            long shiftedExponent = (long) doubleExponent << DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH;
            long rawVal = doubleFraction | shiftedExponent | shiftedSignBit;
            return Double.longBitsToDouble(rawVal);
        }
    }

    public LLVM80BitFloat negate() {
        return new LLVM80BitFloat((short) (expSign ^ SIGN_BIT), fraction);
    }

    public static LLVM80BitFloat fromByte(byte from) {
        return fromInt(from);
    }

    public static LLVM80BitFloat fromUnsignedByte(byte from) {
        return fromInt(from & 0xFF);
    }

    public static LLVM80BitFloat fromUnsignedShort(short from) {
        return fromUnsignedInt(from & 0xFFFF);
    }

    public static LLVM80BitFloat fromShort(short from) {
        return fromInt(from);
    }

    @ExplodeLoop
    public static boolean areOrdered(LLVM80BitFloat... vals) {
        CompilerAsserts.compilationConstant(vals.length);
        for (LLVM80BitFloat val : vals) {
            if (!val.isOrdered()) {
                return false;
            }
        }
        return true;
    }

    public static int compare(LLVM80BitFloat val1, LLVM80BitFloat val2) {
        return val1.compareOrdered(val2);
    }

    // serialization for NFI

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isSerializable() {
        return true;
    }

    @ExportMessage(limit = "1")
    void serialize(Object buffer,
                    @CachedLibrary("buffer") InteropLibrary interop) {
        try {
            interop.writeBufferLong(buffer, ByteOrder.LITTLE_ENDIAN, 0, fraction);
            interop.writeBufferShort(buffer, ByteOrder.LITTLE_ENDIAN, 8, expSign);
        } catch (UnsupportedMessageException | InvalidBufferOffsetException ex) {
            throw CompilerDirectives.shouldNotReachHere(ex);
        }
    }

    public abstract static class FP80Node extends LLVMExpressionNode {

        final String name;

        private final String functionName;
        private final String signature;

        final ContextExtension.Key<NativeContextExtension> nativeCtxExtKey;

        public abstract LLVM80BitFloat execute(Object... args);

        FP80Node(String name, String signature) {
            this.name = name;
            this.functionName = "__sulong_fp80_" + name;
            this.signature = signature;
            this.nativeCtxExtKey = LLVMLanguage.get(this).lookupContextExtension(NativeContextExtension.class);
        }

        protected WellKnownNativeFunctionNode createFunction() {
            LLVMContext context = LLVMContext.get(this);
            NativeContextExtension nativeContextExtension = context.getContextExtensionOrNull(NativeContextExtension.class);
            if (nativeContextExtension == null) {
                return null;
            } else {
                return nativeContextExtension.getWellKnownNativeFunction(functionName, signature);
            }
        }

        protected NativeContextExtension.WellKnownNativeFunctionAndSignature getFunction() {
            NativeContextExtension nativeContextExtension = nativeCtxExtKey.get(LLVMContext.get(this));
            return nativeContextExtension.getWellKnownNativeFunctionAndSignature(functionName, signature);
        }

        protected LLVMNativeConvertNode createToFP80() {
            return LLVMNativeConvertNode.createFromNative(PrimitiveType.X86_FP80);
        }

    }

    @NodeChild(value = "x", type = LLVMExpressionNode.class)
    @NodeChild(value = "y", type = LLVMExpressionNode.class)
    abstract static class LLVM80BitFloatNativeCallNode extends FP80Node {

        LLVM80BitFloatNativeCallNode(String name) {
            super(name, "(FP80,FP80):FP80");
        }

        @Specialization(guards = "function != null")
        protected LLVM80BitFloat doCall(Object x, Object y,
                        @Cached("createFunction()") WellKnownNativeFunctionNode function,
                        @Cached("createToFP80()") LLVMNativeConvertNode nativeConvert) {
            try {
                Object ret = function.execute(x, y);
                return (LLVM80BitFloat) nativeConvert.executeConvert(ret);
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = "nativeCtxExtKey != null", replaces = "doCall")
        protected LLVM80BitFloat doCallAOT(Object x, Object y,
                        @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary,
                        @Cached("createToFP80()") LLVMNativeConvertNode nativeConvert) {
            NativeContextExtension.WellKnownNativeFunctionAndSignature wkFunSig = getFunction();
            try {
                Object ret = signatureLibrary.call(wkFunSig.getSignature(), wkFunSig.getFunction(), x, y);
                return (LLVM80BitFloat) nativeConvert.executeConvert(ret);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @TruffleBoundary
        @Specialization(guards = "nativeCtxExtKey == null")
        protected LLVM80BitFloat doCallNoNative(LLVM80BitFloat x, LLVM80BitFloat y) {
            // imprecise workaround for cases in which NFI isn't available
            double xDouble = x.getDoubleValue();
            double yDouble = y.getDoubleValue();
            double result;
            switch (name) {
                case "add":
                    result = xDouble + yDouble;
                    break;
                case "sub":
                    result = xDouble - yDouble;
                    break;
                case "mul":
                    result = xDouble * yDouble;
                    break;
                case "div":
                    result = xDouble / yDouble;
                    break;
                case "mod":
                    result = xDouble % yDouble;
                    break;
                default:
                    throw new AssertionError("unexpected 80 bit float operation: " + name);
            }
            return LLVM80BitFloat.fromDouble(result);
        }

        @Override
        public String toString() {
            return "fp80 " + name;
        }
    }

    @NodeChild(value = "x", type = LLVMExpressionNode.class)
    abstract static class LLVM80BitFloatUnaryNativeCallNode extends FP80Node {

        LLVM80BitFloatUnaryNativeCallNode(String name) {
            super(name, "(FP80):FP80");
        }

        @Specialization(guards = "function != null")
        protected LLVM80BitFloat doCall(Object x,
                        @Cached("createFunction()") WellKnownNativeFunctionNode function,
                        @Cached("createToFP80()") LLVMNativeConvertNode nativeConvert) {
            try {
                Object ret = function.execute(x);
                return (LLVM80BitFloat) nativeConvert.executeConvert(ret);
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = "nativeCtxExtKey != null", replaces = "doCall")
        protected LLVM80BitFloat doCallAOT(Object x,
                        @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary,
                        @Cached("createToFP80()") LLVMNativeConvertNode nativeConvert) {
            NativeContextExtension.WellKnownNativeFunctionAndSignature wkFunSig = getFunction();
            try {
                Object ret = signatureLibrary.call(wkFunSig.getSignature(), wkFunSig.getFunction(), x);
                return (LLVM80BitFloat) nativeConvert.executeConvert(ret);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    public static FP80Node createAddNode() {
        return LLVM80BitFloatNativeCallNodeGen.create("add", null, null);
    }

    public static FP80Node createSubNode() {
        return LLVM80BitFloatNativeCallNodeGen.create("sub", null, null);
    }

    public static FP80Node createMulNode() {
        return LLVM80BitFloatNativeCallNodeGen.create("mul", null, null);
    }

    public static FP80Node createDivNode() {
        return LLVM80BitFloatNativeCallNodeGen.create("div", null, null);
    }

    public static FP80Node createRemNode() {
        return LLVM80BitFloatNativeCallNodeGen.create("mod", null, null);
    }

    public static FP80Node createPowNode(LLVMExpressionNode x, LLVMExpressionNode y) {
        return LLVM80BitFloatNativeCallNodeGen.create("pow", x, y);
    }

    public static FP80Node createUnary(String name, LLVMExpressionNode x) {
        return LLVM80BitFloatUnaryNativeCallNodeGen.create(name, x);
    }
}
