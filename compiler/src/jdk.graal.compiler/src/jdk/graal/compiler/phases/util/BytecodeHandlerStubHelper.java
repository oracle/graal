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
package jdk.graal.compiler.phases.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeadEndNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MultiReturnNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchorNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.FieldAliasNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig.ArgumentInfo;
import jdk.graal.compiler.replacements.GraphKit;
import jdk.graal.compiler.replacements.nodes.ReadRegisterNode;
import jdk.graal.compiler.nodes.extended.BytecodeHandlerDispatchAddressNode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Shared helper for constructing bytecode handler stubs across HotSpot and Substrate backends.
 */
public final class BytecodeHandlerStubHelper {

    private BytecodeHandlerStubHelper() {
    }

    /**
     * Returns the synthetic method name used for the stub generated from {@code targetMethod}.
     */
    public static String getStubName(ResolvedJavaMethod targetMethod) {
        return "__stub_" + targetMethod.getName();
    }

    /**
     * Creates one stub parameter per configured ABI argument and applies non-null stamps for
     * arguments whose bytecode-handler configuration guarantees non-null values.
     */
    public static ParameterNode[] collectParameterNodes(BytecodeHandlerConfig config, GraphKit kit) {
        List<ArgumentInfo> stubAbiArgumentInfos = config.getStubAbiArgumentInfos();
        ParameterNode[] stubParameters = new ParameterNode[stubAbiArgumentInfos.size()];

        for (ArgumentInfo argumentInfo : stubAbiArgumentInfos) {
            ParameterNode stubParameter = kit.unique(new ParameterNode(argumentInfo.index(), StampFactory.forDeclaredType(kit.getAssumptions(), argumentInfo.type(), false)));
            if (argumentInfo.nonNull() && stubParameter.stamp(NodeView.DEFAULT).isObjectStamp()) {
                stubParameter.setStamp(((AbstractObjectStamp) stubParameter.stamp(NodeView.DEFAULT)).asNonNull());
            }
            stubParameters[argumentInfo.index()] = stubParameter;
        }

        return stubParameters;
    }

    /**
     * Reconstructs the original Java handler argument list from the stub ABI parameters. Virtual
     * expanded arguments are re-materialized as virtual objects whose fields alias the separate
     * stub parameters.
     */
    private static ValueNode[] createHandlerArguments(BytecodeHandlerConfig handlerConfig, ResolvedJavaMethod targetMethod, GraphKit kit, ParameterNode[] stubParameters, int templateIndex) {
        ArrayList<ValueNode> handlerArguments = new ArrayList<>();

        AllocatedObjectNode[] allocatedObjects = new AllocatedObjectNode[targetMethod.getSignature().getParameterCount(targetMethod.hasReceiver())];
        EconomicMap<AllocatedObjectNode, List<ValueNode>> virtualFields = EconomicMap.create();

        List<ArgumentInfo> allArgumentInfos = handlerConfig.getAllArgumentInfos();
        int remainingTemplateIndex = templateIndex;
        for (ArgumentInfo argumentInfo : allArgumentInfos) {
            if (argumentInfo.isExpanded()) {
                int index = argumentInfo.originalIndex();
                if (argumentInfo.isOwnerVirtual()) {
                    AllocatedObjectNode allocatedObj = allocatedObjects[index];
                    if (allocatedObj == null) {
                        VirtualInstanceNode virtualObj = kit.add(new VirtualInstanceNode(argumentInfo.ownerType(), true));
                        allocatedObj = kit.unique(new AllocatedObjectNode(virtualObj));

                        allocatedObjects[index] = allocatedObj;
                        virtualFields.put(allocatedObj, new ArrayList<>());

                        handlerArguments.add(allocatedObj);
                    }
                    if (argumentInfo.isTemplateVariable()) {
                        int templateValue = remainingTemplateIndex % argumentInfo.templateVariants();
                        remainingTemplateIndex /= argumentInfo.templateVariants();
                        virtualFields.get(allocatedObj).add(kit.unique(ConstantNode.forInt(templateValue)));
                    } else {
                        virtualFields.get(allocatedObj).add(stubParameters[argumentInfo.index()]);
                    }
                } else {
                    ValueNode owner = handlerArguments.getLast();
                    kit.append(new FieldAliasNode(owner, argumentInfo.field(), stubParameters[argumentInfo.index()]));
                }
            } else {
                handlerArguments.add(stubParameters[argumentInfo.index()]);
            }
        }
        GraalError.guarantee(remainingTemplateIndex == 0, "Invalid template index %d for %d template variants", templateIndex, handlerConfig.getTemplatesLength());

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
        return handlerArguments.toArray(ValueNode.EMPTY_ARRAY);
    }

