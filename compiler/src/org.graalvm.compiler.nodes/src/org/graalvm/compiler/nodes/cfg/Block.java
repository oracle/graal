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
import java.util.Iterator;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;
import org.graalvm.compiler.nodes.ProfileData.ProfileSource;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.memory.MultiMemoryKill;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.word.LocationIdentity;

public final class Block extends AbstractBlockBase<Block> {
    public static final Block[] EMPTY_ARRAY = new Block[0];

    protected final AbstractBeginNode beginNode;

    protected FixedNode endNode;

    protected double relativeFrequency = -1D;
    protected ProfileSource frequencySource;
    private Loop<Block> loop;

    protected Block postdominator;
    private LocationSet killLocations;
    private LocationSet killLocationsBetweenThisAndDominator;

    public Block(AbstractBeginNode node) {
        this.beginNode = node;
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
    public boolean isLoopEnd() {
        return getEndNode() instanceof LoopEndNode;
    }

    @Override
    public boolean isExceptionEntry() {
        Node predecessor = getBeginNode().predecessor();
        return predecessor != null && predecessor instanceof WithExceptionNode && getBeginNode() == ((WithExceptionNode) predecessor).exceptionEdge();
    }

    public Block getFirstPredecessor() {
        return getPredecessors()[0];
    }

    public Block getFirstSuccessor() {
        return getSuccessors()[0];
    }

    public Block getEarliestPostDominated() {
        Block b = this;
        while (true) {
            Block dom = b.getDominator();
            if (dom != null && dom.getPostdominator() == b) {
                b = dom;
            } else {
                break;
            }
        }
        return b;
    }

    @Override
    public Block getPostdominator() {
        return postdominator;
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
        return new Iterable<FixedNode>() {

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
                    sb.append('B').append(getSuccessors()[i].getId());
                }
                sb.append(']');
            }

            if (getPredecessorCount() > 0) {
                sb.append(" <-[");
                for (int i = 0; i < getPredecessorCount(); ++i) {
                    if (i != 0) {
                        sb.append(',');
                    }
                    sb.append('B').append(getPredecessors()[i].getId());
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
            if (node instanceof SingleMemoryKill) {
                LocationIdentity identity = ((SingleMemoryKill) node).getKilledLocationIdentity();
                result.add(identity);
            } else if (node instanceof MultiMemoryKill) {
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
                for (Block b : this.getPredecessors()) {
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

    @Override
    public void delete() {

        // adjust successor and predecessor lists
        Block next = getSuccessors()[0];
        for (Block pred : getPredecessors()) {
            Block[] predSuccs = pred.successors;
            Block[] newPredSuccs = new Block[predSuccs.length];
            for (int i = 0; i < predSuccs.length; ++i) {
                if (predSuccs[i] == this) {
                    newPredSuccs[i] = next;
                } else {
                    newPredSuccs[i] = predSuccs[i];
                }
            }
            pred.setSuccessors(newPredSuccs);
        }

        ArrayList<Block> newPreds = new ArrayList<>();
        for (int i = 0; i < next.getPredecessorCount(); i++) {
            Block curPred = next.getPredecessors()[i];
            if (curPred == this) {
                for (Block b : getPredecessors()) {
                    newPreds.add(b);
                }
            } else {
                newPreds.add(curPred);
            }
        }

        next.setPredecessors(newPreds.toArray(new Block[0]));
    }

    protected void setPostDominator(Block postdominator) {
        this.postdominator = postdominator;
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
}
