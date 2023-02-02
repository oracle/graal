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
package com.oracle.truffle.regex.tregex.nodes.dfa;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public abstract class DFACaptureGroupLazyTransition {

    public final void apply(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        apply(locals, executor, false);
    }

    public final void applyPreFinal(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        apply(locals, executor, true);
    }

    protected abstract void apply(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean preFinal);

    public static final class Single extends DFACaptureGroupLazyTransition {

        private static final Single EMPTY = new Single(DFACaptureGroupPartialTransition.getEmptyInstance());

        private final DFACaptureGroupPartialTransition transition;

        public Single(DFACaptureGroupPartialTransition transition) {
            this.transition = transition;
        }

        public static Single create(DFACaptureGroupPartialTransition transition) {
            return transition.isEmpty() ? EMPTY : new Single(transition);
        }

        @Override
        protected void apply(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean preFinal) {
            transition.apply(executor, locals.getCGData(), locals.getLastIndex(), preFinal, true);
        }
    }

    abstract static class Branches extends DFACaptureGroupLazyTransition {

        final DFACaptureGroupPartialTransition common;
        @CompilationFinal(dimensions = 1) final DFACaptureGroupPartialTransition[] transitions;

        protected Branches(DFACaptureGroupPartialTransition[] transitions) {
            this.common = DFACaptureGroupPartialTransition.intersect(transitions);
            this.transitions = common.isEmpty() ? transitions : subtract(common, transitions);
            assert transitions.length > 1;
        }

        private static DFACaptureGroupPartialTransition[] subtract(DFACaptureGroupPartialTransition common, DFACaptureGroupPartialTransition[] transitions) {
            for (int i = 0; i < transitions.length; i++) {
                transitions[i] = transitions[i].subtract(common);
            }
            return transitions;
        }
    }

    public static final class BranchesDirect extends Branches {

        public BranchesDirect(DFACaptureGroupPartialTransition[] transitions) {
            super(transitions);
        }

        public static BranchesDirect create(DFACaptureGroupPartialTransition[] transitions) {
            return new BranchesDirect(transitions);
        }

        @ExplodeLoop
        @Override
        protected void apply(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean preFinal) {
            int lastTransition = locals.getLastTransition();
            DFACaptureGroupTrackingData d = locals.getCGData();
            int lastIndex = locals.getLastIndex();
            common.apply(executor, d, lastIndex, preFinal, true);
            for (int i = 0; i < transitions.length; i++) {
                // i == transitions.length - 1 transforms the last exploded iteration into an
                // else-branch
                if (i == transitions.length - 1 || i == lastTransition) {
                    assert i == lastTransition;
                    transitions[i].apply(executor, d, lastIndex, preFinal, common.isEmpty());
                    return;
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public static final class BranchesIndirect extends Branches {

        @CompilationFinal(dimensions = 1) private final short[] possibleValues;

        public BranchesIndirect(DFACaptureGroupPartialTransition[] transitions, short[] possibleValues) {
            super(transitions);
            this.possibleValues = possibleValues;
            assert possibleValues.length == transitions.length - 1;
        }

        public static BranchesIndirect create(DFACaptureGroupPartialTransition[] transitions, short[] possibleValues) {
            return new BranchesIndirect(transitions, possibleValues);
        }

        @ExplodeLoop
        @Override
        protected void apply(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean preFinal) {
            int lastTransition = locals.getLastTransition();
            DFACaptureGroupTrackingData d = locals.getCGData();
            int lastIndex = locals.getLastIndex();
            common.apply(executor, d, lastIndex, preFinal, true);
            for (int i = 0; i < transitions.length; i++) {
                // i == transitions.length - 1 transforms the last exploded iteration into an
                // else-branch
                if (i == transitions.length - 1 || possibleValues[i] == lastTransition) {
                    transitions[i].apply(executor, d, lastIndex, preFinal, common.isEmpty());
                    return;
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public static final class BranchesWithLookupTable extends Branches {

        @CompilationFinal(dimensions = 1) private final byte[] lookupTable;

        public BranchesWithLookupTable(DFACaptureGroupPartialTransition[] transitions, byte[] lookupTable) {
            super(transitions);
            this.lookupTable = lookupTable;
        }

        public static BranchesWithLookupTable create(DFACaptureGroupPartialTransition[] transitions, byte[] lookupTable) {
            return new BranchesWithLookupTable(transitions, lookupTable);
        }

        @ExplodeLoop
        @Override
        protected void apply(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean preFinal) {
            int lastTransitionMapped = Byte.toUnsignedInt(lookupTable[locals.getLastTransition()]);
            DFACaptureGroupTrackingData d = locals.getCGData();
            int lastIndex = locals.getLastIndex();
            common.apply(executor, d, lastIndex, preFinal, true);
            for (int i = 0; i < transitions.length; i++) {
                // i == transitions.length - 1 transforms the last exploded iteration into an
                // else-branch
                if (i == transitions.length - 1 || i == lastTransitionMapped) {
                    transitions[i].apply(executor, d, lastIndex, preFinal, common.isEmpty());
                    return;
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }
}
