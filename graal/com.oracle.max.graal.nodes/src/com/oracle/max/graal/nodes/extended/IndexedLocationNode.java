/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.nodes.extended;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiAddress.Scale;

public final class IndexedLocationNode extends LocationNode implements LIRLowerable, Canonicalizable {
    @Input private ValueNode index;
    @Data private boolean indexScalingEnabled;

    public ValueNode index() {
        return index;
    }

    public static Object getArrayLocation(CiKind elementKind) {
        return elementKind;
    }

    /**
     * @return whether scaling of the index by the value kind's size is enabled (the default) or disabled.
     */
    public boolean indexScalingEnabled() {
        return indexScalingEnabled;
    }

    /**
     * Enables or disables scaling of the index by the value kind's size. Has no effect if the index input is not used.
     */
    public void setIndexScalingEnabled(boolean enable) {
        this.indexScalingEnabled = enable;
    }

    public static IndexedLocationNode create(Object identity, CiKind kind, int displacement, ValueNode index, Graph graph) {
        return create(identity, kind, displacement, index, graph, true);
    }

    public static IndexedLocationNode create(Object identity, CiKind kind, int displacement, ValueNode index, Graph graph, boolean indexScalingEnabled) {
        return graph.unique(new IndexedLocationNode(identity, kind, index, displacement, indexScalingEnabled));
    }

    private IndexedLocationNode(Object identity, CiKind kind, ValueNode index, int displacement, boolean indexScalingEnabled) {
        super(identity, kind, displacement);
        this.index = index;
        this.indexScalingEnabled = indexScalingEnabled;
    }

    @Override
    public CiAddress createAddress(LIRGeneratorTool gen, ValueNode object) {
        CiValue base = gen.operand(object);
        if (base.isConstant() && ((CiConstant) base).isNull()) {
            base = CiValue.IllegalValue;
        }

        CiValue indexValue = gen.operand(index());
        Scale indexScale = Scale.Times1;
        if (indexScalingEnabled) {
            indexScale = Scale.fromInt(gen.target().sizeInBytes(getValueKind()));
        }

        return new CiAddress(getValueKind(), base, indexValue, indexScale, displacement());
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        CiConstant constantIndex = index.asConstant();
        if (constantIndex != null && constantIndex.kind.stackKind().isInt()) {
            long constantIndexLong = constantIndex.asInt();
            if (indexScalingEnabled && tool.target() != null) {
                constantIndexLong *= tool.target().sizeInBytes(getValueKind());
            }
            constantIndexLong += displacement();
            int constantIndexInt = (int) constantIndexLong;
            if (constantIndexLong == constantIndexInt) {
                return LocationNode.create(locationIdentity(), getValueKind(), constantIndexInt, graph());
            }
        }
        return this;
    }
}
