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
 * Extension of a {@linkplain LocationNode location} to include a scaled index or an additional offset.
 */
public final class IndexedLocationNode extends LocationNode implements LIRLowerable, Canonicalizable {

    /**
     * An offset or index depending on whether {@link #indexScalingEnabled} is true or false respectively.
     */
    @Input private ValueNode index;
    private final boolean indexScalingEnabled;

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
     * @return whether scaling of the index by the value kind's size is enabled (the default) or disabled.
     */
    public boolean indexScalingEnabled() {
        return indexScalingEnabled;
    }

    public static IndexedLocationNode create(Object identity, Kind kind, int displacement, ValueNode index, Graph graph, boolean indexScalingEnabled) {
        return graph.unique(new IndexedLocationNode(identity, kind, index, displacement, indexScalingEnabled));
    }

    private IndexedLocationNode(Object identity, Kind kind, ValueNode index, int displacement, boolean indexScalingEnabled) {
        super(identity, kind, displacement);
        this.index = index;
        this.indexScalingEnabled = indexScalingEnabled;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        Constant constantIndex = index.asConstant();
        if (constantIndex != null && constantIndex.kind.stackKind().isInt()) {
            long constantIndexLong = constantIndex.asInt();
            if (indexScalingEnabled) {
                if (tool.target() == null) {
                    return this;
                }
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
