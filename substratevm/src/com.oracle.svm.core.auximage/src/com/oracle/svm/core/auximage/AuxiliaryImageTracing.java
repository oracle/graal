/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Set;

import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.shared.util.TimeUtils;
import com.oracle.svm.core.util.Timer;
import com.oracle.svm.shared.util.ClassUtil;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;

final class AuxiliaryImageTracing {
    static class Options {
        @Option(help = "Enables detailed inspection of auxiliary image classes.", type = OptionType.Expert) //
        public static final RuntimeOptionKey<Boolean> TraceAuxiliaryImageClassHistogram = new RuntimeOptionKey<>(false);
        @Option(help = "Enables detailed inspection of auxiliary image reference trees.", type = OptionType.Expert) //
        public static final RuntimeOptionKey<Boolean> TraceAuxiliaryImageReferenceTree = new RuntimeOptionKey<>(false);
        @Option(help = "Enables detailed tracing of auxiliary image operations.", type = OptionType.Expert)//
        public static final RuntimeOptionKey<Boolean> TraceAuxiliaryImageOperations = new RuntimeOptionKey<>(false);
    }

    static final PersistTimers persistTimers = new PersistTimers();
    static final LoadTimers loadTimers = new LoadTimers();

    static void traceAfterSnapshot(AuxiliaryImageHeap heap, int imageSize) {
        if (Options.TraceAuxiliaryImageClassHistogram.getValue() || Options.TraceAuxiliaryImageReferenceTree.getValue()) {
            Log trace = Log.log();
            printPrefix(trace, "persist").string("bytes written: ").unsigned(imageSize).newline();
            traceHistogramAndTree(trace, heap);
        }
        if (Options.TraceAuxiliaryImageOperations.getValue()) {
            persistTimers.log(Log.log());
            persistTimers.reset();
        }
    }

    static void traceAfterLoad() {
        if (Options.TraceAuxiliaryImageOperations.getValue()) {
            loadTimers.log(Log.log());
            loadTimers.reset();
        }
    }

    private static void traceHistogramAndTree(Log trace, AuxiliaryImageHeap heap) {
        if (Options.TraceAuxiliaryImageReferenceTree.getValue()) {
            trace.newline();
            printPrefix(trace, "persist").string("Object tree:").newline();
            Map<Object, List<Object>> tree = new IdentityHashMap<>();
            for (AuxiliaryImageHeapObject info : heap.getObjects()) {
                Object from = info.getReachableFrom();
                Object to = info.getObject();
                tree.computeIfAbsent(from, _ -> new ArrayList<>()).add(to);
            }
            Object root = heap.getRootObject();
            printObjectTree(trace, tree.get(root), tree, 0, 140);
        }

        if (Options.TraceAuxiliaryImageClassHistogram.getValue()) {
            printPrefix(trace, "persist").string("Object histogram:").newline();
            Map<String, LongSummaryStatistics> statistics = new LinkedHashMap<>();
            for (Object obj : heap.getPlainObjects()) {
                LongSummaryStatistics stats = statistics.computeIfAbsent(getFullClassName(obj), _ -> new LongSummaryStatistics());
                stats.accept(LayoutEncoding.getMomentarySizeFromObject(obj).rawValue());
            }
            if (!statistics.isEmpty()) {
                List<Map.Entry<String, LongSummaryStatistics>> sortedEntries = statistics.entrySet().stream().sorted(Comparator.comparingLong(s -> s.getValue().getSum())).toList();
                int maxNameLength = sortedEntries.stream().mapToInt(e -> e.getKey().length()).max().getAsInt();
                for (Map.Entry<String, LongSummaryStatistics> entry : sortedEntries) {
                    trace.string("`- ").string(entry.getKey(), maxNameLength, Log.LEFT_ALIGN);
                    trace.character(' ');
                    LongSummaryStatistics stats = entry.getValue();
                    trace.string("size ").unsigned(stats.getSum(), 5, Log.RIGHT_ALIGN).string(" byte");
                    printObjectSizeStatistics(trace, stats);
                    trace.newline();
                }
            }
        }
    }

    private static Map<String, ObjectSizeStatistics> computeHistogramAndSize(Map<Object, List<Object>> tree, Collection<Object> objects) {
        Map<String, ObjectSizeStatistics> statistics = new LinkedHashMap<>();
        for (Object obj : objects) {
            ObjectSizeStatistics stat = statistics.computeIfAbsent(ClassUtil.getUnqualifiedName(obj.getClass()), ObjectSizeStatistics::new);
            stat.instances.accept(LayoutEncoding.getMomentarySizeFromObject(obj).rawValue());
            stat.totalInstancesDeepSize += computeDeepSize(tree, obj);
        }
        return statistics;
    }

    private static long computeDeepSize(Map<Object, List<Object>> tree, Object parent) {
        long size = LayoutEncoding.getMomentarySizeFromObject(parent).rawValue();
        for (Object c : tree.getOrDefault(parent, Collections.emptyList())) {
            size += computeDeepSize(tree, c);
        }
        return size;
    }

