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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractStateSplit;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;

@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public class StoreVMThreadLocalNode extends AbstractStateSplit implements Lowerable {
    public static final NodeClass<StoreVMThreadLocalNode> TYPE = NodeClass.create(StoreVMThreadLocalNode.class);

    protected final VMThreadLocalInfo threadLocalInfo;
    protected final BarrierType barrierType;
    @Input protected ValueNode holder;
    @Input protected ValueNode value;

    public StoreVMThreadLocalNode(VMThreadLocalInfo threadLocalInfo, ValueNode holder, ValueNode value, BarrierType barrierType) {
        super(TYPE, StampFactory.forVoid());
        this.threadLocalInfo = threadLocalInfo;
        this.barrierType = barrierType;
        this.holder = holder;
        this.value = value;
    }

    @Override
    public void lower(LoweringTool tool) {
        assert threadLocalInfo.offset >= 0;

        ConstantNode offset = ConstantNode.forLong(threadLocalInfo.offset, graph());
        AddressNode address = graph().unique(new OffsetAddressNode(holder, offset));
        JavaWriteNode write = graph().add(new JavaWriteNode(threadLocalInfo.storageKind, address, threadLocalInfo.locationIdentity, value, barrierType, true));
        write.setStateAfter(stateAfter());
        graph().replaceFixedWithFixed(this, write);
        tool.getLowerer().lower(write, tool);
    }
}
