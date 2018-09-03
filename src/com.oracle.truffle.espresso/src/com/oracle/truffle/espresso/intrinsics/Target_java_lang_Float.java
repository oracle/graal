package com.oracle.truffle.espresso.intrinsics;

@EspressoIntrinsics
public class Target_java_lang_Float {
    @Intrinsic
    public static int floatToRawIntBits(float value) {
        return Float.floatToRawIntBits(value);
    }
}
