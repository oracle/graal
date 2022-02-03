/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.debug.GraalError.unimplemented;
import static org.graalvm.compiler.nodeinfo.InputType.Anchor;
import static org.graalvm.compiler.nodeinfo.InputType.Guard;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.cfg.CFGVerifier;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.SourceLanguagePosition;
import org.graalvm.compiler.graph.SourceLanguagePositionProvider;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.DeoptBciSupplier;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PluginReplacementInterface;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.SimplifyingGraphDecoder;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.AnchoringNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.InvocationPluginReceiver;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind;
import org.graalvm.compiler.nodes.graphbuilderconf.MethodSubstitutionPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.CoreProvidersDelegate;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

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

    private static final Object CACHED_NULL_VALUE = new Object();

    private static final TimerKey InvocationPluginTimer = DebugContext.timer("PartialEvaluation-InvocationPlugin").doc("Time spent in invocation plugins.");

    private static final TimerKey TryInlineTimer = DebugContext.timer("PartialEvaluation-TryInline").doc("Time spent in trying to inline an invoke.");

    private static final TimerKey TrySimplifyCallTarget = DebugContext.timer("PartialEvaluation-TrySimplifyCallTarget").doc("Time spent in trying to simplify a call target.");

    public static class Options {
        @Option(help = "Maximum inlining depth during partial evaluation before reporting an infinite recursion")//
        public static final OptionKey<Integer> InliningDepthError = new OptionKey<>(1000);

        @Option(help = "Max number of loop explosions per method.", type = OptionType.Debug)//
        public static final OptionKey<Integer> MaximumLoopExplosionCount = new OptionKey<>(10000);

        @Option(help = "Do not bail out but throw an exception on failed loop explosion.", type = OptionType.Debug)//
        public static final OptionKey<Boolean> FailedLoopExplosionIsFatal = new OptionKey<>(false);
    }

    protected class PEMethodScope extends MethodScope {
        /** The state of the caller method. Only non-null during method inlining. */
        public final PEMethodScope caller;
        public final ResolvedJavaMethod method;
        public final InvokeData invokeData;
        public final int inliningDepth;

        protected final ValueNode[] arguments;
        private SourceLanguagePosition sourceLanguagePosition;

        protected FrameState outerState;
        protected FrameState exceptionState;
        public ExceptionPlaceholderNode exceptionPlaceholderNode;
        protected NodeSourcePosition callerBytecodePosition;

        protected PEMethodScope(StructuredGraph targetGraph, PEMethodScope caller, LoopScope callerLoopScope, EncodedGraph encodedGraph, ResolvedJavaMethod method, InvokeData invokeData,
                        int inliningDepth, ValueNode[] arguments) {
            super(callerLoopScope, targetGraph, encodedGraph, loopExplosionKind(method, loopExplosionPlugin));

            this.caller = caller;
            this.method = method;
            this.invokeData = invokeData;
            this.inliningDepth = inliningDepth;
            this.arguments = arguments;

            if (sourceLanguagePositionProvider != null) {
                /* Marker value to compute actual position lazily when needed the first time. */
                sourceLanguagePosition = UnresolvedSourceLanguagePosition.INSTANCE;
            }
        }

        @Override
        public boolean isInlinedMethod() {
            return caller != null;
        }

        /**
         * Gets the call stack representing this method scope and its callers.
         */
        public StackTraceElement[] getCallStack() {
            StackTraceElement[] stack = new StackTraceElement[inliningDepth + 1];
            PEMethodScope frame = this;
            int index = 0;
            int bci = -1;
            while (frame != null) {
                stack[index++] = frame.method.asStackTraceElement(bci);
                bci = frame.invokeData == null ? 0 : frame.invokeData.invoke.bci();
                frame = frame.caller;
            }
            assert index == stack.length : index + " != " + stack.length;
            return stack;
        }

        @Override
        public NodeSourcePosition getCallerBytecodePosition(NodeSourcePosition position) {
            if (caller == null) {
                return position;
            }
            if (callerBytecodePosition == null) {
                NodeSourcePosition invokePosition = invokeData.invoke.asNode().getNodeSourcePosition();
                if (invokePosition == null) {
                    assert position == null : "should only happen when tracking is disabled";
                    return null;
                }
                callerBytecodePosition = invokePosition;
            }
            if (position != null) {
                return position.addCaller(resolveSourceLanguagePosition(), callerBytecodePosition);
            }
            final SourceLanguagePosition pos = resolveSourceLanguagePosition();
            if (pos != null && callerBytecodePosition != null) {
                return new NodeSourcePosition(pos, callerBytecodePosition.getCaller(), callerBytecodePosition.getMethod(), callerBytecodePosition.getBCI());
            }
            return callerBytecodePosition;
        }

        private SourceLanguagePosition resolveSourceLanguagePosition() {
            SourceLanguagePosition res = sourceLanguagePosition;
            if (res == UnresolvedSourceLanguagePosition.INSTANCE) {
                res = null;
                if (arguments != null && method.hasReceiver() && arguments.length > 0 && arguments[0].isJavaConstant()) {
                    JavaConstant constantArgument = arguments[0].asJavaConstant();
                    res = sourceLanguagePositionProvider.getPosition(constantArgument);
                }
                sourceLanguagePosition = res;
            }
            return res;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + '[' + method.format("%H.%n(%p)") + ']';
        }
    }

    private static final class UnresolvedSourceLanguagePosition implements SourceLanguagePosition {
        static final SourceLanguagePosition INSTANCE = new UnresolvedSourceLanguagePosition();

        @Override
        public String toShortString() {
            throw new IllegalStateException(getClass().getSimpleName() + " should not be reachable.");
        }

        @Override
        public int getOffsetEnd() {
            throw new IllegalStateException(getClass().getSimpleName() + " should not be reachable.");
        }

        @Override
        public int getOffsetStart() {
            throw new IllegalStateException(getClass().getSimpleName() + " should not be reachable.");
        }

        @Override
        public int getLineNumber() {
            throw new IllegalStateException(getClass().getSimpleName() + " should not be reachable.");
        }

        @Override
        public URI getURI() {
            throw new IllegalStateException(getClass().getSimpleName() + " should not be reachable.");
        }

        @Override
        public String getLanguage() {
            throw new IllegalStateException(getClass().getSimpleName() + " should not be reachable.");
        }

        @Override
        public int getNodeId() {
            throw new IllegalStateException(getClass().getSimpleName() + " should not be reachable.");
        }

        @Override
        public String getNodeClassName() {
            throw new IllegalStateException(getClass().getSimpleName() + " should not be reachable.");
        }
    }

    protected class PENonAppendGraphBuilderContext extends CoreProvidersDelegate implements GraphBuilderContext {
        protected final PEMethodScope methodScope;
        protected final Invoke invoke;

        @Override
        public ExternalInliningContext getExternalInliningContext() {
            return new ExternalInliningContext() {
                @Override
                public int getInlinedDepth() {
                    int count = 0;
                    PEGraphDecoder.PEMethodScope scope = methodScope;
                    while (scope != null) {
                        if (scope.method.equals(peRootForInlining)) {
                            count++;
                        }
                        scope = scope.caller;
                    }
                    return count;
                }
            };
        }

        public PENonAppendGraphBuilderContext(PEMethodScope methodScope, Invoke invoke) {
            super(providers);
            this.methodScope = methodScope;
            this.invoke = invoke;
        }

        @Override
        public boolean needsExplicitException() {
            return needsExplicitException;
        }

        @Override
        public boolean isParsingInvocationPlugin() {
            return false;
        }

        /**
         * {@link Fold} and {@link NodeIntrinsic} can be deferred during parsing/decoding. Only by
         * the end of {@linkplain SnippetTemplate#instantiate Snippet instantiation} do they need to
         * have been processed.
         *
         * This is how SVM handles snippets. They are parsed with plugins disabled and then encoded
         * and stored in the image. When the snippet is needed at runtime the graph is decoded and
         * the plugins are run during the decoding process. If they aren't handled at this point
         * then they will never be handled.
         */
        @Override
        public boolean canDeferPlugin(GeneratedInvocationPlugin plugin) {
            return plugin.isGeneratedFromFoldOrNodeIntrinsic();
        }

        @Override
        public BailoutException bailout(String string) {
            BailoutException bailout = new PermanentBailoutException(string);
            throw GraphUtil.createBailoutException(string, bailout, methodScope.getCallStack());
        }

        @Override
        public StructuredGraph getGraph() {
            return graph;
        }

        @Override
        public int getDepth() {
            return methodScope.inliningDepth;
        }

        @Override
        public int recursiveInliningDepth(ResolvedJavaMethod method) {
            int result = 0;
            for (PEMethodScope cur = methodScope; cur != null; cur = cur.caller) {
                if (method.equals(cur.method)) {
                    result++;
                }
            }
            return result;
        }

        @Override
        public IntrinsicContext getIntrinsic() {
            return PEGraphDecoder.this.getIntrinsic();
        }

        @Override
        public <T extends ValueNode> T append(T value) {
            throw unimplemented();
        }

        @Override
        public void push(JavaKind kind, ValueNode value) {
            throw unimplemented();
        }

        @Override
        public Invoke handleReplacedInvoke(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, boolean inlineEverything) {
            throw unimplemented();
        }

        @Override
        public void handleReplacedInvoke(CallTargetNode callTarget, JavaKind resultType) {
            throw unimplemented();
        }

        @Override
        public boolean intrinsify(BytecodeProvider bytecodeProvider, ResolvedJavaMethod targetMethod, ResolvedJavaMethod substitute, InvocationPlugin.Receiver receiver, ValueNode[] args) {
            return false;
        }

        @Override
        public boolean intrinsify(ResolvedJavaMethod targetMethod, StructuredGraph substituteGraph, InvocationPlugin.Receiver receiver, ValueNode[] argsIncludingReceiver) {
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
        public Bytecode getCode() {
            throw unimplemented();
        }

        @Override
        public ResolvedJavaMethod getMethod() {
            if (isParsingInvocationPlugin()) {
                /*
                 * While processing an invocation plugin, it is required to return the method that
                 * calls the intrinsified method. But our methodScope object is for the callee,
                 * i.e., the intrinsified method itself, for various other reasons. So we need to
                 * compensate for that.
                 */
                return methodScope.caller.method;
            }
            return methodScope.method;
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

        @Override
        public String toString() {
            Formatter fmt = new Formatter();
            fmt.format("Decoding %s", methodScope.method.format("%H.%n(%p)"));
            for (StackTraceElement e : methodScope.getCallStack()) {
                fmt.format("%n\tat %s", e);
            }
            return fmt.toString();
        }
    }

    protected IntrinsicContext getIntrinsic() {
        return null;
    }

    protected class PEAppendGraphBuilderContext extends PENonAppendGraphBuilderContext {
        protected FixedWithNextNode lastInstr;
        protected ValueNode pushedNode;
        protected boolean invokeConsumed;
        protected boolean exceptionEdgeConsumed;
        protected final InvokeKind invokeKind;
        protected final JavaType invokeReturnType;
        protected final boolean parsingInvocationPlugin;

        public PEAppendGraphBuilderContext(PEMethodScope inlineScope, FixedWithNextNode lastInstr) {
            this(inlineScope, lastInstr, null, null, false);
        }

        public PEAppendGraphBuilderContext(PEMethodScope inlineScope, FixedWithNextNode lastInstr, InvokeKind invokeKind, JavaType invokeReturnType, boolean parsingInvocationPlugin) {
            super(inlineScope, inlineScope.invokeData != null ? inlineScope.invokeData.invoke : null);
            this.lastInstr = lastInstr;
            this.invokeKind = invokeKind;
            this.invokeReturnType = invokeReturnType;
            this.parsingInvocationPlugin = parsingInvocationPlugin;
        }

        @Override
        public boolean isParsingInvocationPlugin() {
            return parsingInvocationPlugin;
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
                T added = getGraph().addOrUniqueWithInputs(v);
                if (added == v) {
                    updateLastInstruction(v);
                }
                return added;
            }
        }

        private DebugCloseable withNodeSoucePosition() {
            if (getGraph().trackNodeSourcePosition()) {
                NodeSourcePosition callerBytecodePosition = methodScope.getCallerBytecodePosition();
                if (callerBytecodePosition != null) {
                    return getGraph().withNodeSourcePosition(callerBytecodePosition);
                }
            }
            return null;
        }

        private <T extends ValueNode> void updateLastInstruction(T v) {
            if (v instanceof FixedNode) {
                FixedNode fixedNode = (FixedNode) v;
                if (lastInstr != null) {
                    lastInstr.setNext(fixedNode);
                }

                if (fixedNode instanceof FixedWithNextNode) {
                    FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) fixedNode;
                    if (fixedWithNextNode.next() == null) {
                        lastInstr = fixedWithNextNode;
                    } else {
                        lastInstr = null;
                    }
                } else if (fixedNode instanceof WithExceptionNode) {
                    if (exceptionEdgeConsumed) {
                        throw GraalError.unimplemented("Only one node can consume the exception edge");
                    }
                    exceptionEdgeConsumed = true;
                    WithExceptionNode withExceptionNode = (WithExceptionNode) fixedNode;
                    if (withExceptionNode.exceptionEdge() == null) {
                        ExceptionObjectNode exceptionEdge = (ExceptionObjectNode) makeStubNode(methodScope.caller, methodScope.callerLoopScope, methodScope.invokeData.exceptionOrderId);
                        withExceptionNode.setExceptionEdge(exceptionEdge);
                    }
                    if (withExceptionNode.next() == null) {
                        AbstractBeginNode nextBegin = graph.add(new BeginNode());
                        withExceptionNode.setNext(nextBegin);
                        lastInstr = nextBegin;
                    } else {
                        lastInstr = null;
                    }
                } else {
                    assert fixedNode instanceof AbstractEndNode || fixedNode instanceof ControlSinkNode || fixedNode instanceof ControlSplitNode : fixedNode;
                    lastInstr = null;
                }
            }
        }

        @Override
        public InvokeKind getInvokeKind() {
            if (invokeKind != null) {
                return invokeKind;
            }
            return super.getInvokeKind();
        }

        @Override
        public JavaType getInvokeReturnType() {
            if (invokeReturnType != null) {
                return invokeReturnType;
            }
            return super.getInvokeReturnType();
        }

        @Override
        public void handleReplacedInvoke(CallTargetNode callTarget, JavaKind resultType) {
            if (invokeConsumed || exceptionEdgeConsumed) {
                throw GraalError.unimplemented("handleReplacedInvoke can be called only once, and also consumes the exception edge");
            }
            invokeConsumed = true;
            exceptionEdgeConsumed = true;

            appendInvoke(methodScope.caller, methodScope.callerLoopScope, methodScope.invokeData, callTarget);

            lastInstr.setNext(invoke.asFixedNode());
            if (invoke instanceof InvokeWithExceptionNode) {
                lastInstr = ((InvokeWithExceptionNode) invoke).next();
            } else {
                lastInstr = (InvokeNode) invoke;
            }
        }

        @Override
        public AbstractBeginNode genExplicitExceptionEdge(BytecodeExceptionNode.BytecodeExceptionKind exceptionKind, ValueNode... exceptionArguments) {
            if (exceptionEdgeConsumed) {
                throw GraalError.unimplemented("Only one node can consume the exception edge");
            }
            exceptionEdgeConsumed = true;

            BytecodeExceptionNode exceptionNode = graph.add(new BytecodeExceptionNode(getMetaAccess(), exceptionKind, exceptionArguments));

            ensureExceptionStateDecoded(methodScope);
            methodScope.exceptionPlaceholderNode.replaceAtUsagesAndDelete(exceptionNode);
            registerNode(methodScope.callerLoopScope, methodScope.invokeData.exceptionOrderId, exceptionNode, true, false);
            exceptionNode.setStateAfter(methodScope.exceptionState);

            exceptionNode.setNext(makeStubNode(methodScope.caller, methodScope.callerLoopScope, methodScope.invokeData.exceptionNextOrderId));
            return BeginNode.begin(exceptionNode);
        }

        @Override
        public GraphBuilderContext getNonIntrinsicAncestor() {
            return null;
        }
    }

    protected class PEPluginGraphBuilderContext extends PENonAppendGraphBuilderContext {
        protected final FixedNode insertBefore;
        protected ValueNode pushedNode;

        public PEPluginGraphBuilderContext(PEMethodScope inlineScope, FixedNode insertBefore) {
            super(inlineScope, inlineScope.invokeData != null ? inlineScope.invokeData.invoke : null);
            this.insertBefore = insertBefore;
        }

        @Override
        public void push(JavaKind kind, ValueNode value) {
            if (pushedNode != null) {
                throw unimplemented("Only one push is supported");
            }
            pushedNode = value;
        }

        @Override
        public void setStateAfter(StateSplit sideEffect) {
            assert sideEffect.hasSideEffect();
            FrameState stateAfter = getGraph().add(new FrameState(BytecodeFrame.BEFORE_BCI));
            sideEffect.setStateAfter(stateAfter);
        }

        @SuppressWarnings("try")
        @Override
        public <T extends ValueNode> T append(T v) {
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

        private DebugCloseable withNodeSoucePosition() {
            if (getGraph().trackNodeSourcePosition()) {
                NodeSourcePosition callerBytecodePosition = methodScope.getCallerBytecodePosition();
                if (callerBytecodePosition != null) {
                    return getGraph().withNodeSourcePosition(callerBytecodePosition);
                }
            }
            return null;
        }

        private <T extends ValueNode> void updateLastInstruction(T value) {
            if (value instanceof FixedWithNextNode) {
                FixedWithNextNode fixed = (FixedWithNextNode) value;
                graph.addBeforeFixed(insertBefore, fixed);
            } else if (value instanceof WithExceptionNode) {
                WithExceptionNode withExceptionNode = (WithExceptionNode) value;
                GraalError.guarantee(insertBefore instanceof WithExceptionNode, "Cannot replace %s with %s which is a %s", insertBefore, value, WithExceptionNode.class.getSimpleName());
                WithExceptionNode replacee = (WithExceptionNode) insertBefore;
                graph.replaceWithExceptionSplit(replacee, withExceptionNode);
                AbstractBeginNode next = withExceptionNode.next();
                if (withExceptionNode instanceof MemoryKill) {
                    /* Insert the correct memory killing begin node at the next edge. */
                    GraalError.guarantee(next instanceof BeginNode, "Not a BeginNode %s", next);
                    AbstractBeginNode beginNode = graph.add(new BeginNode());
                    withExceptionNode.setNext(beginNode);
                    beginNode.setNext(next);
                }
            } else if (value instanceof FixedNode) {
                // Block terminating fixed nodes shouldn't be inserted
                throw GraalError.shouldNotReachHere(String.format("value: %s, insertBefore: %s", value, insertBefore));
            }
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED, allowedUsageTypes = {InputType.Value, InputType.Guard, InputType.Anchor})
    public static class ExceptionPlaceholderNode extends ValueNode {
        public static final NodeClass<ExceptionPlaceholderNode> TYPE = NodeClass.create(ExceptionPlaceholderNode.class);

        protected ExceptionPlaceholderNode() {
            super(TYPE, StampFactory.object());
        }
    }

    @NodeInfo(allowedUsageTypes = {Anchor, Guard, InputType.Value}, cycles = CYCLES_0, size = SIZE_0)
    static final class FixedAnchorNode extends FixedWithNextNode implements AnchoringNode, GuardingNode, IterableNodeType {

        public static final NodeClass<FixedAnchorNode> TYPE = NodeClass.create(FixedAnchorNode.class);
        @Input ValueNode value;

        protected FixedAnchorNode(ValueNode value) {
            super(TYPE, value.stamp(NodeView.DEFAULT));
            this.value = value;
        }
    }

    public static class SpecialCallTargetCacheKey {
        private final InvokeKind invokeKind;
        private final ResolvedJavaMethod targetMethod;
        private final ResolvedJavaType contextType;
        private final Stamp receiverStamp;

        public SpecialCallTargetCacheKey(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ResolvedJavaType contextType, Stamp receiverStamp) {
            this.invokeKind = invokeKind;
            this.targetMethod = targetMethod;
            this.contextType = contextType;
            this.receiverStamp = receiverStamp;
        }

        @Override
        public int hashCode() {
            return invokeKind.hashCode() ^ targetMethod.hashCode() ^ contextType.hashCode() ^ receiverStamp.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SpecialCallTargetCacheKey) {
                SpecialCallTargetCacheKey key = (SpecialCallTargetCacheKey) obj;
                return key.invokeKind.equals(this.invokeKind) && key.targetMethod.equals(this.targetMethod) && key.contextType.equals(this.contextType) && key.receiverStamp.equals(this.receiverStamp);
            }
            return false;
        }
    }

    private final LoopExplosionPlugin loopExplosionPlugin;
    private final InvocationPlugins invocationPlugins;
    private final InlineInvokePlugin[] inlineInvokePlugins;
    private final ParameterPlugin parameterPlugin;
    private final NodePlugin[] nodePlugins;
    private final ConcurrentHashMap<SpecialCallTargetCacheKey, Object> specialCallTargetCache;
    private final ConcurrentHashMap<ResolvedJavaMethod, Object> invocationPluginCache;
    private final ResolvedJavaMethod peRootForInlining;
    protected final SourceLanguagePositionProvider sourceLanguagePositionProvider;
    protected final boolean needsExplicitException;

    public PEGraphDecoder(Architecture architecture, StructuredGraph graph, CoreProviders providers, LoopExplosionPlugin loopExplosionPlugin, InvocationPlugins invocationPlugins,
                    InlineInvokePlugin[] inlineInvokePlugins, ParameterPlugin parameterPlugin,
                    NodePlugin[] nodePlugins, ResolvedJavaMethod peRootForInlining, SourceLanguagePositionProvider sourceLanguagePositionProvider,
                    ConcurrentHashMap<SpecialCallTargetCacheKey, Object> specialCallTargetCache,
                    ConcurrentHashMap<ResolvedJavaMethod, Object> invocationPluginCache, boolean needsExplicitException) {
        super(architecture, graph, providers, true);
        this.loopExplosionPlugin = loopExplosionPlugin;
        this.invocationPlugins = invocationPlugins;
        this.inlineInvokePlugins = inlineInvokePlugins;
        this.parameterPlugin = parameterPlugin;
        this.nodePlugins = nodePlugins;
        this.specialCallTargetCache = specialCallTargetCache;
        this.invocationPluginCache = invocationPluginCache;
        this.peRootForInlining = peRootForInlining;
        this.sourceLanguagePositionProvider = sourceLanguagePositionProvider;
        this.needsExplicitException = needsExplicitException;
    }

    protected static LoopExplosionKind loopExplosionKind(ResolvedJavaMethod method, LoopExplosionPlugin loopExplosionPlugin) {
        if (loopExplosionPlugin == null) {
            return LoopExplosionKind.NONE;
        } else {
            return loopExplosionPlugin.loopExplosionKind(method);
        }
    }

    @SuppressWarnings("try")
    public void decode(ResolvedJavaMethod method, boolean isSubstitution, boolean trackNodeSourcePosition) {
        try (DebugContext.Scope scope = debug.scope("PEGraphDecode", graph)) {
            EncodedGraph encodedGraph = lookupEncodedGraph(method, null, null, isSubstitution, trackNodeSourcePosition);
            recordGraphElements(encodedGraph);
            PEMethodScope methodScope = createMethodScope(graph, null, null, encodedGraph, method, null, 0, null);
            decode(createInitialLoopScope(methodScope, null));
            debug.dump(DebugContext.VERBOSE_LEVEL, graph, "Before graph cleanup");
            cleanupGraph(methodScope);

            debug.dump(DebugContext.VERBOSE_LEVEL, graph, "After graph cleanup");
            assert graph.verify();
        } catch (Throwable t) {
            throw debug.handle(t);
        }

        try {
            /* Check that the control flow graph can be computed, to catch problems early. */
            assert CFGVerifier.verify(ControlFlowGraph.compute(graph, true, true, true, true));
        } catch (Throwable ex) {
            throw GraalError.shouldNotReachHere(ex, "Control flow graph not valid after partial evaluation");
        }
    }

    protected PEMethodScope createMethodScope(StructuredGraph targetGraph, PEMethodScope caller, LoopScope callerLoopScope, EncodedGraph encodedGraph, ResolvedJavaMethod method, InvokeData invokeData,
                    int inliningDepth, ValueNode[] arguments) {
        return new PEMethodScope(targetGraph, caller, callerLoopScope, encodedGraph, method, invokeData, inliningDepth, arguments);
    }

    private void recordGraphElements(EncodedGraph encodedGraph) {
        List<ResolvedJavaMethod> inlinedMethods = encodedGraph.getInlinedMethods();
        if (inlinedMethods != null) {
            for (ResolvedJavaMethod other : inlinedMethods) {
                graph.recordMethod(other);
            }
        }
        Assumptions assumptions = graph.getAssumptions();
        Assumptions inlinedAssumptions = encodedGraph.getAssumptions();
        if (assumptions != null) {
            if (inlinedAssumptions != null) {
                assumptions.record(inlinedAssumptions);
            }
        } else {
            assert inlinedAssumptions == null : String.format("cannot inline graph (%s) which makes assumptions into a graph (%s) that doesn't", encodedGraph, graph);
        }
        if (encodedGraph.hasUnsafeAccess()) {
            graph.markUnsafeAccess();
        }
    }

    @Override
    protected void cleanupGraph(MethodScope methodScope) {
        super.cleanupGraph(methodScope);

        for (FrameState frameState : graph.getNodes(FrameState.TYPE)) {
            if (frameState.bci == BytecodeFrame.UNWIND_BCI) {
                /*
                 * handleMissingAfterExceptionFrameState is called during graph decoding from
                 * InliningUtil.processFrameState - but during graph decoding it does not do
                 * anything because the usages of the frameState are not available yet. So we need
                 * to call it again.
                 */
                PEMethodScope peMethodScope = (PEMethodScope) methodScope;
                Invoke invoke = peMethodScope.invokeData != null ? peMethodScope.invokeData.invoke : null;
                InliningUtil.handleMissingAfterExceptionFrameState(frameState, invoke, null, true);

                /*
                 * The frameState must be gone now, because it is not a valid deoptimization point.
                 */
                assert frameState.isDeleted();
            }
        }
        /*
         * Cleanup anchor nodes introduced for exception object anchors during inlining. When we
         * inline through an invoke with exception and the caller has an exception handler attached
         * this handler can use the exception object node as anchor or guard. During inlining the
         * exception object node is replaced with its actual value produced in the callee, however
         * this value is not necessarily a guarding or anchoring node. Thus, we introduce artificial
         * fixed anchor nodes in the exceptional path in the callee the caller can use as anchor,
         * guard or value input. After we are done we remove all anchors that are not needed any
         * more, i.e., they do not have any guard or anchor usages. If they have guard or anchor
         * usages we rewrite the anchor for exactly those nodes to a real value anchor node that can
         * be optimized later.
         *
         * Optimizing the anchor early is not possible during partial evaluation, since we do not
         * know if a node not yet decoded in the caller will reference the exception object
         * replacement node in the callee as an anchor or guard.
         */
        for (FixedAnchorNode anchor : graph.getNodes(FixedAnchorNode.TYPE).snapshot()) {
            AbstractBeginNode newAnchor = AbstractBeginNode.prevBegin(anchor);
            assert newAnchor != null : "Must find prev begin node";
            anchor.replaceAtUsages(newAnchor, InputType.Guard, InputType.Anchor);
            // all other usages can really consume the value
            anchor.replaceAtUsages(anchor.value);
            assert anchor.hasNoUsages();
            GraphUtil.unlinkFixedNode(anchor);
            anchor.safeDelete();
        }
    }

    @Override
    protected void checkLoopExplosionIteration(MethodScope s, LoopScope loopScope) {
        PEMethodScope methodScope = (PEMethodScope) s;
        if (loopScope.loopIteration > Options.MaximumLoopExplosionCount.getValue(options)) {
            throw tooManyLoopExplosionIterations(methodScope, options);
        }
    }

    private static RuntimeException tooManyLoopExplosionIterations(PEMethodScope methodScope, OptionValues options) {
        String message = "too many loop explosion iterations - does the explosion not terminate for method " + methodScope.method + "?";
        RuntimeException bailout = Options.FailedLoopExplosionIsFatal.getValue(options) ? new RuntimeException(message) : new PermanentBailoutException(message);
        throw GraphUtil.createBailoutException(message, bailout, methodScope.getCallStack());
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
        invokeData.callTarget = callTarget;
        if (callTarget instanceof MethodCallTargetNode) {
            MethodCallTargetNode methodCall = (MethodCallTargetNode) callTarget;
            if (methodCall.invokeKind().hasReceiver()) {
                invokeData.constantReceiver = methodCall.arguments().get(0).asJavaConstant();
            }
            callTarget = trySimplifyCallTarget(methodScope, invokeData, (MethodCallTargetNode) callTarget);
            LoopScope inlineLoopScope = trySimplifyInvoke(methodScope, loopScope, invokeData, (MethodCallTargetNode) callTarget);
            if (inlineLoopScope != null) {
                return inlineLoopScope;
            }
        }

        handleNonInlinedInvoke(methodScope, loopScope, invokeData);
        return loopScope;
    }

    protected void handleNonInlinedInvoke(MethodScope methodScope, LoopScope loopScope, InvokeData invokeData) {
        CallTargetNode callTarget = invokeData.callTarget;

        /* We know that we need an invoke, so now we can add the call target to the graph. */
        graph.add(callTarget);
        registerNode(loopScope, invokeData.callTargetOrderId, callTarget, false, false);
        appendInvoke(methodScope, loopScope, invokeData, callTarget);
    }

    @SuppressWarnings({"unused", "try"})
    protected MethodCallTargetNode trySimplifyCallTarget(PEMethodScope methodScope, InvokeData invokeData, MethodCallTargetNode callTarget) {
        try (DebugCloseable a = TrySimplifyCallTarget.start(debug)) {
            // attempt to devirtualize the call
            ResolvedJavaMethod specialCallTarget = getSpecialCallTarget(invokeData, callTarget);
            if (specialCallTarget != null) {
                callTarget.setTargetMethod(specialCallTarget);
                callTarget.setInvokeKind(InvokeKind.Special);
                return callTarget;
            }
            if (callTarget.invokeKind().isInterface()) {
                Invoke invoke = invokeData.invoke;
                ResolvedJavaType contextType = methodScope.method.getDeclaringClass();
                return MethodCallTargetNode.tryDevirtualizeInterfaceCall(callTarget.receiver(), callTarget.targetMethod(), null, graph.getAssumptions(), contextType, callTarget, invoke.asFixedNode());
            }
            return callTarget;
        }
    }

    protected LoopScope trySimplifyInvoke(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, MethodCallTargetNode callTarget) {
        final boolean invocationPluginTriggered = tryInvocationPlugin(methodScope, loopScope, invokeData, callTarget);
        if (invocationPluginTriggered) {
            /*
             * The invocation plugin handled the call, so decoding continues in the calling method.
             */
            return loopScope;
        }
        final LoopScope inlineLoopScope = tryInline(methodScope, loopScope, invokeData, callTarget);
        if (inlineLoopScope != null) {
            /*
             * We can inline the call, so decoding continues in the inlined method.
             */
            return inlineLoopScope;
        }

        for (InlineInvokePlugin plugin : inlineInvokePlugins) {
            plugin.notifyNotInlined(new PENonAppendGraphBuilderContext(methodScope, invokeData.invoke), callTarget.targetMethod(), invokeData.invoke);
        }
        return null;
    }

    private ResolvedJavaMethod getSpecialCallTarget(InvokeData invokeData, MethodCallTargetNode callTarget) {
        if (callTarget.invokeKind().isDirect()) {
            return null;
        }

        // check for trivial cases (e.g. final methods, nonvirtual methods)
        if (callTarget.targetMethod().canBeStaticallyBound()) {
            return callTarget.targetMethod();
        }

        SpecialCallTargetCacheKey key = new SpecialCallTargetCacheKey(callTarget.invokeKind(), callTarget.targetMethod(), invokeData.contextType, callTarget.receiver().stamp(NodeView.DEFAULT));
        Object specialCallTarget = specialCallTargetCache.computeIfAbsent(key, k -> {
            Object target = MethodCallTargetNode.devirtualizeCall(k.invokeKind, k.targetMethod, k.contextType, graph.getAssumptions(),
                            k.receiverStamp);
            if (target == null) {
                target = CACHED_NULL_VALUE;
            }
            return target;
        });

        return specialCallTarget == CACHED_NULL_VALUE ? null : (ResolvedJavaMethod) specialCallTarget;
    }

    @SuppressWarnings({"unused", "try"})
    protected boolean tryInvocationPlugin(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, MethodCallTargetNode callTarget) {
        try (DebugCloseable a = InvocationPluginTimer.start(debug)) {
            if (invocationPlugins == null || invocationPlugins.isEmpty()) {
                return false;
            }
            if (!callTarget.invokeKind().isDirect()) {
                /*
                 * Like method inlining, method intrinsification using InvocationPlugin can only
                 * handle direct calls. Indirect calls can only be intrinsified by a NodePlugin (and
                 * currently only during bytecode parsing and not during partial evaluation).
                 */
                return false;
            }

            Invoke invoke = invokeData.invoke;

            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            if (loopScope.methodScope.encodedGraph.isCallToOriginal(targetMethod)) {
                return false;
            }

            InvocationPlugin invocationPlugin = getInvocationPlugin(targetMethod);
            if (invocationPlugin == null) {
                return false;
            }

            if (loopScope.methodScope.encodedGraph.isCallToOriginal(targetMethod)) {
                return false;
            }

            ValueNode[] arguments = callTarget.arguments().toArray(new ValueNode[0]);
            FixedWithNextNode invokePredecessor = (FixedWithNextNode) invoke.asNode().predecessor();

            /*
             * Remove invoke from graph so that invocation plugin can append nodes to the
             * predecessor.
             */
            invoke.asNode().replaceAtPredecessor(null);

            PEMethodScope inlineScope = createMethodScope(graph, methodScope, loopScope, null, targetMethod, invokeData, methodScope.inliningDepth + 1, arguments);

            JavaType returnType = targetMethod.getSignature().getReturnType(methodScope.method.getDeclaringClass());
            PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(inlineScope, invokePredecessor, callTarget.invokeKind(), returnType, true);
            InvocationPluginReceiver invocationPluginReceiver = new InvocationPluginReceiver(graphBuilderContext);

            if (invocationPlugin.execute(graphBuilderContext, targetMethod, invocationPluginReceiver.init(targetMethod, arguments), arguments)) {

                if (graphBuilderContext.invokeConsumed) {
                    /* Nothing to do. */
                } else if (graphBuilderContext.lastInstr != null) {
                    if (graphBuilderContext.lastInstr instanceof DeoptBciSupplier && !BytecodeFrame.isPlaceholderBci(invokeData.invoke.bci()) &&
                                    BytecodeFrame.isPlaceholderBci(((DeoptBciSupplier) graphBuilderContext.lastInstr).bci())) {
                        ((DeoptBciSupplier) graphBuilderContext.lastInstr).setBci(invokeData.invoke.bci());
                    }
                    registerNode(loopScope, invokeData.invokeOrderId, graphBuilderContext.pushedNode, true, true);
                    invoke.asNode().replaceAtUsages(graphBuilderContext.pushedNode);
                    BeginNode begin = graphBuilderContext.lastInstr instanceof BeginNode ? (BeginNode) graphBuilderContext.lastInstr : null;
                    FixedNode afterInvoke = nodeAfterInvoke(methodScope, loopScope, invokeData, begin);
                    if (afterInvoke != graphBuilderContext.lastInstr) {
                        graphBuilderContext.lastInstr.setNext(afterInvoke);
                    }
                    deleteInvoke(invoke);
                } else {
                    assert graphBuilderContext.pushedNode == null : "Why push a node when the invoke does not return anyway?";
                    invoke.asNode().replaceAtUsages(null);
                    deleteInvoke(invoke);
                }
                return true;

            } else {
                /* Intrinsification failed, restore original state: invoke is in Graph. */
                invokePredecessor.setNext(invoke.asFixedNode());
                return false;
            }
        }
    }

    private InvocationPlugin getInvocationPlugin(ResolvedJavaMethod targetMethod) {
        Object invocationPlugin = invocationPluginCache.computeIfAbsent(targetMethod, method -> {
            Object plugin = invocationPlugins.lookupInvocation(targetMethod);
            if (plugin == null) {
                plugin = CACHED_NULL_VALUE;
            }
            return plugin;
        });

        return invocationPlugin == CACHED_NULL_VALUE ? null : (InvocationPlugin) invocationPlugin;
    }

    @SuppressWarnings({"unused", "try"})
    protected LoopScope tryInline(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, MethodCallTargetNode callTarget) {
        try (DebugCloseable a = TryInlineTimer.start(debug)) {
            if (!callTarget.invokeKind().isDirect()) {
                return null;
            }

            ResolvedJavaMethod targetMethod = callTarget.targetMethod();
            if (targetMethod.hasNeverInlineDirective()) {
                return null;
            }

            ValueNode[] arguments = callTarget.arguments().toArray(new ValueNode[0]);
            GraphBuilderContext graphBuilderContext = new PENonAppendGraphBuilderContext(methodScope, invokeData.invoke);

            for (InlineInvokePlugin plugin : inlineInvokePlugins) {
                InlineInfo inlineInfo = plugin.shouldInlineInvoke(graphBuilderContext, targetMethod, arguments);
                if (inlineInfo != null) {
                    if (inlineInfo.allowsInlining()) {
                        return doInline(methodScope, loopScope, invokeData, inlineInfo, arguments);
                    } else {
                        return null;
                    }
                }
            }
            return null;
        }
    }

    protected LoopScope doInline(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, InlineInfo inlineInfo, ValueNode[] arguments) {
        if (invokeData.invoke.getInlineControl() != Invoke.InlineControl.Normal) {
            // The graph decoder only has one version of the method so treat the BytecodesOnly case
            // as don't inline.
            return null;
        }
        ResolvedJavaMethod inlineMethod = inlineInfo.getMethodToInline();
        EncodedGraph graphToInline = lookupEncodedGraph(inlineMethod, inlineInfo.getPlugin(), inlineInfo.getIntrinsicBytecodeProvider(), inlineInfo.isSubstitution(), graph.trackNodeSourcePosition());
        if (graphToInline == null) {
            return null;
        }

        assert !graph.trackNodeSourcePosition() || graphToInline.trackNodeSourcePosition() : graph + " " + graphToInline;
        if (methodScope.inliningDepth > Options.InliningDepthError.getValue(options)) {
            throw tooDeepInlining(methodScope);
        }

        for (InlineInvokePlugin plugin : inlineInvokePlugins) {
            plugin.notifyBeforeInline(inlineMethod);
        }

        Invoke invoke = invokeData.invoke;
        FixedNode invokeNode = invoke.asFixedNode();
        FixedWithNextNode predecessor = (FixedWithNextNode) invokeNode.predecessor();
        invokeData.invokePredecessor = predecessor;
        invokeNode.replaceAtPredecessor(null);

        PEMethodScope inlineScope = createMethodScope(graph, methodScope, loopScope, graphToInline, inlineMethod, invokeData, methodScope.inliningDepth + 1, arguments);

        if (!inlineMethod.isStatic()) {
            if (StampTool.isPointerAlwaysNull(arguments[0])) {
                /*
                 * The receiver is null, so we can unconditionally throw a NullPointerException
                 * instead of performing any inlining.
                 */
                DeoptimizeNode deoptimizeNode = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.NullCheckException));
                predecessor.setNext(deoptimizeNode);
                finishInlining(inlineScope);
                /* Continue decoding in the caller. */
                return loopScope;

            } else if (!StampTool.isPointerNonNull(arguments[0])) {
                /* The receiver might be null, so we need to insert a null check. */
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(inlineScope, predecessor);
                arguments[0] = graphBuilderContext.nullCheckedValue(arguments[0]);
                predecessor = graphBuilderContext.lastInstr;
            }
        }

        LoopScope inlineLoopScope = createInitialLoopScope(inlineScope, predecessor);

        /*
         * The GraphEncoder assigns parameters a nodeId immediately after the fixed nodes.
         * Initializing createdNodes here avoid decoding and immediately replacing the
         * ParameterNodes.
         */
        int firstArgumentNodeId = inlineScope.maxFixedNodeOrderId + 1;
        for (int i = 0; i < arguments.length; i++) {
            inlineLoopScope.createdNodes[firstArgumentNodeId + i] = arguments[i];
        }

        // Copy inlined methods from inlinee to caller
        recordGraphElements(graphToInline);

        /*
         * Do the actual inlining by returning the initial loop scope for the inlined method scope.
         */
        return inlineLoopScope;
    }

    @Override
    protected void finishInlining(MethodScope is) {
        PEMethodScope inlineScope = (PEMethodScope) is;
        ResolvedJavaMethod inlineMethod = inlineScope.method;
        PEMethodScope methodScope = inlineScope.caller;
        LoopScope loopScope = inlineScope.callerLoopScope;
        InvokeData invokeData = inlineScope.invokeData;
        Invoke invoke = invokeData.invoke;
        FixedNode invokeNode = invoke.asFixedNode();

        ValueNode exceptionValue = null;
        int returnNodeCount = 0;
        int unwindNodeCount = 0;
        List<ControlSinkNode> returnAndUnwindNodes = inlineScope.returnAndUnwindNodes;
        for (int i = 0; i < returnAndUnwindNodes.size(); i++) {
            FixedNode fixedNode = returnAndUnwindNodes.get(i);
            if (fixedNode instanceof ReturnNode) {
                returnNodeCount++;
            } else if (fixedNode.isAlive()) {
                assert fixedNode instanceof UnwindNode;
                unwindNodeCount++;
            }
        }

        if (unwindNodeCount > 0) {
            FixedNode unwindReplacement;
            if (invoke instanceof InvokeWithExceptionNode) {
                /* Decoding continues for the exception handler. */
                unwindReplacement = makeStubNode(methodScope, loopScope, invokeData.exceptionNextOrderId);
            } else {
                /* No exception handler available, so the only thing we can do is deoptimize. */
                unwindReplacement = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.NotCompiledExceptionHandler));
            }

            if (unwindNodeCount == 1) {
                /* Only one UnwindNode, we can use the exception directly. */
                UnwindNode unwindNode = getSingleMatchingNode(returnAndUnwindNodes, returnNodeCount > 0, UnwindNode.class);
                exceptionValue = unwindNode.exception();
                unwindNode.replaceAndDelete(unwindReplacement);

            } else {
                /*
                 * More than one UnwindNode. This can happen with the loop explosion strategy
                 * FULL_EXPLODE_UNTIL_RETURN, where we keep exploding after the loop and therefore
                 * also explode exception paths. Merge the exception in a similar way as multiple
                 * return values.
                 */
                MergeNode unwindMergeNode = graph.add(new MergeNode());
                exceptionValue = InliningUtil.mergeValueProducers(unwindMergeNode, getMatchingNodes(returnAndUnwindNodes, returnNodeCount > 0, UnwindNode.class, unwindNodeCount),
                                null, unwindNode -> unwindNode.exception());
                unwindMergeNode.setNext(unwindReplacement);
                ensureExceptionStateDecoded(inlineScope);
                unwindMergeNode.setStateAfter(inlineScope.exceptionState.duplicateModified(JavaKind.Object, JavaKind.Object, exceptionValue, null));
            }
            if (invoke instanceof InvokeWithExceptionNode) {
                /*
                 * Exceptionobject nodes are begin nodes, i.e., they can be used as guards/anchors
                 * thus we need to ensure nodes decoded later have a correct guarding/anchoring node
                 * in place, i.e., the exception value must be a node that can be used like the
                 * original node as value, guard and anchor.
                 *
                 * The node unwindReplacement is a stub, its not yet processed thus we insert our
                 * artificial anchor before it.
                 */
                assert unwindReplacement != exceptionValue : "Unschedulable unwind replacement";
                FixedAnchorNode anchor = graph.add(new FixedAnchorNode(exceptionValue));
                graph.addBeforeFixed(unwindReplacement, anchor);
                exceptionValue = anchor;
                assert anchor.predecessor() != null;
            }
        }

        assert invoke.next() == null;
        assert !(invoke instanceof InvokeWithExceptionNode) || ((InvokeWithExceptionNode) invoke).exceptionEdge() == null;

        ValueNode returnValue;
        if (returnNodeCount == 0) {
            returnValue = null;
        } else if (returnNodeCount == 1) {
            ReturnNode returnNode = getSingleMatchingNode(returnAndUnwindNodes, unwindNodeCount > 0, ReturnNode.class);
            returnValue = returnNode.result();
            FixedNode n = nodeAfterInvoke(methodScope, loopScope, invokeData, null);
            returnNode.replaceAndDelete(n);
        } else {
            AbstractMergeNode merge = graph.add(new MergeNode());
            merge.setStateAfter((FrameState) ensureNodeCreated(methodScope, loopScope, invokeData.stateAfterOrderId));
            returnValue = InliningUtil.mergeReturns(merge, getMatchingNodes(returnAndUnwindNodes, unwindNodeCount > 0, ReturnNode.class, returnNodeCount));
            FixedNode n = nodeAfterInvoke(methodScope, loopScope, invokeData, null);
            merge.setNext(n);
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

        assert exceptionValue == null || exceptionValue instanceof FixedAnchorNode && exceptionValue.predecessor() != null;

        for (InlineInvokePlugin plugin : inlineInvokePlugins) {
            plugin.notifyAfterInline(inlineMethod);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getSingleMatchingNode(List<ControlSinkNode> returnAndUnwindNodes, boolean hasNonMatchingEntries, Class<T> clazz) {
        if (!hasNonMatchingEntries) {
            assert returnAndUnwindNodes.size() == 1;
            return (T) returnAndUnwindNodes.get(0);
        }

        for (int i = 0; i < returnAndUnwindNodes.size(); i++) {
            ControlSinkNode node = returnAndUnwindNodes.get(i);
            if (clazz.isInstance(node)) {
                return (T) node;
            }
        }
        throw GraalError.shouldNotReachHere();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> getMatchingNodes(List<ControlSinkNode> returnAndUnwindNodes, boolean hasNonMatchingEntries, Class<T> clazz, int resultCount) {
        if (!hasNonMatchingEntries) {
            return (List<T>) returnAndUnwindNodes;
        }

        List<T> result = new ArrayList<>(resultCount);
        for (int i = 0; i < returnAndUnwindNodes.size(); i++) {
            ControlSinkNode node = returnAndUnwindNodes.get(i);
            if (clazz.isInstance(node)) {
                result.add((T) node);
            }
        }
        assert result.size() == resultCount;
        return result;
    }

    private static RuntimeException tooDeepInlining(PEMethodScope methodScope) {
        HashMap<ResolvedJavaMethod, Integer> methodCounts = new HashMap<>();
        for (PEMethodScope cur = methodScope; cur != null; cur = cur.caller) {
            Integer oldCount = methodCounts.get(cur.method);
            methodCounts.put(cur.method, oldCount == null ? 1 : oldCount + 1);
        }

        List<Map.Entry<ResolvedJavaMethod, Integer>> methods = new ArrayList<>(methodCounts.entrySet());
        methods.sort((e1, e2) -> -Integer.compare(e1.getValue(), e2.getValue()));

        StringBuilder msg = new StringBuilder("Too deep inlining, probably caused by recursive inlining.").append(System.lineSeparator()).append("== Inlined methods ordered by inlining frequency:");
        for (Map.Entry<ResolvedJavaMethod, Integer> entry : methods) {
            msg.append(System.lineSeparator()).append(entry.getKey().format("%H.%n(%p) [")).append(entry.getValue()).append("]");
        }
        msg.append(System.lineSeparator()).append("== Complete stack trace of inlined methods:");
        int lastBci = 0;
        for (PEMethodScope cur = methodScope; cur != null; cur = cur.caller) {
            msg.append(System.lineSeparator()).append(cur.method.asStackTraceElement(lastBci));
            if (cur.invokeData != null) {
                lastBci = cur.invokeData.invoke.bci();
            } else {
                lastBci = 0;
            }
        }

        throw new PermanentBailoutException(msg.toString());
    }

    public FixedNode nodeAfterInvoke(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, BeginNode prevBegin) {
        assert prevBegin == null || prevBegin.isAlive();
        if (invokeData.invoke instanceof InvokeWithExceptionNode) {
            if (prevBegin != null && getNodeClass(methodScope, loopScope, invokeData.nextOrderId) == prevBegin.getNodeClass()) {
                // Reuse the previous Node but mark it in nodesToProcess so that the decoding loop
                // continues decoding.
                loopScope.nodesToProcess.set(invokeData.nextOrderId);
                registerNode(loopScope, invokeData.nextOrderId, prevBegin, false, false);
                return prevBegin;
            }
        }
        return makeStubNode(methodScope, loopScope, invokeData.nextOrderId);
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

    protected abstract EncodedGraph lookupEncodedGraph(ResolvedJavaMethod method, MethodSubstitutionPlugin plugin, BytecodeProvider intrinsicBytecodeProvider, boolean isSubstitution,
                    boolean trackNodeSourcePosition);

    @SuppressWarnings("try")
    @Override
    protected Node canonicalizeFixedNode(MethodScope s, Node node) {
        PEMethodScope methodScope = (PEMethodScope) s;

        Node replacedNode = node;
        if (nodePlugins != null && nodePlugins.length > 0) {
            if (node instanceof LoadFieldNode) {
                LoadFieldNode loadFieldNode = (LoadFieldNode) node;
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, loadFieldNode);
                ResolvedJavaField field = loadFieldNode.field();
                if (loadFieldNode.isStatic()) {
                    for (NodePlugin nodePlugin : nodePlugins) {
                        if (nodePlugin.handleLoadStaticField(graphBuilderContext, field)) {
                            replacedNode = graphBuilderContext.pushedNode;
                            break;
                        }
                    }
                } else {
                    ValueNode object = loadFieldNode.object();
                    for (NodePlugin nodePlugin : nodePlugins) {
                        if (nodePlugin.handleLoadField(graphBuilderContext, object, field)) {
                            replacedNode = graphBuilderContext.pushedNode;
                            break;
                        }
                    }
                }
            } else if (node instanceof StoreFieldNode) {
                StoreFieldNode storeFieldNode = (StoreFieldNode) node;
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, storeFieldNode);
                ResolvedJavaField field = storeFieldNode.field();
                if (storeFieldNode.isStatic()) {
                    ValueNode value = storeFieldNode.value();
                    for (NodePlugin nodePlugin : nodePlugins) {
                        if (nodePlugin.handleStoreStaticField(graphBuilderContext, field, value)) {
                            replacedNode = graphBuilderContext.pushedNode;
                            break;
                        }
                    }
                } else {
                    ValueNode object = storeFieldNode.object();
                    ValueNode value = storeFieldNode.value();
                    for (NodePlugin nodePlugin : nodePlugins) {
                        if (nodePlugin.handleStoreField(graphBuilderContext, object, field, value)) {
                            replacedNode = graphBuilderContext.pushedNode;
                            break;
                        }
                    }
                }
            } else if (node instanceof LoadIndexedNode) {
                LoadIndexedNode loadIndexedNode = (LoadIndexedNode) node;
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, loadIndexedNode);
                ValueNode array = loadIndexedNode.array();
                ValueNode index = loadIndexedNode.index();
                for (NodePlugin nodePlugin : nodePlugins) {
                    if (nodePlugin.handleLoadIndexed(graphBuilderContext, array, index, loadIndexedNode.getBoundsCheck(), loadIndexedNode.elementKind())) {
                        replacedNode = graphBuilderContext.pushedNode;
                        break;
                    }
                }
            } else if (node instanceof StoreIndexedNode) {
                StoreIndexedNode storeIndexedNode = (StoreIndexedNode) node;
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, storeIndexedNode);
                ValueNode array = storeIndexedNode.array();
                ValueNode index = storeIndexedNode.index();
                ValueNode value = storeIndexedNode.value();
                for (NodePlugin nodePlugin : nodePlugins) {
                    if (nodePlugin.handleStoreIndexed(graphBuilderContext, array, index, storeIndexedNode.getBoundsCheck(), storeIndexedNode.getStoreCheck(), storeIndexedNode.elementKind(), value)) {
                        replacedNode = graphBuilderContext.pushedNode;
                        break;
                    }
                }
            } else if (node instanceof NewInstanceNode) {
                NewInstanceNode newInstanceNode = (NewInstanceNode) node;
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, newInstanceNode);
                ResolvedJavaType type = newInstanceNode.instanceClass();
                for (NodePlugin nodePlugin : nodePlugins) {
                    if (nodePlugin.handleNewInstance(graphBuilderContext, type)) {
                        replacedNode = graphBuilderContext.pushedNode;
                        break;
                    }
                }
            } else if (node instanceof NewArrayNode) {
                NewArrayNode newArrayNode = (NewArrayNode) node;
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, newArrayNode);
                ResolvedJavaType elementType = newArrayNode.elementType();
                ValueNode length = newArrayNode.length();
                for (NodePlugin nodePlugin : nodePlugins) {
                    if (nodePlugin.handleNewArray(graphBuilderContext, elementType, length)) {
                        replacedNode = graphBuilderContext.pushedNode;
                        break;
                    }
                }
            } else if (node instanceof NewMultiArrayNode) {
                NewMultiArrayNode newArrayNode = (NewMultiArrayNode) node;
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, newArrayNode);
                ResolvedJavaType elementType = newArrayNode.type();
                ValueNode[] dimensions = newArrayNode.dimensions().toArray(new ValueNode[0]);
                for (NodePlugin nodePlugin : nodePlugins) {
                    if (nodePlugin.handleNewMultiArray(graphBuilderContext, elementType, dimensions)) {
                        replacedNode = graphBuilderContext.pushedNode;
                        break;
                    }
                }
            }
        }
        if (node instanceof PluginReplacementInterface) {
            PluginReplacementInterface pluginReplacementNode = (PluginReplacementInterface) node;
            PEPluginGraphBuilderContext graphBuilderContext = new PEPluginGraphBuilderContext(methodScope,
                            pluginReplacementNode.asFixedNode());
            boolean success = pluginReplacementNode.replace(graphBuilderContext, providers.getReplacements());
            if (success) {
                replacedNode = graphBuilderContext.pushedNode;
            } else if (pluginReplacementMustSucceed()) {
                throw new GraalError("Plugin failed:" + node);
            }
        }

        return super.canonicalizeFixedNode(methodScope, replacedNode);
    }

    protected boolean pluginReplacementMustSucceed() {
        return false;
    }

    @Override
    protected Node handleFloatingNodeBeforeAdd(MethodScope s, LoopScope loopScope, Node n) {
        PEMethodScope methodScope = (PEMethodScope) s;

        Node node = n;
        if (node instanceof ParameterNode) {
            ParameterNode param = (ParameterNode) node;
            if (methodScope.isInlinedMethod()) {
                throw GraalError.shouldNotReachHere("Parameter nodes are already registered when the inlined scope is created");

            } else if (parameterPlugin != null) {
                assert !methodScope.isInlinedMethod();
                GraphBuilderContext graphBuilderContext = new PENonAppendGraphBuilderContext(methodScope, null);
                Node result = parameterPlugin.interceptParameter(graphBuilderContext, param.index(),
                                StampPair.create(param.stamp(NodeView.DEFAULT), param.uncheckedStamp()));
                if (result != null) {
                    return result;
                }
            }
            node = param.copyWithInputs();
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
            FrameState outerState = stateAtReturn.duplicateModified(graph, methodScope.invokeData.invoke.bci(), stateAtReturn.rethrowException(), true, invokeReturnKind, null, null, null);

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
            methodScope.exceptionPlaceholderNode = graph.add(new ExceptionPlaceholderNode());
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
                return InliningUtil.processFrameState(frameState, methodScope.invokeData.invoke, null, methodScope.method, methodScope.exceptionState, methodScope.outerState, true,
                                methodScope.method, invokeArgsList);

            } else if (node instanceof MonitorIdNode) {
                ensureOuterStateDecoded(methodScope);
                InliningUtil.processMonitorId(methodScope.outerState, (MonitorIdNode) node);
                return node;
            }
        }

        return node;
    }
}
