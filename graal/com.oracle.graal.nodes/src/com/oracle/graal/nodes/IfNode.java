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
package com.oracle.graal.nodes;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.JavaTypeProfile.ProfiledType;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;

/**
 * The {@code IfNode} represents a branch that can go one of two directions depending on the outcome
 * of a comparison.
 */
@NodeInfo
public final class IfNode extends ControlSplitNode implements Simplifiable, LIRLowerable {
    public static final NodeClass<IfNode> TYPE = NodeClass.create(IfNode.class);

    private static final DebugMetric CORRECTED_PROBABILITIES = Debug.metric("CorrectedProbabilities");

    @Successor AbstractBeginNode trueSuccessor;
    @Successor AbstractBeginNode falseSuccessor;
    @Input(InputType.Condition) LogicNode condition;
    protected double trueSuccessorProbability;

    public LogicNode condition() {
        return condition;
    }

    public void setCondition(LogicNode x) {
        updateUsages(condition, x);
        condition = x;
    }

    public IfNode(LogicNode condition, FixedNode trueSuccessor, FixedNode falseSuccessor, double trueSuccessorProbability) {
        this(condition, BeginNode.begin(trueSuccessor), BeginNode.begin(falseSuccessor), trueSuccessorProbability);
    }

    public IfNode(LogicNode condition, AbstractBeginNode trueSuccessor, AbstractBeginNode falseSuccessor, double trueSuccessorProbability) {
        super(TYPE, StampFactory.forVoid());
        this.condition = condition;
        this.falseSuccessor = falseSuccessor;
        this.trueSuccessor = trueSuccessor;
        setTrueSuccessorProbability(trueSuccessorProbability);
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
        return this.trueSuccessorProbability;
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

    public void setTrueSuccessorProbability(double prob) {
        assert prob >= -0.000000001 && prob <= 1.000000001 : "Probability out of bounds: " + prob;
        trueSuccessorProbability = Math.min(1.0, Math.max(0.0, prob));
    }

    @Override
    public double probability(AbstractBeginNode successor) {
        return successor == trueSuccessor ? trueSuccessorProbability : 1 - trueSuccessorProbability;
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

    @Override
    public void simplify(SimplifierTool tool) {
        if (trueSuccessor().next() instanceof DeoptimizeNode) {
            if (trueSuccessorProbability != 0) {
                CORRECTED_PROBABILITIES.increment();
                trueSuccessorProbability = 0;
            }
        } else if (falseSuccessor().next() instanceof DeoptimizeNode) {
            if (trueSuccessorProbability != 1) {
                CORRECTED_PROBABILITIES.increment();
                trueSuccessorProbability = 1;
            }
        }

        if (condition() instanceof LogicNegationNode) {
            AbstractBeginNode trueSucc = trueSuccessor();
            AbstractBeginNode falseSucc = falseSuccessor();
            setTrueSuccessor(null);
            setFalseSuccessor(null);
            LogicNegationNode negation = (LogicNegationNode) condition();
            IfNode newIfNode = graph().add(new IfNode(negation.getValue(), falseSucc, trueSucc, 1 - trueSuccessorProbability));
            predecessor().replaceFirstSuccessor(this, newIfNode);
            GraphUtil.killWithUnusedFloatingInputs(this);
            return;
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
        if (trueSuccessor().hasNoUsages() && falseSuccessor().hasNoUsages()) {

            pushNodesThroughIf(tool);

            if (checkForUnsignedCompare(tool) || removeOrMaterializeIf(tool)) {
                return;
            }
        }

        if (removeIntermediateMaterialization(tool)) {
            return;
        }

        if (splitIfAtPhi(tool)) {
            return;
        }

        if (falseSuccessor().hasNoUsages() && (!(falseSuccessor() instanceof LoopExitNode)) && falseSuccessor().next() instanceof IfNode) {
            AbstractBeginNode intermediateBegin = falseSuccessor();
            IfNode nextIf = (IfNode) intermediateBegin.next();
            double probabilityB = (1.0 - this.trueSuccessorProbability) * nextIf.trueSuccessorProbability;
            if (this.trueSuccessorProbability < probabilityB) {
                // Reordering of those two if statements is beneficial from the point of view of
                // their probabilities.
                if (prepareForSwap(tool.getConstantReflection(), condition(), nextIf.condition(), this.trueSuccessorProbability, probabilityB)) {
                    // Reordering is allowed from (if1 => begin => if2) to (if2 => begin => if1).
                    assert intermediateBegin.next() == nextIf;
                    AbstractBeginNode bothFalseBegin = nextIf.falseSuccessor();
                    nextIf.setFalseSuccessor(null);
                    intermediateBegin.setNext(null);
                    this.setFalseSuccessor(null);

                    this.replaceAtPredecessor(nextIf);
                    nextIf.setFalseSuccessor(intermediateBegin);
                    intermediateBegin.setNext(this);
                    this.setFalseSuccessor(bothFalseBegin);
                    nextIf.setTrueSuccessorProbability(probabilityB);
                    if (probabilityB == 1.0) {
                        this.setTrueSuccessorProbability(0.0);
                    } else {
                        double newProbability = this.trueSuccessorProbability / (1.0 - probabilityB);
                        this.setTrueSuccessorProbability(Math.min(1.0, newProbability));
                    }
                    return;
                }
            }
        }
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
                    if (nodeClass.getInputEdges().areEqualIn(trueNext, falseNext) && trueNext.valueEquals(falseNext)) {
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
                                        usage.replaceAtUsages(newNode);
                                        usage.safeDelete();
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
    private boolean checkForUnsignedCompare(SimplifierTool tool) {
        assert trueSuccessor().hasNoUsages() && falseSuccessor().hasNoUsages();
        if (condition() instanceof IntegerLessThanNode) {
            IntegerLessThanNode lessThan = (IntegerLessThanNode) condition();
            Constant y = lessThan.getY().stamp().asConstant();
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
                    if (lessThan2.getX() == lessThan.getX() && lessThan2.getY().stamp() instanceof IntegerStamp && ((IntegerStamp) lessThan2.getY().stamp()).isPositive() &&
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
                        if (positive != null && positive.asLong() > 0 && positive.asLong() < positive.getKind().getMaxValue()) {
                            ConstantNode newLimit = ConstantNode.forIntegerStamp(lessThan2.getX().stamp(), positive.asLong() + 1, graph());
                            below = graph().unique(new IntegerBelowNode(lessThan.getX(), newLimit));
                        }
                    }
                    if (below != null) {
                        ifNode2.setTrueSuccessor(null);
                        ifNode2.setFalseSuccessor(null);

                        IfNode newIfNode = graph().add(new IfNode(below, falseSucc, trueSucc, 1 - trueSuccessorProbability));
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
        }
        return false;
    }

    /**
     * Check it these two blocks end up at the same place. Meeting at the same merge, or
     * deoptimizing in the same way.
     */
    private static boolean sameDestination(AbstractBeginNode succ1, AbstractBeginNode succ2) {
        Node next1 = succ1.next();
        Node next2 = succ2.next();
        if (next1 instanceof EndNode && next2 instanceof EndNode) {
            EndNode end1 = (EndNode) next1;
            EndNode end2 = (EndNode) next2;
            if (end1.merge() == end2.merge()) {
                // They go to the same MergeNode
                return true;
            }
        } else if (next1 instanceof DeoptimizeNode && next2 instanceof DeoptimizeNode) {
            DeoptimizeNode deopt1 = (DeoptimizeNode) next1;
            DeoptimizeNode deopt2 = (DeoptimizeNode) next2;
            if (deopt1.reason() == deopt2.reason() && deopt1.action() == deopt2.action()) {
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

    private static final class MutableProfiledType {
        final ResolvedJavaType type;
        double probability;

        public MutableProfiledType(ResolvedJavaType type, double probability) {
            this.type = type;
            this.probability = probability;
        }
    }

    private static boolean prepareForSwap(ConstantReflectionProvider constantReflection, LogicNode a, LogicNode b, double probabilityA, double probabilityB) {
        if (a instanceof InstanceOfNode) {
            InstanceOfNode instanceOfA = (InstanceOfNode) a;
            if (b instanceof IsNullNode) {
                IsNullNode isNullNode = (IsNullNode) b;
                if (isNullNode.getValue() == instanceOfA.getValue()) {
                    if (instanceOfA.profile() != null && instanceOfA.profile().getNullSeen() != TriState.FALSE) {
                        instanceOfA.setProfile(new JavaTypeProfile(TriState.FALSE, instanceOfA.profile().getNotRecordedProbability(), instanceOfA.profile().getTypes()));
                    }
                    Debug.log("Can swap instanceof and isnull if");
                    return true;
                }
            } else if (b instanceof InstanceOfNode) {
                InstanceOfNode instanceOfB = (InstanceOfNode) b;
                if (instanceOfA.getValue() == instanceOfB.getValue() && !instanceOfA.type().isInterface() && !instanceOfB.type().isInterface() &&
                                !instanceOfA.type().isAssignableFrom(instanceOfB.type()) && !instanceOfB.type().isAssignableFrom(instanceOfA.type())) {
                    // Two instanceof on the same value with mutually exclusive types.
                    Debug.log("Can swap instanceof for types %s and %s", instanceOfA.type(), instanceOfB.type());
                    swapInstanceOfProfiles(probabilityA, probabilityB, instanceOfA, instanceOfB);
                    return true;
                }
            }
        } else if (a instanceof CompareNode) {
            CompareNode compareA = (CompareNode) a;
            Condition conditionA = compareA.condition();
            if (compareA.unorderedIsTrue()) {
                return false;
            }
            if (b instanceof CompareNode) {
                CompareNode compareB = (CompareNode) b;
                if (compareA == compareB) {
                    Debug.log("Same conditions => do not swap and leave the work for global value numbering.");
                    return false;
                }
                if (compareB.unorderedIsTrue()) {
                    return false;
                }
                Condition comparableCondition = null;
                Condition conditionB = compareB.condition();
                if (compareB.getX() == compareA.getX() && compareB.getY() == compareA.getY()) {
                    comparableCondition = conditionB;
                } else if (compareB.getX() == compareA.getY() && compareB.getY() == compareA.getX()) {
                    comparableCondition = conditionB.mirror();
                }

                if (comparableCondition != null) {
                    Condition combined = conditionA.join(comparableCondition);
                    if (combined == null) {
                        // The two conditions are disjoint => can reorder.
                        Debug.log("Can swap disjoint coditions on same values: %s and %s", conditionA, comparableCondition);
                        return true;
                    }
                } else if (conditionA == Condition.EQ && conditionB == Condition.EQ) {
                    boolean canSwap = false;
                    if ((compareA.getX() == compareB.getX() && valuesDistinct(constantReflection, compareA.getY(), compareB.getY()))) {
                        canSwap = true;
                    } else if ((compareA.getX() == compareB.getY() && valuesDistinct(constantReflection, compareA.getY(), compareB.getX()))) {
                        canSwap = true;
                    } else if ((compareA.getY() == compareB.getX() && valuesDistinct(constantReflection, compareA.getX(), compareB.getY()))) {
                        canSwap = true;
                    } else if ((compareA.getY() == compareB.getY() && valuesDistinct(constantReflection, compareA.getX(), compareB.getX()))) {
                        canSwap = true;
                    }

                    if (canSwap) {
                        Debug.log("Can swap equality condition with one shared and one disjoint value.");
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Arbitrary fraction of not recorded types that we'll guess are sub-types of B.
     *
     * This is is used because we can not check if the unrecorded types are sub-types of B or not.
     */
    private static final double NOT_RECORDED_SUBTYPE_B = 0.5;

    /**
     * If the not-recorded fraction of types for the new profile of <code>instanceOfA</code> is
     * above this threshold, no profile is used for this <code>instanceof</code> after the swap.
     *
     * The idea is that the reconstructed profile would contain too much unknowns to be of any use.
     */
    private static final double NOT_RECORDED_CUTOFF = 0.4;

    /**
     * Tries to reconstruct profiles for the swapped <code>instanceof</code> checks.
     *
     * The tested types must be mutually exclusive.
     */
    private static void swapInstanceOfProfiles(double probabilityA, double probabilityB, InstanceOfNode instanceOfA, InstanceOfNode instanceOfB) {
        JavaTypeProfile profileA = instanceOfA.profile();
        JavaTypeProfile profileB = instanceOfB.profile();

        JavaTypeProfile newProfileA = null;
        JavaTypeProfile newProfileB = null;
        if (profileA != null || profileB != null) {
            List<MutableProfiledType> newProfiledTypesA = new ArrayList<>();
            List<MutableProfiledType> newProfiledTypesB = new ArrayList<>();
            double totalProbabilityA = 0.0;
            double totalProbabilityB = 0.0;
            double newNotRecordedA = 0.0;
            double newNotRecordedB = 0.0;
            TriState nullSeen = TriState.UNKNOWN;
            if (profileA != null) {
                for (ProfiledType profiledType : profileA.getTypes()) {
                    newProfiledTypesB.add(new MutableProfiledType(profiledType.getType(), profiledType.getProbability()));
                    totalProbabilityB += profiledType.getProbability();
                    if (!instanceOfB.type().isAssignableFrom(profiledType.getType())) {
                        double typeProbabilityA = profiledType.getProbability() / (1 - probabilityB);
                        newProfiledTypesA.add(new MutableProfiledType(profiledType.getType(), typeProbabilityA));
                        totalProbabilityA += typeProbabilityA;
                    }
                }
                newNotRecordedB += profileA.getNotRecordedProbability();
                newNotRecordedA += NOT_RECORDED_SUBTYPE_B * profileA.getNotRecordedProbability() / (1 - probabilityB);
                nullSeen = profileA.getNullSeen();
            }
            int searchA = newProfiledTypesA.size();
            int searchB = newProfiledTypesB.size();
            if (profileB != null) {
                for (ProfiledType profiledType : profileB.getTypes()) {
                    double typeProbabilityB = profiledType.getProbability() * (1 - probabilityA);
                    appendOrMerge(profiledType.getType(), typeProbabilityB, searchB, newProfiledTypesB);
                    totalProbabilityB += typeProbabilityB;
                    if (!instanceOfB.type().isAssignableFrom(profiledType.getType())) {
                        double typeProbabilityA = typeProbabilityB / (1 - probabilityB);
                        appendOrMerge(profiledType.getType(), typeProbabilityA, searchA, newProfiledTypesA);
                        totalProbabilityA += typeProbabilityA;
                    }
                }
                newNotRecordedB += profileB.getNotRecordedProbability() * (1 - probabilityA);
                newNotRecordedA += NOT_RECORDED_SUBTYPE_B * profileB.getNotRecordedProbability() * (1 - probabilityA) / (1 - probabilityB);
                nullSeen = TriState.merge(nullSeen, profileB.getNullSeen());
            }
            totalProbabilityA += newNotRecordedA;
            totalProbabilityB += newNotRecordedB;

            if (newNotRecordedA / NOT_RECORDED_SUBTYPE_B > NOT_RECORDED_CUTOFF) {
                // too much unknown
                newProfileA = null;
            } else {
                newProfileA = makeProfile(totalProbabilityA, newNotRecordedA, newProfiledTypesA, nullSeen);
            }
            newProfileB = makeProfile(totalProbabilityB, newNotRecordedB, newProfiledTypesB, nullSeen);
        }

        instanceOfB.setProfile(newProfileB);
        instanceOfA.setProfile(newProfileA);
    }

    private static JavaTypeProfile makeProfile(double totalProbability, double notRecorded, List<MutableProfiledType> profiledTypes, TriState nullSeen) {
        // protect against NaNs and empty profiles
        if (totalProbability > 0.0) {
            // normalize
            ProfiledType[] profiledTypesArray = new ProfiledType[profiledTypes.size()];
            int i = 0;
            for (MutableProfiledType profiledType : profiledTypes) {
                profiledTypesArray[i++] = new ProfiledType(profiledType.type, profiledType.probability / totalProbability);
            }

            // sort
            Arrays.sort(profiledTypesArray);

            return new JavaTypeProfile(nullSeen, notRecorded / totalProbability, profiledTypesArray);
        }
        return null;
    }

    private static void appendOrMerge(ResolvedJavaType type, double probability, int searchUntil, List<MutableProfiledType> list) {
        for (int i = 0; i < searchUntil; i++) {
            MutableProfiledType profiledType = list.get(i);
            if (profiledType.type.equals(type)) {
                profiledType.probability += probability;
                return;
            }
        }
        list.add(new MutableProfiledType(type, probability));
    }

    private static boolean valuesDistinct(ConstantReflectionProvider constantReflection, ValueNode a, ValueNode b) {
        if (a.isConstant() && b.isConstant()) {
            Boolean equal = constantReflection.constantEquals(a.asConstant(), b.asConstant());
            if (equal != null) {
                return !equal.booleanValue();
            }
        }

        Stamp stampA = a.stamp();
        Stamp stampB = b.stamp();
        return stampA.alwaysDistinct(stampB);
    }

    /**
     * Tries to remove an empty if construct or replace an if construct with a materialization.
     *
     * @return true if a transformation was made, false otherwise
     */
    private boolean removeOrMaterializeIf(SimplifierTool tool) {
        assert trueSuccessor().hasNoUsages() && falseSuccessor().hasNoUsages();
        if (trueSuccessor().next() instanceof AbstractEndNode && falseSuccessor().next() instanceof AbstractEndNode) {
            AbstractEndNode trueEnd = (AbstractEndNode) trueSuccessor().next();
            AbstractEndNode falseEnd = (AbstractEndNode) falseSuccessor().next();
            AbstractMergeNode merge = trueEnd.merge();
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
                    removeThroughFalseBranch(tool);
                    return true;
                } else if (distinct == 1) {
                    ValueNode trueValue = singlePhi.valueAt(trueEnd);
                    ValueNode falseValue = singlePhi.valueAt(falseEnd);
                    ConditionalNode conditional = canonicalizeConditionalCascade(trueValue, falseValue);
                    if (conditional != null) {
                        singlePhi.setValueAt(trueEnd, conditional);
                        removeThroughFalseBranch(tool);
                        return true;
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
            if (trueValue != null) {
                if (trueValue == falseValue) {
                    value = trueValue;
                } else {
                    value = canonicalizeConditionalCascade(trueValue, falseValue);
                    if (value == null) {
                        return false;
                    }
                }
            }
            ReturnNode newReturn = graph().add(new ReturnNode(value));
            replaceAtPredecessor(newReturn);
            GraphUtil.killCFG(this);
            return true;
        }
        return false;
    }

    protected void removeThroughFalseBranch(SimplifierTool tool) {
        AbstractBeginNode trueBegin = trueSuccessor();
        graph().removeSplitPropagate(this, trueBegin, tool);
        tool.addToWorkList(trueBegin);
        if (condition().isAlive() && condition().hasNoUsages()) {
            GraphUtil.killWithUnusedFloatingInputs(condition());
        }
    }

    private ConditionalNode canonicalizeConditionalCascade(ValueNode trueValue, ValueNode falseValue) {
        if (trueValue.getKind() != falseValue.getKind()) {
            return null;
        }
        if (trueValue.getKind() != Kind.Int && trueValue.getKind() != Kind.Long) {
            return null;
        }
        if (trueValue.isConstant() && falseValue.isConstant()) {
            return graph().unique(new ConditionalNode(condition(), trueValue, falseValue));
        } else {
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
            boolean negateConditionalCondition;
            ValueNode otherValue;
            if (constant == conditional.trueValue()) {
                otherValue = conditional.falseValue();
                negateConditionalCondition = false;
            } else if (constant == conditional.falseValue()) {
                otherValue = conditional.trueValue();
                negateConditionalCondition = true;
            } else {
                return null;
            }
            if (otherValue.isConstant()) {
                double shortCutProbability = probability(trueSuccessor());
                LogicNode newCondition = LogicNode.or(condition(), negateCondition, conditional.condition(), negateConditionalCondition, shortCutProbability);
                return graph().unique(new ConditionalNode(newCondition, constant, otherValue));
            }
        }
        return null;
    }

    /**
     * Take an if that is immediately dominated by a merge with a single phi and split off any paths
     * where the test would be statically decidable creating a new merge below the approriate side
     * of the IfNode. Any undecidable tests will continue to use the original IfNode.
     *
     * @param tool
     */
    @SuppressWarnings("unchecked")
    private boolean splitIfAtPhi(SimplifierTool tool) {
        if (!(predecessor() instanceof MergeNode)) {
            return false;
        }
        MergeNode merge = (MergeNode) predecessor();
        if (merge.forwardEndCount() == 1) {
            // Don't bother.
            return false;
        }
        if (merge.usages().count() != 1 || merge.phis().count() != 1) {
            return false;
        }
        if (merge.stateAfter() != null) {
            /* We'll get the chance to simplify this after frame state assignment. */
            return false;
        }
        PhiNode phi = merge.phis().first();
        if (phi.usages().count() != 1 || condition().usages().count() != 1) {
            /*
             * For simplicity the below code assumes assumes the phi goes dead at the end so skip
             * this case.
             */
            return false;
        }

        if (condition() instanceof Canonicalizable.Unary<?>) {
            Canonicalizable.Unary<?> unary = (Canonicalizable.Unary<?>) condition();
            if (unary.getValue() != phi) {
                return false;
            }
        } else if (condition() instanceof Canonicalizable.Binary<?>) {
            Canonicalizable.Binary<?> binary = (Canonicalizable.Binary<?>) condition();
            if (binary.getX() != phi && binary.getY() != phi) {
                return false;
            }
        } else {
            return false;
        }

        /*
         * We could additionally filter for the case that at least some of the Phi inputs or one of
         * the condition inputs are constants but there are cases where a non-constant is
         * simplifiable, usually where the stamp allows the question to be answered.
         */

        /* Each successor of the if gets a new merge if needed. */
        MergeNode trueMerge = null;
        MergeNode falseMerge = null;
        assert merge.stateAfter() == null;

        for (EndNode end : merge.forwardEnds().snapshot()) {
            Node value = phi.valueAt(end);
            Node result = null;
            if (condition() instanceof Canonicalizable.Binary<?>) {
                Canonicalizable.Binary<Node> compare = (Canonicalizable.Binary<Node>) condition;
                if (compare.getX() == phi) {
                    result = compare.canonical(tool, value, compare.getY());
                } else {
                    result = compare.canonical(tool, compare.getX(), value);
                }
            } else {
                assert condition() instanceof Canonicalizable.Unary<?>;
                Canonicalizable.Unary<Node> compare = (Canonicalizable.Unary<Node>) condition;
                result = compare.canonical(tool, value);
            }
            if (result instanceof LogicConstantNode) {
                merge.removeEnd(end);
                if (((LogicConstantNode) result).getValue()) {
                    if (trueMerge == null) {
                        trueMerge = insertMerge(trueSuccessor());
                    }
                    trueMerge.addForwardEnd(end);
                } else {
                    if (falseMerge == null) {
                        falseMerge = insertMerge(falseSuccessor());
                    }
                    falseMerge.addForwardEnd(end);
                }
            }
        }
        transferProxies(trueSuccessor(), trueMerge);
        transferProxies(falseSuccessor(), falseMerge);

        cleanupMerge(tool, merge);
        cleanupMerge(tool, trueMerge);
        cleanupMerge(tool, falseMerge);

        return true;
    }

    private static void transferProxies(AbstractBeginNode successor, MergeNode falseMerge) {
        if (falseMerge != null) {
            for (ProxyNode proxy : successor.proxies().snapshot()) {
                proxy.replaceFirstInput(successor, falseMerge);
            }
        }
    }

    private void cleanupMerge(SimplifierTool tool, MergeNode merge) {
        if (merge != null && merge.isAlive()) {
            if (merge.forwardEndCount() == 0) {
                GraphUtil.killCFG(merge, tool);
            } else if (merge.forwardEndCount() == 1) {
                graph().reduceTrivialMerge(merge);
            }
        }
    }

    private MergeNode insertMerge(AbstractBeginNode begin) {
        MergeNode merge = graph().add(new MergeNode());
        if (!begin.anchored().isEmpty()) {
            Object before = null;
            before = begin.anchored().snapshot();
            begin.replaceAtUsages(InputType.Guard, merge);
            begin.replaceAtUsages(InputType.Anchor, merge);
            assert begin.anchored().isEmpty() : before + " " + begin.anchored().snapshot();
        }

        AbstractBeginNode theBegin = begin;
        if (begin instanceof LoopExitNode) {
            // Insert an extra begin to make it easier.
            theBegin = graph().add(new BeginNode());
            begin.replaceAtPredecessor(theBegin);
            theBegin.setNext(begin);
        }
        FixedNode next = theBegin.next();
        next.replaceAtPredecessor(merge);
        theBegin.setNext(graph().add(new EndNode()));
        merge.addForwardEnd((EndNode) theBegin.next());
        merge.setNext(next);
        return merge;
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

        // Ensure phi is used by at most the comparison and the merge's frame state (if any)
        ValuePhiNode phi = (ValuePhiNode) singleUsage;
        NodeIterable<Node> phiUsages = phi.usages();
        if (phiUsages.count() > 2) {
            return false;
        }
        for (Node usage : phiUsages) {
            if (usage != compare && usage != merge.stateAfter()) {
                return false;
            }
        }

        List<EndNode> mergePredecessors = merge.cfgPredecessors().snapshot();
        assert phi.valueCount() == merge.forwardEndCount();

        Constant[] xs = constantValues(compare.getX(), merge, false);
        Constant[] ys = constantValues(compare.getY(), merge, false);
        if (xs == null || ys == null) {
            return false;
        }

        // Sanity check that both ends are not followed by a merge without frame state.
        if (!checkFrameState(trueSuccessor()) && !checkFrameState(falseSuccessor())) {
            return false;
        }

        List<EndNode> falseEnds = new ArrayList<>(mergePredecessors.size());
        List<EndNode> trueEnds = new ArrayList<>(mergePredecessors.size());
        Map<AbstractEndNode, ValueNode> phiValues = CollectionsFactory.newMap(mergePredecessors.size());

        AbstractBeginNode oldFalseSuccessor = falseSuccessor();
        AbstractBeginNode oldTrueSuccessor = trueSuccessor();

        setFalseSuccessor(null);
        setTrueSuccessor(null);

        Iterator<EndNode> ends = mergePredecessors.iterator();
        for (int i = 0; i < xs.length; i++) {
            EndNode end = ends.next();
            phiValues.put(end, phi.valueAt(end));
            if (compare.condition().foldCondition(xs[i], ys[i], tool.getConstantReflection(), compare.unorderedIsTrue())) {
                trueEnds.add(end);
            } else {
                falseEnds.add(end);
            }
        }
        assert !ends.hasNext();
        assert falseEnds.size() + trueEnds.size() == xs.length;

        connectEnds(falseEnds, phiValues, oldFalseSuccessor, merge, tool);
        connectEnds(trueEnds, phiValues, oldTrueSuccessor, merge, tool);

        if (this.trueSuccessorProbability == 0.0) {
            for (AbstractEndNode endNode : trueEnds) {
                propagateZeroProbability(endNode);
            }
        }

        if (this.trueSuccessorProbability == 1.0) {
            for (AbstractEndNode endNode : falseEnds) {
                propagateZeroProbability(endNode);
            }
        }

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

    private void propagateZeroProbability(FixedNode startNode) {
        Node prev = null;
        for (FixedNode node : GraphUtil.predecessorIterable(startNode)) {
            if (node instanceof IfNode) {
                IfNode ifNode = (IfNode) node;
                if (ifNode.trueSuccessor() == prev) {
                    if (ifNode.trueSuccessorProbability == 0.0) {
                        return;
                    } else if (ifNode.trueSuccessorProbability == 1.0) {
                        continue;
                    } else {
                        ifNode.setTrueSuccessorProbability(0.0);
                        return;
                    }
                } else if (ifNode.falseSuccessor() == prev) {
                    if (ifNode.trueSuccessorProbability == 1.0) {
                        return;
                    } else if (ifNode.trueSuccessorProbability == 0.0) {
                        continue;
                    } else {
                        ifNode.setTrueSuccessorProbability(1.0);
                        return;
                    }
                } else {
                    throw new GraalInternalError("Illegal state");
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

    private static boolean checkFrameState(FixedNode start) {
        FixedNode node = start;
        while (true) {
            if (node instanceof AbstractMergeNode) {
                AbstractMergeNode mergeNode = (AbstractMergeNode) node;
                if (mergeNode.stateAfter() == null) {
                    return false;
                } else {
                    return true;
                }
            } else if (node instanceof StateSplit) {
                StateSplit stateSplitNode = (StateSplit) node;
                if (stateSplitNode.stateAfter() != null) {
                    return true;
                }
            }

            if (node instanceof ControlSplitNode) {
                ControlSplitNode controlSplitNode = (ControlSplitNode) node;
                for (Node succ : controlSplitNode.cfgSuccessors()) {
                    if (checkFrameState((FixedNode) succ)) {
                        return true;
                    }
                }
                return false;
            } else if (node instanceof FixedWithNextNode) {
                FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) node;
                node = fixedWithNextNode.next();
            } else if (node instanceof AbstractEndNode) {
                AbstractEndNode endNode = (AbstractEndNode) node;
                node = endNode.merge();
            } else if (node instanceof ControlSinkNode) {
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Connects a set of ends to a given successor, inserting a merge node if there is more than one
     * end. If {@code ends} is not empty, then {@code successor} is added to {@code tool}'s
     * {@linkplain SimplifierTool#addToWorkList(com.oracle.graal.graph.Node) work list}.
     *
     * @param oldMerge the merge being removed
     * @param phiValues the values of the phi at the merge, keyed by the merge ends
     */
    private void connectEnds(List<EndNode> ends, Map<AbstractEndNode, ValueNode> phiValues, AbstractBeginNode successor, AbstractMergeNode oldMerge, SimplifierTool tool) {
        if (!ends.isEmpty()) {
            if (ends.size() == 1) {
                AbstractEndNode end = ends.get(0);
                ((FixedWithNextNode) end.predecessor()).setNext(successor);
                oldMerge.removeEnd(end);
                GraphUtil.killCFG(end);
            } else {
                // Need a new phi in case the frame state is used by more than the merge being
                // removed
                AbstractMergeNode newMerge = graph().add(new MergeNode());
                PhiNode oldPhi = (PhiNode) oldMerge.usages().first();
                PhiNode newPhi = graph().addWithoutUnique(new ValuePhiNode(oldPhi.stamp(), newMerge));

                for (EndNode end : ends) {
                    newPhi.addInput(phiValues.get(end));
                    newMerge.addForwardEnd(end);
                }

                FrameState stateAfter = oldMerge.stateAfter();
                if (stateAfter != null) {
                    stateAfter = stateAfter.duplicate();
                    stateAfter.replaceFirstInput(oldPhi, newPhi);
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
            JavaConstant[] result = new JavaConstant[merge.forwardEndCount()];
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
        return this.trueSuccessor();
    }

    public AbstractBeginNode getSuccessor(boolean result) {
        return result ? this.trueSuccessor() : this.falseSuccessor();
    }
}
