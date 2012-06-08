/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.meta;


public class RiUtil {



    /**
     * Extends the functionality of {@link Class#getSimpleName()} to include a non-empty string for anonymous and local
     * classes.
     *
     * @param clazz the class for which the simple name is being requested
     * @param withEnclosingClass specifies if the returned name should be qualified with the name(s) of the enclosing
     *            class/classes of {@code clazz} (if any). This option is ignored if {@code clazz} denotes an anonymous
     *            or local class.
     * @return the simple name
     */
    public static String getSimpleName(Class< ? > clazz, boolean withEnclosingClass) {
        final String simpleName = clazz.getSimpleName();
        if (simpleName.length() != 0) {
            if (withEnclosingClass) {
                String prefix = "";
                Class< ? > enclosingClass = clazz;
                while ((enclosingClass = enclosingClass.getEnclosingClass()) != null) {
                    prefix = prefix + enclosingClass.getSimpleName() + ".";
                }
                return prefix + simpleName;
            }
            return simpleName;
        }
        // Must be an anonymous or local class
        final String name = clazz.getName();
        int index = name.indexOf('$');
        if (index == -1) {
            return name;
        }
        index = name.lastIndexOf('.', index);
        if (index == -1) {
            return name;
        }
        return name.substring(index + 1);
    }


    /**
     * Converts a given type to its Java programming language name. The following are examples of strings returned by
     * this method:
     *
     * <pre>
     *     qualified == true:
     *         java.lang.Object
     *         int
     *         boolean[][]
     *     qualified == false:
     *         Object
     *         int
     *         boolean[][]
     * </pre>
     *
     * @param riType the type to be converted to a Java name
     * @param qualified specifies if the package prefix of the type should be included in the returned name
     * @return the Java name corresponding to {@code riType}
     */
    public static String toJavaName(RiType riType, boolean qualified) {
        RiKind kind = riType.kind();
        if (kind.isPrimitive() || kind == RiKind.Void) {
            return kind.javaName;
        }
        return internalNameToJava(riType.name(), qualified);
    }

    /**
     * Converts a given type to its Java programming language name. The following are examples of strings returned by
     * this method:
     *
     * <pre>
     *      java.lang.Object
     *      int
     *      boolean[][]
     * </pre>
     *
     * @param riType the type to be converted to a Java name
     * @return the Java name corresponding to {@code riType}
     */
    public static String toJavaName(RiType riType) {
        return (riType == null) ? null : internalNameToJava(riType.name(), true);
    }

    public static String internalNameToJava(String name, boolean qualified) {
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
                return internalNameToJava(name.substring(1), qualified) + "[]";
            default:
                if (name.length() != 1) {
                    throw new IllegalArgumentException("Illegal internal name: " + name);
                }
                return RiKind.fromPrimitiveOrVoidTypeChar(name.charAt(0)).javaName;
        }
    }
}
