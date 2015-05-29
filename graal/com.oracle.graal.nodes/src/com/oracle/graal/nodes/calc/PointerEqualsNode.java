/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.Canonicalizable.BinaryCommutative;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.jvmci.meta.*;

@NodeInfo(shortName = "==")
public class PointerEqualsNode extends CompareNode implements BinaryCommutative<ValueNode> {

    public static final NodeClass<PointerEqualsNode> TYPE = NodeClass.create(PointerEqualsNode.class);

    public PointerEqualsNode(ValueNode x, ValueNode y) {
        this(TYPE, x, y);
    }

    protected PointerEqualsNode(NodeClass<? extends PointerEqualsNode> c, ValueNode x, ValueNode y) {
        super(c, Condition.EQ, false, x, y);
        assert x.stamp() instanceof AbstractPointerStamp;
        assert y.stamp() instanceof AbstractPointerStamp;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        LogicNode result = findSynonym(forX, forY);
        if (result != null) {
            return result;
        }
        return super.canonical(tool, forX, forY);
    }

    public static LogicNode findSynonym(ValueNode forX, ValueNode forY) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return LogicConstantNode.tautology();
        } else if (forX.stamp().alwaysDistinct(forY.stamp())) {
            return LogicConstantNode.contradiction();
        } else if (((AbstractPointerStamp) forX.stamp()).alwaysNull()) {
            return new IsNullNode(forY);
        } else if (((AbstractPointerStamp) forY.stamp()).alwaysNull()) {
            return new IsNullNode(forX);
        } else {
            return null;
        }
    }

    @Override
    protected CompareNode duplicateModified(ValueNode newX, ValueNode newY) {
        return new PointerEqualsNode(newX, newY);
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated) {
        if (!negated) {
            Stamp xStamp = getX().stamp();
            Stamp newStamp = xStamp.join(getY().stamp());
            if (!newStamp.equals(xStamp)) {
                return newStamp;
            }
        }
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated) {
        if (!negated) {
            Stamp yStamp = getY().stamp();
            Stamp newStamp = yStamp.join(getX().stamp());
            if (!newStamp.equals(yStamp)) {
                return newStamp;
            }
        }
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        if (xStampGeneric instanceof ObjectStamp && yStampGeneric instanceof ObjectStamp) {
            ObjectStamp xStamp = (ObjectStamp) xStampGeneric;
            ObjectStamp yStamp = (ObjectStamp) yStampGeneric;
            if (xStamp.alwaysDistinct(yStamp)) {
                return TriState.FALSE;
            } else if (xStamp.neverDistinct(yStamp)) {
                return TriState.TRUE;
            }
        }
        return TriState.UNKNOWN;
    }
}
