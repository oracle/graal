/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.truffle.common.TruffleCompilerListener.CompilationResultInfo;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.GraphInfo;
import org.graalvm.compiler.truffle.runtime.AbstractGraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.runtime.OptimizedIndirectCallNode;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.runtime.TruffleInlining.CallTreeNodeVisitor;
import org.graalvm.compiler.truffle.runtime.TruffleInliningDecision;

import com.oracle.truffle.api.nodes.Node;

/**
 * Traces the inlined Truffle call tree after each successful Truffle compilation.
 */
public final class TraceCallTreeListener extends AbstractGraalTruffleRuntimeListener {

    private TraceCallTreeListener(GraalTruffleRuntime runtime) {
        super(runtime);
    }

    public static void install(GraalTruffleRuntime runtime) {
        runtime.addListener(new TraceCallTreeListener(runtime));
    }

    @Override
    public void onCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graphInfo, CompilationResultInfo compilationResultInfo) {
        if (target.getOptionValue(PolyglotCompilerOptions.TraceCompilationCallTree)) {
            runtime.logEvent(target, 0, "opt call tree", target.getDebugProperties(inliningDecision));
            logTruffleCallTree(target, inliningDecision);
        }
    }

    private void logTruffleCallTree(OptimizedCallTarget compilable, TruffleInlining inliningDecision) {
        CallTreeNodeVisitor visitor = new CallTreeNodeVisitor() {

            @Override
            public boolean visit(List<TruffleInlining> decisionStack, Node node) {
                if (node instanceof OptimizedDirectCallNode) {
                    OptimizedDirectCallNode callNode = ((OptimizedDirectCallNode) node);
                    int depth = decisionStack == null ? 0 : decisionStack.size() - 1;
                    TruffleInliningDecision inlining = CallTreeNodeVisitor.getCurrentInliningDecision(decisionStack);
                    String dispatched = "<dispatched>";
                    if (inlining != null && inlining.shouldInline()) {
                        dispatched = "";
                    }
                    Map<String, Object> properties = new LinkedHashMap<>();
                    GraalTruffleRuntimeListener.addASTSizeProperty(callNode.getCurrentCallTarget(), inliningDecision, properties);
                    properties.putAll(callNode.getCurrentCallTarget().getDebugProperties(inliningDecision));
                    runtime.logEvent(compilable, depth, "opt call tree", callNode.getCurrentCallTarget().toString() + dispatched, properties, null);
                } else if (node instanceof OptimizedIndirectCallNode) {
                    int depth = decisionStack == null ? 0 : decisionStack.size() - 1;
                    runtime.logEvent(compilable, depth, "opt call tree", "<indirect>", null, null);
                }
                return true;
            }

        };
        compilable.accept(visitor, inliningDecision);
    }

}
