/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

import java.util.Arrays;

public class DFAStateNode extends DFAAbstractStateNode {

    private static final byte FINAL_STATE_FLAG = 1;
    private static final byte ANCHORED_FINAL_STATE_FLAG = 1 << 1;
    private static final byte FIND_SINGLE_CHAR_FLAG = 1 << 2;

    private final short id;
    private final byte flags;
    protected final short loopToSelf;
    @CompilationFinal(dimensions = 1) protected final CharMatcher[] matchers;

    public DFAStateNode(short id,
                    boolean finalState,
                    boolean anchoredFinalState,
                    boolean findSingleChar,
                    short loopToSelf,
                    short[] successors,
                    CharMatcher[] matchers) {
        super(successors);
        assert id > 0;
        this.id = id;
        byte newFlags = 0;
        if (finalState) {
            newFlags |= FINAL_STATE_FLAG;
        }
        if (anchoredFinalState) {
            newFlags |= ANCHORED_FINAL_STATE_FLAG;
        }
        if (findSingleChar) {
            newFlags |= FIND_SINGLE_CHAR_FLAG;
        }
        this.loopToSelf = loopToSelf;
        this.flags = newFlags;
        this.matchers = matchers;
    }

    protected DFAStateNode(DFAStateNode nodeSplitCopy, short copyID) {
        this(copyID, nodeSplitCopy.isFinalState(), nodeSplitCopy.isAnchoredFinalState(), nodeSplitCopy.isFindSingleChar(), nodeSplitCopy.loopToSelf,
                        Arrays.copyOf(nodeSplitCopy.getSuccessors(), nodeSplitCopy.getSuccessors().length), nodeSplitCopy.getMatchers());
    }

    @Override
    public DFAStateNode createNodeSplitCopy(short copyID) {
        return new DFAStateNode(this, copyID);
    }

    @Override
    public short getId() {
        return id;
    }

    public CharMatcher[] getMatchers() {
        return matchers;
    }

    public boolean isFinalState() {
        return flagIsSet(FINAL_STATE_FLAG);
    }

    public boolean isAnchoredFinalState() {
        return flagIsSet(ANCHORED_FINAL_STATE_FLAG);
    }

    public boolean isFindSingleChar() {
        return flagIsSet(FIND_SINGLE_CHAR_FLAG);
    }

    private boolean flagIsSet(byte flag) {
        return (flags & flag) != 0;
    }

    public boolean isLoopToSelf() {
        return loopToSelf != -1;
    }

    /**
     * Calculates this state's successor by finding a transition that matches the current input. If
     * the successor is the state itself, this method continues consuming input characters until a
     * different successor is found. This special handling allows for partial loop unrolling inside
     * the DFA, as well as some optimizations in {@link CGTrackingDFAStateNode}.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     */
    @Override
    public void executeFindSuccessor(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        beforeFindSuccessor(frame, executor);
        if (!executor.hasNext(frame)) {
            executor.setSuccessorIndex(frame, atEnd1(frame, executor));
            return;
        }
        if (checkMatch1(frame, executor)) {
            if (executor.hasNext(frame)) {
                if (!checkMatch2(frame, executor)) {
                    return;
                }
            } else {
                executor.setSuccessorIndex(frame, atEnd2(frame, executor));
                return;
            }
            final int preLoopIndex = executor.getIndex(frame);
            while (executor.hasNext(frame)) {
                if (!checkMatch3(frame, executor, preLoopIndex)) {
                    return;
                }
            }
            executor.setSuccessorIndex(frame, atEnd3(frame, executor, preLoopIndex));
        }
    }

