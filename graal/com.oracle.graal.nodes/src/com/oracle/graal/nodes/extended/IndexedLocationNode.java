/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Location node that has a displacement and a scaled index. Can represent locations in the form of
 * [base + index * scale + disp] where base and index are nodes and scale and disp are integer
 * constants.
 */
@NodeInfo(nameTemplate = "IdxLoc {p#locationIdentity/s}")
public class IndexedLocationNode extends LocationNode implements Canonicalizable {

    private final Kind valueKind;
    private final LocationIdentity locationIdentity;
    private final long displacement;
    @Input ValueNode index;
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
        return graph.unique(IndexedLocationNode.create(identity, kind, displacement, index, indexScaling));
    }

    public static IndexedLocationNode create(LocationIdentity identity, Kind kind, long displacement, ValueNode index, int indexScaling) {
        return new IndexedLocationNodeGen(identity, kind, displacement, index, indexScaling);
    }

    IndexedLocationNode(LocationIdentity identity, Kind kind, long displacement, ValueNode index, int indexScaling) {
        super(StampFactory.forVoid());
        assert index != null;
        assert indexScaling != 0;
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
        if (index.isConstant()) {
            return ConstantLocationNode.create(getLocationIdentity(), getValueKind(), index.asConstant().asLong() * indexScaling + displacement);
        }
        return this;
    }

    @Override
    public IntegerStamp getDisplacementStamp() {
        assert indexScaling > 0 && CodeUtil.isPowerOf2(indexScaling);
        int scale = CodeUtil.log2(indexScaling);
        return (IntegerStamp) StampTool.add(StampFactory.forInteger(64, displacement, displacement),
                        StampTool.signExtend(StampTool.leftShift(index.stamp(), StampFactory.forInteger(64, scale, scale)), 64));
    }

    @Override
    public Value generateAddress(NodeMappableLIRBuilder builder, LIRGeneratorTool gen, Value base) {
        return gen.emitAddress(base, displacement, builder.operand(getIndex()), getIndexScaling());
    }
}
