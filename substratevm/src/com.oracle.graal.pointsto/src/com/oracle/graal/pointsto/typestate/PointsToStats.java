/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.typestate;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.AllInstantiatedTypeFlow;
import com.oracle.graal.pointsto.flow.AllSynchronizedTypeFlow;
import com.oracle.graal.pointsto.flow.ArrayElementsTypeFlow;
import com.oracle.graal.pointsto.flow.CloneTypeFlow;
import com.oracle.graal.pointsto.flow.ContextInsensitiveFieldTypeFlow;
import com.oracle.graal.pointsto.flow.DynamicNewInstanceTypeFlow;
import com.oracle.graal.pointsto.flow.FieldFilterTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.FilterTypeFlow;
import com.oracle.graal.pointsto.flow.FormalParamTypeFlow;
import com.oracle.graal.pointsto.flow.FormalReturnTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.LoadFieldTypeFlow.LoadInstanceFieldTypeFlow;
import com.oracle.graal.pointsto.flow.LoadFieldTypeFlow.LoadStaticFieldTypeFlow;
import com.oracle.graal.pointsto.flow.MergeTypeFlow;
import com.oracle.graal.pointsto.flow.NewInstanceTypeFlow;
import com.oracle.graal.pointsto.flow.NullCheckTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.LoadIndexedTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.UnsafeLoadTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.StoreIndexedTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.UnsafeStoreTypeFlow;
import com.oracle.graal.pointsto.flow.SourceTypeFlow;
import com.oracle.graal.pointsto.flow.StoreFieldTypeFlow.StoreInstanceFieldTypeFlow;
import com.oracle.graal.pointsto.flow.StoreFieldTypeFlow.StoreStaticFieldTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.builder.TypeFlowBuilder;
import com.oracle.graal.pointsto.flow.context.bytecode.ContextSensitiveMultiTypeState;
import com.oracle.graal.pointsto.flow.context.bytecode.ContextSensitiveSingleTypeState;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This class provides methods for collecting and reporting statistics about
 * {@link PointsToAnalysis}. It tracks various metrics such as {@link TypeState} memory footprint,
 * {@link TypeFlow} statistics, and union operation statistics. If the {@link TypeFlow} or
 * {@link TypeState} hierarchy changes, this class might have to be updated to reflect that.
 *
 * @see PointsToAnalysis
 * @see TypeFlow
 * @see TypeState
 */
public class PointsToStats {

    static boolean reportStatistics;
    static boolean reportTypeStateMemoryFootPrint;

    public static void init(PointsToAnalysis bb) {
        reportStatistics = bb.reportAnalysisStatistics();
        reportTypeStateMemoryFootPrint = bb.reportTypeStateMemoryFootprint();
        registerTypeState(bb, EmptyTypeState.SINGLETON);
        registerTypeState(bb, NullTypeState.SINGLETON);
        registerTypeState(bb, AnyPrimitiveTypeState.SINGLETON);
        PrimitiveConstantTypeState.registerCachedTypeStates(bb);
    }

