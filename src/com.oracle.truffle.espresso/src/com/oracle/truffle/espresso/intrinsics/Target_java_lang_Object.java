package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

@EspressoIntrinsics
public class Target_java_lang_Object {
    @Intrinsic(hasReceiver = true)
    public static int hashCode(Object obj) {
        // TODO(peterssen): Check for primitive arrays.
        return obj.hashCode();
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Class.class) StaticObject getClass(Object self) {
        // TODO(peterssen): Check for primitive arrays.
        return ((StaticObject) self).getKlass().mirror();
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Object.class) Object clone(Object self) {
        // TODO(peterssen): self must implement Cloneable
        if (self instanceof StaticObjectArray) {
            // For arrays.
            return ((StaticObjectArray) self).clone();
        }

        if (self instanceof int[]) {
            return ((int[]) self).clone();
        } else if (self instanceof byte[]) {
            return ((byte[]) self).clone();
        } else if (self instanceof boolean[]) {
            return ((boolean[]) self).clone();
        } else if (self instanceof long[]) {
            return ((long[]) self).clone();
        } else if (self instanceof float[]) {
            return ((float[]) self).clone();
        } else if (self instanceof double[]) {
            return ((double[]) self).clone();
        } else if (self instanceof char[]) {
            return ((char[]) self).clone();
        } else if (self instanceof short[]) {
            return ((short[]) self).clone();
        }

        // Normal object just copy the
        return ((StaticObjectImpl) self).clone();
    }

    @Intrinsic
    public static void registerNatives() {
        /* nop */
    }

    @Intrinsic(hasReceiver = true)
    public static void notifyAll(Object self) {
        /* nop */
    }
}
