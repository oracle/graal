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
package com.oracle.max.graal.nodes.calc;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;

/**
 * The {@code NegateNode} node negates its operand.
 */
public final class NegateNode extends FloatingNode implements Canonicalizable, LIRLowerable {

    @Input
    private ValueNode x;

    public ValueNode x() {
        return x;
    }

    /**
     * Creates new NegateOp instance.
     *
     * @param x the instruction producing the value that is input to this instruction
     */
    public NegateNode(ValueNode x) {
        super(StampFactory.forKind(x.kind()));
        this.x = x;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x().isConstant()) {
            switch (x().kind()) {
                case Int:
                    return ConstantNode.forInt(-x().asConstant().asInt(), graph());
                case Long:
                    return ConstantNode.forLong(-x().asConstant().asLong(), graph());
                case Float:
                    return ConstantNode.forFloat(-x().asConstant().asFloat(), graph());
                case Double:
                    return ConstantNode.forDouble(-x().asConstant().asDouble(), graph());
            }
        }
        if (x() instanceof NegateNode) {
            return ((NegateNode) x()).x();
        }
        return this;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.setResult(this, gen.emitNegate(gen.operand(x())));
    }
}
