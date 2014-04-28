/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

@NodeInfo(shortName = "-")
public class IntegerSubNode extends IntegerArithmeticNode implements Canonicalizable, NarrowableArithmeticNode {

    public IntegerSubNode(Stamp stamp, ValueNode x, ValueNode y) {
        super(stamp, x, y);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(StampTool.sub(x().stamp(), y().stamp()));
    }

    @Override
    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 2;
        return Constant.forPrimitiveInt(PrimitiveStamp.getBits(stamp()), inputs[0].asLong() - inputs[1].asLong());
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x() == y()) {
            return ConstantNode.forIntegerStamp(stamp(), 0, graph());
        }
        if (x() instanceof IntegerAddNode) {
            IntegerAddNode x = (IntegerAddNode) x();
            if (x.y() == y()) {
                // (a + b) - b
                return x.x();
            }
            if (x.x() == y()) {
                // (a + b) - a
                return x.y();
            }
        } else if (x() instanceof IntegerSubNode) {
            IntegerSubNode x = (IntegerSubNode) x();
            if (x.x() == y()) {
                // (a - b) - a
                return graph().unique(new NegateNode(x.y()));
            }
        }
        if (y() instanceof IntegerAddNode) {
            IntegerAddNode y = (IntegerAddNode) y();
            if (y.x() == x()) {
                // a - (a + b)
                return graph().unique(new NegateNode(y.y()));
            }
            if (y.y() == x()) {
                // b - (a + b)
                return graph().unique(new NegateNode(y.x()));
            }
        } else if (y() instanceof IntegerSubNode) {
            IntegerSubNode y = (IntegerSubNode) y();
            if (y.x() == x()) {
                // a - (a - b)
                return y.y();
            }
        }
        if (x().isConstant() && y().isConstant()) {
            return ConstantNode.forPrimitive(evalConst(x().asConstant(), y().asConstant()), graph());
        } else if (y().isConstant()) {
            long c = y().asConstant().asLong();
            if (c == 0) {
                return x();
            }
            BinaryNode reassociated = BinaryNode.reassociate(this, ValueNode.isConstantPredicate());
            if (reassociated != this) {
                return reassociated;
            }
            if (c < 0 || ((IntegerStamp) StampFactory.forKind(y().getKind())).contains(-c)) {
                // Adding a negative is more friendly to the backend since adds are
                // commutative, so prefer add when it fits.
                return IntegerArithmeticNode.add(graph(), x(), ConstantNode.forIntegerStamp(stamp(), -c, graph()));
            }
        } else if (x().isConstant()) {
            long c = x().asConstant().asLong();
            if (c == 0) {
                return graph().unique(new NegateNode(y()));
            }
            return BinaryNode.reassociate(this, ValueNode.isConstantPredicate());
        }
        if (y() instanceof NegateNode) {
            return IntegerArithmeticNode.add(graph(), x(), ((NegateNode) y()).x());
        }
        return this;
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        builder.setResult(this, gen.emitSub(builder.operand(x()), builder.operand(y())));
    }

    @Override
    public boolean generate(MemoryArithmeticLIRLowerer gen, Access access) {
        Value result = gen.emitSubMemory(x(), y(), access);
        if (result != null) {
            gen.setResult(this, result);
        }
        return result != null;
    }
}
