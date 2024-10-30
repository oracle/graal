/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.reports.causality;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.graalvm.collections.Pair;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AccessFieldTypeFlow;
import com.oracle.graal.pointsto.flow.AllInstantiatedTypeFlow;
import com.oracle.graal.pointsto.flow.ArrayElementsTypeFlow;
import com.oracle.graal.pointsto.flow.ConstantTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.FormalParamTypeFlow;
import com.oracle.graal.pointsto.flow.FormalReceiverTypeFlow;
import com.oracle.graal.pointsto.flow.NewInstanceTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow;
import com.oracle.graal.pointsto.flow.SourceTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.causality.facts.Fact;
import com.oracle.graal.pointsto.reports.causality.facts.FactKinds;
import com.oracle.graal.pointsto.typestate.MultiTypeState;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.typestate.TypeStateUtils;
import com.oracle.svm.util.ClassUtil;

import jdk.vm.ci.code.BytecodePosition;

class Graph {
    abstract static class Node implements Comparable<Node> {
        private final String toStringCached;

        Node(String debugStr) {
            toStringCached = debugStr;
        }

        public final String toString() {
            return toStringCached;
        }

        @Override
        public int compareTo(Node o) {
            return toStringCached.compareTo(o.toStringCached);
        }
    }

    static class FlowNode extends Node {
        public final Fact containing;
        public final TypeState filter;

        FlowNode(String debugStr, Fact containing, TypeState filter) {
            super(debugStr);
            this.containing = containing;
            this.filter = filter;
        }

        // If this returns true, the "containing" MethodNode is to be interpreted as a method made
        // reachable through this flow, instead of a method needing to be reachable for this flow to
        // be reachable
        public boolean makesContainingReachable() {
            return false;
        }
    }

    static final class InvocationFlowNode extends FlowNode {
        InvocationFlowNode(Fact invocationTarget, TypeState filter) {
            super("Virtual Invocation Flow Node: " + invocationTarget, invocationTarget, filter);
        }

        @Override
        public boolean makesContainingReachable() {
            return true;
        }
    }

    static class DirectEdge {
        public final Fact from;
        public final Fact to;

        DirectEdge(Fact from, Fact to) {
            assert to != null;
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DirectEdge that = (DirectEdge) o;
            return Objects.equals(from, that.from) && to.equals(that.to);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }

        @Override
        public String toString() {
            return (from == null ? "" : from.toString()) + "->" + to.toString();
        }
    }

    static class HyperEdge {
        public final Fact from1;
        public final Fact from2;
        public final Fact to;

        HyperEdge(Fact from1, Fact from2, Fact to) {
            assert from1 != null;
            assert from2 != null;
            assert to != null;
            this.from1 = from1;
            this.from2 = from2;
            this.to = to;
        }

        @Override
        public int hashCode() {
            return (from1.hashCode() ^ from2.hashCode()) + 31 * to.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            HyperEdge that = (HyperEdge) o;
            return to.equals(that.to) && ((from1.equals(that.from1) && from2.equals(that.from2)) || (from1.equals(that.from2) && from2.equals(that.from1)));
        }

        @Override
        public String toString() {
            return "{" + from1 + "," + from2 + "}" + "->" + to;
        }
    }

    static class FlowEdge {
        public final FlowNode from;
        public final FlowNode to;

        FlowEdge(FlowNode from, FlowNode to) {
            if (to == null) {
                throw new NullPointerException();
            }

            this.from = from;
            this.to = to;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FlowEdge flowEdge = (FlowEdge) o;
            return Objects.equals(from, flowEdge.from) && to.equals(flowEdge.to);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }
    }

    static class RealFlowNode extends FlowNode {
        private final TypeFlow<?> f;

