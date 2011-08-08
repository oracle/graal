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


public final class FloatAdd extends FloatArithmetic {
    private static final FloatAddCanonicalizerOp CANONICALIZER = new FloatAddCanonicalizerOp();

    public FloatAdd(CiKind kind, Value x, Value y, boolean isStrictFP, Graph graph) {
        super(kind, kind == CiKind.Double ? Bytecodes.DADD : Bytecodes.FADD, x, y, isStrictFP, graph);
    }

    @Override
    public String shortName() {
        return "+";
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == CanonicalizerOp.class) {
            return (T) CANONICALIZER;
        }
        return super.lookup(clazz);
    }

    private static class FloatAddCanonicalizerOp implements CanonicalizerOp {
        @Override
        public Node canonical(Node node, NotifyReProcess reProcess) {
            FloatAdd add = (FloatAdd) node;
            Value x = add.x();
            Value y = add.y();
            CiKind kind = add.kind;
            Graph graph = add.graph();
            if (x.isConstant() && !y.isConstant()) {
                add.swapOperands();
                Value t = y;
                y = x;
                x = t;
            }
            if (x.isConstant()) {
                if (kind == CiKind.Float) {
                    return Constant.forFloat(x.asConstant().asFloat() + y.asConstant().asFloat(), graph);
                } else {
                    assert kind == CiKind.Double;
                    return Constant.forDouble(x.asConstant().asDouble() + y.asConstant().asDouble(), graph);
                }
            } else if (y.isConstant()) {
                if (kind == CiKind.Float) {
                    float c = y.asConstant().asFloat();
                    if (c == 0.0f) {
                        return x;
                    }
                } else {
                    assert kind == CiKind.Double;
                    double c = y.asConstant().asDouble();
                    if (c == 0.0) {
                        return x;
                    }
                }
            }
            return add;
        }
    }
}
