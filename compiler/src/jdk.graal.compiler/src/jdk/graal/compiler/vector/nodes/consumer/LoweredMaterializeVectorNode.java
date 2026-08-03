/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.consumer;

import static jdk.graal.compiler.nodeinfo.InputType.Association;

import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.producer.FillVectorNode;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.extended.PublishWritesNode;
import jdk.graal.compiler.nodes.java.AbstractNewArrayNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Allocate a new array and initialize it. This node stores the initial values with the raw memory
 * access semantics of {@link WriteNode}. The initializing write is not a side effect that can be
 * observed outside this node. Therefore this, like other allocating nodes, is not a memory kill.
 */
@NodeInfo(allowedUsageTypes = {Association})
public final class LoweredMaterializeVectorNode extends AbstractMaterializeVectorNode implements IterableNodeType {

    public static final NodeClass<LoweredMaterializeVectorNode> TYPE = NodeClass.create(LoweredMaterializeVectorNode.class);

    /** @see LowerableVectorConsumer#vectorLoopMarker() */
    @OptionalInput(Association) VectorLoopMarkerNode vectorLoopMarker;

    private final int baseOffset;
    private final int elementStride;
    private final JavaKind elementKind;
    private final BarrierType barrierType;
    private final double trustedBodyIterations;

    public LoweredMaterializeVectorNode(Allocator allocator, Stamp stamp, VectorNode vector, ValueNode length, int baseOffset, int elementStride, BarrierType barrierType,
                    double trustedBodyIterations) {
        super(TYPE, allocator, stamp, vector.asNode(), length);
        assert NumUtil.assertNonNegativeInt(elementStride);
        this.baseOffset = baseOffset;
        this.elementStride = elementStride;
        this.elementKind = allocator.getArrayKind();
        this.barrierType = barrierType;
        this.trustedBodyIterations = trustedBodyIterations;
    }

    public LoweredMaterializeVectorNode(Allocator allocator, Stamp stamp, VectorNode vector, ValueNode length, int baseOffset, int elementStride, BarrierType barrierType) {
        this(allocator, stamp, vector, length, baseOffset, elementStride, barrierType, -1.0);
    }

    @SuppressWarnings("try")
    public void materialize(MetaAccessProvider runtime) {
        try (DebugCloseable nsp = withNodeSourcePosition()) {
            boolean inVectorLoop = usages().filter(VectorLoopNode.class).isNotEmpty();
            AbstractNewArrayNode newArray = graph().add(getAllocator().createAllocationNode(runtime, getLength()));
            newArray.setStateBefore(stateBefore());
            newArray.setEmitMemoryBarrier(emitMemoryBarrier());

            if (getVector() instanceof FillVectorNode && ((FillVectorNode) getVector()).getElement().isDefaultConstant() && !inVectorLoop) {
                // Fall back to normal memory zeroing.
                newArray.setFillContents(true);
                this.replaceAtUsages(graph().start(), InputType.Memory);
                graph().replaceFixedWithFixed(this, newArray);
            } else {
                AddressNode address = createStartAddress(newArray);
                VectorWriteNode vectorWrite = graph().add(new VectorWriteNode(address, NamedLocationIdentity.getArrayLocation(elementKind), getVector().asNode(), getLength(), elementStride, true,
                                barrierType));
                vectorWrite.setLastLocationAccess(graph().start());
                vectorWrite.setTrustedBodyIterations(trustedBodyIterations);

                if (inVectorLoop) {
                    /*
                     * This node was lowered from a write that was part of a vector loop. Make sure
                     * the new write replaces it in that group.
                     */
                    this.replaceAtUsages(vectorWrite, InputType.Association);
                    vectorWrite.setVectorLoopMarker(this.vectorLoopMarker());
                }
                PublishWritesNode anchor = graph().add(new PublishWritesNode(newArray));
                graph().replaceFixedWithFixed(this, anchor);
                graph().addBeforeFixed(anchor, vectorWrite);
                graph().addBeforeFixed(vectorWrite, newArray);
                MembarNode memBar = graph().add(MembarNode.forInitialization());
                graph().addAfterFixed(anchor, memBar);
            }
        }
    }

    private AddressNode createStartAddress(AbstractNewArrayNode newArray) {
        return graph().unique(new OffsetAddressNode(newArray, ConstantNode.forLong(baseOffset, graph())));
    }

    public int getBaseOffset() {
        return baseOffset;
    }

    public int getElementStride() {
        return elementStride;
    }

    @Override
    public void lower(LoweringTool tool) {
        // Nothing to do during normal lowering, this node is lowered further by vector
        // materialization.
    }

    /** @see LowerableVectorConsumer#vectorLoopMarker() */
    public VectorLoopMarkerNode vectorLoopMarker() {
        return vectorLoopMarker;
    }

    public void setVectorLoopMarker(VectorLoopMarkerNode vectorLoopMarker) {
        GraalError.guarantee(this.vectorLoopMarker == null, "vectorLoopMarker may only be set once");
        updateUsages(this.vectorLoopMarker, vectorLoopMarker);
        this.vectorLoopMarker = vectorLoopMarker;
    }
}
