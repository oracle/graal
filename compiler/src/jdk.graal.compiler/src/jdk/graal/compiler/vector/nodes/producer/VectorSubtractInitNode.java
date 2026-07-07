/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.vector.nodes.producer;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import jdk.graal.compiler.vector.nodes.simd.SimdFromScalarNode;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;

/**
 * A node representing a SIMD constant {@code <initialValue, 0, ..., 0>}. Used as the starting
 * vector for a vectorized fold computation containing a subtraction.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public class VectorSubtractInitNode extends VectorBinaryMacroInitNode {

    public static final NodeClass<VectorSubtractInitNode> TYPE = NodeClass.create(VectorSubtractInitNode.class);

    public VectorSubtractInitNode(ValueNode initialElement) {
        super(TYPE, initialElement);
    }

    @Override
    public ValueNode simdify(int length, Direction consumerDirection) {
        /**
         * In contrast to {@link VectorHashInitNode#simdify} the initial value of a subtraction does
         * not depend on the stride direction because the initial value may be placed anywhere in
         * the initial vector.
         */
        if (length == 1) {
            return initialElement;
        } else {
            // initialize value to <init, 0, ..., 0>
            return graph().unique(new SimdFromScalarNode(initialElement, length));
        }
    }
}