        static String customToString(TypeFlow<?> f) {
            String str = ClassUtil.getUnqualifiedName(f.getClass());

            if (f.getSource() instanceof BytecodePosition pos) {
                StackTraceElement ste = pos.getMethod().asStackTraceElement(pos.getBCI());
                if (ste.getFileName() != null && ste.getLineNumber() > 0) {
                    str += " in " + ste;
                } else {
                    str += pos.getMethod().format(" in %H.%n(%p)");
                }
            }

            String detail = null;

            if (f instanceof ArrayElementsTypeFlow) {
                detail = ((ArrayElementsTypeFlow) f).getSource().toJavaName();
            } else if (f instanceof FieldTypeFlow) {
                detail = ((FieldTypeFlow) f).getSource().format("%H.%n");
            } else if (f instanceof AccessFieldTypeFlow) {
                detail = ((AccessFieldTypeFlow) f).field().format("%H.%n");
            } else if (f instanceof NewInstanceTypeFlow || f instanceof ConstantTypeFlow || f instanceof SourceTypeFlow) {
                detail = f.getDeclaredType().toJavaName();
            } else if (f instanceof OffsetLoadTypeFlow) {
                detail = f.getDeclaredType().toJavaName();
            } else if (f instanceof OffsetStoreTypeFlow) {
                detail = f.getDeclaredType().toJavaName();
            } else if (f instanceof AllInstantiatedTypeFlow) {
                detail = f.getDeclaredType().toJavaName();
            } else if (f instanceof FormalParamTypeFlow && !(f instanceof FormalReceiverTypeFlow)) {
                detail = Integer.toString(((FormalParamTypeFlow) f).position());
            }

            if (detail != null) {
                str += ": " + detail;
            }

            return str;
        }

        static TypeState customFilter(PointsToAnalysis bb, TypeFlow<?> f) {
            if (f instanceof NewInstanceTypeFlow || f instanceof SourceTypeFlow || f instanceof ConstantTypeFlow) {
                return TypeState.forExactType(bb, f.getDeclaredType(), false);
            } else if (f instanceof FormalReceiverTypeFlow) {
                // No saturation happens here, therefore we can use all flowing types as empirical
                // filter
                return f.getState();
            } else {
                return f.filter(bb, bb.getAllInstantiatedTypeFlow().getState());
            }
        }

        RealFlowNode(TypeFlow<?> f, Fact containing, TypeState filter) {
            super(customToString(f), containing, filter);
            this.f = f;
        }

        public static RealFlowNode create(PointsToAnalysis bb, TypeFlow<?> f, Fact containing) {
            return new RealFlowNode(f, containing, customFilter(bb, f));
        }
    }

    private ArrayList<DirectEdge> directEdges = new ArrayList<>();
    private ArrayList<HyperEdge> hyperEdges = new ArrayList<>();
    private HashSet<FlowEdge> interflows = new HashSet<>();

    public void add(DirectEdge e) {
        directEdges.add(e);
    }

    public void add(HyperEdge e) {
        hyperEdges.add(e);
    }

    public void add(FlowEdge e) {
        interflows.add(e);
    }

    private static <T> HashMap<T, Integer> inverse(T[] arr, int startIndex) {
        HashMap<T, Integer> idMap = new HashMap<>();

        int i = startIndex;
        for (T a : arr) {
            idMap.put(a, i);
            i++;
        }

        return idMap;
    }

    private static class SeenTypestates implements Iterable<TypeState> {
        private final ArrayList<TypeState> typestateById = new ArrayList<>();
        private final HashMap<TypeState, Integer> typestateToId = new HashMap<>();

        private int assignId(TypeState s) {
            int size = typestateById.size();
            typestateById.add(s);
            return size;
        }

        public Integer getId(PointsToAnalysis bb, TypeState s) {
            return typestateToId.computeIfAbsent(s.forCanBeNull(bb, true), this::assignId);
        }

        @Override
        public Iterator<TypeState> iterator() {
            return typestateById.iterator();
        }
    }

