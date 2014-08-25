/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.nodes.arithmetic;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo
public class IntegerSubExactSplitNode extends IntegerExactArithmeticSplitNode {

    public static IntegerSubExactSplitNode create(Stamp stamp, ValueNode x, ValueNode y, BeginNode next, BeginNode overflowSuccessor) {
        return USE_GENERATED_NODES ? new IntegerSubExactSplitNodeGen(stamp, x, y, next, overflowSuccessor) : new IntegerSubExactSplitNode(stamp, x, y, next, overflowSuccessor);
    }

    protected IntegerSubExactSplitNode(Stamp stamp, ValueNode x, ValueNode y, BeginNode next, BeginNode overflowSuccessor) {
        super(stamp, x, y, next, overflowSuccessor);
    }

    @Override
    protected Value generateArithmetic(NodeLIRBuilderTool gen) {
        return gen.getLIRGeneratorTool().emitSub(gen.operand(getX()), gen.operand(getY()));
    }
}
