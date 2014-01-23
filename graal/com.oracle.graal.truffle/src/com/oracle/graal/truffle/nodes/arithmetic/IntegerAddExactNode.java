/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.nodes.arithmetic;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.truffle.api.*;

/**
 * Node representing an exact integer addition that will throw an {@link ArithmeticException} in
 * case the addition would overflow the 32 bit range.
 */
public class IntegerAddExactNode extends IntegerAddNode implements Canonicalizable, IntegerExactArithmeticNode {

    public IntegerAddExactNode(ValueNode x, ValueNode y) {
        super(x.kind(), x, y);
        assert x.kind() == y.kind() && (x.kind() == Kind.Int || x.kind() == Kind.Long);
    }

    @Override
    public boolean inferStamp() {
        // TODO Should probably use a specialised version which understands that it can't overflow
        return updateStamp(StampTool.add(x().stamp(), y().stamp()));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x().isConstant() && !y().isConstant()) {
            return graph().unique(new IntegerAddExactNode(y(), x()));
        }
        if (x().isConstant()) {
            try {
                if (kind() == Kind.Int) {
                    return ConstantNode.forInt(ExactMath.addExact(x().asConstant().asInt(), y().asConstant().asInt()), graph());
                } else {
                    assert kind() == Kind.Long;
                    return ConstantNode.forLong(ExactMath.addExact(x().asConstant().asLong(), y().asConstant().asLong()), graph());
                }
            } catch (ArithmeticException ex) {
                // The operation will result in an overflow exception, so do not canonicalize.
            }
        } else if (y().isConstant()) {
            long c = y().asConstant().asLong();
            if (c == 0) {
                return x();
            }
        }
        return this;
    }

    @Override
    public IntegerExactArithmeticSplitNode createSplit(AbstractBeginNode next, AbstractBeginNode deopt) {
        return graph().add(new IntegerAddExactSplitNode(stamp(), x(), y(), next, deopt));
    }

    @Override
    public void lower(LoweringTool tool) {
        IntegerExactArithmeticSplitNode.lower(tool, this);
    }

    @NodeIntrinsic
    public static int addExact(int a, int b) {
        return ExactMath.addExact(a, b);
    }

    @NodeIntrinsic
    public static long addExact(long a, long b) {
        return ExactMath.addExact(a, b);
    }
}
