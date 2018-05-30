package org.graalvm.compiler.truffle.pelang.util;

import org.graalvm.collections.EconomicMap;

public class ClassUtil {

    private static final EconomicMap<Class<?>, Class<?>> wrappers = EconomicMap.create();

    static {
        wrappers.put(boolean.class, Boolean.class);
        wrappers.put(byte.class, Byte.class);
        wrappers.put(char.class, Character.class);
        wrappers.put(short.class, Short.class);
        wrappers.put(int.class, Integer.class);
        wrappers.put(float.class, Float.class);
        wrappers.put(long.class, Long.class);
        wrappers.put(double.class, Double.class);
    }

    public static boolean isAssignableTo(Class<?> from, Class<?> to) {
        if (to.isAssignableFrom(from)) {
            return true;
        } else if (from.isPrimitive()) {
            return isWrapper(from, to);
        } else if (to.isPrimitive()) {
            return isWrapper(to, from);
        } else {
            return false;
        }
    }

    private static boolean isWrapper(Class<?> primitive, Class<?> wrapper) {
        if (!primitive.isPrimitive()) {
            throw new IllegalArgumentException("given parameter must represent a primitive type");
        }
        return wrappers.get(primitive) == wrapper;
    }

}
