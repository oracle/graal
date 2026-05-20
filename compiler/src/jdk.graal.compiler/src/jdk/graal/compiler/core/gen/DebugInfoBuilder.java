/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.gen;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.ImplicitLIRFrameState;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeValueMap;
import jdk.graal.compiler.nodes.spi.NodeWithState;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.EscapeObjectState;
import jdk.graal.compiler.nodes.virtual.MaterializedObjectState;
import jdk.graal.compiler.nodes.virtual.VirtualBoxingNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectState;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackLockValue;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;

/**
 * Builds {@link LIRFrameState}s from {@link FrameState}s.
 */
public class DebugInfoBuilder {

    protected final NodeValueMap nodeValueMap;
    protected final MetaAccessExtensionProvider metaAccessExtensionProvider;
    protected final DebugContext debug;

    public DebugInfoBuilder(NodeValueMap nodeValueMap, MetaAccessExtensionProvider metaAccessExtensionProvider, DebugContext debug) {
        this.nodeValueMap = nodeValueMap;
        this.metaAccessExtensionProvider = metaAccessExtensionProvider;
        this.debug = debug;
    }

    private static final JavaValue[] NO_JAVA_VALUES = {};
    private static final JavaKind[] NO_JAVA_KINDS = {};

    protected final EconomicMap<VirtualObjectNode, VirtualObject> virtualObjects = EconomicMap.create(Equivalence.IDENTITY);
    protected final EconomicMap<VirtualObjectNode, EscapeObjectState> objectStates = EconomicMap.create(Equivalence.IDENTITY);

    /**
     * Cache of {@link BytecodeFrame} templates keyed by {@link FrameState} identity during a single
     * LIR generation. LIR phases mutate {@link BytecodeFrame#values} arrays in place (e.g. during
     * register allocation), so cached entries are kept as immutable templates and callers must use
     * {@link #copyFrame(BytecodeFrame)} before publishing them into an {@link LIRFrameState}.
     * Entries are restricted to frame-state chains accepted by
     * {@link #canCacheFrameStateChain(FrameState)}.
     */
    private final EconomicMap<FrameState, BytecodeFrame> bytecodeFrameCache = EconomicMap.create(Equivalence.IDENTITY);

    /**
     * Cache of complete frame-state templates keyed by {@link FrameState} identity during a single
     * LIR generation. Unlike {@link #bytecodeFrameCache}, entries include the {@link VirtualObject}
     * array needed by the resulting {@link LIRFrameState}. LIR phases mutate both
     * {@link BytecodeFrame#values} arrays and virtual-object value arrays in place, so cache hits
     * must deep-copy the template before publishing it. When a deep frame-state chain creates
     * virtual objects, templates are recorded for every cacheable suffix so a later state such as
     * {@code A' -> B -> C} can reuse the existing {@code B -> C} template from an earlier
     * {@code A -> B -> C} build.
     */
    private final EconomicMap<FrameState, LIRFrameStateTemplate> lirFrameStateCache = EconomicMap.create(Equivalence.IDENTITY);

    protected final Queue<VirtualObjectNode> pendingVirtualObjects = new ArrayDeque<>();

