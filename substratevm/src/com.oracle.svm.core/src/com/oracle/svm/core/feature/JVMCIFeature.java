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

import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

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
     * JVMCI-based counterpart of {@link Feature#isInConfiguration(Feature.IsInConfigurationAccess)}.
     */
    default boolean isInConfiguration(@SuppressWarnings("unused") JVMCIFeatureAccess.IsInConfigurationAccess access) {
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
    default void onRegistration(@SuppressWarnings("unused") JVMCIFeatureAccess.OnRegistrationAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#afterRegistration(Feature.AfterRegistrationAccess)}.
     */
    default void afterRegistration(@SuppressWarnings("unused") JVMCIFeatureAccess.AfterRegistrationAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#duringSetup(Feature.DuringSetupAccess)}.
     */
    default void duringSetup(@SuppressWarnings("unused") JVMCIFeatureAccess.DuringSetupAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#beforeAnalysis(Feature.BeforeAnalysisAccess)}.
     */
    default void beforeAnalysis(@SuppressWarnings("unused") JVMCIFeatureAccess.BeforeAnalysisAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#duringAnalysis(Feature.DuringAnalysisAccess)}.
     */
    default void duringAnalysis(@SuppressWarnings("unused") JVMCIFeatureAccess.DuringAnalysisAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#afterAnalysis(Feature.AfterAnalysisAccess)}.
     */
    default void afterAnalysis(@SuppressWarnings("unused") JVMCIFeatureAccess.AfterAnalysisAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#onAnalysisExit(Feature.OnAnalysisExitAccess)}.
     */
    default void onAnalysisExit(@SuppressWarnings("unused") JVMCIFeatureAccess.OnAnalysisExitAccess access) {
    }

    /**
     * JVMCI-based counterpart of
     * {@link Feature#beforeUniverseBuilding(Feature.BeforeUniverseBuildingAccess)}.
     */
    default void beforeUniverseBuilding(@SuppressWarnings("unused") JVMCIFeatureAccess.BeforeUniverseBuildingAccess access) {
    }

    /**
     * JVMCI-based counterpart of
     * {@link Feature#beforeCompilation(Feature.BeforeCompilationAccess)}.
     */
    default void beforeCompilation(@SuppressWarnings("unused") JVMCIFeatureAccess.BeforeCompilationAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#afterCompilation(Feature.AfterCompilationAccess)}.
     */
    default void afterCompilation(@SuppressWarnings("unused") JVMCIFeatureAccess.AfterCompilationAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#beforeHeapLayout(Feature.BeforeHeapLayoutAccess)}.
     */
    default void beforeHeapLayout(@SuppressWarnings("unused") JVMCIFeatureAccess.BeforeHeapLayoutAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#afterHeapLayout(Feature.AfterHeapLayoutAccess)}.
     */
    default void afterHeapLayout(@SuppressWarnings("unused") JVMCIFeatureAccess.AfterHeapLayoutAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#beforeImageWrite(Feature.BeforeImageWriteAccess)}.
     */
    default void beforeImageWrite(@SuppressWarnings("unused") JVMCIFeatureAccess.BeforeImageWriteAccess access) {
    }

    /**
     * JVMCI-based counterpart of {@link Feature#afterImageWrite(Feature.AfterImageWriteAccess)}.
     */
    default void afterImageWrite(@SuppressWarnings("unused") JVMCIFeatureAccess.AfterImageWriteAccess access) {
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
