/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.pointsto.reports.ReportUtils.invokeComparator;
import static com.oracle.graal.pointsto.reports.ReportUtils.methodComparator;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class CallTreePrinter {

    public static void print(BigBang bigbang, String path, String reportName) {
        CallTreePrinter printer = new CallTreePrinter(bigbang);
        printer.buildCallTree();

        ReportUtils.report("call tree", path + File.separatorChar + "reports", "call_tree_" + reportName, "txt",
                        writer -> printer.printMethods(writer));
        ReportUtils.report("list of used methods", path + File.separatorChar + "reports", "used_methods_" + reportName, "txt",
                        writer -> printer.printUsedMethods(writer));
        ReportUtils.report("list of used classes", path + File.separatorChar + "reports", "used_classes_" + reportName, "txt",
                        writer -> printer.printClasses(writer, false));
        ReportUtils.report("list of used packages", path + File.separatorChar + "reports", "used_packages_" + reportName, "txt",
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

    private final BigBang bigbang;
    private final Map<AnalysisMethod, MethodNode> methodToNode;

    public CallTreePrinter(BigBang bigbang) {
        this.bigbang = bigbang;
        /* Use linked hash map for predictable iteration order. */
        this.methodToNode = new LinkedHashMap<>();
    }

    public void buildCallTree() {

        /* Add all the roots to the tree. */
        bigbang.getUniverse().getMethods().stream()
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
            node.method.getTypeFlow().getInvokes().stream()
                            .sorted(invokeComparator)
                            .forEach(invoke -> processInvoke(invoke, node, workList));

        }
    }

    private void processInvoke(InvokeTypeFlow invokeFlow, MethodNode callerNode, Deque<MethodNode> workList) {

        InvokeNode invokeNode = new InvokeNode(invokeFlow.getTargetMethod(), invokeFlow.isDirectInvoke(), sourceReference(invokeFlow));
        callerNode.addInvoke(invokeNode);

        invokeFlow.getCallees().stream().sorted(methodComparator).forEach(callee -> {
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

    private static SourceReference[] sourceReference(InvokeTypeFlow invoke) {
        List<SourceReference> sourceReference = new ArrayList<>();
        BytecodePosition state = invoke.getSource();
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
}
