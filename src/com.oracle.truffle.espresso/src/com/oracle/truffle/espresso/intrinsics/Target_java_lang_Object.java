package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.runtime.StaticObjectWrapper;
import com.oracle.truffle.espresso.runtime.Utils;

import java.util.Arrays;

@EspressoIntrinsics
public class Target_java_lang_Object {
    @Intrinsic(hasReceiver = true)
    public static int hashCode(Object self) {
        // (Identity) hash code must be respected for wrappers.
        // The same object could be wrapped by two different instances of StaticObjectWrapper.
        // Wrappers are transparent, it's identity comes from the wrapped object.
        Object target = (self instanceof StaticObjectWrapper) ? ((StaticObjectWrapper) self).getWrapped() : self;
        return System.identityHashCode(target);
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Class.class) StaticObject getClass(Object self) {
        if (self instanceof StaticObject) {
            return ((StaticObject) self).getKlass().mirror();
        }
        Meta meta = Utils.getContext().getMeta();
        if (self instanceof int[]) {
            return meta.INT.array().rawKlass().mirror();
        } else if (self instanceof byte[]) {
            return meta.BYTE.array().rawKlass().mirror();
        } else if (self instanceof boolean[]) {
            return meta.BOOLEAN.array().rawKlass().mirror();
        } else if (self instanceof long[]) {
            return meta.LONG.array().rawKlass().mirror();
        } else if (self instanceof float[]) {
            return meta.FLOAT.array().rawKlass().mirror();
        } else if (self instanceof double[]) {
            return meta.DOUBLE.array().rawKlass().mirror();
        } else if (self instanceof char[]) {
            return meta.CHAR.array().rawKlass().mirror();
        } else if (self instanceof short[]) {
            return meta.SHORT.array().rawKlass().mirror();
        }
        throw EspressoError.shouldNotReachHere(".getClass failed. Non-espresso object: " + self);
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
