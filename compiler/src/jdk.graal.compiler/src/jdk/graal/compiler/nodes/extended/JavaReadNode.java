/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.OrderedMemoryAccess;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

/**
 * Read a raw memory location according to Java field or array read semantics. It will perform read
 * barriers, implicit conversions and optionally oop uncompression.
 */
@NodeInfo(nameTemplate = "JavaRead#{p#location/s}", cycles = CYCLES_2, size = SIZE_1)
public class JavaReadNode extends FixedAccessNode implements Lowerable, GuardingNode, Canonicalizable, OrderedMemoryAccess, SingleMemoryKill {

    public static final NodeClass<JavaReadNode> TYPE = NodeClass.create(JavaReadNode.class);
    protected final JavaKind readKind;
    protected final boolean compressible;
    private final MemoryOrderMode memoryOrder;

    public JavaReadNode(JavaKind readKind, AddressNode address, LocationIdentity location, BarrierType barrierType, MemoryOrderMode memoryOrder, boolean compressible) {
        this(StampFactory.forKind(readKind), readKind, address, location, barrierType, memoryOrder, compressible);
    }

    public JavaReadNode(Stamp stamp, JavaKind readKind, AddressNode address, LocationIdentity location, BarrierType barrierType, MemoryOrderMode memoryOrder, boolean compressible) {
        this(TYPE, stamp, readKind, address, location, barrierType, memoryOrder, compressible);
    }

    protected JavaReadNode(NodeClass<? extends JavaReadNode> c, Stamp stamp, JavaKind readKind, AddressNode address, LocationIdentity location, BarrierType barrierType, MemoryOrderMode memoryOrder,
                    boolean compressible) {
        super(c, address, location, stamp, barrierType);
        this.readKind = readKind;
        this.compressible = compressible;
        this.memoryOrder = memoryOrder;
        assert barrierType == BarrierType.NONE || stamp instanceof AbstractObjectStamp : "incorrect barrier on non-object type: " + location;
    }

    @Override
    public boolean canNullCheck() {
        return true;
    }

    public JavaKind getReadKind() {
        return readKind;
    }

    public boolean isCompressible() {
        return compressible;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (getAddress() instanceof OffsetAddressNode) {
            OffsetAddressNode objAddress = (OffsetAddressNode) getAddress();
            return ReadNode.canonicalizeRead(this, tool, this.readKind, objAddress.getBase(), objAddress.getOffset(), getLocationIdentity());
        }
        return this;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        if (ordersMemoryAccesses()) {
            return LocationIdentity.any();
        }
        return MemoryKill.NO_LOCATION;
    }

    @Override
    public MemoryOrderMode getMemoryOrder() {
        return memoryOrder;
    }
}
