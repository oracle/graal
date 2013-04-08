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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Extension of a {@linkplain LocationNode location} to include a scaled index. Can represent
 * locations in the form of [base + index * scale + disp] where base and index are nodes and scale
 * and disp are integer constants.
 */
public final class IndexedLocationNode extends LocationNode implements Canonicalizable {

    @Input private ValueNode index;
    private final int indexScaling;

    /**
     * Gets the index or offset of this location.
     */
    public ValueNode index() {
        return index;
    }

    public static Object getArrayLocation(Kind elementKind) {
        return elementKind;
    }

    /**
     * @return Constant that is used to scale the index.
     */
    public int indexScaling() {
        return indexScaling;
    }

    public static IndexedLocationNode create(Object identity, Kind kind, int displacement, ValueNode index, Graph graph, int indexScaling) {
        return graph.unique(new IndexedLocationNode(identity, kind, index, displacement, indexScaling));
    }

    private IndexedLocationNode(Object identity, Kind kind, ValueNode index, int displacement, int indexScaling) {
        super(identity, kind, displacement);
        this.index = index;
        this.indexScaling = indexScaling;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        Constant constantIndex = index.asConstant();
        if (constantIndex != null) {
            long constantIndexLong = constantIndex.asLong();
            constantIndexLong *= indexScaling;
            constantIndexLong += displacement();
            int constantIndexInt = (int) constantIndexLong;
            if (constantIndexLong == constantIndexInt) {
                return LocationNode.create(locationIdentity(), getValueKind(), constantIndexInt, graph());
            }
        }
        return this;
    }

    @Override
    public Value generateLea(LIRGeneratorTool gen, ValueNode base) {
        return gen.emitLea(gen.operand(base), displacement(), gen.operand(index()), indexScaling());
    }

    @Override
    public Value generateLoad(LIRGeneratorTool gen, ValueNode base, DeoptimizingNode deopting) {
        return gen.emitLoad(getValueKind(), gen.operand(base), displacement(), gen.operand(index()), indexScaling(), deopting);
    }

    @Override
    public void generateStore(LIRGeneratorTool gen, ValueNode base, ValueNode value, DeoptimizingNode deopting) {
        gen.emitStore(getValueKind(), gen.operand(base), displacement(), gen.operand(index()), indexScaling(), gen.operand(value), deopting);
    }
}
