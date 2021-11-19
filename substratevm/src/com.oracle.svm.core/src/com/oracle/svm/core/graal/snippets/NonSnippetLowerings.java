/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import org.graalvm.compiler.nodes.extended.FixedValueAnchorNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.OpaqueNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.LoweringTool.StandardLoweringStage;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.graal.nodes.ThrowBytecodeExceptionNode;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class NonSnippetLowerings {

    public static final SnippetRuntime.SubstrateForeignCallDescriptor REPORT_VERIFY_TYPES_ERROR = SnippetRuntime.findForeignCall(
                    NonSnippetLowerings.class, "reportVerifyTypesError", false, LocationIdentity.any());

    private final RuntimeConfiguration runtimeConfig;
    private final Predicate<ResolvedJavaMethod> mustNotAllocatePredicate;

    final boolean verifyTypes = SubstrateOptions.VerifyTypes.getValue();

    @SuppressWarnings("unused")
    protected NonSnippetLowerings(RuntimeConfiguration runtimeConfig, Predicate<ResolvedJavaMethod> mustNotAllocatePredicate, OptionValues options,
                    Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        this.runtimeConfig = runtimeConfig;
        this.mustNotAllocatePredicate = mustNotAllocatePredicate;

        lowerings.put(BytecodeExceptionNode.class, new BytecodeExceptionLowering());
        lowerings.put(ThrowBytecodeExceptionNode.class, new ThrowBytecodeExceptionLowering());
        lowerings.put(GetClassNode.class, new GetClassLowering());
        lowerings.put(InvokeNode.class, new InvokeLowering());
        lowerings.put(InvokeWithExceptionNode.class, new InvokeLowering());
    }

    private static final EnumMap<BytecodeExceptionKind, ForeignCallDescriptor> getCachedExceptionDescriptors;
    private static final EnumMap<BytecodeExceptionKind, ForeignCallDescriptor> createExceptionDescriptors;
    private static final EnumMap<BytecodeExceptionKind, ForeignCallDescriptor> throwCachedExceptionDescriptors;
    private static final EnumMap<BytecodeExceptionKind, ForeignCallDescriptor> throwNewExceptionDescriptors;

    static {
        getCachedExceptionDescriptors = new EnumMap<>(BytecodeExceptionKind.class);
        getCachedExceptionDescriptors.put(BytecodeExceptionKind.NULL_POINTER, ImplicitExceptions.GET_CACHED_NULL_POINTER_EXCEPTION);
        getCachedExceptionDescriptors.put(BytecodeExceptionKind.OUT_OF_BOUNDS, ImplicitExceptions.GET_CACHED_OUT_OF_BOUNDS_EXCEPTION);
        getCachedExceptionDescriptors.put(BytecodeExceptionKind.INTRINSIC_OUT_OF_BOUNDS, ImplicitExceptions.GET_CACHED_OUT_OF_BOUNDS_EXCEPTION);
        getCachedExceptionDescriptors.put(BytecodeExceptionKind.CLASS_CAST, ImplicitExceptions.GET_CACHED_CLASS_CAST_EXCEPTION);
        getCachedExceptionDescriptors.put(BytecodeExceptionKind.ARRAY_STORE, ImplicitExceptions.GET_CACHED_ARRAY_STORE_EXCEPTION);
        getCachedExceptionDescriptors.put(BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_NEGATIVE_LENGTH, ImplicitExceptions.GET_CACHED_ILLEGAL_ARGUMENT_EXCEPTION);
        getCachedExceptionDescriptors.put(BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_ARGUMENT_IS_NOT_AN_ARRAY, ImplicitExceptions.GET_CACHED_ILLEGAL_ARGUMENT_EXCEPTION);
        getCachedExceptionDescriptors.put(BytecodeExceptionKind.NEGATIVE_ARRAY_SIZE, ImplicitExceptions.GET_CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION);
        getCachedExceptionDescriptors.put(BytecodeExceptionKind.DIVISION_BY_ZERO, ImplicitExceptions.GET_CACHED_ARITHMETIC_EXCEPTION);
        getCachedExceptionDescriptors.put(BytecodeExceptionKind.ASSERTION_ERROR_NULLARY, ImplicitExceptions.GET_CACHED_ASSERTION_ERROR);
        getCachedExceptionDescriptors.put(BytecodeExceptionKind.ASSERTION_ERROR_OBJECT, ImplicitExceptions.GET_CACHED_ASSERTION_ERROR);

        createExceptionDescriptors = new EnumMap<>(BytecodeExceptionKind.class);
        createExceptionDescriptors.put(BytecodeExceptionKind.NULL_POINTER, ImplicitExceptions.CREATE_NULL_POINTER_EXCEPTION);
        createExceptionDescriptors.put(BytecodeExceptionKind.OUT_OF_BOUNDS, ImplicitExceptions.CREATE_OUT_OF_BOUNDS_EXCEPTION);
        createExceptionDescriptors.put(BytecodeExceptionKind.INTRINSIC_OUT_OF_BOUNDS, ImplicitExceptions.CREATE_INTRINSIC_OUT_OF_BOUNDS_EXCEPTION);
        createExceptionDescriptors.put(BytecodeExceptionKind.CLASS_CAST, ImplicitExceptions.CREATE_CLASS_CAST_EXCEPTION);
        createExceptionDescriptors.put(BytecodeExceptionKind.ARRAY_STORE, ImplicitExceptions.CREATE_ARRAY_STORE_EXCEPTION);
        createExceptionDescriptors.put(BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_NEGATIVE_LENGTH, ImplicitExceptions.CREATE_ILLEGAL_ARGUMENT_EXCEPTION);
        createExceptionDescriptors.put(BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_ARGUMENT_IS_NOT_AN_ARRAY, ImplicitExceptions.CREATE_ILLEGAL_ARGUMENT_EXCEPTION);
        createExceptionDescriptors.put(BytecodeExceptionKind.NEGATIVE_ARRAY_SIZE, ImplicitExceptions.CREATE_NEGATIVE_ARRAY_SIZE_EXCEPTION);
        createExceptionDescriptors.put(BytecodeExceptionKind.DIVISION_BY_ZERO, ImplicitExceptions.CREATE_DIVISION_BY_ZERO_EXCEPTION);
        createExceptionDescriptors.put(BytecodeExceptionKind.ASSERTION_ERROR_NULLARY, ImplicitExceptions.CREATE_ASSERTION_ERROR_NULLARY);
        createExceptionDescriptors.put(BytecodeExceptionKind.ASSERTION_ERROR_OBJECT, ImplicitExceptions.CREATE_ASSERTION_ERROR_OBJECT);

        throwCachedExceptionDescriptors = new EnumMap<>(BytecodeExceptionKind.class);
        throwCachedExceptionDescriptors.put(BytecodeExceptionKind.NULL_POINTER, ImplicitExceptions.THROW_CACHED_NULL_POINTER_EXCEPTION);
        throwCachedExceptionDescriptors.put(BytecodeExceptionKind.OUT_OF_BOUNDS, ImplicitExceptions.THROW_CACHED_OUT_OF_BOUNDS_EXCEPTION);
        throwCachedExceptionDescriptors.put(BytecodeExceptionKind.INTRINSIC_OUT_OF_BOUNDS, ImplicitExceptions.THROW_CACHED_OUT_OF_BOUNDS_EXCEPTION);
        throwCachedExceptionDescriptors.put(BytecodeExceptionKind.CLASS_CAST, ImplicitExceptions.THROW_CACHED_CLASS_CAST_EXCEPTION);
        throwCachedExceptionDescriptors.put(BytecodeExceptionKind.ARRAY_STORE, ImplicitExceptions.THROW_CACHED_ARRAY_STORE_EXCEPTION);
        throwCachedExceptionDescriptors.put(BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_NEGATIVE_LENGTH, ImplicitExceptions.THROW_CACHED_ILLEGAL_ARGUMENT_EXCEPTION);
        throwCachedExceptionDescriptors.put(BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_ARGUMENT_IS_NOT_AN_ARRAY, ImplicitExceptions.THROW_CACHED_ILLEGAL_ARGUMENT_EXCEPTION);
        throwCachedExceptionDescriptors.put(BytecodeExceptionKind.NEGATIVE_ARRAY_SIZE, ImplicitExceptions.THROW_CACHED_NEGATIVE_ARRAY_SIZE_EXCEPTION);
        throwCachedExceptionDescriptors.put(BytecodeExceptionKind.DIVISION_BY_ZERO, ImplicitExceptions.THROW_CACHED_ARITHMETIC_EXCEPTION);
        throwCachedExceptionDescriptors.put(BytecodeExceptionKind.ASSERTION_ERROR_NULLARY, ImplicitExceptions.THROW_CACHED_ASSERTION_ERROR);
        throwCachedExceptionDescriptors.put(BytecodeExceptionKind.ASSERTION_ERROR_OBJECT, ImplicitExceptions.THROW_CACHED_ASSERTION_ERROR);

        throwNewExceptionDescriptors = new EnumMap<>(BytecodeExceptionKind.class);
        throwNewExceptionDescriptors.put(BytecodeExceptionKind.NULL_POINTER, ImplicitExceptions.THROW_NEW_NULL_POINTER_EXCEPTION);
        throwNewExceptionDescriptors.put(BytecodeExceptionKind.OUT_OF_BOUNDS, ImplicitExceptions.THROW_NEW_OUT_OF_BOUNDS_EXCEPTION_WITH_ARGS);
        throwNewExceptionDescriptors.put(BytecodeExceptionKind.INTRINSIC_OUT_OF_BOUNDS, ImplicitExceptions.THROW_NEW_INTRINSIC_OUT_OF_BOUNDS_EXCEPTION);
        throwNewExceptionDescriptors.put(BytecodeExceptionKind.CLASS_CAST, ImplicitExceptions.THROW_NEW_CLASS_CAST_EXCEPTION_WITH_ARGS);
        throwNewExceptionDescriptors.put(BytecodeExceptionKind.ARRAY_STORE, ImplicitExceptions.THROW_NEW_ARRAY_STORE_EXCEPTION_WITH_ARGS);
        throwNewExceptionDescriptors.put(BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_NEGATIVE_LENGTH, ImplicitExceptions.THROW_NEW_ILLEGAL_ARGUMENT_EXCEPTION_WITH_ARGS);
        throwNewExceptionDescriptors.put(BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_ARGUMENT_IS_NOT_AN_ARRAY, ImplicitExceptions.THROW_NEW_ILLEGAL_ARGUMENT_EXCEPTION_WITH_ARGS);
        throwNewExceptionDescriptors.put(BytecodeExceptionKind.NEGATIVE_ARRAY_SIZE, ImplicitExceptions.THROW_NEW_NEGATIVE_ARRAY_SIZE_EXCEPTION);
        throwNewExceptionDescriptors.put(BytecodeExceptionKind.DIVISION_BY_ZERO, ImplicitExceptions.THROW_NEW_DIVISION_BY_ZERO_EXCEPTION);
        throwNewExceptionDescriptors.put(BytecodeExceptionKind.ASSERTION_ERROR_NULLARY, ImplicitExceptions.THROW_NEW_ASSERTION_ERROR_NULLARY);
        throwNewExceptionDescriptors.put(BytecodeExceptionKind.ASSERTION_ERROR_OBJECT, ImplicitExceptions.THROW_NEW_ASSERTION_ERROR_OBJECT);
    }

    private ForeignCallDescriptor lookupBytecodeException(BytecodeExceptionKind exceptionKind, NodeInputList<ValueNode> exceptionArguments, StructuredGraph graph,
                    LoweringTool tool, EnumMap<BytecodeExceptionKind, ForeignCallDescriptor> withoutAllocationDescriptors,
                    EnumMap<BytecodeExceptionKind, ForeignCallDescriptor> withAllocationDescriptors, List<ValueNode> outArguments) {
        ForeignCallDescriptor descriptor;
        if (mustNotAllocatePredicate != null && mustNotAllocatePredicate.test(graph.method())) {
            descriptor = withoutAllocationDescriptors.get(exceptionKind);
        } else {
            descriptor = withAllocationDescriptors.get(exceptionKind);
            if (exceptionKind.getExceptionMessage() != null) {
                outArguments.add(ConstantNode.forConstant(tool.getConstantReflection().forString(exceptionKind.getExceptionMessage()), tool.getMetaAccess(), graph));
            }
            outArguments.addAll(exceptionArguments);
        }
        VMError.guarantee(descriptor != null, "No ForeignCallDescriptor for ByteCodeExceptionKind " + exceptionKind);
        assert descriptor.getArgumentTypes().length == outArguments.size();
        return descriptor;
    }

    private class BytecodeExceptionLowering implements NodeLoweringProvider<BytecodeExceptionNode> {
        @Override
        public void lower(BytecodeExceptionNode node, LoweringTool tool) {
            if (tool.getLoweringStage() == StandardLoweringStage.HIGH_TIER) {
                return;
            }

            StructuredGraph graph = node.graph();
            List<ValueNode> arguments = new ArrayList<>();
            ForeignCallDescriptor descriptor = lookupBytecodeException(node.getExceptionKind(), node.getArguments(), graph, tool,
                            getCachedExceptionDescriptors, createExceptionDescriptors, arguments);

            ForeignCallNode foreignCallNode = graph.add(new ForeignCallNode(descriptor, node.stamp(NodeView.DEFAULT), arguments));
            foreignCallNode.setStateAfter(node.createStateDuring());
            graph.replaceFixedWithFixed(node, foreignCallNode);
        }
    }

    private class ThrowBytecodeExceptionLowering implements NodeLoweringProvider<ThrowBytecodeExceptionNode> {
        @Override
        public void lower(ThrowBytecodeExceptionNode node, LoweringTool tool) {
            if (tool.getLoweringStage() == StandardLoweringStage.HIGH_TIER) {
                return;
            }

            StructuredGraph graph = node.graph();
            List<ValueNode> arguments = new ArrayList<>();
            ForeignCallDescriptor descriptor = lookupBytecodeException(node.getExceptionKind(), node.getArguments(), graph, tool,
                            throwCachedExceptionDescriptors, throwNewExceptionDescriptors, arguments);

            ForeignCallNode foreignCallNode = graph.add(new ForeignCallNode(descriptor, node.stamp(NodeView.DEFAULT), arguments));
            foreignCallNode.setStateDuring(node.stateBefore());
            node.replaceAndDelete(foreignCallNode);

            LoweredDeadEndNode deadEnd = graph.add(new LoweredDeadEndNode());
            foreignCallNode.setNext(deadEnd);
        }
    }

    private static class GetClassLowering implements NodeLoweringProvider<GetClassNode> {
        @Override
        public void lower(GetClassNode node, LoweringTool tool) {
            StampProvider stampProvider = tool.getStampProvider();
            LoadHubNode loadHub = node.graph().unique(new LoadHubNode(stampProvider, node.getObject()));
            node.replaceAtUsagesAndDelete(loadHub);
            tool.getLowerer().lower(loadHub, tool);
        }
    }

    private class InvokeLowering implements NodeLoweringProvider<FixedNode> {

        @Override
        public void lower(FixedNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            Invoke invoke = (Invoke) node;
            if (invoke.callTarget() instanceof MethodCallTargetNode) {
                MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
                NodeInputList<ValueNode> parameters = callTarget.arguments();
                ValueNode receiver = parameters.size() <= 0 ? null : parameters.get(0);
                FixedGuardNode nullCheck = null;
                if (!callTarget.isStatic() && receiver.getStackKind() == JavaKind.Object && !StampTool.isPointerNonNull(receiver)) {
                    LogicNode isNull = graph.unique(IsNullNode.create(receiver));
                    nullCheck = graph.add(new FixedGuardNode(isNull, DeoptimizationReason.NullCheckException, DeoptimizationAction.None, true));
                    graph.addBeforeFixed(node, nullCheck);
                }
                SharedMethod method = (SharedMethod) callTarget.targetMethod();
                JavaType[] signature = method.getSignature().toParameterTypes(callTarget.isStatic() ? null : method.getDeclaringClass());
                CallingConvention.Type callType = method.getCallingConventionKind().toType(true);
                InvokeKind invokeKind = callTarget.invokeKind();
                SharedMethod[] implementations = method.getImplementations();

                if (verifyTypes && !callTarget.isStatic() && receiver.getStackKind() == JavaKind.Object && !Uninterruptible.Utils.isUninterruptible(graph.method())) {
                    /*
                     * Verify that the receiver is an instance of the class that declares the call
                     * target method. To avoid that the new type check floats above a deoptimization
                     * entry point, we need to anchor the receiver to the control flow. To avoid
                     * that Graal optimizes away the InstanceOfNode immediately, we need an
                     * OpaqueNode that removes all type information from the receiver. Then we wire
                     * up an IfNode that leads to a ForeignCallNode in case the verification fails.
                     */
                    FixedValueAnchorNode anchoredReceiver = graph.add(new FixedValueAnchorNode(receiver));
                    graph.addBeforeFixed(node, anchoredReceiver);
                    ValueNode opaqueReceiver = graph.unique(new OpaqueNode(anchoredReceiver));
                    TypeReference declaringClass = TypeReference.createTrustedWithoutAssumptions(method.getDeclaringClass());
                    LogicNode instanceOf = graph.addOrUniqueWithInputs(InstanceOfNode.create(declaringClass, opaqueReceiver));
                    BeginNode passingBegin = graph.add(new BeginNode());
                    BeginNode failingBegin = graph.add(new BeginNode());
                    IfNode ifNode = graph.add(new IfNode(instanceOf, passingBegin, failingBegin, BranchProbabilityNode.EXTREMELY_FAST_PATH_PROFILE));

                    ((FixedWithNextNode) node.predecessor()).setNext(ifNode);
                    passingBegin.setNext(node);

                    String errorMessage;
                    if (SubstrateUtil.HOSTED) {
                        errorMessage = "Invoke " + invokeKind + " of " + method.format("%H.%n(%p)%r");
                        errorMessage += System.lineSeparator() + "  declaringClass = " + declaringClass;
                        if (implementations.length == 0 || implementations.length > 10) {
                            errorMessage += System.lineSeparator() + "  implementations.length = " + implementations.length;
                        } else {
                            for (int i = 0; i < implementations.length; i++) {
                                errorMessage += System.lineSeparator() + "  implementations[" + i + "] = " + implementations[i].format("%H.%n(%p)%r");
                            }
                        }
                    } else {
                        errorMessage = "Invoke (method name not added because message must be a compile-time constant)";
                    }
                    ConstantNode errorConstant = ConstantNode.forConstant(tool.getConstantReflection().forString(errorMessage), tool.getMetaAccess(), graph);
                    ForeignCallNode reportError = graph.add(new ForeignCallNode(REPORT_VERIFY_TYPES_ERROR, opaqueReceiver, errorConstant));
                    reportError.setStateAfter(invoke.stateAfter().duplicateModifiedDuringCall(invoke.bci(), node.getStackKind()));
                    failingBegin.setNext(reportError);
                    reportError.setNext(graph.add(new LoweredDeadEndNode()));
                }

                LoadHubNode hub = null;
                CallTargetNode loweredCallTarget;
                if (invokeKind.isDirect() || implementations.length == 1) {
                    SharedMethod targetMethod = method;
                    if (!invokeKind.isDirect()) {
                        /*
                         * We only have one possible implementation for a indirect call, so we can
                         * emit a direct call to the unique implementation.
                         */
                        targetMethod = implementations[0];
                    }

                    if (!SubstrateBackend.shouldEmitOnlyIndirectCalls()) {
                        loweredCallTarget = graph.add(new DirectCallTargetNode(parameters.toArray(new ValueNode[parameters.size()]),
                                        callTarget.returnStamp(), signature, targetMethod, callType, invokeKind));
                    } else if (!targetMethod.hasCodeOffsetInImage()) {
                        /*
                         * The target method is not included in the image. This means that it was
                         * also not needed for the deoptimization entry point. Thus, we are certain
                         * that this branch will fold away. If not, we will fail later on.
                         *
                         * Also lower the MethodCallTarget below to avoid recursive lowering error
                         * messages. The invoke and call target are actually dead and will be
                         * removed by a subsequent dead code elimination pass.
                         */
                        loweredCallTarget = createUnreachableCallTarget(tool, node, parameters, callTarget.returnStamp(), signature, method, callType, invokeKind);
                    } else {
                        /*
                         * In runtime-compiled code, we emit indirect calls via the respective heap
                         * objects to avoid patching and creating trampolines.
                         */
                        JavaConstant codeInfo = SubstrateObjectConstant.forObject(CodeInfoTable.getImageCodeCache());
                        ValueNode codeInfoConstant = ConstantNode.forConstant(codeInfo, tool.getMetaAccess(), graph);
                        ValueNode codeStartFieldOffset = ConstantNode.forIntegerKind(FrameAccess.getWordKind(), runtimeConfig.getImageCodeInfoCodeStartOffset(), graph);
                        AddressNode codeStartField = graph.unique(new OffsetAddressNode(codeInfoConstant, codeStartFieldOffset));
                        // Not constant because runtime code can be persisted and loaded in a
                        // process where image code is located elsewhere:
                        ReadNode codeStart = graph.add(new ReadNode(codeStartField, LocationIdentity.ANY_LOCATION, FrameAccess.getWordStamp(), BarrierType.NONE));
                        ValueNode offset = ConstantNode.forIntegerKind(FrameAccess.getWordKind(), targetMethod.getCodeOffsetInImage(), graph);
                        AddressNode address = graph.unique(new OffsetAddressNode(codeStart, offset));

                        loweredCallTarget = graph.add(new IndirectCallTargetNode(
                                        address, parameters.toArray(new ValueNode[parameters.size()]), callTarget.returnStamp(), signature, targetMethod, callType, invokeKind));
                        graph.addBeforeFixed(node, codeStart);
                    }
                } else if (implementations.length == 0) {
                    /*
                     * We are calling an abstract method with no implementation, i.e., the
                     * closed-world analysis showed that there is no concrete receiver ever
                     * allocated. This must be dead code.
                     *
                     * Also lower the MethodCallTarget below to avoid recursive lowering error
                     * messages. The invoke and call target are actually dead and will be removed by
                     * a subsequent dead code elimination pass.
                     */
                    loweredCallTarget = createUnreachableCallTarget(tool, node, parameters, callTarget.returnStamp(), signature, method, callType, invokeKind);

                } else {
                    int vtableEntryOffset = runtimeConfig.getVTableOffset(method.getVTableIndex());

                    hub = graph.unique(new LoadHubNode(runtimeConfig.getProviders().getStampProvider(), graph.maybeAddOrUnique(PiNode.create(receiver, nullCheck))));
                    AddressNode address = graph.unique(new OffsetAddressNode(hub, ConstantNode.forIntegerKind(FrameAccess.getWordKind(), vtableEntryOffset, graph)));
                    ReadNode entry = graph.add(new ReadNode(address, NamedLocationIdentity.FINAL_LOCATION, FrameAccess.getWordStamp(), BarrierType.NONE));
                    loweredCallTarget = graph.add(
                                    new IndirectCallTargetNode(entry, parameters.toArray(new ValueNode[parameters.size()]), callTarget.returnStamp(), signature, method, callType, invokeKind));

                    graph.addBeforeFixed(node, entry);
                }

                callTarget.replaceAndDelete(loweredCallTarget);

                // Recursive lowering
                if (nullCheck != null) {
                    nullCheck.lower(tool);
                }
                if (hub != null) {
                    hub.lower(tool);
                }
            }
        }

        private CallTargetNode createUnreachableCallTarget(LoweringTool tool, FixedNode node, NodeInputList<ValueNode> parameters, StampPair returnStamp, JavaType[] signature, SharedMethod method,
                        CallingConvention.Type callType, InvokeKind invokeKind) {
            StructuredGraph graph = node.graph();
            FixedGuardNode unreachedGuard = graph.add(new FixedGuardNode(LogicConstantNode.forBoolean(true, graph), DeoptimizationReason.UnreachedCode, DeoptimizationAction.None, true));
            graph.addBeforeFixed(node, unreachedGuard);
            // Recursive lowering
            unreachedGuard.lower(tool);

            /*
             * Also lower the MethodCallTarget below to avoid recursive lowering error messages. The
             * invoke and call target are actually dead and will be removed by a subsequent dead
             * code elimination pass.
             */
            return graph.add(new DirectCallTargetNode(parameters.toArray(new ValueNode[parameters.size()]), returnStamp, signature, method, callType, invokeKind));
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void reportVerifyTypesError(Object object, String message) {
        throw VMError.shouldNotReachHere("VerifyTypes: object=" + (object == null ? "null" : object.getClass().getTypeName()) +
                        System.lineSeparator() + message +
                        System.lineSeparator() + "VerifyTypes found a type error");
    }
}
