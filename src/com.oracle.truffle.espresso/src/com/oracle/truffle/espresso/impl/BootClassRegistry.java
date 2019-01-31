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

import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.descriptors.TypeDescriptor;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ByteString.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.ClasspathFile;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * A {@link GuestClassRegistry} maps class names to resolved {@link Klass} instances. Each class
 * loader is associated with a {@link GuestClassRegistry} and vice versa.
 *
 * This class is analogous to the ClassLoaderData C++ class in HotSpot.
 */
public class BootClassRegistry implements ClassRegistry {

    private final EspressoContext context;

    /**
     * The map from symbol to classes for the classes defined by the class loader associated with
     * this registry. Use of {@link ConcurrentHashMap} allows for atomic insertion while still
     * supporting fast, non-blocking lookup. There's no need for deletion as class unloading removes
     * a whole class registry and all its contained classes.
     */
    private final ConcurrentHashMap<ByteString<Type>, Klass> classes = new ConcurrentHashMap<>();

    public BootClassRegistry(EspressoContext context) {
        this.context = context;
        // Primitive classes do not have a .class definition, inject them directly in the BCL.
        for (JavaKind kind : JavaKind.values()) {
            if (kind.isPrimitive()) {
                classes.put(context.getTypes().make(kind.getTypeChar() + ""), new PrimitiveKlass(context, kind));
            }
        }
    }

    @Override
    public Klass resolve(ByteString<Type> type) {
        if (Types.isArray(type)) {
            ByteString<Type> elemental = Types.getElementalType(type);
            Klass klass = resolve(elemental);
            if (klass == null) {
                return null;
            }
            return klass.getArrayClass(Types.getArrayDimensions(type));
        }

        // TODO(peterssen): Make boot class registry thread-safe. Class loading is not a
        // trivial operation, it loads super classes as well, which discards computeIfAbsent.

        Klass klass = classes.get(type);
        if (klass != null) {
            return klass;
        }

        Klass hostClass = null;
        // String className = TypeDescriptor.slashified(type.toJavaName());

        EspressoError.guarantee(!Types.isPrimitive(type), "Primitives must be in the registry");

        ClasspathFile classpathFile = context.getBootClasspath().readClassFile(type);
        if (classpathFile == null) {
            return null;
        }

        // TODO(peterssen): Parsing does not trigger any additional loading, so this doesn't deadlock the BCL.
        synchronized (this) {
            klass = classes.get(type);
            if (klass == null) {
                klass = ClassfileParser.parse(StaticObject.NULL, classpathFile, className, hostClass, context);
                classes.putIfAbsent(type, klass);
            }
        }

        return klass;
    }

    @Override
    public Klass findLoadedKlass(ByteString<Type> type) {
        if (Types.isArray(type)) {
            ByteString<Type> elemental = Types.getElementalType(type);
            Klass klass = findLoadedKlass(elemental);
            if (klass == null) {
                return null;
            }
            return klass.getArrayClass(Types.getArrayDimensions(type));
        }
        return classes.get(type);
    }

    @Override
    public Klass defineKlass(ByteString<Type> type, Klass klass) {
        assert !classes.containsKey(type);
        Klass prevKlass = classes.putIfAbsent(type, klass);
        if (prevKlass != null) {
            return prevKlass;
        }
        return klass;
    }
}
