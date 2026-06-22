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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

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
 * JVMCI-based counterpart of {@link org.graalvm.nativeimage.hosted.Feature} for internal native
 * image builder code.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public interface JVMCIFeature {

    /**
     * JVMCI-based counterpart of {@link Feature#getURL()}.
     */
    default String getURL() {
        return null;
    }

    /**
     * JVMCI-based counterpart of {@link Feature#getDescription()}.
     */
    default String getDescription() {
        return null;
    }

    /**
     * JVMCI-based counterpart of {@link Feature.FeatureAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface FeatureAccess {
        /**
         * JVMCI-based counterpart of {@link Feature.FeatureAccess#findClassByName(String)}.
         */
        ResolvedJavaType findTypeByName(String className);

        /**
         * Provides JVMCI metadata access.
         */
        MetaAccessProvider getMetaAccess();

    }

    /**
     * JVMCI-based counterpart of {@link Feature.IsInConfigurationAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface IsInConfigurationAccess extends FeatureAccess {
    }

    /**
     * JVMCI-based counterpart of {@link Feature.OnRegistrationAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface OnRegistrationAccess extends FeatureAccess {
    }

    /**
     * JVMCI-based counterpart of {@link Feature.AfterRegistrationAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterRegistrationAccess extends FeatureAccess {
        /**
         * JVMCI-based counterpart of
         * {@link Feature.AfterRegistrationAccess#getReflectiveAccess()}.
         */
        JVMCIReflectiveAccess getJVMCIReflectiveAccess();

        /**
         * Counterpart of {@link Feature.AfterRegistrationAccess#getResourceAccess()}.
         */
        JVMCIResourceAccess getJVMCIResourceAccess();

        /**
         * JVMCI-based counterpart of {@link Feature.AfterRegistrationAccess#getJNIAccess()}.
         */
        JVMCIJNIAccess getJVMCIJNIAccess();

        /**
         * JVMCI-based counterpart of {@link Feature.AfterRegistrationAccess#getForeignAccess()}.
         */
        Object getJVMCIForeignAccess();
    }

    /**
     * JVMCI-based counterpart of {@link Feature.DuringSetupAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface DuringSetupAccess extends FeatureAccess {
        /**
         * JVMCI-based counterpart of
         * {@link Feature.DuringSetupAccess#registerObjectReplacer(Function)}.
         */
        void registerJVMCIObjectReplacer(Function<JavaConstant, JavaConstant> replacer);

        /**
         * JVMCI-based counterpart of
         * {@link Feature.DuringSetupAccess#registerObjectReachabilityHandler(Consumer, Class)}.
         */
        void registerObjectReachabilityHandler(Consumer<JavaConstant> callback, ResolvedJavaType type);
    }

    /**
     * JVMCI-based counterpart of {@link Feature.BeforeAnalysisAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface BeforeAnalysisAccess extends FeatureAccess {
        /**
         * JVMCI-based counterpart of {@link Feature.BeforeAnalysisAccess#registerAsUsed(Class)}.
         */
        void registerAsUsed(ResolvedJavaType type);

        /**
         * JVMCI-based counterpart of {@link Feature.BeforeAnalysisAccess#registerAsInHeap(Class)}.
         */
        void registerAsInHeap(ResolvedJavaType type);

        /**
         * JVMCI-based counterpart of
         * {@link Feature.BeforeAnalysisAccess#registerAsUnsafeAllocated(Class)}.
         */
        void registerAsUnsafeAllocated(ResolvedJavaType type);

        /**
         * JVMCI-based counterpart of
         * {@link Feature.BeforeAnalysisAccess#registerAsAccessed(java.lang.reflect.Field)}.
         */
        void registerAsAccessed(ResolvedJavaField field);

        /**
         * JVMCI-based counterpart of
         * {@link Feature.BeforeAnalysisAccess#registerAsUnsafeAccessed(java.lang.reflect.Field)}.
         */
        void registerAsUnsafeAccessed(ResolvedJavaField field);

        /**
         * JVMCI-based counterpart of
         * {@link Feature.BeforeAnalysisAccess#registerReachabilityHandler(Consumer, Object...)}.
         */
        void registerJVMCIReachabilityHandler(Consumer<DuringAnalysisAccess> callback,
                        ModifiersProvider... elements);

        /**
         * JVMCI-based counterpart of
         * {@link Feature.BeforeAnalysisAccess#registerMethodOverrideReachabilityHandler(BiConsumer, java.lang.reflect.Executable)}.
         */
        void registerMethodOverrideReachabilityHandler(BiConsumer<DuringAnalysisAccess, ResolvedJavaMethod> callback,
                        ResolvedJavaMethod baseMethod);

        /**
         * JVMCI-based counterpart of
         * {@link Feature.BeforeAnalysisAccess#registerSubtypeReachabilityHandler(BiConsumer, Class)}.
         */
        void registerSubtypeReachabilityHandler(BiConsumer<DuringAnalysisAccess, ResolvedJavaType> callback,
                        ResolvedJavaType baseType);

        /**
         * JVMCI-based counterpart of
         * {@link Feature.BeforeAnalysisAccess#registerClassInitializerReachabilityHandler(Consumer, Class)}.
         */
        void registerClassInitializerReachabilityHandler(Consumer<DuringAnalysisAccess> callback,
                        ResolvedJavaType type);

        /**
         * JVMCI-based counterpart of
         * {@link Feature.BeforeAnalysisAccess#registerFieldValueTransformer(java.lang.reflect.Field, org.graalvm.nativeimage.hosted.FieldValueTransformer)}.
         */
        void registerFieldValueTransformer(ResolvedJavaField field, JVMCIFieldValueTransformer transformer);
    }

    /**
     * JVMCI-based counterpart of {@link Feature.DuringAnalysisAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface DuringAnalysisAccess extends BeforeAnalysisAccess, QueryReachabilityAccess {
        /**
         * Counterpart of {@link Feature.DuringAnalysisAccess#requireAnalysisIteration()}.
         */
        void requireAnalysisIteration();
    }

    /**
     * JVMCI-based counterpart of {@link Feature.AfterAnalysisAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterAnalysisAccess extends QueryReachabilityAccess {
    }

    /**
     * JVMCI-based counterpart of {@link Feature.QueryReachabilityAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface QueryReachabilityAccess extends FeatureAccess {
        /**
         * JVMCI-based counterpart of {@link Feature.QueryReachabilityAccess#isReachable(Class)}.
         */
        boolean isReachable(ResolvedJavaType type);

        /**
         * JVMCI-based counterpart of
         * {@link Feature.QueryReachabilityAccess#isReachable(java.lang.reflect.Field)}.
         */
        boolean isReachable(ResolvedJavaField field);

        /**
         * JVMCI-based counterpart of
         * {@link Feature.QueryReachabilityAccess#isReachable(java.lang.reflect.Executable)}.
         */
        boolean isReachable(ResolvedJavaMethod method);

        /**
         * JVMCI-based counterpart of
         * {@link Feature.QueryReachabilityAccess#reachableSubtypes(Class)}.
         */
        Set<ResolvedJavaType> reachableSubtypes(ResolvedJavaType baseType);

        /**
         * JVMCI-based counterpart of
         * {@link Feature.QueryReachabilityAccess#reachableMethodOverrides(java.lang.reflect.Executable)}.
         */
        Set<ResolvedJavaMethod> reachableMethodOverrides(ResolvedJavaMethod baseMethod);
    }

    /**
     * JVMCI-based counterpart of {@link Feature.OnAnalysisExitAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface OnAnalysisExitAccess extends FeatureAccess {
    }

    /**
     * JVMCI-based counterpart of {@link Feature.BeforeUniverseBuildingAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface BeforeUniverseBuildingAccess extends FeatureAccess {
    }

    /**
     * JVMCI-based counterpart of {@link Feature.CompilationAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface CompilationAccess extends FeatureAccess {
        /**
         * JVMCI-based counterpart of
         * {@link Feature.CompilationAccess#objectFieldOffset(java.lang.reflect.Field)}.
         */
        long objectFieldOffset(ResolvedJavaField field);

        /**
         * JVMCI-based counterpart of {@link Feature.CompilationAccess#registerAsImmutable(Object)}.
         */
        void registerAsImmutable(JavaConstant object);

        /**
         * JVMCI-based counterpart of
         * {@link Feature.CompilationAccess#registerAsImmutable(Object, Predicate)}.
         */
        void registerAsImmutable(JavaConstant root, Predicate<JavaConstant> includeObject);
    }

    /**
     * JVMCI-based counterpart of {@link Feature.BeforeCompilationAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface BeforeCompilationAccess extends CompilationAccess {
    }

    /**
     * JVMCI-based counterpart of {@link Feature.AfterCompilationAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterCompilationAccess extends CompilationAccess {
    }

    /**
     * JVMCI-based counterpart of {@link Feature.BeforeHeapLayoutAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface BeforeHeapLayoutAccess extends CompilationAccess {
    }

    /**
     * JVMCI-based counterpart of {@link Feature.AfterHeapLayoutAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterHeapLayoutAccess extends FeatureAccess {
    }

    /**
     * JVMCI-based counterpart of {@link Feature.BeforeImageWriteAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface BeforeImageWriteAccess extends FeatureAccess {
    }

    /**
     * JVMCI-based counterpart of {@link Feature.AfterImageWriteAccess}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    interface AfterImageWriteAccess extends FeatureAccess {
        /**
         * Counterpart of {@link Feature.AfterImageWriteAccess#getImagePath()}.
         */
        Path getImagePath();
    }

    /**
     * JVMCI-based counterpart of {@link Feature#isInConfiguration(Feature.IsInConfigurationAccess)}.
     */
    default boolean isInConfiguration(@SuppressWarnings("unused") IsInConfigurationAccess access) {
        return true;
    }

    /**
     * JVMCI-based counterpart of {@link Feature#getRequiredFeatures()}.
     */
    default List<Class<? extends JVMCIFeature>> getRequiredJVMCIFeatures() {
        return Collections.emptyList();
    }

    /**
     * JVMCI-based counterpart of {@link Feature#onRegistration(Feature.OnRegistrationAccess)}.
     */
    default void onRegistration(@SuppressWarnings("unused") OnRegistrationAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#afterRegistration(Feature.AfterRegistrationAccess)}.
     */
    default void afterRegistration(@SuppressWarnings("unused") AfterRegistrationAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#duringSetup(Feature.DuringSetupAccess)}.
     */
    default void duringSetup(@SuppressWarnings("unused") DuringSetupAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#beforeAnalysis(Feature.BeforeAnalysisAccess)}.
     */
    default void beforeAnalysis(@SuppressWarnings("unused") BeforeAnalysisAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#duringAnalysis(Feature.DuringAnalysisAccess)}.
     */
    default void duringAnalysis(@SuppressWarnings("unused") DuringAnalysisAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#afterAnalysis(Feature.AfterAnalysisAccess)}.
     */
    default void afterAnalysis(@SuppressWarnings("unused") AfterAnalysisAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#onAnalysisExit(Feature.OnAnalysisExitAccess)}.
     */
    default void onAnalysisExit(@SuppressWarnings("unused") OnAnalysisExitAccess access) {
    }

    /**
     * JVMCI-based counterpart of
     * {@link Feature#beforeUniverseBuilding(Feature.BeforeUniverseBuildingAccess)}.
     */
    default void beforeUniverseBuilding(@SuppressWarnings("unused") BeforeUniverseBuildingAccess access) {
    }

    /**
     * JVMCI-based counterpart of
     * {@link Feature#beforeCompilation(Feature.BeforeCompilationAccess)}.
     */
    default void beforeCompilation(@SuppressWarnings("unused") BeforeCompilationAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#afterCompilation(Feature.AfterCompilationAccess)}.
     */
    default void afterCompilation(@SuppressWarnings("unused") AfterCompilationAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#beforeHeapLayout(Feature.BeforeHeapLayoutAccess)}.
     */
    default void beforeHeapLayout(@SuppressWarnings("unused") BeforeHeapLayoutAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#afterHeapLayout(Feature.AfterHeapLayoutAccess)}.
     */
    default void afterHeapLayout(@SuppressWarnings("unused") AfterHeapLayoutAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#beforeImageWrite(Feature.BeforeImageWriteAccess)}.
     */
    default void beforeImageWrite(@SuppressWarnings("unused") BeforeImageWriteAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#afterImageWrite(Feature.AfterImageWriteAccess)}.
     */
    default void afterImageWrite(@SuppressWarnings("unused") AfterImageWriteAccess access) {
    }

    /**
     * Counterpart of {@link Feature#cleanup()}.
     */
    default void cleanup() {
        /*
         * Usually, overriding this method can be avoided by putting a configuration object into the
         * ImageSingletons.
         */
    }
}
