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

import java.util.Collections;
import java.util.List;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.vector.nodes.AbstractVectorNode;
import jdk.graal.compiler.vector.nodes.LowerableVectorNode;
import jdk.graal.compiler.vector.nodes.ShiftableVectorNode;
import jdk.graal.compiler.vector.nodes.SimdifyableVectorOperation;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorGatherIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorIterator;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Gathers a vector of values from memory. The values are addressed via a general notion of a scalar
 * base and a vector of offsets. The base is a pointer, the offsets are integers.
 */
// @formatter:off
@NodeInfo(allowedUsageTypes = {Association},
          cycles = CYCLES_UNKNOWN,
          cyclesRationale = "We cannot argue about vector nodes statically.",
          size = SIZE_UNKNOWN,
          sizeRationale = "We cannot argue about vector nodes statically.")
// @formatter:on
public class VectorGatherNode extends FixedWithNextNode
                implements ShiftableVectorNode, SimdifyableVectorOperation, VectorTransformation, VectorLIRLowerable, LowerableVectorNode, GuardedNode, MemoryAccess, Canonicalizable {
    public static final NodeClass<VectorGatherNode> TYPE = NodeClass.create(VectorGatherNode.class);

    @OptionalInput(InputType.Guard) GuardingNode guard;
    @Input ValueNode base;
    @Input ValueNode offsets;
    private LocationIdentity location;
    private final BarrierType barrierType;
    @OptionalInput(InputType.Memory) MemoryKill lastLocationAccess;

    public VectorGatherNode(ValueNode base, ValueNode offsets, LocationIdentity location, Stamp stamp, BarrierType barrierType, MemoryKill lastLocationAccess, GuardingNode guard) {
        super(TYPE, stamp);
        assert !(base instanceof AbstractVectorNode) : "vector gather's base must be scalar, got: " + base;
        this.base = base;
        this.offsets = offsets.asNode();
        this.location = location;
        this.barrierType = barrierType;
        this.lastLocationAccess = lastLocationAccess;
        this.guard = guard;
    }

    @Override
    public VectorNode shift(ValueNode index, GuardingNode newGuard, FixedNode insertBefore, ConstantReflectionProvider constantReflection) {
        VectorNode shiftedOffsets = ((ShiftableVectorNode) offsets).shift(index, newGuard, insertBefore, constantReflection);
        GuardingNode finalGuard = newGuard == null ? guard : newGuard;
        VectorGatherNode shiftedGather = graph().add(new VectorGatherNode(base, shiftedOffsets.asNode(), location, getVectorStamp(), barrierType, lastLocationAccess, finalGuard));
        graph().addBeforeFixed(insertBefore, shiftedGather);
        return shiftedGather;
    }

    @Override
    public List<? extends ValueNode> getVectorInputs() {
        return Collections.singletonList(offsets);
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
    @SuppressWarnings("try")
    public ValueNode simdify(VectorArchitecture arch, ValueNode... inputs) {
        assert inputs.length == 1 : inputs;
        ValueNode simdOffsets = inputs[0];
        int vectorLength = 1;
        Stamp offsetStamp = simdOffsets.stamp(NodeView.DEFAULT);
        if (offsetStamp instanceof SimdStamp) {
            vectorLength = ((SimdStamp) offsetStamp).getVectorLength();
        }
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            if (vectorLength == 1) {
                // Lower to a normal read.
                Stamp scalarStamp = getVectorStamp().getElementStamp();
                OffsetAddressNode address = graph().unique(new OffsetAddressNode(base, simdOffsets));
                ReadNode scalarRead = graph().add(new ReadNode(address, location, lastLocationAccess, scalarStamp, guard, barrierType));
                graph().addBeforeFixed(this, scalarRead);
                return scalarRead;
            } else {
                Stamp simdStamp = getVectorStamp().toSimd(vectorLength);
                VectorGatherNode simdGather = graph().add(new VectorGatherNode(base, simdOffsets, location, simdStamp, barrierType, lastLocationAccess, guard));
                graph().addBeforeFixed(this, simdGather);
                return simdGather;
            }
        }
    }

    @Override
    public VectorNode simplify(VectorSimplifier simplifier) {
        VectorNode newOffsets = simplifier.simplify((VectorNode) offsets);
        if (newOffsets != offsets) {
            updateUsages(offsets, newOffsets.asNode());
            offsets = newOffsets.asNode();
        }
        return this;
    }

    @Override
    public VectorTransformation createCopy(FixedNode insertBefore, ValueNode... inputs) {
        assert inputs.length == 1 : inputs;
        VectorGatherNode copy = graph().add(new VectorGatherNode(base, inputs[0], location, stamp, barrierType, lastLocationAccess, guard));
        graph().addBeforeFixed(insertBefore, copy);
        return copy;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        LIRKind resultKind = builder.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
        builder.setResult(this, gen.emitVectorGather(resultKind, builder.operand(base), builder.operand(offsets)));
    }

    @Override
    public VectorIterator createInitialIterator(AnchoringNode anchor, TargetDescription target) {
        return VectorGatherIterator.createInitialIterator(this, anchor, target);
    }

    @Override
    public VectorIterator createPhiIterator(AbstractMergeNode merge, AnchoringNode anchor, TargetDescription target) {
        return VectorGatherIterator.createPhiIterator(this, merge, anchor, target);
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
    public VectorStamp getVectorStamp() {
        return (VectorStamp) stamp(NodeView.DEFAULT);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        return this;
    }
}
