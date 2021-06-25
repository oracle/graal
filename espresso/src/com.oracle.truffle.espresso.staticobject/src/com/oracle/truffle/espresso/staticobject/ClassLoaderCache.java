/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.staticobject;

/**
 * This interface is a temporary workaround to store the class loader of generated classes in the
 * TruffleLanguage instance. Once the Static Object Model is moved to Truffle, it will be removed.
 *
 * <p>
 * Implementations of ClassLoaderCache must store the provided {@link ClassLoader} instance without
 * modifying it.
 */
public interface ClassLoaderCache {
    /**
     * Stores a {@link ClassLoader} instance.
     *
     * @param cl the {@link ClassLoader} instance to be stored
     */
    void setClassLoader(ClassLoader cl);

    /**
     * Returns the previously stored {@link ClassLoader} instance.
     *
     * @return the previously stored {@link ClassLoader}
     */
    ClassLoader getClassLoader();
}
