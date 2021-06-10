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
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * A StaticShape is an immutable descriptor of the layout of a static object and is a good entry
 * point to learn about the Static Object Model. Here is an overview:
 * <ul>
 * <li>{@link StaticShape#newBuilder(ClassLoaderCache)} returns a {@link StaticShape.Builder} object
 * that can be used to {@linkplain StaticShape.Builder#property(StaticProperty) register}
 * {@linkplain StaticProperty static properties} and to generate a new static shape by calling one
 * of its {@linkplain Builder#build() build methods}.
 * <li>{@link StaticShape#getFactory()} returns an implementation of the {@linkplain Builder#build()
 * default} or the {@linkplain StaticShape.Builder#build(Class, Class) user-defined} factory
 * interface that must be used to allocate static objects with the current shape.
 * <li>Property values stored in a static object of a given shape can be accessed by the
 * {@link StaticProperty} instances registered to the builder that generated that shape or one of
 * its {@linkplain StaticShape.Builder#build(StaticShape) parent shapes}. Note that static shapes do
 * not store the list of {@linkplain StaticProperty static properties} associated to them. It is up
 * to the user to store this information when required, for example in a class that contains
 * references to the static shape and the list of {@linkplain StaticProperty static properties}.
 * </ul>
 *
 * <p>
 * StaticShape cannot be subclassed by custom implementations and, when required, it allows
 * {@linkplain StaticProperty static properties} to check that the receiver object matches the
 * expected shape.
 * 
 * @see StaticShape#newBuilder(ClassLoaderCache)
 * @see StaticShape.Builder
 * @see StaticProperty
 * @see DefaultStaticProperty
 * @see DefaultStaticObjectFactory
 * @param <T> the {@linkplain Builder#build() default} or the
 *            {@linkplain StaticShape.Builder#build(Class, Class) user-defined} factory interface to
 *            allocate static objects
 */
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

    /**
     * Creates a new static shape builder.
     *
     * The builder instance is not thread-safe and must not be used from multiple threads at the
     * same time. Users of the Static Object Model are expected to define custom subtypes of
     * {@link StaticProperty} or use {@link DefaultStaticProperty}, a trivial default
     * implementation. In both cases, static properties must be registered to a static shape builder
     * using {@link StaticShape.Builder#property(StaticProperty)}. Then, after allocating a
     * {@link StaticShape} instance with one of the {@link StaticShape.Builder#build()} methods and
     * allocating a static object using the factory class provided by
     * {@link StaticShape#getFactory()}, users can call the accessor methods defined in
     * {@link StaticProperty} to get and set property values stored in a static object instance.
     *
     * @param clc a class that can be used to cache the class loader instance used to load classes
     *            that extend the static object {@linkplain StaticShape.Builder#build(Class, Class)
     *            super class} and implement the corresponding {@linkplain Builder#build() default}
     *            or {@linkplain StaticShape.Builder#build(Class, Class) user-defined} factory
     *            interface. This argument will be removed once the code of the Static Object Model
     *            is moved to Truffle
     * @return a new static shape builder
     * 
     * @see StaticShape
     * @see StaticProperty
     * @see DefaultStaticProperty
     * @see DefaultStaticObjectFactory
     * @see ClassLoaderCache
     */
    public static Builder newBuilder(ClassLoaderCache clc) {
        return new Builder(clc);
    }

    final void setFactory(T factory) {
        assert this.factory == null;
        this.factory = factory;
    }

    /**
     * Returns an instance of the {@linkplain Builder#build() default} or the
     * {@linkplain StaticShape.Builder#build(Class, Class) user-defined} factory interface that must
     * be used to allocate static objects with the current shape.
     *
     * @see StaticShape.Builder#build()
     * @see StaticShape.Builder#build(StaticShape)
     * @see StaticShape.Builder#build(Class, Class)
     */
    public final T getFactory() {
        return factory;
    }

    final Class<?> getStorageClass() {
        return storageClass;
    }

    abstract Object getStorage(Object obj, boolean primitive);

    static <T> T cast(Object obj, Class<T> type) {
        try {
            return type.cast(obj);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Object '" + obj + "' of class '" + obj.getClass().getName() + "' does not have the expected shape", e);
        }
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

    /**
     * Builder class to construct {@link StaticShape} instances. The builder instance is not
     * thread-safe and must not be used from multiple threads at the same time.
     *
     * @see StaticShape#newBuilder(ClassLoaderCache)
     */
    public static final class Builder {
        private static final char[] FORBIDDEN_CHARS = new char[]{'.', ';', '[', '/'};
        private final HashMap<String, StaticProperty> staticProperties = new LinkedHashMap<>();
        private final ClassLoaderCache clc;

        Builder(ClassLoaderCache clc) {
            this.clc = clc;
        }

        /**
         * Adds a {@link StaticProperty} to the static shape to be constructed. The
         * {@linkplain StaticProperty#getId() property id} cannot be an empty String, or contain
         * characters that are illegal for field names ('.', ';', '[', '/'). It is not allowed to
         * add two {@linkplain StaticProperty properties} with the same
         * {@linkplain StaticProperty#getId() id} to the same Builder, or to add the same
         * {@linkplain StaticProperty property} to more than one Builder. Static shapes that
         * {@linkplain StaticShape.Builder#build(StaticShape) extend a parent shape} can have
         * {@linkplain StaticProperty properties} with the same {@linkplain StaticProperty#getId()
         * id} of those in the parent shape.
         *
         * @see DefaultStaticProperty
         * @param property the {@link StaticProperty} to be added
         * @return the Builder instance
         * @throws IllegalArgumentException if the {@linkplain StaticProperty#getId() property id}
         *             is an empty string, contains a forbidden character, or is the same of another
         *             static property already registered to this builder
         */
        public Builder property(StaticProperty property) {
            CompilerAsserts.neverPartOfCompilation();
            validatePropertyId(property.getId());
            staticProperties.put(property.getId(), property);
            return this;
        }

        /**
         * Builds a new {@linkplain StaticShape static shape} using the configuration of this
         * builder. The factory class returned by {@link StaticShape#getFactory()} implements
         * {@link DefaultStaticObjectFactory} and static objects extend {@link Object}.
         *
         * @see DefaultStaticObjectFactory
         * @see StaticShape.Builder#build(StaticShape)
         * @see StaticShape.Builder#build(Class, Class)
         * @return the new {@link StaticShape}
         */
        public StaticShape<DefaultStaticObjectFactory> build() {
            return build(Object.class, DefaultStaticObjectFactory.class);
        }

        /**
         * Builds a new {@linkplain StaticShape static shape} that extends the provided parent
         * {@link StaticShape}. {@linkplain StaticProperty Static properties} of the parent shape
         * can be used to access field values of static objects with the child shape. The factory
         * class returned by {@link StaticShape#getFactory()} extends the one of the parent shape
         * and static objects extend the static object class allocated by the factory class of the
         * parent shape.
         *
         * @see StaticShape.Builder#build()
         * @see StaticShape.Builder#build(Class, Class)
         * @param parentShape the parent {@linkplain StaticShape shape}
         * @param <T> the generic type of the parent {@linkplain StaticShape shape}
         * @return the new {@link StaticShape}
         */
        public <T> StaticShape<T> build(StaticShape<T> parentShape) {
            Objects.requireNonNull(parentShape);
            GeneratorClassLoader gcl = getOrCreateClassLoader(parentShape.getFactoryInterface());
            ShapeGenerator<T> sg = ShapeGenerator.getShapeGenerator(gcl, parentShape);
            return build(sg, parentShape);
        }

        /**
         * Builds a new {@linkplain StaticShape static shape} using the configuration of this
         * builder. The factory class returned by {@link StaticShape#getFactory()} implements
         * factoryInterface and static objects extend superClass.
         *
         * <p>
         * The following constraints are enforced:
         * <ul>
         * <li>factoryInterface must be an interface
         * <li>the arguments of every method in factoryInterface must match those of a visible
         * constructor of superClass
         * <li>the return type of every method in factoryInterface must be assignable from the
         * superClass
         * <li>if superClass is {@link Cloneable}, it cannot override {@link Object#clone()} with a
         * final method
         * </ul>
         *
         * @see StaticShape.Builder#build()
         * @see StaticShape.Builder#build(StaticShape)
         * @param superClass the class that static objects must extend
         * @param factoryInterface the factory interface that the factory class returned by
         *            {@link StaticShape#getFactory()} must implement
         * @param <T> the class of the factory interface
         * @return the new {@link StaticShape}
         * @throws IllegalArgumentException if factoryInterface is not an interface, if the
         *             arguments of a method in factoryInterface do not match those of a visible
         *             constructor in superClass, if the return type of a method in factoryInterface
         *             is not assignable from superClass, or if superClass is {@link Cloneable} and
         *             overrides {@link Object#clone()} with a final method.
         * @throws RuntimeException if a static property was added to more than one builder or
         *             multiple times to the same builder
         */
        public <T> StaticShape<T> build(Class<?> superClass, Class<T> factoryInterface) {
            validateClasses(factoryInterface, superClass);
            GeneratorClassLoader gcl = getOrCreateClassLoader(factoryInterface);
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
                throw new IllegalArgumentException(storageFactoryInterface.getName() + " must be an interface.");
            }
            // since methods in the factory interface must have the storage super class as return
            // type, calling `storageFactoryInterface.getMethods()` also verifies that the class
            // loader of the factory interface can load the storage super class
            for (Method m : storageFactoryInterface.getMethods()) {
                // this also verifies that the class loader of the factory interface is the same or
                // a child of the class loader of the storage super class
                if (!m.getReturnType().isAssignableFrom(storageSuperClass)) {
                    throw new IllegalArgumentException("The return type of '" + m + "' is not assignable from '" + storageSuperClass.getName() + "'");
                }
                try {
                    storageSuperClass.getDeclaredConstructor(m.getParameterTypes());
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException("Method '" + m + "' does not match any constructor in '" + storageSuperClass.getName() + "'", e);
                }
            }
            if (Cloneable.class.isAssignableFrom(storageSuperClass)) {
                Method clone = getCloneMethod(storageSuperClass);
                if (clone != null && Modifier.isFinal(clone.getModifiers())) {
                    throw new IllegalArgumentException("'" + storageSuperClass.getName() + "' implements Cloneable and declares a final 'clone()' method");
                }
            }
        }

        private static Method getCloneMethod(Class<?> c) {
            for (Class<?> clazz = c; clazz != null; clazz = clazz.getSuperclass()) {
                try {
                    return clazz.getDeclaredMethod("clone");
                } catch (NoSuchMethodException e) {
                    // Swallow the error, check the super class
                }
            }
            return null;
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
