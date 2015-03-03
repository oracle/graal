/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.cfg;

import java.util.*;

public abstract class AbstractBlockBase<T extends AbstractBlockBase<T>> {

    protected int id;
    protected int domDepth;

    protected List<T> predecessors;
    protected List<T> successors;

    private T dominator;
    private List<T> dominated;
    private int domNumber;
    private int maxChildDomNumber;

    private boolean align;
    private int linearScanNumber;

    protected AbstractBlockBase() {
        this.id = AbstractControlFlowGraph.BLOCK_ID_INITIAL;
        this.linearScanNumber = -1;
    }

    public void setDominatorNumbers(int domNumber, int maxChildDomNumber) {
        this.domNumber = domNumber;
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

    public List<T> getPredecessors() {
        return predecessors;
    }

    public void setPredecessors(List<T> predecessors) {
        this.predecessors = predecessors;
    }

    public List<T> getSuccessors() {
        return successors;
    }

    public void setSuccessors(List<T> successors) {
        this.successors = successors;
    }

    public T getDominator() {
        return dominator;
    }

    public void setDominator(T dominator) {
        this.dominator = dominator;
        this.domDepth = dominator.domDepth + 1;
    }

    public int getDominatorDepth() {
        return domDepth;
    }

    public List<T> getDominated() {
        if (dominated == null) {
            return Collections.emptyList();
        }
        return dominated;
    }

    public void setDominated(List<T> blocks) {
        dominated = blocks;
    }

    @Override
    public String toString() {
        return "B" + id;
    }

    public int getPredecessorCount() {
        return getPredecessors().size();
    }

    public int getSuccessorCount() {
        return getSuccessors().size();
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

    public abstract boolean isLoopEnd();

    public abstract boolean isLoopHeader();

    public abstract T getPostdominator();

    public abstract double probability();

    public abstract T getDominator(int distance);
}
