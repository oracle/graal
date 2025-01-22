/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import static com.oracle.svm.core.MissingRegistrationUtils.throwMissingRegistrationErrors;

import java.util.EnumSet;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.configure.ConditionalRuntimeValue;
import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;
import com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;

@AutomaticallyRegisteredImageSingleton
public final class ClassForNameSupport implements MultiLayeredImageSingleton, UnsavedSingleton {

    private ClassLoader libGraalLoader;

    public void setLibGraalLoader(ClassLoader libGraalLoader) {
        this.libGraalLoader = libGraalLoader;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static ClassForNameSupport currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(ClassForNameSupport.class, false, true);
    }

    private static ClassForNameSupport[] layeredSingletons() {
        return MultiLayeredImageSingleton.getAllLayers(ClassForNameSupport.class);
    }

    /**
     * The map used to collect registered classes.
     */
    private final EconomicMap<String, ConditionalRuntimeValue<Object>> knownClasses = ImageHeapMap.create();
    /**
     * The map used to collect unsafe allocated classes.
     */
    private final EconomicMap<Class<?>, RuntimeConditionSet> unsafeInstantiatedClasses = ImageHeapMap.create();

    private static final Object NEGATIVE_QUERY = new Object();

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerClass(Class<?> clazz) {
        registerClass(ConfigurationCondition.alwaysTrue(), clazz);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerClass(ConfigurationCondition condition, Class<?> clazz) {
        assert !clazz.isPrimitive() : "primitive classes cannot be looked up by name";
        if (PredefinedClassesSupport.isPredefined(clazz)) {
            return; // must be defined at runtime before it can be looked up
        }
        synchronized (knownClasses) {
            String name = clazz.getName();
            ConditionalRuntimeValue<Object> exisingEntry = knownClasses.get(name);
            Object currentValue = exisingEntry == null ? null : exisingEntry.getValueUnconditionally();

            /* TODO: Remove workaround once GR-53985 is implemented */
            if (currentValue instanceof Class<?> currentClazz && clazz.getClassLoader() != currentClazz.getClassLoader()) {
                /* Ensure runtime lookup of LibGraalClassLoader classes */
                if (isLibGraalClass(currentClazz)) {
                    return;
                }
                if (isLibGraalClass(clazz)) {
                    currentValue = null;
                }
            }

            if (currentValue == null || // never seen
                            currentValue == NEGATIVE_QUERY ||
                            currentValue == clazz) {
                currentValue = clazz;
                var cond = updateConditionalValue(exisingEntry, currentValue, condition);
                knownClasses.put(name, cond);
            } else if (currentValue instanceof Throwable) { // failed at linking time
                var cond = updateConditionalValue(exisingEntry, currentValue, condition);
                /*
                 * If the class has already been seen as throwing an error, we don't overwrite this
                 * error. Nevertheless, we have to update the set of conditionals to be correct.
                 */
                knownClasses.put(name, cond);
            } else {
                throw VMError.shouldNotReachHere("""
                                Invalid Class.forName value for %s: %s
                                If the class is already registered as negative, it means that it exists but is not
                                accessible through the builder class loader, and it was already registered by name (as
                                negative query) before this point. In that case, we update the map to contain the actual
                                class.
                                """, name, currentValue);
            }
        }
    }

    @Platforms(HOSTED_ONLY.class)
    private boolean isLibGraalClass(Class<?> clazz) {
        return libGraalLoader != null && clazz.getClassLoader() == libGraalLoader;
    }

    public static ConditionalRuntimeValue<Object> updateConditionalValue(ConditionalRuntimeValue<Object> existingConditionalValue, Object newValue,
                    ConfigurationCondition additionalCondition) {
        if (existingConditionalValue == null) {
            return new ConditionalRuntimeValue<>(RuntimeConditionSet.createHosted(additionalCondition), newValue);
        } else {
            existingConditionalValue.getConditions().addCondition(additionalCondition);
            existingConditionalValue.updateValue(newValue);
            return existingConditionalValue;
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerExceptionForClass(ConfigurationCondition condition, String className, Throwable t) {
        updateCondition(condition, className, t);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerNegativeQuery(ConfigurationCondition condition, String className) {
        /*
         * If the class is not accessible by the builder class loader, but was already registered
         * through registerClass(Class<?>), we don't overwrite the actual class or exception.
         */
        updateCondition(condition, className, NEGATIVE_QUERY);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerUnsafeAllocated(ConfigurationCondition condition, Class<?> clazz) {
        if (!clazz.isArray()) {
            var conditionSet = unsafeInstantiatedClasses.putIfAbsent(clazz, RuntimeConditionSet.createHosted(condition));
            if (conditionSet != null) {
                conditionSet.addCondition(condition);
            }
        }
    }

    private void updateCondition(ConfigurationCondition condition, String className, Object value) {
        synchronized (knownClasses) {
            var runtimeConditions = knownClasses.putIfAbsent(className, new ConditionalRuntimeValue<>(RuntimeConditionSet.createHosted(condition), value));
            if (runtimeConditions != null) {
                runtimeConditions.getConditions().addCondition(condition);
            }
        }
    }

    public static Class<?> forNameOrNull(String className, ClassLoader classLoader) {
        try {
            return forName(className, classLoader, true);
        } catch (ClassNotFoundException e) {
            throw VMError.shouldNotReachHere("ClassForNameSupport#forNameOrNull should not throw", e);
        }
    }

    public static Class<?> forName(String className, ClassLoader classLoader) throws ClassNotFoundException {
        return forName(className, classLoader, false);
    }

    private static Class<?> forName(String className, ClassLoader classLoader, boolean returnNullOnException) throws ClassNotFoundException {
        if (className == null) {
            return null;
        }
        Object result = null;
        for (var singleton : layeredSingletons()) {
            result = singleton.forName0(className, classLoader);
            if (result != null) {
                break;
            }
        }
        // Note: for non-predefined classes, we (currently) don't need to check the provided loader
        // TODO rewrite stack traces (GR-42813)
        if (result instanceof Class<?>) {
            return (Class<?>) result;
        } else if (result instanceof Throwable) {
            if (returnNullOnException) {
                return null;
            }

            if (result instanceof Error) {
                throw (Error) result;
            } else if (result instanceof ClassNotFoundException) {
                throw (ClassNotFoundException) result;
            }
        } else if (result == null) {
            if (throwMissingRegistrationErrors()) {
                MissingReflectionRegistrationUtils.forClass(className);
            }

            if (returnNullOnException) {
                return null;
            } else {
                throw new ClassNotFoundException(className);
            }
        }
        throw VMError.shouldNotReachHere("Class.forName result should be Class, ClassNotFoundException or Error: " + result);
    }

    private Object forName0(String className, ClassLoader classLoader) {
        var conditional = knownClasses.get(className);
        Object result = conditional == null ? null : conditional.getValue();
        if (result == NEGATIVE_QUERY || className.endsWith("[]")) {
            /* Querying array classes with their "TypeName[]" name always throws */
            result = new ClassNotFoundException(className);
        }
        if (result == null) {
            result = PredefinedClassesSupport.getLoadedForNameOrNull(className, classLoader);
        }
        return result;
    }

    public int count() {
        return knownClasses.size();
    }

    public static RuntimeConditionSet getConditionFor(Class<?> jClass) {
        Objects.requireNonNull(jClass);
        String jClassName = jClass.getName();
        ConditionalRuntimeValue<Object> conditionalClass = null;
        for (var singleton : layeredSingletons()) {
            conditionalClass = singleton.knownClasses.get(jClassName);
            if (conditionalClass != null) {
                break;
            }
        }
        if (conditionalClass == null) {
            return RuntimeConditionSet.unmodifiableEmptySet();
        } else {
            return conditionalClass.getConditions();
        }
    }

    /**
     * Checks whether {@code hub} can be instantiated with {@code Unsafe.allocateInstance}. Note
     * that arrays can't be instantiated and this function will always return false for array types.
     */
    public static boolean canUnsafeInstantiateAsInstance(DynamicHub hub) {
        Class<?> clazz = DynamicHub.toClass(hub);
        RuntimeConditionSet conditionSet = null;
        for (var singleton : layeredSingletons()) {
            conditionSet = singleton.unsafeInstantiatedClasses.get(clazz);
            if (conditionSet != null) {
                break;
            }
        }
        return conditionSet != null && conditionSet.satisfied();
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS;
    }
}
