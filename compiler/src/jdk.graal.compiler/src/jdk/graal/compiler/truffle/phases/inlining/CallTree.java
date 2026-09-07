/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.truffle.PostPartialEvaluationSuite;
import jdk.graal.compiler.truffle.TruffleCompilerOptions;
import jdk.graal.compiler.truffle.TruffleTierContext;

import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;

public final class CallTree extends Graph {

    /**
     * Matches {@link jdk.graal.compiler.phases.common.priorityinline.Optimizer}'s
     * {@code FREQUENCY_UPDATE_THRESHOLD}.
     */
    private static final double FREQUENCY_UPDATE_THRESHOLD = 0.1D;

    private final InliningPolicy policy;
    private final GraphManager graphManager;
    private final CallNode root;
    private final TruffleTierContext context;
    final boolean useSize;
    int expanded = 1;
    int inlined = 1;
    int frontierSize;
    private int nextId = 0;

    CallTree(PostPartialEvaluationSuite postPartialEvaluationSuite, TruffleTierContext context, InliningPolicy policy) {
        super(context.graph.getOptions(), context.debug);
        this.policy = policy;
        this.context = context;
        this.graphManager = new GraphManager(postPartialEvaluationSuite, context);
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

    /**
     * Updates frequencies for the live call-tree frontier represented in the root graph. Inlined
     * nodes remain in the guest call tree, so this traverses through them to find the nodes whose
     * invokes now belong to the root graph.
     *
     * @return {@code true} if the call tree changed
     */
    public boolean updateRootFrequencies() {
        StructuredGraph rootGraph = root.getIR();
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(rootGraph).connectBlocks(true).computeFrequency(true).build();
        return updateRootFrequencies(root, rootGraph, cfg);
    }

    private static boolean updateRootFrequencies(CallNode node, StructuredGraph rootGraph, ControlFlowGraph cfg) {
        boolean changed = false;
        for (CallNode child : node.getChildren()) {
            switch (child.getState()) {
                case Inlined:
                    changed |= updateRootFrequencies(child, rootGraph, cfg);
                    break;
                case Cutoff:
                case Expanded:
                case BailedOut:
                    Invoke invoke = child.getInvoke();
                    if (invoke == null || !invoke.isAlive()) {
                        child.remove();
                        changed = true;
                        break;
                    }
                    assert invoke.asNode().graph() == rootGraph : "Invoke is not in the root graph: " + invoke;
                    double newFrequency = CallNode.getLocalFrequency(cfg, invoke);
                    double factor = newFrequency / Math.max(0.01D, child.getRootRelativeFrequency());
                    if (Math.abs(1.0D - factor) > FREQUENCY_UPDATE_THRESHOLD) {
                        child.setRootRelativeFrequency(Math.max(0.01D, child.getRootRelativeFrequency()));
                        child.adjustSubtreeFrequency(factor);
                        changed = true;
                    }
                    break;
                case Removed:
                case Indirect:
                    break;
            }
        }
        return changed;
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
