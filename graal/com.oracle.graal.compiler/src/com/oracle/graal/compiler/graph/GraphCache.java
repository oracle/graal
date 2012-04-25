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
package com.oracle.graal.compiler.graph;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import com.oracle.graal.compiler.*;
import com.oracle.graal.cri.*;
import com.oracle.graal.nodes.*;
import com.oracle.max.cri.ri.*;

public class GraphCache implements RiGraphCache {

    private static final PrintStream out = System.out;
    private final boolean dump;
    private boolean enabled = true;

    private final AtomicLong hitCounter = new AtomicLong();
    private final AtomicLong missCounter = new AtomicLong();
    private final AtomicLong removeHitCounter = new AtomicLong();
    private final AtomicLong removeMissCounter = new AtomicLong();
    private final AtomicLong putCounter = new AtomicLong();

    private class LRUCache extends LinkedHashMap<RiResolvedMethod, Long> {
        private static final long serialVersionUID = -3973307040793397840L;

        public LRUCache(int initialCapacity) {
            super(initialCapacity * 2, 0.75f, false);
        }
        @Override
        protected boolean removeEldestEntry(Entry<RiResolvedMethod, Long> eldest) {
            if (size() > GraalOptions.GraphCacheSize) {
                graphs.remove(eldest.getValue());
                cachedGraphIds.remove(eldest.getKey());
                return true;
            } else {
                return false;
            }
        }

    }

    private final Map<RiResolvedMethod, Long> currentGraphIds = Collections.synchronizedMap(new LRUCache(GraalOptions.GraphCacheSize));

    private final ConcurrentHashMap<Long, StructuredGraph> graphs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RiResolvedMethod> cachedGraphIds = new ConcurrentHashMap<>();


    public GraphCache(boolean dump) {
        this.dump = dump;

        if (dump) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    out.println("put: " + putCounter.get());
                    out.println("get hit: " + hitCounter.get());
                    out.println("get miss: " + missCounter.get());
                    out.println("remove hit: " + removeHitCounter.get());
                    out.println("remove miss: " + removeMissCounter.get());
                }
            });
        }
    }

    public void enable() {
        enabled = true;
    }

    @Override
    public StructuredGraph get(RiResolvedMethod method) {
        if (!enabled) {
            return null;
        }
        Long currentId = currentGraphIds.get(method);
        StructuredGraph result = null;
        if (currentId != null) {
            result = graphs.get(currentId);
        }

        if (dump) {
            if (result == null) {
                missCounter.incrementAndGet();
            } else {
                hitCounter.incrementAndGet();
            }
//            if (result == null) {
//                out.println("miss: " + missCounter.incrementAndGet() + " " + method);
//            } else {
//                out.println("hit: " + hitCounter.incrementAndGet() + " " + method);
//            }
        }
        return result;
    }

    @Override
    public void put(StructuredGraph graph) {
        if (!enabled) {
            return;
        }
        assert graph.method() != null;
        Long currentId = currentGraphIds.get(graph.method());
        if (currentId != null) {
            graphs.remove(currentId);
            cachedGraphIds.remove(currentId);
        }
        currentGraphIds.put(graph.method(), graph.graphId());
        cachedGraphIds.put(graph.graphId(), graph.method());
        graphs.put(graph.graphId(), graph);

        if (dump) {
            putCounter.incrementAndGet();
//            out.println("put: " + putCounter.incrementAndGet() + " (size: " + graphs.size() + ")");
        }
    }

    @Override
    public void clear() {
        graphs.clear();
        currentGraphIds.clear();
        cachedGraphIds.clear();
        hitCounter.set(0);
        missCounter.set(0);
        removeHitCounter.set(0);
        removeMissCounter.set(0);
        putCounter.set(0);
    }

    @Override
    public void removeGraphs(long[] deoptedGraphs) {
        for (long graphId : deoptedGraphs) {
            graphs.remove(graphId);
            RiResolvedMethod method = cachedGraphIds.get(graphId);
            if (method != null) {
                cachedGraphIds.remove(graphId);
                currentGraphIds.remove(method);
            }
            if (dump) {
                if (method != null) {
                    removeHitCounter.incrementAndGet();
                } else {
                    removeMissCounter.incrementAndGet();
                }
//                if (method != null) {
//                    out.println("remove hit: " + removeHitCounter.incrementAndGet() + " (" + graphId + " " + method + ")");
//                } else {
//                    out.println("remove miss: " + removeMissCounter.incrementAndGet() + " (" + graphId + ")");
//                }
            }
        }
    }
}
