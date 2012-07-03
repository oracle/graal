/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.snippets;

import static com.oracle.graal.snippets.WordTypeRewriterPhase.*;

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.snippets.Word.*;

/**
 * Verifies invariants that must hold for snippet code above and beyond normal
 * bytecode verification.
 */
public class SnippetVerificationPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (ValueNode node : graph.getNodes().filter(ValueNode.class)) {
            for (Node usage : node.usages()) {
                if (usage instanceof AccessMonitorNode) {
                    verify(!isWord(node), node, usage, "word value has no monitor");
                } else if (usage instanceof LoadFieldNode) {
                    verify(!isWord(node) || ((LoadFieldNode) usage).object() != node, node, usage, "cannot load from word value");
                } else if (usage instanceof StoreFieldNode) {
                    verify(!isWord(node) || ((StoreFieldNode) usage).object() != node, node, usage, "cannot store to word value");
                } else if (usage instanceof CheckCastNode) {
                    verify(!isWord(node), node, usage, "word value cannot be cast");
                    verify(!isWord(((CheckCastNode) usage).targetClass()), node, usage, "cannot cast to word value");
                } else if (usage instanceof LoadIndexedNode) {
                    verify(!isWord(node) || ((LoadIndexedNode) usage).array() != node, node, usage, "cannot load from word value");
                    verify(!isWord(node) || ((LoadIndexedNode) usage).index() != node, node, usage, "cannot use word value as index");
                } else if (usage instanceof StoreIndexedNode) {
                    verify(!isWord(node) || ((StoreIndexedNode) usage).array() != node, node, usage, "cannot store to word value");
                    verify(!isWord(node) || ((StoreIndexedNode) usage).index() != node, node, usage, "cannot use word value as index");
                    verify(!isWord(node) || ((StoreIndexedNode) usage).value() != node, node, usage, "cannot store word value to array");
                } else if (usage instanceof MethodCallTargetNode) {
                    MethodCallTargetNode callTarget = (MethodCallTargetNode) usage;
                    ResolvedJavaMethod method = callTarget.targetMethod();
                    if (method.getAnnotation(NodeIntrinsic.class) == null) {
                        Invoke invoke = (Invoke) callTarget.usages().first();
                        NodeInputList<ValueNode> arguments = callTarget.arguments();
                        boolean isStatic = Modifier.isStatic(method.accessFlags());
                        int argc = 0;
                        if (!isStatic) {
                            ValueNode receiver = arguments.get(argc);
                            if (receiver == node && isWord(node)) {
                                Operation operation = method.getAnnotation(Word.Operation.class);
                                verify(operation != null, node, invoke.node(), "cannot dispatch on word value to non @Operation annotated method " + method);
                            }
                            argc++;
                        }
                        Signature signature = method.signature();
                        for (int i = 0; i < signature.argumentCount(false); i++) {
                            ValueNode argument = arguments.get(argc);
                            if (argument == node) {
                                ResolvedJavaType type = (ResolvedJavaType) signature.argumentTypeAt(i, method.holder());
                                verify((type.toJava() == Word.class) == isWord(argument), node, invoke.node(), "cannot pass word value to non-word parameter " + i + " or vice-versa");
                            }
                            argc++;
                        }
                    }
                } else if (usage instanceof ObjectEqualsNode) {
                    ObjectEqualsNode compare = (ObjectEqualsNode) usage;
                    if (compare.x() == node || compare.y() == node) {
                        verify(isWord(compare.x()) == isWord(compare.y()), node, compare.usages().first(), "cannot mixed word and now-word type in use of '==' or '!='");
                    }
                } else if (usage instanceof ArrayLengthNode) {
                    verify(!isWord(node) || ((ArrayLengthNode) usage).array() != node, node, usage, "cannot get array length from word value");
                } else if (usage instanceof PhiNode) {
                    if (!(node instanceof MergeNode)) {
                        PhiNode phi = (PhiNode) usage;
                        for (ValueNode input : phi.values()) {
                            verify(isWord(node) == isWord(input), node, input, "cannot merge word and non-word values");
                        }
                    }
                }
            }
        }
    }

    private static void verify(boolean condition, Node node, Node usage, String message) {
        if (!condition) {
            error(node, usage, message);
        }
    }

    private static void error(Node node, Node usage, String message) {
        throw new GraalInternalError(String.format("Snippet verification error: %s" +
                        "%n   node: %s (%s)" +
                        "%n  usage: %s (%s)", message, node, sourceLocation(node), usage, sourceLocation(usage)));
    }

    private static String sourceLocation(Node n) {
        if (n instanceof PhiNode) {
            StringBuilder buf = new StringBuilder();
            for (Node usage : n.usages()) {
                String loc = sourceLocation(usage);
                if (!loc.equals("<unknown>")) {
                    if (buf.length() != 0) {
                        buf.append(", ");
                    }
                    buf.append(loc);
                }
            }
            return buf.toString();
        } else {
            String loc = GraphUtil.approxSourceLocation(n);
            return loc == null ? "<unknown>" : loc;
        }
    }
}
