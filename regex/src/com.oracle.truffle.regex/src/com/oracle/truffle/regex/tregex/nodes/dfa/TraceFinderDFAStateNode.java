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
package com.oracle.truffle.regex.tregex.nodes.dfa;

public class TraceFinderDFAStateNode extends BackwardDFAStateNode {

    public static final byte NO_PRE_CALC_RESULT = (byte) 0xff;

    private final byte preCalculatedUnAnchoredResult;
    private final byte preCalculatedAnchoredResult;

    public TraceFinderDFAStateNode(short id, byte flags, short loopTransitionIndex, LoopOptimizationNode loopOptimizationNode, short[] successors, Matchers matchers,
                    AllTransitionsInOneTreeMatcher allTransitionsInOneTreeMatcher, byte preCalculatedUnAnchoredResult, byte preCalculatedAnchoredResult) {
        super(id, flags, loopTransitionIndex, loopOptimizationNode, successors, matchers, null, allTransitionsInOneTreeMatcher);
        this.preCalculatedUnAnchoredResult = preCalculatedUnAnchoredResult;
        this.preCalculatedAnchoredResult = initPreCalculatedAnchoredResult(preCalculatedUnAnchoredResult, preCalculatedAnchoredResult);
    }

    private TraceFinderDFAStateNode(TraceFinderDFAStateNode copy, short copyID) {
        super(copy, copyID);
        this.preCalculatedUnAnchoredResult = copy.preCalculatedUnAnchoredResult;
        this.preCalculatedAnchoredResult = copy.preCalculatedAnchoredResult;
    }

    private static byte initPreCalculatedAnchoredResult(byte preCalculatedUnAnchoredResult, byte preCalculatedAnchoredResult) {
        if (Byte.toUnsignedInt(preCalculatedUnAnchoredResult) < Byte.toUnsignedInt(preCalculatedAnchoredResult)) {
            // unanchored result has higher priority, so the anchored result will never be chosen
            return NO_PRE_CALC_RESULT;
        }
        return preCalculatedAnchoredResult;
    }

    @Override
    public DFAStateNode createNodeSplitCopy(short copyID) {
        return new TraceFinderDFAStateNode(this, copyID);
    }

    private boolean hasPreCalculatedUnAnchoredResult() {
        return preCalculatedUnAnchoredResult != NO_PRE_CALC_RESULT;
    }

    private int getPreCalculatedUnAnchoredResult() {
        return Byte.toUnsignedInt(preCalculatedUnAnchoredResult);
    }

    private boolean hasPreCalculatedAnchoredResult() {
        return preCalculatedAnchoredResult != NO_PRE_CALC_RESULT;
    }

    private int getPreCalculatedAnchoredResult() {
        return Byte.toUnsignedInt(preCalculatedAnchoredResult);
    }

    @Override
    void storeResult(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean anchored) {
        if (hasPreCalculatedAnchoredResult() && anchored) {
            locals.setResultInt(getPreCalculatedAnchoredResult());
        } else {
            assert hasPreCalculatedUnAnchoredResult();
            locals.setResultInt(getPreCalculatedUnAnchoredResult());
        }
    }
}