    private static void printObjectTree(Log trace, Collection<Object> parents, Map<Object, List<Object>> tree, int depth, int maxLength) {
        if (parents == null || parents.isEmpty()) {
            return;
        }
        Set<Object> children = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object parent : parents) {
            children.addAll(tree.getOrDefault(parent, Collections.emptyList()));
        }
        if (children.isEmpty()) {
            return;
        }
        Map<String, List<Object>> objectsGroupedBySimpleName = new LinkedHashMap<>();
        for (Object obj : children) {
            objectsGroupedBySimpleName.computeIfAbsent(ClassUtil.getUnqualifiedName(obj.getClass()), _ -> new ArrayList<>()).add(obj);
        }
        Map<String, ObjectSizeStatistics> statistics = computeHistogramAndSize(tree, children);
        List<ObjectSizeStatistics> list = statistics.values().stream().sorted(Comparator.comparingLong(ObjectSizeStatistics::getTotalInstancesDeepSize).reversed()).toList();
        for (ObjectSizeStatistics entry : list) {
            for (int i = 0; i < depth; i++) {
                trace.string("   ");
            }
            trace.string("`- ");
            trace.string(entry.name);
            int consumedChars = 3 * depth + 3 + entry.name.length();
            for (int i = 0; i < maxLength - consumedChars; i++) {
                trace.character(' ');
            }
            trace.character(' ');

            trace.string("deep size ").unsigned(entry.totalInstancesDeepSize, 7, Log.RIGHT_ALIGN).string(" byte");
            trace.string(", shallow size ").unsigned(entry.instances.getSum(), 5, Log.RIGHT_ALIGN).string(" byte");
            printObjectSizeStatistics(trace, entry.instances);
            trace.newline();
            printObjectTree(trace, objectsGroupedBySimpleName.getOrDefault(entry.name, Collections.emptyList()), tree, depth + 1, maxLength);
        }
    }

    private static void printObjectSizeStatistics(Log trace, LongSummaryStatistics stats) {
        trace.string(", instances ").unsigned(stats.getCount(), 5, Log.RIGHT_ALIGN);
        trace.string(", min  ").unsigned(stats.getMin(), 5, Log.RIGHT_ALIGN).string(" byte");
        trace.string(", max ").unsigned(stats.getMax(), 5, Log.RIGHT_ALIGN).string(" byte");
        long count = stats.getCount() > 0 ? stats.getCount() : 1;   // avoid zero division
        trace.string(", avg ").rational(stats.getSum(), count, 2).string(" byte");
    }

    private static String getFullClassName(Object obj) {
        return obj.getClass().getName();
    }

    private static Log printPrefix(Log trace, String prefix) {
        return trace.string("[auximage:").string(prefix).string("] ");
    }

    public static Log logTimer(Log trace, Timer timer) {
        long elapsedMillis = TimeUtils.roundNanosToMillis(timer.totalNanos());
        return trace.string("`- ").string(timer.name()).string(": ").signed(elapsedMillis).newline();
    }

    private static class ObjectSizeStatistics {
        final String name;
        final LongSummaryStatistics instances = new LongSummaryStatistics();
        long totalInstancesDeepSize;

        ObjectSizeStatistics(String name) {
            this.name = name;
        }

        long getTotalInstancesDeepSize() {
            return totalInstancesDeepSize;
        }
    }

    static final class PersistTimers {
        final Timer initialGC = new Timer("initial GC");
        final Timer traverseObjMap = new Timer("user obj traversal");
        final Timer traverseMetaObj = new Timer("meta obj traversal");
        final Timer initHeap = new Timer("heap init");
        final Timer layoutHeap = new Timer("heap layout");
        final Timer write = new Timer("write");
        final Timer total = new Timer("total");

        void log(Log trace) {
            printPrefix(trace, "persist").string("Timers (ms):").newline();
            logTimer(trace, initialGC);
            logTimer(trace, traverseObjMap);
            logTimer(trace, traverseMetaObj);
            logTimer(trace, initHeap);
            logTimer(trace, layoutHeap);
            logTimer(trace, write);
            logTimer(trace, total);
        }

        void reset() {
            initialGC.reset();
            traverseObjMap.reset();
            traverseMetaObj.reset();
            initHeap.reset();
            layoutHeap.reset();
            write.reset();
            total.reset();
        }
    }

    static final class LoadTimers {
        final Timer load = new Timer("load");
        final Timer installCode = new Timer("install code");
        final Timer total = new Timer("total");

        void log(Log trace) {
            printPrefix(trace, "load").string("Timers (ms):").newline();
            logTimer(trace, load);
            logTimer(trace, installCode);
            logTimer(trace, total);
        }

        void reset() {
            load.reset();
            installCode.reset();
            total.reset();
        }
    }

    private AuxiliaryImageTracing() {
    }
}
