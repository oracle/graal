package com.oracle.truffle.espresso;

public class InternalUtils {
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    public static byte[] getUnderlyingFieldArray(@SuppressWarnings("unused") Object obj) {
        return EMPTY_BYTE_ARRAY;
    }

    public static String toVerboseString(@SuppressWarnings("unused") Object obj) {
        return obj.toString();
    }
}
