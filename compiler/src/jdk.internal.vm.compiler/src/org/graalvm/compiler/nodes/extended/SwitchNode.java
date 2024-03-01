/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_64;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.Arrays;

import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeSuccessorList;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ProfileData;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;
import org.graalvm.compiler.nodes.ProfileData.SwitchProbabilityData;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.SimplifierTool;

import jdk.vm.ci.meta.Constant;

/**
 * The {@code SwitchNode} class is the base of both lookup and table switches.
 */
// @formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
          cyclesRationale = "We cannot estimate the runtime cost of a switch statement without knowing the number" +
                            "of case statements and the involved keys.",
          size = SIZE_UNKNOWN,
          sizeRationale = "We cannot estimate the code size of a switch statement without knowing the number" +
                          "of case statements.")
// @formatter:on
public abstract class SwitchNode extends ControlSplitNode {

    public static final NodeClass<SwitchNode> TYPE = NodeClass.create(SwitchNode.class);
    @Successor protected NodeSuccessorList<AbstractBeginNode> successors;
    @Input protected ValueNode value;

    // do not change the contents of keySuccessors, nor of profileData.getKeyProbabilities()
    protected final int[] keySuccessors;
    protected SwitchProbabilityData profileData;

    /**
     * Constructs a new Switch.
     *
     * @param value the instruction that provides the value to be switched over
     * @param successors the list of successors of this switch
     */
    @SuppressWarnings("this-escape")
    protected SwitchNode(NodeClass<? extends SwitchNode> c, ValueNode value, AbstractBeginNode[] successors, int[] keySuccessors, SwitchProbabilityData profileData) {
        super(c, StampFactory.forVoid());
        assert value.stamp(NodeView.DEFAULT).getStackKind().isNumericInteger() || value.stamp(NodeView.DEFAULT) instanceof AbstractPointerStamp : value.stamp(NodeView.DEFAULT) +
                        " key not supported by SwitchNode";
        assert keySuccessors.length == profileData.getKeyProbabilities().length;
        this.successors = new NodeSuccessorList<>(this, successors);
        this.value = value;
        this.keySuccessors = keySuccessors;
        this.profileData = profileData;
        assert assertProbabilities();
    }

    protected double[] getKeyProbabilities() {
        return profileData.getKeyProbabilities();
    }

    private boolean assertProbabilities() {
        double total = 0;
        for (double d : getKeyProbabilities()) {
            total += d;
            GraalError.guarantee(d >= 0.0, "Cannot have negative probabilities in switch node: %s", d);
        }
        GraalError.guarantee(Math.abs(total - 1.0) <= ProfileData.EPSILON, "Total probability across branches not equal to one: %.10f", total);
        return true;
    }

    @Override
    public int getSuccessorCount() {
        return successors.count();
    }

    @Override
    public double probability(AbstractBeginNode successor) {
        double sum = 0;
        for (int i = 0; i < keySuccessors.length; i++) {
            if (successors.get(keySuccessors[i]) == successor) {
                sum += getKeyProbabilities()[i];
            }
        }
        return sum;
    }

    @Override
    public boolean setProbability(AbstractBeginNode successor, BranchProbabilityData successorProfileData) {
        double newProbability = successorProfileData.getDesignatedSuccessorProbability();
        assert newProbability <= 1.0 && newProbability >= 0.0 : newProbability;

        double[] keyProbabilities = getKeyProbabilities().clone();
        double sum = 0;
        double otherSum = 0;
        for (int i = 0; i < keySuccessors.length; i++) {
            if (successors.get(keySuccessors[i]) == successor) {
                sum += keyProbabilities[i];
            } else {
                otherSum += keyProbabilities[i];
            }
        }

        if (otherSum == 0 || sum == 0) {
            // Cannot correctly adjust probabilities.
            return false;
        }

        double delta = newProbability - sum;

        for (int i = 0; i < keySuccessors.length; i++) {
            if (successors.get(keySuccessors[i]) == successor) {
                keyProbabilities[i] = Math.max(0.0, keyProbabilities[i] + (delta * keyProbabilities[i]) / sum);
            } else {
                keyProbabilities[i] = Math.max(0.0, keyProbabilities[i] - (delta * keyProbabilities[i]) / otherSum);
            }
        }
        profileData = SwitchProbabilityData.create(keyProbabilities, profileData.getProfileSource().combine(successorProfileData.getProfileSource()));
        assert assertProbabilities();
        return true;
    }

    public void setProfileData(SwitchProbabilityData profileData) {
        this.profileData = profileData;
        assert assertProbabilities();
    }

    @Override
    public SwitchProbabilityData getProfileData() {
        return profileData;
    }

    public ValueNode value() {
        return value;
    }

    public abstract boolean isSorted();

    /**
     * The number of distinct keys in this switch.
     */
    public abstract int keyCount();

    /**
     * The key at the specified position, encoded in a Constant.
     */
    public abstract Constant keyAt(int i);

    public boolean structureEquals(SwitchNode switchNode) {
        return Arrays.equals(keySuccessors, switchNode.keySuccessors) && equalKeys(switchNode);
    }

    /**
     * Returns true if the switch has the same keys in the same order as this switch.
     */
    public abstract boolean equalKeys(SwitchNode switchNode);

    /**
     * Returns the index of the successor belonging to the key at the specified index.
     */
    public int keySuccessorIndex(int i) {
        return keySuccessors[i];
    }

    /**
     * Returns the successor for the key at the given index.
     */
    public AbstractBeginNode keySuccessor(int i) {
        return successors.get(keySuccessors[i]);
    }

