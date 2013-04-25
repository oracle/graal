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
 * Location node that has a constant displacement. Can represent addresses of the form [base + disp]
 * where base is a node and disp is a constant.
 */
@NodeInfo(nameTemplate = "Loc {p#locationIdentity/s}")
public class ConstantLocationNode extends LocationNode {

    private final long displacement;

    public long displacement() {
        return displacement;
    }

    public static ConstantLocationNode create(Object identity, Kind kind, long displacement, Graph graph) {
        return graph.unique(new ConstantLocationNode(identity, kind, displacement));
    }

    protected ConstantLocationNode(Object identity, Kind kind, long displacement) {
        super(identity, kind);
        this.displacement = displacement;
    }

    @Override
    protected ConstantLocationNode addDisplacement(long x) {
        return create(locationIdentity(), getValueKind(), displacement + x, graph());
    }

    @Override
    public Value generateAddress(LIRGeneratorTool gen, Value base) {
        return gen.emitLea(base, displacement(), Value.ILLEGAL, 0);
    }

    @Override
    public Value generateLoad(LIRGeneratorTool gen, Value base, DeoptimizingNode deopting) {
        return gen.emitLoad(getValueKind(), base, displacement(), Value.ILLEGAL, 0, deopting);
    }

    @Override
    public void generateStore(LIRGeneratorTool gen, Value base, Value value, DeoptimizingNode deopting) {
        gen.emitStore(getValueKind(), base, displacement(), Value.ILLEGAL, 0, value, deopting);
    }
}
