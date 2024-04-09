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
package com.oracle.truffle.regex.tregex.nodes.nfa;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.tregex.nfa.PureNFATransition;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.util.BitSets;

/**
 * Contains the stack used by {@link TRegexBacktrackingNFAExecutorNode}. One stack frame represents
 * a snapshot of the backtracker's state. The backtracker state consists of:
 * <ul>
 * <li>the current index in the input string</li>
 * <li>the current NFA state</li>
 * <li>all current capture group boundaries (including the number of the last matched group, if
 * tracked)</li>
 * <li>all current quantifier loop counters</li>
 * <li>all saved indices for zero-width checks in quantifiers</li>
 * <li>all saved capture groups for zero-width checks in quantifiers</li>
 * <li>all saved capture groups for recursive back-references (if tracked)</li>
 * </ul>
 * The backtracker state is written to the stack in the order given above, so one stack frame looks
 * like this:
 *
 * <pre>
 * sp    sp+1      sp+2           sp+2+ncg           sp+2+ncg+nq                   sp+2+ncg+nq+nzwq         sp+2+ncg+nq+nzwq+nzwqcg
 * |     |         |              |                  |                             |                        |
 * v     v         v              v                  v                             v                        v
 * -----------------------------------------------------------------------------------------------------------------------------------
 * |index|nfa_state|capture_groups|quantifiers_counts|zero_width_quantifier_indices|zero_width_quantifier_CG|recursive_capture_groups|
 * -----------------------------------------------------------------------------------------------------------------------------------
 *
 * frame size: 2 + n_capture_groups*2 [+ 1 last_group] + n_quantifiers + n_zero_width_quantifiers + zero_width_quantifier_CG_length + n_recursive_capture_groups
 * </pre>
 */
public final class TRegexBacktrackingNFAExecutorLocals extends TRegexExecutorLocals {

    private final int stackFrameSize;
    private final int nQuantifierCounts;
    private final int nZeroWidthQuantifiers;
    private final int[] zeroWidthTermEnclosedCGLow;
    private final int[] zeroWidthQuantifierCGOffsets;
    private final int[] stackFrameBuffer;
    private final int stackBase;
    private final Stack stack;
    private int sp;
    private final int[] result;
    private final long[] transitionBitSet;
    private final boolean trackLastGroup;
    private final boolean dontOverwriteLastGroup;
    private final boolean recursiveBackReferences;
    private int lastResultSp = -1;
    private int lastResultIndex = -1;
    private int lastInnerLiteralIndex;
    private int lastInitialStateIndex;

    private TRegexBacktrackingNFAExecutorLocals(TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index,
                    int nCaptureGroups,
                    int nQuantifiers,
                    int nZeroWidthQuantifiers,
                    int[] zeroWidthTermEnclosedCGLow,
                    int[] zeroWidthQuantifierCGOffsets,
                    int[] stackFrameBuffer,
                    Stack stack,
                    int stackBase,
                    int stackFrameSize,
                    long[] transitionBitSet,
                    boolean trackLastGroup,
                    boolean dontOverwriteLastGroup,
                    boolean recursiveBackReferences) {
        super(input, fromIndex, maxIndex, regionFrom, regionTo, index);
        this.stackFrameSize = stackFrameSize;
        this.nQuantifierCounts = nQuantifiers;
        this.nZeroWidthQuantifiers = nZeroWidthQuantifiers;
        this.zeroWidthTermEnclosedCGLow = zeroWidthTermEnclosedCGLow;
        this.zeroWidthQuantifierCGOffsets = zeroWidthQuantifierCGOffsets;
        this.stackFrameBuffer = stackFrameBuffer;
        this.stack = stack;
        this.stackBase = stackBase;
        this.sp = stackBase;
        this.result = new int[nCaptureGroups * 2 + (trackLastGroup ? 1 : 0)];
        this.transitionBitSet = transitionBitSet;
        this.trackLastGroup = trackLastGroup;
        this.dontOverwriteLastGroup = dontOverwriteLastGroup;
        this.recursiveBackReferences = recursiveBackReferences;
    }

