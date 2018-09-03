package com.oracle.truffle.espresso.intrinsics;

import java.lang.reflect.Field;

@EspressoIntrinsics
public class Target_java_util_concurrent_atomic_AtomicLong {
    @Intrinsic
    public static boolean VMSupportsCS8() {
        try {
            Class<?> klass = Class.forName("java.util.concurrent.atomic.AtomicLong");
            Field field = klass.getDeclaredField("VM_SUPPORTS_LONG_CAS");
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
            throw new RuntimeException("Cannot access host VMSupportsCS8");
        }
    }
}