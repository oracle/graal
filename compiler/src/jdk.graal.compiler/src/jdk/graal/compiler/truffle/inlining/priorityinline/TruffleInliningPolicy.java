/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.inlining.priorityinline;

import java.util.EnumSet;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.graal.compiler.core.common.GraalBailoutException;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Scope;
import jdk.graal.compiler.hotspot.HotSpotCompilationIdentifier;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.priorityinline.data.BenefitKind;
import jdk.graal.compiler.phases.common.priorityinline.CallTree;
import jdk.graal.compiler.phases.common.priorityinline.Expander;
import jdk.graal.compiler.phases.common.priorityinline.GraphCache;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.IndirectNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;
import jdk.graal.compiler.truffle.PartialEvaluator;
import jdk.graal.compiler.truffle.PerformanceInformationHandler;
import jdk.graal.compiler.truffle.TruffleCompilation;
import jdk.graal.compiler.truffle.TruffleCompilerImpl;
import jdk.graal.compiler.truffle.TruffleDebugJavaMethod;
import jdk.graal.compiler.truffle.TruffleTierContext;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment.TruffleRuntimeScope;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilationIdentifier;
import jdk.graal.compiler.truffle.phases.TruffleTier;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

public class TruffleInliningPolicy extends Expander.DefaultPolicy {

    public TruffleInliningPolicy() {
    }

    @SuppressWarnings("try")
    private static StructuredGraph createTruffleMethodGraph(TruffleHostEnvironment env, CallTree callTree, ConstantNode constantNode) {
        JavaConstant constant = constantNode.asJavaConstant();
        TruffleCompilerRuntime runtime = env.runtime();
        TruffleCompilable compilable = runtime.asCompilableTruffleAST(constant);
        if (compilable == null) {
            return null;
        }
        StructuredGraph rootGraph = callTree.root().getReadonlySubgraph();
        if (TruffleCompilation.isTruffleCompilation(rootGraph)) {
            // we do not inline here for Truffle compilations because Truffle has its own inlining
            // passes that have already or will take care of this.
            return null;
        }
        TruffleCompilerImpl compiler = env.getTruffleCompiler(compilable);
        TruffleTier truffleTier = compiler.getTruffleTier();
        PartialEvaluator partialEvaluator = compiler.getPartialEvaluator();

        HotSpotCompilationRequest request;
        if (rootGraph.compilationId() instanceof HotSpotCompilationIdentifier hotSpotId) {
            request = hotSpotId.getRequest();
        } else {
            throw new AssertionError("Expected HotSpot compilation.");
        }
        OptionValues compilerOptions = compiler.getOrCreateCompilerOptions(compilable);

        TruffleCompilationTask task = new TruffleHostToGuestCompilationTask();
        HotSpotTruffleCompilationIdentifier compilationId = new HotSpotTruffleCompilationIdentifier(request, task, compilable);
        DebugContext debug = rootGraph.getDebug();
        try (PerformanceInformationHandler handler = PerformanceInformationHandler.install(runtime, compilerOptions);
                        Scope scope = debug.scope("Truffle", new TruffleDebugJavaMethod(task, compilable))) {

            SpeculationLog log = rootGraph.getSpeculationLog();
            TruffleTierContext context = TruffleTierContext.createInitialContext(partialEvaluator,
                            compilerOptions, debug, compilable,
                            compilationId, log, new TruffleHostToGuestCompilationTask(), handler);
            truffleTier.apply(context.graph, context);
            return context.graph;
        } catch (GraalBailoutException t) {
            /*
             * A bailout in the truffle compilation means that the compilation is not yet ready for
             * compilation or the compilation bailed out for other reasons, e..g if the graph was
             * too big to compile. In such a case we materialize the call-site.
             */
            return null;
        } catch (Throwable t) {
            throw debug.handle(t);
        }
    }

    private static SubgraphNode expandAsTruffleSubgraph(TruffleHostEnvironment env, CallTree callTree, CallTreeNode node, ConstantNode receiver) {
        StructuredGraph graph = createTruffleMethodGraph(env, callTree, receiver);
        if (graph == null) {
            return null;
        }
        GraphCache.Ref<ResolvedJavaMethod, StructuredGraph> subgraphRef = callTree.getGraphCache(node.isInOOMEProtectedInlineContext()).createNonCounted(graph);
        EnumSet<BenefitKind> benefits = EnumSet.of(BenefitKind.NewAllocation, BenefitKind.Type, BenefitKind.Devirtualization);
        ResolvedJavaMethod targetMethod = graph.method();
        SubgraphNode subgraphNode = callTree.add(new SubgraphNode(null, node.invoke(), node.getFrequency(), subgraphRef, true, targetMethod, null, null, benefits, false));
        // TODO (yz) subgraphNode.createImmediateChildren();
        return subgraphNode;
    }

    private static boolean isOptimizedCallTargetCall(TruffleHostEnvironment env, ResolvedJavaMethod method) {
        return method.equals(env.types().OptimizedCallTarget_call);
    }

    @Override
    @SuppressWarnings("try")
    public boolean enhanceIndirectNode(IndirectNode node) {
        MethodCallTargetNode callTarget = (MethodCallTargetNode) node.invoke().callTarget();
        TruffleHostEnvironment env = TruffleHostEnvironment.get(callTarget.targetMethod());
        if (env == null) {
            return false;
        }
        boolean isTruffleCall = isOptimizedCallTargetCall(env, callTarget.targetMethod());
        ValueNode receiver = callTarget.receiver();
        if (isTruffleCall && receiver != null && receiver.isConstant()) {
            try (TruffleRuntimeScope scope = env.openTruffleRuntimeScope()) {
                SubgraphNode truffleMethod = expandAsTruffleSubgraph(env, node.callTree(), node, (ConstantNode) receiver);
                if (truffleMethod != null) {
                    node.replaceAtPredecessor(truffleMethod);
                    node.safeDelete();
                    truffleMethod.callTree().restoreSubtreeInvariants(truffleMethod, false);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isSpecialCallTarget(Invoke invoke) {
        MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
        ValueNode receiver = callTarget.receiver();
        ResolvedJavaMethod method = callTarget.targetMethod();
        TruffleHostEnvironment env = TruffleHostEnvironment.get(method);
        if (env == null) {
            return false;
        }
        return isOptimizedCallTargetCall(env, method) && receiver != null;
    }

    @Override
    @SuppressWarnings("try")
    public CallTreeNode expandSpecialTarget(CallTree callTree, CutoffNode node) {
        MethodCallTargetNode callTarget = (MethodCallTargetNode) node.invoke().callTarget();
        ValueNode receiver = callTarget.receiver();
        if (receiver.isConstant()) {
            TruffleHostEnvironment env = TruffleHostEnvironment.get(callTarget.targetMethod());
            if (env != null) {
                try (TruffleRuntimeScope scope = env.openTruffleRuntimeScope()) {
                    SubgraphNode subGraph = expandAsTruffleSubgraph(env, callTree, node, (ConstantNode) receiver);
                    if (subGraph != null) {
                        return subGraph;
                    }
                }
            }
        }
        return callTree.createIndirectChild(node.parent(), node.invoke(), node.getFrequency());
    }

    private static final class TruffleHostToGuestCompilationTask implements TruffleCompilationTask {
        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isLastTier() {
            return true;
        }

        @Override
        public boolean hasNextTier() {
            return false;
        }
    }
}
