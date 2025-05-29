/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub.registry;

import java.util.Objects;

import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;

import jdk.internal.loader.ClassLoaders;

/**
 * This class registry corresponds to any non-null class loader when runtime class loading is
 * supported.
 * <p>
 * Note that "user-defined" should be understood from the JVM's point of view: it is any type of
 * classloader that extends {@link ClassLoader}, including those that are part of the standard
 * library.
 */
public final class UserDefinedClassRegistry extends AbstractRuntimeClassRegistry {
    private final ClassLoader loader;
    private final boolean isPlatform;

    UserDefinedClassRegistry(ClassLoader loader) {
        this.loader = Objects.requireNonNull(loader);
        this.isPlatform = loader == ClassLoaders.platformClassLoader();
    }

    @Override
    public Class<?> doLoadClass(Symbol<Type> type) throws ClassNotFoundException {
        assert type.byteAt(0) == 'L' && type.byteAt(type.length() - 1) == ';' : type;
        String name = type.subSequence(1, type.length() - 1).toString().replace('/', '.');
        return loader.loadClass(name);
    }

    @Override
    protected boolean loaderIsBootOrPlatform() {
        return isPlatform;
    }

    @Override
    public ClassLoader getClassLoader() {
        return loader;
    }
}
