/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.pointsto;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.nodes.FrameState;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AnalysisPrinter {

    static final Comparator<AnalysisField> fieldComparator = (f1, f2) -> f1.format("%H.%n").compareTo(f2.format("%H.%n"));
    static final Comparator<AnalysisMethod> methodComparator = (m1, m2) -> m1.format("%H.%n(%p)").compareTo(m2.format("%H.%n(%p)"));
    static final Comparator<AnalysisType> typeComparator = (t1, t2) -> t1.toJavaName(true).compareTo(t2.toJavaName(true));

    public static void printBootImageHeap(BigBang bigbang) {
        BootImageHeapPrinter printer = new BootImageHeapPrinter(bigbang);
        printer.scanBootImageHeapRoots(fieldComparator, methodComparator);
        printer.printTypeHierarchy();
    }

    public static void printCallTree(BigBang bigbang) {
        CallTreePrinter printer = new CallTreePrinter(bigbang);
        printer.preprocessMethods();
        printer.printCallTree();
    }

    static class TreeNode<T> {
        private T target;
        private String source;
        private TreeNode<T> parent;
        private List<TreeNode<T>> children;
        int level;

        TreeNode(T t, String source, int level) {
            this.target = t;
            this.source = source;
            this.children = new LinkedList<>();
            this.level = level;
        }

        public void setParent(TreeNode<T> parent) {
            this.parent = parent;
        }

        public void addChild(TreeNode<T> child) {
            children.add(child);
        }

        public TreeNode<T> parent() {
            return parent;
        }

        public T target() {
            return target;
        }
    }

    static class BootImageHeapPrinter extends ObjectScanner {
        private Map<AnalysisType, TreeNode<AnalysisType>> typeToNode;

        BootImageHeapPrinter(BigBang bigbang) {
            super(bigbang);

            /* Use linked hash map for predictable iteration order. */
            this.typeToNode = new LinkedHashMap<>();
        }

        private void recordTypeLink(AnalysisType referencingType, AnalysisType referencedType) {
            TreeNode<AnalysisType> parentNode = typeToNode.get(referencingType);
            assert parentNode != null;
            TreeNode<AnalysisType> childNode = new TreeNode<>(referencedType, "", parentNode.level + 1);
            childNode.setParent(parentNode);
            parentNode.addChild(childNode);
            typeToNode.put(referencedType, childNode);
        }

        @Override
        public void forRelocatedPointerFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue) {
        }

        @Override
        public void forNullFieldValue(JavaConstant receiver, AnalysisField field) {
        }

        @Override
        public void forNonNullFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue) {
            AnalysisType fieldType = bb.getMetaAccess().lookupJavaType(bb.getSnippetReflectionProvider().asObject(Object.class, fieldValue).getClass());
            if (!typeToNode.containsKey(fieldType)) {
                AnalysisType receiverType = bb.getMetaAccess().lookupJavaType(bb.getSnippetReflectionProvider().asObject(Object.class, receiver).getClass());
                recordTypeLink(receiverType, fieldType);
            }
        }

        @Override
        public void forNullArrayElement(JavaConstant array, AnalysisType arrayType) {
        }

        @Override
        public void forNonNullArrayElement(JavaConstant array, AnalysisType arrayType, JavaConstant elementConstant, AnalysisType elementType) {
            if (!typeToNode.containsKey(elementType)) {
                recordTypeLink(arrayType, elementType);
            }
        }

        @Override
        protected void forScannedConstant(JavaConstant scannedValue, Object reason) {
            AnalysisType scannedType = bb.getMetaAccess().lookupJavaType(bb.getSnippetReflectionProvider().asObject(Object.class, scannedValue).getClass());
            if (!typeToNode.containsKey(scannedType)) {
                if (reason instanceof ResolvedJavaField) {
                    ResolvedJavaField field = (ResolvedJavaField) reason;
                    TreeNode<AnalysisType> node = new TreeNode<>(scannedType, field.format("%H.%n"), 0);
                    typeToNode.put(scannedType, node);
                } else if (reason instanceof ResolvedJavaMethod) {
                    ResolvedJavaMethod method = (ResolvedJavaMethod) reason;
                    TreeNode<AnalysisType> node = new TreeNode<>(scannedType, method.format("%H.%n(%p)"), 0);
                    typeToNode.put(scannedType, node);
                }
            }
        }

        public void printTypeHierarchy() {
            System.out.println("depth ; type ; source ; full type name");
            for (TreeNode<AnalysisType> node : typeToNode.values()) {
                if (node.level == 0) {
                    printTypeHierarchyNode(node);
                }
            }
            System.out.println();
        }

        private void printTypeHierarchyNode(TreeNode<AnalysisType> node) {
            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < node.level; i++) {
                indent.append("  ");
            }
            indent.append(node.target.toJavaName(false));

            System.out.format("%4d ; %-80s  ; %s ; %s", node.level, indent, node.source, node.target.toJavaName(true));
            System.out.println();

            for (TreeNode<AnalysisType> child : node.children) {
                printTypeHierarchyNode(child);
            }
        }

    }

    public static final class CallTreePrinter {

        private BigBang bigbang;
        private Map<AnalysisMethod, TreeNode<AnalysisMethod>> methodToNode;

        private CallTreePrinter(BigBang bigbang) {
            this.bigbang = bigbang;
            /* Use linked hash map for predictable iteration order. */
            this.methodToNode = new LinkedHashMap<>();
        }

        public void preprocessMethods() {

            /* Add all the roots to the tree. */
            bigbang.universe.getMethods().stream()
                            .filter(m -> m.isRootMethod() && !methodToNode.containsKey(m))
                            .sorted(methodComparator)
                            .forEach(method -> methodToNode.put(method, new TreeNode<>(method, "", 0)));

            /* Walk the call graph starting from the roots, do a breath-first tree reduction. */
            ArrayDeque<TreeNode<AnalysisMethod>> worklist = new ArrayDeque<>();
            worklist.addAll(methodToNode.values());

            while (!worklist.isEmpty()) {
                TreeNode<AnalysisMethod> node = worklist.removeFirst();
                /*
                 * Process the method: iterate the invokes, for each invoke iterate the callees, if
                 * the callee was not already processed add it to the tree and to the work list.
                 */
                node.target.getTypeFlow().getInvokes().stream()
                                .sorted((i1, i2) -> Integer.compare(i1.id(), i2.id()))
                                .forEach(invoke -> processInvoke(invoke, node, worklist));

            }
        }

        private void processInvoke(InvokeTypeFlow invoke, TreeNode<AnalysisMethod> node, Deque<TreeNode<AnalysisMethod>> worklist) {

            String sourceReference = "";
            for (FrameState state = invoke.getSource().invoke().stateAfter(); state != null; state = state.outerFrameState()) {
                if (sourceReference.length() > 0) {
                    sourceReference += " -> ";
                }
                sourceReference += (state.getCode() != null ? state.getCode().asStackTraceElement(state.bci) : "<Unknown>");
            }

            final String source = sourceReference;

            invoke.getCallees().stream()
                            .filter(c -> !methodToNode.containsKey(c))
                            .sorted(methodComparator)
                            .forEach(callee -> {
                                TreeNode<AnalysisMethod> calleeNode = new TreeNode<>(callee, source, node.level + 1);
                                calleeNode.setParent(node);
                                node.addChild(calleeNode);
                                methodToNode.put(callee, calleeNode);
                                worklist.add(calleeNode);
                            });
        }

        public void printCallTree() {
            System.out.println("depth ; method ; source ; full method name");
            for (TreeNode<AnalysisMethod> node : methodToNode.values()) {
                if (node.level == 0) {
                    printCallTreeNode(node);
                }
            }
            System.out.println();
        }

        private void printCallTreeNode(TreeNode<AnalysisMethod> node) {
            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < node.level; i++) {
                indent.append("  ");
            }
            indent.append(node.target.format("%h.%n"));

            System.out.format("%4d ; %-80s  ; %s ; %s", node.level, indent, node.source, node.target.format("%H.%n(%p)"));
            System.out.println();

            for (TreeNode<AnalysisMethod> child : node.children) {
                printCallTreeNode(child);
            }
        }
    }

}
