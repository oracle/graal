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
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MultiReturnNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReadArgumentNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.FieldAliasNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.replacements.GraphKit;
import jdk.graal.compiler.truffle.nodes.TruffleBytecodeHandlerDispatchAddressNode;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Represents a call site for a Truffle bytecode handler, describing the target method, its
 * arguments, return type, and context within the enclosing method. This class is responsible for
 * managing argument transformations for Truffle interpreter handler methods. It also provides logic
 * to generate stubs for Truffle bytecode handlers, determine calling/register conventions, and
 * manage return/argument transformations between the caller and callee.
 * <p>
 * Construction and use of this class is based on detection of specific annotations (such as
 * {@code BytecodeInterpreterSwitch} and {@code BytecodeInterpreterHandler}) on methods and types.
 */
public final class TruffleBytecodeHandlerCallsite {

    /**
     * Provides metadata and mapping information about arguments to the handler stub, including
     * source type, index (in both stub and original method parameter list), expansion state, field
     * association, and mutability state.
     */
    public record ArgumentInfo(ResolvedJavaType type,
                    int index,
                    int originalIndex,
                    boolean copyFromReturn,
                    boolean isOwner,
                    boolean isExpanded,
                    ResolvedJavaType ownerType, // valid iff isExpanded is true
                    ResolvedJavaField field, // valid iff isExpanded is true
                    boolean isOwnerVirtual, // valid iff isExpanded is true
                    boolean isImmutable,
                    boolean nonNull) {
    }

    /**
     * Holds the annotation types used by Truffle interpreters.
     */
    public record TruffleBytecodeHandlerTypes(ResolvedJavaType typeBytecodeInterpreterSwitch,
                    ResolvedJavaType typeBytecodeInterpreterHandlerConfig,
                    ResolvedJavaType typeBytecodeInterpreterHandler,
                    ResolvedJavaType typeBytecodeInterpreterFetchOpcode) {
    }

    private final ResolvedJavaMethod enclosingMethod;
    private final int bci;
    private final ResolvedJavaMethod targetMethod;
    private final ResolvedJavaType returnType;
    private final List<ArgumentInfo> argumentInfos;
    private final List<ResolvedJavaType> argumentTypes;

