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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.Feature.DuringAnalysisAccess;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;

public class ReflectionDataBuilder implements RuntimeReflectionSupport {

    private boolean modified;
    private boolean sealed;

    private Set<Class<?>> reflectionClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Set<Executable> reflectionMethods = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Set<Field> reflectionFields = Collections.newSetFromMap(new ConcurrentHashMap<>());

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
    public void register(Field... fields) {
        checkNotSealed();
        if (reflectionFields.addAll(Arrays.asList(fields))) {
            modified = true;
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

        Method reflectionDataMethod = findMethod(Class.class, "reflectionData");
        Class<?> originalReflectionDataClass = access.getImageClassLoader().findClassByName("java.lang.Class$ReflectionData");
        Field declaredFieldsField = findField(originalReflectionDataClass, "declaredFields");
        Field publicFieldsField = findField(originalReflectionDataClass, "publicFields");
        Field declaredMethodsField = findField(originalReflectionDataClass, "declaredMethods");
        Field publicMethodsField = findField(originalReflectionDataClass, "publicMethods");
        Field declaredConstructorsField = findField(originalReflectionDataClass, "declaredConstructors");
        Field publicConstructorsField = findField(originalReflectionDataClass, "publicConstructors");
        Field declaredPublicFieldsField = findField(originalReflectionDataClass, "declaredPublicFields");
        Field declaredPublicMethodsField = findField(originalReflectionDataClass, "declaredPublicMethods");

        Field[] emptyFields = new Field[0];
        Method[] emptyMethods = new Method[0];
        Constructor<?>[] emptyConstructors = new Constructor<?>[0];

        Set<Class<?>> allClasses = new HashSet<>(reflectionClasses);
        reflectionMethods.stream().map(method -> method.getDeclaringClass()).forEach(clazz -> allClasses.add(clazz));
        reflectionFields.stream().map(field -> field.getDeclaringClass()).forEach(clazz -> allClasses.add(clazz));

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
            clazz.getDeclaredFields();
            clazz.getFields();
            clazz.getDeclaredMethods();
            clazz.getMethods();
            clazz.getDeclaredConstructors();
            clazz.getConstructors();

            try {
                Object originalReflectionData = reflectionDataMethod.invoke(clazz);
                hub.setReflectionData(new DynamicHub.ReflectionData(
                                filter(declaredFieldsField.get(originalReflectionData), reflectionFields, emptyFields),
                                filter(publicFieldsField.get(originalReflectionData), reflectionFields, emptyFields),
                                filter(declaredMethodsField.get(originalReflectionData), reflectionMethods, emptyMethods),
                                filter(publicMethodsField.get(originalReflectionData), reflectionMethods, emptyMethods),
                                filter(declaredConstructorsField.get(originalReflectionData), reflectionMethods, emptyConstructors),
                                filter(publicConstructorsField.get(originalReflectionData), reflectionMethods, emptyConstructors),
                                nullaryConstructor(declaredConstructorsField.get(originalReflectionData), reflectionMethods),
                                filter(declaredPublicFieldsField.get(originalReflectionData), reflectionFields, emptyFields),
                                filter(declaredPublicMethodsField.get(originalReflectionData), reflectionMethods, emptyMethods),
                                enclosingMethodOrConstructor(clazz)));
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
            throw VMError.shouldNotReachHere("Classs has both an enclosingMethod and an enclosingConstructor: " + clazz + ", " + enclosingMethod + ", " + enclosingConstructor);
        }

        Executable enclosingMethodOrConstructor = enclosingMethod != null ? enclosingMethod : enclosingConstructor;

        if (reflectionMethods.contains(enclosingMethodOrConstructor)) {
            return enclosingMethodOrConstructor;
        } else {
            return null;
        }
    }

    private static <T> T[] filter(Object elements, Set<?> filter, T[] prototypeArray) {
        List<Object> result = new ArrayList<>();
        for (Object element : (Object[]) elements) {
            if (filter.contains(element)) {
                result.add(element);
            }
        }
        return result.toArray(prototypeArray);
    }

    private static Method findMethod(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        try {
            Method result = declaringClass.getDeclaredMethod(methodName, parameterTypes);
            result.setAccessible(true);
            return result;
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private static Field findField(Class<?> declaringClass, String fieldName) {
        try {
            Field result = declaringClass.getDeclaredField(fieldName);
            result.setAccessible(true);
            return result;
        } catch (NoSuchFieldException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}
