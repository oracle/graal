/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common.cfg;

import java.util.Comparator;

import jdk.graal.compiler.debug.Assertions;

/**
 * Abstract representation of a basic block in the Graal IR. A basic block is the longest sequence
 * of instructions without a jump in between. A sequential ordering of blocks is maintained by
 * {@code AbstractControlFlowGraph} (typically in a reverse post order fashion). Both the frontend
 * of Graal as well as the backend operate on the same abstract block representation.
 *
 * The frontend builds, while scheduling the graph, the final control flow graph used by the
 * backend. Since Graal has a strict dependency separation between frontend and backend this
 * abstract basic block is the coupling API.
 */
public abstract class BasicBlock<T extends BasicBlock<T>> {

    /**
     * Id of this basic block. The id is concurrently used as a unique identifier for the block as
     * well as its index into the @{@link #getBlocks()} array of the associated
     * {@link AbstractControlFlowGraph}.
     */
    protected int id = AbstractControlFlowGraph.INVALID_BLOCK_ID;
    /**
     * Block id of the dominator of this block. See
     * <a href="https://en.wikipedia.org/wiki/Dominator_(graph_theory)">dominator theory<a/> for
     * details.
     */
    private int dominator = AbstractControlFlowGraph.INVALID_BLOCK_ID;
    /**
     * Block id of the first dominated block. A block can dominate more basic blocks: they are
     * connected sequentially via the {@link BasicBlock#dominatedSibling} index pointer into the
     * {@link #getBlocks()}array.
     */
    private int firstDominated = AbstractControlFlowGraph.INVALID_BLOCK_ID;
    /**
     * The dominated sibling of this block. See {@link BasicBlock#firstDominated} for details.
     */
    private int dominatedSibling = AbstractControlFlowGraph.INVALID_BLOCK_ID;
    /**
     * See {@link #getDominatorDepth()}.
     */
    protected int domDepth = 0;
    /**
     * Dominator number of this block: the dominator number is assigned for each basic block when
     * building the dominator tree. It is a numbering scheme used for fast and efficient dominance
     * checks. It attributes each basic block a numerical value that adheres to the following
     * constraints
     * <ul>
     * <li>b1.domNumber <= b2.domNumber iff b1 dominates b2</li>
     * <li>b1.domNumber == b2.domNumber iff b1 == b2</li>
     * </ul>
     *
     * Two distinct branches in the dominator tree always have distinct ids. This means all
     * dominated blocks between {@code this} and the deepest dominated block in dominator tree are
     * within the {@code [domNumber;maxChildDomNumber]} interval.
     */
    private int domNumber = AbstractControlFlowGraph.INVALID_BLOCK_ID;
    /**
     * The maximum child dominator number, i.e., the maximum dom number of the deepest dominated
     * block along the particular branch on the dominator tree rooted at this block.
     *
     * See {@link #domNumber} for details.
     */
    private int maxChildDomNumber = AbstractControlFlowGraph.INVALID_BLOCK_ID;
    /**
     * Indicates if this block is a target of an indirect branch.
     */
    private boolean indirectBranchTarget = false;
    protected final AbstractControlFlowGraph<T> cfg;

    protected BasicBlock(AbstractControlFlowGraph<T> cfg) {
        this.cfg = cfg;
    }

    public void setDominatorNumber(int domNumber) {
        this.domNumber = domNumber;
    }

    public void setMaxChildDomNumber(int maxChildDomNumber) {
        this.maxChildDomNumber = maxChildDomNumber;
    }

    public int getDominatorNumber() {
        if (domNumber == AbstractControlFlowGraph.INVALID_BLOCK_ID) {
            return -1;
        }
        return domNumber;
    }

    public int getMaxChildDominatorNumber() {
        if (maxChildDomNumber == AbstractControlFlowGraph.INVALID_BLOCK_ID) {
            return -1;
        }
        return this.maxChildDomNumber;
    }

    public int getId() {
        return id;
    }

    /**
     * Gets the block ordering of the graph in which this block lies. The {@linkplain #getId() id}
     * of each block in the graph is an index into the returned array.
     */
    public T[] getBlocks() {
        return cfg.getBlocks();
    }

    public void setId(int id) {
        assert id <= AbstractControlFlowGraph.LAST_VALID_BLOCK_INDEX : id;
        this.id = id;
    }

    public abstract int getPredecessorCount();

    public abstract int getSuccessorCount();

    private boolean contains(T key, boolean usePred) {
        for (int i = 0; i < (usePred ? getPredecessorCount() : getSuccessorCount()); i++) {
            T b = usePred ? getPredecessorAt(i) : getSuccessorAt(i);
            if (b == key) {
                return true;
            }
        }
        return false;
    }

