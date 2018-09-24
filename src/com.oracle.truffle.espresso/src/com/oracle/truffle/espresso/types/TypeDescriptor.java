/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.types;

import com.oracle.truffle.espresso.meta.JavaKind;

/**
 * A string description of a Java runtime type, e.g. a field's type, see #4.3.2.
 */
public final class TypeDescriptor extends Descriptor {

    TypeDescriptor(String string) {
        super(string);
    }

    public static String stringToJava(String string) {
        switch (string.charAt(0)) {
            // @formatter: off
            case 'L':
                return dottified(string.substring(1, string.length() - 1));
            case '[':
                return stringToJava(string.substring(1)) + "[]";
            case 'B':
                return "byte";
            case 'C':
                return "char";
            case 'D':
                return "double";
            case 'F':
                return "float";
            case 'I':
                return "int";
            case 'J':
                return "long";
            case 'S':
                return "short";
            case 'V':
                return "void";
            case 'Z':
                return "boolean";
            default:
                throw new InternalError("invalid type descriptor: " + "\"" + string + "\"");
                // @formatter: on
        }
    }

    public static String fromJavaName(String javaName) {
        if (javaName.endsWith("[]")) {
            return "[" + fromJavaName(javaName.substring(0, javaName.length() - 2));
        }
        switch (javaName) {
            case "byte":
                return "B";
            case "char":
                return "C";
            case "double":
                return "D";
            case "float":
                return "F";
            case "int":
                return "I";
            case "long":
                return "J";
            case "short":
                return "S";
            case "void":
                return "V";
            case "boolean":
                return "Z";
            default:
                // Reference descriptor.
                return "L" + slashified(javaName) + ";";
        }

    }

    public boolean isPrimitive() {
        if (value.length() != 1) {
            return false;
        }
        switch (value.charAt(0)) {
            case 'B': // byte
            case 'C': // char
            case 'D': // double
            case 'F': // float
            case 'I': // int
            case 'J': // long
            case 'S': // short
            case 'V': // void
            case 'Z': // boolean
                return true;
        }
        return false;
    }

    public String toJavaName() {
        return stringToJava(toString());
    }

    /**
     * Gets the kind denoted by this type descriptor.
     *
     * @return the kind denoted by this type descriptor
     */
    public JavaKind toKind() {
        if (value.length() == 1) {
            return JavaKind.fromPrimitiveOrVoidTypeChar(value.charAt(0));
        }

        return JavaKind.Object;
    }

    /**
     * Gets the number of array dimensions in this type descriptor.
     */
    public int getArrayDimensions() {
        int dimensions = 0;
        while (value.charAt(dimensions) == '[') {
            dimensions++;
        }
        return dimensions;
    }

    @Override
    public void verify() {
        int endIndex = TypeDescriptors.skipValidTypeDescriptor(value, 0, true);
        if (endIndex != value.length()) {
            throw new ClassFormatError("Invalid type descriptor " + value);
        }
    }

    public boolean isArray() {
        return value.startsWith("[");
    }

    public TypeDescriptor getComponentType() {
        if (value.startsWith("[")) {
            return new TypeDescriptor(value.substring(1));
        }
        return null;
    }

    public TypeDescriptor getElementalType() {
        return new TypeDescriptor(value.substring(getArrayDimensions()));
    }
}
