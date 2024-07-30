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
package jdk.graal.compiler.nodes.cfg;

import static jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph.INVALID_BLOCK_ID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MultiMemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;

/**
 * {@link StructuredGraph} based implementation of {@link BasicBlock}. Instances of subclasses of
 * this are allocated by {@link ControlFlowGraph}. Stores accompanying meta-information about this
 * {@link HIRBlock} in context of its {@link ControlFlowGraph}.
 */
public abstract class HIRBlock extends BasicBlock<HIRBlock> {

    protected final AbstractBeginNode beginNode;
    protected FixedNode endNode;

    protected double relativeFrequency = -1D;
    protected ProfileSource frequencySource;
    protected CFGLoop<HIRBlock> loop;

    protected int numBackedges = -1;

    protected int postdominator = INVALID_BLOCK_ID;
    private LocationSet killLocations;
    private LocationSet killLocationsBetweenThisAndDominator;

    HIRBlock(AbstractBeginNode node, ControlFlowGraph cfg) {
        super(cfg);
        this.beginNode = node;
    }

    public AbstractBeginNode getBeginNode() {
        return beginNode;
    }

    public FixedNode getEndNode() {
        return endNode;
    }

    @Override
    public CFGLoop<HIRBlock> getLoop() {
        return loop;
    }

    public void setLoop(CFGLoop<HIRBlock> loop) {
        this.loop = loop;
        this.numBackedges = (isLoopHeader() ? loop.numBackedges() : -1);
    }

    @Override
    public int getLoopDepth() {
        return loop == null ? 0 : loop.getDepth();
    }

    @Override
    public boolean isLoopHeader() {
        return getBeginNode() instanceof LoopBeginNode;
    }

    @Override
    public int numBackedges() {
        return numBackedges;
    }

    @Override
    public boolean isLoopEnd() {
        return getEndNode() instanceof LoopEndNode;
    }

    @Override
    public boolean isExceptionEntry() {
        Node predecessor = getBeginNode().predecessor();
        return predecessor != null && predecessor instanceof WithExceptionNode && getBeginNode() == ((WithExceptionNode) predecessor).exceptionEdge();
    }

    public HIRBlock getFirstPredecessor() {
        return getPredecessorAt(0);
    }

    public HIRBlock getFirstSuccessor() {
        return getSuccessorAt(0);
    }

    @Override
    public HIRBlock getPostdominator() {
        return postdominator != INVALID_BLOCK_ID ? getBlocks()[postdominator] : null;
    }

    @Override
    public boolean isModifiable() {
        return this instanceof ModifiableBlock;
    }

    private class NodeIterator implements Iterator<FixedNode> {

        private FixedNode cur;

        NodeIterator() {
            cur = getBeginNode();
        }

        @Override
        public boolean hasNext() {
            return cur != null;
        }

        @Override
        public FixedNode next() {
            FixedNode result = cur;
            if (result instanceof FixedWithNextNode) {
                FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) result;
                FixedNode next = fixedWithNextNode.next();
                if (next instanceof AbstractBeginNode) {
                    next = null;
                }
                cur = next;
            } else {
                cur = null;
            }
            assert !(cur instanceof AbstractBeginNode) : Assertions.errorMessageContext("cur", cur);
            return result;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public Iterable<FixedNode> getNodes() {
        return new Iterable<>() {

            @Override
            public Iterator<FixedNode> iterator() {
                return new NodeIterator();
            }

            @Override
            public String toString() {
                StringBuilder str = new StringBuilder().append('[');
                for (FixedNode node : this) {
                    str.append(node).append(", ");
                }
                if (str.length() > 1) {
                    str.setLength(str.length() - 2);
                }
                return str.append(']').toString();
            }
        };
    }

    @Override
    public String toString() {
        return toString(Verbosity.Id);
    }

