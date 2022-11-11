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
package org.graalvm.compiler.truffle.compiler.hotspot;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.hotspot.HotSpotGraalServices;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerConfiguration;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;
import org.graalvm.options.OptionValues;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class HotSpotPartialEvaluator extends PartialEvaluator {

    private final AtomicReference<EconomicMap<ResolvedJavaMethod, EncodedGraph>> graphCacheRef;

    private int jvmciReservedReference0Offset = -1;

    private boolean disableEncodedGraphCachePurges;

    public HotSpotPartialEvaluator(TruffleCompilerConfiguration config, GraphBuilderConfiguration configForRoot, KnownTruffleTypes knownTruffleTypes) {
        super(config, configForRoot, knownTruffleTypes);
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
    protected void initialize(OptionValues options) {
        super.initialize(options);
    }

    @Override
    protected void registerGraphBuilderInvocationPlugins(InvocationPlugins invocationPlugins, boolean canDelayIntrinsification) {
        super.registerGraphBuilderInvocationPlugins(invocationPlugins, canDelayIntrinsification);
        HotSpotTruffleGraphBuilderPlugins.registerCompilationFinalReferencePlugins(invocationPlugins, canDelayIntrinsification,
                        (HotSpotKnownTruffleTypes) getKnownTruffleTypes());
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
}
