/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
