/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.nodes.memory.address;

import jdk.compiler.graal.core.common.type.AbstractPointerStamp;
import jdk.compiler.graal.core.common.type.IntegerStamp;
import jdk.compiler.graal.core.common.type.PrimitiveStamp;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.AddNode;
import jdk.compiler.graal.nodes.calc.BinaryArithmeticNode;
import jdk.compiler.graal.nodes.spi.Canonicalizable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodeinfo.InputType;
import jdk.compiler.graal.nodeinfo.NodeInfo;

import jdk.vm.ci.meta.JavaKind;

/**
 * Represents an address that is composed of a base and an offset. The base can be either a
 * {@link JavaKind#Object}, a word-sized integer or another pointer. The offset must be a word-sized
 * integer.
 */
@NodeInfo(allowedUsageTypes = InputType.Association)
public class OffsetAddressNode extends AddressNode implements Canonicalizable {
    public static final NodeClass<OffsetAddressNode> TYPE = NodeClass.create(OffsetAddressNode.class);

    @Node.Input ValueNode base;
    @Node.Input ValueNode offset;

    public OffsetAddressNode(ValueNode base, ValueNode offset) {
        super(TYPE);
        this.base = base;
        this.offset = offset;
        assert base != null && (base.stamp(NodeView.DEFAULT) instanceof AbstractPointerStamp || IntegerStamp.getBits(base.stamp(NodeView.DEFAULT)) == 64) &&
                        offset != null && IntegerStamp.getBits(offset.stamp(NodeView.DEFAULT)) == 64 : "both values must have 64 bits";
    }

    public static OffsetAddressNode create(ValueNode base) {
        ValueNode offset;
        if (base.stamp(NodeView.DEFAULT) instanceof AbstractPointerStamp) {
            offset = ConstantNode.forIntegerBits(64, 0);
        } else {
            offset = ConstantNode.forIntegerBits(PrimitiveStamp.getBits(base.stamp(NodeView.DEFAULT)), 0);
        }
        return new OffsetAddressNode(base, offset);
    }

    @Override
    public ValueNode getBase() {
        return base;
    }

    public void setBase(ValueNode base) {
        updateUsages(this.base, base);
        this.base = base;
        assert base != null && (base.stamp(NodeView.DEFAULT) instanceof AbstractPointerStamp || IntegerStamp.getBits(base.stamp(NodeView.DEFAULT)) == 64);
    }

    public ValueNode getOffset() {
        return offset;
    }

    public void setOffset(ValueNode offset) {
        updateUsages(this.offset, offset);
        this.offset = offset;
        assert offset != null && IntegerStamp.getBits(offset.stamp(NodeView.DEFAULT)) == 64;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (base instanceof OffsetAddressNode) {
            NodeView view = NodeView.from(tool);
            // Rewrite (&base[offset1])[offset2] to base[offset1 + offset2].
            OffsetAddressNode b = (OffsetAddressNode) base;
            return new OffsetAddressNode(b.getBase(), BinaryArithmeticNode.add(b.getOffset(), this.getOffset(), view));
        } else if (base instanceof AddNode) {
            AddNode add = (AddNode) base;
            if (add.getY().isConstant()) {
                return new OffsetAddressNode(add.getX(), new AddNode(add.getY(), getOffset()));
            }
        }
        return this;
    }

    @Node.NodeIntrinsic
    public static native Address address(Object base, long offset);

    @Override
    public long getMaxConstantDisplacement() {
        Stamp curStamp = offset.stamp(NodeView.DEFAULT);
        if (curStamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) curStamp;
            if (integerStamp.lowerBound() >= 0) {
                return integerStamp.upperBound();
            }
        }
        return Long.MAX_VALUE;
    }

    @Override
    public ValueNode getIndex() {
        return null;
    }
}
