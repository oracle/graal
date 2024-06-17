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

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.TriState;

@NodeInfo(shortName = "==")
public final class IntegerEqualsNode extends CompareNode implements Canonicalizable.BinaryCommutative<ValueNode> {
    public static final NodeClass<IntegerEqualsNode> TYPE = NodeClass.create(IntegerEqualsNode.class);
    private static final IntegerEqualsOp OP = new IntegerEqualsOp();

    public IntegerEqualsNode(ValueNode x, ValueNode y) {
        super(TYPE, CanonicalCondition.EQ, false, x, y);
        Stamp xStamp = x.stamp(NodeView.DEFAULT);
        Stamp yStamp = y.stamp(NodeView.DEFAULT);
        assert xStamp.isIntegerStamp() : "expected integer x value: " + x;
        assert yStamp.isIntegerStamp() : "expected integer y value: " + y;
        assert xStamp.isCompatible(yStamp) : "expected compatible stamps: " + xStamp + " / " + yStamp;
    }

    public static LogicNode create(ValueNode x, ValueNode y, NodeView view) {
        LogicNode result = tryConstantFoldPrimitive(CanonicalCondition.EQ, x, y, false, view);
        if (result != null) {
            return result;
        }
        return new IntegerEqualsNode(x, y).maybeCommuteInputs();
    }

    public static LogicNode create(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth, ValueNode x, ValueNode y,
                    NodeView view) {
        LogicNode value = OP.canonical(constantReflection, metaAccess, options, smallestCompareWidth, CanonicalCondition.EQ, false, x, y, view);
        if (value != null) {
            return value;
        }
        return create(x, y, view);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode value = OP.canonical(tool.getConstantReflection(), tool.getMetaAccess(), tool.getOptions(), tool.smallestCompareWidth(), CanonicalCondition.EQ, false, forX, forY, view);
        if (value != null) {
            return value;
        }
        return super.canonical(tool, forX, forY);
    }

    public static class IntegerEqualsOp extends CompareOp {
        @Override
        protected LogicNode optimizeNormalizeCompare(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                        Constant constant, AbstractNormalizeCompareNode normalizeNode, boolean mirrored, NodeView view) {
            PrimitiveConstant primitive = (PrimitiveConstant) constant;
            long cst = primitive.asLong();
            if (cst == 0) {
                return normalizeNode.createEqualComparison(constantReflection, metaAccess, options, smallestCompareWidth, view);
            } else if (cst == 1) {
                return normalizeNode.createLowerComparison(true, constantReflection, metaAccess, options, smallestCompareWidth, view);
            } else if (cst == -1) {
                return normalizeNode.createLowerComparison(false, constantReflection, metaAccess, options, smallestCompareWidth, view);
            } else {
                return LogicConstantNode.contradiction();
            }
        }

