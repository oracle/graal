/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.word.nodes;

import static com.oracle.graal.api.meta.LocationIdentity.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Location node that can be used inside a snippet without having the elements (including the
 * location identity and kind) as a snippet constant. Can represent locations in the form of [base +
 * index * scale + disp]. When the location is created, all elements (base, index, scale, disp) are
 * nodes. Both scale and disp must eventually canonicalize to {@link ConstantNode constants} so that
 * this node can be canonicalized to a {@link IndexedLocationNode} or {@link ConstantLocationNode}.
 */
public final class SnippetLocationNode extends LocationNode implements Canonicalizable {

    private final SnippetReflectionProvider snippetReflection;

    @Input private ValueNode valueKind;
    @Input(InputType.Association) private ValueNode locationIdentity;
    @Input private ValueNode displacement;
    @Input private ValueNode index;
    @Input private ValueNode indexScaling;

    public static SnippetLocationNode create(SnippetReflectionProvider snippetReflection, ValueNode identity, ValueNode kind, ValueNode displacement, ValueNode index, ValueNode indexScaling,
                    Graph graph) {
        return graph.unique(new SnippetLocationNode(snippetReflection, identity, kind, displacement, index, indexScaling));
    }

    private SnippetLocationNode(@InjectedNodeParameter SnippetReflectionProvider snippetReflection, ValueNode locationIdentity, ValueNode kind, ValueNode displacement) {
        this(snippetReflection, locationIdentity, kind, displacement, null, null);
    }

    private SnippetLocationNode(@InjectedNodeParameter SnippetReflectionProvider snippetReflection, ValueNode locationIdentity, ValueNode kind, ValueNode displacement, ValueNode index,
                    ValueNode indexScaling) {
        super(StampFactory.object());
        this.snippetReflection = snippetReflection;
        this.valueKind = kind;
        this.locationIdentity = locationIdentity;
        this.displacement = displacement;
        this.index = index;
        this.indexScaling = indexScaling;
    }

    @Override
    public Kind getValueKind() {
        if (valueKind.isConstant()) {
            return (Kind) snippetReflection.asObject(valueKind.asConstant());
        }
        throw new GraalInternalError("Cannot access kind yet because it is not constant: " + valueKind);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        if (locationIdentity.isConstant()) {
            return (LocationIdentity) snippetReflection.asObject(locationIdentity.asConstant());
        }
        // We do not know our actual location identity yet, so be conservative.
        return ANY_LOCATION;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (valueKind.isConstant() && locationIdentity.isConstant() && displacement.isConstant() && (indexScaling == null || indexScaling.isConstant())) {
            Kind constKind = (Kind) snippetReflection.asObject(valueKind.asConstant());
            LocationIdentity constLocation = (LocationIdentity) snippetReflection.asObject(locationIdentity.asConstant());
            long constDisplacement = displacement.asConstant().asLong();
            int constIndexScaling = indexScaling == null ? 0 : indexScaling.asConstant().asInt();

            if (index == null || constIndexScaling == 0) {
                return ConstantLocationNode.create(constLocation, constKind, constDisplacement, graph());
            } else if (index.isConstant()) {
                return ConstantLocationNode.create(constLocation, constKind, index.asConstant().asLong() * constIndexScaling + constDisplacement, graph());
            } else {
                return IndexedLocationNode.create(constLocation, constKind, constDisplacement, index, graph(), constIndexScaling);
            }
        }
        return this;
    }

    @Override
    public Value generateAddress(NodeLIRBuilderTool gen, Value base) {
        throw new GraalInternalError("locationIdentity must be a constant so that this node can be canonicalized: " + locationIdentity);
    }

    @NodeIntrinsic
    public static native Location constantLocation(LocationIdentity identity, Kind kind, long displacement);

    @NodeIntrinsic
    public static native Location indexedLocation(LocationIdentity identity, Kind kind, long displacement, int index, int indexScaling);
}
