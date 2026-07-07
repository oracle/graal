/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.vector.nodes.simd.SimdPermuteNode;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.spi.NodeWithIdentity;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;

/**
 * A node representing a SIMD constant {@code <initialValue, 0, ..., 0>} or
 * {@code <0, ..., 0, initialValue>}. Used as the starting vector for a vectorized hash computation.
 * The element order depends on the stride direction of the corresponding vector loop.
 *
 * @implNote This node's SIMD form depends on the consumer's length in a way that is not easy to
 *           model using a naive cut: Taking the lower half of {@code <0, ..., 0, initialValue>}
 *           would produce {@code <0, ..., 0>}, which is incorrect for a hash with ascending stride.
 *           To avoid this cutting, this node must not be shared between consumers with different
 *           step lengths. Therefore, this node implements {@link NodeWithIdentity}. After the phase
 *           SimdifyVector similar produced by VectorHashInitNodes may be shared again.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public class VectorHashInitNode extends VectorBinaryMacroInitNode implements NodeWithIdentity {

    public static final NodeClass<VectorHashInitNode> TYPE = NodeClass.create(VectorHashInitNode.class);

    private final Direction strideDirection;

    public VectorHashInitNode(ValueNode initialElement, Direction strideDirection) {
        super(TYPE, initialElement);
        this.strideDirection = strideDirection;
    }

    @Override
    public ValueNode simdify(int length, Direction consumerDirection) {
        if (length == 1) {
            return initialElement;
        } else {
            /*
             * if stride == up, initial element must be last element of vector. if stride == down,
             * initial element must be first elem of vector, to line up with multiplier**0 from
             * VectorHashStepNode#preExpand
             */
            ValueNode init = graph().unique(new SimdFromScalarNode(initialElement, length));
            if (strideDirection == Direction.Up) {
                int[] permute = new int[length];
                for (int i = 0, j = length - 1; i < length; i++, j--) {
                    permute[i] = j;
                }
                // inverting the element order in the vector
                init = graph().unique(new SimdPermuteNode(init, permute));
            }
            return init;
        }
    }
}
