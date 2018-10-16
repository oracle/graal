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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.ClasspathFile;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.types.TypeDescriptor;

public class ClassRegistries {

    private final ConcurrentHashMap<TypeDescriptor, Klass> bootClassRegistry = new ConcurrentHashMap<>();;

    private final ConcurrentHashMap<Object, ClassRegistry> registries = new ConcurrentHashMap<>();
    private final EspressoContext context;

    public ClassRegistries(EspressoContext context) {
        this.context = context;
        // Primitive classes do not have a .class definition, inject them directly in the BCL.
        for (JavaKind kind : JavaKind.values()) {
            if (kind.isPrimitive()) {
                bootClassRegistry.put(context.getTypeDescriptors().make(kind.getTypeChar() + ""), new PrimitiveKlass(context, kind));
            }
        }
    }

    public Klass findLoadedClass(TypeDescriptor type, Object classLoader) {
        if (type.isArray()) {
            Klass pepe = findLoadedClass(type.getComponentType(), classLoader);
            if (pepe != null) {
                return pepe.getArrayClass();
            }
            return null;
        }
        if (classLoader == null) {
            return bootClassRegistry.get(type);
        }
        ClassRegistry registry = registries.get(classLoader);
        if (registry == null) {
            return null;
        }
        return registry.findLoadedClass(type);
    }

    @CompilerDirectives.TruffleBoundary
    public Klass resolve(TypeDescriptor type, Object classLoader) {

        Klass k = findLoadedClass(type, classLoader);
        if (k != null) {
            return k;
        }
        if (classLoader == null) {
            // TODO(peterssen): Make boot class registry thread-safe. Class loading is not a
            // trivial operation, it loads super classes as well, which discards computeIfAbsent.
            Klass klass = bootClassRegistry.get(type);

            if (klass == null) {
                Klass hostClass = null;
                String className = type.toJavaName();

                if (type.isPrimitive()) {
                    throw EspressoError.shouldNotReachHere("Primitives must be in the registry");
                }

                if (type.isArray()) {
                    int dim = type.getArrayDimensions();
                    Klass arrType = resolve(type.getElementalType(), classLoader);
                    for (int i = 0; i < dim; ++i) {
                        arrType = arrType.getArrayClass();
                    }
                    return arrType;
                }

                // System.err.println("Try load BCL: " + type.toString());
                ClasspathFile classpathFile = context.getBootClasspath().readClassFile(className);
                if (classpathFile == null) {
                    return null;
                }
                ClassfileParser parser = new ClassfileParser(classLoader, classpathFile, className, hostClass, context);
                klass = parser.parseClass();
                Klass loadedFirst = bootClassRegistry.putIfAbsent(type, klass);
                if (loadedFirst != null) {
                    klass = loadedFirst;
                }
            }

            return klass;
        } else {
            ClassRegistry registry = registries.computeIfAbsent(classLoader, cl -> new ClassRegistryImpl(context, cl));
            return registry.resolve(type);
        }
    }
}
