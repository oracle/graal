/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.jdwp.api;

import com.oracle.truffle.espresso.classfile.JavaKind;

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
        throw new RuntimeException("boxed object: " + boxed.getClass() + " is not a primitive");
    }

    public static byte toTagConstant(JavaKind kind) {
        switch (kind) {
            case Boolean:
                return TagConstants.BOOLEAN;
            case Byte:
                return TagConstants.BYTE;
            case Short:
                return TagConstants.SHORT;
            case Char:
                return TagConstants.CHAR;
            case Int:
                return TagConstants.INT;
            case Float:
                return TagConstants.FLOAT;
            case Long:
                return TagConstants.LONG;
            case Double:
                return TagConstants.DOUBLE;
            case Object:
                return TagConstants.OBJECT;
            default:
                return -1;
        }
    }
}
