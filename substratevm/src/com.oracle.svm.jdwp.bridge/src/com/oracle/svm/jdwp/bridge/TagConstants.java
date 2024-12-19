/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.bridge;

import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaKind;

public final class TagConstants {

    public static final byte ARRAY = '[';
    public static final byte BYTE = 'B';
    public static final byte CHAR = 'C';
    public static final byte OBJECT = 'L';
    public static final byte FLOAT = 'F';
    public static final byte DOUBLE = 'D';
    public static final byte INT = 'I';
    public static final byte LONG = 'J';
    public static final byte SHORT = 'S';
    public static final byte VOID = 'V';
    public static final byte BOOLEAN = 'Z';
    public static final byte STRING = 's';
    public static final byte THREAD = 't';
    public static final byte THREAD_GROUP = 'g';
    public static final byte CLASS_LOADER = 'l';
    public static final byte CLASS_OBJECT = 'c';

    private TagConstants() {
    }

    public static boolean isPrimitive(byte tag) {
        return tag != OBJECT &&
                        tag != STRING &&
                        tag != ARRAY &&
                        tag != THREAD &&
                        tag != THREAD_GROUP &&
                        tag != CLASS_OBJECT &&
                        tag != CLASS_LOADER;
    }

    public static byte getTagFromPrimitive(Object boxed) {
        if (boxed instanceof Integer) {
            return INT;
        }
        if (boxed instanceof Float) {
            return FLOAT;
        }
        if (boxed instanceof Double) {
            return DOUBLE;
        }
        if (boxed instanceof Long) {
            return LONG;
        }
        if (boxed instanceof Byte) {
            return BYTE;
        }
        if (boxed instanceof Short) {
            return SHORT;
        }
        if (boxed instanceof Character) {
            return CHAR;
        }
        if (boxed instanceof Boolean) {
            return BOOLEAN;
        }
        throw new RuntimeException("Boxed object: " + boxed.getClass() + " is not a primitive");
    }

    public static Class<?> getClassOfPrimitiveTag(int tag) {
        return switch (tag) {
            case INT -> Integer.TYPE;
            case FLOAT -> Float.TYPE;
            case DOUBLE -> Double.TYPE;
            case LONG -> Long.TYPE;
            case BYTE -> Byte.TYPE;
            case SHORT -> Short.TYPE;
            case CHAR -> Character.TYPE;
            case BOOLEAN -> Boolean.TYPE;
            default -> throw new IllegalArgumentException(Integer.toString(tag));
        };
    }

    public static boolean isValidTag(byte tag) {
        return switch (tag) {
            case ARRAY, BYTE, CHAR, OBJECT, FLOAT, DOUBLE, INT, LONG, SHORT, VOID, BOOLEAN, STRING, THREAD, THREAD_GROUP, CLASS_LOADER, CLASS_OBJECT -> true;
            default -> false;
        };
    }

    public static byte getTagFromClass(Class<?> clazz) {
        if (clazz.isArray()) {
            return ARRAY;
        }
        if (clazz.isPrimitive()) {
            if (clazz == Integer.TYPE) {
                return INT;
            }
            if (clazz == Float.TYPE) {
                return FLOAT;
            }
            if (clazz == Double.TYPE) {
                return DOUBLE;
            }
            if (clazz == Long.TYPE) {
                return LONG;
            }
            if (clazz == Byte.TYPE) {
                return BYTE;
            }
            if (clazz == Short.TYPE) {
                return SHORT;
            }
            if (clazz == Character.TYPE) {
                return CHAR;
            }
            if (clazz == Boolean.TYPE) {
                return BOOLEAN;
            }
            if (clazz == Void.TYPE) {
                return VOID;
            }
            throw VMError.shouldNotReachHere("Unknown primitive class: " + clazz.getName());
        } else {
            if (clazz == String.class) {
                return STRING;
            }
            if (clazz == Class.class) {
                return CLASS_OBJECT;
            }
            // These can be sub-classed, check for subtypes.
            if (Thread.class.isAssignableFrom(clazz)) {
                return THREAD;
            }
            if (ThreadGroup.class.isAssignableFrom(clazz)) {
                return THREAD_GROUP;
            }
            if (ClassLoader.class.isAssignableFrom(clazz)) {
                return CLASS_LOADER;
            }
            return OBJECT;
        }
    }

    public static byte getTagFromReference(Object ref) {
        if (ref == null) {
            return OBJECT;
        }
        return getTagFromClass(ref.getClass());
    }

    public static JavaKind tagToKind(byte tag) {
        return switch (tag) {
            case BYTE -> JavaKind.Byte;
            case CHAR -> JavaKind.Char;
            case FLOAT -> JavaKind.Float;
            case DOUBLE -> JavaKind.Double;
            case INT -> JavaKind.Int;
            case LONG -> JavaKind.Long;
            case SHORT -> JavaKind.Short;
            case VOID -> JavaKind.Void;
            case BOOLEAN -> JavaKind.Boolean;
            case OBJECT, ARRAY, STRING, THREAD, THREAD_GROUP, CLASS_LOADER, CLASS_OBJECT -> JavaKind.Object;
            default -> throw VMError.shouldNotReachHere("unreachable");
        };
    }
}
