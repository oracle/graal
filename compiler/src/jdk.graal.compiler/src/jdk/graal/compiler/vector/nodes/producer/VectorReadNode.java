/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.nodes.extended.LoadAddressNode;
import jdk.graal.compiler.vector.nodes.LowerableVectorNode;
import jdk.graal.compiler.vector.nodes.ShiftableVectorNode;
import jdk.graal.compiler.vector.nodes.SimdifyableVectorProducer;
import jdk.graal.compiler.vector.nodes.VectorAccess;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorReadIterator;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.memory.AddressableMemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;

/**
 * Reads a vector value from memory.
 */
//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
          cyclesRationale = "We cannot reason about vector nodes statically.",
          size = SIZE_UNKNOWN,
          sizeRationale = "We cannot reason about vector nodes statically.")
//@formatter:on
public final class VectorReadNode extends FixedWithNextNode
                implements ShiftableVectorNode, LowerableVectorNode, SimdifyableVectorProducer, GuardedNode, AddressableMemoryAccess, VectorAccess, Canonicalizable {
    public static final NodeClass<VectorReadNode> TYPE = NodeClass.create(VectorReadNode.class);

    @OptionalInput(InputType.Guard) GuardingNode guard;
    @Input ValueNode address;
    private LocationIdentity location;

    private final BarrierType barrierType;
    @OptionalInput(InputType.Memory) MemoryKill lastLocationAccess;

    protected final int elementStride;

    public VectorReadNode(AddressNode address, LocationIdentity location, int elementStride, VectorStamp stamp, BarrierType barrierType, MemoryKill lastLocationAccess, GuardingNode guard) {
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
        ValueNode rawAddress = this.address;
        if (rawAddress instanceof AddressNode) {
            return (AddressNode) rawAddress;
        } else {
            AddressNode ret = new OffsetAddressNode(rawAddress, ConstantNode.forLong(0));
            if (this.isAlive()) {
                ret = graph().addOrUniqueWithInputs(ret);
            }
            return ret;
        }
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

    public VectorNode shift(ValueNode offset, GuardingNode newGuard, FixedNode insertBefore, MemoryKill newLastLocationAccess) {
        assert CodeUtil.isPowerOf2(elementStride) : elementStride;
        ValueNode longStart = IntegerConvertNode.convert(offset, StampFactory.forKind(JavaKind.Long), graph(), NodeView.DEFAULT);
        ValueNode scaledIndex = graph().unique(new LeftShiftNode(longStart, ConstantNode.forInt(CodeUtil.log2(elementStride), graph())));
        AddressNode newAddress = graph().unique(new OffsetAddressNode(address, scaledIndex));

        GuardingNode finalGuard = newGuard == null ? guard : newGuard;
        VectorReadNode shifted = graph().add(new VectorReadNode(newAddress, location, elementStride, getVectorStamp(), barrierType, newLastLocationAccess, finalGuard));
        if (insertBefore != null) {
            graph().addBeforeFixed(insertBefore, shifted);
        } else {
            graph().addAfterFixed(this, shifted);
        }
        return shifted;
    }

    @Override
    public VectorNode shift(ValueNode offset, GuardingNode newGuard, FixedNode insertBefore, ConstantReflectionProvider constantReflection) {
        return shift(offset, newGuard, insertBefore, lastLocationAccess);
    }

    @Override
    public ValueNode simdify(int length, Direction consumerDirection) {
        Stamp simdStamp = getVectorStamp().toSimd(length);
        ReadNode simdRead = graph().add(new ReadNode(getAddress(), location, lastLocationAccess, simdStamp, guard, getBarrierType()));
        graph().addBeforeFixed(this, simdRead);
        return simdRead;
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
    public VectorIterator createInitialIterator(AnchoringNode anchor, TargetDescription target) {
        return new VectorReadIterator(graph().addOrUniqueWithInputs(LoadAddressNode.create(target.wordJavaKind, (OffsetAddressNode) getAddress(), anchor)));
    }

    @Override
    public VectorIterator createPhiIterator(AbstractMergeNode merge, AnchoringNode anchor, TargetDescription target) {
        return new VectorReadIterator(graph().addOrUniqueWithInputs(LoadAddressNode.create(target.wordJavaKind, (OffsetAddressNode) getAddress(), anchor)));
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
    public VectorStamp getVectorStamp() {
        return (VectorStamp) stamp(NodeView.DEFAULT);
    }

    /**
     * The number of fixed nodes in the same block to search during canonicalization to find an
     * equivalent variant of this node.
     */
    private static final int CANONICAL_SEARCH_WINDOW = 10;

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        /* Find an equivalent alternative to this node in the same block. */
        int i = 0;
        Node pred = predecessor();
        while (i++ < CANONICAL_SEARCH_WINDOW && pred instanceof FixedWithNextNode fixedPred) {
            if (fixedPred instanceof VectorReadNode otherRead && otherRead.dataFlowEquals(this)) {
                return otherRead;
            }
            pred = fixedPred.predecessor();
        }
        return this;
    }
}