    /**
     * Determines the invoke kind used for directly calling the original handler or fetch-opcode
     * method from its generated stub.
     */
    private static CallTargetNode.InvokeKind invokeKind(ResolvedJavaMethod method) {
        return method.isStatic() ? CallTargetNode.InvokeKind.Static : CallTargetNode.InvokeKind.Special;
    }

    /**
     * Produces the handler argument list after a normal handler return. {@code returnValue}
     * arguments observe the handler result before invoking the fetch-opcode method for threading.
     */
    private static ValueNode[] loadCurrentHandlerArguments(BytecodeHandlerConfig handlerConfig, ValueNode[] handlerArguments, ValueNode handlerResult) {
        ValueNode[] updatedHandlerArguments = handlerArguments.clone();
        for (ArgumentInfo argumentInfo : handlerConfig.getStubAbiArgumentInfos()) {
            if (argumentInfo.copyFromReturn()) {
                GraalError.guarantee(handlerResult != null, "copying from Void");
                updatedHandlerArguments[argumentInfo.originalIndex()] = handlerResult;
            }
        }
        return updatedHandlerArguments;
    }

    /**
     * Produces the current stub ABI values at a control-flow point. Mutable expanded fields are read
     * from their owner object, while immutable values can reuse their incoming stub parameters. When
     * called for an exception edge, {@code handlerResult} is {@code null}; a
     * {@code copyFromReturn} slot therefore keeps its incoming stub parameter, because the throwing
     * call produced no return value. Backend-specific unwind handling can then publish this current
     * stub ABI snapshot before the stub rethrows.
     */
    private static ValueNode[] loadCurrentStubArguments(BytecodeHandlerConfig handlerConfig, GraphKit kit, ParameterNode[] stubParameters, ValueNode[] handlerArguments, ValueNode handlerResult) {
        ValueNode[] values = new ValueNode[handlerConfig.getStubAbiArgumentInfos().size()];
        for (ArgumentInfo argumentInfo : handlerConfig.getStubAbiArgumentInfos()) {
            if (argumentInfo.isExpanded()) {
                if (argumentInfo.isImmutable()) {
                    values[argumentInfo.index()] = stubParameters[argumentInfo.index()];
                } else {
                    ValueNode owner = handlerArguments[argumentInfo.originalIndex()];
                    values[argumentInfo.index()] = kit.append(LoadFieldNode.create(kit.getAssumptions(), owner, argumentInfo.field()));
                }
            } else if (argumentInfo.copyFromReturn() && handlerResult != null) {
                values[argumentInfo.index()] = handlerResult;
            } else {
                values[argumentInfo.index()] = stubParameters[argumentInfo.index()];
            }
        }
        return values;
    }

    /**
     * Builds the multi-return payload used by the bytecode-handler stub ABI: the Java handler
     * result, an optional tail-call target, and the current value of every stub argument.
     */
    private static MultiReturnNode createStubReturn(BytecodeHandlerConfig handlerConfig, GraphKit kit, ValueNode handlerResult, ValueNode tailCallTarget,
                    ValueNode[] currentStubArguments) {
        MultiReturnNode multiReturnNode = kit.unique(new MultiReturnNode(handlerResult, tailCallTarget));
        List<ValueNode> additionalReturnResults = multiReturnNode.getAdditionalReturnResults();

        for (ArgumentInfo argumentInfo : handlerConfig.getStubAbiArgumentInfos()) {
            additionalReturnResults.add(currentStubArguments[argumentInfo.index()]);
            if (tailCallTarget != null && argumentInfo.nonNull() && !argumentInfo.type().isPrimitive()) {
                LogicNode isNull = kit.unique(IsNullNode.create(additionalReturnResults.getLast()));
                kit.append(new FixedGuardNode(isNull, DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, true));
            }
        }
        return multiReturnNode;
    }

    /**
     * Backend-specific hook for preserving current stub ABI values on the handler invoke's
     * exception path before the stub rethrows the same exception.
     */
    @FunctionalInterface
    public interface UnwindPathSupplier {
        /**
         * Emits backend-specific unwind handling. {@code exceptionPathStubArguments} contains the
         * current stub ABI values at the throwing handler call site.
         */
        void apply(BytecodeHandlerConfig handlerConfig, GraphKit kit, ValueNode[] exceptionPathStubArguments);
    }

