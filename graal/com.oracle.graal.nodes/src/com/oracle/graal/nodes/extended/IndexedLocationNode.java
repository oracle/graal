/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Location node that has a displacement and a scaled index. Can represent locations in the form of
 * [base + index * scale + disp] where base and index are nodes and scale and disp are integer
 * constants.
 */
@NodeInfo(nameTemplate = "IdxLoc {p#locationIdentity/s}")
public final class IndexedLocationNode extends LocationNode implements Canonicalizable {

    private final Kind valueKind;
    private final LocationIdentity locationIdentity;
    private final long displacement;
    @Input private ValueNode index;
    private final int indexScaling;

    /**
     * Gets the index or offset of this location.
     */
    public ValueNode getIndex() {
        return index;
    }

    public long getDisplacement() {
        return displacement;
    }

    /**
     * @return Constant that is used to scale the index.
     */
    public int getIndexScaling() {
        return indexScaling;
    }

    public static IndexedLocationNode create(LocationIdentity identity, Kind kind, long displacement, ValueNode index, Graph graph, int indexScaling) {
        return graph.unique(new IndexedLocationNode(identity, kind, displacement, index, indexScaling));
    }

    public IndexedLocationNode(LocationIdentity identity, Kind kind, long displacement, ValueNode index, int indexScaling) {
        super(StampFactory.extension());
        assert kind != Kind.Illegal && kind != Kind.Void;
        this.valueKind = kind;
        this.locationIdentity = identity;
        this.index = index;
        this.displacement = displacement;
        this.indexScaling = indexScaling;
    }

    @Override
    public Kind getValueKind() {
        return valueKind;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (index == null || indexScaling == 0) {
            return ConstantLocationNode.create(getLocationIdentity(), getValueKind(), displacement, graph());
        } else if (index.isConstant()) {
            return ConstantLocationNode.create(getLocationIdentity(), getValueKind(), index.asConstant().asLong() * indexScaling + displacement, graph());
        }
        return this;
    }

    @Override
    public Value generateAddress(LIRGeneratorTool gen, Value base) {
        return gen.emitAddress(base, displacement, gen.operand(getIndex()), getIndexScaling());
    }
}
