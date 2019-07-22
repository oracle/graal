/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;

public class CGTrackingDFAStateNode extends DFAStateNode {

    private final DFACaptureGroupPartialTransitionNode anchoredFinalStateTransition;
    private final DFACaptureGroupPartialTransitionNode unAnchoredFinalStateTransition;

    @CompilationFinal(dimensions = 1) private final short[] captureGroupTransitions;

    @CompilationFinal(dimensions = 1) private final short[] precedingCaptureGroupTransitions;

    @Child private DFACaptureGroupPartialTransitionDispatchNode transitionDispatchNode;

    public CGTrackingDFAStateNode(short id, byte flags, LoopOptimizationNode loopOptimizationNode, short[] successors, CharMatcher[] matchers,
                    AllTransitionsInOneTreeMatcher allTransitionsInOneTreeMatcher, short[] captureGroupTransitions,
                    short[] precedingCaptureGroupTransitions,
                    DFACaptureGroupPartialTransitionNode anchoredFinalStateTransition,
                    DFACaptureGroupPartialTransitionNode unAnchoredFinalStateTransition) {
        super(id, flags, loopOptimizationNode, successors, matchers, allTransitionsInOneTreeMatcher);
        this.captureGroupTransitions = captureGroupTransitions;
        this.precedingCaptureGroupTransitions = precedingCaptureGroupTransitions;
        transitionDispatchNode = precedingCaptureGroupTransitions.length > 1 ? DFACaptureGroupPartialTransitionDispatchNode.create(precedingCaptureGroupTransitions) : null;
        this.anchoredFinalStateTransition = anchoredFinalStateTransition;
        this.unAnchoredFinalStateTransition = unAnchoredFinalStateTransition;
    }

    private CGTrackingDFAStateNode(CGTrackingDFAStateNode copy, short copyID) {
        super(copy, copyID);
        this.captureGroupTransitions = copy.captureGroupTransitions;
        this.precedingCaptureGroupTransitions = copy.precedingCaptureGroupTransitions;
        this.anchoredFinalStateTransition = copy.anchoredFinalStateTransition;
        this.unAnchoredFinalStateTransition = copy.unAnchoredFinalStateTransition;
        transitionDispatchNode = precedingCaptureGroupTransitions.length > 1 ? DFACaptureGroupPartialTransitionDispatchNode.create(precedingCaptureGroupTransitions) : null;
    }

    private DFACaptureGroupLazyTransitionNode getCGTransitionToSelf(TRegexDFAExecutorNode executor) {
        return executor.getCGTransitions()[captureGroupTransitions[getLoopToSelf()]];
    }

    @Override
    public DFAStateNode createNodeSplitCopy(short copyID) {
        return new CGTrackingDFAStateNode(this, copyID);
    }

    @Override
    public void executeFindSuccessor(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(compactString);
        beforeFindSuccessor(locals, executor);
        if (!executor.hasNext(locals)) {
            locals.setSuccessorIndex(atEnd1(locals, executor));
            return;
        }
        if (checkMatch1(locals, executor, compactString)) {
            if (executor.hasNext(locals)) {
                if (!checkMatch2(locals, executor, compactString)) {
                    return;
                }
            } else {
                locals.setSuccessorIndex(atEnd2(locals, executor));
                return;
            }
            final int preLoopIndex = locals.getIndex();
            if (executor.isForward() && hasLoopToSelf() && loopOptimizationNode.getIndexOfChars() != null) {
                int indexOfResult = loopOptimizationNode.getIndexOfNode().execute(locals.getInput(),
                                preLoopIndex,
                                locals.getCurMaxIndex(),
                                loopOptimizationNode.getIndexOfChars());
                if (indexOfResult < 0) {
                    locals.setIndex(locals.getCurMaxIndex());
                    locals.setSuccessorIndex(atEnd3(locals, executor, preLoopIndex));
                    return;
                } else {
                    if (successors.length == 2) {
                        int successor = (getLoopToSelf() + 1) % 2;
                        CompilerAsserts.partialEvaluationConstant(successor);
                        locals.setIndex(indexOfResult + 1);
                        locals.setSuccessorIndex(successor);
                        successorFound3(locals, executor, successor, preLoopIndex);
                        return;
                    } else {
                        locals.setIndex(indexOfResult);
                    }
                }
            }
            while (executor.hasNext(locals)) {
                if (!checkMatch3(locals, executor, compactString, preLoopIndex)) {
                    return;
                }
            }
            locals.setSuccessorIndex(atEnd3(locals, executor, preLoopIndex));
        }
    }

