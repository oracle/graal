/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(nameTemplate = "OrderedJavaRead#{p#location/s}", allowedUsageTypes = Memory, cycles = CYCLES_2, size = SIZE_1)
public class JavaOrderedReadNode extends JavaReadNode implements SingleMemoryKill {
    public static final NodeClass<JavaOrderedReadNode> TYPE = NodeClass.create(JavaOrderedReadNode.class);
    private final MemoryOrderMode memoryOrder;

    public JavaOrderedReadNode(JavaKind readKind, AddressNode address, LocationIdentity location, BarrierType barrierType, MemoryOrderMode memoryOrder, boolean compressible) {
        this(StampFactory.forKind(readKind), readKind, address, location, barrierType, memoryOrder, compressible);
    }

    public JavaOrderedReadNode(Stamp stamp, JavaKind readKind, AddressNode address, LocationIdentity location, BarrierType barrierType, MemoryOrderMode memoryOrder, boolean compressible) {
        super(TYPE, stamp, readKind, address, location, barrierType, compressible);
        // Node is expected to have ordering requirements
        assert MemoryOrderMode.ordersMemoryAccesses(memoryOrder);
        this.memoryOrder = memoryOrder;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public MemoryOrderMode getMemoryOrder() {
        return memoryOrder;
    }
}
