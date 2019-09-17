/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.RegexRootNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputCharAtNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputLengthNode;

public final class TRegexDFAExecutorNode extends Node {

    public static final int NO_MATCH = -2;
    private final TRegexDFAExecutorProperties props;
    private final int maxNumberOfNFAStates;
    @Child private InputLengthNode lengthNode = InputLengthNode.create();
    @Child private InputCharAtNode charAtNode = InputCharAtNode.create();
    @Children private final DFAAbstractStateNode[] states;
    @Children private final DFACaptureGroupLazyTransitionNode[] cgTransitions;
    private final TRegexDFAExecutorDebugRecorder debugRecorder;

    public TRegexDFAExecutorNode(
                    TRegexDFAExecutorProperties props,
                    int maxNumberOfNFAStates,
                    DFAAbstractStateNode[] states,
                    DFACaptureGroupLazyTransitionNode[] cgTransitions,
                    TRegexDFAExecutorDebugRecorder debugRecorder) {
        this.props = props;
        this.maxNumberOfNFAStates = maxNumberOfNFAStates;
        this.states = states;
        this.cgTransitions = cgTransitions;
        this.debugRecorder = debugRecorder;
    }

    public TRegexDFAExecutorNode(
                    TRegexDFAExecutorProperties props,
                    int maxNumberOfNFAStates,
                    DFAAbstractStateNode[] states,
                    DFACaptureGroupLazyTransitionNode[] cgTransitions) {
        this(props, maxNumberOfNFAStates, states, cgTransitions, null);
    }

    private DFAInitialStateNode getInitialState() {
        return (DFAInitialStateNode) states[0];
    }

    public int getPrefixLength() {
        return getInitialState().getPrefixLength();
    }

    public boolean isAnchored() {
        return !getInitialState().hasUnAnchoredEntry();
    }

    public boolean isForward() {
        return props.isForward();
    }

    public boolean isBackward() {
        return !props.isForward();
    }

    public boolean isSearching() {
        return props.isSearching();
    }

    public boolean isRegressionTestMode() {
        return props.isRegressionTestMode();
    }

    public DFACaptureGroupLazyTransitionNode[] getCGTransitions() {
        return cgTransitions;
    }

    public int getNumberOfStates() {
        return states.length;
    }

    public int getNumberOfCaptureGroups() {
        return props.getNumberOfCaptureGroups();
    }

    public boolean recordExecution() {
        return debugRecorder != null;
    }

    public TRegexDFAExecutorDebugRecorder getDebugRecorder() {
        return debugRecorder;
    }

    /**
     * records position of the END of the match found, or -1 if no match exists.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    protected Object execute(final TRegexDFAExecutorLocals locals, final boolean compactString) {
        CompilerDirectives.ensureVirtualized(locals);
        CompilerAsserts.compilationConstant(states);
        CompilerAsserts.compilationConstant(states.length);
        if (!validArgs(locals)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException(String.format("Got illegal args! (fromIndex %d, initialIndex %d, maxIndex %d)",
                            locals.getFromIndex(), locals.getIndex(), locals.getMaxIndex()));
        }
        if (props.isTrackCaptureGroups()) {
            initResultOrder(locals);
            locals.setResultObject(null);
            locals.setLastTransition((short) -1);
        } else {
            locals.setResultInt(TRegexDFAExecutorNode.NO_MATCH);
        }
        // check if input is long enough for a match
        if (props.getMinResultLength() > 0 && (isForward() ? locals.getMaxIndex() - locals.getIndex() : locals.getIndex() - locals.getMaxIndex()) < props.getMinResultLength()) {
            // no match possible, break immediately
            return props.isTrackCaptureGroups() ? null : TRegexDFAExecutorNode.NO_MATCH;
        }
        if (recordExecution()) {
            debugRecorder.startRecording(locals);
        }
        if (isBackward() && locals.getFromIndex() - 1 > locals.getMaxIndex()) {
            locals.setCurMaxIndex(locals.getFromIndex() - 1);
        } else {
            locals.setCurMaxIndex(locals.getMaxIndex());
        }
        int ip = 0;
        outer: while (true) {
            if (CompilerDirectives.inInterpreter()) {
                RegexRootNode.checkThreadInterrupted();
            }
            CompilerAsserts.partialEvaluationConstant(ip);
            if (ip == -1) {
                break;
            }
            final DFAAbstractStateNode curState = states[ip];
            CompilerAsserts.partialEvaluationConstant(curState);
            final short[] successors = curState.getSuccessors();
            CompilerAsserts.partialEvaluationConstant(successors);
            CompilerAsserts.partialEvaluationConstant(successors.length);
            int prevIndex = locals.getIndex();
            curState.executeFindSuccessor(locals, this, compactString);
            if (recordExecution() && ip != 0) {
                debugRecordTransition(locals, (DFAStateNode) curState, prevIndex);
            }
            for (int i = 0; i < successors.length; i++) {
                if (i == locals.getSuccessorIndex()) {
                    if (successors[i] != -1 && states[successors[i]] instanceof DFAStateNode) {
                        ((DFAStateNode) states[successors[i]]).getStateReachedProfile().enter();
                    }
                    ip = successors[i];
                    continue outer;
                }
            }
            assert locals.getSuccessorIndex() == -1;
            break;
        }
        if (recordExecution()) {
            debugRecorder.finishRecording();
        }
        return props.isTrackCaptureGroups() ? locals.getResultCaptureGroups() : locals.getResultInt();
    }

    private void debugRecordTransition(TRegexDFAExecutorLocals locals, DFAStateNode curState, int prevIndex) {
        short ip = curState.getId();
        boolean hasSuccessor = locals.getSuccessorIndex() != -1;
        if (isForward()) {
            for (int i = prevIndex; i < locals.getIndex() - (hasSuccessor ? 1 : 0); i++) {
                if (curState.hasLoopToSelf()) {
                    debugRecorder.recordTransition(i, ip, curState.getLoopToSelf());
                }
            }
        } else {
            for (int i = prevIndex; i > locals.getIndex() + (hasSuccessor ? 1 : 0); i--) {
                if (curState.hasLoopToSelf()) {
                    debugRecorder.recordTransition(i, ip, curState.getLoopToSelf());
                }
            }
        }
        if (hasSuccessor) {
            debugRecorder.recordTransition(locals.getIndex() + (isForward() ? -1 : 1), ip, locals.getSuccessorIndex());
        }
    }

    /**
     * The length of the {@code input} argument given to
     * {@link TRegexExecRootNode#execute(Object, int)}.
     *
     * @return the length of the {@code input} argument given to
     *         {@link TRegexExecRootNode#execute(Object, int)}.
     */
    public int getInputLength(TRegexDFAExecutorLocals locals) {
        return lengthNode.execute(locals.getInput());
    }

