/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.options.OptionValues;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class HotSpotPartialEvaluator extends PartialEvaluator {

    private final AtomicReference<EconomicMap<ResolvedJavaMethod, EncodedGraph>> graphCacheRef;

    public boolean isEncodedGraphCacheEnabled() {
        return encodedGraphCacheCapacity != 0;
    }

    private int encodedGraphCacheCapacity;

    public HotSpotPartialEvaluator(Providers providers, GraphBuilderConfiguration configForRoot, SnippetReflectionProvider snippetReflection, Architecture architecture) {
        super(providers, configForRoot, snippetReflection, architecture, new HotSpotKnownTruffleTypes(providers.getMetaAccess()));
        this.graphCacheRef = new AtomicReference<>();
    }

    @Override
    protected void initialize(OptionValues options) {
        super.initialize(options);
        encodedGraphCacheCapacity = TruffleCompilerOptions.getPolyglotOptionValue(options, PolyglotCompilerOptions.EncodedGraphCacheCapacity);
    }

    @Override
    protected void registerTruffleInvocationPlugins(InvocationPlugins invocationPlugins, boolean canDelayIntrinsification) {
        super.registerTruffleInvocationPlugins(invocationPlugins, canDelayIntrinsification);
        HotSpotTruffleGraphBuilderPlugins.registerCompilationFinalReferencePlugins(invocationPlugins, canDelayIntrinsification, (HotSpotKnownTruffleTypes) getKnownTruffleTypes());
    }

    @SuppressWarnings("serial")
    private Map<ResolvedJavaMethod, EncodedGraph> createEncodedGraphMap() {
        if (encodedGraphCacheCapacity < 0) {
            // Unbounded cache.
            return new ConcurrentHashMap<>();
        }

        // Access-based LRU bounded cache. The overhead of the synchronized map is negligible
        // compared to the cost of re-parsing the graphs.
        return Collections.synchronizedMap(
                        new LinkedHashMap<ResolvedJavaMethod, EncodedGraph>(16, 0.75f, true) {
                            @Override
                            protected boolean removeEldestEntry(Map.Entry<ResolvedJavaMethod, EncodedGraph> eldest) {
                                // encodedGraphCacheCapacity < 0 => unbounded capacity
                                return (encodedGraphCacheCapacity >= 0) && size() > encodedGraphCacheCapacity;
                            }
                        });
    }

    @Override
    public EconomicMap<ResolvedJavaMethod, EncodedGraph> getOrCreateEncodedGraphCache() {
        if (encodedGraphCacheCapacity == 0) {
            // The encoded graph cache is disabled across different compilations. The returned map
            // can still be used and propagated within the same compilation unit.
            return super.getOrCreateEncodedGraphCache();
        }
        EconomicMap<ResolvedJavaMethod, EncodedGraph> cache;
        do {
            cache = graphCacheRef.get();
        } while (cache == null &&
                        !graphCacheRef.compareAndSet(null, cache = EconomicMap.wrapMap(createEncodedGraphMap())));
        assert cache != null;
        return cache;
    }

    public void purgeEncodedGraphCache() {
        graphCacheRef.set(null);
    }
}
