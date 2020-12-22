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
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.substitute.DeletedElementException;
import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;
import com.oracle.svm.util.ReflectionUtil;

public class ReflectionDataBuilder implements RuntimeReflectionSupport {

    public static final Field[] EMPTY_FIELDS = new Field[0];
    public static final Method[] EMPTY_METHODS = new Method[0];
    public static final Constructor<?>[] EMPTY_CONSTRUCTORS = new Constructor<?>[0];
    public static final Class<?>[] EMPTY_CLASSES = new Class<?>[0];

    private enum FieldFlag {
        FINAL_BUT_WRITABLE,
        UNSAFE_ACCESSIBLE,
    }

    private boolean modified;
    private boolean sealed;

    private final DynamicHub.ReflectionData arrayReflectionData;
    private final Set<Class<?>> reflectionClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Method> reflectionMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Constructor<?>> reflectionConstructors = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Field, EnumSet<FieldFlag>> reflectionFields = new ConcurrentHashMap<>();
    private final Set<Class<?>> reflectionMethodsDeclaringClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Class<?>> reflectionConstructorsDeclaringClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Class<?>> reflectionFieldsDeclaringClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Field> analyzedFinalFields = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /* Keep track of classes already processed for reflection. */
    private final Set<Class<?>> processedClasses = new HashSet<>();

    private final ReflectionDataAccessors accessors;

    public ReflectionDataBuilder(DuringSetupAccessImpl access) {
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
        return new DynamicHub.ReflectionData(
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
                        null);
    }

    @Override
    public void register(Class<?>... classes) {
        checkNotSealed();
        if (reflectionClasses.addAll(Arrays.asList(classes))) {
            modified = true;
        }
    }

    @Override
    public void register(Executable... methods) {
        checkNotSealed();
        for (Executable executable : methods) {
            if (executable instanceof Method) {
                reflectionMethods.add((Method) executable);
                reflectionMethodsDeclaringClasses.add(executable.getDeclaringClass());
            } else {
                reflectionConstructors.add((Constructor<?>) executable);
                reflectionConstructorsDeclaringClasses.add(executable.getDeclaringClass());
            }
            modified = true;
        }
    }