    public char getChar(TRegexDFAExecutorLocals locals) {
        return charAtNode.execute(locals.getInput(), locals.getIndex());
    }

    public void advance(TRegexDFAExecutorLocals locals) {
        locals.setIndex(props.isForward() ? locals.getIndex() + 1 : locals.getIndex() - 1);
    }

    public boolean hasNext(TRegexDFAExecutorLocals locals) {
        return props.isForward() ? Integer.compareUnsigned(locals.getIndex(), locals.getCurMaxIndex()) < 0 : locals.getIndex() > locals.getCurMaxIndex();
    }

    public boolean atBegin(TRegexDFAExecutorLocals locals) {
        return locals.getIndex() == (props.isForward() ? 0 : getInputLength(locals) - 1);
    }

    public boolean atEnd(TRegexDFAExecutorLocals locals) {
        final int i = locals.getIndex();
        if (props.isForward()) {
            return i == getInputLength(locals);
        } else {
            return i < 0;
        }
    }

    public int rewindUpTo(TRegexDFAExecutorLocals locals, int length) {
        if (props.isForward()) {
            final int offset = Math.min(locals.getIndex(), length);
            locals.setIndex(locals.getIndex() - offset);
            return offset;
        } else {
            assert length == 0;
            return 0;
        }
    }

    private boolean validArgs(TRegexDFAExecutorLocals locals) {
        final int initialIndex = locals.getIndex();
        final int inputLength = getInputLength(locals);
        final int fromIndex = locals.getFromIndex();
        final int maxIndex = locals.getMaxIndex();
        if (props.isForward()) {
            return inputLength >= 0 && inputLength < Integer.MAX_VALUE - 20 &&
                            fromIndex >= 0 && fromIndex <= inputLength &&
                            initialIndex >= 0 && initialIndex <= inputLength &&
                            maxIndex >= 0 && maxIndex <= inputLength &&
                            initialIndex <= maxIndex;
        } else {
            return inputLength >= 0 && inputLength < Integer.MAX_VALUE - 20 &&
                            fromIndex >= 0 && fromIndex <= inputLength &&
                            initialIndex >= -1 && initialIndex < inputLength &&
                            maxIndex >= -1 && maxIndex < inputLength &&
                            initialIndex >= maxIndex;
        }
    }

    @ExplodeLoop
    private void initResultOrder(TRegexDFAExecutorLocals locals) {
        DFACaptureGroupTrackingData cgData = locals.getCGData();
        for (int i = 0; i < maxNumberOfNFAStates; i++) {
            cgData.currentResultOrder[i] = i * props.getNumberOfCaptureGroups() * 2;
        }
    }

    public TRegexDFAExecutorProperties getProperties() {
        return props;
    }

    public int getMaxNumberOfNFAStates() {
        return maxNumberOfNFAStates;
    }

    public double getCGReorderRatio() {
        if (!props.isTrackCaptureGroups()) {
            return 0;
        }
        int nPT = 0;
        int nReorder = 0;
        for (DFACaptureGroupLazyTransitionNode t : cgTransitions) {
            nPT += t.getPartialTransitions().length;
            for (DFACaptureGroupPartialTransitionNode pt : t.getPartialTransitions()) {
                if (pt.doesReorderResults()) {
                    nReorder++;
                }
            }
        }
        if (nPT > 0) {
            return (double) nReorder / nPT;
        }
        return 0;
    }

    public double getCGArrayCopyRatio() {
        if (!props.isTrackCaptureGroups()) {
            return 0;
        }
        int nPT = 0;
        int nArrayCopy = 0;
        for (DFACaptureGroupLazyTransitionNode t : cgTransitions) {
            nPT += t.getPartialTransitions().length;
            for (DFACaptureGroupPartialTransitionNode pt : t.getPartialTransitions()) {
                nArrayCopy += pt.getArrayCopies().length / 2;
            }
        }
        if (nPT > 0) {
            return (double) nArrayCopy / nPT;
        }
        return 0;
    }
}
