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
import java.util.function.Predicate;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.LocationIdentity;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.svm.graal.SubstrateGraalUtils;
import com.oracle.svm.graal.hosted.runtimecompilation.RuntimeCompiledMethodSupport;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.truffle.KnownTruffleTypes;
import jdk.graal.compiler.truffle.nodes.ObjectLocationIdentity;
import jdk.graal.compiler.truffle.phases.DeoptimizeOnExceptionPhase;
import jdk.graal.compiler.truffle.phases.TruffleEarlyEscapeAnalysisPhase;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Truffle specific runtime compilation feature enriching runtime-compilation with truffle specific
 * parts.
 */
public final class TruffleRuntimeCompiledMethodSupport extends RuntimeCompiledMethodSupport {
    @Override
    public DeoptimizeOnExceptionPhase getDeoptOnExceptionPhase(Predicate<ResolvedJavaMethod> deoptimizeOnExceptionPredicate) {
        return new DeoptimizeOnExceptionPhase(deoptimizeOnExceptionPredicate);
    }

    @Override
    public RuntimeCompilationGraphEncoder createGraphEncoder(Architecture architecture, ImageHeapScanner heapScanner) {
        return new TruffleRuntimeCompilationGraphEncoder(architecture, heapScanner);
    }

    @Override
    public RuntimeCompilationGraphDecoder createDecoder(Architecture architecture, StructuredGraph graph, ImageHeapScanner heapScanner) {
        return new TruffleRuntimeCompilationGraphDecoder(architecture, graph, heapScanner);
    }

    @Override
    public void applyParsingHookPhases(DebugContext debug, StructuredGraph graph, Function<ResolvedJavaMethod, StructuredGraph> graphBuilder,
                    Function<ResolvedJavaMethod, ResolvedJavaMethod> targetResolver, CanonicalizerPhase canonicalizer, Providers providers) {
        /*
         * Keep this in sync with CachingPEGraphDecoder#createGraph.
         */
        KnownTruffleTypes truffleTypes = ImageSingletons.lookup(KnownTruffleTypes.class);
        /*
         * The annotation APIs in SubstrateVM unfortunately operate on the unwrapped Java type, so
         * we need to unwrap it for the early inline type to match.
         */
        ResolvedJavaType earlyInline = OriginalClassProvider.getOriginalType(truffleTypes.CompilerDirectives_EarlyInline);
        new SubstrateEarlyInliningPhase(debug.getOptions(), canonicalizer, providers, graphBuilder, targetResolver, earlyInline).apply(graph, providers);

        /*
         * TruffleEarlyEscapeAnalysisPhase must be run later in
         * TruffleRuntimeCompiledMethodSupport#optimizeBeforeEncoding. Running it here would lead to
         * InlinedInvokeArgumentNodes preventing objects which "escape" into inlined methods from
         * being virtualized.
         */
    }

    @Override
    protected void optimizeBeforeEncoding(StructuredGraph graph, Providers providers, CanonicalizerPhase canonicalizer) {
        KnownTruffleTypes truffleTypes = ImageSingletons.lookup(KnownTruffleTypes.class);
        ResolvedJavaType earlyEscapeAnalysisType = OriginalClassProvider.getOriginalType(truffleTypes.CompilerDirectives_EarlyEscapeAnalysis);
        /*
         * TruffleEarlyEscapeAnalysisPhase must run after analysis where InlinedInvokeArgumentNodes
         * are already removed as they would force materialization.
         */
        new TruffleEarlyEscapeAnalysisPhase(canonicalizer, graph.getDebug().getOptions(), earlyEscapeAnalysisType).apply(graph, providers);

        super.optimizeBeforeEncoding(graph, providers, canonicalizer);
    }

    @SuppressWarnings("javadoc")
    private static final class TruffleRuntimeCompilationGraphEncoder extends RuntimeCompilationGraphEncoder {
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

    private static final class TruffleRuntimeCompilationGraphDecoder extends RuntimeCompilationGraphDecoder {
        /**
         * Cache already converted location identity objects to avoid creating multiple new
         * instances for the same underlying location identity.
         */
        private final Map<JavaConstant, LocationIdentity> locationIdentityCache;

        private TruffleRuntimeCompilationGraphDecoder(Architecture architecture, StructuredGraph graph, ImageHeapScanner heapScanner) {
            super(architecture, graph, heapScanner);
            this.locationIdentityCache = new ConcurrentHashMap<>();
        }

        @Override
        protected Object readObject(MethodScope methodScope) {
            Object object = super.readObject(methodScope);
            if (object instanceof ObjectLocationIdentity oli) {
                return locationIdentityCache.computeIfAbsent(oli.getObject(), (constant) -> ObjectLocationIdentity.create(SubstrateGraalUtils.runtimeToHosted(constant, heapScanner)));
            }
            return object;
        }
    }
}
