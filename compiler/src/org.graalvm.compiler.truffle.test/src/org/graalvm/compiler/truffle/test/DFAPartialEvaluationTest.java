/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

import static org.graalvm.compiler.core.common.CompilationIdentifier.INVALID_COMPILATION_ID;

import org.junit.Assert;
import org.junit.Test;

public class DFAPartialEvaluationTest extends PartialEvaluationTest {

    public static class CompilationFinalBitSet {

        private static int wordIndex(int i) {
            return i >> 6;
        }

        @CompilationFinal(dimensions = 1) private final long[] words;

        public CompilationFinalBitSet(long[] words) {
            this.words = words;
        }

        public boolean get(int b) {
            return (words[wordIndex(b)] & (1L << b)) != 0;
        }

        public boolean get(byte b) {
            return get(Byte.toUnsignedInt(b));
        }
    }

    public interface ByteMatcher {
        boolean match(byte c);
    }

    public static final class AnyByteMatcher implements ByteMatcher {

        private AnyByteMatcher() {
        }

        private static final AnyByteMatcher INSTANCE = new AnyByteMatcher();

        public static AnyByteMatcher create() {
            return INSTANCE;
        }

        @Override
        public boolean match(byte b) {
            return true;
        }
    }

    public static final class EmptyByteMatcher implements ByteMatcher {

        private EmptyByteMatcher() {
        }

        private static final EmptyByteMatcher INSTANCE = new EmptyByteMatcher();

        public static EmptyByteMatcher create() {
            return INSTANCE;
        }

        @Override
        public boolean match(byte c) {
            return false;
        }
    }

    public static final class SingleByteMatcher implements ByteMatcher {

        public final byte b;

        private SingleByteMatcher(byte b) {
            this.b = b;
        }

        public static SingleByteMatcher create(byte b) {
            return new SingleByteMatcher(b);
        }

        public static SingleByteMatcher create(int b) {
            return new SingleByteMatcher((byte) b);
        }

        @Override
        public boolean match(byte bo) {
            return b == bo;
        }
    }

    public static final class RangeByteMatcher implements ByteMatcher {

        public final char lo;
        public final char hi;

        private RangeByteMatcher(char lo, char hi) {
            assert hi > lo;
            this.lo = lo;
            this.hi = hi;
        }

        public static ByteMatcher create(char lo, char hi) {
            return new RangeByteMatcher(lo, hi);
        }

        public static ByteMatcher create(int lo, int hi) {
            return create((char) lo, (char) hi);
        }

        private boolean match(int b) {
            return lo <= b && hi >= b;
        }

        @Override
        public boolean match(byte b) {
            return match(Byte.toUnsignedInt(b));
        }
    }

    public static final class MultiByteMatcher implements ByteMatcher {

        private final CompilationFinalBitSet bitSet;

        private MultiByteMatcher(CompilationFinalBitSet bitSet) {
            this.bitSet = bitSet;
        }

        public static ByteMatcher create(CompilationFinalBitSet bitSet) {
            return new MultiByteMatcher(bitSet);
        }

        @Override
        public boolean match(byte b) {
            return bitSet.get(b);
        }
    }

    public static class DFAStateNode extends Node {

        @CompilationFinal(dimensions = 1) private int[] successors;

        @CompilationFinal(dimensions = 1) private ByteMatcher[] matchers;

        @CompilationFinal private boolean isFinalState;

        public void setMatchers(ByteMatcher[] matchers) {
            this.matchers = matchers;
        }

        public ByteMatcher[] getMatchers() {
            return matchers;
        }

        public int[] getSuccessors() {
            return successors;
        }

        public void setSuccessors(int[] successors) {
            this.successors = successors;
        }

        public void setFinalState() {
            isFinalState = true;
        }

        public boolean isFinalState() {
            return isFinalState;
        }

        @ExplodeLoop
        public int executeSuccessorIndex(byte value) {
            for (int i = 0; i < matchers.length; i++) {
                if (matchers[i].match(value)) {
                    return i;
                }
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException();
        }
    }