    public static TRegexBacktrackingNFAExecutorLocals create(
                    TruffleString input,
                    int fromIndex,
                    int maxIndex,
                    int regionFrom,
                    int regionTo,
                    int index,
                    int nCaptureGroups,
                    int nQuantifiers,
                    int nZeroWidthQuantifiers,
                    int[] zeroWidthTermEnclosedCGLow,
                    int[] zeroWidthQuantifierCGOffsets,
                    boolean allocateStackFrameBuffer,
                    int maxNTransitions,
                    boolean trackLastGroup,
                    boolean dontOverwriteLastGroup,
                    boolean recursiveBackrefs) {
        int stackFrameSize = getStackFrameSize(nCaptureGroups, nQuantifiers, nZeroWidthQuantifiers, zeroWidthQuantifierCGOffsets, trackLastGroup, recursiveBackrefs);
        TRegexBacktrackingNFAExecutorLocals ret = new TRegexBacktrackingNFAExecutorLocals(
                        input,
                        fromIndex,
                        maxIndex,
                        regionFrom,
                        regionTo,
                        index,
                        nCaptureGroups,
                        nQuantifiers,
                        nZeroWidthQuantifiers,
                        zeroWidthTermEnclosedCGLow,
                        zeroWidthQuantifierCGOffsets,
                        allocateStackFrameBuffer ? new int[stackFrameSize] : null,
                        new Stack(new int[stackFrameSize * 4]),
                        0,
                        stackFrameSize,
                        BitSets.createBitSetArray(maxNTransitions),
                        trackLastGroup,
                        dontOverwriteLastGroup,
                        recursiveBackrefs);
        ret.setIndex(fromIndex);
        ret.clearCaptureGroups();
        if (recursiveBackrefs) {
            ret.clearRecursiveCaptureGroups();
        }
        return ret;
    }

    private int[] stack() {
        return stack.stack;
    }

    private static int getStackFrameSize(int nCaptureGroups, int nQuantifiers, int nZeroWidthQuantifiers, int[] zeroWidthQuantifierCGOffsets, boolean trackLastGroup, boolean recursiveBackrefs) {
        return 2 + nCaptureGroups * (recursiveBackrefs ? 3 : 2) + (trackLastGroup ? 1 : 0) + nQuantifiers + nZeroWidthQuantifiers +
                        zeroWidthQuantifierCGOffsets[zeroWidthQuantifierCGOffsets.length - 1];
    }

    public TRegexBacktrackingNFAExecutorLocals createSubNFALocals(boolean newDontOverwriteLastGroup) {
        dupFrame();
        // The state of lastGroup must be reset to -1 for the submatcher so that we can detect when
        // the submatcher is writing the lastGroup for the first time.
        if (trackLastGroup && newDontOverwriteLastGroup) {
            stack()[offsetLastGroup() + stackFrameSize] = -1;
        }
        return newSubLocals(newDontOverwriteLastGroup);
    }

    public TRegexBacktrackingNFAExecutorLocals createSubNFALocals(PureNFATransition t, boolean newDontOverwriteLastGroup) {
        dupFrame();
        if (trackLastGroup && newDontOverwriteLastGroup) {
            stack()[offsetLastGroup() + stackFrameSize] = -1;
        }
        t.getGroupBoundaries().applyExploded(stack(), offsetCaptureGroups() + stackFrameSize, offsetLastGroup() + stackFrameSize, getIndex(), trackLastGroup, dontOverwriteLastGroup);
        return newSubLocals(newDontOverwriteLastGroup);
    }

