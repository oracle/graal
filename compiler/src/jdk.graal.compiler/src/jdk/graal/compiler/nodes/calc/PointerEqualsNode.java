/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.AbstractBoxingNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.LoadMethodNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.options.OptionValues;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

@NodeInfo(shortName = "==")
public class PointerEqualsNode extends CompareNode implements Canonicalizable.BinaryCommutative<ValueNode> {

    public static final NodeClass<PointerEqualsNode> TYPE = NodeClass.create(PointerEqualsNode.class);
    private static final PointerEqualsOp OP = new PointerEqualsOp();

    public PointerEqualsNode(ValueNode x, ValueNode y) {
        this(TYPE, x, y);
    }

    public static LogicNode create(ValueNode x, ValueNode y, NodeView view) {
        LogicNode result = findSynonym(x, y, view);
        if (result != null) {
            return result;
        }
        return new PointerEqualsNode(x, y);
    }

    protected PointerEqualsNode(NodeClass<? extends PointerEqualsNode> c, ValueNode x, ValueNode y) {
        super(c, CanonicalCondition.EQ, false, x, y);
        assert x.stamp(NodeView.DEFAULT).isPointerStamp();
        assert y.stamp(NodeView.DEFAULT).isPointerStamp();
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode value = OP.canonical(tool.getConstantReflection(), tool.getMetaAccess(), tool.getOptions(), tool.smallestCompareWidth(), CanonicalCondition.EQ, false, forX, forY, view);
        if (value != null) {
            return value;
        }
        return this;
    }

    public static class PointerEqualsOp extends CompareOp {

