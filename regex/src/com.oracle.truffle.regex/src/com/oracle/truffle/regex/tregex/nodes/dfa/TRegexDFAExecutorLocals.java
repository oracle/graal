/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.dfa;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;

/**
 * Container for all local variables used in {@link TRegexDFAExecutorNode}.
 */
public final class TRegexDFAExecutorLocals extends TRegexExecutorLocals {

    private int curMinIndex;
    private int result;
    private int matchStart;
    private short lastTransition;
    private int lastIndex;
    private final DFACaptureGroupTrackingData cgData;

    public TRegexDFAExecutorLocals(TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index, DFACaptureGroupTrackingData cgData) {
        super(input, fromIndex, maxIndex, regionFrom, regionTo, index);
        result = TRegexDFAExecutorNode.NO_MATCH;
        this.cgData = cgData;
    }

    /**
     * The minimum index as checked by
     * {@link TRegexExecutorNode#inputHasNext(TRegexExecutorLocals)}. In most cases this value is
     * {@code 0}, but backward matching nodes change this value while matching.
     *
     * @return the minimum index as checked by
     *         {@link TRegexExecutorNode#inputHasNext(TRegexExecutorLocals)}.
     *
     * @see BackwardDFAStateNode
     */
    public int getCurMinIndex() {
        return curMinIndex;
    }

    public void setCurMinIndex(int curMinIndex) {
        this.curMinIndex = curMinIndex;
    }

    public short getLastTransition() {
        return lastTransition;
    }

    public void setLastTransition(short lastTransition) {
        this.lastTransition = lastTransition;
    }

    public void setLastIndex() {
        lastIndex = getIndex();
    }

    public int getLastIndex() {
        return lastIndex;
    }

    public int getResultInt() {
        return result;
    }

    public void setResultInt(int result) {
        this.result = result;
    }

    public int getMatchStart() {
        return matchStart;
    }

    public void setMatchStart(int matchStart) {
        this.matchStart = matchStart;
    }

    public DFACaptureGroupTrackingData getCGData() {
        return cgData;
    }

    public TRegexDFAExecutorLocals toInnerLiteralBackwardLocals() {
        return new TRegexDFAExecutorLocals(getInput(), getFromIndex(), getMaxIndex(), getRegionFrom(), getRegionTo(), getIndex(), cgData);
    }
}
