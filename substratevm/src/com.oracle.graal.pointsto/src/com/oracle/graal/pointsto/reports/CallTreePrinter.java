/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.graal.compiler.java.LambdaUtils;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaKind;

public final class CallTreePrinter {

    public static final Pattern CAMEL_CASE_PATTERN = Pattern.compile(
                    "\\b[a-zA-Z]|[A-Z]|\\.");
    public static final String METHOD_FORMAT = "%H.%n(%P):%R";

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
            return ReportUtils.loaderName(methodNode.method.getDeclaringClass()) + ':' + methodNode.method.format(METHOD_FORMAT) + " id-ref=" + methodNode.id;
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
            return ReportUtils.loaderName(method.getDeclaringClass()) + ':' + method.format(METHOD_FORMAT) + " id=" + id;
        }
    }

    static class InvokeNode {
        static int invokeId = 0;

        private final int id;
        private final AnalysisMethod targetMethod;
        private final List<Node> callees;
        private final boolean isDirectInvoke;
        private final SourceReference[] sourceReferences;

        InvokeNode(AnalysisMethod targetMethod, boolean isDirectInvoke, SourceReference[] sourceReferences) {
            this.id = invokeId++;
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
        List<AnalysisMethod> roots = AnalysisUniverse.getCallTreeRoots(bb.getUniverse());

        roots.sort(methodComparator);
        for (AnalysisMethod m : roots) {
            methodToNode.put(m, new MethodNode(m, true));
        }

        /*
         * Walk the call graph starting from the roots (deterministically sorted), do a
         * breadth-first tree reduction.
         */
        ArrayDeque<MethodNode> workList = new ArrayDeque<>(methodToNode.values());

        while (!workList.isEmpty()) {
            MethodNode node = workList.removeFirst();
            /*
             * Process the method: iterate the invokes, for each invoke iterate the callees, if the
             * callee was not already processed add it to the tree and to the work list.
             */
            ArrayList<InvokeInfo> invokeInfos = new ArrayList<>();
            for (var invokeInfo : node.method.getInvokes()) {
                invokeInfos.add(invokeInfo);
            }

            /*
             * In order to have deterministic order of invokes we sort them by position and names.
             * In case of Lambda names we avoid the non-deterministic hash part while sorting.
             */
            invokeInfos.sort(invokeInfoComparator);

            for (var invokeInfo : invokeInfos) {
                processInvoke(invokeInfo, node, workList);
            }

        }
    }

    private void processInvoke(InvokeInfo invokeInfo, MethodNode callerNode, Deque<MethodNode> workList) {

        InvokeNode invokeNode = new InvokeNode(invokeInfo.getTargetMethod(), invokeInfo.isDirectInvoke(), sourceReference(invokeInfo.getPosition()));
        callerNode.addInvoke(invokeNode);

        invokeInfo.getAllCallees().stream().sorted(methodComparator).forEach(callee -> {
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

    private void printMethods(PrintWriter out) {
        out.println("VM Entry Points");
        Iterator<MethodNode> iterator = methodToNode.values().stream().filter(n -> n.isEntryPoint).iterator();
        while (iterator.hasNext()) {
            MethodNode node = iterator.next();
            boolean lastEntryPoint = !iterator.hasNext();
            out.format("%s%s %s, parsing reason:  %s %n", lastEntryPoint ? LAST_CHILD : CHILD, "entry", node.format(),
                            PointsToAnalysisMethod.unwrapInvokeReason(node.method.getImplementationInvokedReason()));
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
        for (AnalysisMethod method : methodToNode.keySet()) {
            methodsList.add(ReportUtils.loaderName(method.getDeclaringClass()) + ':' + method.format(METHOD_FORMAT));
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
        for (AnalysisType type : usedAnalysisTypes()) {
            String name = type.toJavaName(true);
            if (packageNameOnly) {
                name = packagePrefix(name);
                if (LambdaUtils.isLambdaClassName(name)) {
                    /* Also strip synthetic package names added for lambdas. */
                    name = packagePrefix(name);
                }
            }
            classSet.add(ReportUtils.loaderName(type) + ':' + name);
        }
        return classSet;
    }

    public Set<AnalysisType> usedAnalysisTypes() {
        Set<AnalysisType> classSet = new HashSet<>();
        for (AnalysisMethod method : methodToNode.keySet()) {
            classSet.add(method.getDeclaringClass());
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
        String msgPrefix = "call tree csv file for ";
        String timeStamp = ReportUtils.getTimeStampString();
        /*
         * We print invokes first, because when traversing the invokes new method nodes (for call
         * targets that were not visited before, e.g. abstract methods) may be created, which we
         * want to print in call_tree_methods as well.
         */
        toCsvFile(msgPrefix + "invokes", reportsPath, "call_tree_invokes", reportName, timeStamp, writer -> printInvokeNodes(methodToNode, writer));
        toCsvFile(msgPrefix + "targets", reportsPath, "call_tree_targets", reportName, timeStamp, writer -> printCallTargets(methodToNode, writer));
        toCsvFile(msgPrefix + "methods", reportsPath, "call_tree_methods", reportName, timeStamp, writer -> printMethodNodes(methodToNode.values(), writer));
    }

    private static void toCsvFile(String description, String reportsPath, String prefix, String reportName, String timeStamp, Consumer<PrintWriter> reporter) {
        final String name = prefix + "_" + reportName;
        final Path csvFile = ReportUtils.report(description, reportsPath, name, "csv", reporter, true, timeStamp);
        final Path csvLink = Paths.get(reportsPath).resolve(prefix + ".csv");

        if (Files.exists(csvLink, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.delete(csvLink);
            } catch (IOException e) {
                // Ignore
            }
        }

        try {
            Files.createSymbolicLink(csvLink, csvFile.getFileName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void printMethodNodes(Collection<MethodNode> methods, PrintWriter writer) {
        writer.println(convertToCSV("Id", "Name", "Type", "Parameters", "Return", "Display", "Flags", "IsEntryPoint"));
        methods.stream()
                        .map(CallTreePrinter::methodNodeInfo)
                        .map(CallTreePrinter::convertToCSV)
                        .forEach(writer::println);
    }

    private static void printInvokeNodes(Map<AnalysisMethod, MethodNode> methodToNode, PrintWriter writer) {
        writer.println(convertToCSV("Id", "MethodId", "BytecodeIndexes", "TargetId", "IsDirect"));
        /*
         * Methods that act as call targets, but are not reachable (e.g. abstract methods), will not
         * have a MethodNode allocated yet. We store them in a separate map, because methodToNode
         * cannot be modified while we iterate over it.
         */
        var callTargets = new HashMap<AnalysisMethod, MethodNode>();
        methodToNode.values().stream()
                        .flatMap(node -> node.invokes.stream()
                                        .map(invoke -> invokeNodeInfo(methodToNode, node, invoke, callTargets)))
                        .map(CallTreePrinter::convertToCSV)
                        .forEach(writer::println);
        for (var entry : callTargets.entrySet()) {
            methodToNode.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private static void printCallTargets(Map<AnalysisMethod, MethodNode> methodToNode, PrintWriter writer) {
        writer.println(convertToCSV("InvokeId", "TargetId"));
        methodToNode.values().stream()
                        .flatMap(node -> node.invokes.stream()
                                        .filter(invoke -> !invoke.callees.isEmpty())
                                        .flatMap(invoke -> invoke.callees.stream()
                                                        .map(callee -> callTargetInfo(invoke, callee))))
                        .map(CallTreePrinter::convertToCSV)
                        .forEach(writer::println);
    }

    private static List<String> methodNodeInfo(MethodNode method) {
        var parameters = method.method.getSignature().getParameterCount(false) > 0
                        ? method.method.format("%P").replace(",", "")
                        : "empty";
        return Arrays.asList(
                        Integer.toString(method.id),
                        method.method.getName(),
                        method.method.getDeclaringClass().toJavaName(true),
                        parameters,
                        method.method.getSignature().getReturnType().toJavaName(true),
                        display(method.method),
                        flags(method.method),
                        String.valueOf(method.isEntryPoint));
    }

    private static List<String> invokeNodeInfo(Map<AnalysisMethod, MethodNode> methodToNode, MethodNode method, InvokeNode invoke, HashMap<AnalysisMethod, MethodNode> callTargets) {
        MethodNode targetMethod = methodToNode.get(invoke.targetMethod);
        if (targetMethod == null) {
            targetMethod = callTargets.computeIfAbsent(invoke.targetMethod, MethodNode::new);
        }
        return Arrays.asList(
                        String.valueOf(invoke.id),
                        String.valueOf(method.id),
                        showBytecodeIndexes(bytecodeIndexes(invoke)),
                        String.valueOf(targetMethod.id),
                        String.valueOf(invoke.isDirectInvoke));
    }

    private static List<String> callTargetInfo(InvokeNode invoke, Node callee) {
        MethodNode node = callee instanceof MethodNodeReference ref ? ref.methodNode : ((MethodNode) callee);
        return Arrays.asList(String.valueOf(invoke.id), String.valueOf(node.id));
    }

    private static List<Integer> bytecodeIndexes(InvokeNode node) {
        return Stream.of(node.sourceReferences)
                        .map(source -> source.bci)
                        .collect(Collectors.toList());
    }

    private static String showBytecodeIndexes(List<Integer> bytecodeIndexes) {
        return bytecodeIndexes.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining("->"));
    }

    private static String display(AnalysisMethod method) {
        final AnalysisType type = method.getDeclaringClass();
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

    private static String flags(AnalysisMethod method) {
        StringBuilder sb = new StringBuilder();
        if (method.isPublic()) {
            sb.append('p');
        } else if (method.isPrivate()) {
            sb.append('P');
        } else if (method.isProtected()) {
            sb.append('d');
        }
        if (method.isStatic()) {
            sb.append('s');
        }
        if (method.isFinal()) {
            sb.append('f');
        }
        if (method.isSynchronized()) {
            sb.append('S');
        }
        if (method.isBridge()) {
            sb.append('b');
        }
        if (method.isVarArgs()) {
            sb.append('v');
        }
        if (method.isNative()) {
            sb.append('n');
        }
        if (method.isAbstract()) {
            sb.append('a');
        }
        if (method.isSynthetic()) {
            sb.append('y');
        }
        return sb.toString();
    }

    private static String convertToCSV(String... data) {
        return String.join(",", data);
    }

    private static String convertToCSV(List<String> data) {
        return String.join(",", data);
    }
}
