package com.oracle.truffle.espresso.vm;

import com.oracle.truffle.espresso.meta.EspressoError;
import sun.misc.Unsafe;

public final class UnsafeAccess {
    private static final Unsafe UNSAFE;

    private UnsafeAccess() {
        /* no instances */
    }

    static {
        try {
            java.lang.reflect.Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public static Unsafe get() {
        return UNSAFE;
    }
}
