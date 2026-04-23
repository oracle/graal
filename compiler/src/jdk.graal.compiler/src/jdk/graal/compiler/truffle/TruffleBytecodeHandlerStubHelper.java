/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MultiReturnNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchorNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.FieldAliasNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.replacements.GraphKit;
import jdk.graal.compiler.truffle.BytecodeHandlerConfig.ArgumentInfo;
import jdk.graal.compiler.truffle.nodes.TruffleBytecodeHandlerDispatchAddressNode;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Shared helper for constructing bytecode handler stubs across HotSpot and Substrate backends.
 */
public final class TruffleBytecodeHandlerStubHelper {

    private TruffleBytecodeHandlerStubHelper() {
    }

    public static String getStubName(ResolvedJavaMethod targetMethod) {
        return "__stub_" + targetMethod.getName();
    }

    public static ParameterNode[] collectParameterNodes(BytecodeHandlerConfig config, GraphKit kit) {
        List<ArgumentInfo> argumentInfos = config.getArgumentInfos();
        ParameterNode[] parameterNodes = new ParameterNode[argumentInfos.size()];

        for (ArgumentInfo argumentInfo : argumentInfos) {
            ParameterNode parameterNode = kit.unique(new ParameterNode(argumentInfo.index(), StampFactory.forDeclaredType(kit.getAssumptions(), argumentInfo.type(), false)));
            if (argumentInfo.nonNull() && parameterNode.stamp(NodeView.DEFAULT).isObjectStamp()) {
                parameterNode.setStamp(((AbstractObjectStamp) parameterNode.stamp(NodeView.DEFAULT)).asNonNull());
            }
            parameterNodes[argumentInfo.index()] = parameterNode;
        }

        return parameterNodes;
    }

    private static ValueNode[] createCalleeArguments(BytecodeHandlerConfig handlerConfig, ResolvedJavaMethod targetMethod, GraphKit kit, ParameterNode[] parameterNodes) {
        ArrayList<ValueNode> argumentNodes = new ArrayList<>();

        AllocatedObjectNode[] allocatedObjects = new AllocatedObjectNode[targetMethod.getSignature().getParameterCount(targetMethod.hasReceiver())];
        EconomicMap<AllocatedObjectNode, List<ValueNode>> virtualFields = EconomicMap.create();

        List<ArgumentInfo> argumentInfos = handlerConfig.getArgumentInfos();
        for (ArgumentInfo argumentInfo : argumentInfos) {
            ParameterNode parameterNode = parameterNodes[argumentInfo.index()];
            if (argumentInfo.isExpanded()) {
                int index = argumentInfo.originalIndex();
                if (argumentInfo.isOwnerVirtual()) {
                    AllocatedObjectNode allocatedObj = allocatedObjects[index];
                    if (allocatedObj == null) {
                        VirtualInstanceNode virtualObj = kit.add(new VirtualInstanceNode(argumentInfo.ownerType(), true));
                        allocatedObj = kit.unique(new AllocatedObjectNode(virtualObj));

                        allocatedObjects[index] = allocatedObj;
                        virtualFields.put(allocatedObj, new ArrayList<>());

                        argumentNodes.add(allocatedObj);
                    }
                    virtualFields.get(allocatedObj).add(parameterNode);
                } else {
                    ValueNode owner = argumentNodes.getLast();
                    kit.append(new FieldAliasNode(owner, argumentInfo.field(), parameterNode));
                }
            } else {
                argumentNodes.add(parameterNode);
            }
        }

        if (!virtualFields.isEmpty()) {
            CommitAllocationNode commit = kit.append(new CommitAllocationNode());
            for (AllocatedObjectNode allocatedObjectNode : allocatedObjects) {
                if (allocatedObjectNode != null) {
                    commit.getVirtualObjects().add(allocatedObjectNode.getVirtualObject());
                    commit.getEnsureVirtual().add(false);
                    commit.getValues().addAll(virtualFields.get(allocatedObjectNode));
                    commit.addLocks(Collections.emptyList());
                    allocatedObjectNode.setCommit(commit);
                }
            }
        }
        return argumentNodes.toArray(ValueNode.EMPTY_ARRAY);
    }

    private static ValueNode[] updateArguments(BytecodeHandlerConfig handlerConfig, ValueNode result, ValueNode[] argumentsToOriginalHandler) {
        ValueNode[] updatedArguments = argumentsToOriginalHandler.clone();
        for (ArgumentInfo argumentInfo : handlerConfig.getArgumentInfos()) {
            if (argumentInfo.copyFromReturn()) {
                updatedArguments[argumentInfo.originalIndex()] = result;
            }
        }
        return updatedArguments;
    }

