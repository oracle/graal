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
import com.oracle.svm.hosted.substitute.DeletedElementException;
import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;
import com.oracle.svm.util.ReflectionUtil;

public class ReflectionDataBuilder implements RuntimeReflectionSupport {
    private enum FieldFlag {
        FINAL_BUT_WRITABLE,
        UNSAFE_ACCESSIBLE,
    }

    private static final EnumSet<FieldFlag> NO_FIELD_FLAGS = EnumSet.noneOf(FieldFlag.class);

    private boolean modified;
    private boolean sealed;

    private final DynamicHub.ReflectionData arrayReflectionData;
    private Set<Class<?>> reflectionClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Set<Executable> reflectionMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Map<Field, EnumSet<FieldFlag>> reflectionFields = new ConcurrentHashMap<>();
    private Set<Field> analyzedFinalFields = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ReflectionDataBuilder() {
        arrayReflectionData = getArrayReflectionData();
    }

    private static DynamicHub.ReflectionData getArrayReflectionData() {
        Method[] publicArrayMethods;
        try {
            Class<?>[] parameterTypes = {};
            Method getPublicMethodsMethod = ReflectionUtil.lookupMethod(Class.class, "privateGetPublicMethods", parameterTypes);
            publicArrayMethods = (Method[]) getPublicMethodsMethod.invoke(Object[].class);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }

        // array classes only have methods inherited from Object
        return new DynamicHub.ReflectionData(
                        new Field[0],
                        new Field[0],
                        new Field[0],
                        new Method[0],
                        publicArrayMethods,
                        new Constructor<?>[0],
                        new Constructor<?>[0],
                        null,
                        new Field[0],
                        new Method[0],
                        new Class<?>[0],
                        new Class<?>[0],
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
        if (reflectionMethods.addAll(Arrays.asList(methods))) {
            modified = true;
        }
    }

    @Override
    public void register(boolean finalIsWritable, boolean allowUnsafeAccess, Field... fields) {
        checkNotSealed();
        for (Field field : fields) {
            boolean writable = finalIsWritable || !Modifier.isFinal(field.getModifiers());
            EnumSet<FieldFlag> flags = (writable && allowUnsafeAccess) ? EnumSet.of(FieldFlag.FINAL_BUT_WRITABLE, FieldFlag.UNSAFE_ACCESSIBLE) : //
                            (writable ? EnumSet.of(FieldFlag.FINAL_BUT_WRITABLE) : (allowUnsafeAccess ? EnumSet.of(FieldFlag.UNSAFE_ACCESSIBLE) : NO_FIELD_FLAGS));
            reflectionFields.compute(field, (key, existingFlags) -> {
                if (writable && (existingFlags == null || !existingFlags.contains(FieldFlag.FINAL_BUT_WRITABLE))) {
                    UserError.guarantee(!analyzedFinalFields.contains(field),
                                    "A field that was already processed by the analysis cannot be re-registered as writable: " + field.toString());
                }
                return flags;
            });
        }
    }

    private void checkNotSealed() {
        if (sealed) {
            throw UserError.abort("Too late to add classes, methods, and fields for reflective access. Registration must happen in a Feature before the analysis has finised.");
        }
    }

    protected void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;

        if (!modified) {
            return;
        }
        modified = false;
        access.requireAnalysisIteration();
        Class<?>[] parameterTypes = {};

        Method reflectionDataMethod = ReflectionUtil.lookupMethod(Class.class, "reflectionData", parameterTypes);
        Class<?> originalReflectionDataClass = access.getImageClassLoader().findClassByName("java.lang.Class$ReflectionData");
        Field declaredFieldsField = ReflectionUtil.lookupField(originalReflectionDataClass, "declaredFields");
        Field publicFieldsField = ReflectionUtil.lookupField(originalReflectionDataClass, "publicFields");
        Field declaredMethodsField = ReflectionUtil.lookupField(originalReflectionDataClass, "declaredMethods");
        Field publicMethodsField = ReflectionUtil.lookupField(originalReflectionDataClass, "publicMethods");
        Field declaredConstructorsField = ReflectionUtil.lookupField(originalReflectionDataClass, "declaredConstructors");
        Field publicConstructorsField = ReflectionUtil.lookupField(originalReflectionDataClass, "publicConstructors");
        Field declaredPublicFieldsField = ReflectionUtil.lookupField(originalReflectionDataClass, "declaredPublicFields");
        Field declaredPublicMethodsField = ReflectionUtil.lookupField(originalReflectionDataClass, "declaredPublicMethods");

        Set<Class<?>> allClasses = new HashSet<>(reflectionClasses);
        reflectionMethods.stream().map(Executable::getDeclaringClass).forEach(allClasses::add);
        reflectionFields.forEach((field, flags) -> {
            if (flags.contains(FieldFlag.UNSAFE_ACCESSIBLE)) {
                access.registerAsUnsafeAccessed(field);
            }
            allClasses.add(field.getDeclaringClass());
        });

        /*
         * We need to find all classes that have an enclosingMethod or enclosingConstructor.
         * Unfortunately, there is no reverse lookup (ask a Method or Constructor about the classes
         * they contain), so we need to iterate through all types that have been loaded so far.
         * Accessing the original java.lang.Class for a ResolvedJavaType is not 100% reliable,
         * especially in the case of class and method substitutions. But it is the best we can do
         * here, and we assume that user code that requires reflection support is not using
         * substitutions.
         */
        for (AnalysisType aType : access.getUniverse().getTypes()) {
            Class<?> originalClass = aType.getJavaClass();
            if (originalClass != null && enclosingMethodOrConstructor(originalClass) != null) {
                /*
                 * We haven an enclosing method or constructor for this class, so we add the class
                 * to the set of processed classes so that the ReflectionData is initialized below.
                 */
                allClasses.add(originalClass);
            } else if (originalClass != null && originalClass.isArray()) {
                // Always register reflection data for array classes
                allClasses.add(originalClass);
            }
        }

        for (Class<?> clazz : allClasses) {
            AnalysisType type = access.getMetaAccess().lookupJavaType(clazz);
            DynamicHub hub = access.getHostVM().dynamicHub(type);

            if (type.isArray()) {
                /*
                 * Array types allocated reflectively need to be registered as instantiated.
                 * Otherwise the isInstantiated check in AllocationSnippets.checkDynamicHub() will
                 * fail at runtime when the array is *only* allocated through Array.newInstance().
                 */
                type.registerAsInHeap();
            }

            if (reflectionClasses.contains(clazz)) {
                ClassForNameSupport.registerClass(clazz);
            }

            /*
             * Ensure all internal fields of the original Class.ReflectionData object are
             * initialized. Calling the public methods triggers lazy initialization of the fields.
             */
            try {
                clazz.getDeclaredFields();
                clazz.getFields();
                clazz.getDeclaredMethods();
                clazz.getMethods();
                clazz.getDeclaredConstructors();
                clazz.getConstructors();
                clazz.getDeclaredClasses();
                clazz.getClasses();
            } catch (NoClassDefFoundError e) {
                /*
                 * If any of the methods or fields reference missing types in their signatures a
                 * NoClassDefFoundError is thrown. Skip registering reflection metadata for this
                 * class.
                 */
                // Checkstyle: stop
                System.out.println("WARNING: Could not register reflection metadata for " + clazz.getTypeName() +
                                ". Reason: " + e.getClass().getTypeName() + ": " + e.getMessage() + ".");
                // Checkstyle: resume
                continue;
            }

            try {
                Object originalReflectionData = reflectionDataMethod.invoke(clazz);
                DynamicHub.ReflectionData reflectionData;

                if (type.isArray()) {
                    // Always register reflection data for array classes
                    reflectionData = arrayReflectionData;
                } else {
                    reflectionData = new DynamicHub.ReflectionData(
                                    filterFields(declaredFieldsField.get(originalReflectionData), reflectionFields.keySet(), access.getMetaAccess()),
                                    filterFields(publicFieldsField.get(originalReflectionData), reflectionFields.keySet(), access.getMetaAccess()),
                                    filterFields(publicFieldsField.get(originalReflectionData), f -> reflectionFields.containsKey(f) && !isHiddenIn(f, clazz), access.getMetaAccess()),
                                    filterMethods(declaredMethodsField.get(originalReflectionData), reflectionMethods, access.getMetaAccess()),
                                    filterMethods(publicMethodsField.get(originalReflectionData), reflectionMethods, access.getMetaAccess()),
                                    filterConstructors(declaredConstructorsField.get(originalReflectionData), reflectionMethods, access.getMetaAccess()),
                                    filterConstructors(publicConstructorsField.get(originalReflectionData), reflectionMethods, access.getMetaAccess()),
                                    nullaryConstructor(declaredConstructorsField.get(originalReflectionData), reflectionMethods),
                                    filterFields(declaredPublicFieldsField.get(originalReflectionData), reflectionFields.keySet(), access.getMetaAccess()),
                                    filterMethods(declaredPublicMethodsField.get(originalReflectionData), reflectionMethods, access.getMetaAccess()),
                                    filterClasses(clazz.getDeclaredClasses(), reflectionClasses, access.getMetaAccess()),
                                    filterClasses(clazz.getClasses(), reflectionClasses, access.getMetaAccess()),
                                    enclosingMethodOrConstructor(clazz));
                }

                hub.setReflectionData(reflectionData);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }
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
        } catch (NoClassDefFoundError e) {
            /*
             * If any of the methods or fields in the class of the enclosing method reference
             * missing types in their signatures a NoClassDefFoundError is thrown. Skip the class.
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
        } else if (enclosingMethod != null && enclosingConstructor != null) {
            throw VMError.shouldNotReachHere("Class has both an enclosingMethod and an enclosingConstructor: " + clazz + ", " + enclosingMethod + ", " + enclosingConstructor);
        }

        Executable enclosingMethodOrConstructor = enclosingMethod != null ? enclosingMethod : enclosingConstructor;

        if (reflectionMethods.contains(enclosingMethodOrConstructor)) {
            return enclosingMethodOrConstructor;
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
        return result.toArray(new Field[0]);
    }

    private static Constructor<?>[] filterConstructors(Object methods, Set<Executable> filter, AnalysisMetaAccess metaAccess) {
        return filterMethods(methods, filter, metaAccess, new Constructor<?>[0]);
    }

    private static Method[] filterMethods(Object methods, Set<Executable> filter, AnalysisMetaAccess metaAccess) {
        return filterMethods(methods, filter, metaAccess, new Method[0]);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Executable> T[] filterMethods(Object methods, Set<Executable> filter, AnalysisMetaAccess metaAccess, T[] prototypeArray) {
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
        return result.toArray(new Class<?>[0]);
    }

    boolean inspectFinalFieldWritableForAnalysis(Field field) {
        assert Modifier.isFinal(field.getModifiers());
        EnumSet<FieldFlag> flags = reflectionFields.getOrDefault(field, NO_FIELD_FLAGS);
        analyzedFinalFields.add(field);
        return flags.contains(FieldFlag.FINAL_BUT_WRITABLE);
    }
}