    public static final class InputStringIterator {

        private int index = 0;

        public boolean hasNext(byte[] inputString) {
            return index < inputString.length;
        }

        public byte next(byte[] inputString) {
            return inputString[index++];
        }
    }

    public static class TRegexDFAExecutorNode extends Node {

        private final InputStringIterator inputStringIterator = new InputStringIterator();
        private final int entry;

        @Children private final DFAStateNode[] states;

        public TRegexDFAExecutorNode(int entry, DFAStateNode[] states) {
            this.entry = entry;
            this.states = states;
        }

        @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
        protected boolean execute(byte[] inputString) {
            CompilerAsserts.compilationConstant(states.length);
            int ip = entry;
            int successor = -1;
            DFAStateNode curState = null;
            outer: while (true) {
                CompilerAsserts.partialEvaluationConstant(ip);
                CompilerAsserts.partialEvaluationConstant(states[ip]);
                curState = states[ip];
                if (curState.isFinalState()) {
                    break;
                }
                if (!inputStringIterator.hasNext(inputString)) {
                    break;
                }
                successor = curState.executeSuccessorIndex(inputStringIterator.next(inputString));
                int[] successors = curState.getSuccessors();
                for (int i = 0; i < successors.length; i++) {
                    if (i == successor) {
                        ip = successors[i];
                        continue outer;
                    }
                }
                CompilerDirectives.transferToInterpreter();
                throw new Error();
            }
            return curState.isFinalState();
        }
    }

    public class TRegexRootNode extends RootNode {

        private final TRegexDFAExecutorNode executorNode;

        public TRegexRootNode(FrameDescriptor frameDescriptor, TRegexDFAExecutorNode executorNode) {
            super(null, frameDescriptor);
            this.executorNode = executorNode;
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            final Object[] args = frame.getArguments();
            final String input = (String) args[0];
            return executorNode.execute(getBytes(input));
        }

        private byte[] getBytes(String input) {
            char[] chars = input.toCharArray();
            byte[] bytes = new byte[chars.length * 2];
            for (int i = 0; i < chars.length; i++) {
                bytes[i * 2] = (byte) (chars[i] >> Byte.SIZE);
                bytes[(i * 2) + 1] = (byte) chars[i];
            }
            return bytes;
        }
    }

    private RootNode createRootNode(int initialState, DFAStateNode[] states) {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        TRegexDFAExecutorNode executorNode = new TRegexDFAExecutorNode(initialState, states);
        return new TRegexRootNode(frameDescriptor, executorNode);
    }

    private static void assertMatches(RootNode program, String input) {
        Object result = Truffle.getRuntime().createCallTarget(program).call(input);
        Assert.assertEquals(Boolean.TRUE, result);
    }

    public static boolean constantTrue() {
        return true;
    }

    private void assertPartialEvalEqualsAndRunsCorrect(RootNode program, String input) {
        assertMatches(program, input);
        final OptimizedCallTarget compilable = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(program);
        partialEval(compilable, new Object[]{input}, INVALID_COMPILATION_ID);
        // fail on Exceptions only for now
    }

    private static DFAStateNode[] createStates(int n) {
        DFAStateNode[] states = new DFAStateNode[n];
        for (int i = 0; i < n; i++) {
            states[i] = new DFAStateNode();
        }
        return states;
    }

