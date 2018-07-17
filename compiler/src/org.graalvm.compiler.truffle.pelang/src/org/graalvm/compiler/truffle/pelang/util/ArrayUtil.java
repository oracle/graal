package org.graalvm.compiler.truffle.pelang.util;

import java.lang.reflect.Array;

public class ArrayUtil {

    public static Object unwrapArray(Object array, long[] indices, int limit) {
        Object value = array;

        for (int i = 0; i < limit; i++) {
            int index = (int) indices[i];
            value = readValue(value, index);
        }
        return value;
    }

    public static Object readValue(Object array, int index) {
        return Array.get(array, index);
    }

    public static void writeValue(Object array, int index, Object value) {
        Array.set(array, index, value);
    }

}