    private static void collectNodesLeadingSomewhere(
                    BitSet neededNodes,
                    int[][] backwardAdj) {

        Queue<Integer> worklist = new ArrayDeque<>(neededNodes.cardinality());
        neededNodes.stream().forEach(worklist::add);

        while (!worklist.isEmpty()) {
            int u = worklist.poll();
            for (int v : backwardAdj[u]) {
                if (!neededNodes.get(v)) {
                    neededNodes.set(v);
                    worklist.add(v);
                }
            }
        }
    }

    private Set<Fact> collectNodes() {
        HashSet<Fact> nodes = new HashSet<>();

        for (DirectEdge e : directEdges) {
            if (e.from != null) {
                nodes.add(e.from);
            }
            nodes.add(e.to);
        }

        for (HyperEdge e : hyperEdges) {
            nodes.add(e.from1);
            nodes.add(e.from2);
            nodes.add(e.to);
        }

        for (FlowEdge e : interflows) {
            if (e.from != null && e.from.containing != null) {
                nodes.add(e.from.containing);
            }
            if (e.to.containing != null) {
                nodes.add(e.to.containing);
            }
        }
        return nodes;
    }

    private Set<FlowNode> collectFlowNodes() {
        HashSet<FlowNode> flowsNodes = new HashSet<>();
        for (FlowEdge e : interflows) {
            if (e.from != null) {
                flowsNodes.add(e.from);
            }
            flowsNodes.add(e.to);
        }
        return flowsNodes;
    }

    private Set<Object> collectNeededAbstractNodes() {
        Set<Fact> methods = collectNodes();
        Set<FlowNode> typeflows = collectFlowNodes();
        Object[] nodes = Stream.concat(Stream.concat(Stream.of((Fact) null), methods.stream()), typeflows.stream()).toArray();
        HashMap<Object, Integer> nodesInverse = new HashMap<>();
        for (int i = 0; i < nodes.length; i++) {
            nodesInverse.put(nodes[i], i);
        }

        BitSet needed = new BitSet(nodes.length);
        methods.stream().filter(Fact::essential).map(nodesInverse::get).forEach(needed::set);

        collectNodesLeadingSomewhere(needed, makeReverseAdjacency(nodes, typeflows, nodesInverse));

        needed.set(0, false);
        return needed.stream().mapToObj(i -> nodes[i]).collect(Collectors.toSet());
    }

    private int[][] makeReverseAdjacency(Object[] nodes, Set<FlowNode> typeflows, HashMap<Object, Integer> nodesInverse) {
        int[] adjReverseLens = new int[nodes.length];

        Consumer<BiConsumer<Object, Object>> forAllEdges = visitor -> {
            for (FlowEdge e : interflows) {
                visitor.accept(e.from, e.to);
            }
            for (var e : directEdges) {
                visitor.accept(e.from, e.to);
            }
            for (var he : hyperEdges) {
                visitor.accept(he.from1, he.to);
                visitor.accept(he.from2, he.to);
            }
            for (var f : typeflows) {
                if (f.containing != null) {
                    if (f.makesContainingReachable()) {
                        visitor.accept(f, f.containing);
                    } else {
                        visitor.accept(f.containing, f);
                    }
                }
            }
        };

        forAllEdges.accept((from, to) -> {
            adjReverseLens[nodesInverse.get(to)]++;
        });

        int[][] adjReverse = new int[nodes.length][];
        for (int i = 0; i < adjReverse.length; i++) {
            adjReverse[i] = new int[adjReverseLens[i]];
        }

        int[] adjReversePositions = new int[nodes.length];

        forAllEdges.accept((from, to) -> {
            int toIndex = nodesInverse.get(to);
            adjReverse[toIndex][adjReversePositions[toIndex]++] = nodesInverse.get(from);
        });

        return adjReverse;
    }

    private static class FastSubsetChecker {
        private final int nTypes;

        FastSubsetChecker(PointsToAnalysis bb) {
            nTypes = bb.getAllInstantiatedTypeFlow().getState().typesCount();
        }

