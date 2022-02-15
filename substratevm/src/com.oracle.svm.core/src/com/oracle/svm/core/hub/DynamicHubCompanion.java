/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.hub.DynamicHub.ReflectionData;
import com.oracle.svm.core.jdk.ProtectionDomainSupport;
import com.oracle.svm.core.reflect.MethodMetadataDecoder;
import com.oracle.svm.core.reflect.MethodMetadataDecoder.MethodDescriptor;
import com.oracle.svm.core.util.VMError;

/**
 * The mutable parts of a {@link DynamicHub} instance.
 */
public final class DynamicHubCompanion {
    /** Marker value for {@link #classLoader}. */
    private static final Object NO_CLASS_LOADER = new Object();

    private String packageName;
    /**
     * Classloader used for loading this class. Most classes have the correct class loader set
     * already at image build time. {@link PredefinedClassesSupport Predefined classes} get their
     * classloader only at run time, before "loading" the field value is {@link #NO_CLASS_LOADER}.
     */
    private Object classLoader;
    private ProtectionDomain protectionDomain;
    private ReflectionData completeReflectionData;

    @Platforms(Platform.HOSTED_ONLY.class)
    DynamicHubCompanion(Class<?> hostedJavaClass, ClassLoader classLoader) {
        this.classLoader = PredefinedClassesSupport.isPredefined(hostedJavaClass) ? NO_CLASS_LOADER : classLoader;
    }

    String getPackageName(DynamicHub hub) {
        if (packageName == null) {
            packageName = hub.computePackageName();
        }
        return packageName;
    }

    boolean hasClassLoader() {
        return classLoader != NO_CLASS_LOADER;
    }

    ClassLoader getClassLoader() {
        Object loader = classLoader;
        VMError.guarantee(loader != NO_CLASS_LOADER);
        return (ClassLoader) loader;
    }

    void setClassLoader(ClassLoader loader) {
        VMError.guarantee(classLoader == NO_CLASS_LOADER && loader != NO_CLASS_LOADER);
        classLoader = loader;
    }

    ProtectionDomain getProtectionDomain() {
        if (protectionDomain == null) {
            protectionDomain = ProtectionDomainSupport.allPermDomain();
        }
        return protectionDomain;
    }

    void setProtectionDomain(ProtectionDomain domain) {
        VMError.guarantee(protectionDomain == null && domain != null);
        protectionDomain = domain;
    }

    ReflectionData getCompleteReflectionData(DynamicHub hub) {
        if (completeReflectionData == null) {
            List<Method> newDeclaredMethods = new ArrayList<>(Arrays.asList(hub.rd.declaredMethods));
            List<Method> newPublicMethods = new ArrayList<>(Arrays.asList(hub.rd.publicMethods));
            List<Constructor<?>> newDeclaredConstructors = new ArrayList<>(Arrays.asList(hub.rd.declaredConstructors));
            List<Constructor<?>> newPublicConstructors = new ArrayList<>(Arrays.asList(hub.rd.publicConstructors));
            List<Method> newDeclaredPublicMethods = new ArrayList<>(Arrays.asList(hub.rd.declaredPublicMethods));

            Pair<Executable[], MethodDescriptor[]> queriedAndHidingMethods = ImageSingletons.lookup(MethodMetadataDecoder.class).getQueriedAndHidingMethods(hub);
            Executable[] queriedMethods = queriedAndHidingMethods.getLeft();
            MethodDescriptor[] hidingMethods = queriedAndHidingMethods.getRight();
            newMethods: for (Executable method : queriedMethods) {
                if (method instanceof Constructor<?>) {
                    Constructor<?> c = (Constructor<?>) method;
                    for (Constructor<?> c2 : hub.rd.declaredConstructors) {
                        if (Arrays.equals(c.getParameterTypes(), c2.getParameterTypes())) {
                            continue newMethods;
                        }
                    }
                    newDeclaredConstructors.add(c);
                    if (Modifier.isPublic(c.getModifiers())) {
                        newPublicConstructors.add(c);
                    }
                } else {
                    Method m = (Method) method;
                    for (Method m2 : hub.rd.declaredMethods) {
                        if (m.getName().equals(m2.getName()) && Arrays.equals(m.getParameterTypes(), m2.getParameterTypes())) {
                            continue newMethods;
                        }
                    }
                    newDeclaredMethods.add(m);
                    if (Modifier.isPublic(m.getModifiers())) {
                        newPublicMethods.add(m);
                        newDeclaredPublicMethods.add(m);
                    }
                }
            }

            /* Recursively add public superclass methods to the public methods list */
            DynamicHub superHub = hub.getSuperHub();
            if (superHub != null) {
                addInheritedPublicMethods(newPublicMethods, superHub.privateGetPublicMethods(), hidingMethods, true);
            }
            for (DynamicHub interfaceHub : hub.getInterfaces()) {
                addInheritedPublicMethods(newPublicMethods, interfaceHub.privateGetPublicMethods(), hidingMethods, hub.isInterface());
            }

            completeReflectionData = new ReflectionData(hub.rd.declaredFields, hub.rd.publicFields, hub.rd.publicUnhiddenFields, newDeclaredMethods.toArray(new Method[0]),
                            newPublicMethods.toArray(new Method[0]),
                            newDeclaredConstructors.toArray(new Constructor<?>[0]), newPublicConstructors.toArray(new Constructor<?>[0]), hub.rd.nullaryConstructor, hub.rd.declaredPublicFields,
                            newDeclaredPublicMethods.toArray(new Method[0]), hub.rd.declaredClasses, hub.rd.permittedSubclasses, hub.rd.publicClasses, hub.rd.enclosingMethodOrConstructor,
                            hub.rd.recordComponents);
        }
        return completeReflectionData;
    }

    private static void addInheritedPublicMethods(List<Method> newPublicMethods, Method[] parentMethods, MethodDescriptor[] hidingMethods, boolean includeStaticMethods) {
        parentMethods: for (Method m : parentMethods) {
            if (!includeStaticMethods && Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            for (Method m2 : newPublicMethods) {
                if (m.getName().equals(m2.getName()) && Arrays.equals(m.getParameterTypes(), m2.getParameterTypes())) {
                    if (m.getDeclaringClass() != m2.getDeclaringClass() && m2.getDeclaringClass().isAssignableFrom(m.getDeclaringClass())) {
                        /* Need to store the more specific method */
                        newPublicMethods.remove(m2);
                        newPublicMethods.add(m);
                    }
                    continue parentMethods;
                }
            }
            for (MethodDescriptor hidingMethod : hidingMethods) {
                if (m.getName().equals(hidingMethod.getName()) && Arrays.equals(m.getParameterTypes(), hidingMethod.getParameterTypes())) {
                    continue parentMethods;
                }
            }
            newPublicMethods.add(m);
        }
    }
}
