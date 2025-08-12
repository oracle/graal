/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure;

import jdk.vm.ci.meta.JavaKind;

/**
 * There isn't a single standard way of referring to classes by name in the Java ecosystem. In the
 * context of Native Image reflection, there are three main ways of referring to a class:
 * 
 * <ul>
 * <li>The "type name": this is the result of calling {@code getTypeName()} on a {@code Class}
 * object. This is a human-readable name and is the preferred way of specifying classes in JSON
 * metadata files.</li>
 * <li>The "reflection name": this is used for calls to {@link Class#forName(String)} and others
 * using the same syntax. It is the binary name of the class except for array classes, where it is
 * formed using the internal name of the class.</li>
 * <li>The "JNI name": this is used for calls to {code FindClass} through JNI. This name is similar
 * to the reflection name but uses '/' instead of '.' as package separator.</li>
 * </ul>
 *
 * This class provides utility methods to be able to switch between those names and avoid confusion
 * about which format a given string is encoded as.
 *
 * Here is a breakdown of the various names of different types of classes:
 * 
 * <pre>
 * | Type            | Type name           | Reflection name      | JNI name             |
 * | --------------- | ------------------- | -------------------- | -------------------- |
 * | Regular class   | package.ClassName   | package.ClassName    | package/ClassName    |
 * | Primitive type  | type                | -                    | -                    |
 * | Array type      | package.ClassName[] | [Lpackage.ClassName; | [Lpackage/ClassName; |
 * | Primitive array | type[]              | [T                   | [T                   |
 * | Inner class     | package.Outer$Inner | package.Outer$Inner  | package/Outer$Inner  |
 * | Anonymous class | package.ClassName$1 | package.ClassName$1  | package/ClassName$1  |
 * </pre>
 */
public class ClassNameSupport {
    public static String reflectionNameToTypeName(String reflectionName) {
        if (!isValidReflectionName(reflectionName)) {
            return reflectionName;
        }
        return reflectionNameToTypeNameUnchecked(reflectionName);
    }

    public static String jniNameToTypeName(String jniName) {
        if (!isValidJNIName(jniName)) {
            return jniName;
        }
        return reflectionNameToTypeNameUnchecked(jniNameToReflectionNameUnchecked(jniName));
    }

    private static String reflectionNameToTypeNameUnchecked(String reflectionName) {
        int arrayDimension = wrappingArrayDimension(reflectionName);
        if (arrayDimension > 0) {
            return arrayElementTypeToTypeName(reflectionName, arrayDimension) + "[]".repeat(arrayDimension);
        }
        return reflectionName;
    }

    public static String typeNameToReflectionName(String typeName) {
        if (!isValidTypeName(typeName)) {
            return typeName;
        }
        return typeNameToReflectionNameUnchecked(typeName);
    }

    public static String typeNameToJNIName(String typeName) {
        if (!isValidTypeName(typeName)) {
            return typeName;
        }
        return reflectionNameToJNINameUnchecked(typeNameToReflectionNameUnchecked(typeName));
    }

    private static String typeNameToReflectionNameUnchecked(String typeName) {
        int arrayDimension = trailingArrayDimension(typeName);
        if (arrayDimension > 0) {
            return "[".repeat(arrayDimension) + typeNameToArrayElementType(typeName.substring(0, typeName.length() - arrayDimension * 2));
        }
        return typeName;
    }

    public static String jniNameToReflectionName(String jniName) {
        if (!isValidJNIName(jniName)) {
            return jniName;
        }
        return jniNameToReflectionNameUnchecked(jniName);
    }

    private static String jniNameToReflectionNameUnchecked(String jniName) {
        return jniName.replace('/', '.');
    }

    public static String reflectionNameToJNIName(String reflectionName) {
        if (!isValidReflectionName(reflectionName)) {
            return reflectionName;
        }
        return reflectionNameToJNINameUnchecked(reflectionName);
    }

    private static String reflectionNameToJNINameUnchecked(String reflectionName) {
        return reflectionName.replace('.', '/');
    }

