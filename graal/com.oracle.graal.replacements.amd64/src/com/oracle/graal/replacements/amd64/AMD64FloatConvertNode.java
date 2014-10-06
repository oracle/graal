/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.amd64;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.FloatConvertOp;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.UnaryOp;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

/**
 * This node has the semantics of the AMD64 floating point conversions. It is used in the lowering
 * of the {@link FloatConvertNode} which, on AMD64 needs a {@link AMD64FloatConvertNode} plus some
 * fixup code that handles the corner cases that differ between AMD64 and Java.
 */
@NodeInfo
public class AMD64FloatConvertNode extends UnaryArithmeticNode implements ArithmeticLIRLowerable {

    public static AMD64FloatConvertNode create(UnaryOp op, ValueNode value) {
        return USE_GENERATED_NODES ? new AMD64FloatConvertNodeGen(op, value) : new AMD64FloatConvertNode(op, value);
    }

    protected AMD64FloatConvertNode(UnaryOp op, ValueNode value) {
        super(op, value);
    }

    @Override
    public Constant evalConst(Constant... inputs) {
        // this node should never have been created if its input is constant
        throw GraalInternalError.shouldNotReachHere();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        // nothing to do
        return this;
    }

    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        builder.setResult(this, gen.emitFloatConvert(((FloatConvertOp) getOp()).getFloatConvert(), builder.operand(getValue())));
    }
}
