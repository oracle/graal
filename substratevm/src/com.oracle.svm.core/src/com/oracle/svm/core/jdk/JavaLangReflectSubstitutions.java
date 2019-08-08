/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.jdk;

// Checkstyle: allow reflection

import java.lang.reflect.Array;

import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

@TargetClass(java.lang.reflect.Array.class)
final class Target_java_lang_reflect_Array {

    @Substitute
    private static int getLength(Object array) {
        if (array == null) {
            throw new NullPointerException();
        } else if (!array.getClass().isArray()) {
            throw new IllegalArgumentException();
        }
        return KnownIntrinsics.readArrayLength(array);
    }

    @Substitute
    private static boolean getBoolean(Object array, int index) {
        if (array instanceof boolean[]) {
            return ((boolean[]) array)[index];
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static void setBoolean(Object array, int index, boolean value) {
        if (array instanceof boolean[]) {
            ((boolean[]) array)[index] = value;
            return;
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static byte getByte(Object array, int index) {
        if (array instanceof byte[]) {
            return ((byte[]) array)[index];
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static void setByte(Object array, int index, byte value) {
        if (array instanceof byte[]) {
            ((byte[]) array)[index] = value;
            return;
        } else if (array instanceof short[]) {
            ((short[]) array)[index] = value;
            return;
        } else if (array instanceof int[]) {
            ((int[]) array)[index] = value;
            return;
        } else if (array instanceof long[]) {
            ((long[]) array)[index] = value;
            return;
        } else if (array instanceof float[]) {
            ((float[]) array)[index] = value;
            return;
        } else if (array instanceof double[]) {
            ((double[]) array)[index] = value;
            return;
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static char getChar(Object array, int index) {
        if (array instanceof char[]) {
            return ((char[]) array)[index];
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static void setChar(Object array, int index, char value) {
        if (array instanceof char[]) {
            ((char[]) array)[index] = value;
            return;
        } else if (array instanceof int[]) {
            ((int[]) array)[index] = value;
            return;
        } else if (array instanceof long[]) {
            ((long[]) array)[index] = value;
            return;
        } else if (array instanceof float[]) {
            ((float[]) array)[index] = value;
            return;
        } else if (array instanceof double[]) {
            ((double[]) array)[index] = value;
            return;
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static short getShort(Object array, int index) {
        if (array instanceof byte[]) {
            return ((byte[]) array)[index];
        } else if (array instanceof short[]) {
            return ((short[]) array)[index];
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static void setShort(Object array, int index, short value) {
        if (array instanceof short[]) {
            ((short[]) array)[index] = value;
            return;
        } else if (array instanceof int[]) {
            ((int[]) array)[index] = value;
            return;
        } else if (array instanceof long[]) {
            ((long[]) array)[index] = value;
            return;
        } else if (array instanceof float[]) {
            ((float[]) array)[index] = value;
            return;
        } else if (array instanceof double[]) {
            ((double[]) array)[index] = value;
            return;
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static int getInt(Object array, int index) {
        if (array instanceof byte[]) {
            return ((byte[]) array)[index];
        } else if (array instanceof short[]) {
            return ((short[]) array)[index];
        } else if (array instanceof char[]) {
            return ((char[]) array)[index];
        } else if (array instanceof int[]) {
            return ((int[]) array)[index];
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static void setInt(Object array, int index, int value) {
        if (array instanceof int[]) {
            ((int[]) array)[index] = value;
            return;
        } else if (array instanceof long[]) {
            ((long[]) array)[index] = value;
            return;
        } else if (array instanceof float[]) {
            ((float[]) array)[index] = value;
            return;
        } else if (array instanceof double[]) {
            ((double[]) array)[index] = value;
            return;
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static long getLong(Object array, int index) {
        if (array instanceof byte[]) {
            return ((byte[]) array)[index];
        } else if (array instanceof short[]) {
            return ((short[]) array)[index];
        } else if (array instanceof char[]) {
            return ((char[]) array)[index];
        } else if (array instanceof int[]) {
            return ((int[]) array)[index];
        } else if (array instanceof long[]) {
            return ((long[]) array)[index];
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static void setLong(Object array, int index, long value) {
        if (array instanceof long[]) {
            ((long[]) array)[index] = value;
            return;
        } else if (array instanceof float[]) {
            ((float[]) array)[index] = value;
            return;
        } else if (array instanceof double[]) {
            ((double[]) array)[index] = value;
            return;
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static float getFloat(Object array, int index) {
        if (array instanceof byte[]) {
            return ((byte[]) array)[index];
        } else if (array instanceof short[]) {
            return ((short[]) array)[index];
        } else if (array instanceof char[]) {
            return ((char[]) array)[index];
        } else if (array instanceof int[]) {
            return ((int[]) array)[index];
        } else if (array instanceof long[]) {
            return ((long[]) array)[index];
        } else if (array instanceof float[]) {
            return ((float[]) array)[index];
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static void setFloat(Object array, int index, float value) {
        if (array instanceof float[]) {
            ((float[]) array)[index] = value;
            return;
        } else if (array instanceof double[]) {
            ((double[]) array)[index] = value;
            return;
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static double getDouble(Object array, int index) {
        if (array instanceof byte[]) {
            return ((byte[]) array)[index];
        } else if (array instanceof short[]) {
            return ((short[]) array)[index];
        } else if (array instanceof char[]) {
            return ((char[]) array)[index];
        } else if (array instanceof int[]) {
            return ((int[]) array)[index];
        } else if (array instanceof long[]) {
            return ((long[]) array)[index];
        } else if (array instanceof float[]) {
            return ((float[]) array)[index];
        } else if (array instanceof double[]) {
            return ((double[]) array)[index];
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static void setDouble(Object array, int index, double value) {
        if (array instanceof double[]) {
            ((double[]) array)[index] = value;
            return;
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static Object get(Object array, int index) {
        if (array instanceof boolean[]) {
            return ((boolean[]) array)[index];
        } else if (array instanceof byte[]) {
            return ((byte[]) array)[index];
        } else if (array instanceof char[]) {
            return ((char[]) array)[index];
        } else if (array instanceof short[]) {
            return ((short[]) array)[index];
        } else if (array instanceof char[]) {
            return ((char[]) array)[index];
        } else if (array instanceof int[]) {
            return ((int[]) array)[index];
        } else if (array instanceof long[]) {
            return ((long[]) array)[index];
        } else if (array instanceof float[]) {
            return ((float[]) array)[index];
        } else if (array instanceof double[]) {
            return ((double[]) array)[index];
        } else if (array instanceof Object[]) {
            return ((Object[]) array)[index];
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static void set(Object array, int index, Object value) {
        if (array instanceof boolean[]) {
            if (value instanceof Boolean) {
                ((boolean[]) array)[index] = ((Boolean) value).booleanValue();
                return;
            }
        } else if (array instanceof byte[]) {
            if (value instanceof Byte) {
                ((byte[]) array)[index] = ((Number) value).byteValue();
                return;
            }
        } else if (array instanceof char[]) {
            if (value instanceof Character) {
                ((char[]) array)[index] = ((Character) value).charValue();
                return;
            }
        } else if (array instanceof short[]) {
            if (value instanceof Byte || value instanceof Short) {
                ((short[]) array)[index] = ((Number) value).shortValue();
                return;
            }
        } else if (array instanceof int[]) {
            if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
                ((int[]) array)[index] = ((Number) value).intValue();
                return;
            } else if (value instanceof Character) {
                ((int[]) array)[index] = ((Character) value).charValue();
                return;
            }
        } else if (array instanceof long[]) {
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                ((long[]) array)[index] = ((Number) value).longValue();
                return;
            } else if (value instanceof Character) {
                ((long[]) array)[index] = ((Character) value).charValue();
                return;
            }
        } else if (array instanceof float[]) {
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float) {
                ((float[]) array)[index] = ((Number) value).floatValue();
                return;
            } else if (value instanceof Character) {
                ((float[]) array)[index] = ((Character) value).charValue();
                return;
            }
        } else if (array instanceof double[]) {
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double) {
                ((double[]) array)[index] = ((Number) value).doubleValue();
                return;
            } else if (value instanceof Character) {
                ((double[]) array)[index] = ((Character) value).charValue();
                return;
            }
        } else if (array instanceof Object[]) {
            if (value == null || array.getClass().getComponentType().isAssignableFrom(value.getClass())) {
                ((Object[]) array)[index] = value;
                return;
            }
        }
        throw new IllegalArgumentException();
    }

    @Substitute
    private static Object multiNewArray(Class<?> componentType, int[] dimensions) {
        if (componentType == null) {
            throw new NullPointerException();
        } else if (dimensions.length == 0 || componentType == void.class) {
            throw new IllegalArgumentException();
        }
        for (int i = 0; i < dimensions.length; i++) {
            if (dimensions[i] < 0) {
                throw new NegativeArraySizeException();
            }
        }

        // get the ultimate outer array type
        DynamicHub arrayHub = DynamicHub.fromClass(componentType);
        for (int i = 0; i < dimensions.length; i++) {
            arrayHub = arrayHub.getArrayHub();
            if (arrayHub == null) {
                throw VMError.unsupportedFeature("Cannot allocate " + dimensions.length + "-dimensional array of " + componentType.getName());
            }
        }

        return Util_java_lang_reflect_Array.createMultiArrayAtIndex(0, arrayHub, dimensions);
    }
}

final class Util_java_lang_reflect_Array {

    static Object createMultiArrayAtIndex(int index, DynamicHub arrayHub, int[] dimensions) {
        final int length = dimensions[index];
        final Object result = Array.newInstance(DynamicHub.toClass(arrayHub.getComponentHub()), length);

        final int nextIndex = index + 1;
        if (nextIndex < dimensions.length && length > 0) {
            DynamicHub subArrayHub = arrayHub.getComponentHub();

            UnsignedWord offset = LayoutEncoding.getArrayBaseOffset(arrayHub.getLayoutEncoding());
            UnsignedWord endOffset = LayoutEncoding.getArrayElementOffset(arrayHub.getLayoutEncoding(), length);
            while (offset.belowThan(endOffset)) {
                Object subArray = createMultiArrayAtIndex(nextIndex, subArrayHub, dimensions);
                // Each subArray could create a cross-generational reference.
                BarrieredAccess.writeObject(result, offset, subArray);
                offset = offset.add(ConfigurationValues.getObjectLayout().getReferenceSize());
            }
        }
        return result;
    }
}

/** Dummy class to have a class with the file's name. */
public final class JavaLangReflectSubstitutions {
}
