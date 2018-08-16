/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.RegexObject;
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
    protected void execute(final VirtualFrame frame) {
        CompilerDirectives.ensureVirtualized(frame);
        CompilerAsserts.compilationConstant(states);
        CompilerAsserts.compilationConstant(states.length);
        if (!validArgs(frame)) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException(String.format("Got illegal args! (fromIndex %d, initialIndex %d, maxIndex %d)",
                            getFromIndex(frame), getIndex(frame), getMaxIndex(frame)));
        }
        if (props.isTrackCaptureGroups()) {
            createCGData(frame);
            initResultOrder(frame);
            setResultObject(frame, null);
            setLastTransition(frame, (short) -1);
        } else {
            setResultInt(frame, TRegexDFAExecutorNode.NO_MATCH);
        }
        // check if input is long enough for a match
        if (props.getMinResultLength() > 0 && (isForward() ? getMaxIndex(frame) - getIndex(frame) : getIndex(frame) - getMaxIndex(frame)) < props.getMinResultLength()) {
            // no match possible, break immediately
            return;
        }
        if (recordExecution()) {
            debugRecorder.startRecording(frame, this);
        }
        if (isBackward() && getFromIndex(frame) - 1 > getMaxIndex(frame)) {
            setCurMaxIndex(frame, getFromIndex(frame) - 1);
        } else {
            setCurMaxIndex(frame, getMaxIndex(frame));
        }
        int ip = 0;
        outer: while (true) {
            CompilerAsserts.partialEvaluationConstant(ip);
            if (ip == -1) {
                break;
            }
            final DFAAbstractStateNode curState = states[ip];
            CompilerAsserts.partialEvaluationConstant(curState);
            final short[] successors = curState.getSuccessors();
            CompilerAsserts.partialEvaluationConstant(successors);
            CompilerAsserts.partialEvaluationConstant(successors.length);
            int prevIndex = getIndex(frame);
            curState.executeFindSuccessor(frame, this);
            if (recordExecution() && ip != 0) {
                debugRecordTransition(frame, (DFAStateNode) curState, prevIndex);
            }
            for (int i = 0; i < successors.length; i++) {
                if (i == getSuccessorIndex(frame)) {
                    ip = successors[i];
                    continue outer;
                }
            }
            assert getSuccessorIndex(frame) == -1;
            break;
        }
        if (recordExecution()) {
            debugRecorder.finishRecording();
        }
    }

    private void debugRecordTransition(VirtualFrame frame, DFAStateNode curState, int prevIndex) {
        short ip = curState.getId();
        boolean hasSuccessor = getSuccessorIndex(frame) != -1;
        if (isForward()) {
            for (int i = prevIndex; i < getIndex(frame) - (hasSuccessor ? 1 : 0); i++) {
                if (curState.hasLoopToSelf()) {
                    debugRecorder.recordTransition(i, ip, curState.getLoopToSelf());
                }
            }
        } else {
            for (int i = prevIndex; i > getIndex(frame) + (hasSuccessor ? 1 : 0); i--) {
                if (curState.hasLoopToSelf()) {
                    debugRecorder.recordTransition(i, ip, curState.getLoopToSelf());
                }
            }
        }
        if (hasSuccessor) {
            debugRecorder.recordTransition(getIndex(frame) + (isForward() ? -1 : 1), ip, getSuccessorIndex(frame));
        }
    }

    public void setInputIsCompactString(VirtualFrame frame, boolean inputIsCompactString) {
        frame.setBoolean(props.getInputIsCompactStringFS(), inputIsCompactString);
    }

    public boolean inputIsCompactString(VirtualFrame frame) {
        boolean ret = FrameUtil.getBooleanSafe(frame, props.getInputIsCompactStringFS());
        CompilerAsserts.partialEvaluationConstant(ret);
        return ret;
    }

    /**
     * The index pointing into {@link #getInput(VirtualFrame)}.
     *
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @return the current index of {@link #getInput(VirtualFrame)} that is being processed.
     */
    public int getIndex(VirtualFrame frame) {
        return FrameUtil.getIntSafe(frame, props.getIndexFS());
    }

    public void setIndex(VirtualFrame frame, int i) {
        frame.setInt(props.getIndexFS(), i);
    }

    /**
     * The {@code fromIndex} argument given to
     * {@link TRegexExecRootNode#execute(VirtualFrame, RegexObject, Object, int)}.
     *
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @return the {@code fromIndex} argument given to
     *         {@link TRegexExecRootNode#execute(VirtualFrame, RegexObject, Object, int)}.
     */
    public int getFromIndex(VirtualFrame frame) {
        return FrameUtil.getIntSafe(frame, props.getFromIndexFS());
    }

    public void setFromIndex(VirtualFrame frame, int fromIndex) {
        frame.setInt(props.getFromIndexFS(), fromIndex);
    }

    /**
     * The maximum index as given by the parent {@link TRegexExecRootNode}.
     *
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @return the maximum index as given by the parent {@link TRegexExecRootNode}.
     */
    public int getMaxIndex(VirtualFrame frame) {
        return FrameUtil.getIntSafe(frame, props.getMaxIndexFS());
    }

    public void setMaxIndex(VirtualFrame frame, int maxIndex) {
        frame.setInt(props.getMaxIndexFS(), maxIndex);
    }

    /**
     * The maximum index as checked by {@link #hasNext(VirtualFrame)}. In most cases this value is
     * equal to {@link #getMaxIndex(VirtualFrame)}, but backward matching nodes change this value
     * while matching.
     *
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @return the maximum index as checked by {@link #hasNext(VirtualFrame)}.
     *
     * @see BackwardDFAStateNode
     */
    public int getCurMaxIndex(VirtualFrame frame) {
        return FrameUtil.getIntSafe(frame, props.getCurMaxIndexFS());
    }

    public void setCurMaxIndex(VirtualFrame frame, int value) {
        frame.setInt(props.getCurMaxIndexFS(), value);
    }

    /**
     * The {@code input} argument given to
     * {@link TRegexExecRootNode#execute(VirtualFrame, RegexObject, Object, int)}.
     *
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @return the {@code input} argument given to
     *         {@link TRegexExecRootNode#execute(VirtualFrame, RegexObject, Object, int)}.
     */
    public Object getInput(VirtualFrame frame) {
        return FrameUtil.getObjectSafe(frame, props.getInputFS());
    }

    public void setInput(VirtualFrame frame, Object input) {
        frame.setObject(props.getInputFS(), input);
    }

    /**
     * The length of the {@code input} argument given to
     * {@link TRegexExecRootNode#execute(VirtualFrame, RegexObject, Object, int)}.
     *
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @return the length of the {@code input} argument given to
     *         {@link TRegexExecRootNode#execute(VirtualFrame, RegexObject, Object, int)}.
     */
    public int getInputLength(VirtualFrame frame) {
        return lengthNode.execute(getInput(frame));
    }

    public char getChar(VirtualFrame frame) {
        return charAtNode.execute(getInput(frame), getIndex(frame));
    }

    public void advance(VirtualFrame frame) {
        setIndex(frame, props.isForward() ? getIndex(frame) + 1 : getIndex(frame) - 1);
    }

    public boolean hasNext(VirtualFrame frame) {
        return props.isForward() ? Integer.compareUnsigned(getIndex(frame), getCurMaxIndex(frame)) < 0 : getIndex(frame) > getCurMaxIndex(frame);
    }

    public boolean atBegin(VirtualFrame frame) {
        return getIndex(frame) == (props.isForward() ? 0 : getInputLength(frame) - 1);
    }

    public boolean atEnd(VirtualFrame frame) {
        final int i = getIndex(frame);
        if (props.isForward()) {
            return i == getInputLength(frame);
        } else {
            return i < 0;
        }
    }

    public int rewindUpTo(VirtualFrame frame, int length) {
        if (props.isForward()) {
            final int offset = Math.min(getIndex(frame), length);
            setIndex(frame, getIndex(frame) - offset);
            return offset;
        } else {
            assert length == 0;
            return 0;
        }
    }

    public void setLastTransition(VirtualFrame frame, short lastTransition) {
        frame.setInt(props.getLastTransitionFS(), lastTransition);
    }

    public short getLastTransition(VirtualFrame frame) {
        return (short) FrameUtil.getIntSafe(frame, props.getLastTransitionFS());
    }

    public void setSuccessorIndex(VirtualFrame frame, int successorIndex) {
        frame.setInt(props.getSuccessorIndexFS(), successorIndex);
    }

    public int getSuccessorIndex(VirtualFrame frame) {
        return FrameUtil.getIntSafe(frame, props.getSuccessorIndexFS());
    }

    public int getResultInt(VirtualFrame frame) {
        assert !props.isTrackCaptureGroups();
        return FrameUtil.getIntSafe(frame, props.getResultFS());
    }

    public void setResultInt(VirtualFrame frame, int result) {
        frame.setInt(props.getResultFS(), result);
    }

    public int[] getResultCaptureGroups(VirtualFrame frame) {
        assert props.isTrackCaptureGroups();
        return (int[]) FrameUtil.getObjectSafe(frame, props.getCaptureGroupResultFS());
    }

    public void setResultObject(VirtualFrame frame, Object result) {
        frame.setObject(props.getCaptureGroupResultFS(), result);
    }

    public DFACaptureGroupTrackingData getCGData(VirtualFrame frame) {
        return (DFACaptureGroupTrackingData) FrameUtil.getObjectSafe(frame, props.getCgDataFS());
    }

    private void createCGData(VirtualFrame frame) {
        DFACaptureGroupTrackingData trackingData = new DFACaptureGroupTrackingData(maxNumberOfNFAStates, props.getNumberOfCaptureGroups());
        frame.setObject(props.getCgDataFS(), trackingData);
    }

    private boolean validArgs(VirtualFrame frame) {
        final int initialIndex = getIndex(frame);
        final int inputLength = getInputLength(frame);
        final int fromIndex = getFromIndex(frame);
        final int maxIndex = getMaxIndex(frame);
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
    private void initResultOrder(VirtualFrame frame) {
        DFACaptureGroupTrackingData cgData = getCGData(frame);
        for (int i = 0; i < maxNumberOfNFAStates; i++) {
            cgData.currentResultOrder[i] = i * props.getNumberOfCaptureGroups() * 2;
        }
    }

    public TRegexDFAExecutorProperties getProperties() {
        return props;
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
