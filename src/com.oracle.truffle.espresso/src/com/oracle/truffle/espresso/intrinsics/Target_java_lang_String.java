package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

@EspressoIntrinsics
public class Target_java_lang_String {
    @Intrinsic(hasReceiver = true)
    public static @Type(String.class) StaticObject intern(@Type(String.class) StaticObject self) {
        return Utils.getVm().intern(self);
    }
}
