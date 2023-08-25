/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.GraphState.StageFlag;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;
import org.graalvm.compiler.nodes.ProfileData.ProfileSource;
import org.graalvm.compiler.nodes.VirtualState.NodePositionClosure;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FloatNormalizeCompareNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.IntegerNormalizeCompareNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.cfg.HIRBlock;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchored;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.memory.MemoryAnchorNode;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.Simplifiable;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.compiler.nodes.spi.SwitchFoldable;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

/**
 * The {@code IfNode} represents a branch that can go one of two directions depending on the outcome
 * of a comparison.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_2, sizeRationale = "2 jmps")
public final class IfNode extends ControlSplitNode implements Simplifiable, LIRLowerable, IterableNodeType, SwitchFoldable {
    public static final NodeClass<IfNode> TYPE = NodeClass.create(IfNode.class);

    private static final CounterKey CORRECTED_PROBABILITIES = DebugContext.counter("CorrectedProbabilities");

    /*
     * Any change to successor fields (reordering, renaming, adding or removing) would need an
     * according update to SimplifyingGraphDecoder#earlyCanonicalization.
     */
    @Successor AbstractBeginNode trueSuccessor;
    @Successor AbstractBeginNode falseSuccessor;
    @Input(InputType.Condition) LogicNode condition;
    protected BranchProbabilityData profileData;

    public LogicNode condition() {
        return condition;
    }

    public void setCondition(LogicNode x) {
        updateUsages(condition, x);
        condition = x;
    }

    public IfNode(LogicNode condition, FixedNode trueSuccessor, FixedNode falseSuccessor, BranchProbabilityData profileData) {
        this(condition, BeginNode.begin(trueSuccessor), BeginNode.begin(falseSuccessor), profileData);
    }

    public IfNode(LogicNode condition, AbstractBeginNode trueSuccessor, AbstractBeginNode falseSuccessor, BranchProbabilityData profileData) {
        super(TYPE, StampFactory.forVoid());
        this.condition = condition;
        this.falseSuccessor = falseSuccessor;
        this.trueSuccessor = trueSuccessor;
        this.profileData = profileData;
    }

    /**
     * Gets the true successor.
     *
     * @return the true successor
     */
    public AbstractBeginNode trueSuccessor() {
        return trueSuccessor;
    }

    /**
     * Gets the false successor.
     *
     * @return the false successor
     */
    public AbstractBeginNode falseSuccessor() {
        return falseSuccessor;
    }

    public double getTrueSuccessorProbability() {
        return profileData.getDesignatedSuccessorProbability();
    }

    public void setTrueSuccessor(AbstractBeginNode node) {
        updatePredecessor(trueSuccessor, node);
        trueSuccessor = node;
    }

    public void setFalseSuccessor(AbstractBeginNode node) {
        updatePredecessor(falseSuccessor, node);
        falseSuccessor = node;
    }

    /**
     * Gets the node corresponding to the specified outcome of the branch.
     *
     * @param istrue {@code true} if the true successor is requested, {@code false} otherwise
     * @return the corresponding successor
     */
    public AbstractBeginNode successor(boolean istrue) {
        return istrue ? trueSuccessor : falseSuccessor;
    }

    public void setTrueSuccessorProbability(BranchProbabilityData profileData) {
        double prob = profileData.getDesignatedSuccessorProbability();
        assert prob >= -0.000000001 && prob <= 1.000000001 : "Probability out of bounds: " + prob;
        double trueSuccessorProbability = Math.min(1.0, Math.max(0.0, prob));
        this.profileData = profileData.copy(trueSuccessorProbability);
    }

    protected BranchProbabilityData trueSuccessorProfile() {
        return profileData;
    }

    @Override
    public double probability(AbstractBeginNode successor) {
        return successor == trueSuccessor ? getTrueSuccessorProbability() : 1 - getTrueSuccessorProbability();
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.emitIf(this);
    }

    @Override
    public boolean verify() {
        assertTrue(condition() != null, "missing condition");
        assertTrue(trueSuccessor() != null, "missing trueSuccessor");
        assertTrue(falseSuccessor() != null, "missing falseSuccessor");
        return super.verify();
    }

    public void eliminateNegation() {
        AbstractBeginNode oldTrueSuccessor = trueSuccessor;
        AbstractBeginNode oldFalseSuccessor = falseSuccessor;
        trueSuccessor = oldFalseSuccessor;
        falseSuccessor = oldTrueSuccessor;
        double trueSuccessorProbability = 1 - getTrueSuccessorProbability();
        profileData = profileData.copy(trueSuccessorProbability);
        setCondition(((LogicNegationNode) condition).getValue());
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (trueSuccessor().next() instanceof DeoptimizeNode) {
            if (getTrueSuccessorProbability() != 0) {
                CORRECTED_PROBABILITIES.increment(getDebug());
                profileData = BranchProbabilityNode.NEVER_TAKEN_PROFILE;
            }
        } else if (falseSuccessor().next() instanceof DeoptimizeNode) {
            if (getTrueSuccessorProbability() != 1) {
                CORRECTED_PROBABILITIES.increment(getDebug());
                profileData = BranchProbabilityNode.ALWAYS_TAKEN_PROFILE;
            }
        }

        if (condition() instanceof LogicNegationNode) {
            eliminateNegation();
        }
        if (condition() instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) condition();
            if (c.getValue()) {
                tool.deleteBranch(falseSuccessor());
                tool.addToWorkList(trueSuccessor());
                graph().removeSplit(this, trueSuccessor());
            } else {
                tool.deleteBranch(trueSuccessor());
                tool.addToWorkList(falseSuccessor());
                graph().removeSplit(this, falseSuccessor());
            }
            return;
        }
        if (tool.allUsagesAvailable() && trueSuccessor().hasNoUsages() && falseSuccessor().hasNoUsages()) {

            pushNodesThroughIf(tool);

            if (checkForUnsignedCompare(tool) || removeOrMaterializeIf(tool)) {
                return;
            }
        }

        if (removeIntermediateMaterialization(tool)) {
            return;
        }

        if (conditionalNodeOptimization(tool)) {
            return;
        }

        if (switchTransformationOptimization(tool)) {
            return;
        }

        if (tool.finalCanonicalization()) {
            if (falseSuccessor().hasNoUsages() && (!(falseSuccessor() instanceof LoopExitNode)) && falseSuccessor().next() instanceof IfNode &&
                            !(((IfNode) falseSuccessor().next()).falseSuccessor() instanceof LoopExitNode)) {
                AbstractBeginNode intermediateBegin = falseSuccessor();
                IfNode nextIf = (IfNode) intermediateBegin.next();
                double probabilityB = (1.0 - this.getTrueSuccessorProbability()) * nextIf.getTrueSuccessorProbability();
                if (this.getTrueSuccessorProbability() < probabilityB) {
                    // Reordering of those two if statements is beneficial from the point of view of
                    // their probabilities.
                    if (prepareForSwap(tool, condition(), nextIf.condition())) {
                        // @formatter:off
                        // Reordering is allowed from (if1 => begin => if2) to (if2 => begin => if1).
                        // @formatter:on
                        assert intermediateBegin.next() == nextIf;
                        AbstractBeginNode bothFalseBegin = nextIf.falseSuccessor();
                        nextIf.setFalseSuccessor(null);
                        intermediateBegin.setNext(null);
                        this.setFalseSuccessor(null);

                        this.replaceAtPredecessor(nextIf);
                        nextIf.setFalseSuccessor(intermediateBegin);
                        intermediateBegin.setNext(this);
                        this.setFalseSuccessor(bothFalseBegin);

                        NodeSourcePosition intermediateBeginPosition = intermediateBegin.getNodeSourcePosition();
                        intermediateBegin.setNodeSourcePosition(bothFalseBegin.getNodeSourcePosition());
                        bothFalseBegin.setNodeSourcePosition(intermediateBeginPosition);

                        ProfileSource combinedSource = profileData.getProfileSource().combine(nextIf.profileData.getProfileSource());
                        nextIf.setTrueSuccessorProbability(BranchProbabilityData.create(probabilityB, combinedSource));
                        if (probabilityB == 1.0) {
                            this.setTrueSuccessorProbability(BranchProbabilityData.create(0.0, combinedSource));
                        } else {
                            double newProbability = this.getTrueSuccessorProbability() / (1.0 - probabilityB);
                            this.setTrueSuccessorProbability(BranchProbabilityData.create(Math.min(1.0, newProbability), combinedSource));
                        }
                        return;
                    }
                }
            }
        }

        if (tryEliminateBoxedReferenceEquals(tool)) {
            return;
        }

        if (optimizeCompoundConditional(this)) {
            return;
        }

        if (this.graph().isAfterStage(StageFlag.HIGH_TIER_LOWERING)) {
            if (splitIfAtPhi(this, tool)) {
                return;
            }
        }

    }

    public static boolean isWorthPerformingSplit(LogicNode newCondition, LogicNode originalCondition) {
        if (newCondition == originalCondition) {
            // No canonicalization occurred.
            return false;
        } else if (newCondition.isAlive()) {
            // The condition folds on one path and is replaced by an existing other condition.
            return true;
        } else if (newCondition instanceof LogicConstantNode) {
            // The condition folds to constant true or constant false.
            return true;
        } else if (newCondition instanceof LogicNegationNode) {
            LogicNegationNode logicNegationNode = (LogicNegationNode) newCondition;
            return isWorthPerformingSplit(logicNegationNode.getValue(), originalCondition);
        } else if (originalCondition instanceof InstanceOfNode && newCondition instanceof IsNullNode) {
            // New condition is substantially simpler.
            return true;
        }

        // Not sufficient progress to justify the duplication of code.
        return false;
    }

    /**
     * Take an if that is immediately dominated by a merge with a single phi and split off any paths
     * where the test would be statically decidable creating a new merge below the appropriate side
     * of the IfNode. Any undecidable tests will continue to use the original IfNode.
     *
     * @param tool
     */
    @SuppressWarnings("try")
    private boolean splitIfAtPhi(IfNode ifNode, SimplifierTool tool) {
        if (!(ifNode.predecessor() instanceof MergeNode)) {
            return false;
        }
        MergeNode merge = (MergeNode) ifNode.predecessor();
        if (merge.forwardEndCount() == 1) {
            // Don't bother.
            return false;
        }
        if (merge.getUsageCount() != 1 || merge.phis().count() != 1) {
            // Don't trigger with multiple phis. Would require more rewiring.
            // Most of the time the additional phis are memory phis that are removed after
            // fixed read phase.
            return false;
        }
        if (ifNode.graph().getGuardsStage().areFrameStatesAtSideEffects() && merge.stateAfter() == null) {
            return false;
        }

        PhiNode generalPhi = merge.phis().first();
        if (!(generalPhi instanceof ValuePhiNode)) {
            return false;
        }

        if (ifNode.trueSuccessor().isUsedAsGuardInput() || ifNode.falseSuccessor().isUsedAsGuardInput()) {
            return false;
        }

        ValuePhiNode phi = (ValuePhiNode) generalPhi;

        EconomicMap<Node, NodeColor> coloredNodes = EconomicMap.create(Equivalence.IDENTITY, 8);

        /*
         * Check that the condition uses the phi and that there is only one user of the condition
         * expression.
         */
        if (!conditionUses(ifNode.condition(), phi, coloredNodes)) {
            return false;
        }

        if (merge.stateAfter() != null && !GraphUtil.mayRemoveSplit(ifNode)) {
            return false;
        }

        LogicNode[] results = new LogicNode[merge.forwardEndCount()];
        boolean success = false;
        for (int i = 0; i < results.length; ++i) {
            ValueNode value = phi.valueAt(i);
            LogicNode curResult = computeCondition(tool, ifNode.condition(), phi, value);
            if (isWorthPerformingSplit(curResult, ifNode.condition())) {
                for (Node n : curResult.inputs()) {
                    if (n instanceof ConstantNode || n instanceof ParameterNode || n instanceof FixedNode) {
                        // Constant inputs or parameters or fixed nodes are OK.
                    } else if (n == value) {
                        // References to the value itself are also OK.
                    } else {
                        // Input may cause scheduling issues.
                        curResult = ifNode.condition();
                        break;
                    }
                }
                success = true;
                results[i] = curResult;
            } else {
                results[i] = ifNode.condition();
            }
        }

        if (!success) {
            return false;
        }

        for (Node usage : phi.usages()) {
            if (usage == merge.stateAfter()) {
                // This usage can be ignored, because it is directly in the state after.
            } else {
                NodeColor color = colorUsage(coloredNodes, usage, merge, ifNode.trueSuccessor(), ifNode.falseSuccessor());
                if (color == NodeColor.MIXED) {
                    return false;
                }
            }
        }

        /*
         * We could additionally filter for the case that at least some of the Phi inputs or one of
         * the condition inputs are constants but there are cases where a non-constant is
         * simplifiable, usually where the stamp allows the question to be answered.
         */

        /* Each successor of the if gets a new merge if needed. */
        MergeNode trueMerge = null;
        MergeNode falseMerge = null;
        int i = 0;
        for (EndNode end : merge.forwardEnds().snapshot()) {
            ValueNode value = phi.valueAt(end);
            LogicNode result = results[i++];
            if (result instanceof LogicConstantNode) {
                if (((LogicConstantNode) result).getValue()) {
                    if (trueMerge == null) {
                        trueMerge = insertMerge(ifNode.trueSuccessor(), phi, merge.stateAfter(), tool);
                        replaceNodesInBranch(coloredNodes, NodeColor.TRUE_BRANCH, phi, trueMerge.phis().first());
                    }
                    trueMerge.phis().first().addInput(value);
                    trueMerge.addForwardEnd(end);
                } else {
                    if (falseMerge == null) {
                        falseMerge = insertMerge(ifNode.falseSuccessor(), phi, merge.stateAfter(), tool);
                        replaceNodesInBranch(coloredNodes, NodeColor.FALSE_BRANCH, phi, falseMerge.phis().first());
                    }
                    falseMerge.phis().first().addInput(value);
                    falseMerge.addForwardEnd(end);
                }
                merge.removeEnd(end);
            } else if (result != ifNode.condition()) {
                // Build a new IfNode using the new condition
                BeginNode trueBegin = ifNode.graph().add(new BeginNode());
                trueBegin.setNodeSourcePosition(ifNode.trueSuccessor().getNodeSourcePosition());
                BeginNode falseBegin = ifNode.graph().add(new BeginNode());
                falseBegin.setNodeSourcePosition(ifNode.falseSuccessor().getNodeSourcePosition());

                if (result.graph() == null) {
                    result = ifNode.graph().addOrUniqueWithInputs(result);
                    result.setNodeSourcePosition(ifNode.condition().getNodeSourcePosition());
                }
                IfNode newIfNode = ifNode.graph().add(new IfNode(result, trueBegin, falseBegin, ifNode.getProfileData()));
                newIfNode.setNodeSourcePosition(ifNode.getNodeSourcePosition());

                if (trueMerge == null) {
                    trueMerge = insertMerge(ifNode.trueSuccessor(), phi, merge.stateAfter(), tool);
                    replaceNodesInBranch(coloredNodes, NodeColor.TRUE_BRANCH, phi, trueMerge.phis().first());
                }
                trueMerge.phis().first().addInput(value);
                trueBegin.setNext(ifNode.graph().add(new EndNode()));
                trueMerge.addForwardEnd((EndNode) trueBegin.next());

                if (falseMerge == null) {
                    falseMerge = insertMerge(ifNode.falseSuccessor(), phi, merge.stateAfter(), tool);
                    replaceNodesInBranch(coloredNodes, NodeColor.FALSE_BRANCH, phi, falseMerge.phis().first());
                }
                falseMerge.phis().first().addInput(value);
                falseBegin.setNext(ifNode.graph().add(new EndNode()));
                falseMerge.addForwardEnd((EndNode) falseBegin.next());

                merge.removeEnd(end);
                ((FixedWithNextNode) end.predecessor()).setNext(newIfNode);
                end.safeDelete();
            }
        }

        cleanupMerge(merge);
        cleanupMerge(trueMerge);
        cleanupMerge(falseMerge);

        return true;
    }

    private static void replaceNodesInBranch(EconomicMap<Node, NodeColor> coloredNodes, NodeColor branch, ValuePhiNode phi, ValueNode newValue) {
        for (Node n : phi.usages().snapshot()) {
            if (coloredNodes.get(n) == branch) {
                n.replaceAllInputs(phi, newValue);
            } else if (coloredNodes.get(n) == NodeColor.PHI_MIXED) {
                assert n instanceof PhiNode;
                PhiNode phiNode = (PhiNode) n;
                AbstractMergeNode merge = phiNode.merge();
                for (int i = 0; i < merge.forwardEndCount(); ++i) {
                    if (phiNode.valueAt(i) == phi && coloredNodes.get(merge.forwardEndAt(i)) == branch) {
                        phiNode.setValueAt(i, newValue);
                    }
                }
            }
        }
    }

    private NodeColor colorUsage(EconomicMap<Node, NodeColor> coloredNodes, Node node, MergeNode merge, AbstractBeginNode trueSucc, AbstractBeginNode falseSucc) {
        NodeColor color = coloredNodes.get(node);
        if (color == null) {

            if (coloredNodes.size() >= MAX_USAGE_COLOR_SET_SIZE) {
                return NodeColor.MIXED;
            }

            coloredNodes.put(node, NodeColor.MIXED);

            if (node == merge) {
                color = NodeColor.MIXED;
            } else if (node == trueSucc) {
                color = NodeColor.TRUE_BRANCH;
            } else if (node == falseSucc) {
                color = NodeColor.FALSE_BRANCH;
            } else {
                if (node instanceof AbstractMergeNode) {
                    AbstractMergeNode mergeNode = (AbstractMergeNode) node;
                    NodeColor combinedColor = null;
                    for (int i = 0; i < mergeNode.forwardEndCount(); ++i) {
                        NodeColor curColor = colorUsage(coloredNodes, mergeNode.forwardEndAt(i), merge, trueSucc, falseSucc);
                        if (combinedColor == null) {
                            combinedColor = curColor;
                        } else if (combinedColor != curColor) {
                            combinedColor = NodeColor.MIXED;
                            break;
                        }
                    }
                    color = combinedColor;
                } else if (node instanceof StartNode) {
                    color = NodeColor.MIXED;
                } else if (node instanceof FixedNode) {
                    FixedNode fixedNode = (FixedNode) node;
                    Node predecessor = fixedNode.predecessor();
                    assert predecessor != null : fixedNode;
                    color = colorUsage(coloredNodes, predecessor, merge, trueSucc, falseSucc);
                } else if (node instanceof PhiNode) {
                    PhiNode phiNode = (PhiNode) node;
                    AbstractMergeNode phiMerge = phiNode.merge();

                    if (phiMerge instanceof LoopBeginNode) {
                        color = colorUsage(coloredNodes, phiMerge, merge, trueSucc, falseSucc);
                    } else {

                        for (int i = 0; i < phiMerge.forwardEndCount(); ++i) {
                            NodeColor curColor = colorUsage(coloredNodes, phiMerge.forwardEndAt(i), merge, trueSucc, falseSucc);
                            if (curColor != NodeColor.TRUE_BRANCH && curColor != NodeColor.FALSE_BRANCH) {
                                color = NodeColor.MIXED;
                                break;
                            }
                        }

                        if (color == null) {
                            // Each of the inputs to the phi are either coming unambigously from
                            // true or false branch.
                            color = NodeColor.PHI_MIXED;
                            assert node instanceof PhiNode;
                        }
                    }
                } else {
                    NodeColor combinedColor = null;
                    for (Node n : node.usages()) {
                        if (n != node) {
                            NodeColor curColor = colorUsage(coloredNodes, n, merge, trueSucc, falseSucc);
                            if (combinedColor == null) {
                                combinedColor = curColor;
                            } else if (combinedColor != curColor) {
                                combinedColor = NodeColor.MIXED;
                                break;
                            }
                        }
                    }
                    if (combinedColor == NodeColor.PHI_MIXED) {
                        combinedColor = NodeColor.MIXED;
                    }
                    if (combinedColor == null) {
                        // Floating node without usages => association unclear.
                        combinedColor = NodeColor.MIXED;
                    }
                    color = combinedColor;
                }
            }

            assert color != null : node;
            coloredNodes.put(node, color);
        }
        return color;
    }

    /**
     * @param condition
     * @param phi
     * @param coloredNodes
     * @return true if the passed in {@code condition} uses {@code phi} and the condition is only
     *         used once. Since the phi will go dead the condition using it will also have to be
     *         dead after the optimization.
     */
    private static boolean conditionUses(LogicNode condition, PhiNode phi, EconomicMap<Node, NodeColor> coloredNodes) {
        if (!condition.hasExactlyOneUsage()) {
            return false;
        }
        if (condition instanceof ShortCircuitOrNode) {
            if (condition.graph().getGuardsStage().areDeoptsFixed()) {
                /*
                 * It can be unsafe to simplify a ShortCircuitOr before deopts are fixed because
                 * conversion to guards assumes that all the required conditions are being tested.
                 * Simplfying the condition based on context before this happens may lose a
                 * condition.
                 */
                ShortCircuitOrNode orNode = (ShortCircuitOrNode) condition;
                return (conditionUses(orNode.getX(), phi, coloredNodes) || conditionUses(orNode.getY(), phi, coloredNodes));
            }
        } else if (condition instanceof Canonicalizable.Unary<?>) {
            Canonicalizable.Unary<?> unary = (Canonicalizable.Unary<?>) condition;
            if (unary.getValue() == phi) {
                coloredNodes.put(condition, NodeColor.CONDITION_USAGE);
                return true;
            }
        } else if (condition instanceof Canonicalizable.Binary<?>) {
            Canonicalizable.Binary<?> binary = (Canonicalizable.Binary<?>) condition;
            if (binary.getX() == phi || binary.getY() == phi) {
                coloredNodes.put(condition, NodeColor.CONDITION_USAGE);
                return true;
            }
        }
        return false;
    }

    /**
     * Canonicalize {@code} condition using {@code value} in place of {@code phi}.
     *
     * @param tool
     * @param condition
     * @param phi
     * @param value
     * @return an improved LogicNode or the original condition
     */
    @SuppressWarnings("unchecked")
    private static LogicNode computeCondition(SimplifierTool tool, LogicNode condition, PhiNode phi, Node value) {
        if (condition instanceof ShortCircuitOrNode) {
            if (condition.graph().getGuardsStage().areDeoptsFixed() && condition.graph().isBeforeStage(StageFlag.EXPAND_LOGIC)) {
                ShortCircuitOrNode orNode = (ShortCircuitOrNode) condition;
                LogicNode resultX = computeCondition(tool, orNode.getX(), phi, value);
                LogicNode resultY = computeCondition(tool, orNode.getY(), phi, value);
                if (resultX != orNode.getX() || resultY != orNode.getY()) {
                    LogicNode result = orNode.canonical(tool, resultX, resultY);
                    if (result != orNode) {
                        return result;
                    }
                    /*
                     * Create a new node to carry the optimized inputs.
                     */
                    ShortCircuitOrNode newOr = new ShortCircuitOrNode(resultX, orNode.isXNegated(), resultY,
                                    orNode.isYNegated(), orNode.getShortCircuitProbability());
                    return newOr.canonical(tool);
                }
                return orNode;
            }
        } else if (condition instanceof Canonicalizable.Binary<?>) {
            Canonicalizable.Binary<Node> compare = (Canonicalizable.Binary<Node>) condition;
            if (compare.getX() == phi || compare.getY() == phi) {
                return (LogicNode) compare.canonical(tool, compare.getX() == phi ? value : compare.getX(), compare.getY() == phi ? value : compare.getY());
            }
        } else if (condition instanceof Canonicalizable.Unary<?>) {
            Canonicalizable.Unary<Node> compare = (Canonicalizable.Unary<Node>) condition;
            if (compare.getValue() == phi) {
                return (LogicNode) compare.canonical(tool, value);
            }
        }
        if (condition instanceof Canonicalizable) {
            return (LogicNode) ((Canonicalizable) condition).canonical(tool);
        }
        return condition;
    }

    private static void cleanupMerge(MergeNode merge) {
        if (merge != null && merge.isAlive()) {
            if (merge.forwardEndCount() == 0) {
                GraphUtil.killCFG(merge);
            } else if (merge.forwardEndCount() == 1) {
                merge.graph().reduceTrivialMerge(merge);
            }
        }
    }

    @SuppressWarnings("try")
    private static MergeNode insertMerge(AbstractBeginNode begin, ValuePhiNode oldPhi, FrameState stateAfter, SimplifierTool tool) {
        MergeNode merge = begin.graph().add(new MergeNode());

        AbstractBeginNode newBegin;
        try (DebugCloseable position = begin.withNodeSourcePosition()) {
            newBegin = begin.graph().add(new BeginNode());
            begin.replaceAtPredecessor(newBegin);
            newBegin.setNext(begin);
        }

        FixedNode next = newBegin.next();
        next.replaceAtPredecessor(merge);
        newBegin.setNext(begin.graph().add(new EndNode()));
        merge.addForwardEnd((EndNode) newBegin.next());

        ValuePhiNode phi = begin.graph().addOrUnique(new ValuePhiNode(oldPhi.stamp(NodeView.DEFAULT), merge));
        phi.addInput(oldPhi);

        if (stateAfter != null) {
            FrameState newState = stateAfter.duplicate();
            newState.replaceAllInputs(oldPhi, phi);
            merge.setStateAfter(newState);
        }
        merge.setNext(next);
        tool.addToWorkList(begin);
        return merge;
    }

    /**
     * @formatter:off
     *  Lemma taken from Hackers Delight:4-1 "Checking Bounds of Integers"
     *
     *  Pattern: a <= x <= b
     *
     *  Theorem: a <= x <= b ==== to x - a <= (unsigned) b - a iff a and b are
     *  signed integers and a <= b
     *
     *  We focus on constants for now to capture the most relevant cases.
     *
     *  Example:
     *     {@code a <= x <= b}  becomes:
     *
     *          a) {@code trueOrdered=true}  : {@code !(x < a)  && x < b + 1}
     *       or b) {@code trueOrdered=false} : {@code x < b + 1 && !(x < a) }
     *
     *    therefore, we can check the static pre-condition {@code a < b + 1}
     *    and transform the check to {@code x - a |<| b + 1 -a}
     *
     *@formatter:on
     */
    private static LogicNode canMergeRangeChecks(LogicNode condition1, LogicNode condition2, boolean trueOrdered) {
        if (condition1.equals(condition2)) {
            return null;
        }
        if (!(condition1 instanceof IntegerLessThanNode)) {
            return null;
        }
        if (!(condition2 instanceof IntegerLessThanNode)) {
            return null;
        }
        IntegerLessThanNode c1 = (IntegerLessThanNode) condition1;
        IntegerLessThanNode c2 = (IntegerLessThanNode) condition2;
        if (c1.getX() != c2.getX()) {
            // not the same check
            return null;
        }
        if (!c1.getY().isConstant() || !c2.getY().isConstant()) {
            // none constant stamps, ignored for now
            return null;
        }
        ValueNode x = c1.getX();
        ValueNode a = trueOrdered ? c1.getY() : c2.getY();
        ValueNode b = trueOrdered ? c2.getY() : c1.getY();

        IntegerStamp xStamp = (IntegerStamp) x.stamp(NodeView.DEFAULT);
        IntegerStamp aStamp = (IntegerStamp) a.stamp(NodeView.DEFAULT);
        IntegerStamp bStamp = (IntegerStamp) b.stamp(NodeView.DEFAULT);

        assert xStamp.getBits() == aStamp.getBits();
        assert xStamp.getBits() == bStamp.getBits();

        long aRaw = aStamp.asConstant().asLong();
        long bRaw = bStamp.asConstant().asLong();

        // theorem
        if (!(aRaw < bRaw)) {
            return null;
        }
        // rewire to new condition
        StructuredGraph graph = condition1.graph();
        ValueNode left = SubNode.create(x, a, NodeView.DEFAULT);
        ValueNode rigt = SubNode.create(b, a, NodeView.DEFAULT);
        LogicNode newCondition = graph.addOrUniqueWithInputs(IntegerBelowNode.create(left, rigt, NodeView.DEFAULT));
        return newCondition;
    }

    private static boolean optimizeCompoundConditional(IfNode ifNode) {
        return mergeShortCircuitOrRangeCheck(ifNode) || mergeShortCircuitAndRangeCheck(ifNode);
    }

    private static boolean mergeShortCircuitOrRangeCheck(IfNode ifNode) {
        LogicNode condition = ifNode.condition();
        if (condition instanceof ShortCircuitOrNode) {
            ShortCircuitOrNode sco = (ShortCircuitOrNode) condition;
            LogicNode left = sco.getX();
            LogicNode right = sco.getY();
            if (!sco.isXNegated() && sco.isYNegated()) {
                LogicNode replacement = canMergeRangeChecks(left, right, true);
                if (replacement != null) {
                    ifNode.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, ifNode.graph(), "Before XooY rewrite %s", ifNode);
                    ifNode.setCondition(condition.graph().addOrUniqueWithInputs(LogicNegationNode.create(replacement)));
                    NumberOfMergedRangeChecksShortCircuitOr.increment(ifNode.getDebug());
                    ifNode.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, ifNode.graph(), "After XooY rewrite %s", ifNode);
                }
            } else if (sco.isXNegated() && !sco.isYNegated()) {
                LogicNode replacement = canMergeRangeChecks(left, right, false);
                if (replacement != null) {
                    ifNode.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, ifNode.graph(), "Before XooY rewrite %s", ifNode);
                    ifNode.setCondition(condition.graph().addOrUniqueWithInputs(LogicNegationNode.create(replacement)));
                    NumberOfMergedRangeChecksShortCircuitOr.increment(ifNode.getDebug());
                    ifNode.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, ifNode.graph(), "After XooY rewrite %s", ifNode);
                }
            }
        }
        return false;
    }

    private static final CounterKey NumberOfMergedRangeChecks = DebugContext.counter("NumberMergedRangeChecks_XaaB");
    private static final CounterKey NumberOfMergedRangeChecksShortCircuitOr = DebugContext.counter("NumberMergedRangeChecks_XooB_ShortCircuitOr");

    private static final int MAX_USAGE_COLOR_SET_SIZE = 64;

    private static boolean mergeShortCircuitAndRangeCheck(IfNode ifNode) {
        AbstractBeginNode trueSucc = ifNode.trueSuccessor();
        AbstractBeginNode falseSucc = ifNode.falseSuccessor();
        if (trueSucc.hasUsages() || falseSucc.hasUsages()) {
            // begin nodes used as guards
            return false;
        }
        if (trueSucc instanceof LoopExitNode || falseSucc instanceof LoopExitNode) {
            // loops
            return false;
        }
        boolean truePattern = falseSucc.next() instanceof IfNode;
        LogicNode originalCondition = ifNode.condition();
        if ((truePattern ? falseSucc.next() : trueSucc.next()) instanceof IfNode) {
            IfNode potentialCompoundIf = (IfNode) (truePattern ? falseSucc.next() : trueSucc.next());
            LogicNode potentialComoundCondition = potentialCompoundIf.condition();
            if (truePattern ? IfNode.sameDestination(trueSucc, potentialCompoundIf.falseSuccessor()) : IfNode.sameDestination(falseSucc, potentialCompoundIf.trueSuccessor())) {
                // we found a compound conditional
                LogicNode replacee = canMergeRangeChecks(originalCondition, potentialComoundCondition, truePattern);
                if (replacee != null) {
                    ifNode.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, ifNode.graph(), "Before XaaY rewrite");
                    // remove conditions
                    ifNode.setCondition(null);
                    potentialCompoundIf.setCondition(null);
                    // remaining condition is the compound one
                    potentialCompoundIf.setCondition(replacee);
                    // Recompute new probability given the combined condition
                    BranchProbabilityData newProfile;
                    if (truePattern) {
                        newProfile = potentialCompoundIf.getProfileData().combineAndWithNegated(ifNode.getProfileData());
                    } else {
                        newProfile = ifNode.getProfileData().combineAndWithNegated(potentialCompoundIf.getProfileData());
                    }
                    FixedNode pred = (FixedNode) ifNode.predecessor();
                    ((FixedWithNextNode) potentialCompoundIf.predecessor()).setNext(null);
                    ((FixedWithNextNode) pred).setNext(potentialCompoundIf);
                    potentialCompoundIf.setTrueSuccessorProbability(newProfile);
                    GraphUtil.killCFG(ifNode);
                    ifNode.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, ifNode.graph(), "After XaaY rewrite");

                    if (!truePattern) {
                        AbstractBeginNode survivingIfTrueSucc = potentialCompoundIf.trueSuccessor();
                        AbstractBeginNode survivingIfFalseSucc = potentialCompoundIf.falseSuccessor();
                        potentialCompoundIf.setTrueSuccessor(null);
                        potentialCompoundIf.setFalseSuccessor(null);
                        potentialCompoundIf.setTrueSuccessor(survivingIfFalseSucc);
                        potentialCompoundIf.setFalseSuccessor(survivingIfTrueSucc);
                    }
                    NumberOfMergedRangeChecks.increment(replacee.graph().getDebug());
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isUnboxedFrom(MetaAccessProvider meta, NodeView view, ValueNode x, ValueNode src) {
        if (x == src) {
            return true;
        } else if (x instanceof UnboxNode) {
            return isUnboxedFrom(meta, view, ((UnboxNode) x).getValue(), src);
        } else if (x instanceof PiNode) {
            PiNode pi = (PiNode) x;
            return isUnboxedFrom(meta, view, pi.getOriginalNode(), src);
        } else if (x instanceof LoadFieldNode) {
            LoadFieldNode load = (LoadFieldNode) x;
            ResolvedJavaType integerType = meta.lookupJavaType(Integer.class);
            if (load.getValue().stamp(view).javaType(meta).equals(integerType)) {
                return isUnboxedFrom(meta, view, load.getValue(), src);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Attempts to replace the following pattern:
     *
     * <pre>
     * Integer x = ...;
     * Integer y = ...;
     * if ((x == y) || x.equals(y)) { ... }
     * </pre>
     *
     * with:
     *
     * <pre>
     * Integer x = ...;
     * Integer y = ...;
     * if (x.equals(y)) { ... }
     * </pre>
     *
     * whenever the probability that the reference check will pass is relatively small.
     *
     * See GR-1315 for more information.
     */
    private boolean tryEliminateBoxedReferenceEquals(SimplifierTool tool) {
        if (!(condition instanceof ObjectEqualsNode)) {
            return false;
        }

        MetaAccessProvider meta = tool.getMetaAccess();
        ObjectEqualsNode equalsCondition = (ObjectEqualsNode) condition;
        ValueNode x = equalsCondition.getX();
        ValueNode y = equalsCondition.getY();
        ResolvedJavaType integerType = meta.lookupJavaType(Integer.class);

        // At least one argument for reference equal must be a boxed primitive.
        NodeView view = NodeView.from(tool);
        if (!x.stamp(view).javaType(meta).equals(integerType) && !y.stamp(view).javaType(meta).equals(integerType)) {
            return false;
        }

        // The reference equality check is usually more efficient compared to a boxing check.
        // The success of the reference equals must therefore be relatively rare, otherwise it makes
        // no sense to eliminate it.
        if (getTrueSuccessorProbability() > 0.4) {
            return false;
        }

        // True branch must be empty.
        if (trueSuccessor instanceof BeginNode || trueSuccessor instanceof LoopExitNode) {
            if (trueSuccessor.next() instanceof EndNode) {
                // Empty true branch.
            } else {
                return false;
            }
        } else {
            return false;
        }

        // False branch must only check the unboxed values.
        UnboxNode unbox = null;
        FixedGuardNode unboxCheck = null;
        for (FixedNode node : falseSuccessor.getBlockNodes()) {
            if (!(node instanceof BeginNode || node instanceof UnboxNode || node instanceof FixedGuardNode || node instanceof EndNode ||
                            node instanceof LoadFieldNode || node instanceof LoopExitNode)) {
                return false;
            }
            if (node instanceof UnboxNode) {
                if (unbox == null) {
                    unbox = (UnboxNode) node;
                } else {
                    return false;
                }
            }
            if (!(node instanceof FixedGuardNode)) {
                continue;
            }
            FixedGuardNode fixed = (FixedGuardNode) node;
            if (!(fixed.condition() instanceof IntegerEqualsNode)) {
                continue;
            }
            IntegerEqualsNode equals = (IntegerEqualsNode) fixed.condition();
            if ((isUnboxedFrom(meta, view, equals.getX(), x) && isUnboxedFrom(meta, view, equals.getY(), y)) ||
                            (isUnboxedFrom(meta, view, equals.getX(), y) && isUnboxedFrom(meta, view, equals.getY(), x))) {
                unboxCheck = fixed;
            }
        }
        if (unbox == null || unboxCheck == null) {
            return false;
        }

        // Falsify the reference check.
        setCondition(graph().addOrUniqueWithInputs(LogicConstantNode.contradiction()));

        return true;
    }

    // SwitchFoldable implementation.

    @Override
    public Node getNextSwitchFoldableBranch() {
        return falseSuccessor();
    }

    @Override
    public boolean isInSwitch(ValueNode switchValue) {
        return SwitchFoldable.maybeIsInSwitch(condition()) && SwitchFoldable.sameSwitchValue(condition(), switchValue);
    }

    @Override
    public void cutOffCascadeNode() {
        setTrueSuccessor(null);
    }

    @Override
    public void cutOffLowestCascadeNode() {
        setFalseSuccessor(null);
        setTrueSuccessor(null);
    }

    @Override
    public AbstractBeginNode getDefault() {
        return falseSuccessor();
    }

    @Override
    public ValueNode switchValue() {
        if (SwitchFoldable.maybeIsInSwitch(condition())) {
            return ((IntegerEqualsNode) condition()).getX();
        }
        return null;
    }

    @Override
    public boolean isNonInitializedProfile() {
        return !ProfileSource.isTrusted(profileSource());
    }

    @Override
    public ProfileSource profileSource() {
        return profileData.getProfileSource();
    }

    @Override
    public BranchProbabilityData getProfileData() {
        return profileData;
    }

    @Override
    public int intKeyAt(int i) {
        assert i == 0;
        return ((IntegerEqualsNode) condition()).getY().asJavaConstant().asInt();
    }

    @Override
    public double keyProbability(int i) {
        assert i == 0;
        return getTrueSuccessorProbability();
    }

    @Override
    public AbstractBeginNode keySuccessor(int i) {
        assert i == 0;
        return trueSuccessor();
    }

    @Override
    public double defaultProbability() {
        return 1.0d - getTrueSuccessorProbability();
    }

    /**
     * Try to optimize this as if it were a {@link ConditionalNode}.
     */
    private boolean conditionalNodeOptimization(SimplifierTool tool) {
        if (trueSuccessor().next() instanceof AbstractEndNode && falseSuccessor().next() instanceof AbstractEndNode) {
            AbstractEndNode trueEnd = (AbstractEndNode) trueSuccessor().next();
            AbstractEndNode falseEnd = (AbstractEndNode) falseSuccessor().next();
            if (trueEnd.merge() != falseEnd.merge()) {
                return false;
            }
            if (!(trueEnd.merge() instanceof MergeNode)) {
                return false;
            }
            MergeNode merge = (MergeNode) trueEnd.merge();
            if (!merge.hasExactlyOneUsage() || merge.phis().count() != 1) {
                return false;
            }

            if (trueSuccessor().hasAnchored() || falseSuccessor().hasAnchored()) {
                return false;
            }

            if (falseSuccessor instanceof LoopExitNode && ((LoopExitNode) falseSuccessor).stateAfter != null) {
                return false;
            }

            PhiNode phi = merge.phis().first();
            ValueNode falseValue = phi.valueAt(falseEnd);
            ValueNode trueValue = phi.valueAt(trueEnd);

            NodeView view = NodeView.from(tool);
            ValueNode result = ConditionalNode.canonicalizeConditional(condition, trueValue, falseValue, phi.stamp(view), view, tool);
            if (result != null) {
                /*
                 * canonicalizeConditional returns possibly new nodes so add them to the graph.
                 */
                if (result.graph() == null) {
                    result = graph().addOrUniqueWithInputs(result);
                }
                result = proxyReplacement(result);
                /*
                 * This optimization can be performed even if multiple values merge at this phi
                 * since the two inputs get simplified into one.
                 */
                phi.setValueAt(trueEnd, result);
                removeThroughFalseBranch(tool, merge);
                return true;
            }
        }

        return false;
    }

    private void pushNodesThroughIf(SimplifierTool tool) {
        assert trueSuccessor().hasNoUsages() && falseSuccessor().hasNoUsages();
        // push similar nodes upwards through the if, thereby deduplicating them
        do {
            AbstractBeginNode trueSucc = trueSuccessor();
            AbstractBeginNode falseSucc = falseSuccessor();
            if (trueSucc instanceof BeginNode && falseSucc instanceof BeginNode && trueSucc.next() instanceof FixedWithNextNode && falseSucc.next() instanceof FixedWithNextNode) {
                FixedWithNextNode trueNext = (FixedWithNextNode) trueSucc.next();
                FixedWithNextNode falseNext = (FixedWithNextNode) falseSucc.next();
                NodeClass<?> nodeClass = trueNext.getNodeClass();
                if (trueNext.getClass() == falseNext.getClass()) {
                    if (trueNext instanceof AbstractBeginNode || trueNext instanceof ControlFlowAnchored || trueNext instanceof MemoryAnchorNode) {
                        /*
                         * Cannot do this optimization for begin nodes, because it could move guards
                         * above the if that need to stay below a branch.
                         *
                         * Cannot do this optimization for ControlFlowAnchored nodes, because these
                         * are anchored in their control-flow position, and should not be moved
                         * upwards.
                         */
                    } else if (nodeClass.equalInputs(trueNext, falseNext) && trueNext.valueEquals(falseNext)) {
                        falseNext.replaceAtUsages(trueNext);
                        graph().removeFixed(falseNext);
                        GraphUtil.unlinkFixedNode(trueNext);
                        graph().addBeforeFixed(this, trueNext);
                        for (Node usage : trueNext.usages().snapshot()) {
                            if (usage.isAlive()) {
                                NodeClass<?> usageNodeClass = usage.getNodeClass();
                                if (usageNodeClass.valueNumberable() && !usageNodeClass.isLeafNode()) {
                                    Node newNode = graph().findDuplicate(usage);
                                    if (newNode != null) {
                                        usage.replaceAtUsagesAndDelete(newNode);
                                    }
                                }
                                if (usage.isAlive()) {
                                    tool.addToWorkList(usage);
                                }
                            }
                        }
                        continue;
                    }
                }
            }
            break;
        } while (true);
    }

    /**
     * Recognize a couple patterns that can be merged into an unsigned compare.
     *
     * @param tool
     * @return true if a replacement was done.
     */
    @SuppressWarnings("try")
    private boolean checkForUnsignedCompare(SimplifierTool tool) {
        assert trueSuccessor().hasNoUsages() && falseSuccessor().hasNoUsages();
        if (condition() instanceof IntegerLessThanNode) {
            NodeView view = NodeView.from(tool);
            IntegerLessThanNode lessThan = (IntegerLessThanNode) condition();
            Constant y = lessThan.getY().stamp(view).asConstant();
            if (y instanceof PrimitiveConstant && ((PrimitiveConstant) y).asLong() == 0 && falseSuccessor().next() instanceof IfNode) {
                IfNode ifNode2 = (IfNode) falseSuccessor().next();
                if (ifNode2.condition() instanceof IntegerLessThanNode) {
                    IntegerLessThanNode lessThan2 = (IntegerLessThanNode) ifNode2.condition();
                    AbstractBeginNode falseSucc = ifNode2.falseSuccessor();
                    AbstractBeginNode trueSucc = ifNode2.trueSuccessor();
                    IntegerBelowNode below = null;
                    /*
                     * Convert x >= 0 && x < positive which is represented as !(x < 0) && x <
                     * <positive> into an unsigned compare.
                     */
                    if (lessThan2.getX() == lessThan.getX() && lessThan2.getY().stamp(view) instanceof IntegerStamp &&
                                    ((IntegerStamp) lessThan2.getY().stamp(view)).isPositive() &&
                                    sameDestination(trueSuccessor(), ifNode2.falseSuccessor)) {
                        below = graph().unique(new IntegerBelowNode(lessThan2.getX(), lessThan2.getY()));
                        // swap direction
                        AbstractBeginNode tmp = falseSucc;
                        falseSucc = trueSucc;
                        trueSucc = tmp;
                    } else if (lessThan2.getY() == lessThan.getX() && sameDestination(trueSuccessor(), ifNode2.trueSuccessor)) {
                        /*
                         * Convert x >= 0 && x <= positive which is represented as !(x < 0) &&
                         * !(<positive> > x), into x <| positive + 1. This can only be done for
                         * constants since there isn't a IntegerBelowEqualThanNode but that doesn't
                         * appear to be interesting.
                         */
                        JavaConstant positive = lessThan2.getX().asJavaConstant();
                        if (positive != null && positive.asLong() > 0 && positive.asLong() < positive.getJavaKind().getMaxValue()) {
                            ConstantNode newLimit = ConstantNode.forIntegerStamp(lessThan2.getX().stamp(view), positive.asLong() + 1, graph());
                            below = graph().unique(new IntegerBelowNode(lessThan.getX(), newLimit));
                        }
                    }
                    if (below != null) {
                        try (DebugCloseable position = ifNode2.withNodeSourcePosition()) {
                            ifNode2.setTrueSuccessor(null);
                            ifNode2.setFalseSuccessor(null);

                            IfNode newIfNode = graph().add(new IfNode(below, falseSucc, trueSucc, profileData.negated()));
                            // Remove the < 0 test.
                            tool.deleteBranch(trueSuccessor);
                            graph().removeSplit(this, falseSuccessor);

                            // Replace the second test with the new one.
                            ifNode2.predecessor().replaceFirstSuccessor(ifNode2, newIfNode);
                            ifNode2.safeDelete();
                            return true;
                        }
                    }
                }
            } else if (y instanceof PrimitiveConstant && ((PrimitiveConstant) y).asLong() < 0 && falseSuccessor().next() instanceof IfNode) {
                IfNode ifNode2 = (IfNode) falseSuccessor().next();
                AbstractBeginNode falseSucc = ifNode2.falseSuccessor();
                AbstractBeginNode trueSucc = ifNode2.trueSuccessor();
                IntegerBelowNode below = null;
                if (ifNode2.condition() instanceof IntegerLessThanNode) {
                    ValueNode x = lessThan.getX();
                    IntegerLessThanNode lessThan2 = (IntegerLessThanNode) ifNode2.condition();
                    /*
                     * Convert x >= -C1 && x < C2, represented as !(x < -C1) && x < C2, into an
                     * unsigned compare. This condition is equivalent to x + C1 |<| C1 + C2 if C1 +
                     * C2 does not overflow.
                     */
                    Constant c2 = lessThan2.getY().stamp(view).asConstant();
                    if (lessThan2.getX() == x && c2 instanceof PrimitiveConstant && ((PrimitiveConstant) c2).asLong() > 0 &&
                                    x.stamp(view).isCompatible(lessThan.getY().stamp(view)) &&
                                    x.stamp(view).isCompatible(lessThan2.getY().stamp(view)) &&
                                    sameDestination(trueSuccessor(), ifNode2.falseSuccessor)) {
                        long newLimitValue = -((PrimitiveConstant) y).asLong() + ((PrimitiveConstant) c2).asLong();
                        // Make sure the limit fits into the target type without overflow.
                        if (newLimitValue > 0 && newLimitValue <= CodeUtil.maxValue(PrimitiveStamp.getBits(x.stamp(view)))) {
                            ConstantNode newLimit = ConstantNode.forIntegerStamp(x.stamp(view), newLimitValue, graph());
                            ConstantNode c1 = ConstantNode.forIntegerStamp(x.stamp(view), -((PrimitiveConstant) y).asLong(), graph());
                            ValueNode addNode = graph().addOrUniqueWithInputs(AddNode.create(x, c1, view));
                            below = graph().unique(new IntegerBelowNode(addNode, newLimit));
                        }
                    }
                }
                if (below != null) {
                    try (DebugCloseable position = ifNode2.withNodeSourcePosition()) {
                        ifNode2.setTrueSuccessor(null);
                        ifNode2.setFalseSuccessor(null);

                        IfNode newIfNode = graph().add(new IfNode(below, trueSucc, falseSucc, profileData));
                        // Remove the < -C1 test.
                        tool.deleteBranch(trueSuccessor);
                        graph().removeSplit(this, falseSuccessor);

                        // Replace the second test with the new one.
                        ifNode2.predecessor().replaceFirstSuccessor(ifNode2, newIfNode);
                        ifNode2.safeDelete();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check it these two blocks end up at the same place. Meeting at the same merge, or
     * deoptimizing in the same way.
     */
    public static boolean sameDestination(AbstractBeginNode succ1, AbstractBeginNode succ2) {
        Node next1 = succ1.next();
        Node next2 = succ2.next();
        if (next1 instanceof AbstractEndNode && next2 instanceof AbstractEndNode) {
            AbstractEndNode end1 = (AbstractEndNode) next1;
            AbstractEndNode end2 = (AbstractEndNode) next2;
            if (end1.merge() == end2.merge()) {
                for (PhiNode phi : end1.merge().phis()) {
                    if (phi.valueAt(end1) != phi.valueAt(end2)) {
                        return false;
                    }
                }
                // They go to the same MergeNode and merge the same values
                return true;
            }
        } else if (next1 instanceof DeoptimizeNode && next2 instanceof DeoptimizeNode) {
            DeoptimizeNode deopt1 = (DeoptimizeNode) next1;
            DeoptimizeNode deopt2 = (DeoptimizeNode) next2;
            if (deopt1.getReason() == deopt2.getReason() && deopt1.getAction() == deopt2.getAction()) {
                // Same deoptimization reason and action.
                return true;
            }
        } else if (next1 instanceof LoopExitNode && next2 instanceof LoopExitNode) {
            LoopExitNode exit1 = (LoopExitNode) next1;
            LoopExitNode exit2 = (LoopExitNode) next2;
            if (exit1.loopBegin() == exit2.loopBegin() && exit1.stateAfter() == exit2.stateAfter() && exit1.stateAfter() == null && sameDestination(exit1, exit2)) {
                // Exit the same loop and end up at the same place.
                return true;
            }
        } else if (next1 instanceof ReturnNode && next2 instanceof ReturnNode) {
            ReturnNode exit1 = (ReturnNode) next1;
            ReturnNode exit2 = (ReturnNode) next2;
            if (exit1.result() == exit2.result()) {
                // Exit the same loop and end up at the same place.
                return true;
            }
        }
        return false;
    }

    private static boolean prepareForSwap(SimplifierTool tool, LogicNode a, LogicNode b) {
        DebugContext debug = a.getDebug();
        if (a instanceof InstanceOfNode) {
            InstanceOfNode instanceOfA = (InstanceOfNode) a;
            if (b instanceof IsNullNode) {
                IsNullNode isNullNode = (IsNullNode) b;
                if (isNullNode.getValue() == instanceOfA.getValue()) {
                    debug.log("Can swap instanceof and isnull if");
                    return true;
                }
            } else if (b instanceof InstanceOfNode) {
                InstanceOfNode instanceOfB = (InstanceOfNode) b;
                if (instanceOfA.getValue() == instanceOfB.getValue() && !instanceOfA.type().getType().isInterface() && !instanceOfB.type().getType().isInterface() &&
                                !instanceOfA.type().getType().isAssignableFrom(instanceOfB.type().getType()) && !instanceOfB.type().getType().isAssignableFrom(instanceOfA.type().getType())) {
                    // Two instanceof on the same value with mutually exclusive types.
                    debug.log("Can swap instanceof for types %s and %s", instanceOfA.type(), instanceOfB.type());
                    return true;
                }
            }
        } else if (a instanceof CompareNode) {
            CompareNode compareA = (CompareNode) a;
            Condition conditionA = compareA.condition().asCondition();
            if (compareA.unorderedIsTrue()) {
                return false;
            }
            if (b instanceof CompareNode) {
                CompareNode compareB = (CompareNode) b;
                if (compareA == compareB) {
                    debug.log("Same conditions => do not swap and leave the work for global value numbering.");
                    return false;
                }
                if (compareB.unorderedIsTrue()) {
                    return false;
                }
                Condition comparableCondition = null;
                Condition conditionB = compareB.condition().asCondition();
                if (compareB.getX() == compareA.getX() && compareB.getY() == compareA.getY()) {
                    comparableCondition = conditionB;
                } else if (compareB.getX() == compareA.getY() && compareB.getY() == compareA.getX()) {
                    comparableCondition = conditionB.mirror();
                }

                if (comparableCondition != null) {
                    if (conditionA.trueIsDisjoint(comparableCondition)) {
                        // The truth of the two conditions is disjoint => can reorder.
                        debug.log("Can swap disjoint coditions on same values: %s and %s", conditionA, comparableCondition);
                        return true;
                    }
                } else if (conditionA == Condition.EQ && conditionB == Condition.EQ) {
                    boolean canSwap = false;
                    if ((compareA.getX() == compareB.getX() && valuesDistinct(tool, compareA.getY(), compareB.getY()))) {
                        canSwap = true;
                    } else if ((compareA.getX() == compareB.getY() && valuesDistinct(tool, compareA.getY(), compareB.getX()))) {
                        canSwap = true;
                    } else if ((compareA.getY() == compareB.getX() && valuesDistinct(tool, compareA.getX(), compareB.getY()))) {
                        canSwap = true;
                    } else if ((compareA.getY() == compareB.getY() && valuesDistinct(tool, compareA.getX(), compareB.getX()))) {
                        canSwap = true;
                    }

                    if (canSwap) {
                        debug.log("Can swap equality condition with one shared and one disjoint value.");
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean valuesDistinct(SimplifierTool tool, ValueNode a, ValueNode b) {
        if (a.isConstant() && b.isConstant()) {
            Boolean equal = tool.getConstantReflection().constantEquals(a.asConstant(), b.asConstant());
            if (equal != null) {
                return !equal.booleanValue();
            }
        }

        NodeView view = NodeView.from(tool);
        Stamp stampA = a.stamp(view);
        Stamp stampB = b.stamp(view);
        return stampA.alwaysDistinct(stampB);
    }

    /**
     * Tries to remove an empty if construct or replace an if construct with a materialization.
     *
     * @return true if a transformation was made, false otherwise
     */
    private boolean removeOrMaterializeIf(SimplifierTool tool) {
        assert trueSuccessor().hasNoUsages() && falseSuccessor().hasNoUsages();
        MergeNode blockingMerge = null;
        if (trueSuccessor().next() instanceof ReturnNode && falseSuccessor().next() instanceof AbstractEndNode) {
            AbstractMergeNode am = ((AbstractEndNode) falseSuccessor.next()).merge();
            if (am instanceof MergeNode) {
                blockingMerge = (MergeNode) am;
            }
        } else if (falseSuccessor().next() instanceof ReturnNode && trueSuccessor().next() instanceof AbstractEndNode) {
            AbstractMergeNode am = ((AbstractEndNode) trueSuccessor().next()).merge();
            if (am instanceof MergeNode) {
                blockingMerge = (MergeNode) am;
            }
        }
        if (blockingMerge != null) {
            if (blockingMerge.next() instanceof ReturnNode) {
                AbstractMergeNode.duplicateReturnThroughMerge(blockingMerge);
            }
        }
        if (trueSuccessor().next() instanceof AbstractEndNode && falseSuccessor().next() instanceof AbstractEndNode) {
            AbstractEndNode trueEnd = (AbstractEndNode) trueSuccessor().next();
            AbstractEndNode falseEnd = (AbstractEndNode) falseSuccessor().next();
            AbstractMergeNode merge = trueEnd.merge();

            if (falseSuccessor instanceof LoopExitNode && ((LoopExitNode) falseSuccessor).stateAfter != null) {
                return false;
            }

            if (merge == falseEnd.merge() && trueSuccessor().anchored().isEmpty() && falseSuccessor().anchored().isEmpty()) {
                PhiNode singlePhi = null;
                int distinct = 0;
                for (PhiNode phi : merge.phis()) {
                    ValueNode trueValue = phi.valueAt(trueEnd);
                    ValueNode falseValue = phi.valueAt(falseEnd);
                    if (trueValue != falseValue) {
                        distinct++;
                        singlePhi = phi;
                    }
                }
                if (distinct == 0) {
                    /*
                     * Multiple phis but merging same values for true and false, so simply delete
                     * the path
                     */
                    removeThroughFalseBranch(tool, merge);
                    return true;
                } else if (distinct == 1) {
                    // Fortify: Suppress Null Dereference false positive
                    assert singlePhi != null;

                    ValueNode trueValue = singlePhi.valueAt(trueEnd);
                    ValueNode falseValue = singlePhi.valueAt(falseEnd);
                    ValueNode conditional = canonicalizeConditionalCascade(tool, trueValue, falseValue);
                    if (conditional != null) {
                        conditional = proxyReplacement(conditional);
                        singlePhi.setValueAt(trueEnd, conditional);
                        removeThroughFalseBranch(tool, merge);
                        return true;
                    }
                    /*-
                     * Remove this pattern:
                     * if (a == null)
                     *     return null
                     * else
                     *     return a
                     */
                    if (condition instanceof IsNullNode && trueValue.isJavaConstant() && trueValue.asJavaConstant().isDefaultForKind() && merge instanceof MergeNode) {
                        ValueNode value = ((IsNullNode) condition).getValue();
                        if (falseValue == value && singlePhi.stamp(NodeView.DEFAULT).equals(value.stamp(NodeView.DEFAULT))) {
                            singlePhi.setValueAt(trueEnd, falseValue);
                            removeThroughFalseBranch(tool, merge);
                            return true;
                        }
                    }
                }
            }
        }
        if (trueSuccessor().next() instanceof ReturnNode && falseSuccessor().next() instanceof ReturnNode) {
            ReturnNode trueEnd = (ReturnNode) trueSuccessor().next();
            ReturnNode falseEnd = (ReturnNode) falseSuccessor().next();
            ValueNode trueValue = trueEnd.result();
            ValueNode falseValue = falseEnd.result();
            ValueNode value = null;
            boolean needsProxy = false;
            if (trueValue != null) {
                if (trueValue == falseValue) {
                    value = trueValue;
                } else {
                    value = canonicalizeConditionalCascade(tool, trueValue, falseValue);
                    if (value == null) {
                        return false;
                    }
                    needsProxy = true;
                }
            }

            if (trueSuccessor() instanceof LoopExitNode) {
                FrameState stateAfter = ((LoopExitNode) trueSuccessor()).stateAfter();
                LoopBeginNode loopBegin = ((LoopExitNode) trueSuccessor()).loopBegin();
                assert loopBegin == ((LoopExitNode) falseSuccessor()).loopBegin();
                LoopExitNode loopExitNode = graph().add(new LoopExitNode(loopBegin));
                loopExitNode.setStateAfter(stateAfter);
                graph().addBeforeFixed(this, loopExitNode);
                if (graph().isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL) && needsProxy) {
                    value = graph().addOrUnique(new ValueProxyNode(value, loopExitNode));
                }
            }

            ReturnNode newReturn = graph().add(new ReturnNode(value));
            replaceAtPredecessor(newReturn);
            GraphUtil.killCFG(this);
            return true;
        }
        return false;
    }

    private ValueNode proxyReplacement(ValueNode replacement) {
        /*
         * Special case: Every empty diamond we collapse to a conditional node can potentially
         * contain loop exit nodes on both branches. See the graph below: The two loop exits
         * (instanceof begin node) exit the same loop. The resulting phi is defined outside the
         * loop, but the resulting conditional node will be inside the loop, so we need to proxy the
         * resulting conditional node. Callers of this method ensure that true and false successor
         * have no usages, therefore a and b in the graph below can never be proxies themselves.
         */
        // @formatter:off
        //              +--+
        //              |If|
        //              +--+      +-----+ +-----+
        //         +----+  +----+ |  a  | |  b  |
        //         |Lex |  |Lex | +----^+ +^----+
        //         +----+  +----+      |   |
        //           +-------+         +---+
        //           | Merge +---------+Phi|
        //           +-------+         +---+
        // @formatter:on
        if (this.graph().isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL)) {
            if (trueSuccessor instanceof LoopExitNode && falseSuccessor instanceof LoopExitNode) {
                assert ((LoopExitNode) trueSuccessor).loopBegin() == ((LoopExitNode) falseSuccessor).loopBegin();
                /*
                 * we can collapse all proxy nodes on one loop exit, the surviving one, which will
                 * be the true successor
                 */
                if (falseSuccessor.anchored().isEmpty() && falseSuccessor.hasUsages()) {
                    for (Node n : falseSuccessor.usages().snapshot()) {
                        assert n instanceof ProxyNode;
                        ((ProxyNode) n).setProxyPoint((LoopExitNode) trueSuccessor);
                    }
                }
                /*
                 * The true successor (surviving loop exit) can have usages, namely proxy nodes, the
                 * false successor however, must not have usages any more after the code above
                 */
                assert trueSuccessor.anchored().isEmpty() && falseSuccessor.hasNoUsages();
                return this.graph().addOrUnique(new ValueProxyNode(replacement, (LoopExitNode) trueSuccessor));
            }
        }
        return replacement;
    }

    protected void removeThroughFalseBranch(SimplifierTool tool, AbstractMergeNode merge) {
        // If the LoopExitNode and the Merge still have states then it's incorrect to arbitrarily
        // pick one side of the branch the represent the control flow. The state on the merge is the
        // real after state but it would need to be adjusted to represent the effects of the
        // conditional conversion.
        assert !(falseSuccessor instanceof LoopExitNode) || ((LoopExitNode) falseSuccessor).stateAfter == null;
        AbstractBeginNode trueBegin = trueSuccessor();
        LogicNode conditionNode = condition();
        graph().removeSplitPropagate(this, trueBegin);
        tool.addToWorkList(trueBegin);
        if (conditionNode != null) {
            GraphUtil.tryKillUnused(conditionNode);
        }
        if (merge.isAlive() && merge.forwardEndCount() > 1) {
            for (FixedNode end : merge.forwardEnds()) {
                Node cur = end;
                while (cur != null && cur.predecessor() instanceof BeginNode) {
                    cur = cur.predecessor();
                }
                if (cur != null && cur.predecessor() instanceof IfNode) {
                    tool.addToWorkList(cur.predecessor());
                }
            }
        }
    }

    private ValueNode canonicalizeConditionalViaImplies(ValueNode trueValue, ValueNode falseValue) {
        ValueNode collapsedTrue = trueValue;
        ValueNode collapsedFalse = falseValue;
        boolean simplify = false;
        if (trueValue instanceof ConditionalNode) {
            TriState result = condition().implies(false, ((ConditionalNode) trueValue).condition());
            if (result.isKnown()) {
                simplify = true;
                collapsedTrue = result.toBoolean() ? ((ConditionalNode) trueValue).trueValue() : ((ConditionalNode) trueValue).falseValue();
            }
        }
        if (falseValue instanceof ConditionalNode) {
            TriState result = condition().implies(true, ((ConditionalNode) falseValue).condition());
            if (result.isKnown()) {
                simplify = true;
                collapsedFalse = result.toBoolean() ? ((ConditionalNode) falseValue).trueValue() : ((ConditionalNode) falseValue).falseValue();
            }
        }
        if (simplify) {
            return graph().unique(new ConditionalNode(condition(), collapsedTrue, collapsedFalse));
        }
        return null;
    }

    private List<Node> getNodesForBlock(AbstractBeginNode successor) {
        StructuredGraph.ScheduleResult schedule = graph().getLastSchedule();
        if (schedule == null) {
            return null;
        }
        if (schedule.getCFG().getNodeToBlock().isNew(successor)) {
            // This can occur when nodes were created after the last schedule.
            return null;
        }
        HIRBlock block = schedule.getCFG().blockFor(successor);
        if (block == null) {
            return null;
        }
        return schedule.nodesFor(block);
    }

    /**
     * Check whether conversion of an If to a Conditional causes extra unconditional work. Generally
     * that transformation is beneficial if it doesn't result in extra work in the main path.
     */
    private boolean isSafeConditionalInput(ValueNode value, AbstractBeginNode successor) {
        assert successor.hasNoUsages();
        if (value.isConstant() || condition.inputs().contains(value)) {
            // Assume constants are cheap to evaluate. Any input to the condition itself is also
            // unconditionally evaluated.
            return true;
        }

        if (graph().isAfterStage(StageFlag.FIXED_READS)) {
            if (value instanceof ParameterNode) {
                // Assume Parameters are always evaluated but only apply this logic to graphs after
                // inlining. Checking for ParameterNode causes it to apply to graphs which are going
                // to be inlined into other graphs which is incorrect.
                return true;
            }
            if (value instanceof FixedNode) {
                List<Node> nodes = getNodesForBlock(successor);
                // The successor block is empty so assume that this input evaluated before the
                // condition.
                return nodes != null && nodes.size() == 2;
            }
        }
        return false;
    }

    private ValueNode canonicalizeConditionalCascade(SimplifierTool tool, ValueNode trueValue, ValueNode falseValue) {
        if (trueValue.getStackKind() != falseValue.getStackKind()) {
            return null;
        }
        if (trueValue.getStackKind() != JavaKind.Int && trueValue.getStackKind() != JavaKind.Long) {
            return null;
        }
        if (isSafeConditionalInput(trueValue, trueSuccessor) && isSafeConditionalInput(falseValue, falseSuccessor)) {
            return graph().unique(new ConditionalNode(condition(), trueValue, falseValue));
        }
        ValueNode value = canonicalizeConditionalViaImplies(trueValue, falseValue);
        if (value != null) {
            return value;
        }
        if (graph().isBeforeStage(StageFlag.EXPAND_LOGIC)) {
            /*
             * !isAfterExpandLogic() => Cannot spawn NormalizeCompareNodes after lowering in the
             * ExpandLogicPhase.
             */
            ConditionalNode conditional = null;
            ValueNode constant = null;
            boolean negateCondition;
            if (trueValue instanceof ConditionalNode && falseValue.isConstant()) {
                conditional = (ConditionalNode) trueValue;
                constant = falseValue;
                negateCondition = true;
            } else if (falseValue instanceof ConditionalNode && trueValue.isConstant()) {
                conditional = (ConditionalNode) falseValue;
                constant = trueValue;
                negateCondition = false;
            } else {
                return null;
            }
            boolean negateConditionalCondition = false;
            ValueNode otherValue = null;
            if (constant == conditional.trueValue()) {
                otherValue = conditional.falseValue();
                negateConditionalCondition = false;
            } else if (constant == conditional.falseValue()) {
                otherValue = conditional.trueValue();
                negateConditionalCondition = true;
            }
            if (otherValue != null && otherValue.isConstant()) {
                BranchProbabilityData shortCutProbability = trueSuccessorProfile();
                LogicNode newCondition = LogicNode.or(condition(), negateCondition, conditional.condition(), negateConditionalCondition, shortCutProbability);
                return graph().unique(new ConditionalNode(newCondition, constant, otherValue));
            }

            if (constant.isJavaConstant() && conditional.trueValue().isJavaConstant() && conditional.falseValue().isJavaConstant() && condition() instanceof CompareNode &&
                            conditional.condition() instanceof CompareNode) {

                CompareNode condition1 = (CompareNode) condition();
                Condition cond1 = condition1.condition().asCondition();
                if (negateCondition) {
                    cond1 = cond1.negate();
                }
                // cond1 is EQ, NE, LT, or GE
                CompareNode condition2 = (CompareNode) conditional.condition();
                Condition cond2 = condition2.condition().asCondition();
                ValueNode x = condition1.getX();
                ValueNode y = condition1.getY();
                ValueNode x2 = condition2.getX();
                ValueNode y2 = condition2.getY();
                // `x cond1 y ? c1 : (x2 cond2 y2 ? c2 : c3)`
                boolean sameVars = x == x2 && y == y2;
                if (!sameVars && x == y2 && y == x2) {
                    sameVars = true;
                    cond2 = cond2.mirror();
                }
                if (sameVars) {

                    JavaKind stackKind = conditional.trueValue().stamp(NodeView.from(tool)).getStackKind();
                    assert !stackKind.isNumericFloat();

                    long c1 = constant.asJavaConstant().asLong();
                    long c2 = conditional.trueValue().asJavaConstant().asLong();
                    long c3 = conditional.falseValue().asJavaConstant().asLong();

                    // canonicalize cond2
                    cond2 = cond2.join(cond1.negate());
                    if (cond2 == null) {
                        // mixing signed and unsigned cases, or useless combination of conditions
                        return null;
                    }
                    // derive cond3 from cond1 and cond2
                    Condition cond3 = cond1.negate().join(cond2.negate());
                    if (cond3 == null) {
                        // mixing signed and unsigned cases, or useless combination of conditions
                        return null;
                    }
                    boolean unsigned = cond1.isUnsigned() || cond2.isUnsigned();
                    boolean floatingPoint = x.stamp(NodeView.from(tool)) instanceof FloatStamp;
                    assert !floatingPoint || y.stamp(NodeView.from(tool)) instanceof FloatStamp;
                    assert !(floatingPoint && unsigned);

                    long expected1 = expectedConstantForNormalize(cond1);
                    long expected2 = expectedConstantForNormalize(cond2);
                    long expected3 = expectedConstantForNormalize(cond3);

                    if (c1 == expected1 && c2 == expected2 && c3 == expected3) {
                        // normal order
                    } else if (c1 == 0 - expected1 && c2 == 0 - expected2 && c3 == 0 - expected3) {
                        // reverse order
                        ValueNode tmp = x;
                        x = y;
                        y = tmp;
                    } else {
                        // cannot be expressed by NormalizeCompareNode
                        return null;
                    }
                    if (floatingPoint) {
                        boolean unorderedLess = false;
                        if (((FloatStamp) x.stamp).canBeNaN() || ((FloatStamp) y.stamp).canBeNaN()) {
                            // we may encounter NaNs, check the unordered value
                            // (following the original condition's "unorderedIsTrue" path)
                            long unorderedValue = condition1.unorderedIsTrue() ? c1 : condition2.unorderedIsTrue() ? c2 : c3;
                            if (unorderedValue == 0) {
                                // returning "0" for unordered is not possible
                                return null;
                            }
                            unorderedLess = unorderedValue == -1;
                        }
                        return graph().unique(new FloatNormalizeCompareNode(x, y, stackKind, unorderedLess));
                    } else {
                        return graph().unique(new IntegerNormalizeCompareNode(x, y, stackKind, unsigned));
                    }
                }
            }
        }
        return null;
    }

    private static long expectedConstantForNormalize(Condition condition) {
        if (condition == Condition.EQ) {
            return 0;
        } else if (condition == Condition.LT || condition == Condition.BT) {
            return -1;
        } else {
            assert condition == Condition.GT || condition == Condition.AT;
            return 1;
        }
    }

    public enum NodeColor {
        NONE,
        CONDITION_USAGE,
        TRUE_BRANCH,
        FALSE_BRANCH,
        PHI_MIXED,
        MIXED
    }

    /**
     * Tries to connect code that initializes a variable directly with the successors of an if
     * construct that switches on the variable. For example, the pseudo code below:
     *
     * <pre>
     * contains(list, e, yes, no) {
     *     if (list == null || e == null) {
     *         condition = false;
     *     } else {
     *         condition = false;
     *         for (i in list) {
     *             if (i.equals(e)) {
     *                 condition = true;
     *                 break;
     *             }
     *         }
     *     }
     *     if (condition) {
     *         return yes;
     *     } else {
     *         return no;
     *     }
     * }
     * </pre>
     *
     * will be transformed into:
     *
     * <pre>
     * contains(list, e, yes, no) {
     *     if (list == null || e == null) {
     *         return no;
     *     } else {
     *         condition = false;
     *         for (i in list) {
     *             if (i.equals(e)) {
     *                 return yes;
     *             }
     *         }
     *         return no;
     *     }
     * }
     * </pre>
     *
     * @return true if a transformation was made, false otherwise
     */
    private boolean removeIntermediateMaterialization(SimplifierTool tool) {
        if (!(predecessor() instanceof AbstractMergeNode) || predecessor() instanceof LoopBeginNode) {
            return false;
        }
        AbstractMergeNode merge = (AbstractMergeNode) predecessor();

        if (!(condition() instanceof CompareNode)) {
            return false;
        }

        CompareNode compare = (CompareNode) condition();
        if (compare.getUsageCount() != 1) {
            return false;
        }

        // Only consider merges with a single usage that is both a phi and an operand of the
        // comparison
        NodeIterable<Node> mergeUsages = merge.usages();
        if (mergeUsages.count() != 1) {
            return false;
        }
        Node singleUsage = mergeUsages.first();
        if (!(singleUsage instanceof ValuePhiNode) || (singleUsage != compare.getX() && singleUsage != compare.getY())) {
            return false;
        }

        if (trueSuccessor().isUsedAsGuardInput() || falseSuccessor().isUsedAsGuardInput()) {
            return false;
        }

        // Ensure phi is used by at most the comparison and the merge's frame state (if any)
        ValuePhiNode phi = (ValuePhiNode) singleUsage;
        NodeIterable<Node> phiUsages = phi.usages();
        for (Node usage : phiUsages) {
            if (usage == compare) {
                continue;
            }
            if (usage == merge.stateAfter()) {
                continue;
            }
            // Checkstyle: stop
            // @formatter:off
            //
            // We also want to allow the usage to be on the loop-proxy if one of the branches is a
            // loop exit.
            //
            // This pattern:
            //
            //      if------->cond
            //     /  \
            // begin  begin
            //   |      |
            //  end    end        C1 V2
            //     \  /            \ /
            //     merge---------->phi<------    C1
            //       |              ^        \  /
            //       if-------------|-------->==
            //      /  \            |
            //     A    B<--------Proxy
            //
            // Must be simplified to:
            //
            //       if---------------------->cond
            //      /  \
            //     A    B<--------Proxy------>V2
            //
            // @formatter:on
            // Checkstyle: resume
            if (usage instanceof ValueProxyNode) {
                ValueProxyNode proxy = (ValueProxyNode) usage;
                if (proxy.proxyPoint() == trueSuccessor || proxy.proxyPoint() == falseSuccessor) {
                    continue;
                }
            }
            return false;
        }

        List<EndNode> mergePredecessors = merge.cfgPredecessors().snapshot();
        assert phi.valueCount() == merge.forwardEndCount();

        Constant[] xs = constantValues(compare.getX(), merge, false);
        Constant[] ys = constantValues(compare.getY(), merge, false);
        if (xs == null || ys == null) {
            return false;
        }

        if (merge.stateAfter() != null && !GraphUtil.mayRemoveSplit(this)) {
            return false;
        }

        boolean[] conditions = new boolean[xs.length];
        Stamp compareStamp = compare.getX().stamp(NodeView.DEFAULT);
        for (int i = 0; i < xs.length; i++) {
            TriState foldedCondition = compare.condition().foldCondition(compareStamp, xs[i], ys[i], tool.getConstantReflection(), compare.unorderedIsTrue());
            if (foldedCondition.isUnknown()) {
                return false;
            } else {
                conditions[i] = foldedCondition.toBoolean();
            }
        }

        List<EndNode> falseEnds = new ArrayList<>(mergePredecessors.size());
        List<EndNode> trueEnds = new ArrayList<>(mergePredecessors.size());
        EconomicMap<AbstractEndNode, ValueNode> phiValues = EconomicMap.create(Equivalence.IDENTITY, mergePredecessors.size());

        AbstractBeginNode oldFalseSuccessor = falseSuccessor();
        AbstractBeginNode oldTrueSuccessor = trueSuccessor();

        setFalseSuccessor(null);
        setTrueSuccessor(null);

        Iterator<EndNode> ends = mergePredecessors.iterator();
        for (int i = 0; i < xs.length; i++) {
            EndNode end = ends.next();
            phiValues.put(end, phi.valueAt(end));
            if (conditions[i]) {
                trueEnds.add(end);
            } else {
                falseEnds.add(end);
            }
        }
        assert !ends.hasNext();
        assert falseEnds.size() + trueEnds.size() == xs.length;

        if (this.getTrueSuccessorProbability() == 0.0) {
            for (AbstractEndNode endNode : trueEnds) {
                propagateZeroProbability(endNode);
            }
        }

        if (this.getTrueSuccessorProbability() == 1.0) {
            for (AbstractEndNode endNode : falseEnds) {
                propagateZeroProbability(endNode);
            }
        }

        if (this.getProfileData().getProfileSource().isInjected()) {
            // Attempt to propagate the injected profile to predecessor if without a profile.
            propagateInjectedProfile(this.getProfileData(), trueEnds, falseEnds);
        }

        connectEnds(falseEnds, phi, phiValues, oldFalseSuccessor, merge, tool);
        connectEnds(trueEnds, phi, phiValues, oldTrueSuccessor, merge, tool);

        /*
         * Remove obsolete ends only after processing all ends, otherwise oldTrueSuccessor or
         * oldFalseSuccessor might have been removed if it is a LoopExitNode.
         */
        if (falseEnds.isEmpty()) {
            GraphUtil.killCFG(oldFalseSuccessor);
        }
        if (trueEnds.isEmpty()) {
            GraphUtil.killCFG(oldTrueSuccessor);
        }
        GraphUtil.killCFG(merge);

        assert !merge.isAlive() : merge;
        assert !phi.isAlive() : phi;
        assert !compare.isAlive() : compare;
        assert !this.isAlive() : this;

        return true;
    }

    private static void propagateZeroProbability(FixedNode startNode) {
        Node prev = null;
        for (FixedNode node : GraphUtil.predecessorIterable(startNode)) {
            if (node instanceof IfNode) {
                IfNode ifNode = (IfNode) node;
                if (ifNode.trueSuccessor() == prev) {
                    if (ifNode.getTrueSuccessorProbability() == 0.0) {
                        return;
                    } else if (ifNode.getTrueSuccessorProbability() == 1.0) {
                        continue;
                    } else {
                        ifNode.setTrueSuccessorProbability(BranchProbabilityNode.NEVER_TAKEN_PROFILE);
                        return;
                    }
                } else if (ifNode.falseSuccessor() == prev) {
                    if (ifNode.getTrueSuccessorProbability() == 1.0) {
                        return;
                    } else if (ifNode.getTrueSuccessorProbability() == 0.0) {
                        continue;
                    } else {
                        ifNode.setTrueSuccessorProbability(BranchProbabilityNode.ALWAYS_TAKEN_PROFILE);
                        return;
                    }
                } else {
                    throw new GraalError("Illegal state");
                }
            } else if (node instanceof AbstractMergeNode && !(node instanceof LoopBeginNode)) {
                for (AbstractEndNode endNode : ((AbstractMergeNode) node).cfgPredecessors()) {
                    propagateZeroProbability(endNode);
                }
                return;
            }
            prev = node;
        }
    }

    /**
     * Try to propagate the injected branch probability of the to-be-removed if to a preceding if
     * node with an unknown branch probability that is connected to the merge preceding the if
     * without any other control flow merges in between. The if node must not be ambiguous.
     *
     * Prerequisite: at least one true end and at least one false end, and either exactly one true
     * end or exactly one false end or both.
     *
     * Simple case: Exactly one true and and one false end in the merge and both ends need to lead
     * to a common predecessor if without merges in between.
     *
     * More general case: there can be merges/ifs in one of the two branches, in which case we would
     * disregard that branch and only look for the predecessor if in the other, merge-less branch:
     *
     * <pre>
     *       if[unknown]---->cond
     *      /    \
     *  begin    begin
     *    |        |
     *    |  [     if      ]
     *    |  [    /  \     ]
     *    |  [ begin begin ]
     *    |  [   |     |   ]
     *    |  [  end   end  ]
     *    |  [   \  /      ]
     *    |  [    merge    ]
     *    |        |
     * trueEnd falseEnd    C1 C0
     *      \  /            \ /
     *      merge---------->phi
     *        |              \
     *      if[injected]----> == C1
     *      /    \
     * trueSucc falseSucc
     * </pre>
     *
     * There can also be either multiple true ends or multiple false ends (but not both). Consider
     * the following example:
     *
     * <pre>
     *      if[unknown]------->cond
     *      /      \
     * falseBegin trueBegin
     *      |        \
     *      if        +
     *     /  \       |
     *  begin begin   |
     *    |...  |...  |
     *    |     |     |
     *   end   end    |
     *     \  /       |
     *     merge      |
     *       |        |
     *      if        |
     *     /  \       |
     *  begin begin   |
     *    |... |...   +
     *    |    |     /
     *  TEnd TEnd  FEnd     C1 C1 C0
     *     \  |   /          \ | /
     *      merge ----------->phi
     *       |                 \
     *      if[injected]------> == C1
     *      /    \
     * trueSucc falseSucc
     * </pre>
     *
     * Here the false successor of the bottom if is rewired through the single false end to the true
     * begin of the top if, while the true successor sticks with the leftover true ends of the
     * merge. We propagate the injected profile from the bottom if to the top if, but because the
     * false successor of the former is wired to the true successor of the latter, we need to invert
     * the branch probability.
     *
     * @param profile the injected {@link BranchProbabilityData profile} to propagate.
     * @param trueEnds merge ends where the if condition is true
     * @param falseEnds merge ends where the if condition is false
     */
    private static void propagateInjectedProfile(BranchProbabilityData profile, List<EndNode> trueEnds, List<EndNode> falseEnds) {
        if (trueEnds.size() >= 1 && falseEnds.size() >= 1 && (trueEnds.size() == 1 || falseEnds.size() == 1)) {
            EndNode singleTrueEnd = trueEnds.size() == 1 ? trueEnds.get(0) : null;
            EndNode singleFalseEnd = falseEnds.size() == 1 ? falseEnds.get(0) : null;
            propagateInjectedProfile(profile, singleTrueEnd, singleFalseEnd);
        }
    }

    /**
     * Try to propagate injected branch probability to a predecessor if.
     *
     * @param profile the injected {@link BranchProbabilityData profile} to propagate.
     * @param singleTrueEnd single true condition merge end or null
     * @param singleFalseEnd single false condition merge end or null
     */
    private static void propagateInjectedProfile(BranchProbabilityData profile, EndNode singleTrueEnd, EndNode singleFalseEnd) {
        IfNode foundIf = null;
        FixedNode prev = null;
        boolean viaFalseEnd = false;
        if (singleTrueEnd != null) {
            for (FixedNode node : GraphUtil.predecessorIterable(singleTrueEnd)) {
                if (node instanceof IfNode) {
                    if (!ProfileSource.isTrusted(((IfNode) node).profileSource())) {
                        foundIf = (IfNode) node;
                    }
                    break;
                } else if (node instanceof AbstractMergeNode || node instanceof LoopExitNode) {
                    break;
                }
                prev = node;
            }
        }
        if (singleFalseEnd != null) {
            FixedNode falsePrev = null;
            for (FixedNode node : GraphUtil.predecessorIterable(singleFalseEnd)) {
                if (node instanceof IfNode) {
                    if (foundIf == node) {
                        // found same if node through true and false end
                        break;
                    } else if (foundIf == null) {
                        // found if node only through false end
                        foundIf = (IfNode) node;
                        prev = falsePrev;
                        viaFalseEnd = true;
                        break;
                    } else {
                        // found different if nodes, abort
                        return;
                    }
                } else if (node instanceof AbstractMergeNode || node instanceof LoopExitNode) {
                    break;
                }
                falsePrev = node;
            }
        }

        if (foundIf != null && !ProfileSource.isTrusted(foundIf.profileSource())) {
            boolean negated;
            if (foundIf.trueSuccessor() == prev) {
                negated = viaFalseEnd;
            } else if (foundIf.falseSuccessor() == prev) {
                negated = !viaFalseEnd;
            } else {
                throw new GraalError("Illegal state");
            }
            foundIf.setTrueSuccessorProbability(negated ? profile.negated() : profile);
        }
    }

    /**
     * Connects a set of ends to a given successor, inserting a merge node if there is more than one
     * end. If {@code ends} is not empty, then {@code successor} is added to {@code tool}'s
     * {@linkplain SimplifierTool#addToWorkList(org.graalvm.compiler.graph.Node) work list}.
     *
     * @param phi the original single-usage phi of the preceding merge
     * @param phiValues the values of the phi at the merge, keyed by the merge ends
     * @param oldMerge the merge being removed
     */
    private void connectEnds(List<EndNode> ends, ValuePhiNode phi, EconomicMap<AbstractEndNode, ValueNode> phiValues, AbstractBeginNode successor, AbstractMergeNode oldMerge, SimplifierTool tool) {
        if (!ends.isEmpty()) {
            // If there was a value proxy usage, then the proxy needs a new value.
            ValueProxyNode valueProxy = null;
            if (successor instanceof LoopExitNode) {
                for (Node usage : phi.usages()) {
                    if (usage instanceof ValueProxyNode && ((ValueProxyNode) usage).proxyPoint() == successor) {
                        valueProxy = (ValueProxyNode) usage;
                    }
                }
            }
            final ValueProxyNode proxy = valueProxy;
            if (ends.size() == 1) {
                AbstractEndNode end = ends.get(0);
                if (proxy != null) {
                    phi.replaceAtUsages(phiValues.get(end), n -> n == proxy);
                }
                ((FixedWithNextNode) end.predecessor()).setNext(successor);
                oldMerge.removeEnd(end);
                GraphUtil.killCFG(end);
            } else {
                // Need a new phi in case the frame state is used by more than the merge being
                // removed.
                NodeView view = NodeView.from(tool);
                AbstractMergeNode newMerge = graph().add(new MergeNode());
                PhiNode oldPhi = (PhiNode) oldMerge.usages().first();
                PhiNode newPhi = graph().addWithoutUnique(new ValuePhiNode(oldPhi.stamp(view), newMerge));

                if (proxy != null) {
                    phi.replaceAtUsages(newPhi, n -> n == proxy);
                }

                for (EndNode end : ends) {
                    newPhi.addInput(phiValues.get(end));
                    newMerge.addForwardEnd(end);
                }

                FrameState stateAfter = oldMerge.stateAfter();
                if (stateAfter != null) {
                    stateAfter = stateAfter.duplicateWithVirtualState();
                    stateAfter.applyToNonVirtual(new NodePositionClosure<>() {
                        @Override
                        public void apply(Node from, Position p) {
                            ValueNode to = (ValueNode) p.get(from);
                            if (to == oldPhi) {
                                p.set(from, newPhi);
                            }
                        }
                    });
                    newMerge.setStateAfter(stateAfter);
                }

                newMerge.setNext(successor);
            }
            tool.addToWorkList(successor);
        }
    }

    /**
     * Gets an array of constants derived from a node that is either a {@link ConstantNode} or a
     * {@link PhiNode} whose input values are all constants. The length of the returned array is
     * equal to the number of ends terminating in a given merge node.
     *
     * @return null if {@code node} is neither a {@link ConstantNode} nor a {@link PhiNode} whose
     *         input values are all constants
     */
    public static Constant[] constantValues(ValueNode node, AbstractMergeNode merge, boolean allowNull) {
        if (node.isConstant()) {
            Constant[] result = new Constant[merge.forwardEndCount()];
            Arrays.fill(result, node.asConstant());
            return result;
        }

        if (node instanceof PhiNode) {
            PhiNode phi = (PhiNode) node;
            if (phi.merge() == merge && phi instanceof ValuePhiNode && phi.valueCount() == merge.forwardEndCount()) {
                Constant[] result = new Constant[merge.forwardEndCount()];
                int i = 0;
                for (ValueNode n : phi.values()) {
                    if (!allowNull && !n.isConstant()) {
                        return null;
                    }
                    result[i++] = n.asConstant();
                }
                return result;
            }
        }

        return null;
    }

    @Override
    public AbstractBeginNode getPrimarySuccessor() {
        return null;
    }

    public AbstractBeginNode getSuccessor(boolean result) {
        return result ? this.trueSuccessor() : this.falseSuccessor();
    }

    @Override
    public boolean setProbability(AbstractBeginNode successor, BranchProbabilityData profileData) {
        if (successor == this.trueSuccessor()) {
            this.setTrueSuccessorProbability(profileData);
            return true;
        } else if (successor == this.falseSuccessor()) {
            this.setTrueSuccessorProbability(profileData.negated());
            return true;
        }
        return false;
    }

    @Override
    public int getSuccessorCount() {
        return 2;
    }

    /**
     * Predict if {@code successor} will be eliminated by the next round of
     * {@linkplain Canonicalizable canonicalization}. A return value {@code false} can indicate that
     * it is statically impossible to predict if the {@code successor} will be eliminated, i.e., it
     * is unknown.
     */
    public boolean successorWillBeEliminated(AbstractBeginNode successor) {
        assert successor == trueSuccessor || successor == falseSuccessor;
        if (condition instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) condition;
            boolean trueSuccessorWillBeEliminated = !c.getValue();
            return trueSuccessorWillBeEliminated ? successor == trueSuccessor : successor == falseSuccessor;
        }
        // unknown
        return false;
    }
}
