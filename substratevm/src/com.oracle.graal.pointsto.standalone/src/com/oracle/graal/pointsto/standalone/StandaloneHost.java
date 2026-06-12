/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone;

import java.util.Comparator;
import java.util.Optional;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.standalone.plugins.StandaloneGraphBuilderPhase;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Standalone host-VM integration for points-to analysis.
 *
 * The host delegates standalone build-time class-initialization policy to a dedicated support
 * object and exposes the resulting decisions through the {@link HostVM} contract used by shared
 * analysis code.
 */
public class StandaloneHost extends HostVM {
    /**
     * Standalone-owned outcome of the build-time-initialization decision for one reachable type.
     */
    public enum ClassInitializationOutcome {
        PENDING,
        INITIALIZED,
        RUNTIME_ONLY,
        FAILED;

        /**
         * Returns whether shadow-heap snapshotting may use build-time static values for the type.
         */
        public boolean allowsStaticFieldSnapshotting() {
            return this == INITIALIZED;
        }
    }

    private final String imageName;
    private final boolean closedTypeWorld;
    private final StandaloneClassInitializationSupport classInitializationSupport;

    public StandaloneHost(OptionValues options, String imageName, StandaloneClassInitializationStrategy classInitializationStrategy, boolean closedTypeWorld) {
        super(options, /*- ClassLoader not supported. */ null);
        this.imageName = imageName;
        this.closedTypeWorld = closedTypeWorld;
        this.classInitializationSupport = new StandaloneClassInitializationSupport(classInitializationStrategy, StandaloneOptions.StandalonePrintClassInitializationFailures.getValue(options));
    }

    /**
     * Initializes {@code type} only when the configured strategy currently allows eager
     * initialization for that type and returns the standalone-owned outcome.
     */
    public ClassInitializationOutcome maybeInitializeAtBuildTime(AnalysisType type) {
        return classInitializationSupport.maybeInitializeAtBuildTime(type);
    }

    /**
     * Returns the current standalone-owned build-time-initialization outcome for {@code type}
     * without starting a new initialization attempt.
     */
    public ClassInitializationOutcome getClassInitializationOutcome(AnalysisType type) {
        return classInitializationSupport.getOutcome(type);
    }

    /**
     * Registers {@code field} for a later retry when the declaring class is still waiting for
     * standalone build-time initialization. The returned outcome is re-read after registration so a
     * caller that races with initialization completion can immediately follow the completed state
     * instead of waiting for a retry that may already have run.
     */
    public ClassInitializationOutcome registerPendingStaticFieldRead(AnalysisField field) {
        return classInitializationSupport.registerPendingStaticFieldRead(field);
    }

    /**
     * Returns whether end-of-analysis reporting of class-initialization failures that fall back to
     * runtime handling is enabled.
     */
    public boolean shouldPrintClassInitializationFailures() {
        return classInitializationSupport.shouldPrintFailures();
    }

    /**
     * Returns the total number of build-time class-initialization attempts that failed and fell
     * back to runtime handling during the current analysis.
     */
    public int getClassInitializationFailureCount() {
        return classInitializationSupport.getFailureCount();
    }

    /**
     * Returns the number of distinct classes whose first fallback-triggering initialization
     * failure was recorded during the current analysis.
     */
    public int getClassInitializationFailureTypeCount() {
        return classInitializationSupport.getFailureTypeCount();
    }

    /**
     * Formats the first recorded class-initialization failure for each class that fell back to
     * runtime handling.
     */
    public String formatClassInitializationFailures() {
        return classInitializationSupport.formatFailures();
    }

    @Override
    public boolean isInitialized(AnalysisType type) {
        return classInitializationSupport.isInitialized(type);
    }

    @Override
    public void onTypeReachable(BigBang bb, AnalysisType type) {
        AnalysisError.guarantee(type.isReachable(), "Registering and initializing a type that was not yet marked as reachable: %s", type.toJavaName());
        maybeInitializeAtBuildTime(type);
    }

    @Override
    public boolean shouldStoreAnalyzedGraph(@SuppressWarnings("unused") BigBang bb, @SuppressWarnings("unused") AnalysisMethod method) {
        return false;
    }

    @Override
    public GraphBuilderPhase.Instance createGraphBuilderPhase(HostedProviders builderProviders, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                    IntrinsicContext initialIntrinsicContext) {
        return new StandaloneGraphBuilderPhase.Instance(builderProviders, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
    }

    @Override
    public String getImageName() {
        return imageName;
    }

    @Override
    public Comparator<? super ResolvedJavaType> getTypeComparator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<AnalysisMethod> handleForeignCall(ForeignCallDescriptor foreignCallDescriptor, ForeignCallsProvider foreignCallsProvider) {
        throw AnalysisError.shouldNotReachHere("StandaloneHost.handleForeignCall");
    }

    @Override
    public boolean isClosedTypeWorld() {
        return closedTypeWorld;
    }

    /**
     * Resolves the guest-side class loader name so standalone analysis reporting reflects the
     * analyzed application rather than the host VM loader graph.
     */
    @Override
    public String loaderName(AnalysisType type) {
        GuestAccess guestAccess = GuestAccess.get();
        JavaConstant classLoaderConstant = getClassLoader(guestAccess, type);
        if (classLoaderConstant.isNull()) {
            return "null";
        }
        String classLoaderName = getClassLoaderName(guestAccess, classLoaderConstant);
        if (classLoaderName != null) {
            return classLoaderName;
        }
        ResolvedJavaType classLoaderType = guestAccess.getProviders().getMetaAccess().lookupJavaType(classLoaderConstant);
        return classLoaderType.toJavaName();
    }

    /**
     * Reads the guest-side {@link ClassLoader} for the original application class represented by
     * {@code type}.
     */
    private static JavaConstant getClassLoader(GuestAccess guestAccess, AnalysisType type) {
        var original = OriginalClassProvider.getOriginalType(type);
        JavaConstant asConstant = guestAccess.getProviders().getConstantReflection().asJavaClass(original);
        return guestAccess.invoke(guestAccess.elements.java_lang_Class_getClassLoader, asConstant);
    }

    /**
     * Returns the guest-side class loader name when the loader exposes one.
     */
    private static String getClassLoaderName(GuestAccess guestAccess, JavaConstant classLoaderConstant) {
        JavaConstant classLoaderName = guestAccess.invoke(guestAccess.elements.java_lang_ClassLoader_getName, classLoaderConstant);
        if (classLoaderName.isNull()) {
            return null;
        }
        return guestAccess.getSnippetReflection().asObject(String.class, classLoaderName);
    }
}
