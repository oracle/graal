/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.phases;

import static com.oracle.graal.pointsto.phases.InlineBeforeAnalysisGraphDecoder.InlineBeforeAnalysisMethodScope.recordInlined;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicSet;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy.AbstractPolicyScope;

import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.replacements.PEGraphDecoder;
import jdk.graal.compiler.replacements.nodes.MethodHandleWithExceptionNode;
import jdk.graal.compiler.replacements.nodes.ResolvedMethodHandleCallTargetNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class InlineBeforeAnalysisGraphDecoder extends PEGraphDecoder {

    public class InlineBeforeAnalysisMethodScope extends PEMethodScope {

        public final AbstractPolicyScope policyScope;

        private boolean inliningAborted;

        /*
         * We temporarily track all graphs actually encoded (i.e., not aborted) so that all
         * recording can be performed afterwards.
         */
        private final EconomicSet<EncodedGraph> encodedGraphs;

        InlineBeforeAnalysisMethodScope(StructuredGraph targetGraph, PEMethodScope caller, LoopScope callerLoopScope, EncodedGraph encodedGraph, AnalysisMethod method,
                        InvokeData invokeData, int inliningDepth, ValueNode[] arguments) {
            super(targetGraph, caller, callerLoopScope, encodedGraph, method, invokeData, inliningDepth, arguments);

            if (caller == null) {
                /* The root method that we are decoding, i.e., inlining into. */
                policyScope = policy.createRootScope();
                if (graph.getDebug().isLogEnabled()) {
                    graph.getDebug().logv("  ".repeat(inliningDepth) + "createRootScope for " + method.format("%H.%n(%p)") + ": " + policyScope);
                }
            } else {
                boolean[] constArgsWithReceiver = new boolean[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    constArgsWithReceiver[i] = arguments[i].isConstant();
                }
                policyScope = policy.openCalleeScope(cast(caller).policyScope, method);
                if (graph.getDebug().isLogEnabled()) {
                    graph.getDebug().logv("  ".repeat(inliningDepth) + "openCalleeScope for " + method.format("%H.%n(%p)") + ": " + policyScope);
                }
            }
            encodedGraphs = EconomicSet.create();
        }

        static void recordInlined(InlineBeforeAnalysisMethodScope callerScope, InlineBeforeAnalysisMethodScope calleeScope) {
            /*
             * Update caller's encoded graphs
             */
            var callerEncodedGraphs = callerScope.encodedGraphs;
            callerEncodedGraphs.addAll(calleeScope.encodedGraphs);
            callerEncodedGraphs.add(calleeScope.encodedGraph);
        }
    }

    static final class InlineBeforeAnalysisInlineInvokePlugin implements InlineInvokePlugin {

        private final InlineBeforeAnalysisPolicy policy;

        InlineBeforeAnalysisInlineInvokePlugin(InlineBeforeAnalysisPolicy policy) {
            this.policy = policy;
        }

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod m, ValueNode[] args) {
            AnalysisMethod method = (AnalysisMethod) m;

            AbstractPolicyScope policyScope = cast(((PENonAppendGraphBuilderContext) b).methodScope).policyScope;
            if (policy.shouldInlineInvoke(b, policyScope, method, args)) {
                return policy.createInvokeInfo(method);
            } else {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            }
        }
    }

    protected final BigBang bb;
    protected final InlineBeforeAnalysisPolicy policy;

    public InlineBeforeAnalysisGraphDecoder(BigBang bb, InlineBeforeAnalysisPolicy policy, StructuredGraph graph, HostedProviders providers, LoopExplosionPlugin loopExplosionPlugin) {
        super(AnalysisParsedGraph.HOST_ARCHITECTURE, graph, providers, loopExplosionPlugin,
                        providers.getGraphBuilderPlugins().getInvocationPlugins(),
                        new InlineInvokePlugin[]{new InlineBeforeAnalysisInlineInvokePlugin(policy)},
                        null, policy.nodePlugins, null, null,
                        new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), policy.needsExplicitExceptions(), false);
        this.bb = bb;
        this.policy = policy;

        if (graph.getDebug().isLogEnabled()) {
            graph.getDebug().logv("InlineBeforeAnalysis: decoding " + graph.method().format("%H.%n(%p)"));
        }
    }

    @Override
    protected InvocationPlugin getInvocationPlugin(ResolvedJavaMethod targetMethod) {
        if (policy.tryInvocationPlugins()) {
            return super.getInvocationPlugin(targetMethod);
        }
        return null;
    }

    @Override
    protected void cleanupGraph(MethodScope ms) {
        super.cleanupGraph(ms);

        // at the very end we record all inlining
        InlineBeforeAnalysisMethodScope methodScope = cast(ms);
        methodScope.encodedGraphs.add(methodScope.encodedGraph);
        for (var encodedGraph : methodScope.encodedGraphs) {
            super.recordGraphElements(encodedGraph);
        }
    }

    @Override
    protected PEMethodScope createMethodScope(StructuredGraph targetGraph, PEMethodScope caller, LoopScope callerLoopScope, EncodedGraph encodedGraph, ResolvedJavaMethod method, InvokeData invokeData,
                    int inliningDepth, ValueNode[] arguments) {
        return new InlineBeforeAnalysisMethodScope(targetGraph, caller, callerLoopScope, encodedGraph, (AnalysisMethod) method, invokeData, inliningDepth, arguments);
    }

    @Override
    protected EncodedGraph lookupEncodedGraph(ResolvedJavaMethod method, BytecodeProvider intrinsicBytecodeProvider) {
        AnalysisMethod aMethod = (AnalysisMethod) method;
        return aMethod.ensureGraphParsed(bb).getEncodedGraph();
    }

    @Override
    protected Node addFloatingNode(MethodScope methodScope, LoopScope loopScope, Node node) {
        assert node.isUnregistered() : "If node is already in the graph, we would count it twice";
        maybeAbortInlining(methodScope, loopScope, node);
        return super.addFloatingNode(methodScope, loopScope, node);
    }

    @Override
    protected final Node canonicalizeFixedNode(MethodScope methodScope, LoopScope loopScope, Node node) {
        Node canonical = super.canonicalizeFixedNode(methodScope, loopScope, node);
        canonical = doCanonicalizeFixedNode(cast(methodScope), loopScope, canonical);
        /*
         * When no canonicalization was done, we check the node that was decoded (which is already
         * alive, but we know it was just decoded and therefore not checked yet).
         *
         * When canonicalization was done, we do not check an already alive node because that node
         * was already counted when it was added.
         */
        if (canonical == node || (canonical != null && canonical.isUnregistered())) {
            maybeAbortInlining(methodScope, loopScope, canonical);
        }
        return canonical;
    }

    @Override
    protected boolean shouldOmitIntermediateMethodInStates(ResolvedJavaMethod method) {
        return policy.shouldOmitIntermediateMethodInState((AnalysisMethod) method);
    }

    @SuppressWarnings("unused")
    protected Node doCanonicalizeFixedNode(InlineBeforeAnalysisMethodScope methodScope, LoopScope loopScope, Node node) {
        return node;
    }

    @Override
    protected void handleNonInlinedInvoke(MethodScope ms, LoopScope loopScope, InvokeData invokeData) {
        InlineBeforeAnalysisMethodScope methodScope = cast(ms);
        maybeAbortInlining(methodScope, loopScope, invokeData.invoke.asNode());

        if (!methodScope.inliningAborted && methodScope.isInlinedMethod()) {
            if (graph.getDebug().isLogEnabled()) {
                graph.getDebug().logv("  ".repeat(methodScope.inliningDepth) + "  nonInlinedInvoke " + invokeData.callTarget.targetMethod() + ": " + methodScope.policyScope);
            }
            if (!methodScope.policyScope.processNonInlinedInvoke(providers, invokeData.callTarget)) {
                abortInlining(methodScope);
            }
        }

        super.handleNonInlinedInvoke(methodScope, loopScope, invokeData);
    }

    protected void maybeAbortInlining(MethodScope ms, @SuppressWarnings("unused") LoopScope loopScope, Node node) {
        InlineBeforeAnalysisMethodScope methodScope = cast(ms);
        if (!methodScope.inliningAborted && methodScope.isInlinedMethod()) {
            if (graph.getDebug().isLogEnabled()) {
                graph.getDebug().logv("  ".repeat(methodScope.inliningDepth) + "  node " + node + ": " + methodScope.policyScope);
            }
            if (!methodScope.policyScope.processNode(bb.getMetaAccess(), (AnalysisMethod) methodScope.method, node)) {
                abortInlining(methodScope);
            }
        }
    }

    protected void abortInlining(InlineBeforeAnalysisMethodScope methodScope) {
        if (!methodScope.inliningAborted) {
            if (graph.getDebug().isLogEnabled()) {
                graph.getDebug().logv("  ".repeat(methodScope.inliningDepth) + "    abort!");
            }
            methodScope.inliningAborted = true;
        }
    }

    @Override
    protected LoopScope processNextNode(MethodScope ms, LoopScope loopScope) {
        InlineBeforeAnalysisMethodScope methodScope = cast(ms);
        if (methodScope.inliningAborted) {
            /*
             * Inlining is aborted, so stop processing this node and also clear the work-list of
             * nodes. Note that this only stops the creation of fixed nodes. Floating nodes are
             * decoded recursively starting from fixed nodes, and there is no good way to
             * immediately stop that decoding once the policy limit is exceeded.
             */
            loopScope.nodesToProcess.clear();
            return loopScope;
        }
        return super.processNextNode(methodScope, loopScope);
    }

    @Override
    protected LoopScope handleMethodHandle(MethodScope s, LoopScope loopScope, InvokableData<MethodHandleWithExceptionNode> invokableData) {
        MethodHandleWithExceptionNode node = invokableData.invoke;
        Node replacement = node.trySimplify(providers.getConstantReflection().getMethodHandleAccess());
        boolean intrinsifiedMethodHandle = (replacement != node);
        if (!intrinsifiedMethodHandle) {
            replacement = node.replaceWithInvoke().asNode();
        }

        InvokeWithExceptionNode invoke = (InvokeWithExceptionNode) replacement;
        registerNode(loopScope, invokableData.orderId, invoke, true, false);
        InvokeData invokeData = new InvokeData(invoke, invokableData.contextType, invokableData.orderId, -1, intrinsifiedMethodHandle, invokableData.stateAfterOrderId,
                        invokableData.nextOrderId, invokableData.exceptionOrderId, invokableData.exceptionStateOrderId, invokableData.exceptionNextOrderId);

        CallTargetNode callTarget;
        if (invoke.callTarget() instanceof ResolvedMethodHandleCallTargetNode t) {
            // This special CallTargetNode lowers itself back to the original target (e.g. linkTo*)
            // if the invocation hasn't been inlined, which we don't want for Native Image.
            callTarget = new MethodCallTargetNode(t.invokeKind(), t.targetMethod(), t.arguments().toArray(ValueNode.EMPTY_ARRAY), t.returnStamp(), t.getTypeProfile());
        } else {
            callTarget = (CallTargetNode) invoke.callTarget().copyWithInputs(false);
        }
        // handleInvoke() expects that CallTargetNode is not eagerly added to the graph
        invoke.callTarget().replaceAtUsagesAndDelete(null);
        invokeData.callTarget = callTarget;

        return handleInvokeWithCallTarget((PEMethodScope) s, loopScope, invokeData);
    }

    @Override
    protected void recordGraphElements(EncodedGraph encodedGraph) {
        /*
         * We temporarily delay recording graph elements, as at this point it is possible inlining
         * will be aborted.
         */
    }

    @Override
    protected void finishInlining(MethodScope is) {
        InlineBeforeAnalysisMethodScope inlineScope = cast(is);
        InlineBeforeAnalysisMethodScope callerScope = cast(inlineScope.caller);
        LoopScope callerLoopScope = inlineScope.callerLoopScope;
        InvokeData invokeData = inlineScope.invokeData;

        if (inlineScope.inliningAborted) {
            if (graph.getDebug().isLogEnabled()) {
                graph.getDebug().logv("  ".repeat(callerScope.inliningDepth) + "  aborted " + invokeData.callTarget.targetMethod().format("%H.%n(%p)") + ": " + inlineScope.policyScope);
            }
            if (callerScope.policyScope != null) {
                callerScope.policyScope.abortCalleeScope(inlineScope.policyScope);
            }
            if (invokeData.invokePredecessor.next() != null) {
                killControlFlowNodes(inlineScope, invokeData.invokePredecessor.next());
                assert invokeData.invokePredecessor.next() == null : "Successor must have been a fixed node created in the aborted scope, which is deleted now";
            }
            invokeData.invokePredecessor.setNext(invokeData.invoke.asFixedNode());

            if (inlineScope.exceptionPlaceholderNode != null) {
                assert invokeData.invoke instanceof InvokeWithExceptionNode : invokeData.invoke;
                assert lookupNode(callerLoopScope, invokeData.exceptionOrderId) == inlineScope.exceptionPlaceholderNode : inlineScope;
                registerNode(callerLoopScope, invokeData.exceptionOrderId, null, true, true);
                ValueNode exceptionReplacement = makeStubNode(callerScope, callerLoopScope, invokeData.exceptionOrderId);
                inlineScope.exceptionPlaceholderNode.replaceAtUsagesAndDelete(exceptionReplacement);
            }

            handleNonInlinedInvoke(callerScope, callerLoopScope, invokeData);
            return;
        }

        if (graph.getDebug().isLogEnabled()) {
            graph.getDebug().logv("  ".repeat(callerScope.inliningDepth) + "  committed " + invokeData.callTarget.targetMethod().format("%H.%n(%p)") + ": " + inlineScope.policyScope);
        }
        if (callerScope.policyScope != null) {
            callerScope.policyScope.commitCalleeScope(inlineScope.policyScope);
        }

        recordInlined(callerScope, inlineScope);

        NodeSourcePosition callerBytecodePosition = callerScope.getCallerNodeSourcePosition();
        Object reason = callerBytecodePosition != null ? callerBytecodePosition : callerScope.method;
        reason = reason == null ? graph.method() : reason;
        ((AnalysisMethod) invokeData.callTarget.targetMethod()).registerAsInlined(reason);

        super.finishInlining(inlineScope);
    }

    /**
     * Kill fixed nodes of structured control flow. Not as generic, but faster, than
     * {@link GraphUtil#killCFG}.
     *
     * We cannot kill unused floating nodes at this point, because we are still in the middle of
     * decoding caller graphs, so floating nodes of the caller that have no usage yet can get used
     * when decoding of the caller continues. Unused floating nodes are cleaned up by the next run
     * of the CanonicalizerPhase.
     */
    private void killControlFlowNodes(PEMethodScope inlineScope, FixedNode start) {
        Deque<Node> workList = null;
        Node cur = start;
        while (true) {
            assert !cur.isDeleted() : cur;
            assert graph.isNew(inlineScope.methodStartMark, cur) : cur;

            Node next = null;
            if (cur instanceof FixedWithNextNode) {
                next = ((FixedWithNextNode) cur).next();
            } else if (cur instanceof ControlSplitNode) {
                for (Node successor : cur.successors()) {
                    if (next == null) {
                        next = successor;
                    } else {
                        if (workList == null) {
                            workList = new ArrayDeque<>();
                        }
                        workList.push(successor);
                    }
                }
            } else if (cur instanceof AbstractEndNode) {
                next = ((AbstractEndNode) cur).merge();
            } else if (cur instanceof ControlSinkNode) {
                /* End of this control flow path. */
            } else {
                throw GraalError.shouldNotReachHereUnexpectedValue(cur); // ExcludeFromJacocoGeneratedReport
            }

            if (cur instanceof AbstractMergeNode) {
                for (ValueNode phi : ((AbstractMergeNode) cur).phis().snapshot()) {
                    phi.replaceAtUsages(null);
                    phi.safeDelete();
                }
            }

            cur.replaceAtPredecessor(null);
            cur.replaceAtUsages(null);
            cur.safeDelete();

            if (next != null) {
                cur = next;
            } else if (workList != null && !workList.isEmpty()) {
                cur = workList.pop();
            } else {
                return;
            }
        }
    }

    /**
     * The generic type of {@link InlineBeforeAnalysisPolicy} makes the policy implementation nice,
     * at the cost of this ugly cast.
     */
    @SuppressWarnings("unchecked")
    protected static InlineBeforeAnalysisMethodScope cast(MethodScope methodScope) {
        return (InlineBeforeAnalysisMethodScope) methodScope;
    }

    @Override
    protected FixedWithNextNode afterMethodScopeCreation(PEMethodScope is, FixedWithNextNode predecessor) {
        InlineBeforeAnalysisMethodScope inlineScope = cast(is);
        var sourcePosition = inlineScope.invokeData.invoke.asNode().getNodeSourcePosition();
        return policy.processInvokeArgs((AnalysisMethod) inlineScope.method, predecessor, inlineScope.getArguments(), sourcePosition);
    }
}