    /**
     * Returns the probability of the key at the given index.
     */
    public double keyProbability(int i) {
        return getKeyProbabilities()[i];
    }

    /**
     * Returns the probability of taking the default branch.
     */
    public double defaultProbability() {
        return getKeyProbabilities()[getKeyProbabilities().length - 1];
    }

    /**
     * Returns the index of the default (fall through) successor of this switch.
     */
    public int defaultSuccessorIndex() {
        return keySuccessors[keySuccessors.length - 1];
    }

    public AbstractBeginNode blockSuccessor(int i) {
        return successors.get(i);
    }

    public void setBlockSuccessor(int i, AbstractBeginNode s) {
        successors.set(i, s);
    }

    public int blockSuccessorCount() {
        return successors.count();
    }

    /**
     * Gets the successor corresponding to the default (fall through) case.
     *
     * @return the default successor
     */
    public AbstractBeginNode defaultSuccessor() {
        if (defaultSuccessorIndex() == -1) {
            throw new GraalError("unexpected");
        }
        return successors.get(defaultSuccessorIndex());
    }

    @Override
    public AbstractBeginNode getPrimarySuccessor() {
        return null;
    }

    protected boolean shouldInjectBranchProbabilities() {
        for (AbstractBeginNode succ : successors) {
            if (succ.next() instanceof SwitchCaseProbabilityNode) {
                return true;
            }
        }
        return false;
    }

    protected void injectBranchProbabilities() {
        /*
         * Since multiple keys can point to the same block, we must divide each block's probability
         * between all keys sharing it.
         */
        int[] numKeysPerBlock = new int[blockSuccessorCount()];
        for (int i = 0; i < keySuccessors.length; ++i) {
            numKeysPerBlock[keySuccessorIndex(i)]++;
        }

        double[] nodeProbabilities = new double[keySuccessors.length];
        for (int i = 0; i < keySuccessors.length; ++i) {
            AbstractBeginNode succ = keySuccessor(i);
            /*
             * When a switch case exits out of a nested loop the SwitchProbabilityNode will be
             * placed after a series of LoopExits, one per noop lesting level.
             */
            while (succ.next() instanceof AbstractBeginNode next) {
                succ = next;
            }
            assertTrue(succ.next() instanceof SwitchCaseProbabilityNode,
                            "Cannot inject switch probability, since key successor %s is not a SwitchCaseProbabilityNode",
                            this, succ.next());
            SwitchCaseProbabilityNode caseProbabilityNode = (SwitchCaseProbabilityNode) succ.next();

            ValueNode probabilityNode = caseProbabilityNode.getProbability();
            if (!probabilityNode.isConstant()) {
                /*
                 * If any of the probabilities are not constant we bail out of simplification, which
                 * will cause compilation to fail later during lowering since the node will be left
                 * behind
                 */
                return;
            }
            double probabilityValue = probabilityNode.asJavaConstant().asDouble();
            if (probabilityValue < 0.0) {
                throw new GraalError("A negative probability of " + probabilityValue + " is not allowed!");
            } else if (probabilityValue > 1.0) {
                throw new GraalError("A probability of more than 1.0 (" + probabilityValue + ") is not allowed!");
            } else if (Double.isNaN(probabilityValue)) {
                /*
                 * We allow NaN if the node is in unreachable code that will eventually fall away,
                 * or else an error will be thrown during lowering since we keep the node around.
                 * See analogous case in BranchProbabilityNode.
                 */
                return;
            }
            nodeProbabilities[i] = probabilityValue / numKeysPerBlock[keySuccessorIndex(i)];
        }

        for (AbstractBeginNode blockSuccessor : successors) {
            AbstractBeginNode succ = blockSuccessor;
            while (succ.next() instanceof AbstractBeginNode next) {
                succ = next;
            }
            SwitchCaseProbabilityNode caseProbabilityNode = (SwitchCaseProbabilityNode) succ.next();
            caseProbabilityNode.replaceAtUsages(null);
            graph().removeFixed(caseProbabilityNode);
        }

        setProfileData(ProfileData.SwitchProbabilityData.create(nodeProbabilities, ProfileData.ProfileSource.INJECTED));
    }

    /**
     * Delete all other successors except for the one reached by {@code survivingEdge}.
     *
     * @param tool
     * @param survivingEdge index of the edge in the {@link SwitchNode#successors} list
     */
    protected void killOtherSuccessors(SimplifierTool tool, int survivingEdge) {
        for (Node successor : successors()) {
            /*
             * Deleting a branch change change the successors so reload the surviving successor each
             * time.
             */
            if (successor != blockSuccessor(survivingEdge)) {
                tool.deleteBranch(successor);
            }
        }
        tool.addToWorkList(blockSuccessor(survivingEdge));
        graph().removeSplit(this, blockSuccessor(survivingEdge));
    }

    public abstract Stamp getValueStampForSuccessor(AbstractBeginNode beginNode);

    @Override
    public NodeCycles estimatedNodeCycles() {
        if (keyCount() == 1) {
            // if
            return CYCLES_2;
        } else if (isSorted()) {
            // good heuristic
            return CYCLES_8;
        } else {
            // not so good
            return CYCLES_64;
        }
    }

    @Override
    protected NodeSize dynamicNodeSizeEstimate() {
        if (keyCount() == 1) {
            // if
            return SIZE_2;
        } else if (isSorted()) {
            // good heuristic
            return SIZE_8;
        } else {
            // not so good
            return SIZE_64;
        }
    }

    public int[] getKeySuccessors() {
        return keySuccessors.clone();
    }
}
