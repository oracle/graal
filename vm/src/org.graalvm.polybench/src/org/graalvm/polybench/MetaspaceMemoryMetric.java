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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.management.MBeanServer;

import org.graalvm.launcher.Launcher;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.visualvm.lib.jfluid.heap.Heap;
import org.graalvm.visualvm.lib.jfluid.heap.HeapFactory;
import org.graalvm.visualvm.lib.jfluid.heap.Instance;

/**
 * This metric collects the memory footprint of code-related data structures of a benchmark. For
 * each iteration, the metric reports the difference of the retained memory used between
 * {@link #beforeInitialize(Config) before-initialization} and {@link #reportAfterIteration(Config)
 * after-iteration}. For each measurement it subtracts the size of objects known to represent
 * non-code related data structures like user objects allocated as part of the benchmark.
 *
 * This metric is currently only supported on JVM. It is recommended to build a libgraal image and
 * not use jargraal for running this metric for more predictable results. Otherwise the heap dumps
 * created to compute the retained heap size are influenced by allocations happening on the compiler
 * threads.
 *
 * This metric might be too slow for large heaps (>10GB). Use VisualVM or other memory inspection
 * tools for debugging regressions measured by this metric.
 */
public final class MetaspaceMemoryMetric extends Metric {

    private final Method dumpHeap;
    private final Object dumpHeapBean; // This is the name of the HotSpot Diagnostic MBean
    private final String hotspotBeanName = "com.sun.management:type=HotSpotDiagnostic";
    private long startHeap;
    private long startMaxContextHeap;

    public MetaspaceMemoryMetric() {
        try {
            if (Launcher.isAOT()) {
                dumpHeapBean = null;
                dumpHeap = null;
            } else {
                Class<?> clazz = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
                MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                dumpHeapBean = ManagementFactory.newPlatformMXBeanProxy(server, hotspotBeanName, clazz);
                dumpHeap = clazz.getMethod("dumpHeap", String.class, boolean.class);
            }
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }

    @Override
    public String unit() {
        return "B";
    }

    @Override
    public Map<String, String> getEngineOptions(Config config) {
        HashMap<String, String> map = new HashMap<>();
        map.put("memory-usage", "true");
        return map;
    }

    @Override
    public void beforeInitialize(Config config) {
        /*
         * This is supposed to trigger an eager compilation before we start to measure. We are not
         * interested in overhead incurred by the compiler here and this is intended to reduce the
         * noise. In particular when running with jargraal (e.g. for debugging purposes).
         */
        Value v = Context.getCurrent().asValue(ProxyArray.fromArray(42));
        for (int i = 0; i < 10000; i++) {
            v.getArrayElement(0);
        }
        startHeap = computeRetainedSizeImpl();
        startMaxContextHeap = contextHeap();
    }

    @Override
    public Optional<Double> reportAfterIteration(Config config) {
        long heap = computeRetainedSizeImpl() - startHeap;
        long maxContextHeap = contextHeap() - startMaxContextHeap;
        return Optional.of((double) (heap - maxContextHeap));
    }

    public static long contextHeap() {
        return Context.getCurrent().getPolyglotBindings().getMember("getContextHeapSize").execute().asLong();
    }

    private long computeRetainedSizeImpl() {
        try {
            System.gc();
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
        if (dumpHeapBean == null) {
            throw new UnsupportedOperationException("Heap dumps are not supported on this platform.");
        }
        try {
            dumpHeap.invoke(dumpHeapBean, fileName, live);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new UnsupportedOperationException(e);
        }
    }

}
