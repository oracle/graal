/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.regex.charset.Constants;

/**
 * This class provides an alternative way of calculating the next transition - instead of checking
 * all transitions in sequential manner, all ranges of all transitions are merged into one sorted
 * array, which is then searched in tree-recursive fashion.
 */
public final class AllTransitionsInOneTreeMatcher {

    @CompilationFinal(dimensions = 1) private final int[] sortedRanges;
    @CompilationFinal(dimensions = 1) private final short[] rangeTreeSuccessors;

    /**
     * Constructs a new {@link AllTransitionsInOneTreeMatcher}.
     *
     * @param sortedRanges a sorted list of adjacent character ranges, in the following format:
     *            Every character in the array simultaneously represents the inclusive lower bound
     *            of a range and the exclusive upper bound of a range. The algorithm adds an
     *            implicit zero at the begin and an implicit {@link Constants#MAX_CODE_POINT} + 1 at
     *            the end of the array. An array representing the ranges
     *            {@code [0x00-0x10][0x10-0xff][0xff-0x2000][0x2000-0x10000]} (represented with
     *            exclusive upper bound) would be: {@code [0x10, 0xff, 0x2000]}.
     * @param rangeTreeSuccessors the list of successors corresponding to every range in the sorted
     *            list of ranges. every entry in this array is an index of
     *            {@link DFAStateNode#getSuccessors()}.
     */
    public AllTransitionsInOneTreeMatcher(int[] sortedRanges, short[] rangeTreeSuccessors) {
        assert sortedRanges.length > 0 : "This class should never be used for trivial transitions, use a list of CharMatchers instead!";
        assert rangeTreeSuccessors.length == sortedRanges.length + 1;
        this.sortedRanges = sortedRanges;
        this.rangeTreeSuccessors = rangeTreeSuccessors;
    }

    public int checkMatchTree(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, DFAStateNode stateNode, int c) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(stateNode);
        return checkMatchTree(locals, executor, stateNode, 0, sortedRanges.length - 1, c);
    }

    private int checkMatchTree(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, DFAStateNode stateNode, int fromIndex, int toIndex, int c) {
        CompilerAsserts.partialEvaluationConstant(stateNode);
        CompilerAsserts.partialEvaluationConstant(fromIndex);
        CompilerAsserts.partialEvaluationConstant(toIndex);
        if (fromIndex > toIndex) {
            final short successor = rangeTreeSuccessors[fromIndex];
            if (successor != DFAStateNode.FS_RESULT_NO_SUCCESSOR) {
                stateNode.successorFound(locals, executor, successor);
            }
            return successor;
        }
        final int mid = (fromIndex + toIndex) >>> 1;
        CompilerAsserts.partialEvaluationConstant(mid);
        if (c < sortedRanges[mid]) {
            return checkMatchTree(locals, executor, stateNode, fromIndex, mid - 1, c);
        } else {
            return checkMatchTree(locals, executor, stateNode, mid + 1, toIndex, c);
        }
    }

    @TruffleBoundary
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AllTransitionsInOneTreeMatcher: [");
        boolean first = true;
        for (int c : sortedRanges) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            if (c > 0xff) {
                sb.append(String.format("%04x", c));
            } else {
                sb.append(String.format("%02x", c));
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
