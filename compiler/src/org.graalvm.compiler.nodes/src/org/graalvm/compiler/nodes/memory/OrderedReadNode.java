/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Red Hat Inc. All rights reserved.
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

package org.graalvm.compiler.nodes.memory;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.memory.MemoryExtendKind;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

@NodeInfo(nameTemplate = "OrderedRead#{p#location/s}", allowedUsageTypes = Memory, cycles = CYCLES_2, size = SIZE_1)
public class OrderedReadNode extends ReadNode {
    public static final NodeClass<OrderedReadNode> TYPE = NodeClass.create(OrderedReadNode.class);
    private final MemoryOrderMode memoryOrder;

    public OrderedReadNode(AddressNode address, Stamp stamp, BarrierType barrierType, MemoryOrderMode memoryOrder) {
        this(address, stamp, MemoryExtendKind.DEFAULT, null, barrierType, memoryOrder, false, null, null);
    }

    protected OrderedReadNode(AddressNode address, Stamp accessStamp, MemoryExtendKind extendKind, GuardingNode guard, BarrierType barrierType, MemoryOrderMode memoryOrder, boolean usedAsNullCheck,
                    FrameState stateBefore, MemoryKill lastLocationAccess) {
        super(TYPE, address, LocationIdentity.any(), accessStamp, extendKind, guard, barrierType, usedAsNullCheck, stateBefore, lastLocationAccess);
        // Node is expected to have ordering requirements
        assert MemoryOrderMode.ordersMemoryAccesses(memoryOrder);
        this.memoryOrder = memoryOrder;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind readKind = gen.getLIRGeneratorTool().getLIRKind(getAccessStamp(NodeView.DEFAULT));
        gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitOrderedLoad(readKind, gen.operand(address), gen.state(this), memoryOrder, extendKind));
    }

    @SuppressWarnings("try")
    @Override
    public FloatingAccessNode asFloatingNode() {
        throw new RuntimeException();
    }

    @Override
    public boolean canFloat() {
        return false;
    }

    @Override
    public MemoryOrderMode getMemoryOrder() {
        return memoryOrder;
    }

    @Override
    public FixedWithNextNode copyWithExtendKind(MemoryExtendKind newExtendKind) {
        assert isCompatibleWithExtend(newExtendKind);
        OrderedReadNode newRead = new OrderedReadNode(address, stamp(NodeView.DEFAULT), newExtendKind, guard, barrierType, memoryOrder, usedAsNullCheck, stateBefore, lastLocationAccess);
        return newRead;
    }
}
