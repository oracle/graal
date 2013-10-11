/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.type.*;

public class BitScanForwardNode extends FloatingNode implements LIRGenLowerable, Canonicalizable {

    @Input private ValueNode value;

    public BitScanForwardNode(ValueNode value) {
        super(StampFactory.forInteger(Kind.Int, 0, value.kind().getBitCount()));
        this.value = value;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (value.isConstant()) {
            long v = value.asConstant().asLong();
            if (value.kind().getStackKind() == Kind.Int) {
                return ConstantNode.forInt(Integer.numberOfTrailingZeros((int) v), graph());
            } else if (value.kind() == Kind.Long) {
                return ConstantNode.forInt(Long.numberOfTrailingZeros(v), graph());
            }
        }
        return this;
    }

    @NodeIntrinsic
    public static int scan(long v) {
        if (v == 0) {
            return -1;
        }
        int index = 0;
        while (((1L << index) & v) == 0) {
            ++index;
        }
        return index;
    }

    @Override
    public void generate(LIRGenerator gen) {
        Variable result = gen.newVariable(Kind.Int);
        gen.emitBitScanForward(result, gen.operand(value));
        gen.setResult(this, result);
    }
}