    private TRegexBacktrackingNFAExecutorLocals newSubLocals(boolean newDontOverwriteLastGroup) {
        return new TRegexBacktrackingNFAExecutorLocals(getInput(), getFromIndex(), getMaxIndex(), getRegionFrom(), getRegionTo(), getIndex(),
                        result.length / 2,
                        nQuantifierCounts,
                        nZeroWidthQuantifiers,
                        zeroWidthTermEnclosedCGLow,
                        zeroWidthQuantifierCGOffsets, stackFrameBuffer, stack, sp + stackFrameSize, stackFrameSize,
                        transitionBitSet, trackLastGroup, newDontOverwriteLastGroup, recursiveBackReferences);
    }

    private int offsetIP() {
        return sp + 1;
    }

    private int offsetCaptureGroups() {
        return offsetCaptureGroups(sp);
    }

    private static int offsetCaptureGroups(int framePointer) {
        return framePointer + 2;
    }

    private int offsetLastGroup() {
        return trackLastGroup ? sp + 2 + result.length - 1 : -1;
    }

    private int offsetQuantifierCounts() {
        return sp + 2 + result.length;
    }

    private int offsetZeroWidthQuantifierIndices() {
        return sp + 2 + result.length + nQuantifierCounts;
    }

    private int offsetZeroWidthQuantifierCG() {
        return sp + 2 + result.length + nQuantifierCounts + nZeroWidthQuantifiers;
    }

    private int offsetRecursiveBackReferences() {
        return sp + 2 + result.length + nQuantifierCounts + nZeroWidthQuantifiers + zeroWidthQuantifierCGOffsets[zeroWidthQuantifierCGOffsets.length - 1];
    }

    private int offsetQuantifierCount(int quantifierIndex) {
        CompilerAsserts.partialEvaluationConstant(quantifierIndex);
        return offsetQuantifierCounts() + quantifierIndex;
    }

    private int offsetZeroWidthQuantifierIndex(int quantifierZeroWidthIndex) {
        CompilerAsserts.partialEvaluationConstant(quantifierZeroWidthIndex);
        return offsetZeroWidthQuantifierIndices() + quantifierZeroWidthIndex;
    }

    private int offsetZeroWidthQuantifierCG(int zeroWidthIndex) {
        CompilerAsserts.partialEvaluationConstant(zeroWidthIndex);
        return offsetZeroWidthQuantifierCG() + zeroWidthQuantifierCGOffsets[zeroWidthIndex];
    }

    public void apply(PureNFATransition t, int index) {
        t.getGroupBoundaries().applyExploded(stack(), offsetCaptureGroups(), offsetLastGroup(), index, trackLastGroup, dontOverwriteLastGroup);
    }

    public void resetToInitialState() {
        clearCaptureGroups();
        clearQuantifierCounts();
        if (recursiveBackReferences) {
            clearRecursiveCaptureGroups();
        }
        // no need to reset zero-width quantifier indices, they will always be overwritten before
        // being checked
    }

    protected void clearCaptureGroups() {
        Arrays.fill(stack(), offsetCaptureGroups(), offsetCaptureGroups() + result.length, -1);
    }

    protected void clearRecursiveCaptureGroups() {
        Arrays.fill(stack(), offsetRecursiveBackReferences(), offsetRecursiveBackReferences() + result.length, -1);
    }

    protected void clearQuantifierCounts() {
        Arrays.fill(stack(), offsetQuantifierCounts(), offsetQuantifierCounts() + nQuantifierCounts, 0);
    }

    public void push() {
        sp += stackFrameSize;
    }

    public void pushFrame(int[] frame) {
        ensureSize(sp + 2 * stackFrameSize);
        push();
        writeFrame(frame);
    }

    public void readFrame(int[] to) {
        assert to == stackFrameBuffer || to.length >= stackFrameSize;
        System.arraycopy(stack(), sp, to, 0, stackFrameSize);
    }

    public void writeFrame(int[] from) {
        System.arraycopy(from, 0, stack(), sp, stackFrameSize);
    }

    public void dupFrame() {
        dupFrame(1);
    }

    public void dupFrame(int n) {
        int minSize = sp + (stackFrameSize * (n + 1));
        ensureSize(minSize);
        int targetFrame = sp;
        for (int i = 0; i < n; i++) {
            targetFrame += stackFrameSize;
            System.arraycopy(stack(), sp, stack(), targetFrame, stackFrameSize);
        }
    }

