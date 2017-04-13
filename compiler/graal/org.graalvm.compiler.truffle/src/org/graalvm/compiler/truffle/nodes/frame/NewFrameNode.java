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
package org.graalvm.compiler.truffle.nodes.frame;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
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
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.spi.VirtualizableAllocation;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedAssumption;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.nodes.AssumptionValidAssumption;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * Intrinsic node representing the call for creating a frame in the {@link OptimizedCallTarget}
 * class.
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
    private final FrameSlot[] frameSlots;

    private final SpeculationReason intrinsifyAccessorsSpeculation;

    static final class IntrinsifyFrameAccessorsSpeculationReason implements SpeculationReason {
        private final FrameDescriptor frameDescriptor;

        IntrinsifyFrameAccessorsSpeculationReason(FrameDescriptor frameDescriptor) {
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

        this.virtualFrame = graph.add(new VirtualInstanceNode(frameType, frameFields, true));
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
        /*
         * The new frame is created with "ensureVirtualized" enabled, so that it cannot be
         * materialized. This can only be lifted by a AllowMaterializeNode, which corresponds to a
         * frame.materialize() call.
         */
        tool.createVirtualObject(virtualFrame, frameEntryState, Collections.<MonitorIdNode> emptyList(), true);
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
