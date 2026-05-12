/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.lang.reflect.Field;
import java.util.List;

import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.graal.code.PendingExceptionStateHolder;
import com.oracle.svm.core.graal.code.PendingExceptionStateSupport;
import com.oracle.svm.core.graal.nodes.ReadReservedRegisterFloatingNode;
import com.oracle.svm.core.graal.thread.LoadVMThreadLocalNode;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;
import com.oracle.svm.shared.util.ReflectionUtil;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.debug.SlowPathBeginNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.extended.JavaWriteNode;
import jdk.graal.compiler.nodes.extended.PendingExceptionStateValueNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig.ArgumentInfo;
import jdk.graal.compiler.replacements.GraphKit;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Maintains bytecode-handler argument state for generated threaded stubs that unwind by throwing.
 * Threaded stubs pass the next handler arguments through the stub ABI. A normal return lets the
 * caller materialize those updates from the stub's multi-return payload, but an unwind bypasses that
 * path. The caller's exception edge still has to observe the arguments that were current at the
 * throwing handler call.
 * <p>
 * The callee side publishes the current stub ABI values, indexed by ABI argument slot, into a
 * per-thread {@link PendingExceptionStateHolder} immediately before rethrowing. The caller side
 * consumes that holder only on exception paths: direct generated-stub exception edges write back
 * mutable virtual-expanded fields, and parser-inserted {@link PendingExceptionStateValueNode}s
 * represent {@code copyFromReturn} local updates until the outline phase can decide whether a
 * particular throwing predecessor is a generated stub. Object slots are cleared after consumption so
 * the thread-local holder does not retain object graphs beyond the exception edge that needed them.
 * <pre>{@code
 * // Callee-side generated stub.
 * try {
 *     invokeOriginalHandler(...);
 * } catch (Throwable e) {
 *     // instrumentation begin
 *     for (ArgumentInfo argument : virtualExpandedMutableFields(handlerConfig)) {
 *         threadLocalHolder[argument.index()] = currentStubAbiValue(argument.index());
 *     }
 *     if (copyFromReturnArgument != null) {
 *         threadLocalHolder[copyFromReturnArgument.index()] =
 *                         currentStubAbiValue(copyFromReturnArgument.index());
 *     }
 *     // instrumentation end
 *     throw e;
 * }
 *
 * // Caller-side exception edge for a direct generated-stub invoke.
 * catch (Throwable e) {
 *     // instrumentation begin
 *     for (ArgumentInfo argument : virtualExpandedMutableFields(handlerConfig)) {
 *         virtualExpandedMutableField(argument) = threadLocalHolder[argument.index()];
 *     }
 *     // instrumentation end
 *     throw e;
 * }
 * }</pre>
 * <p>
 * Exception dispatch for a {@code copyFromReturn} local inserts a
 * {@link PendingExceptionStateValueNode} during parsing. This phase later replaces that node with a
 * {@code threadLocalHolder} read when the throwing predecessor is a generated stub, or with the
 * original input value for ordinary Java invokes.
 */
final class SubstrateBytecodeHandlerUnwindPath {

    private static final Field OBJECT_SLOTS_FIELD = ReflectionUtil.lookupField(PendingExceptionStateHolder.class, "objectSlots");
    private static final Field PRIMITIVE_SLOTS_FIELD = ReflectionUtil.lookupField(PendingExceptionStateHolder.class, "primitiveSlots");

    private record PendingStateRead(ValueNode value, FixedWithNextNode last) {
    }

    private SubstrateBytecodeHandlerUnwindPath() {
    }

