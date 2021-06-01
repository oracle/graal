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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;

public abstract class StaticShape<T> {
    protected static final Unsafe UNSAFE = getUnsafe();
    protected final Class<?> storageClass;
    @CompilationFinal //
    protected T factory;

    StaticShape(Class<?> storageClass, PrivilegedToken privilegedToken) {
        this.storageClass = storageClass;
        if (privilegedToken == null) {
            throw new AssertionError("Only known implementations can create subclasses of " + StaticShape.class.getName());
        }
    }

    public static Builder newBuilder(ClassLoaderCache clc) {
        return new Builder(clc);
    }

    protected final void setFactory(T factory) {
        assert this.factory == null;
        this.factory = factory;
    }

    public final T getFactory() {
        return factory;
    }

    final Class<?> getStorageClass() {
        return storageClass;
    }

    abstract Object getStorage(Object obj, boolean primitive);

    static <T> T cast(Object obj, Class<T> type) {
        return type.cast(obj);
    }

    @SuppressWarnings("unchecked")
    final Class<T> getFactoryInterface() {
        // Builder.validate() makes sure that the factory class implements a single interface
        assert factory.getClass().getInterfaces().length == 1;
        return (Class<T>) factory.getClass().getInterfaces()[0];
    }

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    public static final class Builder {
        private static final char[] FORBIDDEN_CHARS = new char[]{'.', ';', '[', '/'};
        private final HashMap<String, StaticProperty> staticProperties = new LinkedHashMap<>();
        private final ClassLoaderCache clc;

        Builder(ClassLoaderCache clc) {
            this.clc = clc;
        }

        public Builder property(StaticProperty property) {
            CompilerAsserts.neverPartOfCompilation();
            validatePropertyId(property.getId());
            staticProperties.put(property.getId(), property);
            return this;
        }

        public StaticShape<DefaultStaticObject.Factory> build() {
            // The classloader that loaded the default superClass must be able to load the default
            // factory.
            // Therefore, we can't use java.lang.Object as default superClass.
            return build(DefaultStaticObject.class, DefaultStaticObject.Factory.class);
        }

        public <T> StaticShape<T> build(StaticShape<T> parentShape) {
            Objects.requireNonNull(parentShape);
            GeneratorClassLoader gcl = getOrCreateClassLoader(parentShape.getStorageClass());
            ShapeGenerator<T> sg = ShapeGenerator.getShapeGenerator(gcl, parentShape);
            return build(sg, parentShape);
        }

        public <T> StaticShape<T> build(Class<?> superClass, Class<T> factoryInterface) {
            validateClasses(factoryInterface, superClass);
            GeneratorClassLoader gcl = getOrCreateClassLoader(superClass);
            ShapeGenerator<T> sg = ShapeGenerator.getShapeGenerator(gcl, superClass, factoryInterface);
            return build(sg, null);
        }

        private <T> StaticShape<T> build(ShapeGenerator<T> sg, StaticShape<T> parentShape) {
            CompilerAsserts.neverPartOfCompilation();
            StaticShape<T> shape = sg.generateShape(parentShape, staticProperties.values());
            for (StaticProperty staticProperty : staticProperties.values()) {
                staticProperty.initShape(shape);
            }
            return shape;
        }

        private GeneratorClassLoader getOrCreateClassLoader(Class<?> referenceClass) {
            ClassLoader cl = clc.getClassLoader();
            if (cl == null) {
                cl = new GeneratorClassLoader(referenceClass.getClassLoader(), referenceClass.getProtectionDomain());
                clc.setClassLoader(cl);
            }
            if (!GeneratorClassLoader.class.isInstance(cl)) {
                throw new RuntimeException("The ClassLoaderCache associated to this Builder returned an unexpected class loader");
            }
            return (GeneratorClassLoader) cl;
        }

        private void validatePropertyId(String id) {
            Objects.requireNonNull(id);
            if (id.length() == 0) {
                throw new IllegalArgumentException("The property id cannot be an empty string");
            }
            for (char forbidden : FORBIDDEN_CHARS) {
                if (id.indexOf(forbidden) != -1) {
                    throw new IllegalArgumentException("Property id '" + id + "' contains a forbidden char: '" + forbidden + "'");
                }
            }
            if (staticProperties.containsKey(id)) {
                throw new IllegalArgumentException("This builder already contains a property with id '" + id + "'");
            }
        }

        private static void validateClasses(Class<?> storageFactoryInterface, Class<?> storageSuperClass) {
            CompilerAsserts.neverPartOfCompilation();
            if (!storageFactoryInterface.isInterface()) {
                throw new RuntimeException(storageFactoryInterface.getName() + " must be an interface.");
            }
            for (Method m : storageFactoryInterface.getMethods()) {
                if (!m.getReturnType().isAssignableFrom(storageSuperClass)) {
                    throw new RuntimeException("The return type of '" + m.getReturnType().getName() + " " + storageFactoryInterface.getName() + "." + m + "' is not assignable from '" +
                                    storageSuperClass.getName() + "'");
                }
                try {
                    storageSuperClass.getDeclaredConstructor(m.getParameterTypes());
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException("Method '" + m + "' does not match any constructor in '" + storageSuperClass.getName() + "'", e);
                }
            }
        }
    }

    abstract static class PrivilegedToken {
        PrivilegedToken() {
            if (!isKnownImplementation()) {
                throw new AssertionError("Only known implementations can create a " + PrivilegedToken.class.getName() + ".\nGot: " + getClass().getName());
            }
        }

        private boolean isKnownImplementation() {
            for (String knownImplementation : new String[]{"com.oracle.truffle.espresso.staticobject.ArrayBasedStaticShape$ArrayBasedPrivilegedToken",
                            "com.oracle.truffle.espresso.staticobject.FieldBasedStaticShape$FieldBasedPrivilegedToken"}) {
                if (getClass().getName().equals(knownImplementation)) {
                    return true;
                }
            }
            return false;
        }
    }
}
