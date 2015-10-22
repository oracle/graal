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
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.IterableNodeType;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.AbstractEndNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.Invoke;
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
import com.oracle.graal.truffle.OptimizedAssumption;
import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.graal.truffle.nodes.AssumptionValidAssumption;
import com.oracle.truffle.api.frame.FrameDescriptor;
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

    private final SnippetReflectionProvider snippetReflection;

    public NewFrameNode(SnippetReflectionProvider snippetReflection, Stamp stamp, ValueNode descriptor, ValueNode arguments) {
        super(TYPE, stamp);
        this.descriptor = descriptor;
        this.arguments = arguments;

        /*
         * This class requires access to the objects encapsulated in Constants, and therefore breaks
         * the compiler-VM separation of object constants.
         */
        this.snippetReflection = snippetReflection;
    }

    public NewFrameNode(SnippetReflectionProvider snippetReflection, ResolvedJavaType frameType, ValueNode descriptor, ValueNode arguments) {
        this(snippetReflection, StampFactory.exactNonNull(frameType), descriptor, arguments);
    }

    public ValueNode getDescriptor() {
        return descriptor;
    }

    public ValueNode getArguments() {
        return arguments;
    }

    private FrameDescriptor getConstantFrameDescriptor() {
        assert descriptor.isConstant() && !descriptor.isNullConstant();
        return snippetReflection.asObject(FrameDescriptor.class, descriptor.asJavaConstant());
    }

    private int getFrameSize() {
        return getConstantFrameDescriptor().getSize();
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
        if (!descriptor.isConstant()) {
            return;
        }

        int frameSize = getFrameSize();

        ResolvedJavaType frameType = stamp().javaType(tool.getMetaAccessProvider());
        ResolvedJavaField[] frameFields = frameType.getInstanceFields(true);

        ResolvedJavaField descriptorField = findField(frameFields, "descriptor");
        ResolvedJavaField argumentsField = findField(frameFields, "arguments");
        ResolvedJavaField localsField = findField(frameFields, "locals");
        ResolvedJavaField primitiveLocalsField = findField(frameFields, "primitiveLocals");
        ResolvedJavaField tagsField = findField(frameFields, "tags");

        VirtualObjectNode virtualFrame = new VirtualOnlyInstanceNode(frameType, frameFields);
        VirtualObjectNode virtualFrameObjectArray = new VirtualArrayNode((ResolvedJavaType) localsField.getType().getComponentType(), frameSize);
        VirtualObjectNode virtualFramePrimitiveArray = (primitiveLocalsField == null ? null : new VirtualArrayNode((ResolvedJavaType) primitiveLocalsField.getType().getComponentType(), frameSize));
        VirtualObjectNode virtualFrameTagArray = (primitiveLocalsField == null ? null : new VirtualArrayNode((ResolvedJavaType) tagsField.getType().getComponentType(), frameSize));

        ValueNode[] objectArrayEntryState = new ValueNode[frameSize];
        ValueNode[] primitiveArrayEntryState = new ValueNode[frameSize];
        ValueNode[] tagArrayEntryState = new ValueNode[frameSize];

        if (frameSize > 0) {
            FrameDescriptor frameDescriptor = getConstantFrameDescriptor();
            ConstantNode objectDefault = ConstantNode.forConstant(snippetReflection.forObject(frameDescriptor.getDefaultValue()), tool.getMetaAccessProvider(), graph());
            ConstantNode tagDefault = ConstantNode.forByte((byte) 0, graph());
            Arrays.fill(objectArrayEntryState, objectDefault);
            if (virtualFrameTagArray != null) {
                Arrays.fill(tagArrayEntryState, tagDefault);
            }
            if (virtualFramePrimitiveArray != null) {
                for (int i = 0; i < frameSize; i++) {
                    primitiveArrayEntryState[i] = initialPrimitiveValue(frameDescriptor.getSlots().get(i).getKind());
                }
            }
            graph().getAssumptions().record(new AssumptionValidAssumption((OptimizedAssumption) frameDescriptor.getVersion()));
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
