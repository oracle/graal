package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.Utils;

@EspressoIntrinsics
public class Target_java_lang_reflect_Array {

    @Intrinsic
    public static Object newArray(@Type(Class.class) StaticObjectClass componentType, int length) { // throws NegativeArraySizeException
        return Utils.getContext().getVm().newArray(componentType.getMirror(), length);
    }
}