    public TruffleBytecodeHandlerCallsite(ResolvedJavaMethod enclosingMethod, int bci, ResolvedJavaMethod targetMethod, TruffleBytecodeHandlerTypes truffleTypes) {
        GraalError.guarantee(AnnotationValueSupport.isAnnotationPresent(truffleTypes.typeBytecodeInterpreterSwitch, enclosingMethod),
                        "Enclosing method %s is not annotated by @BytecodeInterpreterSwitch", enclosingMethod.format("%H.%n(%p)"));
        this.enclosingMethod = enclosingMethod;
        this.bci = bci;

        GraalError.guarantee(AnnotationValueSupport.isAnnotationPresent(truffleTypes.typeBytecodeInterpreterHandler, targetMethod),
                        "Target method %s is not annotated by @BytecodeInterpreterHandler", targetMethod.format("%H.%n(%p)"));
        this.targetMethod = targetMethod;

        AnnotationValue configAnnotation = AnnotationValueSupport.getDeclaredAnnotationValue(truffleTypes.typeBytecodeInterpreterHandlerConfig, enclosingMethod);
        List<?> argumentConfigs = configAnnotation.get("arguments", List.class);

        ResolvedJavaType enclosingClass = targetMethod.getDeclaringClass();
        Signature signature = targetMethod.getSignature();
        this.returnType = signature.getReturnType(enclosingClass).resolve(enclosingClass);

        List<ArgumentInfo> localArguments = new ArrayList<>();
        int originalIndex = 0;
        int currentIndex = 0;
        if (!targetMethod.isStatic()) {
            AnnotationValue receiverConfig = (AnnotationValue) argumentConfigs.get(originalIndex);
            boolean nonNull = receiverConfig.getBoolean("nonNull");

            String name = receiverConfig.getEnum("expand").name;
            switch (name) {
                case "NONE" -> localArguments.add(new ArgumentInfo(enclosingClass, currentIndex++, originalIndex, false, false, false, null, null, false, true, nonNull));
                case "VIRTUAL" -> throw GraalError.shouldNotReachHere("Receiver cannot be VIRTUAL");
                case "MATERIALIZED" -> {
                    // @Argument(expand = MATERIALIZED, fields = {...})
                    localArguments.add(new ArgumentInfo(enclosingClass, currentIndex++, originalIndex, false, true, false, null, null, false, true, nonNull));
                    List<?> fields = receiverConfig.get("fields", List.class);
                    for (ResolvedJavaField javaField : enclosingClass.getInstanceFields(true)) {
                        for (Object field : fields) {
                            AnnotationValue fieldConfig = (AnnotationValue) field;
                            if (javaField.getName().equals(fieldConfig.getString("name"))) {
                                ResolvedJavaType fieldType = javaField.getType().resolve(enclosingClass);
                                localArguments.add(new ArgumentInfo(fieldType, currentIndex++, originalIndex, false, false, true, enclosingClass, javaField, false, javaField.isFinal(),
                                                !fieldType.isPrimitive() && fieldConfig.getBoolean("nonNull")));
                                break;
                            }
                        }
                    }
                }
                default -> throw GraalError.shouldNotReachHere("Unknown expansion kind " + name);
            }
            originalIndex++;
        }

        final int length = signature.getParameterCount(false);
        for (int i = 0; i < length; i++, originalIndex++) {
            ResolvedJavaType parameterType = signature.getParameterType(i, enclosingClass).resolve(enclosingClass);
            AnnotationValue parameterConfig = (AnnotationValue) argumentConfigs.get(originalIndex);
            boolean copyFromReturn = parameterConfig.getBoolean("returnValue");
            boolean nonNull = !parameterType.isPrimitive() && parameterConfig.getBoolean("nonNull");

            String name = parameterConfig.getEnum("expand").name;
            switch (name) {
                case "NONE" -> localArguments.add(new ArgumentInfo(parameterType, currentIndex++, originalIndex, copyFromReturn, false, false, null, null, false, !copyFromReturn, nonNull));
                case "VIRTUAL" -> {
                    // @Argument(expand = VIRTUAL, fields = {...})
                    List<?> fields = parameterConfig.get("fields", List.class);

                    for (ResolvedJavaField javaField : parameterType.getInstanceFields(true)) {
                        ResolvedJavaType fieldType = javaField.getType().resolve(enclosingClass);
                        boolean fieldNonNull = false;
                        if (!fieldType.isPrimitive()) {
                            for (Object field : fields) {
                                AnnotationValue fieldConfig = (AnnotationValue) field;
                                if (javaField.getName().equals(fieldConfig.getString("name"))) {
                                    fieldNonNull = fieldConfig.getBoolean("nonNull");
                                    break;
                                }
                            }
                        }
                        localArguments.add(new ArgumentInfo(fieldType, currentIndex++, originalIndex, false, false, true, parameterType, javaField, true, javaField.isFinal(), fieldNonNull));
                    }
                }
                case "MATERIALIZED" -> {
                    // @Argument(expand = MATERIALIZED, fields = {...})
                    localArguments.add(new ArgumentInfo(parameterType, currentIndex++, originalIndex, copyFromReturn, true, false, null, null, false, true, nonNull));
                    List<?> fields = parameterConfig.get("fields", List.class);

                    for (ResolvedJavaField javaField : parameterType.getInstanceFields(true)) {
                        for (Object field : fields) {
                            AnnotationValue fieldConfig = (AnnotationValue) field;
                            if (javaField.getName().equals(fieldConfig.getString("name"))) {
                                ResolvedJavaType fieldType = javaField.getType().resolve(enclosingClass);
                                localArguments.add(new ArgumentInfo(fieldType, currentIndex++, originalIndex, false, false, true, enclosingClass, javaField, false, javaField.isFinal(),
                                                !fieldType.isPrimitive() && fieldConfig.getBoolean("nonNull")));
                            }
                        }
                    }
                }
                default -> throw GraalError.shouldNotReachHere("Unknown expansion kind " + name);
            }
        }
        this.argumentInfos = Collections.unmodifiableList(localArguments);
        this.argumentTypes = argumentInfos.stream().map(ArgumentInfo::type).toList();

        assert verifyArguments(argumentInfos);
    }

