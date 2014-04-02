/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

@NodeInfo(shortName = "|%|")
public class UnsignedRemNode extends FixedBinaryNode implements Canonicalizable, Lowerable, LIRLowerable {

    /**
     * Used by {@code NodeIntrinsic} in {@code UnsignedMathSubstitutions}.
     */
    @SuppressWarnings("unused")
    private UnsignedRemNode(Kind kind, ValueNode x, ValueNode y) {
        this(StampFactory.forKind(kind), x, y);
    }

    public UnsignedRemNode(Stamp stamp, ValueNode x, ValueNode y) {
        super(stamp, x, y);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x().isConstant() && y().isConstant()) {
            long yConst = y().asConstant().asLong();
            if (yConst == 0) {
                return this; // this will trap, cannot canonicalize
            }
            return ConstantNode.forIntegerStamp(stamp(), UnsignedMath.remainder(x().asConstant().asLong(), yConst), graph());
        } else if (y().isConstant()) {
            long c = y().asConstant().asLong();
            if (c == 1) {
                return ConstantNode.forIntegerStamp(stamp(), 0, graph());
            } else if (CodeUtil.isPowerOf2(c)) {
                return graph().unique(new AndNode(stamp(), x(), ConstantNode.forIntegerStamp(stamp(), c - 1, graph())));
            }
        }
        return this;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitURem(gen.operand(x()), gen.operand(y()), this));
    }

    @Override
    public boolean canDeoptimize() {
        return !(y().stamp() instanceof IntegerStamp) || ((IntegerStamp) y().stamp()).contains(0);
    }
}
