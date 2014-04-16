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
 * The {@code NegateNode} node negates its operand.
 */
public final class NegateNode extends FloatingNode implements Canonicalizable, ArithmeticLIRLowerable, NarrowableArithmeticNode {

    @Input private ValueNode x;

    public ValueNode x() {
        return x;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(StampTool.negate(x().stamp()));
    }

    /**
     * Creates new NegateNode instance.
     *
     * @param x the instruction producing the value that is input to this instruction
     */
    public NegateNode(ValueNode x) {
        super(StampTool.negate(x.stamp()));
        this.x = x;
    }

    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 1;
        Constant constant = inputs[0];
        switch (constant.getKind()) {
            case Int:
                return Constant.forInt(-(constant.asInt()));
            case Long:
                return Constant.forLong(-(constant.asLong()));
            case Float:
                return Constant.forFloat(-(constant.asFloat()));
            case Double:
                return Constant.forDouble(-(constant.asDouble()));
            default:
                throw GraalInternalError.shouldNotReachHere("unknown kind " + constant.getKind());
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x().isConstant()) {
            return ConstantNode.forPrimitive(evalConst(x.asConstant()), graph());
        }
        if (x() instanceof NegateNode) {
            return ((NegateNode) x()).x();
        }
        if (x() instanceof IntegerSubNode) {
            IntegerSubNode sub = (IntegerSubNode) x;
            return IntegerArithmeticNode.sub(graph(), sub.y(), sub.x());
        }
        return this;
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        builder.setResult(this, gen.emitNegate(builder.operand(x())));
    }
}
