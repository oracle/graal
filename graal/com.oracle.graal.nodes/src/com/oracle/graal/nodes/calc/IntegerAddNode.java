/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

@NodeInfo(shortName = "+")
public class IntegerAddNode extends IntegerArithmeticNode implements Canonicalizable, NarrowableArithmeticNode {

    public IntegerAddNode(Stamp stamp, ValueNode x, ValueNode y) {
        super(stamp, x, y);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(StampTool.add(x().stamp(), y().stamp()));
    }

    @Override
    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 2;
        return Constant.forPrimitiveInt(PrimitiveStamp.getBits(stamp()), inputs[0].asLong() + inputs[1].asLong());
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x().isConstant() && !y().isConstant()) {
            return graph().unique(new IntegerAddNode(stamp(), y(), x()));
        }
        if (x() instanceof IntegerSubNode) {
            IntegerSubNode sub = (IntegerSubNode) x();
            if (sub.y() == y()) {
                // (a - b) + b
                return sub.x();
            }
        }
        if (y() instanceof IntegerSubNode) {
            IntegerSubNode sub = (IntegerSubNode) y();
            if (sub.y() == x()) {
                // b + (a - b)
                return sub.x();
            }
        }
        if (x().isConstant()) {
            return ConstantNode.forPrimitive(evalConst(x().asConstant(), y().asConstant()), graph());
        } else if (y().isConstant()) {
            long c = y().asConstant().asLong();
            if (c == 0) {
                return x();
            }
            // canonicalize expressions like "(a + 1) + 2"
            BinaryNode reassociated = BinaryNode.reassociate(this, ValueNode.isConstantPredicate());
            if (reassociated != this) {
                return reassociated;
            }
        }
        if (x() instanceof NegateNode) {
            return IntegerArithmeticNode.sub(graph(), y(), ((NegateNode) x()).x());
        } else if (y() instanceof NegateNode) {
            return IntegerArithmeticNode.sub(graph(), x(), ((NegateNode) y()).x());
        }
        return this;
    }

    @Override
    public void generate(ArithmeticLIRGenerator gen) {
        Value op1 = gen.operand(x());
        assert op1 != null : x() + ", this=" + this;
        Value op2 = gen.operand(y());
        if (!y().isConstant() && !FloatAddNode.livesLonger(this, y(), gen)) {
            Value op = op1;
            op1 = op2;
            op2 = op;
        }
        gen.setResult(this, gen.emitAdd(op1, op2));
    }

    @Override
    public boolean generate(MemoryArithmeticLIRLowerer gen, Access access) {
        Value result = gen.emitAddMemory(x(), y(), access);
        if (result != null) {
            gen.setResult(this, result);
        }
        return result != null;
    }
}
