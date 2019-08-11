package com.oracle.truffle.wasm.benchmark.util;

public class Assert {

    public static void assertTrue(boolean cond, String message) {
        if (!cond) {
            fail(message);
        }
    }

    public static void assertNotNull(Object object, String message) {
        if (object == null) {
            fail(message);
        }
    }

    public static void fail(String message) {
        throw new RuntimeException(message);
    }
}
