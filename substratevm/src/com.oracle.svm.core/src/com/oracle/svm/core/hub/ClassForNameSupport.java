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
import static jdk.graal.compiler.options.OptionStability.EXPERIMENTAL;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.configure.ClassNameSupport;
import com.oracle.svm.core.configure.ConditionalRuntimeValue;
import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LayerVerifiedOption;
import com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;

@AutomaticallyRegisteredImageSingleton
public final class ClassForNameSupport implements MultiLayeredImageSingleton {

    public static final String CLASSES_REGISTERED = "classes registered";
    public static final String CLASSES_REGISTERED_STATES = "classes registered states";
    public static final String UNSAFE_REGISTERED = "unsafe registered";
    public static final String RESPECTS_CLASS_LOADER = "respects class loader";

    public static final class Options {
        @LayerVerifiedOption(kind = LayerVerifiedOption.Kind.Changed, severity = LayerVerifiedOption.Severity.Error)//
        @Option(help = "Class.forName and similar respect their class loader argument.", stability = EXPERIMENTAL)//
        public static final HostedOptionKey<Boolean> ClassForNameRespectsClassLoader = new HostedOptionKey<>(false);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class RespectsClassLoader implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return respectClassLoader();
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class IgnoresClassLoader implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return !respectClassLoader();
        }
    }

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
     * The map used to collect registered classes. Not used when respecting class loaders. This map
     * only collects data for the current layer.
     */
    private final EconomicMap<String, ConditionalRuntimeValue<Object>> knownClasses;
    /**
     * The map used to collect registered class names. When respecting class loaders this replaces
     * knownClasses.
     */
    private final EconomicMap<String, RuntimeConditionSet> knownClassNames;
    /**
     * The map used to collect exceptions that should be thrown by Class.forName. Only used when
     * respecting class loaders.
     */
    private final EconomicMap<String, Throwable> knownExceptions;
    /**
     * The map used to collect unsafe allocated classes. This map only collects data for the current
     * layer.
     */
    private final EconomicMap<Class<?>, RuntimeConditionSet> unsafeInstantiatedClasses;

    /**
     * The map used to collect classes registered in previous layers. The boolean associated to each
     * class is true if the registered value is complete and false in the case of a negative query.
     * A complete data registered in the current layer will overwrite a negative query in previous
     * layers. In this case, the data will be stored in {@link ClassForNameSupport#knownClasses} of
     * the current layer and the boolean value will be changed in the map of the next extension
     * layer.
     */
    @Platforms(HOSTED_ONLY.class) //
    private final Map<String, Boolean> previousLayerClasses;

    /**
     * The set used to collect unsafe allocated classes in previous layers.
     */
    @Platforms(HOSTED_ONLY.class) //
    private final Set<String> previousLayerUnsafe;

    private static final Object NEGATIVE_QUERY = new Object();

    public ClassForNameSupport() {
        this(Map.of(), Set.of(), respectClassLoader());
    }

    public ClassForNameSupport(Map<String, Boolean> previousLayerClasses, Set<String> previousLayerUnsafe, boolean respectsClassLoader) {
        if (respectsClassLoader) {
            knownClasses = null;
            knownClassNames = ImageHeapMap.createNonLayeredMap();
            knownExceptions = ImageHeapMap.createNonLayeredMap();
        } else {
            knownClasses = ImageHeapMap.createNonLayeredMap();
            knownClassNames = null;
            knownExceptions = null;
        }
        unsafeInstantiatedClasses = ImageHeapMap.createNonLayeredMap();
        this.previousLayerClasses = previousLayerClasses;
        this.previousLayerUnsafe = previousLayerUnsafe;
    }

    @Fold
    public static boolean respectClassLoader() {
        return Options.ClassForNameRespectsClassLoader.getValue();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerClass(Class<?> clazz, ClassLoader runtimeClassLoader) {
        registerClass(ConfigurationCondition.alwaysTrue(), clazz, runtimeClassLoader);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerClass(ConfigurationCondition condition, Class<?> clazz, ClassLoader runtimeClassLoader) {
        assert !clazz.isPrimitive() : "primitive classes cannot be looked up by name";
        if (PredefinedClassesSupport.isPredefined(clazz)) {
            return; // must be defined at runtime before it can be looked up
        }
        String name = clazz.getName();
        if (respectClassLoader()) {
            registerKnownClassName(condition, name);
            Class<?> elemental = clazz;
            while (elemental.isArray()) {
                elemental = elemental.getComponentType();
            }
            if (!elemental.isPrimitive()) {
                ClassRegistries.addAOTClass(runtimeClassLoader, elemental);
            }
        } else {
            synchronized (knownClasses) {
                ConditionalRuntimeValue<Object> existingEntry = knownClasses.get(name);
                Object currentValue = existingEntry == null ? null : existingEntry.getValueUnconditionally();

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
                    var cond = updateConditionalValue(existingEntry, currentValue, condition);
                    addKnownClass(name, cond);
                } else if (currentValue instanceof Throwable) { // failed at linking time
                    var cond = updateConditionalValue(existingEntry, currentValue, condition);
                    /*
                     * If the class has already been seen as throwing an error, we don't overwrite
                     * this error. Nevertheless, we have to update the set of conditionals to be
                     * correct.
                     */
                    addKnownClass(name, cond);
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
    }

    private void addKnownClass(String name, ConditionalRuntimeValue<Object> cond) {
        addKnownClass(name, (map) -> map.put(name, cond), cond);
    }

    private void addKnownClass(String name, Consumer<EconomicMap<String, ConditionalRuntimeValue<Object>>> callback, ConditionalRuntimeValue<Object> cond) {
        Boolean previousLayerData = previousLayerClasses.get(name);
        /* GR-66387: The runtime condition should be combined across layers. */
        if (previousLayerData == null || (!previousLayerData && cond.getValueUnconditionally() != NEGATIVE_QUERY)) {
            callback.accept(knownClasses);
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
        if (RuntimeClassLoading.isSupported()) {
            return;
        }
        if (respectClassLoader()) {
            registerKnownClassName(condition, className);
            synchronized (knownExceptions) {
                knownExceptions.put(className, t);
            }
        } else {
            updateCondition(condition, className, t);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerNegativeQuery(ConfigurationCondition condition, String className) {
        if (respectClassLoader()) {
            registerKnownClassName(condition, className);
        } else {
            /*
             * If the class is not accessible by the builder class loader, but was already
             * registered through registerClass(Class<?>), we don't overwrite the actual class or
             * exception.
             */
            updateCondition(condition, className, NEGATIVE_QUERY);
        }
    }

    private void registerKnownClassName(ConfigurationCondition condition, String className) {
        assert respectClassLoader();
        synchronized (knownClassNames) {
            RuntimeConditionSet existingConditions = knownClassNames.get(className);
            if (existingConditions == null) {
                knownClassNames.put(className, RuntimeConditionSet.createHosted(condition));
            } else {
                existingConditions.addCondition(condition);
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerUnsafeAllocated(ConfigurationCondition condition, Class<?> clazz) {
        if (!clazz.isArray() && !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
            /* Otherwise, UNSAFE.allocateInstance results in InstantiationException */
            if (!previousLayerUnsafe.contains(clazz.getName())) {
                var conditionSet = unsafeInstantiatedClasses.putIfAbsent(clazz, RuntimeConditionSet.createHosted(condition));
                if (conditionSet != null) {
                    conditionSet.addCondition(condition);
                }
            }
        }
    }

    private void updateCondition(ConfigurationCondition condition, String className, Object value) {
        synchronized (knownClasses) {
            var cond = new ConditionalRuntimeValue<>(RuntimeConditionSet.createHosted(condition), value);
            addKnownClass(className, (map) -> {
                var runtimeConditions = map.putIfAbsent(className, cond);
                if (runtimeConditions != null) {
                    runtimeConditions.getConditions().addCondition(condition);
                }
            }, cond);
        }
    }

    public static Class<?> forNameOrNull(String className, ClassLoader classLoader) {
        try {
            return forName(className, classLoader, true);
        } catch (ClassNotFoundException e) {
            throw VMError.shouldNotReachHere("ClassForNameSupport#forNameOrNull should not throw", e);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Set<String> getKnownClassNames() {
        EconomicMap<String, ?> map = respectClassLoader() ? knownClassNames : knownClasses;
        Set<String> set = new HashSet<>(map.size());
        for (String key : map.getKeys()) {
            set.add(key);
        }
        return set;
    }

    public static Class<?> forName(String className, ClassLoader classLoader) throws ClassNotFoundException {
        return forName(className, classLoader, false);
    }

    private static Class<?> forName(String className, ClassLoader classLoader, boolean returnNullOnException) throws ClassNotFoundException {
        if (className == null) {
            return null;
        }
        Object result = queryResultFor(className, classLoader);
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
                MissingReflectionRegistrationUtils.reportClassAccess(className);
            }

            if (returnNullOnException) {
                return null;
            } else {
                throw new ClassNotFoundException(className);
            }
        }
        throw VMError.shouldNotReachHere("Class.forName result should be Class, ClassNotFoundException or Error: " + result);
    }

    private static Object queryResultFor(String className, ClassLoader classLoader) {
        Object result = null;
        for (var singleton : layeredSingletons()) {
            Object newResult = singleton.forName0(className, classLoader);
            result = newResult != null ? newResult : result;
            /*
             * The class might have been registered in a shared layer but was not yet available. In
             * that case, the extension layers need to be checked too.
             */
            if (result != null && result != NEGATIVE_QUERY) {
                break;
            }
        }
        return result;
    }

    private Object forName0(String className, ClassLoader classLoader) {
        if (className.endsWith("[]")) {
            /* Querying array classes with their "TypeName[]" name always throws */
            return new ClassNotFoundException(className);
        }
        var conditional = knownClasses.get(className);
        Object result = conditional == null ? null : conditional.getValue();
        if (result == null) {
            result = PredefinedClassesSupport.getLoadedForNameOrNull(className, classLoader);
        }
        if (result == null && !ClassNameSupport.isValidReflectionName(className)) {
            /* Invalid class names always throw, no need for reflection data */
            return new ClassNotFoundException(className);
        }
        if (MetadataTracer.enabled()) {
            // NB: the early returns above ensure we do not trace calls with bad type args.
            MetadataTracer.singleton().traceReflectionType(ClassNameSupport.reflectionNameToTypeName(className));
        }
        return result == NEGATIVE_QUERY ? new ClassNotFoundException(className) : result;
    }

    public int count() {
        if (respectClassLoader()) {
            return knownClassNames.size();
        } else {
            return knownClasses.size();
        }
    }

    public static RuntimeConditionSet getConditionFor(Class<?> jClass) {
        Objects.requireNonNull(jClass);
        String jClassName = jClass.getName();
        if (respectClassLoader()) {
            RuntimeConditionSet conditionSet = getConditionForName(jClassName);
            if (conditionSet == null) {
                return RuntimeConditionSet.unmodifiableEmptySet();
            }
            return conditionSet;
        }
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

    public static boolean isRegisteredClass(String className) {
        if (respectClassLoader()) {
            RuntimeConditionSet conditionSet = getConditionForName(className);
            if (conditionSet == null) {
                return false;
            }
            return conditionSet.satisfied();
        } else {
            return queryResultFor(className, null) != null;
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean isCurrentLayerRegisteredClass(String className) {
        assert respectClassLoader();
        RuntimeConditionSet conditionSet = currentLayer().knownClassNames.get(className);
        if (conditionSet == null) {
            return false;
        }
        return conditionSet.satisfied();
    }

    private static RuntimeConditionSet getConditionForName(String className) {
        for (var singleton : layeredSingletons()) {
            RuntimeConditionSet conditionSet = singleton.knownClassNames.get(className);
            if (conditionSet != null) {
                return conditionSet;
            }
        }
        return null;
    }

    public static Throwable getSavedException(String className) {
        assert respectClassLoader() && !RuntimeClassLoading.isSupported();
        if (!isRegisteredClass(className)) {
            return null;
        }
        Throwable exception = null;
        for (var singleton : layeredSingletons()) {
            exception = singleton.knownExceptions.get(className);
            if (exception != null) {
                break;
            }
        }
        return exception;
    }

    /**
     * Checks whether {@code hub} can be instantiated with {@code Unsafe.allocateInstance}. Note
     * that arrays can't be instantiated and this function will always return false for array types.
     */
    public static boolean canUnsafeInstantiateAsInstance(DynamicHub hub) {
        Class<?> clazz = DynamicHub.toClass(hub);
        if (MetadataTracer.enabled()) {
            MetadataTracer.singleton().traceUnsafeAllocatedType(clazz);
        }
        RuntimeConditionSet conditionSet = null;
        for (var singleton : layeredSingletons()) {
            conditionSet = singleton.unsafeInstantiatedClasses.get(clazz);
            if (conditionSet != null) {
                break;
            }
        }
        if (conditionSet != null) {
            return conditionSet.satisfied();
        }
        return false;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        List<String> classNames = new ArrayList<>();
        List<Boolean> classStates = new ArrayList<>();
        Set<String> unsafeNames = new HashSet<>(previousLayerUnsafe);

        var cursor = knownClasses.getEntries();
        while (cursor.advance()) {
            classNames.add(cursor.getKey());
            boolean isNegativeQuery = cursor.getValue().getValueUnconditionally() == NEGATIVE_QUERY;
            classStates.add(!isNegativeQuery);
        }

        for (var entry : previousLayerClasses.entrySet()) {
            /*
             * If a complete entry overwrites a negative query from a previous layer, the
             * previousLayerClasses map entry needs to be skipped to register the new entry for
             * extension layers.
             */
            if (!classNames.contains(entry.getKey())) {
                classNames.add(entry.getKey());
                classStates.add(entry.getValue());
            }
        }

        unsafeInstantiatedClasses.getKeys().iterator().forEachRemaining(c -> unsafeNames.add(c.getName()));

        writer.writeStringList(CLASSES_REGISTERED, classNames);
        writer.writeBoolList(CLASSES_REGISTERED_STATES, classStates);
        writer.writeStringList(UNSAFE_REGISTERED, unsafeNames.stream().toList());
        /*
         * The option is not accessible when the singleton is loaded, so the boolean needs to be
         * persisted.
         */
        writer.writeInt(RESPECTS_CLASS_LOADER, respectClassLoader() ? 1 : 0);

        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        List<String> previousLayerClassKeys = loader.readStringList(CLASSES_REGISTERED);
        List<Boolean> previousLayerClassStates = loader.readBoolList(CLASSES_REGISTERED_STATES);

        Map<String, Boolean> previousLayerClasses = new HashMap<>();
        for (int i = 0; i < previousLayerClassKeys.size(); ++i) {
            previousLayerClasses.put(previousLayerClassKeys.get(i), previousLayerClassStates.get(i));
        }

        Set<String> previousLayerUnsafe = Set.copyOf(loader.readStringList(UNSAFE_REGISTERED));
        boolean respectsClassLoader = loader.readInt(RESPECTS_CLASS_LOADER) == 1;

        return new ClassForNameSupport(Collections.unmodifiableMap(previousLayerClasses), previousLayerUnsafe, respectsClassLoader);
    }
}
