/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;

/**
 * Contains the set of reference-counted cached graphs, where graphs are distinct according to some
 * key. The reference objects provided by this cache can return the graph in a read-only mode, or
 * ensure that the graph is unique for modification purposes (in which case they might duplicate
 * it).
 *
 * If the graph reference is created with a null key, then the graph will not be cached nor shared
 * with a reference count.
 */
public class GraphCache<K, G extends Graph> {
    private EconomicMap<K, ReferenceCounted<G>> rawCache = EconomicMap.create();
    private EconomicMap<K, WeakReference<G>> weakCache = EconomicMap.create();
    private int weakCacheHits = 0;
    private int weakCacheProbes = 0;
    private int cacheProbes = 0;

    public Ref<K, G> getRef(K key) {
        ReferenceCounted<G> counted = rawCache.get(key);
        cacheProbes++;
        if (counted != null) {
            counted.count++;
            return new Ref<>(this, key, counted);
        }
        WeakReference<G> weak = weakCache.get(key);
        if (weak == null) {
            return null;
        }
        G graph = weak.get();
        weakCacheProbes++;
        if (graph == null) {
            weakCache.removeKey(key);
            return null;
        }
        weakCacheHits++;
        Ref<K, G> ref = createRef(key, graph);
        weakCache.removeKey(key);
        return ref;
    }

    public Ref<K, G> createRef(K key, G graph) {
        if (key != null && rawCache.get(key) != null) {
            throw GraalError.shouldNotReachHere("Cannot cache a graph under an existing key: " + key); // ExcludeFromJacocoGeneratedReport
        }
        ReferenceCounted<G> counted = new ReferenceCounted<>(graph);
        if (key != null) {
            rawCache.put(key, counted);
            graph.temporaryFreeze();
        }
        return new Ref<>(this, key, counted);
    }

    public Ref<K, G> createNonCounted(G graph) {
        return new Ref<>(this, null, new ReferenceCounted<>(graph));
    }

    public int getWeakCacheHits() {
        return weakCacheHits;
    }

    public int getWeakCacheProbes() {
        return weakCacheProbes;
    }

    public int getCacheProbes() {
        return cacheProbes;
    }

    /**
     * Represents a counted reference to a graph.
     */
    public static final class Ref<K, G extends Graph> {
        private final GraphCache<K, G> cache;
        private K key;
        private ReferenceCounted<G> counted;

        private Ref(GraphCache<K, G> cache, K key, ReferenceCounted<G> counted) {
            this.cache = cache;
            this.key = key;
            this.counted = counted;
        }

        public G readonly() {
            return counted.graph;
        }

        public void release() {
            if (counted.count == 1 && key != null) {
                cache.rawCache.removeKey(key);
                counted.graph.unfreeze();
                assert cache.weakCache.get(key) == null;
                cache.weakCache.put(key, new WeakReference<>(counted.graph));
            }
            counted.count--;
            counted = null;
        }

        /**
         * Ensures that this Ref is the only reference pointing to the underlying graph.
         *
         * After calling this method, it is safe to modify the underlying graph. The uniqueness is
         * achieved by either copying the graph if there are multiple references pointing to it, or
         * just reusing the underlying graph if this is the single reference pointing to it.
         */
        @SuppressWarnings("unchecked")
        public G uniqueRef(K newKey, Consumer<UnmodifiableEconomicMap<Node, Node>> duplicationMapCallback) {
            assert NumUtil.assertPositiveInt(counted.count);
            if (newKey != null) {
                throw GraalError.unimplemented("newKey expected"); // ExcludeFromJacocoGeneratedReport
            }
            if (counted.count == 1) {
                if (key != null) {
                    cache.rawCache.removeKey(key);
                    counted.graph.unfreeze();
                }
                key = newKey;
                return counted.graph;
            }
            counted.count--;
            counted = new ReferenceCounted<>((G) counted.graph.copy(duplicationMapCallback, counted.graph.getDebug()));
            key = newKey;
            return counted.graph;
        }

        public int referenceCount() {
            return counted.count;
        }
    }

    private static class ReferenceCounted<G extends Graph> {
        private G graph;
        private int count;

        ReferenceCounted(G graph) {
            this.graph = graph;
            this.count = 1;
        }
    }
}
