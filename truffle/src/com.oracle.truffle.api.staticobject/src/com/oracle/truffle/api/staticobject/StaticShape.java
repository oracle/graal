/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.staticobject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A StaticShape is an immutable descriptor of the layout of a static object and is a good entry
 * point to learn about the Static Object Model. Here is an overview:
 * <ul>
 * <li>{@link StaticShape#newBuilder(TruffleLanguage)} returns a {@link StaticShape.Builder} object
 * that can be used to {@linkplain StaticShape.Builder#property(StaticProperty, Class, boolean)
 * register} {@linkplain StaticProperty static properties} and to generate a new static shape by
 * calling one of its {@linkplain Builder#build() build methods}.
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
 * @see StaticShape#newBuilder(TruffleLanguage)
 * @see StaticShape.Builder
 * @see StaticProperty
 * @see DefaultStaticProperty
 * @see DefaultStaticObjectFactory
 * @param <T> the {@linkplain Builder#build() default} or the
 *            {@linkplain StaticShape.Builder#build(Class, Class) user-defined} factory interface to
 *            allocate static objects
 * @since 21.3.0
 */
public abstract class StaticShape<T> {
    enum StorageStrategy {
        ARRAY_BASED,
        FIELD_BASED
    }

    static final Unsafe UNSAFE = getUnsafe();
    final Class<?> storageClass;
    final boolean safetyChecks;
    @CompilationFinal //
    T factory;

    StaticShape(Class<?> storageClass, boolean safetyChecks) {
        this.storageClass = storageClass;
        this.safetyChecks = safetyChecks;
    }

    /**
     * Creates a new static shape builder.
     *
     * The builder instance is not thread-safe and must not be used from multiple threads at the
     * same time. Users of the Static Object Model are expected to define custom subtypes of
     * {@link StaticProperty} or use {@link DefaultStaticProperty}, a trivial default
     * implementation. In both cases, static properties must be registered to a static shape builder
     * using {@link StaticShape.Builder#property(StaticProperty, Class, boolean)}. Then, after
     * allocating a {@link StaticShape} instance with one of the {@link StaticShape.Builder#build()}
     * methods and allocating a static object using the factory class provided by
     * {@link StaticShape#getFactory()}, users can call the accessor methods defined in
     * {@link StaticProperty} to get and set property values stored in a static object instance.
     *
     * @param language an instance of the {@link TruffleLanguage} that uses the Static Object Model
     * @return a new static shape builder
     * @throws NullPointerException if language is null
     *
     * @see StaticShape
     * @see StaticProperty
     * @see DefaultStaticProperty
     * @see DefaultStaticObjectFactory
     * @since 21.3.0
     */
    public static Builder newBuilder(TruffleLanguage<?> language) {
        Objects.requireNonNull(language);
        return new Builder(language);
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
     * @since 21.3.0
     */
    public final T getFactory() {
        return factory;
    }

    final Class<?> getStorageClass() {
        return storageClass;
    }

    abstract Object getStorage(Object obj, boolean primitive);

    final <U> U cast(Object obj, Class<U> type, boolean checkCondition) {
        if (safetyChecks) {
            return checkedCast(obj, type);
        } else {
            assert checkedCast(obj, type) != null;
            return SomAccessor.RUNTIME.unsafeCast(obj, type, !checkCondition || type.isInstance(obj), false, false);
        }
    }

    @SuppressWarnings("unchecked")
    final Class<T> getFactoryInterface() {
        // Builder.validate() makes sure that the factory class implements a single interface
        assert factory.getClass().getInterfaces().length == 1;
        return (Class<T>) factory.getClass().getInterfaces()[0];
    }

    private static <U> U checkedCast(Object obj, Class<U> type) {
        try {
            return type.cast(obj);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Object '" + obj + "' of class '" + obj.getClass().getName() + "' does not have the expected shape", e);
        }
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
     * @see StaticShape#newBuilder(TruffleLanguage)
     * @since 21.3.0
     */
    public static final class Builder {
        private static final int MAX_NUMBER_OF_PROPERTIES = 65535;
        private static final int MAX_PROPERTY_ID_BYTE_LENGTH = 65535;
        private static final String DELIMITER = "$$";
        private static final AtomicInteger counter = new AtomicInteger();
        private final String storageClassName;
        private final HashMap<String, StaticProperty> staticProperties = new LinkedHashMap<>();
        private final TruffleLanguage<?> language;
        boolean hasLongPropertyId = false;
        boolean isActive = true;

        Builder(TruffleLanguage<?> language) {
            this.language = language;
            storageClassName = storageClassName();
        }

        static String storageClassName() {
            return ShapeGenerator.class.getPackage().getName().replace('.', '/') + "/GeneratedStaticObject" + DELIMITER + counter.incrementAndGet();
        }

        /**
         * Adds a {@link StaticProperty} to the static shape to be constructed. The
         * {@linkplain StaticProperty#getId() property id} cannot be null or an empty String. It is
         * not allowed to add two {@linkplain StaticProperty properties} with the same
         * {@linkplain StaticProperty#getId() id} to the same builder, or to add the same
         * {@linkplain StaticProperty property} to more than one builder. Static shapes that
         * {@linkplain StaticShape.Builder#build(StaticShape) extend a parent shape} can have
         * {@linkplain StaticProperty properties} with the same {@linkplain StaticProperty#getId()
         * id} of those in the parent shape.
         *
         * Only property accesses that match the specified type are allowed. Property values can be
         * optionally stored in a final field. Accesses to such values might be specially optimized
         * by the compiler. For example, reads might be constant-folded. It is up to the user to
         * enforce that property values stored as final are not assigned more than once.
         *
         * @see DefaultStaticProperty
         * @param property the {@link StaticProperty} to be added
         * @param type the type of the {@link StaticProperty} to be added.
         * @param storeAsFinal if this property value can be stored in a final field
         * @return the Builder instance
         * @throws IllegalArgumentException if more than 65535 properties are added, or if the
         *             {@linkplain StaticProperty#getId() property id} is an empty string or it is
         *             equal to the id of another static property already registered to this
         *             builder.
         * @throws IllegalStateException if this method is invoked after building a static shape
         * @throws NullPointerException if the {@linkplain StaticProperty#getId() property id} is
         *             null
         * @since 21.3.0
         */
        public Builder property(StaticProperty property, Class<?> type, boolean storeAsFinal) {
            CompilerAsserts.neverPartOfCompilation();
            StaticPropertyValidator.validate(type);
            checkStatus();
            property.init(type, storeAsFinal);
            staticProperties.put(validateAndGetId(property), property);
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
         * @throws IllegalStateException if a static property was added to more than one builder or
         *             multiple times to the same builder, if this method is invoked more than once,
         *             or if one of the static property types is not visible to the class loader
         *             that loaded the default factory interface.
         * @since 21.3.0
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
         * @throws IllegalStateException if a static property was added to more than one builder or
         *             multiple times to the same builder, if this method is invoked more than once,
         *             or if one of the static property types is not visible to the class loader
         *             that loaded the default factory interface.
         * @since 21.3.0
         */
        public <T> StaticShape<T> build(StaticShape<T> parentShape) {
            Objects.requireNonNull(parentShape);
            GeneratorClassLoader gcl = getOrCreateClassLoader(parentShape.getFactoryInterface());
            ShapeGenerator<T> sg = ShapeGenerator.getShapeGenerator(language, gcl, parentShape, getStorageStrategy(), storageClassName);
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
         * <li>superClass does not have abstract methods
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
         *             is not assignable from superClass, if {@link StaticShape} is not visible to
         *             the class loader of factoryInterface, if superClass has abstract methods or
         *             if superClass is {@link Cloneable} and overrides {@link Object#clone()} with
         *             a final method
         * @throws IllegalStateException if a static property was added to more than one builder or
         *             multiple times to the same builder, if this method is invoked more than once,
         *             or if one of the static property types is not visible to the class loader
         *             that loaded the factory interface.
         * @since 21.3.0
         */
        public <T> StaticShape<T> build(Class<?> superClass, Class<T> factoryInterface) {
            validateClasses(superClass, factoryInterface);
            GeneratorClassLoader gcl = getOrCreateClassLoader(factoryInterface);
            ShapeGenerator<T> sg = ShapeGenerator.getShapeGenerator(language, gcl, superClass, factoryInterface, getStorageStrategy(), storageClassName);
            return build(sg, null);
        }

        private <T> StaticShape<T> build(ShapeGenerator<T> sg, StaticShape<T> parentShape) {
            CompilerAsserts.neverPartOfCompilation();
            checkStatus();
            Map<String, StaticProperty> properties = hasLongPropertyId ? defaultPropertyIds(staticProperties) : staticProperties;
            boolean safetyChecks = !SomAccessor.ENGINE.areStaticObjectSafetyChecksRelaxed(SomAccessor.LANGUAGE.getPolyglotLanguageInstance(language));
            StaticShape<T> shape = sg.generateShape(parentShape, properties, safetyChecks, storageClassName);
            for (StaticProperty staticProperty : properties.values()) {
                staticProperty.initShape(shape);
            }
            setInactive();
            return shape;
        }

        private void checkStatus() {
            if (!isActive) {
                throw new IllegalStateException("This Builder instance has already built a StaticShape. It is not possible to add static properties or build other shapes");
            }
        }

        private void setInactive() {
            isActive = false;
        }

        private GeneratorClassLoader getOrCreateClassLoader(Class<?> referenceClass) {
            ClassLoader cl = SomAccessor.ENGINE.getStaticObjectClassLoader(SomAccessor.LANGUAGE.getPolyglotLanguageInstance(language), referenceClass);
            if (cl == null) {
                cl = new GeneratorClassLoader(referenceClass);
                SomAccessor.ENGINE.setStaticObjectClassLoader(SomAccessor.LANGUAGE.getPolyglotLanguageInstance(language), referenceClass, cl);
            }
            if (!GeneratorClassLoader.class.isInstance(cl)) {
                throw new RuntimeException("The Truffle language instance associated to this Builder returned an unexpected class loader");
            }
            return (GeneratorClassLoader) cl;
        }

        private String validateAndGetId(StaticProperty property) {
            String id = property.getId();
            Objects.requireNonNull(id);
            if (staticProperties.size() == MAX_NUMBER_OF_PROPERTIES) {
                throw new IllegalArgumentException("This builder already contains the maximum number of properties: " + MAX_NUMBER_OF_PROPERTIES);
            }
            if (id.length() == 0) {
                throw new IllegalArgumentException("The property id cannot be an empty string");
            }
            // escape chars that are forbidden for field names
            id = id.replace("_", "__");
            id = id.replace(".", "_,");
            id = id.replace(";", "_:");
            id = id.replace("[", "_]");
            id = id.replace("/", "_\\");
            if (staticProperties.containsKey(id)) {
                throw new IllegalArgumentException("This builder already contains a property with id '" + id + "'");
            }
            if (modifiedUtfLength(id) > MAX_PROPERTY_ID_BYTE_LENGTH) {
                hasLongPropertyId = true;
            }
            return id;
        }

        // Reflectively invoked also from TruffleBaseFeature.StaticObjectSupport
        private static void validateClasses(Class<?> storageSuperClass, Class<?> storageFactoryInterface) {
            CompilerAsserts.neverPartOfCompilation();
            if (!storageFactoryInterface.isInterface()) {
                throw new IllegalArgumentException(storageFactoryInterface.getName() + " must be an interface.");
            }
            // Since methods in the factory interface must have the storage super class as return
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
            // The array-based storage strategy stores the StaticShape in an extra field of the
            // generated class. Therefore, the class loader that loads generated classes must have
            // visibility of StaticShape.
            if (!isClassVisible(storageFactoryInterface.getClassLoader(), StaticShape.class)) {
                throw new IllegalArgumentException("The class loader of factory interface '" + storageFactoryInterface.getName() + "' (cl: '" + storageFactoryInterface.getClassLoader() +
                                "') must have visibility of '" + StaticShape.class.getName() + "' (cl: '" + StaticShape.class.getClassLoader() + "')");
            }
            for (Class<?> c = storageSuperClass; c != null; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (Modifier.isAbstract(m.getModifiers())) {
                        throw new IllegalArgumentException("'" + storageSuperClass.getName() + "' has abstract methods");
                    }
                }
            }
            if (Cloneable.class.isAssignableFrom(storageSuperClass)) {
                Method clone = getCloneMethod(storageSuperClass);
                if (clone != null && Modifier.isFinal(clone.getModifiers())) {
                    throw new IllegalArgumentException("'" + storageSuperClass.getName() + "' implements Cloneable and declares a final 'clone()' method");
                }
            }
        }

        private static boolean isClassVisible(ClassLoader cl, Class<?> clazz) {
            if (cl == null) {
                return clazz.getClassLoader() == null;
            } else {
                try {
                    cl.loadClass(clazz.getName());
                    return true;
                } catch (ClassNotFoundException e) {
                    // Swallow the exception
                    return false;
                }
            }
        }

        private static Map<String, StaticProperty> defaultPropertyIds(Map<String, StaticProperty> staticProperties) {
            Map<String, StaticProperty> newStaticProperties = new LinkedHashMap<>();
            int idx = 0;
            for (StaticProperty property : staticProperties.values()) {
                newStaticProperties.put("field" + idx++, property);
            }
            return newStaticProperties;
        }

        private static int modifiedUtfLength(String str) {
            int strlen = str.length();
            int utflen = 0;

            /* use charAt instead of copying String to char array */
            for (int i = 0; i < strlen; i++) {
                int c = str.charAt(i);
                if ((c >= 0x0001) && (c <= 0x007F)) {
                    utflen++;
                } else if (c > 0x07FF) {
                    utflen += 3;
                } else {
                    utflen += 2;
                }
            }
            return utflen;
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

        private StorageStrategy getStorageStrategy() {
            String strategy = SomAccessor.ENGINE.getStaticObjectStorageStrategy(SomAccessor.LANGUAGE.getPolyglotLanguageInstance(language));
            switch (strategy) {
                case "DEFAULT":
                    return TruffleOptions.AOT ? StorageStrategy.ARRAY_BASED : StorageStrategy.FIELD_BASED;
                case "ARRAY_BASED":
                    return StorageStrategy.ARRAY_BASED;
                case "FIELD_BASED":
                    if (TruffleOptions.AOT) {
                        throw new IllegalArgumentException("The field-based storage strategy is not yet supported on Native Image");
                    }
                    return StorageStrategy.FIELD_BASED;
                default:
                    throw new IllegalArgumentException("Should not reach here. Unexpected storage strategy: " + strategy);
            }
        }
    }
}
