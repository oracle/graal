/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;

@NodeInfo(allowedUsageTypes = InputType.Memory, cycles = NodeCycles.CYCLES_2, size = NodeSize.SIZE_1)
public class VolatileStoreVMThreadLocalNode extends StoreVMThreadLocalNode implements SingleMemoryKill {
    public static final NodeClass<VolatileStoreVMThreadLocalNode> TYPE = NodeClass.create(VolatileStoreVMThreadLocalNode.class);

    public VolatileStoreVMThreadLocalNode(VMThreadLocalInfo threadLocalInfo, ValueNode holder, ValueNode value, OnHeapMemoryAccess.BarrierType barrierType) {
        super(TYPE, threadLocalInfo, holder, value, barrierType, MemoryOrderMode.VOLATILE);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }
}
