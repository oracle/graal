/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.graalvm.collections.EconomicMap;

public final class RubyCaseUnfoldingTrie {

    public static final RubyCaseUnfoldingTrie CASE_UNFOLD;

    static {
        CASE_UNFOLD = new RubyCaseUnfoldingTrie(0);
        RubyCaseFoldingData.CASE_FOLD.forEach((k, v) -> CASE_UNFOLD.add(k, v, 0));
    }

    private final List<Integer> codepoints;
    private final EconomicMap<Integer, RubyCaseUnfoldingTrie> childNodes;
    private final int depth;

    public RubyCaseUnfoldingTrie(int depth) {
        this.codepoints = new ArrayList<>();
        this.childNodes = EconomicMap.create();
        this.depth = depth;
    }

    public void add(int codepoint, int[] caseFoldedString, int offset) {
        if (caseFoldedString.length == offset) {
            codepoints.add(codepoint);
            return;
        }

        if (!hasChildAt(caseFoldedString[offset])) {
            childNodes.put(caseFoldedString[offset], new RubyCaseUnfoldingTrie(depth + 1));
        }
        getChildAt(caseFoldedString[offset]).add(codepoint, caseFoldedString, offset + 1);
    }

    public boolean hasChildAt(int index) {
        return childNodes.containsKey(index);
    }

    public RubyCaseUnfoldingTrie getChildAt(int index) {
        return childNodes.get(index);
    }

    public List<Integer> getCodepoints() {
        return codepoints;
    }

    public int getDepth() {
        return depth;
    }

    public static final class Unfolding {
        private final int start;
        private final int length;
        private final int codepoint;

        public Unfolding(int start, int length, int codepoint) {
            this.start = start;
            this.length = length;
            this.codepoint = codepoint;
        }

        public int getStart() {
            return start;
        }

        public int getLength() {
            return length;
        }

        public int getEnd() {
            return start + length;
        }

        public int getCodepoint() {
            return codepoint;
        }
    }

    public static List<Unfolding> findUnfoldings(List<Integer> caseFolded) {
        List<RubyCaseUnfoldingTrie> states = new ArrayList<>();
        List<RubyCaseUnfoldingTrie> nextStates = new ArrayList<>();
        List<Unfolding> unfoldings = new ArrayList<>();

        for (int i = 0; i < caseFolded.size(); i++) {
            int codepoint = caseFolded.get(i);

            states.add(RubyCaseUnfoldingTrie.CASE_UNFOLD);

            for (RubyCaseUnfoldingTrie state : states) {
                if (state.hasChildAt(codepoint)) {
                    RubyCaseUnfoldingTrie newState = state.getChildAt(codepoint);
                    nextStates.add(newState);
                    for (int unfoldedCodepoint : newState.getCodepoints()) {
                        unfoldings.add(new Unfolding(i + 1 - newState.getDepth(), newState.getDepth(), unfoldedCodepoint));
                    }
                }
            }

            List<RubyCaseUnfoldingTrie> statesTmp = states;
            states = nextStates;
            nextStates = statesTmp;

            nextStates.clear();
        }

        unfoldings.sort(Comparator.comparingInt(Unfolding::getStart).thenComparing(Comparator.comparingInt(Unfolding::getLength).reversed()));

        return unfoldings;
    }

    public static List<Integer> findSingleCharUnfoldings(int[] caseFolded) {
        RubyCaseUnfoldingTrie state = CASE_UNFOLD;

        for (int codepoint : caseFolded) {
            assert state.hasChildAt(codepoint);
            state = state.getChildAt(codepoint);
        }

        return state.getCodepoints();
    }

    public static List<Integer> findSingleCharUnfoldings(int caseFolded) {
        if (CASE_UNFOLD.hasChildAt(caseFolded)) {
            return CASE_UNFOLD.getChildAt(caseFolded).getCodepoints();
        } else {
            return Collections.emptyList();
        }
    }
}
