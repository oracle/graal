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
package jdk.graal.compiler.truffle.phases.inlining;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.truffle.compiler.TruffleCompilable;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Scope;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.contract.NodeCostUtil;
import jdk.graal.compiler.truffle.PEAgnosticInlineInvokePlugin;
import jdk.graal.compiler.truffle.PartialEvaluator;
import jdk.graal.compiler.truffle.PostPartialEvaluationSuite;
import jdk.graal.compiler.truffle.TruffleCompilerOptions;
import jdk.graal.compiler.truffle.TruffleDebugJavaMethod;
import jdk.graal.compiler.truffle.TruffleTierContext;
import jdk.graal.compiler.truffle.nodes.TruffleAssumption;
import jdk.vm.ci.meta.ResolvedJavaMethod;

final class GraphManager {

    public static final int TRIVIAL_NODE_COUNT_LIMIT = 500;
    private final PartialEvaluator partialEvaluator;
    private final EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCacheForInlining;
    private final EconomicMap<TruffleCompilable, GraphManager.Entry> irCache = EconomicMap.create();
    private final TruffleTierContext rootContext;
    private final PostPartialEvaluationSuite postPartialEvaluationSuite;
    private final boolean useSize;

    GraphManager(PostPartialEvaluationSuite postPartialEvaluationSuite, TruffleTierContext rootContext) {
        this.partialEvaluator = rootContext.partialEvaluator;
        this.postPartialEvaluationSuite = postPartialEvaluationSuite;
        this.rootContext = rootContext;
        this.graphCacheForInlining = partialEvaluator.getOrCreateEncodedGraphCache();
        this.useSize = TruffleCompilerOptions.InliningUseSize.getValue(rootContext.compilerOptions);
    }

    TruffleTierContext rootContext() {
        return rootContext;
    }

    @SuppressWarnings("try")
    Entry pe(TruffleCompilable truffleAST) {
        Entry entry = irCache.get(truffleAST);
        if (entry == null) {
            // the guest scope represents the guest language method of truffle
            try (AutoCloseable guestScope = rootContext.debug.scope("Truffle", new TruffleDebugJavaMethod(rootContext.task, truffleAST))) {
                final TruffleTierContext context = rootContext.createInlineContext(truffleAST);
                try (Scope hostScope = context.debug.scope("CreateGraph", context.graph);
                                Indent indent = context.debug.logAndIndent("evaluate %s", context.graph);) {
                    TruffleAssumption nodeRewritingAssumption = new TruffleAssumption(context.getNodeRewritingAssumption(partialEvaluator.getProviders()));
                    entry = pe(context, nodeRewritingAssumption, DebugContext.INFO_LEVEL);
                } catch (Throwable e) {
                    throw context.debug.handle(e);
                }
            } catch (Throwable e) {
                throw rootContext.debug.handle(e);
            }
            irCache.put(truffleAST, entry);
        }
        return entry;
    }

    Entry peRoot() {
        return pe(rootContext, null, DebugContext.BASIC_LEVEL);
    }

    private Entry pe(TruffleTierContext context, TruffleAssumption nodeRewritingAssumption, int dumpLevel) {
        final PEAgnosticInlineInvokePlugin plugin = new PEAgnosticInlineInvokePlugin(partialEvaluator);
        partialEvaluator.doGraphPE(context, plugin, graphCacheForInlining);
        if (nodeRewritingAssumption != null) {
            context.graph.getAssumptions().record(nodeRewritingAssumption);
        }
        final StructuredGraph graphAfterPE = copyGraphForDebugDump(context);
        postPartialEvaluationSuite.apply(context.graph, context);
        context.debug.dump(dumpLevel, context.graph, "After PE Tier");
        return new Entry(context.graph, plugin, graphAfterPE, useSize ? NodeCostUtil.computeGraphSize(context.graph) : -1);
    }

    UnmodifiableEconomicMap<Node, Node> doInline(Invoke invoke, StructuredGraph ir, TruffleCompilable truffleAST, InliningUtil.InlineeReturnAction returnAction) {
        return InliningUtil.inline(invoke, ir, true, partialEvaluator.inlineRootForCallTarget(truffleAST),
                        "cost-benefit analysis", AgnosticInliningPhase.class.getName(), returnAction);
    }

    void finalizeGraph(Invoke invoke, TruffleCompilable truffleAST) {
        final TruffleTierContext context = rootContext.createFinalizationContext(truffleAST);
        partialEvaluator.doGraphPE(context, new InlineInvokePlugin() {
            @Override
            public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                return partialEvaluator.asInlineInfo(method);
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
        final EconomicSet<Invoke> directInvokes;
        final List<Invoke> indirectInvokes;
        final boolean trivial;
        // Populated only when debug dump is enabled with debug dump level >= info.
        final StructuredGraph graphAfterPEForDebugDump;
        // Only populated if TruffleCompilerOptions.InliningUseSize is true
        final int graphSize;

        Entry(StructuredGraph graph, PEAgnosticInlineInvokePlugin plugin, StructuredGraph graphAfterPEForDebugDump, int graphSize) {
            this.graph = graph;
            this.directInvokes = plugin.getDirectInvokes();
            this.indirectInvokes = plugin.getIndirectInvokes();
            this.trivial = directInvokes.isEmpty() &&
                            indirectInvokes.isEmpty() &&
                            graph.getNodes(LoopBeginNode.TYPE).count() == 0 &&
                            graph.getNodeCount() < TRIVIAL_NODE_COUNT_LIMIT;
            this.graphAfterPEForDebugDump = graphAfterPEForDebugDump;
            this.graphSize = graphSize;
        }
    }

}
