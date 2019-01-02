/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.nodes.input.InputIndexOfNode;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public class DFAStateNode extends DFAAbstractStateNode {

    public static class LoopOptimizationNode extends Node {

        private final short loopTransitionIndex;
        @CompilationFinal(dimensions = 1) private final char[] indexOfChars;
        @Child private InputIndexOfNode indexOfNode;

        public LoopOptimizationNode(short loopTransitionIndex, char[] indexOfChars) {
            this.loopTransitionIndex = loopTransitionIndex;
            this.indexOfChars = indexOfChars;
        }

        private LoopOptimizationNode nodeSplitCopy() {
            return new LoopOptimizationNode(loopTransitionIndex, indexOfChars);
        }

        public char[] getIndexOfChars() {
            return indexOfChars;
        }

        public InputIndexOfNode getIndexOfNode() {
            if (indexOfNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                indexOfNode = insert(InputIndexOfNode.create());
            }
            return indexOfNode;
        }
    }

    private static final byte FLAG_FINAL_STATE = 1;
    private static final byte FLAG_ANCHORED_FINAL_STATE = 1 << 1;
    private static final byte FLAG_HAS_BACKWARD_PREFIX_STATE = 1 << 2;

    private final short id;
    private final byte flags;
    @Child LoopOptimizationNode loopOptimizationNode;
    @Children protected final CharMatcher[] matchers;
    private final AllTransitionsInOneTreeMatcher allTransitionsInOneTreeMatcher;
    private final BranchProfile stateReachedProfile = BranchProfile.create();

    DFAStateNode(DFAStateNode nodeSplitCopy, short copyID) {
        this(copyID, nodeSplitCopy.flags, nodeSplitCopy.loopOptimizationNode.nodeSplitCopy(),
                        Arrays.copyOf(nodeSplitCopy.getSuccessors(), nodeSplitCopy.getSuccessors().length),
                        nodeSplitCopy.getMatchers(), nodeSplitCopy.allTransitionsInOneTreeMatcher);
    }

    public DFAStateNode(short id, byte flags, LoopOptimizationNode loopOptimizationNode, short[] successors, CharMatcher[] matchers, AllTransitionsInOneTreeMatcher allTransitionsInOneTreeMatcher) {
        super(successors);
        assert id > 0;
        this.id = id;
        this.flags = flags;
        this.loopOptimizationNode = loopOptimizationNode;
        this.matchers = matchers;
        this.allTransitionsInOneTreeMatcher = allTransitionsInOneTreeMatcher;
    }

    public static byte buildFlags(boolean finalState, boolean anchoredFinalState, boolean hasBackwardPrefixState) {
        byte flags = 0;
        if (finalState) {
            flags |= FLAG_FINAL_STATE;
        }
        if (anchoredFinalState) {
            flags |= FLAG_ANCHORED_FINAL_STATE;
        }
        if (hasBackwardPrefixState) {
            flags |= FLAG_HAS_BACKWARD_PREFIX_STATE;
        }
        return flags;
    }

    public static LoopOptimizationNode buildLoopOptimizationNode(short loopTransitionIndex, char[] indexOfChars) {
        return new LoopOptimizationNode(loopTransitionIndex, indexOfChars);
    }

    @Override
    public DFAStateNode createNodeSplitCopy(short copyID) {
        return new DFAStateNode(this, copyID);
    }

    @Override
    public short getId() {
        return id;
    }

    public final CharMatcher[] getMatchers() {
        return matchers;
    }

    public BranchProfile getStateReachedProfile() {
        return stateReachedProfile;
    }

    public boolean isFinalState() {
        return flagIsSet(FLAG_FINAL_STATE);
    }

    public boolean isAnchoredFinalState() {
        return flagIsSet(FLAG_ANCHORED_FINAL_STATE);
    }

    public boolean hasBackwardPrefixState() {
        return flagIsSet(FLAG_HAS_BACKWARD_PREFIX_STATE);
    }

    private boolean flagIsSet(byte flag) {
        return (flags & flag) != 0;
    }

    public boolean hasLoopToSelf() {
        return loopOptimizationNode != null;
    }

    boolean isLoopToSelf(int transitionIndex) {
        return hasLoopToSelf() && transitionIndex == getLoopToSelf();
    }

    short getLoopToSelf() {
        assert hasLoopToSelf();
        return loopOptimizationNode.loopTransitionIndex;
    }

    boolean treeTransitionMatching() {
        return allTransitionsInOneTreeMatcher != null;
    }

    AllTransitionsInOneTreeMatcher getTreeMatcher() {
        return allTransitionsInOneTreeMatcher;
    }

    boolean sameResultAsRegularMatchers(TRegexDFAExecutorNode executor, char c, boolean compactString, int allTransitionsMatcherResult) {
        CompilerAsserts.neverPartOfCompilation();
        if (executor.isRegressionTestMode()) {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].execute(c, compactString)) {
                    return i == allTransitionsMatcherResult;
                }
            }
            return allTransitionsMatcherResult == -1;
        }
        return true;
    }

    /**
     * Calculates this state's successor by finding a transition that matches the current input. If
     * the successor is the state itself, this method continues consuming input characters until a
     * different successor is found. This special handling allows for partial loop unrolling inside
     * the DFA, as well as some optimizations in {@link CGTrackingDFAStateNode}.
     *
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param compactString {@code true} if the input string is a compact string, must be partial
     *            evaluation constant.
     */
    @Override
    public void executeFindSuccessor(VirtualFrame frame, TRegexDFAExecutorNode executor, boolean compactString) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(compactString);
        if (hasLoopToSelf()) {
            if (executor.isForward() && loopOptimizationNode.indexOfChars != null) {
                runIndexOf(frame, executor, compactString);
            } else {
                while (executor.hasNext(frame)) {
                    if (!checkMatch(frame, executor, compactString)) {
                        checkFinalState(frame, executor, prevIndex(frame, executor));
                        return;
                    }
                }
                executor.setSuccessorIndex(frame, atEnd(frame, executor));
            }
        } else {
            if (!executor.hasNext(frame)) {
                executor.setSuccessorIndex(frame, atEnd(frame, executor));
                return;
            }
            checkFinalState(frame, executor, curIndex(frame, executor));
            checkMatch(frame, executor, compactString);
        }
    }

    private void runIndexOf(VirtualFrame frame, TRegexDFAExecutorNode executor, boolean compactString) {
        final int preLoopIndex = executor.getIndex(frame);
        int indexOfResult = loopOptimizationNode.getIndexOfNode().execute(executor.getInput(frame),
                        preLoopIndex,
                        executor.getCurMaxIndex(frame),
                        loopOptimizationNode.indexOfChars);
        if (indexOfResult < 0) {
            executor.setIndex(frame, executor.getCurMaxIndex(frame));
            executor.setSuccessorIndex(frame, atEnd(frame, executor));
        } else {
            checkFinalState(frame, executor, indexOfResult);
            if (successors.length == 2) {
                int successor = (getLoopToSelf() + 1) % 2;
                CompilerAsserts.partialEvaluationConstant(successor);
                executor.setIndex(frame, indexOfResult + 1);
                executor.setSuccessorIndex(frame, successor);
            } else {
                executor.setIndex(frame, indexOfResult);
                checkMatch(frame, executor, compactString);
            }
        }
    }

    /**
     * Finds the first matching transition. The index of the element of {@link #getMatchers()} that
     * matched the current input character ( {@link TRegexDFAExecutorNode#getChar(VirtualFrame)}) or
     * {@link #FS_RESULT_NO_SUCCESSOR} is stored via
     * {@link TRegexDFAExecutorNode#setSuccessorIndex(VirtualFrame, int)}.
     *
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @param compactString {@code true} if the input string is a compact string, must be partial
     *            evaluation constant.
     * @return {@code true} if the matching transition loops back to this state, {@code false}
     *         otherwise.
     */
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private boolean checkMatch(VirtualFrame frame, TRegexDFAExecutorNode executor, boolean compactString) {
        final char c = executor.getChar(frame);
        executor.advance(frame);
        if (treeTransitionMatching()) {
            int successor = getTreeMatcher().checkMatchTree1(frame, executor, this, c);
            assert sameResultAsRegularMatchers(executor, c, compactString, successor) : this.toString();
            executor.setSuccessorIndex(frame, successor);
            return isLoopToSelf(successor);
        } else {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].execute(c, compactString)) {
                    CompilerAsserts.partialEvaluationConstant(i);
                    executor.setSuccessorIndex(frame, i);
                    return isLoopToSelf(i);
                }
            }
            executor.setSuccessorIndex(frame, FS_RESULT_NO_SUCCESSOR);
            return false;
        }
    }

    private void checkFinalState(VirtualFrame frame, TRegexDFAExecutorNode executor, int index) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState()) {
            storeResult(frame, executor, index, false);
        }
    }

    /**
     * Gets called if {@link TRegexDFAExecutorNode#getCurMaxIndex(VirtualFrame)} is reached (!
     * {@link TRegexDFAExecutorNode#hasNext(VirtualFrame)}). In {@link BackwardDFAStateNode},
     * execution may still continue here, which is why this method can return a successor index.
     *
     * @param frame a virtual frame as described by {@link TRegexDFAExecutorProperties}.
     * @param executor this node's parent {@link TRegexDFAExecutorNode}.
     * @return a successor index.
     */
    int atEnd(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        boolean anchored = isAnchoredFinalState() && executor.atEnd(frame);
        if (isFinalState() || anchored) {
            storeResult(frame, executor, curIndex(frame, executor), anchored);
        }
        return FS_RESULT_NO_SUCCESSOR;
    }

    void storeResult(VirtualFrame frame, TRegexDFAExecutorNode executor, int index, @SuppressWarnings("unused") boolean anchored) {
        CompilerAsserts.partialEvaluationConstant(this);
        executor.setResultInt(frame, index);
    }

    int curIndex(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        return executor.getIndex(frame);
    }

    int prevIndex(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        return executor.getIndex(frame) - 1;
    }

    int nextIndex(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        return executor.getIndex(frame) + 1;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        DebugUtil.appendNodeId(sb, id).append(": ");
        if (!treeTransitionMatching()) {
            sb.append(matchers.length).append(" successors");
        }
        if (isAnchoredFinalState()) {
            sb.append(", AFS");
        }
        if (isFinalState()) {
            sb.append(", FS");
        }
        sb.append(":\n");
        if (treeTransitionMatching()) {
            sb.append("      ").append(getTreeMatcher()).append("\n      successors: ").append(Arrays.toString(successors)).append("\n");
        } else {
            for (int i = 0; i < matchers.length; i++) {
                sb.append("      ").append(i).append(": ").append(matchers[i]).append(" -> ");
                DebugUtil.appendNodeId(sb, getSuccessors()[i]).append("\n");
            }
        }
        return sb.toString();
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        JsonArray transitions = Json.array();
        if (matchers != null) {
            for (int i = 0; i < matchers.length; i++) {
                transitions.append(Json.obj(Json.prop("matcher", matchers[i].toString()), Json.prop("target", successors[i])));
            }
        }
        return Json.obj(Json.prop("id", id),
                        Json.prop("anchoredFinalState", isAnchoredFinalState()),
                        Json.prop("finalState", isFinalState()),
                        Json.prop("loopToSelf", hasLoopToSelf()),
                        Json.prop("transitions", transitions));
    }
}
