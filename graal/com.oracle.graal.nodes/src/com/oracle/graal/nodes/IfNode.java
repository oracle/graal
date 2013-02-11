/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;

/**
 * The {@code IfNode} represents a branch that can go one of two directions depending on the outcome
 * of a comparison.
 */
public final class IfNode extends ControlSplitNode implements Simplifiable, LIRLowerable, Negatable {

    @Successor private BeginNode trueSuccessor;
    @Successor private BeginNode falseSuccessor;
    @Input private LogicNode condition;
    private double trueSuccessorProbability;

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

    public IfNode(LogicNode condition, BeginNode trueSuccessor, BeginNode falseSuccessor, double trueSuccessorProbability) {
        super(StampFactory.forVoid());
        this.condition = condition;
        this.falseSuccessor = falseSuccessor;
        this.trueSuccessorProbability = trueSuccessorProbability;
        this.trueSuccessor = trueSuccessor;

    }

    /**
     * Gets the true successor.
     * 
     * @return the true successor
     */
    public BeginNode trueSuccessor() {
        return trueSuccessor;
    }

    /**
     * Gets the false successor.
     * 
     * @return the false successor
     */
    public BeginNode falseSuccessor() {
        return falseSuccessor;
    }

    public void setTrueSuccessor(BeginNode node) {
        updatePredecessor(trueSuccessor, node);
        trueSuccessor = node;
    }

    public void setFalseSuccessor(BeginNode node) {
        updatePredecessor(falseSuccessor, node);
        falseSuccessor = node;
    }

    /**
     * Gets the node corresponding to the specified outcome of the branch.
     * 
     * @param istrue {@code true} if the true successor is requested, {@code false} otherwise
     * @return the corresponding successor
     */
    public BeginNode successor(boolean istrue) {
        return istrue ? trueSuccessor : falseSuccessor;
    }

    @Override
    public Negatable negate() {
        BeginNode trueSucc = trueSuccessor();
        BeginNode falseSucc = falseSuccessor();
        setTrueSuccessor(null);
        setFalseSuccessor(null);
        setTrueSuccessor(falseSucc);
        setFalseSuccessor(trueSucc);
        trueSuccessorProbability = 1 - trueSuccessorProbability;
        return this;
    }

    public void setTrueSuccessorProbability(double prob) {
        trueSuccessorProbability = prob;
    }