        @Override
        protected LogicNode duplicateModified(ValueNode newX, ValueNode newY, boolean unorderedIsTrue, NodeView view) {
            if (newX.stamp(view) instanceof IntegerStamp && newY.stamp(view) instanceof IntegerStamp) {
                return IntegerEqualsNode.create(newX, newY, view);
            }
            throw GraalError.shouldNotReachHere(newX.stamp(view) + " " + newY.stamp(view)); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public LogicNode canonical(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth, CanonicalCondition condition,
                        boolean unorderedIsTrue, ValueNode forX, ValueNode forY, NodeView view) {
            if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
                return LogicConstantNode.tautology();
            } else if (forX.stamp(view).alwaysDistinct(forY.stamp(view))) {
                return LogicConstantNode.contradiction();
            }

            if ((forX instanceof AddNode && forY instanceof AddNode) || (forX instanceof XorNode && forY instanceof XorNode)) {
                BinaryNode addX = (BinaryNode) forX;
                BinaryNode addY = (BinaryNode) forY;
                ValueNode v1 = null;
                ValueNode v2 = null;
                if (addX.getX() == addY.getX()) {
                    // (x op y) == (x op z) => y == z for op == + || op == ^
                    v1 = addX.getY();
                    v2 = addY.getY();
                } else if (addX.getX() == addY.getY()) {
                    // (x op y) == (z op x) => y == z for op == + || op == ^
                    v1 = addX.getY();
                    v2 = addY.getX();
                } else if (addX.getY() == addY.getX()) {
                    // (y op x) == (x op z) => y == z for op == + || op == ^
                    v1 = addX.getX();
                    v2 = addY.getY();
                } else if (addX.getY() == addY.getY()) {
                    // (y op x) == (z op x) => y == z for op == + || op == ^
                    v1 = addX.getX();
                    v2 = addY.getX();
                }
                if (v1 != null) {
                    assert v2 != null;
                    return create(v1, v2, view);
                }
            }

            if (forX instanceof SubNode && forY instanceof SubNode) {
                SubNode subX = (SubNode) forX;
                SubNode subY = (SubNode) forY;
                ValueNode v1 = null;
                ValueNode v2 = null;
                if (subX.getX() == subY.getX()) {
                    // (x - y) == (x - z) => y == z
                    v1 = subX.getY();
                    v2 = subY.getY();
                } else if (subX.getY() == subY.getY()) {
                    // (y - x) == (z - x) => y == z
                    v1 = subX.getX();
                    v2 = subY.getX();
                }
                if (v1 != null) {
                    assert v2 != null;
                    return create(v1, v2, view);
                }
            }

            if (forX instanceof AddNode || forX instanceof XorNode) {
                BinaryNode binaryNode = (BinaryNode) forX;
                if (binaryNode.getX() == forY) {
                    // (x op y) == x => y == 0 for op == + || op == ^
                    return create(binaryNode.getY(), ConstantNode.forIntegerStamp(view.stamp(binaryNode), 0), view);
                } else if (binaryNode.getY() == forY) {
                    // (x op y) == y => x == 0 for op == + || op == ^
                    return create(binaryNode.getX(), ConstantNode.forIntegerStamp(view.stamp(binaryNode), 0), view);
                }
            }

            if (forY instanceof AddNode || forY instanceof XorNode) {
                BinaryNode binaryNode = (BinaryNode) forY;
                if (binaryNode.getX() == forX) {
                    // x == (x op y) => y == 0 for op == + || op == ^
                    return create(binaryNode.getY(), ConstantNode.forIntegerStamp(view.stamp(binaryNode), 0), view);
                } else if (binaryNode.getY() == forX) {
                    // y == (x op y) => x == 0 for op == + || op == ^
                    return create(binaryNode.getX(), ConstantNode.forIntegerStamp(view.stamp(binaryNode), 0), view);
                }
            }

            if (forX instanceof SubNode) {
                SubNode subNode = (SubNode) forX;
                if (subNode.getX() == forY) {
                    // (x - y) == x => y == 0
                    return create(subNode.getY(), ConstantNode.forIntegerStamp(view.stamp(subNode), 0), view);
                }
            }

            if (forY instanceof SubNode) {
                SubNode subNode = (SubNode) forY;
                if (forX == subNode.getX()) {
                    // x == (x - y) => y == 0
                    return create(subNode.getY(), ConstantNode.forIntegerStamp(view.stamp(subNode), 0), view);
                }
            }

            if (forX instanceof NotNode notY && notY.getValue() == forY) {
                // ~y == y => false
                return LogicConstantNode.contradiction();
            }
            if (forY instanceof NotNode notX && forX == notX.getValue()) {
                // x == ~x => false
                return LogicConstantNode.contradiction();
            }

            return super.canonical(constantReflection, metaAccess, options, smallestCompareWidth, condition, unorderedIsTrue, forX, forY, view);
        }

