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

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;

/**
 * The {@code BinaryNode} class is the base of arithmetic and logic operations with two inputs.
 */
@NodeInfo
public abstract class BinaryNode extends FloatingNode implements Canonicalizable.Binary<ValueNode> {

    @Input private ValueNode x;
    @Input private ValueNode y;

    public ValueNode getX() {
        return x;
    }

    public ValueNode getY() {
        return y;
    }

    /**
     * Creates a new BinaryNode instance.
     *
     * @param stamp the result type of this instruction
     * @param x the first input instruction
     * @param y the second input instruction
     */
    public BinaryNode(Stamp stamp, ValueNode x, ValueNode y) {
        super(stamp);
        this.x = x;
        this.y = y;
    }

    public enum ReassociateMatch {
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

    public static BinaryNode add(StructuredGraph graph, ValueNode x, ValueNode y) {
        assert x.stamp().isCompatible(y.stamp());
        Stamp stamp = x.stamp();
        if (stamp instanceof IntegerStamp) {
            return IntegerArithmeticNode.add(graph, x, y);
        } else if (stamp instanceof FloatStamp) {
            return graph.unique(new FloatAddNode(x, y, false));
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static BinaryNode sub(StructuredGraph graph, ValueNode x, ValueNode y) {
        assert x.stamp().isCompatible(y.stamp());
        Stamp stamp = x.stamp();
        if (stamp instanceof IntegerStamp) {
            return IntegerArithmeticNode.sub(graph, x, y);
        } else if (stamp instanceof FloatStamp) {
            return graph.unique(new FloatSubNode(x, y, false));
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static BinaryNode mul(StructuredGraph graph, ValueNode x, ValueNode y) {
        assert x.stamp().isCompatible(y.stamp());
        Stamp stamp = x.stamp();
        if (stamp instanceof IntegerStamp) {
            return IntegerArithmeticNode.mul(graph, x, y);
        } else if (stamp instanceof FloatStamp) {
            return graph.unique(new FloatMulNode(x, y, false));
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    public static boolean canTryReassociate(BinaryNode node) {
        return node instanceof IntegerAddNode || node instanceof IntegerSubNode || node instanceof IntegerMulNode || node instanceof AndNode || node instanceof OrNode || node instanceof XorNode;
    }

    public static ReassociateMatch findReassociate(BinaryNode binary, NodePredicate criterion) {
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
     * This method accepts only {@linkplain #canTryReassociate(BinaryNode) reassociable} operations
     * such as +, -, *, &amp;, | and ^
     *
     * @param forY
     * @param forX
     */
    public static BinaryNode reassociate(BinaryNode node, NodePredicate criterion, ValueNode forX, ValueNode forY) {
        assert canTryReassociate(node);
        ReassociateMatch match1 = findReassociate(node, criterion);
        if (match1 == null) {
            return node;
        }
        ValueNode otherValue = match1.getOtherValue(node);
        boolean addSub = false;
        boolean subAdd = false;
        if (otherValue.getClass() != node.getClass()) {
            if (node instanceof IntegerAddNode && otherValue instanceof IntegerSubNode) {
                addSub = true;
            } else if (node instanceof IntegerSubNode && otherValue instanceof IntegerAddNode) {
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
        } else if (node instanceof IntegerSubNode && other instanceof IntegerSubNode) {
            invertA = match1 == ReassociateMatch.x ^ match2 == ReassociateMatch.x;
            aSub = match1 == ReassociateMatch.y && match2 == ReassociateMatch.y;
            invertM1 = match1 == ReassociateMatch.y && match2 == ReassociateMatch.x;
            invertM2 = match1 == ReassociateMatch.x && match2 == ReassociateMatch.x;
        }
        assert !(invertM1 && invertM2) && !(invertA && aSub);
        ValueNode m1 = match1.getValue(node);
        ValueNode m2 = match2.getValue(other);
        ValueNode a = match2.getOtherValue(other);
        if (node instanceof IntegerAddNode || node instanceof IntegerSubNode) {
            BinaryNode associated;
            if (invertM1) {
                associated = IntegerArithmeticNode.sub(m2, m1);
            } else if (invertM2) {
                associated = IntegerArithmeticNode.sub(m1, m2);
            } else {
                associated = IntegerArithmeticNode.add(m1, m2);
            }
            if (invertA) {
                return IntegerArithmeticNode.sub(associated, a);
            }
            if (aSub) {
                return IntegerArithmeticNode.sub(a, associated);
            }
            return IntegerArithmeticNode.add(a, associated);
        } else if (node instanceof IntegerMulNode) {
            return IntegerArithmeticNode.mul(a, IntegerAddNode.mul(m1, m2));
        } else if (node instanceof AndNode) {
            return BitLogicNode.and(a, BitLogicNode.and(m1, m2));
        } else if (node instanceof OrNode) {
            return BitLogicNode.or(a, BitLogicNode.or(m1, m2));
        } else if (node instanceof XorNode) {
            return BitLogicNode.xor(a, BitLogicNode.xor(m1, m2));
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }
}
