package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.Utils;

@EspressoIntrinsics
public class Target_sun_reflect_Reflection {
    @Intrinsic
    public static @Type(Class.class) StaticObject getCallerClass() {
        return Utils.getCallerNode().getMethod().getDeclaringClass().mirror();
    }

    @Intrinsic
    public static int getClassAccessFlags(@Type(Class.class) StaticObjectClass clazz) {
        // TODO(peterssen): This is blatantly wrong, investigate access vs. modifiers.
        return clazz.getMirror().getModifiers();
    }
}