    public String toString(Verbosity verbosity) {
        StringBuilder sb = new StringBuilder();
        sb.append('B').append(id);
        if (verbosity == Verbosity.Name) {
            sb.append("{");
            sb.append(getBeginNode());
            sb.append("->");
            sb.append(getEndNode());
            sb.append("}");
        } else if (verbosity != Verbosity.Id) {
            sb.append("{");
            sb.append(getBeginNode());
            sb.append("->");
            sb.append(getEndNode());
            sb.append("}");
            if (isLoopHeader()) {
                sb.append(" lh");
            }

            if (getSuccessorCount() > 0) {
                sb.append(" ->[");
                for (int i = 0; i < getSuccessorCount(); ++i) {
                    if (i != 0) {
                        sb.append(',');
                    }
                    sb.append('B').append(getSuccessorAt(i).getId());
                }
                sb.append(']');
            }

            if (getPredecessorCount() > 0) {
                sb.append(" <-[");
                for (int i = 0; i < getPredecessorCount(); ++i) {
                    if (i != 0) {
                        sb.append(',');
                    }
                    sb.append('B').append(getPredecessorAt(i).getId());
                }
                sb.append(']');
            }
        }
        return sb.toString();
    }

    /**
     * Get the relative Frequency of a basic block.
     *
     * In order for profile guided optimizations to utilize profiling information from the
     * interpreter during optimization Graal uses the concept of block and loop frequencies, i.e.,
     * the frequency of a certain piece of code relative to the start of a method. This is used as a
     * proxy for the importance of code inside a single method.
     *
     * During the life cycle of a method executed by the JavaVM every method is initially executed
     * by the interpreter which gathers profiling information. Among this profiling information is
     * the so called branch probability, i.e. the probability for the true and false successor of a
     * single binary branch.
     *
     * For a simple if then else construct like
     *
     * <pre>
     * if (a) {
     *  thenAction()
     * } else {
     *  elseAction()
     * }
     * </pre>
     *
     * and a true successor probability of 0.5 this means 50% of the time when executing the code
     * condition a was false. This only becomes relevant in a large context: e.g., out of 1000 times
     * the code is executed, 500 times a is false.
     *
     * The interpreter collects these branch profiles for every java bytecode if instruction. The
     * Graal compiler uses them to derive its internal representation of execution probabilities
     * called "block frequencies". Since the Graal compiler only compiles one method at a time and
     * does not perform inter method optimizations the actual total numbers for invocation and
     * execution counts are not interesting. Thus, Graal uses the branch probabilities from the
     * interpreter to derive a metric for profiles within a single compilation unit. These are the
     * block frequencies. Block frequencies are applied to basic blocks, i.e., every basic block has
     * one. It is a floating point number that expresses how often a basic block will be executed
     * with respect to the start of a method. Thus, the metric only makes sense within a single
     * compilation unit and it marks hot regions of code.
     *
     * Consider the following method foo:
     *
     * <pre>
     * void foo() {
     *  // method start: frequency = 1
     *  int i=0;
     *  while (true) {
     *      if(i>=10) { // exit
     *          break;
     *      }
     *      consume(i)
     *      i++;
     *  }
     *  return // method end: relative frequency = 1
     * }
     * </pre>
     *
     * Every method's start basic block is unconditionally executed thus it has a frequency of 1.
     * Then foo contains a loop that consists of a loop header, a condition, an exit and a loop
     * body. In this while loop, the header is executed initially once and then how often the back
     * edges indicate that the loop will be executed. For this Graal uses the frequency of the loop
     * exit condition (i.e. {@code i >= 10}). When the condition has a false successor (enter the
     * loop body) frequency of roughly 90% we can calculate the loop frequency from that: the loop
     * frequency is the entry block frequency times the frequency from the exit condition's false
     * successor which accumulates roughly to 1/0.1 which amounts to roughly 10 loop iterations.
     * However, since we know the loop is exited at some point the code after the loop has again a
     * block frequency set to 1 (loop entry frequency).
     *
     * Graal {@linkplain IfNode#setTrueSuccessorProbability(BranchProbabilityData) sets the
     * profiles} during parsing and later computes loop frequencies for {@link LoopBeginNode}.
     * Finally, the frequency for basic {@link HIRBlock}s is set during {@link ControlFlowGraph}
     * construction.
     */
    @Override
    public double getRelativeFrequency() {
        return relativeFrequency;
    }

    public void setRelativeFrequency(double relativeFrequency) {
        assert relativeFrequency >= 0 && Double.isFinite(relativeFrequency) : "Relative Frequency=" + relativeFrequency;
        this.relativeFrequency = relativeFrequency;
    }

    public void setFrequencySource(ProfileSource frequencySource) {
        this.frequencySource = frequencySource;
    }

    public ProfileSource getFrequencySource() {
        return frequencySource;
    }

    @Override
    public ControlFlowGraph getCfg() {
        return (ControlFlowGraph) super.getCfg();
    }