    @Test
    public void abORcd() {
        // DFA for /ab|cd/
        int initialState = 0;
        DFAStateNode[] states = createStates(7);
        states[0].setSuccessors(new int[]{3, 0});
        states[0].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        AnyByteMatcher.create()
        });
        states[1].setSuccessors(new int[]{4, 0});
        states[1].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        AnyByteMatcher.create()
        });
        states[2].setSuccessors(new int[]{3, 0});
        states[2].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        AnyByteMatcher.create()
        });
        states[2].setFinalState();
        states[3].setSuccessors(new int[]{3, 1, 5, 0});
        states[3].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        SingleByteMatcher.create(0x61),
                        SingleByteMatcher.create(0x63),
                        AnyByteMatcher.create()
        });
        states[4].setSuccessors(new int[]{3, 1, 2, 5, 0});
        states[4].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        SingleByteMatcher.create(0x61),
                        SingleByteMatcher.create(0x62),
                        SingleByteMatcher.create(0x63),
                        AnyByteMatcher.create()
        });
        states[5].setSuccessors(new int[]{6, 0});
        states[5].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        AnyByteMatcher.create()
        });
        states[6].setSuccessors(new int[]{3, 1, 5, 2, 0});
        states[6].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        SingleByteMatcher.create(0x61),
                        SingleByteMatcher.create(0x63),
                        SingleByteMatcher.create(0x64),
                        AnyByteMatcher.create()
        });

        assertPartialEvalEqualsAndRunsCorrect(createRootNode(initialState, states), "xxxxxxxxxxaxxxcxxxxabxxxxxxxxx");
    }

    @Test
    public void abSTARc() {
        int initialState = 0;
        DFAStateNode[] states = createStates(5);
        states[0].setSuccessors(new int[]{1, 0});
        states[0].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        AnyByteMatcher.create()
        });
        states[1].setSuccessors(new int[]{1, 2, 0});
        states[1].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        SingleByteMatcher.create(0x61),
                        AnyByteMatcher.create()
        });
        states[2].setSuccessors(new int[]{3, 0});
        states[2].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        AnyByteMatcher.create()
        });
        states[3].setSuccessors(new int[]{1, 4, 2, 0});
        states[3].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        SingleByteMatcher.create(0x63),
                        RangeByteMatcher.create(0x61, 0x62),
                        AnyByteMatcher.create()
        });
        states[4].setSuccessors(new int[]{1, 0});
        states[4].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        AnyByteMatcher.create()
        });
        states[4].setFinalState();

        assertPartialEvalEqualsAndRunsCorrect(createRootNode(initialState, states), "xxxxxxxxxxaxxxcxxxxabbbbcxxxxxxxxx");
    }

    @Test
    public void xabORxcd() {
        int initialState = 0;
        DFAStateNode[] states = createStates(9);
        states[0].setSuccessors(new int[]{3, 0});
        states[0].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        AnyByteMatcher.create()
        });
        states[1].setSuccessors(new int[]{4, 0});
        states[1].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        AnyByteMatcher.create()
        });
        states[2].setSuccessors(new int[]{3, 0});
        states[2].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        AnyByteMatcher.create()
        });
        states[2].setFinalState();
        states[3].setSuccessors(new int[]{3, 5, 0});
        states[3].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        SingleByteMatcher.create(0x78),
                        AnyByteMatcher.create()
        });
        states[4].setSuccessors(new int[]{3, 2, 5, 0});
        states[4].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        SingleByteMatcher.create(0x62),
                        SingleByteMatcher.create(0x78),
                        AnyByteMatcher.create()
        });
        states[5].setSuccessors(new int[]{6, 0});
        states[5].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        AnyByteMatcher.create()
        });
        states[6].setSuccessors(new int[]{3, 1, 7, 5, 0});
        states[6].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        SingleByteMatcher.create(0x61),
                        SingleByteMatcher.create(0x62),
                        SingleByteMatcher.create(0x78),
                        AnyByteMatcher.create()
        });
        states[7].setSuccessors(new int[]{8, 0});
        states[7].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        AnyByteMatcher.create()
        });
        states[8].setSuccessors(new int[]{3, 2, 5, 0});
        states[8].setMatchers(new ByteMatcher[]{
                        SingleByteMatcher.create(0x00),
                        SingleByteMatcher.create(0x63),
                        SingleByteMatcher.create(0x78),
                        AnyByteMatcher.create()
        });

        assertPartialEvalEqualsAndRunsCorrect(createRootNode(initialState, states), "xxxxxxxxxxaxxxcxxxxabbbbcxxxxxxxxx");
    }
}
