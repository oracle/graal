/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.LocationIdentity;

/**
 * Zeros a chunk of memory.
 */
@NodeInfo(nameTemplate = "ZeroMemory#{p#location/s}", allowedUsageTypes = {InputType.Memory}, cycles = CYCLES_8, size = SIZE_8)
public class ZeroMemoryNode extends FixedAccessNode implements LIRLowerable, SingleMemoryKill {
    public static final NodeClass<ZeroMemoryNode> TYPE = NodeClass.create(ZeroMemoryNode.class);

    @Input ValueNode length;
    private final boolean isAligned;

    public ZeroMemoryNode(ValueNode address, ValueNode length, boolean isAligned, LocationIdentity locationIdentity) {
        this(OffsetAddressNode.create(address), length, isAligned, locationIdentity, BarrierType.NONE);
    }

    public ZeroMemoryNode(AddressNode address, ValueNode length, boolean isAligned, LocationIdentity locationIdentity, BarrierType type) {
        super(TYPE, address, locationIdentity, StampFactory.forVoid(), type);
        this.length = length;
        this.isAligned = isAligned;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitZeroMemory(gen.operand(getAddress()), gen.operand(length), isAligned);
    }

    @Override
    public boolean canNullCheck() {
        return false;
    }

    @NodeIntrinsic
    public static native void zero(Word address, long length, @ConstantNodeParameter boolean isAligned, @ConstantNodeParameter LocationIdentity locationIdentity);

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return location;
    }
}