    @Override
    public HIRBlock getDominator(int distance) {
        HIRBlock result = this;
        for (int i = 0; i < distance; ++i) {
            result = result.getDominator();
        }
        return result;
    }

    public boolean canKill(LocationIdentity location) {
        if (location.isImmutable()) {
            return false;
        }
        return getKillLocations().contains(location);
    }

    public LocationSet getKillLocations() {
        if (killLocations == null) {
            killLocations = calcKillLocations();
        }
        return killLocations;
    }

    private LocationSet calcKillLocations() {
        LocationSet result = new LocationSet();
        for (FixedNode node : this.getNodes()) {
            if (MemoryKill.isSingleMemoryKill(node)) {
                LocationIdentity identity = ((SingleMemoryKill) node).getKilledLocationIdentity();
                result.add(identity);
            } else if (MemoryKill.isMultiMemoryKill(node)) {
                for (LocationIdentity identity : ((MultiMemoryKill) node).getKilledLocationIdentities()) {
                    result.add(identity);
                }
            }
            if (result.isAny()) {
                break;
            }
        }
        return result;
    }

    public boolean canKillBetweenThisAndDominator(LocationIdentity location) {
        if (location.isImmutable()) {
            return false;
        }
        return this.getKillLocationsBetweenThisAndDominator().contains(location);
    }

    private LocationSet getKillLocationsBetweenThisAndDominator() {
        if (this.killLocationsBetweenThisAndDominator == null) {
            LocationSet dominatorResult = new LocationSet();
            HIRBlock stopBlock = getDominator();
            if (this.isLoopHeader()) {
                assert stopBlock.getLoopDepth() < this.getLoopDepth() : Assertions.errorMessage(stopBlock, this);
                dominatorResult.addAll(((HIRLoop) this.getLoop()).getKillLocations());
            } else {
                for (int i = 0; i < getPredecessorCount(); i++) {
                    HIRBlock b = getPredecessorAt(i);
                    assert !this.isLoopHeader();
                    if (b != stopBlock) {
                        dominatorResult.addAll(b.getKillLocations());
                        if (dominatorResult.isAny()) {
                            break;
                        }
                        b.calcKillLocationsBetweenThisAndTarget(dominatorResult, stopBlock);
                        if (dominatorResult.isAny()) {
                            break;
                        }
                    }
                }
            }
            this.killLocationsBetweenThisAndDominator = dominatorResult;
        }
        return this.killLocationsBetweenThisAndDominator;
    }

    private void calcKillLocationsBetweenThisAndTarget(LocationSet result, HIRBlock stopBlock) {
        assert stopBlock.dominates(this);
        if (stopBlock == this || result.isAny()) {
            // We reached the stop block => nothing to do.
            return;
        } else {
            if (stopBlock == this.getDominator()) {
                result.addAll(this.getKillLocationsBetweenThisAndDominator());
            } else {
                // Divide and conquer: Aggregate kill locations from this to the dominator and then
                // from the dominator onwards.
                calcKillLocationsBetweenThisAndTarget(result, this.getDominator());
                result.addAll(this.getDominator().getKillLocations());
                if (result.isAny()) {
                    return;
                }
                this.getDominator().calcKillLocationsBetweenThisAndTarget(result, stopBlock);
            }
        }
    }

    protected void setPostDominator(HIRBlock postdominator) {
        if (postdominator != null) {
            this.postdominator = postdominator.getId();
        }
    }

    /**
     * Checks whether {@code this} block is in the same loop or an outer loop of the block given as
     * parameter.
     */
    public boolean isInSameOrOuterLoopOf(HIRBlock block) {
        if (this.loop == null) {
            // We are in no loop, so this holds true for every other block.
            return true;
        }

        CFGLoop<HIRBlock> l = block.loop;
        while (l != null) {
            if (l == this.loop) {
                return true;
            }
            l = l.getParent();
        }

        return false;
    }

    public static void computeLoopPredecessors(NodeMap<HIRBlock> nodeMap, ModifiableBlock block, LoopBeginNode loopBeginNode) {
        int forwardEndCount = loopBeginNode.forwardEndCount();
        LoopEndNode[] loopEnds = loopBeginNode.orderedLoopEnds();
        int firstPredecessor = nodeMap.get(loopBeginNode.forwardEndAt(0)).getId();
        int[] extraPredecessors = new int[forwardEndCount + loopEnds.length - 1];
        for (int i = 1; i < forwardEndCount; ++i) {
            extraPredecessors[i - 1] = nodeMap.get(loopBeginNode.forwardEndAt(i)).getId();
        }
        for (int i = 0; i < loopEnds.length; ++i) {
            extraPredecessors[i + forwardEndCount - 1] = nodeMap.get(loopEnds[i]).getId();
        }
        block.setPredecessors(firstPredecessor, extraPredecessors);
    }

