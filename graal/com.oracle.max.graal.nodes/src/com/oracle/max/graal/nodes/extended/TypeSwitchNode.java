/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;

/**
 * The {@code TypeSwitchNode} is a switch node that dispatches to a successor based on a given type.
 */
public final class TypeSwitchNode extends SwitchNode implements LIRLowerable {

    @Data private final RiResolvedType[] types;

    /**
     * Constructs a new TypeSwitchNode instruction.
     * @param object the instruction producing the value which type should be tested
     * @param successors the list of successors (default successor is last entry)
     * @param types the list of types, same order as successors but no value for default successor
     * @param probability the list of probabilities, same order as successors
     */
    public TypeSwitchNode(ValueNode object, BeginNode[] successors, RiResolvedType[] types, double[] probability) {
        super(object, successors, probability);
        assert object.exactType() == null : "emitting useless guard";
        this.types = types;
    }

    /**
     * Gets the type at the specified index.
     * @param i the index
     * @return the type at that index
     */
    public RiResolvedType typeAt(int i) {
        return types[i];
    }

    public int typesLength() {
        return types.length;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.emitTypeSwitch(this);
    }
}
