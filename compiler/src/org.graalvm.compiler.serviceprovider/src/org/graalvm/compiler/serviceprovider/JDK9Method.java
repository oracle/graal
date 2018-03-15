/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.serviceprovider;

import java.io.InputStream;
import java.util.Set;

/**
 * Access to API introduced by JDK 9. Use of any method in this class must ensure that the current
 * runtime is JDK 9 or later. For example:
 *
 * <pre>
 * if (!JDK9Method.isJava8OrEarlier) {
 *     Object module = JDK9Method.getModule(c);
 *     ...
 * }
 * </pre>
 *
 * This version of the class must be used on JDK 8.
 *
 * @see "https://docs.oracle.com/javase/9/docs/specs/jar/jar.html#Multi-release"
 */
public final class JDK9Method {

    private static int getJavaSpecificationVersion() {
        String value = System.getProperty("java.specification.version");
        if (value.startsWith("1.")) {
            value = value.substring(2);
        }
        return Integer.parseInt(value);
    }

    /**
     * The integer value corresponding to the value of the {@code java.specification.version} system
     * property after any leading {@code "1."} has been stripped.
     */
    public static final int JAVA_SPECIFICATION_VERSION = getJavaSpecificationVersion();

    /**
     * Determines if the Java runtime is version 8 or earlier.
     */
    public static final boolean Java8OrEarlier = JAVA_SPECIFICATION_VERSION <= 8;

    /**
     * Wrapper for {@code Class.getModule()}.
     */
    @SuppressWarnings("unused")
    public static Object getModule(Class<?> clazz) {
        throw new InternalError();
    }

    /**
     * Wrapper for {@code Module.getPackages()}.
     */
    @SuppressWarnings("unused")
    public static Set<String> getPackages(Object module) {
        throw new InternalError();
    }

    /**
     * Wrapper for {@code Module.getResourceAsStream(String)}.
     */
    @SuppressWarnings("unused")
    public static InputStream getResourceAsStream(Object module, String resource) {
        throw new InternalError();
    }

    /**
     * Wrapper for {@code Module.addOpens(String, Module)}.
     */
    @SuppressWarnings("unused")
    static void addOpens(Object thisModule, String packageName, Object otherModule) {
        throw new InternalError();
    }

    /**
     * Wrapper for {@code Module.isOpen(String, Module)}.
     */
    @SuppressWarnings("unused")
    public static boolean isOpenTo(Object module1, String pkg, Object module2) {
        throw new InternalError();
    }
}
