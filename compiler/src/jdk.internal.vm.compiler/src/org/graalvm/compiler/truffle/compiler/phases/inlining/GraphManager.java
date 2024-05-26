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
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.debug.Indent;
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
import org.graalvm.compiler.truffle.compiler.PEAgnosticInlineInvokePlugin;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.PostPartialEvaluationSuite;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.compiler.TruffleDebugJavaMethod;
import org.graalvm.compiler.truffle.compiler.TruffleTierContext;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;

import com.oracle.truffle.compiler.TruffleCompilable;

import jdk.vm.ci.meta.ResolvedJavaMethod;

final class GraphManager {

    public static final int TRIVIAL_NODE_COUNT_LIMIT = 500;
    private final PartialEvaluator partialEvaluator;
    private final EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCacheForInlining;
    private final EconomicMap<TruffleCompilable, GraphManager.Entry> irCache = EconomicMap.create();
    private final TruffleTierContext rootContext;
    private final PostPartialEvaluationSuite postPartialEvaluationSuite;
    private final boolean useSize;

    GraphManager(PartialEvaluator partialEvaluator, PostPartialEvaluationSuite postPartialEvaluationSuite, TruffleTierContext rootContext) {
        this.partialEvaluator = partialEvaluator;
        this.postPartialEvaluationSuite = postPartialEvaluationSuite;
        this.rootContext = rootContext;
        this.graphCacheForInlining = partialEvaluator.getOrCreateEncodedGraphCache();
        this.useSize = TruffleCompilerOptions.InliningUseSize.getValue(rootContext.compilerOptions);
    }

    @SuppressWarnings("try")
    Entry pe(TruffleCompilable truffleAST) {
        Entry entry = irCache.get(truffleAST);
        if (entry == null) {
            // the guest scope represents the guest language method of truffle
            try (AutoCloseable guestScope = rootContext.debug.scope("Truffle", new TruffleDebugJavaMethod(rootContext.task, truffleAST))) {
                final PEAgnosticInlineInvokePlugin plugin = newPlugin();
                final TruffleTierContext context = newContext(truffleAST, false);
                try (Scope hostScope = context.debug.scope("CreateGraph", context.graph);
                                Indent indent = context.debug.logAndIndent("evaluate %s", context.graph);) {
                    partialEvaluator.doGraphPE(context, plugin, graphCacheForInlining);
                    context.graph.getAssumptions().record(new TruffleAssumption(context.getNodeRewritingAssumption(partialEvaluator.getProviders())));
                    StructuredGraph graphAfterPE = copyGraphForDebugDump(context);
                    postPartialEvaluationSuite.apply(context.graph, context);
                    entry = new Entry(context.graph, plugin, graphAfterPE, useSize ? NodeCostUtil.computeGraphSize(context.graph) : -1);
                    context.debug.dump(DebugContext.INFO_LEVEL, context.graph, "After PE Tier");
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

    private TruffleTierContext newContext(TruffleCompilable truffleAST, boolean finalize) {
        return new TruffleTierContext(
                        partialEvaluator,
                        rootContext.compilerOptions,
                        rootContext.debug,
                        truffleAST,
                        finalize ? partialEvaluator.getCallDirect() : partialEvaluator.inlineRootForCallTarget(truffleAST),
                        rootContext.compilationId,
                        rootContext.log,
                        rootContext.task,
                        rootContext.handler);
    }

    private PEAgnosticInlineInvokePlugin newPlugin() {
        return new PEAgnosticInlineInvokePlugin(partialEvaluator);
    }

    TruffleTierContext rootContext() {
        return rootContext;
    }

    Entry peRoot() {
        final PEAgnosticInlineInvokePlugin plugin = newPlugin();
        partialEvaluator.doGraphPE(rootContext, plugin, graphCacheForInlining);
        StructuredGraph graphAfterPE = copyGraphForDebugDump(rootContext);
        postPartialEvaluationSuite.apply(rootContext.graph, rootContext);
        rootContext.debug.dump(DebugContext.BASIC_LEVEL, rootContext.graph, "After PE Tier");
        return new Entry(rootContext.graph, plugin, graphAfterPE, useSize ? NodeCostUtil.computeGraphSize(rootContext.graph) : -1);
    }

    UnmodifiableEconomicMap<Node, Node> doInline(Invoke invoke, StructuredGraph ir, TruffleCompilable truffleAST, InliningUtil.InlineeReturnAction returnAction) {
        return InliningUtil.inline(invoke, ir, true, partialEvaluator.inlineRootForCallTarget(truffleAST),
                        "cost-benefit analysis", AgnosticInliningPhase.class.getName(), returnAction);
    }

    void finalizeGraph(Invoke invoke, TruffleCompilable truffleAST) {
        final TruffleTierContext context = newContext(truffleAST, true);
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
