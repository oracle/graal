/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.impl;

import static com.oracle.truffle.espresso.impl.LoadingConstraints.INVALID_LOADER_ID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

public final class ClassRegistries {

    private final ClassRegistry bootClassRegistry;
    private final LoadingConstraints constraints;
    private final EspressoContext context;

    private final Set<StaticObject> weakClassLoaderSet = Collections.newSetFromMap(new WeakHashMap<>());
    private volatile int totalClassLoadersSet = 0;

    public ClassRegistries(EspressoContext context) {
        this.context = context;
        this.bootClassRegistry = new BootClassRegistry(context);
        this.constraints = new LoadingConstraints(context);
    }

    private ClassRegistry getClassRegistry(@Host(ClassLoader.class) StaticObject classLoader) {
        if (StaticObject.isNull(classLoader)) {
            return bootClassRegistry;
        }

        // Double-checked locking to attach class registry to guest instance.
        ClassRegistry classRegistry = (ClassRegistry) classLoader.getHiddenFieldVolatile(context.getMeta().HIDDEN_CLASS_LOADER_REGISTRY);
        if (classRegistry == null) {
            // Synchronizing on the classLoader instance would be the natural choice here, but:
            // On SubstrateVM, synchronizing on a StaticObject instance will add an extra slot/field
            // to all StaticObject instances. Locking on the weak set instead (maybe) spares one
            // slot/field in every single guest object.
            // Setting the class registry happens only once, for such rare operations, no contention
            // is expected.
            synchronized (weakClassLoaderSet) {
                classRegistry = (ClassRegistry) classLoader.getHiddenFieldVolatile(context.getMeta().HIDDEN_CLASS_LOADER_REGISTRY);
                if (classRegistry == null) {
                    classRegistry = new GuestClassRegistry(context, classLoader);
                    classLoader.setHiddenFieldVolatile(context.getMeta().HIDDEN_CLASS_LOADER_REGISTRY, classRegistry);
                    // Register the class loader in the weak set.
                    assert Thread.holdsLock(weakClassLoaderSet);
                    weakClassLoaderSet.add(classLoader);
                    totalClassLoadersSet++;
                    int size = weakClassLoaderSet.size();
                    if (totalClassLoadersSet > size) {
                        constraints.purge();
                        totalClassLoadersSet = size;
                    }
                }
            }
        }

        assert classRegistry != null;
        return classRegistry;
    }

    @TruffleBoundary
    public Klass findLoadedClass(Symbol<Type> type, @Host(ClassLoader.class) StaticObject classLoader) {
        assert classLoader != null : "use StaticObject.NULL for BCL";

        if (Types.isArray(type)) {
            Klass elemental = findLoadedClass(context.getTypes().getElementalType(type), classLoader);
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayClass(Types.getArrayDimensions(type));
        }

        ClassRegistry registry = getClassRegistry(classLoader);
        assert registry != null;
        return registry.findLoadedKlass(type);
    }

    @TruffleBoundary
    public Klass[] getLoadedClassesByLoader(StaticObject classLoader) {
        if (classLoader == StaticObject.NULL) {
            return bootClassRegistry.classes.values().toArray(new Klass[0]);
        }
        return getClassRegistry(classLoader).getLoadedKlasses();
    }

    @TruffleBoundary
    public Klass[] findLoadedClassAny(Symbol<Type> type) {
        ArrayList<Klass> klasses = new ArrayList<>();
        // look in boot class registry
        if (bootClassRegistry.classes.containsKey(type)) {
            klasses.add(bootClassRegistry.classes.get(type));
            // if a type loaded by the boot loader, there can't
            // be any others, so return immediately
            return klasses.toArray(new Klass[0]);
        }
        // continue search in all other registries
        synchronized (weakClassLoaderSet) {
            for (StaticObject classLoader : weakClassLoaderSet) {
                ClassRegistry registry = getClassRegistry(classLoader);
                if (registry != null && registry.classes != null && registry.classes.containsKey(type)) {
                    klasses.add(registry.classes.get(type));
                }
            }
        }
        return klasses.toArray(new Klass[0]);
    }

    @TruffleBoundary
    public Klass[] getAllLoadedClasses() {
        // add classes from boot registry
        ArrayList<Klass> list = new ArrayList<>(bootClassRegistry.classes.values());
        // add classes from all other registries
        synchronized (weakClassLoaderSet) {
            for (StaticObject classLoader : weakClassLoaderSet) {
                list.addAll(getClassRegistry(classLoader).classes.values());
            }
        }
        return list.toArray(Klass.EMPTY_ARRAY);
    }

    @TruffleBoundary
    public Klass loadKlassWithBootClassLoader(Symbol<Type> type) {
        return loadKlass(type, StaticObject.NULL);
    }

    @TruffleBoundary
    public Klass loadKlass(Symbol<Type> type, @Host(ClassLoader.class) StaticObject classLoader) {
        assert classLoader != null : "use StaticObject.NULL for BCL";

        if (Types.isArray(type)) {
            Klass elemental = loadKlass(context.getTypes().getElementalType(type), classLoader);
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayClass(Types.getArrayDimensions(type));
        }
        ClassRegistry registry = getClassRegistry(classLoader);
        return registry.loadKlass(type);
    }

    @TruffleBoundary
    public Klass defineKlass(Symbol<Type> type, byte[] bytes, StaticObject classLoader) {
        assert classLoader != null;
        ClassRegistry registry = getClassRegistry(classLoader);
        return registry.defineKlass(type, bytes);
    }

    public BootClassRegistry getBootClassRegistry() {
        return (BootClassRegistry) bootClassRegistry;
    }

    public void checkLoadingConstraint(Symbol<Type> type, StaticObject loader1, StaticObject loader2) {
        Symbol<Type> toCheck = context.getTypes().getElementalType(type);
        if (!Types.isPrimitive(toCheck) && loader1 != loader2) {
            constraints.checkConstraint(toCheck, loader1, loader2);
        }
    }

    void recordConstraint(Symbol<Type> type, Klass klass, StaticObject loader) {
        assert !Types.isArray(type);
        if (!Types.isPrimitive(type)) {
            constraints.recordConstraint(type, klass, loader);
        }
    }

    public long getLoadedClassesCount() {
        long result = bootClassRegistry.classes.size();
        synchronized (weakClassLoaderSet) {
            for (StaticObject classLoader : weakClassLoaderSet) {
                result += getClassRegistry(classLoader).classes.size();
            }
        }
        assert result >= 0;
        return result;
    }

    public boolean isClassLoader(StaticObject object) {
        if (InterpreterToVM.instanceOf(object, context.getMeta().java_lang_ClassLoader)) {
            synchronized (weakClassLoaderSet) {
                return weakClassLoaderSet.contains(object);
            }
        }
        return false;
    }

    /**
     * Collects IDs of all class loaders that have not been collected by the GC.
     */
    int[] aliveLoaders() {
        int[] loaders = new int[weakClassLoaderSet.size() + 1];
        loaders[0] = context.getBootClassLoaderID(); // Boot loader is always alive
        int i = 1;
        synchronized (weakClassLoaderSet) {
            for (StaticObject loader : weakClassLoaderSet) {
                if (loader != null) {
                    loaders[i++] = getClassRegistry(loader).getLoaderID();
                }
            }
        }
        if (i < loaders.length) {
            loaders[i++] = INVALID_LOADER_ID;
        }
        return loaders;
    }
}
