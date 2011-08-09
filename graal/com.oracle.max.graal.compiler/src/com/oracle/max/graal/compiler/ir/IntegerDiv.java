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

import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

@NodeInfo(shortName = "/")
public final class IntegerDiv extends IntegerArithmeticNode {
    private static final IntegerDivCanonicalizerOp CANONICALIZER = new IntegerDivCanonicalizerOp();

    public IntegerDiv(CiKind kind, Value x, Value y, Graph graph) {
        super(kind, kind == CiKind.Int ? Bytecodes.IDIV : Bytecodes.LDIV, x, y, graph);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == CanonicalizerOp.class) {
            return (T) CANONICALIZER;
        }
        return super.lookup(clazz);
    }

    private static class IntegerDivCanonicalizerOp implements CanonicalizerOp {
        @Override
        public Node canonical(Node node, NotifyReProcess reProcess) {
            IntegerDiv div = (IntegerDiv) node;
            Value x = div.x();
            Value y = div.y();
            CiKind kind = div.kind;
            Graph graph = div.graph();
            if (x.isConstant() && y.isConstant()) {
                long yConst = y.asConstant().asLong();
                if (yConst == 0) {
                    return div; // this will trap, can not canonicalize
                }
                if (kind == CiKind.Int) {
                    return Constant.forInt(x.asConstant().asInt() / (int) yConst, graph);
                } else {
                    assert kind == CiKind.Long;
                    return Constant.forLong(x.asConstant().asLong() / yConst, graph);
                }
            } else if (y.isConstant()) {
                long c = y.asConstant().asLong();
                if (c == 1) {
                    return x;
                }
            }
            return div;
        }
    }
}
