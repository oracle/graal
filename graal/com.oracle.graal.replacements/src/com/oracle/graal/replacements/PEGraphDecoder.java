/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import static com.oracle.graal.debug.GraalError.unimplemented;
import static com.oracle.graal.java.BytecodeParserOptions.DumpDuringGraphBuilding;
import static com.oracle.graal.java.BytecodeParserOptions.FailedLoopExplosionIsFatal;
import static com.oracle.graal.java.BytecodeParserOptions.MaximumLoopExplosionCount;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.graal.compiler.common.cfg.CFGVerifier;
import com.oracle.graal.compiler.common.spi.ConstantFieldProvider;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.compiler.common.type.StampPair;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugCloseable;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.NodeSourcePosition;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.AbstractBeginNode;
import com.oracle.graal.nodes.AbstractMergeNode;
import com.oracle.graal.nodes.CallTargetNode;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.DeoptimizeNode;
import com.oracle.graal.nodes.EncodedGraph;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.IfNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.InvokeWithExceptionNode;
import com.oracle.graal.nodes.MergeNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.ReturnNode;
import com.oracle.graal.nodes.SimplifyingGraphDecoder;
import com.oracle.graal.nodes.StateSplit;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.UnwindNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.cfg.ControlFlowGraph;
import com.oracle.graal.nodes.extended.ForeignCallNode;
import com.oracle.graal.nodes.extended.IntegerSwitchNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InlineInvokePlugin;
import com.oracle.graal.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import com.oracle.graal.nodes.graphbuilderconf.IntrinsicContext;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins.InvocationPluginReceiver;
import com.oracle.graal.nodes.graphbuilderconf.LoopExplosionPlugin;
import com.oracle.graal.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind;
import com.oracle.graal.nodes.graphbuilderconf.ParameterPlugin;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.java.MonitorIdNode;
import com.oracle.graal.nodes.spi.StampProvider;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.phases.common.inlining.InliningUtil;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

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
public abstract class PEGraphDecoder extends SimplifyingGraphDecoder {

    public static class Options {
        @Option(help = "Maximum inlining depth during partial evaluation before reporting an infinite recursion")//
        public static final OptionValue<Integer> InliningDepthError = new OptionValue<>(1000);
    }

    protected class PEMethodScope extends MethodScope {
        /** The state of the caller method. Only non-null during method inlining. */
        protected final PEMethodScope caller;
        protected final ResolvedJavaMethod method;
        protected final InvokeData invokeData;
        protected final int inliningDepth;

        protected final LoopExplosionPlugin loopExplosionPlugin;
        protected final InvocationPlugins invocationPlugins;
        protected final InlineInvokePlugin[] inlineInvokePlugins;
        protected final ParameterPlugin parameterPlugin;
        protected final ValueNode[] arguments;

        protected FrameState outerState;
        protected FrameState exceptionState;
        protected ExceptionPlaceholderNode exceptionPlaceholderNode;
        protected NodeSourcePosition bytecodePosition;

        protected PEMethodScope(StructuredGraph targetGraph, PEMethodScope caller, LoopScope callerLoopScope, EncodedGraph encodedGraph, ResolvedJavaMethod method, InvokeData invokeData,
                        int inliningDepth, LoopExplosionPlugin loopExplosionPlugin, InvocationPlugins invocationPlugins, InlineInvokePlugin[] inlineInvokePlugins, ParameterPlugin parameterPlugin,
                        ValueNode[] arguments) {
            super(callerLoopScope, targetGraph, encodedGraph, loopExplosionKind(method, loopExplosionPlugin));

            this.caller = caller;
            this.method = method;
            this.invokeData = invokeData;
            this.inliningDepth = inliningDepth;
            this.loopExplosionPlugin = loopExplosionPlugin;
            this.invocationPlugins = invocationPlugins;
            this.inlineInvokePlugins = inlineInvokePlugins;
            this.parameterPlugin = parameterPlugin;
            this.arguments = arguments;
        }

        public boolean isInlinedMethod() {
            return caller != null;
        }

        public NodeSourcePosition getBytecodePosition() {
            if (bytecodePosition == null) {
                ensureOuterStateDecoded(this);
                ensureExceptionStateDecoded(this);
                JavaConstant constantReceiver = invokeData.constantReceiver;
                bytecodePosition = new NodeSourcePosition(constantReceiver, FrameState.toSourcePosition(outerState), method, invokeData.invoke.bci());
            }
            return bytecodePosition;
        }
    }

