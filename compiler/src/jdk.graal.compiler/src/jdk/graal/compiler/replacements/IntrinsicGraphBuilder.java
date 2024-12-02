/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements;

import static jdk.vm.ci.code.BytecodeFrame.AFTER_BCI;

import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.Invokable;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.CoreProvidersDelegate;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Implementation of {@link GraphBuilderContext} used to produce a graph for a method based on an
 * {@link InvocationPlugin} for the method.
 */
public class IntrinsicGraphBuilder extends CoreProvidersDelegate implements GraphBuilderContext, Receiver {

    protected final StructuredGraph graph;
    protected final Bytecode code;
    protected final ResolvedJavaMethod method;
    protected final int invokeBci;
    protected final JavaKind returnKind;
    protected FixedWithNextNode lastInstr;
    protected ValueNode[] arguments;
    protected ValueNode returnValue;

    private FrameState createStateAfterStartOfReplacementGraph(ResolvedJavaMethod original, GraphBuilderConfiguration graphBuilderConfig) {
        FrameStateBuilder startFrameState = new FrameStateBuilder(this, code, graph, graphBuilderConfig.retainLocalVariables());
        startFrameState.initializeForMethodStart(graph.getAssumptions(), false, graphBuilderConfig.getPlugins(), null);
        return startFrameState.createInitialIntrinsicFrameState(original);
    }

    public IntrinsicGraphBuilder(OptionValues options,
                    DebugContext debug,
                    CoreProviders providers,
                    Bytecode code,
                    int invokeBci) {
        this(options, debug, providers, code, invokeBci, AllowAssumptions.YES, null);
    }

    public IntrinsicGraphBuilder(OptionValues options,
                    DebugContext debug,
                    CoreProviders providers,
                    Bytecode code,
                    int invokeBci,
                    AllowAssumptions allowAssumptions) {
        this(options, debug, providers, code, invokeBci, allowAssumptions, null);
    }

    @SuppressWarnings("this-escape")
    protected IntrinsicGraphBuilder(OptionValues options,
                    DebugContext debug,
                    CoreProviders providers,
                    Bytecode code,
                    int invokeBci,
                    AllowAssumptions allowAssumptions,
                    GraphBuilderConfiguration graphBuilderConfig) {
        super(providers);
        this.code = code;
        this.method = code.getMethod();
        this.graph = new StructuredGraph.Builder(options, debug, allowAssumptions).method(method).setIsSubstitution(true).trackNodeSourcePosition(true).build();
        this.invokeBci = invokeBci;
        this.lastInstr = graph.start();
        if (graphBuilderConfig != null && !method.isNative()) {
            graph.start().setStateAfter(createStateAfterStartOfReplacementGraph(method, graphBuilderConfig));
        }
        // Record method dependency in the graph
        graph.recordMethod(method);

        Signature sig = method.getSignature();
        int max = sig.getParameterCount(false);
        this.arguments = new ValueNode[max + (method.isStatic() ? 0 : 1)];
        this.returnKind = method.getSignature().getReturnKind();

        int javaIndex = 0;
        int index = 0;
        if (!method.isStatic()) {
            // add the receiver
            Stamp receiverStamp = StampFactory.objectNonNull(TypeReference.createWithoutAssumptions(method.getDeclaringClass()));
            ValueNode receiver = graph.addWithoutUnique(new ParameterNode(javaIndex, StampPair.createSingle(receiverStamp)));
            arguments[index] = receiver;
            javaIndex = 1;
            index = 1;
        }
        ResolvedJavaType accessingClass = method.getDeclaringClass();
        for (int i = 0; i < max; i++) {
            JavaType type = sig.getParameterType(i, accessingClass).resolve(accessingClass);
            JavaKind kind = type.getJavaKind();
            Stamp stamp;
            if (kind == JavaKind.Object && type instanceof ResolvedJavaType) {
                stamp = StampFactory.object(TypeReference.createWithoutAssumptions((ResolvedJavaType) type));
            } else if (kind.getStackKind() != kind) {
                assert kind.getStackKind() == JavaKind.Int : Assertions.errorMessage(kind, type);
                stamp = StampFactory.forKind(JavaKind.Int);
            } else {
                stamp = StampFactory.forKind(kind);
            }
            ValueNode param = graph.addWithoutUnique(new ParameterNode(index, StampPair.createSingle(stamp)));
            arguments[index] = param;
            javaIndex += kind.getSlotCount();
            index++;
        }
    }

