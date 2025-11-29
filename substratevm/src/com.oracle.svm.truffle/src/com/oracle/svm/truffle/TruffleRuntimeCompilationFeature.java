/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.truffle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.LocationIdentity;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.graal.SubstrateGraalUtils;
import com.oracle.svm.graal.hosted.runtimecompilation.RuntimeCompilationFeature;
import com.oracle.svm.graal.hosted.runtimecompilation.RuntimeCompiledMethodSupport;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.truffle.KnownTruffleTypes;
import jdk.graal.compiler.truffle.nodes.ObjectLocationIdentity;
import jdk.graal.compiler.truffle.phases.DeoptimizeOnExceptionPhase;
import jdk.graal.compiler.truffle.phases.PrePartialEvaluationSuite;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Truffle specific runtime compilation feature enriching runtime-compilation with truffle specific
 * parts.
 */
public final class TruffleRuntimeCompilationFeature extends RuntimeCompilationFeature {

    private TruffleRuntimeCompilationFeature() {
        // in order to pass all runtime compilation available checks
        ImageSingletons.add(RuntimeCompilationFeature.class, this);
    }

    public static TruffleRuntimeCompilationFeature singleton() {
        return ImageSingletons.lookup(TruffleRuntimeCompilationFeature.class);
    }

    @Override
    protected DeoptimizeOnExceptionPhase getDeoptOnExceptionPhase() {
        return new DeoptimizeOnExceptionPhase(deoptimizeOnExceptionPredicate);
    }

    @Override
    protected RuntimeCompiledMethodSupport.RuntimeCompilationGraphEncoder createGraphEncoder(FeatureImpl.BeforeAnalysisAccessImpl config) {
        return new TruffleRuntimeCompilationGraphEncoder(ConfigurationValues.getTarget().arch, config.getUniverse().getHeapScanner());
    }

    @Override
    protected void applyParsingHookPhases(DebugContext debug, StructuredGraph graph, Function<ResolvedJavaMethod, StructuredGraph> buildGraph, CanonicalizerPhase canonicalizer,
                    Providers providers) {
        if (!ImageSingletons.contains(KnownTruffleTypes.class)) {
            return;
        }

        KnownTruffleTypes truffleTypes = ImageSingletons.lookup(KnownTruffleTypes.class);
        new PrePartialEvaluationSuite(debug.getOptions(), truffleTypes,
                        providers, canonicalizer, buildGraph, OriginalClassProvider::getOriginalType).apply(graph, providers);
    }

    @SuppressWarnings("javadoc")
    private static final class TruffleRuntimeCompilationGraphEncoder extends RuntimeCompiledMethodSupport.RuntimeCompilationGraphEncoder {
        /**
         * Cache already converted location identity objects to avoid creating multiple new
         * instances for the same underlying location identity.
         */
        private final Map<ImageHeapConstant, LocationIdentity> locationIdentityCache;

        private TruffleRuntimeCompilationGraphEncoder(Architecture architecture, ImageHeapScanner heapScanner) {
            super(architecture, heapScanner);
            this.locationIdentityCache = new ConcurrentHashMap<>();
        }

        @Override
        protected Object replaceObjectForEncoding(Object object) {
            if (object instanceof ObjectLocationIdentity oli && oli.getObject() instanceof ImageHeapConstant heapConstant) {
                return locationIdentityCache.computeIfAbsent(heapConstant, (hc) -> ObjectLocationIdentity.create(SubstrateGraalUtils.hostedToRuntime(hc, heapScanner.getConstantReflection())));

            }
            return super.replaceObjectForEncoding(object);
        }
    }

}
