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

public final class TagConstants {

    public static final byte ARRAY = 91;	        // '[' - an array object (objectID size).
    public static final byte BYTE = 66;	            // 'B' - a byte value (1 byte).
    public static final byte CHAR = 67;	            // 'C' - a character value (2 bytes).
    public static final byte OBJECT = 76;	        // 'L' - an object (objectID size).
    public static final byte FLOAT = 70;	        // 'F' - a float value (4 bytes).
    public static final byte DOUBLE = 68;	        // 'D' - a double value (8 bytes).
    public static final byte INT = 73;	            // 'I' - an int value (4 bytes).
    public static final byte LONG = 74;	            // 'J' - a long value (8 bytes).
    public static final byte SHORT = 83;	        // 'S' - a short value (2 bytes).
    public static final byte VOID = 86;	            // 'V' - a void value (no bytes).
    public static final byte BOOLEAN = 90;	        // 'Z' - a boolean value (1 byte).
    public static final byte STRING = 115;	        // 's' - a String object (objectID size).
    public static final byte THREAD = 116;	        // 't' - a Thread object (objectID size).
    public static final byte THREAD_GROUP = 103;	// 'g' - a ThreadGroup object (objectID size).
    public static final byte CLASS_LOADER = 108;	// 'l' - a ClassLoader object (objectID size).
    public static final byte CLASS_OBJECT = 99;     // 'c' - a class object object (objectID size).

    private TagConstants() {}

    public static boolean isPrimitive(byte tag) {
        return
                tag != OBJECT &&
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
}
