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
package org.graalvm.compiler.core.common.cfg;

import static org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph.BLOCK_ID_INITIAL;

import java.util.Comparator;

import org.graalvm.compiler.core.common.RetryableBailoutException;

public abstract class AbstractBlockBase<T extends AbstractBlockBase<T>> {
    public static final double[] EMPTY_PROBABILITY_ARRAY = new double[0];
    public static final double[] SINGLETON_PROBABILITY_ARRAY = new double[]{1.0};

    protected char id = BLOCK_ID_INITIAL;
    protected char domDepth = 0;

    private char dominator = BLOCK_ID_INITIAL;
    private char firstDominated = BLOCK_ID_INITIAL;
    private char dominatedSibling = BLOCK_ID_INITIAL;

    private char domNumber = BLOCK_ID_INITIAL;
    private char maxChildDomNumber = BLOCK_ID_INITIAL;
    protected final AbstractControlFlowGraph<T> cfg;

    protected AbstractBlockBase(AbstractControlFlowGraph<T> cfg) {
        this.cfg = cfg;
    }

    public void setDominatorNumber(char domNumber) {
        this.domNumber = domNumber;
    }

    public void setMaxChildDomNumber(char maxChildDomNumber) {
        this.maxChildDomNumber = maxChildDomNumber;
    }

    public int getDominatorNumber() {
        if (domNumber == BLOCK_ID_INITIAL) {
            return -1;
        }
        return domNumber;
    }

    public int getMaxChildDominatorNumber() {
        if (maxChildDomNumber == BLOCK_ID_INITIAL) {
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

    public void setId(char id) {
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
        return dominator != BLOCK_ID_INITIAL ? cfg.getBlocks()[dominator] : null;
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
            assert d.getLoopDepth() == getLoopDepth() - 1;
            assert d.getLoop() != getLoop();
            return d;
        }

        while (d.getLoop() != getLoop()) {
            // We have a case where our dominator is in a different loop. Move further along
            // the domiantor tree until we hit our loop again.
            d = d.getDominator();
        }

        assert d.getLoopDepth() <= getLoopDepth();

        return d;
    }

    public void setDominator(T dominator) {
        this.dominator = safeCast(dominator.getId());
        this.domDepth = addExact(dominator.domDepth, 1);
    }

    public static char addExact(char x, int y) {
        int result = x + y;
        char res = (char) (x + y);
        if (res != result) {
            throw new RetryableBailoutException("Graph too large to safely compile in reasonable time. Dominator tree depth ids create numerical overflows");
        }
        return res;
    }

    public static char safeCast(int i) {
        if (i < 0 || i > AbstractControlFlowGraph.LAST_VALID_BLOCK_INDEX) {
            throw new RetryableBailoutException("Graph too large to safely compile in reasonable time.");
        }
        return (char) i;
    }

    /**
     * Level in the dominator tree starting with 0 for the start block.
     */
    public int getDominatorDepth() {
        return domDepth;
    }

    public T getFirstDominated() {
        return firstDominated != BLOCK_ID_INITIAL ? cfg.getBlocks()[firstDominated] : null;
    }

    public void setFirstDominated(T block) {
        this.firstDominated = safeCast(block.getId());
    }

    public T getDominatedSibling() {
        return dominatedSibling != BLOCK_ID_INITIAL ? cfg.getBlocks()[dominatedSibling] : null;
    }

    public void setDominatedSibling(T block) {
        if (block != null) {
            this.dominatedSibling = safeCast(block.getId());
        }
    }

    @Override
    public String toString() {
        return "B" + (int) id;
    }

    public abstract int getLinearScanNumber();

    public abstract void setLinearScanNumber(int linearScanNumber);

    public abstract boolean isAligned();

    public abstract void setAlign(boolean align);

    public abstract boolean isExceptionEntry();

    public abstract Loop<T> getLoop();

    public abstract char getLoopDepth();

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

    @Override
    public int hashCode() {
        return id;
    }

    public static class BlockIdComparator implements Comparator<AbstractBlockBase<?>> {
        @Override
        public int compare(AbstractBlockBase<?> o1, AbstractBlockBase<?> o2) {
            return Integer.compare(o1.getId(), o2.getId());
        }
    }

    public static final BlockIdComparator BLOCK_ID_COMPARATOR = new BlockIdComparator();
}
