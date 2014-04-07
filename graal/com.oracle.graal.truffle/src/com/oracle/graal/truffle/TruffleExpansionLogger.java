/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.util.*;

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
        ResolvedJavaMethod sourceMethod = callTarget.invoke().stateAfter().method();

        int sourceMethodBci = callTarget.invoke().bci();
        ResolvedJavaMethod targetMethod = callTarget.targetMethod();
        ResolvedJavaType targetReceiverType = null;
        if (!Modifier.isStatic(sourceMethod.getModifiers()) && callTarget.receiver().isConstant()) {
            targetReceiverType = providers.getMetaAccess().lookupJavaType(callTarget.arguments().first().asConstant());
        }

        if (targetReceiverType != null) {
            ExpansionTree parent = callToParentTree.get(callTarget);
            assert parent != null;
            callToParentTree.remove(callTarget);
            ExpansionTree tree = new ExpansionTree(parent, targetReceiverType, targetMethod, sourceMethodBci);
            registerParentInCalls(tree, inliningGraph);
        }
    }

    @SuppressWarnings("unchecked")
    public void postExpand(Map<Node, Node> states) {
        Iterable<Entry<Node, Node>> entries;
        if (states instanceof NodeMap) {
            entries = ((NodeMap<Node>) states).entries();
        } else {
            entries = states.entrySet();
        }

        for (Entry<Node, Node> entry : entries) {
            Node key = entry.getKey();
            Node value = entry.getValue();

            if (value instanceof MethodCallTargetNode && callToParentTree.containsKey(key)) {
                callToParentTree.put((MethodCallTargetNode) value, callToParentTree.get(key));
                callToParentTree.remove(key);
            }
        }
    }

    private void registerParentInCalls(ExpansionTree parentTree, StructuredGraph graph) {
        for (MethodCallTargetNode target : graph.getNodes(MethodCallTargetNode.class)) {
            callToParentTree.put(target, parentTree);
        }
    }

    public void print() {
        root.print(System.out);
    }

    private static final class ExpansionTree implements Comparable<ExpansionTree> {

        private final ExpansionTree parent;
        private final ResolvedJavaType targetReceiverType;
        private final ResolvedJavaMethod targetMethod;
        private final int parentBci;
        private final List<ExpansionTree> children = new ArrayList<>();

        public ExpansionTree(ExpansionTree parent, ResolvedJavaType targetReceiverType, ResolvedJavaMethod targetMethod, int parentBci) {
            this.parent = parent;
            this.targetReceiverType = targetReceiverType;
            this.targetMethod = targetMethod;
            this.parentBci = parentBci;
            if (parent != null) {
                parent.children.add(this);
            }
        }

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

            lastIndex = className.lastIndexOf('$');
            if (lastIndex != -1) {
                className = className.substring(lastIndex + 1, className.length());
            }

            String constantType = "";
            if (targetReceiverType != null) {
                if (!targetReceiverType.getName().equals(className)) {
                    constantType = "<" + targetReceiverType.getName() + ">";
                }
            }

            String sourceSource = "";
            String targetSource = "";
            if (TruffleCompilerOptions.TraceTruffleExpansionSource.getValue()) {
                sourceSource = formatSource(sourceElement);
                targetSource = formatSource(targetElement);
            }
            p.printf("%s%s %s%s.%s%s%n", indent, sourceSource, className, constantType, targetMethod.getName(), targetSource);

            Collections.sort(children);

            for (ExpansionTree child : children) {
                child.print(p, indent + "  ");
            }
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
                return String.format("(Unknown Source)");
            }
        }
    }

}
