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
package com.oracle.graal.loop;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.type.*;

public class DerivedOffsetInductionVariable extends InductionVariable {

    private InductionVariable base;
    private ValueNode offset;
    private IntegerArithmeticNode value;

    public DerivedOffsetInductionVariable(LoopEx loop, InductionVariable base, ValueNode offset, IntegerArithmeticNode value) {
        super(loop);
        this.base = base;
        this.offset = offset;
        this.value = value;
    }

    @Override
    public StructuredGraph graph() {
        return base.graph();
    }

    @Override
    public Direction direction() {
        return base.direction();
    }

    @Override
    public ValueNode valueNode() {
        return value;
    }

    @Override
    public boolean isConstantInit() {
        return offset.isConstant() && base.isConstantInit();
    }

    @Override
    public boolean isConstantStride() {
        return base.isConstantStride();
    }

    @Override
    public long constantInit() {
        return op(base.constantInit(), offset.asConstant().asLong());
    }

    @Override
    public long constantStride() {
        if (value instanceof IntegerSubNode && base.valueNode() == value.y()) {
            return -base.constantStride();
        }
        return base.constantStride();
    }

    @Override
    public ValueNode initNode() {
        return op(base.initNode(), offset);
    }

    @Override
    public ValueNode strideNode() {
        if (value instanceof IntegerSubNode && base.valueNode() == value.y()) {
            return graph().unique(new NegateNode(base.strideNode()));
        }
        return base.strideNode();
    }

    @Override
    public ValueNode extremumNode(boolean assumePositiveTripCount, Stamp stamp) {
        return op(base.extremumNode(assumePositiveTripCount, stamp), IntegerConvertNode.convert(offset, stamp));
    }

    @Override
    public ValueNode exitValueNode() {
        return op(base.exitValueNode(), offset);
    }

    @Override
    public boolean isConstantExtremum() {
        return offset.isConstant() && base.isConstantExtremum();
    }

    @Override
    public long constantExtremum() {
        return op(base.constantExtremum(), offset.asConstant().asLong());
    }

    private long op(long b, long o) {
        if (value instanceof IntegerAddNode) {
            return b + o;
        }
        if (value instanceof IntegerSubNode) {
            if (base.valueNode() == value.x()) {
                return b - o;
            } else {
                assert base.valueNode() == value.y();
                return o - b;
            }
        }
        throw GraalInternalError.shouldNotReachHere();
    }

    private ValueNode op(ValueNode b, ValueNode o) {
        if (value instanceof IntegerAddNode) {
            return IntegerArithmeticNode.add(graph(), b, o);
        }
        if (value instanceof IntegerSubNode) {
            if (base.valueNode() == value.x()) {
                return IntegerArithmeticNode.sub(graph(), b, o);
            } else {
                assert base.valueNode() == value.y();
                return IntegerArithmeticNode.sub(graph(), o, b);
            }
        }
        throw GraalInternalError.shouldNotReachHere();
    }
}
