/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.vmaccess;

import java.lang.module.ModuleDescriptor;
import java.util.Set;

/**
 * JVMCI equivalent to {@link Module}. Do not compare with {@code ==}, use {@code #equals(Object)}
 * instead.
 */
public interface ResolvedJavaModule {
    /**
     * Returns the module name or {@code null} if this module is an unnamed module. See
     * {@link Module#getName()}.
     */
    String getName();

    /**
     * Returns {@code true} if this module has <em>opened</em> a package unconditionally. See
     * {@link Module#isOpen(String)}.
     */
    boolean isOpen(String pn);

    /**
     * Returns {@code true} if this module has <em>opened</em> a package to at least the given
     * module. See {@link Module#isOpen(String, java.lang.Module)}.
     */
    boolean isOpen(String packageName, ResolvedJavaModule accessingModule);

    /**
     * Returns {@code true} if this module exports the given package unconditionally. See
     * {@link Module#isExported(String)}.
     */
    boolean isExported(String packageName);

    /**
     * Returns {@code true} if this module exports the given package to at least the given module.
     * See {@link Module#isExported(String, java.lang.Module)}.
     */
    boolean isExported(String packageName, ResolvedJavaModule accessingModule);

    /**
     * Returns the set of package names for the packages in this module. See
     * {@link java.lang.Module#getPackages()}.
     */
    Set<String> getPackages();

    /**
     * Returns {@code true} if this module is a named module. See {@link Module#isNamed()}.
     */
    boolean isNamed();

    /**
     * Returns the module descriptor for this module or {@code null} if this module is an unnamed
     * module. See {@link Module#getDescriptor()}.
     * <p>
     * Note that although {@link ModuleDescriptor} is a JDK class, it is OK in JVMCI because it is
     * purely symbolic.
     */
    ModuleDescriptor getDescriptor();
}
