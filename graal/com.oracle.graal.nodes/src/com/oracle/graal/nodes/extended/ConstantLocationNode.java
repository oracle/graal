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
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Location node that has a constant displacement. Can represent addresses of the form [base + disp]
 * where base is a node and disp is a constant.
 */
@NodeInfo(nameTemplate = "Loc {p#locationIdentity/s}")
public class ConstantLocationNode extends LocationNode {

    private final Kind valueKind;
    private final LocationIdentity locationIdentity;
    private final long displacement;

    public static ConstantLocationNode create(LocationIdentity identity, Kind kind, long displacement, Graph graph) {
        return graph.unique(ConstantLocationNode.create(identity, kind, displacement));
    }

    public static ConstantLocationNode create(LocationIdentity identity, Kind kind, long displacement) {
        return new ConstantLocationNodeGen(identity, kind, displacement);
    }

    ConstantLocationNode(LocationIdentity identity, Kind kind, long displacement) {
        super(StampFactory.forVoid());
        assert kind != Kind.Illegal && kind != Kind.Void;
        this.valueKind = kind;
        this.locationIdentity = identity;
        this.displacement = displacement;
    }

    @Override
    public Kind getValueKind() {
        return valueKind;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    public long getDisplacement() {
        return displacement;
    }

    @Override
    public Value generateAddress(NodeMappableLIRBuilder builder, LIRGeneratorTool gen, Value base) {
        return gen.emitAddress(base, getDisplacement(), Value.ILLEGAL, 0);
    }

    @Override
    public IntegerStamp getDisplacementStamp() {
        return StampFactory.forInteger(64, displacement, displacement);
    }
}
