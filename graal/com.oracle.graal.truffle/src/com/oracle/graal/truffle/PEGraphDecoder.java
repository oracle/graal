/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle;

import static com.oracle.graal.compiler.common.GraalInternalError.*;
import static com.oracle.graal.java.AbstractBytecodeParser.Options.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.InvocationPluginReceiver;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.common.inlining.*;

/**
 * A graph decoder that performs partial evaluation, i.e., that performs method inlining and
 * canonicalization/simplification of nodes during decoding.
 *
 * Inlining and loop explosion are configured via the plugin mechanism also used by the
 * {@link GraphBuilderPhase}. However, not all callback methods defined in
 * {@link GraphBuilderContext} are available since decoding is more limited than graph building.
 *
 * The standard {@link Canonicalizable#canonical node canonicalization} interface is used to
 * canonicalize nodes during decoding. Additionally, {@link IfNode branches} and
 * {@link IntegerSwitchNode switches} with constant conditions are simplified.
 */
public abstract class PEGraphDecoder extends GraphDecoder {

    protected final MetaAccessProvider metaAccess;
    protected final ConstantReflectionProvider constantReflection;
    protected final StampProvider stampProvider;
    protected final SnippetReflectionProvider snippetReflection;

    protected class PEMethodScope extends MethodScope {
        protected final ResolvedJavaMethod method;
        protected final Invoke invoke;
        protected final int inliningDepth;

        protected final LoopExplosionPlugin loopExplosionPlugin;
        protected final InvocationPlugins invocationPlugins;
        protected final InlineInvokePlugin inlineInvokePlugin;
        protected final ParameterPlugin parameterPlugin;
        protected final ValueNode[] arguments;

        protected FrameState outerFrameState;
        protected BytecodePosition bytecodePosition;

        protected PEMethodScope(StructuredGraph targetGraph, MethodScope caller, EncodedGraph encodedGraph, ResolvedJavaMethod method, Invoke invoke, int inliningDepth,
                        LoopExplosionPlugin loopExplosionPlugin, InvocationPlugins invocationPlugins, InlineInvokePlugin inlineInvokePlugin, ParameterPlugin parameterPlugin, ValueNode[] arguments) {
            super(targetGraph, caller, encodedGraph, loopExplosionKind(method, loopExplosionPlugin));

            this.method = method;
            this.invoke = invoke;
            this.inliningDepth = inliningDepth;
            this.loopExplosionPlugin = loopExplosionPlugin;
            this.invocationPlugins = invocationPlugins;
            this.inlineInvokePlugin = inlineInvokePlugin;
            this.parameterPlugin = parameterPlugin;
            this.arguments = arguments;
        }
    }

    protected class PECanonicalizerTool implements CanonicalizerTool {
        @Override
        public MetaAccessProvider getMetaAccess() {
            return metaAccess;
        }

        @Override
        public ConstantReflectionProvider getConstantReflection() {
            return constantReflection;
        }

        @Override
        public boolean canonicalizeReads() {
            return true;
        }

        @Override
        public boolean allUsagesAvailable() {
            return false;
        }
    }

    protected class PENonAppendGraphBuilderContext implements GraphBuilderContext {
        private final PEMethodScope methodScope;

        public PENonAppendGraphBuilderContext(PEMethodScope methodScope) {
            this.methodScope = methodScope;
        }

        @Override
        public BailoutException bailout(String string) {
            throw new BailoutException(string);
        }

        @Override
        public StampProvider getStampProvider() {
            return stampProvider;
        }

        @Override
        public MetaAccessProvider getMetaAccess() {
            return metaAccess;
        }

        @Override
        public ConstantReflectionProvider getConstantReflection() {
            return constantReflection;
        }

        @Override
        public SnippetReflectionProvider getSnippetReflection() {
            return snippetReflection;
        }

        @Override
        public StructuredGraph getGraph() {
            return methodScope.graph;
        }

        @Override
        public int getDepth() {
            return methodScope.inliningDepth;
        }

        @Override
        public Replacement getReplacement() {
            return null;
        }

        @Override
        public <T extends ValueNode> T append(T value) {
            throw unimplemented();
        }

        @Override
        public <T extends ValueNode> T recursiveAppend(T value) {
            throw unimplemented();
        }

        @Override
        public void push(Kind kind, ValueNode value) {
            throw unimplemented();
        }

        @Override
        public void handleReplacedInvoke(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args) {
            throw unimplemented();
        }

