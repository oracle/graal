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

import java.util.Comparator;

public abstract class AbstractBlockBase<T extends AbstractBlockBase<T>> {
    public static final double[] EMPTY_PROBABILITY_ARRAY = new double[0];
    public static final double[] SINGLETON_PROBABILITY_ARRAY = new double[]{1.0};

    protected int id;
    protected int domDepth;

    private T pred0;
    private T[] extraPred;

    private T succ0;
    private T[] extraSucc;

    private double succ0Probability;
    private double[] extraProbabilities;

    private T dominator;
    private T firstDominated;
    private T dominatedSibling;
    private int domNumber;
    private int maxChildDomNumber;

    private boolean align;
    private int linearScanNumber;

    protected AbstractBlockBase() {
        this.id = AbstractControlFlowGraph.BLOCK_ID_INITIAL;
        this.linearScanNumber = -1;
        this.domNumber = -1;
        this.maxChildDomNumber = -1;
    }

    public void setDominatorNumber(int domNumber) {
        this.domNumber = domNumber;
    }

    public void setMaxChildDomNumber(int maxChildDomNumber) {
        this.maxChildDomNumber = maxChildDomNumber;
    }

    public int getDominatorNumber() {
        return domNumber;
    }

    public int getMaxChildDominatorNumber() {
        return this.maxChildDomNumber;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    private static <T extends AbstractBlockBase<T>> int getCount(T first, T[] extra) {
        return first == null ? 0 : 1 + (extra == null ? 0 : extra.length);
    }

    private static <T extends AbstractBlockBase<T>> T getAtIndex(T first, T[] extra, int index) {
        return index == 0 ? first : extra[index - 1];
    }

    public int getPredecessorCount() {
        return getCount(pred0, extraPred);
    }

    public int getSuccessorCount() {
        return getCount(succ0, extraSucc);
    }

    private static <T extends AbstractBlockBase<T>> boolean contains(T key, T first, T[] extra) {
        if (first == key) {
            return true;
        }
        if (extra != null) {
            for (int i = 0; i < extra.length; i++) {
                if (extra[i] == key) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean containsPred(T key) {
        return contains(key, pred0, extraPred);
    }

    public boolean containsSucc(T key) {
        return contains(key, succ0, extraSucc);
    }

    public T getPredecessorAt(int predIndex) {
        assert predIndex < getPredecessorCount();
        return getAtIndex(pred0, extraPred, predIndex);
    }

    public T getSuccessorAt(int succIndex) {
        assert succIndex < getSuccessorCount();
        return getAtIndex(succ0, extraSucc, succIndex);
    }

    public void setPredecessor(T p0) {
        pred0 = p0;
    }

    @SuppressWarnings("unchecked")
    public void setPredecessors(T p0, T[] rest) {
        this.pred0 = p0;
        this.extraPred = rest;
    }

    public void setSuccessor(T s0) {
        succ0 = s0;
        succ0Probability = 1.0D;
    }

    @SuppressWarnings("unchecked")
    public void setSuccessors(T s0, T[] rest) {
        this.succ0 = s0;
        this.extraSucc = rest;
    }

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

    public T getDominator() {
        return dominator;
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
        this.dominator = dominator;
        this.domDepth = dominator.domDepth + 1;
    }

    /**
     * Level in the dominator tree starting with 0 for the start block.
     */
    public int getDominatorDepth() {
        return domDepth;
    }

    public T getFirstDominated() {
        return this.firstDominated;
    }

    public void setFirstDominated(T block) {
        this.firstDominated = block;
    }

    public T getDominatedSibling() {
        return this.dominatedSibling;
    }

    public void setDominatedSibling(T block) {
        this.dominatedSibling = block;
    }

    @Override
    public String toString() {
        return "B" + id;
    }

    public int getLinearScanNumber() {
        return linearScanNumber;
    }

    public void setLinearScanNumber(int linearScanNumber) {
        this.linearScanNumber = linearScanNumber;
    }

    public boolean isAligned() {
        return align;
    }

    public void setAlign(boolean align) {
        this.align = align;
    }

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
    public abstract long numBackedges();

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