        @Override
        protected LogicNode canonicalizeSymmetricConstant(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                        CanonicalCondition condition, Constant constant, ValueNode nonConstant, boolean mirrored, boolean unorderedIsTrue, NodeView view) {
            if (constant instanceof PrimitiveConstant) {
                PrimitiveConstant primitiveConstant = (PrimitiveConstant) constant;
                IntegerStamp nonConstantStamp = ((IntegerStamp) nonConstant.stamp(view));
                if ((primitiveConstant.asLong() == 1 && nonConstantStamp.upperBound() == 1 && nonConstantStamp.lowerBound() == 0) ||
                                (primitiveConstant.asLong() == -1 && nonConstantStamp.upperBound() == 0 && nonConstantStamp.lowerBound() == -1)) {
                    // nonConstant can only be 0 or 1 (respective -1), test against 0 instead of 1
                    // (respective -1) for a more canonical graph and also to allow for faster
                    // execution
                    // on specific platforms.
                    return LogicNegationNode.create(
                                    IntegerEqualsNode.create(constantReflection, metaAccess, options, smallestCompareWidth, nonConstant, ConstantNode.forIntegerStamp(nonConstantStamp, 0),
                                                    view));
                } else if (primitiveConstant.asLong() == 0) {
                    if (nonConstant instanceof AndNode) {
                        AndNode andNode = (AndNode) nonConstant;
                        return new IntegerTestNode(andNode.getX(), andNode.getY());
                    } else if (nonConstant instanceof SubNode || nonConstant instanceof XorNode) {
                        BinaryNode binaryNode = (BinaryNode) nonConstant;
                        return IntegerEqualsNode.create(constantReflection, metaAccess, options, smallestCompareWidth, binaryNode.getX(), binaryNode.getY(), view);
                    } else if (nonConstant instanceof ShiftNode && nonConstant.stamp(view) instanceof IntegerStamp) {
                        if (nonConstant instanceof LeftShiftNode) {
                            LeftShiftNode shift = (LeftShiftNode) nonConstant;
                            if (shift.getY().isConstant()) {
                                int mask = shift.getShiftAmountMask();
                                int amount = shift.getY().asJavaConstant().asInt() & mask;
                                if (shift.getX().getStackKind() == JavaKind.Int) {
                                    return new IntegerTestNode(shift.getX(), ConstantNode.forInt(-1 >>> amount));
                                } else {
                                    assert shift.getX().getStackKind() == JavaKind.Long : Assertions.errorMessage(shift, shift.getX(), shift.getY());
                                    return new IntegerTestNode(shift.getX(), ConstantNode.forLong(-1L >>> amount));
                                }
                            }
                        } else if (nonConstant instanceof RightShiftNode) {
                            RightShiftNode shift = (RightShiftNode) nonConstant;
                            if (shift.getY().isConstant() && ((IntegerStamp) shift.getX().stamp(view)).isPositive()) {
                                int mask = shift.getShiftAmountMask();
                                int amount = shift.getY().asJavaConstant().asInt() & mask;
                                if (shift.getX().getStackKind() == JavaKind.Int) {
                                    return new IntegerTestNode(shift.getX(), ConstantNode.forInt(-1 << amount));
                                } else {
                                    assert shift.getX().getStackKind() == JavaKind.Long : Assertions.errorMessage(shift, shift.getX(), shift.getY());
                                    return new IntegerTestNode(shift.getX(), ConstantNode.forLong(-1L << amount));
                                }
                            }
                        } else if (nonConstant instanceof UnsignedRightShiftNode) {
                            UnsignedRightShiftNode shift = (UnsignedRightShiftNode) nonConstant;
                            if (shift.getY().isConstant()) {
                                int mask = shift.getShiftAmountMask();
                                int amount = shift.getY().asJavaConstant().asInt() & mask;
                                if (shift.getX().getStackKind() == JavaKind.Int) {
                                    return new IntegerTestNode(shift.getX(), ConstantNode.forInt(-1 << amount));
                                } else {
                                    assert shift.getX().getStackKind() == JavaKind.Long : Assertions.errorMessage(shift, shift.getX(), shift.getY());
                                    return new IntegerTestNode(shift.getX(), ConstantNode.forLong(-1L << amount));
                                }
                            }
                        }
                    }
                }
                if (nonConstant instanceof AddNode) {
                    AddNode addNode = (AddNode) nonConstant;
                    if (addNode.getY().isJavaConstant()) {
                        return new IntegerEqualsNode(addNode.getX(), ConstantNode.forIntegerStamp(nonConstantStamp, primitiveConstant.asLong() - addNode.getY().asJavaConstant().asLong()));
                    }
                }
                if (nonConstant instanceof AndNode) {
                    /*
                     * a & c == c is the same as a & c != 0, if c is a single bit.
                     */
                    AndNode andNode = (AndNode) nonConstant;
                    if (Long.bitCount(((PrimitiveConstant) constant).asLong()) == 1 && andNode.getY().isConstant() && andNode.getY().asJavaConstant().equals(constant)) {
                        return LogicNegationNode.create(new IntegerTestNode(andNode.getX(), andNode.getY()));
                    }
                }

                if (nonConstant instanceof XorNode && nonConstant.stamp(view) instanceof IntegerStamp) {
                    XorNode xorNode = (XorNode) nonConstant;
                    if (xorNode.getY().isJavaConstant() && xorNode.getY().asJavaConstant().asLong() == 1 && ((IntegerStamp) xorNode.getX().stamp(view)).mayBeSet() == 1) {
                        // x ^ 1 == 0 is the same as x == 1 if x in [0, 1]
                        // x ^ 1 == 1 is the same as x == 0 if x in [0, 1]
                        return new IntegerEqualsNode(xorNode.getX(), ConstantNode.forIntegerStamp(xorNode.getX().stamp(view), primitiveConstant.asLong() ^ 1));
                    }
                }
            }
            return super.canonicalizeSymmetricConstant(constantReflection, metaAccess, options, smallestCompareWidth, condition, constant, nonConstant, mirrored, unorderedIsTrue, view);
        }
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated, Stamp xStamp, Stamp yStamp) {
        if (!negated) {
            return xStamp.join(yStamp);
        }
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated, Stamp xStamp, Stamp yStamp) {
        if (!negated) {
            return xStamp.join(yStamp);
        }
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        if (xStampGeneric instanceof IntegerStamp && yStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
            if (xStamp.alwaysDistinct(yStamp)) {
                return TriState.FALSE;
            } else if (xStamp.neverDistinct(yStamp)) {
                return TriState.TRUE;
            }
        }
        return TriState.UNKNOWN;
    }

    @Override
    public TriState implies(boolean thisNegated, LogicNode other) {
        // x == y => !(x < y)
        // x == y => !(y < x)
        if (!thisNegated && other instanceof IntegerLowerThanNode) {
            ValueNode otherX = ((IntegerLowerThanNode) other).getX();
            ValueNode otherY = ((IntegerLowerThanNode) other).getY();
            if ((getX() == otherX && getY() == otherY) || (getX() == otherY && getY() == otherX)) {
                return TriState.FALSE;
            }
        }
        return super.implies(thisNegated, other);
    }
}
