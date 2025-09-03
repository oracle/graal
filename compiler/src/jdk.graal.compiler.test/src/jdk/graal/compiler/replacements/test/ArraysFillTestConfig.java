/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import jdk.graal.compiler.replacements.test.ArraysSubstitutionsTestBase.ArrayBuilder;

class ArraysFillTestConfig {
    private Class<?> type = null;
    private Object constant = null;
    private int length = 0;

    private Object[] parameterTypes = new Object[]{
                    new Class<?>[]{boolean[].class, boolean.class},
                    new Class<?>[]{byte[].class, byte.class},
                    new Class<?>[]{char[].class, char.class},
                    new Class<?>[]{short[].class, short.class},
                    new Class<?>[]{int[].class, int.class},
                    new Class<?>[]{long[].class, long.class},
                    new Class<?>[]{float[].class, float.class},
                    new Class<?>[]{double[].class, double.class}
    };

    private ArrayBuilder[] builders = new ArrayBuilder[]{
                    ArraysSubstitutionsTestBase::booleanArray,
                    ArraysSubstitutionsTestBase::byteArray,
                    ArraysSubstitutionsTestBase::charArray,
                    ArraysSubstitutionsTestBase::shortArray,
                    ArraysSubstitutionsTestBase::intArray,
                    ArraysSubstitutionsTestBase::longArray,
                    ArraysSubstitutionsTestBase::floatArray,
                    ArraysSubstitutionsTestBase::doubleArray,
    };

    private String[] testMethodNames = new String[]{
                    "arraysFillBoolean",
                    "arraysFillByte",
                    "arraysFillChar",
                    "arraysFillShort",
                    "arraysFillInt",
                    "arraysFillLong",
                    "arraysFillFloat",
                    "arraysFillDouble",
    };

    ArraysFillTestConfig(Class<?> type, int length, Object constant) {
        this.type = type;
        this.length = length;
        this.constant = constant;
    }

    public String testMethodName() throws IllegalArgumentException {
        return testMethodNames[index()];
    }

    public Object newArray() throws IllegalArgumentException {
        ArrayBuilder builder = builders[index()];
        return builder.newArray(this.length, 0, 0);
    }

    public Class<?>[] parameterType() throws IllegalArgumentException {
        return (Class<?>[]) parameterTypes[index()];
    }

    public Object getConstant() {
        return this.constant;
    }

    private int index() {
        if (type == boolean.class) {
            return 0;
        } else if (type == byte.class) {
            return 1;
        } else if (type == char.class) {
            return 2;
        } else if (type == short.class) {
            return 3;
        } else if (type == int.class) {
            return 4;
        } else if (type == long.class) {
            return 5;
        } else if (type == float.class) {
            return 6;
        } else if (type == double.class) {
            return 7;
        } else {
            throw new IllegalArgumentException("Unexpected type for 'type' field: " + type);
        }
    }
}