        public boolean isSubset(TypeState t1, TypeState t2) {
            if (t1.typesCount() == 0 || t2.typesCount() == nTypes) {
                return true;
            }
            if (t1.typesCount() == 1) {
                return t2.containsType(t1.exactType());
            }
            if (t2.typesCount() <= 1) {
                return false;
            }
            var t1m = (MultiTypeState) t1;
            var t2m = (MultiTypeState) t2;
            return TypeStateUtils.isSuperset(t2m.typesBitSet(), t1m.typesBitSet());
        }
    }

    private static void removeUnneededInnerTypeflows(PointsToAnalysis bb, Map<FlowNode, Pair<Set<FlowNode>, Set<FlowNode>>> adj) {
        FastSubsetChecker subsetChecker = new FastSubsetChecker(bb);

        int initialNodeCount = adj.size();
        boolean changed;
        int removed = 0;
        int iterations = 0;

        long t1 = System.currentTimeMillis();

        do {
            changed = false;
            iterations++;
            var nodes = adj.keySet().stream().filter(f -> f != null && !f.makesContainingReachable()).toArray(FlowNode[]::new);
            for (FlowNode f : nodes) {
                var forwardAndBackward = adj.get(f);
                var forward = forwardAndBackward.getLeft();
                var backward = forwardAndBackward.getRight();

                if (forward.size() > 1 && backward.size() > 1) {
                    continue;
                }
                if (!(f.containing == null || forward.stream().allMatch(ff -> ff.containing == f.containing) || backward.stream().allMatch(ff -> ff != null && ff.containing == f.containing))) {
                    continue;
                }
                if (!forward.stream().allMatch(ff -> subsetChecker.isSubset(ff.filter, f.filter))) {
                    continue;
                }

                adj.remove(f);
                for (FlowNode next : forward) {
                    assert (next != f);
                    var nextBackward = adj.get(next).getRight();
                    for (FlowNode prev : backward) {
                        if (prev != f && prev != next) {
                            nextBackward.add(prev);
                        }
                    }
                    nextBackward.remove(f);
                }
                for (FlowNode prev : backward) {
                    assert (prev != f);
                    var prevForward = adj.get(prev).getLeft();
                    for (FlowNode next : forward) {
                        if (next != f && next != prev) {
                            prevForward.add(next);
                        }
                    }
                    prevForward.remove(f);
                }
                removed++;
                changed = true;
            }
        } while (changed && iterations < 10);

        long t2 = System.currentTimeMillis();
        System.err.println("Removed " + removed + " out of " + initialNodeCount + " typeflows in " + iterations + " iterations. Duration: " + (t2 - t1) + " ms");
    }

    private static Map<FlowNode, Pair<Set<FlowNode>, Set<FlowNode>>> edgeListToAdjacency(HashSet<FlowEdge> interflows) {
        Map<FlowNode, Pair<Set<FlowNode>, Set<FlowNode>>> adj = new HashMap<>();
        for (FlowEdge e : interflows) {
            adj.computeIfAbsent(e.from, f -> Pair.create(new HashSet<>(), new HashSet<>())).getLeft().add(e.to);
            adj.computeIfAbsent(e.to, f -> Pair.create(new HashSet<>(), new HashSet<>())).getRight().add(e.from);
        }
        return adj;
    }

    private static HashSet<FlowEdge> adjacencyToEdgeList(Map<FlowNode, Pair<Set<FlowNode>, Set<FlowNode>>> adj) {
        HashSet<FlowEdge> interflows = new HashSet<>();
        for (var pair : adj.entrySet()) {
            var from = pair.getKey();
            for (var to : pair.getValue().getLeft()) {
                interflows.add(new FlowEdge(from, to));
            }
        }
        return interflows;
    }

