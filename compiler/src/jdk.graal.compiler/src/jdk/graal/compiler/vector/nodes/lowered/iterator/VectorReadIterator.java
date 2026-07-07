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

import jdk.graal.compiler.nodes.extended.LoadAddressNode;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.producer.VectorReadNode;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public class VectorReadIterator extends DefaultVectorIterator {

    private final LoadAddressNode startAddress;

    public VectorReadIterator(LoadAddressNode startAddress) {
        this.startAddress = startAddress;
    }

    @Override
    public VectorNode getVector(VectorNode source, VectorIterationState state, FixedNode position, ConstantReflectionProvider constantReflection, int stepLength) {
        VectorReadNode read = (VectorReadNode) source;

        // Check if we have already generated a lowered read for this original read at this
        // position. This preserves sharing of reads.
        VectorReadNode cachedRead = state.getCachedVectorRead(read, position);
        if (cachedRead != null) {
            return cachedRead;
        }

        MemoryKill lastLocationAccess = state.getLastLocationAccess(read.getLocationIdentity());
        if (lastLocationAccess == null) {
            lastLocationAccess = read.getLastLocationAccess();
        }

        StructuredGraph graph = read.graph();
        ValueNode index = state.getIndex();
        int elementStride = read.getElementStride();
        if (elementStride < 0) {
            // convert a negative stride to the correct address offset
            index = BinaryArithmeticNode.add(index, ConstantNode.forIntegerStamp(index.stamp(NodeView.DEFAULT), stepLength, graph), NodeView.DEFAULT);
        }
        ValueNode scaledIndex = BinaryArithmeticNode.mul(graph, index, ConstantNode.forIntegerStamp(index.stamp(NodeView.DEFAULT), read.getElementStride(), graph), NodeView.DEFAULT);
        AddressNode address = graph.unique(new OffsetAddressNode(startAddress, scaledIndex));
        GuardingNode newGuard = state.getCachedVectorGuard(read.getGuard(), position);
        if (read.getGuard() != null) {
            GraalError.guarantee(newGuard != null, "must have cached guard for read %s with original guard %s, got: %s", read, read.getGuard(), newGuard);
        }

        VectorReadNode loweredVectorRead = read.graph().add(new VectorReadNode(address, read.getLocationIdentity(), read.getElementStride(), read.getVectorStamp(), read.getBarrierType(),
                        lastLocationAccess, newGuard));
        read.graph().addBeforeFixed(position, loweredVectorRead);
        state.cacheVectorRead(read, position, loweredVectorRead);
        return loweredVectorRead;
    }
}
