/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.nfa;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;

public final class TRegexNFAExecutorLocals extends TRegexExecutorLocals {

    /**
     * Frame size = 1 (state ID) + 2 * nCaptureGroups (start and end indices).
     */
    private final int frameSize;
    private final int maxSize;
    /**
     * A record of the paths that we are considering for our optimal match. Every path is
     * represented as a frame which consists of the ID of the last state on the path and the capture
     * group indices that have been set along the path. The paths in this array is sorted in
     * priority order, from highest priority to lowest priority.
     */
    private int[] curStates;
    /**
     * A buffer of the paths that we will be considering in the next step. Every path in this array
     * was created by taking a path from {@link #curStates} and following the transition labeled
     * with the current character.
     */
    private int[] nextStates;
    int curStatesLength = 0;
    int nextStatesLength = 0;
    int iCurStates = 0;
    private long[] marks;
    /**
     * This array stores the best (highest priority) match found so far. Whenever a match is found,
     * all lower priority paths through the NFA are discarded. Therefore, any match which would be
     * found later is guaranteed to be of higher priority and can be used to overwrite this result.
     */
    private int[] result;
    /**
     * Indicates whether a path to a final state has been completed in this step. If true, then no
     * further paths should be considered, as they would have a lower priority than this completed
     * path.
     */
    private boolean resultPushed = false;

    public TRegexNFAExecutorLocals(Object input, int fromIndex, int index, int maxIndex, int nCaptureGroups, int nStates) {
        super(input, fromIndex, maxIndex, index);
        this.frameSize = 1 + nCaptureGroups * 2;
        this.maxSize = nStates * frameSize;
        this.curStates = new int[frameSize * 8];
        this.nextStates = new int[frameSize * 8];
        this.marks = new long[((nStates - 1) >> 6) + 1];
    }

    public void addInitialState(int stateId) {
        curStates[curStatesLength] = stateId;
        Arrays.fill(curStates, curStatesLength + 1, curStatesLength + frameSize, -1);
        curStatesLength += frameSize;
    }

    public boolean curStatesEmpty() {
        return curStatesLength == 0;
    }

    public boolean successorsEmpty() {
        return nextStatesLength == 0;
    }

    public boolean hasNext() {
        return iCurStates < curStatesLength;
    }

    public int next() {
        iCurStates += frameSize;
        return curStates[iCurStates - frameSize];
    }

    public long[] getMarks() {
        return marks;
    }

    public void pushSuccessor(NFAStateTransition t, boolean copy) {
        if (nextStatesLength >= nextStates.length) {
            nextStates = Arrays.copyOf(nextStates, Math.min(nextStates.length * 2, maxSize));
        }
        nextStates[nextStatesLength] = t.getTarget().getId();
        if (copy) {
            System.arraycopy(curStates, iCurStates - frameSize + 1, nextStates, nextStatesLength + 1, frameSize - 1);
        } else {
            Arrays.fill(nextStates, nextStatesLength + 1, nextStatesLength + frameSize, -1);
        }
        t.getGroupBoundaries().apply(nextStates, nextStatesLength + 1, getIndex());
        nextStatesLength += frameSize;
    }

    public void nextState() {
        int[] tmp = curStates;
        curStates = nextStates;
        nextStates = tmp;
        curStatesLength = nextStatesLength;
        nextStatesLength = 0;
        iCurStates = 0;
        Arrays.fill(marks, 0);
        resultPushed = false;
    }

    public void pushResult(NFAStateTransition t, boolean copy) {
        resultPushed = true;
        if (result == null) {
            result = new int[frameSize - 1];
        }
        if (copy) {
            System.arraycopy(curStates, iCurStates + 1 - frameSize, result, 0, frameSize - 1);
        } else {
            Arrays.fill(result, -1);
        }
        t.getGroupBoundaries().apply(result, 0, getIndex());
    }

    public boolean hasResult() {
        return result != null;
    }

    public boolean isResultPushed() {
        return resultPushed;
    }

    public int[] getResult() {
        return result;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("curState: (");
        for (int i = 0; i < curStatesLength; i += frameSize) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(curStates[i]);
        }
        sb.append("), nextState: (");
        for (int i = 0; i < nextStatesLength; i += frameSize) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(nextStates[i]);
        }
        return sb.append(")").toString();
    }
}
