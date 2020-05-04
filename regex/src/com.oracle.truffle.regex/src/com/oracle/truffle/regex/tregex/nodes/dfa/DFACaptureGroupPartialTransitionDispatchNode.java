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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.tregex.util.Exceptions;

public final class DFACaptureGroupPartialTransitionDispatchNode extends Node {

    private static final int EXPLODE_THRESHOLD = 20;

    public static DFACaptureGroupPartialTransitionDispatchNode create(short[] precedingTransitions) {
        return new DFACaptureGroupPartialTransitionDispatchNode(precedingTransitions);
    }

    @CompilationFinal(dimensions = 1) private final short[] precedingTransitions;

    private DFACaptureGroupPartialTransitionDispatchNode(short[] precedingTransitions) {
        this.precedingTransitions = precedingTransitions;
    }

    public void applyPartialTransition(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, short transitionIndex, int partialTransitionIndex, int currentIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (precedingTransitions.length > EXPLODE_THRESHOLD) {
            applyPartialTransitionBoundary(executor, locals.getCGData(), transitionIndex, partialTransitionIndex, currentIndex);
        } else {
            applyPartialTransitionExploded(locals, executor, transitionIndex, partialTransitionIndex, currentIndex);
        }
    }

    @TruffleBoundary
    private static void applyPartialTransitionBoundary(TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, short transitionIndex, int partialTransitionIndex, int currentIndex) {
        executor.getCGTransitions()[transitionIndex].getPartialTransitions()[partialTransitionIndex].apply(executor, d, currentIndex);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private void applyPartialTransitionExploded(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, short transitionIndex, int partialTransitionIndex, int currentIndex) {
        for (short possibleTransition : precedingTransitions) {
            if (transitionIndex == possibleTransition) {
                final DFACaptureGroupPartialTransition[] partialTransitions = executor.getCGTransitions()[possibleTransition].getPartialTransitions();
                for (int i = 0; i < partialTransitions.length; i++) {
                    CompilerAsserts.partialEvaluationConstant(i);
                    if (i == partialTransitionIndex) {
                        partialTransitions[i].apply(executor, locals.getCGData(), currentIndex);
                        return;
                    }
                }
                throw Exceptions.shouldNotReachHere();
            }
        }
        throw Exceptions.shouldNotReachHere();
    }

    public void applyPreAnchoredFinalTransition(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, short transitionIndex, int currentIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (precedingTransitions.length > EXPLODE_THRESHOLD) {
            applyPreAnchoredFinalTransitionBoundary(executor, locals.getCGData(), transitionIndex, currentIndex);
        } else {
            applyPreAnchoredFinalTransitionExploded(locals, executor, transitionIndex, currentIndex);
        }
    }

    @TruffleBoundary
    private static void applyPreAnchoredFinalTransitionBoundary(TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, short transitionIndex, int currentIndex) {
        executor.getCGTransitions()[transitionIndex].getTransitionToAnchoredFinalState().applyPreFinalStateTransition(executor, d, currentIndex);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private void applyPreAnchoredFinalTransitionExploded(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, short transitionIndex, int currentIndex) {
        for (short possibleTransition : precedingTransitions) {
            if (transitionIndex == possibleTransition) {
                executor.getCGTransitions()[possibleTransition].getTransitionToAnchoredFinalState().applyPreFinalStateTransition(executor, locals.getCGData(), currentIndex);
                return;
            }
        }
        throw Exceptions.shouldNotReachHere();
    }

    public void applyPreFinalTransition(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, short transitionIndex, int currentIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (precedingTransitions.length > EXPLODE_THRESHOLD) {
            applyPreFinalTransitionBoundary(executor, locals.getCGData(), transitionIndex, currentIndex);
        } else {
            applyPreFinalTransitionExploded(locals, executor, executor, transitionIndex, currentIndex);
        }
    }

    @TruffleBoundary
    private static void applyPreFinalTransitionBoundary(TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, short transitionIndex, int currentIndex) {
        executor.getCGTransitions()[transitionIndex].getTransitionToFinalState().applyPreFinalStateTransition(executor, d, currentIndex);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private void applyPreFinalTransitionExploded(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, TRegexDFAExecutorNode executorNode, short transitionIndex, int currentIndex) {
        for (short possibleTransition : precedingTransitions) {
            if (transitionIndex == possibleTransition) {
                executorNode.getCGTransitions()[possibleTransition].getTransitionToFinalState().applyPreFinalStateTransition(executor, locals.getCGData(), currentIndex);
                return;
            }
        }
        throw Exceptions.shouldNotReachHere();
    }
}
