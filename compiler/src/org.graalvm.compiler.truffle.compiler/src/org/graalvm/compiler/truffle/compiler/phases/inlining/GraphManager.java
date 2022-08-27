/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.contract.NodeCostUtil;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.compiler.truffle.compiler.PEAgnosticInlineInvokePlugin;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.PostPartialEvaluationSuite;
import org.graalvm.compiler.truffle.compiler.TruffleTierContext;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;

import jdk.vm.ci.meta.ResolvedJavaMethod;

final class GraphManager {

    public static final int TRIVIAL_NODE_COUNT_LIMIT = 500;
    private final PartialEvaluator partialEvaluator;
    private final EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCacheForInlining;
    private final EconomicMap<CompilableTruffleAST, GraphManager.Entry> irCache = EconomicMap.create();
    private final TruffleTierContext rootContext;
    private final PostPartialEvaluationSuite postPartialEvaluationSuite;
    private final boolean useSize;

    GraphManager(PartialEvaluator partialEvaluator, PostPartialEvaluationSuite postPartialEvaluationSuite, TruffleTierContext rootContext) {
        this.partialEvaluator = partialEvaluator;
        this.postPartialEvaluationSuite = postPartialEvaluationSuite;
        this.rootContext = rootContext;
        this.graphCacheForInlining = partialEvaluator.getOrCreateEncodedGraphCache();
        this.useSize = rootContext.options.get(PolyglotCompilerOptions.InliningUseSize);
    }

    Entry pe(CompilableTruffleAST truffleAST) {
        Entry entry = irCache.get(truffleAST);
        if (entry == null) {
            final PEAgnosticInlineInvokePlugin plugin = newPlugin();
            final TruffleTierContext context = newContext(truffleAST, false);
            partialEvaluator.doGraphPE(context, plugin, graphCacheForInlining);
            context.graph.getAssumptions().record(new TruffleAssumption(truffleAST.getNodeRewritingAssumptionConstant()));
            StructuredGraph graphAfterPE = copyGraphForDebugDump(context);
            postPartialEvaluationSuite.apply(context.graph, context);
            entry = new Entry(context.graph, plugin, graphAfterPE, useSize ? NodeCostUtil.computeGraphSize(context.graph) : -1);
            irCache.put(truffleAST, entry);
        }
        return entry;
    }

    private TruffleTierContext newContext(CompilableTruffleAST truffleAST, boolean finalize) {
        return new TruffleTierContext(
                        partialEvaluator,
                        rootContext.options,
                        rootContext.debug,
                        truffleAST,
                        finalize ? partialEvaluator.getCallDirect() : partialEvaluator.inlineRootForCallTarget(truffleAST),
                        rootContext.compilationId,
                        rootContext.log,
                        rootContext.task,
                        rootContext.handler);
    }

    private PEAgnosticInlineInvokePlugin newPlugin() {
        return new PEAgnosticInlineInvokePlugin(rootContext.task.inliningData(), partialEvaluator);
    }

    Entry peRoot() {
        final PEAgnosticInlineInvokePlugin plugin = newPlugin();
        partialEvaluator.doGraphPE(rootContext, plugin, graphCacheForInlining);
        StructuredGraph graphAfterPE = copyGraphForDebugDump(rootContext);
        postPartialEvaluationSuite.apply(rootContext.graph, rootContext);
        return new Entry(rootContext.graph, plugin, graphAfterPE, useSize ? NodeCostUtil.computeGraphSize(rootContext.graph) : -1);
    }

    UnmodifiableEconomicMap<Node, Node> doInline(Invoke invoke, StructuredGraph ir, CompilableTruffleAST truffleAST, InliningUtil.InlineeReturnAction returnAction) {
        return InliningUtil.inline(invoke, ir, true, partialEvaluator.inlineRootForCallTarget(truffleAST),
                        "cost-benefit analysis", AgnosticInliningPhase.class.getName(), returnAction);
    }

    void finalizeGraph(Invoke invoke, CompilableTruffleAST truffleAST) {
        final TruffleTierContext context = newContext(truffleAST, true);
        partialEvaluator.doGraphPE(context, new InlineInvokePlugin() {
            @Override
            public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                return PartialEvaluator.asInlineInfo(method);
            }
        }, graphCacheForInlining);
        InliningUtil.inline(invoke, context.graph, true, partialEvaluator.getCallInlined(), "finalization", AgnosticInliningPhase.class.getName());
    }

    private static StructuredGraph copyGraphForDebugDump(TruffleTierContext context) {
        if (context.debug.isDumpEnabled(DebugContext.INFO_LEVEL)) {
            return (StructuredGraph) context.graph.copy(context.debug);
        }
        return null;
    }

    static final class Entry {
        final StructuredGraph graph;
        final EconomicMap<Invoke, TruffleCallNode> invokeToTruffleCallNode;
        final List<Invoke> indirectInvokes;
        final boolean trivial;
        // Populated only when debug dump is enabled with debug dump level >= info.
        final StructuredGraph graphAfterPEForDebugDump;
        // Only populated if PolyglotCompilerOptions.InliningUseSize is true
        final int graphSize;

        Entry(StructuredGraph graph, PEAgnosticInlineInvokePlugin plugin, StructuredGraph graphAfterPEForDebugDump, int graphSize) {
            this.graph = graph;
            this.invokeToTruffleCallNode = plugin.getInvokeToTruffleCallNode();
            this.indirectInvokes = plugin.getIndirectInvokes();
            this.trivial = invokeToTruffleCallNode.isEmpty() &&
                            indirectInvokes.isEmpty() &&
                            graph.getNodes(LoopBeginNode.TYPE).count() == 0 &&
                            graph.getNodeCount() < TRIVIAL_NODE_COUNT_LIMIT;
            this.graphAfterPEForDebugDump = graphAfterPEForDebugDump;
            this.graphSize = graphSize;
        }
    }

}
