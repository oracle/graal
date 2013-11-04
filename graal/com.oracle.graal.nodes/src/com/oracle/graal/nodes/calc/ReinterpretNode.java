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
package com.oracle.graal.nodes.calc;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code ReinterpretNode} class represents a reinterpreting conversion between primitive types.
 */
public class ReinterpretNode extends FloatingNode implements Canonicalizable, ArithmeticLIRLowerable {

    @Input private ValueNode value;

    public ValueNode value() {
        return value;
    }

    public ReinterpretNode(Kind to, ValueNode value) {
        super(StampFactory.forKind(to.getStackKind()));
        this.value = value;
    }

    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 1;
        Constant c = inputs[0];
        assert c.getKind() == value.kind();
        switch (c.getKind()) {
            case Int:
                switch (kind()) {
                    case Int:
                        return c;
                    case Long:
                        return Constant.forLong(c.asInt() & 0xFFFFFFFFL);
                    case Float:
                        return Constant.forFloat(Float.intBitsToFloat(c.asInt()));
                    case Double:
                        return Constant.forDouble(Double.longBitsToDouble(c.asInt() & 0xFFFFFFFFL));
                }
                break;
            case Long:
                switch (kind()) {
                    case Int:
                        return Constant.forInt((int) c.asLong());
                    case Long:
                        return c;
                    case Float:
                        return Constant.forFloat(Float.intBitsToFloat((int) c.asLong()));
                    case Double:
                        return Constant.forDouble(Double.longBitsToDouble(c.asLong()));
                }
                break;
            case Float:
                switch (kind()) {
                    case Int:
                        return Constant.forInt(Float.floatToRawIntBits(c.asFloat()));
                    case Long:
                        return Constant.forLong(Float.floatToRawIntBits(c.asFloat()) & 0xFFFFFFFFL);
                    case Float:
                        return c;
                    case Double:
                        return Constant.forDouble(Double.longBitsToDouble(Float.floatToRawIntBits(c.asFloat()) & 0xFFFFFFFFL));
                }
                break;
            case Double:
                switch (kind()) {
                    case Int:
                        return Constant.forInt((int) Double.doubleToRawLongBits(c.asDouble()));
                    case Long:
                        return Constant.forLong(Double.doubleToRawLongBits(c.asDouble()));
                    case Float:
                        return Constant.forFloat(Float.intBitsToFloat((int) Double.doubleToRawLongBits(c.asDouble())));
                    case Double:
                        return c;
                }
                break;
        }
        throw GraalInternalError.shouldNotReachHere();
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (value.isConstant()) {
            return ConstantNode.forPrimitive(evalConst(value.asConstant()), graph());
        }
        return this;
    }

    @Override
    public void generate(ArithmeticLIRGenerator gen) {
        gen.setResult(this, gen.emitReinterpret(kind(), gen.operand(value())));
    }

    public static ValueNode reinterpret(Kind toKind, ValueNode value) {
        Kind fromKind = value.kind();
        if (fromKind == toKind) {
            return value;
        }
        return value.graph().unique(new ReinterpretNode(toKind, value));
    }

    @NodeIntrinsic
    public static native float reinterpret(@ConstantNodeParameter Kind kind, int value);

    @NodeIntrinsic
    public static native int reinterpret(@ConstantNodeParameter Kind kind, float value);

    @NodeIntrinsic
    public static native double reinterpret(@ConstantNodeParameter Kind kind, long value);

    @NodeIntrinsic
    public static native long reinterpret(@ConstantNodeParameter Kind kind, double value);
}
