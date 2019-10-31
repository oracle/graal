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
package com.oracle.truffle.regex.tregex.nodes.nfa;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;

public final class TRegexBacktrackingNFAExecutorLocals extends TRegexExecutorLocals {

    private final int stackFrameSize;
    private int[] stack;
    private int sp;
    private final int[] result;
    private int lastResultSp = -1;
    private boolean forward = true;

    public TRegexBacktrackingNFAExecutorLocals(Object input, int fromIndex, int index, int maxIndex, int nCaptureGroups) {
        super(input, fromIndex, maxIndex, index);
        this.stackFrameSize = 2 + nCaptureGroups * 2;
        this.stack = new int[stackFrameSize * 8];
        this.sp = 0;
        this.result = new int[nCaptureGroups * 2];
        setIndex(fromIndex);
        Arrays.fill(stack, sp + 2, stackFrameSize, -1);
    }

    public void switchDirection() {
        forward = !forward;
    }

    public void apply(NFAStateTransition t) {
        t.getGroupBoundaries().apply(stack, sp + 2, getIndex());
    }

    public void push(NFAStateTransition t) {
        if (stack.length < sp + (stackFrameSize * 2)) {
            stack = Arrays.copyOf(stack, stack.length * 2);
        }
        System.arraycopy(stack, sp, stack, sp + stackFrameSize, stackFrameSize);
        t.getGroupBoundaries().apply(stack, sp + 2, getIndex());
        stack[sp]++;
        stack[sp + 1] = t.getTarget().getId();
        sp += stackFrameSize;
    }

    public void pushResult(NFAStateTransition t) {
        System.arraycopy(stack, sp + 2, result, 0, result.length);
        t.getGroupBoundaries().apply(result, 0, getIndex());
        lastResultSp = sp;
    }

    public boolean canPopResult() {
        return lastResultSp == sp;
    }

    public int[] popResult() {
        return result;
    }

    public boolean canPop() {
        return sp > 0;
    }

    public int pop() {
        assert sp > 0;
        sp -= stackFrameSize;
        return stack[sp + 1];
    }

    @Override
    public int getIndex() {
        return stack[sp];
    }

    @Override
    public void setIndex(int i) {
        stack[sp] = i;
    }

    @Override
    public void incIndex(int i) {
        stack[sp] += forward ? i : -i;
    }

    public int[] toResult() {
        return Arrays.copyOfRange(stack, sp + 2, sp + stackFrameSize);
    }

    @TruffleBoundary
    public void printStack(int curPc) {
        for (int i = sp; i >= 0; i -= stackFrameSize) {
            System.out.print(String.format("pc: %3d, i: %3d, cg: [", (i == sp ? curPc : stack[i + 1]), stack[i]));
            for (int j = i + 2; j < i + stackFrameSize - 1; j++) {
                System.out.print(String.format("%2d, ", stack[j]));
            }
            System.out.println(String.format("%2d]", stack[i + stackFrameSize - 1]));
        }
    }
}