    @Override
    public void register(boolean finalIsWritable, boolean allowUnsafeAccess, Field... fields) {
        checkNotSealed();
        for (Field field : fields) {
            EnumSet<FieldFlag> flags = EnumSet.noneOf(FieldFlag.class);
            if (finalIsWritable) {
                flags.add(FieldFlag.FINAL_BUT_WRITABLE);
            }
            if (allowUnsafeAccess) {
                flags.add(FieldFlag.UNSAFE_ACCESSIBLE);
            }

            reflectionFieldsDeclaringClasses.add(field.getDeclaringClass());
            reflectionFields.compute(field, (key, existingFlags) -> {
                if (existingFlags == null || !existingFlags.containsAll(flags)) {
                    modified = true;
                }
                if (existingFlags != null) {
                    /* Preserve flags of existing registration. */
                    flags.addAll(existingFlags);
                }

                if (finalIsWritable && (existingFlags == null || !existingFlags.contains(FieldFlag.FINAL_BUT_WRITABLE))) {
                    UserError.guarantee(!analyzedFinalFields.contains(field),
                                    "A field that was already processed by the analysis cannot be re-registered as writable: %s", field);
                }
                return flags;
            });
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
        if (!modified) {
            return;
        }
        modified = false;
        access.requireAnalysisIteration();

        reflectionFields.forEach((field, flags) -> {
            if (flags.contains(FieldFlag.UNSAFE_ACCESSIBLE)) {
                access.registerAsUnsafeAccessed(field);
            }
        });

        Set<Class<?>> allClasses = new HashSet<>(reflectionClasses);
        allClasses.addAll(reflectionFieldsDeclaringClasses);
        allClasses.addAll(reflectionMethodsDeclaringClasses);
        allClasses.addAll(reflectionConstructorsDeclaringClasses);
        allClasses.forEach(clazz -> processClass(access, clazz));
    }

    private void processClass(DuringAnalysisAccessImpl access, Class<?> clazz) {
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

        DynamicHub.ReflectionData reflectionData;

        if (type.isArray()) {
            // Always register reflection data for array classes
            reflectionData = arrayReflectionData;
        } else {
            try {
                Object originalReflectionData = accessors.getReflectionData(clazz);

                Field[] declaredFields = EMPTY_FIELDS;
                Field[] publicFields = EMPTY_FIELDS;
                Field[] publicUnhiddenFields = EMPTY_FIELDS;
                Field[] declaredPublicFields = EMPTY_FIELDS;
                if (reflectionFieldsDeclaringClasses.contains(clazz)) {
                    /*
                     * Ensure all internal fields of the original Class.ReflectionData object are
                     * initialized. Calling the public methods triggers lazy initialization of the
                     * fields.
                     */
                    clazz.getDeclaredFields();
                    clazz.getFields();
                    declaredFields = filterFields(accessors.getDeclaredFields(originalReflectionData), reflectionFields.keySet(), access.getMetaAccess());
                    publicFields = filterFields(accessors.getPublicFields(originalReflectionData), reflectionFields.keySet(), access.getMetaAccess());
                    publicUnhiddenFields = filterFields(accessors.getPublicFields(originalReflectionData), f -> reflectionFields.containsKey(f) && !isHiddenIn(f, clazz), access.getMetaAccess());
                    declaredPublicFields = filterFields(accessors.getDeclaredPublicFields(originalReflectionData), reflectionFields.keySet(), access.getMetaAccess());
                }

                Method[] declaredMethods = EMPTY_METHODS;
                Method[] publicMethods = EMPTY_METHODS;
                Method[] declaredPublicMethods = EMPTY_METHODS;
                if (reflectionMethodsDeclaringClasses.contains(clazz)) {
                    /*
                     * Ensure all internal fields of the original Class.ReflectionData object are
                     * initialized. Calling the public methods triggers lazy initialization of the
                     * fields.
                     */
                    clazz.getDeclaredMethods();
                    clazz.getMethods();
                    declaredMethods = filterMethods(accessors.getDeclaredMethods(originalReflectionData), reflectionMethods, access.getMetaAccess());
                    publicMethods = filterMethods(accessors.getPublicMethods(originalReflectionData), reflectionMethods, access.getMetaAccess());
                    declaredPublicMethods = filterMethods(accessors.getDeclaredPublicMethods(originalReflectionData), reflectionMethods, access.getMetaAccess());
                }

                Constructor<?>[] declaredConstructors = EMPTY_CONSTRUCTORS;
                Constructor<?>[] publicConstructors = EMPTY_CONSTRUCTORS;
                Constructor<?> nullaryConstructor = null;
                if (reflectionConstructorsDeclaringClasses.contains(clazz)) {
                    /*
                     * Ensure all internal fields of the original Class.ReflectionData object are
                     * initialized. Calling the public methods triggers lazy initialization of the
                     * fields.
                     */
                    clazz.getDeclaredConstructors();
                    clazz.getConstructors();
                    declaredConstructors = filterConstructors(accessors.getDeclaredConstructors(originalReflectionData), reflectionConstructors, access.getMetaAccess());
                    publicConstructors = filterConstructors(accessors.getPublicConstructors(originalReflectionData), reflectionConstructors, access.getMetaAccess());
                    nullaryConstructor = nullaryConstructor(accessors.getDeclaredConstructors(originalReflectionData), reflectionConstructors);
                }

                Class<?>[] declaredClasses = EMPTY_CLASSES;
                Class<?>[] publicClasses = EMPTY_CLASSES;
                if (reflectionClasses.contains(clazz)) {
                    /*
                     * Ensure all internal fields of the original Class.ReflectionData object are
                     * initialized. Calling the public methods triggers lazy initialization of the
                     * fields.
                     */
                    clazz.getDeclaredClasses();
                    clazz.getClasses();
                    declaredClasses = filterClasses(clazz.getDeclaredClasses(), reflectionClasses, access.getMetaAccess());
                    publicClasses = filterClasses(clazz.getClasses(), reflectionClasses, access.getMetaAccess());
                }

                reflectionData = new DynamicHub.ReflectionData(
                                declaredFields,
                                publicFields,
                                publicUnhiddenFields,
                                declaredMethods,
                                publicMethods,
                                declaredConstructors,
                                publicConstructors,
                                nullaryConstructor,
                                declaredPublicFields,
                                declaredPublicMethods,
                                declaredClasses,
                                publicClasses,
                                enclosingMethodOrConstructor(clazz));
            } catch (TypeNotPresentException | LinkageError e) {
                /*
                 * If any of the methods or fields signatures reference missing types or types that
                 * have incompatible changes a LinkageError is thrown. Skip registering reflection
                 * metadata for this class.
                 *
                 * If the class fails verification then no reflection metadata can be registered.
                 * However, the class is still registered for run time loading with Class.forName()
                 * and its class initializer is replaced with a synthesized 'throw new
                 * VerifyError()' (see ClassInitializationFeature.buildRuntimeInitializationInfo()).
                 */
                // Checkstyle: stop
                System.out.println("WARNING: Could not register reflection metadata for " + clazz.getTypeName() +
                                ". Reason: " + e.getClass().getTypeName() + ": " + e.getMessage() + '.');
                // Checkstyle: resume
                return;
            }
        }

        hub.setReflectionData(reflectionData);
    }

    protected void afterAnalysis() {
        sealed = true;
        if (modified) {
            throw UserError.abort("Registration of classes, methods, and fields for reflective access during analysis must set DuringAnalysisAccess.requireAnalysisIteration().");
        }
    }

    private static Constructor<?> nullaryConstructor(Object constructors, Set<?> reflectionMethods) {
        for (Constructor<?> constructor : (Constructor<?>[]) constructors) {
            if (constructor.getParameterCount() == 0 && reflectionMethods.contains(constructor)) {
                return constructor;
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
            // Checkstyle: stop
            System.err.println("GR-7731: Could not find the enclosing method of class " + clazz.getTypeName() +
                            ". This is a known transient error and most likely does not cause any problems, unless your code relies on the enclosing method of exactly this class. If you can reliably reproduce this problem, please send us a test case.");
            // Checkstyle: resume
            return null;
        }

        if (enclosingMethod == null && enclosingConstructor == null) {
            return null;
        }
        if (enclosingMethod != null && enclosingConstructor != null) {
            throw VMError.shouldNotReachHere("Class has both an enclosingMethod and an enclosingConstructor: " + clazz + ", " + enclosingMethod + ", " + enclosingConstructor);
        }

        if (enclosingMethod != null && reflectionMethods.contains(enclosingMethod)) {
            return enclosingMethod;
        } else if (enclosingConstructor != null && reflectionConstructors.contains(enclosingConstructor)) {
            return enclosingConstructor;
        } else {
            return null;
        }
    }

    private static Field[] filterFields(Object fields, Set<Field> filterSet, AnalysisMetaAccess metaAccess) {
        return filterFields(fields, filterSet::contains, metaAccess);
    }

    private static boolean isHiddenIn(Field field, Class<?> clazz) {
        try {
            return !clazz.getField(field.getName()).equals(field);
        } catch (NoSuchFieldException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static Field[] filterFields(Object fields, Predicate<Field> filter, AnalysisMetaAccess metaAccess) {
        List<Field> result = new ArrayList<>();
        for (Field field : (Field[]) fields) {
            if (filter.test(field) && !SubstitutionReflectivityFilter.shouldExclude(field, metaAccess)) {
                try {
                    if (!metaAccess.lookupJavaField(field).isAnnotationPresent(Delete.class)) {
                        result.add(field);
                    }
                } catch (DeletedElementException ignored) { // filter
                }
            }
        }
        return result.toArray(EMPTY_FIELDS);
    }

    private static Constructor<?>[] filterConstructors(Object methods, Set<Constructor<?>> filter, AnalysisMetaAccess metaAccess) {
        return filterMethods(methods, filter, metaAccess, EMPTY_CONSTRUCTORS);
    }

    private static Method[] filterMethods(Object methods, Set<Method> filter, AnalysisMetaAccess metaAccess) {
        return filterMethods(methods, filter, metaAccess, EMPTY_METHODS);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Executable> T[] filterMethods(Object methods, Set<T> filter, AnalysisMetaAccess metaAccess, T[] prototypeArray) {
        List<T> result = new ArrayList<>();
        for (T method : (T[]) methods) {
            if (filter.contains(method) && !SubstitutionReflectivityFilter.shouldExclude(method, metaAccess)) {
                result.add(method);
            }
        }
        return result.toArray(prototypeArray);
    }

    private static Class<?>[] filterClasses(Object classes, Set<Class<?>> filter, AnalysisMetaAccess metaAccess) {
        List<Class<?>> result = new ArrayList<>();
        for (Class<?> clazz : (Class<?>[]) classes) {
            if (filter.contains(clazz) && !SubstitutionReflectivityFilter.shouldExclude(clazz, metaAccess)) {
                result.add(clazz);
            }
        }
        return result.toArray(EMPTY_CLASSES);
    }

    boolean inspectFinalFieldWritableForAnalysis(Field field) {
        assert Modifier.isFinal(field.getModifiers());
        EnumSet<FieldFlag> flags = reflectionFields.get(field);
        analyzedFinalFields.add(field);
        return flags != null && flags.contains(FieldFlag.FINAL_BUT_WRITABLE);
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

        ReflectionDataAccessors(DuringSetupAccessImpl access) {
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
