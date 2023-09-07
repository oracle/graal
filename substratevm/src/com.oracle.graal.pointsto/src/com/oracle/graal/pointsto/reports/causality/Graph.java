package com.oracle.graal.pointsto.reports.causality;

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
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.CausalityExport;
import com.oracle.graal.pointsto.typestate.TypeState;
import org.graalvm.collections.Pair;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Graph {
    static abstract class Node implements Comparable<Node> {
        private final String toStringCached;

        public Node(String debugStr) {
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
        public final CausalityExport.Event containing;
        public final TypeState filter;

        FlowNode(String debugStr, CausalityExport.Event containing, TypeState filter) {
            super(debugStr);
            this.containing = containing;
            this.filter = filter;
        }

        // If this returns true, the "containing" MethodNode is to be interpreted as a method made reachable through this flow,
        // instead of a method needing to be reachable for this flow to be reachable
        public boolean makesContainingReachable() {
            return false;
        }
    }

    static final class InvocationFlowNode extends FlowNode {
        public InvocationFlowNode(CausalityExport.Event invocationTarget, TypeState filter) {
            super("Virtual Invocation Flow Node: " + invocationTarget, invocationTarget, filter);
        }

        @Override
        public boolean makesContainingReachable() {
            return true;
        }
    }

    static class DirectEdge {
        public final CausalityExport.Event from, to;

        DirectEdge(CausalityExport.Event from, CausalityExport.Event to) {
            assert to != null;
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
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
        public final CausalityExport.Event from1, from2, to;

        HyperEdge(CausalityExport.Event from1, CausalityExport.Event from2, CausalityExport.Event to) {
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
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HyperEdge that = (HyperEdge) o;
            return to.equals(that.to) && (
                    (from1.equals(that.from1) && from2.equals(that.from2))
                 || (from1.equals(that.from2) && from2.equals(that.from1))
            );
        }

        @Override
        public String toString() {
            return "{" + from1 + "," + from2 + "}" + "->" + to;
        }
    }

    static class FlowEdge {
        public final FlowNode from, to;

        FlowEdge(FlowNode from, FlowNode to) {
            if (to == null)
                throw new NullPointerException();

            this.from = from;
            this.to = to;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
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
            String str = f.getClass().getSimpleName();

            if (f.method() != null) {
                str += " in " + f.method().getQualifiedName();
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

            if (detail != null)
                str += ": " + detail;

            return str;
        }

        static TypeState customFilter(PointsToAnalysis bb, TypeFlow<?> f) {
            if (f instanceof NewInstanceTypeFlow || f instanceof SourceTypeFlow || f instanceof ConstantTypeFlow) {
                return TypeState.forExactType(bb, f.getDeclaredType(), false);
            } else if (f instanceof FormalReceiverTypeFlow) {
                // No saturation happens here, therefore we can use all flowing types as empirical filter
                return f.getState();
            } else {
                return f.filter(bb, bb.getAllInstantiatedTypeFlow().getState());
            }
        }

        public RealFlowNode(TypeFlow<?> f, CausalityExport.Event containing, TypeState filter) {
            super(customToString(f), containing, filter);
            this.f = f;
        }

        public static RealFlowNode create(PointsToAnalysis bb, TypeFlow<?> f, CausalityExport.Event containing) {
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
        private final ArrayList<TypeState> typestate_by_id = new ArrayList<>();
        private final HashMap<TypeState, Integer> typestate_to_id = new HashMap<>();

        private int assignId(TypeState s) {
            int size = typestate_by_id.size();
            typestate_by_id.add(s);
            return size;
        }

        public Integer getId(PointsToAnalysis bb, TypeState s) {
            return typestate_to_id.computeIfAbsent(s.forCanBeNull(bb, true), this::assignId);
        }

        @Override
        public Iterator<TypeState> iterator() {
            return typestate_by_id.iterator();
        }
    }

    private static void collectNodesLeadingSomewhere(
            BitSet neededNodes,
            int[][] backwardAdj) {

        Queue<Integer> worklist = new ArrayDeque<>(neededNodes.cardinality());
        neededNodes.stream().forEach(worklist::add);

        while(!worklist.isEmpty()) {
            int u = worklist.poll();
            for(int v : backwardAdj[u]) {
                if (!neededNodes.get(v)) {
                    neededNodes.set(v);
                    worklist.add(v);
                }
            }
        }
    }

    private Set<CausalityExport.Event> collectNodes() {
        HashSet<CausalityExport.Event> nodes = new HashSet<>();

        for (DirectEdge e : directEdges) {
            if (e.from != null)
                nodes.add(e.from);
            nodes.add(e.to);
        }

        for (HyperEdge e : hyperEdges) {
            nodes.add(e.from1);
            nodes.add(e.from2);
            nodes.add(e.to);
        }

        for (FlowEdge e : interflows) {
            if (e.from != null && e.from.containing != null)
                nodes.add(e.from.containing);
            if (e.to.containing != null)
                nodes.add(e.to.containing);
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
        Set<CausalityExport.Event> methods = collectNodes();
        Set<FlowNode> typeflows = collectFlowNodes();
        Object[] nodes = Stream.concat(Stream.concat(Stream.of((CausalityExport.Event) null), methods.stream()), typeflows.stream()).toArray();
        HashMap<Object, Integer> nodesInverse = new HashMap<>();
        for (int i = 0; i < nodes.length; i++) {
            nodesInverse.put(nodes[i], i);
        }

        BitSet needed = new BitSet(nodes.length);
        methods.stream().filter(CausalityExport.Event::essential).map(nodesInverse::get).forEach(needed::set);

        {
            int[] adjReverseLens = new int[nodes.length];

            Consumer<BiConsumer<Object, Object>> forAllEdges = visitor -> {
                for (FlowEdge e : interflows)
                    visitor.accept(e.from, e.to);
                for (var e : directEdges)
                    visitor.accept(e.from, e.to);
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
            for (int i = 0; i < adjReverse.length; i++)
                adjReverse[i] = new int[adjReverseLens[i]];

            int[] adjReversePositions = new int[nodes.length];

            forAllEdges.accept((from, to) -> {
                int toIndex = nodesInverse.get(to);
                adjReverse[toIndex][adjReversePositions[toIndex]++] = nodesInverse.get(from);
            });

            collectNodesLeadingSomewhere(needed, adjReverse);
        }

        needed.set(0, false);
        return needed.stream().mapToObj(i -> nodes[i]).collect(Collectors.toSet());
    }

    private static <T> Stream<T> filterType(Class<T> type, Stream<?> s) {
        return (Stream<T>) s.filter(o -> type.isAssignableFrom(o.getClass()));
    }

    public void export(PointsToAnalysis bb, ZipOutputStream zip, boolean exportTypeflowNames) throws java.io.IOException {
        Map<AnalysisType, Integer> typeIdMap = makeDenseTypeIdMap(bb, bb.getAllInstantiatedTypeFlow().getState()::containsType);
        AnalysisType[] typesSorted = getRelevantTypes(bb, typeIdMap);
        CausalityExport.Event[] methodsSorted;
        FlowNode[] flowsSorted;

        {
            var neededAbstractNodes = collectNeededAbstractNodes();
            methodsSorted = filterType(CausalityExport.Event.class, neededAbstractNodes.stream())
                .map(reason -> Pair.create(reason.toString(bb.getMetaAccess()), reason))
                .sorted(Comparator.comparing(Pair::getLeft))
                .map(Pair::getRight)
                .toArray(CausalityExport.Event[]::new);
            flowsSorted = filterType(FlowNode.class, neededAbstractNodes.stream())
                    .sorted()
                    .toArray(FlowNode[]::new);
        }

        HashMap<CausalityExport.Event, Integer> methodIdMap = inverse(methodsSorted, 1);
        HashMap<FlowNode, Integer> flowIdMap = inverse(flowsSorted, 1);

        if(typesSorted.length > 0xFFFF) {
            throw new RuntimeException("Too many types! CausalityExport can only handle up to 65535.");
        }

        zip.putNextEntry(new ZipEntry("types.txt"));
        {
            PrintStream w = new PrintStream(zip);
            for (AnalysisType type : typesSorted) {
                w.println(type.toJavaName());
            }
        }

        zip.putNextEntry(new ZipEntry("methods.txt"));
        {
            PrintStream w = new PrintStream(zip);
            for (CausalityExport.Event method : methodsSorted) {
                w.println(method.toString(bb.getMetaAccess()));
            }
        }

        zip.putNextEntry(new ZipEntry("direct_invokes.bin"));
        {
            WritableByteChannel c = Channels.newChannel(zip);
            ByteBuffer b = ByteBuffer.allocate(2 * Integer.BYTES);
            b.order(ByteOrder.LITTLE_ENDIAN);

            for (DirectEdge e : directEdges) {
                Integer src = e.from == null ? Integer.valueOf(0) : methodIdMap.get(e.from);
                Integer dst = methodIdMap.get(e.to);

                if (src == null || dst == null)
                    continue;

                b.putInt(src);
                b.putInt(dst);
                b.flip();
                c.write(b);
                b.flip();
            }
        }

        zip.putNextEntry(new ZipEntry("hyper_edges.bin"));
        {
            WritableByteChannel c = Channels.newChannel(zip);
            ByteBuffer b = ByteBuffer.allocate(3 * Integer.BYTES);
            b.order(ByteOrder.LITTLE_ENDIAN);

            for (HyperEdge e : hyperEdges) {
                Integer src1 = methodIdMap.get(e.from1);
                Integer src2 = methodIdMap.get(e.from2);
                Integer dst = methodIdMap.get(e.to);

                if (src1 == null || src2 == null || dst == null)
                    continue;

                b.putInt(src1);
                b.putInt(src2);
                b.putInt(dst);
                b.flip();
                c.write(b);
                b.flip();
            }
        }

        SeenTypestates typestates = new SeenTypestates();

        zip.putNextEntry(new ZipEntry("interflows.bin"));
        {
            WritableByteChannel c = Channels.newChannel(zip);
            ByteBuffer b = ByteBuffer.allocate(2 * Integer.BYTES);
            b.order(ByteOrder.LITTLE_ENDIAN);

            for (FlowEdge e : interflows) {
                Integer fromId = e.from == null ? Integer.valueOf(0) : flowIdMap.get(e.from);
                Integer toId = flowIdMap.get(e.to);

                if(fromId == null || toId == null)
                    continue;

                b.putInt(fromId);
                b.putInt(toId);
                b.flip();
                c.write(b);
                b.flip();
            }
        }

        zip.putNextEntry(new ZipEntry("typeflow_filters.bin"));
        {
            WritableByteChannel c = Channels.newChannel(zip);
            ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
            b.order(ByteOrder.LITTLE_ENDIAN);

            for (FlowNode flow : flowsSorted) {
                int typestate_id = typestates.getId(bb, flow.filter);
                b.putInt(typestate_id);
                b.flip();
                c.write(b);
                b.flip();
            }
        }

        zip.putNextEntry(new ZipEntry("typestates.bin"));
        {
            WritableByteChannel c = Channels.newChannel(zip);
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
                    if (maybeId == null)
                        continue;
                    int id = maybeId;
                    int byte_index = id / 8;
                    int bit_index = id % 8;
                    byte old = b.get(byte_index);
                    old |= (byte) (1 << bit_index);
                    b.put(byte_index, old);
                }

                b.flip();
                c.write(b);
            }
        }

        zip.putNextEntry(new ZipEntry("typeflow_methods.bin"));
        {
            WritableByteChannel c = Channels.newChannel(zip);
            ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
            b.order(ByteOrder.LITTLE_ENDIAN);

            for (FlowNode f : flowsSorted) {
                int mid = f.containing == null ? 0 : methodIdMap.get(f.containing);

                if (f.makesContainingReachable())
                    mid |= Integer.MIN_VALUE; // Set MSB

                b.putInt(mid);
                b.flip();
                c.write(b);
                b.flip();
            }
        }

        if(exportTypeflowNames) {
            zip.putNextEntry(new ZipEntry("typeflows.txt"));
            {
                PrintStream w = new PrintStream(zip);
                for (FlowNode flow : flowsSorted) {
                    w.println(flow);
                }
            }
        }
    }


    private static Map<AnalysisType, Integer> makeDenseTypeIdMap(BigBang bb, Predicate<AnalysisType> shouldBeIncluded) {
        ArrayList<AnalysisType> typesInPreorder = new ArrayList<>();

        // Execute inorder-tree-traversal on subclass hierarchy in order to have hierarchy subtrees in one contiguous id range
        Stack<AnalysisType> worklist = new Stack<>();
        worklist.add(bb.getUniverse().objectType());

        while (!worklist.empty()) {
            AnalysisType u = worklist.pop();

            if (shouldBeIncluded.test(u))
                typesInPreorder.add(u);

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

        for (AnalysisType t : bb.getAllInstantiatedTypes())
            types[typeIdMap.get(t)] = t;

        return types;
    }
}