    /**
     * Emits the callee-side pending-state writes on the handler invoke's exception path. The writes
     * snapshot values by stub ABI argument index so caller-side exception edges can recover them
     * without depending on a Java return from the throwing handler.
     */
    static void writeOnCallee(BytecodeHandlerConfig handlerConfig, GraphKit kit, ValueNode[] exceptionPathValues) {
        if (!handlerConfig.hasPendingExceptionState()) {
            return;
        }
        kit.append(new SlowPathBeginNode());

        StructuredGraph graph = kit.getGraph();
        MetaAccessProvider metaAccess = kit.getMetaAccess();
        ResolvedJavaField objectSlotsField = metaAccess.lookupJavaField(OBJECT_SLOTS_FIELD);
        ResolvedJavaField primitiveSlotsField = metaAccess.lookupJavaField(PRIMITIVE_SLOTS_FIELD);
        long objectArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Object);
        int objectArrayIndexScale = metaAccess.getArrayIndexScale(JavaKind.Object);
        long primitiveArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Long);
        int primitiveArrayIndexScale = metaAccess.getArrayIndexScale(JavaKind.Long);
        FrameState stateAfter = kit.exceptionObject().stateAfter().duplicate();

        ValueNode threadNode = graph.addOrUniqueWithInputs(new ReadReservedRegisterFloatingNode(ReservedRegisters.singleton().getThreadRegister()));
        ValueNode holder = kit.append(createLoadPendingExceptionStateHolder(metaAccess, threadNode));

        ValueNode objectSlots = null;
        ValueNode primitiveSlots = null;
        /* Publish only slots that caller-side exception dispatch may observe after an unwind. */
        for (ArgumentInfo argumentInfo : handlerConfig.getArgumentInfos()) {
            if (!argumentInfo.needsPendingExceptionState()) {
                continue;
            }
            int slotIndex = argumentInfo.index();
            JavaKind kind = argumentInfo.type().getJavaKind();
            ValueNode value = exceptionPathValues[slotIndex];
            GraalError.guarantee(value != null, "Missing pending exception state value for %s", argumentInfo);
            if (kind == JavaKind.Object) {
                if (objectSlots == null) {
                    objectSlots = kit.append(LoadFieldNode.create(kit.getAssumptions(), holder, objectSlotsField));
                }
                JavaWriteNode store = new JavaWriteNode(JavaKind.Object,
                                elementAddress(graph, objectSlots, objectArrayBaseOffset + (long) slotIndex * objectArrayIndexScale),
                                NamedLocationIdentity.getArrayLocation(JavaKind.Object),
                                value,
                                BarrierType.ARRAY,
                                true,
                                true,
                                MemoryOrderMode.PLAIN);
                kit.append(store);
                store.setStateAfter(stateAfter);
            } else {
                if (primitiveSlots == null) {
                    primitiveSlots = kit.append(LoadFieldNode.create(kit.getAssumptions(), holder, primitiveSlotsField));
                }
                JavaWriteNode store = new JavaWriteNode(JavaKind.Long,
                                elementAddress(graph, primitiveSlots, primitiveArrayBaseOffset + (long) slotIndex * primitiveArrayIndexScale),
                                NamedLocationIdentity.getArrayLocation(JavaKind.Long),
                                encodePrimitiveValue(graph, value, kind),
                                BarrierType.NONE,
                                false,
                                true,
                                MemoryOrderMode.PLAIN);
                kit.append(store);
                store.setStateAfter(stateAfter);
            }
        }
    }

    /**
     * Emits caller-side reads for mutable virtual-expanded fields on a generated-stub invoke's
     * exception edge. This is exception-path-only; normal returns use the stub's multi-return
     * payload. {@code copyFromReturn} locals are handled separately by processing
     * {@link PendingExceptionStateValueNode}s inserted while parsing exception dispatch.
     */
    static void readOnCaller(MetaAccessProvider metaAccess, BytecodeHandlerConfig handlerConfig, InvokeWithExceptionNode invoke, ValueNode[] arguments) {
        if (!handlerConfig.hasPendingExceptionState()) {
            return;
        }

        ExceptionObjectNode exceptionObject = (ExceptionObjectNode) invoke.exceptionEdge();
        StructuredGraph graph = exceptionObject.graph();
        FixedWithNextNode insertAfter = exceptionObject;
        FrameState stateAfter = exceptionObject.stateAfter().duplicate();
        ResolvedJavaField objectSlotsField = metaAccess.lookupJavaField(OBJECT_SLOTS_FIELD);
        ResolvedJavaField primitiveSlotsField = metaAccess.lookupJavaField(PRIMITIVE_SLOTS_FIELD);

        LoadVMThreadLocalNode holder = null;
        ValueNode objectSlots = null;
        ValueNode primitiveSlots = null;
        /* Recover published values and write mutable expanded fields back on the exception edge. */
        for (ArgumentInfo argumentInfo : handlerConfig.getArgumentInfos()) {
            if (!argumentInfo.needsPendingExceptionState()) {
                continue;
            }
            if (argumentInfo.copyFromReturn()) {
                continue;
            }
            ValueNode originalValue = arguments[argumentInfo.index()];
            int slotIndex = argumentInfo.index();
            JavaKind kind = argumentInfo.type().getJavaKind();
            if (holder == null) {
                ValueNode threadNode = graph.addOrUniqueWithInputs(new ReadReservedRegisterFloatingNode(ReservedRegisters.singleton().getThreadRegister()));
                holder = graph.add(createLoadPendingExceptionStateHolder(metaAccess, threadNode));
                graph.addAfterFixed(insertAfter, holder);
                insertAfter = holder;
            }
            ValueNode preservedValue;
            if (kind == JavaKind.Object) {
                if (objectSlots == null) {
                    LoadFieldNode loadObjectSlots = graph.add(LoadFieldNode.create(graph.getAssumptions(), holder, objectSlotsField));
                    graph.addAfterFixed(insertAfter, loadObjectSlots);
                    insertAfter = loadObjectSlots;
                    objectSlots = loadObjectSlots;
                }
                PendingStateRead read = readObjectPendingStateSlot(metaAccess, graph, insertAfter, objectSlots, slotIndex, originalValue, stateAfter);
                insertAfter = read.last();
                preservedValue = read.value();
            } else {
                if (primitiveSlots == null) {
                    LoadFieldNode loadPrimitiveSlots = graph.add(LoadFieldNode.create(graph.getAssumptions(), holder, primitiveSlotsField));
                    graph.addAfterFixed(insertAfter, loadPrimitiveSlots);
                    insertAfter = loadPrimitiveSlots;
                    primitiveSlots = loadPrimitiveSlots;
                }
                PendingStateRead read = readPrimitivePendingStateSlot(metaAccess, graph, insertAfter, primitiveSlots, slotIndex, kind, stateAfter);
                insertAfter = read.last();
                preservedValue = read.value();
            }
            GraalError.guarantee(originalValue instanceof LoadFieldNode, "Expected expanded argument %s to be loaded from a field", argumentInfo);
            LoadFieldNode originalLoad = (LoadFieldNode) originalValue;
            StoreFieldNode writeBack = graph.add(new StoreFieldNode(originalLoad.object(), originalLoad.field(), preservedValue));
            graph.addAfterFixed(insertAfter, writeBack);
            writeBack.setStateAfter(stateAfter);
            insertAfter = writeBack;
        }
    }

    static void processPendingExceptionStateValues(MetaAccessProvider metaAccess, StructuredGraph graph) {
        List<PendingExceptionStateValueNode> pendingExceptionValues = graph.getNodes().filter(PendingExceptionStateValueNode.class).snapshot();
        for (PendingExceptionStateValueNode pendingExceptionValue : pendingExceptionValues) {
            if (pendingExceptionValue.isAlive()) {
                switch (pendingExceptionValue.source()) {
                    case STUB -> replaceWithPendingStateRead(metaAccess, pendingExceptionValue);
                    case INFER -> processInferredPendingExceptionStateValue(metaAccess, pendingExceptionValue);
                }
            }
        }
    }

    private static void replaceWithPendingStateRead(MetaAccessProvider metaAccess, PendingExceptionStateValueNode pendingExceptionValue) {
        if (pendingExceptionValue.hasUsages()) {
            ValueNode replacement = insertPendingStateReadAfter(metaAccess, pendingExceptionValue, pendingExceptionValue);
            pendingExceptionValue.replaceAtUsages(replacement, InputType.Value);
        } else if (pendingExceptionValue.kind() == JavaKind.Object) {
            /*
             * The callee-side stub may have published an object even when later liveness removes all
             * exception-path uses of the parser proxy. Clear that thread-local slot anyway so the
             * holder does not retain the object graph until another exception overwrites it.
             */
            clearObjectPendingStateSlot(metaAccess, pendingExceptionValue, pendingExceptionValue.slotIndex());
        }
        GraalError.guarantee(pendingExceptionValue.hasNoUsages(), "Unexpected non-value usages of %s", pendingExceptionValue);
        pendingExceptionValue.graph().removeFixed(pendingExceptionValue);
    }

    /*
     * Inferred nodes come from switch-extension calls. After inlining they can sit after a merge
     * whose predecessors are a mix of generated stubs and ordinary Java calls. Build the final phi
     * at that merge directly: generated-stub exception edges read the thread-local holder, while
     * ordinary Java invoke edges keep the original input value.
     */
    private static void processInferredPendingExceptionStateValueAtMerge(MetaAccessProvider metaAccess,
                    PendingExceptionStateValueNode pendingExceptionValue, AbstractMergeNode merge) {
        if (pendingExceptionValue.hasUsages()) {
            pendingExceptionValue.replaceAtUsages(createInferredPendingExceptionStatePhi(metaAccess, pendingExceptionValue, merge),
                            InputType.Value);
        } else if (pendingExceptionValue.kind() == JavaKind.Object) {
            for (EndNode end : merge.forwardEnds()) {
                processInferredPendingExceptionStatePath(metaAccess, pendingExceptionValue, end, false);
            }
        }
        GraalError.guarantee(pendingExceptionValue.hasNoUsages(), "Unexpected non-value usages of %s", pendingExceptionValue);
        pendingExceptionValue.graph().removeFixed(pendingExceptionValue);
    }

    private static void processInferredPendingExceptionStateValue(MetaAccessProvider metaAccess, PendingExceptionStateValueNode pendingExceptionValue) {
        if (pendingExceptionValue.predecessor() instanceof AbstractMergeNode merge) {
            processInferredPendingExceptionStateValueAtMerge(metaAccess, pendingExceptionValue, merge);
            return;
        }

        if (pendingExceptionValue.hasUsages()) {
            pendingExceptionValue.replaceAtUsages(processInferredPendingExceptionStatePath(metaAccess, pendingExceptionValue, pendingExceptionValue, true),
                            InputType.Value);
        } else if (pendingExceptionValue.kind() == JavaKind.Object) {
            processInferredPendingExceptionStatePath(metaAccess, pendingExceptionValue, pendingExceptionValue, false);
        }
        GraalError.guarantee(pendingExceptionValue.hasNoUsages(), "Unexpected non-value usages of %s", pendingExceptionValue);
        pendingExceptionValue.graph().removeFixed(pendingExceptionValue);
    }

    private static ValuePhiNode createInferredPendingExceptionStatePhi(MetaAccessProvider metaAccess,
                    PendingExceptionStateValueNode pendingExceptionValue, AbstractMergeNode merge) {
        StructuredGraph graph = pendingExceptionValue.graph();
        ValuePhiNode phi = graph.addWithoutUnique(new ValuePhiNode(pendingExceptionValue.stamp(NodeView.DEFAULT).unrestricted(), merge));
        for (EndNode end : merge.forwardEnds()) {
            phi.addInput(processInferredPendingExceptionStatePath(metaAccess, pendingExceptionValue, end, true));
        }
        return phi;
    }

    private static ValueNode processInferredPendingExceptionStatePath(MetaAccessProvider metaAccess,
                    PendingExceptionStateValueNode pendingExceptionValue, Node pathEnd, boolean needsValue) {
        /*
         * Walk backward to the throwing WithExceptionNode. If this path crosses another merge first,
         * resolve the value at that merge and let the current path consume that nested phi.
         */
        Node current = pathEnd;
        while (true) {
            Node predecessor = current.predecessor();
            GraalError.guarantee(predecessor != null, "Could not find throwing predecessor for %s", pendingExceptionValue);
            if (predecessor instanceof AbstractMergeNode merge) {
                if (needsValue) {
                    return createInferredPendingExceptionStatePhi(metaAccess, pendingExceptionValue, merge);
                }
                for (EndNode end : merge.forwardEnds()) {
                    processInferredPendingExceptionStatePath(metaAccess, pendingExceptionValue, end, false);
                }
                return null;
            }
            if (predecessor instanceof WithExceptionNode withException) {
                if (current == withException.exceptionEdge()) {
                    if (withException instanceof InvokeWithExceptionNode invoke) {
                        if (isBytecodeHandlerStubInvoke(invoke)) {
                            FixedWithNextNode insertAfter;
                            if (pathEnd instanceof EndNode end) {
                                Node pathPredecessor = end.predecessor();
                                GraalError.guarantee(pathPredecessor instanceof FixedWithNextNode,
                                                "Expected predecessor of %s to be fixed-with-next: %s", end, pathPredecessor);
                                insertAfter = (FixedWithNextNode) pathPredecessor;
                            } else {
                                GraalError.guarantee(pathEnd instanceof FixedWithNextNode, "Expected fixed path end for %s: %s",
                                                pendingExceptionValue, pathEnd);
                                insertAfter = (FixedWithNextNode) pathEnd;
                            }
                            if (needsValue) {
                                return insertPendingStateReadAfter(metaAccess, pendingExceptionValue, insertAfter);
                            } else if (pendingExceptionValue.kind() == JavaKind.Object) {
                                /*
                                 * The callee-side stub may have published an object even when later
                                 * liveness removes all exception-path uses of the parser proxy. Clear
                                 * that thread-local slot anyway so the holder does not retain the
                                 * object graph until another exception overwrites it.
                                 */
                                clearObjectPendingStateSlot(metaAccess, insertAfter, pendingExceptionValue.slotIndex());
                            }
                            return null;
                        }
                    }
                    return needsValue ? pendingExceptionValue.value() : null;
                }
                GraalError.guarantee(current == withException.next(), "Expected %s to be a successor of %s", current, withException);
            }
            current = predecessor;
        }
    }

    private static ValueNode insertPendingStateReadAfter(MetaAccessProvider metaAccess, PendingExceptionStateValueNode pendingExceptionValue,
                    FixedWithNextNode insertAfter) {
        StructuredGraph graph = pendingExceptionValue.graph();
        int slotIndex = pendingExceptionValue.slotIndex();
        JavaKind kind = pendingExceptionValue.kind();

        ValueNode threadNode = graph.addOrUniqueWithInputs(new ReadReservedRegisterFloatingNode(ReservedRegisters.singleton().getThreadRegister()));
        LoadVMThreadLocalNode holder = graph.add(createLoadPendingExceptionStateHolder(metaAccess, threadNode));
        graph.addAfterFixed(insertAfter, holder);
        FixedWithNextNode last = holder;

        /* Append the slot-array load, then the typed slot read, on this exception path. */
        if (kind == JavaKind.Object) {
            ResolvedJavaField objectSlotsField = metaAccess.lookupJavaField(OBJECT_SLOTS_FIELD);

            LoadFieldNode objectSlots = graph.add(LoadFieldNode.create(graph.getAssumptions(), holder, objectSlotsField));
            graph.addAfterFixed(last, objectSlots);
            last = objectSlots;

            return readObjectPendingStateSlot(metaAccess, graph, last, objectSlots, slotIndex, pendingExceptionValue, null).value();
        }

        ResolvedJavaField primitiveSlotsField = metaAccess.lookupJavaField(PRIMITIVE_SLOTS_FIELD);

        LoadFieldNode primitiveSlots = graph.add(LoadFieldNode.create(graph.getAssumptions(), holder, primitiveSlotsField));
        graph.addAfterFixed(last, primitiveSlots);
        last = primitiveSlots;

        return readPrimitivePendingStateSlot(metaAccess, graph, last, primitiveSlots, slotIndex, kind, null).value();
    }

    private static PendingStateRead readObjectPendingStateSlot(MetaAccessProvider metaAccess, StructuredGraph graph, FixedWithNextNode insertAfter,
                    ValueNode objectSlots, int slotIndex, ValueNode stampSource, FrameState stateAfter) {
        long objectArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Object);
        int objectArrayIndexScale = metaAccess.getArrayIndexScale(JavaKind.Object);
        AddressNode slotAddress = elementAddress(graph, objectSlots, objectArrayBaseOffset + (long) slotIndex * objectArrayIndexScale);
        JavaReadNode read = graph.add(new JavaReadNode(stampSource.stamp(NodeView.DEFAULT),
                        JavaKind.Object,
                        slotAddress,
                        NamedLocationIdentity.getArrayLocation(JavaKind.Object),
                        BarrierType.ARRAY,
                        MemoryOrderMode.PLAIN,
                        true));
        graph.addAfterFixed(insertAfter, read);
        /*
         * Object pending-state slots are thread-local roots. Clear consumed references so they do not
         * keep object graphs live until a later exception overwrites this slot.
         */
        JavaWriteNode clear = graph.add(createClearObjectSlotWrite(metaAccess, graph, slotAddress));
        graph.addAfterFixed(read, clear);
        if (stateAfter != null) {
            clear.setStateAfter(stateAfter);
        }
        return new PendingStateRead(read, clear);
    }

    private static PendingStateRead readPrimitivePendingStateSlot(MetaAccessProvider metaAccess, StructuredGraph graph, FixedWithNextNode insertAfter,
                    ValueNode primitiveSlots, int slotIndex, JavaKind kind, FrameState stateAfter) {
        long primitiveArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Long);
        int primitiveArrayIndexScale = metaAccess.getArrayIndexScale(JavaKind.Long);
        AddressNode slotAddress = elementAddress(graph, primitiveSlots, primitiveArrayBaseOffset + (long) slotIndex * primitiveArrayIndexScale);
        JavaReadNode read = graph.add(new JavaReadNode(JavaKind.Long,
                        slotAddress,
                        NamedLocationIdentity.getArrayLocation(JavaKind.Long),
                        BarrierType.NONE,
                        MemoryOrderMode.PLAIN,
                        false));
        graph.addAfterFixed(insertAfter, read);
        FixedWithNextNode last = read;
        ValueNode replacement = decodePrimitiveValue(graph, read, kind);
        FrameState debugStateAfter = stateAfter;
        if (debugStateAfter == null && useSlotDebugSentinel()) {
            FrameState lastFrameState = GraphUtil.findLastFrameState(last);
            debugStateAfter = lastFrameState == null ? null : lastFrameState.duplicate();
        }
        last = appendPrimitiveSlotDebugWrite(graph, last, debugStateAfter, slotAddress);
        return new PendingStateRead(replacement, last);
    }

    private static boolean isBytecodeHandlerStubInvoke(InvokeWithExceptionNode invoke) {
        return unwrapAll(invoke.callTarget().targetMethod()) instanceof SubstrateBytecodeHandlerStub;
    }

    private static ResolvedJavaMethod unwrapAll(ResolvedJavaMethod method) {
        ResolvedJavaMethod current = method;
        while (current instanceof WrappedJavaMethod wrappedJavaMethod) {
            current = wrappedJavaMethod.getWrapped();
        }
        return current;
    }

    private static void clearObjectPendingStateSlot(MetaAccessProvider metaAccess, FixedWithNextNode insertAfter, int slotIndex) {
        StructuredGraph graph = insertAfter.graph();
        ValueNode threadNode = graph.addOrUniqueWithInputs(new ReadReservedRegisterFloatingNode(ReservedRegisters.singleton().getThreadRegister()));
        LoadVMThreadLocalNode holder = graph.add(createLoadPendingExceptionStateHolder(metaAccess, threadNode));
        graph.addAfterFixed(insertAfter, holder);
        FixedWithNextNode last = holder;

        ResolvedJavaField objectSlotsField = metaAccess.lookupJavaField(OBJECT_SLOTS_FIELD);
        LoadFieldNode objectSlots = graph.add(LoadFieldNode.create(graph.getAssumptions(), holder, objectSlotsField));
        graph.addAfterFixed(last, objectSlots);
        last = objectSlots;

        long objectArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Object);
        int objectArrayIndexScale = metaAccess.getArrayIndexScale(JavaKind.Object);
        AddressNode slotAddress = elementAddress(graph, objectSlots, objectArrayBaseOffset + (long) slotIndex * objectArrayIndexScale);
        JavaWriteNode clear = graph.add(createClearObjectSlotWrite(metaAccess, graph, slotAddress));
        graph.addAfterFixed(last, clear);
    }

    private static JavaWriteNode createClearObjectSlotWrite(MetaAccessProvider metaAccess, StructuredGraph graph, AddressNode slotAddress) {
        return new JavaWriteNode(JavaKind.Object,
                        slotAddress,
                        NamedLocationIdentity.getArrayLocation(JavaKind.Object),
                        clearObjectSlotValue(metaAccess, graph),
                        BarrierType.ARRAY,
                        true,
                        true,
                        MemoryOrderMode.PLAIN);
    }

    private static ValueNode clearObjectSlotValue(MetaAccessProvider metaAccess, StructuredGraph graph) {
        if (useSlotDebugSentinel()) {
            return ConstantNode.forConstant(SubstrateObjectConstant.forObject(PendingExceptionStateSupport.OBJECT_SLOT_SENTINEL), metaAccess, graph);
        }
        return ConstantNode.defaultForKind(JavaKind.Object, graph);
    }

    private static FixedWithNextNode appendPrimitiveSlotDebugWrite(StructuredGraph graph, FixedWithNextNode insertAfter,
                    FrameState stateAfter, AddressNode slotAddress) {
        if (!useSlotDebugSentinel()) {
            return insertAfter;
        }
        JavaWriteNode debugWrite = graph.add(createPrimitiveSlotDebugWrite(graph, slotAddress));
        graph.addAfterFixed(insertAfter, debugWrite);
        if (stateAfter != null) {
            debugWrite.setStateAfter(stateAfter);
        }
        return debugWrite;
    }

    private static JavaWriteNode createPrimitiveSlotDebugWrite(StructuredGraph graph, AddressNode slotAddress) {
        return new JavaWriteNode(JavaKind.Long,
                        slotAddress,
                        NamedLocationIdentity.getArrayLocation(JavaKind.Long),
                        ConstantNode.forLong(PendingExceptionStateSupport.PRIMITIVE_SLOT_SENTINEL, graph),
                        BarrierType.NONE,
                        false,
                        true,
                        MemoryOrderMode.PLAIN);
    }

    private static boolean useSlotDebugSentinel() {
        return BytecodeHandlerFeature.Options.BytecodeHandlerSlotSentinel.getValue();
    }

    /**
     * Builds the address of a pending-state array slot from the array base and pre-scaled byte
     * offset.
     */
    private static AddressNode elementAddress(StructuredGraph graph, ValueNode base, long offset) {
        return graph.unique(new OffsetAddressNode(base, ConstantNode.forLong(offset, graph)));
    }

    /**
     * Loads the per-thread pending-state holder. The holder is assumed non-null for running Java
     * threads because {@link PendingExceptionStateSupport#beforeThreadRun()} installs it before
     * Java code can execute and clears it only after the thread exits.
     */
    private static LoadVMThreadLocalNode createLoadPendingExceptionStateHolder(MetaAccessProvider metaAccess, ValueNode threadNode) {
        VMThreadLocalInfo threadLocalInfo = PendingExceptionStateSupport.singleton().getThreadLocalInfo();
        LoadVMThreadLocalNode holder = new LoadVMThreadLocalNode(metaAccess, threadLocalInfo, threadNode, BarrierType.NONE, MemoryOrderMode.PLAIN);
        /*
         * PendingExceptionStateSupport initializes this holder in beforeThreadRun(), before Java
         * code can execute on the thread, and clears it only after thread exit.
         */
        holder.setStamp(((ObjectStamp) holder.stamp(NodeView.DEFAULT)).asNonNull());
        return holder;
    }

    /**
     * Encodes primitive and floating-point values into the raw long slot representation used by
     * {@link PendingExceptionStateHolder#primitiveSlots}.
     */
    private static ValueNode encodePrimitiveValue(StructuredGraph graph, ValueNode value, JavaKind kind) {
        return switch (kind) {
            case Long -> value;
            case Int -> graph.addOrUniqueWithInputs(ZeroExtendNode.create(value, JavaKind.Long.getBitCount(), NodeView.DEFAULT));
            case Short -> {
                ValueNode narrow = graph.addOrUniqueWithInputs(NarrowNode.create(value, Short.SIZE, NodeView.DEFAULT));
                yield graph.addOrUniqueWithInputs(ZeroExtendNode.create(narrow, JavaKind.Long.getBitCount(), NodeView.DEFAULT));
            }
            case Byte, Boolean -> {
                ValueNode narrow = graph.addOrUniqueWithInputs(NarrowNode.create(value, Byte.SIZE, NodeView.DEFAULT));
                yield graph.addOrUniqueWithInputs(ZeroExtendNode.create(narrow, JavaKind.Long.getBitCount(), NodeView.DEFAULT));
            }
            case Char -> {
                ValueNode narrow = graph.addOrUniqueWithInputs(NarrowNode.create(value, Character.SIZE, NodeView.DEFAULT));
                yield graph.addOrUniqueWithInputs(ZeroExtendNode.create(narrow, JavaKind.Long.getBitCount(), NodeView.DEFAULT));
            }
            case Float -> {
                ValueNode bits = graph.addOrUniqueWithInputs(ReinterpretNode.create(JavaKind.Int, value, NodeView.DEFAULT));
                yield graph.addOrUniqueWithInputs(ZeroExtendNode.create(bits, JavaKind.Long.getBitCount(), NodeView.DEFAULT));
            }
            case Double -> graph.addOrUniqueWithInputs(ReinterpretNode.create(JavaKind.Long, value, NodeView.DEFAULT));
            default -> throw GraalError.shouldNotReachHere("Unsupported pending exception state kind " + kind);
        };
    }

    /**
     * Decodes a raw long slot value back to the Java kind expected by the original stub argument.
     */
    private static ValueNode decodePrimitiveValue(StructuredGraph graph, ValueNode value, JavaKind kind) {
        return switch (kind) {
            case Long -> value;
            case Int -> graph.addOrUniqueWithInputs(NarrowNode.create(value, Integer.SIZE, NodeView.DEFAULT));
            case Short -> {
                ValueNode narrow = graph.addOrUniqueWithInputs(NarrowNode.create(value, Short.SIZE, NodeView.DEFAULT));
                yield graph.addOrUniqueWithInputs(SignExtendNode.create(narrow, Integer.SIZE, NodeView.DEFAULT));
            }
            case Byte -> {
                ValueNode narrow = graph.addOrUniqueWithInputs(NarrowNode.create(value, Byte.SIZE, NodeView.DEFAULT));
                yield graph.addOrUniqueWithInputs(SignExtendNode.create(narrow, Integer.SIZE, NodeView.DEFAULT));
            }
            case Boolean -> {
                ValueNode narrow = graph.addOrUniqueWithInputs(NarrowNode.create(value, Byte.SIZE, NodeView.DEFAULT));
                yield graph.addOrUniqueWithInputs(ZeroExtendNode.create(narrow, Integer.SIZE, NodeView.DEFAULT));
            }
            case Char -> {
                ValueNode narrow = graph.addOrUniqueWithInputs(NarrowNode.create(value, Character.SIZE, NodeView.DEFAULT));
                yield graph.addOrUniqueWithInputs(ZeroExtendNode.create(narrow, Integer.SIZE, NodeView.DEFAULT));
            }
            case Float -> {
                ValueNode narrow = graph.addOrUniqueWithInputs(NarrowNode.create(value, Integer.SIZE, NodeView.DEFAULT));
                yield graph.addOrUniqueWithInputs(ReinterpretNode.create(JavaKind.Float, narrow, NodeView.DEFAULT));
            }
            case Double -> graph.addOrUniqueWithInputs(ReinterpretNode.create(JavaKind.Double, value, NodeView.DEFAULT));
            default -> throw GraalError.shouldNotReachHere("Unsupported pending exception state kind " + kind);
        };
    }
}
