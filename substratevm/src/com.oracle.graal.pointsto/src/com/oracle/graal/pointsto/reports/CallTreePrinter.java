/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.reports;

import static com.oracle.graal.pointsto.reports.ReportUtils.CHILD;
import static com.oracle.graal.pointsto.reports.ReportUtils.CONNECTING_INDENT;
import static com.oracle.graal.pointsto.reports.ReportUtils.EMPTY_INDENT;
import static com.oracle.graal.pointsto.reports.ReportUtils.LAST_CHILD;
import static com.oracle.graal.pointsto.reports.ReportUtils.invokeInfoComparator;
import static com.oracle.graal.pointsto.reports.ReportUtils.methodComparator;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;

import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.util.AnalysisError;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class CallTreePrinter {

    public static final Pattern CAMEL_CASE_PATTERN = Pattern.compile(
                    "\\b[a-zA-Z]|[A-Z]|\\.");

    public static void print(BigBang bb, String reportsPath, String reportName) {
        CallTreePrinter printer = new CallTreePrinter(bb);
        printer.buildCallTree();

        AnalysisReportsOptions.CallTreeType optionValue = AnalysisReportsOptions.PrintAnalysisCallTreeType.getValue(bb.getOptions());
        switch (optionValue) {
            case TXT:
                ReportUtils.report("call tree", reportsPath, "call_tree_" + reportName, "txt",
                                printer::printMethods);
                break;
            case CSV:
                printCsvFiles(printer.methodToNode, reportsPath, reportName);
                break;
            default:
                throw AnalysisError.shouldNotReachHere("Unsupported CallTreeType " + optionValue + " used with PrintAnalysisCallTreeType option");
        }
        ReportUtils.report("list of used methods", reportsPath, "used_methods_" + reportName, "txt",
                        printer::printUsedMethods);
        ReportUtils.report("list of used classes", reportsPath, "used_classes_" + reportName, "txt",
                        writer -> printer.printClasses(writer, false));
        ReportUtils.report("list of used packages", reportsPath, "used_packages_" + reportName, "txt",
                        writer -> printer.printClasses(writer, true));
    }

    interface Node {
        String format();
    }

    static class MethodNodeReference implements Node {
        private final MethodNode methodNode;

        MethodNodeReference(MethodNode methodNode) {
            this.methodNode = methodNode;
        }

        @Override
        public String format() {
            return methodNode.method.format(METHOD_FORMAT) + " id-ref=" + methodNode.id;
        }
    }

    static class MethodNode implements Node {
        static int methodId = 0;

        private final int id;
        private final AnalysisMethod method;
        private final List<InvokeNode> invokes;
        private final boolean isEntryPoint;

        MethodNode(AnalysisMethod method) {
            this(method, false);
        }

        MethodNode(AnalysisMethod method, boolean isEntryPoint) {
            this.id = methodId++;
            this.method = method;
            this.invokes = new ArrayList<>();
            this.isEntryPoint = isEntryPoint;
        }

        void addInvoke(InvokeNode invoke) {
            invokes.add(invoke);
        }

        @Override
        public String format() {
            return method.format(METHOD_FORMAT) + " id=" + id;
        }
    }

    static class InvokeNode {
        private final AnalysisMethod targetMethod;
        private final List<Node> callees;
        private final boolean isDirectInvoke;
        private final SourceReference[] sourceReferences;

        InvokeNode(AnalysisMethod targetMethod, boolean isDirectInvoke, SourceReference[] sourceReferences) {
            this.targetMethod = targetMethod;
            this.isDirectInvoke = isDirectInvoke;
            this.sourceReferences = sourceReferences;
            this.callees = new ArrayList<>();
        }

        void addCallee(Node callee) {
            callees.add(callee);
        }

        String formatLocation() {
            return Arrays.stream(sourceReferences).map(s -> String.valueOf(s.bci)).collect(Collectors.joining("->"));
        }

        String formatTarget() {
            return targetMethod.format(METHOD_FORMAT);
        }
    }

    private final BigBang bb;
    private final Map<AnalysisMethod, MethodNode> methodToNode;

    public CallTreePrinter(BigBang bb) {
        this.bb = bb;
        /* Use linked hash map for predictable iteration order. */
        this.methodToNode = new LinkedHashMap<>();
    }

    public void buildCallTree() {

        /* Add all the roots to the tree. */
        bb.getUniverse().getMethods().stream()
                        .filter(m -> m.isRootMethod() && !methodToNode.containsKey(m))
                        .sorted(methodComparator)
                        .forEach(method -> methodToNode.put(method, new MethodNode(method, true)));

        /* Walk the call graph starting from the roots, do a breadth-first tree reduction. */
        ArrayDeque<MethodNode> workList = new ArrayDeque<>();
        workList.addAll(methodToNode.values());

        while (!workList.isEmpty()) {
            MethodNode node = workList.removeFirst();
            /*
             * Process the method: iterate the invokes, for each invoke iterate the callees, if the
             * callee was not already processed add it to the tree and to the work list.
             */
            node.method.getInvokes()
                            .stream()
                            .sorted(invokeInfoComparator)
                            .forEach(invokeInfo -> processInvoke(invokeInfo, node, workList));
        }
    }

    private void processInvoke(InvokeInfo invokeInfo, MethodNode callerNode, Deque<MethodNode> workList) {

        InvokeNode invokeNode = new InvokeNode(invokeInfo.getTargetMethod(), invokeInfo.isDirectInvoke(), sourceReference(invokeInfo.getPosition()));
        callerNode.addInvoke(invokeNode);

        invokeInfo.getCallees().stream().sorted(methodComparator).forEach(callee -> {
            if (methodToNode.containsKey(callee)) {
                MethodNodeReference calleeNode = new MethodNodeReference(methodToNode.get(callee));
                invokeNode.addCallee(calleeNode);
            } else {
                MethodNode calleeNode = new MethodNode(callee);
                invokeNode.addCallee(calleeNode);
                methodToNode.put(callee, calleeNode);
                workList.add(calleeNode);
            }
        });
    }

    static class SourceReference {
        static final SourceReference UNKNOWN_SOURCE_REFERENCE = new SourceReference(-1, null);

        final int bci;
        final StackTraceElement trace;

        SourceReference(int bci, StackTraceElement trace) {
            this.bci = bci;
            this.trace = trace;
        }
    }

    private static SourceReference[] sourceReference(BytecodePosition position) {
        List<SourceReference> sourceReference = new ArrayList<>();
        BytecodePosition state = position;
        while (state != null) {
            sourceReference.add(new SourceReference(state.getBCI(), state.getMethod().asStackTraceElement(state.getBCI())));
            state = state.getCaller();
        }
        return sourceReference.toArray(new SourceReference[sourceReference.size()]);
    }

    private static final String METHOD_FORMAT = "%H.%n(%P):%R";

    private void printMethods(PrintWriter out) {
        out.println("VM Entry Points");
        Iterator<MethodNode> iterator = methodToNode.values().stream().filter(n -> n.isEntryPoint).iterator();
        while (iterator.hasNext()) {
            MethodNode node = iterator.next();
            boolean lastEntryPoint = !iterator.hasNext();
            out.format("%s%s %s %n", lastEntryPoint ? LAST_CHILD : CHILD, "entry", node.format());
            printCallTreeNode(out, lastEntryPoint ? EMPTY_INDENT : CONNECTING_INDENT, node);
        }
        out.println();
    }

    private static void printCallTreeNode(PrintWriter out, String prefix, MethodNode node) {

        for (int invokeIdx = 0; invokeIdx < node.invokes.size(); invokeIdx++) {
            InvokeNode invoke = node.invokes.get(invokeIdx);
            boolean lastInvoke = invokeIdx == node.invokes.size() - 1;
            if (invoke.isDirectInvoke) {
                if (invoke.callees.size() > 0) {
                    Node calleeNode = invoke.callees.get(0);
                    out.format("%s%s%s %s @bci=%s %n", prefix, (lastInvoke ? LAST_CHILD : CHILD),
                                    "directly calls", calleeNode.format(), invoke.formatLocation());
                    if (calleeNode instanceof MethodNode) {
                        printCallTreeNode(out, prefix + (lastInvoke ? EMPTY_INDENT : CONNECTING_INDENT), (MethodNode) calleeNode);
                    }
                }
            } else {
                out.format("%s%s%s %s @bci=%s%n", prefix, (lastInvoke ? LAST_CHILD : CHILD),
                                "virtually calls", invoke.formatTarget(), invoke.formatLocation());
                for (int calleeIdx = 0; calleeIdx < invoke.callees.size(); calleeIdx++) {
                    boolean lastCallee = calleeIdx == invoke.callees.size() - 1;
                    Node calleeNode = invoke.callees.get(calleeIdx);
                    out.format("%s%s%s %s %n", prefix + (lastInvoke ? EMPTY_INDENT : CONNECTING_INDENT), (lastCallee ? LAST_CHILD : CHILD),
                                    "is overridden by", calleeNode.format());
                    if (calleeNode instanceof MethodNode) {
                        printCallTreeNode(out, prefix + (lastInvoke ? EMPTY_INDENT : CONNECTING_INDENT) + (lastCallee ? EMPTY_INDENT : CONNECTING_INDENT), (MethodNode) calleeNode);
                    }
                }
            }
        }
    }

    private void printUsedMethods(PrintWriter out) {
        List<String> methodsList = new ArrayList<>();
        for (ResolvedJavaMethod method : methodToNode.keySet()) {
            methodsList.add(method.format("%H.%n(%p):%r"));
        }
        methodsList.sort(null);
        for (String name : methodsList) {
            out.println(name);
        }
    }

    private void printClasses(PrintWriter out, boolean packageNameOnly) {
        List<String> classList = new ArrayList<>(classesSet(packageNameOnly));
        classList.sort(null);
        for (String name : classList) {
            out.println(name);
        }
    }

    public Set<String> classesSet(boolean packageNameOnly) {
        Set<String> classSet = new HashSet<>();
        for (ResolvedJavaMethod method : methodToNode.keySet()) {
            String name = method.getDeclaringClass().toJavaName(true);
            if (packageNameOnly) {
                name = packagePrefix(name);
                if (name.contains("$$Lambda$")) {
                    /* Also strip synthetic package names added for lambdas. */
                    name = packagePrefix(name);
                }
            }
            classSet.add(name);
        }
        return classSet;
    }

    private static String packagePrefix(String name) {
        int lastDot = name.lastIndexOf('.');
        if (lastDot == -1) {
            return name;
        }
        return name.substring(0, lastDot);
    }

    private static void printCsvFiles(Map<AnalysisMethod, MethodNode> methodToNode, String reportsPath, String reportName) {
        // Set virtual node at next available method id
        final AtomicInteger virtualNodeId = new AtomicInteger(MethodNode.methodId);

        Set<Integer> entryPointIds = new HashSet<>();
        Set<MethodNode> nonVirtualNodes = new HashSet<>();
        Map<List<String>, Integer> virtualNodes = new HashMap<>();

        Map<Integer, Set<BciEndEdge>> directEdges = new HashMap<>();
        Map<Integer, Set<BciEndEdge>> virtualEdges = new HashMap<>();
        Map<Integer, Set<Integer>> overridenByEdges = new HashMap<>();

        final Iterator<MethodNode> iterator = methodToNode.values().stream().filter(n -> n.isEntryPoint).iterator();
        while (iterator.hasNext()) {
            final MethodNode node = iterator.next();
            entryPointIds.add(node.id);
            walkNodes(node, directEdges, virtualEdges, overridenByEdges, virtualNodes, nonVirtualNodes, virtualNodeId);
        }

        String msgPrefix = "call tree csv file for ";
        toCsvFile(msgPrefix + "vm entry point", reportsPath, "call_tree_vm", reportName, CallTreePrinter::printVMEntryPoint);
        toCsvFile(msgPrefix + "methods", reportsPath, "call_tree_methods", reportName, writer -> printMethodNodes(methodToNode.values(), writer));
        toCsvFile(msgPrefix + "virtual methods", reportsPath, "call_tree_virtual_methods", reportName, writer -> printVirtualNodes(virtualNodes, writer));
        toCsvFile(msgPrefix + "entry points", reportsPath, "call_tree_entry_points", reportName, writer -> printEntryPointIds(entryPointIds, writer));
        toCsvFile(msgPrefix + "direct edges", reportsPath, "call_tree_direct_edges", reportName, writer -> printBciEdges(directEdges, writer));
        toCsvFile(msgPrefix + "overriden by edges", reportsPath, "call_tree_override_by_edges", reportName, writer -> printNonBciEdges(overridenByEdges, writer));
        toCsvFile(msgPrefix + "virtual edges", reportsPath, "call_tree_virtual_edges", reportName, writer -> printBciEdges(virtualEdges, writer));
    }

    private static void toCsvFile(String description, String reportsPath, String prefix, String reportName, Consumer<PrintWriter> reporter) {
        final String name = prefix + "_" + reportName;
        final Path csvFile = ReportUtils.report(description, reportsPath, name, "csv", reporter);
        final Path csvLink = Paths.get(reportsPath).resolve(prefix + ".csv");

        if (Files.exists(csvLink, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.delete(csvLink);
            } catch (IOException e) {
                // Ignore
            }
        }

        try {
            Files.createSymbolicLink(csvLink, csvFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void printVMEntryPoint(PrintWriter writer) {
        writer.println(convertToCSV("Id", "Name"));
        writer.println(convertToCSV("0", "VM"));
    }

    private static void printMethodNodes(Collection<MethodNode> methods, PrintWriter writer) {
        writer.println(convertToCSV("Id", "Name", "Type", "Parameters", "Return", "Display"));
        methods.stream()
                        .map(CallTreePrinter::methodNodeInfo)
                        .map(CallTreePrinter::convertToCSV)
                        .forEach(writer::println);
    }

    private static List<String> methodNodeInfo(MethodNode method) {
        return resolvedJavaMethodInfo(method.id, method.method);
    }

    private static void walkNodes(MethodNode methodNode, Map<Integer, Set<BciEndEdge>> directEdges, Map<Integer, Set<BciEndEdge>> virtualEdges, Map<Integer, Set<Integer>> overridenByEdges,
                    Map<List<String>, Integer> virtualNodes, Set<MethodNode> nonVirtualNodes, AtomicInteger virtualNodeId) {
        for (InvokeNode invoke : methodNode.invokes) {
            if (invoke.isDirectInvoke) {
                if (invoke.callees.size() > 0) {
                    Node calleeNode = invoke.callees.get(0);
                    addDirectEdge(methodNode.id, invoke, calleeNode, directEdges, nonVirtualNodes);
                    if (calleeNode instanceof MethodNode) {
                        walkNodes((MethodNode) calleeNode, directEdges, virtualEdges, overridenByEdges, virtualNodes, nonVirtualNodes, virtualNodeId);
                    }
                }
            } else {
                final int nodeId = addVirtualNode(invoke, virtualNodes, virtualNodeId);
                addVirtualMethodEdge(methodNode.id, invoke, nodeId, virtualEdges);
                for (Node calleeNode : invoke.callees) {
                    addOverridenByEdge(nodeId, calleeNode, overridenByEdges, nonVirtualNodes);
                    if (calleeNode instanceof MethodNode) {
                        walkNodes((MethodNode) calleeNode, directEdges, virtualEdges, overridenByEdges, virtualNodes, nonVirtualNodes, virtualNodeId);
                    }
                }
            }
        }
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

    private static int addVirtualNode(InvokeNode node, Map<List<String>, Integer> virtualNodes, AtomicInteger virtualNodeId) {
        final List<String> virtualMethodInfo = virtualMethodInfo(node.targetMethod);
        return virtualNodes.computeIfAbsent(virtualMethodInfo, k -> virtualNodeId.getAndIncrement());
    }

    private static void addVirtualMethodEdge(int startId, InvokeNode invoke, int endId, Map<Integer, Set<BciEndEdge>> edges) {
        Set<BciEndEdge> nodeEdges = edges.computeIfAbsent(startId, k -> new HashSet<>());
        nodeEdges.add(new BciEndEdge(endId, bytecodeIndexes(invoke)));
    }

    private static void printVirtualNodes(Map<List<String>, Integer> virtualNodes, PrintWriter writer) {
        writer.println(convertToCSV("Id", "Name", "Type", "Parameters", "Return", "Display"));
        virtualNodes.entrySet().stream()
                        .map(CallTreePrinter::virtualMethodAndIdInfo)
                        .map(CallTreePrinter::convertToCSV)
                        .forEach(writer::println);
    }

    private static List<String> virtualMethodAndIdInfo(Map.Entry<List<String>, Integer> entry) {
        final List<String> methodInfo = entry.getKey();
        final List<String> result = new ArrayList<>(methodInfo.size() + 1);
        result.add(String.valueOf(entry.getValue()));
        for (int i = 1; i < methodInfo.size(); i++) {
            result.add(i, methodInfo.get(i));
        }
        return result;
    }

    private static void printEntryPointIds(Set<Integer> entryPoints, PrintWriter writer) {
        writer.println(convertToCSV("Id"));
        entryPoints.forEach(writer::println);
    }

    private static void addOverridenByEdge(int nodeId, Node calleeNode, Map<Integer, Set<Integer>> edges, Set<MethodNode> nodes) {
        Set<Integer> nodeEdges = edges.computeIfAbsent(nodeId, k -> new HashSet<>());
        MethodNode methodNode = calleeNode instanceof MethodNode
                        ? (MethodNode) calleeNode
                        : ((MethodNodeReference) calleeNode).methodNode;
        nodes.add(methodNode);
        nodeEdges.add(methodNode.id);
    }

    private static void printBciEdges(Map<Integer, Set<BciEndEdge>> edges, PrintWriter writer) {
        final Set<BciEdge> idEdges = edges.entrySet().stream()
                        .flatMap(entry -> entry.getValue().stream().map(endId -> new BciEdge(entry.getKey(), endId)))
                        .collect(Collectors.toSet());

        writer.println(convertToCSV("StartId", "EndId", "BytecodeIndexes"));
        idEdges.stream()
                        .map(edge -> convertToCSV(String.valueOf(edge.startId), String.valueOf(edge.endEdge.id), showBytecodeIndexes(edge.endEdge.bytecodeIndexes)))
                        .forEach(writer::println);
    }

    private static String showBytecodeIndexes(List<Integer> bytecodeIndexes) {
        return bytecodeIndexes.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining("->"));
    }

    private static void printNonBciEdges(Map<Integer, Set<Integer>> edges, PrintWriter writer) {
        final Set<NonBciEdge> idEdges = edges.entrySet().stream()
                        .flatMap(entry -> entry.getValue().stream().map(endId -> new NonBciEdge(entry.getKey(), endId)))
                        .collect(Collectors.toSet());

        writer.println(convertToCSV("StartId", "EndId"));
        idEdges.stream()
                        .map(edge -> convertToCSV(String.valueOf(edge.startId), String.valueOf(edge.endId)))
                        .forEach(writer::println);
    }

    private static List<String> virtualMethodInfo(AnalysisMethod method) {
        return resolvedJavaMethodInfo(null, method);
    }

    private static List<String> resolvedJavaMethodInfo(Integer id, ResolvedJavaMethod method) {
        // TODO method parameter types are opaque, but could in the future be split out and link
        // together
        // e.g. each method could BELONG to a type, and a method could have PARAMETER relationships
        // with N types
        // see https://neo4j.com/developer/guide-import-csv/#_converting_data_values_with_load_csv
        // for examples
        final String parameters = method.getSignature().getParameterCount(false) > 0
                        ? method.format("%P").replace(",", "")
                        : "empty";

        return Arrays.asList(
                        id == null ? null : Integer.toString(id),
                        method.getName(),
                        method.getDeclaringClass().toJavaName(true),
                        parameters,
                        method.getSignature().getReturnType(null).toJavaName(true),
                        display(method));
    }

    private static String display(ResolvedJavaMethod method) {
        final ResolvedJavaType type = method.getDeclaringClass();
        final String typeName = type.toJavaName(true);
        if (type.getJavaKind() == JavaKind.Object) {
            List<String> matchResults = new ArrayList<>();
            Matcher matcher = CAMEL_CASE_PATTERN.matcher(typeName);
            while (matcher.find()) {
                matchResults.add(matcher.toMatchResult().group());
            }

            return String.join("", matchResults) + "." + method.getName();
        }

        return typeName + "." + method.getName();
    }

    private static String convertToCSV(String... data) {
        return String.join(",", data);
    }

    private static String convertToCSV(List<String> data) {
        return String.join(",", data);
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
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            BciEndEdge endEdge = (BciEndEdge) o;
            return id == endEdge.id &&
                            bytecodeIndexes.equals(endEdge.bytecodeIndexes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, bytecodeIndexes);
        }
    }
}
