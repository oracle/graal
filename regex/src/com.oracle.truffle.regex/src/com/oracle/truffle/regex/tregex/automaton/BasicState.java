/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.regex.tregex.automaton;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;

/**
 * Abstract base class for states of an automaton.
 */
public abstract class BasicState<S extends BasicState<S, T>, T extends AbstractTransition<S, T>> implements AbstractState<S, T> {

    protected static final byte FLAG_ANCHORED_INITIAL_STATE = 1 << 0;
    protected static final byte FLAG_UN_ANCHORED_INITIAL_STATE = 1 << 1;
    protected static final byte FLAG_ANCHORED_FINAL_STATE = 1 << 2;
    protected static final byte FLAG_UN_ANCHORED_FINAL_STATE = 1 << 3;
    protected static final byte FLAG_ANY_INITIAL_STATE = FLAG_ANCHORED_INITIAL_STATE | FLAG_UN_ANCHORED_INITIAL_STATE;
    protected static final byte FLAG_ANY_FINAL_STATE = FLAG_ANCHORED_FINAL_STATE | FLAG_UN_ANCHORED_FINAL_STATE;
    protected static final byte FLAG_ANY_INITIAL_OR_FINAL_STATE = FLAG_ANY_INITIAL_STATE | FLAG_ANY_FINAL_STATE;
    /**
     * Number of flag bits occupied by this class. Child classes may add their own flags with
     * {@code byte NEW_FLAG = 1 << N_FLAGS; byte NEW_FLAG2 = 1 << (N_FLAGS + 1)} etc.
     */
    protected static final int N_FLAGS = 4;

    private final int id;
    @CompilationFinal private byte flags;
    @CompilationFinal(dimensions = 1) private T[] successors;
    @CompilationFinal(dimensions = 1) private T[] predecessors;
    private int nPredecessors = 0;

    /**
     * @param id unique id.
     * @param emptyTransitions static final empty array of transitions. This will be shared for all
     *            empty transition arrays.
     */
    protected BasicState(int id, T[] emptyTransitions) {
        this(id, (byte) 0, emptyTransitions);
    }

    protected BasicState(int id, byte flags, T[] emptyTransitions) {
        this.id = id;
        this.flags = flags;
        this.successors = emptyTransitions;
        this.predecessors = emptyTransitions;
    }

    @Override
    public final int getId() {
        return id;
    }

    protected byte getFlags() {
        return flags;
    }

    protected boolean getFlag(byte flag) {
        return (flags & flag) != 0;
    }

    protected void setFlag(byte flag) {
        flags |= flag;
    }

    protected void setFlag(byte flag, boolean value) {
        if (value) {
            flags |= flag;
        } else {
            flags &= ~flag;
        }
    }

    public boolean isInitialState() {
        return getFlag(FLAG_ANY_INITIAL_STATE);
    }

    /**
     * Anchored final states are implicitly guarded by a {@code ^}-{@link PositionAssertion}.
     */
    public boolean isAnchoredInitialState() {
        return getFlag(FLAG_ANCHORED_INITIAL_STATE);
    }

    public void setAnchoredInitialState() {
        setFlag(FLAG_ANCHORED_INITIAL_STATE);
    }

    public boolean isUnAnchoredInitialState() {
        return getFlag(FLAG_UN_ANCHORED_INITIAL_STATE);
    }

    public void setUnAnchoredInitialState() {
        setFlag(FLAG_UN_ANCHORED_INITIAL_STATE);
    }

    public void setUnAnchoredInitialState(boolean value) {
        setFlag(FLAG_UN_ANCHORED_INITIAL_STATE, value);
    }

    public boolean isFinalState() {
        return getFlag(FLAG_ANY_FINAL_STATE);
    }

    /**
     * Anchored final states are implicitly guarded by a {@code $}-{@link PositionAssertion}.
     */
    public boolean isAnchoredFinalState() {
        return getFlag(FLAG_ANCHORED_FINAL_STATE);
    }

