/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle.nodes.frame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.compiler.common.type.TypeReference;
import com.oracle.graal.graph.IterableNodeType;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.AbstractEndNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.java.MonitorIdNode;
import com.oracle.graal.nodes.java.StoreFieldNode;
import com.oracle.graal.nodes.spi.VirtualizableAllocation;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.nodes.virtual.AllocatedObjectNode;
import com.oracle.graal.nodes.virtual.LockState;
import com.oracle.graal.nodes.virtual.VirtualArrayNode;
import com.oracle.graal.nodes.virtual.VirtualInstanceNode;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;
import com.oracle.graal.truffle.GraalTruffleRuntime;
import com.oracle.graal.truffle.OptimizedAssumption;
import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.graal.truffle.nodes.AssumptionValidAssumption;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;

/**
 * Intrinsic node representing the call for creating a frame in the {@link OptimizedCallTarget}
 * class.
 */
@NodeInfo
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
    private final FrameSlot[] frameSlots;

    private final SpeculationReason intrinsifyAccessorsSpeculation;

    static final class IntrinsifyFrameAccessorsSpeculationReason implements SpeculationReason {
        private final FrameDescriptor frameDescriptor;

        public IntrinsifyFrameAccessorsSpeculationReason(FrameDescriptor frameDescriptor) {
            this.frameDescriptor = frameDescriptor;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof IntrinsifyFrameAccessorsSpeculationReason && ((IntrinsifyFrameAccessorsSpeculationReason) obj).frameDescriptor == this.frameDescriptor;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(frameDescriptor);
        }
    }

    public NewFrameNode(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, StructuredGraph graph, ResolvedJavaType frameType, FrameDescriptor frameDescriptor,
                    ValueNode frameDescriptorNode, ValueNode arguments) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createExactTrusted(frameType)));

        this.descriptor = frameDescriptorNode;
        this.arguments = arguments;

        /*
         * We access the FrameDescriptor only here and copy out all relevant data. So later
         * modifications to the FrameDescriptor by the running Truffle thread do not interfere. The
         * frame version assumption is registered first, so that we get invalidated in case the
         * FrameDescriptor changes.
         */
        graph.getAssumptions().record(new AssumptionValidAssumption((OptimizedAssumption) frameDescriptor.getVersion()));

        /*
         * We only want to intrinsify get/set/is accessor methods of a virtual frame when we expect
         * that the frame is not going to be materialized. Materialization results in heap-based
         * data arrays, which means that set-methods need a FrameState. Most of the benefit of
         * accessor method intrinsification is avoding the FrameState creation during partial
         * evaluation.
         */
        this.intrinsifyAccessorsSpeculation = new IntrinsifyFrameAccessorsSpeculationReason(frameDescriptor);
        this.intrinsifyAccessors = !GraalTruffleRuntime.getRuntime().getFrameMaterializeCalled(frameDescriptor) && graph.getSpeculationLog().maySpeculate(intrinsifyAccessorsSpeculation);

        this.frameDefaultValue = ConstantNode.forConstant(snippetReflection.forObject(frameDescriptor.getDefaultValue()), metaAccess, graph);
        this.frameSlots = frameDescriptor.getSlots().toArray(new FrameSlot[0]);

        ResolvedJavaField[] frameFields = frameType.getInstanceFields(true);

        ResolvedJavaField localsField = findField(frameFields, "locals");
        ResolvedJavaField primitiveLocalsField = findField(frameFields, "primitiveLocals");
        ResolvedJavaField tagsField = findField(frameFields, "tags");

        this.virtualFrame = graph.add(new VirtualOnlyInstanceNode(frameType, frameFields));
        this.virtualFrameObjectArray = graph.add(new VirtualArrayNode((ResolvedJavaType) localsField.getType().getComponentType(), frameSlots.length));
        if (primitiveLocalsField != null) {
            this.virtualFramePrimitiveArray = graph.add(new VirtualArrayNode((ResolvedJavaType) primitiveLocalsField.getType().getComponentType(), frameSlots.length));
            this.virtualFrameTagArray = graph.add(new VirtualArrayNode((ResolvedJavaType) tagsField.getType().getComponentType(), frameSlots.length));
        }

        ValueNode[] c = new ValueNode[FrameSlotKind.values().length];
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

    private static ResolvedJavaField findField(ResolvedJavaField[] fields, String fieldName) {
        for (ResolvedJavaField field : fields) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    @NodeInfo
    public static final class VirtualOnlyInstanceNode extends VirtualInstanceNode {

        public static final NodeClass<VirtualOnlyInstanceNode> TYPE = NodeClass.create(VirtualOnlyInstanceNode.class);
        protected boolean allowMaterialization;

        public VirtualOnlyInstanceNode(ResolvedJavaType type, ResolvedJavaField[] fields) {
            super(TYPE, type, fields, true);
        }

        @Override
        public ValueNode getMaterializedRepresentation(FixedNode fixed, ValueNode[] entries, LockState locks) {
            if (allowMaterialization) {
                return super.getMaterializedRepresentation(fixed, entries, locks);
            }
            return getMaterializedRepresentationHelper(this, fixed);
        }

        public void setAllowMaterialization(boolean b) {
            this.allowMaterialization = b;
        }
    }

    public static ValueNode getMaterializedRepresentationHelper(VirtualObjectNode virtualNode, FixedNode fixed) {
        if (fixed instanceof MaterializeFrameNode || fixed instanceof AbstractEndNode || fixed instanceof ForceMaterializeNode) {
            // We need to conservatively assume that a materialization of a virtual frame can also
            // happen at a merge point.
            return new AllocatedObjectNode(virtualNode);
        }
        String escapeReason;
        if (fixed instanceof StoreFieldNode) {
            escapeReason = "Must not store virtual frame object into a field.";
        } else if (fixed instanceof Invoke) {
            escapeReason = "Must not pass virtual frame object into an invoke that cannot be inlined.";
        } else {
            escapeReason = "Must not let virtual frame object escape at node " + fixed + ".";
        }

        Throwable exception = new JVMCIError(escapeReason +
                        " Insert a call to VirtualFrame.materialize() to convert the instance to a materialized frame object (source position of following stack trace is approximate)");
        throw GraphUtil.approxSourceException(fixed, exception);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        int frameSize = frameSlots.length;

        ResolvedJavaType frameType = stamp().javaType(tool.getMetaAccessProvider());
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
                    primitiveArrayEntryState[i] = initialPrimitiveValue(frameSlots[i].getKind());
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
        tool.createVirtualObject(virtualFrame, frameEntryState, Collections.<MonitorIdNode> emptyList(), false);
        tool.replaceWithVirtual(virtualFrame);
    }

    private ValueNode initialPrimitiveValue(FrameSlotKind kind) {
        JavaKind graalKind = null;
        switch (kind) {
            case Boolean:
            case Byte:
            case Int:
                graalKind = JavaKind.Int;
                break;
            case Double:
                graalKind = JavaKind.Double;
                break;
            case Float:
                graalKind = JavaKind.Float;
                break;
            case Long:
                graalKind = JavaKind.Long;
                break;
            case Object:
            case Illegal:
                // won't be stored in the primitive array, so default to long
                graalKind = JavaKind.Long;
                break;
            default:
                throw new IllegalStateException("Unexpected frame slot kind: " + kind);
        }

        return ConstantNode.defaultForKind(graalKind, graph());
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        } else {
            return this;
        }
    }
}
