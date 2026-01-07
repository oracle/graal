/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.thread;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.memory.FloatableThreadLocalAccess;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.OrderedMemoryAccess;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;

import jdk.vm.ci.meta.MetaAccessProvider;

@NodeInfo(cycles = NodeCycles.CYCLES_2, size = NodeSize.SIZE_1)
public class LoadVMThreadLocalNode extends FixedWithNextNode implements VMThreadLocalAccess, Lowerable, SingleMemoryKill, OrderedMemoryAccess, MemoryAccess, FloatableThreadLocalAccess {
    public static final NodeClass<LoadVMThreadLocalNode> TYPE = NodeClass.create(LoadVMThreadLocalNode.class);

    protected final VMThreadLocalInfo threadLocalInfo;
    protected final BarrierType barrierType;
    @Input protected ValueNode holder;
    private final MemoryOrderMode memoryOrder;

    public LoadVMThreadLocalNode(MetaAccessProvider metaAccess, VMThreadLocalInfo threadLocalInfo, ValueNode holder, BarrierType barrierType,
                    MemoryOrderMode memoryOrder) {
        super(TYPE, threadLocalInfo.isObject ? StampFactory.object(TypeReference.createTrustedWithoutAssumptions(metaAccess.lookupJavaType(threadLocalInfo.valueClass)))
                        : StampFactory.forKind(threadLocalInfo.storageKind));
        this.threadLocalInfo = threadLocalInfo;
        this.barrierType = barrierType;
        this.holder = holder;
        this.memoryOrder = memoryOrder;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return threadLocalInfo.locationIdentity;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        if (ordersMemoryAccesses()) {
            return LocationIdentity.any();
        }
        return MemoryKill.NO_LOCATION;
    }

    @Override
    public boolean canFloat() {
        return !ordersMemoryAccesses() && threadLocalInfo.allowFloatingReads;
    }

    @Override
    public MemoryOrderMode getMemoryOrder() {
        return memoryOrder;
    }

    @Override
    public void lower(LoweringTool tool) {
        assert threadLocalInfo.offset >= 0;

        ConstantNode offset = ConstantNode.forLong(threadLocalInfo.offset, holder.graph());
        AddressNode address = graph().unique(new OffsetAddressNode(holder, offset));

        JavaReadNode read = graph().add(new JavaReadNode(stamp, threadLocalInfo.storageKind, address, threadLocalInfo.locationIdentity, barrierType, memoryOrder, true));
        if (canFloat()) {
            /*
             * Setting a guarding node allows a JavaReadNode to float when lowered. Otherwise they
             * will be conservatively be forced at a fixed location.
             */
            read.setGuard(read.graph().start());
        }

        graph().replaceFixedWithFixed(this, read);
        tool.getLowerer().lower(read, tool);
    }
}
