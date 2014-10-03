/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;

@NodeInfo(shortName = "<")
public class FloatLessThanNode extends CompareNode {

    protected final boolean unorderedIsTrue;

    /**
     * Constructs a new floating point comparison node.
     *
     * @param x the instruction producing the first input to the instruction
     * @param y the instruction that produces the second input to this instruction
     * @param unorderedIsTrue whether a comparison that is undecided (involving NaNs, etc.) leads to
     *            a "true" result
     */
    public static FloatLessThanNode create(ValueNode x, ValueNode y, boolean unorderedIsTrue) {
        return USE_GENERATED_NODES ? new FloatLessThanNodeGen(x, y, unorderedIsTrue) : new FloatLessThanNode(x, y, unorderedIsTrue);
    }

    protected FloatLessThanNode(ValueNode x, ValueNode y, boolean unorderedIsTrue) {
        super(x, y);
        assert x.stamp() instanceof FloatStamp && y.stamp() instanceof FloatStamp;
        assert x.stamp().isCompatible(y.stamp());
        this.unorderedIsTrue = unorderedIsTrue;
    }

    @Override
    public Condition condition() {
        return Condition.LT;
    }

    @Override
    public boolean unorderedIsTrue() {
        return unorderedIsTrue;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode result = super.canonical(tool, forX, forY);
        if (result != this) {
            return result;
        }
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY) && !unorderedIsTrue()) {
            return LogicConstantNode.contradiction();
        }
        return this;
    }

    @Override
    protected CompareNode duplicateModified(ValueNode newX, ValueNode newY) {
        if (newX.stamp() instanceof FloatStamp && newY.stamp() instanceof FloatStamp) {
            return FloatLessThanNode.create(newX, newY, unorderedIsTrue);
        } else if (newX.stamp() instanceof IntegerStamp && newY.stamp() instanceof IntegerStamp) {
            return IntegerLessThanNode.create(newX, newY);
        }
        throw GraalInternalError.shouldNotReachHere();
    }
}
