/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.type.*;

/**
 * Compares two {@code char} arrays with the same length.
 */
public class CharArrayEqualsNode extends FloatingNode implements LIRGenLowerable, Canonicalizable {

    @Input private ValueNode thisArray;
    @Input private ValueNode thatArray;
    @Input private ValueNode length;

    public CharArrayEqualsNode(ValueNode thisArray, ValueNode thatArray, ValueNode length) {
        super(StampFactory.forKind(Kind.Boolean));
        this.thisArray = thisArray;
        this.thatArray = thatArray;
        this.length = length;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (thisArray.isConstant() && thatArray.isConstant()) {
            char[] array1 = (char[]) thisArray.asConstant().asObject();
            char[] array2 = (char[]) thatArray.asConstant().asObject();
            final boolean result = Arrays.equals(array1, array2);
            return ConstantNode.forBoolean(result, graph());
        }
        return this;
    }

    @NodeIntrinsic
    public static native boolean equals(char[] thisArray, char[] thatArray, int length);

    @Override
    public void generate(LIRGenerator gen) {
        Variable result = gen.newVariable(Kind.Boolean);
        gen.emitCharArrayEquals(result, gen.operand(thisArray), gen.operand(thatArray), gen.operand(length));
        gen.setResult(this, result);
    }
}