    @Override
    public double probability(BeginNode successor) {
        return successor == trueSuccessor ? trueSuccessorProbability : 1 - trueSuccessorProbability;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
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
        if (condition() instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) condition();
            if (c.getValue()) {
                tool.deleteBranch(falseSuccessor());
                tool.addToWorkList(trueSuccessor());
                ((StructuredGraph) graph()).removeSplit(this, trueSuccessor());
            } else {
                tool.deleteBranch(trueSuccessor());
                tool.addToWorkList(falseSuccessor());
                ((StructuredGraph) graph()).removeSplit(this, falseSuccessor());
            }
        } else if (trueSuccessor().guards().isEmpty() && falseSuccessor().guards().isEmpty()) {
            if (removeOrMaterializeIf(tool)) {
                return;
            } else if (removeIntermediateMaterialization(tool)) {
                return;
            }
        }
    }

    /**
     * Tries to remove an empty if construct or replace an if construct with a materialization.
     * 
     * @return true if a transformation was made, false otherwise
     */
    private boolean removeOrMaterializeIf(SimplifierTool tool) {
        if (trueSuccessor().next() instanceof EndNode && falseSuccessor().next() instanceof EndNode) {
            EndNode trueEnd = (EndNode) trueSuccessor().next();
            EndNode falseEnd = (EndNode) falseSuccessor().next();
            MergeNode merge = trueEnd.merge();
            if (merge == falseEnd.merge() && merge.forwardEndCount() == 2 && trueSuccessor().anchored().isEmpty() && falseSuccessor().anchored().isEmpty()) {
                Iterator<PhiNode> phis = merge.phis().iterator();
                if (!phis.hasNext()) {
                    // empty if construct with no phis: remove it
                    removeEmptyIf(tool);
                    return false;
                } else {
                    PhiNode singlePhi = phis.next();
                    if (!phis.hasNext()) {
                        // one phi at the merge of an otherwise empty if construct: try to convert
                        // into a MaterializeNode
                        boolean inverted = trueEnd == merge.forwardEndAt(1);
                        ValueNode trueValue = singlePhi.valueAt(inverted ? 1 : 0);
                        ValueNode falseValue = singlePhi.valueAt(inverted ? 0 : 1);
                        if (trueValue.kind() != falseValue.kind()) {
                            return false;
                        }
                        if (trueValue.kind() != Kind.Int && trueValue.kind() != Kind.Long) {
                            return false;
                        }
                        if (trueValue.isConstant() && falseValue.isConstant()) {
                            ConditionalNode materialize = graph().unique(new ConditionalNode(condition(), trueValue, falseValue));
                            ((StructuredGraph) graph()).replaceFloating(singlePhi, materialize);
                            removeEmptyIf(tool);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
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
        if (!(condition() instanceof CompareNode)) {
            return false;
        }

        CompareNode compare = (CompareNode) condition();
        if (compare.usages().count() != 1) {
            return false;
        }

        if (!(predecessor() instanceof MergeNode)) {
            return false;
        }

        MergeNode merge = (MergeNode) predecessor();
        if (!merge.anchored().isEmpty()) {
            return false;
        }

        // Only consider merges with a single usage that is both a phi and an operand of the
        // comparison
        NodeIterable<Node> mergeUsages = merge.usages();
        if (mergeUsages.count() != 1) {
            return false;
        }
        Node singleUsage = mergeUsages.first();
        if (!(singleUsage instanceof PhiNode) || (singleUsage != compare.x() && singleUsage != compare.y())) {
            return false;
        }

        // Ensure phi is used by at most the comparison and the merge's frame state (if any)
        PhiNode phi = (PhiNode) singleUsage;
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
        if (phi.valueCount() != merge.forwardEndCount()) {
            // Handles a loop begin merge
            return false;
        }

        Constant[] xs = constantValues(compare.x(), merge);
        Constant[] ys = constantValues(compare.y(), merge);
        if (xs == null || ys == null) {
            return false;
        }

        List<EndNode> falseEnds = new ArrayList<>(mergePredecessors.size());
        List<EndNode> trueEnds = new ArrayList<>(mergePredecessors.size());
        Map<EndNode, ValueNode> phiValues = new HashMap<>(mergePredecessors.size());

        BeginNode oldFalseSuccessor = falseSuccessor();
        BeginNode oldTrueSuccessor = trueSuccessor();

        setFalseSuccessor(null);
        setTrueSuccessor(null);

        Iterator<EndNode> ends = mergePredecessors.iterator();
        for (int i = 0; i < xs.length; i++) {
            EndNode end = ends.next();
            phiValues.put(end, phi.valueAt(end));
            if (compare.condition().foldCondition(xs[i], ys[i], tool.runtime(), compare.unorderedIsTrue())) {
                trueEnds.add(end);
            } else {
                falseEnds.add(end);
            }
        }
        assert !ends.hasNext();
        assert falseEnds.size() + trueEnds.size() == xs.length;

        connectEnds(falseEnds, phiValues, oldFalseSuccessor, merge, tool);
        connectEnds(trueEnds, phiValues, oldTrueSuccessor, merge, tool);

        GraphUtil.killCFG(merge);

        assert !merge.isAlive() : merge;
        assert !phi.isAlive() : phi;
        assert !compare.isAlive() : compare;
        assert !this.isAlive() : this;

        return true;
    }

    /**
     * Connects a set of ends to a given successor, inserting a merge node if there is more than one
     * end. If {@code ends} is empty, then {@code successor} is
     * {@linkplain GraphUtil#killCFG(FixedNode) killed} otherwise it is added to {@code tool}'s
     * {@linkplain SimplifierTool#addToWorkList(com.oracle.graal.graph.Node) work list}.
     * 
     * @param oldMerge the merge being removed
     * @param phiValues the values of the phi at the merge, keyed by the merge ends
     */
    private void connectEnds(List<EndNode> ends, Map<EndNode, ValueNode> phiValues, BeginNode successor, MergeNode oldMerge, SimplifierTool tool) {
        if (ends.isEmpty()) {
            GraphUtil.killCFG(successor);
        } else {
            if (ends.size() == 1) {
                EndNode end = ends.get(0);
                ((FixedWithNextNode) end.predecessor()).setNext(successor);
                oldMerge.removeEnd(end);
                GraphUtil.killCFG(end);
            } else {
                // Need a new phi in case the frame state is used by more than the merge being
                // removed
                MergeNode newMerge = graph().add(new MergeNode());
                PhiNode oldPhi = (PhiNode) oldMerge.usages().first();
                PhiNode newPhi = graph().add(new PhiNode(oldPhi.stamp(), newMerge));

                double probability = 0.0;
                for (EndNode end : ends) {
                    newPhi.addInput(phiValues.get(end));
                    newMerge.addForwardEnd(end);
                    probability += end.probability();
                }
                newMerge.setProbability(probability);

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
    private static Constant[] constantValues(ValueNode node, MergeNode merge) {
        if (node.isConstant()) {
            Constant[] result = new Constant[merge.forwardEndCount()];
            Arrays.fill(result, node.asConstant());
            return result;
        }

        if (node instanceof PhiNode) {
            PhiNode phi = (PhiNode) node;
            if (phi.merge() == merge && phi.type() == PhiType.Value && phi.valueCount() == merge.forwardEndCount()) {
                Constant[] result = new Constant[merge.forwardEndCount()];
                int i = 0;
                for (ValueNode n : phi.values()) {
                    if (!n.isConstant()) {
                        return null;
                    }
                    result[i++] = n.asConstant();
                }
                return result;
            }
        }

        return null;
    }

    private void removeEmptyIf(SimplifierTool tool) {
        BeginNode originalTrueSuccessor = trueSuccessor();
        BeginNode originalFalseSuccessor = falseSuccessor();
        assert originalTrueSuccessor.next() instanceof EndNode && originalFalseSuccessor.next() instanceof EndNode;

        EndNode trueEnd = (EndNode) originalTrueSuccessor.next();
        EndNode falseEnd = (EndNode) originalFalseSuccessor.next();
        assert trueEnd.merge() == falseEnd.merge();

        FixedWithNextNode pred = (FixedWithNextNode) predecessor();
        MergeNode merge = trueEnd.merge();
        merge.prepareDelete(pred);
        assert merge.usages().isEmpty();
        originalTrueSuccessor.prepareDelete();
        originalFalseSuccessor.prepareDelete();

        FixedNode next = merge.next();
        FrameState state = merge.stateAfter();
        merge.setNext(null);
        setTrueSuccessor(null);
        setFalseSuccessor(null);
        pred.setNext(next);
        safeDelete();
        originalTrueSuccessor.safeDelete();
        originalFalseSuccessor.safeDelete();
        merge.safeDelete();
        trueEnd.safeDelete();
        falseEnd.safeDelete();
        if (state != null) {
            tool.removeIfUnused(state);
        }
        tool.addToWorkList(next);
    }
}
