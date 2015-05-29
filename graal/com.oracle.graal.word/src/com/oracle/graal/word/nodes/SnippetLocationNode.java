/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.jvmci.meta.Value;
import com.oracle.jvmci.meta.LocationIdentity;
import static com.oracle.jvmci.meta.LocationIdentity.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.jvmci.common.*;

/**
 * Location node that can be used inside a snippet without having the elements (including the
 * location identity and kind) as a snippet constant. Can represent locations in the form of [base +
 * index * scale + disp]. When the location is created, all elements (base, index, scale, disp) are
 * nodes. Both scale and disp must eventually canonicalize to {@link ConstantNode constants} so that
 * this node can be canonicalized to a {@link IndexedLocationNode} or {@link ConstantLocationNode}.
 */
@NodeInfo
public final class SnippetLocationNode extends LocationNode implements Canonicalizable {
    public static final NodeClass<SnippetLocationNode> TYPE = NodeClass.create(SnippetLocationNode.class);

    protected final SnippetReflectionProvider snippetReflection;

    @Input(InputType.Association) ValueNode locationIdentity;
    @Input ValueNode displacement;
    @Input ValueNode index;
    @Input ValueNode indexScaling;

    public SnippetLocationNode(@InjectedNodeParameter SnippetReflectionProvider snippetReflection, ValueNode locationIdentity, ValueNode displacement) {
        this(snippetReflection, locationIdentity, displacement, null, null);
    }

    public SnippetLocationNode(@InjectedNodeParameter SnippetReflectionProvider snippetReflection, ValueNode locationIdentity, ValueNode displacement, ValueNode index, ValueNode indexScaling) {
        super(TYPE, StampFactory.object());
        this.snippetReflection = snippetReflection;
        this.locationIdentity = locationIdentity;
        this.displacement = displacement;
        this.index = index;
        this.indexScaling = indexScaling;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        if (locationIdentity.isConstant()) {
            LocationIdentity identity = snippetReflection.asObject(LocationIdentity.class, locationIdentity.asJavaConstant());
            return identity;
        }
        // We do not know our actual location identity yet, so be conservative.
        return any();
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (locationIdentity.isConstant() && displacement.isConstant() && (indexScaling == null || indexScaling.isConstant())) {
            LocationIdentity constLocation = snippetReflection.asObject(LocationIdentity.class, locationIdentity.asJavaConstant());
            long constDisplacement = displacement.asJavaConstant().asLong();
            int constIndexScaling = indexScaling == null ? 0 : indexScaling.asJavaConstant().asInt();

            if (index == null || constIndexScaling == 0) {
                return graph().unique(new ConstantLocationNode(constLocation, constDisplacement));
            } else if (index.isConstant()) {
                return graph().unique(new ConstantLocationNode(constLocation, index.asJavaConstant().asLong() * constIndexScaling + constDisplacement));
            } else {
                return graph().unique(new IndexedLocationNode(constLocation, constDisplacement, index, constIndexScaling));
            }
        }
        return this;
    }

    @Override
    public Value generateAddress(NodeMappableLIRBuilder builder, LIRGeneratorTool gen, Value base) {
        throw new JVMCIError("locationIdentity must be a constant so that this node can be canonicalized: " + locationIdentity);
    }

    @Override
    public IntegerStamp getDisplacementStamp() {
        throw JVMCIError.shouldNotReachHere();
    }

    @NodeIntrinsic
    public static native Location constantLocation(LocationIdentity identity, long displacement);

    @NodeIntrinsic
    public static native Location indexedLocation(LocationIdentity identity, long displacement, int index, int indexScaling);
}
