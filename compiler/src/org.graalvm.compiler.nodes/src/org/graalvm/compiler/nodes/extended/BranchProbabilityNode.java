/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.iterators.NodePredicates;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;
import org.graalvm.compiler.nodes.ProfileData.ProfileSource;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.ShortCircuitOrNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.Simplifiable;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.JavaKind;

/**
 * Instances of this node class will look for a preceding if node and put the given probability into
 * the if node's taken probability. Then the branch probability node will be removed. This node is
 * intended primarily for snippets, so that they can define their fast and slow paths.
 */
@NodeInfo(cycles = CYCLES_0, cyclesRationale = "Artificial Node", size = SIZE_0)
public final class BranchProbabilityNode extends FloatingNode implements Simplifiable, Lowerable, Canonicalizable {

    public static final NodeClass<BranchProbabilityNode> TYPE = NodeClass.create(BranchProbabilityNode.class);
    public static final double LIKELY_PROBABILITY = 0.6;
    public static final double NOT_LIKELY_PROBABILITY = 1 - LIKELY_PROBABILITY;
    public static final BranchProbabilityData LIKELY_PROFILE = BranchProbabilityData.injected(LIKELY_PROBABILITY);
    public static final BranchProbabilityData NOT_LIKELY_PROFILE = BranchProbabilityData.injected(NOT_LIKELY_PROBABILITY);

    public static final double FREQUENT_PROBABILITY = 0.9;
    public static final double NOT_FREQUENT_PROBABILITY = 1 - FREQUENT_PROBABILITY;
    public static final BranchProbabilityData FREQUENT_PROFILE = BranchProbabilityData.injected(FREQUENT_PROBABILITY);
    public static final BranchProbabilityData NOT_FREQUENT_PROFILE = BranchProbabilityData.injected(NOT_FREQUENT_PROBABILITY);

    public static final double FAST_PATH_PROBABILITY = 0.99;
    public static final double SLOW_PATH_PROBABILITY = 1 - FAST_PATH_PROBABILITY;
    public static final BranchProbabilityData FAST_PATH_PROFILE = BranchProbabilityData.injected(FAST_PATH_PROBABILITY);
    public static final BranchProbabilityData SLOW_PATH_PROFILE = BranchProbabilityData.injected(SLOW_PATH_PROBABILITY);

    public static final double VERY_FAST_PATH_PROBABILITY = 0.999;
    public static final double VERY_SLOW_PATH_PROBABILITY = 1 - VERY_FAST_PATH_PROBABILITY;
    public static final BranchProbabilityData VERY_FAST_PATH_PROFILE = BranchProbabilityData.injected(VERY_FAST_PATH_PROBABILITY);
    public static final BranchProbabilityData VERY_SLOW_PATH_PROFILE = BranchProbabilityData.injected(VERY_SLOW_PATH_PROBABILITY);

    /*
     * This probability may seem excessive, but it makes a difference in long running loops. Let's
     * say a loop is executed 100k times and has a few null checks with probability 0.999. As these
     * probabilities multiply for every loop iteration, the overall loop frequency will be
     * calculated as approximately 30 while it should be 100k.
     */
    public static final double EXTREMELY_FAST_PATH_PROBABILITY = 0.999999;
    public static final double EXTREMELY_SLOW_PATH_PROBABILITY = 1 - EXTREMELY_FAST_PATH_PROBABILITY;
    public static final BranchProbabilityData EXTREMELY_FAST_PATH_PROFILE = BranchProbabilityData.injected(EXTREMELY_FAST_PATH_PROBABILITY);
    public static final BranchProbabilityData EXTREMELY_SLOW_PATH_PROFILE = BranchProbabilityData.injected(EXTREMELY_SLOW_PATH_PROBABILITY);

    public static final double DEOPT_PROBABILITY = 0.0;
    public static final BranchProbabilityData DEOPT_PROFILE = BranchProbabilityData.injected(DEOPT_PROBABILITY);

