/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.producer;

import jdk.graal.compiler.vector.nodes.VectorNode;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.vector.nodes.AbstractVectorNode;
import jdk.graal.compiler.vector.nodes.VectorAccess;
import jdk.graal.compiler.vector.nodes.consumer.VectorGuardNode;
import jdk.graal.compiler.vector.nodes.op.FloatingVectorGatherNode;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.MultiGuardNode;
import jdk.graal.compiler.nodes.memory.AddressableMemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;

/**
 * Temporary placeholder for use during loop vectorization. To be replaced by a fixed
 * {@link VectorReadNode} if loop vectorization succeeds. This node must not survive after the loop
 * vectorization phase.
 * </p>
 *
 * We don't place fixed vector reads directly because the read's guard may itself be replaced by a
 * vectorized guard. If that is the case, the read must be placed after the vectorized guard.
 * However, LoopVectorizationPhase delays insertion of those guards until the whole loop is
 * analyzed, while the vectorized versions of reads and other values are produced eagerly during the
 * analysis. So we don't know the correct insertion position for a fixed vector read right away.
 */
@NodeInfo
public final class FloatingVectorReadNode extends AbstractVectorNode implements GuardedNode, AddressableMemoryAccess, VectorAccess, Canonicalizable {
    public static final NodeClass<FloatingVectorReadNode> TYPE = NodeClass.create(FloatingVectorReadNode.class);

    @OptionalInput(InputType.Guard) GuardingNode guard;
    @Input AddressNode address;
    private LocationIdentity location;

    private final BarrierType barrierType;
    @OptionalInput(InputType.Memory) MemoryKill lastLocationAccess;

    protected final int elementStride;

    public FloatingVectorReadNode(AddressNode address, LocationIdentity location, int elementStride, VectorStamp stamp, BarrierType barrierType, MemoryKill lastLocationAccess, GuardingNode guard) {
        super(TYPE, stamp);
        this.address = address;
        this.location = location;
        this.elementStride = elementStride;
        this.barrierType = barrierType;
        this.lastLocationAccess = lastLocationAccess;
        this.guard = guard;
    }

    @Override
    public AddressNode getAddress() {
        return address;
    }

    @Override
    public int getElementStride() {
        return elementStride;
    }

    @Override
    public BarrierType getBarrierType() {
        return barrierType;
    }

    @Override
    public boolean canNullCheck() {
        return false;
    }

    @Override
    public void setAddress(AddressNode address) {
        updateUsages(this.address, address);
        this.address = address;
    }

    @Override
    public GuardingNode getGuard() {
        return guard;
    }

    @Override
    public void setGuard(GuardingNode guard) {
        updateUsagesInterface(this.guard, guard);
        this.guard = guard;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return location;
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (graph().isAfterStage(GraphState.StageFlag.VECTOR_MATERIALIZATION)) {
            throw GraalError.shouldNotReachHere("node should not have survived LoopVectorization: " + this);
        }
        return this;
    }

    public void replaceWithFixedRead(FixedWithNextNode loopEntryPoint, Graph.Mark beforeVectorization) {
        VectorReadNode fixedVectorRead = graph().add(new VectorReadNode(address, location, elementStride, getVectorStamp(), barrierType, lastLocationAccess, guard));
        FixedWithNextNode insertionPoint = fixedReadInsertionPoint(loopEntryPoint, beforeVectorization, this, guard);
        graph().addAfterFixed(insertionPoint, fixedVectorRead);
        replaceAndDelete(fixedVectorRead);
    }

    /**
     * Compute the insertion point for a fixed node corresponding to {@code self}, a temporary
     * floating vector read or gather node. If the {@code guard} is newer than the
     * {@code beforeVectorization} mark, the fixed node should be inserted right after that guard.
     * This indicates that the original guard of the vectorized read was inside the loop and is now
     * a {@link VectorGuardNode}. Otherwise, the original guard was somewhere before the vectorized
     * loop, and we can insert the fixed read after the {@code loopEntryPoint}, which is just before
     * the original loop's begin node.
     */
    public static FixedWithNextNode fixedReadInsertionPoint(FixedWithNextNode loopEntryPoint, Graph.Mark beforeVectorization, AbstractVectorNode self, GuardingNode guard) {
        FixedWithNextNode insertionPoint = loopEntryPoint;
        StructuredGraph graph = insertionPoint.graph();
        if (self instanceof FloatingVectorGatherNode) {
            NodeFlood inputs = graph.createNodeFlood();
            inputs.add(self);
            for (Node input : inputs) {
                if (input instanceof VectorGuardNode vectorGuard && graph.isNew(beforeVectorization, vectorGuard)) {
                    if (insertionPoint == loopEntryPoint) {
                        insertionPoint = vectorGuard;
                    } else {
                        for (Node predecessor = vectorGuard.predecessor(); predecessor != null; predecessor = predecessor.predecessor()) {
                            if (predecessor == insertionPoint) {
                                insertionPoint = vectorGuard;
                                break;
                            }
                        }
                    }
                    continue;
                }
                if (input instanceof VectorNode || input instanceof MultiGuardNode) {
                    inputs.addAll(input.inputs());
                }
            }
        } else if (graph.isNew(beforeVectorization, guard.asNode())) {
            if (guard instanceof VectorGuardNode vectorGuard) {
                insertionPoint = vectorGuard;
            } else if (guard instanceof MultiGuardNode multiGuard) {
                for (ValueNode inputGuard : multiGuard.getGuards()) {
                    if (inputGuard instanceof VectorGuardNode vectorGuard && graph.isNew(beforeVectorization, vectorGuard)) {
                        if (insertionPoint == loopEntryPoint) {
                            insertionPoint = vectorGuard;
                        } else {
                            throw GraalError.shouldNotReachHere("expect only one vectorized guard for read: " + self + ", got " + insertionPoint + " before, now found " + inputGuard);
                        }
                    }
                }
            } else {
                throw GraalError.shouldNotReachHereUnexpectedValue(guard);
            }
        }
        return insertionPoint;
    }

}