    /**
     * Verifies that the given {@code arguments} are sorted by their index and that there is at most
     * one {@link ArgumentInfo} with {@link ArgumentInfo#copyFromReturn} set to {@code true}.
     */
    private static boolean verifyArguments(List<ArgumentInfo> arguments) {
        boolean copyFromReturn = false;
        for (int i = 0; i < arguments.size(); i++) {
            ArgumentInfo argumentInfo = arguments.get(i);
            assert argumentInfo.index == i : Assertions.errorMessage("Unaligned argument", argumentInfo);
            assert !(argumentInfo.copyFromReturn && copyFromReturn) : Assertions.errorMessage("Multiple arguments with returnValue set to true", argumentInfo);
            copyFromReturn |= argumentInfo.copyFromReturn;
        }
        return true;
    }

    public List<ResolvedJavaType> getArgumentTypes() {
        return argumentTypes;
    }

    public ResolvedJavaType getReturnType() {
        return returnType;
    }

    public boolean isArgumentImmutable(int index) {
        return argumentInfos.get(index).isImmutable;
    }

    public ResolvedJavaMethod getEnclosingMethod() {
        return enclosingMethod;
    }

    public int getBci() {
        return bci;
    }

    public ResolvedJavaMethod getTargetMethod() {
        return targetMethod;
    }

    public List<ArgumentInfo> getArgumentInfos() {
        return argumentInfos;
    }

    public String getStubName() {
        return "__stub_" + targetMethod.getName();
    }

    /**
     * Creates (if not yet) and returns {@link ParameterNode}s for the arguments described by
     * {@link #argumentInfos}.
     */
    private ParameterNode[] collectParameterNodes(GraphKit kit) {
        ParameterNode[] parameterNodes = new ParameterNode[argumentInfos.size()];

        for (ArgumentInfo argumentInfo : argumentInfos) {
            // Use unique with original stamp to find an existing ParameterNode if any
            ParameterNode parameterNode = kit.unique(new ParameterNode(argumentInfo.index, StampFactory.forDeclaredType(kit.getAssumptions(), argumentInfo.type, false)));
            if (argumentInfo.nonNull && parameterNode.stamp(NodeView.DEFAULT).isObjectStamp()) {
                parameterNode.setStamp(((AbstractObjectStamp) parameterNode.stamp(NodeView.DEFAULT)).asNonNull());
            }
            parameterNodes[argumentInfo.index] = parameterNode;
        }

        return parameterNodes;
    }