    public LIRFrameState build(NodeWithState node, FrameState topState, LabelRef exceptionEdge, JavaConstant deoptReasonAndAction, JavaConstant deoptSpeculation) {
        assert virtualObjects.size() == 0 : virtualObjects;
        assert objectStates.size() == 0 : objectStates;
        assert pendingVirtualObjects.size() == 0 : pendingVirtualObjects;

        verifyFrameState(node, topState);

        boolean shouldCacheFrameState = shouldCacheFrameStateChain(topState) && canCacheLIRFrameState(topState);

        boolean validForDeoptimization = true;
        // collect all VirtualObjectField instances:
        FrameState current = topState;
        do {
            if (current.virtualObjectMappingCount() > 0) {
                for (EscapeObjectState state : current.virtualObjectMappings()) {
                    GraalError.guarantee(state.object() != null, "Object must be non-null %s %s", state, current);
                    if (!objectStates.containsKey(state.object())) {
                        if (!(state instanceof MaterializedObjectState) || ((MaterializedObjectState) state).materializedValue() != state.object()) {
                            objectStates.put(state.object(), state);
                        }
                    }
                }
            }
            validForDeoptimization = validForDeoptimization && current.isValidForDeoptimization();
            current = current.outerFrameState();
        } while (current != null);

        BytecodeFrame frame = computeFrameForState(node, topState);

        VirtualObject[] virtualObjectsArray = null;
        if (virtualObjects.size() != 0) {
            // fill in the VirtualObject values
            VirtualObjectNode vobjNode;
            while ((vobjNode = pendingVirtualObjects.poll()) != null) {
                VirtualObject vobjValue = virtualObjects.get(vobjNode);
                assert vobjValue.getValues() == null;

                JavaValue[] values;
                JavaKind[] slotKinds;
                int entryCount = vobjNode.entryCount();
                if (entryCount == 0) {
                    values = NO_JAVA_VALUES;
                    slotKinds = NO_JAVA_KINDS;
                } else {
                    values = new JavaValue[entryCount];
                    slotKinds = new JavaKind[entryCount];
                }
                if (values.length > 0) {
                    VirtualObjectState currentField = (VirtualObjectState) objectStates.get(vobjNode);
                    assert currentField != null;
                    int pos = 0;
                    for (int i = 0; i < entryCount; i++) {
                        ValueNode value = currentField.values().get(i);
                        if (value == null) {
                            JavaKind entryKind = vobjNode.entryKind(metaAccessExtensionProvider, i);
                            values[pos] = JavaConstant.defaultForKind(entryKind.getStackKind());
                            slotKinds[pos] = entryKind.getStackKind();
                            pos++;
                        } else if (!value.isJavaConstant() || (value.asJavaConstant().getJavaKind() != JavaKind.Illegal)) {
                            values[pos] = toJavaValue(value);
                            slotKinds[pos] = toSlotKind(value);
                            pos++;
                        } else {
                            assert value.getStackKind() == JavaKind.Illegal : Assertions.errorMessage(value);
                            ValueNode previousValue = currentField.values().get(i - 1);
                            assert (previousValue != null && (previousValue.getStackKind().needsTwoSlots()) || vobjNode.isVirtualByteArray(metaAccessExtensionProvider)) : vobjNode + " " + i +
                                            " " + previousValue + " " + currentField.values().snapshot();
                            if (vobjNode.isVirtualByteArray(metaAccessExtensionProvider)) {
                                /*
                                 * Let Illegals pass through to help knowing the number of bytes to
                                 * write. For example, writing a short to index 2 of a byte array of
                                 * size 6 would look like, in debug info:
                                 *
                                 * {b0, b1, INT(...), ILLEGAL, b4, b5}
                                 *
                                 * Thus, from the VM, we can simply count the number of illegals to
                                 * restore the byte count.
                                 */
                                values[pos] = Value.ILLEGAL;
                                slotKinds[pos] = JavaKind.Illegal;
                                pos++;
                            } else if (previousValue == null || !previousValue.getStackKind().needsTwoSlots()) {
                                // Don't allow the IllegalConstant to leak into the debug info
                                JavaKind entryKind = vobjNode.entryKind(metaAccessExtensionProvider, i);
                                values[pos] = JavaConstant.defaultForKind(entryKind.getStackKind());
                                slotKinds[pos] = entryKind.getStackKind();
                                pos++;
                            }
                        }
                    }
                    if (pos != entryCount) {
                        values = Arrays.copyOf(values, pos);
                        slotKinds = Arrays.copyOf(slotKinds, pos);
                    }
                }
                assert checkValues(vobjValue.getType(), values, slotKinds);
                vobjValue.setValues(values, slotKinds);
            }
        }

        if (virtualObjects.size() != 0) {
            virtualObjectsArray = createVirtualObjectsArray();
        }

        if (shouldCacheFrameState && virtualObjectsArray != null) {
            cacheFrameStateTemplates(topState, frame, virtualObjectsArray);
        }

        virtualObjects.clear();
        objectStates.clear();

        return createLIRFrameState(frame, virtualObjectsArray, exceptionEdge, deoptReasonAndAction, deoptSpeculation, validForDeoptimization);
    }

    private static LIRFrameState createLIRFrameState(BytecodeFrame frame, VirtualObject[] virtualObjectsArray, LabelRef exceptionEdge, JavaConstant deoptReasonAndAction, JavaConstant deoptSpeculation,
                    boolean validForDeoptimization) {
        if (deoptReasonAndAction == null && deoptSpeculation == null) {
            return new LIRFrameState(frame, virtualObjectsArray, exceptionEdge, validForDeoptimization);
        } else {
            return new ImplicitLIRFrameState(frame, virtualObjectsArray, exceptionEdge, deoptReasonAndAction, deoptSpeculation, validForDeoptimization);
        }
    }

