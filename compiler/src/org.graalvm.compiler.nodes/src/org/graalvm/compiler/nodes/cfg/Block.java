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
package org.graalvm.compiler.nodes.cfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;
import org.graalvm.compiler.nodes.ProfileData.ProfileSource;
import org.graalvm.compiler.nodes.extended.SwitchNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.memory.MultiMemoryKill;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.word.LocationIdentity;

public abstract class Block extends AbstractBlockBase<Block> {
    public static final Block[] EMPTY_ARRAY = new Block[0];

    protected final AbstractBeginNode beginNode;
    protected FixedNode endNode;

    protected double relativeFrequency = -1D;
    protected ProfileSource frequencySource;
    protected Loop<Block> loop;

    // Extra data for cases where the loop information is no longer fully up to date due to blocks
    // being deleted during LIR control flow optimization.
    private boolean markedAsLoopEnd = false;
    protected long numBackedges = -1;

    protected int postdominator = -1;
    private LocationSet killLocations;
    private LocationSet killLocationsBetweenThisAndDominator;

    protected final ControlFlowGraph cfg;

    public Block(AbstractBeginNode node, ControlFlowGraph cfg) {
        this.beginNode = node;
        this.cfg = cfg;
    }

    public AbstractBeginNode getBeginNode() {
        return beginNode;
    }

    public FixedNode getEndNode() {
        return endNode;
    }

    @Override
    public Loop<Block> getLoop() {
        return loop;
    }

