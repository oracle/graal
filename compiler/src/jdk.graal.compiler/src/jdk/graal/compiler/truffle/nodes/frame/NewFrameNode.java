/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.nodes.frame;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import jdk.graal.compiler.truffle.KnownTruffleTypes;
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
    public static final byte FrameSlotKindIntTag = 2; // FrameSlotKind.Int.tag
    public static final byte FrameSlotKindDoubleTag = 3; // FrameSlotKind.Double.tag
    public static final byte FrameSlotKindFloatTag = 4; // FrameSlotKind.Float.tag
    public static final byte FrameSlotKindBooleanTag = 5; // FrameSlotKind.Boolean.tag
    public static final byte FrameSlotKindByteTag = 6; // FrameSlotKind.Byte.tag
    public static final byte FrameSlotKindIllegalTag = 7; // FrameSlotKind.Illegal.tag
    public static final byte FrameSlotKindStaticTag = 8; // FrameSlotKind.Static.tag

    public static final NodeClass<NewFrameNode> TYPE = NodeClass.create(NewFrameNode.class);
    @Input ValueNode descriptor;
    @Input ValueNode arguments;

    @Input VirtualInstanceNode virtualFrame;
    @Input NodeInputList<ValueNode> virtualFrameArrays;

    private static final int INDEXED_OBJECT_ARRAY = 0;
    private static final int INDEXED_PRIMITIVE_ARRAY = 1;
    private static final int INDEXED_TAGS_ARRAY = 2;
    private static final int AUXILIARY_SLOTS_ARRAY = 3;

    @Input NodeInputList<ValueNode> smallIntConstants;

    @Input private ValueNode frameDefaultValue;
    private final boolean intrinsifyAccessors;
    private final byte[] indexedFrameSlotKinds;
    private final int indexedFrameSize;
    private final int auxiliarySize;

    private final SpeculationReason intrinsifyAccessorsSpeculation;

    private boolean isBytecodeOSRTransferTarget = false;

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
            case FrameSlotKindStaticTag:
                return FrameSlotKindStaticTag;
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
        super(TYPE, StampFactory.objectNonNull(TypeReference.createExactTrusted(types.FrameWithoutBoxing)));

        this.descriptor = frameDescriptorNode;
        this.arguments = arguments;
        this.types = types;

        StructuredGraph graph = b.getGraph();
        MetaAccessProvider metaAccess = b.getMetaAccess();
        ConstantReflectionProvider constantReflection = b.getConstantReflection();
        JavaConstant frameDescriptor = frameDescriptorNode.asJavaConstant();

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
        if (!constantReflection.readFieldValue(types.FrameDescriptor_materializeCalled, frameDescriptor).asBoolean()) {
            SpeculationLog speculationLog = graph.getSpeculationLog();
            intrinsify = speculationLog != null && speculationLog.maySpeculate(intrinsifyAccessorsSpeculation);
        }
        this.intrinsifyAccessors = intrinsify;

        JavaConstant defaultValue = constantReflection.readFieldValue(types.FrameDescriptor_defaultValue, frameDescriptor);
        this.frameDefaultValue = ConstantNode.forConstant(defaultValue, metaAccess, graph);

        JavaConstant indexedTagsArray = constantReflection.readFieldValue(types.FrameDescriptor_indexedSlotTags, frameDescriptor);
        if (types.FrameDescriptor_indexedSlotCount == null) {
            this.indexedFrameSize = constantReflection.readArrayLength(indexedTagsArray);
        } else {
            this.indexedFrameSize = constantReflection.readFieldValue(types.FrameDescriptor_indexedSlotCount, frameDescriptor).asInt();
        }

        byte[] indexedFrameSlotKindsCandidate = new byte[indexedFrameSize];
        if (indexedTagsArray.isNull()) {
            Arrays.fill(indexedFrameSlotKindsCandidate, FrameSlotKindLongTag);
        } else {
            final int indexedTagsArrayLength = constantReflection.readArrayLength(indexedTagsArray);
            for (int i = 0; i < indexedTagsArrayLength; i++) {
                final int slot = constantReflection.readArrayElement(indexedTagsArray, i).asInt();
                if (slot == FrameSlotKindStaticTag) {
                    indexedFrameSlotKindsCandidate[i] = FrameSlotKindStaticTag;
                } else {
                    indexedFrameSlotKindsCandidate[i] = FrameSlotKindLongTag;
                }
            }
        }
        this.indexedFrameSlotKinds = indexedFrameSlotKindsCandidate;

        this.auxiliarySize = constantReflection.readFieldValue(types.FrameDescriptor_auxiliarySlotCount, frameDescriptor).asInt();

        ResolvedJavaType frameType = types.FrameWithoutBoxing;

        ResolvedJavaField[] frameFields = frameType.getInstanceFields(true);
        this.virtualFrame = graph.add(new VirtualInstanceNode(frameType, frameFields, true));

        ValueNode emptyObjectArray = constantObject(graph, b, types.FrameWithoutBoxing_EMPTY_OBJECT_ARRAY);
        ValueNode emptyLongArray = constantObject(graph, b, types.FrameWithoutBoxing_EMPTY_LONG_ARRAY);
        ValueNode emptyByteArray = constantObject(graph, b, types.FrameWithoutBoxing_EMPTY_BYTE_ARRAY);
        boolean emptyArraysAvailable = emptyObjectArray != null && emptyLongArray != null && emptyByteArray != null;

        ValueNode indexedLocals;
        ValueNode indexedPrimitiveLocals;
        ValueNode indexedTags;
        if (indexedFrameSize == 0 && emptyArraysAvailable) {
            indexedLocals = emptyObjectArray;
            indexedPrimitiveLocals = emptyLongArray;
            indexedTags = emptyByteArray;
        } else {
            indexedLocals = graph.add(new VirtualArrayNode((ResolvedJavaType) types.FrameWithoutBoxing_indexedLocals.getType().getComponentType(), indexedFrameSize));
            indexedPrimitiveLocals = graph.add(new VirtualArrayNode((ResolvedJavaType) types.FrameWithoutBoxing_indexedPrimitiveLocals.getType().getComponentType(), indexedFrameSize));
            indexedTags = graph.add(new VirtualArrayNode((ResolvedJavaType) types.FrameWithoutBoxing_indexedTags.getType().getComponentType(), indexedFrameSize));
        }
        ValueNode auxiliarySlotsArray;
        if (auxiliarySize == 0 && emptyArraysAvailable) {
            auxiliarySlotsArray = emptyObjectArray;
        } else {
            auxiliarySlotsArray = graph.add(new VirtualArrayNode((ResolvedJavaType) types.FrameWithoutBoxing_auxiliarySlots.getType().getComponentType(), auxiliarySize));
        }
        this.virtualFrameArrays = new NodeInputList<>(this, new ValueNode[]{indexedLocals, indexedPrimitiveLocals, indexedTags, auxiliarySlotsArray});

        // We double the frame slot kind tags count to support static assertion tags.
        ValueNode[] c = new ValueNode[types.FrameSlotKind_tagIndexToJavaKind.length * 2];
        for (int i = 0; i < c.length; i++) {
            c[i] = ConstantNode.forInt(i, graph);
        }

        this.smallIntConstants = new NodeInputList<>(this, c);
    }

    private static ValueNode constantObject(StructuredGraph graph, GraphBuilderContext b, ResolvedJavaField field) {
        JavaConstant fieldValue = b.getConstantReflection().readFieldValue(field, null);
        return fieldValue == null ? null : ConstantNode.forConstant(fieldValue, b.getMetaAccess(), graph);
    }

    public byte[] getIndexedFrameSlotKinds() {
        return indexedFrameSlotKinds;
    }

    public boolean isStatic(int slot) {
        return indexedFrameSlotKinds[slot] == FrameSlotKindStaticTag;
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
        assert frameType.equals(types.FrameWithoutBoxing);
        NodeSourcePosition sourcePosition = getNodeSourcePosition();

        ConstantNode defaultLong = ConstantNode.defaultForKind(JavaKind.Long, graph());

        if (virtualFrameArrays.get(INDEXED_OBJECT_ARRAY) instanceof VirtualArrayNode) {
            ValueNode[] indexedObjectArrayEntryState = new ValueNode[indexedFrameSize];
            ValueNode[] indexedPrimitiveArrayEntryState = new ValueNode[indexedFrameSize];
            ValueNode[] indexedTagArrayEntryState = new ValueNode[indexedFrameSize];

            JavaConstant illegalDefaultValue = null;
            // the field may not be defined in older Truffle versions.
            if (types.FrameDescriptor_illegalDefaultValue != null) {
                illegalDefaultValue = tool.getConstantReflection().readFieldValue(types.FrameDescriptor_illegalDefaultValue, null);
            }

            if (illegalDefaultValue != null && tool.getConstantReflection().constantEquals(frameDefaultValue.asJavaConstant(), illegalDefaultValue)) {
                Arrays.fill(indexedObjectArrayEntryState, ConstantNode.defaultForKind(JavaKind.Object, graph()));
                Arrays.fill(indexedTagArrayEntryState, smallIntConstants.get(FrameSlotKindIllegalTag));
            } else {
                Arrays.fill(indexedObjectArrayEntryState, frameDefaultValue);
                Arrays.fill(indexedTagArrayEntryState, smallIntConstants.get(0));
            }

            Arrays.fill(indexedPrimitiveArrayEntryState, defaultLong);
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

        assert types.FrameWithoutBoxing_instanceFields.length == 6 : "Must have 6 known fields but found " + types.FrameWithoutBoxing_instanceFields;
        ValueNode[] frameEntryState = new ValueNode[types.FrameWithoutBoxing_instanceFields.length];
        List<ResolvedJavaField> frameFieldList = Arrays.asList(types.FrameWithoutBoxing_instanceFields);
        frameEntryState[frameFieldList.indexOf(types.FrameWithoutBoxing_descriptor)] = getDescriptor();
        frameEntryState[frameFieldList.indexOf(types.FrameWithoutBoxing_indexedLocals)] = virtualFrameArrays.get(INDEXED_OBJECT_ARRAY);
        frameEntryState[frameFieldList.indexOf(types.FrameWithoutBoxing_indexedPrimitiveLocals)] = virtualFrameArrays.get(INDEXED_PRIMITIVE_ARRAY);
        frameEntryState[frameFieldList.indexOf(types.FrameWithoutBoxing_indexedTags)] = virtualFrameArrays.get(INDEXED_TAGS_ARRAY);
        frameEntryState[frameFieldList.indexOf(types.FrameWithoutBoxing_arguments)] = getArguments();
        frameEntryState[frameFieldList.indexOf(types.FrameWithoutBoxing_auxiliarySlots)] = virtualFrameArrays.get(AUXILIARY_SLOTS_ARRAY);
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

    public ValueNode getTagArray(VirtualFrameAccessType type) {
        /*
         * If one of these casts fails, there's an access into a zero-length array, which should not
         * get to this point.
         */
        switch (type) {
            case Indexed:
                return virtualFrameArrays.get(INDEXED_TAGS_ARRAY);
            case Auxiliary:
                return null;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(type); // ExcludeFromJacocoGeneratedReport
        }
    }

    public ValueNode getObjectArray(VirtualFrameAccessType type) {
        /*
         * If one of these casts fails, there's an access into a zero-length array, which should not
         * get to this point.
         */
        switch (type) {
            case Indexed:
                return virtualFrameArrays.get(INDEXED_OBJECT_ARRAY);
            case Auxiliary:
                return virtualFrameArrays.get(AUXILIARY_SLOTS_ARRAY);
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(type); // ExcludeFromJacocoGeneratedReport
        }
    }

    public ValueNode getPrimitiveArray(VirtualFrameAccessType type) {
        /*
         * If one of these casts fails, there's an access into a zero-length array, which should not
         * get to this point.
         */
        switch (type) {
            case Indexed:
                return virtualFrameArrays.get(INDEXED_PRIMITIVE_ARRAY);
            case Auxiliary:
                return null;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(type); // ExcludeFromJacocoGeneratedReport
        }
    }

    public int getIndexedFrameSize() {
        return indexedFrameSize;
    }

    public boolean isBytecodeOSRTransferTarget() {
        return isBytecodeOSRTransferTarget;
    }

    public void setBytecodeOSRTransferTarget() {
        isBytecodeOSRTransferTarget = true;
    }
}
