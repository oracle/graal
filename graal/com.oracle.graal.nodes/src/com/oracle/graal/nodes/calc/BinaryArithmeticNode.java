/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo
public abstract class BinaryArithmeticNode extends BinaryNode implements ArithmeticLIRLowerable {

    private final BinaryOp op;

    public BinaryArithmeticNode(BinaryOp op, ValueNode x, ValueNode y) {
        super(op.foldStamp(x.stamp(), y.stamp()), x, y);
        this.op = op;
    }

    public BinaryOp getOp() {
        return op;
    }

    @Override
    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 2;
        return op.foldConstant(inputs[0], inputs[1]);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (forX.isConstant() && forY.isConstant()) {
            Constant ret = op.foldConstant(forX.asConstant(), forY.asConstant());
            return ConstantNode.forPrimitive(stamp(), ret);
        }
        return this;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(op.foldStamp(getX().stamp(), getY().stamp()));
    }

    public static AddNode add(StructuredGraph graph, ValueNode v1, ValueNode v2) {
        return graph.unique(AddNode.create(v1, v2));
    }

    public static AddNode add(ValueNode v1, ValueNode v2) {
        return AddNode.create(v1, v2);
    }

    public static MulNode mul(StructuredGraph graph, ValueNode v1, ValueNode v2) {
        return graph.unique(MulNode.create(v1, v2));
    }

    public static MulNode mul(ValueNode v1, ValueNode v2) {
        return MulNode.create(v1, v2);
    }

    public static SubNode sub(StructuredGraph graph, ValueNode v1, ValueNode v2) {
        return graph.unique(SubNode.create(v1, v2));
    }

    public static SubNode sub(ValueNode v1, ValueNode v2) {
        return SubNode.create(v1, v2);
    }

    private enum ReassociateMatch {
        x,
        y;

        public ValueNode getValue(BinaryNode binary) {
            switch (this) {
                case x:
                    return binary.getX();
                case y:
                    return binary.getY();
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }

        public ValueNode getOtherValue(BinaryNode binary) {
            switch (this) {
                case x:
                    return binary.getY();
                case y:
                    return binary.getX();
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    private static ReassociateMatch findReassociate(BinaryNode binary, NodePredicate criterion) {
        boolean resultX = criterion.apply(binary.getX());
        boolean resultY = criterion.apply(binary.getY());
        if (resultX && !resultY) {
            return ReassociateMatch.x;
        }
        if (!resultX && resultY) {
            return ReassociateMatch.y;
        }
        return null;
    }

    //@formatter:off
    /*
     * In reassociate, complexity comes from the handling of IntegerSub (non commutative) which can
     * be mixed with IntegerAdd. It first tries to find m1, m2 which match the criterion :
     * (a o m2) o m1
     * (m2 o a) o m1
     * m1 o (a o m2)
     * m1 o (m2 o a)
     * It then produces 4 boolean for the -/+ cases:
     * invertA : should the final expression be like *-a (rather than a+*)
     * aSub : should the final expression be like a-* (rather than a+*)
     * invertM1 : should the final expression contain -m1
     * invertM2 : should the final expression contain -m2
     *
     */
    //@formatter:on
    /**
     * Tries to re-associate values which satisfy the criterion. For example with a constantness
     * criterion: {@code (a + 2) + 1 => a + (1 + 2)}
     * <p>
     * This method accepts only {@linkplain BinaryOp#isAssociative() associative} operations such as
     * +, -, *, &amp;, | and ^
     *
     * @param forY
     * @param forX
     */
    public static BinaryArithmeticNode reassociate(BinaryArithmeticNode node, NodePredicate criterion, ValueNode forX, ValueNode forY) {
        assert node.getOp().isAssociative();
        ReassociateMatch match1 = findReassociate(node, criterion);
        if (match1 == null) {
            return node;
        }
        ValueNode otherValue = match1.getOtherValue(node);
        boolean addSub = false;
        boolean subAdd = false;
        if (otherValue.getClass() != node.getClass()) {
            if (node instanceof AddNode && otherValue instanceof SubNode) {
                addSub = true;
            } else if (node instanceof SubNode && otherValue instanceof AddNode) {
                subAdd = true;
            } else {
                return node;
            }
        }
        BinaryNode other = (BinaryNode) otherValue;
        ReassociateMatch match2 = findReassociate(other, criterion);
        if (match2 == null) {
            return node;
        }
        boolean invertA = false;
        boolean aSub = false;
        boolean invertM1 = false;
        boolean invertM2 = false;
        if (addSub) {
            invertM2 = match2 == ReassociateMatch.y;
            invertA = !invertM2;
        } else if (subAdd) {
            invertA = invertM2 = match1 == ReassociateMatch.x;
            invertM1 = !invertM2;
        } else if (node instanceof SubNode && other instanceof SubNode) {
            invertA = match1 == ReassociateMatch.x ^ match2 == ReassociateMatch.x;
            aSub = match1 == ReassociateMatch.y && match2 == ReassociateMatch.y;
            invertM1 = match1 == ReassociateMatch.y && match2 == ReassociateMatch.x;
            invertM2 = match1 == ReassociateMatch.x && match2 == ReassociateMatch.x;
        }
        assert !(invertM1 && invertM2) && !(invertA && aSub);
        ValueNode m1 = match1.getValue(node);
        ValueNode m2 = match2.getValue(other);
        ValueNode a = match2.getOtherValue(other);
        if (node instanceof AddNode || node instanceof SubNode) {
            BinaryNode associated;
            if (invertM1) {
                associated = BinaryArithmeticNode.sub(m2, m1);
            } else if (invertM2) {
                associated = BinaryArithmeticNode.sub(m1, m2);
            } else {
                associated = BinaryArithmeticNode.add(m1, m2);
            }
            if (invertA) {
                return BinaryArithmeticNode.sub(associated, a);
            }
            if (aSub) {
                return BinaryArithmeticNode.sub(a, associated);
            }
            return BinaryArithmeticNode.add(a, associated);
        } else if (node instanceof MulNode) {
            return BinaryArithmeticNode.mul(a, AddNode.mul(m1, m2));
        } else if (node instanceof AndNode) {
            return AndNode.create(a, AndNode.create(m1, m2));
        } else if (node instanceof OrNode) {
            return OrNode.create(a, OrNode.create(m1, m2));
        } else if (node instanceof XorNode) {
            return XorNode.create(a, XorNode.create(m1, m2));
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }
}
