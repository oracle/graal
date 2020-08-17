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

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.StateSplitProxyNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.GraphKit;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.meta.SubstrateLoweringProvider;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class SubstrateGraphKit extends GraphKit {

    private final FrameStateBuilder frameState;
    private int nextBCI;

    public SubstrateGraphKit(DebugContext debug, ResolvedJavaMethod stubMethod, Providers providers, WordTypes wordTypes, GraphBuilderConfiguration.Plugins graphBuilderPlugins,
                    CompilationIdentifier compilationId) {
        super(debug, stubMethod, providers, wordTypes, graphBuilderPlugins, compilationId, null);
        assert wordTypes != null : "Support for Word types is mandatory";
        frameState = new FrameStateBuilder(this, stubMethod, graph);
        frameState.disableKindVerification();
        frameState.initializeForMethodStart(null, true, graphBuilderPlugins);
        graph.start().setStateAfter(frameState.create(bci(), graph.start()));
    }

    public SubstrateLoweringProvider getLoweringProvider() {
        return (SubstrateLoweringProvider) providers.getLowerer();
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

    public ValueNode createLoadIndexed(ValueNode array, int index, JavaKind kind) {
        ValueNode loadIndexed = LoadIndexedNode.create(null, array, ConstantNode.forInt(index, getGraph()), null, kind, getMetaAccess(), getConstantReflection());
        if (loadIndexed instanceof FixedNode) {
            return append((FixedNode) loadIndexed);
        }
        return unique((FloatingNode) loadIndexed);
    }

    public ValueNode createUnboxing(ValueNode boxed, JavaKind targetKind, MetaAccessProvider metaAccess) {
        return append(new UnboxNode(boxed, targetKind, metaAccess));
    }

    public ValueNode createInvokeWithExceptionAndUnwind(Class<?> declaringClass, String name, InvokeKind invokeKind, ValueNode... args) {
        return createInvokeWithExceptionAndUnwind(findMethod(declaringClass, name, invokeKind == InvokeKind.Static), invokeKind, frameState, bci(), args);
    }

    public ValueNode createJavaCallWithException(InvokeKind kind, ResolvedJavaMethod targetMethod, ValueNode... arguments) {
        return startInvokeWithException(targetMethod, kind, frameState, bci(), arguments);
    }

    public ValueNode createJavaCallWithExceptionAndUnwind(InvokeKind kind, ResolvedJavaMethod targetMethod, ValueNode... arguments) {
        return createInvokeWithExceptionAndUnwind(targetMethod, kind, frameState, bci(), arguments);
    }

    public ConstantNode createConstant(Constant value, JavaKind kind) {
        return ConstantNode.forConstant(StampFactory.forKind(kind), value, getMetaAccess(), getGraph());
    }

    public ValueNode createCFunctionCall(ValueNode targetAddress, List<ValueNode> arguments, Signature signature, int newThreadStatus, boolean emitDeoptTarget) {
        boolean emitTransition = StatusSupport.isValidStatus(newThreadStatus);
        if (emitTransition) {
            append(new CFunctionPrologueNode(newThreadStatus));

        }

        InvokeNode invoke = createIndirectCall(targetAddress, arguments, signature, SubstrateCallingConventionType.NativeCall);

        assert !emitDeoptTarget || !emitTransition : "cannot have transition for deoptimization targets";
        if (emitTransition) {
            CFunctionEpilogueNode epilogue = new CFunctionEpilogueNode(newThreadStatus);
            append(epilogue);
            epilogue.setStateAfter(invoke.stateAfter().duplicateWithVirtualState());
        } else if (emitDeoptTarget) {
            DeoptEntryNode deoptEntry = append(new DeoptEntryNode());
            deoptEntry.setStateAfter(invoke.stateAfter());
        }

        /*
         * Sign extend or zero the upper bits of a return value smaller than an int to preserve the
         * invariant that all such values are represented by an int in the VM. We cannot rely on the
         * native C compiler doing this for us.
         */
        return getLoweringProvider().implicitLoadConvert(getGraph(), asKind(signature.getReturnType(null)), invoke);
    }

    public InvokeNode createIndirectCall(ValueNode targetAddress, List<ValueNode> arguments, Signature signature, CallingConvention.Type callType) {
        assert arguments.size() == signature.getParameterCount(false);
        frameState.clearStack();

        Stamp stamp = returnStamp(signature);
        int bci = bci();

        CallTargetNode callTarget = getGraph().add(
                        new IndirectCallTargetNode(targetAddress, arguments.toArray(new ValueNode[arguments.size()]), StampPair.createSingle(stamp), signature.toParameterTypes(null), null,
                                        callType, InvokeKind.Static));
        InvokeNode invoke = append(new InvokeNode(callTarget, bci));

        // Insert framestate.
        frameState.pushReturn(signature.getReturnKind(), invoke);
        FrameState stateAfter = frameState.create(bci, invoke);
        invoke.setStateAfter(stateAfter);
        return invoke;
    }

    private Stamp returnStamp(Signature signature) {
        JavaType returnType = signature.getReturnType(null);
        JavaKind returnKind = signature.getReturnKind();

        if (returnKind == JavaKind.Object && returnType instanceof ResolvedJavaType) {
            return StampFactory.object(TypeReference.createTrustedWithoutAssumptions((ResolvedJavaType) returnType));
        } else {
            return getLoweringProvider().loadStamp(StampFactory.forKind(returnKind), signature.getReturnKind());
        }
    }

    public ConstantNode createLong(long value) {
        return ConstantNode.forLong(value, getGraph());
    }

    public ConstantNode createInt(int value) {
        return ConstantNode.forInt(value, getGraph());
    }

    public ConstantNode createObject(Object value) {
        return ConstantNode.forConstant(SubstrateObjectConstant.forObject(value), getMetaAccess(), graph);
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
            ValueNode exceptionValue = InliningUtil.mergeValueProducers(unwindMergeNode, unwinds, null, UnwindNode::exception);
            UnwindNode unwindReplacement = add(new UnwindNode(exceptionValue));
            unwindMergeNode.setNext(unwindReplacement);

            FrameStateBuilder exceptionState = getFrameState().copy();
            exceptionState.clearStack();
            exceptionState.push(JavaKind.Object, exceptionValue);
            exceptionState.setRethrowException(true);
            unwindMergeNode.setStateAfter(exceptionState.create(BytecodeFrame.AFTER_EXCEPTION_BCI, unwindMergeNode));
        }
    }

    public void appendStateSplitProxy(FrameState state) {
        StateSplitProxyNode proxy = new StateSplitProxyNode(null);
        append(proxy);
        proxy.setStateAfter(state);
    }

    public void appendStateSplitProxy(FrameStateBuilder stateBuilder) {
        StateSplitProxyNode proxy = new StateSplitProxyNode(null);
        append(proxy);
        proxy.setStateAfter(stateBuilder.create(bci(), proxy));
    }
}
