/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.types.TypeDescriptor;

import static com.oracle.truffle.espresso.meta.Meta.meta;

/**
 * A {@link GuestClassRegistry} maps class names to resolved {@link Klass} instances. Each class
 * loader is associated with a {@link GuestClassRegistry} and vice versa.
 *
 * This class is analogous to the ClassLoaderData C++ class in HotSpot.
 */
public class GuestClassRegistry implements ClassRegistry {

    private final EspressoContext context;

    /**
     * The map from symbol to classes for the classes defined by the class loader associated with
     * this registry. Use of {@link ConcurrentHashMap} allows for atomic insertion while still
     * supporting fast, non-blocking lookup. There's no need for deletion as class unloading removes
     * a whole class registry and all its contained classes.
     */
    private final ConcurrentHashMap<TypeDescriptor, Klass> classes = new ConcurrentHashMap<>();

    /**
     * The class loader associated with this registry.
     */
    private final StaticObject classLoader;

    public GuestClassRegistry(EspressoContext context, StaticObject classLoader) {
        this.context = context;
        this.classLoader = classLoader;
    }

    @Override
    public Klass resolve(TypeDescriptor type) {
        if (type.isArray()) {
            return resolve(type.getComponentType()).getArrayClass();
        }
        assert classLoader != null;
        // TODO(peterssen): Should the class be resolved?
        StaticObjectClass guestClass = (StaticObjectClass) Meta.meta(classLoader).method("loadClass", Class.class, String.class, boolean.class)
                .invokeDirect(context.getMeta().toGuest(type.toJavaName()), false);
        Klass k = guestClass.getMirror();
        meta(classLoader).method("addClass", void.class, Class.class).invokeDirect(guestClass);
        classes.put(type, k);
        return k;
    }

    @Override
    public Klass findLoadedClass(TypeDescriptor type) {
        if (type.isArray()) {
            Klass klass = findLoadedClass(type.getComponentType());
            if (klass == null) {
                return null;
            }
            return klass.getArrayClass();
        }
        return classes.get(type);
    }

    @Override
    public Klass defineKlass(TypeDescriptor type, Klass klass) {
        assert !classes.contains(type);
        classes.putIfAbsent(type, klass);
        // Register class in guest CL. Mimics HotSpot behavior.
        meta(classLoader).method("addClass", void.class, Class.class).invokeDirect(klass.mirror());
        return klass;
    }
}