    /**
     * Finds the first matching transition. If a transition matches,
     * {@link #successorFound1(VirtualFrame, TRegexDFAExecutorNode, int)} is called. The index of
     * the element of {@link #getMatchers()} that matched the current input character (
     * {@link TRegexDFAExecutorNode#getChar(VirtualFrame)}) or {@link #FS_RESULT_NO_SUCCESSOR} is
     * stored via {@link TRegexDFAExecutorNode#setSuccessorIndex(VirtualFrame, int)}.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @return <code>true</code> if the matching transition loops back to this state,
     *         <code>false</code> otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch1(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        final char c = executor.getChar(frame);
        executor.advance(frame);
        for (int i = 0; i < matchers.length; i++) {
            if (DebugUtil.DEBUG_STEP_EXECUTION) {
                System.out.println("check " + matchers[i]);
            }
            if (matchers[i].match(c)) {
                CompilerAsserts.partialEvaluationConstant(i);
                successorFound1(frame, executor, i);
                executor.setSuccessorIndex(frame, i);
                return isLoopToSelf() && i == loopToSelf;
            }
        }
        executor.setSuccessorIndex(frame, FS_RESULT_NO_SUCCESSOR);
        return false;
    }

    /**
     * Finds the first matching transition. This method is called only if the transition found by
     * {@link #checkMatch1(VirtualFrame, TRegexDFAExecutorNode)} was a loop back to this state
     * (indicated by {@link #loopToSelf}). If a transition <i>other than</i> the looping transition
     * matches, {@link #successorFound2(VirtualFrame, TRegexDFAExecutorNode, int)} is called. The
     * index of the element of {@link #getMatchers()} that matched the current input character (
     * {@link TRegexDFAExecutorNode#getChar(VirtualFrame)}) or {@link #FS_RESULT_NO_SUCCESSOR} is
     * stored via {@link TRegexDFAExecutorNode#setSuccessorIndex(VirtualFrame, int)}. If no
     * transition matches, {@link #noSuccessor2(VirtualFrame, TRegexDFAExecutorNode)} is called.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @return <code>true</code> if the matching transition loops back to this state,
     *         <code>false</code> otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch2(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        final char c = executor.getChar(frame);
        executor.advance(frame);
        for (int i = 0; i < matchers.length; i++) {
            if (DebugUtil.DEBUG_STEP_EXECUTION) {
                System.out.println("check " + matchers[i]);
            }
            if (matchers[i].match(c)) {
                executor.setSuccessorIndex(frame, i);
                if (i != loopToSelf) {
                    CompilerAsserts.partialEvaluationConstant(i);
                    successorFound2(frame, executor, i);
                    return false;
                }
                return true;
            }
        }
        executor.setSuccessorIndex(frame, FS_RESULT_NO_SUCCESSOR);
        noSuccessor2(frame, executor);
        return false;
    }

    /**
     * Finds the first matching transition. This method is called only if the transitions found by
     * {@link #checkMatch1(VirtualFrame, TRegexDFAExecutorNode)} AND
     * {@link #checkMatch2(VirtualFrame, TRegexDFAExecutorNode)} both were a loop back to this state
     * (indicated by {@link #loopToSelf}), and will be called in a loop until a transition other
     * than the loop back transition matches. If a transition <i>other than</i> the looping
     * transition matches, {@link #successorFound3(VirtualFrame, TRegexDFAExecutorNode, int, int)}
     * is called. The index of the element of {@link #getMatchers()} that matched the current input
     * character ({@link TRegexDFAExecutorNode#getChar(VirtualFrame)}) or
     * {@link #FS_RESULT_NO_SUCCESSOR} is stored via
     * {@link TRegexDFAExecutorNode#setSuccessorIndex(VirtualFrame, int)}. If no transition matches,
     * {@link #noSuccessor3(VirtualFrame, TRegexDFAExecutorNode, int)} is called.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param preLoopIndex the index pointed to by
     *            {@link TRegexDFAExecutorNode#getIndex(VirtualFrame)} <i>before</i> this method is
     *            called for the first time.
     * @return <code>true</code> if the matching transition loops back to this state,
     *         <code>false</code> otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch3(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
        final char c = executor.getChar(frame);
        executor.advance(frame);
        for (int i = 0; i < matchers.length; i++) {
            if (DebugUtil.DEBUG_STEP_EXECUTION) {
                System.out.println("check " + matchers[i]);
            }
            if (matchers[i].match(c)) {
                executor.setSuccessorIndex(frame, i);
                if (i != loopToSelf) {
                    CompilerAsserts.partialEvaluationConstant(i);
                    successorFound3(frame, executor, i, preLoopIndex);
                    return false;
                }
                return true;
            }
        }
        executor.setSuccessorIndex(frame, FS_RESULT_NO_SUCCESSOR);
        noSuccessor3(frame, executor, preLoopIndex);
        return false;
    }

    /**
     * Gets called at the very beginning of
     * {@link #executeFindSuccessor(VirtualFrame, TRegexDFAExecutorNode)}.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     */
    protected void beforeFindSuccessor(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            storeResult(frame, executor, curIndex(frame, executor), false);
        }
    }

    /**
     * Gets called when {@link #checkMatch1(VirtualFrame, TRegexDFAExecutorNode)} finds a successor.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param i the index of the matching transition (corresponds to an entry in
     *            {@link #getMatchers()} and {@link #getSuccessors()}).
     */
    protected void successorFound1(VirtualFrame frame, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
    }

    /**
     * Gets called if the end of the input is reached (!
     * {@link TRegexDFAExecutorNode#hasNext(VirtualFrame)}) before
     * {@link #checkMatch1(VirtualFrame, TRegexDFAExecutorNode)} is called. In
     * {@link BackwardDFAStateNode}, execution may still continue here, which is why this method can
     * return a successor index.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @return a successor index.
     */
    protected int atEnd1(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isAnchoredFinalState() && executor.atEnd(frame)) {
            storeResult(frame, executor, curIndex(frame, executor), true);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    /**
     * Gets called when {@link #checkMatch2(VirtualFrame, TRegexDFAExecutorNode)} finds a successor
     * <i>other than</i> the looping transition indicated by {@link #loopToSelf}.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param i the index of the matching transition (corresponds to an entry in
     *            {@link #getMatchers()} and {@link #getSuccessors()}).
     */
    protected void successorFound2(VirtualFrame frame, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            storeResult(frame, executor, prevIndex(frame, executor), false);
        }
    }

    /**
     * Gets called if {@link #checkMatch2(VirtualFrame, TRegexDFAExecutorNode)} does not find a
     * successor.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     */
    protected void noSuccessor2(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            storeResult(frame, executor, prevIndex(frame, executor), false);
        }
    }

    /**
     * Gets called if the end of the input is reached (!
     * {@link TRegexDFAExecutorNode#hasNext(VirtualFrame)}) directly after
     * {@link #checkMatch1(VirtualFrame, TRegexDFAExecutorNode)} is called. In
     * {@link BackwardDFAStateNode}, execution may still continue here, which is why this method can
     * return a successor index.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @return a successor index.
     */
    protected int atEnd2(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        boolean anchored = isAnchoredFinalState() && executor.atEnd(frame);
        if (isFinalState() || anchored) {
            storeResult(frame, executor, curIndex(frame, executor), anchored);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    /**
     * Gets called when {@link #checkMatch2(VirtualFrame, TRegexDFAExecutorNode)} finds a successor
     * <i>other than</i> the looping transition indicated by {@link #loopToSelf}.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param i the index of the matching transition (corresponds to an entry in
     *            {@link #getMatchers()} and {@link #getSuccessors()}).
     * @param preLoopIndex the index pointed to by
     *            {@link TRegexDFAExecutorNode#getIndex(VirtualFrame)} <i>before</i>
     *            {@link #checkMatch3(VirtualFrame, TRegexDFAExecutorNode, int)} is called for the
     *            first time.
     */
    protected void successorFound3(VirtualFrame frame, TRegexDFAExecutorNode executor, int i, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            storeResult(frame, executor, prevIndex(frame, executor), false);
        }
    }

    /**
     * Gets called if {@link #checkMatch3(VirtualFrame, TRegexDFAExecutorNode, int)} does not find a
     * successor.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param preLoopIndex the index pointed to by
     *            {@link TRegexDFAExecutorNode#getIndex(VirtualFrame)} <i>before</i>
     *            {@link #checkMatch3(VirtualFrame, TRegexDFAExecutorNode, int)} is called for the
     *            first time.
     */
    protected void noSuccessor3(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            storeResult(frame, executor, prevIndex(frame, executor), false);
        }
    }

    /**
     * Gets called if the end of the input is reached (!
     * {@link TRegexDFAExecutorNode#hasNext(VirtualFrame)}) in the loop that calls
     * {@link #checkMatch3(VirtualFrame, TRegexDFAExecutorNode, int)}. In
     * {@link BackwardDFAStateNode}, execution may still continue here, which is why this method can
     * return a successor index.
     * 
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param preLoopIndex the index pointed to by
     *            {@link TRegexDFAExecutorNode#getIndex(VirtualFrame)} <i>before</i>
     *            {@link #checkMatch3(VirtualFrame, TRegexDFAExecutorNode, int)} is called for the
     *            first time.
     * @return a successor index.
     */
    protected int atEnd3(VirtualFrame frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        boolean anchored = isAnchoredFinalState() && executor.atEnd(frame);
        if (isFinalState() || anchored) {
            storeResult(frame, executor, curIndex(frame, executor), anchored);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    @SuppressWarnings("unused")
    protected void storeResult(VirtualFrame frame, TRegexDFAExecutorNode executor, int index, boolean anchored) {
        CompilerAsserts.partialEvaluationConstant(this);
        executor.setResultInt(frame, index);
    }

    protected int curIndex(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        return executor.getIndex(frame);
    }

    protected int prevIndex(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        return executor.getIndex(frame) - 1;
    }

    protected int nextIndex(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        return executor.getIndex(frame) + 1;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        DebugUtil.appendNodeId(sb, id).append(": ").append(matchers.length).append(" successors");
        if (isAnchoredFinalState()) {
            sb.append(", AFS");
        }
        if (isFinalState()) {
            sb.append(", FS");
        }
        sb.append(":\n");
        for (int i = 0; i < matchers.length; i++) {
            sb.append("      - ").append(matchers[i]).append(" -> ");
            DebugUtil.appendNodeId(sb, getSuccessors()[i]).append("\n");
        }
        return sb.toString();
    }

    @TruffleBoundary
    private DebugUtil.Table transitionToTable(int i) {
        return new DebugUtil.Table("Transition",
                        new DebugUtil.Value("matcher", matchers[i]),
                        new DebugUtil.Value("target", DebugUtil.nodeID(successors[i])));
    }

    @TruffleBoundary
    @Override
    public DebugUtil.Table toTable() {
        DebugUtil.Table table = new DebugUtil.Table(DebugUtil.nodeID(id));
        if (isAnchoredFinalState()) {
            table.append(new DebugUtil.Value("anchoredFinalState", "true"));
        }
        if (isFinalState()) {
            table.append(new DebugUtil.Value("finalState", "true"));
        }
        if (isLoopToSelf()) {
            table.append(new DebugUtil.Value("loopToSelf", "true"));
        }
        for (int i = 0; i < matchers.length; i++) {
            table.append(transitionToTable(i));
        }
        return table;
    }
}
