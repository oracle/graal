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

import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.security.ProtectionDomain;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jdk.ProtectionDomainSupport;
import com.oracle.svm.core.util.VMError;

import sun.reflect.annotation.AnnotationType;
import sun.reflect.generics.repository.ClassRepository;

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
    private ClassRepository genericInfo;
    private SoftReference<Target_java_lang_Class_ReflectionData<?>> reflectionData;
    private AnnotationType annotationType;
    private Target_java_lang_Class_AnnotationData annotationData;
    private Constructor<?> cachedConstructor;
    private Class<?> newInstanceCallerCache;
    private Object jfrEventConfiguration;

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

    public ClassRepository getGenericInfo(DynamicHub hub) {
        if (genericInfo == null) {
            genericInfo = hub.computeGenericInfo();
        }
        return (genericInfo != ClassRepository.NONE) ? genericInfo : null;
    }

    SoftReference<Target_java_lang_Class_ReflectionData<?>> getReflectionData() {
        return reflectionData;
    }

    AnnotationType getAnnotationType() {
        return annotationType;
    }

    Target_java_lang_Class_AnnotationData getAnnotationData() {
        return annotationData;
    }

    Constructor<?> getCachedConstructor() {
        return cachedConstructor;
    }

    void setCachedConstructor(Constructor<?> constructor) {
        cachedConstructor = constructor;
    }

    Class<?> getNewInstanceCallerCache() {
        return newInstanceCallerCache;
    }

    void setNewInstanceCallerCache(Class<?> constructor) {
        newInstanceCallerCache = constructor;
    }

    public void setJfrEventConfiguration(Object configuration) {
        jfrEventConfiguration = configuration;
    }

    public Object getJfrEventConfiguration() {
        return jfrEventConfiguration;
    }
}
