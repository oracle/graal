/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.strings.TruffleString;

public class DFASimpleCGTrackingStateNode extends DFAStateNode {

    private final DFASimpleCGTransition transitionToFinalState;

    public DFASimpleCGTrackingStateNode(short id, byte flags, short loopTransitionIndex, short indexOfNodeId, byte indexOfIsFast,
                    short[] successors,
                    Matchers matchers,
                    DFASimpleCGTransition transitionToFinalState,
                    short anchoredFinalSuccessor) {
        super(id, flags, loopTransitionIndex, indexOfNodeId, indexOfIsFast, successors, matchers, anchoredFinalSuccessor);
        this.transitionToFinalState = transitionToFinalState;
    }

    @Override
    void afterIndexOf(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, final int preLoopIndex, int postLoopIndex, TruffleString.CodeRange codeRange) {
        locals.setIndex(postLoopIndex);
        DFAAbstractNode loopSuccessor = executor.getNodes()[successors[getLoopToSelf()]];
        CompilerAsserts.partialEvaluationConstant(loopSuccessor);
        if (loopSuccessor instanceof DFASimpleCGTransition simpleCGTransition && locals.getIndex() > preLoopIndex) {
            int curIndex = locals.getIndex();
            executor.inputSkipReverse(locals, codeRange);
            simpleCGTransition.apply(locals, executor);
            locals.setIndex(curIndex);
        }
        checkFinalState(locals, executor);
    }

    @Override
    boolean checkFinalState(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (isFinalState() && !(isAnchoredFinalState() && executor.inputAtEnd(locals))) {
            storeResult(locals, executor, false);
            applySimpleCGFinalTransition(executor, locals);
        }
        return false;
    }

    @Override
    void atEnd(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean inputAtEnd) {
        CompilerAsserts.partialEvaluationConstant(this);
        boolean anchored = isAnchoredFinalState() && inputAtEnd;
        if (isFinalState() || anchored) {
            storeResult(locals, executor, anchored);
            if (!isAnchoredFinalState() && isFinalState()) {
                applySimpleCGFinalTransition(executor, locals);
            }
        }
    }

    @Override
    void storeResult(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, @SuppressWarnings("unused") boolean anchored) {
        CompilerAsserts.partialEvaluationConstant(this);
        if (executor.getProperties().isSimpleCGMustCopy()) {
            System.arraycopy(locals.getCGData().results, 0, locals.getCGData().currentResult, 0, locals.getCGData().currentResult.length);
        }
        locals.setResultInt(0);
    }

    private void applySimpleCGFinalTransition(TRegexDFAExecutorNode executor, TRegexDFAExecutorLocals locals) {
        if (transitionToFinalState != null) {
            transitionToFinalState.apply(locals, executor);
        }
    }
}
