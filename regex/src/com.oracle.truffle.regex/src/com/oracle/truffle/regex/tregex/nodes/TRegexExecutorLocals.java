/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.regex.tregex.nodes;

import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.RegexExecNode;

/**
 * Base class for local variables used by an executor node called by a {@link TRegexExecNode}.
 */
public abstract class TRegexExecutorLocals {

    private final TruffleString input;
    private final int fromIndex;
    private final int maxIndex;
    private final int regionFrom;
    private final int regionTo;
    private int index;
    private int nextIndex;
    private int loopCount;

    public TRegexExecutorLocals(TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index) {
        this.input = input;
        this.fromIndex = fromIndex;
        this.maxIndex = maxIndex;
        this.regionFrom = regionFrom;
        this.regionTo = regionTo;
        this.index = index;
    }

    /**
     * The {@code input} argument given to
     * {@link RegexExecNode#execute(com.oracle.truffle.api.frame.VirtualFrame)}.
     *
     * @return the {@code input} argument given to
     *         {@link RegexExecNode#execute(com.oracle.truffle.api.frame.VirtualFrame)}.
     */
    public final TruffleString getInput() {
        return input;
    }

    /**
     * The {@code fromIndex} argument given to
     * {@link RegexExecNode#execute(com.oracle.truffle.api.frame.VirtualFrame)}.
     *
     * @return the {@code fromIndex} argument given to
     *         {@link RegexExecNode#execute(com.oracle.truffle.api.frame.VirtualFrame)}.
     */
    public final int getFromIndex() {
        return fromIndex;
    }

    /**
     * The maximum index as given by the parent {@link TRegexExecNode}.
     *
     * @return the maximum index as given by the parent {@link TRegexExecNode}.
     */
    public final int getMaxIndex() {
        return maxIndex;
    }

    public int getRegionFrom() {
        return regionFrom;
    }

    public int getRegionTo() {
        return regionTo;
    }

    /**
     * The index pointing into {@link #getInput()}.
     *
     * @return the current index of {@link #getInput()} that is being processed.
     */
    public final int getIndex() {
        return index;
    }

    public final void setIndex(int index) {
        this.index = index;
    }

    public final int getNextIndex() {
        return nextIndex;
    }

    public final void setNextIndex(int nextIndex) {
        this.nextIndex = nextIndex;
    }

    public final void incLoopCount(TRegexExecutorNode executorNode) {
        if (((++loopCount) & 0xffff) == 0) {
            LoopNode.reportLoopCount(executorNode, 0x10000);
        }
    }
}
