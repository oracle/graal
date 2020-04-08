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
package com.oracle.truffle.regex.tregex.nodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node;

public abstract class TRegexExecutorNode extends Node {

    @CompilationFinal protected TRegexExecRootNode root;

    public void setRoot(TRegexExecRootNode root) {
        this.root = root;
    }

    /**
     * The length of the {@code input} argument given to
     * {@link TRegexExecRootNode#execute(Object, int)}.
     *
     * @return the length of the {@code input} argument given to
     *         {@link TRegexExecRootNode#execute(Object, int)}.
     */
    public int getInputLength(TRegexExecutorLocals locals) {
        assert root != null;
        return root.inputLength(locals.getInput());
    }

    /**
     * Returns {@code true} iff the index is at the beginning of the input string in respect to
     * {@link #isForward()}.
     */
    public boolean inputAtBegin(TRegexExecutorLocals locals) {
        return locals.getIndex() == (isForward() ? 0 : getInputLength(locals));
    }

    /**
     * Returns {@code true} iff the index is at the end of the input string in respect to
     * {@link #isForward()}.
     */
    public boolean inputAtEnd(TRegexExecutorLocals locals) {
        return locals.getIndex() == (isForward() ? getInputLength(locals) : 0);
    }

    public int getMinIndex(@SuppressWarnings("unused") TRegexExecutorLocals locals) {
        return 0;
    }

    public int getMaxIndex(TRegexExecutorLocals locals) {
        return locals.getMaxIndex();
    }

    public boolean inputHasNext(TRegexExecutorLocals locals) {
        return isForward() ? locals.getIndex() < getMaxIndex(locals) : locals.getIndex() > getMinIndex(locals);
    }

    public int inputRead(TRegexExecutorLocals locals) {
        return inputRead(locals, locals.getIndex());
    }

    public int inputRead(TRegexExecutorLocals locals, int index) {
        assert root != null;
        locals.setNextIndex(isForward() ? index + 1 : index - 1);
        return root.inputRead(locals.getInput(), isForward() ? index : index - 1);
    }

    public void inputAdvance(TRegexExecutorLocals locals) {
        assert isForward() ? locals.getIndex() < locals.getNextIndex() : locals.getIndex() > locals.getNextIndex();
        locals.setIndex(locals.getNextIndex());
    }

    public void inputSkip(TRegexExecutorLocals locals) {
        locals.setIndex(isForward() ? locals.getIndex() + 1 : locals.getIndex() - 1);
    }

    public void inputSkipReverse(TRegexExecutorLocals locals) {
        locals.setIndex(isForward() ? locals.getIndex() - 1 : locals.getIndex() + 1);
    }

    public void inputIncRaw(TRegexExecutorLocals locals) {
        inputIncRaw(locals, 1);
    }

    public void inputIncRaw(TRegexExecutorLocals locals, int offset) {
        assert offset > 0;
        locals.setIndex(isForward() ? locals.getIndex() + offset : locals.getIndex() - offset);
    }

    public int countUpTo(TRegexExecutorLocals locals, int max, int nCodePoints) {
        CompilerAsserts.partialEvaluationConstant(nCodePoints);
        if (nCodePoints > 0) {
            assert isForward();
            int i = 0;
            int index = locals.getIndex();
            while (index < max && i < nCodePoints) {
                // TODO: decode here
                index++;
                i++;
            }
            return i;
        }
        return 0;
    }

    public int rewindUpTo(TRegexExecutorLocals locals, int min, int nCodePoints) {
        CompilerAsserts.partialEvaluationConstant(nCodePoints);
        if (nCodePoints > 0) {
            assert isForward();
            int i = 0;
            while (locals.getIndex() > min && i < nCodePoints) {
                // TODO: decode here
                locals.setIndex(locals.getIndex() - 1);
                i++;
            }
            return i;
        }
        return 0;
    }

    protected int getNumberOfCaptureGroups() {
        assert root != null;
        return root.getNumberOfCaptureGroups();
    }

    public abstract boolean isForward();

    /**
     * Returns {@code true} if this executor may write any new capture group boundaries.
     */
    public abstract boolean writesCaptureGroups();

    public abstract TRegexExecutorLocals createLocals(Object input, int fromIndex, int index, int maxIndex);

    public abstract Object execute(TRegexExecutorLocals locals, boolean compactString);

}
