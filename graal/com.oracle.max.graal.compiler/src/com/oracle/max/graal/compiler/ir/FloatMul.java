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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.NotifyReProcess;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;


/**
 *
 */
public final class FloatMul extends FloatArithmetic {
    private static final FloatMulCanonicalizerOp CANONICALIZER = new FloatMulCanonicalizerOp();

    /**
     * @param opcode
     * @param kind
     * @param x
     * @param y
     * @param isStrictFP
     * @param graph
     */
    public FloatMul(CiKind kind, Value x, Value y, boolean isStrictFP, Graph graph) {
        super(kind, kind == CiKind.Double ? Bytecodes.DMUL : Bytecodes.FMUL, x, y, isStrictFP, graph);
    }

    @Override
    public String shortName() {
        return "*";
    }

    @Override
    public Node copy(Graph into) {
        return new FloatMul(kind, null, null, isStrictFP(), into);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == CanonicalizerOp.class) {
            return (T) CANONICALIZER;
        }
        return super.lookup(clazz);
    }

    private static class FloatMulCanonicalizerOp implements CanonicalizerOp {
        @Override
        public Node canonical(Node node, NotifyReProcess reProcess) {
            FloatMul mul = (FloatMul) node;
            Value x = mul.x();
            Value y = mul.y();
            CiKind kind = mul.kind;
            Graph graph = mul.graph();
            if (x.isConstant() && !y.isConstant()) {
                mul.swapOperands();
                Value t = y;
                y = x;
                x = t;
            }
            if (x.isConstant()) {
                if (kind == CiKind.Float) {
                    return Constant.forFloat(x.asConstant().asFloat() * y.asConstant().asFloat(), graph);
                } else {
                    assert kind == CiKind.Double;
                    return Constant.forDouble(x.asConstant().asDouble() * y.asConstant().asDouble(), graph);
                }
            } else if (y.isConstant()) {
                if (kind == CiKind.Float) {
                    float c = y.asConstant().asFloat();
                    if (c == 0.0f) {
                        return Constant.forFloat(0.0f, graph);
                    }
                } else {
                    assert kind == CiKind.Double;
                    double c = y.asConstant().asDouble();
                    if (c == 0.0) {
                        return Constant.forDouble(0.0, graph);
                    }
                }
            }
            return mul;
        }
    }
}
