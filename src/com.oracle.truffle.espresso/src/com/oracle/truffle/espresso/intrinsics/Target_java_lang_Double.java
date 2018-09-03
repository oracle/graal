package com.oracle.truffle.espresso.intrinsics;

@EspressoIntrinsics
public class Target_java_lang_Double {
    @Intrinsic
    public static long doubleToRawLongBits(double value) {
        return Double.doubleToRawLongBits(value);
    }

    @Intrinsic
    public static double longBitsToDouble(long bits) {
        return Double.longBitsToDouble(bits);
    }
}