    protected class PENonAppendGraphBuilderContext implements GraphBuilderContext {
        protected final PEMethodScope methodScope;
        protected final Invoke invoke;

        public PENonAppendGraphBuilderContext(PEMethodScope methodScope, Invoke invoke) {
            this.methodScope = methodScope;
            this.invoke = invoke;
        }

        @Override
        public BailoutException bailout(String string) {
            BailoutException bailout = new BailoutException(string);
            throw GraphUtil.createBailoutException(string, bailout, GraphUtil.approxSourceStackTraceElement(methodScope.getBytecodePosition()));
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
        public ConstantFieldProvider getConstantFieldProvider() {
            return constantFieldProvider;
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
        public IntrinsicContext getIntrinsic() {
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
        public void push(JavaKind kind, ValueNode value) {
            throw unimplemented();
        }

        @Override
        public void handleReplacedInvoke(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, boolean inlineEverything) {
            throw unimplemented();
        }

        @Override
        public boolean intrinsify(ResolvedJavaMethod targetMethod, ResolvedJavaMethod substitute, InvocationPlugin.Receiver receiver, ValueNode[] args) {
            return false;
        }

        @Override
        public void setStateAfter(StateSplit stateSplit) {
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
            return invoke.bci();
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
        protected FixedWithNextNode lastInstr;
        protected ValueNode pushedNode;

        public PEAppendGraphBuilderContext(PEMethodScope inlineScope, FixedWithNextNode lastInstr) {
            super(inlineScope, inlineScope.invokeData.invoke);
            this.lastInstr = lastInstr;
        }

        @Override
        public void push(JavaKind kind, ValueNode value) {
            if (pushedNode != null) {
                throw unimplemented("Only one push is supported");
            }
            pushedNode = value;
        }

        @Override
        public void setStateAfter(StateSplit stateSplit) {
            Node stateAfter = decodeFloatingNode(methodScope.caller, methodScope.callerLoopScope, methodScope.invokeData.stateAfterOrderId);
            getGraph().add(stateAfter);
            FrameState fs = (FrameState) handleFloatingNodeAfterAdd(methodScope.caller, methodScope.callerLoopScope, stateAfter);
            stateSplit.setStateAfter(fs);
        }

        @SuppressWarnings("try")
        @Override
        public <T extends ValueNode> T append(T v) {
            if (v.graph() != null) {
                return v;
            }
            try (DebugCloseable position = withNodeSoucePosition()) {
                T added = getGraph().addOrUnique(v);
                if (added == v) {
                    updateLastInstruction(v);
                }
                return added;
            }
        }

        private DebugCloseable withNodeSoucePosition() {
            if (getGraph().mayHaveNodeSourcePosition()) {
                return getGraph().withNodeSourcePosition(methodScope.getBytecodePosition());
            }
            return null;
        }

        @SuppressWarnings("try")
        @Override
        public <T extends ValueNode> T recursiveAppend(T v) {
            if (v.graph() != null) {
                return v;
            }
            try (DebugCloseable position = withNodeSoucePosition()) {
                T added = getGraph().addOrUniqueWithInputs(v);
                if (added == v) {
                    updateLastInstruction(v);
                }
                return added;
            }
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

    @NodeInfo
    static class ExceptionPlaceholderNode extends ValueNode {
        public static final NodeClass<ExceptionPlaceholderNode> TYPE = NodeClass.create(ExceptionPlaceholderNode.class);

        protected ExceptionPlaceholderNode() {
            super(TYPE, StampFactory.object());
        }
    }

    public PEGraphDecoder(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider, StampProvider stampProvider,
                    Architecture architecture) {
        super(metaAccess, constantReflection, constantFieldProvider, stampProvider, true, architecture);
    }

    protected static LoopExplosionKind loopExplosionKind(ResolvedJavaMethod method, LoopExplosionPlugin loopExplosionPlugin) {
        if (loopExplosionPlugin == null) {
            return LoopExplosionKind.NONE;
        } else {
            return loopExplosionPlugin.loopExplosionKind(method);
        }
    }

    public void decode(StructuredGraph targetGraph, ResolvedJavaMethod method, LoopExplosionPlugin loopExplosionPlugin, InvocationPlugins invocationPlugins, InlineInvokePlugin[] inlineInvokePlugins,
                    ParameterPlugin parameterPlugin) {
        PEMethodScope methodScope = new PEMethodScope(targetGraph, null, null, lookupEncodedGraph(method, false), method, null, 0, loopExplosionPlugin, invocationPlugins, inlineInvokePlugins,
                        parameterPlugin, null);
        decode(createInitialLoopScope(methodScope, null));
        cleanupGraph(methodScope);
        assert methodScope.graph.verify();

        try {
            /* Check that the control flow graph can be computed, to catch problems early. */
            assert CFGVerifier.verify(ControlFlowGraph.compute(methodScope.graph, true, true, true, true));
        } catch (Throwable ex) {
            throw GraalError.shouldNotReachHere("Control flow graph not valid after partial evaluation");
        }
    }

    @Override
    protected void cleanupGraph(MethodScope methodScope) {
        super.cleanupGraph(methodScope);

        for (FrameState frameState : methodScope.graph.getNodes(FrameState.TYPE)) {
            if (frameState.bci == BytecodeFrame.UNWIND_BCI) {
                /*
                 * handleMissingAfterExceptionFrameState is called during graph decoding from
                 * InliningUtil.processFrameState - but during graph decoding it does not do
                 * anything because the usages of the frameState are not available yet. So we need
                 * to call it again.
                 */
                InliningUtil.handleMissingAfterExceptionFrameState(frameState);

                /*
                 * The frameState must be gone now, because it is not a valid deoptimization point.
                 */
                assert frameState.isDeleted();
            }
        }
    }

    @Override
    protected void checkLoopExplosionIteration(MethodScope s, LoopScope loopScope) {
        PEMethodScope methodScope = (PEMethodScope) s;

        if (loopScope.loopIteration > MaximumLoopExplosionCount.getValue()) {
            throw tooManyLoopExplosionIterations(methodScope);
        }
    }

    private static RuntimeException tooManyLoopExplosionIterations(PEMethodScope methodScope) {
        String message = "too many loop explosion iterations - does the explosion not terminate for method " + methodScope.method + "?";
        RuntimeException bailout = FailedLoopExplosionIsFatal.getValue() ? new RuntimeException(message) : new BailoutException(message);
        throw GraphUtil.createBailoutException(message, bailout, GraphUtil.approxSourceStackTraceElement(methodScope.getBytecodePosition()));
    }

    @Override
    protected LoopScope handleInvoke(MethodScope s, LoopScope loopScope, InvokeData invokeData) {
        PEMethodScope methodScope = (PEMethodScope) s;

        /*
         * Decode the call target, but do not add it to the graph yet. This avoids adding usages for
         * all the arguments, which are expensive to remove again when we can inline the method.
         */
        assert invokeData.invoke.callTarget() == null : "callTarget edge is ignored during decoding of Invoke";
        CallTargetNode callTarget = (CallTargetNode) decodeFloatingNode(methodScope, loopScope, invokeData.callTargetOrderId);
        if (callTarget instanceof MethodCallTargetNode) {
            MethodCallTargetNode methodCall = (MethodCallTargetNode) callTarget;
            if (methodCall.invokeKind().hasReceiver()) {
                invokeData.constantReceiver = methodCall.arguments().get(0).asJavaConstant();
            }
            LoopScope inlineLoopScope = trySimplifyInvoke(methodScope, loopScope, invokeData, (MethodCallTargetNode) callTarget);
            if (inlineLoopScope != null) {
                return inlineLoopScope;
            }
        }

        /* We know that we need an invoke, so now we can add the call target to the graph. */
        methodScope.graph.add(callTarget);
        registerNode(loopScope, invokeData.callTargetOrderId, callTarget, false, false);
        return super.handleInvoke(methodScope, loopScope, invokeData);
    }

    protected LoopScope trySimplifyInvoke(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, MethodCallTargetNode callTarget) {
        // attempt to devirtualize the call
        ResolvedJavaMethod specialCallTarget = MethodCallTargetNode.findSpecialCallTarget(callTarget.invokeKind(), callTarget.receiver(), callTarget.targetMethod(), invokeData.contextType);
        if (specialCallTarget != null) {
            callTarget.setTargetMethod(specialCallTarget);
            callTarget.setInvokeKind(InvokeKind.Special);
        }

        if (tryInvocationPlugin(methodScope, loopScope, invokeData, callTarget)) {
            /*
             * The invocation plugin handled the call, so decoding continues in the calling method.
             */
            return loopScope;
        }
        LoopScope inlineLoopScope = tryInline(methodScope, loopScope, invokeData, callTarget);
        if (inlineLoopScope != null) {
            /*
             * We can inline the call, so decoding continues in the inlined method.
             */
            return inlineLoopScope;
        }

        for (InlineInvokePlugin plugin : methodScope.inlineInvokePlugins) {
            plugin.notifyNotInlined(new PENonAppendGraphBuilderContext(methodScope, invokeData.invoke), callTarget.targetMethod(), invokeData.invoke);
        }
        return null;
    }

    protected boolean tryInvocationPlugin(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, MethodCallTargetNode callTarget) {
        if (methodScope.invocationPlugins == null) {
            return false;
        }

        Invoke invoke = invokeData.invoke;

        ResolvedJavaMethod targetMethod = callTarget.targetMethod();
        InvocationPlugin invocationPlugin = methodScope.invocationPlugins.lookupInvocation(targetMethod);
        if (invocationPlugin == null) {
            return false;
        }

        ValueNode[] arguments = callTarget.arguments().toArray(new ValueNode[0]);
        FixedWithNextNode invokePredecessor = (FixedWithNextNode) invoke.asNode().predecessor();

        /*
         * Remove invoke from graph so that invocation plugin can append nodes to the predecessor.
         */
        invoke.asNode().replaceAtPredecessor(null);

        PEMethodScope inlineScope = new PEMethodScope(methodScope.graph, methodScope, loopScope, null, targetMethod, invokeData, methodScope.inliningDepth + 1, methodScope.loopExplosionPlugin,
                        methodScope.invocationPlugins, methodScope.inlineInvokePlugins, null, arguments);
        PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(inlineScope, invokePredecessor);
        InvocationPluginReceiver invocationPluginReceiver = new InvocationPluginReceiver(graphBuilderContext);

        if (invocationPlugin.execute(graphBuilderContext, targetMethod, invocationPluginReceiver.init(targetMethod, arguments), arguments)) {

            if (graphBuilderContext.lastInstr != null) {
                registerNode(loopScope, invokeData.invokeOrderId, graphBuilderContext.pushedNode, true, true);
                invoke.asNode().replaceAtUsages(graphBuilderContext.pushedNode);
                graphBuilderContext.lastInstr.setNext(nodeAfterInvoke(methodScope, loopScope, invokeData, AbstractBeginNode.prevBegin(graphBuilderContext.lastInstr)));
            } else {
                assert graphBuilderContext.pushedNode == null : "Why push a node when the invoke does not return anyway?";
                invoke.asNode().replaceAtUsages(null);
            }

            deleteInvoke(invoke);
            return true;

        } else {
            /* Intrinsification failed, restore original state: invoke is in Graph. */
            invokePredecessor.setNext(invoke.asNode());
            return false;
        }
    }

    protected LoopScope tryInline(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, MethodCallTargetNode callTarget) {
        if (!callTarget.invokeKind().isDirect()) {
            return null;
        }

        ResolvedJavaMethod targetMethod = callTarget.targetMethod();
        if (!targetMethod.canBeInlined()) {
            return null;
        }

        ValueNode[] arguments = callTarget.arguments().toArray(new ValueNode[0]);
        GraphBuilderContext graphBuilderContext = new PENonAppendGraphBuilderContext(methodScope, invokeData.invoke);

        for (InlineInvokePlugin plugin : methodScope.inlineInvokePlugins) {
            InlineInfo inlineInfo = plugin.shouldInlineInvoke(graphBuilderContext, targetMethod, arguments);
            if (inlineInfo != null) {
                if (inlineInfo.getMethodToInline() == null) {
                    return null;
                } else {
                    return doInline(methodScope, loopScope, invokeData, inlineInfo, arguments);
                }
            }
        }
        return null;
    }

    protected LoopScope doInline(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, InlineInfo inlineInfo, ValueNode[] arguments) {
        ResolvedJavaMethod inlineMethod = inlineInfo.getMethodToInline();
        EncodedGraph graphToInline = lookupEncodedGraph(inlineMethod, inlineInfo.isIntrinsic());
        if (graphToInline == null) {
            return null;
        }

        if (methodScope.inliningDepth > Options.InliningDepthError.getValue()) {
            throw tooDeepInlining(methodScope);
        }

        for (InlineInvokePlugin plugin : methodScope.inlineInvokePlugins) {
            plugin.notifyBeforeInline(inlineMethod);
        }

        Invoke invoke = invokeData.invoke;
        FixedNode invokeNode = invoke.asNode();
        FixedWithNextNode predecessor = (FixedWithNextNode) invokeNode.predecessor();
        invokeNode.replaceAtPredecessor(null);

        PEMethodScope inlineScope = new PEMethodScope(methodScope.graph, methodScope, loopScope, graphToInline, inlineMethod, invokeData, methodScope.inliningDepth + 1,
                        methodScope.loopExplosionPlugin, methodScope.invocationPlugins, methodScope.inlineInvokePlugins, null, arguments);

        /*
         * After decoding all the nodes of the inlined method, we need to re-wire the return and
         * unwind nodes. Since inlining is non-recursive, this cannot be done at the end of this
         * method, but must be registered as a cleanup task that runs when all nodes of the inlined
         * methods have been decoded.
         */
        inlineScope.cleanupTasks.add(() -> finishInlining(methodScope, loopScope, invokeData, inlineMethod, inlineScope));

        /*
         * Do the actual inlining by returning the initial loop scope for the inlined method scope.
         */
        return createInitialLoopScope(inlineScope, predecessor);
    }

    protected void finishInlining(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, ResolvedJavaMethod inlineMethod, PEMethodScope inlineScope) {
        Invoke invoke = invokeData.invoke;
        FixedNode invokeNode = invoke.asNode();

        ValueNode exceptionValue = null;
        if (inlineScope.unwindNode != null) {
            exceptionValue = inlineScope.unwindNode.exception();
        }
        UnwindNode unwindNode = inlineScope.unwindNode;

        if (invoke instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeWithException = ((InvokeWithExceptionNode) invoke);
            assert invokeWithException.next() == null;
            assert invokeWithException.exceptionEdge() == null;

            if (unwindNode != null) {
                assert unwindNode.predecessor() != null;
                Node n = makeStubNode(methodScope, loopScope, invokeData.exceptionNextOrderId);
                unwindNode.replaceAndDelete(n);
            }

        } else {
            if (unwindNode != null && !unwindNode.isDeleted()) {
                DeoptimizeNode deoptimizeNode = methodScope.graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.NotCompiledExceptionHandler));
                unwindNode.replaceAndDelete(deoptimizeNode);
            }
        }

        assert invoke.next() == null;

        ValueNode returnValue;
        List<ReturnNode> returnNodes = inlineScope.returnNodes;
        if (!returnNodes.isEmpty()) {
            if (returnNodes.size() == 1) {
                ReturnNode returnNode = returnNodes.get(0);
                returnValue = returnNode.result();
                FixedNode n = nodeAfterInvoke(methodScope, loopScope, invokeData, AbstractBeginNode.prevBegin(returnNode));
                returnNode.replaceAndDelete(n);
            } else {
                AbstractMergeNode merge = methodScope.graph.add(new MergeNode());
                merge.setStateAfter((FrameState) ensureNodeCreated(methodScope, loopScope, invokeData.stateAfterOrderId));
                returnValue = InliningUtil.mergeReturns(merge, returnNodes, null);
                FixedNode n = nodeAfterInvoke(methodScope, loopScope, invokeData, merge);
                merge.setNext(n);
            }
        } else {
            returnValue = null;
        }
        invokeNode.replaceAtUsages(returnValue);

        /*
         * Usage the handles that we have on the return value and the exception to update the
         * orderId->Node table.
         */
        registerNode(loopScope, invokeData.invokeOrderId, returnValue, true, true);
        if (invoke instanceof InvokeWithExceptionNode) {
            registerNode(loopScope, invokeData.exceptionOrderId, exceptionValue, true, true);
        }
        if (inlineScope.exceptionPlaceholderNode != null) {
            inlineScope.exceptionPlaceholderNode.replaceAtUsagesAndDelete(exceptionValue);
        }
        deleteInvoke(invoke);

        for (InlineInvokePlugin plugin : methodScope.inlineInvokePlugins) {
            plugin.notifyAfterInline(inlineMethod);
        }

        if (Debug.isDumpEnabled(Debug.INFO_LOG_LEVEL) && DumpDuringGraphBuilding.getValue()) {
            Debug.dump(Debug.INFO_LOG_LEVEL, methodScope.graph, "Inline finished: %s.%s", inlineMethod.getDeclaringClass().getUnqualifiedName(), inlineMethod.getName());
        }
    }

    private static RuntimeException tooDeepInlining(PEMethodScope methodScope) {
        HashMap<ResolvedJavaMethod, Integer> methodCounts = new HashMap<>();
        for (PEMethodScope cur = methodScope; cur != null; cur = cur.caller) {
            Integer oldCount = methodCounts.get(cur.method);
            methodCounts.put(cur.method, oldCount == null ? 1 : oldCount + 1);
        }

        List<Map.Entry<ResolvedJavaMethod, Integer>> methods = new ArrayList<>(methodCounts.entrySet());
        methods.sort((e1, e2) -> -Integer.compare(e1.getValue(), e2.getValue()));

        StringBuilder msg = new StringBuilder("Too deep inlining, probably caused by recursive inlining. Inlined methods ordered by inlining frequency:");
        for (Map.Entry<ResolvedJavaMethod, Integer> entry : methods) {
            msg.append(System.lineSeparator()).append(entry.getKey().format("%H.%n(%p) [")).append(entry.getValue()).append("]");
        }
        throw new BailoutException(msg.toString());
    }

    public FixedNode nodeAfterInvoke(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, AbstractBeginNode lastBlock) {
        assert lastBlock.isAlive();
        FixedNode n;
        if (invokeData.invoke instanceof InvokeWithExceptionNode) {
            registerNode(loopScope, invokeData.nextOrderId, lastBlock, false, false);
            n = makeStubNode(methodScope, loopScope, invokeData.nextNextOrderId);
        } else {
            n = makeStubNode(methodScope, loopScope, invokeData.nextOrderId);
        }
        return n;
    }

    private static void deleteInvoke(Invoke invoke) {
        /*
         * Clean up unused nodes. We cannot just call killCFG on the invoke node because that can
         * kill too much: nodes that are decoded later can use values that appear unused by now.
         */
        FrameState frameState = invoke.stateAfter();
        invoke.asNode().safeDelete();
        assert invoke.callTarget() == null : "must not have been added to the graph yet";
        if (frameState != null && frameState.hasNoUsages()) {
            frameState.safeDelete();
        }
    }

    protected abstract EncodedGraph lookupEncodedGraph(ResolvedJavaMethod method, boolean isIntrinsic);

    @SuppressWarnings("try")
    @Override
    protected void handleFixedNode(MethodScope s, LoopScope loopScope, int nodeOrderId, FixedNode node) {
        PEMethodScope methodScope = (PEMethodScope) s;
        if (node instanceof ForeignCallNode) {
            ForeignCallNode foreignCall = (ForeignCallNode) node;
            if (foreignCall.getBci() == BytecodeFrame.UNKNOWN_BCI && methodScope.invokeData != null) {
                foreignCall.setBci(methodScope.invokeData.invoke.bci());
            }
        }

        NodeSourcePosition pos = node.getNodeSourcePosition();
        if (pos != null && methodScope.isInlinedMethod()) {
            NodeSourcePosition newPosition = pos.addCaller(methodScope.getBytecodePosition());
            try (DebugCloseable scope = node.graph().withoutNodeSourcePosition()) {
                super.handleFixedNode(s, loopScope, nodeOrderId, node);
            }
            if (node.isAlive()) {
                node.setNodeSourcePosition(newPosition);
            }
        } else {
            super.handleFixedNode(s, loopScope, nodeOrderId, node);
        }

    }

    @Override
    protected Node handleFloatingNodeBeforeAdd(MethodScope s, LoopScope loopScope, Node node) {
        PEMethodScope methodScope = (PEMethodScope) s;

        if (node instanceof ParameterNode) {
            ParameterNode param = (ParameterNode) node;
            if (methodScope.arguments != null) {
                Node result = methodScope.arguments[param.index()];
                assert result != null;
                return result;

            } else if (methodScope.parameterPlugin != null) {
                GraphBuilderContext graphBuilderContext = new PENonAppendGraphBuilderContext(methodScope, null);
                Node result = methodScope.parameterPlugin.interceptParameter(graphBuilderContext, param.index(),
                                StampPair.create(param.stamp(), param.uncheckedStamp()));
                if (result != null) {
                    return result;
                }
            }

        }

        return super.handleFloatingNodeBeforeAdd(methodScope, loopScope, node);
    }

    protected void ensureOuterStateDecoded(PEMethodScope methodScope) {
        if (methodScope.outerState == null && methodScope.caller != null) {
            FrameState stateAtReturn = methodScope.invokeData.invoke.stateAfter();
            if (stateAtReturn == null) {
                stateAtReturn = (FrameState) decodeFloatingNode(methodScope.caller, methodScope.callerLoopScope, methodScope.invokeData.stateAfterOrderId);
            }

            JavaKind invokeReturnKind = methodScope.invokeData.invoke.asNode().getStackKind();
            FrameState outerState = stateAtReturn.duplicateModified(methodScope.graph, methodScope.invokeData.invoke.bci(), stateAtReturn.rethrowException(), true, invokeReturnKind, null, null);

            /*
             * When the encoded graph has methods inlining, we can already have a proper caller
             * state. If not, we set the caller state here.
             */
            if (outerState.outerFrameState() == null && methodScope.caller != null) {
                ensureOuterStateDecoded(methodScope.caller);
                outerState.setOuterFrameState(methodScope.caller.outerState);
            }
            methodScope.outerState = outerState;
        }
    }

    protected void ensureStateAfterDecoded(PEMethodScope methodScope) {
        if (methodScope.invokeData.invoke.stateAfter() == null) {
            methodScope.invokeData.invoke.setStateAfter((FrameState) ensureNodeCreated(methodScope.caller, methodScope.callerLoopScope, methodScope.invokeData.stateAfterOrderId));
        }
    }

    protected void ensureExceptionStateDecoded(PEMethodScope methodScope) {
        if (methodScope.exceptionState == null && methodScope.caller != null && methodScope.invokeData.invoke instanceof InvokeWithExceptionNode) {
            ensureStateAfterDecoded(methodScope);

            assert methodScope.exceptionPlaceholderNode == null;
            methodScope.exceptionPlaceholderNode = methodScope.graph.add(new ExceptionPlaceholderNode());
            registerNode(methodScope.callerLoopScope, methodScope.invokeData.exceptionOrderId, methodScope.exceptionPlaceholderNode, false, false);
            FrameState exceptionState = (FrameState) ensureNodeCreated(methodScope.caller, methodScope.callerLoopScope, methodScope.invokeData.exceptionStateOrderId);

            if (exceptionState.outerFrameState() == null && methodScope.caller != null) {
                ensureOuterStateDecoded(methodScope.caller);
                exceptionState.setOuterFrameState(methodScope.caller.outerState);
            }
            methodScope.exceptionState = exceptionState;
        }
    }

    @Override
    protected Node addFloatingNode(MethodScope s, Node node) {
        Node addedNode = super.addFloatingNode(s, node);
        PEMethodScope methodScope = (PEMethodScope) s;
        NodeSourcePosition pos = node.getNodeSourcePosition();
        if (methodScope.isInlinedMethod()) {
            if (pos != null) {
                NodeSourcePosition bytecodePosition = methodScope.getBytecodePosition();
                node.setNodeSourcePosition(pos.addCaller(bytecodePosition));
            }
        }
        return addedNode;
    }

    @Override
    protected Node handleFloatingNodeAfterAdd(MethodScope s, LoopScope loopScope, Node node) {
        PEMethodScope methodScope = (PEMethodScope) s;

        if (methodScope.isInlinedMethod()) {
            if (node instanceof FrameState) {
                FrameState frameState = (FrameState) node;

                ensureOuterStateDecoded(methodScope);
                if (frameState.bci < 0) {
                    ensureExceptionStateDecoded(methodScope);
                }
                List<ValueNode> invokeArgsList = null;
                if (frameState.bci == BytecodeFrame.BEFORE_BCI) {
                    /*
                     * We know that the argument list is only used in this case, so avoid the List
                     * allocation for "normal" bcis.
                     */
                    invokeArgsList = Arrays.asList(methodScope.arguments);
                }
                return InliningUtil.processFrameState(frameState, methodScope.invokeData.invoke, methodScope.method, methodScope.exceptionState, methodScope.outerState, true, methodScope.method,
                                invokeArgsList);

            } else if (node instanceof MonitorIdNode) {
                ensureOuterStateDecoded(methodScope);
                InliningUtil.processMonitorId(methodScope.outerState, (MonitorIdNode) node);
                return node;
            }
        }

        return node;
    }
}
