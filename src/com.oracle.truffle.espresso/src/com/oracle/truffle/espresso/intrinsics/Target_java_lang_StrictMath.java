package com.oracle.truffle.espresso.intrinsics;

@EspressoIntrinsics
public class Target_java_lang_StrictMath {
    @Intrinsic
    public static double log(double a) {
        return StrictMath.log(a);
    }
}
