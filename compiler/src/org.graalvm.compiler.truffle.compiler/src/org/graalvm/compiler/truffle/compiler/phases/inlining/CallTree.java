/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases.inlining;

import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.getPolyglotOptionValue;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleMetaAccessProvider;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;

public final class CallTree extends Graph {

    private final InliningPolicy policy;
    private final GraphManager graphManager;
    private final CallNode root;
    private final PartialEvaluator.Request request;
    int expanded = 1;
    int inlined = 1;
    private int nextId = 0;

    CallTree(PartialEvaluator partialEvaluator, PartialEvaluator.Request request, InliningPolicy policy) {
        super(request.graph.getOptions(), request.debug);
        this.policy = policy;
        this.request = request;
        this.graphManager = new GraphManager(partialEvaluator, request);
        // Should be kept as the last call in the constructor, as this is an argument.
        this.root = CallNode.makeRoot(this, request);
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
        Boolean details = getPolyglotOptionValue(request.options, PolyglotCompilerOptions.TraceInliningDetails);
        if (getPolyglotOptionValue(request.options, PolyglotCompilerOptions.TraceInlining) || details) {
            TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
            runtime.logEvent(root.getTruffleAST(), 0, "inline start", root.getName(), root.getStringProperties(), null);
            traceRecursive(runtime, root, details, 0);
            runtime.logEvent(root.getTruffleAST(), 0, "inline done", root.getName(), root.getStringProperties(), null);
        }
    }

    private void traceRecursive(TruffleCompilerRuntime runtime, CallNode node, boolean details, int depth) {
        if (depth != 0) {
            runtime.logEvent(root.getTruffleAST(), depth, node.getState().toString(), node.getName(), node.getStringProperties(), null);
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

    void collectTargetsToDequeue(TruffleMetaAccessProvider provider) {
        root.collectTargetsToDequeue(provider);
    }
}
