package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoIntrinsics
public class Target_sun_launcher_LauncherHelper {

    @Intrinsic
    public static void validateMainClass(@Type(Class.class) StaticObject clazz) {
        // TODO(peterssen): Verifies the class contains a main(String[]) method.
        // To avoid going down the reflection hole e.g. Class.getMethods()
        // make this a nop.
    }
}
