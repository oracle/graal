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
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.ClasspathFile;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.types.TypeDescriptor;

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
    private final ConcurrentHashMap<TypeDescriptor, Klass> classes = new ConcurrentHashMap<>();

    public BootClassRegistry(EspressoContext context) {
        this.context = context;
        // Primitive classes do not have a .class definition, inject them directly in the BCL.
        for (JavaKind kind : JavaKind.values()) {
            if (kind.isPrimitive()) {
                classes.put(context.getTypeDescriptors().make(kind.getTypeChar() + ""), new PrimitiveKlass(context, kind));
            }
        }
    }

    @Override
    public Klass resolve(TypeDescriptor type) {
        if (type.isArray()) {
            Klass k = resolve(type.getComponentType());
            if (k == null) {
                return null;
            }
            return k.getArrayClass();
        }

        // TODO(peterssen): Make boot class registry thread-safe. Class loading is not a
        // trivial operation, it loads super classes as well, which discards computeIfAbsent.
        Klass klass = classes.get(type);

        if (klass == null) {
            Klass hostClass = null;
            String className = type.toJavaName();

            if (type.isPrimitive()) {
                throw EspressoError.shouldNotReachHere("Primitives must be in the registry");
            }

            if (type.isArray()) {
                int dim = type.getArrayDimensions();
                Klass arrType = resolve(type.getElementalType());
                for (int i = 0; i < dim; ++i) {
                    arrType = arrType.getArrayClass();
                }
                return arrType;
            }
            ClasspathFile classpathFile = context.getBootClasspath().readClassFile(className);
            if (classpathFile == null) {
                return null;
            }
            ClassfileParser parser = new ClassfileParser(null, classpathFile, className, hostClass, context);
            klass = parser.parseClass();
            Klass loadedFirst = classes.putIfAbsent(type, klass);
            if (loadedFirst != null) {
                klass = loadedFirst;
            }
        }

        return klass;
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
}
