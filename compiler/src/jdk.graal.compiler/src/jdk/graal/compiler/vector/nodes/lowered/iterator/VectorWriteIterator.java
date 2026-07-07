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
package jdk.graal.compiler.vector.nodes.lowered.iterator;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.nodes.extended.LoadAddressNode;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorWriteNode;
import jdk.graal.compiler.vector.nodes.lowered.FinishVectorConsumerNode;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public class VectorWriteIterator extends DefaultVectorConsumerIterator implements ShiftableVectorConsumerIterator {

    private final VectorIterator value;

    private final LoadAddressNode startAddress;
    private final LocationIdentity locationIdentity;
    private MemoryKill lastLocationAccess;
    private VectorWriteNode partialWrite;

    public VectorWriteIterator(ValueNode index, VectorIterator value, LoadAddressNode startAddress, LocationIdentity locationIdentity, MemoryKill lastLocationAccess) {
        super(index);
        this.value = value;
        this.startAddress = startAddress;
        this.locationIdentity = locationIdentity;
        this.lastLocationAccess = lastLocationAccess;
        this.partialWrite = null;
    }

    @Override
    public MemoryKill getLastLocationAccess(LocationIdentity location) {
        if (locationIdentity.equals(location)) {
            return lastLocationAccess;
        } else {
            return null;
        }
    }

    public void setLastLocationAccess(MemoryKill lastLocationAccess) {
        this.lastLocationAccess = lastLocationAccess;
    }

    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public void addPhiInput(VectorConsumer consumer, VectorConsumerIterator input, AbstractEndNode branch) {
        super.addPhiInput(consumer, input, branch);

        VectorWriteIterator write = (VectorWriteIterator) input;
        value.addPhiInput(write.value);

        PhiNode memoryPhi = (PhiNode) lastLocationAccess;
        memoryPhi.addInput((ValueNode) write.lastLocationAccess);
    }

    @Override
    public VectorConsumerIterator next(VectorConsumer consumer, int stepLength, FixedNode position, ConstantReflectionProvider constantReflection) {
        VectorWriteNode write = (VectorWriteNode) consumer;

        StructuredGraph graph = write.graph();
        ValueNode adjustedIndex = getIndex();
        int elementStride = write.getElementStride();
        if (elementStride < 0) {
            // convert a negative stride to the correct address offset
            adjustedIndex = BinaryArithmeticNode.add(adjustedIndex, ConstantNode.forIntegerStamp(adjustedIndex.stamp(NodeView.DEFAULT), stepLength, graph), NodeView.DEFAULT);
        }
        ValueNode scaledIndex = BinaryArithmeticNode.mul(graph, adjustedIndex, ConstantNode.forIntegerStamp(adjustedIndex.stamp(NodeView.DEFAULT), elementStride, graph), NodeView.DEFAULT);
        AddressNode address = graph.unique(new OffsetAddressNode(startAddress, scaledIndex));

        VectorNode vector = value.getVector(write.getVector(), this, position, constantReflection, stepLength);
        ValueNode stepLengthNode = ConstantNode.forInt(stepLength, graph);

        partialWrite = graph.add(new VectorWriteNode(address, write.getLocationIdentity(), vector.asNode(), stepLengthNode, write.getElementStride(), write.isInitialization(),
                        write.getBarrierType()));
        partialWrite.setLastLocationAccess(lastLocationAccess);
        graph.addBeforeFixed(position, partialWrite);

        return shift(write, stepLengthNode, partialWrite);
    }

    @Override
    public VectorConsumerIterator shift(VectorConsumer consumer, ValueNode shiftAmount) {
        return shift(consumer, shiftAmount, lastLocationAccess);
    }

    private VectorConsumerIterator shift(VectorConsumer consumer, ValueNode shiftAmount, MemoryKill locationAccess) {
        VectorWriteNode write = (VectorWriteNode) consumer;
        StructuredGraph graph = write.graph();
        return new VectorWriteIterator(getNextIndex(graph, shiftAmount), value.next(write.getVector(), shiftAmount), startAddress, locationIdentity, locationAccess);
    }

    @Override
    public LogicNode hasNext(VectorConsumer consumer, int stepLength, ValueNode limit, ConstantReflectionProvider constantReflection) {
        LogicNode hasNext = super.hasNext(consumer, stepLength, limit, constantReflection);

        VectorWriteNode write = (VectorWriteNode) consumer;
        return hasNextValue(hasNext, value, write.getVector(), stepLength, limit);
    }

    @Override
    public ValueNode getAlignCount(VectorConsumer consumer, int align) {
        VectorWriteNode write = (VectorWriteNode) consumer;
        StructuredGraph graph = write.graph();

        int absElementStride = NumUtil.safeAbs(write.getElementStride());
        assert CodeUtil.isPowerOf2(absElementStride) && CodeUtil.isPowerOf2(align) : absElementStride + " " + align + " " + consumer;

        Stamp stamp = startAddress.stamp(NodeView.DEFAULT);
        ValueNode memIdx = graph.unique(new UnsignedRightShiftNode(startAddress, ConstantNode.forInt(CodeUtil.log2(absElementStride), graph)));
        if (write.getElementStride() > 0) {
            memIdx = BinaryArithmeticNode.add(graph, memIdx, getIndex(), NodeView.DEFAULT);
            memIdx = graph.addOrUnique(NegateNode.create(memIdx, NodeView.DEFAULT));
            return graph.unique(new AndNode(memIdx, ConstantNode.forIntegerStamp(stamp, align - 1, graph)));
        } else {
            memIdx = BinaryArithmeticNode.sub(graph, memIdx, getIndex(), NodeView.DEFAULT);
            return graph.unique(new AndNode(memIdx, ConstantNode.forIntegerStamp(stamp, align - 1, graph)));
        }
    }

    @Override
    public void finishConsumer(FinishVectorConsumerNode finish) {
        VectorWriteNode write = (VectorWriteNode) finish.getConsumer();
        MemoryKill lla = lastLocationAccess;
        /*
         * During expansion of vector consumers, the original node is now only a placeholder that
         * will disappear, along with all other writes in the same vector loop. Find the last
         * location access outside the loop.
         */
        if (write.isPartOfALoop()) {
            while (lla instanceof VectorWriteNode && ((VectorWriteNode) lla).vectorLoop() == write.vectorLoop()) {
                lla = ((VectorWriteNode) lla).getLastLocationAccess();
            }
        }
        write.replaceAtUsages(lla.asNode(), InputType.Memory);
        super.finishConsumer(finish);
    }

    public VectorWriteNode getPartialWrite() {
        assert partialWrite != null : "next() must be called before calling getPartialWrite()";
        return partialWrite;
    }
}
