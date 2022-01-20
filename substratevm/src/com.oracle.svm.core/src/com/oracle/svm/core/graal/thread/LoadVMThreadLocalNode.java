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

import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.JavaOrderedReadNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;

import jdk.vm.ci.meta.MetaAccessProvider;

@NodeInfo(cycles = NodeCycles.CYCLES_2, size = NodeSize.SIZE_1)
public class LoadVMThreadLocalNode extends FixedWithNextNode implements VMThreadLocalAccess, Lowerable {
    public static final NodeClass<LoadVMThreadLocalNode> TYPE = NodeClass.create(LoadVMThreadLocalNode.class);

    protected final VMThreadLocalInfo threadLocalInfo;
    protected final BarrierType barrierType;
    @Input protected ValueNode holder;
    private final MemoryOrderMode memoryOrder;

    public LoadVMThreadLocalNode(MetaAccessProvider metaAccess, VMThreadLocalInfo threadLocalInfo, ValueNode holder, BarrierType barrierType) {
        this(TYPE, metaAccess, threadLocalInfo, holder, barrierType, MemoryOrderMode.PLAIN);
    }

    protected LoadVMThreadLocalNode(NodeClass<? extends LoadVMThreadLocalNode> c, MetaAccessProvider metaAccess, VMThreadLocalInfo threadLocalInfo, ValueNode holder, BarrierType barrierType,
                    MemoryOrderMode memoryOrder) {
        super(c, threadLocalInfo.isObject ? StampFactory.object(TypeReference.createTrustedWithoutAssumptions(metaAccess.lookupJavaType(threadLocalInfo.valueClass)))
                        : StampFactory.forKind(threadLocalInfo.storageKind));
        this.threadLocalInfo = threadLocalInfo;
        this.barrierType = barrierType;
        this.holder = holder;
        this.memoryOrder = memoryOrder;
    }

    @Override
    public void lower(LoweringTool tool) {
        assert threadLocalInfo.offset >= 0;

        ConstantNode offset = ConstantNode.forLong(threadLocalInfo.offset, holder.graph());
        AddressNode address = graph().unique(new OffsetAddressNode(holder, offset));

        JavaReadNode read;
        if (MemoryOrderMode.ordersMemoryAccesses(memoryOrder)) {
            read = graph().add(new JavaOrderedReadNode(stamp, threadLocalInfo.storageKind, address, threadLocalInfo.locationIdentity, barrierType, memoryOrder, true));
        } else {
            read = graph().add(new JavaReadNode(stamp, threadLocalInfo.storageKind, address, threadLocalInfo.locationIdentity, barrierType, true));
            if (threadLocalInfo.allowFloatingReads) {
                /*
                 * Setting a guarding node allows a JavaReadNode to float when lowered. Otherwise
                 * they will be conservatively be forced at a fixed location.
                 */
                read.setGuard(read.graph().start());
            }
        }

        graph().replaceFixedWithFixed(this, read);
        tool.getLowerer().lower(read, tool);
    }
}
