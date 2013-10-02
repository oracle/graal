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
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo(shortName = "-")
public final class FloatSubNode extends FloatArithmeticNode implements Canonicalizable {

    public FloatSubNode(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
        super(kind, x, y, isStrictFP);
    }

    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 2;
        if (kind() == Kind.Float) {
            return Constant.forFloat(inputs[0].asFloat() - inputs[1].asFloat());
        } else {
            assert kind() == Kind.Double;
            return Constant.forDouble(inputs[0].asDouble() - inputs[1].asDouble());
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x() == y()) {
            return ConstantNode.forFloatingKind(kind(), 0.0f, graph());
        }
        if (x().isConstant() && y().isConstant()) {
            return ConstantNode.forPrimitive(evalConst(x().asConstant(), y().asConstant()), graph());
        } else if (y().isConstant()) {
            if (kind() == Kind.Float) {
                float c = y().asConstant().asFloat();
                if (c == 0.0f) {
                    return x();
                }
                return graph().unique(new FloatAddNode(kind(), x(), ConstantNode.forFloat(-c, graph()), isStrictFP()));
            } else {
                assert kind() == Kind.Double;
                double c = y().asConstant().asDouble();
                if (c == 0.0) {
                    return x();
                }
                return graph().unique(new FloatAddNode(kind(), x(), ConstantNode.forDouble(-c, graph()), isStrictFP()));
            }
        }
        return this;
    }

    @Override
    public void generate(ArithmeticLIRGenerator gen) {
        gen.setResult(this, gen.emitSub(gen.operand(x()), gen.operand(y())));
    }
}
