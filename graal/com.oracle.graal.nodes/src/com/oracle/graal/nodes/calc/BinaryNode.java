/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.calc;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;

/**
 * The {@code BinaryNode} class is the base of arithmetic and logic operations with two inputs.
 */
@NodeInfo
public abstract class BinaryNode extends FloatingNode implements Canonicalizable.Binary<ValueNode> {

    @Input protected ValueNode x;
    @Input protected ValueNode y;

    public ValueNode getX() {
        return x;
    }

    public ValueNode getY() {
        return y;
    }

    /**
     * Creates a new BinaryNode instance.
     *
     * @param stamp the result type of this instruction
     * @param x the first input instruction
     * @param y the second input instruction
     */
    public BinaryNode(Stamp stamp, ValueNode x, ValueNode y) {
        super(stamp);
        this.x = x;
        this.y = y;
    }

    public static BinaryNode add(StructuredGraph graph, ValueNode x, ValueNode y) {
        assert x.stamp().isCompatible(y.stamp());
        Stamp stamp = x.stamp();
        if (stamp instanceof IntegerStamp) {
            return BinaryArithmeticNode.add(graph, x, y);
        } else if (stamp instanceof FloatStamp) {
            return graph.unique(FloatAddNode.create(x, y, false));
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static BinaryNode sub(StructuredGraph graph, ValueNode x, ValueNode y) {
        assert x.stamp().isCompatible(y.stamp());
        Stamp stamp = x.stamp();
        if (stamp instanceof IntegerStamp) {
            return BinaryArithmeticNode.sub(graph, x, y);
        } else if (stamp instanceof FloatStamp) {
            return graph.unique(FloatSubNode.create(x, y, false));
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static BinaryNode mul(StructuredGraph graph, ValueNode x, ValueNode y) {
        assert x.stamp().isCompatible(y.stamp());
        Stamp stamp = x.stamp();
        if (stamp instanceof IntegerStamp) {
            return BinaryArithmeticNode.mul(graph, x, y);
        } else if (stamp instanceof FloatStamp) {
            return graph.unique(FloatMulNode.create(x, y, false));
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }
}
