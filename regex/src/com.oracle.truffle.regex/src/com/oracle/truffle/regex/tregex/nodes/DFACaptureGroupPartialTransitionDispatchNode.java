/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

import static com.oracle.truffle.api.CompilerDirectives.*;

public final class DFACaptureGroupPartialTransitionDispatchNode extends Node {

    private static final int EXPLODE_THRESHOLD = 20;

    public static DFACaptureGroupPartialTransitionDispatchNode create(short[] precedingTransitions) {
        return new DFACaptureGroupPartialTransitionDispatchNode(precedingTransitions);
    }

    @CompilationFinal(dimensions = 1) private final short[] precedingTransitions;

    private DFACaptureGroupPartialTransitionDispatchNode(short[] precedingTransitions) {
        this.precedingTransitions = precedingTransitions;
    }

    public void applyPartialTransition(VirtualFrame frame, TRegexDFAExecutorNode executor, short transitionIndex, int partialTransitionIndex, int currentIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (precedingTransitions.length > EXPLODE_THRESHOLD) {
            applyPartialTransitionBoundary(executor, executor.getCGData(frame), transitionIndex, partialTransitionIndex, currentIndex);
        } else {
            applyPartialTransitionExploded(frame, executor, transitionIndex, partialTransitionIndex, currentIndex);
        }
    }

    @TruffleBoundary
    private static void applyPartialTransitionBoundary(TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, short transitionIndex, int partialTransitionIndex, int currentIndex) {
        executor.getCGTransitions()[transitionIndex].getPartialTransitions()[partialTransitionIndex].apply(executor, d, currentIndex);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private void applyPartialTransitionExploded(VirtualFrame frame, TRegexDFAExecutorNode executor, short transitionIndex, int partialTransitionIndex, int currentIndex) {
        for (short possibleTransition : precedingTransitions) {
            if (transitionIndex == possibleTransition) {
                final DFACaptureGroupPartialTransitionNode[] partialTransitions = executor.getCGTransitions()[possibleTransition].getPartialTransitions();
                for (int i = 0; i < partialTransitions.length; i++) {
                    CompilerAsserts.partialEvaluationConstant(i);
                    if (i == partialTransitionIndex) {
                        partialTransitions[i].apply(executor, executor.getCGData(frame), currentIndex);
                        return;
                    }
                }
                throw new IllegalStateException();
            }
        }
        throw new IllegalStateException();
    }

    public void applyPreAnchoredFinalTransition(VirtualFrame frame, TRegexDFAExecutorNode executor, short transitionIndex, int currentIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (precedingTransitions.length > EXPLODE_THRESHOLD) {
            applyPreAnchoredFinalTransitionBoundary(executor, executor.getCGData(frame), transitionIndex, currentIndex);
        } else {
            applyPreAnchoredFinalTransitionExploded(frame, executor, transitionIndex, currentIndex);
        }
    }

    @TruffleBoundary
    private static void applyPreAnchoredFinalTransitionBoundary(TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, short transitionIndex, int currentIndex) {
        executor.getCGTransitions()[transitionIndex].getTransitionToAnchoredFinalState().applyPreFinalStateTransition(executor, d, executor.isSearching(), currentIndex);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private void applyPreAnchoredFinalTransitionExploded(VirtualFrame frame, TRegexDFAExecutorNode executor, short transitionIndex, int currentIndex) {
        for (short possibleTransition : precedingTransitions) {
            if (transitionIndex == possibleTransition) {
                executor.getCGTransitions()[possibleTransition].getTransitionToAnchoredFinalState().applyPreFinalStateTransition(
                                executor, executor.getCGData(frame), executor.isSearching(), currentIndex);
                return;
            }
        }
        throw new IllegalStateException();
    }

    public void applyPreFinalTransition(VirtualFrame frame, TRegexDFAExecutorNode executor, short transitionIndex, int currentIndex) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (precedingTransitions.length > EXPLODE_THRESHOLD) {
            applyPreFinalTransitionBoundary(executor, executor.getCGData(frame), transitionIndex, currentIndex);
        } else {
            applyPreFinalTransitionExploded(frame, executor, executor, transitionIndex, currentIndex);
        }
    }

    @TruffleBoundary
    private static void applyPreFinalTransitionBoundary(TRegexDFAExecutorNode executor, DFACaptureGroupTrackingData d, short transitionIndex, int currentIndex) {
        executor.getCGTransitions()[transitionIndex].getTransitionToFinalState().applyPreFinalStateTransition(executor, d, executor.isSearching(), currentIndex);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    private void applyPreFinalTransitionExploded(VirtualFrame frame, TRegexDFAExecutorNode executor, TRegexDFAExecutorNode executorNode, short transitionIndex, int currentIndex) {
        for (short possibleTransition : precedingTransitions) {
            if (transitionIndex == possibleTransition) {
                executorNode.getCGTransitions()[possibleTransition].getTransitionToFinalState().applyPreFinalStateTransition(
                                executor, executorNode.getCGData(frame), executorNode.isSearching(), currentIndex);
                return;
            }
        }
        throw new IllegalStateException();
    }
}
