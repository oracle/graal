/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.RuntimeReflection.RuntimeReflectionSupport;
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

        for (Class<?> clazz : allClasses) {
            DynamicHub hub = access.getHostVM().dynamicHub(access.getMetaAccess().lookupJavaType(clazz));

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
                                filter(declaredPublicMethodsField.get(originalReflectionData), reflectionMethods, emptyMethods)));
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }
    }

    protected void afterAnalysis() {
        sealed = true;
        if (modified) {
            throw UserError.abort("Registration of of classes, methods, and fields for reflective access during analysis must set DuringAnalysisAccess.requireAnalysisIteration().");
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