        @Override
        public FrameState createStateAfter() {
            throw unimplemented();
        }

        @Override
        public GraphBuilderContext getParent() {
            throw unimplemented();
        }

        @Override
        public ResolvedJavaMethod getMethod() {
            throw unimplemented();
        }

        @Override
        public int bci() {
            throw unimplemented();
        }

        @Override
        public InvokeKind getInvokeKind() {
            throw unimplemented();
        }

        @Override
        public JavaType getInvokeReturnType() {
            throw unimplemented();
        }
    }

    protected class PEAppendGraphBuilderContext extends PENonAppendGraphBuilderContext {
        protected final Invoke invoke;
        protected FixedWithNextNode lastInstr;
        protected ValueNode pushedNode;

        public PEAppendGraphBuilderContext(PEMethodScope methodScope, Invoke invoke, FixedWithNextNode lastInstr) {
            super(methodScope);
            this.invoke = invoke;
            this.lastInstr = lastInstr;
        }

        @Override
        public void push(Kind kind, ValueNode value) {
            if (pushedNode != null) {
                throw unimplemented("Only one push is supported");
            }
            pushedNode = value;
        }

        @Override
        public FrameState createStateAfter() {
            return invoke.stateAfter().duplicate();
        }

        @Override
        public <T extends ValueNode> T append(T v) {
            if (v.graph() != null) {
                return v;
            }
            T added = getGraph().addOrUnique(v);
            if (added == v) {
                updateLastInstruction(v);
            }
            return added;
        }

        @Override
        public <T extends ValueNode> T recursiveAppend(T v) {
            if (v.graph() != null) {
                return v;
            }
            T added = getGraph().addOrUniqueWithInputs(v);
            if (added == v) {
                updateLastInstruction(v);
            }
            return added;
        }

