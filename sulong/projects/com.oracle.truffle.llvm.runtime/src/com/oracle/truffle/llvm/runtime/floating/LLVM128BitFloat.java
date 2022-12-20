package com.oracle.truffle.llvm.runtime.floating;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;

import java.util.Arrays;

public final class LLVM128BitFloat extends LLVMInternalTruffleObject {
    //private static final int BIT_TO_HEX_FACTOR = 4;
    public static final long SIGN_BIT = 1L << 63;
    //private static final int EXPONENT_BIT_WIDTH = 15;
    private static final int FRACTION_BIT_WIDTH = 112;
    //private static final int EXPLICIT_LEADING_ONE_BITS = 1;
    public static final int BIT_WIDTH = 128;
    public static final int EXPONENT_POSITION = FRACTION_BIT_WIDTH - Long.SIZE; // 112 - 64 = 48
    public static final int BYTE_WIDTH = BIT_WIDTH / Byte.SIZE;
    public static final long EXPONENT_MASK = 0b111111111111111L << EXPONENT_POSITION; // 15 bit, shifted to the left by 48bits.
    public static final long FRACTION_MASK = (1L << EXPONENT_POSITION) - 1;
    private static final LLVM128BitFloat POSITIVE_INFINITY = LLVM128BitFloat.fromRawValues(false, EXPONENT_MASK, bit(112L));
    private static final LLVM128BitFloat NEGATIVE_INFINITY = LLVM128BitFloat.fromRawValues(true, EXPONENT_MASK, bit(112L));
    private static final LLVM128BitFloat POSITIVE_ZERO = LLVM128BitFloat.fromRawValues(false, 0, 0);
    private static final LLVM128BitFloat NEGATIVE_ZERO = LLVM128BitFloat.fromRawValues(true, 0, 0);
    private static final int EXPONENT_BIAS = 16383;
    //private static final int FLOAT_EXPONENT_BIAS = 127;
    //private static final int DOUBLE_EXPONENT_BIAS = 1023;


    @Override
    public String toString() {
        return toLLVMString(this);
    }

