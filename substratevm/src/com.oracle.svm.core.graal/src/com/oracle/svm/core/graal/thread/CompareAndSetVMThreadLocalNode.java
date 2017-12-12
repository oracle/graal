/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.AbstractStateSplit;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LogicCompareAndSwapNode;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_8, size = NodeSize.SIZE_8)
public class CompareAndSetVMThreadLocalNode extends AbstractStateSplit implements Lowerable {
    public static final NodeClass<CompareAndSetVMThreadLocalNode> TYPE = NodeClass.create(CompareAndSetVMThreadLocalNode.class);

    protected final VMThreadLocalInfo threadLocalInfo;
    protected final BarrierType barrierType;
    @Input protected ValueNode holder;
    @Input protected ValueNode expect;
    @Input protected ValueNode update;

    public CompareAndSetVMThreadLocalNode(VMThreadLocalInfo threadLocalInfo, ValueNode holder, ValueNode expect, ValueNode update, BarrierType barrierType) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean.getStackKind()));
        this.threadLocalInfo = threadLocalInfo;
        this.barrierType = barrierType;
        this.holder = holder;
        this.expect = expect;
        this.update = update;
    }

    @Override
    public void lower(LoweringTool tool) {
        assert threadLocalInfo.offset >= 0;

        ConstantNode offset = ConstantNode.forIntegerKind(FrameAccess.getWordKind(), threadLocalInfo.offset, holder.graph());
        AddressNode address = graph().unique(new OffsetAddressNode(holder, offset));
        LogicCompareAndSwapNode atomic = graph().add(new LogicCompareAndSwapNode(address, threadLocalInfo.locationIdentity, expect, update, barrierType));
        atomic.setStateAfter(stateAfter());
        graph().replaceFixedWithFixed(this, atomic);
    }
}
