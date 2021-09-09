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
package com.oracle.svm.reflect.hosted;

//Checkstyle: allow reflection

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.RecordSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.FeatureAccessImpl;
import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;
import com.oracle.svm.util.ReflectionUtil;

public class ReflectionDataBuilder implements RuntimeReflectionSupport {

    public static final Field[] EMPTY_FIELDS = new Field[0];
    public static final Method[] EMPTY_METHODS = new Method[0];
    public static final Constructor<?>[] EMPTY_CONSTRUCTORS = new Constructor<?>[0];
    public static final Class<?>[] EMPTY_CLASSES = new Class<?>[0];

    private final Set<Class<?>> modifiedClasses = ConcurrentHashMap.newKeySet();
    private boolean sealed;

    private final DynamicHub.ReflectionData arrayReflectionData;
    private final Set<Class<?>> reflectionClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Executable> reflectionMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Field> reflectionFields = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /* Keep track of classes already processed for reflection. */
    private final Set<Class<?>> processedClasses = new HashSet<>();

    private final ReflectionDataAccessors accessors;

    public ReflectionDataBuilder(FeatureAccessImpl access) {
        arrayReflectionData = getArrayReflectionData();
        accessors = new ReflectionDataAccessors(access);
    }

    private static DynamicHub.ReflectionData getArrayReflectionData() {
        Method[] publicArrayMethods;
        try {
            Method getPublicMethodsMethod = ReflectionUtil.lookupMethod(Class.class, "privateGetPublicMethods");
            publicArrayMethods = (Method[]) getPublicMethodsMethod.invoke(Object[].class);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }

        // array classes only have methods inherited from Object
        return DynamicHub.ReflectionData.get(
                        EMPTY_FIELDS,
                        EMPTY_FIELDS,
                        EMPTY_FIELDS,
                        EMPTY_METHODS,
                        publicArrayMethods,
                        EMPTY_CONSTRUCTORS,
                        EMPTY_CONSTRUCTORS,
                        null,
                        EMPTY_FIELDS,
                        EMPTY_METHODS,
                        EMPTY_CLASSES,
                        EMPTY_CLASSES,
                        null,
                        null);
    }

    @Override
    public void register(Class<?>... classes) {
        checkNotSealed();
        for (Class<?> clazz : classes) {
            if (reflectionClasses.add(clazz)) {
                modifiedClasses.add(clazz);
            }
        }
    }

    @Override
    public void register(Executable... methods) {
        checkNotSealed();
        for (Executable method : methods) {
            if (reflectionMethods.add(method)) {
                modifiedClasses.add(method.getDeclaringClass());
            }
        }
    }

    @Override
    public void register(boolean finalIsWritable, Field... fields) {
        checkNotSealed();
        // Unsafe and write accesses are always enabled for fields because accessors use Unsafe.
        for (Field field : fields) {
            if (reflectionFields.add(field)) {
                modifiedClasses.add(field.getDeclaringClass());
            }
        }
    }

    private void checkNotSealed() {
        if (sealed) {
            throw UserError.abort("Too late to add classes, methods, and fields for reflective access. Registration must happen in a Feature before the analysis has finished.");
        }
    }

