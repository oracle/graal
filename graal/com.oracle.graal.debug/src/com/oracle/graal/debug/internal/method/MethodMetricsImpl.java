/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.debug.internal.method;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugCounter;
import com.oracle.graal.debug.DebugMethodMetrics;
import com.oracle.graal.debug.internal.DebugScope;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class MethodMetricsImpl implements DebugMethodMetrics {

    private static final DebugCounter CompilationEntries = Debug.counter("MethodMetricCompilationEntries");

    private final ResolvedJavaMethod method;
    private ArrayList<CompilationData> compilationEntries;

    private static class CompilationData {
        private final long id;
        private final HashMap<String, Long> counterMap;

        CompilationData(long id, HashMap<String, Long> metrics) {
            this.id = id;
            this.counterMap = metrics;
        }
    }

    MethodMetricsImpl(ResolvedJavaMethod method) {
        this.method = method;
    }

    private synchronized int getIndex(long compilationID) {
        if (compilationEntries == null) {
            compilationEntries = new ArrayList<>();
        }
        for (int i = 0; i < compilationEntries.size(); i++) {
            if (compilationEntries.get(i).id == compilationID) {
                return i;
            }
        }
        compilationEntries.add(new CompilationData(compilationID, new HashMap<>()));
        CompilationEntries.increment();
        return compilationEntries.size() - 1;
    }

    private synchronized long getCounterInternal(int ci, String metricName) {
        assert ci >= 0;
        assert compilationEntries != null;
        assert ci < compilationEntries.size();
        if (!compilationEntries.get(ci).counterMap.containsKey(metricName)) {
            return 0;
        }
        return compilationEntries.get(ci).counterMap.get(metricName);
    }

    private synchronized void putCounterInternal(int ci, String metricName, long val) {
        assert compilationEntries != null;
        assert ci < compilationEntries.size();
        compilationEntries.get(ci).counterMap.put(metricName, val);
    }

    private static long compilationId() {
        return DebugScope.getInstance().scopeId();
    }

    @Override
    public void addToMetric(long value, String metricName) {
        if (!Debug.isMethodMeterEnabled() || value == 0) {
            return;
        }
        assert metricName != null;
        long compilationId = compilationId();
        assert compilationId >= 0;
        putCounterInternal(getIndex(compilationId), metricName, getCounterInternal(getIndex(compilationId), metricName) + value);
    }

    @Override
    public long getCurrentMetricValue(String metricName) {
        assert metricName != null;
        long compilationId = compilationId();
        assert compilationId >= 0;
        int index = getIndex(compilationId);
        if (compilationEntries == null) {
            return 0;
        }
        return getCounterInternal(index, metricName);
    }

    @Override
    public void addToMetric(long value, String format, Object arg1) {
        addToMetric(value, String.format(format, arg1));
    }

    @Override
    public void addToMetric(long value, String format, Object arg1, Object arg2) {
        addToMetric(value, String.format(format, arg1, arg2));
    }

    @Override
    public void addToMetric(long value, String format, Object arg1, Object arg2, Object arg3) {
        addToMetric(value, String.format(format, arg1, arg2, arg3));
    }

    @Override
    public void incrementMetric(String metricName) {
        addToMetric(1, metricName);
    }

    @Override
    public void incrementMetric(String format, Object arg1) {
        incrementMetric(String.format(format, arg1));
    }

    @Override
    public void incrementMetric(String format, Object arg1, Object arg2) {
        incrementMetric(String.format(format, arg1, arg2));
    }

    @Override
    public void incrementMetric(String format, Object arg1, Object arg2, Object arg3) {
        incrementMetric(String.format(format, arg1, arg2, arg3));
    }

    @Override
    public long getCurrentMetricValue(String format, Object arg1) {
        return getCurrentMetricValue(String.format(format, arg1));
    }

    @Override
    public long getCurrentMetricValue(String format, Object arg1, Object arg2) {
        return getCurrentMetricValue(String.format(format, arg1, arg2));
    }

    @Override
    public long getCurrentMetricValue(String format, Object arg1, Object arg2, Object arg3) {
        return getCurrentMetricValue(String.format(format, arg1, arg2, arg3));
    }

    public long getMetricValueFromCompilationIndex(int compilationIndex, String metricName) {
        if (compilationEntries == null || compilationIndex >= compilationEntries.size()) {
            return 0;
        }
        return getCounterInternal(compilationIndex, metricName);
    }

    public long getCompilationIndexForId(long compilationId) {
        if (compilationEntries == null) {
            return 0;
        }
        return getIndex(compilationId);
    }

    @Override
    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public static DebugMethodMetrics getMetricIfDefined(ResolvedJavaMethod method) {
        assert method != null;
        return MethodMetricsCache.getMethodMetrics(method);
    }

    public static DebugMethodMetrics getMethodMetrics(ResolvedJavaMethod method) {
        assert method != null;
        DebugMethodMetrics metric = MethodMetricsCache.getMethodMetrics(method);
        if (metric == null) {
            MethodMetricsCache.defineMethodMetrics(method);
            metric = MethodMetricsCache.getMethodMetrics(method);
        }
        return metric;
    }

    private static final boolean DUMP_SCOPE_ID = false;

    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        final String lineSep = System.lineSeparator();
        String methodName = method.toString();
        int maxLen = methodName.length();
        // determine longest key value pair
        if (compilationEntries != null) {
            for (int i = 0; i < compilationEntries.size(); i++) {
                HashMap<String, Long> table = compilationEntries.get(i).counterMap;
                if (table != null) {
                    Optional<String> sl = table.keySet().stream().max((x, y) -> Integer.valueOf(x.length()).compareTo(y.length()));
                    if (sl.isPresent()) {
                        maxLen = Math.max(maxLen, sl.get().length() + 23/* formatting */);
                    }
                }
            }
        }
        for (int i = 0; i < maxLen; i++) {
            sb.append("=");
        }
        sb.append(lineSep);
        sb.append(methodName).append(lineSep);
        for (int i = 0; i < maxLen; i++) {
            sb.append("_");
        }
        sb.append(lineSep);
        if (compilationEntries != null) {
            for (int i = 0; i < compilationEntries.size(); i++) {
                HashMap<String, Long> table = compilationEntries.get(i).counterMap;
                if (table != null) {
                    if (DUMP_SCOPE_ID) {
                        sb.append("Compilation Debug Scope ID  -> ").append(compilationEntries.get(i).id).append(lineSep);
                    }
                    Set<Map.Entry<String, Long>> entries = table.entrySet();
                    for (Map.Entry<String, Long> entry : entries.stream().sorted((x, y) -> x.getKey().compareTo(y.getKey())).collect(Collectors.toList())) {
                        long value = entry.getValue();
                        // report timers in ms and memory in
                        if ((entry.getKey().endsWith("Accm") || entry.getKey().endsWith("Flat")) && !entry.getKey().toLowerCase().contains("mem")) {
                            value = value / 1000000;
                        }
                        if (value == 0) {
                            continue;
                        }
                        sb.append(String.format("%-" + String.valueOf(maxLen - 23) + "s = %20d", entry.getKey(), value)).append(lineSep);
                    }
                    for (int j = 0; j < maxLen; j++) {
                        sb.append("~");
                    }
                    sb.append(lineSep);
                }
            }
        }
        for (int i = 0; i < maxLen; i++) {
            sb.append("=");
        }
        sb.append(lineSep);
        return sb.toString();
    }

    public synchronized void dumpCSV(PrintStream p) {
        String methodName = method.format("%H.%n(%p)%R").replace(",", "_");
        if (compilationEntries != null) {
            for (int i = 0; i < compilationEntries.size(); i++) {
                HashMap<String, Long> table = compilationEntries.get(i).counterMap;
                if (table != null) {
                    Set<Map.Entry<String, Long>> entries = table.entrySet();
                    for (Map.Entry<String, Long> entry : entries.stream().sorted((x, y) -> x.getKey().compareTo(y.getKey())).collect(Collectors.toList())) {
                        p.printf("%s,%d,%s,%d", methodName, i,
                                        entry.getKey().replace(" ", "_").replace(",", "_"), entry.getValue());
                        p.println();
                    }
                }
            }
        }
    }

    private static class MethodMetricsCache {
        private static final DebugCounter CacheSize = Debug.counter("MethodMetricCacheSize");

        private static ConcurrentHashMap<ResolvedJavaMethod, DebugMethodMetrics> cache;

        private static void initizalize() {
            cache = new ConcurrentHashMap<>();
        }

        private static synchronized void ensureInitialized() {
            if (cache == null) {
                initizalize();
            }
        }

        private static boolean isDefined(ResolvedJavaMethod m) {
            return cache != null && cache.containsKey(m);
        }

        private static void defineMethodMetrics(ResolvedJavaMethod method) {
            assert method != null;
            ensureInitialized();
            synchronized (MethodMetricsCache.class) {
                cache.put(method, new MethodMetricsImpl(method));
            }
            CacheSize.increment();
        }

        private static DebugMethodMetrics getMethodMetrics(ResolvedJavaMethod method) {
            assert method != null;
            ensureInitialized();
            return cache.get(method);
        }
    }

    public static Collection<DebugMethodMetrics> collectedMetrics() {
        /*
         * we want to avoid a concurrent modification when collecting metrics therefore we lock the
         * cache class
         */
        synchronized (MethodMetricsCache.class) {
            if (MethodMetricsCache.cache == null) {
                return Collections.emptyList();
            }
            ArrayList<DebugMethodMetrics> mm = new ArrayList<>();
            MethodMetricsCache.cache.values().forEach(x -> mm.add(x));
            return mm;
        }
    }

    public static void clearMM() {
        MethodMetricsCache.cache = null;
    }

    private static final String INLINEE_PREFIX = "INLINING_SCOPE_";

    private static final boolean TRACK_INLINED_SCOPES = false;

    public static void recordInlinee(ResolvedJavaMethod root, ResolvedJavaMethod caller, ResolvedJavaMethod inlinee) {
        if (TRACK_INLINED_SCOPES) {
            // format is costly
            Debug.methodMetrics(root).addToMetric(1, "INLINED_METHOD_root: caller:%s inlinee:%s",
                            caller, inlinee);
        }
    }

    public static void addToCurrentScopeMethodMetrics(String metricName, long value) {
        DebugScope.ExtraInfo metaInfo = DebugScope.getInstance().getExtraInfo();
        if (metaInfo instanceof MethodMetricsRootScopeInfo) {
            ResolvedJavaMethod rootMethod = ((MethodMetricsRootScopeInfo) metaInfo).getRootMethod();
            if (metaInfo instanceof MethodMetricsInlineeScopeInfo) {
                /*
                 * if we make use of a method filter(s) together with interception we get a problem
                 * with inlined methods and their scopes. Inlining will put the inlinee(s) on the
                 * debug scope context thus Debug.areMethodMetricsEnabled() will yield true if an
                 * inlinee matches a method filter. Thus we must make sure the root is defined as
                 * this means the root matched a method filter and therefore the inlinee can be
                 * safely recorded.
                 */
                if (TRACK_INLINED_SCOPES) {
                    if (MethodMetricsCache.isDefined(rootMethod)) {
                        Debug.methodMetrics(rootMethod).addToMetric(value, "%s%s", INLINEE_PREFIX, metricName);
                    }
                }
            } else {
                // avoid the lookup over Debug.methodMetrics
                getMethodMetrics(rootMethod).addToMetric(value, metricName);
            }
        }
    }

}
