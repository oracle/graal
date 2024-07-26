/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.FixedValueAnchorNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.GraphKit;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.meta.SubstrateLoweringProvider;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.nodes.CFunctionCaptureNode;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;

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

    // For GR-45916 this should be unconditionally true when parseOnce is enabled.
    private static boolean trackNodeSourcePosition(boolean forceTrackNodeSourcePosition) {
        return forceTrackNodeSourcePosition || (SubstrateOptions.parseOnce() && !SubstrateOptions.ParseOnceJIT.getValue());
    }

    @SuppressWarnings("this-escape")
    public SubstrateGraphKit(DebugContext debug, ResolvedJavaMethod stubMethod, Providers providers, WordTypes wordTypes,
                    GraphBuilderConfiguration.Plugins graphBuilderPlugins, CompilationIdentifier compilationId, boolean forceTrackNodeSourcePosition, boolean recordInlinedMethods) {
        super(debug, stubMethod, providers, wordTypes, graphBuilderPlugins, compilationId, null, trackNodeSourcePosition(forceTrackNodeSourcePosition), recordInlinedMethods);
        assert wordTypes != null : "Support for Word types is mandatory";
        frameState = new FrameStateBuilder(this, stubMethod, graph);
        frameState.disableKindVerification();
        frameState.disableStateVerification();
        frameState.initializeForMethodStart(null, true, graphBuilderPlugins);
        graph.start().setStateAfter(frameState.create(bci(), graph.start()));
    }

    public SubstrateGraphKit(DebugContext debug, ResolvedJavaMethod stubMethod, Providers providers, WordTypes wordTypes,
                    GraphBuilderConfiguration.Plugins graphBuilderPlugins, CompilationIdentifier compilationId, boolean forceTrackNodeSourcePosition) {
        this(debug, stubMethod, providers, wordTypes, graphBuilderPlugins, compilationId, forceTrackNodeSourcePosition, false);
    }

    @Override
    protected MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, StampPair returnStamp, int bci) {
        return new SubstrateMethodCallTargetNode(invokeKind, targetMethod, args, returnStamp, null, null, null);
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

    public List<ValueNode> loadArguments(JavaType[] paramTypes) {
        List<ValueNode> arguments = new ArrayList<>();
        int numOfParams = paramTypes.length;
        int javaIndex = 0;

        for (int i = 0; i < numOfParams; i++) {
            JavaType type = paramTypes[i];
            JavaKind kind = type.getJavaKind();

            assert frameState.loadLocal(javaIndex, kind) != null;
            arguments.add(frameState.loadLocal(javaIndex, kind));

            javaIndex += kind.getSlotCount();
        }

        return arguments;
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

    public InvokeWithExceptionNode createJavaCallWithException(InvokeKind kind, ResolvedJavaMethod targetMethod, ValueNode... arguments) {
        return startInvokeWithException(targetMethod, kind, frameState, bci(), arguments);
    }

    public InvokeWithExceptionNode createJavaCallWithExceptionAndUnwind(InvokeKind kind, ResolvedJavaMethod targetMethod, ValueNode... arguments) {
        return createInvokeWithExceptionAndUnwind(targetMethod, kind, frameState, bci(), arguments);
    }

    public ConstantNode createConstant(Constant value, JavaKind kind) {
        return ConstantNode.forConstant(StampFactory.forKind(kind), value, getMetaAccess(), getGraph());
    }

    public ValueNode createCFunctionCall(ValueNode targetAddress, List<ValueNode> arguments, Signature signature, int newThreadStatus, boolean emitDeoptTarget) {
        return createCFunctionCallWithCapture(targetAddress, arguments, signature, newThreadStatus, emitDeoptTarget, SubstrateCallingConventionKind.Native.toType(true),
                        null, null, null);
    }

    public ValueNode createCFunctionCallWithCapture(ValueNode targetAddress, List<ValueNode> arguments, Signature signature, int newThreadStatus, boolean emitDeoptTarget,
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
        InvokeNode invoke = createIndirectCall(targetAddress, arguments, signature.toParameterTypes(null), returnStamp, cReturnKind, convention);

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

    public InvokeNode createIndirectCall(ValueNode targetAddress, List<ValueNode> arguments, Signature signature, SubstrateCallingConventionKind callKind) {
        assert arguments.size() == signature.getParameterCount(false);
        assert callKind != SubstrateCallingConventionKind.Native : "return kind and stamp would be incorrect";
        JavaKind returnKind = signature.getReturnKind().getStackKind();
        Stamp returnStamp = returnStamp(signature.getReturnType(null), returnKind);
        return createIndirectCall(targetAddress, arguments, signature.toParameterTypes(null), returnStamp, returnKind, callKind);
    }

    private InvokeNode createIndirectCall(ValueNode targetAddress, List<ValueNode> arguments, JavaType[] parameterTypes, Stamp returnStamp, JavaKind returnKind,
                    SubstrateCallingConventionKind callKind) {
        return createIndirectCall(targetAddress, arguments, parameterTypes, returnStamp, returnKind, callKind.toType(true));
    }

    private InvokeNode createIndirectCall(ValueNode targetAddress, List<ValueNode> arguments, JavaType[] parameterTypes, Stamp returnStamp, JavaKind returnKind,
                    CallingConvention.Type convention) {
        frameState.clearStack();

        int bci = bci();
        CallTargetNode callTarget = getGraph().add(
                        new IndirectCallTargetNode(targetAddress, arguments.toArray(new ValueNode[arguments.size()]), StampPair.createSingle(returnStamp), parameterTypes, null,
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
        SnippetReflectionProvider snippetReflection = getProviders().getSnippetReflection();
        return ConstantNode.forConstant(snippetReflection.forObject(value), getMetaAccess(), graph);
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

    public static boolean isWord(Class<?> klass) {
        return WordBase.class.isAssignableFrom(klass);
    }

    public StructuredGraph finalizeGraph() {
        if (lastFixedNode != null) {
            throw VMError.shouldNotReachHere("Manually constructed graph does not terminate control flow properly. lastFixedNode: " + lastFixedNode);
        }

        mergeUnwinds();
        assert graph.verify();
        assert wordTypes.ensureGraphContainsNoWordTypeReferences(graph);
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
}