    private static ValueNode appendInvoke(BytecodeHandlerConfig handlerConfig, ResolvedJavaMethod method, int bci, ValueNode[] inputs, FrameStateBuilder frameStateBuilder, GraphKit kit) {
        CallTargetNode.InvokeKind invokeKind = method.isStatic() ? CallTargetNode.InvokeKind.Static : CallTargetNode.InvokeKind.Special;
        ValueNode invoke = kit.createInvokeWithExceptionAndUnwind(method, invokeKind, frameStateBuilder, bci, inputs);
        return handlerConfig.getReturnType().getJavaKind() == JavaKind.Void ? null : invoke;
    }

    private static MultiReturnNode createStubReturn(BytecodeHandlerConfig handlerConfig, GraphKit kit, ParameterNode[] parameterNodes, ValueNode result, ValueNode tailCallTarget,
                    ValueNode[] argumentsToOriginalHandler) {
        MultiReturnNode multiReturnNode = kit.unique(new MultiReturnNode(result, tailCallTarget));
        List<ValueNode> additionalReturnResults = multiReturnNode.getAdditionalReturnResults();

        for (ArgumentInfo argumentInfo : handlerConfig.getArgumentInfos()) {
            if (argumentInfo.isExpanded()) {
                if (argumentInfo.isImmutable()) {
                    additionalReturnResults.add(parameterNodes[argumentInfo.index()]);
                } else {
                    ValueNode owner = argumentsToOriginalHandler[argumentInfo.originalIndex()];
                    LoadFieldNode load = kit.append(LoadFieldNode.create(kit.getAssumptions(), owner, argumentInfo.field()));
                    additionalReturnResults.add(load);
                }
            } else if (argumentInfo.copyFromReturn()) {
                GraalError.guarantee(result != null, "copying from Void");
                additionalReturnResults.add(result);
            } else {
                additionalReturnResults.add(argumentsToOriginalHandler[argumentInfo.originalIndex()]);
            }
            if (tailCallTarget != null && argumentInfo.nonNull() && !argumentInfo.type().isPrimitive()) {
                LogicNode isNull = kit.unique(IsNullNode.create(additionalReturnResults.getLast()));
                kit.append(new FixedGuardNode(isNull, DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, true));
            }
        }
        return multiReturnNode;
    }

    /**
     * Generates a bytecode handler stub that expands handler inputs, invokes the original handler,
     * and returns the updated arguments in the stub calling convention.
     */
    public static StructuredGraph createStub(GraphKit kit, ResolvedJavaMethod frameOwner, int bci, boolean threading, ResolvedJavaMethod nextOpcodeMethod,
                    Supplier<Object> bytecodeHandlerTableSupplier, BytecodeHandlerConfig handlerConfig, ResolvedJavaMethod targetMethod) {
        StructuredGraph graph = kit.getGraph();
        FrameStateBuilder frameStateBuilder = new FrameStateBuilder(kit, frameOwner, graph);
        graph.start().setStateAfter(frameStateBuilder.create(bci, graph.start()));

        graph.getGraphState().forceDisableFrameStateVerification();

        ParameterNode[] parameterNodes = collectParameterNodes(handlerConfig, kit);
        ValueNode[] argumentsToOriginalHandler = createCalleeArguments(handlerConfig, targetMethod, kit, parameterNodes);
        ValueNode handlerResult = appendInvoke(handlerConfig, targetMethod, bci, argumentsToOriginalHandler, frameStateBuilder, kit);

        kit.append(new ControlFlowAnchorNode());

        TruffleBytecodeHandlerDispatchAddressNode tailCallTarget = null;
        if (threading) {
            ValueNode[] updatedArguments = updateArguments(handlerConfig, handlerResult, argumentsToOriginalHandler);
            ValueNode nextOpcode = appendInvoke(handlerConfig, nextOpcodeMethod, bci, updatedArguments, frameStateBuilder, kit);
            tailCallTarget = kit.append(new TruffleBytecodeHandlerDispatchAddressNode(nextOpcode, bytecodeHandlerTableSupplier));
        }

        kit.append(new ReturnNode(createStubReturn(handlerConfig, kit, parameterNodes, handlerResult, tailCallTarget, argumentsToOriginalHandler)));
        graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, "Initial graph for bytecode handler stub");
        return graph;
    }
}
