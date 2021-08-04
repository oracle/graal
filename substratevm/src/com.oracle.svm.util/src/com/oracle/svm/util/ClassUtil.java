/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.util;

public final class ClassUtil {
    /**
     * Alternative to {@link Class#getSimpleName} that does not probe an enclosing class or method,
     * which can fail when they cannot be loaded.
     *
     * Note the differences to {@link Class#getName} and {@link Class#getSimpleName} (which might
     * actually be preferable):
     *
     * <pre>
     * Class.getName()                              Class.getSimpleName()   ClassUtil.getUnqualifiedName()
     * ---------------------------------------------------------------------------------------------------
     * int                                          int                     int
     * java.lang.String                             String                  String
     * [Ljava.lang.String;                          String[]                String[]
     * java.util.HashMap$EntrySet                   EntrySet                HashMap$EntrySet
     * com.example.ClassWithAnonymousInnerClass$1   ""                      ClassWithAnonymousInnerClass$1
     * </pre>
     */
    public static String getUnqualifiedName(Class<?> clazz) {
        String name = clazz.getTypeName();
        return name.substring(name.lastIndexOf('.') + 1); // strip the package name
    }

    public static boolean isSameOrParentLoader(ClassLoader parent, ClassLoader child) {
        if (parent == null) {
            return true; // boot loader: any loader's parent
        }
        ClassLoader c = child;
        while (c != null) {
            if (c == parent) {
                return true;
            }
            c = c.getParent();
        }
        return false;
    }

    private ClassUtil() {
    }
}