    public static String getArrayReflectionName(String componentReflectionName) {
        if (!isValidReflectionName(componentReflectionName)) {
            return componentReflectionName;
        }
        return "[" + (wrappingArrayDimension(componentReflectionName) > 0 ? componentReflectionName : typeNameToArrayElementType(componentReflectionName));
    }

    private static String arrayElementTypeToTypeName(String arrayElementType, int startIndex) {
        char typeChar = arrayElementType.charAt(startIndex);
        return switch (typeChar) {
            case 'L' -> arrayElementType.substring(startIndex + 1, arrayElementType.length() - 1);
            case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z' -> JavaKind.fromPrimitiveOrVoidTypeChar(typeChar).getJavaName();
            default -> null;
        };
    }

    private static String typeNameToArrayElementType(String typeName) {
        Class<?> primitiveType = forPrimitiveName(typeName);
        if (primitiveType != null) {
            return String.valueOf(JavaKind.fromJavaClass(primitiveType).getTypeChar());
        }
        return "L" + typeName + ";";
    }

    public static boolean isValidTypeName(String name) {
        return isValidFullyQualifiedClassName(name, 0, name.length() - trailingArrayDimension(name) * 2, '.');
    }

    public static boolean isValidReflectionName(String name) {
        return isValidWrappingArraySyntaxName(name, '.');
    }

    public static boolean isValidJNIName(String name) {
        return isValidWrappingArraySyntaxName(name, '/');
    }

    private static boolean isValidWrappingArraySyntaxName(String name, char packageSeparator) {
        int arrayDimension = wrappingArrayDimension(name);
        if (arrayDimension > 0) {
            return isValidWrappingArrayElementType(name, arrayDimension, packageSeparator);
        }
        return isValidFullyQualifiedClassName(name, 0, name.length(), packageSeparator);
    }

    private static boolean isValidWrappingArrayElementType(String name, int startIndex, char packageSeparator) {
        if (startIndex == name.length()) {
            return false;
        }
        return switch (name.charAt(startIndex)) {
            case 'L' ->
                name.charAt(name.length() - 1) == ';' && isValidFullyQualifiedClassName(name, startIndex + 1, name.length() - 1, packageSeparator);
            case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z' -> startIndex == name.length() - 1;
            default -> false;
        };
    }

    private static boolean isValidFullyQualifiedClassName(String name, int startIndex, int endIndex, char packageSeparator) {
        int lastPackageSeparatorIndex = -1;
        for (int i = startIndex; i < endIndex; ++i) {
            char current = name.charAt(i);
            if (current == packageSeparator) {
                if (lastPackageSeparatorIndex == i - 1) {
                    return false;
                }
                lastPackageSeparatorIndex = i;
            } else if (current == '.' || current == ';' || current == '[' || current == '/') {
                /*
                 * Some special characters are allowed in class files while not being permitted as
                 * code identifiers (e.g. '+', '-', ',').
                 *
                 * @see https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.2.2
                 */
                return false;
            }
        }
        return true;
    }

    private static int wrappingArrayDimension(String name) {
        int arrayDimension = 0;
        while (arrayDimension < name.length() && name.charAt(arrayDimension) == '[') {
            arrayDimension++;
        }
        return arrayDimension;
    }

    private static int trailingArrayDimension(String name) {
        int arrayDimension = 0;
        while (endsWithTrailingArraySyntax(name, name.length() - arrayDimension * 2)) {
            arrayDimension++;
        }
        return arrayDimension;
    }

    private static boolean endsWithTrailingArraySyntax(String string, int endIndex) {
        return endIndex >= "[]".length() && string.charAt(endIndex - 2) == '[' && string.charAt(endIndex - 1) == ']';
    }

    // Copied from java.lang.Class from JDK 22
    public static Class<?> forPrimitiveName(String primitiveName) {
        return switch (primitiveName) {
            // Integral types
            case "int" -> int.class;
            case "long" -> long.class;
            case "short" -> short.class;
            case "char" -> char.class;
            case "byte" -> byte.class;

            // Floating-point types
            case "float" -> float.class;
            case "double" -> double.class;

            // Other types
            case "boolean" -> boolean.class;
            case "void" -> void.class;

            default -> null;
        };
    }
}