    /**
     * Constructs the argument nodes required for invoking the {@link #targetMethod} within the
     * generated stub.
     * <p>
     * For parameters whose configurations are with {@code ExpansionKind.VIRTUAL}, their original
     * object arguments undergo scalar replacement. In such cases, new instances representing these
     * virtualized arguments must be recreated prior to invocation.
     * <p>
     * For parameters whose configurations are with {@code ExpansionKind.MATERIALIZED}, their
     * original object arguments are partially expanded. The necessary aliasing relationships
     * between the original object and its expanded fields are restored using
     * {@link FieldAliasNode}.
     */
    private ValueNode[] createCalleeArguments(GraphKit kit, ParameterNode[] parameterNodes) {
        ArrayList<ValueNode> argumentNodes = new ArrayList<>();

        AllocatedObjectNode[] allocatedObjects = new AllocatedObjectNode[targetMethod.getSignature().getParameterCount(targetMethod.hasReceiver())];
        EconomicMap<AllocatedObjectNode, List<ValueNode>> virtualFields = EconomicMap.create();

        for (ArgumentInfo argumentInfo : argumentInfos) {
            ParameterNode parameterNode = parameterNodes[argumentInfo.index];
            if (argumentInfo.isExpanded) {
                int index = argumentInfo.originalIndex;
                if (argumentInfo.isOwnerVirtual) {
                    AllocatedObjectNode allocatedObj = allocatedObjects[index];
                    if (allocatedObj == null) {
                        // Instantiate once for all scalar replaced fields of the same owner
                        VirtualInstanceNode virtualObj = kit.add(new VirtualInstanceNode(argumentInfo.ownerType, true));
                        allocatedObj = kit.unique(new AllocatedObjectNode(virtualObj));

                        allocatedObjects[index] = allocatedObj;
                        virtualFields.put(allocatedObj, new ArrayList<>());

                        argumentNodes.add(allocatedObj);
                    }
                    virtualFields.get(allocatedObj).add(parameterNode);
                } else {
                    // During construction, the MATERIALIZED owner is processed before the expanded
                    // fields
                    ValueNode owner = argumentNodes.getLast();
                    kit.append(new FieldAliasNode(owner, argumentInfo.field, parameterNode));
                }
            } else {
                argumentNodes.add(parameterNode);
            }
        }

        // Recreates the scalar replaced objects
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

    /**
     * Updates argument configured with {@code returnValue=true} to the original handler result.
     */
    private ValueNode[] updateArguments(ValueNode result, ValueNode[] argumentsToOriginalHandler) {
        ValueNode[] updatedArguments = argumentsToOriginalHandler.clone();
        for (ArgumentInfo argumentInfo : argumentInfos) {
            if (argumentInfo.copyFromReturn) {
                updatedArguments[argumentInfo.originalIndex] = result;
            }
        }
        return updatedArguments;
    }

    private ValueNode appendInvoke(ResolvedJavaMethod method, ValueNode[] inputs, FrameStateBuilder frameStateBuilder, GraphKit kit) {
        CallTargetNode.InvokeKind invokeKind = method.isStatic() ? CallTargetNode.InvokeKind.Static : CallTargetNode.InvokeKind.Special;
        ValueNode invoke = kit.createInvokeWithExceptionAndUnwind(method, invokeKind, frameStateBuilder, bci, inputs);
        return returnType.getJavaKind() == JavaKind.Void ? null : invoke;
    }

    /**
     * Constructs and returns a {@link MultiReturnNode} representing the updated arguments to a
     * Truffle bytecode handler stub.
     */
    private MultiReturnNode createStubReturn(GraphKit kit, ParameterNode[] parameterNodes, ValueNode result, ValueNode tailCallTarget, ValueNode[] argumentsToOriginalHandler) {
        MultiReturnNode multiReturnNode = kit.unique(new MultiReturnNode(result, tailCallTarget));
        List<ValueNode> additionalReturnResults = multiReturnNode.getAdditionalReturnResults();

        // Returns the updated arguments in the same order as the stub parameters
        for (ArgumentInfo argumentInfo : argumentInfos) {
            if (argumentInfo.isExpanded) {
                if (argumentInfo.isImmutable) {
                    additionalReturnResults.add(parameterNodes[argumentInfo.index]);
                } else {
                    ValueNode owner = argumentsToOriginalHandler[argumentInfo.originalIndex];
                    LoadFieldNode load = kit.append(LoadFieldNode.create(kit.getAssumptions(), owner, argumentInfo.field));
                    additionalReturnResults.add(load);
                }
            } else {
                if (argumentInfo.copyFromReturn) {
                    GraalError.guarantee(result != null, "copying from Void");
                    additionalReturnResults.add(result);
                } else {
                    additionalReturnResults.add(argumentsToOriginalHandler[argumentInfo.originalIndex]);
                }
            }
            if (tailCallTarget != null && argumentInfo.nonNull && !argumentInfo.type.isPrimitive()) {
                LogicNode isNull = kit.unique(IsNullNode.create(additionalReturnResults.getLast()));
                kit.append(new FixedGuardNode(isNull, DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, true));
            }
        }
        return multiReturnNode;
    }

    /**
     * Generates a stub that expands the handler's input parameters as required, invokes the
     * handler, and returns all updated parameter values. The returned values are stored in the same
     * register locations as the input parameters.
     * <p>
     * For more details, see the <a href=
     * "https://github.com/oracle/graal/blob/master/truffle/docs/OneCompilationPerBytecodeHandler.md">
     * One Compilation per Bytecode Handler documentation</a>.
     */
    public StructuredGraph createStub(GraphKit kit, ResolvedJavaMethod frameOwner, boolean threading, ResolvedJavaMethod nextOpcodeMethod, Supplier<Object> bytecodeHandlerTableSupplier) {
        StructuredGraph graph = kit.getGraph();
        FrameStateBuilder frameStateBuilder = new FrameStateBuilder(kit, frameOwner, graph);
        graph.start().setStateAfter(frameStateBuilder.create(bci, graph.start()));

        graph.getGraphState().forceDisableFrameStateVerification();

        ParameterNode[] parameterNodes = collectParameterNodes(kit);
        // Invoke original handler
        ValueNode[] argumentsToOriginalHandler = createCalleeArguments(kit, parameterNodes);
        ValueNode handlerResult = appendInvoke(targetMethod, argumentsToOriginalHandler, frameStateBuilder, kit);

        TruffleBytecodeHandlerDispatchAddressNode tailCallTarget = null;
        if (threading) {
            // Invoke nextOpcodeMethod
            ValueNode[] updatedArguments = updateArguments(handlerResult, argumentsToOriginalHandler);
            ValueNode nextOpcode = appendInvoke(nextOpcodeMethod, updatedArguments, frameStateBuilder, kit);
            tailCallTarget = kit.append(new TruffleBytecodeHandlerDispatchAddressNode(nextOpcode, bytecodeHandlerTableSupplier));
        }

        kit.append(new ReturnNode(createStubReturn(kit, parameterNodes, handlerResult, tailCallTarget, argumentsToOriginalHandler)));
        graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, "Initial graph for bytecode handler stub");
        return graph;
    }