    private void ensureSize(int minSize) {
        if (stack().length < minSize) {
            int newLength = stack().length << 1;
            while (newLength < minSize) {
                newLength <<= 1;
            }
            stack.stack = Arrays.copyOf(stack(), newLength);
        }
    }

    public void pushResult(PureNFATransition t, int index) {
        t.getGroupBoundaries().applyExploded(result, 0, result.length - 1, index, trackLastGroup, dontOverwriteLastGroup);
        pushResult();
    }

    /**
     * Marks that a result was pushed at the current stack frame.
     */
    public void pushResult() {
        lastResultSp = sp;
        lastResultIndex = getIndex();
    }

    /**
     * Copies the current capture group boundaries to the result array.
     */
    public void setResult() {
        System.arraycopy(stack(), offsetCaptureGroups(), result, 0, result.length);
    }

    public boolean canPopResult() {
        return lastResultSp == sp;
    }

    public int[] popResult() {
        if (lastResultSp < 0) {
            return null;
        }
        // Restore the index to the locals to reflect the state of the successful match.
        // This index will be reused when used inside a submatcher for an atomic group.
        setIndex(lastResultIndex);
        return result;
    }

    public boolean canPop() {
        return sp > stackBase;
    }

    public int pop() {
        assert sp > stackBase;
        sp -= stackFrameSize;
        restoreIndex();
        return stack()[offsetIP()];
    }

    public void saveIndex(int index) {
        stack()[sp] = index;
    }

    public void restoreIndex() {
        setIndex(stack()[sp]);
    }

    public int getPc() {
        return stack()[offsetIP()];
    }

    public int setPc(int pc) {
        return stack()[offsetIP()] = pc;
    }

    public int getCaptureGroupBoundary(int boundary) {
        return stack()[offsetCaptureGroups() + boundary];
    }

    public void setCaptureGroupBoundary(int boundary, int index) {
        stack()[offsetCaptureGroups() + boundary] = index;
    }

    public int getCaptureGroupStart(int groupNumber) {
        return getCaptureGroupBoundary(Group.groupNumberToBoundaryIndexStart(groupNumber));
    }

    public int getCaptureGroupEnd(int groupNumber) {
        return getCaptureGroupBoundary(Group.groupNumberToBoundaryIndexEnd(groupNumber));
    }

    public int getRecursiveCaptureGroupStart(int groupNumber) {
        return stack()[offsetRecursiveBackReferences() + groupNumber];
    }

    public void saveRecursiveBackrefGroupStart(int groupNumber) {
        stack()[offsetRecursiveBackReferences() + groupNumber] = getCaptureGroupStart(groupNumber);
    }

    public void overwriteCaptureGroups(int[] captureGroups) {
        assert captureGroups.length == result.length;
        if (trackLastGroup) {
            System.arraycopy(captureGroups, 0, stack(), offsetCaptureGroups(), captureGroups.length - 1);
            setLastGroup(captureGroups[captureGroups.length - 1]);
        } else {
            System.arraycopy(captureGroups, 0, stack(), offsetCaptureGroups(), captureGroups.length);
        }
    }

    public void setLastGroup(int newLastGroup) {
        if (trackLastGroup && newLastGroup != -1 && (!dontOverwriteLastGroup || stack()[offsetLastGroup()] == -1)) {
            stack()[offsetLastGroup()] = newLastGroup;
        }
    }

    public int getQuantifierCount(int quantifierIndex) {
        return stack()[offsetQuantifierCount(quantifierIndex)];
    }

    public void setQuantifierCount(int quantifierIndex, int count) {
        stack()[offsetQuantifierCount(quantifierIndex)] = count;
    }

    public void resetQuantifierCount(int quantifierIndex) {
        stack()[offsetQuantifierCount(quantifierIndex)] = 0;
    }