    /**
     * Generates a bytecode-handler stub that expands stub ABI inputs back to the Java handler call
     * shape, invokes the original handler, and returns the current stub ABI values. If the handler
     * throws, the threading-exit callback runs before the optional {@code unwindPathSupplier}
     * publishes backend-specific pending state and the stub unwinds the same exception.
     */
    public static StructuredGraph createStub(GraphKit kit, ResolvedJavaMethod frameOwner, int bci, boolean threading, ResolvedJavaMethod nextOpcodeMethod,
                    ResolvedJavaMethod threadingExitMethod, IntFunction<Object> bytecodeHandlerTableSupplier, BytecodeHandlerConfig handlerConfig, ResolvedJavaMethod targetMethod, int templateIndex,
                    UnwindPathSupplier unwindPathSupplier) {
        StructuredGraph graph = kit.getGraph();
        FrameStateBuilder frameStateBuilder = new FrameStateBuilder(kit, frameOwner, graph);
        graph.start().setStateAfter(frameStateBuilder.create(bci, graph.start()));

        graph.getGraphState().forceDisableFrameStateVerification();

        ParameterNode[] stubParameters = collectParameterNodes(handlerConfig, kit);
        ValueNode[] handlerArguments = createHandlerArguments(handlerConfig, targetMethod, kit, stubParameters, templateIndex);
        InvokeWithExceptionNode handlerInvocation = kit.startInvokeWithException(targetMethod, invokeKind(targetMethod), frameStateBuilder, bci,
                        handlerArguments);
        if (unwindPathSupplier != null) {
            /*
             * SVM bytecode-handler stubs preserve pending state on the handler exception path. Mark
             * this invoke so decoded allocations in an inlined handler can restore OOME exception
             * edges and unwind through that path without relying on generic catch-all/finally
             * classification.
             */
            handlerInvocation.setInOOMETry(true);
        }
        ValueNode handlerResult = targetMethod.getSignature().getReturnType(targetMethod.getDeclaringClass()).getJavaKind() == JavaKind.Void ? null
                        : handlerInvocation;

        kit.noExceptionPart();
        kit.append(new ControlFlowAnchorNode());

        BytecodeHandlerDispatchAddressNode tailCallTarget = null;
        if (threading) {
            GraalError.guarantee(nextOpcodeMethod != null, "Threaded bytecode handler stubs require a BytecodeInterpreterFetchOpcode method");
            GraalError.guarantee(nextOpcodeMethod.getSignature().getReturnType(nextOpcodeMethod.getDeclaringClass()).getJavaKind() != JavaKind.Void,
                            "BytecodeInterpreterFetchOpcode method must not return void: %s", nextOpcodeMethod);
            ValueNode[] updatedHandlerArguments = loadCurrentHandlerArguments(handlerConfig, handlerArguments, handlerResult);
            ValueNode nextOpcode = createFetchOpcodeInvoke(kit, nextOpcodeMethod, frameStateBuilder, bci, updatedHandlerArguments);
            TemplateSelection template = loadTemplateSelection(handlerConfig, kit, updatedHandlerArguments);
            tailCallTarget = kit.append(new BytecodeHandlerDispatchAddressNode(nextOpcode, template.values(), template.variants(), bytecodeHandlerTableSupplier));
        } else if (threadingExitMethod != null) {
            ValueNode[] updatedHandlerArguments = loadCurrentHandlerArguments(handlerConfig, handlerArguments, handlerResult);
            invokeThreadingExit(kit, threadingExitMethod, frameStateBuilder, bci, updatedHandlerArguments);
        }

        ValueNode[] normalPathStubArguments = loadCurrentStubArguments(handlerConfig, kit, stubParameters, handlerArguments, handlerResult);
        kit.append(new ReturnNode(createStubReturn(handlerConfig, kit, handlerResult, tailCallTarget, normalPathStubArguments)));

        kit.exceptionPart();
        if (threadingExitMethod != null) {
            invokeThreadingExit(kit, threadingExitMethod, frameStateBuilder, bci, handlerArguments);
        }
        if (unwindPathSupplier != null) {
            ValueNode[] exceptionPathStubArguments = loadCurrentStubArguments(handlerConfig, kit, stubParameters, handlerArguments, null);
            unwindPathSupplier.apply(handlerConfig, kit, exceptionPathStubArguments);
        }
        kit.append(new UnwindNode(kit.exceptionObject()));
        kit.endInvokeWithException();
        graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, "Initial graph for bytecode handler stub");
        return graph;
    }

    private static ValueNode createFetchOpcodeInvoke(GraphKit kit, ResolvedJavaMethod nextOpcodeMethod, FrameStateBuilder frameStateBuilder, int bci, ValueNode[] updatedHandlerArguments) {
        ValueNode nextOpcode = kit.startInvokeWithException(nextOpcodeMethod, invokeKind(nextOpcodeMethod), frameStateBuilder, bci, updatedHandlerArguments);
        kit.exceptionPart();
        /*
         * BytecodeInterpreterFetchOpcode may need an exception edge while parsing, but threaded
         * stubs do not support unwinding from it. Any such edge that remains in the stub is a
         * contract violation, so terminate it instead of running the handler unwind path.
         */
        kit.append(new DeadEndNode());
        kit.endInvokeWithException();
        return nextOpcode;
    }

    private static TemplateSelection loadTemplateSelection(BytecodeHandlerConfig handlerConfig, GraphKit kit, ValueNode[] currentHandlerArguments) {
        List<ArgumentInfo> templateVariables = handlerConfig.getTemplateVariableArguments();
        ValueNode[] templateValues = new ValueNode[templateVariables.size()];
        int[] templateVariants = new int[templateVariables.size()];
        for (int i = 0; i < templateVariables.size(); i++) {
            ArgumentInfo templateVariable = templateVariables.get(i);
            ValueNode owner = currentHandlerArguments[templateVariable.originalIndex()];
            ValueNode templateValue = kit.append(LoadFieldNode.create(kit.getAssumptions(), owner, templateVariable.field()));
            templateValues[i] = templateValue;
            templateVariants[i] = templateVariable.templateVariants();
        }
        return new TemplateSelection(templateValues, templateVariants);
    }

    private static void invokeThreadingExit(GraphKit kit, ResolvedJavaMethod threadingExitMethod, FrameStateBuilder frameStateBuilder, int bci, ValueNode[] updatedHandlerArguments) {
        GraalError.guarantee(threadingExitMethod.getSignature().getReturnKind() == JavaKind.Void, "Expected BytecodeInterpreterThreadingExit to return void: %s",
                        threadingExitMethod.format("%H.%n(%p)"));
        kit.createInvokeWithExceptionAndUnwind(threadingExitMethod, invokeKind(threadingExitMethod), frameStateBuilder, bci, updatedHandlerArguments);
    }

    private record TemplateSelection(ValueNode[] values, int[] variants) {
    }

    /**
     * Generates a graph for a default bytecode-handler stub that serves as a fallback when no
     * specific handler is available.
     *
     * The generated graph returns the current {@code copyFromReturn} parameter, or the platform
     * return register when no such parameter exists, and passes the original parameters as
     * additional return results. This stub effectively terminates threading and triggers a
     * re-dispatch of the bytecode in the caller.
     */
    public static StructuredGraph createEmptyStub(GraphKit kit, BytecodeHandlerConfig handlerConfig, Register returnRegister) {
        return createEmptyStub(kit, null, 0, handlerConfig, returnRegister, null, 0);
    }

    public static StructuredGraph createEmptyStub(GraphKit kit, ResolvedJavaMethod frameOwner, int bci, BytecodeHandlerConfig handlerConfig, Register returnRegister,
                    ResolvedJavaMethod threadingExitMethod, int templateIndex) {
        StructuredGraph graph = kit.getGraph();
        graph.getGraphState().forceDisableFrameStateVerification();

        ParameterNode[] stubParameters = collectParameterNodes(handlerConfig, kit);
        ValueNode returnResult = null;

        for (ArgumentInfo argumentInfo : handlerConfig.getStubAbiArgumentInfos()) {
            if (argumentInfo.copyFromReturn()) {
                returnResult = stubParameters[argumentInfo.index()];
                break;
            }
        }

        if (returnResult == null) {
            JavaKind returnKind = handlerConfig.getReturnType().getJavaKind();
            if (returnKind != JavaKind.Void) {
                returnResult = kit.append(new ReadRegisterNode(returnRegister, returnKind, false, true));
            }
        }

        if (threadingExitMethod != null) {
            GraalError.guarantee(frameOwner != null, "Default handler stubs require a frame owner");
            FrameStateBuilder frameStateBuilder = new FrameStateBuilder(kit, frameOwner, graph);
            graph.start().setStateAfter(frameStateBuilder.create(bci, graph.start()));
            ValueNode[] handlerArguments = createHandlerArguments(handlerConfig, threadingExitMethod, kit, stubParameters, templateIndex);
            invokeThreadingExit(kit, threadingExitMethod, frameStateBuilder, bci, handlerArguments);
            ValueNode[] currentStubArguments = loadCurrentStubArguments(handlerConfig, kit, stubParameters, handlerArguments, returnResult);
            kit.append(new ReturnNode(createStubReturn(handlerConfig, kit, returnResult, null, currentStubArguments)));
        } else {
            MultiReturnNode multiReturnNode = kit.unique(new MultiReturnNode(returnResult, null));
            multiReturnNode.getAdditionalReturnResults().addAll(Arrays.asList(stubParameters));
            kit.append(new ReturnNode(multiReturnNode));
        }
        graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, "Initial graph for default bytecode handler stub");
        return graph;
    }
}
