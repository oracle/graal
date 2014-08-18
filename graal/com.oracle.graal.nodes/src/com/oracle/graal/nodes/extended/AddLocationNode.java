/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.extended;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Location node that is the sum of two other location nodes. Can represent locations in the form of
 * [(base + x) + y] where base is a node and x and y are location nodes.
 */
@NodeInfo(nameTemplate = "AddLoc {p#locationIdentity/s}")
public class AddLocationNode extends LocationNode implements Canonicalizable.Binary<LocationNode> {

    @Input(InputType.Association) private ValueNode x;
    @Input(InputType.Association) private ValueNode y;

    public LocationNode getX() {
        return (LocationNode) x;
    }

    public LocationNode getY() {
        return (LocationNode) y;
    }

    public static AddLocationNode create(LocationNode x, LocationNode y, Graph graph) {
        assert x.getValueKind().equals(y.getValueKind()) && x.getLocationIdentity() == y.getLocationIdentity();
        return graph.unique(AddLocationNode.create(x, y));
    }

    public static AddLocationNode create(ValueNode x, ValueNode y) {
        return new AddLocationNodeGen(x, y);
    }

    AddLocationNode(ValueNode x, ValueNode y) {
        super(StampFactory.forVoid());
        this.x = x;
        this.y = y;
    }

    @Override
    public Kind getValueKind() {
        return getX().getValueKind();
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return getX().getLocationIdentity();
    }

    public LocationNode canonical(CanonicalizerTool tool, LocationNode forX, LocationNode forY) {
        if (forX instanceof ConstantLocationNode) {
            return canonical((ConstantLocationNode) forX, forY);
        }
        if (forY instanceof ConstantLocationNode) {
            return canonical((ConstantLocationNode) forY, forX);
        }
        if (forX instanceof IndexedLocationNode && forY instanceof IndexedLocationNode) {
            IndexedLocationNode xIdx = (IndexedLocationNode) forX;
            IndexedLocationNode yIdx = (IndexedLocationNode) forY;
            if (xIdx.getIndexScaling() == yIdx.getIndexScaling()) {
                long displacement = xIdx.getDisplacement() + yIdx.getDisplacement();
                ValueNode index = IntegerArithmeticNode.add(xIdx.getIndex(), yIdx.getIndex());
                return IndexedLocationNode.create(getLocationIdentity(), getValueKind(), displacement, index, xIdx.getIndexScaling());
            }
        }
        return this;
    }

    private LocationNode canonical(ConstantLocationNode constant, LocationNode other) {
        if (other instanceof ConstantLocationNode) {
            ConstantLocationNode otherConst = (ConstantLocationNode) other;
            return ConstantLocationNode.create(getLocationIdentity(), getValueKind(), otherConst.getDisplacement() + constant.getDisplacement());
        } else if (other instanceof IndexedLocationNode) {
            IndexedLocationNode otherIdx = (IndexedLocationNode) other;
            return IndexedLocationNode.create(getLocationIdentity(), getValueKind(), otherIdx.getDisplacement() + constant.getDisplacement(), otherIdx.getIndex(), otherIdx.getIndexScaling());
        } else if (other instanceof AddLocationNode) {
            AddLocationNode otherAdd = (AddLocationNode) other;
            LocationNode newInner = otherAdd.canonical(constant, otherAdd.getX());
            if (newInner != otherAdd) {
                return AddLocationNode.create(newInner, otherAdd.getY());
            }
        }
        return this;
    }

    @Override
    public Value generateAddress(NodeMappableLIRBuilder builder, LIRGeneratorTool gen, Value base) {
        Value xAddr = getX().generateAddress(builder, gen, base);
        return getY().generateAddress(builder, gen, xAddr);
    }

    @Override
    public IntegerStamp getDisplacementStamp() {
        return StampTool.add(getX().getDisplacementStamp(), getY().getDisplacementStamp());
    }

    @NodeIntrinsic
    public static native Location addLocation(Location x, Location y);
}