    public void incQuantifierCount(int quantifierIndex) {
        stack()[offsetQuantifierCount(quantifierIndex)]++;
    }

    public int getZeroWidthQuantifierGuardIndex(int quantifierZeroWidthIndex) {
        return stack()[offsetZeroWidthQuantifierIndex(quantifierZeroWidthIndex)];
    }

    public void setZeroWidthQuantifierGuardIndex(int quantifierZeroWidthIndex) {
        stack()[offsetZeroWidthQuantifierIndex(quantifierZeroWidthIndex)] = getIndex();
    }

    public boolean isResultUnmodifiedByZeroWidthQuantifier(int quantifierZeroWidthIndex) {
        int start = offsetCaptureGroups() + 2 * zeroWidthTermEnclosedCGLow[quantifierZeroWidthIndex];
        int length = zeroWidthQuantifierCGOffsets[quantifierZeroWidthIndex + 1] - zeroWidthQuantifierCGOffsets[quantifierZeroWidthIndex];
        for (int i = 0; i < length; i++) {
            if (stack()[offsetZeroWidthQuantifierCG(quantifierZeroWidthIndex) + i] != stack()[start + i]) {
                return false;
            }
        }
        return true;
    }

    public void setZeroWidthQuantifierResults(int quantifierZeroWidthIndex) {
        int start = offsetCaptureGroups() + 2 * zeroWidthTermEnclosedCGLow[quantifierZeroWidthIndex];
        int length = zeroWidthQuantifierCGOffsets[quantifierZeroWidthIndex + 1] - zeroWidthQuantifierCGOffsets[quantifierZeroWidthIndex];
        System.arraycopy(stack(), start, stack(), offsetZeroWidthQuantifierCG(quantifierZeroWidthIndex), length);
    }

    public long[] getTransitionBitSet() {
        return transitionBitSet;
    }

    public int getLastInnerLiteralIndex() {
        return lastInnerLiteralIndex;
    }

    public void setLastInnerLiteralIndex(int i) {
        this.lastInnerLiteralIndex = i;
    }

    public int getLastInitialStateIndex() {
        return lastInitialStateIndex;
    }

    public void setLastInitialStateIndex(int i) {
        this.lastInitialStateIndex = i;
    }

    public int[] getStackFrameBuffer() {
        return stackFrameBuffer;
    }

    @TruffleBoundary
    public void printStack(int curPc) {
        System.out.println("STACK SNAPSHOT");
        System.out.println("==============");
        for (int i = sp; i >= 0; i -= stackFrameSize) {
            System.out.print(String.format("pc: %d, i: %d,\n  cg: [", (i == sp ? curPc : stack()[i + 1]), stack()[i]));
            for (int j = offsetCaptureGroups(); j < offsetQuantifierCounts(); j++) {
                System.out.print(String.format("%d, ", stack()[i + j - sp]));
            }
            System.out.print(String.format("],\n  quant: ["));
            for (int j = offsetQuantifierCounts(); j < offsetZeroWidthQuantifierIndices(); j++) {
                System.out.print(String.format("%d, ", stack()[i + j - sp]));
            }
            System.out.print(String.format("],\n  zwq-indices: ["));
            for (int j = offsetZeroWidthQuantifierIndices(); j < offsetZeroWidthQuantifierCG(); j++) {
                System.out.print(String.format("%d, ", stack()[i + j - sp]));
            }
            System.out.print(String.format("],\n  zwq-cg: {\n"));
            for (int zwq = 0; zwq < nZeroWidthQuantifiers; zwq++) {
                System.out.print(String.format("    %d: [", zwq));
                for (int j = offsetZeroWidthQuantifierCG(zwq); j < offsetZeroWidthQuantifierCG(zwq + 1); j++) {
                    System.out.print(String.format("%d, ", stack()[i + j - sp]));
                }
                System.out.print("],\n");
            }
            System.out.println("}\n");
        }
    }

    private static final class Stack {

        private int[] stack;

        Stack(int[] stack) {
            this.stack = stack;
        }
    }
}
