package org.graalvm.compiler.truffle.pelang;

import org.graalvm.collections.EconomicMap;

public final class PELangState {

    private static final Object NULL = new Object();
    private static final EconomicMap<String, Object> GLOBALS = EconomicMap.create();
    
    public static Object getNullObject() {
        return NULL;
    }

    public static Object readGlobal(String identifier) {
        return getGlobal(identifier);
    }

    public static Object writeGlobal(String identifier, Object value) {
        GLOBALS.put(identifier, value);
        return value;
    }

    public static long readLongGlobal(String identifier) {
        return (long) getGlobal(identifier);
    }

    public static long writeLongGlobal(String identifier, long value) {
        GLOBALS.put(identifier, value);
        return value;
    }

    public static boolean isLongGlobal(String identifier) {
        return getGlobal(identifier) instanceof Long;
    }

    private static Object getGlobal(String identifier) {
        return GLOBALS.get(identifier, NULL);
    }

}
