package com.oracle.truffle.espresso.jni;

import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Retains one exception per thread that is pending to be handled in that thread (or none).
 */
public class JniThreadLocalPendingException {
    private static final ThreadLocal<StaticObject> pendingException = new ThreadLocal<>();

    public static StaticObject get() {
        return pendingException.get();
    }

    public static void set(StaticObject t) {
        pendingException.set(t);
    }

    public static void clear() {
        set(null);
    }
}
