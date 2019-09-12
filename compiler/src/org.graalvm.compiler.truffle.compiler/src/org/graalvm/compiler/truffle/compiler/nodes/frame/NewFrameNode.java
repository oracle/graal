/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
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

    public static final NodeClass<NewFrameNode> TYPE = NodeClass.create(NewFrameNode.class);
    @Input ValueNode descriptor;
    @Input ValueNode arguments;

    @Input VirtualObjectNode virtualFrame;
    @Input VirtualObjectNode virtualFrameObjectArray;
    @OptionalInput VirtualObjectNode virtualFramePrimitiveArray;
    @OptionalInput VirtualObjectNode virtualFrameTagArray;
    @Input NodeInputList<ValueNode> smallIntConstants;

    @Input private ValueNode frameDefaultValue;
    private final boolean intrinsifyAccessors;
    private final JavaKind[] frameSlotKinds;
    private final int frameSize;

    private final SpeculationReason intrinsifyAccessorsSpeculation;

    private static JavaKind asJavaKind(JavaConstant frameSlotTag) {
        int tagValue = frameSlotTag.asInt();
        JavaKind rawKind = TruffleCompilerRuntime.getRuntime().getJavaKindForFrameSlotKind(tagValue);
        switch (rawKind) {
            case Boolean:
            case Byte:
            case Int:
                return JavaKind.Int;
            case Double:
            case Float:
                return rawKind;
            case Long:
            case Object:
            case Illegal:
                return JavaKind.Long;
        }
        throw new IllegalStateException("Unexpected frame slot kind tag: " + tagValue);
    }

    private static final SpeculationReasonGroup INTRINSIFY_FRAME_ACCESSORS_SPECULATIONS = new SpeculationReasonGroup("IntrinsifyFrameAccessor");

    public NewFrameNode(GraphBuilderContext b, ValueNode frameDescriptorNode, ValueNode arguments, KnownTruffleTypes types) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createExactTrusted(types.classFrameClass)));

        this.descriptor = frameDescriptorNode;
        this.arguments = arguments;

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

        JavaKind[] frameSlotKindsCandidate = new JavaKind[frameLength];
        int limit = -1;
        for (int i = 0; i < slotsArrayLength; i++) {
            JavaConstant slot = constantReflection.readArrayElement(slotArray, i);
            if (slot.isNonNull()) {
                JavaConstant slotKind = constantReflection.readFieldValue(types.fieldFrameSlotKind, slot);
                JavaConstant slotIndex = constantReflection.readFieldValue(types.fieldFrameSlotIndex, slot);
                if (slotKind.isNonNull() && slotIndex.isNonNull()) {
                    final JavaKind kind = asJavaKind(constantReflection.readFieldValue(types.fieldFrameSlotKindTag, slotKind));
                    final int index = slotIndex.asInt();
                    limit = Math.max(index, limit);
                    if (index >= frameLength) {
                        /*
                         * Since the size and slotArrayList of the FrameDescriptor are read
                         * asynchronously we have to defensively check that we did not get old size
                         * not matching the slot's index. If we did the frameSlotKinds array has to
                         * be expanded.
                         */
                        final JavaKind[] newArray = new JavaKind[index + 1];
                        System.arraycopy(frameSlotKindsCandidate, 0, newArray, 0, frameSlotKindsCandidate.length);
                        frameSlotKindsCandidate = newArray;
                    }
                    frameSlotKindsCandidate[index] = kind;
                }
            }
        }
        this.frameSlotKinds = frameSlotKindsCandidate;
        this.frameSize = limit + 1;

        ResolvedJavaType frameType = types.classFrameClass;
        ResolvedJavaField[] frameFields = frameType.getInstanceFields(true);
        ResolvedJavaField localsField = findField(frameFields, "locals");
        ResolvedJavaField primitiveLocalsField = findField(frameFields, "primitiveLocals");
        ResolvedJavaField tagsField = findField(frameFields, "tags");

        this.virtualFrame = graph.add(new VirtualInstanceNode(frameType, frameFields, true));
        this.virtualFrameObjectArray = graph.add(new VirtualArrayNode((ResolvedJavaType) localsField.getType().getComponentType(), frameSize));
        if (primitiveLocalsField != null) {
            this.virtualFramePrimitiveArray = graph.add(new VirtualArrayNode((ResolvedJavaType) primitiveLocalsField.getType().getComponentType(), frameSize));
            this.virtualFrameTagArray = graph.add(new VirtualArrayNode((ResolvedJavaType) tagsField.getType().getComponentType(), frameSize));
        }

        ValueNode[] c = new ValueNode[TruffleCompilerRuntime.getRuntime().getFrameSlotKindTagsCount()];
        for (int i = 0; i < c.length; i++) {
            c[i] = ConstantNode.forInt(i, graph);
        }
        this.smallIntConstants = new NodeInputList<>(this, c);
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
        return index >= 0 && index < frameSize && frameSlotKinds[index] != null;
    }

    private static ResolvedJavaField findField(ResolvedJavaField[] fields, String fieldName) {
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
        ResolvedJavaField[] frameFields = frameType.getInstanceFields(true);

        ResolvedJavaField descriptorField = findField(frameFields, "descriptor");
        ResolvedJavaField argumentsField = findField(frameFields, "arguments");
        ResolvedJavaField localsField = findField(frameFields, "locals");
        ResolvedJavaField primitiveLocalsField = findField(frameFields, "primitiveLocals");
        ResolvedJavaField tagsField = findField(frameFields, "tags");

        ValueNode[] objectArrayEntryState = new ValueNode[frameSize];
        ValueNode[] primitiveArrayEntryState = new ValueNode[frameSize];
        ValueNode[] tagArrayEntryState = new ValueNode[frameSize];

        if (frameSize > 0) {
            Arrays.fill(objectArrayEntryState, frameDefaultValue);
            if (virtualFrameTagArray != null) {
                Arrays.fill(tagArrayEntryState, smallIntConstants.get(0));
            }
            if (virtualFramePrimitiveArray != null) {
                for (int i = 0; i < frameSize; i++) {
                    JavaKind kind = frameSlotKinds[i];
                    if (kind == null) {
                        kind = JavaKind.Int;
                    }
                    primitiveArrayEntryState[i] = ConstantNode.defaultForKind(kind, graph());
                }
            }
        }

        tool.createVirtualObject(virtualFrameObjectArray, objectArrayEntryState, Collections.<MonitorIdNode> emptyList(), false);
        if (virtualFramePrimitiveArray != null) {
            tool.createVirtualObject(virtualFramePrimitiveArray, primitiveArrayEntryState, Collections.<MonitorIdNode> emptyList(), false);
        }
        if (virtualFrameTagArray != null) {
            tool.createVirtualObject(virtualFrameTagArray, tagArrayEntryState, Collections.<MonitorIdNode> emptyList(), false);
        }

        assert frameFields.length == 5 || frameFields.length == 3;
        ValueNode[] frameEntryState = new ValueNode[frameFields.length];
        List<ResolvedJavaField> frameFieldList = Arrays.asList(frameFields);
        frameEntryState[frameFieldList.indexOf(descriptorField)] = getDescriptor();
        frameEntryState[frameFieldList.indexOf(argumentsField)] = getArguments();
        frameEntryState[frameFieldList.indexOf(localsField)] = virtualFrameObjectArray;
        if (primitiveLocalsField != null) {
            frameEntryState[frameFieldList.indexOf(primitiveLocalsField)] = virtualFramePrimitiveArray;
        }
        if (tagsField != null) {
            frameEntryState[frameFieldList.indexOf(tagsField)] = virtualFrameTagArray;
        }
        /*
         * The new frame is created with "ensureVirtualized" enabled, so that it cannot be
         * materialized. This can only be lifted by a AllowMaterializeNode, which corresponds to a
         * frame.materialize() call.
         */
        tool.createVirtualObject(virtualFrame, frameEntryState, Collections.<MonitorIdNode> emptyList(), true);
        tool.replaceWithVirtual(virtualFrame);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        return this;
    }
}
