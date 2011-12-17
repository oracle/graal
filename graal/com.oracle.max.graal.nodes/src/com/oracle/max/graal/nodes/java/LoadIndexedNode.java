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
package com.oracle.max.graal.nodes.java;

import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;

/**
 * The {@code LoadIndexedNode} represents a read from an element of an array.
 */
public final class LoadIndexedNode extends AccessIndexedNode implements Lowerable, LIRLowerable, Node.IterableNodeType {

    /**
     * Creates a new LoadIndexedNode.
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     * @param length the instruction producing the length
     * @param elementKind the element type
     */
    public LoadIndexedNode(ValueNode array, ValueNode index, ValueNode length, CiKind elementKind) {
        super(createStamp(array, elementKind), array, index, length, elementKind);
    }

    private static Stamp createStamp(ValueNode array, CiKind kind) {
        if (kind == CiKind.Object && array.declaredType() != null) {
            return StampFactory.declared(array.declaredType().componentType());
        } else {
            return StampFactory.forKind(kind);
        }
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.visitLoadIndexed(this);
    }

    @Override
    public boolean needsStateAfter() {
        return false;
    }

    @Override
    public void lower(CiLoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }
}
