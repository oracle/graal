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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CallTreeCypher {

    // TODO temporarily use a system property to try different values
    private static final int BATCH_SIZE = Integer.getInteger("cypher.batch.size", 2);

    public static void print(BigBang bigbang, String path, String reportName) {
        // Re-initialize method ids back to 0 to better diagnose disparities
        MethodNode.methodId = 0;

        CallTreePrinter printer = new CallTreePrinter(bigbang);
        printer.buildCallTree();

        ReportUtils.report("call tree cypher", path + File.separatorChar + "reports", "call_tree_" + reportName, "cypher",
                writer -> printCypher(printer.methodToNode, writer));
    }

    private static void printCypher(Map<AnalysisMethod, MethodNode> methodToNode, PrintWriter writer) {
        printMethodNodes(methodToNode.values(), writer);
        printMethodEdges(methodToNode, writer);
    }

    private static void printMethodNodes(Collection<MethodNode> methods, PrintWriter writer) {
        final Collection<List<MethodNode>> methodsBatches = batched(methods);

        writer.print(vmEntryPoint());

        final String unwindMethod = unwindMethod();
        final String script = methodsBatches.stream()
                .map(methodBatch -> methodBatch.stream()
                        .map(method -> String.format("{_id:%d, %s}", method.id, methodProperties(method)))
                        .collect(Collectors.joining(", ", ":param rows => [", "]"))
                )
                .collect(Collectors.joining(unwindMethod, "", unwindMethod));

        writer.print(script);
    }

    private static String methodProperties(MethodNode method) {
        return method.method.format("properties:{name:'%n', signature:'" + CallTreePrinter.METHOD_FORMAT + "'}");
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

    private static void printMethodEdges(Map<AnalysisMethod, MethodNode> methodToNode, PrintWriter writer) {
        Map<Integer, Set<String>> direct = new HashMap<>();
        Map<Integer, Set<String>> virtual = new HashMap<>();
        Map<Integer, Set<Integer>> override = new HashMap<>();
        final Iterator<MethodNode> iterator = methodToNode.values().stream().filter(n -> n.isEntryPoint).iterator();
        while (iterator.hasNext()) {
            final MethodNode node = iterator.next();
            writer.print(entryEdge(node.method.format(CallTreePrinter.METHOD_FORMAT)));
            addMethodEdges(node, direct, virtual, override);
        }

        printDirectEdges(direct, writer);
    }

    private static String entryEdge(String signature) {
        return "\n\nMATCH (v:VM),(m:Method)\n" +
                "  WHERE v.name = 'VM' AND m.signature = '" + signature + "'\n" +
                "CREATE (v)-[r:ENTRY]->(m);\n\n";
    }

    private static void addMethodEdges(MethodNode methodNode, Map<Integer, Set<String>> direct, Map<Integer, Set<String>> virtual, Map<Integer, Set<Integer>> override) {
        for (InvokeNode invoke : methodNode.invokes) {
            if (invoke.isDirectInvoke) {
                if (invoke.callees.size() > 0) {
                    Node calleeNode = invoke.callees.get(0);
                    addMethodEdge(methodNode.id, calleeNode, direct);
                    if (calleeNode instanceof MethodNode) {
                        addMethodEdges((MethodNode) calleeNode, direct, virtual, override);
                    }
                }
            } else {
//                addSignatureEdge(methodNode.id, invoke, virtual);
//                for (Node calleeNode : invoke.callees) {
//                    addIdEdge(methodNode.id, calleeNode, override);
//                    if (calleeNode instanceof MethodNode) {
//                        addMethodEdges((MethodNode) calleeNode, direct, virtual, override);
//                    }
//                }
            }
        }
    }

    private static void addMethodEdge(int nodeId, Node calleeNode, Map<Integer, Set<String>> edges) {
        Set<String> nodeEdges = edges.computeIfAbsent(nodeId, k -> new HashSet<>());
        if (calleeNode instanceof MethodNode) {
            nodeEdges.add(((MethodNode) calleeNode).method.format(CallTreePrinter.METHOD_FORMAT));
        } else {
            nodeEdges.add(((MethodNodeReference) calleeNode).methodNode.method.format(CallTreePrinter.METHOD_FORMAT));
        }
    }

//    private static void addSignatureEdge(int nodeId, InvokeNode invokeNode, Map<Integer, Set<String>> edges) {
//        Set<String> nodeEdges = edges.computeIfAbsent(nodeId, k -> new HashSet<>());
//        nodeEdges.add(invokeNode.formatTarget());
//    }

    private static void printDirectEdges(Map<Integer, Set<String>> directEdges, PrintWriter writer) {
        final Set<Edge> idEdges = directEdges.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(signature -> new Edge(entry.getKey(), signature)))
                .collect(Collectors.toSet());

        final Collection<List<Edge>> idEdgeBatches = batched(idEdges);

        final String unwindEdge = directEdge();
        final String script = idEdgeBatches.stream()
                .map(edges -> edges.stream()
                        .map(edge -> String.format("{start: {_id:%d}, end: {signature:'%s'}}", edge.id, edge.signature))
                        .collect(Collectors.joining(", ", ":param rows => [", "]"))
                )
                .collect(Collectors.joining(unwindEdge, "", unwindEdge));

        writer.print(script);
    }

    private static String directEdge() {
        return "\n\n:begin\n" +
                "UNWIND $rows as row\n" +
                "MATCH (start:`UNIQUE IMPORT LABEL`\n" +
                "               {`UNIQUE IMPORT ID`: row.start._id})\n" +
                "MATCH (end:Method {signature: row.end.signature})\n" +
                "CREATE (start)-[r:DIRECT]->(end);\n" +
                ":commit\n\n";
    }

    private static <E> Collection<List<E>> batched(Collection<E> methods) {
        final AtomicInteger counter = new AtomicInteger();
        return methods.stream()
                .collect(Collectors.groupingBy(m -> counter.getAndIncrement() / BATCH_SIZE))
                .values();
    }

    private static final class Edge {
        final int id;
        final String signature;

        private Edge(int id, String signature) {
            this.id = id;
            this.signature = signature;
        }
    }
}
