/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.phases.GraalOptions.*;

import java.io.*;
import java.lang.ref.*;
import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * This class implements the graph caching system for the HotSpot platform.
 * 
 * This implementation does not use a map to store the actual cached graphs. The problem is that
 * such maps keep the graph, and therefore the {@link ResolvedJavaMethod} referenced from the graph,
 * alive. For some applications and benchmarks this is a problem, e.g., the DaCapoScala "scalatest"
 * benchmark will quickly run out of perm gen because of this.
 * 
 * This cannot be solved with a {@code WeakHashMap<ResolvedJavaMethod, Graph>}, since the values
 * within the map will keep the keys alive. In order for this to work we would require a weak map in
 * which the "strongness" of the value references depends upon the reachability of the keys.
 * 
 * Therefore the graph cache is implemented in such a way that it stores its cache entries within
 * the {@link ResolvedJavaMethod}. It uses the {@link ResolvedJavaMethod#getCompilerStorage()} map
 * with the HotSpotGraphCache instance as key. The cached graph will be kept alive as long as the
 * {@link ResolvedJavaMethod} is alive, but does not prevent the method, and therefore the class,
 * from being unloaded.
 * 
 * The {@link #cachedGraphIds} map is used to find the graphs that should be removed because of
 * deoptimization, and to enforce the graph cache size restriction.
 */
public class HotSpotGraphCache implements GraphCache {

    private static final PrintStream out = System.out;

    private volatile long hitCounter;
    private volatile long missCounter;
    private volatile long removeHitCounter;
    private volatile long removeCounter;
    private volatile long putCounter;

    /**
     * An ordered hash map for looking up the methods corresponding to a specific graph id. It
     * enforces the maximum graph cache size by removing the oldest (in insertion-order) element if
     * the cache gets too big.
     */
    private final class LRUCache extends LinkedHashMap<Long, WeakReference<ResolvedJavaMethod>> {

        private static final long serialVersionUID = -3973307040793397840L;

        public LRUCache() {
            super(GraphCacheSize.getValue() * 2, 0.75f, false);
        }

        @Override
        protected boolean removeEldestEntry(Entry<Long, WeakReference<ResolvedJavaMethod>> eldest) {
            if (size() > GraphCacheSize.getValue()) {
                ResolvedJavaMethod method = eldest.getValue().get();
                if (method != null) {
                    StructuredGraph cachedGraph = (StructuredGraph) method.getCompilerStorage().get(HotSpotGraphCache.this);
                    if (cachedGraph != null && cachedGraph.graphId() == eldest.getKey()) {
                        method.getCompilerStorage().remove(HotSpotGraphCache.this);
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    }

    private final Map<Long, WeakReference<ResolvedJavaMethod>> cachedGraphIds = Collections.synchronizedMap(new LRUCache());

    public HotSpotGraphCache(CompilerToVM compilerToVM) {
        this.compilerToVM = compilerToVM;
        if (PrintGraphCache.getValue()) {
            Runtime.getRuntime().addShutdownHook(new Thread() {

                @Override
                public void run() {
                    out.println("put: " + putCounter);
                    out.println("get hit: " + hitCounter);
                    out.println("get miss: " + missCounter);
                    out.println("remove hit: " + removeHitCounter);
                    out.println("remove miss: " + (removeCounter - removeHitCounter));
                }
            });
        }
    }

    @Override
    public StructuredGraph get(ResolvedJavaMethod method) {
        StructuredGraph result = (StructuredGraph) method.getCompilerStorage().get(this);

        if (PrintGraphCache.getValue()) {
            if (result == null) {
                missCounter++;
            } else {
                hitCounter++;
            }
        }
        return result;
    }

    @Override
    public boolean put(StructuredGraph graph, boolean hasMatureProfilingInfo) {
        assert graph.method() != null;
        if (hasMatureProfilingInfo) {
            cachedGraphIds.put(graph.graphId(), new WeakReference<>(graph.method()));
            graph.method().getCompilerStorage().put(this, graph);

            if (PrintGraphCache.getValue()) {
                putCounter++;
            }
            return true;
        }
        return false;
    }

    public void clear() {
        synchronized (cachedGraphIds) {
            for (WeakReference<ResolvedJavaMethod> ref : cachedGraphIds.values()) {
                ResolvedJavaMethod method = ref.get();
                if (method != null) {
                    method.getCompilerStorage().remove(this);
                }
            }
            cachedGraphIds.clear();
            hitCounter = 0;
            missCounter = 0;
            removeHitCounter = 0;
            removeCounter = 0;
            putCounter = 0;
        }
    }

    public void removeGraphs(long[] deoptedGraphs) {
        for (long graphId : deoptedGraphs) {
            WeakReference<ResolvedJavaMethod> ref = cachedGraphIds.get(graphId);
            ResolvedJavaMethod method = ref == null ? null : ref.get();
            if (method != null) {
                StructuredGraph cachedGraph = (StructuredGraph) method.getCompilerStorage().get(this);
                if (cachedGraph != null && cachedGraph.graphId() == graphId) {
                    method.getCompilerStorage().remove(this);
                    if (PrintGraphCache.getValue()) {
                        removeHitCounter++;
                    }
                }
            }
            if (PrintGraphCache.getValue()) {
                removeCounter++;
            }
        }
    }

    private final CompilerToVM compilerToVM;

    public void removeStaleGraphs() {
        long[] deoptedGraphs = compilerToVM.getDeoptedLeafGraphIds();
        if (deoptedGraphs != null) {
            if (deoptedGraphs.length == 0) {
                clear();
            } else {
                removeGraphs(deoptedGraphs);
            }
        }
    }
}