    private void contractTypeflows(PointsToAnalysis bb) {
        long t1 = System.currentTimeMillis();
        var adj = edgeListToAdjacency(interflows);
        long t2 = System.currentTimeMillis();
        System.err.println("Converted " + interflows.size() + " typeflow edges to adjacency list. Duration: " + (t2 - t1) + " ms");
        removeUnneededInnerTypeflows(bb, adj);
        t1 = System.currentTimeMillis();
        interflows = adjacencyToEdgeList(adj);
        t2 = System.currentTimeMillis();
        System.err.println("Converted " + interflows.size() + " typeflows edges back to edge list. Duration: " + (t2 - t1) + " ms");
    }

    private static <T> Stream<T> filterType(Class<T> type, Stream<?> s) {
        return s.filter(o -> type.isAssignableFrom(o.getClass())).map(type::cast);
    }

    public void export(PointsToAnalysis bb, ReachabilityExport hierarchy, ZipOutputStream zip, boolean exportTypeflowNames) throws java.io.IOException {
        Map<AnalysisType, Integer> typeIdMap = makeDenseTypeIdMap(bb, bb.getAllInstantiatedTypeFlow().getState()::containsType);
        AnalysisType[] typesSorted = getRelevantTypes(bb, typeIdMap);

        var neededAbstractNodes = collectNeededAbstractNodes();
        Fact[] methodsSorted = filterType(Fact.class, neededAbstractNodes.stream())
                        .map(reason -> Pair.create(reason.toString(bb.getMetaAccess()), reason))
                        .sorted(Comparator.comparing(Pair::getLeft))
                        .map(Pair::getRight)
                        .toArray(Fact[]::new);
        var neededFlows = filterType(FlowNode.class, neededAbstractNodes.stream()).collect(Collectors.toSet());
        neededFlows.add(null); // Always needed
        interflows.removeIf(e -> !neededFlows.contains(e.from) || !neededFlows.contains(e.to));
        contractTypeflows(bb);
        var flowsSorted = collectFlowNodes().stream().sorted().toArray(FlowNode[]::new);

        HashMap<Fact, Integer> methodIdMap = inverse(methodsSorted, 1);
        HashMap<FlowNode, Integer> flowIdMap = inverse(flowsSorted, 1);

        if (typesSorted.length > 0xFFFF) {
            throw new RuntimeException("Too many types! CausalityExport can only handle up to 65535.");
        }

        zip.putNextEntry(new ZipEntry("types.txt"));
        writeTypes(zip, typesSorted);

        zip.putNextEntry(new ZipEntry("kinds.txt"));
        writeKinds(zip);

        zip.putNextEntry(new ZipEntry("node_kinds.bin"));
        writeMethodKinds(zip, methodsSorted);

        zip.putNextEntry(new ZipEntry("node_parents.bin"));
        writeNodeParents(zip, methodsSorted, bb.getMetaAccess(), hierarchy);

        zip.putNextEntry(new ZipEntry("direct_invokes.bin"));
        writeDirectEdges(zip, methodIdMap);

        zip.putNextEntry(new ZipEntry("hyper_edges.bin"));
        writeHyperEdges(zip, methodIdMap);

        SeenTypestates typestates = new SeenTypestates();

        zip.putNextEntry(new ZipEntry("interflows.bin"));
        writeInterflows(zip, flowIdMap);

        zip.putNextEntry(new ZipEntry("typeflow_filters.bin"));
        writeTypeflowFilters(bb, zip, flowsSorted, typestates);

        zip.putNextEntry(new ZipEntry("typestates.bin"));
        writeTypestates(bb, zip, typesSorted, typestates, typeIdMap);

        zip.putNextEntry(new ZipEntry("typeflow_methods.bin"));
        writeTypeflowMethods(zip, flowsSorted, methodIdMap);

        if (exportTypeflowNames) {
            zip.putNextEntry(new ZipEntry("methods.txt"));
            writeMethods(bb, zip, methodsSorted);

            zip.putNextEntry(new ZipEntry("typeflows.txt"));
            writeTypeflows(zip, flowsSorted);
        }
    }