        private <T extends ValueNode> void updateLastInstruction(T v) {
            if (v instanceof FixedNode) {
                FixedNode fixedNode = (FixedNode) v;
                lastInstr.setNext(fixedNode);
                if (fixedNode instanceof FixedWithNextNode) {
                    FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) fixedNode;
                    assert fixedWithNextNode.next() == null : "cannot append instruction to instruction which isn't end";
                    lastInstr = fixedWithNextNode;
                } else {
                    lastInstr = null;
                }
            }
        }
    }

    public PEGraphDecoder(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, StampProvider stampProvider, SnippetReflectionProvider snippetReflection) {
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
        this.stampProvider = stampProvider;
        this.snippetReflection = snippetReflection;
    }

    protected static LoopExplosionKind loopExplosionKind(ResolvedJavaMethod method, LoopExplosionPlugin loopExplosionPlugin) {
        if (loopExplosionPlugin == null) {
            return LoopExplosionKind.NONE;
        } else if (loopExplosionPlugin.shouldMergeExplosions(method)) {
            return LoopExplosionKind.MERGE_EXPLODE;
        } else if (loopExplosionPlugin.shouldExplodeLoops(method)) {
            return LoopExplosionKind.FULL_EXPLODE;
        } else {
            return LoopExplosionKind.NONE;
        }
    }

    public void decode(StructuredGraph targetGraph, ResolvedJavaMethod method, LoopExplosionPlugin loopExplosionPlugin, InvocationPlugins invocationPlugins, InlineInvokePlugin inlineInvokePlugin,
                    ParameterPlugin parameterPlugin) {
        PEMethodScope methodScope = new PEMethodScope(targetGraph, null, lookupEncodedGraph(method), method, null, 0, loopExplosionPlugin, invocationPlugins, inlineInvokePlugin, parameterPlugin, null);
        decode(methodScope);
        cleanupGraph(methodScope);
        methodScope.graph.verify();
    }

    @Override
    protected void cleanupGraph(MethodScope methodScope) {
        GraphBuilderPhase.connectLoopEndToBegin(methodScope.graph);
        super.cleanupGraph(methodScope);
    }

    @Override
    protected void checkLoopExplosionIteration(MethodScope s, LoopScope loopScope) {
        PEMethodScope methodScope = (PEMethodScope) s;

        Debug.dump(methodScope.graph, "Loop iteration " + loopScope.loopIteration);

        if (loopScope.loopIteration > MaximumLoopExplosionCount.getValue()) {
            String message = "too many loop explosion iterations - does the explosion not terminate for method " + methodScope.method + "?";
            if (FailedLoopExplosionIsFatal.getValue()) {
                throw new RuntimeException(message);
            } else {
                throw new BailoutException(message);
            }
        }
    }

    @Override
    protected void simplifyInvoke(MethodScope s, LoopScope loopScope, int invokeOrderId, Invoke invoke) {
        if (!(invoke.callTarget() instanceof MethodCallTargetNode)) {
            return;
        }
        PEMethodScope methodScope = (PEMethodScope) s;
        MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();

        // attempt to devirtualize the call
        ResolvedJavaType contextType = (invoke.stateAfter() == null && invoke.stateDuring() == null) ? null : invoke.getContextType();
        ResolvedJavaMethod specialCallTarget = MethodCallTargetNode.findSpecialCallTarget(callTarget.invokeKind(), callTarget.receiver(), callTarget.targetMethod(), contextType);
        if (specialCallTarget != null) {
            callTarget.setTargetMethod(specialCallTarget);
            callTarget.setInvokeKind(InvokeKind.Special);
        }

        if (tryInvocationPlugin(methodScope, loopScope, invokeOrderId, invoke)) {
            return;
        }
        if (tryInline(methodScope, loopScope, invokeOrderId, invoke)) {
            return;
        }

        if (methodScope.inlineInvokePlugin != null) {
            methodScope.inlineInvokePlugin.notifyOfNoninlinedInvoke(new PENonAppendGraphBuilderContext(methodScope), callTarget.targetMethod(), invoke);
        }
    }

    protected boolean tryInvocationPlugin(PEMethodScope methodScope, LoopScope loopScope, int invokeOrderId, Invoke invoke) {
        if (methodScope.invocationPlugins == null) {
            return false;
        }

        MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
        ResolvedJavaMethod targetMethod = callTarget.targetMethod();
        InvocationPlugin invocationPlugin = methodScope.invocationPlugins.lookupInvocation(targetMethod);
        if (invocationPlugin == null) {
            return false;
        }

        ValueNode[] arguments = callTarget.arguments().toArray(new ValueNode[0]);
        FixedWithNextNode invokePredecessor = (FixedWithNextNode) invoke.asNode().predecessor();
        FixedNode invokeNext = invoke.next();
        AbstractBeginNode invokeException = null;
        if (invoke instanceof InvokeWithExceptionNode) {
            invokeException = ((InvokeWithExceptionNode) invoke).exceptionEdge();
        }

        invoke.asNode().replaceAtPredecessor(null);

        PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, invoke, invokePredecessor);
        InvocationPluginReceiver invocationPluginReceiver = new InvocationPluginReceiver(graphBuilderContext);
        if (InvocationPlugin.execute(graphBuilderContext, targetMethod, invocationPlugin, invocationPluginReceiver.init(targetMethod, arguments), arguments)) {

            if (graphBuilderContext.lastInstr != null) {
                registerNode(loopScope, invokeOrderId, graphBuilderContext.pushedNode, true, true);
                invoke.asNode().replaceAtUsages(graphBuilderContext.pushedNode);
            } else {
                assert graphBuilderContext.pushedNode == null : "Why push a node when the invoke does not return anyway?";
                invoke.asNode().replaceAtUsages(null);
            }

            deleteInvoke(invoke);
            if (invokeException != null) {
                invokeException.safeDelete();
            }

            if (graphBuilderContext.lastInstr != null) {
                graphBuilderContext.lastInstr.setNext(invokeNext);
            } else {
                invokeNext.replaceAtPredecessor(null);
                invokeNext.safeDelete();
            }
            return true;

        } else {
            /* Restore original state: invoke is in Graph. */
            invokePredecessor.setNext(invoke.asNode());
            return false;
        }
    }

    protected boolean tryInline(PEMethodScope methodScope, LoopScope loopScope, int invokeOrderId, Invoke invoke) {
        if (methodScope.inlineInvokePlugin == null || !invoke.getInvokeKind().isDirect()) {
            return false;
        }

        MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
        ResolvedJavaMethod targetMethod = callTarget.targetMethod();
        if (!targetMethod.canBeInlined()) {
            return false;
        }

        ValueNode[] arguments = callTarget.arguments().toArray(new ValueNode[0]);
        GraphBuilderContext graphBuilderContext = new PENonAppendGraphBuilderContext(methodScope);
        InlineInfo inlineInfo = methodScope.inlineInvokePlugin.getInlineInfo(graphBuilderContext, targetMethod, arguments, callTarget.returnType());
        if (inlineInfo == null) {
            return false;
        }
        assert !inlineInfo.isIntrinsic && !inlineInfo.isReplacement : "not supported";

        ResolvedJavaMethod inlineMethod = inlineInfo.methodToInline;
        EncodedGraph graphToInline = lookupEncodedGraph(inlineMethod);
        if (graphToInline == null) {
            return false;
        }

        int exceptionObjectOrderId = -1;
        if (invoke instanceof InvokeWithExceptionNode) {
            /*
             * We need to have the regular next node (usually a KillingBeginNode) and the exception
             * edge node (always an ExceptionObjectNode) fully decoded, because both can be changed
             * or replaced as part of the inlining process. The GraphEncoder adds these two
             * successors in a known order (first the regular next node, then the exception edge)
             * that we can rely on here.
             */
            assert ((InvokeWithExceptionNode) invoke).next().next() == null;
            processNextNode(methodScope, loopScope);
            assert ((InvokeWithExceptionNode) invoke).next().next() != null;

            assert ((InvokeWithExceptionNode) invoke).exceptionEdge().next() == null;
            exceptionObjectOrderId = loopScope.nodesToProcess.nextSetBit(0);
            processNextNode(methodScope, loopScope);
            assert ((InvokeWithExceptionNode) invoke).exceptionEdge().next() != null;
        }

        PEMethodScope inlineScope = new PEMethodScope(methodScope.graph, methodScope, graphToInline, inlineMethod, invoke, methodScope.inliningDepth + 1, methodScope.loopExplosionPlugin,
                        methodScope.invocationPlugins, methodScope.inlineInvokePlugin, null, arguments);
        /* Do the actual inlining by decoding the inlineMethod */
        decode(inlineScope);

        ValueNode exceptionValue = null;
        if (inlineScope.unwindNode != null) {
            exceptionValue = inlineScope.unwindNode.exception();
        }

        FixedNode firstInlinedNode = inlineScope.startNode.next();
        /* The StartNode was only necessary as a placeholder during decoding. */
        inlineScope.startNode.safeDelete();

        assert inlineScope.startNode.stateAfter() == null;
        ValueNode returnValue = InliningUtil.finishInlining(invoke, methodScope.graph, firstInlinedNode, inlineScope.returnNodes, inlineScope.unwindNode, inlineScope.encodedGraph.getAssumptions(),
                        inlineScope.encodedGraph.getInlinedMethods(), null);

        /*
         * Usage the handles that we have on the return value and the exception to update the
         * orderId->Node table.
         */
        registerNode(loopScope, invokeOrderId, returnValue, true, true);
        if (invoke instanceof InvokeWithExceptionNode) {
            registerNode(loopScope, exceptionObjectOrderId, exceptionValue, true, true);
        }
        deleteInvoke(invoke);

        methodScope.inlineInvokePlugin.postInline(inlineMethod);
        return true;
    }

    private static void deleteInvoke(Invoke invoke) {
        /*
         * Clean up unused nodes. We cannot just call killCFG on the invoke node because that can
         * kill too much: nodes that are decoded later can use values that appear unused by now.
         */
        FrameState frameState = invoke.stateAfter();
        invoke.asNode().safeDelete();
        invoke.callTarget().safeDelete();
        if (frameState != null && frameState.hasNoUsages()) {
            frameState.safeDelete();
        }
    }

    protected abstract EncodedGraph lookupEncodedGraph(ResolvedJavaMethod method);

    @Override
    protected void simplifyFixedNode(MethodScope s, LoopScope loopScope, int nodeOrderId, FixedNode node) {
        PEMethodScope methodScope = (PEMethodScope) s;

        if (node instanceof IfNode && ((IfNode) node).condition() instanceof LogicConstantNode) {
            IfNode ifNode = (IfNode) node;
            boolean condition = ((LogicConstantNode) ifNode.condition()).getValue();
            AbstractBeginNode survivingSuccessor = ifNode.getSuccessor(condition);
            AbstractBeginNode deadSuccessor = ifNode.getSuccessor(!condition);

            methodScope.graph.removeSplit(ifNode, survivingSuccessor);
            assert deadSuccessor.next() == null : "must not be parsed yet";
            deadSuccessor.safeDelete();

        } else if (node instanceof IntegerSwitchNode && ((IntegerSwitchNode) node).value().isConstant()) {
            IntegerSwitchNode switchNode = (IntegerSwitchNode) node;
            int value = switchNode.value().asJavaConstant().asInt();
            AbstractBeginNode survivingSuccessor = switchNode.successorAtKey(value);
            List<Node> allSuccessors = switchNode.successors().snapshot();

            methodScope.graph.removeSplit(switchNode, survivingSuccessor);
            for (Node successor : allSuccessors) {
                if (successor != survivingSuccessor) {
                    assert ((AbstractBeginNode) successor).next() == null : "must not be parsed yet";
                    successor.safeDelete();
                }
            }

        } else if (node instanceof Canonicalizable) {
            Node canonical = ((Canonicalizable) node).canonical(new PECanonicalizerTool());
            if (canonical == null) {
                /*
                 * This is a possible return value of canonicalization. However, we might need to
                 * add additional usages later on for which we need a node. Therefore, we just do
                 * nothing and leave the node in place.
                 */
            } else if (canonical != node) {
                if (!canonical.isAlive()) {
                    assert !canonical.isDeleted();
                    canonical = methodScope.graph.addOrUniqueWithInputs(canonical);
                    if (canonical instanceof FixedWithNextNode) {
                        methodScope.graph.addBeforeFixed(node, (FixedWithNextNode) canonical);
                    }
                }
                GraphUtil.unlinkFixedNode((FixedWithNextNode) node);
                node.replaceAtUsages(canonical);
                node.safeDelete();
                assert lookupNode(loopScope, nodeOrderId) == node;
                registerNode(loopScope, nodeOrderId, canonical, true, false);
            }
        }
    }

    @Override
    protected Node handleFloatingNodeBeforeAdd(MethodScope s, LoopScope loopScope, Node node) {
        PEMethodScope methodScope = (PEMethodScope) s;

        if (node instanceof ParameterNode) {
            if (methodScope.arguments != null) {
                Node result = methodScope.arguments[((ParameterNode) node).index()];
                assert result != null;
                return result;

            } else if (methodScope.parameterPlugin != null) {
                GraphBuilderContext graphBuilderContext = new PENonAppendGraphBuilderContext(methodScope);
                Node result = methodScope.parameterPlugin.interceptParameter(graphBuilderContext, ((ParameterNode) node).index(), ((ParameterNode) node).stamp());
                if (result != null) {
                    return result;
                }
            }

        } else if (node instanceof Canonicalizable) {
            Node canonical = ((Canonicalizable) node).canonical(new PECanonicalizerTool());
            if (canonical == null) {
                /*
                 * This is a possible return value of canonicalization. However, we might need to
                 * add additional usages later on for which we need a node. Therefore, we just do
                 * nothing and leave the node in place.
                 */
            } else if (canonical != node) {
                if (!canonical.isAlive()) {
                    assert !canonical.isDeleted();
                    canonical = methodScope.graph.addOrUniqueWithInputs(canonical);
                }
                assert node.hasNoUsages();
                // methodScope.graph.replaceFloating((FloatingNode) node, canonical);
                return canonical;
            }
        }
        return node;
    }

    @Override
    protected Node handleFloatingNodeAfterAdd(MethodScope s, LoopScope loopScope, Node node) {
        PEMethodScope methodScope = (PEMethodScope) s;

        if (methodScope.isInlinedMethod()) {
            if (node instanceof SimpleInfopointNode) {
                methodScope.bytecodePosition = InliningUtil.processSimpleInfopoint(methodScope.invoke, (SimpleInfopointNode) node, methodScope.bytecodePosition);
                return node;

            } else if (node instanceof FrameState) {
                FrameState stateAtExceptionEdge = null;
                if (methodScope.invoke instanceof InvokeWithExceptionNode) {
                    InvokeWithExceptionNode invokeWithException = ((InvokeWithExceptionNode) methodScope.invoke);
                    ExceptionObjectNode obj = (ExceptionObjectNode) invokeWithException.exceptionEdge();
                    stateAtExceptionEdge = obj.stateAfter();
                }
                if (methodScope.outerFrameState == null) {
                    FrameState stateAtReturn = methodScope.invoke.stateAfter();
                    Kind invokeReturnKind = methodScope.invoke.asNode().getKind();
                    methodScope.outerFrameState = stateAtReturn.duplicateModifiedDuringCall(methodScope.invoke.bci(), invokeReturnKind);
                }
                return InliningUtil.processFrameState((FrameState) node, methodScope.invoke, methodScope.method, stateAtExceptionEdge, methodScope.outerFrameState, true);

            } else if (node instanceof MonitorIdNode) {
                InliningUtil.processMonitorId(methodScope.invoke, (MonitorIdNode) node);
                return node;
            }
        }

        return node;
    }
}