    public static void assignPredecessorsAndSuccessors(HIRBlock[] blocks, ControlFlowGraph cfg) {
        for (int bI = 0; bI < blocks.length; bI++) {
            ModifiableBlock b = (ModifiableBlock) blocks[bI];
            FixedNode blockEndNode = b.getEndNode();
            if (blockEndNode instanceof EndNode) {
                EndNode endNode = (EndNode) blockEndNode;
                HIRBlock suxBlock = cfg.getNodeToBlock().get(endNode.merge());
                b.setSuccessor(suxBlock.getId());
            } else if (blockEndNode instanceof ControlSplitNode) {
                ControlSplitNode split = (ControlSplitNode) blockEndNode;
                final int splitSuccessorcount = split.getSuccessorCount();
                int index = 0;
                int succ0 = INVALID_BLOCK_ID;
                int succ1 = INVALID_BLOCK_ID;
                int[] extraSucc = splitSuccessorcount > 2 ? new int[split.getSuccessorCount() - 2] : null;
                for (Node sux : blockEndNode.successors()) {
                    ModifiableBlock sucBlock = (ModifiableBlock) cfg.getNodeToBlock().get(sux);
                    if (index == 0) {
                        succ0 = sucBlock.getId();
                    } else if (index == 1) {
                        succ1 = sucBlock.getId();
                    } else {
                        extraSucc[index - 2] = sucBlock.getId();
                    }
                    index++;
                    sucBlock.setPredecessor(b.getId());
                }
                double[] succP = ((ControlSplitNode) blockEndNode).successorProbabilities();
                if (splitSuccessorcount == 1) {
                    // degenerated graphs before the next canonicalization
                    b.setSuccessor(succ0);
                } else if (splitSuccessorcount == 2) {
                    assert succP.length == 2 : Assertions.errorMessage(succP);
                    b.setSuccessors(succ0, succ1, succP[0], succP[1]);
                } else {
                    assert succP.length > 2 : Assertions.errorMessageContext("b", b, "succP", succP);
                    b.setSuccessors(succ0, succ1, extraSucc, succP[0], succP[1], Arrays.copyOfRange(succP, 2, succP.length));
                }
            } else if (blockEndNode instanceof LoopEndNode) {
                LoopEndNode loopEndNode = (LoopEndNode) blockEndNode;
                b.setSuccessor(cfg.getNodeToBlock().get(loopEndNode.loopBegin()).getId());
            } else if (blockEndNode instanceof ControlSinkNode) {
                // nothing to do
            } else {
                assert !(blockEndNode instanceof AbstractEndNode) : "Algorithm only supports EndNode and LoopEndNode.";
                for (Node suxNode : blockEndNode.successors()) {
                    ModifiableBlock sux = (ModifiableBlock) cfg.getNodeToBlock().get(suxNode);
                    sux.setPredecessor(b.getId());
                }
                assert blockEndNode.successors().count() == 1 : "Node " + blockEndNode;
                HIRBlock sequentialSuc = cfg.getNodeToBlock().get(blockEndNode.successors().first());
                b.setSuccessor(sequentialSuc.getId());
            }
            FixedNode blockBeginNode = b.getBeginNode();
            if (blockBeginNode instanceof LoopBeginNode) {
                computeLoopPredecessors(cfg.getNodeToBlock(), b, (LoopBeginNode) blockBeginNode);
            } else if (blockBeginNode instanceof AbstractMergeNode) {
                AbstractMergeNode mergeNode = (AbstractMergeNode) blockBeginNode;
                int forwardEndCount = mergeNode.forwardEndCount();
                int[] extraPred = new int[forwardEndCount - 1];
                int pred0 = cfg.getNodeToBlock().get(mergeNode.forwardEndAt(0)).getId();
                for (int i = 1; i < forwardEndCount; ++i) {
                    extraPred[i - 1] = cfg.getNodeToBlock().get(mergeNode.forwardEndAt(i)).getId();
                }
                b.setPredecessors(pred0, extraPred);
            }
        }
    }

