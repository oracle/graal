package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoIntrinsics
public class Target_sun_misc_VM {
    @Intrinsic
    public static boolean isSystemDomainLoader(@Type(ClassLoader.class) StaticObject cl) {
        return true;
    }

    @Intrinsic
    public static void initialize() {
        /* nop */
    }
}
