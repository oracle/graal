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
package org.graalvm.compiler.nodes.memory;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import java.util.List;

@NodeInfo(nameTemplate = "VectorRead#{p#locations/s}", shortName = "VectorRead")
public class VectorReadNode extends VectorFixedAccessNode implements LIRLowerable {

    public static final NodeClass<VectorReadNode> TYPE = NodeClass.create(VectorReadNode.class);

    public VectorReadNode(AddressNode address, LocationIdentity[] locations, Stamp stamp, BarrierType barrierType) {
        this(TYPE, address, locations, stamp, null, barrierType, false);
    }

    public VectorReadNode(NodeClass<? extends VectorReadNode> c, AddressNode address, LocationIdentity[] locations, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean nullCheck) {
        super(c, address, locations, stamp, guard, barrierType, nullCheck);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        final LIRKind vectorReadKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
        gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitVectorLoad(vectorReadKind, locations.length, gen.operand(address), null));
    }

    @Override
    public boolean isAllowedUsageType(InputType type) {
        return getNullCheck() && type == InputType.Guard || super.isAllowedUsageType(type);
    }

    public static VectorReadNode fromPackElements(List<ReadNode> nodes) {
        assert nodes.size() != 0 : "pack empty";
        // Pre: nodes all have the same guard.
        // Pre: nodes are contiguous
        // Pre: nodes are from the same memory region
        // ???

        final ReadNode anchor = nodes.get(0);
        final AddressNode address = anchor.getAddress();
        final LocationIdentity[] locations = nodes.stream().map(ReadNode::getLocationIdentity).toArray(LocationIdentity[]::new);

        final Stamp stamp = anchor.getAccessStamp().asVector(locations.length);
        return new VectorReadNode(TYPE, address, locations, stamp, anchor.getGuard(), anchor.getBarrierType(), anchor.getNullCheck());
    }
}
