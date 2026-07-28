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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

/// Transitional bridge for moving [InternalFeature] implementations away from the public,
/// core-reflection based [Feature] API internals and toward the JVMCI-based [JVMCIFeature] API.
///
/// The current feature handler still dispatches lifecycle hooks through [Feature]. During the
/// migration, every feature access implementation object is expected to implement both the public
/// `Feature.*Access` interface and the matching [JVMCIFeatureAccess] interface. This bridge resolves
/// the default-method conflicts from inheriting both APIs and keeps a JVMCI forwarding path available
/// while [InternalFeature] still has [Feature] as a superinterface.
/// GR-76794 tracks finalizing the migration plan.
///
/// Migration path:
///
/// 1. During the incremental migration, keep [InternalFeature] implementations overriding the normal
///    [Feature] lifecycle methods. For example, keep using
///    [Feature#beforeAnalysis(Feature.BeforeAnalysisAccess)] rather than changing the feature to
///    override [JVMCIFeature#beforeAnalysis(JVMCIFeatureAccess.BeforeAnalysisAccess)]. This keeps
///    internal features consistent while the feature handler still dispatches through [Feature].
/// 2. When migrated code needs JVMCI-only operations from an access object, cast the public
///    `Feature.*Access` parameter to the matching implementation class in
///    `com.oracle.svm.hosted.FeatureImpl`, such as `FeatureImpl.BeforeAnalysisAccessImpl`. Those
///    implementation classes are the temporary bridge point because they implement both access
///    interface families.
/// 3. Inside the migrated hook body, use JVMCI metadata types, such as
///    [jdk.vm.ci.meta.ResolvedJavaType], [jdk.vm.ci.meta.ResolvedJavaMethod],
///    [jdk.vm.ci.meta.ResolvedJavaField], and [jdk.vm.ci.meta.MetaAccessProvider], instead of core
///    reflection types such as [Class], [java.lang.reflect.Field], [java.lang.reflect.Method],
///    [java.lang.reflect.Constructor], or [java.lang.reflect.Executable].
/// 4. Keep feature dependency declarations on [Feature#getRequiredFeatures()] until the feature
///    registration code is migrated to consume [JVMCIFeature#getRequiredJVMCIFeatures()].
/// 5. Maintain the invariant that the access implementation classes in
///    `com.oracle.svm.hosted.FeatureImpl` implement both access interface families. If a new
///    lifecycle access type is added, add it to [JVMCIFeatureAccess], implement it in the matching
///    access implementation class, and add the forwarding default here if the lifecycle method also
///    exists on [JVMCIFeature].
/// 6. After all [InternalFeature] implementations use JVMCI metadata types internally, change
///    [InternalFeature] to extend only [JVMCIFeature], remove this bridge, and remove the remaining
///    internal dependency on public [Feature] lifecycle dispatch. The lifecycle method signatures can
///    then be moved from `Feature.*Access` to [JVMCIFeatureAccess]. Most casts to
///    `FeatureImpl.*AccessImpl` should become unnecessary cleanup because the JVMCI access interface
///    exposes the migrated operations directly.
/// 7. At the same time, move the [JVMCIFeatureAccess] nested access interfaces back into
///    [JVMCIFeature]. They live in a separate holder while [InternalFeature] still also inherits
///    [Feature], because otherwise simple nested names such as `BeforeAnalysisAccess` would be
///    ambiguous or would disappear when the public [Feature] superinterface is removed.
@Platforms(Platform.HOSTED_ONLY.class)
public interface InternalFeatureBridge extends Feature, JVMCIFeature {

    @Override
    default String getURL() {
        return JVMCIFeature.super.getURL();
    }

    @Override
    default String getDescription() {
        return JVMCIFeature.super.getDescription();
    }

    @Override
    default boolean isInConfiguration(Feature.IsInConfigurationAccess access) {
        return isInConfiguration((JVMCIFeatureAccess.IsInConfigurationAccess) access);
    }

    @Override
    default void onRegistration(Feature.OnRegistrationAccess access) {
        onRegistration((JVMCIFeatureAccess.OnRegistrationAccess) access);
    }

    @Override
    default void afterRegistration(Feature.AfterRegistrationAccess access) {
        afterRegistration((JVMCIFeatureAccess.AfterRegistrationAccess) access);
    }

    @Override
    default void duringSetup(Feature.DuringSetupAccess access) {
        duringSetup((JVMCIFeatureAccess.DuringSetupAccess) access);
    }

    @Override
    default void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        beforeAnalysis((JVMCIFeatureAccess.BeforeAnalysisAccess) access);
    }

    @Override
    default void duringAnalysis(Feature.DuringAnalysisAccess access) {
        duringAnalysis((JVMCIFeatureAccess.DuringAnalysisAccess) access);
    }

    @Override
    default void afterAnalysis(Feature.AfterAnalysisAccess access) {
        afterAnalysis((JVMCIFeatureAccess.AfterAnalysisAccess) access);
    }

    @Override
    default void onAnalysisExit(Feature.OnAnalysisExitAccess access) {
        onAnalysisExit((JVMCIFeatureAccess.OnAnalysisExitAccess) access);
    }

    @Override
    default void beforeUniverseBuilding(Feature.BeforeUniverseBuildingAccess access) {
        beforeUniverseBuilding((JVMCIFeatureAccess.BeforeUniverseBuildingAccess) access);
    }

    @Override
    default void beforeCompilation(Feature.BeforeCompilationAccess access) {
        beforeCompilation((JVMCIFeatureAccess.BeforeCompilationAccess) access);
    }

    @Override
    default void afterCompilation(Feature.AfterCompilationAccess access) {
        afterCompilation((JVMCIFeatureAccess.AfterCompilationAccess) access);
    }

    @Override
    default void beforeHeapLayout(Feature.BeforeHeapLayoutAccess access) {
        beforeHeapLayout((JVMCIFeatureAccess.BeforeHeapLayoutAccess) access);
    }

    @Override
    default void afterHeapLayout(Feature.AfterHeapLayoutAccess access) {
        afterHeapLayout((JVMCIFeatureAccess.AfterHeapLayoutAccess) access);
    }

    @Override
    default void beforeImageWrite(Feature.BeforeImageWriteAccess access) {
        beforeImageWrite((JVMCIFeatureAccess.BeforeImageWriteAccess) access);
    }

    @Override
    default void afterImageWrite(Feature.AfterImageWriteAccess access) {
        afterImageWrite((JVMCIFeatureAccess.AfterImageWriteAccess) access);
    }

    @Override
    default void cleanup() {
        JVMCIFeature.super.cleanup();
    }
}
