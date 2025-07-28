/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.InputType.Condition;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProvidersDelegate;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.options.OptionValues;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.TriState;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class ShortCircuitOrNode extends LogicNode implements IterableNodeType, Canonicalizable.Binary<LogicNode> {
    public static final NodeClass<ShortCircuitOrNode> TYPE = NodeClass.create(ShortCircuitOrNode.class);
    @Input(Condition) LogicNode x;
    @Input(Condition) LogicNode y;
    protected boolean xNegated;
    protected boolean yNegated;
    protected ProfileData.BranchProbabilityData shortCircuitProbability;

    protected ShortCircuitOrNode(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, ProfileData.BranchProbabilityData shortCircuitProbability) {
        super(TYPE);
        this.x = x;
        this.xNegated = xNegated;
        this.y = y;
        this.yNegated = yNegated;
        this.shortCircuitProbability = shortCircuitProbability;
    }

    public static LogicNode create(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, ProfileData.BranchProbabilityData shortCircuitProbability) {
        LogicNode canonical = canonicalize(null, null, shortCircuitProbability, x, xNegated, y, yNegated);
        if (canonical != null) {
            return canonical;
        }
        return new ShortCircuitOrNode(x, xNegated, y, yNegated, shortCircuitProbability);
    }

    @Override
    public LogicNode getX() {
        return x;
    }

    @Override
    public LogicNode getY() {
        return y;
    }

    public boolean isXNegated() {
        return xNegated;
    }

    public boolean isYNegated() {
        return yNegated;
    }

    /**
     * Gets the probability that the {@link #getY() y} part of this binary node is <b>not</b>
     * evaluated. This is the probability that this operator will short-circuit its execution.
     */
    public ProfileData.BranchProbabilityData getShortCircuitProbability() {
        return shortCircuitProbability;
    }

    protected static ShortCircuitOrNode canonicalizeNegation(LogicNode forX, boolean xNegated, LogicNode forY, boolean yNegated, ProfileData.BranchProbabilityData shortCircuitProbability) {
        LogicNode xCond = forX;
        boolean xNeg = xNegated;
        while (xCond instanceof LogicNegationNode) {
            xCond = ((LogicNegationNode) xCond).getValue();
            xNeg = !xNeg;
        }

        LogicNode yCond = forY;
        boolean yNeg = yNegated;
        while (yCond instanceof LogicNegationNode) {
            yCond = ((LogicNegationNode) yCond).getValue();
            yNeg = !yNeg;
        }

        if (xCond != forX || yCond != forY) {
            return new ShortCircuitOrNode(xCond, xNeg, yCond, yNeg, shortCircuitProbability);
        } else {
            return null;
        }
    }

    @Override
    public LogicNode canonical(CanonicalizerTool tool, LogicNode forX, LogicNode forY) {
        LogicNode canonical = canonicalize(this, tool, shortCircuitProbability, forX, xNegated, forY, yNegated);
        if (canonical != this) {
            return canonical;
        }
        return this;
    }

    private static LogicNode canonicalize(ShortCircuitOrNode self, CanonicalizerTool tool, ProfileData.BranchProbabilityData shortCircuitProbability, LogicNode forX, boolean xNegated, LogicNode forY,
                    boolean yNegated) {
        ShortCircuitOrNode ret = canonicalizeNegation(forX, xNegated, forY, yNegated, shortCircuitProbability);
        if (ret != self && ret != null) {
            return ret;
        }
        NodeView view = tool == null ? NodeView.DEFAULT : NodeView.from(tool);
        if (forX == forY) {
            // @formatter:off
            //  a ||  a = a
            //  a || !a = true
            // !a ||  a = true
            // !a || !a = !a
            // @formatter:on
            if (xNegated) {
                if (yNegated) {
                    // !a || !a = !a
                    return LogicNegationNode.create(forX);
                } else {
                    // !a || a = true
                    return LogicConstantNode.tautology();
                }
            } else {
                if (yNegated) {
                    // a || !a = true
                    return LogicConstantNode.tautology();
                } else {
                    // a || a = a
                    return forX;
                }
            }
        }
        if (forX instanceof LogicConstantNode) {
            if (((LogicConstantNode) forX).getValue() ^ xNegated) {
                return LogicConstantNode.tautology();
            } else {
                if (yNegated) {
                    return LogicNegationNode.create(forY);
                } else {
                    return forY;
                }
            }
        }
        if (forY instanceof LogicConstantNode) {
            if (((LogicConstantNode) forY).getValue() ^ yNegated) {
                return LogicConstantNode.tautology();
            } else {
                if (xNegated) {
                    return LogicNegationNode.create(forX);
                } else {
                    return forX;
                }
            }
        }

        if (forX instanceof ShortCircuitOrNode) {
            ShortCircuitOrNode inner = (ShortCircuitOrNode) forX;
            if (forY == inner.getX()) {
                return optimizeShortCircuit(inner, xNegated, yNegated, true);
            } else if (forY == inner.getY()) {
                return optimizeShortCircuit(inner, xNegated, yNegated, false);
            }
        }

        if (forY instanceof ShortCircuitOrNode) {
            ShortCircuitOrNode inner = (ShortCircuitOrNode) forY;
            if (inner.getX() == forX) {
                return optimizeShortCircuit(inner, yNegated, xNegated, true);
            } else if (inner.getY() == forX) {
                return optimizeShortCircuit(inner, yNegated, xNegated, false);
            }
        }

        // !X => Y constant
        TriState impliedForY = forX.implies(!xNegated, forY);
        if (impliedForY.isKnown()) {
            boolean yResult = impliedForY.toBoolean() ^ yNegated;
            return yResult ? LogicConstantNode.tautology() : (xNegated ? LogicNegationNode.create(forX) : forX);
        }

        // if X >= 0:
        // u < 0 || X < u ==>> X |<| u
        if (!xNegated && !yNegated) {
            LogicNode sym = simplifyComparison(forX, forY);
            if (sym != null) {
                return sym;
            }
        }

        // if X >= 0:
        // X |<| u || X < u ==>> X |<| u
        if (forX instanceof IntegerBelowNode && forY instanceof IntegerLessThanNode && !xNegated && !yNegated) {
            IntegerBelowNode xNode = (IntegerBelowNode) forX;
            IntegerLessThanNode yNode = (IntegerLessThanNode) forY;
            ValueNode xxNode = xNode.getX(); // X >= 0
            ValueNode yxNode = yNode.getX(); // X >= 0
            if (xxNode == yxNode && ((IntegerStamp) xxNode.stamp(view)).isPositive()) {
                ValueNode xyNode = xNode.getY(); // u
                ValueNode yyNode = yNode.getY(); // u
                if (xyNode == yyNode) {
                    return forX;
                }
            }
        }

        // if X >= 0:
        // u < 0 || (X < u || tail) ==>> X |<| u || tail
        if (forY instanceof ShortCircuitOrNode && !xNegated && !yNegated) {
            ShortCircuitOrNode yNode = (ShortCircuitOrNode) forY;
            if (!yNode.isXNegated()) {
                LogicNode sym = simplifyComparison(forX, yNode.getX());
                if (sym != null) {
                    ProfileData.BranchProbabilityData combinedProfile = ProfileData.BranchProbabilityData.combineShortCircuitOr(shortCircuitProbability, yNode.getShortCircuitProbability());
                    return new ShortCircuitOrNode(sym, xNegated, yNode.getY(), yNode.isYNegated(), combinedProfile);
                }
            }
        }

        if (tool != null && forX instanceof CompareNode && forY instanceof CompareNode) {
            CompareNode xCompare = (CompareNode) forX;
            CompareNode yCompare = (CompareNode) forY;
            if (xCompare.getX() == yCompare.getX() || xCompare.getX() == yCompare.getY()) {
                Stamp succeedingStampX = xCompare.getSucceedingStampForX(!xNegated, xCompare.getX().stamp(view), xCompare.getY().stamp(view));
                // Try to canonicalize the other comparison using the knowledge gained from assuming
                // the first part of the short circuit or is false (which is the only relevant case
                // for the second part of the short circuit or).
                if (succeedingStampX != null && !succeedingStampX.isUnrestricted()) {
                    CanonicalizerTool proxyTool = new ProxyCanonicalizerTool(succeedingStampX, xCompare.getX(), tool, view);
                    ValueNode result = yCompare.canonical(proxyTool);
                    if (result != yCompare) {
                        return ShortCircuitOrNode.create(forX, xNegated, (LogicNode) result, yNegated, shortCircuitProbability);
                    }
                }
            }
        }
        // can be null
        return self;
    }

    private static class ProxyCanonicalizerTool extends CoreProvidersDelegate implements CanonicalizerTool, NodeView {

        private final Stamp stamp;
        private final ValueNode node;
        private final CanonicalizerTool tool;
        private final NodeView view;

        ProxyCanonicalizerTool(Stamp stamp, ValueNode node, CanonicalizerTool tool, NodeView view) {
            super(tool);
            this.stamp = stamp;
            this.node = node;
            this.tool = tool;
            this.view = view;
        }

        @Override
        public Stamp stamp(ValueNode n) {
            if (n == node) {
                return stamp;
            }
            return view.stamp(n);
        }

        @Override
        public Assumptions getAssumptions() {
            return tool.getAssumptions();
        }

        @Override
        public boolean canonicalizeReads() {
            return tool.canonicalizeReads();
        }

        @Override
        public boolean allUsagesAvailable() {
            return tool.allUsagesAvailable();
        }

        @Override
        public Integer smallestCompareWidth() {
            return tool.smallestCompareWidth();
        }

        @Override
        public OptionValues getOptions() {
            return tool.getOptions();
        }

        @Override
        public boolean divisionOverflowIsJVMSCompliant() {
            return tool.divisionOverflowIsJVMSCompliant();
        }
    }

    private static LogicNode simplifyComparison(LogicNode forX, LogicNode forY) {
        LogicNode sym = simplifyComparisonOrdered(forX, forY);
        if (sym == null) {
            return simplifyComparisonOrdered(forY, forX);
        }
        return sym;
    }

    private static LogicNode simplifyComparisonOrdered(LogicNode forX, LogicNode forY) {
        // if X is >= 0:
        // u < 0 || X < u ==>> X |<| u
        if (forX instanceof IntegerLessThanNode && forY instanceof IntegerLessThanNode) {
            IntegerLessThanNode xNode = (IntegerLessThanNode) forX;
            IntegerLessThanNode yNode = (IntegerLessThanNode) forY;
            ValueNode xyNode = xNode.getY(); // 0
            if (xyNode.isConstant() && IntegerStamp.OPS.getAdd().isNeutral(xyNode.asConstant())) {
                ValueNode yxNode = yNode.getX(); // X >= 0
                IntegerStamp stamp = (IntegerStamp) yxNode.stamp(NodeView.DEFAULT);
                if (stamp.isPositive()) {
                    if (xNode.getX() == yNode.getY()) {
                        ValueNode u = xNode.getX();
                        return IntegerBelowNode.create(yxNode, u, NodeView.DEFAULT);
                    }
                }
            }
        }

        return null;
    }

    private static LogicNode optimizeShortCircuit(ShortCircuitOrNode inner, boolean innerNegated, boolean matchNegated, boolean matchIsInnerX) {
        boolean innerMatchNegated;
        if (matchIsInnerX) {
            innerMatchNegated = inner.isXNegated();
        } else {
            innerMatchNegated = inner.isYNegated();
        }
        if (!innerNegated) {
            // The four digit results of the expression used in the 16 subsequent formula comments
            // correspond to results when using the following truth table for inputs a and b
            // and testing all 4 possible input combinations:
            // _ 1234
            // a 1100
            // b 1010
            if (innerMatchNegated == matchNegated) {
                // ( (!a ||!b) ||!a) => 0111 (!a ||!b)
                // ( (!a || b) ||!a) => 1011 (!a || b)
                // ( ( a ||!b) || a) => 1101 ( a ||!b)
                // ( ( a || b) || a) => 1110 ( a || b)
                // Only the inner or is relevant, the outer or never adds information.
                return inner;
            } else {
                // ( ( a || b) ||!a) => 1111 (true)
                // ( (!a ||!b) || a) => 1111 (true)
                // ( (!a || b) || a) => 1111 (true)
                // ( ( a ||!b) ||!a) => 1111 (true)
                // The result of the expression is always true.
                return LogicConstantNode.tautology();
            }
        } else {
            if (innerMatchNegated == matchNegated) {
                // (!(!a ||!b) ||!a) => 1011 (!a || b)
                // (!(!a || b) ||!a) => 0111 (!a ||!b)
                // (!( a ||!b) || a) => 1110 ( a || b)
                // (!( a || b) || a) => 1101 ( a ||!b)
                boolean newInnerXNegated = inner.isXNegated();
                boolean newInnerYNegated = inner.isYNegated();
                ProfileData.BranchProbabilityData newProbability = inner.getShortCircuitProbability();
                if (matchIsInnerX) {
                    newInnerYNegated = !newInnerYNegated;
                } else {
                    newInnerXNegated = !newInnerXNegated;
                    newProbability = newProbability.negated();
                }
                // The expression can be transformed into a single or.
                return new ShortCircuitOrNode(inner.getX(), newInnerXNegated, inner.getY(), newInnerYNegated, newProbability);
            } else {
                // (!(!a ||!b) || a) => 1100 (a)
                // (!(!a || b) || a) => 1100 (a)
                // (!( a ||!b) ||!a) => 0011 (!a)
                // (!( a || b) ||!a) => 0011 (!a)
                LogicNode result = inner.getY();
                if (matchIsInnerX) {
                    result = inner.getX();
                }
                // Only the second part of the outer or is relevant.
                if (matchNegated) {
                    return LogicNegationNode.create(result);
                } else {
                    return result;
                }
            }
        }
    }
}
