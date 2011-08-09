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
import com.oracle.max.graal.nodes.base.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

@NodeInfo(shortName = "*")
public final class IntegerMulNode extends IntegerArithmeticNode implements Canonicalizable {

    public IntegerMulNode(CiKind kind, ValueNode x, ValueNode y, Graph graph) {
        super(kind, kind == CiKind.Int ? Bytecodes.IMUL : Bytecodes.LMUL, x, y, graph);
    }

    @Override
    public Node canonical(NotifyReProcess reProcess) {
        if (x().isConstant() && !y().isConstant()) {
            swapOperands();
        }
        if (x().isConstant()) {
            if (kind == CiKind.Int) {
                return ConstantNode.forInt(x().asConstant().asInt() * y().asConstant().asInt(), graph());
            } else {
                assert kind == CiKind.Long;
                return ConstantNode.forLong(x().asConstant().asLong() * y().asConstant().asLong(), graph());
            }
        } else if (y().isConstant()) {
            long c = y().asConstant().asLong();
            if (c == 1) {
                return x();
            }
            if (c == 0) {
                return ConstantNode.forInt(0, graph());
            }
            if (c > 0 && CiUtil.isPowerOf2(c)) {
                return new LeftShiftNode(kind, x(), ConstantNode.forInt(CiUtil.log2(c), graph()), graph());
            }
        }
        return this;
    }
}
