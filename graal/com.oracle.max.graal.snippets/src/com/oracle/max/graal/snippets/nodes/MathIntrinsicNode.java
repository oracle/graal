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
package com.oracle.max.graal.snippets.nodes;

import com.oracle.max.graal.compiler.target.amd64.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.oracle.max.graal.snippets.target.amd64.*;
import com.sun.cri.ci.*;

public class MathIntrinsicNode extends FloatingNode implements Canonicalizable, AMD64LIRLowerable {

    @Input private ValueNode x;
    @Data private final Operation operation;

    public enum Operation {
        ABS, SQRT, LOG, LOG10, SIN, COS, TAN,
    }

    public ValueNode x() {
        return x;
    }

    public Operation operation() {
        return operation;
    }

    public MathIntrinsicNode(ValueNode x, Operation op) {
        super(StampFactory.forKind(x.kind()));
        assert x.kind() == CiKind.Double;
        this.x = x;
        this.operation = op;
    }

    @Override
    public void generateAmd64(AMD64LIRGenerator gen) {
        CiVariable input = gen.load(gen.operand(x()));
        CiVariable result = gen.newVariable(kind());
        switch (operation()) {
            case ABS:   gen.append(AMD64LogicFloatOpcode.DAND.create(result, input, CiConstant.forDouble(Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL)))); break;
            case SQRT:  gen.append(AMD64MathIntrinsicOpcode.SQRT.create(result, input)); break;
            case LOG:   gen.append(AMD64MathIntrinsicOpcode.LOG.create(result, input)); break;
            case LOG10: gen.append(AMD64MathIntrinsicOpcode.LOG10.create(result, input)); break;
            case SIN:   gen.append(AMD64MathIntrinsicOpcode.SIN.create(result, input)); break;
            case COS:   gen.append(AMD64MathIntrinsicOpcode.COS.create(result, input)); break;
            case TAN:   gen.append(AMD64MathIntrinsicOpcode.TAN.create(result, input)); break;
            default:    throw Util.shouldNotReachHere();
        }
        gen.setResult(this, result);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x().isConstant()) {
            double value = x().asConstant().asDouble();
            switch (operation()) {
                case ABS:   return ConstantNode.forDouble(Math.abs(value), graph());
                case SQRT:  return ConstantNode.forDouble(Math.sqrt(value), graph());
                case LOG:   return ConstantNode.forDouble(Math.log(value), graph());
                case LOG10: return ConstantNode.forDouble(Math.log10(value), graph());
                case SIN:   return ConstantNode.forDouble(Math.sin(value), graph());
                case COS:   return ConstantNode.forDouble(Math.cos(value), graph());
                case TAN:   return ConstantNode.forDouble(Math.tan(value), graph());
            }
        }
        return this;
    }

    @NodeIntrinsic
    public static double compute(double x, @ConstantNodeParameter Operation op) {
        throw new UnsupportedOperationException("This method may only be compiled with the Graal compiler");
    }
}
