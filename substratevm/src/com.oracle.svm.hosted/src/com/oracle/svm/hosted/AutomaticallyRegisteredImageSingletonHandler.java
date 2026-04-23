/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import com.oracle.svm.core.layeredimagesingleton.LoadedLayeredImageSingletonInfo;
import com.oracle.svm.core.singleton.AutomaticallyRegisteredImageSingletonServiceRegistration;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.ReflectionUtil.ReflectionUtilError;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Registers classes annotated with {@link AutomaticallyRegisteredImageSingleton} before feature
 * registration begins.
 * <p>
 * The handler reconciles annotation-processor-generated service metadata with classes visible to
 * the {@link ImageClassLoader}, evaluates {@code onlyWith} predicates, picks at most one enabled
 * class per annotated inheritance root, and installs the resulting singleton under the keys
 * declared by that class and its annotated superclasses.
 * <p>
 * For layered images, if all registration keys were already handled during layer loading, the
 * singleton is not instantiated. Otherwise, one instance is created and only the still-missing keys
 * are added.
 */
final class AutomaticallyRegisteredImageSingletonHandler {

    /**
     * Loads and validates the generated service registrations, filters out disabled classes, and
     * registers the selected singleton for each annotated hierarchy.
     */
    static void registerImageSingletons(ImageClassLoader loader) {
        AutomaticallyRegisteredImageSingletonLoader automaticSingletonLoader = new AutomaticallyRegisteredImageSingletonLoader(loader);
        LinkedHashSet<Class<?>> automaticSingletons = automaticSingletonLoader.loadRegisteredClasses();
        automaticSingletonLoader.verifyGeneratedRegistrations(automaticSingletons);

        LinkedHashSet<Class<?>> enabledSingletons = new LinkedHashSet<>();
        for (Class<?> automaticSingleton : automaticSingletons) {
            if (isEnabled(automaticSingleton)) {
                enabledSingletons.add(automaticSingleton);
            }
        }

        LoadedLayeredImageSingletonInfo layeredSingletonInfo = ImageSingletons.lookup(LoadedLayeredImageSingletonInfo.class);
        /*
         * Build the hierarchy from all annotated classes, not just enabled ones. A subclass
         * disabled by onlyWith stops being a candidate, but it must not prevent an enabled
         * annotated superclass from being selected as the fallback registration.
         */
        for (Class<?> rootSingleton : findHierarchyRoots(automaticSingletons)) {
            Class<?> singletonClass = resolveSingletonForRoot(rootSingleton, enabledSingletons, automaticSingletonLoader);
            if (singletonClass != null) {
                registerSingleton(singletonClass, layeredSingletonInfo);
            }
        }
    }

    private static boolean isEnabled(Class<?> singletonClass) {
        AutomaticallyRegisteredImageSingleton annotation = getSingletonAnnotation(singletonClass);
        assert annotation != null;
        for (Class<? extends BooleanSupplier> predicateClass : annotation.onlyWith()) {
            BooleanSupplier predicate;
            try {
                predicate = ReflectionUtil.newInstance(predicateClass);
            } catch (ReflectionUtilError ex) {
                throw UserError.abort(ex.getCause(),
                                "Error instantiating onlyWith predicate %s for automatically registered image singleton class %s. " +
                                                "Ensure the predicate has a no-argument constructor.",
                                predicateClass.getTypeName(), singletonClass.getTypeName());
            }
            if (!predicate.getAsBoolean()) {
                return false;
            }
        }
        return true;
    }

    private static LinkedHashSet<Class<?>> findHierarchyRoots(LinkedHashSet<Class<?>> automaticSingletons) {
        LinkedHashSet<Class<?>> roots = new LinkedHashSet<>();
        for (Class<?> automaticSingleton : automaticSingletons) {
            if (!hasAutomaticSingletonSuperclass(automaticSingleton, automaticSingletons)) {
                roots.add(automaticSingleton);
            }
        }
        return roots;
    }

