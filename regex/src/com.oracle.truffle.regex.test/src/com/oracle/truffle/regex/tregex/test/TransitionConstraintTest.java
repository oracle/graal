/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.test;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.automaton.AbstractTransition;
import com.oracle.truffle.regex.tregex.automaton.BasicState;
import com.oracle.truffle.regex.tregex.automaton.StateIndex;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.automaton.StateTransitionCanonicalizer;
import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.automaton.TransitionConstraint;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.string.Encoding;

public class TransitionConstraintTest {

    private static final long[] EMPTY = new long[0];
    private static final long[] GUARD = {TransitionConstraint.create(0, 0, TransitionConstraint.anyLtMax)};

    @Test
    public void transitionConstraintDocTest() {
        long a = TransitionConstraint.create(0, 0, TransitionConstraint.anyLtMax);
        long b = TransitionConstraint.create(1, 0, TransitionConstraint.anyLtMax);
        long c = TransitionConstraint.create(2, 0, TransitionConstraint.anyLtMax);
        long d = TransitionConstraint.create(3, 0, TransitionConstraint.anyLtMax);
        long[] lhs = new long[]{a, b, TransitionConstraint.not(c)};
        long[] rhs = new long[]{TransitionConstraint.not(c), d};
        TransitionConstraint.MergeResult result = TransitionConstraint.intersectAndSubtract(lhs, rhs);
        Assert.assertEquals(1, result.lhs().length);
        Assert.assertArrayEquals(new long[]{a, b, TransitionConstraint.not(c), TransitionConstraint.not(d)}, result.lhs()[0]);
        Assert.assertArrayEquals(new long[]{a, b, TransitionConstraint.not(c), d}, result.middle());
        Assert.assertEquals(2, result.rhs().length);
        Assert.assertArrayEquals(new long[]{TransitionConstraint.not(a), TransitionConstraint.not(c), d}, result.rhs()[0]);
        Assert.assertArrayEquals(new long[]{TransitionConstraint.not(b), TransitionConstraint.not(c), d}, result.rhs()[1]);
    }

    private static int[] order(Fragment fragment) {
        return Arrays.stream(fragment.getTransitionSet().getTransitions()).mapToInt(Edge::getId).toArray();
    }

    private static Fragment[] canonicalize(boolean constrained, boolean finalTargets) {
        Index index = new Index(finalTargets);
        Canonicalizer canonicalizer = new Canonicalizer(index);
        for (int i = 1; i <= 3; i++) {
            canonicalizer.addArgument(new Edge(i, index.states[0], index.states[i]), CodePointSet.create('a'), constrained ? GUARD : EMPTY, EMPTY);
        }
        return canonicalizer.run(new CompilationBuffer(Encoding.UTF_16));
    }

    @Test
    public void unconstrainedInputOrderControl() {
        Fragment[] result = canonicalize(false, false);
        Assert.assertEquals(1, result.length);
        Assert.assertArrayEquals(new int[]{1, 2, 3}, order(result[0]));
    }

    @Test
    public void constrainedInputOrderPreserved() {
        Fragment[] result = canonicalize(true, false);
        Assert.assertEquals(1, result.length);
        Assert.assertArrayEquals(GUARD, result[0].getConstraints());
        Assert.assertArrayEquals(new int[]{1, 2, 3}, order(result[0]));
    }

    @Test
    public void unconstrainedFinalPriorityControl() {
        Fragment[] result = canonicalize(false, true);
        Assert.assertEquals(1, result.length);
        Assert.assertArrayEquals(new int[]{1}, order(result[0]));
    }

    @Test
    public void constrainedFinalPriorityPreserved() {
        Fragment[] result = canonicalize(true, true);
        Assert.assertEquals(1, result.length);
        Assert.assertArrayEquals(new int[]{1}, order(result[0]));
    }

    @Test
    public void unrelatedGuardDoesNotReverseUnconstrainedTransitions() {
        Index index = new Index(false);
        Canonicalizer canonicalizer = new Canonicalizer(index);
        canonicalizer.addArgument(new Edge(1, index.states[0], index.states[1]), CodePointSet.create('a'), EMPTY, EMPTY);
        canonicalizer.addArgument(new Edge(2, index.states[0], index.states[2]), CodePointSet.create('a'), EMPTY, EMPTY);
        canonicalizer.addArgument(new Edge(3, index.states[0], index.states[3]), CodePointSet.create('z'), GUARD, EMPTY);
        Fragment[] result = canonicalizer.run(new CompilationBuffer(Encoding.UTF_16));
        Fragment a = Arrays.stream(result).filter(f -> f.getCodePointSet().equals(CodePointSet.create('a'))).findFirst().orElseThrow();
        Assert.assertEquals(0, a.getConstraints().length);
        Assert.assertArrayEquals(new int[]{1, 2}, order(a));
    }

    // A four-state automaton exercises canonicalization without depending on regex parsing.
    private static final class State extends BasicState<State, Edge> {
        private final boolean reachesFinal;

        State(int id, boolean reachesFinal) {
            super(id, new Edge[0]);
            this.reachesFinal = reachesFinal;
        }

        @Override
        protected boolean hasUnGuardedTransitionToUnAnchoredFinalState(boolean forward) {
            return reachesFinal;
        }

        @Override
        protected Edge[] createTransitionsArray(int length) {
            return new Edge[length];
        }
    }

    private record Edge(int id, State source, State target) implements AbstractTransition<State, Edge> {
        @Override
        public int getId() {
            return id;
        }

        @Override
        public State getSource() {
            return source;
        }

        @Override
        public State getTarget() {
            return target;
        }
    }

    private static final class Index implements StateIndex<State> {
        private final State[] states = new State[4];

        Index(boolean finalTargets) {
            for (int i = 0; i < states.length; i++) {
                states[i] = new State(i, i != 0 && finalTargets);
            }
        }

        @Override
        public int getNumberOfStates() {
            return states.length;
        }

        @Override
        public int getId(State state) {
            return state.getId();
        }

        @Override
        public State getState(int id) {
            return states[id];
        }
    }

    private static final class Fragment extends TransitionBuilder<Index, State, Edge> {
        Fragment(Edge[] transitions, StateSet<Index, State> states, CodePointSet cps, long[] constraints, long[] operations) {
            super(transitions, states, cps, constraints, operations);
        }
    }

    private static final class Canonicalizer extends StateTransitionCanonicalizer<Index, State, Edge, Fragment> {
        Canonicalizer(Index index) {
            super(index, true, true, false);
        }

        @Override
        protected Fragment createTransitionBuilder(Edge[] transitions, StateSet<Index, State> states, CodePointSet cps, long[] constraints, long[] operations) {
            return new Fragment(transitions, states, cps, constraints, operations);
        }

        @Override
        protected boolean canMerge(Fragment a, Fragment b) {
            return Arrays.equals(a.getTransitionSet().getTransitions(), b.getTransitionSet().getTransitions());
        }

        @Override
        protected Edge[] createTransitionArray(int size) {
            return new Edge[size];
        }

        @Override
        protected Fragment[] createResultArray(int size) {
            return new Fragment[size];
        }
    }
}
