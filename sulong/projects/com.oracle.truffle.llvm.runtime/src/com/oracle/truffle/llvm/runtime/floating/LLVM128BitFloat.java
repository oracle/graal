package com.oracle.truffle.llvm.runtime.floating;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;

public final class LLVM128BitFloat extends LLVMInternalTruffleObject {

    private static final int BIT_TO_HEX_FACTOR = 4;
    public static final long SIGN_BIT = (long) (1 << 15);
    private static final int EXPONENT_BIT_WIDTH = 15;
    private static final int FRACTION_BIT_WIDTH = 112;
    public static final long EXPONENT_MASK = 0b111111111111111; // 15 bit
    private static final int HEX_DIGITS_FRACTION = FRACTION_BIT_WIDTH / BIT_TO_HEX_FACTOR;
    private static final LLVM128BitFloat POSITIVE_INFINITY = LLVM128BitFloat.fromRawValues(false, EXPONENT_MASK, bit(112L));
    private static final LLVM128BitFloat NEGATIVE_INFINITY = LLVM128BitFloat.fromRawValues(true, EXPONENT_MASK, bit(112L));


    @Override
    public String toString() {
        return toLLVMString(this);
    }

    @CompilerDirectives.TruffleBoundary
    public String toDebugString() {
        return String.format("sign: %s\nexponent: %s\nfraction: %s %s\n", getSign(), getBinaryString(EXPONENT_BIT_WIDTH, getExponent()), getBinaryString(FRACTION_BIT_WIDTH, getFraction()),
                getHexString(HEX_DIGITS_FRACTION, getFraction()));
    }

    @CompilerDirectives.TruffleBoundary
    public static String toLLVMString(LLVM128BitFloat value) {
        /*if (value.isQNaN()) {
            return "QNaN";

        } else if (value.isSNaN()) {
            return "SNaN";

        } else*/ if (value.isInfinity()) {
            return "INF";

        } else {
            long exponent = value.getExponent();
            if (value.getSign()) {
                exponent |= (1 << EXPONENT_BIT_WIDTH);
            }
            long fraction = value.getFraction();
            return String.format("0xK%4x%16x", exponent, fraction).replace(' ', '0');
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


    private static final int FP128_EXPONENT_BIAS = 16383;
    private static final int DOUBLE_EXPONENT_BIAS = 1023;

    private final long expSignFraction; // 64 bit
    private final long fraction; // 64 bit

    public LLVM128BitFloat(long expSignFraction, long fraction) {
        this.expSignFraction = expSignFraction;
        this.fraction = fraction;
    }

    public static LLVM128BitFloat fromRawValues(boolean sign, long exponent, long fraction) {
        assert (exponent & 0x7FFFF) == exponent;
        long expSignFraction = exponent;
        if (sign) {
            expSignFraction |= SIGN_BIT;
        }
        return new LLVM128BitFloat(expSignFraction, fraction);
    }

    public long getExponent() {
        return (long) (expSignFraction & EXPONENT_MASK);
    }

    public long getFraction() {
        return fraction;
    }

    public long getExpSign() {
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

    public static long bit(long i) {
        return 1L << i;
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


}
