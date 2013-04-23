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
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Location node that is the sum of two other location nodes. Can represent locations in the form of
 * [(base + x) + y] where base is a node and x and y are location nodes.
 */
@NodeInfo(nameTemplate = "AddLoc {p#locationIdentity/s}")
public final class AddLocationNode extends LocationNode implements Canonicalizable {

    @Input private ValueNode x;
    @Input private ValueNode y;

    public LocationNode getX() {
        return (LocationNode) x;
    }

    public LocationNode getY() {
        return (LocationNode) y;
    }

    public static AddLocationNode create(LocationNode x, LocationNode y, Graph graph) {
        assert x.getValueKind().equals(y.getValueKind()) && x.locationIdentity() == y.locationIdentity();
        return graph.unique(new AddLocationNode(x.locationIdentity(), x.getValueKind(), x, y));
    }

    private AddLocationNode(Object identity, Kind kind, ValueNode x, ValueNode y) {
        super(identity, kind);
        this.x = x;
        this.y = y;
    }

    @Override
    protected LocationNode addDisplacement(long displacement) {
        LocationNode added = getX().addDisplacement(displacement);
        return graph().unique(new AddLocationNode(locationIdentity(), getValueKind(), added, getY()));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (x instanceof ConstantLocationNode) {
            return getY().addDisplacement(((ConstantLocationNode) x).displacement());
        }
        if (y instanceof ConstantLocationNode) {
            return getX().addDisplacement(((ConstantLocationNode) y).displacement());
        }

        if (x instanceof IndexedLocationNode && y instanceof IndexedLocationNode) {
            IndexedLocationNode xIdx = (IndexedLocationNode) x;
            IndexedLocationNode yIdx = (IndexedLocationNode) y;
            if (xIdx.indexScaling() == yIdx.indexScaling()) {
                long displacement = xIdx.displacement() + yIdx.displacement();
                ValueNode index = IntegerArithmeticNode.add(xIdx.index(), yIdx.index());
                return IndexedLocationNode.create(locationIdentity(), getValueKind(), displacement, index, graph(), xIdx.indexScaling());
            }
        }

        return this;
    }

    @Override
    public Value generateAddress(LIRGeneratorTool gen, Value base) {
        Value xAddr = getX().generateAddress(gen, base);
        return getY().generateAddress(gen, xAddr);
    }

    @Override
    public Value generateLoad(LIRGeneratorTool gen, Value base, DeoptimizingNode deopting) {
        Value xAddr = getX().generateAddress(gen, base);
        return getY().generateLoad(gen, xAddr, deopting);
    }

    @Override
    public void generateStore(LIRGeneratorTool gen, Value base, Value value, DeoptimizingNode deopting) {
        Value xAddr = getX().generateAddress(gen, base);
        getY().generateStore(gen, xAddr, value, deopting);
    }

    @NodeIntrinsic
    public static native Location addLocation(@ConstantNodeParameter Object identity, @ConstantNodeParameter Kind kind, Location x, Location y);
}
