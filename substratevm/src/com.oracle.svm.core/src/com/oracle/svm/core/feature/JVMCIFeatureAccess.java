/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.feature;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import com.oracle.svm.util.JVMCIFieldValueTransformer;
import com.oracle.svm.util.dynamicaccess.JVMCIJNIAccess;
import com.oracle.svm.util.dynamicaccess.JVMCIReflectiveAccess;
import com.oracle.svm.util.dynamicaccess.JVMCIResourceAccess;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ModifiersProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * JVMCI-based access types used by {@link JVMCIFeature}. See {@link InternalFeatureBridge} for the
 * migration plan, including why these access types temporarily live in this holder.
 */
public final class JVMCIFeatureAccess {
    private JVMCIFeatureAccess() {
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.FeatureAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface FeatureAccess {
        /**
         * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.FeatureAccess#findClassByName(String)}.
         */
        ResolvedJavaType findTypeByName(String className);

        /**
         * Provides JVMCI metadata access.
         */
        MetaAccessProvider getMetaAccess();
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.IsInConfigurationAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface IsInConfigurationAccess extends FeatureAccess {
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.OnRegistrationAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface OnRegistrationAccess extends FeatureAccess {
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.AfterRegistrationAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface AfterRegistrationAccess extends FeatureAccess {
        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.AfterRegistrationAccess#getReflectiveAccess()}.
         */
        JVMCIReflectiveAccess getJVMCIReflectiveAccess();

        /**
         * Counterpart of {@link org.graalvm.nativeimage.hosted.Feature.AfterRegistrationAccess#getResourceAccess()}.
         */
        JVMCIResourceAccess getJVMCIResourceAccess();

        /**
         * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.AfterRegistrationAccess#getJNIAccess()}.
         */
        JVMCIJNIAccess getJVMCIJNIAccess();

        /**
         * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.AfterRegistrationAccess#getForeignAccess()}.
         */
        Object getJVMCIForeignAccess();
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.DuringSetupAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface DuringSetupAccess extends FeatureAccess {
        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.DuringSetupAccess#registerObjectReplacer(Function)}.
         */
        void registerJVMCIObjectReplacer(Function<JavaConstant, JavaConstant> replacer);

        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.DuringSetupAccess#registerObjectReachabilityHandler(Consumer, Class)}.
         */
        void registerObjectReachabilityHandler(Consumer<JavaConstant> callback, ResolvedJavaType type);
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface BeforeAnalysisAccess extends FeatureAccess {
        /**
         * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess#registerAsUsed(Class)}.
         */
        void registerAsUsed(ResolvedJavaType type);

        /**
         * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess#registerAsInHeap(Class)}.
         */
        void registerAsInHeap(ResolvedJavaType type);

        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess#registerAsUnsafeAllocated(Class)}.
         */
        void registerAsUnsafeAllocated(ResolvedJavaType type);

        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess#registerAsAccessed(java.lang.reflect.Field)}.
         */
        void registerAsAccessed(ResolvedJavaField field);

        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess#registerAsUnsafeAccessed(java.lang.reflect.Field)}.
         */
        void registerAsUnsafeAccessed(ResolvedJavaField field);

        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess#registerReachabilityHandler(Consumer, Object...)}.
         */
        void registerJVMCIReachabilityHandler(Consumer<DuringAnalysisAccess> callback,
                        ModifiersProvider... elements);

        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess#registerMethodOverrideReachabilityHandler(BiConsumer, java.lang.reflect.Executable)}.
         */
        void registerMethodOverrideReachabilityHandler(BiConsumer<DuringAnalysisAccess, ResolvedJavaMethod> callback,
                        ResolvedJavaMethod baseMethod);

        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess#registerSubtypeReachabilityHandler(BiConsumer, Class)}.
         */
        void registerSubtypeReachabilityHandler(BiConsumer<DuringAnalysisAccess, ResolvedJavaType> callback,
                        ResolvedJavaType baseType);

        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess#registerClassInitializerReachabilityHandler(Consumer, Class)}.
         */
        void registerClassInitializerReachabilityHandler(Consumer<DuringAnalysisAccess> callback,
                        ResolvedJavaType type);

        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess#registerFieldValueTransformer(java.lang.reflect.Field, org.graalvm.nativeimage.hosted.FieldValueTransformer)}.
         */
        void registerFieldValueTransformer(ResolvedJavaField field, JVMCIFieldValueTransformer transformer);
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface DuringAnalysisAccess extends BeforeAnalysisAccess, QueryReachabilityAccess {
        /**
         * Counterpart of {@link org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess#requireAnalysisIteration()}.
         */
        void requireAnalysisIteration();
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.AfterAnalysisAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface AfterAnalysisAccess extends QueryReachabilityAccess {
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.QueryReachabilityAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface QueryReachabilityAccess extends FeatureAccess {
        /**
         * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.QueryReachabilityAccess#isReachable(Class)}.
         */
        boolean isReachable(ResolvedJavaType type);

        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.QueryReachabilityAccess#isReachable(java.lang.reflect.Field)}.
         */
        boolean isReachable(ResolvedJavaField field);

        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.QueryReachabilityAccess#isReachable(java.lang.reflect.Executable)}.
         */
        boolean isReachable(ResolvedJavaMethod method);

        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.QueryReachabilityAccess#reachableSubtypes(Class)}.
         */
        Set<ResolvedJavaType> reachableSubtypes(ResolvedJavaType baseType);

        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.QueryReachabilityAccess#reachableMethodOverrides(java.lang.reflect.Executable)}.
         */
        Set<ResolvedJavaMethod> reachableMethodOverrides(ResolvedJavaMethod baseMethod);
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.OnAnalysisExitAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface OnAnalysisExitAccess extends FeatureAccess {
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.BeforeUniverseBuildingAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface BeforeUniverseBuildingAccess extends FeatureAccess {
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.CompilationAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface CompilationAccess extends FeatureAccess {
        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.CompilationAccess#objectFieldOffset(java.lang.reflect.Field)}.
         */
        long objectFieldOffset(ResolvedJavaField field);

        /**
         * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.CompilationAccess#registerAsImmutable(Object)}.
         */
        void registerAsImmutable(JavaConstant object);

        /**
         * JVMCI-based counterpart of
         * {@link org.graalvm.nativeimage.hosted.Feature.CompilationAccess#registerAsImmutable(Object, Predicate)}.
         */
        void registerAsImmutable(JavaConstant root, Predicate<JavaConstant> includeObject);
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.BeforeCompilationAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface BeforeCompilationAccess extends CompilationAccess {
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.AfterCompilationAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface AfterCompilationAccess extends CompilationAccess {
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.BeforeHeapLayoutAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface BeforeHeapLayoutAccess extends CompilationAccess {
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.AfterHeapLayoutAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface AfterHeapLayoutAccess extends FeatureAccess {
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.BeforeImageWriteAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface BeforeImageWriteAccess extends FeatureAccess {
    }

    /**
     * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature.AfterImageWriteAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public interface AfterImageWriteAccess extends FeatureAccess {
        /**
         * Counterpart of {@link org.graalvm.nativeimage.hosted.Feature.AfterImageWriteAccess#getImagePath()}.
         */
        Path getImagePath();
    }
}
