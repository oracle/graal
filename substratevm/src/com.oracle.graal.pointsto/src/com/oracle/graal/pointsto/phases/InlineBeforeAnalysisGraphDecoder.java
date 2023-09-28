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

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.extended.UnsafeAccessNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.replacements.PEGraphDecoder;
import org.graalvm.compiler.replacements.nodes.MethodHandleWithExceptionNode;
import org.graalvm.compiler.replacements.nodes.ResolvedMethodHandleCallTargetNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class InlineBeforeAnalysisGraphDecoder extends PEGraphDecoder {

    public class InlineBeforeAnalysisMethodScope extends PEMethodScope {

        public final InlineBeforeAnalysisPolicy.AbstractPolicyScope policyScope;

        private boolean inliningAborted;

        /*
         * We temporarily track all graphs actually encoded (i.e., not aborted) so that all
         * recording can be performed afterwards.
         */
        private final EconomicSet<EncodedGraph> encodedGraphs;

        InlineBeforeAnalysisMethodScope(StructuredGraph targetGraph, PEMethodScope caller, LoopScope callerLoopScope, EncodedGraph encodedGraph, ResolvedJavaMethod method,
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
                policyScope = policy.openCalleeScope(cast(caller).policyScope, bb.getMetaAccess(), method, constArgsWithReceiver, invokeData.intrinsifiedMethodHandle);
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

    private Field dmhStaticAccessorOffsetField;
    private Field dmhStaticAccessorBaseField;
    private AnalysisField dmhStaticAccessorOffsetAnalysisField;
    private AnalysisField dmhStaticAccessorBaseAnalysisField;

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
        var methodScope = cast(ms);
        methodScope.encodedGraphs.add(methodScope.encodedGraph);
        for (var encodedGraph : methodScope.encodedGraphs) {
            super.recordGraphElements(encodedGraph);
        }
    }

    @Override
    protected PEMethodScope createMethodScope(StructuredGraph targetGraph, PEMethodScope caller, LoopScope callerLoopScope, EncodedGraph encodedGraph, ResolvedJavaMethod method, InvokeData invokeData,
                    int inliningDepth, ValueNode[] arguments) {
        return new InlineBeforeAnalysisMethodScope(targetGraph, caller, callerLoopScope, encodedGraph, method, invokeData, inliningDepth, arguments);
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
        Node canonical = node;
        if (node instanceof UnsafeAccessNode unsafeAccess) {
            canonical = canonicalizeUnsafeAccess(unsafeAccess);
        }
        canonical = super.canonicalizeFixedNode(methodScope, loopScope, canonical);
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
        return policy.shouldOmitIntermediateMethodInState(method);
    }

    @SuppressWarnings("unused")
    protected Node doCanonicalizeFixedNode(InlineBeforeAnalysisMethodScope methodScope, LoopScope loopScope, Node node) {
        return node;
    }

    /**
     * Try to replace unsafe field accesses by offset via {@code DirectMethodHandle$StaticAccessor}
     * with accesses to the actual target fields which can be constant-folded. This enables us to
     * further simplify and inline through internal usages of {@code StaticAccessor} in method
     * handle code itself, such as that generated by {@code InnerClassLambdaMetafactory}. A
     * corresponding substitution recomputes the offsets stored in {@code StaticAccessor} objects to
     * match those in the image, but it applies only much later.
     *
     * @see #canonicalizeIsNull
     */
    private Node canonicalizeUnsafeAccess(UnsafeAccessNode node) {
        if (!(node.isCanonicalizable() && node.offset() instanceof LoadFieldNode offsetLoad && offsetLoad.object() != null && offsetLoad.object().isJavaConstant())) {
            return node;
        }
        ensureDMHStaticAccessorFieldsInitialized();
        if (!offsetLoad.field().equals(dmhStaticAccessorOffsetAnalysisField)) {
            return node;
        }
        JavaConstant accessorConstant = offsetLoad.object().asJavaConstant();
        Object accessor = bb.getSnippetReflectionProvider().asObject(Object.class, accessorConstant);
        long offset;
        Class<?> clazz; // HotSpot-specific: field holder Class object as Unsafe.staticFieldBase()
        try {
            offset = dmhStaticAccessorOffsetField.getLong(accessor);
            clazz = (Class<?>) dmhStaticAccessorBaseField.get(accessor);
        } catch (IllegalAccessException e) {
            throw AnalysisError.shouldNotReachHere(e);
        }
        if (clazz == null) {
            return node;
        }
        ResolvedJavaType type = GraalAccess.getOriginalProviders().getMetaAccess().lookupJavaType(clazz);
        ResolvedJavaField hostField = UnsafeAccessNode.findStaticFieldWithOffset(type, offset, node.accessKind());
        if (hostField == null) {
            return node;
        }
        AnalysisField field = bb.getUniverse().lookup(hostField);
        if (field.isInternal() || field.getJavaKind() != node.accessKind()) {
            return node;
        }
        return node.cloneAsFieldAccess(field);
    }

    @Override
    protected Node handleFloatingNodeAfterAdd(MethodScope s, LoopScope loopScope, Node node) {
        Node canonical = node;
        if (canonical instanceof IsNullNode isNull) {
            canonical = canonicalizeIsNull(isNull);
        }
        if (canonical != node) {
            canonical.setNodeSourcePosition(node.getNodeSourcePosition());
            node.replaceAtUsagesAndDelete(canonical);
        }
        return super.handleFloatingNodeAfterAdd(s, loopScope, canonical);
    }

    /**
     * Constant-fold null checks of {@code DirectMethodHandle$StaticAccessor.staticBase}. This
     * enables us to further simplify and inline through internal usages of {@code StaticAccessor}
     * in method handle code itself, such as that generated by {@code InnerClassLambdaMetafactory}.
     * A corresponding substitution computes the final value of {@code staticBase} in the image, but
     * it applies only much later, and we know that it will never be {@code null}.
     *
     * @see #canonicalizeUnsafeAccess
     */
    private Node canonicalizeIsNull(IsNullNode node) {
        if (!(node.getValue() instanceof LoadFieldNode fieldLoad && fieldLoad.object() != null && fieldLoad.object().isJavaConstant())) {
            return node;
        }
        ensureDMHStaticAccessorFieldsInitialized();
        if (!fieldLoad.field().equals(dmhStaticAccessorBaseAnalysisField)) {
            return node;
        }
        // The base is always non-null, which we also assume in our field substitution.
        return LogicConstantNode.contradiction(node.graph());
    }

    private void ensureDMHStaticAccessorFieldsInitialized() {
        if (dmhStaticAccessorOffsetField == null) {
            assert dmhStaticAccessorBaseField == null && dmhStaticAccessorOffsetAnalysisField == null && dmhStaticAccessorBaseAnalysisField == null;
            Class<?> staticAccessorClass = ReflectionUtil.lookupClass(false, "java.lang.invoke.DirectMethodHandle$StaticAccessor");
            dmhStaticAccessorOffsetField = ReflectionUtil.lookupField(staticAccessorClass, "staticOffset");
            dmhStaticAccessorBaseField = ReflectionUtil.lookupField(staticAccessorClass, "staticBase");
            dmhStaticAccessorOffsetAnalysisField = bb.getMetaAccess().lookupJavaField(dmhStaticAccessorOffsetField);
            dmhStaticAccessorBaseAnalysisField = bb.getMetaAccess().lookupJavaField(dmhStaticAccessorBaseField);
        }
    }

    @Override
    protected void handleNonInlinedInvoke(MethodScope methodScope, LoopScope loopScope, InvokeData invokeData) {
        maybeAbortInlining(methodScope, loopScope, invokeData.invoke.asNode());
        super.handleNonInlinedInvoke(methodScope, loopScope, invokeData);
    }

    protected void maybeAbortInlining(MethodScope ms, @SuppressWarnings("unused") LoopScope loopScope, Node node) {
        InlineBeforeAnalysisMethodScope methodScope = cast(ms);
        if (!methodScope.inliningAborted && methodScope.isInlinedMethod()) {
            if (graph.getDebug().isLogEnabled()) {
                graph.getDebug().logv("  ".repeat(methodScope.inliningDepth) + "  node " + node + ": " + methodScope.policyScope);
            }
            if (!methodScope.policyScope.processNode(bb.getMetaAccess(), methodScope.method, node)) {
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
            AnalysisError.guarantee(inlineScope.policyScope.allowAbort(), "Unexpected abort: %s", inlineScope);
            if (callerScope.policyScope != null) {
                callerScope.policyScope.abortCalleeScope(inlineScope.policyScope);
            }
            if (invokeData.invokePredecessor.next() != null) {
                killControlFlowNodes(inlineScope, invokeData.invokePredecessor.next());
                assert invokeData.invokePredecessor.next() == null : "Successor must have been a fixed node created in the aborted scope, which is deleted now";
            }
            invokeData.invokePredecessor.setNext(invokeData.invoke.asFixedNode());

            if (inlineScope.exceptionPlaceholderNode != null) {
                assert invokeData.invoke instanceof InvokeWithExceptionNode;
                assert lookupNode(callerLoopScope, invokeData.exceptionOrderId) == inlineScope.exceptionPlaceholderNode;
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
            assert !cur.isDeleted();
            assert graph.isNew(inlineScope.methodStartMark, cur);

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
    protected InlineBeforeAnalysisMethodScope cast(MethodScope methodScope) {
        return (InlineBeforeAnalysisMethodScope) methodScope;
    }

    @Override
    protected FixedWithNextNode afterMethodScopeCreation(PEMethodScope is, FixedWithNextNode predecessor) {
        InlineBeforeAnalysisMethodScope inlineScope = cast(is);
        var sourcePosition = inlineScope.invokeData.invoke.asNode().getNodeSourcePosition();
        return policy.processInvokeArgs(inlineScope.method, predecessor, inlineScope.getArguments(), sourcePosition);
    }
}