    private <T extends Node> void updateLastInstruction(T v) {
        if (v instanceof FixedNode) {
            FixedNode fixedNode = (FixedNode) v;
            if (lastInstr != null) {
                lastInstr.setNext(fixedNode);
            }
            if (fixedNode instanceof FixedWithNextNode) {
                FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) fixedNode;
                assert fixedWithNextNode.next() == null : "cannot append instruction to instruction which isn't end";
                lastInstr = fixedWithNextNode;

            } else if (fixedNode instanceof WithExceptionNode) {
                WithExceptionNode withExceptionNode = (WithExceptionNode) fixedNode;
                AbstractBeginNode normalSuccessor = graph.add(new BeginNode());
                ExceptionObjectNode exceptionSuccessor = graph.add(new ExceptionObjectNode(getMetaAccess()));
                setExceptionState(exceptionSuccessor);
                exceptionSuccessor.setNext(graph.add(new UnwindNode(exceptionSuccessor)));

                withExceptionNode.setNext(normalSuccessor);
                withExceptionNode.setExceptionEdge(exceptionSuccessor);
                lastInstr = normalSuccessor;

            } else {
                lastInstr = null;
            }
        }
    }

    @Override
    public boolean hasParseTerminated() {
        return lastInstr == null;
    }

    @Override
    public AbstractBeginNode genExplicitExceptionEdge(BytecodeExceptionNode.BytecodeExceptionKind exceptionKind, ValueNode... exceptionArguments) {
        BytecodeExceptionNode exceptionNode = graph.add(new BytecodeExceptionNode(getMetaAccess(), exceptionKind, exceptionArguments));
        setExceptionState(exceptionNode);
        exceptionNode.setNext(graph.add(new UnwindNode(exceptionNode)));
        return BeginNode.begin(exceptionNode);
    }

    /**
     * Currently unimplemented here, but implemented in subclasses that need it.
     *
     * @param exceptionObject The node that needs an exception state.
     */
    protected void setExceptionState(StateSplit exceptionObject) {
        throw GraalError.shouldNotReachHere("unsupported by this IntrinsicGraphBuilder"); // ExcludeFromJacocoGeneratedReport
    }

    /**
     * If the graph contains multiple unwind nodes, then this method merges them into a single
     * unwind node containing a merged ExceptionNode. This is needed because an IntrinsicGraph can
     * only contain at most a single UnwindNode.
     *
     * Currently unimplemented here, but implemented in subclasses that need it.
     */
    protected void mergeUnwinds() {
        if (getGraph().getNodes(UnwindNode.TYPE).snapshot().size() > 1) {
            throw GraalError.shouldNotReachHere("mergeUnwinds unsupported by this IntrinsicGraphBuilder"); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public <T extends Node> T append(T v) {
        if (v.graph() != null) {
            return v;
        }
        T added = graph.addOrUniqueWithInputs(v);
        if (added == v) {
            updateLastInstruction(v);
        }
        return added;
    }

    @Override
    public void push(JavaKind kind, ValueNode value) {
        GraalError.guarantee(kind == returnKind, "expected to return %s but returning %s", returnKind, kind);
        GraalError.guarantee(kind != JavaKind.Void, "can't push value for void return");
        GraalError.guarantee(returnValue == null, "can only push one value");
        returnValue = value;
    }

    @Override
    public ValueNode pop(JavaKind slotKind) {
        GraalError.guarantee(returnValue != null, "no value pushed");
        ValueNode result = returnValue;
        returnValue = null;
        return result;
    }

    @Override
    public Invokable handleReplacedInvoke(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, boolean forceInlineEverything) {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public void handleReplacedInvoke(CallTargetNode callTarget, JavaKind resultType) {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public StructuredGraph getGraph() {
        return graph;
    }

    @Override
    public void setStateAfter(StateSplit sideEffect) {
        assert sideEffect.hasSideEffect();
        FrameState stateAfter = getGraph().add(new FrameState(AFTER_BCI));
        sideEffect.setStateAfter(stateAfter);
    }

    @Override
    public GraphBuilderContext getParent() {
        return null;
    }

    @Override
    public Bytecode getCode() {
        return code;
    }

    @Override
    public ResolvedJavaMethod getMethod() {
        /*
         * Invocation plugins expect to get the caller method that triggers the intrinsification.
         * Since we are compiling the intrinsic on its own, we do not have any such caller method.
         *
         * In particular, returning `method` would be misleading because it is the method that is
         * intrinsified, not the caller. The invocation plugin gets that method passed in as the
         * `targetMethod` already.
         */
        return null;
    }

    @Override
    public int bci() {
        return invokeBci;
    }

    @Override
    public InvokeKind getInvokeKind() {
        return method.isStatic() ? InvokeKind.Static : InvokeKind.Virtual;
    }

    @Override
    public JavaType getInvokeReturnType() {
        return method.getSignature().getReturnType(method.getDeclaringClass());
    }

    @Override
    public int getDepth() {
        return 0;
    }

    @Override
    public boolean parsingIntrinsic() {
        return false;
    }

    @Override
    public IntrinsicContext getIntrinsic() {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public BailoutException bailout(String string) {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    /**
     * An {@link IntrinsicGraphBuilder} is used to produce a graph for inlining. It assumes the
     * inliner does any required null checking of the receiver as part of inlining. As such,
     * {@code performNullCheck} is ignored here.
     */
    @Override
    public ValueNode get(boolean performNullCheck) {
        return arguments[0];
    }

    @SuppressWarnings("try")
    public final StructuredGraph buildGraph(InvocationPlugin plugin) {
        // The caller is expected to have filtered out decorator plugins since they cannot be
        // processed without special handling.
        assert !plugin.isDecorator() : plugin;
        NodeSourcePosition position = graph.trackNodeSourcePosition() ? NodeSourcePosition.placeholder(method) : null;
        try (DebugContext.Scope scope = graph.getDebug().scope("BuildGraph", graph)) {
            try (DebugCloseable context = graph.withNodeSourcePosition(position)) {
                Receiver receiver = method.isStatic() ? null : this;
                if (plugin.execute(this, method, receiver, arguments)) {
                    assert (returnValue != null) == (method.getSignature().getReturnKind() != JavaKind.Void) : method;
                    assert lastInstr != null : "ReturnNode must be linked into control flow";
                    append(new ReturnNode(returnValue));
                    mergeUnwinds();
                    return graph;
                }
                return null;
            }
        } catch (Throwable t) {
            throw graph.getDebug().handle(t);
        }
    }

    @Override
    public FrameState getInvocationPluginReturnState(JavaKind kind, ValueNode retVal) {
        return getGraph().add(new FrameState(AFTER_BCI));
    }

    @Override
    public FrameState getInvocationPluginBeforeState() {
        return getGraph().start().stateAfter();
    }

    @Override
    public boolean canMergeIntrinsicReturns() {
        return true;
    }

    @Override
    public boolean isParsingInvocationPlugin() {
        return true;
    }

    @Override
    public boolean canInvokeFallback() {
        return true;
    }

    @Override
    public Invoke invokeFallback(FixedWithNextNode predecessor, EndNode end) {
        assert isParsingInvocationPlugin();
        DeoptimizeNode deopt = getGraph().add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint));
        predecessor.setNext(deopt);
        return null;
    }

    @Override
    public String toString() {
        return String.format("%s:intrinsic", method.format("%H.%n(%p)"));
    }

}
