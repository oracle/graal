/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.api.test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ModuleSupport {

    /**
     * Exports the package named {@code packageName} declared in {@code moduleMember}'s module to
     * {@code requestor}'s module.
     */
    @SuppressWarnings("unused")
    public static void exportPackageTo(Class<?> moduleMember, String packageName, Class<?> requestor) {
    }

    /**
     * Exports all packages declared in {@code moduleMember}'s module to {@code requestor}'s module.
     */
    @SuppressWarnings("unused")
    public static void exportAllPackagesTo(Class<?> moduleMember, Class<?> requestor) {
    }

    /**
     * Exports all packages declared in {@code moduleMember}'s module to the unnamed module
     * associated with {@code cl}.
     */
    @SuppressWarnings("unused")
    public static void exportAllPackagesTo(Class<?> moduleMember, ClassLoader cl) {
    }

    /**
     * Exports and opens all packages in the module named {@code name} to all unnamed modules.
     */
    @SuppressWarnings("unused")
    public static void exportAndOpenAllPackagesToUnnamed(String name) {
    }

    /**
     * Gets the names of the classes in the JDK image (i.e. lib/modules) that implement Graal.
     *
     * @throws IOException
     */
    @SuppressWarnings("unused")
    public static List<String> getJRTGraalClassNames() throws IOException {
        return Collections.emptyList();
    }
}
