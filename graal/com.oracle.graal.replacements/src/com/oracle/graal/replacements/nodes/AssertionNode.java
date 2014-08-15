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
package com.oracle.graal.replacements.nodes;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Assertion nodes will go away as soon as the value evaluates to true. Compile-time assertions will
 * fail if this has not happened by the time the node is lowered to LIR, while runtime assertions
 * may need to insert a check.
 */
@NodeInfo
public class AssertionNode extends FixedWithNextNode implements Lowerable, Canonicalizable, LIRLowerable {

    @Input private ValueNode value;

    private final boolean compileTimeAssertion;
    private final String message;

    public AssertionNode(boolean compileTimeAssertion, ValueNode value, String message) {
        super(StampFactory.forVoid());
        this.value = value;
        this.compileTimeAssertion = compileTimeAssertion;
        this.message = message;
    }

    public ValueNode value() {
        return value;
    }

    public String message() {
        return message;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (value.isConstant() && value.asConstant().asInt() != 0) {
            return null;
        }
        /*
         * Assertions with a constant "false" value do not immediately cause an error, since they
         * may be unreachable and could thus be removed by later optimizations.
         */
        return this;
    }

    public void lower(LoweringTool tool) {
        if (!compileTimeAssertion) {
            tool.getLowerer().lower(this, tool);
        }
    }

    public void generate(NodeLIRBuilderTool generator) {
        assert compileTimeAssertion;
        if (value.isConstant() && value.asConstant().asInt() == 0) {
            throw new GraalInternalError("%s: failed compile-time assertion: %s", this, message);
        } else {
            throw new GraalInternalError("%s: failed compile-time assertion (value %s): %s", this, value, message);
        }
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void assertion(@ConstantNodeParameter boolean compileTimeAssertion, boolean value, @ConstantNodeParameter String message) {
        assert value : message;
    }
}