    public boolean containsPred(T key) {
        return contains(key, true);
    }

    public boolean containsSucc(T key) {
        return contains(key, false);
    }

    public abstract T getPredecessorAt(int predIndex);

    public abstract T getSuccessorAt(int succIndex);

    public abstract double getSuccessorProbabilityAt(int succIndex);

    public T getDominator() {
        return dominator != AbstractControlFlowGraph.INVALID_BLOCK_ID ? cfg.getBlocks()[dominator] : null;
    }

    /**
     * Returns the next dominator of this block that is either in the same loop of this block or in
     * an outer loop.
     *
     * @return the next dominator while skipping over loops
     */
    public T getDominatorSkipLoops() {
        T d = getDominator();

        if (d == null) {
            // We are at the start block and don't have a dominator.
            return null;
        }

        if (isLoopHeader()) {
            // We are moving out of current loop => just return dominator.
            assert d.getLoopDepth() == getLoopDepth() - 1 : Assertions.errorMessageContext("d", d, "d.getloop", d.getLoop(), "this", this, "this.getLoop", getLoop());
            assert d.getLoop() != getLoop() : Assertions.errorMessageContext("d", d, "d.getloop", d.getLoop(), "this", this, "this.getLoop", getLoop());
            return d;
        }

        while (d.getLoop() != getLoop()) {
            // We have a case where our dominator is in a different loop. Move further along
            // the domiantor tree until we hit our loop again.
            d = d.getDominator();
        }

        assert d.getLoopDepth() <= getLoopDepth() : Assertions.errorMessageContext("d", d, "d.getloop", d.getLoop(), "d.loopDepth", d.getLoopDepth(), "this.loopDepth", this.getLoopDepth());

        return d;
    }

    public void setDominator(T dominator) {
        this.dominator = dominator.getId();
        this.domDepth = dominator.domDepth + 1;
    }

    /**
     * Level in the dominator tree starting with 0 for the start block.
     */
    public int getDominatorDepth() {
        return domDepth;
    }

    public T getFirstDominated() {
        return firstDominated != AbstractControlFlowGraph.INVALID_BLOCK_ID ? cfg.getBlocks()[firstDominated] : null;
    }

    public void setFirstDominated(T block) {
        this.firstDominated = block.getId();
    }

    public T getDominatedSibling() {
        return dominatedSibling != AbstractControlFlowGraph.INVALID_BLOCK_ID ? cfg.getBlocks()[dominatedSibling] : null;
    }

    public void setDominatedSibling(T block) {
        if (block != null) {
            this.dominatedSibling = block.getId();
        }
    }

    public final boolean dominates(BasicBlock<?> other) {
        return AbstractControlFlowGraph.dominates(this, other);
    }

    public final boolean strictlyDominates(BasicBlock<T> other) {
        return AbstractControlFlowGraph.strictlyDominates(this, other);
    }

    public void setIndirectBranchTarget() {
        indirectBranchTarget = true;
    }

    public boolean isIndirectBranchTarget() {
        return indirectBranchTarget;
    }

    @Override
    public String toString() {
        return "B" + id;
    }

    public abstract int getLinearScanNumber();

    public abstract void setLinearScanNumber(int linearScanNumber);

    public abstract boolean isAligned();

    public abstract void setAlign(boolean align);

    public abstract boolean isExceptionEntry();

    public abstract Loop<T> getLoop();

    public abstract int getLoopDepth();

    public abstract void delete();

    public abstract boolean isLoopEnd();

    public abstract boolean isLoopHeader();

    /**
     * If this block {@linkplain #isLoopHeader() is a loop header}, returns the number of the loop's
     * backedges. Note that due to control flow optimizations after computing loops this value may
     * differ from that computed via {@link #getLoop()}. Returns -1 if this is not a loop header.
     */
    public abstract int numBackedges();

    public abstract T getPostdominator();

    public abstract double getRelativeFrequency();

    public abstract T getDominator(int distance);

    /**
     * Determine if this block is modifiable in the context of its {@link AbstractControlFlowGraph}.
     * This includes deleting this block, modifying its predecessors and successors.
     */
    public abstract boolean isModifiable();

    @Override
    public int hashCode() {
        return id;
    }

    public static class BlockIdComparator implements Comparator<BasicBlock<?>> {
        @Override
        public int compare(BasicBlock<?> o1, BasicBlock<?> o2) {
            return Integer.compare(o1.getId(), o2.getId());
        }
    }

    public static final BlockIdComparator BLOCK_ID_COMPARATOR = new BlockIdComparator();

    public AbstractControlFlowGraph<T> getCfg() {
        return cfg;
    }
}
