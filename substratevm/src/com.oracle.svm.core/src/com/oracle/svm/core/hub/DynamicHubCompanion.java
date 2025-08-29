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
import com.oracle.svm.core.meta.SharedType;

import jdk.internal.vm.annotation.Stable;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.generics.repository.ClassRepository;

/**
 * Storage for non-critical or mutable data of {@link DynamicHub}.
 *
 * Some of these fields are immutable and moving them to a separate read-only companion class might
 * improve sharing between isolates and processes, but could increase image size.
 */
public final class DynamicHubCompanion {

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

    /**
     * The access flags of this class as they were in the class's bytecode, including the original
     * setting of ACC_SUPER.
     */
    final int classFileAccessFlags;

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
    @UnknownObjectField(availability = BuildPhaseProvider.AfterAnalysis.class, fullyQualifiedTypes = "com.oracle.svm.graal.meta.SubstrateType", canBeNull = true) //
    @Stable SharedType metaType;

    /**
     * Metadata for running class initializers at run time. Refers to a singleton marker object for
     * classes/interfaces already initialized during image generation, i.e., this field is never
     * null at run time.
     */
    @Stable ClassInitializationInfo classInitializationInfo;

    /**
     * Metadata for querying the reflection data. When using layered images this field is always
     * null and should not be queried. Instead, use {@link LayeredReflectionMetadataSingleton}.
     */
    @UnknownObjectField(canBeNull = true, availability = BuildPhaseProvider.AfterCompilation.class) //
    @Stable DynamicHub.ReflectionMetadata reflectionMetadata;

    @UnknownObjectField(canBeNull = true, availability = BuildPhaseProvider.AfterCompilation.class) //
    @Stable DynamicHub.DynamicHubMetadata hubMetadata;

    /**
     * Classloader used for loading this class. Most classes have the correct class loader set
     * already at image build time. {@link PredefinedClassesSupport Predefined classes} get their
     * classloader only at run time, before "loading" the field value is
     * {@link DynamicHub#NO_CLASS_LOADER}.
     */
    Object classLoader;
    ResolvedJavaType interpreterType;

    String packageName;
    ProtectionDomain protectionDomain;
    ClassRepository genericInfo;
    SoftReference<Target_java_lang_Class_ReflectionData<?>> reflectionData;
    AnnotationType annotationType;
    Target_java_lang_Class_AnnotationData annotationData;
    Constructor<?> cachedConstructor;
    Object jfrEventConfiguration;
    @Stable boolean canUnsafeAllocate;

    @Platforms(Platform.HOSTED_ONLY.class)
    static DynamicHubCompanion createHosted(Module module, DynamicHub superHub, String sourceFileName, int modifiers, int classFileAccessFlags,
                    Object classLoader, Class<?> nestHost, String simpleBinaryName, Object declaringClass, String signature) {

        return new DynamicHubCompanion(module, superHub, sourceFileName, modifiers, classFileAccessFlags, classLoader, nestHost, simpleBinaryName, declaringClass, signature);
    }

    static DynamicHubCompanion createAtRuntime(Module module, DynamicHub superHub, String sourceFileName, int modifiers, int classFileAccessFlags,
                    ClassLoader classLoader, Class<?> nestHost, String simpleBinaryName, Object declaringClass, String signature) {
        assert RuntimeClassLoading.isSupported();
        return new DynamicHubCompanion(module, superHub, sourceFileName, modifiers, classFileAccessFlags, classLoader, nestHost, simpleBinaryName, declaringClass, signature);
    }

    private DynamicHubCompanion(Module module, DynamicHub superHub, String sourceFileName, int modifiers, int classFileAccessFlags,
                    Object classLoader, Class<?> nestHost, String simpleBinaryName, Object declaringClass, String signature) {
        this.module = module;
        this.superHub = superHub;
        this.sourceFileName = sourceFileName;
        this.modifiers = modifiers;
        this.classFileAccessFlags = classFileAccessFlags;
        this.nestHost = nestHost;
        this.simpleBinaryName = simpleBinaryName;
        this.declaringClass = declaringClass;
        this.signature = signature;

        this.classLoader = classLoader;
    }
}
