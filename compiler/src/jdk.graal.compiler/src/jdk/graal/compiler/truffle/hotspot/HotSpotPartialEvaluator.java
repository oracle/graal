/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.hotspot;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.compiler.ConstantFieldInfo;
import com.oracle.truffle.compiler.PartialEvaluationMethodInfo;

import jdk.graal.compiler.core.common.util.FieldKey;
import jdk.graal.compiler.core.common.util.MethodKey;
import jdk.graal.compiler.hotspot.HotSpotGraalServices;
import jdk.graal.compiler.hotspot.HotSpotGraphBuilderInstance;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.truffle.PartialEvaluator;
import jdk.graal.compiler.truffle.TruffleCompilerConfiguration;
import jdk.graal.compiler.truffle.TruffleElementCache;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class HotSpotPartialEvaluator extends PartialEvaluator {

    private static final int CONSTANT_INFO_CACHE_SIZE = 1024;
    private static final int METHOD_INFO_CACHE_SIZE = 1024;

    private final AtomicReference<EconomicMap<ResolvedJavaMethod, EncodedGraph>> graphCacheRef;

    private int jvmciReservedReference0Offset = -1;
    private boolean disableEncodedGraphCachePurges;

    private final PartialEvaluationMethodInfoCache methodInfoCache = new PartialEvaluationMethodInfoCache();
    private final ConstantFieldInfoCache constantInfoCache = new ConstantFieldInfoCache();

    private final ConcurrentMap<PartialEvaluationMethodInfo, PartialEvaluationMethodInfo> canonicalMethodInfos = new ConcurrentHashMap<>();

    public HotSpotPartialEvaluator(TruffleCompilerConfiguration config, GraphBuilderConfiguration configForRoot) {
        super(config, configForRoot);
        this.graphCacheRef = new AtomicReference<>();
        this.disableEncodedGraphCachePurges = false;
    }

    void setJvmciReservedReference0Offset(int jvmciReservedReference0Offset) {
        this.jvmciReservedReference0Offset = jvmciReservedReference0Offset;
    }

    public int getJvmciReservedReference0Offset() {
        return jvmciReservedReference0Offset;
    }

    @Override
    protected GraphBuilderPhase.Instance createGraphBuilderPhaseInstance(CoreProviders providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts) {
        return new HotSpotGraphBuilderInstance(providers, graphBuilderConfig, optimisticOpts, null);
    }

    @Override
    protected void initialize(OptionValues options) {
        super.initialize(options);
    }

    @Override
    public ConstantFieldInfo getConstantFieldInfo(ResolvedJavaField field) {
        return constantInfoCache.get(field);
    }

    @Override
    public PartialEvaluationMethodInfo getMethodInfo(ResolvedJavaMethod method) {
        return methodInfoCache.get(method);
    }

    @Override
    protected void registerGraphBuilderInvocationPlugins(InvocationPlugins invocationPlugins, boolean canDelayIntrinsification) {
        super.registerGraphBuilderInvocationPlugins(invocationPlugins, canDelayIntrinsification);
        HotSpotTruffleGraphBuilderPlugins.registerCompilationFinalReferencePlugins(invocationPlugins, canDelayIntrinsification,
                        (HotSpotKnownTruffleTypes) getTypes());
    }

    @Override
    public EconomicMap<ResolvedJavaMethod, EncodedGraph> getOrCreateEncodedGraphCache() {
        if (!persistentEncodedGraphCache) {
            // The encoded graph cache is disabled across different compilations. The returned map
            // can still be used and propagated within the same compilation unit.
            return super.getOrCreateEncodedGraphCache();
        }
        EconomicMap<ResolvedJavaMethod, EncodedGraph> cache;
        do {
            cache = graphCacheRef.get();
        } while (cache == null &&
                        !graphCacheRef.compareAndSet(null, cache = EconomicMap.wrapMap(new ConcurrentHashMap<>())));
        assert cache != null;
        return cache;
    }

    /**
     * Called in unit-tests via reflection.
     */
    public void purgeEncodedGraphCache() {
        // Disabling purges only for tests.
        if (!disableEncodedGraphCachePurges) {
            graphCacheRef.set(null);
        }
    }

    /**
     * Used only in unit-tests, to avoid transient failures caused by multiple compiler threads
     * racing to purge the cache. Called reflectively from EncodedGraphCacheTest.
     */
    public boolean disableEncodedGraphCachePurges(boolean value) {
        boolean oldValue = disableEncodedGraphCachePurges;
        disableEncodedGraphCachePurges = value;
        return oldValue;
    }

    /**
     * Used only in unit-tests. Called reflectively from EncodedGraphCacheTest.
     */
    public boolean persistentEncodedGraphCache(boolean value) {
        boolean oldValue = persistentEncodedGraphCache;
        persistentEncodedGraphCache = value;
        return oldValue;
    }

    @Override
    protected Supplier<AutoCloseable> getCreateCachedGraphScope() {
        if (persistentEncodedGraphCache) {
            // The interpreter graphs may be cached across compilations, keep JavaConstants
            // references to the application heap alive in the libgraal global scope.
            return HotSpotGraalServices::enterGlobalCompilationContext;
        } else {
            return super.getCreateCachedGraphScope();
        }
    }

    final class PartialEvaluationMethodInfoCache extends TruffleElementCache<ResolvedJavaMethod, PartialEvaluationMethodInfo> {

        PartialEvaluationMethodInfoCache() {
            super(METHOD_INFO_CACHE_SIZE); // cache size
        }

        @Override
        protected Object createKey(ResolvedJavaMethod method) {
            return new MethodKey(method);
        }

        @Override
        protected PartialEvaluationMethodInfo computeValue(ResolvedJavaMethod method) {
            PartialEvaluationMethodInfo methodInfo = config.runtime().getPartialEvaluationMethodInfo(method);
            /*
             * We can canonicalize the instances to reduce space required in the cache. There are
             * only a small number of possible instances of PartialEvaluationMethodInfo as it just
             * contains a bunch of flags.
             */
            return canonicalMethodInfos.computeIfAbsent(methodInfo, k -> k);
        }

    }

    final class ConstantFieldInfoCache extends TruffleElementCache<ResolvedJavaField, ConstantFieldInfo> {

        ConstantFieldInfoCache() {
            super(CONSTANT_INFO_CACHE_SIZE); // cache size
        }

        @Override
        protected Object createKey(ResolvedJavaField field) {
            return new FieldKey(field);
        }

        @Override
        protected ConstantFieldInfo computeValue(ResolvedJavaField field) {
            return config.runtime().getConstantFieldInfo(field);
        }

    }

}
