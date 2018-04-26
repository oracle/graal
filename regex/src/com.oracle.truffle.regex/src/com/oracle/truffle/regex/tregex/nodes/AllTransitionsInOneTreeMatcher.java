/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.util.MathUtil;

/**
 * This class is not really a {@link CharMatcher}, but implements the interface anyway in order to
 * avoid extra pointers in {@link com.oracle.truffle.regex.tregex.nodes.DFAStateNode}. It provides
 * an alternative way of calculating the next transition - instead of checking all transitions in
 * sequential manner, all ranges of all transitions are merged into one sorted array, which is then
 * searched in tree-recursive fashion.
 */
public final class AllTransitionsInOneTreeMatcher implements CharMatcher {

    @CompilerDirectives.CompilationFinal(dimensions = 1) private final char[] sortedRanges;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private final short[] rangeTreeSuccessors;

    /**
     * Constructs a new {@link AllTransitionsInOneTreeMatcher}.
     *
     * @param sortedRanges a sorted list of adjacent character ranges, in the following format:
     *            Every character in the array simultaneously represents the inclusive lower bound
     *            of a range and the exclusive upper bound of a range. The algorithm adds an
     *            implicit zero at the begin and an implicit {@link Character#MAX_VALUE} + 1 at the
     *            end of the array. An array representing the ranges
     *            {@code [0x00-0x10][0x10-0xff][0xff-0x2000][0x2000-0x10000]} (represented with
     *            exclusive upper bound) would be: {@code [0x10, 0xff, 0x2000]}.
     * @param rangeTreeSuccessors the list of successors corresponding to every range in the sorted
     *            list of ranges. every entry in this array is an index of
     *            {@link DFAStateNode#getSuccessors()}.
     */
    public AllTransitionsInOneTreeMatcher(char[] sortedRanges, short[] rangeTreeSuccessors) {
        assert sortedRanges.length > 0 : "This class should never be used for trivial transitions, use a list of CharMatchers instead!";
        assert rangeTreeSuccessors.length == sortedRanges.length + 1;
        this.sortedRanges = sortedRanges;
        this.rangeTreeSuccessors = rangeTreeSuccessors;
    }

    public int checkMatchTree1(VirtualFrame frame, TRegexDFAExecutorNode executor, DFAStateNode stateNode, char c) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(stateNode);
        return checkMatchTree1(frame, executor, stateNode, 0, sortedRanges.length - 1, c);
    }

    private int checkMatchTree1(VirtualFrame frame, TRegexDFAExecutorNode executor, DFAStateNode stateNode, int fromIndex, int toIndex, char c) {
        CompilerAsserts.partialEvaluationConstant(fromIndex);
        CompilerAsserts.partialEvaluationConstant(toIndex);
        if (fromIndex > toIndex) {
            final short successor = rangeTreeSuccessors[fromIndex];
            if (successor != DFAStateNode.FS_RESULT_NO_SUCCESSOR) {
                stateNode.successorFound1(frame, executor, successor);
            }
            return successor;
        }
        final int mid = (fromIndex + toIndex) >>> 1;
        CompilerAsserts.partialEvaluationConstant(mid);
        if (c < sortedRanges[mid]) {
            return checkMatchTree1(frame, executor, stateNode, fromIndex, mid - 1, c);
        } else {
            return checkMatchTree1(frame, executor, stateNode, mid + 1, toIndex, c);
        }
    }

    public int checkMatchTree2(VirtualFrame frame, TRegexDFAExecutorNode executor, DFAStateNode stateNode, char c) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(stateNode);
        return checkMatchTree2(frame, executor, stateNode, 0, sortedRanges.length - 1, c);
    }

    private int checkMatchTree2(VirtualFrame frame, TRegexDFAExecutorNode executor, DFAStateNode stateNode, int fromIndex, int toIndex, char c) {
        CompilerAsserts.partialEvaluationConstant(fromIndex);
        CompilerAsserts.partialEvaluationConstant(toIndex);
        if (fromIndex > toIndex) {
            final short successor = rangeTreeSuccessors[fromIndex];
            if (successor == DFAStateNode.FS_RESULT_NO_SUCCESSOR) {
                stateNode.noSuccessor2(frame, executor);
            } else if (!stateNode.isLoopToSelf(successor)) {
                stateNode.successorFound2(frame, executor, successor);
            }
            return successor;
        }
        final int mid = (fromIndex + toIndex) >>> 1;
        CompilerAsserts.partialEvaluationConstant(mid);
        if (c < sortedRanges[mid]) {
            return checkMatchTree2(frame, executor, stateNode, fromIndex, mid - 1, c);
        } else {
            return checkMatchTree2(frame, executor, stateNode, mid + 1, toIndex, c);
        }
    }

    public int checkMatchTree3(VirtualFrame frame, TRegexDFAExecutorNode executor, DFAStateNode stateNode, char c, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(stateNode);
        return checkMatchTree3(frame, executor, stateNode, 0, sortedRanges.length - 1, c, preLoopIndex);
    }

    private int checkMatchTree3(VirtualFrame frame, TRegexDFAExecutorNode executor, DFAStateNode stateNode, int fromIndex, int toIndex, char c, int preLoopIndex) {
        CompilerAsserts.partialEvaluationConstant(fromIndex);
        CompilerAsserts.partialEvaluationConstant(toIndex);
        if (fromIndex > toIndex) {
            final short successor = rangeTreeSuccessors[fromIndex];
            if (successor == DFAStateNode.FS_RESULT_NO_SUCCESSOR) {
                stateNode.noSuccessor3(frame, executor, preLoopIndex);
            } else if (!stateNode.isLoopToSelf(successor)) {
                stateNode.successorFound3(frame, executor, successor, preLoopIndex);
            }
            return successor;
        }
        final int mid = (fromIndex + toIndex) >>> 1;
        CompilerAsserts.partialEvaluationConstant(mid);
        if (c < sortedRanges[mid]) {
            return checkMatchTree3(frame, executor, stateNode, fromIndex, mid - 1, c, preLoopIndex);
        } else {
            return checkMatchTree3(frame, executor, stateNode, mid + 1, toIndex, c, preLoopIndex);
        }
    }

    @Override
    public boolean match(char c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int estimatedCost() {
        return MathUtil.log2ceil(sortedRanges.length);
    }

    @CompilerDirectives.TruffleBoundary
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AllTransitionsInOneTreeMatcher: [");
        boolean first = true;
        for (char c : sortedRanges) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            if (c > 0xff) {
                sb.append(String.format("%04x", (int) c));
            } else {
                sb.append(String.format("%02x", (int) c));
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
