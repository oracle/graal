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

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;

public final class ClassRegistries {

    private final ClassRegistry bootClassRegistry;
    private final ConcurrentHashMap<StaticObject, ClassRegistry> registries;
    private final LoadingConstraints constraints;
    private final EspressoContext context;

    public ClassRegistries(EspressoContext context) {
        this.context = context;
        this.registries = new ConcurrentHashMap<>();
        this.bootClassRegistry = new BootClassRegistry(context);
        this.constraints = new LoadingConstraints(context);
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

        ClassRegistry registry = StaticObject.isNull(classLoader)
                        ? bootClassRegistry
                        : registries.get(classLoader);

        // Unknown class loader; no class has been loaded with it.
        if (registry == null) {
            return null;
        }

        return registry.findLoadedKlass(type);
    }

    @TruffleBoundary
    public Klass[] getLoadedClassesByLoader(StaticObject classLoader) {
        if (classLoader == StaticObject.NULL) {
            return bootClassRegistry.classes.values().toArray(new Klass[0]);
        }
        return registries.get(classLoader).getLoadedKlasses();
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
        for (ClassRegistry registry : registries.values()) {
            if (registry != null && registry.classes != null && registry.classes.containsKey(type)) {
                klasses.add(registry.classes.get(type));
            }
        }
        return klasses.toArray(new Klass[0]);
    }

    @TruffleBoundary
    public Klass[] getAllLoadedClasses() {
        // add classes from boot registry
        ArrayList<Klass> list = new ArrayList<>(bootClassRegistry.classes.values());
        // add classes from all other registries
        for (ClassRegistry registry : registries.values()) {
            list.addAll(registry.classes.values());
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

        ClassRegistry registry = StaticObject.isNull(classLoader)
                        ? bootClassRegistry
                        : registries.computeIfAbsent(classLoader, new Function<StaticObject, ClassRegistry>() {
                            @Override
                            public ClassRegistry apply(StaticObject cl) {
                                return new GuestClassRegistry(context, cl);
                            }
                        });

        return registry.loadKlass(type);
    }

    @TruffleBoundary
    public Klass defineKlass(Symbol<Type> type, byte[] bytes, StaticObject classLoader) {
        assert classLoader != null;

        ClassRegistry registry = StaticObject.isNull(classLoader)
                        ? bootClassRegistry
                        : registries.computeIfAbsent(classLoader, new Function<StaticObject, ClassRegistry>() {
                            @Override
                            public ClassRegistry apply(StaticObject cl) {
                                return new GuestClassRegistry(context, cl);
                            }
                        });

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

    public boolean isClassLoader(StaticObject object) {
        return registries.keySet().contains(object);
    }
}
