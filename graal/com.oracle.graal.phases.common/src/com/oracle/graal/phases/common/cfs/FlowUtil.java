/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.cfs;

import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FlowUtil {

    private FlowUtil() {
        // no instances of this class
    }

    public static boolean lacksUsages(Node n) {
        return n.recordsUsages() && n.usages().isEmpty();
    }

    public static ResolvedJavaType widen(ResolvedJavaType a, ResolvedJavaType b) {
        if (a == null || b == null) {
            return null;
        } else if (a.equals(b)) {
            return a;
        } else {
            return a.findLeastCommonAncestor(b);
        }
    }

    /**
     * @return whether the first argument is strictly more precise than the second.
     */
    public static boolean isMorePrecise(ResolvedJavaType a, ResolvedJavaType b) {
        if (a == null) {
            return false;
        }
        if (b == null) {
            return true;
        }
        assert !a.isPrimitive();
        assert !b.isPrimitive();
        if (a.equals(b)) {
            return false;
        }
        if (b.isInterface()) {
            return b.isAssignableFrom(a);
        }
        if (a.isInterface()) {
            return b.isInterface() && b.isAssignableFrom(a);
        }
        return b.isAssignableFrom(a);
    }

    public static ResolvedJavaType tighten(ResolvedJavaType a, ResolvedJavaType b) {
        if (a == null) {
            assert b == null || !b.isPrimitive();
            return b;
        }
        if (b == null) {
            assert !a.isPrimitive();
            return a;
        }
        assert !a.isPrimitive();
        assert !b.isPrimitive();
        if (a.equals(b)) {
            return a;
        }
        if (isMorePrecise(a, b)) {
            return a;
        } else if (isMorePrecise(b, a)) {
            return b;
        } else {
            /*
             * Not comparable, two cases:
             * 
             * Example 1: 'a' standing for j.l.Number and 'b' for j.l.String We return null for lack
             * of a value representing NullType, the right answer. Same goes when both arguments are
             * non-comparable interfaces.
             * 
             * Example 2: 'a' standing for sun/nio/ch/DirectBuffer (an interface) and b for
             * java/nio/Buffer (an abstract class). The class always takes precedence.
             */
            if (a.isInterface()) {
                return b.isInterface() ? null : b;
            }
            if (b.isInterface()) {
                return a.isInterface() ? null : a;
            }
            return null; // a and b aren't comparable, can't tighten() them
        }
    }

    /**
     *
     * There are "illegal" stamps that are not of type IllegalStamp.
     *
     * For example, there may be an IntegerStamp with upperBound < lowerBound that returns
     * !isLegal() but we still know it's an integer and thus not of type IllegalStamp.
     *
     * An IllegalStamp should never happen. In contrast, !isLegal() values could happen due to dead
     * code not yet removed, or upon some non-sideeffecting instructions floating out of a dead
     * branch.
     */
    public static boolean isLegalObjectStamp(Stamp s) {
        return isObjectStamp(s) && s.isLegal();
    }

    public static boolean hasLegalObjectStamp(ValueNode v) {
        return isLegalObjectStamp(v.stamp());
    }

    public static boolean isObjectStamp(Stamp stamp) {
        return stamp instanceof ObjectStamp;
    }

    public static void inferStampAndCheck(ValueNode n) {
        n.inferStamp();
        if (n.stamp() instanceof ObjectStamp) {
            ObjectStamp objectStamp = (ObjectStamp) n.stamp();
            assert !objectStamp.isExactType() || objectStamp.type() != null;
        }
    }

    /**
     * Compares the arguments along three dimensions (nullness, exactness, and type). For the first
     * argument to be more precise than the second, it may not score lower in any dimension and must
     * score higher in at least one dimension.
     *
     * When comparing types s and t, sameness counts as 0; while being more precise is awarded with
     * a score of 1. In all other cases (non-comparable, or supertype) the score is -1.
     *
     * @return whether the first argument is strictly more precise than the second.
     */
    public static boolean isMorePrecise(ObjectStamp a, ObjectStamp b) {
        int d0 = MINUS(a.alwaysNull(), b.alwaysNull());
        if (d0 == -1) {
            return false;
        }
        int d1 = MINUS(a.nonNull(), b.nonNull());
        if (d1 == -1) {
            return false;
        }
        int d2 = MINUS(a.isExactType(), b.isExactType());
        if (d2 == -1) {
            return false;
        }
        int d3;
        ResolvedJavaType ta = a.type();
        ResolvedJavaType tb = b.type();
        if (ta == null) {
            d3 = (tb == null) ? 0 : -1;
        } else if (tb == null) {
            d3 = 1;
        } else if (isMorePrecise(ta, tb)) {
            d3 = 1;
        } else if (ta.equals(tb)) {
            d3 = 0;
        } else {
            d3 = -1;
        }
        if (d3 == -1) {
            return false;
        }
        int maxScore = Math.max(Math.max(d0, d1), Math.max(d2, d3));
        return maxScore > 0;
    }

    private static int MINUS(boolean a, boolean b) {
        int aa = a ? 1 : 0;
        int bb = b ? 1 : 0;
        return aa - bb;
    }

    public static LogicConstantNode asLogicConstantNode(LogicNode cond) {
        return (cond instanceof LogicConstantNode) ? (LogicConstantNode) cond : null;
    }

    public static boolean isLiteralNode(ValueNode f) {
        return f instanceof ConstantNode || f instanceof LogicConstantNode;
    }

    public static boolean isConstantTrue(LogicNode cond) {
        LogicConstantNode c = asLogicConstantNode(cond);
        return (c != null) && c.getValue();
    }

    public static boolean isConstantFalse(LogicNode cond) {
        LogicConstantNode c = asLogicConstantNode(cond);
        return (c != null) && !c.getValue();
    }

    public static boolean alwaysFails(boolean isNegated, LogicNode cond) {
        LogicConstantNode c = asLogicConstantNode(cond);
        return (c != null) && (c.getValue() == isNegated);
    }

    public static boolean alwaysSucceeds(boolean isNegated, LogicNode cond) {
        LogicConstantNode c = asLogicConstantNode(cond);
        return (c != null) && (c.getValue() != isNegated);
    }

    /**
     * Returns (preserving order) the ValueNodes without duplicates found among the argument's
     * direct inputs.
     */
    @SuppressWarnings("unchecked")
    public static List<ValueNode> distinctValueAndConditionInputs(Node n) {
        ArrayList<ValueNode> result = null;
        NodeClass.NodeClassIterator iter = n.inputs().iterator();
        while (iter.hasNext()) {
            NodeClass.Position pos = iter.nextPosition();
            InputType inputType = pos.getInputType(n);
            boolean isReducibleInput = (inputType == InputType.Value || inputType == InputType.Condition);
            if (isReducibleInput) {
                ValueNode i = (ValueNode) pos.get(n);
                if (!isLiteralNode(i)) {
                    if (result == null) {
                        result = new ArrayList<>();
                    }
                    if (!result.contains(i)) {
                        result.add(i);
                    }
                }
            }
        }
        return result == null ? Collections.EMPTY_LIST : result;
    }

    public static ObjectStamp asNonNullStamp(ObjectStamp stamp) {
        ObjectStamp result = (ObjectStamp) stamp.join(StampFactory.objectNonNull());
        assert result.isLegal();
        return result;
    }

    public static ObjectStamp asRefinedStamp(ObjectStamp stamp, ResolvedJavaType joinType) {
        assert !joinType.isInterface();
        ObjectStamp result = (ObjectStamp) stamp.join(StampFactory.declared(joinType));
        assert result.isLegal();
        return result;
    }

    /**
     * Start situation: the parent node has <code>oldInput</code> among its (direct) inputs. After
     * this method has run, all such occurrences have been replaced with <code>newInput</code>. In
     * case that makes <code>oldInput</code> disconnected, it is removed from the graph.
     */
    public static void replaceInPlace(Node parent, Node oldInput, Node newInput) {
        assert parent != null;
        assert parent.inputs().contains(oldInput);
        if (oldInput == newInput) {
            return;
        }
        assert oldInput != null && newInput != null;
        assert !isLiteralNode((ValueNode) oldInput);
        do {
            parent.replaceFirstInput(oldInput, newInput);
        } while (parent.inputs().contains(oldInput));
        // `oldInput` if unused wil be removed in finished()
    }

}
