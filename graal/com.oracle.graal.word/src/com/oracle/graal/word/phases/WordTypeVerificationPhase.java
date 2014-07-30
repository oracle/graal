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
package com.oracle.graal.word.phases;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.word.*;
import com.oracle.graal.word.Word.Operation;

/**
 * Verifies invariants that must hold for code that uses the {@link WordBase word type} above and
 * beyond normal bytecode verification.
 */
public class WordTypeVerificationPhase extends Phase {

    private final WordTypeRewriterPhase wordAccess;

    public WordTypeVerificationPhase(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, Kind wordKind) {
        this.wordAccess = new WordTypeRewriterPhase(metaAccess, snippetReflection, wordKind);
    }

    @Override
    protected void run(StructuredGraph graph) {
        assert verify(graph);
    }

    protected boolean verify(StructuredGraph inputGraph) {
        /*
         * This is a verification phase, so we do not want to have side effects. Since inferStamps()
         * modifies the stamp of nodes, we copy the graph before running the verification.
         */
        StructuredGraph graph = inputGraph.copy();
        InferStamps.inferStamps(graph);

        for (ValueNode node : graph.getNodes().filter(ValueNode.class)) {
            if (!node.recordsUsages()) {
                continue;
            }
            for (Node usage : node.usages()) {
                if (usage instanceof AccessMonitorNode) {
                    verify(!isWord(node), node, usage, "word value has no monitor");
                } else if (usage instanceof LoadFieldNode) {
                    verify(!isWord(node) || ((LoadFieldNode) usage).object() != node, node, usage, "cannot load from word value");
                } else if (usage instanceof StoreFieldNode) {
                    verify(!isWord(node) || ((StoreFieldNode) usage).object() != node, node, usage, "cannot store to word value");
                } else if (usage instanceof CheckCastNode) {
                    verify(isWord(((CheckCastNode) usage).type()) == isWord(node), node, usage, "word cannot be cast to object, and vice versa");
                } else if (usage instanceof LoadIndexedNode) {
                    verify(!isWord(node) || ((LoadIndexedNode) usage).array() != node, node, usage, "cannot load from word value");
                    verify(!isWord(node) || ((LoadIndexedNode) usage).index() != node, node, usage, "cannot use word value as index");
                } else if (usage instanceof StoreIndexedNode) {
                    verify(!isWord(node) || ((StoreIndexedNode) usage).array() != node, node, usage, "cannot store to word value");
                    verify(!isWord(node) || ((StoreIndexedNode) usage).index() != node, node, usage, "cannot use word value as index");
                } else if (usage instanceof MethodCallTargetNode) {
                    MethodCallTargetNode callTarget = (MethodCallTargetNode) usage;
                    verifyInvoke(node, callTarget);
                } else if (usage instanceof ObjectEqualsNode) {
                    verify(!isWord(node) || ((ObjectEqualsNode) usage).getX() != node, node, usage, "cannot use word type in comparison");
                    verify(!isWord(node) || ((ObjectEqualsNode) usage).getY() != node, node, usage, "cannot use word type in comparison");
                } else if (usage instanceof ArrayLengthNode) {
                    verify(!isWord(node) || ((ArrayLengthNode) usage).array() != node, node, usage, "cannot get array length from word value");
                } else if (usage instanceof ValuePhiNode) {
                    if (!(node instanceof MergeNode)) {
                        ValuePhiNode phi = (ValuePhiNode) usage;
                        for (ValueNode input : phi.values()) {
                            verify(isWord(node) == isWord(input), node, input, "cannot merge word and non-word values");
                        }
                    }
                }
            }
        }
        return true;
    }

    protected void verifyInvoke(ValueNode node, MethodCallTargetNode callTarget) {
        ResolvedJavaMethod method = callTarget.targetMethod();
        if (method.getAnnotation(NodeIntrinsic.class) == null) {
            Invoke invoke = (Invoke) callTarget.usages().first();
            NodeInputList<ValueNode> arguments = callTarget.arguments();
            boolean isStatic = method.isStatic();
            int argc = 0;
            if (!isStatic) {
                ValueNode receiver = arguments.get(argc);
                if (receiver == node && isWord(node)) {
                    ResolvedJavaMethod resolvedMethod = wordAccess.wordImplType.resolveMethod(method, invoke.getContextType());
                    verify(resolvedMethod != null, node, invoke.asNode(), "cannot resolve method on Word class: " + method.format("%H.%n(%P) %r"));
                    Operation operation = resolvedMethod.getAnnotation(Word.Operation.class);
                    verify(operation != null, node, invoke.asNode(), "cannot dispatch on word value to non @Operation annotated method " + resolvedMethod);
                }
                argc++;
            }
            Signature signature = method.getSignature();
            for (int i = 0; i < signature.getParameterCount(false); i++) {
                ValueNode argument = arguments.get(argc);
                if (argument == node) {
                    ResolvedJavaType type = (ResolvedJavaType) signature.getParameterType(i, method.getDeclaringClass());
                    verify(isWord(type) == isWord(argument), node, invoke.asNode(), "cannot pass word value to non-word parameter " + i + " or vice-versa");
                }
                argc++;
            }
        }
    }

    private boolean isWord(ValueNode node) {
        return wordAccess.isWord(node);
    }

    private boolean isWord(ResolvedJavaType type) {
        return wordAccess.isWord(type);
    }

    private static void verify(boolean condition, Node node, Node usage, String message) {
        if (!condition) {
            error(node, usage, message);
        }
    }

    private static void error(Node node, Node usage, String message) {
        throw new GraalInternalError(String.format("Snippet verification error: %s" + "%n   node: %s (%s)" + "%n  usage: %s (%s)", message, node, sourceLocation(node), usage, sourceLocation(usage)));
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
            return loc == null ? ((StructuredGraph) n.graph()).method().format("method %h.%n") : loc;
        }
    }
}