    @CompilerDirectives.TruffleBoundary
    public static String toLLVMString(LLVM128BitFloat value) {
        if (value.isInfinity()) {
            return "INF";
        } else {
            return String.format("0xK%016x%016x", value.expSignFraction, value.fraction);
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static String getBinaryString(int bitWidth, long number) {
        return String.format("%" + bitWidth + "s", Long.toBinaryString(number)).replace(" ", "0");
    }

    @CompilerDirectives.TruffleBoundary
    private static String getHexString(int bitWidth, long number) {
        return String.format("%" + bitWidth + "x", number).replace(" ", "0");
    }

    private final long expSignFraction; // 64 bit -- the left over of the fraction goes into here.
    private final long fraction; // 64 bit -- fill this part first.

    public LLVM128BitFloat(long expSignFraction, long fraction) {
        this.expSignFraction = expSignFraction;
        this.fraction = fraction;
    }

    private LLVM128BitFloat(LLVM128BitFloat value) {
        this.expSignFraction = value.expSignFraction;
        this.fraction = value.fraction;
    }

    public static LLVM128BitFloat fromRawValues(boolean sign, long exponentFraction, long fraction) {
        assert (exponentFraction & 0x7FFFF) == exponentFraction;
        long expSignFraction = exponentFraction;
        if (sign) {
            expSignFraction |= SIGN_BIT;
        }
        return new LLVM128BitFloat(expSignFraction, fraction);
    }

    public long getExponent() {
        return (expSignFraction & EXPONENT_MASK) >> EXPONENT_POSITION;
    }

    public long getExpSignFractionPart() {
        return expSignFraction;
    }

    public boolean getSign() {
        return (expSignFraction & SIGN_BIT) != 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LLVM128BitFloat)) {
            return false;
        }
        LLVM128BitFloat other = ((LLVM128BitFloat) obj);
        return this.expSignFraction == other.expSignFraction && this.fraction == other.fraction;
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

    public static LLVM128BitFloat createPositiveZero() {
        if (CompilerDirectives.inCompiledCode()) {
            return LLVM128BitFloat.fromRawValues(false, 0, 0);
        } else {
            return POSITIVE_ZERO;
        }
    }

    public static long bit(long i) {
        return 1L << i;
    }

    public static LLVM128BitFloat fromLong(long val) {
        if (val == 0) {
            return createPositiveZero();
        }
        boolean sign = val < 0;
        return fromLong(Math.abs(val), sign);
    }

    public long getFractionWithoutImplicitZero() {
        return fraction << 1;
    }

    public long getFractionPart() {
        return fraction;
    }

    private long getUnbiasedExponent() {
        return ((expSignFraction & EXPONENT_MASK) >>> EXPONENT_POSITION) - (EXPONENT_BIAS + 1);
    }

    private long getFractionAsLong() {
        long unbiasedExponent = getUnbiasedExponent();
        long returnFraction = (1L << unbiasedExponent);
        if (unbiasedExponent <= 48) {
            returnFraction |= (expSignFraction & FRACTION_MASK)  >>> (EXPONENT_POSITION - unbiasedExponent);
        } else if (unbiasedExponent < 64) {
            returnFraction |= (expSignFraction & FRACTION_MASK)  << (unbiasedExponent - EXPONENT_POSITION);
            returnFraction |= fraction >>> (Long.SIZE - (unbiasedExponent - EXPONENT_POSITION));
        } else { // TODO: problematic when unbiasedExponent > 112.
            returnFraction = 0L; //TODO: overflow case.
            // TODO: need test cases for each condition of the unbiasedExponent.
        }
        return returnFraction;
    }

    public int toIntValue() {
        int value = (int) getFractionAsLong();
        return getSign() ? -value : value;
    }

    public long toLongValue() {
        long value = getFractionAsLong();
        return getSign() ? -value : value;
    }

    public double toDoubleValue() {
       if (isPositiveInfinity()) {
            return DoubleHelper.POSITIVE_INFINITY;
        } else if (isNegativeInfinity()) {
            return DoubleHelper.NEGATIVE_INFINITY;
        } else {
            long doubleExponent = getUnbiasedExponent()  + DoubleHelper.DOUBLE_EXPONENT_BIAS;
            long doubleFraction = (expSignFraction & FRACTION_MASK) << (DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH - EXPONENT_POSITION); // 48bits from expSignFraction, with 4 bits shift left.
            doubleFraction |= fraction >>> (Long.SIZE - (DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH - EXPONENT_POSITION)); // 4bits from fraction
            long shiftedSignBit = (getSign() ? 1L : 0L) << DoubleHelper.DOUBLE_SIGN_POS;
            long shiftedExponent = doubleExponent << DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH; //TODO: overflow case. Test this.
            long rawVal = doubleFraction | shiftedExponent | shiftedSignBit;
            return Double.longBitsToDouble(rawVal);
        }
    }

    public byte[] getBytes() {
        byte[] array = new byte[BYTE_WIDTH];
        ByteArraySupport.littleEndian().putLong(array, 0, fraction);
        ByteArraySupport.littleEndian().putLong(array, 8, expSignFraction);
        return array;
    }

    public static LLVM128BitFloat fromBytesBigEndian(byte[] bytes) {
        assert bytes.length == BYTE_WIDTH;
        long expSignFraction = ByteArraySupport.bigEndian().getLong(bytes, 0);
        long fraction = ByteArraySupport.bigEndian().getLong(bytes, 8);
        return new LLVM128BitFloat(expSignFraction, fraction);
    }

    public static LLVM128BitFloat fromBytes(byte[] bytes) {
        assert bytes.length == BYTE_WIDTH;
        long fraction = ByteArraySupport.littleEndian().getLong(bytes, 0);
        long expSignFraction = ByteArraySupport.littleEndian().getLong(bytes, 8);
        return new LLVM128BitFloat(expSignFraction, fraction);
    }

    public static LLVM128BitFloat fromInt(int val) {
        boolean sign = val < 0;
        return fromInt(val, sign);
    }

    private static LLVM128BitFloat fromInt(int val, boolean sign) {
        return fromLong(Math.abs(val), sign);
    }

    private static LLVM128BitFloat fromLong(long val, boolean sign) {
        int leadingOnePosition = Long.SIZE - Long.numberOfLeadingZeros(val);
        long exponent = EXPONENT_BIAS + (leadingOnePosition - 1);
        long shiftAmount = FRACTION_BIT_WIDTH - leadingOnePosition + 1;
        long fraction;
        long exponentFraction;
        if (shiftAmount >= Long.SIZE) { // Need to test both cases.
            exponentFraction = (exponent << EXPONENT_POSITION) | (val << (shiftAmount - Long.SIZE));
            fraction = 0;
        } else {
            exponentFraction = (exponent << EXPONENT_POSITION) | (val >> (Long.SIZE - shiftAmount));
            fraction = val << (shiftAmount);
        }
        return LLVM128BitFloat.fromRawValues(sign, fraction, exponentFraction);
    }

    public static LLVM128BitFloat fromFloat(float val) {
        return fromDouble(val);
    }

    public static LLVM128BitFloat fromDouble(double val) {
        boolean sign = val < 0;
        if (DoubleHelper.isPositiveZero(val)) {
            return new LLVM128BitFloat(POSITIVE_ZERO);
        } else if (DoubleHelper.isNegativeZero(val)) {
            return new LLVM128BitFloat(NEGATIVE_ZERO);
        } else {
            long rawValue = Double.doubleToRawLongBits(val);
            int doubleExponent = DoubleHelper.getUnbiasedExponent(val);
            int biasedExponent = doubleExponent + EXPONENT_BIAS;
            long doubleFraction = rawValue & DoubleHelper.FRACTION_MASK;
            long shiftAmount = FRACTION_BIT_WIDTH - DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH; //112 - 52 = 60
            long fraction = doubleFraction << (shiftAmount);
            long biasedExponentFraction = ((long) biasedExponent << EXPONENT_POSITION) | (doubleFraction >> (Long.SIZE - shiftAmount)); // 64 - 60 = 4
            return LLVM128BitFloat.fromRawValues(sign, fraction, biasedExponentFraction);
        }
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getBytes());
    }
}
