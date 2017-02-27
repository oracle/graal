/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.debug;

import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TraceTruffleCompilationCallTree;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.OptimizedIndirectCallNode;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.TruffleInlining;
import org.graalvm.compiler.truffle.TruffleInlining.CallTreeNodeVisitor;
import org.graalvm.compiler.truffle.TruffleInliningDecision;

import com.oracle.truffle.api.nodes.Node;

public final class TraceCompilationCallTreeListener extends AbstractDebugCompilationListener {

    private TraceCompilationCallTreeListener() {
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (TruffleCompilerOptions.getValue(TraceTruffleCompilationCallTree)) {
            runtime.addCompilationListener(new TraceCompilationCallTreeListener());
        }
    }

    @Override
    public void notifyCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph, CompilationResult result,
                    Map<OptimizedCallTarget, Object> compilationMap) {
        log(0, "opt call tree", target.toString(), target.getDebugProperties(inliningDecision));
        logTruffleCallTree(target, inliningDecision);
    }

    private static void logTruffleCallTree(OptimizedCallTarget compilable, TruffleInlining inliningDecision) {
        CallTreeNodeVisitor visitor = new CallTreeNodeVisitor() {

            @Override
            public boolean visit(List<TruffleInlining> decisionStack, Node node) {
                if (node instanceof OptimizedDirectCallNode) {
                    OptimizedDirectCallNode callNode = ((OptimizedDirectCallNode) node);
                    int depth = decisionStack == null ? 0 : decisionStack.size() - 1;
                    TruffleInliningDecision inlining = CallTreeNodeVisitor.getCurrentInliningDecision(decisionStack);
                    String dispatched = "<dispatched>";
                    if (inlining != null && inlining.isInline()) {
                        dispatched = "";
                    }
                    Map<String, Object> properties = new LinkedHashMap<>();
                    addASTSizeProperty(callNode.getCurrentCallTarget(), inliningDecision, properties);
                    properties.putAll(callNode.getCurrentCallTarget().getDebugProperties(inliningDecision));
                    log(depth, "opt call tree", callNode.getCurrentCallTarget().toString() + dispatched, properties);
                } else if (node instanceof OptimizedIndirectCallNode) {
                    int depth = decisionStack == null ? 0 : decisionStack.size() - 1;
                    log(depth, "opt call tree", "<indirect>", new LinkedHashMap<>());
                }
                return true;
            }

        };
        compilable.accept(visitor, inliningDecision);
    }

}