    public void setLoop(Loop<Block> loop) {
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
    public long numBackedges() {
        return numBackedges;
    }

    @Override
    public boolean isLoopEnd() {
        return markedAsLoopEnd || getEndNode() instanceof LoopEndNode;
    }

    public void markAsLoopEnd() {
        markedAsLoopEnd = true;
    }

    @Override
    public boolean isExceptionEntry() {
        Node predecessor = getBeginNode().predecessor();
        return predecessor != null && predecessor instanceof WithExceptionNode && getBeginNode() == ((WithExceptionNode) predecessor).exceptionEdge();
    }

    public Block getFirstPredecessor() {
        return getPredecessorAt(0);
    }

    public Block getFirstSuccessor() {
        return getSuccessorAt(0);
    }

    @Override
    public Block getPostdominator() {
        return postdominator >= 0 ? getRpo()[postdominator] : null;
    }

    @Override
    public Block[] getRpo() {
        return cfg.reversePostOrder();
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
            assert !(cur instanceof AbstractBeginNode);
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
     * Finally, the frequency for basic {@link Block}s is set during {@link ControlFlowGraph}
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
    public Block getDominator(int distance) {
        Block result = this;
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
            Block stopBlock = getDominator();
            if (this.isLoopHeader()) {
                assert stopBlock.getLoopDepth() < this.getLoopDepth();
                dominatorResult.addAll(((HIRLoop) this.getLoop()).getKillLocations());
            } else {
                for (int i = 0; i < getPredecessorCount(); i++) {
                    Block b = getPredecessorAt(i);
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

    private void calcKillLocationsBetweenThisAndTarget(LocationSet result, Block stopBlock) {
        assert AbstractControlFlowGraph.dominates(stopBlock, this);
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

    protected void setPostDominator(Block postdominator) {
        if (postdominator != null) {
            this.postdominator = postdominator.getId();
        }
    }

    /**
     * Checks whether {@code this} block is in the same loop or an outer loop of the block given as
     * parameter.
     */
    public boolean isInSameOrOuterLoopOf(Block block) {
        if (this.loop == null) {
            // We are in no loop, so this holds true for every other block.
            return true;
        }

        Loop<Block> l = block.loop;
        while (l != null) {
            if (l == this.loop) {
                return true;
            }
            l = l.getParent();
        }

        return false;
    }

    public static void computeLoopPredecessors(NodeMap<Block> nodeMap, ModifiableBasicBlock block, LoopBeginNode loopBeginNode) {
        int forwardEndCount = loopBeginNode.forwardEndCount();
        LoopEndNode[] loopEnds = loopBeginNode.orderedLoopEnds();
        int firstPred = nodeMap.get(loopBeginNode.forwardEndAt(0)).getId();
        int[] extraPred = new int[forwardEndCount + loopEnds.length - 1];
        for (int i = 1; i < forwardEndCount; ++i) {
            extraPred[i - 1] = nodeMap.get(loopBeginNode.forwardEndAt(i)).getId();
        }
        for (int i = 0; i < loopEnds.length; ++i) {
            extraPred[i + forwardEndCount - 1] = nodeMap.get(loopEnds[i]).getId();
        }
        block.setPredecessors(firstPred, extraPred);
    }

    public static void assignPredecessorsAndSuccessors(Block[] blocks, ControlFlowGraph cfg) {
        for (int bI = 0; bI < blocks.length; bI++) {
            ModifiableBasicBlock b = (ModifiableBasicBlock) blocks[bI];
            FixedNode blockEndNode = b.getEndNode();
            if (blockEndNode instanceof EndNode) {
                EndNode endNode = (EndNode) blockEndNode;
                Block suxBlock = cfg.getNodeToBlock().get(endNode.merge());
                b.setSuccessor(suxBlock.getId());
            } else if (blockEndNode instanceof ControlSplitNode) {
                ControlSplitNode split = (ControlSplitNode) blockEndNode;
                int index = 0;
                int succ0 = -1;
                int[] extraSucc = new int[split.getSuccessorCount() - 1];
                for (Node sux : blockEndNode.successors()) {
                    ModifiableBasicBlock sucBlock = (ModifiableBasicBlock) cfg.getNodeToBlock().get(sux);
                    if (index == 0) {
                        succ0 = sucBlock.getId();
                    } else {
                        extraSucc[index - 1] = sucBlock.getId();
                    }
                    index++;
                    sucBlock.setPredecessor(b.getId());
                }
                b.setSuccessors(succ0, extraSucc);
                double[] succP = ((ControlSplitNode) blockEndNode).successorProbabilities();
                b.setSuccessorProbabilities(succP[0], Arrays.copyOfRange(succP, 1, succP.length));
            } else if (blockEndNode instanceof LoopEndNode) {
                LoopEndNode loopEndNode = (LoopEndNode) blockEndNode;
                b.setSuccessor(cfg.getNodeToBlock().get(loopEndNode.loopBegin()).getId());
            } else if (blockEndNode instanceof ControlSinkNode) {
                // nothing to do
            } else {
                assert !(blockEndNode instanceof AbstractEndNode) : "Algorithm only supports EndNode and LoopEndNode.";
                for (Node suxNode : blockEndNode.successors()) {
                    ModifiableBasicBlock sux = (ModifiableBasicBlock) cfg.getNodeToBlock().get(suxNode);
                    sux.setPredecessor(b.getId());
                }
                assert blockEndNode.successors().count() == 1 : "Node " + blockEndNode;
                Block sequentialSuc = cfg.getNodeToBlock().get(blockEndNode.successors().first());
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

    public static class ModifiableBasicBlock extends Block {

        private boolean align;
        private int linearScanNumber = -1;

        public ModifiableBasicBlock(AbstractBeginNode node, ControlFlowGraph cfg) {
            super(node, cfg);
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
            return getCount(pred0, extraPred);
        }

        @Override
        public int getSuccessorCount() {
            return getCount(succ0, extraSucc);
        }

        // the following fields are all indices into the PRO array
        private int pred0 = -1;
        private int[] extraPred;
        private int succ0 = -1;
        private int[] extraSucc;
        private double succ0Probability;
        private double[] extraProbabilities;

        @Override
        public Block getPredecessorAt(int predIndex) {
            assert predIndex < getPredecessorCount();
            return getRpo()[getAtIndex(pred0, extraPred, predIndex)];
        }

        @Override
        public Block getSuccessorAt(int succIndex) {
            assert succIndex < getSuccessorCount();
            return getRpo()[getAtIndex(succ0, extraSucc, succIndex)];
        }

        public void setPredecessor(int p0) {
            pred0 = p0;
        }

        @SuppressWarnings("unchecked")
        public void setPredecessors(int p0, int[] rest) {
            this.pred0 = p0;
            this.extraPred = rest;
        }

        public void setSuccessor(int s0) {
            succ0 = s0;
            succ0Probability = 1.0D;
        }

        @SuppressWarnings("unchecked")
        public void setSuccessors(int s0, int[] rest) {
            this.succ0 = s0;
            this.extraSucc = rest;
        }

        @Override
        public double getSuccessorProbabilityAt(int succIndex) {
            if (succIndex == 0) {
                return succ0Probability;
            }
            return extraProbabilities[succIndex - 1];
        }

        public void setSuccessorProbabilities(double succ0Probability, double[] extraSuccProbabilities) {
            this.succ0Probability = succ0Probability;
            this.extraProbabilities = extraSuccProbabilities;
        }

        private static int getCount(int first, int[] extra) {
            return first == -1 ? 0 : 1 + (extra == null ? 0 : extra.length);
        }

        private static int getAtIndex(int first, int[] extra, int index) {
            return index == 0 ? first : extra[index - 1];
        }

        @Override
        public void delete() {

            // adjust successor and predecessor lists
            GraalError.guarantee(getSuccessorCount() == 1, "can only delete blocks with exactly one successor");
            ModifiableBasicBlock next = (ModifiableBasicBlock) getSuccessorAt(0);
            int predecessorCount = getPredecessorCount();
            for (int i = 0; i < getPredecessorCount(); i++) {
                ModifiableBasicBlock pred = (ModifiableBasicBlock) getPredecessorAt(i);
                int[] newPredSuccs = new int[pred.getSuccessorCount()];
                for (int j = 0; j < pred.getSuccessorCount(); j++) {
                    Block predSuccAt = pred.getSuccessorAt(j);
                    if (predSuccAt == this) {
                        newPredSuccs[j] = next.getId();
                    } else {
                        newPredSuccs[j] = predSuccAt.getId();
                    }
                }
                pred.setSuccessors(newPredSuccs[0], Arrays.copyOfRange(newPredSuccs, 1, newPredSuccs.length));

                if (isLoopEnd()) {
                    // The predecessor becomes a loop end.
                    pred.markAsLoopEnd();
                }
            }
            if (isLoopEnd()) {
                GraalError.guarantee(next.isLoopHeader(), "a loop end's successor must be a loop header");
                next.numBackedges += predecessorCount - 1;
            }

            ArrayList<Block> newPreds = new ArrayList<>();
            for (int i = 0; i < next.getPredecessorCount(); i++) {
                Block curPred = next.getPredecessorAt(i);
                if (curPred == this) {
                    for (int j = 0; j < getPredecessorCount(); j++) {
                        newPreds.add(getPredecessorAt(j));
                    }
                } else {
                    newPreds.add(curPred);
                }
            }

            Block firstPred = newPreds.get(0);
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

            // next.setPredecessors(newPreds.toArray(Block.EMPTY_ARRAY));

            // Remove the current block from the blocks of the loops it belongs to
            for (Loop<Block> currLoop = loop; currLoop != null; currLoop = currLoop.getParent()) {
                GraalError.guarantee(currLoop.getBlocks().contains(this), "block not contained in a loop it is referencing");
                currLoop.getBlocks().remove(this);
            }
        }
    }

    public static class GraphBasedBasicBlock extends Block {

        public GraphBasedBasicBlock(AbstractBeginNode node, ControlFlowGraph cfg) {
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
        public Block getPredecessorAt(int predIndex) {
            if (beginNode instanceof AbstractMergeNode) {
                if (beginNode instanceof LoopBeginNode) {
                    return cfg.blockFor((((LoopBeginNode) beginNode).phiPredecessorAt(predIndex)));
                }
                return cfg.blockFor(((AbstractMergeNode) beginNode).forwardEndAt(predIndex));
            }
            return cfg.blockFor(beginNode.predecessor());
        }

        @Override
        public Block getSuccessorAt(int succIndex) {
            if (endNode instanceof EndNode) {
                return cfg.blockFor(((EndNode) endNode).merge());
            } else if (endNode instanceof ControlSplitNode) {
                ControlSplitNode split = (ControlSplitNode) endNode;
                if (split instanceof IfNode) {
                    // if node fast path
                    IfNode ifNode = (IfNode) split;
                    return succIndex == 0 ? cfg.blockFor(ifNode.trueSuccessor()) : cfg.blockFor(ifNode.falseSuccessor());
                } else if (split instanceof SwitchNode) {
                    SwitchNode switchNode = (SwitchNode) split;
                    return cfg.blockFor(switchNode.blockSuccessor(succIndex));
                } else if (split instanceof WithExceptionNode) {
                    GraalError.guarantee(succIndex <= 1, "With exception nodes only have 2 successors");
                    WithExceptionNode wen = (WithExceptionNode) split;
                    return succIndex == 0 ? cfg.blockFor(wen.getPrimarySuccessor()) : cfg.blockFor(wen.exceptionEdge());
                } else {
                    int index = 0;
                    for (Node successor : split.successors()) {
                        if (index++ == succIndex) {
                            return cfg.blockFor(successor);
                        }
                    }
                    throw GraalError.shouldNotReachHere();
                }
            } else if (endNode instanceof LoopEndNode) {
                return cfg.blockFor(((LoopEndNode) endNode).loopBegin());
            } else if (endNode instanceof ControlSinkNode) {
                throw GraalError.shouldNotReachHere("Sink has no successor");
            } else {
                return cfg.blockFor(endNode.successors().first());
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
            throw GraalError.shouldNotReachHere("Do not delete graph based blocks");
        }

        @Override
        public int getLinearScanNumber() {
            throw GraalError.shouldNotReachHere("Graph based blocks should not be used for backend operations");
        }

        @Override
        public void setLinearScanNumber(int linearScanNumber) {
            throw GraalError.shouldNotReachHere("Graph based blocks should not be used for backend operations");
        }

        @Override
        public boolean isAligned() {
            throw GraalError.shouldNotReachHere("Graph based blocks should not be used for backend operations");
        }

        @Override
        public void setAlign(boolean align) {
            throw GraalError.shouldNotReachHere("Graph based blocks should not be used for backend operations");
        }
    }

}
