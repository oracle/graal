/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TraceTruffleExpansionSource;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class TruffleExpansionLogger {

    private final Providers providers;
    private final ExpansionTree root;
    private final Map<MethodCallTargetNode, ExpansionTree> callToParentTree = new HashMap<>();

    public TruffleExpansionLogger(Providers providers, StructuredGraph graph) {
        this.providers = providers;
        root = new ExpansionTree(null, null, graph.method(), -1);
        registerParentInCalls(root, graph);
    }

    public void preExpand(MethodCallTargetNode callTarget, StructuredGraph inliningGraph) {
        ResolvedJavaMethod sourceMethod = callTarget.invoke().stateAfter().getMethod();

        int sourceMethodBci = callTarget.invoke().bci();
        ResolvedJavaMethod targetMethod = callTarget.targetMethod();
        ResolvedJavaType targetReceiverType = null;
        if (!sourceMethod.isStatic() && callTarget.receiver() != null && callTarget.receiver().isConstant()) {
            targetReceiverType = providers.getMetaAccess().lookupJavaType(callTarget.arguments().first().asJavaConstant());
        }

        if (targetReceiverType != null) {
            ExpansionTree parent = callToParentTree.get(callTarget);
            assert parent != null;
            callToParentTree.remove(callTarget);
            ExpansionTree tree = new ExpansionTree(parent, targetReceiverType, targetMethod, sourceMethodBci);
            registerParentInCalls(tree, inliningGraph);
        }
    }

    private void registerParentInCalls(ExpansionTree parentTree, StructuredGraph graph) {
        for (MethodCallTargetNode target : graph.getNodes(MethodCallTargetNode.TYPE)) {
            callToParentTree.put(target, parentTree);
        }
    }

    public void print(OptimizedCallTarget target) {
        System.out.printf("Expansion tree for %s: %n", target);
        root.print(System.out);
    }

    private static final class ExpansionTree implements Comparable<ExpansionTree> {

        private final ExpansionTree parent;
        private final ResolvedJavaType targetReceiverType;
        private final ResolvedJavaMethod targetMethod;
        private final int parentBci;
        private final List<ExpansionTree> children = new ArrayList<>();

        ExpansionTree(ExpansionTree parent, ResolvedJavaType targetReceiverType, ResolvedJavaMethod targetMethod, int parentBci) {
            this.parent = parent;
            this.targetReceiverType = targetReceiverType;
            this.targetMethod = targetMethod;
            this.parentBci = parentBci;
            if (parent != null) {
                parent.children.add(this);
            }
        }

        @Override
        public int compareTo(ExpansionTree o) {
            if (parent == o.parent) {
                return parentBci - o.parentBci;
            }
            return 0;
        }

        public void print(PrintStream p) {
            print(p, "");
        }

        private void print(PrintStream p, String indent) {
            StackTraceElement targetElement = targetMethod.asStackTraceElement(0);
            StackTraceElement sourceElement = null;

            ExpansionTree currentParent = this.parent;
            if (currentParent != null) {
                sourceElement = currentParent.targetMethod.asStackTraceElement(parentBci);
            }

            String className = targetElement.getClassName();
            int lastIndex = className.lastIndexOf('.');
            if (lastIndex != -1) {
                className = className.substring(lastIndex + 1, className.length());
            }

            className = extractInnerClassName(className);

            String constantType = "";
            if (targetReceiverType != null) {
                String javaName = targetReceiverType.toJavaName(false);

                javaName = extractInnerClassName(javaName);

                if (!javaName.equals(className)) {
                    constantType = "<" + javaName + ">";
                }
            }

            String sourceSource = "";
            String targetSource = "";
            if (TruffleCompilerOptions.getValue(TraceTruffleExpansionSource)) {
                sourceSource = formatSource(sourceElement);
                targetSource = formatSource(targetElement);
            }
            p.printf("%s%s %s%s.%s%s%n", indent, sourceSource, className, constantType, targetMethod.getName(), targetSource);

            Collections.sort(children);

            for (ExpansionTree child : children) {
                child.print(p, indent + "  ");
            }
        }

        private static String extractInnerClassName(String className) {
            int lastIndex = className.lastIndexOf('$');
            if (lastIndex != -1) {
                return className.substring(lastIndex + 1, className.length());
            }
            return className;
        }

        private static String formatSource(StackTraceElement e) {
            if (e == null) {
                return "";
            }
            if (e.getFileName() != null) {
                if (e.getLineNumber() >= 0) {
                    return String.format("(%s:%d)", e.getFileName(), e.getLineNumber());
                } else {
                    return String.format("(%s)", e.getFileName());
                }
            } else {
                return "(Unknown Source)";
            }
        }
    }

}
