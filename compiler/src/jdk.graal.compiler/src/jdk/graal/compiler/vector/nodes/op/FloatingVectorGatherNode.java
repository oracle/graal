/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.op;

import static jdk.graal.compiler.nodeinfo.InputType.Association;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.vector.nodes.AbstractVectorNode;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.producer.FloatingVectorReadNode;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;

/**
 * Temporary placeholder for use during loop vectorization. To be replaced by a fixed
 * {@link VectorGatherNode} if loop vectorization succeeds. This node must not survive after the
 * loop vectorization phase.
 */
// @formatter:off
@NodeInfo(allowedUsageTypes = {Association},
          cycles = CYCLES_UNKNOWN,
          cyclesRationale = "We cannot argue about vector nodes statically.",
          size = SIZE_UNKNOWN,
          sizeRationale = "We cannot argue about vector nodes statically.")
// @formatter:on
public class FloatingVectorGatherNode extends AbstractVectorNode implements GuardedNode, MemoryAccess, Canonicalizable {
    public static final NodeClass<FloatingVectorGatherNode> TYPE = NodeClass.create(FloatingVectorGatherNode.class);

    @OptionalInput(InputType.Guard) GuardingNode guard;
    @Input ValueNode base;
    @Input ValueNode offsets;
    private LocationIdentity location;
    private final BarrierType barrierType;
    @OptionalInput(InputType.Memory) MemoryKill lastLocationAccess;

    public FloatingVectorGatherNode(ValueNode base, ValueNode offsets, LocationIdentity location, Stamp stamp, BarrierType barrierType, MemoryKill lastLocationAccess, GuardingNode guard) {
        super(TYPE, stamp);
        assert !(base instanceof AbstractVectorNode) : "vector gather's base must be scalar, got: " + base;
        this.base = base;
        this.offsets = offsets.asNode();
        this.location = location;
        this.barrierType = barrierType;
        this.lastLocationAccess = lastLocationAccess;
        this.guard = guard;
    }

    public BarrierType getBarrierType() {
        return barrierType;
    }

    public ValueNode getBase() {
        return base;
    }

    public VectorNode getOffsets() {
        return (VectorNode) offsets;
    }

    @Override
    public GuardingNode getGuard() {
        return guard;
    }

    @Override
    public void setGuard(GuardingNode newGuard) {
        updateUsagesInterface(this.guard, newGuard);
        this.guard = newGuard;
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
    public void setLastLocationAccess(MemoryKill lastLocationAccess) {
        if (this.lastLocationAccess != lastLocationAccess) {
            updateUsagesInterface(this.lastLocationAccess, lastLocationAccess);
            this.lastLocationAccess = lastLocationAccess;
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (graph().isAfterStage(GraphState.StageFlag.VECTOR_MATERIALIZATION)) {
            throw GraalError.shouldNotReachHere("node should not have survived LoopVectorization: " + this);
        }
        return this;
    }

    public void replaceWithFixedGather(FixedWithNextNode loopEntryPoint, Graph.Mark beforeVectorization) {
        VectorGatherNode fixedVectorGather = graph().add(new VectorGatherNode(base, offsets, location, getVectorStamp(), barrierType, lastLocationAccess, guard));
        FixedWithNextNode insertionPoint = FloatingVectorReadNode.fixedReadInsertionPoint(loopEntryPoint, beforeVectorization, this, guard);
        graph().addAfterFixed(insertionPoint, fixedVectorGather);
        replaceAndDelete(fixedVectorGather);
    }
}
