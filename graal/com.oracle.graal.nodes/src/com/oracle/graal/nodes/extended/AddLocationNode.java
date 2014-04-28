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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Location node that is the sum of two other location nodes. Can represent locations in the form of
 * [(base + x) + y] where base is a node and x and y are location nodes.
 */
@NodeInfo(nameTemplate = "AddLoc {p#locationIdentity/s}")
public final class AddLocationNode extends LocationNode implements Canonicalizable {

    @Input(InputType.Association) private ValueNode x;
    @Input(InputType.Association) private ValueNode y;

    protected LocationNode getX() {
        return (LocationNode) x;
    }

    protected LocationNode getY() {
        return (LocationNode) y;
    }

    public static AddLocationNode create(LocationNode x, LocationNode y, Graph graph) {
        assert x.getValueKind().equals(y.getValueKind()) && x.getLocationIdentity() == y.getLocationIdentity();
        return graph.unique(new AddLocationNode(x, y));
    }

    private AddLocationNode(ValueNode x, ValueNode y) {
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

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x instanceof ConstantLocationNode) {
            return canonical((ConstantLocationNode) x, getY());
        }
        if (y instanceof ConstantLocationNode) {
            return canonical((ConstantLocationNode) y, getX());
        }
        if (x instanceof IndexedLocationNode && y instanceof IndexedLocationNode) {
            IndexedLocationNode xIdx = (IndexedLocationNode) x;
            IndexedLocationNode yIdx = (IndexedLocationNode) y;
            if (xIdx.getIndexScaling() == yIdx.getIndexScaling()) {
                long displacement = xIdx.getDisplacement() + yIdx.getDisplacement();
                ValueNode index = IntegerArithmeticNode.add(graph(), xIdx.getIndex(), yIdx.getIndex());
                return IndexedLocationNode.create(getLocationIdentity(), getValueKind(), displacement, index, graph(), xIdx.getIndexScaling());
            }
        }
        return this;
    }

    private LocationNode canonical(ConstantLocationNode constant, LocationNode other) {
        if (other instanceof ConstantLocationNode) {
            ConstantLocationNode otherConst = (ConstantLocationNode) other;
            return ConstantLocationNode.create(getLocationIdentity(), getValueKind(), otherConst.getDisplacement() + constant.getDisplacement(), graph());
        } else if (other instanceof IndexedLocationNode) {
            IndexedLocationNode otherIdx = (IndexedLocationNode) other;
            return IndexedLocationNode.create(getLocationIdentity(), getValueKind(), otherIdx.getDisplacement() + constant.getDisplacement(), otherIdx.getIndex(), graph(), otherIdx.getIndexScaling());
        } else if (other instanceof AddLocationNode) {
            AddLocationNode otherAdd = (AddLocationNode) other;
            LocationNode newInner = otherAdd.canonical(constant, otherAdd.getX());
            if (newInner != otherAdd) {
                return AddLocationNode.create(newInner, otherAdd.getY(), graph());
            }
        }
        return this;
    }

    @Override
    public Value generateAddress(NodeMappableLIRBuilder builder, LIRGeneratorTool gen, Value base) {
        Value xAddr = getX().generateAddress(builder, gen, base);
        return getY().generateAddress(builder, gen, xAddr);
    }

    @NodeIntrinsic
    public static native Location addLocation(Location x, Location y);
}
