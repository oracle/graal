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
package com.oracle.svm.core.hub.registry;

import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;

/**
 * This class registry is used for ClassLoader instances if runtime class loading is unsupported.
 */
public final class AOTClassRegistry extends AbstractClassRegistry {
    private final ClassLoader classLoader;

    public AOTClassRegistry(ClassLoader classLoader) {
        super(classLoader == null ? null : new ConcurrentHashMap<>());
        this.classLoader = classLoader;
    }

    @Override
    public Class<?> loadClass(Symbol<Type> type) throws ClassNotFoundException {
        Class<?> cls = findLoadedClass(type);
        if (cls != null || classLoader == null) {
            return cls;
        }
        assert type.byteAt(0) == 'L' && type.byteAt(type.length() - 1) == ';' : type;
        String name = type.subSequence(1, type.length() - 1).toString().replace('/', '.');
        cls = classLoader.loadClass(name);
        var prev = runtimeClasses.put(type, cls);
        assert prev == null || prev == cls;
        return cls;
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }
}
