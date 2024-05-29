/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.replacements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.meta.SubstrateLoweringProvider;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.nodes.CFunctionCaptureNode;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IndirectCallTargetNode;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.FixedValueAnchorNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.StateSplitProxyNode;
import jdk.graal.compiler.nodes.extended.UnboxNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.GraphKit;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class SubstrateGraphKit extends GraphKit {

    private final FrameStateBuilder frameState;
    private int nextBCI;
    private final List<ValueNode> initialArguments;

    @SuppressWarnings("this-escape")
    public SubstrateGraphKit(DebugContext debug, ResolvedJavaMethod stubMethod, Providers providers,
                    GraphBuilderConfiguration.Plugins graphBuilderPlugins, CompilationIdentifier compilationId, boolean recordInlinedMethods) {
        super(debug, stubMethod, providers, graphBuilderPlugins, compilationId, null, true, recordInlinedMethods);
        assert getWordTypes() != null : "Support for Word types is mandatory";
        frameState = new FrameStateBuilder(this, stubMethod, graph);
        frameState.disableKindVerification();
        frameState.disableStateVerification();
        List<ValueNode> collectedArguments = new ArrayList<>();
        frameState.initializeForMethodStart(null, true, graphBuilderPlugins, collectedArguments);
        initialArguments = Collections.unmodifiableList(collectedArguments);
        graph.start().setStateAfter(frameState.create(bci(), graph.start()));
        // Record method dependency in the graph
        graph.recordMethod(graph.method());
    }

    @Override
    public MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, StampPair returnStamp, int bci) {
        return new SubstrateMethodCallTargetNode(invokeKind, targetMethod, args, returnStamp);
    }

    public SubstrateLoweringProvider getLoweringProvider() {
        return (SubstrateLoweringProvider) getLowerer();
    }

    public FrameStateBuilder getFrameState() {
        return frameState;
    }

    public ValueNode loadLocal(int index, JavaKind slotKind) {
        return frameState.loadLocal(index, slotKind);
    }

    public void storeLocal(int index, JavaKind slotKind, ValueNode value) {
        frameState.storeLocal(index, slotKind, value);
    }

    public List<ValueNode> getInitialArguments() {
        return initialArguments;
    }

    public LoadFieldNode createLoadField(ValueNode object, ResolvedJavaField field) {
        return append(LoadFieldNode.create(null, object, field));
    }

    public ValueNode createLoadIndexed(ValueNode array, int index, JavaKind kind, GuardingNode boundsCheck) {
        return createLoadIndexed(array, ConstantNode.forInt(index, getGraph()), kind, boundsCheck);
    }

    public ValueNode createLoadIndexed(ValueNode array, ValueNode index, JavaKind kind, GuardingNode boundsCheck) {
        ValueNode loadIndexed = LoadIndexedNode.create(null, array, index, boundsCheck, kind, getMetaAccess(), getConstantReflection());
        if (loadIndexed instanceof FixedNode) {
            return append((FixedNode) loadIndexed);
        }
        return unique((FloatingNode) loadIndexed);
    }

    public ValueNode createStoreIndexed(ValueNode array, int index, JavaKind kind, ValueNode value) {
        return append(new StoreIndexedNode(array, ConstantNode.forInt(index, getGraph()), null, null, kind, value));
    }

    public ValueNode createUnboxing(ValueNode boxed, JavaKind targetKind) {
        return append(new UnboxNode(boxed, targetKind, getMetaAccess()));
    }

    public ValueNode createInvokeWithExceptionAndUnwind(Class<?> declaringClass, String name, InvokeKind invokeKind, ValueNode... args) {
        return createInvokeWithExceptionAndUnwind(findMethod(declaringClass, name, invokeKind == InvokeKind.Static), invokeKind, frameState, bci(), args);
    }

    public InvokeWithExceptionNode createJavaCallWithException(InvokeKind kind, ResolvedJavaMethod targetMethod, ValueNode... args) {
        return startInvokeWithException(targetMethod, kind, frameState, bci(), args);
    }

    public InvokeWithExceptionNode createJavaCallWithExceptionAndUnwind(InvokeKind kind, ResolvedJavaMethod targetMethod, ValueNode... args) {
        return createInvokeWithExceptionAndUnwind(targetMethod, kind, frameState, bci(), args);
    }

    public ConstantNode createConstant(Constant value, JavaKind kind) {
        return ConstantNode.forConstant(StampFactory.forKind(kind), value, getMetaAccess(), getGraph());
    }

    public ValueNode createCFunctionCall(ValueNode targetAddress, List<ValueNode> args, Signature signature, int newThreadStatus, boolean emitDeoptTarget) {
        return createCFunctionCallWithCapture(targetAddress, args, signature, newThreadStatus, emitDeoptTarget, SubstrateCallingConventionKind.Native.toType(true),
                        null, null, null);
    }

    public ValueNode createCFunctionCallWithCapture(ValueNode targetAddress, List<ValueNode> args, Signature signature, int newThreadStatus, boolean emitDeoptTarget,
                    CallingConvention.Type convention, ForeignCallDescriptor captureFunction, ValueNode statesToCapture, ValueNode captureBuffer) {
        assert ((captureFunction == null) && (statesToCapture == null) && (captureBuffer == null)) ||
                        ((captureFunction != null) && (statesToCapture != null) && (captureBuffer != null));

        var fixedStatesToCapture = statesToCapture;
        if (fixedStatesToCapture != null) {
            fixedStatesToCapture = append(new FixedValueAnchorNode(fixedStatesToCapture));
        }

        boolean emitTransition = StatusSupport.isValidStatus(newThreadStatus);
        if (emitTransition) {
            append(new CFunctionPrologueNode(newThreadStatus));
        }

        /*
         * For CFunction calls that return a value smaller than int, we must assume that the C
         * compiler generated code that returns a value that is in the int range. In GraalVM, we
         * have the invariant that all values smaller than int are represented by int. To preserve
         * this invariant, some special handling is needed as we cannot rely on the native C
         * compiler doing this for us.
         */
        JavaKind javaReturnKind = signature.getReturnKind();
        JavaKind cReturnKind = javaReturnKind.getStackKind();
        JavaType returnType = signature.getReturnType(null);
        Stamp returnStamp = returnStamp(returnType, cReturnKind);
        InvokeNode invoke = createIndirectCall(targetAddress, args, signature.toParameterTypes(null), returnStamp, cReturnKind, convention);

        if (fixedStatesToCapture != null) {
            append(new CFunctionCaptureNode(captureFunction, fixedStatesToCapture, captureBuffer));
        }

        assert !emitDeoptTarget || !emitTransition : "cannot have transition for deoptimization targets";
        if (emitTransition) {
            CFunctionEpilogueNode epilogue = new CFunctionEpilogueNode(newThreadStatus);
            append(epilogue);
            epilogue.setStateAfter(invoke.stateAfter().duplicateWithVirtualState());
        } else if (emitDeoptTarget) {
            /*
             * Since this deoptimization is occurring in a custom graph, assume there are no
             * exception handlers and directly unwind.
             */
            int bci = invoke.stateAfter().bci;
            appendWithUnwind(DeoptEntryNode.create(invoke.bci()), bci);
        }

        ValueNode result = invoke;
        if (javaReturnKind != cReturnKind) {
            // Narrow the int value that we received from the C-side to 1 or 2 bytes.
            assert javaReturnKind.getByteCount() < cReturnKind.getByteCount();
            result = append(new NarrowNode(result, javaReturnKind.getByteCount() << 3));
        }

        // Sign or zero extend to get a clean int value. If a boolean result is expected, the int
        // value is coerced to true or false.
        return getLoweringProvider().implicitLoadConvertWithBooleanCoercionIfNecessary(getGraph(), asKind(returnType), result);
    }

    public InvokeNode createIndirectCall(ValueNode targetAddress, List<ValueNode> args, Signature signature, SubstrateCallingConventionKind callKind) {
        assert args.size() == signature.getParameterCount(false);
        assert callKind != SubstrateCallingConventionKind.Native : "return kind and stamp would be incorrect";
        JavaKind returnKind = signature.getReturnKind().getStackKind();
        Stamp returnStamp = returnStamp(signature.getReturnType(null), returnKind);
        return createIndirectCall(targetAddress, args, signature.toParameterTypes(null), returnStamp, returnKind, callKind);
    }

    private InvokeNode createIndirectCall(ValueNode targetAddress, List<ValueNode> args, JavaType[] parameterTypes, Stamp returnStamp, JavaKind returnKind,
                    SubstrateCallingConventionKind callKind) {
        return createIndirectCall(targetAddress, args, parameterTypes, returnStamp, returnKind, callKind.toType(true));
    }

    private InvokeNode createIndirectCall(ValueNode targetAddress, List<ValueNode> args, JavaType[] parameterTypes, Stamp returnStamp, JavaKind returnKind,
                    CallingConvention.Type convention) {
        frameState.clearStack();

        int bci = bci();
        CallTargetNode callTarget = getGraph().add(
                        new IndirectCallTargetNode(targetAddress, args.toArray(new ValueNode[args.size()]), StampPair.createSingle(returnStamp), parameterTypes, null,
                                        convention, InvokeKind.Static));
        InvokeNode invoke = append(new InvokeNode(callTarget, bci));

        // Insert framestate.
        frameState.pushReturn(returnKind, invoke);
        FrameState stateAfter = frameState.create(bci, invoke);
        invoke.setStateAfter(stateAfter);
        return invoke;
    }

    private Stamp returnStamp(JavaType returnType, JavaKind returnKind) {
        if (returnKind == JavaKind.Object && returnType instanceof ResolvedJavaType) {
            return StampFactory.object(TypeReference.createTrustedWithoutAssumptions((ResolvedJavaType) returnType));
        } else {
            return getLoweringProvider().loadStamp(StampFactory.forKind(returnKind), returnKind);
        }
    }

    public ConstantNode createLong(long value) {
        return ConstantNode.forLong(value, getGraph());
    }

    public ConstantNode createInt(int value) {
        return ConstantNode.forInt(value, getGraph());
    }

    public ConstantNode createObject(Object value) {
        return ConstantNode.forConstant(getSnippetReflection().forObject(value), getMetaAccess(), graph);
    }

    public ValueNode createBoxing(ValueNode value, JavaKind kind, ResolvedJavaType targetType) {
        return append(BoxNode.create(value, targetType, kind));
    }

    public ValueNode createReturn(ValueNode retValue, JavaKind returnKind) {
        if (returnKind == JavaKind.Void) {
            return append(new ReturnNode(null));
        }

        return append(new ReturnNode(retValue));
    }

    public PiNode createPiNode(ValueNode value, Stamp stamp) {
        return append(new PiNode(value, stamp, AbstractBeginNode.prevBegin(lastFixedNode)));
    }

    public int bci() {
        return nextBCI++;
    }

    public StructuredGraph finalizeGraph() {
        if (lastFixedNode != null) {
            throw VMError.shouldNotReachHere("Manually constructed graph does not terminate control flow properly. lastFixedNode: " + lastFixedNode);
        }

        mergeUnwinds();
        assert graph.verify();
        assert getWordTypes().ensureGraphContainsNoWordTypeReferences(graph);
        return graph;
    }

    /** A graph with multiple unwinds is invalid. Merge the various unwind paths. */
    private void mergeUnwinds() {
        List<UnwindNode> unwinds = new ArrayList<>();
        for (Node node : getGraph().getNodes()) {
            if (node instanceof UnwindNode) {
                unwinds.add((UnwindNode) node);
            }
        }

        if (unwinds.size() > 1) {
            MergeNode unwindMergeNode = add(new MergeNode());
            ValueNode exceptionValue = InliningUtil.mergeUnwindExceptions(unwindMergeNode, unwinds);
            UnwindNode unwindReplacement = add(new UnwindNode(exceptionValue));
            unwindMergeNode.setNext(unwindReplacement);

            FrameStateBuilder exceptionState = getFrameState().copy();
            exceptionState.clearStack();
            exceptionState.push(JavaKind.Object, exceptionValue);
            exceptionState.setRethrowException(true);
            unwindMergeNode.setStateAfter(exceptionState.create(BytecodeFrame.AFTER_EXCEPTION_BCI, unwindMergeNode));
        }
    }

    /**
     * Appends the provided node to the control flow graph. The exception edge is connected to an
     * {@link UnwindNode}, i.e., the exception is not handled in this method.
     */
    protected <T extends WithExceptionNode> T appendWithUnwind(T withExceptionNode, int bci) {
        WithExceptionNode appended = append(withExceptionNode);
        assert appended == withExceptionNode;

        if (withExceptionNode instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) withExceptionNode;
            stateSplit.setStateAfter(frameState.create(bci, stateSplit));
        }

        AbstractBeginNode noExceptionEdge = add(new BeginNode());
        withExceptionNode.setNext(noExceptionEdge);
        ExceptionObjectNode exceptionEdge = createExceptionObjectNode(frameState, bci);
        withExceptionNode.setExceptionEdge(exceptionEdge);

        assert lastFixedNode == null;
        lastFixedNode = exceptionEdge;
        append(new UnwindNode(exceptionEdge));

        assert lastFixedNode == null;
        lastFixedNode = noExceptionEdge;

        return withExceptionNode;
    }

    public void appendStateSplitProxy() {
        StateSplitProxyNode proxy = new StateSplitProxyNode();
        append(proxy);
        proxy.setStateAfter(frameState.create(bci(), proxy));
    }
}
