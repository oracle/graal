/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.debug;

import static org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions.TraceTruffleCompilationAST;

import java.util.List;

import org.graalvm.compiler.truffle.common.TruffleCompilerListener.CompilationResultInfo;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.GraphInfo;
import org.graalvm.compiler.truffle.runtime.AbstractGraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.runtime.TruffleInlining.CallTreeNodeVisitor;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeClass;

/**
 * Traces all polymorphic and generic nodes after each successful Truffle compilation.
 */
public final class TraceASTCompilationListener extends AbstractGraalTruffleRuntimeListener {

    private TraceASTCompilationListener(GraalTruffleRuntime runtime) {
        super(runtime);
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (TruffleRuntimeOptions.getValue(TraceTruffleCompilationAST)) {
            runtime.addListener(new TraceASTCompilationListener(runtime));
        }
    }

    @Override
    public void onCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graphInfo, CompilationResultInfo compilationResultInfo) {
        runtime.logEvent(0, "opt AST", target.toString(), target.getDebugProperties(inliningDecision));
        printCompactTree(target, inliningDecision);
    }

    private void printCompactTree(OptimizedCallTarget target, TruffleInlining inliningDecision) {
        target.accept(new CallTreeNodeVisitor() {

            @Override
            public boolean visit(List<TruffleInlining> decisionStack, Node node) {
                if (node == null) {
                    return true;
                }
                int level = CallTreeNodeVisitor.getNodeDepth(decisionStack, node);
                StringBuilder indent = new StringBuilder();
                for (int i = 0; i < level; i++) {
                    indent.append("  ");
                }
                Node parent = node.getParent();

                if (parent == null) {
                    runtime.log(String.format("%s%s", indent, node.getClass().getSimpleName()));
                } else {
                    String fieldName = getFieldName(parent, node);
                    runtime.log(String.format("%s%s = %s", indent, fieldName, node.getClass().getSimpleName()));
                }
                return true;
            }

            @SuppressWarnings("deprecation")
            private String getFieldName(Node parent, Node node) {
                for (com.oracle.truffle.api.nodes.NodeFieldAccessor field : NodeClass.get(parent).getFields()) {
                    Object value = field.loadValue(parent);
                    if (value == node) {
                        return field.getName();
                    } else if (value instanceof Node[]) {
                        int index = 0;
                        for (Node arrayNode : (Node[]) value) {
                            if (arrayNode == node) {
                                return field.getName() + "[" + index + "]";
                            }
                            index++;
                        }
                    }
                }
                return "unknownField";
            }

        }, inliningDecision);
    }
}