    /**
     * A basic block that can have its edges edited.
     */
    static class ModifiableBlock extends HIRBlock {
        /**
         * Determine in the backend if this block should be aligned.
         */
        private boolean align;
        private int linearScanNumber = -1;
        /**
         * Extra data for cases where the loop information is no longer fully up to date due to
         * blocks being deleted during LIR control flow optimization.
         */
        private boolean markedAsLoopEnd = false;
        /**
         * Index into {@link #getBlocks} of this block's first predecessor.
         */
        private int firstPredecessor = INVALID_BLOCK_ID;
        /**
         * Indices into {@link #getBlocks} of this block's extra predecessors.
         */
        private int[] extraPredecessors;
        /**
         * Index into {@link #getBlocks} of this block's first successor.
         */
        private int firstSuccessor = INVALID_BLOCK_ID;
        /**
         * Index into {@link #getBlocks} of this block's second successor.
         */
        private int secondSuccessor = INVALID_BLOCK_ID;

        /**
         * Indices into {@link #getBlocks} of this block's extra successors.
         */
        private int[] extraSuccessors;
        private double firstSuccessorProbability;
        private double secondSuccessorProbability;
        private double[] extraSuccessorsProbabilities;

        ModifiableBlock(AbstractBeginNode node, ControlFlowGraph cfg) {
            super(node, cfg);
        }

        @Override
        public boolean isLoopEnd() {
            return markedAsLoopEnd || super.isLoopEnd();
        }

        public void markAsLoopEnd() {
            markedAsLoopEnd = true;
        }

        @Override
        public int getLinearScanNumber() {
            return linearScanNumber;
        }

        @Override
        public void setLinearScanNumber(int linearScanNumber) {
            this.linearScanNumber = linearScanNumber;
        }

        @Override
        public boolean isAligned() {
            return align;
        }

        @Override
        public void setAlign(boolean align) {
            this.align = align;
        }

        @Override
        public int getPredecessorCount() {
            return getCount(firstPredecessor, extraPredecessors);
        }

        @Override
        public int getSuccessorCount() {
            return getCount(firstSuccessor, secondSuccessor, extraSuccessors);
        }

        private static int getCount(int first, int second, int[] extra) {
            if (first == INVALID_BLOCK_ID) {
                return 0;
            }
            if (second == INVALID_BLOCK_ID) {
                return 1;
            }
            return 2 + (extra == null ? 0 : extra.length);
        }

        private static int getCount(int first, int[] extra) {
            return first == INVALID_BLOCK_ID ? 0 : 1 + (extra == null ? 0 : extra.length);
        }

        private static int getAtIndex(int first, int[] extra, int index) {
            return index == 0 ? first : extra[index - 1];
        }

        private static int getAtIndex(int first, int second, int[] extra, int index) {
            if (index == 0) {
                return first;
            }
            if (index == 1) {
                return second;
            }
            return extra[index - 2];
        }

        @Override
        public HIRBlock getPredecessorAt(int predIndex) {
            assert predIndex < getPredecessorCount() : "Pred index " + predIndex + " must always be smaller than pred count " + getPredecessorCount();
            return getBlocks()[getAtIndex(firstPredecessor, extraPredecessors, predIndex)];
        }

        @Override
        public HIRBlock getSuccessorAt(int succIndex) {
            assert succIndex < getSuccessorCount() : "Succ index " + succIndex + " must always be smaller than succ count " + getSuccessorCount();
            return getBlocks()[getAtIndex(firstSuccessor, secondSuccessor, extraSuccessors, succIndex)];
        }

        public void setPredecessor(int firstPredecessor) {
            this.firstPredecessor = firstPredecessor;
        }

        @SuppressWarnings("unchecked")
        public void setPredecessors(int firstPredecessor, int[] extraPredecessors) {
            this.firstPredecessor = firstPredecessor;
            this.extraPredecessors = extraPredecessors;
        }

        public void setSuccessor(int firstSuccessor) {
            this.firstSuccessor = firstSuccessor;
            firstSuccessorProbability = 1.0D;
        }

        @SuppressWarnings("unchecked")
        public void setSuccessors(int firstSuccessor, int secondSuccessor, double firstSuccP, double secondSuccP) {
            this.firstSuccessor = firstSuccessor;
            this.secondSuccessor = secondSuccessor;
            this.firstSuccessorProbability = firstSuccP;
            this.secondSuccessorProbability = secondSuccP;
        }

