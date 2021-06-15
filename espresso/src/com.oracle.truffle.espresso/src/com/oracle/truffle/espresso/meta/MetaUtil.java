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
package com.oracle.truffle.espresso.meta;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Miscellaneous collection of utility methods used by {@code jdk.vm.ci.meta} and its clients.
 */
public class MetaUtil {

    /**
     * Extends the functionality of {@link Class#getSimpleName()} to include a non-empty string for
     * anonymous and local classes.
     *
     * @param clazz the class for which the simple name is being requested
     * @param withEnclosingClass specifies if the returned name should be qualified with the name(s)
     *            of the enclosing class/classes of {@code clazz} (if any). This option is ignored
     *            if {@code clazz} denotes an anonymous or local class.
     * @return the simple name
     */
    public static String getSimpleName(Class<?> clazz, boolean withEnclosingClass) {
        String simpleName = null;
        try {
            simpleName = clazz.getSimpleName();
        } catch (InternalError ignored) {
        }
        if (simpleName != null && simpleName.length() != 0) {
            if (withEnclosingClass) {
                String prefix = "";
                Class<?> enclosingClass = clazz;
                while ((enclosingClass = enclosingClass.getEnclosingClass()) != null) {
                    prefix = enclosingClass.getSimpleName() + "." + prefix;
                }
                return prefix + simpleName;
            }
            return simpleName;
        }
        // Must be an anonymous or local class
        final String name = clazz.getName();
        int index = name.lastIndexOf('.');
        return name.substring(index + 1);
    }

    /**
     * Converts a type name in internal form to an external form.
     *
     * @param name the internal name to convert
     * @param qualified whether the returned name should be qualified with the package name
     * @param classForNameCompatible specifies if the returned name for array types should be in
     *            {@link Class#forName(String)} format (e.g., {@code "[Ljava.lang.Object;"},
     *            {@code "[[I"}) or in Java source code format (e.g., {@code "java.lang.Object[]"},
     *            {@code "int[][]"} ).
     */
    @TruffleBoundary
    public static String internalNameToJava(String name, boolean qualified, boolean classForNameCompatible) {
        switch (name.charAt(0)) {
            case 'L': {
                String result = name.substring(1, name.length() - 1).replace('/', '.');
                if (!qualified) {
                    final int lastDot = result.lastIndexOf('.');
                    if (lastDot != -1) {
                        result = result.substring(lastDot + 1);
                    }
                }
                return result;
            }
            case '[':
                return classForNameCompatible ? name.replace('/', '.') : internalNameToJava(name.substring(1), qualified, classForNameCompatible) + "[]";
            default:
                if (name.length() != 1) {
                    throw new IllegalArgumentException("Illegal internal name: " + name);
                }
                return JavaKind.fromPrimitiveOrVoidTypeChar(name.charAt(0)).getJavaName();
        }
    }

    /**
     * Converts a Java source-language class name into the internal form.
     *
     * @param className the class name
     * @return the internal name form of the class name
     */
    public static String toInternalName(String className) {
        // Already internal name.
        if (className.startsWith("L") && className.endsWith(";")) {
            return className.replace('.', '/');
        }

        if (className.startsWith("[")) {
            /* Already in the correct array style. */
            return className.replace('.', '/');
        }

        StringBuilder result = new StringBuilder();
        String base = className;
        while (base.endsWith("[]")) {
            result.append("[");
            base = base.substring(0, base.length() - 2);
        }

        switch (base) {
            case "boolean":
                result.append("Z");
                break;
            case "byte":
                result.append("B");
                break;
            case "short":
                result.append("S");
                break;
            case "char":
                result.append("C");
                break;
            case "int":
                result.append("I");
                break;
            case "float":
                result.append("F");
                break;
            case "long":
                result.append("J");
                break;
            case "double":
                result.append("D");
                break;
            case "void":
                result.append("V");
                break;
            default:
                result.append("L").append(base.replace('.', '/')).append(";");
                break;
        }
        return result.toString();
    }

    /**
     * Gets a string representation of an object based solely on its class and its
     * {@linkplain System#identityHashCode(Object) identity hash code}. This avoids and calls to
     * virtual methods on the object such as {@link Object#hashCode()}.
     */
    public static String identityHashCodeString(Object obj) {
        if (obj == null) {
            return "null";
        }
        return obj.getClass().getName() + "@" + System.identityHashCode(obj);
    }

    public static Object unwrapArrayOrNull(StaticObject object) {
        if (StaticObject.isNull(object)) {
            return null;
        }
        if (object.isArray()) {
            return object.unwrap();
        }
        return object;
    }

    public static Object maybeUnwrapNull(StaticObject object) {
        if (StaticObject.isNull(object)) {
            return null;
        }
        return object;
    }

    public static Object defaultFieldValue(JavaKind kind) {
        switch (kind) {
            case Object:
                return StaticObject.NULL;
            // The primitives stay here, if this method is needed later.
            case Float:
                return 0f;
            case Double:
                return 0.0d;
            case Long:
                return 0L;
            case Char:
                return (char) 0;
            case Short:
                return (short) 0;
            case Int:
                return 0;
            case Byte:
                return (byte) 0;
            case Boolean:
                return false;
            case Illegal: // fall-though
            case Void:    // fall-though
            default:
                CompilerAsserts.neverPartOfCompilation();
                throw EspressoError.shouldNotReachHere("Invalid field type " + kind);
        }
    }

    public static int defaultWordFieldValue(JavaKind kind) {
        switch (kind) {
            case Char:
                return (char) 0;
            case Short:
                return (short) 0;
            case Int:
                return 0;
            case Byte:
                return (byte) 0;
            case Boolean:
                return 0;
            default:
                CompilerAsserts.neverPartOfCompilation();
                throw EspressoError.shouldNotReachHere("Invalid Word field type " + kind);
        }
    }
}
