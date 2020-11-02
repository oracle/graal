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
    private static final int BATCH_SIZE = Integer.getInteger("cypher.batch.size", 256);

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
        writer.print(snippetVM());
        printAll(methodToNode, writer);
    }

    private static String snippetVM() {
        return ":param rows => [{_id:-1, properties:{name:'VM'}}]\n" +
                ":begin\n" +
                "UNWIND $rows as row\n" +
                "CREATE (n:`UNIQUE IMPORT LABEL`{`UNIQUE IMPORT ID`: row._id})\n" +
                "  SET n += row.properties SET n:VM;\n" +
                ":commit\n\n";
    }

    private static void printAll(Map<AnalysisMethod, MethodNode> methodToNode, PrintWriter writer) {
        Set<MethodNode> entryNodes = new HashSet<>();
        Set<MethodNode> nonVirtualNodes = new HashSet<>();
        Map<VirtualInvokeId, Integer> virtualNodes = new HashMap<>();

        Map<Integer, Set<BciEndEdge>> directEdges = new HashMap<>();
        Map<Integer, Set<BciEndEdge>> virtualEdges = new HashMap<>();
        Map<Integer, Set<Integer>> overridenByEdges = new HashMap<>();

        final Iterator<MethodNode> iterator = methodToNode.values().stream().filter(n -> n.isEntryPoint).iterator();
        while (iterator.hasNext()) {
            final MethodNode node = iterator.next();
            entryNodes.add(node);
            walkNodes(node, directEdges, virtualEdges, overridenByEdges, virtualNodes, nonVirtualNodes);
        }

        printNodes(entryNodes, writer);
        printNodes(nonVirtualNodes, writer);
        printVirtualNodes(virtualNodes, writer);

        printEntryEdges(entryNodes, writer);
        printBciEdges("DIRECT", directEdges, writer);
        printBciEdges("VIRTUAL", virtualEdges, writer);
        printNonBciEdges(overridenByEdges, writer);
    }

    private static void walkNodes(MethodNode methodNode, Map<Integer, Set<BciEndEdge>> directEdges, Map<Integer, Set<BciEndEdge>> virtualEdges, Map<Integer, Set<Integer>> overridenByEdges, Map<VirtualInvokeId, Integer> virtualNodes, Set<MethodNode> nonVirtualNodes) {
        for (InvokeNode invoke : methodNode.invokes) {
            if (invoke.isDirectInvoke) {
                if (invoke.callees.size() > 0) {
                    Node calleeNode = invoke.callees.get(0);
                    addDirectEdge(methodNode.id, invoke, calleeNode, directEdges, nonVirtualNodes);
                    if (calleeNode instanceof MethodNode) {
                        walkNodes((MethodNode) calleeNode, directEdges, virtualEdges, overridenByEdges, virtualNodes, nonVirtualNodes);
                    }
                }
            } else {
                final int virtualNodeId = addVirtualNode(invoke, virtualNodes);
                addVirtualMethodEdge(methodNode.id, invoke, virtualNodeId, virtualEdges);
                for (Node calleeNode : invoke.callees) {
                    addOverridenByEdge(virtualNodeId, calleeNode, overridenByEdges, nonVirtualNodes);
                    if (calleeNode instanceof MethodNode) {
                        walkNodes((MethodNode) calleeNode, directEdges, virtualEdges, overridenByEdges, virtualNodes, nonVirtualNodes);
                    }
                }
            }
        }
    }

    private static void printNodes(Set<MethodNode> nodes, PrintWriter writer) {
        final Collection<List<MethodNode>> batches = batched(nodes);
        final String snippet = snippetMethod();
        final String script = batches.stream()
                .map(methodBatch -> methodBatch.stream()
                        .map(method -> String.format("{_id:%d, %s}", method.id, asProperties(method.method.format(METHOD_INFO_FORMAT))))
                        .collect(Collectors.joining(", ", ":param rows => [", "]"))
                )
                .collect(Collectors.joining(snippet, "", snippet));

        writer.print(script);
    }

    private static String snippetMethod() {
        return "\n\n:begin\n" +
                "UNWIND $rows as row\n" +
                "CREATE (n:`UNIQUE IMPORT LABEL`{`UNIQUE IMPORT ID`: row._id})\n" +
                "  SET n += row.properties SET n:Method;\n" +
                ":commit\n\n";
    }

    private static int addVirtualNode(InvokeNode node, Map<VirtualInvokeId, Integer> virtualNodes) {
        final String methodInfo = node.targetMethod.format(METHOD_INFO_FORMAT);
        final List<Integer> bytecodeIndexes = bytecodeIndexes(node);

        final VirtualInvokeId id = new VirtualInvokeId(methodInfo, bytecodeIndexes);
        return virtualNodes.computeIfAbsent(id, k -> CallTreeCypher.virtualNodeId.getAndIncrement());
    }

    private static void addVirtualMethodEdge(int startId, InvokeNode invoke, int endId, Map<Integer, Set<BciEndEdge>> edges) {
        Set<BciEndEdge> nodeEdges = edges.computeIfAbsent(startId, k -> new HashSet<>());
        nodeEdges.add(new BciEndEdge(endId, bytecodeIndexes(invoke)));
    }

    private static void addDirectEdge(int nodeId, InvokeNode invoke, Node calleeNode, Map<Integer, Set<BciEndEdge>> edges, Set<MethodNode> nodes) {
        Set<BciEndEdge> nodeEdges = edges.computeIfAbsent(nodeId, k -> new HashSet<>());
        MethodNode methodNode = calleeNode instanceof MethodNode
                ? (MethodNode) calleeNode
                : ((MethodNodeReference) calleeNode).methodNode;
        nodes.add(methodNode);
        nodeEdges.add(new BciEndEdge(methodNode.id, bytecodeIndexes(invoke)));
    }

    private static List<Integer> bytecodeIndexes(InvokeNode node) {
        return Stream.of(node.sourceReferences)
                .map(source -> source.bci)
                .collect(Collectors.toList());
    }

    private static void printVirtualNodes(Map<VirtualInvokeId, Integer> virtualNodes, PrintWriter writer) {
        final Collection<List<Map.Entry<VirtualInvokeId, Integer>>> virtualNodesBatches = batched(virtualNodes.entrySet());
        final String snippet = snippetMethod();
        final String script = virtualNodesBatches.stream()
                .map(virtualNodesBatch -> virtualNodesBatch.stream()
                        .map(virtualNode -> String.format("{_id:%d, %s}", virtualNode.getValue(), asProperties(virtualNode.getKey().methodInfo)))
                        .collect(Collectors.joining(", ", ":param rows => [", "]"))
                )
                .collect(Collectors.joining(snippet, "", snippet));
        writer.print(script);
    }

    private static void addOverridenByEdge(int nodeId, Node calleeNode, Map<Integer, Set<Integer>> edges, Set<MethodNode> nodes) {
        Set<Integer> nodeEdges = edges.computeIfAbsent(nodeId, k -> new HashSet<>());
        MethodNode methodNode = calleeNode instanceof MethodNode
                ? (MethodNode) calleeNode
                : ((MethodNodeReference) calleeNode).methodNode;
        nodes.add(methodNode);
        nodeEdges.add(methodNode.id);
    }

    private static void printEntryEdges(Set<MethodNode> edges, PrintWriter writer) {
        final Collection<List<MethodNode>> batchedEdges = batched(edges);

        final String snippet = snippetNonBciEdge("ENTRY");
        final String script = batchedEdges.stream()
                .map(edgesBatch -> edgesBatch.stream()
                        .map(edge -> String.format("{start: {_id:%d}, end: {_id:%d}}", -1, edge.id))
                        .collect(Collectors.joining(", ", ":param rows => [", "]"))
                )
                .collect(Collectors.joining(snippet, "", snippet));

        writer.print(script);
    }

    private static String snippetNonBciEdge(String edgeName) {
        return "\n\n:begin\n" +
                "UNWIND $rows as row\n" +
                "MATCH (start:`UNIQUE IMPORT LABEL`\n" +
                "               {`UNIQUE IMPORT ID`: row.start._id})\n" +
                "MATCH (end:`UNIQUE IMPORT LABEL`\n" +
                "               {`UNIQUE IMPORT ID`: row.end._id})\n" +
                "CREATE (start)-[r:" + edgeName + "]->(end);\n" +
                ":commit\n\n";
    }

    private static void printBciEdges(String edgeName, Map<Integer, Set<BciEndEdge>> edges, PrintWriter writer) {
        final Set<BciEdge> idEdges = edges.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(endId -> new BciEdge(entry.getKey(), endId)))
                .collect(Collectors.toSet());

        final Collection<List<BciEdge>> batchedEdges = batched(idEdges);

        final String unwindEdge = snippetBciEdge(edgeName);
        final String script = batchedEdges.stream()
                .map(edgesBatch -> edgesBatch.stream()
                        .map(edge -> String.format("{start: {_id:%d}, end: {_id:%d}, bci: '%s'}", edge.startId, edge.endEdge.id, showBytecodeIndexes(edge.endEdge.bytecodeIndexes)))
                        .collect(Collectors.joining(", ", ":param rows => [", "]"))
                )
                .collect(Collectors.joining(unwindEdge, "", unwindEdge));

        writer.print(script);
    }

    private static String showBytecodeIndexes(List<Integer> bytecodeIndexes) {
        return bytecodeIndexes.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("->"));
    }

    private static String snippetBciEdge(String edgeName) {
        return "\n\n:begin\n" +
                "UNWIND $rows as row\n" +
                "MATCH (start:`UNIQUE IMPORT LABEL`\n" +
                "               {`UNIQUE IMPORT ID`: row.start._id})\n" +
                "MATCH (end:`UNIQUE IMPORT LABEL`\n" +
                "               {`UNIQUE IMPORT ID`: row.end._id})\n" +
                "CREATE (start)-[r:" + edgeName + " { bci: row.bci }]->(end);\n" +
                ":commit\n\n";
    }

    private static void printNonBciEdges(Map<Integer, Set<Integer>> edges, PrintWriter writer) {
        final Set<NonBciEdge> idEdges = edges.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(endId -> new NonBciEdge(entry.getKey(), endId)))
                .collect(Collectors.toSet());

        final Collection<List<NonBciEdge>> batchedEdges = batched(idEdges);

        final String unwindEdge = snippetNonBciEdge("OVERRIDDEN_BY");
        final String script = batchedEdges.stream()
                .map(edgesBatch -> edgesBatch.stream()
                        .map(edge -> String.format("{start: {_id:%d}, end: {_id:%d}}", edge.startId, edge.endId))
                        .collect(Collectors.joining(", ", ":param rows => [", "]"))
                )
                .collect(Collectors.joining(unwindEdge, "", unwindEdge));

        writer.print(script);
    }

    private static String asProperties(String properties) {
        return "properties:{" + properties + "}";
    }

    private static <E> Collection<List<E>> batched(Collection<E> methods) {
        final AtomicInteger counter = new AtomicInteger();
        return methods.stream()
                .collect(Collectors.groupingBy(m -> counter.getAndIncrement() / BATCH_SIZE))
                .values();
    }

    private static final class NonBciEdge {

        final int startId;
        final int endId;

        private NonBciEdge(int startId, int endId) {
            this.startId = startId;
            this.endId = endId;
        }
    }

    private static final class BciEdge {
        final int startId;
        final BciEndEdge endEdge;

        private BciEdge(int startId, BciEndEdge endEdge) {
            this.startId = startId;
            this.endEdge = endEdge;
        }
    }

    private static final class BciEndEdge {
        final int id;
        final List<Integer> bytecodeIndexes;

        private BciEndEdge(int id, List<Integer> bytecodeIndexes) {
            this.id = id;
            this.bytecodeIndexes = bytecodeIndexes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BciEndEdge endEdge = (BciEndEdge) o;
            return id == endEdge.id &&
                    bytecodeIndexes.equals(endEdge.bytecodeIndexes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, bytecodeIndexes);
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