    private static void writeTypes(OutputStream out, AnalysisType[] typesSorted) {
        PrintStream w = new PrintStream(out);
        for (AnalysisType type : typesSorted) {
            w.println(type.toJavaName());
        }
    }

    private static void writeKinds(OutputStream out) {
        PrintStream w = new PrintStream(out);
        assert FactKinds.values().length <= 0x100;
        for (var kind : FactKinds.values()) {
            w.println(kind.name);
        }
    }

    private static void writeMethodKinds(OutputStream out, Fact[] methodsSorted) throws IOException {
        for (Fact method : methodsSorted) {
            int kindIndex = method.typeDescriptor().ordinal();
            assert kindIndex <= 0xFF;
            out.write(kindIndex);
        }
    }

    private void writeDirectEdges(OutputStream out, HashMap<Fact, Integer> methodIdMap) throws IOException {
        WritableByteChannel c = Channels.newChannel(out);
        ByteBuffer b = ByteBuffer.allocate(2 * Integer.BYTES);
        b.order(ByteOrder.LITTLE_ENDIAN);

        for (DirectEdge e : directEdges) {
            Integer src = e.from == null ? Integer.valueOf(0) : methodIdMap.get(e.from);
            Integer dst = methodIdMap.get(e.to);

            if (src == null || dst == null) {
                continue;
            }

            b.putInt(src);
            b.putInt(dst);
            b.flip();
            c.write(b);
            b.flip();
        }
    }

    private void writeHyperEdges(OutputStream out, HashMap<Fact, Integer> methodIdMap) throws IOException {
        WritableByteChannel c = Channels.newChannel(out);
        ByteBuffer b = ByteBuffer.allocate(3 * Integer.BYTES);
        b.order(ByteOrder.LITTLE_ENDIAN);

        for (HyperEdge e : hyperEdges) {
            Integer src1 = methodIdMap.get(e.from1);
            Integer src2 = methodIdMap.get(e.from2);
            Integer dst = methodIdMap.get(e.to);

            if (src1 == null || src2 == null || dst == null) {
                continue;
            }

            b.putInt(src1);
            b.putInt(src2);
            b.putInt(dst);
            b.flip();
            c.write(b);
            b.flip();
        }
    }

    private void writeInterflows(OutputStream out, HashMap<FlowNode, Integer> flowIdMap) throws IOException {
        WritableByteChannel c = Channels.newChannel(out);
        ByteBuffer b = ByteBuffer.allocate(2 * Integer.BYTES);
        b.order(ByteOrder.LITTLE_ENDIAN);

        for (FlowEdge e : interflows) {
            Integer fromId = e.from == null ? Integer.valueOf(0) : flowIdMap.get(e.from);
            Integer toId = flowIdMap.get(e.to);

            b.putInt(fromId);
            b.putInt(toId);
            b.flip();
            c.write(b);
            b.flip();
        }
    }

    private static void writeTypeflowFilters(PointsToAnalysis bb, OutputStream out, FlowNode[] flowsSorted, SeenTypestates typestates) throws IOException {
        WritableByteChannel c = Channels.newChannel(out);
        ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
        b.order(ByteOrder.LITTLE_ENDIAN);

        for (FlowNode flow : flowsSorted) {
            int id = typestates.getId(bb, flow.filter);
            b.putInt(id);
            b.flip();
            c.write(b);
            b.flip();
        }
    }

    private static void writeTypestates(PointsToAnalysis bb, ZipOutputStream out, AnalysisType[] typesSorted, SeenTypestates typestates, Map<AnalysisType, Integer> typeIdMap) throws IOException {
        WritableByteChannel c = Channels.newChannel(out);
        int bytesPerTypestate = (typesSorted.length + 7) / 8;

        ByteBuffer zero = ByteBuffer.allocate(bytesPerTypestate);
        ByteBuffer b = ByteBuffer.allocate(bytesPerTypestate);
        b.order(ByteOrder.LITTLE_ENDIAN);

        for (TypeState state : typestates) {
            b.clear();
            zero.clear();

            b.put(zero);

            for (AnalysisType t : state.types(bb)) {
                Integer maybeId = typeIdMap.get(t);
                if (maybeId == null) {
                    continue;
                }
                int id = maybeId;
                int byteIndex = id / 8;
                int bitIndex = id % 8;
                byte old = b.get(byteIndex);
                old |= (byte) (1 << bitIndex);
                b.put(byteIndex, old);
            }

            b.flip();
            c.write(b);
        }
    }