    private static boolean hasAutomaticSingletonSuperclass(Class<?> singletonClass, LinkedHashSet<Class<?>> automaticSingletons) {
        for (Class<?> superclass : getSingletonSuperclasses(singletonClass)) {
            if (automaticSingletons.contains(superclass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the one enabled class that represents the given annotated hierarchy root.
     * <p>
     * The most specific enabled subclass wins, a disabled subclass falls back to an enabled
     * annotated superclass, and multiple enabled sibling candidates are rejected as ambiguous.
     */
    private static Class<?> resolveSingletonForRoot(Class<?> rootSingleton, LinkedHashSet<Class<?>> enabledSingletons,
                    AutomaticallyRegisteredImageSingletonLoader automaticSingletonLoader) {
        List<Class<?>> mostSpecificEnabledSingletons = automaticSingletonLoader.findMostSpecificClasses(rootSingleton, enabledSingletons);
        if (mostSpecificEnabledSingletons.isEmpty()) {
            return null;
        }
        if (mostSpecificEnabledSingletons.size() > 1) {
            String candidates = mostSpecificEnabledSingletons.stream().map(Class::getName).collect(Collectors.joining(", "));
            throw UserError.abort("Ambiguous @%s extension for %s. Expected one most-specific annotated class, but found %s.",
                            AutomaticallyRegisteredImageSingleton.class.getSimpleName(), rootSingleton.getName(), candidates);
        }
        return mostSpecificEnabledSingletons.getFirst();
    }

    /**
     * Registers one singleton instance unless all of its registration keys were already handled
     * while loading a previous layer.
     * <p>
     * The handled-during-loading check happens before instantiation so layered builds do not
     * allocate replacement objects when the loaded singleton is still authoritative for every key.
     */
    private static void registerSingleton(Class<?> singletonClass, LoadedLayeredImageSingletonInfo layeredSingletonInfo) {
        List<Class<?>> keys = getRegistrationKeys(singletonClass);
        boolean hasUnhandledKey = false;
        for (Class<?> key : keys) {
            hasUnhandledKey = hasUnhandledKey || !layeredSingletonInfo.handledDuringLoading(key);
        }
        if (!hasUnhandledKey) {
            return;
        }

        Object singleton;
        try {
            singleton = ReflectionUtil.newInstance(singletonClass);
        } catch (ReflectionUtilError ex) {
            throw UserError.abort(ex.getCause(), "Error instantiating automatically registered image singleton class %s. " +
                            "Ensure the class is not abstract and has a no-argument constructor.", singletonClass.getTypeName());
        }
        for (Class<?> key : keys) {
            if (!layeredSingletonInfo.handledDuringLoading(key)) {
                addSingleton(key, singleton);
            }
        }
    }

    private static <T> void addSingleton(Class<T> key, Object singleton) {
        ImageSingletons.add(key, key.cast(singleton));
    }

    /**
     * Returns the registration keys for the selected singleton class.
     * <p>
     * Keys declared on annotated superclasses are inherited, in subclass-to-superclass order. If no
     * key is declared anywhere in the annotated chain, the selected class itself becomes the key.
     */
    private static List<Class<?>> getRegistrationKeys(Class<?> singletonClass) {
        ArrayList<Class<?>> keys = new ArrayList<>();
        addRegistrationKeys(singletonClass, keys);
        for (Class<?> superclass : getSingletonSuperclasses(singletonClass)) {
            addRegistrationKeys(superclass, keys);
        }
        if (keys.isEmpty()) {
            keys.add(singletonClass);
        }
        return keys;
    }

    private static void addRegistrationKeys(Class<?> singletonClass, List<Class<?>> keys) {
        AutomaticallyRegisteredImageSingleton annotation = getSingletonAnnotation(singletonClass);
        assert annotation != null;
        for (Class<?> key : annotation.value()) {
            keys.add(key);
        }
    }

    /**
     * Returns the contiguous chain of annotated superclasses, starting with the direct superclass
     * and stopping at the first superclass that is not annotated with
     * {@link AutomaticallyRegisteredImageSingleton}.
     */
    private static List<Class<?>> getSingletonSuperclasses(Class<?> singletonClass) {
        ArrayList<Class<?>> superclasses = new ArrayList<>();
        for (var current = singletonClass.getSuperclass(); current != null; current = current.getSuperclass()) {
            if (getSingletonAnnotation(current) == null) {
                break;
            }
            superclasses.add(current);
        }
        return superclasses;
    }

    private static AutomaticallyRegisteredImageSingleton getSingletonAnnotation(Class<?> singletonClass) {
        return AnnotationAccess.getAnnotation(singletonClass, AutomaticallyRegisteredImageSingleton.class);
    }

    private static final class AutomaticallyRegisteredImageSingletonLoader extends
                    AutomaticallyRegisteredClassSupport<AutomaticallyRegisteredImageSingletonServiceRegistration, AutomaticallyRegisteredImageSingleton> {
        private AutomaticallyRegisteredImageSingletonLoader(ImageClassLoader loader) {
            super(loader);
        }

        @Override
        protected Class<AutomaticallyRegisteredImageSingletonServiceRegistration> serviceRegistrationClass() {
            return AutomaticallyRegisteredImageSingletonServiceRegistration.class;
        }

        @Override
        protected Class<AutomaticallyRegisteredImageSingleton> annotationClass() {
            return AutomaticallyRegisteredImageSingleton.class;
        }

        @Override
        protected Error missingClassError(Throwable cause, String className) {
            throw UserError.abort(cause, "Could not load automatically registered image singleton class %s from generated service metadata. " +
                            "Clean and rebuild the affected project to refresh generated annotation-processor outputs.",
                            className);
        }

        @Override
        protected Error missingGeneratedRegistrationError(Class<?> annotatedClass) {
            throw UserError.abort("Image singleton %s annotated with @%s was not properly registered as a service. " +
                            "Either the annotation processor did not run for the project containing the singleton, or the class is not on the class path of the image generator. " +
                            "The annotation is only for internal usage. Clean and rebuild the affected project to refresh generated annotation-processor outputs.",
                            annotatedClass, AutomaticallyRegisteredImageSingleton.class.getSimpleName());
        }

        @Override
        protected Error staleGeneratedRegistrationError(Class<?> registeredClass) {
            throw UserError.abort("Class %s was registered as an @%s service but is no longer annotated. " +
                            "Clean and rebuild the affected project to refresh generated annotation-processor outputs.",
                            registeredClass.getName(), AutomaticallyRegisteredImageSingleton.class.getSimpleName());
        }
    }
}