    /**
     * Immutable template for a {@link BytecodeFrame} chain and the reachable {@link VirtualObject}
     * graph. Template virtual-object ids are local to the template. Hits that splice the template
     * into a partially built frame state remap those ids to fresh ids in the target builder.
     * Separately from the copied virtual-object graph, the template records all virtual-object state
     * dependencies that can affect the cached values, including materialized-object states that do
     * not produce a {@link VirtualObject} in the final debug info.
     */
    private static final class LIRFrameStateTemplate {
        private final BytecodeFrame frame;
        private final VirtualObject[] virtualObjects;
        private final VirtualObjectNode[] virtualObjectNodes;
        private final VirtualObjectNode[] dependencyVirtualObjectNodes;
        private final EscapeObjectState[] dependencyObjectStates;

        private LIRFrameStateTemplate(BytecodeFrame frame, VirtualObject[] virtualObjects, VirtualObjectNode[] virtualObjectNodes, VirtualObjectNode[] dependencyVirtualObjectNodes,
                        EscapeObjectState[] dependencyObjectStates) {
            this.frame = frame;
            this.virtualObjects = virtualObjects;
            this.virtualObjectNodes = virtualObjectNodes;
            this.dependencyVirtualObjectNodes = dependencyVirtualObjectNodes;
            this.dependencyObjectStates = dependencyObjectStates;
        }

        static LIRFrameStateTemplate create(FrameState state, BytecodeFrame frame, VirtualObject[] allVirtualObjects, EconomicMap<VirtualObjectNode, VirtualObject> virtualObjectMap,
                        EconomicMap<VirtualObjectNode, EscapeObjectState> objectStateMap) {
            EconomicSet<VirtualObject> reachableVirtualObjects = EconomicSet.create(Equivalence.IDENTITY);
            collectVirtualObjects(frame, reachableVirtualObjects);
            VirtualObject[] reachable = filterReachableVirtualObjects(allVirtualObjects, reachableVirtualObjects);
            VirtualObjectNode[] virtualObjectNodes = new VirtualObjectNode[reachable.length];
            for (int i = 0; i < reachable.length; i++) {
                virtualObjectNodes[i] = findVirtualObjectNode(reachable[i], virtualObjectMap);
            }

            VirtualObjectNode[] dependencyVirtualObjectNodes = collectVirtualObjectStateDependencies(state, objectStateMap);
            EscapeObjectState[] dependencyObjectStates = new EscapeObjectState[dependencyVirtualObjectNodes.length];
            for (int i = 0; i < dependencyVirtualObjectNodes.length; i++) {
                VirtualObjectNode node = dependencyVirtualObjectNodes[i];
                dependencyObjectStates[i] = objectStateMap.get(node);
            }

            EconomicMap<VirtualObject, VirtualObject> copies = EconomicMap.create(Equivalence.IDENTITY);
            VirtualObject[] copiedVirtualObjects = copyVirtualObjects(reachable, copies, 0);
            BytecodeFrame copiedFrame = copyFrame(frame, copies);
            return new LIRFrameStateTemplate(copiedFrame, copiedVirtualObjects, virtualObjectNodes, dependencyVirtualObjectNodes, dependencyObjectStates);
        }