    private static void writeNodeParents(OutputStream out, Fact[] methodsSorted, AnalysisMetaAccess metaAccess, ReachabilityExport export) throws IOException {
        WritableByteChannel c = Channels.newChannel(out);
        ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
        b.order(ByteOrder.LITTLE_ENDIAN);

        for (Fact node : methodsSorted) {
            var hierarchyNode = node.getParent(export, metaAccess);
            int parentId = hierarchyNode == null ? 0 : hierarchyNode.id;
            b.putInt(parentId);
            b.flip();
            c.write(b);
            b.flip();
        }
    }

    private static void writeTypeflowMethods(OutputStream out, FlowNode[] flowsSorted, HashMap<Fact, Integer> methodIdMap) throws IOException {
        WritableByteChannel c = Channels.newChannel(out);
        ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
        b.order(ByteOrder.LITTLE_ENDIAN);

        for (FlowNode f : flowsSorted) {
            int mid = f.containing == null ? 0 : methodIdMap.get(f.containing);

            if (f.makesContainingReachable()) {
                mid |= Integer.MIN_VALUE; // Set MSB
            }

            b.putInt(mid);
            b.flip();
            c.write(b);
            b.flip();
        }
    }

    private static void writeMethods(PointsToAnalysis bb, OutputStream out, Fact[] methodsSorted) {
        PrintStream w = new PrintStream(out);
        for (Fact method : methodsSorted) {
            w.println(method.toString(bb.getMetaAccess()));
        }
    }

    private static void writeTypeflows(OutputStream out, FlowNode[] flowsSorted) {
        PrintStream w = new PrintStream(out);
        for (FlowNode flow : flowsSorted) {
            w.println(flow);
        }
    }

    private static Map<AnalysisType, Integer> makeDenseTypeIdMap(BigBang bb, Predicate<AnalysisType> shouldBeIncluded) {
        ArrayList<AnalysisType> typesInPreorder = new ArrayList<>();

        // Execute inorder-tree-traversal on subclass hierarchy in order to have hierarchy subtrees
        // in one contiguous id range
        Deque<AnalysisType> worklist = new ArrayDeque<>();
        worklist.add(bb.getUniverse().objectType());

        while (!worklist.isEmpty()) {
            AnalysisType u = worklist.pop();

            if (shouldBeIncluded.test(u)) {
                typesInPreorder.add(u);
            }

            for (AnalysisType v : u.getSubTypes()) {
                if (v != u && !v.isInterface()) {
                    worklist.push(v);
                }
            }
        }

        // Add interfaces at the end
        for (AnalysisType t : bb.getAllInstantiatedTypes()) {
            if (shouldBeIncluded.test(t) && t.isInterface()) {
                typesInPreorder.add(t);
            }
        }

        HashMap<AnalysisType, Integer> idMap = new HashMap<>(typesInPreorder.size());

        int newId = 0;
        for (AnalysisType t : typesInPreorder) {
            idMap.put(t, newId);
            newId++;
        }

        return idMap;
    }

    private static AnalysisType[] getRelevantTypes(PointsToAnalysis bb, Map<AnalysisType, Integer> typeIdMap) {
        AnalysisType[] types = new AnalysisType[typeIdMap.size()];
        for (AnalysisType t : bb.getAllInstantiatedTypes()) {
            types[typeIdMap.get(t)] = t;
        }
        return types;
    }
}
