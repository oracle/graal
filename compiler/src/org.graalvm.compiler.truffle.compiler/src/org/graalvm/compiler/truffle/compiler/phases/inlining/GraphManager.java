/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.compiler.truffle.compiler.PEAgnosticInlineInvokePlugin;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;

import jdk.vm.ci.meta.ResolvedJavaMethod;

final class GraphManager {

    private final PartialEvaluator partialEvaluator;
    private final EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCacheForInlining;
    private final EconomicMap<CompilableTruffleAST, GraphManager.Entry> irCache = EconomicMap.create();
    private final PartialEvaluator.Request rootRequest;

    GraphManager(PartialEvaluator partialEvaluator, PartialEvaluator.Request rootRequest) {
        this.partialEvaluator = partialEvaluator;
        this.rootRequest = rootRequest;
        this.graphCacheForInlining = partialEvaluator.getOrCreateEncodedGraphCache();
    }

    Entry pe(CompilableTruffleAST truffleAST) {
        Entry entry = irCache.get(truffleAST);
        if (entry == null) {
            final PEAgnosticInlineInvokePlugin plugin = newPlugin();
            final PartialEvaluator.Request request = newRequest(truffleAST, false);
            request.graph.getAssumptions().record(new TruffleAssumption(truffleAST.getNodeRewritingAssumptionConstant()));
            partialEvaluator.doGraphPE(request, plugin, graphCacheForInlining);
            entry = new Entry(request.graph, plugin.getInvokeToTruffleCallNode(), plugin.getIndirectInvokes());
            irCache.put(truffleAST, entry);
        }
        return entry;
    }

    private PartialEvaluator.Request newRequest(CompilableTruffleAST truffleAST, boolean finalize) {
        return partialEvaluator.new Request(
                        rootRequest.options,
                        rootRequest.debug,
                        truffleAST,
                        finalize ? partialEvaluator.getCallDirect() : partialEvaluator.inlineRootForCallTarget(truffleAST),
                        rootRequest.inliningPlan,
                        rootRequest.compilationId,
                        rootRequest.log,
                        rootRequest.cancellable);
    }

    private PEAgnosticInlineInvokePlugin newPlugin() {
        return new PEAgnosticInlineInvokePlugin(rootRequest.inliningPlan, partialEvaluator);
    }

    EconomicMap<Invoke, TruffleCallNode> peRoot() {
        final PEAgnosticInlineInvokePlugin plugin = newPlugin();
        partialEvaluator.doGraphPE(rootRequest, plugin, graphCacheForInlining);
        return plugin.getInvokeToTruffleCallNode();
    }

    UnmodifiableEconomicMap<Node, Node> doInline(Invoke invoke, StructuredGraph ir, CompilableTruffleAST truffleAST) {
        return InliningUtil.inline(invoke, ir, true, partialEvaluator.inlineRootForCallTarget(truffleAST),
                        "cost-benefit analysis", AgnosticInliningPhase.class.getName());
    }

    void finalizeGraph(Invoke invoke, CompilableTruffleAST truffleAST) {
        final PartialEvaluator.Request request = newRequest(truffleAST, true);
        partialEvaluator.doGraphPE(request, new InlineInvokePlugin() {
            @Override
            public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                return PartialEvaluator.asInlineInfo(method);
            }
        }, graphCacheForInlining);
        InliningUtil.inline(invoke, request.graph, true, partialEvaluator.getCallInlined(), "finalization", AgnosticInliningPhase.class.getName());
    }

    static class Entry {
        final StructuredGraph graph;
        final EconomicMap<Invoke, TruffleCallNode> invokeToTruffleCallNode;
        final List<Invoke> indirectInvokes;

        Entry(StructuredGraph graph, EconomicMap<Invoke, TruffleCallNode> invokeToTruffleCallNode, List<Invoke> indirectInvokes) {
            this.graph = graph;
            this.invokeToTruffleCallNode = invokeToTruffleCallNode;
            this.indirectInvokes = indirectInvokes;
        }
    }

}