    /**
     * Constructs the expanded list of caller argument {@link ValueNode}s for invoking a Truffle
     * bytecode handler stub.
     */
    public ValueNode[] createCallerArguments(ValueNode[] oldArguments, FixedNode insertBefore, Function<ResolvedJavaField, ResolvedJavaField> fieldMap) {
        List<ValueNode> newArguments = new ArrayList<>();
        StructuredGraph graph = insertBefore.graph();

        for (ArgumentInfo argumentInfo : argumentInfos) {
            if (argumentInfo.isExpanded) {
                ValueNode owner = oldArguments[argumentInfo.originalIndex];
                LoadFieldNode load = LoadFieldNode.create(graph.getAssumptions(), owner, fieldMap.apply(argumentInfo.field));
                graph.addBeforeFixed(insertBefore, graph.add(load));
                newArguments.add(load);
            } else {
                newArguments.add(oldArguments[argumentInfo.originalIndex]);
            }
            if (argumentInfo.nonNull && !argumentInfo.type.isPrimitive()) {
                LogicNode isNull = graph.addOrUnique(IsNullNode.create(newArguments.getLast()));
                graph.addBeforeFixed(insertBefore, graph.add(new FixedGuardNode(isNull, DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, true)));
            }
        }
        return newArguments.toArray(ValueNode.EMPTY_ARRAY);
    }

    /**
     * Updates mutable expanded arguments in the caller frame with new return values produced by the
     * Truffle handler stub.
     */
    public void updateCallerReturns(FixedNode newInvoke, ValueNode[] oldArguments, FixedNode insertBefore, Function<ResolvedJavaField, ResolvedJavaField> fieldMap) {
        StructuredGraph graph = insertBefore.graph();

        for (ArgumentInfo argumentInfo : argumentInfos) {
            if (argumentInfo.isExpanded && !argumentInfo.isImmutable) {
                ReadArgumentNode fetchReturn = graph.unique(new ReadArgumentNode(newInvoke, argumentInfo.type.getJavaKind(), argumentInfo.index));
                ValueNode owner = oldArguments[argumentInfo.originalIndex];
                StoreFieldNode writeback = new StoreFieldNode(owner, fieldMap.apply(argumentInfo.field), fetchReturn);
                graph.addBeforeFixed(insertBefore, graph.add(writeback));
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TruffleBytecodeHandlerCallsite other) {
            return enclosingMethod.equals(other.enclosingMethod) && bci == other.bci && targetMethod.equals(other.targetMethod) && returnType.equals(other.returnType) &&
                            argumentInfos.equals(other.argumentInfos) && argumentTypes.equals(other.argumentTypes);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enclosingMethod, bci, targetMethod, returnType, argumentInfos, argumentTypes);
    }
}