    protected void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        processReachableTypes(access);
        processRegisteredElements(access);
    }

    /*
     * Process all reachable types, looking for array types or types that have an enclosing method
     * or constructor. Initialize the reflection metadata for those types.
     */
    private void processReachableTypes(DuringAnalysisAccessImpl access) {
        /*
         * We need to find all classes that have an enclosingMethod or enclosingConstructor.
         * Unfortunately, there is no reverse lookup (ask a Method or Constructor about the classes
         * they contain), so we need to iterate through all types that have been loaded so far.
         * Accessing the original java.lang.Class for a ResolvedJavaType is not 100% reliable,
         * especially in the case of class and method substitutions. But it is the best we can do
         * here, and we assume that user code that requires reflection support is not using
         * substitutions.
         */
        for (AnalysisType type : access.getUniverse().getTypes()) {
            Class<?> originalClass = type.getJavaClass();
            if (originalClass != null) {
                if (processedClasses.contains(originalClass)) {
                    /* Class has already been processed. */
                    continue;
                }
                if (type.isArray() && !access.isReachable(type)) {
                    /*
                     * We don't want the array type (and its elemental type) to become reachable as
                     * a result of initializing its reflection data.
                     */
                    continue;
                }
                if (type.isArray() || enclosingMethodOrConstructor(originalClass) != null) {
                    /*
                     * This type is either an array or it has an enclosing method or constructor. In
                     * either case we process the class, i.e., initialize its reflection data, mark
                     * it as processed and require an analysis iteration.
                     */
                    processClass(access, originalClass);
                    processedClasses.add(originalClass);
                    access.requireAnalysisIteration();
                }
            }
        }
    }

    private void processRegisteredElements(DuringAnalysisAccessImpl access) {
        if (modifiedClasses.isEmpty()) {
            return;
        }
        access.requireAnalysisIteration();

        for (Class<?> clazz : modifiedClasses) {
            processClass(access, clazz);
        }
        modifiedClasses.clear();
    }

    private void processClass(DuringAnalysisAccessImpl access, Class<?> clazz) {
        if (SubstitutionReflectivityFilter.shouldExclude(clazz, access.getMetaAccess(), access.getUniverse())) {
            return;
        }

        AnalysisType type = access.getMetaAccess().lookupJavaType(clazz);
        /*
         * Make sure the class is registered as reachable before its fields are accessed below to
         * build the reflection metadata.
         */
        type.registerAsReachable();
        DynamicHub hub = access.getHostVM().dynamicHub(type);

        if (reflectionClasses.contains(clazz)) {
            ClassForNameSupport.registerClass(clazz);
        }

        /*
         * Trigger initialization of the fields of the original Class.ReflectionData object by
         * calling the public methods.
         *
         * If one of the called methods throws a LinkageError because of a missing type or types
         * that have incompatible changes, we skip registering that part of the reflection metadata
         * for this class, but continue to try to register other parts of the reflection metadata.
         *
         * If the class fails verification then no reflection metadata can be registered. However,
         * the class is still registered for run time loading with Class.forName() and its class
         * initializer is replaced with a synthesized 'throw new VerifyError()' (see
         * ClassInitializationFeature.buildRuntimeInitializationInfo()).
         */
        List<Throwable> errors = new ArrayList<>();
        query(clazz::getDeclaredFields, errors);
        query(clazz::getFields, errors);
        query(clazz::getDeclaredMethods, errors);
        query(clazz::getMethods, errors);
        query(clazz::getDeclaredConstructors, errors);
        query(clazz::getConstructors, errors);
        Class<?>[] declaredClasses = query(clazz::getDeclaredClasses, errors);
        Class<?>[] classes = query(clazz::getClasses, errors);
        reportLinkingErrors(clazz, errors);

        Object originalReflectionData = accessors.getReflectionData(clazz);
        DynamicHub.ReflectionData reflectionData;

        if (type.isArray()) {
            // Always register reflection data for array classes
            reflectionData = arrayReflectionData;
        } else {
            reflectionData = DynamicHub.ReflectionData.get(
                            filterFields(accessors.getDeclaredFields(originalReflectionData), reflectionFields, access),
                            filterFields(accessors.getPublicFields(originalReflectionData), reflectionFields, access),
                            filterFields(accessors.getPublicFields(originalReflectionData), f -> reflectionFields.contains(f) && !isHiddenIn(f, clazz), access),
                            filterMethods(accessors.getDeclaredMethods(originalReflectionData), reflectionMethods, access),
                            filterMethods(accessors.getPublicMethods(originalReflectionData), reflectionMethods, access),
                            filterConstructors(accessors.getDeclaredConstructors(originalReflectionData), reflectionMethods, access),
                            filterConstructors(accessors.getPublicConstructors(originalReflectionData), reflectionMethods, access),
                            nullaryConstructor(accessors.getDeclaredConstructors(originalReflectionData), reflectionMethods, access),
                            filterFields(accessors.getDeclaredPublicFields(originalReflectionData), reflectionFields, access),
                            filterMethods(accessors.getDeclaredPublicMethods(originalReflectionData), reflectionMethods, access),
                            filterClasses(declaredClasses, reflectionClasses, access),
                            filterClasses(classes, reflectionClasses, access),
                            enclosingMethodOrConstructor(clazz),
                            buildRecordComponents(clazz, access));
        }
        hub.setReflectionData(reflectionData);
    }

    private static <T> T query(Callable<T> callable, List<Throwable> errors) {
        try {
            return callable.call();
        } catch (TypeNotPresentException | LinkageError e) {
            errors.add(e);
        } catch (Exception e) {
            throw VMError.shouldNotReachHere(e);
        }
        return null;
    }

    private Object[] buildRecordComponents(Class<?> clazz, DuringAnalysisAccessImpl access) {
        RecordSupport support = RecordSupport.singleton();
        if (!support.isRecord(clazz)) {
            return null;
        }

        /*
         * RecordComponent objects expose the "accessor method" as a java.lang.reflect.Method
         * object. We leverage this tight coupling of RecordComponent and its accessor method to
         * avoid a separate reflection configuration for record components: When all accessor
         * methods of the record class are registered for reflection, then the record components are
         * available. We do not want to expose a partial list of record components, that would be
         * confusing and error-prone. So as soon as a single accessor method is missing from the
         * reflection configuration, we provide no record components. Accessing the record
         * components in that case will throw an exception at image run time, see
         * DynamicHub.getRecordComponents0().
         */
        Method[] allMethods = support.getRecordComponentAccessorMethods(clazz);
        Method[] filteredMethods = filterMethods(allMethods, reflectionMethods, access);

        if (allMethods.length == filteredMethods.length) {
            return support.getRecordComponents(clazz);
        } else {
            return null;
        }
    }

    private static void reportLinkingErrors(Class<?> clazz, List<Throwable> errors) {
        if (errors.isEmpty()) {
            return;
        }
        String messages = errors.stream().map(e -> e.getClass().getTypeName() + ": " + e.getMessage())
                        .distinct().collect(Collectors.joining(", "));
        // Checkstyle: stop
        System.out.println("Warning: Could not register complete reflection metadata for " + clazz.getTypeName() + ". Reason(s): " + messages);
        // Checkstyle: resume
    }

    protected void afterAnalysis() {
        sealed = true;
        if (!modifiedClasses.isEmpty()) {
            throw UserError.abort("Registration of classes, methods, and fields for reflective access during analysis must set DuringAnalysisAccess.requireAnalysisIteration().");
        }
    }

    private static Constructor<?> nullaryConstructor(Object constructors, Set<?> reflectionMethods, DuringAnalysisAccessImpl access) {
        if (constructors != null) {
            for (Constructor<?> constructor : (Constructor<?>[]) constructors) {
                if (constructor.getParameterCount() == 0 && reflectionMethods.contains(constructor) &&
                                !SubstitutionReflectivityFilter.shouldExclude(constructor, access.getMetaAccess(), access.getUniverse())) {
                    return constructor;
                }
            }
        }
        return null;
    }

    private Executable enclosingMethodOrConstructor(Class<?> clazz) {
        Method enclosingMethod;
        Constructor<?> enclosingConstructor;
        try {
            enclosingMethod = clazz.getEnclosingMethod();
            enclosingConstructor = clazz.getEnclosingConstructor();
        } catch (TypeNotPresentException | LinkageError e) {
            /*
             * If any of the methods or fields in the class of the enclosing method reference
             * missing types or types that have incompatible changes a LinkageError is thrown. Skip
             * the class.
             */
            return null;
        } catch (InternalError ex) {
            /*
             * Could not find the enclosing method of the class. This is a host VM error which can
             * happen due to invalid bytecode. For example if the eclosing method index points to a
             * synthetic method for a anonymous class declared inside a lambda. We skip registering
             * the enclosing method for such classes.
             */
            return null;
        }

        if (enclosingMethod == null && enclosingConstructor == null) {
            return null;
        }
        if (enclosingMethod != null && enclosingConstructor != null) {
            throw VMError.shouldNotReachHere("Class has both an enclosingMethod and an enclosingConstructor: " + clazz + ", " + enclosingMethod + ", " + enclosingConstructor);
        }

        Executable enclosingMethodOrConstructor = enclosingMethod != null ? enclosingMethod : enclosingConstructor;

        if (reflectionMethods.contains(enclosingMethodOrConstructor)) {
            return enclosingMethodOrConstructor;
        } else {
            return null;
        }
    }

    private static Field[] filterFields(Object fields, Set<Field> filterSet, DuringAnalysisAccessImpl access) {
        return filterFields(fields, filterSet::contains, access);
    }

    private static boolean isHiddenIn(Field field, Class<?> clazz) {
        try {
            return !clazz.getField(field.getName()).equals(field);
        } catch (NoSuchFieldException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static Field[] filterFields(Object fields, Predicate<Field> filter, DuringAnalysisAccessImpl access) {
        if (fields == null) {
            return EMPTY_FIELDS;
        }
        List<Field> result = new ArrayList<>();
        for (Field field : (Field[]) fields) {
            if (filter.test(field) && !SubstitutionReflectivityFilter.shouldExclude(field, access.getMetaAccess(), access.getUniverse())) {
                result.add(field);
            }
        }
        return result.toArray(EMPTY_FIELDS);
    }

    private static Constructor<?>[] filterConstructors(Object methods, Set<Executable> filter, DuringAnalysisAccessImpl access) {
        return filterMethods(methods, filter, access, EMPTY_CONSTRUCTORS);
    }

    private static Method[] filterMethods(Object methods, Set<Executable> filter, DuringAnalysisAccessImpl access) {
        return filterMethods(methods, filter, access, EMPTY_METHODS);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Executable> T[] filterMethods(Object methods, Set<Executable> filter, DuringAnalysisAccessImpl access, T[] emptyArray) {
        if (methods == null) {
            return emptyArray;
        }
        List<T> result = new ArrayList<>();
        for (T method : (T[]) methods) {
            if (filter.contains(method) && !SubstitutionReflectivityFilter.shouldExclude(method, access.getMetaAccess(), access.getUniverse())) {
                result.add(method);
            }
        }
        return result.toArray(emptyArray);
    }

    private static Class<?>[] filterClasses(Object classes, Set<Class<?>> filter, DuringAnalysisAccessImpl access) {
        if (classes == null) {
            return EMPTY_CLASSES;
        }
        List<Class<?>> result = new ArrayList<>();
        for (Class<?> clazz : (Class<?>[]) classes) {
            if (filter.contains(clazz) && !SubstitutionReflectivityFilter.shouldExclude(clazz, access.getMetaAccess(), access.getUniverse())) {
                result.add(clazz);
            }
        }
        return result.toArray(EMPTY_CLASSES);
    }

    static final class ReflectionDataAccessors {
        private final Method reflectionDataMethod;
        private final Field declaredFieldsField;
        private final Field publicFieldsField;
        private final Field declaredMethodsField;
        private final Field publicMethodsField;
        private final Field declaredConstructorsField;
        private final Field publicConstructorsField;
        private final Field declaredPublicFieldsField;
        private final Field declaredPublicMethodsField;

        ReflectionDataAccessors(FeatureAccessImpl access) {
            reflectionDataMethod = ReflectionUtil.lookupMethod(Class.class, "reflectionData");
            Class<?> originalReflectionDataClass = access.getImageClassLoader().findClassOrFail("java.lang.Class$ReflectionData");
            declaredFieldsField = ReflectionUtil.lookupField(originalReflectionDataClass, "declaredFields");
            publicFieldsField = ReflectionUtil.lookupField(originalReflectionDataClass, "publicFields");
            declaredMethodsField = ReflectionUtil.lookupField(originalReflectionDataClass, "declaredMethods");
            publicMethodsField = ReflectionUtil.lookupField(originalReflectionDataClass, "publicMethods");
            declaredConstructorsField = ReflectionUtil.lookupField(originalReflectionDataClass, "declaredConstructors");
            publicConstructorsField = ReflectionUtil.lookupField(originalReflectionDataClass, "publicConstructors");
            declaredPublicFieldsField = ReflectionUtil.lookupField(originalReflectionDataClass, "declaredPublicFields");
            declaredPublicMethodsField = ReflectionUtil.lookupField(originalReflectionDataClass, "declaredPublicMethods");
        }

        public Object getReflectionData(Class<?> clazz) {
            try {
                return reflectionDataMethod.invoke(clazz);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        public Object getDeclaredFields(Object obj) {
            try {
                return declaredFieldsField.get(obj);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        public Object getPublicFields(Object obj) {
            try {
                return publicFieldsField.get(obj);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        public Object getDeclaredMethods(Object obj) {
            try {
                return declaredMethodsField.get(obj);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        public Object getPublicMethods(Object obj) {
            try {
                return publicMethodsField.get(obj);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        public Object getDeclaredConstructors(Object obj) {
            try {
                return declaredConstructorsField.get(obj);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        public Object getPublicConstructors(Object obj) {
            try {
                return publicConstructorsField.get(obj);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        public Object getDeclaredPublicFields(Object obj) {
            try {
                return declaredPublicFieldsField.get(obj);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        public Object getDeclaredPublicMethods(Object obj) {
            try {
                return declaredPublicMethodsField.get(obj);
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
    }
}
