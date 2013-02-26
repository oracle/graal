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
import com.oracle.graal.graph.Node.ValueNumberable;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * A location for a memory access in terms of the kind of value accessed and how to access it. The
 * base version can represent addresses of the form [base + disp] where base is a node and disp is a
 * constant.
 */
@NodeInfo(nameTemplate = "Loc {p#locationIdentity/s}")
public class LocationNode extends FloatingNode implements LIRLowerable, ValueNumberable {

    private int displacement;
    private Kind valueKind;
    private Object locationIdentity;

    /**
     * Denotes any location. A write to such a location kills all values in a memory map during an
     * analysis of memory accesses in a graph.
     */
    public static final Object ANY_LOCATION = new Object() {

        @Override
        public String toString() {
            return "ANY_LOCATION";
        }
    };

    /**
     * Denotes the location of a value that is guaranteed to be final.
     */
    public static final Object FINAL_LOCATION = new Object() {

        @Override
        public String toString() {
            return "FINAL_LOCATION";
        }
    };

    public static Object getArrayLocation(Kind elementKind) {
        return elementKind;
    }

    public int displacement() {
        return displacement;
    }

    public static LocationNode create(Object identity, Kind kind, int displacement, Graph graph) {
        return graph.unique(new LocationNode(identity, kind, displacement));
    }

    protected LocationNode(Object identity, Kind kind, int displacement) {
        super(StampFactory.extension());
        assert kind != Kind.Illegal && kind != Kind.Void;
        this.displacement = displacement;
        this.valueKind = kind;
        this.locationIdentity = identity;
    }

    public Kind getValueKind() {
        return valueKind;
    }

    public Object locationIdentity() {
        return locationIdentity;
    }

    @Override
    public void generate(LIRGeneratorTool generator) {
        // nothing to do...
    }

    public Value generateLea(LIRGeneratorTool gen, ValueNode base) {
        return gen.emitLea(gen.operand(base), displacement(), Value.ILLEGAL, 0);
    }

    public Value generateLoad(LIRGeneratorTool gen, ValueNode base, boolean canTrap) {
        return gen.emitLoad(getValueKind(), gen.operand(base), displacement(), Value.ILLEGAL, 0, canTrap);
    }

    public void generateStore(LIRGeneratorTool gen, ValueNode base, ValueNode value, boolean canTrap) {
        gen.emitStore(getValueKind(), gen.operand(base), displacement(), Value.ILLEGAL, 0, gen.operand(value), canTrap);
    }
}