        /**
         * Determines whether this suffix template can be copied into {@code builder}. A caller frame
         * may already have created one of the virtual objects copied by this template, or may have
         * collected a different {@link EscapeObjectState} for any virtual object that affected the
         * cached values. Either case requires rebuilding the suffix normally.
         */
        boolean canCopyInto(DebugInfoBuilder builder) {
            for (VirtualObjectNode virtualObjectNode : virtualObjectNodes) {
                if (builder.virtualObjects.containsKey(virtualObjectNode)) {
                    return false;
                }
            }
            for (int i = 0; i < dependencyVirtualObjectNodes.length; i++) {
                VirtualObjectNode node = dependencyVirtualObjectNodes[i];
                if (builder.objectStates.get(node) != dependencyObjectStates[i]) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Copies this suffix template into {@code builder}, assigning fresh virtual-object ids after
         * any objects already created by the caller frame. This preserves the invariant that
         * {@link VirtualObject#getId()} is the object's index in the final virtual-object array.
         */
        BytecodeFrame copyInto(DebugInfoBuilder builder) {
            EconomicMap<VirtualObject, VirtualObject> copies = EconomicMap.create(Equivalence.IDENTITY);
            for (int i = 0; i < virtualObjects.length; i++) {
                VirtualObject virtualObject = virtualObjects[i];
                VirtualObject copy = VirtualObject.get(virtualObject.getType(), builder.virtualObjects.size(), virtualObject.isAutoBox());
                copies.put(virtualObject, copy);
                builder.virtualObjects.put(virtualObjectNodes[i], copy);
            }
            for (int i = 0; i < virtualObjects.length; i++) {
                JavaValue[] values = virtualObjects[i].getValues();
                if (values != null) {
                    copies.get(virtualObjects[i]).setValues(copyValues(values, copies), virtualObjects[i].getSlotKinds());
                }
            }
            return DebugInfoBuilder.copyFrame(frame, copies);
        }
    }

    private VirtualObject[] createVirtualObjectsArray() {
        VirtualObject[] virtualObjectsArray = new VirtualObject[virtualObjects.size()];
        int index = 0;
        for (VirtualObject value : virtualObjects.getValues()) {
            assert value.getId() == index : Assertions.errorMessage(value, value.getId(), index);
            virtualObjectsArray[index++] = value;
        }
        return virtualObjectsArray;
    }

    private void cacheFrameStateTemplates(FrameState topState, BytecodeFrame topFrame, VirtualObject[] virtualObjectsArray) {
        assert canCacheLIRFrameState(topState) : topState;
        FrameState currentState = topState;
        BytecodeFrame currentFrame = topFrame;
        while (currentState != null && currentFrame != null) {
            if (shouldCacheFrameStateChain(currentState) && !lirFrameStateCache.containsKey(currentState)) {
                assert canCacheLIRFrameState(currentState) : currentState;
                lirFrameStateCache.put(currentState, LIRFrameStateTemplate.create(currentState, currentFrame, virtualObjectsArray, virtualObjects, objectStates));
            }
            currentState = currentState.outerFrameState();
            currentFrame = currentFrame.caller();
        }
    }

    private static void collectVirtualObjects(BytecodeFrame frame, EconomicSet<VirtualObject> virtualObjects) {
        for (BytecodeFrame current = frame; current != null; current = current.caller()) {
            collectVirtualObjects(current.values, virtualObjects);
        }
    }

    private static void collectVirtualObjects(JavaValue[] values, EconomicSet<VirtualObject> virtualObjects) {
        for (JavaValue value : values) {
            if (value instanceof VirtualObject virtualObject && virtualObjects.add(virtualObject) && virtualObject.getValues() != null) {
                collectVirtualObjects(virtualObject.getValues(), virtualObjects);
            } else if (value instanceof StackLockValue lock) {
                collectVirtualObject(lock.getOwner(), virtualObjects);
            }
        }
    }

    private static void collectVirtualObject(JavaValue value, EconomicSet<VirtualObject> virtualObjects) {
        if (value instanceof VirtualObject virtualObject && virtualObjects.add(virtualObject) && virtualObject.getValues() != null) {
            collectVirtualObjects(virtualObject.getValues(), virtualObjects);
        }
    }

    private static VirtualObject[] filterReachableVirtualObjects(VirtualObject[] allVirtualObjects, EconomicSet<VirtualObject> reachableVirtualObjects) {
        if (reachableVirtualObjects.isEmpty()) {
            return new VirtualObject[0];
        }
        VirtualObject[] reachable = new VirtualObject[reachableVirtualObjects.size()];
        int index = 0;
        for (VirtualObject virtualObject : allVirtualObjects) {
            if (reachableVirtualObjects.contains(virtualObject)) {
                reachable[index++] = virtualObject;
            }
        }
        assert index == reachable.length : Assertions.errorMessage(index, reachable.length);
        return reachable;
    }

    private static VirtualObjectNode[] collectVirtualObjectStateDependencies(FrameState state, EconomicMap<VirtualObjectNode, EscapeObjectState> objectStateMap) {
        EconomicSet<VirtualObjectNode> dependencies = EconomicSet.create(Equivalence.IDENTITY);
        for (FrameState current = state; current != null; current = current.outerFrameState()) {
            for (ValueNode value : current.values()) {
                collectVirtualObjectStateDependency(value, objectStateMap, dependencies);
            }
            if (current.virtualObjectMappingCount() > 0) {
                for (EscapeObjectState objectState : current.virtualObjectMappings()) {
                    collectVirtualObjectStateDependency(objectState.object(), objectStateMap, dependencies);
                }
            }
        }
        return dependencies.toArray(new VirtualObjectNode[dependencies.size()]);
    }

    private static void collectVirtualObjectStateDependency(ValueNode value, EconomicMap<VirtualObjectNode, EscapeObjectState> objectStateMap, EconomicSet<VirtualObjectNode> dependencies) {
        if (value instanceof VirtualObjectNode virtualObjectNode) {
            collectVirtualObjectStateDependency(virtualObjectNode, objectStateMap, dependencies);
        }
    }

    private static void collectVirtualObjectStateDependency(VirtualObjectNode virtualObjectNode, EconomicMap<VirtualObjectNode, EscapeObjectState> objectStateMap,
                    EconomicSet<VirtualObjectNode> dependencies) {
        if (!dependencies.add(virtualObjectNode)) {
            return;
        }
        EscapeObjectState objectState = objectStateMap.get(virtualObjectNode);
        if (objectState instanceof VirtualObjectState virtualObjectState) {
            for (ValueNode value : virtualObjectState.values()) {
                collectVirtualObjectStateDependency(value, objectStateMap, dependencies);
            }
        } else if (objectState instanceof MaterializedObjectState materializedObjectState) {
            collectVirtualObjectStateDependency(materializedObjectState.materializedValue(), objectStateMap, dependencies);
        }
    }

    private static VirtualObjectNode findVirtualObjectNode(VirtualObject virtualObject, EconomicMap<VirtualObjectNode, VirtualObject> virtualObjectMap) {
        MapCursor<VirtualObjectNode, VirtualObject> cursor = virtualObjectMap.getEntries();
        while (cursor.advance()) {
            if (cursor.getValue() == virtualObject) {
                return cursor.getKey();
            }
        }
        throw GraalError.shouldNotReachHere("missing VirtualObjectNode for " + virtualObject);
    }

    private boolean checkValues(ResolvedJavaType type, JavaValue[] values, JavaKind[] slotKinds) {
        assert (values == null) == (slotKinds == null) : Assertions.errorMessage(values, slotKinds);
        if (values != null) {
            assert values.length == slotKinds.length : Assertions.errorMessage(values, slotKinds);
            if (!type.isArray()) {
                ResolvedJavaField[] fields = type.getInstanceFields(true);
                int fieldIndex = 0;
                for (int valueIndex = 0; valueIndex < values.length; valueIndex++, fieldIndex++) {
                    ResolvedJavaField field = fields[fieldIndex];
                    JavaKind valKind = slotKinds[valueIndex].getStackKind();
                    JavaKind fieldKind = storageKind(field.getType());
                    if ((valKind == JavaKind.Double || valKind == JavaKind.Long) && fieldKind == JavaKind.Int) {
                        assert fieldIndex + 1 < fields.length : String.format("Not enough fields for fieldIndex = %d valueIndex = %d %s %s", fieldIndex, valueIndex, Arrays.toString(fields),
                                        Arrays.toString(values));
                        assert storageKind(fields[fieldIndex + 1].getType()) == JavaKind.Int : String.format("fieldIndex = %d valueIndex = %d %s %s %s", fieldIndex, valueIndex,
                                        storageKind(fields[fieldIndex + 1].getType()), Arrays.toString(fields),
                                        Arrays.toString(values));
                        fieldIndex++;
                    } else {
                        assert valKind == fieldKind.getStackKind() : field + ": " + valKind + " != " + fieldKind;
                    }
                }
                assert fields.length == fieldIndex : type + ": fields=" + Arrays.toString(fields) + ", field values=" + Arrays.toString(values);
            } else {
                JavaKind componentKind = storageKind(type.getComponentType()).getStackKind();
                if (componentKind == JavaKind.Object) {
                    for (int i = 0; i < values.length; i++) {
                        assert slotKinds[i].isObject() : slotKinds[i] + " != " + componentKind;
                    }
                } else {
                    for (int i = 0; i < values.length; i++) {
                        assert slotKinds[i] == componentKind ||
                                        (slotKinds[i] == JavaKind.Illegal && storageKind(type.getComponentType()) == JavaKind.Byte) ||
                                        componentKind.getBitCount() >= slotKinds[i].getBitCount() ||
                                        (componentKind == JavaKind.Int && slotKinds[i].getBitCount() >= JavaKind.Int.getBitCount()) : slotKinds[i] + " != " + componentKind;
                    }
                }
            }
        }
        return true;
    }

    /*
     * Customization point for subclasses. For example, Word types have a kind Object, but are
     * internally stored as a primitive value. We do not know about Word types here, but subclasses
     * do know.
     */
    protected JavaKind storageKind(JavaType type) {
        return type.getJavaKind();
    }

    /**
     * Perform platform dependent verification of the FrameState.
     *
     * @param node the node using the state
     * @param topState the state
     */
    protected void verifyFrameState(NodeWithState node, FrameState topState) {
    }

    /**
     * Builds a {@link BytecodeFrame} for {@code state}, first reusing a complete debug-info suffix
     * template when virtual-object side state can be copied safely, and otherwise falling back to a
     * plain frame-chain template when the complete
     * {@link FrameState#outerFrameState() outer-frame chain} is independent of virtual-object and
     * lock side state.
     */
    protected BytecodeFrame computeFrameForState(NodeWithState node, FrameState state) {
        try {
            LIRFrameStateTemplate cachedState = lirFrameStateCache.get(state);
            if (cachedState != null && cachedState.canCopyInto(this)) {
                return cachedState.copyInto(this);
            }

            BytecodeFrame cachedFrame = bytecodeFrameCache.get(state);
            if (cachedFrame != null) {
                return copyFrame(cachedFrame);
            }

            assert state.bci != BytecodeFrame.INVALID_FRAMESTATE_BCI : Assertions.errorMessageContext("node", node, "state", state);
            assert state.bci != BytecodeFrame.UNKNOWN_BCI : Assertions.errorMessageContext("node", node, "state", state);

            assert state.bci != BytecodeFrame.BEFORE_BCI || state.locksSize() == 0 : Assertions.errorMessageContext("node", node, "state", state);

            assert state.bci != BytecodeFrame.AFTER_BCI || state.locksSize() == 0 : Assertions.errorMessageContext("node", node, "state", state);

            assert state.bci != BytecodeFrame.AFTER_EXCEPTION_BCI || state.locksSize() == 0 : Assertions.errorMessageContext("node", node, "state", state);

            assert state.verify();

            int numLocals = state.localsSize();
            int numStack = state.stackSize();
            int numLocks = state.locksSize();

            int numValues = numLocals + numStack + numLocks;
            int numKinds = numLocals + numStack;

            JavaValue[] values = numValues == 0 ? NO_JAVA_VALUES : new JavaValue[numValues];
            JavaKind[] slotKinds = numKinds == 0 ? NO_JAVA_KINDS : new JavaKind[numKinds];
            computeLocals(state, numLocals, values, slotKinds);
            computeStack(state, numLocals, numStack, values, slotKinds);
            computeLocks(state, values);

            BytecodeFrame caller = null;
            if (state.outerFrameState() != null) {
                caller = computeFrameForState(node, state.outerFrameState());
            }

            if (!state.canProduceBytecodeFrame()) {
                // This typically means a snippet or intrinsic frame state made it to the backend
                String ste = state.getCode() != null ? state.getCode().asStackTraceElement(state.bci).toString() : state.toString();
                throw new GraalError("Frame state for %s cannot be converted to a BytecodeFrame since the frame state's code is " +
                                "not the same as the frame state method's code", ste);
            }

            BytecodeFrame result = new BytecodeFrame(caller, state.getMethod(), state.bci, state.getStackState().rethrowException, state.getStackState().duringCall, values, slotKinds, numLocals,
                            numStack,
                            numLocks);
            if (virtualObjects.size() == 0 && pendingVirtualObjects.size() == 0 && shouldCacheFrameStateChain(state) && canCacheFrameStateChain(state) && !bytecodeFrameCache.containsKey(state)) {
                bytecodeFrameCache.put(state, copyFrame(result));
            }
            return result;
        } catch (GraalError e) {
            throw e.addContext("FrameState: ", state);
        }
    }

    /**
     * Determines whether {@code state} and all of its outer frame states can be cached as
     * side-effect-free {@link BytecodeFrame} templates. Building debug info for a frame state can do
     * more than fill the frame value arrays: virtual object mappings populate {@link #virtualObjects}
     * and {@link #pendingVirtualObjects}, direct {@link VirtualObjectNode} values are translated
     * through those side tables, and locks may allocate backend-specific lock slots while also
     * referring to eliminated virtual objects. Reusing a cached frame for any of those cases would
     * either skip required side effects or share per-build debug-info objects. Therefore only plain
     * local/stack value frame-state chains are cached.
     */
    private static boolean canCacheFrameStateChain(FrameState state) {
        for (FrameState current = state; current != null; current = current.outerFrameState()) {
            if (!canCacheFrameState(current)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true when {@code state} itself can be described only by immutable frame metadata and
     * local/stack values. Caller chains are checked by {@link #canCacheFrameStateChain(FrameState)}.
     */
    private static boolean canCacheFrameState(FrameState state) {
        if (state.locksSize() != 0 || state.virtualObjectMappingCount() != 0) {
            return false;
        }
        for (int i = 0; i < state.localsSize(); i++) {
            if (state.localAt(i) instanceof VirtualObjectNode) {
                return false;
            }
        }
        for (int i = 0; i < state.stackSize(); i++) {
            if (state.stackAt(i) instanceof VirtualObjectNode) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true when a complete {@link LIRFrameState} can be cached as a template. Virtual
     * objects are permitted because the template cache deep-copies both the frame chain and the
     * virtual-object graph on every hit. Locks are still excluded because HotSpot lock states
     * allocate scoped lock slots as a side effect while debug info is built.
     */
    protected static boolean canCacheLIRFrameState(FrameState state) {
        for (FrameState current = state; current != null; current = current.outerFrameState()) {
            if (current.locksSize() != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Avoids creating debug-info templates for shallow frame states. Callers also require the build
     * to have created at least one {@link VirtualObject} before inserting full LIR frame-state
     * templates, so plain frame-state chains stay on the lighter {@link #bytecodeFrameCache}.
     */
    private static boolean shouldCacheFrameStateChain(FrameState state) {
        return state.outerFrameState() != null;
    }

    /**
     * Creates a {@link BytecodeFrame} copy, including caller frames, with cloned
     * {@link BytecodeFrame#values} and slot-kind arrays. {@link VirtualObject} and
     * {@link StackLockValue} instances are copied because their contents can be mutated by later LIR
     * phases. Other {@link JavaValue} instances are reused because later LIR phases replace array
     * entries instead of mutating those values.
     */
    protected static BytecodeFrame copyFrame(BytecodeFrame frame) {
        return copyFrame(frame, EconomicMap.create(Equivalence.IDENTITY));
    }

    private static BytecodeFrame copyFrame(BytecodeFrame frame, EconomicMap<VirtualObject, VirtualObject> virtualObjectCopies) {
        if (frame == null) {
            return null;
        }
        return new BytecodeFrame(copyFrame(frame.caller(), virtualObjectCopies), frame.getMethod(), frame.getBCI(), frame.rethrowException, frame.duringCall, copyValues(frame.values,
                        virtualObjectCopies), frame.getSlotKinds(), frame.numLocals,
                        frame.numStack, frame.numLocks);
    }

    private static VirtualObject[] copyVirtualObjects(VirtualObject[] virtualObjects, EconomicMap<VirtualObject, VirtualObject> virtualObjectCopies, int firstId) {
        if (virtualObjects == null) {
            return null;
        }
        VirtualObject[] copies = new VirtualObject[virtualObjects.length];
        for (int i = 0; i < virtualObjects.length; i++) {
            if (firstId >= 0) {
                copies[i] = copyVirtualObjectHeader(virtualObjects[i], virtualObjectCopies, firstId + i);
            } else {
                copies[i] = copyVirtualObjectHeader(virtualObjects[i], virtualObjectCopies);
            }
        }
        for (int i = 0; i < virtualObjects.length; i++) {
            JavaValue[] values = virtualObjects[i].getValues();
            if (values != null) {
                copies[i].setValues(copyValues(values, virtualObjectCopies), virtualObjects[i].getSlotKinds());
            }
        }
        return copies;
    }

    private static JavaValue[] copyValues(JavaValue[] values, EconomicMap<VirtualObject, VirtualObject> virtualObjectCopies) {
        JavaValue[] copy = values.clone();
        for (int i = 0; i < copy.length; i++) {
            copy[i] = copyValue(copy[i], virtualObjectCopies);
        }
        return copy;
    }

    private static JavaValue copyValue(JavaValue value, EconomicMap<VirtualObject, VirtualObject> virtualObjectCopies) {
        if (value instanceof VirtualObject virtualObject) {
            return copyVirtualObjectHeader(virtualObject, virtualObjectCopies);
        } else if (value instanceof StackLockValue lock) {
            return new StackLockValue(copyValue(lock.getOwner(), virtualObjectCopies), lock.getSlot(), lock.isEliminated());
        }
        return value;
    }

    private static VirtualObject copyVirtualObjectHeader(VirtualObject virtualObject, EconomicMap<VirtualObject, VirtualObject> virtualObjectCopies) {
        VirtualObject copy = virtualObjectCopies.get(virtualObject);
        if (copy == null) {
            copy = copyVirtualObjectHeader(virtualObject, virtualObjectCopies, virtualObject.getId());
        }
        return copy;
    }

    private static VirtualObject copyVirtualObjectHeader(VirtualObject virtualObject, EconomicMap<VirtualObject, VirtualObject> virtualObjectCopies, int id) {
        VirtualObject copy = virtualObjectCopies.get(virtualObject);
        if (copy == null) {
            copy = VirtualObject.get(virtualObject.getType(), id, virtualObject.isAutoBox());
            virtualObjectCopies.put(virtualObject, copy);
        }
        return copy;
    }

    protected void computeLocals(FrameState state, int numLocals, JavaValue[] values, JavaKind[] slotKinds) {
        for (int i = 0; i < numLocals; i++) {
            ValueNode local = state.localAt(i);
            values[i] = toJavaValue(local);
            slotKinds[i] = toSlotKind(local);
        }
    }

    protected void computeStack(FrameState state, int numLocals, int numStack, JavaValue[] values, JavaKind[] slotKinds) {
        for (int i = 0; i < numStack; i++) {
            ValueNode stack = state.stackAt(i);
            values[numLocals + i] = toJavaValue(stack);
            slotKinds[numLocals + i] = toSlotKind(stack);
        }
    }

    protected void computeLocks(FrameState state, JavaValue[] values) {
        for (int i = 0; i < state.locksSize(); i++) {
            values[state.localsSize() + state.stackSize() + i] = computeLockValue(state, i);
        }
    }

    protected JavaValue computeLockValue(FrameState state, int i) {
        return toJavaValue(state.lockAt(i));
    }

    private static final CounterKey STATE_VIRTUAL_OBJECTS = DebugContext.counter("StateVirtualObjects");
    private static final CounterKey STATE_ILLEGALS = DebugContext.counter("StateIllegals");
    private static final CounterKey STATE_VARIABLES = DebugContext.counter("StateVariables");
    private static final CounterKey STATE_CONSTANTS = DebugContext.counter("StateConstants");

    private static JavaKind toSlotKind(ValueNode value) {
        if (value == null) {
            return JavaKind.Illegal;
        } else {
            return value.getStackKind();
        }
    }

    protected JavaValue toJavaValue(ValueNode value) {
        try {
            if (value instanceof VirtualObjectNode) {
                VirtualObjectNode obj = (VirtualObjectNode) value;
                EscapeObjectState state = objectStates.get(obj);
                if (state == null && obj.entryCount() > 0) {
                    // null states occur for objects with 0 fields
                    throw new GraalError("no mapping found for virtual object %s", obj);
                }
                if (state instanceof MaterializedObjectState) {
                    return toJavaValue(((MaterializedObjectState) state).materializedValue());
                } else {
                    assert obj.entryCount() == 0 || state instanceof VirtualObjectState : Assertions.errorMessage(obj, obj.entryCount(), state);
                    VirtualObject vobject = virtualObjects.get(obj);
                    if (vobject == null) {
                        boolean isAutoBox = obj instanceof VirtualBoxingNode;
                        vobject = VirtualObject.get(obj.type(), virtualObjects.size(), isAutoBox);
                        virtualObjects.put(obj, vobject);
                        pendingVirtualObjects.add(obj);
                    }
                    STATE_VIRTUAL_OBJECTS.increment(debug);
                    return vobject;
                }
            } else {
                // Remove proxies from constants so the constant can be directly embedded.
                ValueNode unproxied = GraphUtil.unproxify(value);
                if (unproxied != null && unproxied.isJavaConstant()) {
                    STATE_CONSTANTS.increment(debug);
                    return unproxied.asJavaConstant();
                } else if (value != null) {
                    STATE_VARIABLES.increment(debug);
                    Value operand = nodeValueMap.operand(value);
                    if (operand instanceof ConstantValue && ((ConstantValue) operand).isJavaConstant()) {
                        return ((ConstantValue) operand).getJavaConstant();
                    } else if (LIRValueUtil.isVariable(operand)) {
                        return LIRValueUtil.asVariable(operand);
                    } else {
                        assert operand instanceof RegisterValue : operand + " for " + value;
                        return (JavaValue) operand;
                    }

                } else {
                    // return a dummy value because real value not needed
                    STATE_ILLEGALS.increment(debug);
                    return Value.ILLEGAL;
                }
            }
        } catch (GraalError e) {
            throw e.addContext("toValue: ", value);
        }
    }
}
