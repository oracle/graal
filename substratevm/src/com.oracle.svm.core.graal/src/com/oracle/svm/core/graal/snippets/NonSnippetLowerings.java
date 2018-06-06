/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
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
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.amd64.AMD64ConvertSnippets;

import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.amd64.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.ImplicitExceptions;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class NonSnippetLowerings {

    @SuppressWarnings("unused")
    public static void registerLowerings(RuntimeConfiguration runtimeConfig, Predicate<ResolvedJavaMethod> mustNotAllocatePredicate, OptionValues options, Iterable<DebugHandlersFactory> factories,
                    Providers providers, SnippetReflectionProvider snippetReflection, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new NonSnippetLowerings(runtimeConfig, mustNotAllocatePredicate, options, factories, providers, snippetReflection, lowerings);
    }

    private final RuntimeConfiguration runtimeConfig;
    private final Predicate<ResolvedJavaMethod> mustNotAllocatePredicate;

    private NonSnippetLowerings(RuntimeConfiguration runtimeConfig, Predicate<ResolvedJavaMethod> mustNotAllocatePredicate, OptionValues options, Iterable<DebugHandlersFactory> factories,
                    Providers providers, SnippetReflectionProvider snippetReflection, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        this.runtimeConfig = runtimeConfig;
        this.mustNotAllocatePredicate = mustNotAllocatePredicate;

        lowerings.put(BytecodeExceptionNode.class, new BytecodeExceptionLowering());
        lowerings.put(GetClassNode.class, new GetClassLowering());
        lowerings.put(InvokeNode.class, new InvokeLowering());
        lowerings.put(InvokeWithExceptionNode.class, new InvokeLowering());
        lowerings.put(FloatConvertNode.class, new FloatConvertLowering(options, factories, providers, snippetReflection, ConfigurationValues.getTarget()));
    }

    private static final EnumMap<BytecodeExceptionKind, RuntimeException> cachedExceptions;
    private static final EnumMap<BytecodeExceptionKind, ForeignCallDescriptor> callDescriptors;

    static {
        cachedExceptions = new EnumMap<>(BytecodeExceptionKind.class);
        cachedExceptions.put(BytecodeExceptionKind.NULL_POINTER, ImplicitExceptions.CACHED_NULL_POINTER_EXCEPTION);
        cachedExceptions.put(BytecodeExceptionKind.OUT_OF_BOUNDS, ImplicitExceptions.CACHED_OUT_OF_BOUNDS_EXCEPTION);
        cachedExceptions.put(BytecodeExceptionKind.CLASS_CAST, ImplicitExceptions.CACHED_CLASS_CAST_EXCEPTION);
        cachedExceptions.put(BytecodeExceptionKind.ARRAY_STORE, ImplicitExceptions.CACHED_ARRAY_STORE_EXCEPTION);
        cachedExceptions.put(BytecodeExceptionKind.DIVISION_BY_ZERO, ImplicitExceptions.CACHED_ARITHMETIC_EXCEPTION);

        callDescriptors = new EnumMap<>(BytecodeExceptionKind.class);
        callDescriptors.put(BytecodeExceptionKind.NULL_POINTER, ImplicitExceptions.CREATE_NULL_POINTER_EXCEPTION);
        callDescriptors.put(BytecodeExceptionKind.OUT_OF_BOUNDS, ImplicitExceptions.CREATE_OUT_OF_BOUNDS_EXCEPTION);
        callDescriptors.put(BytecodeExceptionKind.CLASS_CAST, ImplicitExceptions.CREATE_CLASS_CAST_EXCEPTION);
        callDescriptors.put(BytecodeExceptionKind.ARRAY_STORE, ImplicitExceptions.CREATE_ARRAY_STORE_EXCEPTION);
        callDescriptors.put(BytecodeExceptionKind.DIVISION_BY_ZERO, ImplicitExceptions.CREATE_DIVISION_BY_ZERO_EXCEPTION);
    }

    private class BytecodeExceptionLowering implements NodeLoweringProvider<BytecodeExceptionNode> {
        @Override
        public void lower(BytecodeExceptionNode node, LoweringTool tool) {
            if (mustNotAllocatePredicate != null && mustNotAllocatePredicate.test(node.graph().method())) {
                RuntimeException exception = cachedExceptions.get(node.getExceptionKind());
                assert exception != null;

                ConstantNode exceptionNode = ConstantNode.forConstant(SubstrateObjectConstant.forObject(exception), tool.getMetaAccess(), node.graph());
                node.graph().replaceFixedWithFloating(node, exceptionNode);

            } else {
                ForeignCallDescriptor descriptor = callDescriptors.get(node.getExceptionKind());
                assert descriptor != null && descriptor.getArgumentTypes().length == node.getArguments().size();

                StructuredGraph graph = node.graph();
                ForeignCallNode foreignCallNode = graph.add(new ForeignCallNode(runtimeConfig.getProviders().getForeignCalls(), descriptor, node.stamp(NodeView.DEFAULT), node.getArguments()));
                foreignCallNode.setStateAfter(node.stateAfter());
                graph.replaceFixedWithFixed(node, foreignCallNode);
            }
        }
    }

    private static class GetClassLowering implements NodeLoweringProvider<GetClassNode> {
        @Override
        public void lower(GetClassNode node, LoweringTool tool) {
            StampProvider stampProvider = tool.getStampProvider();
            LoadHubNode loadHub = node.graph().unique(new LoadHubNode(stampProvider, node.getObject()));
            node.replaceAtUsagesAndDelete(loadHub);
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
                CallingConvention.Type callType = SubstrateCallingConventionType.JavaCall;

                InvokeKind invokeKind = callTarget.invokeKind();
                SharedMethod[] implementations = method.getImplementations();
                LoadHubNode hub = null;
                CallTargetNode loweredCallTarget;

                if (invokeKind != InvokeKind.Static && implementations.length == 0) {
                    /*
                     * We are calling an abstract method with no implementation, i.e., the
                     * closed-world analysis showed that there is no concrete receiver ever
                     * allocated. This must be dead code.
                     */
                    FixedGuardNode unreachedGuard = graph.add(new FixedGuardNode(LogicConstantNode.forBoolean(true, graph), DeoptimizationReason.UnreachedCode, DeoptimizationAction.None, true));
                    graph.addBeforeFixed(node, unreachedGuard);
                    // Recursive lowering
                    unreachedGuard.lower(tool);

                    /*
                     * Also lower the MethodCallTarget to avoid recursive lowering error messages.
                     * The invoke and call target are actually dead and will be removed by a
                     * subsequent dead code elimination pass.
                     */
                    loweredCallTarget = graph.add(new DirectCallTargetNode(parameters.toArray(new ValueNode[parameters.size()]), callTarget.returnStamp(), signature, method, callType,
                                    invokeKind));

                } else if (invokeKind.isDirect()) {
                    loweredCallTarget = graph.add(new DirectCallTargetNode(parameters.toArray(new ValueNode[parameters.size()]), callTarget.returnStamp(), signature, callTarget.targetMethod(),
                                    callType, invokeKind));

                } else if (implementations.length == 1) {
                    /*
                     * We only have one possible implementation for a indirect call, so we can emit
                     * a direct call to the uniqye implementation.
                     */
                    SharedMethod uniqueImplementation = implementations[0];
                    loweredCallTarget = graph.add(new DirectCallTargetNode(parameters.toArray(new ValueNode[parameters.size()]), callTarget.returnStamp(), signature, uniqueImplementation,
                                    callType, invokeKind));

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
    }

    protected static class FloatConvertLowering implements NodeLoweringProvider<FloatConvertNode> {

        private final AMD64ConvertSnippets.Templates convertSnippets;

        public FloatConvertLowering(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            convertSnippets = new AMD64ConvertSnippets.Templates(options, factories, providers, snippetReflection, target);
        }

        @Override
        public void lower(FloatConvertNode node, LoweringTool tool) {
            convertSnippets.lower(node, tool);
        }
    }
}
