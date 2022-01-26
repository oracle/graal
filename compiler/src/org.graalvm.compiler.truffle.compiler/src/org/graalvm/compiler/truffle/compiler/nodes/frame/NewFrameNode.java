/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.nodes.frame;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.VirtualizableAllocation;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.serviceprovider.SpeculationReasonGroup;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * Intrinsic node representing the call for creating a frame.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class NewFrameNode extends FixedWithNextNode implements IterableNodeType, VirtualizableAllocation, Canonicalizable {

    public static final byte NO_TYPE_MARKER = 0x79;
    public static final byte INITIAL_TYPE_MARKER = 0x78;

    /*
     * The compiler should not import classes from Truffle for libgraal, so we manually encode these
     * constants:
     */
    public static final byte FrameSlotKindObjectTag = 0; // FrameSlotKind.Object.tag
    public static final byte FrameSlotKindLongTag = 1; // FrameSlotKind.Long.tag
    private static final byte FrameSlotKindIntTag = 2; // FrameSlotKind.Int.tag
    private static final byte FrameSlotKindDoubleTag = 3; // FrameSlotKind.Double.tag
    private static final byte FrameSlotKindFloatTag = 4; // FrameSlotKind.Float.tag
    private static final byte FrameSlotKindBooleanTag = 5; // FrameSlotKind.Boolean.tag
    private static final byte FrameSlotKindByteTag = 6; // FrameSlotKind.Byte.tag
    public static final byte FrameSlotKindIllegalTag = 7; // FrameSlotKind.Illegal.tag

    public static final NodeClass<NewFrameNode> TYPE = NodeClass.create(NewFrameNode.class);
    @Input ValueNode descriptor;
    @Input ValueNode arguments;

    @Input VirtualInstanceNode virtualFrame;
    @Input NodeInputList<ValueNode> virtualFrameArrays;

    private static final int OBJECT_ARRAY = 0;
    private static final int PRIMITIVE_ARRAY = 1;
    private static final int TAGS_ARRAY = 2;
    private static final int INDEXED_OBJECT_ARRAY = 3;
    private static final int INDEXED_PRIMITIVE_ARRAY = 4;
    private static final int INDEXED_TAGS_ARRAY = 5;
    private static final int AUXILIARY_SLOTS_ARRAY = 6;

    @Input NodeInputList<ValueNode> smallIntConstants;

    @Input private ValueNode frameDefaultValue;
    private final boolean intrinsifyAccessors;
    private final byte[] frameSlotKinds;
    private final byte[] indexedFrameSlotKinds;
    private final int frameSize;
    private final int indexedFrameSize;
    private final int auxiliarySize;

    private final SpeculationReason intrinsifyAccessorsSpeculation;

    public static byte asStackTag(byte tag) {
        switch (tag) {
            case FrameSlotKindBooleanTag:
            case FrameSlotKindByteTag:
            case FrameSlotKindIntTag:
                return FrameSlotKindIntTag;
            case FrameSlotKindDoubleTag:
                return FrameSlotKindDoubleTag;
            case FrameSlotKindFloatTag:
                return FrameSlotKindFloatTag;
            case FrameSlotKindLongTag:
            case FrameSlotKindObjectTag:
            case FrameSlotKindIllegalTag:
            case NO_TYPE_MARKER:
            case INITIAL_TYPE_MARKER:
                return FrameSlotKindLongTag;
        }
        throw new IllegalStateException("Unexpected frame slot kind tag: " + tag);
    }

    public static JavaKind asJavaKind(byte tag) {
        switch (tag) {
            case FrameSlotKindIntTag:
                return JavaKind.Int;
            case FrameSlotKindDoubleTag:
                return JavaKind.Double;
            case FrameSlotKindFloatTag:
                return JavaKind.Float;
            case FrameSlotKindLongTag:
            case NO_TYPE_MARKER:
                return JavaKind.Long;
        }
        throw new IllegalStateException("Unexpected frame slot kind tag: " + tag);
    }

    private static final SpeculationReasonGroup INTRINSIFY_FRAME_ACCESSORS_SPECULATIONS = new SpeculationReasonGroup("IntrinsifyFrameAccessor");
    private final KnownTruffleTypes types;

    public NewFrameNode(GraphBuilderContext b, ValueNode frameDescriptorNode, ValueNode arguments, KnownTruffleTypes types) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createExactTrusted(types.classFrameClass)));

        this.descriptor = frameDescriptorNode;
        this.arguments = arguments;
        this.types = types;

        StructuredGraph graph = b.getGraph();
        MetaAccessProvider metaAccess = b.getMetaAccess();
        ConstantReflectionProvider constantReflection = b.getConstantReflection();
        JavaConstant frameDescriptor = frameDescriptorNode.asJavaConstant();

        /*
         * We access the FrameDescriptor only here and copy out all relevant data (being extra
         * paranoid when copying data out since they may be concurrently modified). So later
         * modifications to the FrameDescriptor by the running Truffle thread do not interfere. The
         * frame version assumption is registered first, so that we get invalidated in case the
         * FrameDescriptor changes.
         */
        JavaConstant version = constantReflection.readFieldValue(types.fieldFrameDescriptorVersion, frameDescriptor);
        graph.getAssumptions().record(new TruffleAssumption(version));

        /*
         * We only want to intrinsify get/set/is accessor methods of a virtual frame when we expect
         * that the frame is not going to be materialized. Materialization results in heap-based
         * data arrays, which means that set-methods need a FrameState. Most of the benefit of
         * accessor method intrinsification is avoiding the FrameState creation during partial
         * evaluation.
         *
         * The frame descriptor of a call target does not change and since a SpeculationLog is
         * already associated with a specific call target we only need a single speculation object
         * representing a speculation on a NewFrameNode.
         */
        this.intrinsifyAccessorsSpeculation = INTRINSIFY_FRAME_ACCESSORS_SPECULATIONS.createSpeculationReason();

        boolean intrinsify = false;
        if (!constantReflection.readFieldValue(types.fieldFrameDescriptorMaterializeCalled, frameDescriptor).asBoolean()) {
            SpeculationLog speculationLog = graph.getSpeculationLog();
            intrinsify = speculationLog != null && speculationLog.maySpeculate(intrinsifyAccessorsSpeculation);
        }
        this.intrinsifyAccessors = intrinsify;

        JavaConstant defaultValue = constantReflection.readFieldValue(types.fieldFrameDescriptorDefaultValue, frameDescriptor);
        this.frameDefaultValue = ConstantNode.forConstant(defaultValue, metaAccess, graph);

        JavaConstant slotArrayList = constantReflection.readFieldValue(types.fieldFrameDescriptorSlots, frameDescriptor);
        JavaConstant slotArray = constantReflection.readFieldValue(types.fieldArrayListElementData, slotArrayList);
        final int slotsArrayLength = constantReflection.readArrayLength(slotArray);
        final int frameLength = constantReflection.readFieldValue(types.fieldFrameDescriptorSize, frameDescriptor).asInt();

        byte[] frameSlotKindsCandidate = new byte[frameLength];
        Arrays.fill(frameSlotKindsCandidate, NO_TYPE_MARKER);
        for (int i = 0; i < slotsArrayLength; i++) {
            JavaConstant slot = constantReflection.readArrayElement(slotArray, i);
            if (slot.isNonNull()) {
                JavaConstant slotKind = constantReflection.readFieldValue(types.fieldFrameSlotKind, slot);
                JavaConstant slotIndex = constantReflection.readFieldValue(types.fieldFrameSlotIndex, slot);
                if (slotKind.isNonNull() && slotIndex.isNonNull()) {
                    byte kind = (byte) constantReflection.readFieldValue(types.fieldFrameSlotKindTag, slotKind).asInt();
                    int index = slotIndex.asInt();
                    if (index >= frameSlotKindsCandidate.length) {
                        /*
                         * Since the size and slotArrayList of the FrameDescriptor are read
                         * asynchronously we have to defensively check that we did not get old size
                         * not matching the slot's index. If we did the frameSlotKinds array has to
                         * be expanded.
                         */
                        final byte[] newArray = Arrays.copyOf(frameSlotKindsCandidate, index + 1);
                        Arrays.fill(newArray, frameSlotKindsCandidate.length, newArray.length, NO_TYPE_MARKER);
                        frameSlotKindsCandidate = newArray;
                    }
                    frameSlotKindsCandidate[index] = asStackTag(kind);
                }
            }
        }
        this.frameSlotKinds = frameSlotKindsCandidate;
        this.frameSize = frameSlotKindsCandidate.length;

        JavaConstant indexedTagsArray = constantReflection.readFieldValue(types.fieldFrameDescriptorIndexedSlotTags, frameDescriptor);
        this.indexedFrameSize = constantReflection.readArrayLength(indexedTagsArray);

        byte[] indexedFrameSlotKindsCandidate = new byte[indexedFrameSize];
        Arrays.fill(indexedFrameSlotKindsCandidate, FrameSlotKindLongTag);
        this.indexedFrameSlotKinds = indexedFrameSlotKindsCandidate;

        this.auxiliarySize = constantReflection.readFieldValue(types.fieldFrameDescriptorAuxiliarySlotCount, frameDescriptor).asInt();

        ResolvedJavaType frameType = types.classFrameClass;

        ResolvedJavaField[] frameFields = frameType.getInstanceFields(true);
        this.virtualFrame = graph.add(new VirtualInstanceNode(frameType, frameFields, true));

        ValueNode emptyObjectArray = constantObject(graph, b, types.fieldEmptyObjectArray);
        ValueNode emptyLongArray = constantObject(graph, b, types.fieldEmptyLongArray);
        ValueNode emptyByteArray = constantObject(graph, b, types.fieldEmptyByteArray);
        boolean emptyArraysAvailable = emptyObjectArray != null && emptyLongArray != null && emptyByteArray != null;

        ValueNode localsArray;
        ValueNode primitiveLocalsArray;
        ValueNode tagsArray;
        if (frameSize == 0 && emptyArraysAvailable) {
            localsArray = emptyObjectArray;
            primitiveLocalsArray = emptyLongArray;
            tagsArray = emptyByteArray;
        } else {
            localsArray = graph.add(new VirtualArrayNode((ResolvedJavaType) types.fieldLocals.getType().getComponentType(), frameSize));
            primitiveLocalsArray = graph.add(new VirtualArrayNode((ResolvedJavaType) types.fieldPrimitiveLocals.getType().getComponentType(), frameSize));
            tagsArray = graph.add(new VirtualArrayNode((ResolvedJavaType) types.fieldTags.getType().getComponentType(), frameSize));
        }
        ValueNode indexedLocals;
        ValueNode indexedPrimitiveLocals;
        ValueNode indexedTags;
        if (indexedFrameSize == 0 && emptyArraysAvailable) {
            indexedLocals = emptyObjectArray;
            indexedPrimitiveLocals = emptyLongArray;
            indexedTags = emptyByteArray;
        } else {
            indexedLocals = graph.add(new VirtualArrayNode((ResolvedJavaType) types.fieldIndexedLocals.getType().getComponentType(), indexedFrameSize));
            indexedPrimitiveLocals = graph.add(new VirtualArrayNode((ResolvedJavaType) types.fieldIndexedPrimitiveLocals.getType().getComponentType(), indexedFrameSize));
            indexedTags = graph.add(new VirtualArrayNode((ResolvedJavaType) types.fieldIndexedTags.getType().getComponentType(), indexedFrameSize));
        }
        ValueNode auxiliarySlotsArray;
        if (auxiliarySize == 0 && emptyArraysAvailable) {
            auxiliarySlotsArray = emptyObjectArray;
        } else {
            auxiliarySlotsArray = graph.add(new VirtualArrayNode((ResolvedJavaType) types.fieldAuxiliarySlots.getType().getComponentType(), auxiliarySize));
        }
        this.virtualFrameArrays = new NodeInputList<>(this, new ValueNode[]{localsArray, primitiveLocalsArray, tagsArray, indexedLocals, indexedPrimitiveLocals, indexedTags, auxiliarySlotsArray});

        ValueNode[] c = new ValueNode[TruffleCompilerRuntime.getRuntime().getFrameSlotKindTagsCount()];
        for (int i = 0; i < c.length; i++) {
            c[i] = ConstantNode.forInt(i, graph);
        }
        this.smallIntConstants = new NodeInputList<>(this, c);
    }

    private static ValueNode constantObject(StructuredGraph graph, GraphBuilderContext b, ResolvedJavaField field) {
        JavaConstant fieldValue = b.getConstantReflection().readFieldValue(field, null);
        return fieldValue == null ? null : ConstantNode.forConstant(fieldValue, b.getMetaAccess(), graph);
    }

    public byte[] getFrameSlotKinds() {
        return frameSlotKinds;
    }

    public byte[] getIndexedFrameSlotKinds() {
        return indexedFrameSlotKinds;
    }

    public ValueNode getDescriptor() {
        return descriptor;
    }

    public ValueNode getArguments() {
        return arguments;
    }

    public boolean getIntrinsifyAccessors() {
        return intrinsifyAccessors;
    }

    public SpeculationReason getIntrinsifyAccessorsSpeculation() {
        return intrinsifyAccessorsSpeculation;
    }

    public boolean isValidSlotIndex(int index) {
        return index >= 0 && index < frameSize && frameSlotKinds[index] != NO_TYPE_MARKER;
    }

    public boolean isValidIndexedSlotIndex(int index) {
        return index >= 0 && index < indexedFrameSize;
    }

    static ResolvedJavaField findField(ResolvedJavaField[] fields, String fieldName) {
        for (ResolvedJavaField field : fields) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ResolvedJavaType frameType = stamp(NodeView.DEFAULT).javaType(tool.getMetaAccess());
        assert frameType.equals(types.classFrameClass);
        NodeSourcePosition sourcePosition = getNodeSourcePosition();

        if (virtualFrameArrays.get(OBJECT_ARRAY) instanceof VirtualArrayNode) {
            ValueNode[] objectArrayEntryState = new ValueNode[frameSize];
            ValueNode[] primitiveArrayEntryState = new ValueNode[frameSize];
            ValueNode[] tagArrayEntryState = new ValueNode[frameSize];

            Arrays.fill(objectArrayEntryState, frameDefaultValue);
            Arrays.fill(tagArrayEntryState, smallIntConstants.get(0));
            for (int i = 0; i < frameSize; i++) {
                byte tag = frameSlotKinds[i];
                primitiveArrayEntryState[i] = ConstantNode.defaultForKind(asJavaKind(tag), graph());
            }
            tool.createVirtualObject((VirtualObjectNode) virtualFrameArrays.get(OBJECT_ARRAY), objectArrayEntryState, Collections.<MonitorIdNode> emptyList(), sourcePosition, false);
            tool.createVirtualObject((VirtualObjectNode) virtualFrameArrays.get(PRIMITIVE_ARRAY), primitiveArrayEntryState, Collections.<MonitorIdNode> emptyList(), sourcePosition, false);
            tool.createVirtualObject((VirtualObjectNode) virtualFrameArrays.get(TAGS_ARRAY), tagArrayEntryState, Collections.<MonitorIdNode> emptyList(), sourcePosition, false);
        }
        if (virtualFrameArrays.get(INDEXED_OBJECT_ARRAY) instanceof VirtualArrayNode) {
            ValueNode[] indexedObjectArrayEntryState = new ValueNode[indexedFrameSize];
            ValueNode[] indexedPrimitiveArrayEntryState = new ValueNode[indexedFrameSize];
            ValueNode[] indexedTagArrayEntryState = new ValueNode[indexedFrameSize];

            Arrays.fill(indexedObjectArrayEntryState, frameDefaultValue);
            Arrays.fill(indexedTagArrayEntryState, smallIntConstants.get(0));
            for (int i = 0; i < indexedFrameSize; i++) {
                byte tag = indexedFrameSlotKinds[i];
                indexedPrimitiveArrayEntryState[i] = ConstantNode.defaultForKind(asJavaKind(tag), graph());
            }
            tool.createVirtualObject((VirtualObjectNode) virtualFrameArrays.get(INDEXED_OBJECT_ARRAY), indexedObjectArrayEntryState, Collections.<MonitorIdNode> emptyList(), sourcePosition, false);
            tool.createVirtualObject((VirtualObjectNode) virtualFrameArrays.get(INDEXED_PRIMITIVE_ARRAY), indexedPrimitiveArrayEntryState, Collections.<MonitorIdNode> emptyList(), sourcePosition,
                            false);
            tool.createVirtualObject((VirtualObjectNode) virtualFrameArrays.get(INDEXED_TAGS_ARRAY), indexedTagArrayEntryState, Collections.<MonitorIdNode> emptyList(), sourcePosition, false);
        }
        if (virtualFrameArrays.get(AUXILIARY_SLOTS_ARRAY) instanceof VirtualArrayNode) {
            ValueNode[] auxiliarySlotArrayEntryState = new ValueNode[auxiliarySize];
            Arrays.fill(auxiliarySlotArrayEntryState, ConstantNode.defaultForKind(JavaKind.Object, graph()));
            tool.createVirtualObject((VirtualObjectNode) virtualFrameArrays.get(AUXILIARY_SLOTS_ARRAY), auxiliarySlotArrayEntryState, Collections.<MonitorIdNode> emptyList(), sourcePosition, false);
        }

        assert types.frameFields.length == 9;
        ValueNode[] frameEntryState = new ValueNode[types.frameFields.length];
        List<ResolvedJavaField> frameFieldList = Arrays.asList(types.frameFields);
        frameEntryState[frameFieldList.indexOf(types.fieldDescriptor)] = getDescriptor();
        frameEntryState[frameFieldList.indexOf(types.fieldLocals)] = virtualFrameArrays.get(OBJECT_ARRAY);
        frameEntryState[frameFieldList.indexOf(types.fieldPrimitiveLocals)] = virtualFrameArrays.get(PRIMITIVE_ARRAY);
        frameEntryState[frameFieldList.indexOf(types.fieldTags)] = virtualFrameArrays.get(TAGS_ARRAY);
        frameEntryState[frameFieldList.indexOf(types.fieldIndexedLocals)] = virtualFrameArrays.get(INDEXED_OBJECT_ARRAY);
        frameEntryState[frameFieldList.indexOf(types.fieldIndexedPrimitiveLocals)] = virtualFrameArrays.get(INDEXED_PRIMITIVE_ARRAY);
        frameEntryState[frameFieldList.indexOf(types.fieldIndexedTags)] = virtualFrameArrays.get(INDEXED_TAGS_ARRAY);
        frameEntryState[frameFieldList.indexOf(types.fieldArguments)] = getArguments();
        frameEntryState[frameFieldList.indexOf(types.fieldAuxiliarySlots)] = virtualFrameArrays.get(AUXILIARY_SLOTS_ARRAY);
        /*
         * The new frame is created with "ensureVirtualized" enabled, so that it cannot be
         * materialized. This can only be lifted by a AllowMaterializeNode, which corresponds to a
         * frame.materialize() call.
         */
        tool.createVirtualObject(virtualFrame, frameEntryState, Collections.<MonitorIdNode> emptyList(), sourcePosition, true);
        tool.replaceWithVirtual(virtualFrame);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        return this;
    }

    public VirtualArrayNode getTagArray(VirtualFrameAccessType type) {
        /*
         * If one of these casts fails, there's an access into a zero-length array, which should not
         * get to this point.
         */
        switch (type) {
            case Indexed:
                return (VirtualArrayNode) virtualFrameArrays.get(INDEXED_TAGS_ARRAY);
            case Legacy:
                return (VirtualArrayNode) virtualFrameArrays.get(TAGS_ARRAY);
            case Auxiliary:
                return null;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    public VirtualArrayNode getObjectArray(VirtualFrameAccessType type) {
        /*
         * If one of these casts fails, there's an access into a zero-length array, which should not
         * get to this point.
         */
        switch (type) {
            case Indexed:
                return (VirtualArrayNode) virtualFrameArrays.get(INDEXED_OBJECT_ARRAY);
            case Legacy:
                return (VirtualArrayNode) virtualFrameArrays.get(OBJECT_ARRAY);
            case Auxiliary:
                return (VirtualArrayNode) virtualFrameArrays.get(AUXILIARY_SLOTS_ARRAY);
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    public VirtualArrayNode getPrimitiveArray(VirtualFrameAccessType type) {
        /*
         * If one of these casts fails, there's an access into a zero-length array, which should not
         * get to this point.
         */
        switch (type) {
            case Indexed:
                return (VirtualArrayNode) virtualFrameArrays.get(INDEXED_PRIMITIVE_ARRAY);
            case Legacy:
                return (VirtualArrayNode) virtualFrameArrays.get(PRIMITIVE_ARRAY);
            case Auxiliary:
                return null;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    public int getFrameSize() {
        return frameSize;
    }

    public int getIndexedFrameSize() {
        return indexedFrameSize;
    }
}
