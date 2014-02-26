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
package com.oracle.graal.nodes.calc;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Binary negation of long or integer values.
 */
public final class NotNode extends FloatingNode implements Canonicalizable, ArithmeticLIRLowerable, NarrowableArithmeticNode {

    @Input private ValueNode x;

    public ValueNode x() {
        return x;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(StampTool.not(x().stamp()));
    }

    @Override
    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 1;
        return Constant.forPrimitiveInt(PrimitiveStamp.getBits(stamp()), ~inputs[0].asLong());
    }

    /**
     * Creates new NegateNode instance.
     * 
     * @param x the instruction producing the value that is input to this instruction
     */
    public NotNode(ValueNode x) {
        super(StampTool.not(x.stamp()));
        this.x = x;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x().isConstant()) {
            return ConstantNode.forPrimitive(evalConst(x().asConstant()), graph());
        }
        if (x() instanceof NotNode) {
            return ((NotNode) x()).x();
        }
        return this;
    }

    @Override
    public void generate(ArithmeticLIRGenerator gen) {
        gen.setResult(this, gen.emitNot(gen.operand(x())));
    }
}
