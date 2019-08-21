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

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.common.CallNodeProvider;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

final class GraphManager {

    private final PartialEvaluator partialEvaluator;
    private final StructuredGraph rootIR;
    private final CallNodeProvider callNodeProvider;
    private final EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCacheForInlining = EconomicMap.create();
    private final EconomicMap<CompilableTruffleAST, GraphManager.Entry> irCache = EconomicMap.create();

    GraphManager(StructuredGraph ir, PartialEvaluator partialEvaluator, CallNodeProvider callNodeProvider) {
        this.partialEvaluator = partialEvaluator;
        this.rootIR = ir;
        this.callNodeProvider = callNodeProvider;
    }

    static ResolvedJavaMethod findRequiredMethod(ResolvedJavaType declaringClass, ResolvedJavaMethod[] methods, String name, String descriptor) {
        for (ResolvedJavaMethod method : methods) {
            if (method.getName().equals(name) && method.getSignature().toMethodDescriptor().equals(descriptor)) {
                return method;
            }
        }
        throw new NoSuchMethodError(declaringClass.toJavaName() + "." + name + descriptor);
    }

    boolean contains(CompilableTruffleAST truffleAST) {
        return irCache.containsKey(truffleAST);
    }

    Entry get(CompilableTruffleAST truffleAST) {
        Entry entry = irCache.get(truffleAST);
        if (entry == null) {
            Cancellable cancellable = rootIR.getCancellable();
            SpeculationLog log = rootIR.getSpeculationLog();
            DebugContext debug = rootIR.getDebug();
            StructuredGraph.AllowAssumptions allowAssumptions = rootIR.getAssumptions() != null ? StructuredGraph.AllowAssumptions.YES : StructuredGraph.AllowAssumptions.NO;
            CompilationIdentifier id = rootIR.compilationId();
            final PEAgnosticInlineInvokePlugin plugin = new PEAgnosticInlineInvokePlugin(partialEvaluator.getProviders(), callNodeProvider);
            StructuredGraph graph = partialEvaluator.createGraphForInlining(debug, truffleAST, callNodeProvider, plugin, allowAssumptions, id, log, cancellable,
                            graphCacheForInlining);
            final EconomicMap<TruffleCallNode, Invoke> truffleCallNodeToInvoke = plugin.getTruffleCallNodeToInvoke();
            entry = new GraphManager.Entry(graph, truffleCallNodeToInvoke);
            irCache.put(truffleAST, entry);
        }
        return entry;
    }

    EconomicMap<TruffleCallNode, Invoke> peRoot(CompilableTruffleAST truffleAST) {
        final PEAgnosticInlineInvokePlugin plugin = new PEAgnosticInlineInvokePlugin(partialEvaluator.getProviders(), callNodeProvider);
        partialEvaluator.parseRootGraphForInlining(truffleAST, rootIR, callNodeProvider, plugin, graphCacheForInlining);
        return plugin.getTruffleCallNodeToInvoke();
    }

    UnmodifiableEconomicMap<Node, Node> doInline(Invoke invoke, StructuredGraph ir, CompilableTruffleAST truffleAST) {
        return InliningUtil.inline(invoke, ir, true, partialEvaluator.inlineRootForCallTargetAgnostic(truffleAST),
                        "cost-benefit analysis", "AgnosticInliningPhase");
    }

    public CoreProviders getCoreProviders() {
        return partialEvaluator.getProviders();
    }

    static class Entry {
        final StructuredGraph graph;
        final EconomicMap<TruffleCallNode, Invoke> truffleCallNodeToInvoke;

        Entry(StructuredGraph graph, EconomicMap<TruffleCallNode, Invoke> truffleCallNodeToInvoke) {
            this.graph = graph;
            this.truffleCallNodeToInvoke = truffleCallNodeToInvoke;
        }
    }

    private static class PEAgnosticInlineInvokePlugin extends PartialEvaluator.PEInlineInvokePlugin {
        private final EconomicMap<TruffleCallNode, Invoke> truffleCallNodeToInvoke;
        private final CallNodeProvider callNodeProvider;
        private final ResolvedJavaMethod callTargetCallDirect;
        private final ResolvedJavaMethod callBoundary;
        private JavaConstant lastDirectCallNode;

        PEAgnosticInlineInvokePlugin(Providers providers, CallNodeProvider callNodeProvider) {
            this.truffleCallNodeToInvoke = EconomicMap.create();
            this.callNodeProvider = callNodeProvider;
            TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
            MetaAccessProvider metaAccess = providers.getMetaAccess();
            ResolvedJavaType callTargetType = runtime.resolveType(metaAccess, "org.graalvm.compiler.truffle.runtime.OptimizedCallTarget");
            this.callTargetCallDirect = findRequiredMethod(callTargetType, callTargetType.getDeclaredMethods(), "callDirect",
                            "(Lcom/oracle/truffle/api/nodes/Node;[Ljava/lang/Object;)Ljava/lang/Object;");
            this.callBoundary = findRequiredMethod(callTargetType, callTargetType.getDeclaredMethods(), "callBoundary",
                            "([Ljava/lang/Object;)Ljava/lang/Object;");

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
            return inlineInfo;
        }

        @Override
        public void notifyNotInlined(GraphBuilderContext b, ResolvedJavaMethod original, Invoke invoke) {
            if (original.equals(callBoundary)) {
                if (lastDirectCallNode == null) {
                    // TODO: Handle indirect call
                    return;
                }
                TruffleCallNode truffleCallNode = callNodeProvider.findCallNode(lastDirectCallNode);
                truffleCallNodeToInvoke.put(truffleCallNode, invoke);
                lastDirectCallNode = null;
            }
        }

        public EconomicMap<TruffleCallNode, Invoke> getTruffleCallNodeToInvoke() {
            return truffleCallNodeToInvoke;
        }
    }
}
