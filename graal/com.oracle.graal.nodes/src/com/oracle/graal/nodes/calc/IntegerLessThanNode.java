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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;

@NodeInfo(shortName = "<")
public final class IntegerLessThanNode extends CompareNode {
    public static final NodeClass<IntegerLessThanNode> TYPE = NodeClass.create(IntegerLessThanNode.class);

    public IntegerLessThanNode(ValueNode x, ValueNode y) {
        super(TYPE, Condition.LT, false, x, y);
        assert !x.getKind().isNumericFloat() && x.getKind() != Kind.Object;
        assert !y.getKind().isNumericFloat() && y.getKind() != Kind.Object;
    }

    public static LogicNode create(ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection) {
        LogicNode result = CompareNode.tryConstantFold(Condition.LT, x, y, constantReflection, false);
        if (result != null) {
            return result;
        } else {
            result = findSynonym(x, y);
            if (result != null) {
                return result;
            }
            return new IntegerLessThanNode(x, y);
        }
    }

    @Override
    protected ValueNode optimizeNormalizeCmp(Constant constant, NormalizeCompareNode normalizeNode, boolean mirrored) {
        PrimitiveConstant primitive = (PrimitiveConstant) constant;
        assert condition() == Condition.LT;
        if (primitive.getKind() == Kind.Int && primitive.asInt() == 0) {
            ValueNode a = mirrored ? normalizeNode.getY() : normalizeNode.getX();
            ValueNode b = mirrored ? normalizeNode.getX() : normalizeNode.getY();

            if (normalizeNode.getX().getKind() == Kind.Double || normalizeNode.getX().getKind() == Kind.Float) {
                return new FloatLessThanNode(a, b, mirrored ^ normalizeNode.isUnorderedLess);
            } else {
                return new IntegerLessThanNode(a, b);
            }
        }
        return this;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode result = super.canonical(tool, forX, forY);
        if (result != this) {
            return result;
        }
        ValueNode synonym = findSynonym(forX, forY);
        if (synonym != null) {
            return synonym;
        }
        if (forX.stamp() instanceof IntegerStamp && forY.stamp() instanceof IntegerStamp) {
            if (IntegerStamp.sameSign((IntegerStamp) forX.stamp(), (IntegerStamp) forY.stamp())) {
                return new IntegerBelowNode(forX, forY);
            }
        }
        return this;
    }

    private static LogicNode findSynonym(ValueNode forX, ValueNode forY) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return LogicConstantNode.contradiction();
        } else if (forX.stamp() instanceof IntegerStamp && forY.stamp() instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) forX.stamp();
            IntegerStamp yStamp = (IntegerStamp) forY.stamp();
            if (xStamp.upperBound() < yStamp.lowerBound()) {
                return LogicConstantNode.tautology();
            } else if (xStamp.lowerBound() >= yStamp.upperBound()) {
                return LogicConstantNode.contradiction();
            }
        }
        return null;
    }

    @Override
    protected CompareNode duplicateModified(ValueNode newX, ValueNode newY) {
        if (newX.stamp() instanceof FloatStamp && newY.stamp() instanceof FloatStamp) {
            return new FloatLessThanNode(newX, newY, true);
        } else if (newX.stamp() instanceof IntegerStamp && newY.stamp() instanceof IntegerStamp) {
            return new IntegerLessThanNode(newX, newY);
        }
        throw GraalInternalError.shouldNotReachHere();
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated) {
        Stamp xStampGeneric = getX().stamp();
        Stamp yStampGeneric = getY().stamp();
        if (xStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            int bits = xStamp.getBits();
            if (yStampGeneric instanceof IntegerStamp) {
                IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
                assert yStamp.getBits() == bits;
                if (negated) {
                    // x >= y
                    long xLowerBound = xStamp.lowerBound();
                    long yLowerBound = yStamp.lowerBound();
                    if (yLowerBound > xLowerBound) {
                        return new IntegerStamp(bits, yLowerBound, xStamp.upperBound(), xStamp.downMask(), xStamp.upMask());
                    }
                } else {
                    // x < y
                    long xUpperBound = xStamp.upperBound();
                    long yUpperBound = yStamp.upperBound();
                    if (yUpperBound == CodeUtil.minValue(bits)) {
                        return null;
                    } else if (yUpperBound <= xUpperBound) {
                        assert yUpperBound != CodeUtil.minValue(bits);
                        return new IntegerStamp(bits, xStamp.lowerBound(), yUpperBound - 1, xStamp.downMask(), xStamp.upMask());
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated) {
        Stamp xStampGeneric = getX().stamp();
        Stamp yStampGeneric = getY().stamp();
        if (xStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            int bits = xStamp.getBits();
            if (yStampGeneric instanceof IntegerStamp) {
                IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
                assert yStamp.getBits() == bits;
                if (negated) {
                    // y <= x
                    long xUpperBound = xStamp.upperBound();
                    long yUpperBound = yStamp.upperBound();
                    if (xUpperBound < yUpperBound) {
                        return new IntegerStamp(bits, yStamp.lowerBound(), xUpperBound, yStamp.downMask(), yStamp.upMask());
                    }
                } else {
                    // y > x
                    long xLowerBound = xStamp.lowerBound();
                    long yLowerBound = yStamp.lowerBound();
                    if (xLowerBound == CodeUtil.maxValue(bits)) {
                        return null;
                    } else if (xLowerBound >= yLowerBound) {
                        assert xLowerBound != CodeUtil.maxValue(bits);
                        return new IntegerStamp(bits, xLowerBound + 1, yStamp.upperBound(), yStamp.downMask(), yStamp.upMask());
                    }
                }
            }
        }
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        if (xStampGeneric instanceof IntegerStamp && yStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
            if (xStamp.upperBound() < yStamp.lowerBound()) {
                return TriState.TRUE;
            }
            if (xStamp.lowerBound() >= yStamp.upperBound()) {
                return TriState.FALSE;
            }
        }
        return TriState.UNKNOWN;
    }
}
