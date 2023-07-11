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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.UnsafeAccessNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.replacements.PEGraphDecoder;
import org.graalvm.compiler.replacements.nodes.MethodHandleWithExceptionNode;
import org.graalvm.compiler.replacements.nodes.ResolvedMethodHandleCallTargetNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class InlineBeforeAnalysisGraphDecoder extends PEGraphDecoder {

    public class InlineBeforeAnalysisMethodScope extends PEMethodScope {

        public final InlineBeforeAnalysisPolicy.AbstractPolicyScope policyScope;

        private boolean inliningAborted;

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
                policyScope = policy.openCalleeScope(cast(caller).policyScope, bb.getMetaAccess(), method, invokeData.intrinsifiedMethodHandle);
                if (graph.getDebug().isLogEnabled()) {
                    graph.getDebug().logv("  ".repeat(inliningDepth) + "openCalleeScope for " + method.format("%H.%n(%p)") + ": " + policyScope);
                }
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
     * Method handles do unsafe field accesses with offsets from the hosting VM which we need to
     * transform to field accesses to intrinsify method handle calls in an effective way (or at all,
     * even). {@link UnsafeAccessNode#canonical} would call {@link AnalysisField#getOffset}, which
     * is deemed not safe in the general case and therefore not allowed. Here, however, we execute
     * before analysis and can assume that any field offsets originate in the hosting VM because we
     * do not assign our field offsets until after the analysis. Therefore, we can transform unsafe
     * accesses by accessing the hosting VM's types and fields, using code adapted from
     * {@link UnsafeAccessNode#canonical}. We cannot do the same for arrays because array offsets
     * with our own object layout can be computed early on (using {@code Unsafe}, even).
     */
    private Node canonicalizeUnsafeAccess(UnsafeAccessNode node) {
        if (!node.isCanonicalizable()) {
            return node;
        }
        JavaConstant offset = node.offset().asJavaConstant();
        JavaConstant object = node.object().asJavaConstant();
        if (offset == null || object == null) {
            return node;
        }
        AnalysisType objectType = (AnalysisType) StampTool.typeOrNull(node.object());
        if (objectType == null || objectType.isArray()) {
            return node;
        }
        AnalysisType objectAsType = (AnalysisType) bb.getConstantReflectionProvider().asJavaType(object);
        if (objectAsType != null) {
            // Note: using the Class object of a type as the base object for accesses
            // of that type's static fields is a HotSpot implementation detail.
            ResolvedJavaType objectAsHostType = bb.getHostVM().getOriginalHostType(objectAsType);
            ResolvedJavaField hostField = UnsafeAccessNode.findStaticFieldWithOffset(objectAsHostType, offset.asLong(), node.accessKind());
            return canonicalizeUnsafeAccessToField(node, hostField);
        }
        ResolvedJavaType objectHostType = bb.getHostVM().getOriginalHostType(objectType);
        ResolvedJavaField hostField = objectHostType.findInstanceFieldWithOffset(offset.asLong(), node.accessKind());
        return canonicalizeUnsafeAccessToField(node, hostField);
    }

    private ValueNode canonicalizeUnsafeAccessToField(UnsafeAccessNode node, ResolvedJavaField unwrappedField) {
        AnalysisError.guarantee(unwrappedField != null, "Unsafe access to object header?");
        AnalysisField field = bb.getUniverse().lookup(unwrappedField);
        AnalysisError.guarantee(!field.isInternal() && field.getJavaKind() == node.accessKind());
        return node.cloneAsFieldAccess(field);
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
        InlineBeforeAnalysisMethodScope methodScope = cast(s);
        if (!intrinsifiedMethodHandle) {
            if (!methodScope.inliningAborted && methodScope.isInlinedMethod()) {
                if (!methodScope.policyScope.shouldInterpretMethodHandleInvoke(methodScope.method, node)) {
                    abortInlining(methodScope);
                    return loopScope;
                }
            }
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
        Object reason = graph.currentNodeSourcePosition() != null ? graph.currentNodeSourcePosition() : graph.method();

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
        return policy.processInvokeArgs(inlineScope.method, predecessor, inlineScope.getArguments());
    }
}
