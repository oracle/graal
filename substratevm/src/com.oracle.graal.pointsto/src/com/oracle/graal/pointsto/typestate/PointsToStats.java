/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.ValueNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.AllInstantiatedTypeFlow;
import com.oracle.graal.pointsto.flow.AllSynchronizedTypeFlow;
import com.oracle.graal.pointsto.flow.ArrayElementsTypeFlow;
import com.oracle.graal.pointsto.flow.CloneTypeFlow;
import com.oracle.graal.pointsto.flow.DynamicNewInstanceTypeFlow;
import com.oracle.graal.pointsto.flow.FieldFilterTypeFlow;
import com.oracle.graal.pointsto.flow.FieldSinkTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.FilterTypeFlow;
import com.oracle.graal.pointsto.flow.FormalParamTypeFlow;
import com.oracle.graal.pointsto.flow.FormalReturnTypeFlow;
import com.oracle.graal.pointsto.flow.FrozenFieldFilterTypeFlow;
import com.oracle.graal.pointsto.flow.InitialParamTypeFlow;
import com.oracle.graal.pointsto.flow.InstanceOfTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.LoadFieldTypeFlow.LoadInstanceFieldTypeFlow;
import com.oracle.graal.pointsto.flow.LoadFieldTypeFlow.LoadStaticFieldTypeFlow;
import com.oracle.graal.pointsto.flow.MergeTypeFlow;
import com.oracle.graal.pointsto.flow.MonitorEnterTypeFlow;
import com.oracle.graal.pointsto.flow.NewInstanceTypeFlow;
import com.oracle.graal.pointsto.flow.NullCheckTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.AtomicReadTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.JavaReadTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.LoadIndexedTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.UnsafeLoadTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.UnsafePartitionLoadTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.AtomicWriteTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.CompareAndSwapTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.JavaWriteTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.StoreIndexedTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.UnsafePartitionStoreTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.UnsafeStoreTypeFlow;
import com.oracle.graal.pointsto.flow.SourceTypeFlow;
import com.oracle.graal.pointsto.flow.StoreFieldTypeFlow.StoreInstanceFieldTypeFlow;
import com.oracle.graal.pointsto.flow.StoreFieldTypeFlow.StoreStaticFieldTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.UnknownTypeFlow;
import com.oracle.graal.pointsto.flow.UnsafeWriteSinkTypeFlow;
import com.oracle.graal.pointsto.flow.builder.TypeFlowBuilder;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class PointsToStats {

    static boolean reportStatistics;

    public static void init(BigBang bb) {
        registerTypeState(bb, EmptyTypeState.SINGLETON);
        registerTypeState(bb, NullTypeState.SINGLETON);
        registerTypeState(bb, UnknownTypeState.SINGLETON);
        reportStatistics = bb.reportAnalysisStatistics();
    }

    public static void report(@SuppressWarnings("unused") BigBang bb, String reportNameRoot) {

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
            String timeStamp = LocalDateTime.now().format(formatter);
            Path statsDirectory = Files.createDirectories(FileSystems.getDefault().getPath("svmbuild").resolve("stats"));

            doReport(statsDirectory, reportNameRoot, "type state stats", timeStamp, PointsToStats::reportTypeStateStats);
            doReport(statsDirectory, reportNameRoot, "union operation stats", timeStamp, PointsToStats::reportUnionOpertationsStats);
            doReport(statsDirectory, reportNameRoot, "type flow stats", timeStamp, PointsToStats::reportTypeFlowStats);
            doReport(statsDirectory, reportNameRoot, "pruned type flow stats", timeStamp, PointsToStats::reportPrunedTypeFlows);

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

    private static List<TypeFlowBuilder<?>> typeFlowBuilders = new ArrayList<>();

    public static void registerTypeFlowBuilder(BigBang bb, TypeFlowBuilder<?> builder) {
        if (!bb.reportAnalysisStatistics()) {
            return;
        }

        typeFlowBuilders.add(builder);
    }

    private static void reportPrunedTypeFlows(BufferedWriter out) {

        doWrite(out, String.format("%-35s\n", "Summary"));
        doWrite(out, String.format("%-35s\t%-10s\n", "Type Flow Class", "Removed Count"));

        typeFlowBuilders.stream().filter(Objects::nonNull).filter(b -> !b.isMaterialized()).collect(Collectors.groupingBy(TypeFlowBuilder::getFlowClass)).forEach((flowClass, providers) -> {
            doWrite(out, String.format("%-35s\t%-10d\n",
                            flowClass.getSimpleName(), providers.size()));
        });

        doWrite(out, String.format("\n%-35s\n", "Removed flows"));
        doWrite(out, String.format("%-35s\t%-10s\n", "Type Flow Class", "Location"));

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
                sourceStr = source.toString();
            }
            doWrite(out, String.format("%-35s\t%-10s\n",
                            provider.getFlowClass().getSimpleName(), sourceStr));
        });

    }

    static class TypeFlowStats {
        static final Comparator<TypeFlowStats> totalUpdatesCountComparator = Comparator.comparingInt(TypeFlowStats::allUpdatesCount);

        /* Reason why this state was not removed during graph pruning. */
        String retainReason;

        final TypeFlow<?> flow;

        final ArrayList<TypeState> allUpdates;
        final ArrayList<TypeState> successfulUpdates;
        final AtomicInteger queuedUpdates;

        TypeFlowStats(TypeFlow<?> flow) {
            this.retainReason = "";
            this.flow = flow;
            this.allUpdates = new ArrayList<>();
            this.successfulUpdates = new ArrayList<>();
            this.queuedUpdates = new AtomicInteger(0);
        }

        public void setRetainReason(String retainReason) {
            this.retainReason = retainReason;
        }

        public String getRetainReason() {
            return retainReason;
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

    public static void registerTypeFlowRetainReason(BigBang bb, TypeFlow<?> flow, String reason) {
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

    public static void registerTypeFlowUpdate(BigBang bb, TypeFlow<?> flow, TypeState state) {
        if (!bb.reportAnalysisStatistics()) {
            return;
        }

        if (state.isUnknown() || state.isEmpty()) {
            return;
        }

        TypeFlowStats stats = typeFlowStats.computeIfAbsent(flow, TypeFlowStats::new);
        stats.registerUpdate(state);
    }

    public static void registerTypeFlowSuccessfulUpdate(BigBang bb, TypeFlow<?> flow, TypeState state) {
        if (!bb.reportAnalysisStatistics()) {
            return;
        }

        if (state.isUnknown() || state.isEmpty()) {
            return;
        }

        TypeFlowStats stats = typeFlowStats.computeIfAbsent(flow, TypeFlowStats::new);
        stats.registerSuccessfulUpdate(state);
    }

    public static void registerTypeFlowQueuedUpdate(BigBang bb, TypeFlow<?> flow) {
        if (!bb.reportAnalysisStatistics()) {
            return;
        }

        TypeFlowStats stats = typeFlowStats.computeIfAbsent(flow, TypeFlowStats::new);
        stats.registerQueuedUpdate();
    }

    static final Comparator<Long> longComparator = Comparator.naturalOrder();

    private static void reportTypeFlowStats(BufferedWriter out) {

        doWrite(out, String.format("%-35s\t%-10s\t%-10s\t%-10s\t%-10s\t%-10s\t%-10s\t%-10s\t%-10s\t%-10s\t%-10s\t%-10s\t%10s\n",
                        "TypeFlow", "TypeStateID", "StateObjects#", "CanBeNull", "IsClone", "Uses", "Observers", "Uses+Observers",
                        "RetainReason", "QueuedUpdates", "AllUpdates", "TypeStateAdds", "All Updates History (<update frequency>x<type state id>)"));

        typeFlowStats.entrySet().stream()
                        .forEach(e -> {
                            TypeFlow<?> flow = e.getKey();
                            TypeFlowStats stats = e.getValue();

                            doWrite(out, String.format("%-35s\t%-10d\t%-10d\t%-10b\t%-10b\t%-10d\t%-10d\t%-10d\t%-10s\t%-10d\t%10d\t%10d\t%10s\n",
                                            asString(flow), stateToId.get(flow.getState()), objectsCount(flow.getState()),
                                            flow.getState().canBeNull(), flow.isClone(),
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

    static void registerTypeState(BigBang bb, TypeState state) {

        if (!bb.reportAnalysisStatistics()) {
            return;
        }

        Integer id = stateToId.computeIfAbsent(state, (s) -> nextStateId.incrementAndGet());
        TypeState actualState = idToState.computeIfAbsent(id, (i) -> state);

        typeStateStats.computeIfAbsent(actualState, (s) -> new AtomicInteger()).incrementAndGet();
    }

    private static int objectsCount(TypeState state) {
        if (state == UnknownTypeState.SINGLETON) {
            return 0;
        }
        return state.objectsCount();
    }

    private static int typesCount(TypeState state) {
        if (state == UnknownTypeState.SINGLETON) {
            return 0;
        }
        return state.typesCount();
    }

    private static void reportTypeStateStats(BufferedWriter out) {

        doWrite(out, String.format("%10s\t%10s\t%10s\t%10s\t%10s\n", "Id", "Frequency", "Types#", "Object#", "Types"));

        typeStateStats.entrySet().stream()
                        .sorted(Entry.comparingByValue(atomicIntegerComparator.reversed()))
                        .forEach(entry -> {
                            TypeState s = entry.getKey();
                            int frequency = entry.getValue().intValue();

                            doWrite(out, String.format("%10d\t%10d\t%10d\t%10d\t%10s\n",
                                            stateToId.get(s), frequency, typesCount(s), objectsCount(s), asString(s)));
                        });
    }

    // union operations frequency

    private static ConcurrentHashMap<UnionOperation, AtomicInteger> unionStats = new ConcurrentHashMap<>();

    static void registerUnionOperation(BigBang bb, TypeState s1, TypeState s2, TypeState result) {

        if (!bb.reportAnalysisStatistics()) {
            return;
        }

        assert typeStateStats.containsKey(s1) && typeStateStats.containsKey(s2) && typeStateStats.containsKey(result);

        UnionOperation union = new UnionOperation(s1, s2, result);
        AtomicInteger counter = unionStats.computeIfAbsent(union, (k) -> new AtomicInteger());
        counter.incrementAndGet();
    }

    private static void reportUnionOpertationsStats(BufferedWriter out) {

        doWrite(out, String.format("%10s + %10s = %10s\t%10s\n", "State1ID", "State2ID", "ResultID", "Frequency"));
        unionStats.entrySet().stream()
                        .filter(e -> e.getValue().intValue() > 1)
                        .sorted(Entry.comparingByValue(atomicIntegerComparator.reversed()))
                        .forEach(entry -> {
                            UnionOperation union = entry.getKey();
                            Integer frequency = entry.getValue().intValue();

                            doWrite(out, String.format("%10d + %10d = %10d\t%10d\t%10s + %10s = %10s\n",
                                            union.getState1Id(), union.getState2Id(), union.getResultId(),
                                            frequency, asString(union.getState1()), asString(union.getState2()), asString(union.getResult())));
                        });
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

    static class FilterOperation {
        int filterId;
        int inputId;
        int resultId;

        FilterOperation(int filterId, int inputId, int resultId) {
            this.filterId = filterId;
            this.inputId = inputId;
            this.resultId = resultId;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof FilterOperation) {
                FilterOperation other = (FilterOperation) obj;
                return this.filterId == other.filterId && this.inputId == other.inputId && this.resultId == other.resultId;
            }
            return false;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + filterId;
            result = prime * result + inputId;
            result = prime * result + resultId;
            return result;
        }
    }

    private static String asString(TypeFlow<?> flow) {

        if (flow instanceof AllInstantiatedTypeFlow) {
            return "AllInstantiated(" + formatType(flow.getDeclaredType(), true) + ")";
        } else if (flow instanceof AllSynchronizedTypeFlow) {
            return "AllSynchronized";
        } else if (flow instanceof UnknownTypeFlow) {
            return "Unknown";
        } else if (flow instanceof FieldSinkTypeFlow) {
            FieldSinkTypeFlow sink = (FieldSinkTypeFlow) flow;
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
        } else if (flow instanceof UnsafePartitionStoreTypeFlow) {
            return "UnsafePartitionStore @ " + formatSource(flow);
        } else if (flow instanceof UnsafeWriteSinkTypeFlow) {
            UnsafeWriteSinkTypeFlow sink = (UnsafeWriteSinkTypeFlow) flow;
            return "UnsafeWriteSink(" + formatField(sink.getSource()) + ")";
        } else if (flow instanceof JavaWriteTypeFlow) {
            return "JavaWrite @ " + formatSource(flow);
        } else if (flow instanceof AtomicWriteTypeFlow) {
            return "AtomicWrite @ " + formatSource(flow);
        } else if (flow instanceof CompareAndSwapTypeFlow) {
            return "CompareAndSwap @ " + formatSource(flow);
        } else if (flow instanceof LoadIndexedTypeFlow) {
            return "IndexedLoad @ " + formatSource(flow);
        } else if (flow instanceof UnsafeLoadTypeFlow) {
            return "UnsafeLoad @ " + formatSource(flow);
        } else if (flow instanceof UnsafePartitionLoadTypeFlow) {
            return "UnsafePartitionLoad @ " + formatSource(flow);
        } else if (flow instanceof JavaReadTypeFlow) {
            return "JavaRead @ " + formatSource(flow);
        } else if (flow instanceof AtomicReadTypeFlow) {
            return "AtomicRead @ " + formatSource(flow);
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
        } else if (flow instanceof FrozenFieldFilterTypeFlow) {
            FrozenFieldFilterTypeFlow filter = (FrozenFieldFilterTypeFlow) flow;
            return "FrozenFieldFilter(" + formatField(filter.getSource()) + ")";
        } else if (flow instanceof InstanceOfTypeFlow) {
            InstanceOfTypeFlow instanceOf = (InstanceOfTypeFlow) flow;
            return "InstanceOf(" + formatType(instanceOf.getDeclaredType(), true) + ")@" + formatSource(flow);
        } else if (flow instanceof NewInstanceTypeFlow) {
            return "NewInstance(" + flow.getDeclaredType().toJavaName(false) + ")@" + formatSource(flow);
        } else if (flow instanceof DynamicNewInstanceTypeFlow) {
            return "DynamicNewInstance @ " + formatSource(flow);
        } else if (flow instanceof InvokeTypeFlow) {
            InvokeTypeFlow invoke = (InvokeTypeFlow) flow;
            return "Invoke(" + formatMethod(invoke.getTargetMethod()) + ")@" + formatSource(flow);
        } else if (flow instanceof InitialParamTypeFlow) {
            InitialParamTypeFlow param = (InitialParamTypeFlow) flow;
            return "InitialParam(" + param.position() + ")@" + formatMethod(param.method());
        } else if (flow instanceof FormalParamTypeFlow) {
            FormalParamTypeFlow param = (FormalParamTypeFlow) flow;
            return "Parameter(" + param.position() + ")@" + formatMethod(param.method());
        } else if (flow instanceof FormalReturnTypeFlow) {
            return "Return @ " + formatSource(flow);
        } else if (flow instanceof ActualReturnTypeFlow) {
            ActualReturnTypeFlow ret = (ActualReturnTypeFlow) flow;
            InvokeTypeFlow invoke = ret.invokeFlow();
            return "ActualReturn(" + formatMethod(invoke.getTargetMethod()) + ")@ " + formatSource(flow);
        } else if (flow instanceof MergeTypeFlow) {
            return "Merge @ " + formatSource(flow);
        } else if (flow instanceof SourceTypeFlow) {
            return "Source @ " + formatSource(flow);
        } else if (flow instanceof CloneTypeFlow) {
            return "Clone @ " + formatSource(flow);
        } else if (flow instanceof MonitorEnterTypeFlow) {
            MonitorEnterTypeFlow monitor = (MonitorEnterTypeFlow) flow;
            return "MonitorEnter @ " + formatMethod(monitor.getMethod());
        } else {
            return flow.getClass().getSimpleName() + "@" + formatSource(flow);
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
            return source.getClass().getSimpleName();
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
        return type.toJavaName(qualified);
    }

    @SuppressWarnings("unused")
    private static String asDetailedString(TypeState s) {
        if (s.isEmpty()) {
            return "<Empty>";
        }
        if (s.isNull()) {
            return "<Null>";
        }

        if (s.isUnknown()) {
            return "<Unknown>";
        }

        String canBeNull = s.canBeNull() ? "null" : "!null";
        String types = s.typesStream().map(JavaType::getUnqualifiedName).sorted().collect(Collectors.joining(", "));

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

        if (s.isUnknown()) {
            return "<Unknown>";
        }

        String sKind = s.isAllocation() ? "Alloc" : s.isConstant() ? "Const" : s.isSingleTypeState() ? "Single" : s.isMultiTypeState() ? "Multi" : "";
        String sSizeOrType = s.isMultiTypeState() ? s.typesCount() + "" : s.exactType().toJavaName(false);
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
