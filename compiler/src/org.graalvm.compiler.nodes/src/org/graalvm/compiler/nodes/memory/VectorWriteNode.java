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
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.VectorPrimitiveStamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import java.util.List;

@NodeInfo(nameTemplate = "VectorWrite#{p#locations/s}", shortName = "VectorWrite", allowedUsageTypes = {InputType.Memory, InputType.Guard})
public class VectorWriteNode extends VectorFixedAccessNode implements LIRLowerable, MemoryAccess {

    public static final NodeClass<VectorWriteNode> TYPE = NodeClass.create(VectorWriteNode.class);

    @Input ValueNode value;
    @OptionalInput(InputType.Memory) Node lastLocationAccess;

    public ValueNode value() {
        return value;
    }

    public VectorWriteNode(AddressNode address, LocationIdentity[] locations, ValueNode value, BarrierType barrierType) {
        this(TYPE, address, locations, value, barrierType);
    }

    protected VectorWriteNode(NodeClass<? extends VectorWriteNode> c, AddressNode address, LocationIdentity[] locations, ValueNode value, BarrierType barrierType) {
        super(c, address, locations, StampFactory.forVoid(), barrierType);
        this.value = value;
    }

    @Override
    public boolean isAllowedUsageType(InputType type) {
        return type == InputType.Guard && getNullCheck() || super.isAllowedUsageType(type);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        throw GraalError.shouldNotReachHere("VectorWriteNode does not have single LocationIdentity");
    }

    @Override
    public MemoryNode getLastLocationAccess() {
        return (MemoryNode) lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryNode lla) {
        Node newLla = ValueNodeUtil.asNode(lla);
        updateUsages(lastLocationAccess, newLla);
        lastLocationAccess = newLla;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        final VectorPrimitiveStamp vectorStamp = (VectorPrimitiveStamp) value.stamp(NodeView.DEFAULT);
        final LIRKind scalarWriteKind = gen.getLIRGeneratorTool().getLIRKind(vectorStamp.getScalar());

        gen.getLIRGeneratorTool().getArithmetic().emitVectorStore(scalarWriteKind, vectorStamp.getElementCount(), gen.operand(address), gen.operand(value), null);
    }

    @Override
    public boolean canNullCheck() {
        return false;
    }

    @Override
    public boolean verify() {
        assertTrue(value.stamp(NodeView.DEFAULT) instanceof VectorPrimitiveStamp, "VectorWriteNode value needs to be vector");
        return super.verify();
    }

    public static VectorWriteNode fromPackElements(List<WriteNode> nodes, ValueNode value) {
        assert nodes.size() != 0 : "pack empty";
        assert value.stamp(NodeView.DEFAULT) instanceof VectorPrimitiveStamp : "value not vector";
        // Pre: nodes all have the same guard.
        // Pre: nodes are contiguous
        // Pre: nodes are from the same memory region
        // ???

        final WriteNode anchor = nodes.get(0);
        final AddressNode address = anchor.getAddress();
        final LocationIdentity[] locations = nodes.stream().map(WriteNode::getLocationIdentity).toArray(LocationIdentity[]::new);

        return new VectorWriteNode(TYPE, address, locations, value, anchor.getBarrierType());
    }
}
