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

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;

/**
 * A class registry is the VM-internal part of a {@link java.lang.ClassLoader}. It maps class names
 * to class objects, and contains entries for every class for which this loader has initiated class
 * loading.
 */
public abstract class AbstractClassRegistry {
    private final EconomicMap<Symbol<Type>, Class<?>> aotClasses;
    /**
     * Classes for which this loader has been an initiating class loader. The values are either
     * Class objects or Placeholder objects. Placeholder objects are only used by
     * {@link AbstractRuntimeClassRegistry} and help detect {@link ClassCircularityError} by
     * reserving the entry in the classloader before the class has been fully loaded. They might
     * also be used to implement parallel class loading (GR-62338).
     */
    protected final ConcurrentHashMap<Symbol<Type>, Object> runtimeClasses;

    AbstractClassRegistry(ConcurrentHashMap<Symbol<Type>, Object> runtimeClasses) {
        if (SubstrateUtil.HOSTED) {
            this.aotClasses = ImageHeapMap.createNonLayeredMap();
        } else {
            this.aotClasses = null;
        }
        this.runtimeClasses = runtimeClasses;
    }

    protected final Class<?> findAOTLoadedClass(Symbol<Type> name) {
        if (aotClasses == null) {
            return null;
        }
        return aotClasses.get(name);
    }

    public final Class<?> findLoadedClass(Symbol<Type> name) {
        Class<?> aotClass = findAOTLoadedClass(name);
        if (aotClass != null || runtimeClasses == null) {
            return aotClass;
        }
        Object maybeClass = runtimeClasses.get(name);
        if (maybeClass instanceof Class<?> entry) {
            return entry;
        }
        return null;
    }

    public abstract ClassLoader getClassLoader();

    public abstract Class<?> loadClass(Symbol<Type> name) throws ClassNotFoundException;

    @Platforms(Platform.HOSTED_ONLY.class)
    public final void addAOTType(Class<?> cls) {
        assert !cls.isArray() && !cls.isPrimitive();
        TypeSymbols types = SymbolsSupport.getTypes();
        ByteSequence typeBytes = ByteSequence.createTypeFromName(cls.getName());
        Symbol<Type> key = types.getOrCreateValidType(typeBytes, true);
        assert key != null : typeBytes;
        Class<?> prev = aotClasses.put(key, cls);
        assert prev == null || prev == cls;
    }
}
