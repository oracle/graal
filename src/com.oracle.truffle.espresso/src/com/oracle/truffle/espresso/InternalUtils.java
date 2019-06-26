package com.oracle.truffle.espresso;

import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Utility class to spy Espresso's underlying representation from the guest. Mostly used to validate
 * Espresso's design choices from a guest point of view.
 * 
 * @see StaticObject
 */
public class InternalUtils {
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /**
     * @param obj the object you want to see the underlying representation of primitive fields.
     * @return a copy of how primitive fields are represented in memory.
     */
    public static byte[] getUnderlyingFieldArray(@SuppressWarnings("unused") Object obj) {
        return EMPTY_BYTE_ARRAY;
    }

    /**
     * Returns a String representing all (even private ones) known fields in obj, except for hidden
     * ones.
     * 
     * @param obj the object you want to represent.
     * @return The representation of the obj.
     */
    public static String toVerboseString(@SuppressWarnings("unused") Object obj) {
        return obj.toString();
    }

    /**
     * Returns the total number of bytes an instance of clazz takes up in memory.
     * 
     * @param clazz the class you want to know the memory consumption of
     * @return the total number of bytes used by an instance of clazz.
     */
    public static int bytesUsed(Class<?> clazz) {
        return 0;
    }

    /**
     * Checks whether or not we are running in espresso.
     * 
     * @return true iff we are running in Espresso.
     */
    public static boolean inEspresso() {
        return false;
    }
}
