/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.phases.inlining;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.truffle.PartialEvaluator;
import jdk.graal.compiler.truffle.PostPartialEvaluationSuite;
import jdk.graal.compiler.truffle.TruffleCompilerOptions;
import jdk.graal.compiler.truffle.TruffleTierContext;

import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;

public final class CallTree extends Graph {

    private final InliningPolicy policy;
    private final GraphManager graphManager;
    private final CallNode root;
    private final TruffleTierContext context;
    final boolean useSize;
    int expanded = 1;
    int inlined = 1;
    int frontierSize;
    private int nextId = 0;

    CallTree(PartialEvaluator partialEvaluator, PostPartialEvaluationSuite postPartialEvaluationSuite, TruffleTierContext context, InliningPolicy policy) {
        super(context.graph.getOptions(), context.debug);
        this.policy = policy;
        this.context = context;
        this.graphManager = new GraphManager(partialEvaluator, postPartialEvaluationSuite, context);
        this.useSize = TruffleCompilerOptions.InliningUseSize.getValue(context.compilerOptions);
        // Should be kept as the last call in the constructor, as this is an argument.
        this.root = CallNode.makeRoot(context, this);
    }

    int nextId() {
        return nextId++;
    }

    InliningPolicy getPolicy() {
        return policy;
    }

    public CallNode getRoot() {
        return root;
    }

    public int getInlinedCount() {
        return inlined;
    }

    public int getExpandedCount() {
        return expanded;
    }

    GraphManager getGraphManager() {
        return graphManager;
    }

    void trace() {
        Boolean details = TruffleCompilerOptions.TraceInliningDetails.getValue(context.compilerOptions);
        if (TruffleCompilerOptions.TraceInlining.getValue(context.compilerOptions) || details) {
            TruffleCompilerRuntime runtime = context.runtime();
            runtime.logEvent(root.getDirectCallTarget(), 0, "Inline start", root.getName(), root.getStringProperties(), null);
            traceRecursive(runtime, root, details, 0);
            runtime.logEvent(root.getDirectCallTarget(), 0, "Inline done", root.getName(), root.getStringProperties(), null);
        }
    }

    private void traceRecursive(TruffleCompilerRuntime runtime, CallNode node, boolean details, int depth) {
        if (depth != 0) {
            runtime.logEvent(root.getDirectCallTarget(), depth, node.getState().toString(), node.getName(), node.getStringProperties(), null);
        }
        if (node.getState() == CallNode.State.Inlined || details) {
            for (CallNode child : node.getChildren()) {
                traceRecursive(runtime, child, details, depth + 1);
            }
        }
    }

    @Override
    public String toString() {
        return "Call Tree";
    }

    void dumpBasic(String format) {
        getDebug().dump(DebugContext.BASIC_LEVEL, this, format, "");
    }

    public void dumpInfo(String format, Object arg) {
        getDebug().dump(DebugContext.INFO_LEVEL, this, format, arg);
    }

    public void finalizeGraph() {
        root.finalizeGraph();
    }

    void collectTargetsToDequeue(TruffleCompilationTask task) {
        root.collectTargetsToDequeue(task);
    }

    public void updateTracingInfo(TruffleCompilationTask task) {
        final int inlinedWithoutRoot = inlined - 1;
        task.setCallCounts(inlinedWithoutRoot + frontierSize, inlinedWithoutRoot);
        if (loggingInlinedTargets()) {
            root.collectInlinedTargets(task);
        }
    }

    private boolean loggingInlinedTargets() {
        return context.debug.isDumpEnabled(DebugContext.BASIC_LEVEL) ||
                        TruffleCompilerOptions.LogInlinedTargets.getValue(context.compilerOptions);
    }
}
