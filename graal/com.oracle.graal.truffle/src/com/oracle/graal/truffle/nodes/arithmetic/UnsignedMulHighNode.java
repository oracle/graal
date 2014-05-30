/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.truffle.api.*;

@NodeInfo(shortName = "|*H|")
public class UnsignedMulHighNode extends IntegerArithmeticNode {

    public UnsignedMulHighNode(ValueNode x, ValueNode y) {
        this(x.stamp().unrestricted(), x, y);
    }

    public UnsignedMulHighNode(Stamp stamp, ValueNode x, ValueNode y) {
        super(stamp, x, y);
    }

    @Override
    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 2 && inputs[0].getKind() == inputs[1].getKind();
        switch (inputs[0].getKind()) {
            case Int:
                return Constant.forInt(ExactMath.multiplyHighUnsigned(inputs[0].asInt(), inputs[1].asInt()));
            case Long:
                return Constant.forLong(ExactMath.multiplyHighUnsigned(inputs[0].asLong(), inputs[1].asLong()));
            default:
                throw GraalInternalError.unimplemented();
        }
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        Value a = builder.operand(x());
        Value b = builder.operand(y());
        builder.setResult(this, gen.emitUMulHigh(a, b));
    }

    @NodeIntrinsic
    public static int multiplyHighUnsigned(int a, int b) {
        return ExactMath.multiplyHighUnsigned(a, b);
    }

    @NodeIntrinsic
    public static long multiplyHighUnsigned(long a, long b) {
        return ExactMath.multiplyHighUnsigned(a, b);
    }
}
