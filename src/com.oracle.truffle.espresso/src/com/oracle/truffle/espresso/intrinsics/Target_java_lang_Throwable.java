package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoIntrinsics
public class Target_java_lang_Throwable {
    @Intrinsic(hasReceiver = true)
    public static @Type(Throwable.class) StaticObject fillInStackTrace(@Type(Throwable.class) StaticObject self, int dummy) {
        /* nop */
        return self;
    }
}
