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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.Arrays;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSuccessorList;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.ProfileData.SwitchProbabilityData;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
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
public abstract class SwitchNode extends ControlSplitNode implements Simplifiable {

    public static final NodeClass<SwitchNode> TYPE = NodeClass.create(SwitchNode.class);
    @Successor protected NodeSuccessorList<AbstractBeginNode> successors;
    @Input protected ValueNode value;

    // do not change the contents of keySuccessors, nor of profileData.getKeyProbabilities()
    protected final int[] keySuccessors;
    protected SwitchProbabilityData profileData;
    protected EconomicMap<AbstractBeginNode, Double> successorProbabilityCache;

    /**
     * Index of the {@link #successors} field in {@link SwitchNode}'s
     * {@linkplain NodeClass#getSuccessorEdges() successor edges}.
     */
    public static final long SUCCESSORS_EDGE_INDEX = TYPE.getSuccessorEdges().getIndex(SwitchNode.class, "successors");

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
        assert keySuccessors.length == profileData.getKeyProbabilities().length : Assertions.errorMessageContext("keySucc", keySuccessors, "profiles", profileData.getKeyProbabilities());
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
        GraalError.guarantee(ProfileData.isApproximatelyEqual(total, 1.0),
                        "Total probability across branches not equal to one: %.10f", total);
        return true;
    }

    @Override
    public int getSuccessorCount() {
        return successors.count();
    }

    @Override
    public double probability(AbstractBeginNode successor) {
        if (successorProbabilityCache != null && successorProbabilityCache.containsKey(successor)) {
            double cachedProbability = successorProbabilityCache.get(successor);
            assert computeProbability(successor) == cachedProbability : Assertions.errorMessage("Different results for cached versus real probability", cachedProbability,
                            computeProbability(successor));
            return cachedProbability;
        }
        if (successorProbabilityCache == null) {
            successorProbabilityCache = EconomicMap.create();
        }
        double sum = computeProbability(successor);
        successorProbabilityCache.put(successor, sum);
        return sum;
    }

    private double computeProbability(AbstractBeginNode successor) {
        double sum = 0;
        double[] keyProbabilities = getKeyProbabilities();
        for (int i = 0; i < keySuccessors.length; i++) {
            if (successors.get(keySuccessors[i]) == successor) {
                sum += keyProbabilities[i];
            }
        }
        return sum;
    }

    @Override
    public boolean setProbability(AbstractBeginNode successor, BranchProbabilityData successorProfileData) {
        invalidateProbabilityCache();
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
        if (successorProbabilityCache != null) {
            successorProbabilityCache.clear();
        }
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
        invalidateProbabilityCache();
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
                            succ.next());
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

    @Override
    public void simplify(SimplifierTool tool) {
        tryPullThroughSwitch(tool);
    }

    private void tryPullThroughSwitch(SimplifierTool tool) {
        GraphUtil.tryDeDuplicateSplitSuccessors(this, tool);
    }

    @Override
    public void beforeEncode() {
        // purge the cache, we will build it newly after decoding
        successorProbabilityCache = null;
    }

    private void invalidateProbabilityCache() {
        if (successorProbabilityCache != null) {
            /*
             * Setting the probability of a single successor requires rebalancing the probabilities
             * of all other successors to ensure the sum remains consistent (i.e., normalized to
             * 1.0). Thus, the entire cache needs invalidation.
             */
            successorProbabilityCache.clear();
        }
    }

    public Stamp getValueStampForSuccessor(AbstractBeginNode beginNode, EconomicMap<AbstractBeginNode, Stamp> successorStampCache) {
        if (successorStampCache.containsKey(beginNode)) {
            Stamp cachedStamp = successorStampCache.get(beginNode);
            if (Assertions.assertionsEnabled()) {
                Stamp computedStamp = computeSuccessorStamp(beginNode);
                assert cachedStamp == null && computedStamp == null || cachedStamp != null && cachedStamp.equals(computedStamp) : Assertions.errorMessage("Stamp must be equal", cachedStamp,
                                computedStamp);
            }
            return cachedStamp;
        }
        Stamp result = computeSuccessorStamp(beginNode);
        successorStampCache.put(beginNode, result);
        return result;
    }

    public final Stamp computeSuccessorStamp(AbstractBeginNode beginNode) {
        Stamp result = null;
        if (beginNode != defaultSuccessor()) {
            for (int i = 0; i < keyCount(); i++) {
                if (keySuccessor(i) == beginNode) {
                    if (result == null) {
                        result = stampAtKeySuccessor(i);
                    } else {
                        result = result.meet(stampAtKeySuccessor(i));
                    }
                }
            }
        }
        // if we cannot compute a better stamp use the stamp of the value
        return result == null ? genericSuccessorStamp() : result;
    }

    public abstract Stamp genericSuccessorStamp();

    public final void getAllSuccessorValueStamps(EconomicMap<AbstractBeginNode, Stamp> cacheInto) {
        for (int i = 0; i < keyCount(); i++) {
            AbstractBeginNode beginNode = keySuccessor(i);
            if (beginNode != this.defaultSuccessor()) {
                if (!cacheInto.containsKey(beginNode)) {
                    cacheInto.put(beginNode, stampAtKeySuccessor(i));
                } else {
                    cacheInto.put(beginNode, cacheInto.get(beginNode).meet(stampAtKeySuccessor(i)));
                }
            }
        }
    }

    protected abstract Stamp stampAtKeySuccessor(int i);
}
