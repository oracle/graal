package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoIntrinsics
public class Target_java_util_jar_JarFile {
    @Intrinsic(hasReceiver = true)
    public static @Type(String[].class) StaticObject getMetaInfEntryNames(Object self) {
        return StaticObject.NULL;
    }
}
