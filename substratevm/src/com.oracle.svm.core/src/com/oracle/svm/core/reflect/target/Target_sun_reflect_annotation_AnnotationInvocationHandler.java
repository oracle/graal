/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect.target;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "sun.reflect.annotation.AnnotationInvocationHandler")
final class Target_sun_reflect_annotation_AnnotationInvocationHandler {

    @Alias
    static native String toSourceString(Class<?> clazz);

    @Alias
    static native String toSourceString(float clazz);

    @Alias
    static native String toSourceString(double clazz);

    @Alias
    static native String toSourceString(byte clazz);

    @Alias
    static native String toSourceString(char clazz);

    @Alias
    static native String toSourceString(long clazz);

    @Alias
    static native String toSourceString(String clazz);

    /* Prevent streams from ending up in simple images */
    @Substitute
    private static String memberValueToString(Object value) {
        Class<?> type = value.getClass();
        if (!type.isArray()) {
            // primitive value, string, class, enum const, or annotation
            if (type == Class.class) {
                return toSourceString((Class<?>) value);
            } else if (type == String.class) {
                return toSourceString((String) value);
            } else if (type == Character.class) {
                return toSourceString((char) value);
            } else if (type == Double.class) {
                return toSourceString((double) value);
            } else if (type == Float.class) {
                return toSourceString((float) value);
            } else if (type == Long.class) {
                return toSourceString((long) value);
            } else if (type == Byte.class) {
                return toSourceString((byte) value);
            } else {
                return value.toString();
            }
        } else {
            List<String> stringList;
            if (type == byte[].class) {
                stringList = Util_sun_reflect_annotation_AnnotationInvocationHandler.convert((byte[]) value);
            } else if (type == char[].class) {
                stringList = Util_sun_reflect_annotation_AnnotationInvocationHandler.convert((char[]) value);
            } else if (type == double[].class) {
                stringList = Util_sun_reflect_annotation_AnnotationInvocationHandler.convert((double[]) value);
            } else if (type == float[].class) {
                stringList = Util_sun_reflect_annotation_AnnotationInvocationHandler.convert((float[]) value);
            } else if (type == int[].class) {
                stringList = Util_sun_reflect_annotation_AnnotationInvocationHandler.convert((int[]) value);
            } else if (type == long[].class) {
                stringList = Util_sun_reflect_annotation_AnnotationInvocationHandler.convert((long[]) value);
            } else if (type == short[].class) {
                stringList = Util_sun_reflect_annotation_AnnotationInvocationHandler.convert((short[]) value);
            } else if (type == boolean[].class) {
                stringList = Util_sun_reflect_annotation_AnnotationInvocationHandler.convert((boolean[]) value);
            } else if (type == Class[].class) {
                stringList = Util_sun_reflect_annotation_AnnotationInvocationHandler.convert((Class<?>[]) value);
            } else if (type == String[].class) {
                stringList = Util_sun_reflect_annotation_AnnotationInvocationHandler.convert((String[]) value);
            } else {
                stringList = Util_sun_reflect_annotation_AnnotationInvocationHandler.convert((Object[]) value);
            }

            return Util_sun_reflect_annotation_AnnotationInvocationHandler.stringListToString(stringList);
        }
    }
}

class Util_sun_reflect_annotation_AnnotationInvocationHandler {
    static List<String> convert(boolean[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (boolean b : values) {
            list.add(Boolean.toString(b));
        }
        return list;
    }

    static List<String> convert(byte[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (byte b : values) {
            list.add(Target_sun_reflect_annotation_AnnotationInvocationHandler.toSourceString(b));
        }
        return list;
    }

    static List<String> convert(short[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (short s : values) {
            list.add(Short.toString(s));
        }
        return list;
    }

    static List<String> convert(char[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (char c : values) {
            list.add(Target_sun_reflect_annotation_AnnotationInvocationHandler.toSourceString(c));
        }
        return list;
    }

    static List<String> convert(int[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (int i : values) {
            list.add(String.valueOf(i));
        }
        return list;
    }

    static List<String> convert(long[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (long l : values) {
            list.add(Target_sun_reflect_annotation_AnnotationInvocationHandler.toSourceString(l));
        }
        return list;
    }

    static List<String> convert(float[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (float f : values) {
            list.add(Target_sun_reflect_annotation_AnnotationInvocationHandler.toSourceString(f));
        }
        return list;
    }

    static List<String> convert(double[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (double d : values) {
            list.add(Target_sun_reflect_annotation_AnnotationInvocationHandler.toSourceString(d));
        }
        return list;
    }

    static List<String> convert(Class<?>[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (Class<?> clazz : values) {
            list.add(Target_sun_reflect_annotation_AnnotationInvocationHandler.toSourceString(clazz));
        }
        return list;
    }

    static List<String> convert(String[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (String string : values) {
            list.add(Target_sun_reflect_annotation_AnnotationInvocationHandler.toSourceString(string));
        }
        return list;
    }

    static List<String> convert(Object[] values) {
        List<String> list = new ArrayList<>(values.length);
        for (Object obj : values) {
            list.add(Objects.toString(obj));
        }
        return list;
    }

    static String stringListToString(List<String> list) {
        StringBuilder string = new StringBuilder();
        string.append("{");
        for (int i = 0; i < list.size(); ++i) {
            string.append(list.get(i));
            if (i < list.size() - 1) {
                string.append(", ");
            }
        }
        string.append("}");
        return string.toString();
    }
}
