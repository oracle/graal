package com.oracle.graal.pointsto.reports;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.reports.CallTreePrinter.InvokeNode;
import com.oracle.graal.pointsto.reports.CallTreePrinter.MethodNode;
import com.oracle.graal.pointsto.reports.CallTreePrinter.MethodNodeReference;
import com.oracle.graal.pointsto.reports.CallTreePrinter.Node;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CallTreeCypher {

    // TODO temporarily use a system property to try different values
    private static final int BATCH_SIZE = Integer.getInteger("cypher.batch.size", 512);

    private static final String METHOD_INFO_FORMAT = "name:'%n', signature:'" + CallTreePrinter.METHOD_FORMAT + "'";

    private static final AtomicInteger virtualNodeId = new AtomicInteger(-1);

    public static void print(BigBang bigbang, String path, String reportName) {
        // Re-initialize method ids back to 0 to better diagnose disparities
        MethodNode.methodId = 0;

        CallTreePrinter printer = new CallTreePrinter(bigbang);
        printer.buildCallTree();

        // Set virtual node at next available method id
        virtualNodeId.set(MethodNode.methodId);

        ReportUtils.report("call tree cypher in batches of " + BATCH_SIZE, path + File.separatorChar + "reports", "call_tree_" + reportName, "cypher",
                writer -> printCypher(printer.methodToNode, writer));
    }

    private static void printCypher(Map<AnalysisMethod, MethodNode> methodToNode, PrintWriter writer) {
        writer.print(vmEntryPoint());
        printMethodNodes(methodToNode.values(), writer);
        printMethodEdges(methodToNode, writer);
    }

    private static void printMethodNodes(Collection<MethodNode> methods, PrintWriter writer) {
        final Collection<List<MethodNode>> methodsBatches = batched(methods);
        final String unwindMethod = unwindMethod();
        final String script = methodsBatches.stream()
                .map(methodBatch -> methodBatch.stream()
                        .map(method -> String.format("{_id:%d, %s}", method.id, asProperties(method.method.format(METHOD_INFO_FORMAT))))
                        .collect(Collectors.joining(", ", ":param rows => [", "]"))
                )
                .collect(Collectors.joining(unwindMethod, "", unwindMethod));

        writer.print(script);
    }

    private static String asProperties(String properties) {
        return "properties:{" + properties + "}";
    }

    private static void printVirtualNodes(Map<VirtualInvokeId, Integer> virtualNodes, PrintWriter writer) {
        final Collection<List<Map.Entry<VirtualInvokeId, Integer>>> virtualNodesBatches = batched(virtualNodes.entrySet());
        final String unwindMethod = unwindVirtualMethod();
        final String script = virtualNodesBatches.stream()
                .map(virtualNodesBatch -> virtualNodesBatch.stream()
                        .map(virtualNode -> String.format("{_id:%d, %s}", virtualNode.getValue(), virtualMethodProperties(virtualNode.getKey())))
                        .collect(Collectors.joining(", ", ":param rows => [", "]"))
                )
                .collect(Collectors.joining(unwindMethod, "", unwindMethod));
        writer.print(script);
    }

    private static String virtualMethodProperties(VirtualInvokeId virtualNode) {
        final String linkedIndexes = virtualNode.bytecodeIndexes.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("->"));
        String properties = virtualNode.methodInfo + ", bci: '" + linkedIndexes + "'";
        return asProperties(properties);
    }

    private static String vmEntryPoint() {
        return ":begin\n" +
                "CREATE (v:VM {name: 'VM'});\n" +
                ":commit\n\n";
    }

    private static String unwindMethod() {
        return "\n\n:begin\n" +
                "UNWIND $rows as row\n" +
                "CREATE (n:`UNIQUE IMPORT LABEL`{`UNIQUE IMPORT ID`: row._id})\n" +
                "  SET n += row.properties SET n:Method;\n" +
                ":commit\n\n";
    }

    private static String unwindVirtualMethod() {
        return "\n\n:begin\n" +
                "UNWIND $rows as row\n" +
                "CREATE (n:`UNIQUE IMPORT LABEL`{`UNIQUE IMPORT ID`: row._id})\n" +
                "  SET n += row.properties SET n:VirtualMethod;\n" +
                ":commit\n\n";
    }

    private static void printMethodEdges(Map<AnalysisMethod, MethodNode> methodToNode, PrintWriter writer) {
        Map<VirtualInvokeId, Integer> virtualNodes = new HashMap<>();

        Map<Integer, Set<Integer>> directEdges = new HashMap<>();
        Map<Integer, Set<Integer>> virtualEdges = new HashMap<>();
        Map<Integer, Set<Integer>> overridenByEdges = new HashMap<>();

        final Iterator<MethodNode> iterator = methodToNode.values().stream().filter(n -> n.isEntryPoint).iterator();
        while (iterator.hasNext()) {
            final MethodNode node = iterator.next();
            writer.print(entryEdge(node.method.format(CallTreePrinter.METHOD_FORMAT)));
            addMethodEdges(node, directEdges, virtualEdges, overridenByEdges, virtualNodes);
        }

        printVirtualNodes(virtualNodes, writer);
        printEdges("DIRECT", directEdges, writer);
        printEdges("VIRTUAL", virtualEdges, writer);
        printEdges("OVERRIDDEN_BY", overridenByEdges, writer);
    }

    private static String entryEdge(String signature) {
        return "\n\nMATCH (v:VM),(m:Method)\n" +
                "  WHERE v.name = 'VM' AND m.signature = '" + signature + "'\n" +
                "CREATE (v)-[r:ENTRY]->(m);\n\n";
    }

    private static void addMethodEdges(MethodNode methodNode, Map<Integer, Set<Integer>> directEdges, Map<Integer, Set<Integer>> virtualEdges, Map<Integer, Set<Integer>> overridenByEdges, Map<VirtualInvokeId, Integer> virtualNodes) {
        for (InvokeNode invoke : methodNode.invokes) {
            if (invoke.isDirectInvoke) {
                if (invoke.callees.size() > 0) {
                    Node calleeNode = invoke.callees.get(0);
                    addMethodEdge(methodNode.id, calleeNode, directEdges);
                    if (calleeNode instanceof MethodNode) {
                        addMethodEdges((MethodNode) calleeNode, directEdges, virtualEdges, overridenByEdges, virtualNodes);
                    }
                }
            } else {
                final int virtualNodeId = addVirtualNode(invoke, virtualNodes);
                addVirtualMethodEdge(methodNode.id, virtualNodeId, virtualEdges);
                for (Node calleeNode : invoke.callees) {
                    addMethodEdge(virtualNodeId, calleeNode, overridenByEdges);
                    if (calleeNode instanceof MethodNode) {
                        addMethodEdges((MethodNode) calleeNode, directEdges, virtualEdges, overridenByEdges, virtualNodes);
                    }
                }
            }
        }
    }

    private static int addVirtualNode(InvokeNode node, Map<VirtualInvokeId, Integer> virtualNodes) {
        final String methodInfo = node.targetMethod.format(METHOD_INFO_FORMAT);
        final List<Integer> bytecodeIndexes = Stream.of(node.sourceReferences)
                .map(source -> source.bci)
                .collect(Collectors.toList());

        final VirtualInvokeId id = new VirtualInvokeId(methodInfo, bytecodeIndexes);
        return virtualNodes.computeIfAbsent(id, k -> CallTreeCypher.virtualNodeId.getAndIncrement());
    }

    private static void addVirtualMethodEdge(int startId, int endId, Map<Integer, Set<Integer>> edges) {
        Set<Integer> nodeEdges = edges.computeIfAbsent(startId, k -> new HashSet<>());
        nodeEdges.add(endId);
    }

    private static void addMethodEdge(int nodeId, Node calleeNode, Map<Integer, Set<Integer>> edges) {
        Set<Integer> nodeEdges = edges.computeIfAbsent(nodeId, k -> new HashSet<>());
        MethodNode methodNode = calleeNode instanceof MethodNode
                ? (MethodNode) calleeNode
                : ((MethodNodeReference) calleeNode).methodNode;
        nodeEdges.add(methodNode.id);
    }

    private static void printEdges(String edgeName, Map<Integer, Set<Integer>> edges, PrintWriter writer) {
        final Set<Edge> idEdges = edges.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(endId -> new Edge(entry.getKey(), endId)))
                .collect(Collectors.toSet());

        final Collection<List<Edge>> batchedEdges = batched(idEdges);

        final String unwindEdge = edge(edgeName);
        final String script = batchedEdges.stream()
                .map(edgesBatch -> edgesBatch.stream()
                        .map(edge -> String.format("{start: {_id:%d}, end: {_id:%d}}", edge.startId, edge.endId))
                        .collect(Collectors.joining(", ", ":param rows => [", "]"))
                )
                .collect(Collectors.joining(unwindEdge, "", unwindEdge));

        writer.print(script);
    }

    private static String edge(String edgeName) {
        return "\n\n:begin\n" +
                "UNWIND $rows as row\n" +
                "MATCH (start:`UNIQUE IMPORT LABEL`\n" +
                "               {`UNIQUE IMPORT ID`: row.start._id})\n" +
                "MATCH (end:`UNIQUE IMPORT LABEL`\n" +
                "               {`UNIQUE IMPORT ID`: row.end._id})\n" +
                "CREATE (start)-[r:" + edgeName + "]->(end);\n" +
                ":commit\n\n";
    }

    private static <E> Collection<List<E>> batched(Collection<E> methods) {
        final AtomicInteger counter = new AtomicInteger();
        return methods.stream()
                .collect(Collectors.groupingBy(m -> counter.getAndIncrement() / BATCH_SIZE))
                .values();
    }

    private static final class Edge {
        final int startId;
        final int endId;

        private Edge(int startId, int endId) {
            this.startId = startId;
            this.endId = endId;
        }
    }

    private static final class VirtualInvokeId {
        final String methodInfo;
        final List<Integer> bytecodeIndexes;

        private VirtualInvokeId(String methodInfo, List<Integer> bytecodeIndexes) {
            this.methodInfo = methodInfo;
            this.bytecodeIndexes = bytecodeIndexes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VirtualInvokeId that = (VirtualInvokeId) o;
            return methodInfo.equals(that.methodInfo) &&
                    bytecodeIndexes.equals(that.bytecodeIndexes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(methodInfo, bytecodeIndexes);
        }
    }
}
