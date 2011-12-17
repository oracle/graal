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
package com.oracle.max.graal.nodes.calc;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;

@NodeInfo(shortName = "/")
public final class FloatDivNode extends FloatArithmeticNode implements Canonicalizable, LIRLowerable {

    public FloatDivNode(CiKind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
        super(kind, x, y, isStrictFP);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x().isConstant() && y().isConstant()) {
            if (kind() == CiKind.Float) {
                return ConstantNode.forFloat(x().asConstant().asFloat() / y().asConstant().asFloat(), graph());
            } else {
                assert kind() == CiKind.Double;
                return ConstantNode.forDouble(x().asConstant().asDouble() / y().asConstant().asDouble(), graph());
            }
        }
        return this;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.setResult(this, gen.emitDiv(gen.operand(x()), gen.operand(y())));
    }
}
