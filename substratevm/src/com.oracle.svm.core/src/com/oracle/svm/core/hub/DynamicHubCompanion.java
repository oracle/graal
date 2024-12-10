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
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.jdk.ProtectionDomainSupport;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.VMError;

import jdk.internal.vm.annotation.Stable;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.generics.repository.ClassRepository;

/**
 * Storage for non-critical or mutable data of {@link DynamicHub}.
 *
 * Some of these fields are immutable and moving them to a separate read-only companion class might
 * improve sharing between isolates and processes, but could increase image size.
 */
public final class DynamicHubCompanion {
    /** Marker value for {@link #classLoader}. */
    private static final Object NO_CLASS_LOADER = new Object();

    /** Field used for module information access at run-time. */
    final Module module;

    /**
     * The hub for the superclass, or null if an interface or primitive type.
     *
     * @see Class#getSuperclass()
     */
    final DynamicHub superHub;

    /** Source file name if known; null otherwise. */
    final String sourceFileName;

    /** The {@link Modifier modifiers} of this class. */
    final int modifiers;

    /** The class that serves as the host for the nest. All nestmates have the same host. */
    final Class<?> nestHost;

    /** The simple binary name of this class, as returned by {@code Class.getSimpleBinaryName0}. */
    final String simpleBinaryName;

    /**
     * The class that declares this class, as returned by {@code Class.getDeclaringClass0} or an
     * exception that happened at image-build time.
     */
    final Object declaringClass;

    final String signature;

    /** Similar to {@code DynamicHub.flags}, but set later during the image build. */
    @UnknownPrimitiveField(availability = BuildPhaseProvider.AfterHostedUniverse.class) //
    @Stable byte additionalFlags;

    /**
     * The hub for an array of this type, or null if the array type has been determined as
     * uninstantiated by the static analysis.
     */
    @Stable DynamicHub arrayHub;

    /**
     * The interfaces that this class implements. Either null (no interfaces), a {@link DynamicHub}
     * (one interface), or a {@link DynamicHub}[] array (more than one interface).
     */
    @Stable Object interfacesEncoding;

    /**
     * Reference to a list of enum values for subclasses of {@link Enum}; null otherwise.
     */
    @Stable Object enumConstantsReference;

    /**
     * Back link to the SubstrateType used by the substrate meta access. Only used for the subset of
     * types for which a SubstrateType exists.
     */
    @UnknownObjectField(fullyQualifiedTypes = "com.oracle.svm.graal.meta.SubstrateType", canBeNull = true) //
    @Stable SharedType metaType;

    /**
     * Metadata for running class initializers at run time. Refers to a singleton marker object for
     * classes/interfaces already initialized during image generation, i.e., this field is never
     * null at run time.
     */
    @Stable ClassInitializationInfo classInitializationInfo;

    @UnknownObjectField(canBeNull = true, availability = BuildPhaseProvider.AfterCompilation.class) //
    @Stable DynamicHub.ReflectionMetadata reflectionMetadata;

    @UnknownObjectField(canBeNull = true, availability = BuildPhaseProvider.AfterCompilation.class) //
    @Stable DynamicHub.DynamicHubMetadata hubMetadata;

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
    private boolean canUnsafeAllocate;

    @Platforms(Platform.HOSTED_ONLY.class)
    DynamicHubCompanion(Class<?> hostedJavaClass, Module module, DynamicHub superHub, String sourceFileName, int modifiers,
                    ClassLoader classLoader, Class<?> nestHost, String simpleBinaryName, Object declaringClass, String signature) {

        this(module, superHub, sourceFileName, modifiers, nestHost, simpleBinaryName, declaringClass, signature);
        this.classLoader = PredefinedClassesSupport.isPredefined(hostedJavaClass) ? NO_CLASS_LOADER : classLoader;
    }

    DynamicHubCompanion(ClassLoader classLoader, Module module, DynamicHub superHub, String sourceFileName, int modifiers,
                    Class<?> nestHost, String simpleBinaryName, Object declaringClass, String signature) {
        this(module, superHub, sourceFileName, modifiers, nestHost, simpleBinaryName, declaringClass, signature);

        assert RuntimeClassLoading.isSupported();
        this.classLoader = classLoader;
    }

    private DynamicHubCompanion(Module module, DynamicHub superHub, String sourceFileName, int modifiers,
                    Class<?> nestHost, String simpleBinaryName, Object declaringClass, String signature) {
        this.module = module;
        this.superHub = superHub;
        this.sourceFileName = sourceFileName;
        this.modifiers = modifiers;
        this.nestHost = nestHost;
        this.simpleBinaryName = simpleBinaryName;
        this.declaringClass = declaringClass;
        this.signature = signature;
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

    public boolean canUnsafeAllocate() {
        return canUnsafeAllocate;
    }

    public void setUnsafeAllocate() {
        canUnsafeAllocate = true;
    }
}