    /**
     * Finds the first matching transition. If a transition matches,
     * {@link #successorFound1(TRegexDFAExecutorLocals, TRegexDFAExecutorNode, int)} is called. The
     * index of the element of {@link #getMatchers()} that matched the current input character (
     * {@link TRegexDFAExecutorNode#getChar(TRegexDFAExecutorLocals)}) or
     * {@link #FS_RESULT_NO_SUCCESSOR} is stored via
     * {@link TRegexDFAExecutorLocals#setSuccessorIndex(int)}.
     *
     * @param locals a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param compactString {@code true} if the input string is a compact string, must be partial
     *            evaluation constant.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch1(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        final char c = executor.getChar(locals);
        executor.advance(locals);
        if (treeTransitionMatching()) {
            int successor = getTreeMatcher().checkMatchTree1(locals, executor, this, c);
            assert sameResultAsRegularMatchers(executor, c, compactString, successor) : this.toString();
            locals.setSuccessorIndex(successor);
            return isLoopToSelf(successor);
        } else {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].execute(c, compactString)) {
                    CompilerAsserts.partialEvaluationConstant(i);
                    successorFound1(locals, executor, i);
                    locals.setSuccessorIndex(i);
                    return isLoopToSelf(i);
                }
            }
            locals.setSuccessorIndex(FS_RESULT_NO_SUCCESSOR);
            return false;
        }
    }

    /**
     * Finds the first matching transition. This method is called only if the transition found by
     * {@link #checkMatch1(TRegexDFAExecutorLocals, TRegexDFAExecutorNode, boolean)} was a loop back
     * to this state (indicated by {@link #isLoopToSelf(int)}). If a transition <i>other than</i>
     * the looping transition matches,
     * {@link #successorFound2(TRegexDFAExecutorLocals, TRegexDFAExecutorNode, int)} is called. The
     * index of the element of {@link #getMatchers()} that matched the current input character (
     * {@link TRegexDFAExecutorNode#getChar(TRegexDFAExecutorLocals)}) or
     * {@link #FS_RESULT_NO_SUCCESSOR} is stored via
     * {@link TRegexDFAExecutorLocals#setSuccessorIndex(int)}. If no transition matches,
     * {@link #noSuccessor2(TRegexDFAExecutorLocals, TRegexDFAExecutorNode)} is called.
     *
     * @param locals a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param compactString {@code true} if the input string is a compact string, must be partial
     *            evaluation constant.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch2(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString) {
        final char c = executor.getChar(locals);
        executor.advance(locals);
        if (treeTransitionMatching()) {
            int successor = getTreeMatcher().checkMatchTree2(locals, executor, this, c);
            assert sameResultAsRegularMatchers(executor, c, compactString, successor) : this.toString();
            locals.setSuccessorIndex(successor);
            return isLoopToSelf(successor);
        } else {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].execute(c, compactString)) {
                    locals.setSuccessorIndex(i);
                    if (!isLoopToSelf(i)) {
                        CompilerAsserts.partialEvaluationConstant(i);
                        successorFound2(locals, executor, i);
                        return false;
                    }
                    return true;
                }
            }
            locals.setSuccessorIndex(FS_RESULT_NO_SUCCESSOR);
            noSuccessor2(locals, executor);
            return false;
        }
    }

    /**
     * Finds the first matching transition. This method is called only if the transitions found by
     * {@link #checkMatch1(TRegexDFAExecutorLocals, TRegexDFAExecutorNode, boolean)} AND
     * {@link #checkMatch2(TRegexDFAExecutorLocals, TRegexDFAExecutorNode, boolean)} both were a
     * loop back to this state (indicated by {@link #isLoopToSelf(int)}), and will be called in a
     * loop until a transition other than the loop back transition matches. If a transition <i>other
     * than</i> the looping transition matches,
     * {@link #successorFound3(TRegexDFAExecutorLocals, TRegexDFAExecutorNode, int, int)} is called.
     * The index of the element of {@link #getMatchers()} that matched the current input character (
     * {@link TRegexDFAExecutorNode#getChar(TRegexDFAExecutorLocals)}) or
     * {@link #FS_RESULT_NO_SUCCESSOR} is stored via
     * {@link TRegexDFAExecutorLocals#setSuccessorIndex(int)}. If no transition matches,
     * {@link #noSuccessor3(TRegexDFAExecutorLocals, TRegexDFAExecutorNode, int)} is called.
     *
     * @param locals a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param compactString {@code true} if the input string is a compact string, must be partial
     *            evaluation constant.
     * @param preLoopIndex the index pointed to by {@link TRegexDFAExecutorLocals#getIndex()}
     *            <i>before</i> this method is called for the first time.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch3(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean compactString, int preLoopIndex) {
        final char c = executor.getChar(locals);
        executor.advance(locals);
        if (treeTransitionMatching()) {
            int successor = getTreeMatcher().checkMatchTree3(locals, executor, this, c, preLoopIndex);
            assert sameResultAsRegularMatchers(executor, c, compactString, successor) : this.toString();
            locals.setSuccessorIndex(successor);
            return isLoopToSelf(successor);
        } else {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].execute(c, compactString)) {
                    locals.setSuccessorIndex(i);
                    if (!isLoopToSelf(i)) {
                        CompilerAsserts.partialEvaluationConstant(i);
                        successorFound3(locals, executor, i, preLoopIndex);
                        return false;
                    }
                    return true;
                }
            }
            locals.setSuccessorIndex(FS_RESULT_NO_SUCCESSOR);
            noSuccessor3(locals, executor, preLoopIndex);
            return false;
        }
    }

    private void beforeFindSuccessor(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (executor.isSearching()) {
            checkFinalState(locals, executor);
        }
    }

    void successorFound1(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        if (precedingCaptureGroupTransitions.length == 1) {
            executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getPartialTransitions()[i].apply(executor, locals.getCGData(), prevIndex(locals));
        } else {
            transitionDispatchNode.applyPartialTransition(locals, executor, locals.getLastTransition(), i, prevIndex(locals));
        }
        locals.setLastTransition(captureGroupTransitions[i]);
    }

    private int atEnd1(TRegexDFAExecutorLocals frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isAnchoredFinalState() && executor.atEnd(frame)) {
            applyAnchoredFinalStateTransition(frame, executor);
        } else {
            checkFinalState(frame, executor);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    void successorFound2(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int i) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        assert locals.getLastTransition() == captureGroupTransitions[getLoopToSelf()];
        if (executor.isSearching()) {
            checkFinalStateLoop(locals, executor);
        }
        getCGTransitionToSelf(executor).getPartialTransitions()[i].apply(executor, locals.getCGData(), prevIndex(locals));
        locals.setLastTransition(captureGroupTransitions[i]);
    }

    void noSuccessor2(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert executor.isSearching();
        checkFinalStateLoop(locals, executor);
    }

    private int atEnd2(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        return atEndLoop(locals, executor);
    }

    void successorFound3(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int i, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(i);
        applyLoopTransitions(locals, executor, preLoopIndex, prevIndex(locals) - 1);
        if (executor.isSearching()) {
            checkFinalStateLoop(locals, executor);
        }
        getCGTransitionToSelf(executor).getPartialTransitions()[i].apply(executor, locals.getCGData(), prevIndex(locals));
        locals.setLastTransition(captureGroupTransitions[i]);
    }

    void noSuccessor3(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert executor.isSearching();
        applyLoopTransitions(locals, executor, preLoopIndex, prevIndex(locals) - 1);
        checkFinalStateLoop(locals, executor);
    }

    private int atEnd3(TRegexDFAExecutorLocals frame, TRegexDFAExecutorNode executor, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        applyLoopTransitions(frame, executor, preLoopIndex, prevIndex(frame));
        return atEndLoop(frame, executor);
    }

    private void applyLoopTransitions(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, int preLoopIndex, int postLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        DFACaptureGroupPartialTransitionNode transition = getCGTransitionToSelf(executor).getPartialTransitions()[getLoopToSelf()];
        if (transition.doesReorderResults()) {
            for (int i = preLoopIndex - 1; i <= postLoopIndex; i++) {
                transition.apply(executor, locals.getCGData(), i);
            }
        } else {
            transition.apply(executor, locals.getCGData(), postLoopIndex);
        }
    }

    private int atEndLoop(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert locals.getLastTransition() == captureGroupTransitions[getLoopToSelf()];
        if (isAnchoredFinalState() && executor.atEnd(locals)) {
            getCGTransitionToSelf(executor).getTransitionToAnchoredFinalState().applyPreFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), curIndex(locals));
            anchoredFinalStateTransition.applyFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), nextIndex(locals));
            storeResult(locals, executor);
        } else if (isFinalState()) {
            getCGTransitionToSelf(executor).getTransitionToFinalState().applyPreFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), curIndex(locals));
            unAnchoredFinalStateTransition.applyFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), nextIndex(locals));
            storeResult(locals, executor);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    private void checkFinalState(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            if (precedingCaptureGroupTransitions.length == 1) {
                executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getTransitionToFinalState().applyPreFinalStateTransition(
                                executor, locals.getCGData(), executor.isSearching(), curIndex(locals));
            } else {
                transitionDispatchNode.applyPreFinalTransition(locals, executor, locals.getLastTransition(), curIndex(locals));
            }
            unAnchoredFinalStateTransition.applyFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), nextIndex(locals));
            storeResult(locals, executor);
        }
    }

    private void applyAnchoredFinalStateTransition(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        if (precedingCaptureGroupTransitions.length == 1) {
            executor.getCGTransitions()[precedingCaptureGroupTransitions[0]].getTransitionToAnchoredFinalState().applyPreFinalStateTransition(
                            executor, locals.getCGData(), executor.isSearching(), curIndex(locals));
        } else {
            transitionDispatchNode.applyPreAnchoredFinalTransition(locals, executor, locals.getLastTransition(), curIndex(locals));
        }
        anchoredFinalStateTransition.applyFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), nextIndex(locals));
        storeResult(locals, executor);
    }

    private void checkFinalStateLoop(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        assert locals.getLastTransition() == captureGroupTransitions[getLoopToSelf()];
        if (isFinalState()) {
            getCGTransitionToSelf(executor).getTransitionToFinalState().applyPreFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), prevIndex(locals));
            unAnchoredFinalStateTransition.applyFinalStateTransition(executor, locals.getCGData(), executor.isSearching(), curIndex(locals));
            storeResult(locals, executor);
        }
    }

    private void storeResult(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (!executor.isSearching()) {
            locals.getCGData().exportResult((byte) DFACaptureGroupPartialTransitionNode.FINAL_STATE_RESULT_INDEX);
        }
        locals.setResultObject(locals.getCGData().currentResult);
    }
}