    public static final double ALWAYS_TAKEN_PROBABILITY = 1.0;
    public static final double NEVER_TAKEN_PROBABILITY = 1 - ALWAYS_TAKEN_PROBABILITY;
    public static final BranchProbabilityData ALWAYS_TAKEN_PROFILE = BranchProbabilityData.injected(ALWAYS_TAKEN_PROBABILITY);
    public static final BranchProbabilityData NEVER_TAKEN_PROFILE = BranchProbabilityData.injected(NEVER_TAKEN_PROBABILITY);

    @Input ValueNode probability;
    @Input ValueNode condition;

    public BranchProbabilityNode(ValueNode probability, ValueNode condition) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean));
        this.probability = probability;
        this.condition = condition;

        GraalError.guarantee(!(condition instanceof ShortCircuitOrNode),
                        "Branch probabilities must be injected on simple conditions, not short-circuiting && or ||: %s", condition);
    }

    public BranchProbabilityNode(ValueNode condition) {
        this(ConstantNode.forDouble(0.5D), condition);
    }

    public ValueNode getProbability() {
        return probability;
    }

    public ValueNode getCondition() {
        return condition;
    }

    public void setProbability(ValueNode probability) {
        updateUsages(this.probability, probability);
        this.probability = probability;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (condition.isConstant()) {
            // fold constant conditions early during PE
            return condition;
        }
        return this;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (!hasUsages()) {
            return;
        }
        if (probability.isConstant()) {
            double probabilityValue = probability.asJavaConstant().asDouble();
            if (probabilityValue < 0.0) {
                throw new GraalError("A negative probability of " + probabilityValue + " is not allowed!");
            } else if (probabilityValue > 1.0) {
                throw new GraalError("A probability of more than 1.0 (" + probabilityValue + ") is not allowed!");
            } else if (Double.isNaN(probabilityValue)) {
                /*
                 * We allow NaN if the node is in unreachable code that will eventually fall away,
                 * or else an error will be thrown during lowering since we keep the node around.
                 */
                return;
            }
            boolean usageFound = false;
            for (IntegerEqualsNode node : this.usages().filter(IntegerEqualsNode.class)) {
                assert node.condition() == CanonicalCondition.EQ;
                ValueNode other = node.getX();
                if (node.getX() == this) {
                    other = node.getY();
                }
                if (other.isConstant()) {
                    double probabilityToSet = probabilityValue;
                    if (other.asJavaConstant().asInt() == 0) {
                        probabilityToSet = 1.0 - probabilityToSet;
                    }
                    for (IfNode ifNodeUsages : node.usages().filter(IfNode.class)) {
                        usageFound = true;
                        ifNodeUsages.setTrueSuccessorProbability(BranchProbabilityData.injected(probabilityToSet));
                    }
                    if (!usageFound) {
                        usageFound = node.usages().filter(NodePredicates.isA(FixedGuardNode.class).or(ConditionalNode.class)).isNotEmpty();
                    }
                }
            }
            if (!usageFound) {
                usageFound = hasValidPhiUsage();
            }
            if (usageFound) {
                ValueNode currentCondition = condition;
                IntegerStamp currentStamp = (IntegerStamp) currentCondition.stamp(NodeView.DEFAULT);
                if (currentStamp.lowerBound() < 0 || 1 < currentStamp.upperBound()) {
                    ValueNode narrow = graph().addOrUnique(NarrowNode.create(currentCondition, 1, NodeView.DEFAULT));
                    currentCondition = graph().addOrUnique(ZeroExtendNode.create(narrow, 32, NodeView.DEFAULT));
                }
                replaceAndDelete(currentCondition);
                if (tool != null) {
                    // @formatter:off
                    // Try to eliminate useless Conditional == Constant eagerly, e.g.:
                    //
                    // <condition>
                    //    |  C(1) C(0)
                    //    |   |   /
                    // Conditional
                    //    |
                    // BranchProbability
                    //    |     C(0|1)
                    //    |       /
                    // IntegerEquals
                    //    |
                    //   If
                    //
                    // Should be directly simplified to:
                    //
                    // <condition>
                    //    |
                    //   If
                    //
                    // This allows the If to be simplified immediately after injecting the profile.
                    // @formatter:on
                    if (currentCondition instanceof ConditionalNode &&
                                    ((ConditionalNode) currentCondition).trueValue().isConstant() && ((ConditionalNode) currentCondition).falseValue().isConstant()) {
                        for (IntegerEqualsNode eq : currentCondition.usages().filter(IntegerEqualsNode.class).snapshot()) {
                            if (eq.getY().isConstant() || eq.getX().isConstant()) {
                                ValueNode canonical = eq.canonical(tool);
                                if (canonical != eq && canonical != null) {
                                    tool.addToWorkList(eq.usages());
                                    eq.replaceAtUsages(graph().addOrUnique(canonical));
                                    GraphUtil.killWithUnusedFloatingInputs(eq);
                                }
                            }
                        }
                    }
                    if (currentCondition.hasUsages()) {
                        tool.addToWorkList(currentCondition.usages());
                    }
                }
            } else {
                if (!isSubstitutionGraph()) {
                    throw new GraalError("Wrong usage of branch probability injection " + this);
                }
            }
        }
    }

    private boolean isSubstitutionGraph() {
        return hasExactlyOneUsage() && usages().first() instanceof ReturnNode;
    }

    /**
     * Normally a branch probability should be consumed directly as a condition, but in some cases
     * it can be used as a value itself. For example:
     *
     * <pre>
     * boolean helper() {
     *     if (probability(a, ...) || probability(b, ...) || probability(c, condition)) {
     *         return true;
     *     } else {
     *         return false;
     *     }
     * }
     *
     * ...
     * if (probability(d, helper()) {
     *     ...
     * }
     * </pre>
     *
     * After inlining the helper, {@code probability(c, condition)} can be represented as a branch
     * probability node that feeds into a phi which is then used in an {@code if} condition. This is
     * benign if that {@code if} has an injected branch probability itself.
     */
    private boolean hasValidPhiUsage() {
        for (Node usage : this.usages()) {
            if (usage instanceof ValuePhiNode && !((ValuePhiNode) usage).isLoopPhi()) {
                Node phi = usage;
                // We want exactly one non-state usage, and it must be a branch probability node.
                Node uniquePhiUsage = null;
                for (Node phiUsage : phi.usages()) {
                    if (phiUsage instanceof FrameState) {
                        continue;
                    } else if (uniquePhiUsage == null) {
                        uniquePhiUsage = phiUsage;
                    } else if (phiUsage != uniquePhiUsage) {
                        uniquePhiUsage = null;
                        break;
                    }
                }
                if (uniquePhiUsage instanceof BranchProbabilityNode) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * This intrinsic should only be used for the condition of an if statement. The parameter
     * condition should also only denote a simple condition and not a combined condition involving
     * &amp;&amp; or || operators. It injects the probability of the condition into the if
     * statement.
     *
     * @param probability the probability that the given condition is true as a double value between
     *            0.0 and 1.0.
     * @param condition the simple condition without any &amp;&amp; or || operators
     * @return the condition
     */
    @NodeIntrinsic
    public static native boolean probability(double probability, boolean condition);

    /**
     * This intrinsic can be used to inject a truly unknown probability (0.5 for both true and false
     * successor) for an {@link IfNode}. While the probability will be 0.5 for both successors this
     * method ensures the {@link ProfileSource} is {@link ProfileSource#isTrusted(ProfileSource)},
     * i.e., the compiler can trust it.
     *
     * This intrinsic should be used with great caution only for cases, e.g. inside snippets, for
     * which absolutely now probability guess can be made.
     */
    @NodeIntrinsic
    public static native boolean unknownProbability(boolean condition);

    @Override
    public void lower(LoweringTool tool) {
        throw new GraalError("Branch probability could not be injected, because the probability value did not reduce to a constant value.");
    }
}