    public static void report(@SuppressWarnings("unused") BigBang bb, String reportNameRoot) {
        assert reportStatistics || reportTypeStateMemoryFootPrint : "At least one of these options should be selected.";
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
            String timeStamp = LocalDateTime.now().format(formatter);
            Path statsDirectory = Files.createDirectories(FileSystems.getDefault().getPath("svmbuild").resolve("stats"));

            /* Both report option include the footprint, so generate it unconditionally. */
            doReport(statsDirectory, reportNameRoot, "type state memory footprint", timeStamp, PointsToStats::reportTypeStateMemoryFootprint);
            if (reportStatistics) {
                /* The rest of reports should only be generated if reportStatistics was enabled. */
                doReport(statsDirectory, reportNameRoot, "detailed type state stats", timeStamp, PointsToStats::reportTypeStateStats);
                doReport(statsDirectory, reportNameRoot, "union operation stats", timeStamp, PointsToStats::reportUnionOpertationsStats);
                doReport(statsDirectory, reportNameRoot, "type flow stats", timeStamp, PointsToStats::reportTypeFlowStats);
                doReport(statsDirectory, reportNameRoot, "pruned type flow stats", timeStamp, PointsToStats::reportPrunedTypeFlows);
            }

        } catch (IOException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }
    }

    private static void doReport(Path dir, String reportNameRoot, String whatIsReported, String timeStamp, Consumer<BufferedWriter> reporter) {
        try {
            Path reportFile = dir.resolve(reportNameRoot + "_" + whatIsReported.replace(' ', '_') + "_" + timeStamp + ".tsv");
            Files.deleteIfExists(reportFile);
            try (FileWriter fw = new FileWriter(Files.createFile(reportFile).toFile())) {
                try (BufferedWriter writer = new BufferedWriter(fw)) {
                    System.out.println("Printing " + whatIsReported + " to " + reportFile.toAbsolutePath());
                    reporter.accept(writer);
                }
            }
        } catch (IOException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }

    }

    private static List<TypeFlowBuilder<?>> typeFlowBuilders = new CopyOnWriteArrayList<>();

    public static void registerTypeFlowBuilder(PointsToAnalysis bb, TypeFlowBuilder<?> builder) {
        if (!bb.reportAnalysisStatistics()) {
            return;
        }

        typeFlowBuilders.add(builder);
    }

    private static void reportPrunedTypeFlows(BufferedWriter out) {

        doWrite(out, String.format("%-35s%n", "Summary"));
        doWrite(out, String.format("%-35s\t%-10s%n", "Type Flow Class", "Removed Count"));

        typeFlowBuilders.stream().filter(Objects::nonNull).filter(b -> !b.isMaterialized()).collect(Collectors.groupingBy(TypeFlowBuilder::getFlowClass)).forEach((flowClass, providers) -> {
            doWrite(out, String.format("%-35s\t%-10d%n",
                            ClassUtil.getUnqualifiedName(flowClass), providers.size()));
        });

        doWrite(out, String.format("%n%-35s%n", "Removed flows"));
        doWrite(out, String.format("%-35s\t%-10s%n", "Type Flow Class", "Location"));

        typeFlowBuilders.stream().filter(Objects::nonNull).filter(b -> !b.isMaterialized()).forEach((provider) -> {
            Object source = provider.getSource();
            String sourceStr;
            if (source instanceof ValueNode) {
                ValueNode value = (ValueNode) source;
                NodeSourcePosition srcPosition = value.getNodeSourcePosition();
                if (srcPosition != null) {
                    sourceStr = srcPosition.toString();
                } else {
                    sourceStr = value.toString() + " @ " + value.graph().method().format("%H.%n(%p)");
                }
            } else {
                sourceStr = source != null ? source.toString() : "null";
            }
            doWrite(out, String.format("%-35s\t%-10s%n",
                            ClassUtil.getUnqualifiedName(provider.getFlowClass()), sourceStr));
        });

    }

    static class TypeFlowStats {
        final TypeFlow<?> flow;

        final List<TypeState> allUpdates;
        final List<TypeState> successfulUpdates;
        final AtomicInteger queuedUpdates;

        TypeFlowStats(TypeFlow<?> flow) {
            this.flow = flow;
            this.allUpdates = new CopyOnWriteArrayList<>();
            this.successfulUpdates = new CopyOnWriteArrayList<>();
            this.queuedUpdates = new AtomicInteger(0);
        }

        int allUpdatesCount() {
            return allUpdates.size();
        }

        int successfulUpdatesCount() {
            return successfulUpdates.size();
        }

        int queuedUpdatesCount() {
            return queuedUpdates.get();
        }

        void registerUpdate(TypeState state) {
            allUpdates.add(state);
        }

        void registerSuccessfulUpdate(TypeState state) {
            successfulUpdates.add(state);
        }

        void registerQueuedUpdate() {
            queuedUpdates.incrementAndGet();
        }

        String allUpdatesHistory() {
            return updatesHistory(allUpdates);
        }

        private static String updatesHistory(List<TypeState> updates) {
            return updates.stream()
                            .filter(Objects::nonNull)
                            // Get a Map<TypeState, UpdateFrequency>
                            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                            .entrySet().stream()
                            // Sort the map by value, i.e, the type state frequency
                            .sorted(Entry.comparingByValue(longComparator.reversed()))
                            // Map each entry to string
                            .map(entry -> entry.getValue() + "x" + stateToId.get(entry.getKey()))
                            .collect(Collectors.joining(", "));

        }
    }

    private static ConcurrentHashMap<TypeFlow<?>, TypeFlowStats> typeFlowStats = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<TypeFlow<?>, String> retainReson = new ConcurrentHashMap<>();

    public static void registerTypeFlowRetainReason(PointsToAnalysis bb, TypeFlow<?> flow, String reason) {
        if (!bb.reportAnalysisStatistics()) {
            return;
        }

        retainReson.put(flow, reason);
    }

    public static void registerTypeFlowRetainReason(TypeFlow<?> flow, TypeFlow<?> original) {
        if (!reportStatistics) {
            return;
        }

        String originalFlowReason = retainReson.getOrDefault(original, "");
        retainReson.put(flow, originalFlowReason);
    }

    public static void registerTypeFlowUpdate(PointsToAnalysis bb, TypeFlow<?> flow, TypeState state) {
        if (!bb.reportAnalysisStatistics()) {
            return;
        }

        if (state.isEmpty()) {
            return;
        }

        TypeFlowStats stats = typeFlowStats.computeIfAbsent(flow, TypeFlowStats::new);
        stats.registerUpdate(state);
    }

    public static void registerTypeFlowSuccessfulUpdate(PointsToAnalysis bb, TypeFlow<?> flow, TypeState state) {
        if (!bb.reportAnalysisStatistics()) {
            return;
        }

        if (state.isEmpty()) {
            return;
        }

        TypeFlowStats stats = typeFlowStats.computeIfAbsent(flow, TypeFlowStats::new);
        stats.registerSuccessfulUpdate(state);
    }

    public static void registerTypeFlowQueuedUpdate(PointsToAnalysis bb, TypeFlow<?> flow) {
        if (!bb.reportAnalysisStatistics()) {
            return;
        }

        TypeFlowStats stats = typeFlowStats.computeIfAbsent(flow, TypeFlowStats::new);
        stats.registerQueuedUpdate();
    }

    static final Comparator<Long> longComparator = Comparator.naturalOrder();

    private static void reportTypeFlowStats(BufferedWriter out) {

        doWrite(out, String.format("%-35s\t%-10s\t%-10s\t%-10s\t%-10s\t%-10s\t%-10s\t%-10s\t%-10s\t%-10s\t%-10s\t%-10s\t%10s%n",
                        "TypeFlow", "TypeStateID", "StateObjects#", "CanBeNull", "IsClone", "Uses", "Observers", "Uses+Observers",
                        "RetainReason", "QueuedUpdates", "AllUpdates", "TypeStateAdds", "All Updates History (<update frequency>x<type state id>)"));

        typeFlowStats.entrySet().stream()
                        .forEach(e -> {
                            TypeFlow<?> flow = e.getKey();
                            TypeFlowStats stats = e.getValue();

                            doWrite(out, String.format("%-35s\t%-10d\t%-10d\t%-10b\t%-10b\t%-10d\t%-10d\t%-10d\t%-10s\t%-10d\t%10d\t%10d\t%10s%n",
                                            asString(flow), stateToId.get(flow.getRawState()), objectsCount(flow.getRawState()),
                                            flow.getRawState().canBeNull(), flow.isClone(),
                                            flow.getUses().size(), flow.getObservers().size(), flow.getUses().size() + flow.getObservers().size(),
                                            retainReson.getOrDefault(flow, ""),
                                            stats.queuedUpdatesCount(), stats.successfulUpdatesCount(), stats.allUpdatesCount(),
                                            stats.allUpdatesHistory()));
                        });

    }

    private static ConcurrentHashMap<TypeState, Integer> stateToId = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<Integer, TypeState> idToState = new ConcurrentHashMap<>();

    // type state frequency
    private static final Comparator<AtomicInteger> atomicIntegerComparator = Comparator.comparingInt(AtomicInteger::intValue);
    private static final AtomicInteger nextStateId = new AtomicInteger();
    private static ConcurrentHashMap<TypeState, AtomicInteger> typeStateStats = new ConcurrentHashMap<>();

    /**
     * Contains the count and total size of the given TypeState class.
     *
     * @see #typeStateFootprint
     * @see #reportTypeStateMemoryFootprint
     * @see #registerTypeStateSize
     */
    private static final class TypeStateMemoryStats {
        AtomicInteger frequency = new AtomicInteger();
        AtomicLong size = new AtomicLong();
    }

    private static Map<Class<? extends TypeState>, TypeStateMemoryStats> typeStateFootprint = new ConcurrentHashMap<>();

    public static <T extends TypeState> T registerTypeState(PointsToAnalysis bb, T state) {
        if (bb.reportAnalysisStatistics() || bb.reportTypeStateMemoryFootprint()) {
            /* TypeState memory footprint is measured in both cases. */
            registerTypeStateSize(state);
        }

        if (!bb.reportAnalysisStatistics()) {
            return state;
        }

        Integer id = stateToId.computeIfAbsent(state, (s) -> nextStateId.incrementAndGet());
        TypeState actualState = idToState.computeIfAbsent(id, (i) -> state);

        typeStateStats.computeIfAbsent(actualState, (s) -> new AtomicInteger()).incrementAndGet();
        return state;
    }

    private static int objectsCount(TypeState state) {
        if (state.isPrimitive()) {
            return 0;
        }
        return state.objectsCount();
    }

    private static int typesCount(TypeState state) {
        if (state.isPrimitive()) {
            return 0;
        }
        return state.typesCount();
    }

    /**
     * This method is used to track the memory footprint of {@link TypeState} classes. It updates
     * the frequency and total size of the given {@link TypeState} class in the
     * {@link #typeStateFootprint} map.
     *
     * @param <T> the type of the {@link TypeState} instance
     * @param state the {@link TypeState} instance to register
     */
    private static <T extends TypeState> void registerTypeStateSize(T state) {
        var stats = typeStateFootprint.computeIfAbsent(state.getClass(), __ -> new TypeStateMemoryStats());
        stats.frequency.incrementAndGet();
        stats.size.addAndGet(getTypeStateMemorySize(state));
    }

    /**
     * In most cases, we use just the shallow size of the object as obtained from the heap dump.
     * However, {@link MultiTypeState} is an exception, because it represents a set of values, so we
     * consider the size of the underlying collection as well.
     */
    private static long getTypeStateMemorySize(TypeState typeState) {
        var shallowSize = getObjectSize(typeState);
        if (!(typeState instanceof MultiTypeState multi)) {
            return shallowSize;
        }
        var bitsetSize = getObjectSize(multi.typesBitSet);
        var wordArraySize = getObjectSize(TypeStateUtils.extractBitSetField(multi.typesBitSet));
        return shallowSize + bitsetSize + wordArraySize;
    }

    private static long getObjectSize(Object object) {
        return GraalAccess.getOriginalProviders().getMetaAccess().getMemorySize(GraalAccess.getOriginalProviders().getSnippetReflection().forObject(object));
    }

    /**
     * Reports the memory footprint of {@link TypeState} classes used by {@link PointsToAnalysis}.
     * <p>
     * This method writes a report to the provided {@link BufferedWriter} containing the frequency
     * and total size of each allocated {@link TypeState} class.
     * <p>
     * The report includes the following information:
     * <ul>
     * <li>Type: the class name of the {@link TypeState}</li>
     * <li>Frequency: the number of instances of the {@link TypeState} class</li>
     * <li>Total Size: the total memory size of all instances of the {@link TypeState} class</li>
     * </ul>
     * <p>
     * The report is written in a tabular format with the columns "Type", "Frequency", and "Total
     * Size".
     *
     * @param out the {@link BufferedWriter} to write the report to
     */
    private static void reportTypeStateMemoryFootprint(BufferedWriter out) {
        doWrite(out, String.format("%30s\t%15s\t%15s%n", "Type", "Frequency", "Total Size"));
        /* Use explicit order for the final report. */
        var typeStateOrder = List.of(EmptyTypeState.class, NullTypeState.class, PrimitiveConstantTypeState.class, AnyPrimitiveTypeState.class, SingleTypeState.class,
                        ContextSensitiveSingleTypeState.class, ConstantTypeState.class,
                        MultiTypeState.class, ContextSensitiveMultiTypeState.class);
        var totalFreq = 0L;
        var totalSize = 0L;
        for (var typeStateClass : typeStateOrder) {
            var stats = typeStateFootprint.remove(typeStateClass);
            if (stats != null) {
                doWrite(out, String.format("%30s\t%15d\t%15d%n", ClassUtil.getUnqualifiedName(typeStateClass), stats.frequency.get(), stats.size.get()));
                totalFreq += stats.frequency.get();
                totalSize += stats.size.get();
            }
        }
        AnalysisError.guarantee(typeStateFootprint.isEmpty(), "Missing elements in the typeStateOrder list: %s, please update it.", typeStateFootprint.keySet());
        doWrite(out, String.format("%30s\t%15d\t%15d%n", "TOTAL", totalFreq, totalSize));
    }

    private static void reportTypeStateStats(BufferedWriter out) {

        doWrite(out, String.format("%10s\t%10s\t%10s\t%10s\t%10s%n", "Id", "Frequency", "Types#", "Object#", "Types"));

        typeStateStats.entrySet().stream()
                        .sorted(Entry.comparingByValue(atomicIntegerComparator.reversed()))
                        .forEach(entry -> {
                            TypeState s = entry.getKey();
                            int frequency = entry.getValue().intValue();

                            doWrite(out, String.format("%10d\t%10d\t%10d\t%10d\t%10s%n",
                                            stateToId.get(s), frequency, typesCount(s), objectsCount(s), asString(s)));
                        });
    }

    // union operations frequency

    private static ConcurrentHashMap<UnionOperation, AtomicInteger> unionStats = new ConcurrentHashMap<>();

    public static void registerUnionOperation(PointsToAnalysis bb, TypeState s1, TypeState s2, TypeState result) {

        if (!bb.reportAnalysisStatistics()) {
            return;
        }

        assert typeStateStats.containsKey(s1) && typeStateStats.containsKey(s2) && typeStateStats.containsKey(result) : typeFlowStats;

        UnionOperation union = new UnionOperation(s1, s2, result);
        AtomicInteger counter = unionStats.computeIfAbsent(union, (k) -> new AtomicInteger());
        counter.incrementAndGet();
    }

    private static void reportUnionOpertationsStats(BufferedWriter out) {

        doWrite(out, String.format("%10s + %10s = %10s\t%10s%n", "State1ID", "State2ID", "ResultID", "Frequency"));
        unionStats.entrySet().stream()
                        .filter(e -> e.getValue().intValue() > 1)
                        .sorted(Entry.comparingByValue(atomicIntegerComparator.reversed()))
                        .forEach(entry -> {
                            UnionOperation union = entry.getKey();
                            Integer frequency = entry.getValue().intValue();

                            doWrite(out, String.format("%10d + %10d = %10d\t%10d\t%10s + %10s = %10s%n",
                                            union.getState1Id(), union.getState2Id(), union.getResultId(),
                                            frequency, asString(union.getState1()), asString(union.getState2()), asString(union.getResult())));
                        });
    }

    public static void cleanupAfterAnalysis() {
        typeStateStats = null;
        typeStateFootprint = null;
    }

    static class UnionOperation {
        int state1Id;
        int state2Id;
        int resultId;

        UnionOperation(TypeState state1, TypeState state2, TypeState result) {
            this.state1Id = stateToId.get(state1);
            this.state2Id = stateToId.get(state2);
            this.resultId = stateToId.get(result);
        }

        int getState1Id() {
            return state1Id;
        }

        TypeState getState1() {
            return PointsToStats.idToState.get(state1Id);
        }

        int getState2Id() {
            return state2Id;
        }

        TypeState getState2() {
            return PointsToStats.idToState.get(state2Id);
        }

        int getResultId() {
            return resultId;
        }

        public TypeState getResult() {
            return PointsToStats.idToState.get(resultId);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof UnionOperation) {
                UnionOperation other = (UnionOperation) obj;
                return this.state1Id == other.state1Id && this.state2Id == other.state2Id && this.resultId == other.resultId;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return 31 ^ state1Id ^ state2Id ^ resultId;
        }
    }

    private static String asString(TypeFlow<?> flow) {

        if (flow instanceof AllInstantiatedTypeFlow) {
            return "AllInstantiated(" + formatType(flow.getDeclaredType(), true) + ")";
        } else if (flow instanceof AllSynchronizedTypeFlow) {
            return "AllSynchronized";
        } else if (flow instanceof ContextInsensitiveFieldTypeFlow) {
            ContextInsensitiveFieldTypeFlow sink = (ContextInsensitiveFieldTypeFlow) flow;
            return "FieldSink(" + formatField(sink.getSource()) + ")";
        } else if (flow instanceof FieldTypeFlow) {
            FieldTypeFlow fieldFlow = (FieldTypeFlow) flow;
            AnalysisField field = fieldFlow.getSource();
            return (field.isStatic() ? "StaticField" : "InstanceField") + "(" + formatField(field) + ")";
        } else if (flow instanceof StoreInstanceFieldTypeFlow) {
            StoreInstanceFieldTypeFlow store = (StoreInstanceFieldTypeFlow) flow;
            return "InstanceStore(" + formatField(store.field()) + ")@" + formatSource(flow);
        } else if (flow instanceof StoreStaticFieldTypeFlow) {
            StoreStaticFieldTypeFlow store = (StoreStaticFieldTypeFlow) flow;
            return "StaticStore(" + formatField(store.field()) + ")@" + formatSource(flow);
        } else if (flow instanceof LoadInstanceFieldTypeFlow) {
            LoadInstanceFieldTypeFlow load = (LoadInstanceFieldTypeFlow) flow;
            return "InstanceLoad(" + formatField(load.field()) + ")@" + formatSource(flow);
        } else if (flow instanceof LoadStaticFieldTypeFlow) {
            LoadStaticFieldTypeFlow load = (LoadStaticFieldTypeFlow) flow;
            return "StaticLoad(" + formatField(load.field()) + ")@" + formatSource(flow);
        } else if (flow instanceof StoreIndexedTypeFlow) {
            return "IndexedStore @ " + formatSource(flow);
        } else if (flow instanceof UnsafeStoreTypeFlow) {
            return "UnsafeStore @ " + formatSource(flow);
        } else if (flow instanceof LoadIndexedTypeFlow) {
            return "IndexedLoad @ " + formatSource(flow);
        } else if (flow instanceof UnsafeLoadTypeFlow) {
            return "UnsafeLoad @ " + formatSource(flow);
        } else if (flow instanceof ArrayElementsTypeFlow) {
            ArrayElementsTypeFlow arrayFlow = (ArrayElementsTypeFlow) flow;
            return "ArrayElements(" + (arrayFlow.object() != null ? arrayFlow.object().type().toJavaName(false) : "?") + ")";
        } else if (flow instanceof NullCheckTypeFlow) {
            NullCheckTypeFlow nullCheck = (NullCheckTypeFlow) flow;
            return "NullCheck(" + (nullCheck.isBlockingNull() ? "not-null" : "only-null") + ")@" + formatSource(flow);
        } else if (flow instanceof FilterTypeFlow) {
            FilterTypeFlow filter = (FilterTypeFlow) flow;
            String properties = filter.isExact() ? "exact" : "not-exact";
            properties += ", " + (filter.isAssignable() ? "assignable" : "not-assignable");
            properties += ", " + (filter.includeNull() ? "include-null" : "not-include-null");
            return "Filter(" + properties + ", " + formatType(filter.getDeclaredType(), true) + ")@" + formatSource(flow);
        } else if (flow instanceof FieldFilterTypeFlow) {
            FieldFilterTypeFlow filter = (FieldFilterTypeFlow) flow;
            return "FieldFilter(" + formatField(filter.getSource()) + ")";
        } else if (flow instanceof NewInstanceTypeFlow) {
            return "NewInstance(" + flow.getDeclaredType().toJavaName(false) + ")@" + formatSource(flow);
        } else if (flow instanceof DynamicNewInstanceTypeFlow) {
            return "DynamicNewInstance @ " + formatSource(flow);
        } else if (flow instanceof InvokeTypeFlow) {
            InvokeTypeFlow invoke = (InvokeTypeFlow) flow;
            return "Invoke(" + formatMethod(invoke.getTargetMethod()) + ")@" + formatSource(flow);
        } else if (flow instanceof FormalParamTypeFlow) {
            FormalParamTypeFlow param = (FormalParamTypeFlow) flow;
            return "Parameter(" + param.position() + ")@" + formatMethod(param.method());
        } else if (flow instanceof FormalReturnTypeFlow) {
            return "Return @ " + formatSource(flow);
        } else if (flow instanceof ActualReturnTypeFlow) {
            ActualReturnTypeFlow ret = (ActualReturnTypeFlow) flow;
            InvokeTypeFlow invoke = ret.invokeFlow();
            return "ActualReturn(" + (invoke == null ? "null" : formatMethod(invoke.getTargetMethod())) + ")@ " + formatSource(flow);
        } else if (flow instanceof MergeTypeFlow) {
            return "Merge @ " + formatSource(flow);
        } else if (flow instanceof SourceTypeFlow) {
            return "Source @ " + formatSource(flow);
        } else if (flow instanceof CloneTypeFlow) {
            return "Clone @ " + formatSource(flow);
        } else {
            return ClassUtil.getUnqualifiedName(flow.getClass()) + "@" + formatSource(flow);
        }
    }

    private static String formatSource(TypeFlow<?> flow) {
        Object source = flow.getSource();
        if (source instanceof BytecodePosition) {
            BytecodePosition nodeSource = (BytecodePosition) source;
            return formatMethod(nodeSource.getMethod()) + ":" + nodeSource.getBCI();
        } else if (source instanceof AnalysisType) {
            return formatType((AnalysisType) source);
        } else if (source instanceof AnalysisField) {
            return formatField((AnalysisField) source);
        } else if (flow.graphRef() != null) {
            return formatMethod(flow.graphRef().getMethod());
        } else if (source == null) {
            return "<no-source>";
        } else {
            return ClassUtil.getUnqualifiedName(source.getClass());
        }
    }

    private static String formatMethod(ResolvedJavaMethod method) {
        return method.format("%H.%n(%p)");
    }

    private static String formatField(AnalysisField field) {
        return field.format("%H.%n");
    }

    private static String formatType(AnalysisType type) {
        return formatType(type, false);
    }

    private static String formatType(AnalysisType type, boolean qualified) {
        return type != null ? type.toJavaName(qualified) : "null";
    }

    @SuppressWarnings("unused")
    private static String asDetailedString(BigBang bb, TypeState s) {
        if (s.isEmpty()) {
            return "<Empty>";
        }
        if (s.isNull()) {
            return "<Null>";
        }

        String canBeNull = s.canBeNull() ? "null" : "!null";
        String types = s.typesStream(bb).map(JavaType::getUnqualifiedName).sorted().collect(Collectors.joining(", "));

        return canBeNull + ", " + types;
    }

    // type state string representation
    public static String asString(TypeState s) {
        if (s.isEmpty()) {
            return "<Empty>";
        }
        if (s.isNull()) {
            return "<Null>";
        }
        if (s.isPrimitive()) {
            return switch (s) {
                case AnyPrimitiveTypeState ignored -> "<AnyInt>";
                case PrimitiveConstantTypeState constant -> "<Int:" + constant.getValue() + ">";
                default -> throw AnalysisError.shouldNotReachHere("Unknown primitive type state: " + s.getClass());
            };
        }

        String sKind = s.isAllocation() ? "Alloc" : s.asConstant() != null ? "Const" : s instanceof SingleTypeState ? "Single" : s instanceof MultiTypeState ? "Multi" : "";
        String sSizeOrType = s instanceof MultiTypeState ? s.typesCount() + "" : s.exactType().toJavaName(false);
        int objectsNumber = s.objectsCount();
        String canBeNull = s.canBeNull() ? "null" : "!null";

        return "<" + sKind + "," + canBeNull + ",T:" + sSizeOrType + ",O:" + objectsNumber + ">";
    }

    /**
     * Wrapper for BufferedWriter.out to deal with checked exception. Useful for avoiding catching
     * exceptions in lamdas.
     *
     * @param out the writer
     * @param str the string to write
     */
    private static void doWrite(BufferedWriter out, String str) {
        try {
            out.write(str);
        } catch (IOException ex) {
            throw JVMCIError.shouldNotReachHere(ex);
        }
    }

}
