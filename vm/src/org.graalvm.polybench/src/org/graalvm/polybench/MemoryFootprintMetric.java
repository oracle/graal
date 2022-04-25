/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.management.MBeanServer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.visualvm.lib.jfluid.heap.Heap;
import org.graalvm.visualvm.lib.jfluid.heap.HeapFactory;
import org.graalvm.visualvm.lib.jfluid.heap.Instance;

public final class MemoryFootprintMetric extends Metric {

    long accumulatedFootprint;
    long previousHeap;
    final List<Long> values = new ArrayList<>();

    private final Method dumpHeap;
    private final Object dumpHeapBean; // This is the name of the HotSpot Diagnostic MBean
    private final String hotspotBeanName = "com.sun.management:type=HotSpotDiagnostic";

    MemoryFootprintMetric() {
        try {
            Class<?> clazz = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            dumpHeapBean = ManagementFactory.newPlatformMXBeanProxy(server, hotspotBeanName, clazz);
            dumpHeap = clazz.getMethod("dumpHeap", String.class, boolean.class);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }

    @Override
    public String name() {
        return "memory-footprint";
    }

    @Override
    public String unit() {
        return "B";
    }

    @Override
    public Map<String, String> getEngineOptions(Config config) {
        HashMap<String, String> map = new HashMap<>();
        /*
         * We use background compilation as this increases determinism of heap dumps. Background
         * compilation might allocate and lead to noise even with libgraal.
         */
        map.put("engine.BackgroundCompilation", "false");
        return map;
    }

    @Override
    public void beforeLoad(Config config) {
        /*
         * This is supposed to trigger an eager compilation before we start to measure. We are not
         * interested in overhead incurred by the compiler here and this is intended to reduce the
         * noise. In particular when running with jargraal (e.g. for debugging purposes).
         */
        Value v = Context.getCurrent().asValue(ProxyArray.fromArray(42));
        for (int i = 0; i < 10000; i++) {
            v.getArrayElement(0);
        }
        previousHeap = computeRetainedSizeImpl();
        accumulatedFootprint = 0;
        values.clear();
    }

    @Override
    public void afterLoad(Config config) {
        computeRetainedSize();
    }

    @Override
    public void beforeIteration(boolean warmup, int iteration, Config config) {
    }

    @Override
    public void reset() {
        values.clear();
    }

    @Override
    public Optional<Double> reportAfterIteration(Config config) {
        return computeRetainedSize();
    }

    private Optional<Double> computeRetainedSize() {
        long heap = computeRetainedSizeImpl();
        this.accumulatedFootprint += heap - previousHeap;
        this.previousHeap = heap;
        values.add(accumulatedFootprint);
        return Optional.of((double) accumulatedFootprint);
    }

    @Override
    public Optional<Double> reportAfterAll() {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        // report median value
        Collections.sort(values);
        return Optional.of((double) values.get(values.size() / 2));
    }

    private long computeRetainedSizeImpl() {
        try {
            File hprof = File.createTempFile("memoryUsage", ".hprof");
            try {
                hprof.delete();

                dumpHeap(hprof.getAbsolutePath(), true);
                Heap heap = HeapFactory.createHeap(hprof);

                Iterator<Instance> instances = heap.getAllInstancesIterator();
                Stream<Instance> targetStream = StreamSupport.stream(
                                Spliterators.spliteratorUnknownSize(instances, Spliterator.ORDERED),
                                false);
                /*
                 * This goes over all instances computing whether the value is reachable. If not
                 * reachable we do not produce its size. Note that using a parallel() stream here is
                 * not any faster, due to getNearestGCRootPointer() requiring a lock almost all the
                 * time.
                 */
                return targetStream.map((instance) -> {
                    Instance root = instance.getNearestGCRootPointer();
                    if (root != null) {
                        return instance.getSize();
                    }
                    if (heap.getGCRoots(instance).size() > 0) {
                        return instance.getSize();
                    }
                    return 0L;
                }).reduce((a, b) -> a + b).orElse(0L);
            } finally {
                hprof.delete();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not create temporary heap dump. ", e);
        }
    }

    private void dumpHeap(String fileName, boolean live) {
        try {
            dumpHeap.invoke(dumpHeapBean, fileName, live);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }

}