        @SuppressWarnings("unchecked")
        public void setSuccessors(int firstSuccessor, int secondSuccessor, int[] extraSuccessors, double firstSuccP, double secondSuccP, double[] restSuccP) {
            this.firstSuccessor = firstSuccessor;
            this.secondSuccessor = secondSuccessor;
            this.extraSuccessors = extraSuccessors;
            this.firstSuccessorProbability = firstSuccP;
            this.secondSuccessorProbability = secondSuccP;
            this.extraSuccessorsProbabilities = restSuccP;
        }

        @Override
        public double getSuccessorProbabilityAt(int succIndex) {
            if (succIndex == 0) {
                return firstSuccessorProbability;
            }
            if (succIndex == 1) {
                return secondSuccessorProbability;
            }
            return extraSuccessorsProbabilities[succIndex - 2];
        }

        @Override
        public void delete() {
            // adjust successor and predecessor lists
            GraalError.guarantee(getSuccessorCount() == 1, "can only delete blocks with exactly one successor.");
            ModifiableBlock next = (ModifiableBlock) getSuccessorAt(0);
            int predecessorCount = getPredecessorCount();
            for (int i = 0; i < getPredecessorCount(); i++) {
                ModifiableBlock pred = (ModifiableBlock) getPredecessorAt(i);
                int[] newPredSuccs = new int[pred.getSuccessorCount()];
                double[] newPredSuccP = new double[pred.getSuccessorCount()];
                for (int j = 0; j < pred.getSuccessorCount(); j++) {
                    HIRBlock predSuccAt = pred.getSuccessorAt(j);
                    if (predSuccAt == this) {
                        newPredSuccs[j] = next.getId();
                        newPredSuccP[j] = pred.getSuccessorProbabilityAt(0);
                    } else {
                        newPredSuccP[j] = pred.getSuccessorProbabilityAt(j);
                        newPredSuccs[j] = predSuccAt.getId();
                    }
                }
                if (newPredSuccs.length == 1) {
                    pred.setSuccessor(newPredSuccs[0]);
                } else if (newPredSuccs.length == 2) {
                    pred.setSuccessors(newPredSuccs[0], newPredSuccs[1], newPredSuccP[0], newPredSuccP[1]);
                } else {
                    pred.setSuccessors(newPredSuccs[0], newPredSuccs[1],
                                    Arrays.copyOfRange(newPredSuccs, 2, newPredSuccs.length),
                                    newPredSuccP[0], newPredSuccP[1],
                                    Arrays.copyOfRange(newPredSuccP, 2, newPredSuccP.length));
                }

                if (isLoopEnd()) {
                    // The predecessor becomes a loop end.
                    pred.markAsLoopEnd();
                }
            }
            if (isLoopEnd()) {
                GraalError.guarantee(next.isLoopHeader(), "a loop end's successor must be a loop header");
                next.numBackedges += predecessorCount - 1;
            }

            ArrayList<HIRBlock> newPreds = new ArrayList<>();
            for (int i = 0; i < next.getPredecessorCount(); i++) {
                HIRBlock curPred = next.getPredecessorAt(i);
                if (curPred == this) {
                    for (int j = 0; j < getPredecessorCount(); j++) {
                        newPreds.add(getPredecessorAt(j));
                    }
                } else {
                    newPreds.add(curPred);
                }
            }

            HIRBlock firstPred = newPreds.get(0);
            int[] extraPred1 = null;
            if (newPreds.size() - 1 > 0) {
                extraPred1 = new int[newPreds.size() - 1];
                for (int i = 1; i < newPreds.size(); i++) {
                    extraPred1[i - 1] = newPreds.get(i).getId();
                }
                next.setPredecessors(firstPred.getId(), extraPred1);
            } else {
                next.setPredecessor(firstPred.getId());
            }

            // Remove the current block from the blocks of the loops it belongs to
            for (CFGLoop<HIRBlock> currLoop = loop; currLoop != null; currLoop = currLoop.getParent()) {
                GraalError.guarantee(currLoop.getBlocks().contains(this), "block not contained in a loop it is referencing");
                currLoop.getBlocks().remove(this);
            }
        }
    }

    /**
     * A basic block that cannot have its edges edited.
     */
    static class UnmodifiableBlock extends HIRBlock {

        UnmodifiableBlock(AbstractBeginNode node, ControlFlowGraph cfg) {
            super(node, cfg);
        }