        /**
         * Determines if this is a comparison used to determine whether dispatching on a receiver
         * could select a certain method and if so, returns {@code true} if the answer is guaranteed
         * to be false. Otherwise, returns {@code false}.
         */
        private static boolean isAlwaysFailingVirtualDispatchTest(CanonicalCondition condition, ValueNode forX, ValueNode forY) {
            if (forY.isConstant()) {
                if (forX instanceof LoadMethodNode && condition == CanonicalCondition.EQ) {
                    LoadMethodNode lm = ((LoadMethodNode) forX);
                    if (lm.getMethod().getEncoding().equals(forY.asConstant())) {
                        if (lm.getHub() instanceof LoadHubNode) {
                            ValueNode object = ((LoadHubNode) lm.getHub()).getValue();
                            ResolvedJavaType type = StampTool.typeOrNull(object);
                            ResolvedJavaType declaringClass = lm.getMethod().getDeclaringClass();
                            if (type != null && !type.equals(declaringClass) && declaringClass.isAssignableFrom(type)) {
                                ResolvedJavaMethod override = type.resolveConcreteMethod(lm.getMethod(), lm.getCallerType());
                                if (override != null && !override.equals(lm.getMethod())) {
                                    assert declaringClass.isAssignableFrom(override.getDeclaringClass());
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }

        /**
         * Determines if this is an equality comparison between pointers that can never be equal.
         * For example, newly allocated (non-boxing) objects are always unequal to each other. New
         * objects are also unequal to any method parameter or constant.
         *
         * @return {@code true} if this is an equality test that will always fail
         */
        private static boolean isAlwaysFailingEqualityTest(CanonicalCondition condition, ValueNode originalX, ValueNode originalY) {
            if (condition != CanonicalCondition.EQ) {
                return false;
            }
            ValueNode forX = GraphUtil.unproxify(originalX);
            ValueNode forY = GraphUtil.unproxify(originalY);
            if (forX != forY) {
                boolean xIsNonVirtualAllocation = forX instanceof AbstractNewObjectNode;
                boolean yIsNonVirtualAllocation = forY instanceof AbstractNewObjectNode;
                if (xIsNonVirtualAllocation && yIsNonVirtualAllocation) {
                    // Two distinct non-virtualized allocations can never equal.
                    return true;
                }

                boolean xIsVirtualAllocation = forX instanceof AllocatedObjectNode;
                boolean yIsVirtualAllocation = forY instanceof AllocatedObjectNode;
                boolean xIsAllocation = xIsNonVirtualAllocation || xIsVirtualAllocation;
                boolean yIsAllocation = yIsNonVirtualAllocation || yIsVirtualAllocation;
                assert !xIsAllocation || !(forX instanceof AbstractBoxingNode) : "unexpected class hierarchy change";
                assert !yIsAllocation || !(forY instanceof AbstractBoxingNode) : "unexpected class hierarchy change";

                boolean xIsParameter = forX instanceof ParameterNode;
                boolean yIsParameter = forY instanceof ParameterNode;
                if ((xIsAllocation && (yIsParameter || forY.isConstant())) || (yIsAllocation && (xIsParameter || forX.isConstant()))) {
                    // A new object can never equal a parameter or constant.
                    return true;
                }
            }
            return false;
        }

        @Override
        public LogicNode canonical(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                        CanonicalCondition condition,
                        boolean unorderedIsTrue, ValueNode forX, ValueNode forY, NodeView view) {
            LogicNode result = findSynonym(forX, forY, view);
            if (result != null) {
                return result;
            }
            if (isAlwaysFailingVirtualDispatchTest(condition, forX, forY)) {
                return LogicConstantNode.contradiction();
            }
            if (isAlwaysFailingEqualityTest(condition, forX, forY)) {
                return LogicConstantNode.contradiction();
            }
            return super.canonical(constantReflection, metaAccess, options, smallestCompareWidth, condition, unorderedIsTrue, forX, forY, view);
        }

        @Override
        protected CompareNode duplicateModified(ValueNode newX, ValueNode newY, boolean unorderedIsTrue, NodeView view) {
            return new PointerEqualsNode(newX, newY);
        }
    }

    public static LogicNode findSynonym(ValueNode forX, ValueNode forY, NodeView view) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return LogicConstantNode.tautology();
        } else if (forX.stamp(view).alwaysDistinct(forY.stamp(view))) {
            return LogicConstantNode.contradiction();
        } else if (forX.stamp(view) instanceof AbstractPointerStamp && ((AbstractPointerStamp) forX.stamp(view)).alwaysNull()) {
            return nullSynonym(forY, forX);
        } else if (forY.stamp(view) instanceof AbstractPointerStamp && ((AbstractPointerStamp) forY.stamp(view)).alwaysNull()) {
            return nullSynonym(forX, forY);
        } else if (forX instanceof BoxNode && forY instanceof BoxNode) {
            /*
             * We have a fast path here for box comparisons of constants to avoid wasting time in
             * PEA / lowering later.
             */
            BoxNode boxX = (BoxNode) forX;
            BoxNode boxY = (BoxNode) forY;
            if (boxX.getValue().isConstant() && boxY.getValue().isConstant()) {
                if (boxX.getBoxingKind() != boxY.getBoxingKind()) {
                    return LogicConstantNode.contradiction();
                }
                if (boxX.getValue().asConstant().equals(boxY.getValue().asConstant())) {
                    return LogicConstantNode.tautology();
                } else {
                    return LogicConstantNode.contradiction();
                }
            }
        }
        return null;
    }

    private static LogicNode nullSynonym(ValueNode nonNullValue, ValueNode nullValue) {
        if (nullValue.isConstant()) {
            return IsNullNode.create(nonNullValue, nullValue.asJavaConstant());
        } else {
            return IsNullNode.create(nonNullValue);
        }
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated, Stamp xStamp, Stamp yStamp) {
        if (!negated) {
            Stamp newStamp = xStamp.join(yStamp);
            if (!newStamp.equals(xStamp)) {
                return newStamp;
            }
        }
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated, Stamp xStamp, Stamp yStamp) {
        if (!negated) {
            Stamp newStamp = yStamp.join(xStamp);
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
