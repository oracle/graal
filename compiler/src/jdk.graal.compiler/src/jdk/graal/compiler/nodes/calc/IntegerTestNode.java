/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.nodes.calc;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.TriState;

/**
 * This node will perform a "test" operation on its arguments. Its result is equivalent to the
 * expression "(x &amp; y) == 0", meaning that it will return true if (and only if) no bit is set in
 * both x and y.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public final class IntegerTestNode extends BinaryOpLogicNode implements Canonicalizable.BinaryCommutative<ValueNode> {
    public static final NodeClass<IntegerTestNode> TYPE = NodeClass.create(IntegerTestNode.class);

    public IntegerTestNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y);
    }

    public static LogicNode create(ValueNode x, ValueNode y, NodeView view) {
        LogicNode value = canonical(x, y, view);
        if (value != null) {
            return value;
        }
        return new IntegerTestNode(x, y);
    }

    private static LogicNode canonical(ValueNode forX, ValueNode forY, NodeView view) {
        if (forX.isConstant() && forY.isConstant()) {
            if (forX.isJavaConstant() && forY.isJavaConstant()) {
                return LogicConstantNode.forBoolean((forX.asJavaConstant().asLong() & forY.asJavaConstant().asLong()) == 0);
            }
        }
        if (forX.stamp(view) instanceof IntegerStamp && forY.stamp(view) instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) forX.stamp(view);
            IntegerStamp yStamp = (IntegerStamp) forY.stamp(view);
            if ((xStamp.mayBeSet() & yStamp.mayBeSet()) == 0) {
                return LogicConstantNode.tautology();
            } else if ((xStamp.mustBeSet() & yStamp.mustBeSet()) != 0) {
                return LogicConstantNode.contradiction();
            }
            // this node is effectively an & operation x & y == 0 so part of the canonicalizations
            // for AndNode apply
            ValueNode newLHS = AndNode.eliminateRedundantBinaryArithmeticOp(forX, yStamp);
            if (newLHS != null) {
                return new IntegerTestNode(newLHS, forY);
            }
            ValueNode newRHS = AndNode.eliminateRedundantBinaryArithmeticOp(forY, xStamp);
            if (newRHS != null) {
                return new IntegerTestNode(forX, newRHS);
            }
        }
        return null;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode value = canonical(forX, forY, NodeView.from(tool));
        return value != null ? value : super.canonical(tool, forX, forY);
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated, Stamp xStamp, Stamp yStamp) {
        return getSucceedingStamp(negated, xStamp, yStamp);
    }

    private static Stamp getSucceedingStamp(boolean negated, Stamp xStampGeneric, Stamp otherStampGeneric) {
        if (xStampGeneric instanceof IntegerStamp && otherStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            IntegerStamp otherStamp = (IntegerStamp) otherStampGeneric;
            if (negated) {
                if (Long.bitCount(otherStamp.mayBeSet()) == 1) {
                    long newMustBeSet = xStamp.mustBeSet() | otherStamp.mayBeSet();
                    if (xStamp.mustBeSet() != newMustBeSet) {
                        return IntegerStamp.stampForMask(xStamp.getBits(), newMustBeSet, xStamp.mayBeSet()).join(xStamp);
                    }
                }
            } else {
                long restrictedMayBeSet = ((~otherStamp.mustBeSet()) & xStamp.mayBeSet());
                if (xStamp.mayBeSet() != restrictedMayBeSet) {
                    return IntegerStamp.stampForMask(xStamp.getBits(), xStamp.mustBeSet(), restrictedMayBeSet).join(xStamp);
                }
            }
        }
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated, Stamp xStamp, Stamp yStamp) {
        return getSucceedingStamp(negated, yStamp, xStamp);
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        if (xStampGeneric instanceof IntegerStamp && yStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
            if ((xStamp.mayBeSet() & yStamp.mayBeSet()) == 0) {
                return TriState.TRUE;
            } else if ((xStamp.mustBeSet() & yStamp.mustBeSet()) != 0) {
                return TriState.FALSE;
            }
        }
        return TriState.UNKNOWN;
    }
}