        @Override
        public int getPredecessorCount() {
            if (beginNode instanceof AbstractMergeNode) {
                if (beginNode instanceof LoopBeginNode) {
                    return ((AbstractMergeNode) beginNode).forwardEndCount() + ((LoopBeginNode) beginNode).getLoopEndCount();
                }
                return ((AbstractMergeNode) beginNode).forwardEndCount();
            } else if (beginNode instanceof StartNode) {
                return 0;
            }
            return 1;
        }

        @Override
        public int getSuccessorCount() {
            if (endNode instanceof EndNode) {
                return 1;
            } else if (endNode instanceof ControlSplitNode) {
                ControlSplitNode split = (ControlSplitNode) endNode;
                return split.getSuccessorCount();
            } else if (endNode instanceof LoopEndNode) {
                return 1;
            } else if (endNode instanceof ControlSinkNode) {
                return 0;
            } else {
                return 1;
            }
        }

        @Override
        public HIRBlock getPredecessorAt(int predIndex) {
            ControlFlowGraph cfg1 = (ControlFlowGraph) this.cfg;
            if (beginNode instanceof AbstractMergeNode) {
                if (beginNode instanceof LoopBeginNode) {
                    return cfg1.blockFor((((LoopBeginNode) beginNode).phiPredecessorAt(predIndex)));
                }
                return cfg1.blockFor(((AbstractMergeNode) beginNode).forwardEndAt(predIndex));
            }
            return cfg1.blockFor(beginNode.predecessor());
        }

        @Override
        public HIRBlock getSuccessorAt(int succIndex) {
            ControlFlowGraph cfg1 = (ControlFlowGraph) this.cfg;
            if (endNode instanceof EndNode) {
                return cfg1.blockFor(((EndNode) endNode).merge());
            } else if (endNode instanceof ControlSplitNode) {
                ControlSplitNode split = (ControlSplitNode) endNode;
                if (split instanceof IfNode) {
                    // if node fast path
                    IfNode ifNode = (IfNode) split;
                    return succIndex == 0 ? cfg1.blockFor(ifNode.trueSuccessor()) : cfg1.blockFor(ifNode.falseSuccessor());
                } else if (split instanceof SwitchNode) {
                    SwitchNode switchNode = (SwitchNode) split;
                    return cfg1.blockFor(switchNode.blockSuccessor(succIndex));
                } else if (split instanceof WithExceptionNode) {
                    GraalError.guarantee(succIndex <= 1, "With exception nodes only have 2 successors");
                    WithExceptionNode wen = (WithExceptionNode) split;
                    return succIndex == 0 ? cfg1.blockFor(wen.getPrimarySuccessor()) : cfg1.blockFor(wen.exceptionEdge());
                } else {
                    int index = 0;
                    for (Node successor : split.successors()) {
                        if (index++ == succIndex) {
                            return cfg1.blockFor(successor);
                        }
                    }
                    throw GraalError.shouldNotReachHereUnexpectedValue(split); // ExcludeFromJacocoGeneratedReport
                }
            } else if (endNode instanceof LoopEndNode) {
                return cfg1.blockFor(((LoopEndNode) endNode).loopBegin());
            } else if (endNode instanceof ControlSinkNode) {
                throw GraalError.shouldNotReachHere("Sink has no successor"); // ExcludeFromJacocoGeneratedReport
            } else {
                return cfg1.blockFor(endNode.successors().first());
            }
        }

        @Override
        public double getSuccessorProbabilityAt(int succIndex) {
            if (endNode instanceof ControlSplitNode) {
                return ((ControlSplitNode) endNode).successorProbabilities()[succIndex];
            } else {
                return 1D;
            }
        }

        @Override
        public void delete() {
            throw GraalError.shouldNotReachHere("Cannot delete a fixed block"); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public int getLinearScanNumber() {
            throw unsupported("have no linear scan properties"); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public void setLinearScanNumber(int linearScanNumber) {
            throw unsupported("have no alignment properties"); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public boolean isAligned() {
            throw unsupported("have no alignment properties"); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public void setAlign(boolean align) {
            throw unsupported("have no alignment properties"); // ExcludeFromJacocoGeneratedReport
        }

        GraalError unsupported(String reason) {
            throw GraalError.shouldNotReachHere(getClass().getSimpleName() + "s " + reason); // ExcludeFromJacocoGeneratedReport
        }
    }
}