    public void setAnchoredFinalState() {
        setFlag(FLAG_ANCHORED_FINAL_STATE);
    }

    public boolean isUnAnchoredFinalState() {
        return getFlag(FLAG_UN_ANCHORED_FINAL_STATE);
    }

    public void setUnAnchoredFinalState() {
        setFlag(FLAG_UN_ANCHORED_FINAL_STATE);
    }

    public boolean isAnchoredInitialState(boolean forward) {
        return forward ? isAnchoredInitialState() : isAnchoredFinalState();
    }

    public boolean isUnAnchoredInitialState(boolean forward) {
        return forward ? isUnAnchoredInitialState() : isUnAnchoredFinalState();
    }

    public boolean isInitialState(boolean forward) {
        return forward ? isInitialState() : isFinalState();
    }

    public boolean isFinalState(boolean forward) {
        return forward ? isFinalState() : isInitialState();
    }

    public boolean isAnchoredFinalState(boolean forward) {
        return forward ? isAnchoredFinalState() : isAnchoredInitialState();
    }

    public boolean isUnAnchoredFinalState(boolean forward) {
        return forward ? isUnAnchoredFinalState() : isUnAnchoredInitialState();
    }

    protected abstract boolean hasTransitionToUnAnchoredFinalState(boolean forward);

    public T[] getSuccessors() {
        return successors;
    }

    public void setSuccessors(T[] successors) {
        this.successors = successors;
    }

    public T[] getPredecessors() {
        return predecessors;
    }

    public void setPredecessors(T[] predecessors) {
        this.predecessors = predecessors;
    }

    public T[] getSuccessors(boolean forward) {
        return forward ? getSuccessors() : getPredecessors();
    }

    public T[] getPredecessors(boolean forward) {
        return forward ? getPredecessors() : getSuccessors();
    }

    public boolean hasSuccessors() {
        return successors.length > 0;
    }

    public boolean hasPredecessors() {
        return predecessors.length > 0;
    }

    /**
     * Returns {@code true} iff this state is non-final and has no successors/predecessors
     * (depending on {@code forward} other than itself.
     */
    public boolean isDead(boolean forward) {
        if (isFinalState(forward)) {
            return false;
        }
        for (int i = 0; i < getSuccessors(forward).length; i++) {
            if (getSuccessors(forward)[i].getTarget(forward) != this) {
                return false;
            }
        }
        return true;
    }

    /**
     * Helper for predecessor initialization. Since the number of predecessors of a given state is
     * unknown during automaton construction, we cannot allocate a suitable array for them
     * immediately. Instead, we capture the number of predecessors with this method, and initialize
     * the array after automaton construction.
     */
    public void incPredecessors() {
        nPredecessors++;
    }

    /**
     * Add a predecessor-transition to this state's predecessor array. The transition's
     * {@link AbstractTransition#getSource() source} is the predecessor state, and
     * {@link AbstractTransition#getTarget() target} is {@code this}. Before calling this method,
     * the automaton generator must make the total number of predecessors known via
     * {@link #incPredecessors()}.
     */
    public void addPredecessor(T predecessor) {
        addPredecessor(predecessor, false);
    }

    /**
     * Identical to {@link #addPredecessor(AbstractTransition)}, but does not assert that the
     * predecessor transition's target is {@code this}.
     */
    public void addPredecessorUnchecked(T predecessor) {
        addPredecessor(predecessor, true);
    }

    private void addPredecessor(T predecessor, boolean unchecked) {
        assert unchecked || predecessor.getTarget() == this;
        if (predecessors.length == 0) {
            predecessors = createTransitionsArray(nPredecessors);
        }
        predecessors[--nPredecessors] = predecessor;
    }

    /**
     * Returns the current value of {@code nPredecessors} set by {@link #incPredecessors()}, which
     * is not necessarily the final number of predecessors. Use with caution.
     */
    protected int getNPredecessors() {
        return nPredecessors;
    }

    protected abstract T[] createTransitionsArray(int length);
}
