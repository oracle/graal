/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.compiler.truffle.common.TruffleMetaAccessProvider;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

final class GraphManager {

    private final PartialEvaluator partialEvaluator;
    private final EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCacheForInlining = EconomicMap.create();
    private final EconomicMap<CompilableTruffleAST, GraphManager.Entry> irCache = EconomicMap.create();
    private final PartialEvaluator.Request rootRequest;

    GraphManager(PartialEvaluator partialEvaluator, PartialEvaluator.Request rootRequest) {
        this.partialEvaluator = partialEvaluator;
        this.rootRequest = rootRequest;
    }

    Entry get(CompilableTruffleAST truffleAST) {
        Entry entry = irCache.get(truffleAST);
        if (entry == null) {
            final PEAgnosticInlineInvokePlugin plugin = new PEAgnosticInlineInvokePlugin(rootRequest.inliningPlan, partialEvaluator.getCallDirectMethod(), partialEvaluator.getCallBoundary(),
                            partialEvaluator.getCallIndirectMethod());
            final PartialEvaluator.Request request = partialEvaluator.new Request(rootRequest.options, rootRequest.debug, truffleAST, partialEvaluator.inlineRootForCallTarget(truffleAST),
                            rootRequest.inliningPlan, rootRequest.allowAssumptions, rootRequest.compilationId, rootRequest.log, rootRequest.cancellable);
            partialEvaluator.doGraphPE(request, plugin, graphCacheForInlining);
            final EconomicMap<TruffleCallNode, Invoke> truffleCallNodeToInvoke = plugin.getTruffleCallNodeToInvoke();
            final List<Invoke> indirectInvokes = plugin.getIndirectInvokes();
            entry = new GraphManager.Entry(request.graph, truffleCallNodeToInvoke, indirectInvokes);
            irCache.put(truffleAST, entry);
        }
        return entry;
    }

    EconomicMap<TruffleCallNode, Invoke> peRoot() {
        final PEAgnosticInlineInvokePlugin plugin = new PEAgnosticInlineInvokePlugin(rootRequest.inliningPlan, partialEvaluator.getCallDirectMethod(), partialEvaluator.getCallBoundary(),
                        partialEvaluator.getCallIndirectMethod());
        partialEvaluator.doGraphPE(rootRequest, plugin, graphCacheForInlining);
        return plugin.getTruffleCallNodeToInvoke();
    }

    UnmodifiableEconomicMap<Node, Node> doInline(Invoke invoke, StructuredGraph ir, CompilableTruffleAST truffleAST) {
        final UnmodifiableEconomicMap<Node, Node> duplicates = InliningUtil.inline(invoke, ir, true, partialEvaluator.inlineRootForCallTarget(truffleAST),
                        "cost-benefit analysis", "AgnosticInliningPhase");
        return duplicates;
    }

    static class Entry {
        final StructuredGraph graph;
        final EconomicMap<TruffleCallNode, Invoke> truffleCallNodeToInvoke;
        final List<Invoke> indirectInvokes;

        Entry(StructuredGraph graph, EconomicMap<TruffleCallNode, Invoke> truffleCallNodeToInvoke, List<Invoke> indirectInvokes) {
            this.graph = graph;
            this.truffleCallNodeToInvoke = truffleCallNodeToInvoke;
            this.indirectInvokes = indirectInvokes;
        }
    }

    private static class PEAgnosticInlineInvokePlugin extends PartialEvaluator.PEInlineInvokePlugin {
        private final EconomicMap<TruffleCallNode, Invoke> truffleCallNodeToInvoke;
        private final List<Invoke> indirectInvokes = new ArrayList<>();
        private final TruffleMetaAccessProvider truffleMetaAccessProvider;
        private final ResolvedJavaMethod callTargetCallDirect;
        private final ResolvedJavaMethod callBoundary;
        private final ResolvedJavaMethod callIndirectMethod;
        private JavaConstant lastDirectCallNode;
        private boolean indirectCall;

        PEAgnosticInlineInvokePlugin(TruffleMetaAccessProvider truffleMetaAccessProvider, ResolvedJavaMethod callTargetCallDirect, ResolvedJavaMethod callBoundary,
                        ResolvedJavaMethod callIndirectMethod) {
            this.callTargetCallDirect = callTargetCallDirect;
            this.callBoundary = callBoundary;
            this.truffleCallNodeToInvoke = EconomicMap.create();
            this.truffleMetaAccessProvider = truffleMetaAccessProvider;
            this.callIndirectMethod = callIndirectMethod;

        }

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext builder, ResolvedJavaMethod original, ValueNode[] arguments) {
            InlineInfo inlineInfo = super.shouldInlineInvoke(builder, original, arguments);
            if (original.equals(callTargetCallDirect)) {
                ValueNode arg0 = arguments[1];
                if (!arg0.isConstant()) {
                    GraalError.shouldNotReachHere("The direct call node does not resolve to a constant!");
                }
                lastDirectCallNode = (JavaConstant) arg0.asConstant();
            }
            if (original.equals(callIndirectMethod)) {
                indirectCall = true;
            }
            return inlineInfo;
        }

        @Override
        public void notifyNotInlined(GraphBuilderContext b, ResolvedJavaMethod original, Invoke invoke) {
            if (original.equals(callBoundary)) {
                if (lastDirectCallNode == null) {
                    if (indirectCall) {
                        indirectCall = false;
                        indirectInvokes.add(invoke);
                    }
                    return;
                }
                TruffleCallNode truffleCallNode = truffleMetaAccessProvider.findCallNode(lastDirectCallNode);
                truffleCallNodeToInvoke.put(truffleCallNode, invoke);
                lastDirectCallNode = null;
            }
        }

        public EconomicMap<TruffleCallNode, Invoke> getTruffleCallNodeToInvoke() {
            return truffleCallNodeToInvoke;
        }

        public List<Invoke> getIndirectInvokes() {
            return indirectInvokes;
        }
    }
}
