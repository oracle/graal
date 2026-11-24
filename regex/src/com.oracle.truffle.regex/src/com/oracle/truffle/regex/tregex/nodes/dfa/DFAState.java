/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputOps;
import com.oracle.truffle.regex.tregex.string.Encoding;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * An ordinary DFA state. All ordinary state kinds share this packed-record prefix; subclasses append
 * their kind-specific fields at {@code 13 + 4 * successorCount}.
 *
 * <pre>
 * +-------------+--------------------+--------------------------------+
 * | byte offset | size               | field                          |
 * +-------------+--------------------+--------------------------------+
 * | 0           | 1                  | kind                           |
 * | 1           | 1                  | flags                          |
 * | 2           | 2                  | loop-transition index (int16)  |
 * | 4           | 2                  | index-of node ID (int16)       |
 * | 6           | 1                  | index-of fast flag             |
 * | 7           | 4                  | matcher reference (int32)      |
 * | 11          | 2                  | successor count (uint16)       |
 * | 13          | 4 * successorCount | successor references (int32[]) |
 * +-------------+--------------------+--------------------------------+
 * </pre>
 */
public class DFAState extends DFAAbstractState {

    private static final int FIELD_FLAGS = 1;
    private static final int FIELD_LOOP_TRANSITION_INDEX = 2;
    private static final int FIELD_INDEX_OF_NODE_ID = 4;
    private static final int FIELD_INDEX_OF_IS_FAST = 6;
    private static final int FIELD_MATCHER_REF = 7;
    private static final int FIELD_SUCCESSOR_COUNT = 11;
    private static final int FIELD_SUCCESSORS = 13;

    static final byte FLAG_FINAL_STATE = 1;
    static final byte FLAG_ANCHORED_FINAL_STATE = 1 << 1;
    static final byte FLAG_HAS_BACKWARD_PREFIX_STATE = 1 << 2;
    static final byte FLAG_UTF_16_MUST_DECODE = 1 << 3;
    static final byte FLAG_GUARDED_FINAL_STATE = 1 << 4;
    static final byte FLAG_GUARDED_ANCHORED_FINAL_STATE = 1 << 5;
    static final byte FLAG_INDEX_OF_MAY_HAVE_NO_SUCCESSOR = 1 << 6;

    private final byte flags;
    private final short loopTransitionIndex;
    private final short indexOfNodeId;
    private final byte indexOfIsFast;
    /**
     * Non-negative values are absolute offsets into the executor's sequential matcher records.
     * Negative values encode an index into the executor's tree matcher array as {@code ~index}.
     */
    private final int matcherRef;
    private final short anchoredFinalSuccessor;

    public DFAState(short id, byte flags, short loopTransitionIndex, short indexOfNodeId, byte indexOfIsFast, short[] successors, int matcherRef, short anchoredFinalSuccessor) {
        super(id, successors);
        assert id > 0;
        assert (flags & FLAG_INDEX_OF_MAY_HAVE_NO_SUCCESSOR) == 0 || (loopTransitionIndex >= 0 && indexOfNodeId >= 0 && indexOfIsFast != 0);
        this.flags = flags;
        this.loopTransitionIndex = loopTransitionIndex;
        this.indexOfNodeId = indexOfNodeId;
        this.indexOfIsFast = indexOfIsFast;
        this.matcherRef = matcherRef;
        this.anchoredFinalSuccessor = anchoredFinalSuccessor;
    }

    public static byte buildFlags(boolean finalState, boolean anchoredFinalState, boolean hasBackwardPrefixState, boolean utf16MustDecode, boolean guardedFinalState,
                    boolean guardedAnchoredFinalState, boolean indexOfMayHaveNoSuccessor) {
        byte flags = 0;
        if (finalState) {
            flags |= FLAG_FINAL_STATE;
        }
        if (anchoredFinalState) {
            flags |= FLAG_ANCHORED_FINAL_STATE;
        }
        if (hasBackwardPrefixState) {
            flags |= FLAG_HAS_BACKWARD_PREFIX_STATE;
        }
        if (utf16MustDecode) {
            flags |= FLAG_UTF_16_MUST_DECODE;
        }
        if (guardedFinalState) {
            flags |= FLAG_GUARDED_FINAL_STATE;
        }
        if (guardedAnchoredFinalState) {
            flags |= FLAG_GUARDED_ANCHORED_FINAL_STATE;
        }
        if (indexOfMayHaveNoSuccessor) {
            flags |= FLAG_INDEX_OF_MAY_HAVE_NO_SUCCESSOR;
        }
        return flags;
    }

    public final int getSequentialMatchersRef() {
        assert !treeTransitionMatching();
        return matcherRef;
    }

    public final int getTreeMatcherIndex() {
        assert treeTransitionMatching();
        return ~matcherRef;
    }

    public static int treeMatcherRef(int treeMatcherIndex) {
        assert treeMatcherIndex >= 0;
        return ~treeMatcherIndex;
    }

    public boolean isFinalState() {
        return flagIsSet(FLAG_FINAL_STATE);
    }

    public boolean isGuardedFinalState() {
        return flagIsSet(FLAG_GUARDED_FINAL_STATE);
    }

    public boolean isGuardedAnchoredFinalState() {
        return flagIsSet(FLAG_GUARDED_ANCHORED_FINAL_STATE);
    }

    public boolean isAnchoredFinalState() {
        return flagIsSet(FLAG_ANCHORED_FINAL_STATE);
    }

    public boolean hasBackwardPrefixState() {
        return flagIsSet(FLAG_HAS_BACKWARD_PREFIX_STATE);
    }

    private boolean flagIsSet(byte flag) {
        return (flags & flag) != 0;
    }

    public boolean hasLoopToSelf() {
        return loopTransitionIndex >= 0;
    }

    boolean treeTransitionMatching() {
        return matcherRef < 0;
    }

    public short getAnchoredFinalSuccessor() {
        return anchoredFinalSuccessor;
    }

    DFAExecutorRecords.Kind getRecordKind() {
        return DFAExecutorRecords.Kind.state;
    }

    int encodedTailSize() {
        return 0;
    }

    void encodeTail(@SuppressWarnings("unused") DFAExecutorRecords.Encoder encoder, @SuppressWarnings("unused") int tailRef) {
    }

    @Override
    final int encodedSize() {
        return Math.addExact(tailOffset(successors.length), encodedTailSize());
    }

    @Override
    final void encode(DFAExecutorRecords.Encoder encoder, int recordRef) {
        encoder.putKind(recordRef, getRecordKind());
        encoder.putByte(recordRef + FIELD_FLAGS, flags);
        encoder.putShort(recordRef + FIELD_LOOP_TRANSITION_INDEX, loopTransitionIndex);
        encoder.putShort(recordRef + FIELD_INDEX_OF_NODE_ID, indexOfNodeId);
        encoder.putByte(recordRef + FIELD_INDEX_OF_IS_FAST, indexOfIsFast);
        encoder.putInt(recordRef + FIELD_MATCHER_REF, matcherRef);
        encoder.putUnsignedShort(recordRef + FIELD_SUCCESSOR_COUNT, successors.length);
        for (int i = 0; i < successors.length; i++) {
            encoder.putNodeRef(recordRef + FIELD_SUCCESSORS + Integer.BYTES * i, successors[i]);
        }
        encodeTail(encoder, recordRef + tailOffset(successors.length));
    }

    static boolean flagIsSet(byte[] records, int recordRef, int flag) {
        return (records[recordRef + FIELD_FLAGS] & flag) != 0;
    }

    static int getLoopTransitionIndex(byte[] records, int recordRef) {
        return DFAExecutorRecords.getShort(records, recordRef + FIELD_LOOP_TRANSITION_INDEX);
    }

    static int getIndexOfNodeId(byte[] records, int recordRef) {
        return DFAExecutorRecords.getShort(records, recordRef + FIELD_INDEX_OF_NODE_ID);
    }

    static int getIndexOfIsFast(byte[] records, int recordRef) {
        return Byte.toUnsignedInt(records[recordRef + FIELD_INDEX_OF_IS_FAST]);
    }

    static int getMatcherRef(byte[] records, int recordRef) {
        return DFAExecutorRecords.getInt(records, recordRef + FIELD_MATCHER_REF);
    }

    static int getSuccessorCount(byte[] records, int recordRef) {
        return DFAExecutorRecords.getUnsignedShort(records, recordRef + FIELD_SUCCESSOR_COUNT);
    }

    @CompilerDirectives.EarlyInline
    static int getSuccessor(byte[] records, int recordRef, int index) {
        return DFAExecutorRecords.getInt(records, recordRef + FIELD_SUCCESSORS + Integer.BYTES * index);
    }

    static int getEncodedSize(byte[] records, int recordRef) {
        return tailOffset(records, recordRef) - recordRef;
    }

    static int tailOffset(byte[] records, int recordRef) {
        return recordRef + tailOffset(getSuccessorCount(records, recordRef));
    }

    private static int tailOffset(int successorCount) {
        return FIELD_SUCCESSORS + Integer.BYTES * successorCount;
    }

    static boolean isFinalState(byte[] records, int recordRef) {
        return flagIsSet(records, recordRef, FLAG_FINAL_STATE);
    }

    static boolean isGuardedFinalState(byte[] records, int recordRef) {
        return flagIsSet(records, recordRef, FLAG_GUARDED_FINAL_STATE);
    }

    static boolean isGuardedAnchoredFinalState(byte[] records, int recordRef) {
        return flagIsSet(records, recordRef, FLAG_GUARDED_ANCHORED_FINAL_STATE);
    }

    static boolean isAnchoredFinalState(byte[] records, int recordRef) {
        return flagIsSet(records, recordRef, FLAG_ANCHORED_FINAL_STATE);
    }

    static boolean hasBackwardPrefixState(byte[] records, int recordRef) {
        return flagIsSet(records, recordRef, FLAG_HAS_BACKWARD_PREFIX_STATE);
    }

    static boolean utf16MustDecode(byte[] records, int recordRef) {
        return flagIsSet(records, recordRef, FLAG_UTF_16_MUST_DECODE);
    }

    static boolean indexOfMayHaveNoSuccessor(byte[] records, int recordRef) {
        return flagIsSet(records, recordRef, FLAG_INDEX_OF_MAY_HAVE_NO_SUCCESSOR);
    }

    /**
     * Returns {@code true} if this state has a {@code indexOf}-node for the given code range.
     */
    static boolean canDoIndexOf(byte[] records, int recordRef, TruffleString.CodeRange codeRange) {
        CompilerAsserts.partialEvaluationConstant(codeRange);
        CompilerAsserts.partialEvaluationConstant(codeRange.ordinal());
        int loopTransitionIndex = getLoopTransitionIndex(records, recordRef);
        int indexOfNodeId = getIndexOfNodeId(records, recordRef);
        int indexOfIsFast = getIndexOfIsFast(records, recordRef);
        return loopTransitionIndex >= 0 && indexOfNodeId >= 0 && (indexOfIsFast & (1 << codeRange.ordinal())) != 0;
    }

    /**
     * Gets called when a new DFA state is entered. Returns true whenever the executor can
     * immediately return the result.
     */
    static boolean beforeFindSuccessor(byte[] records, int recordRef, TRegexDFAExecutorLocals locals) {
        return checkFinalState(records, recordRef, locals);
    }

    /**
     * Gets called after every call to
     * {@link InputOps#indexOf(TruffleString, int, int, TruffleString.CodePointSet, Encoding, TruffleString.ByteIndexOfCodePointSetNode)}
     * which we call an {@code indexOf}-operation.
     *
     * @param postLoopIndex the index found by the {@code indexOf}-operation. If the {@code indexOf}
     *            -operation did not find a match, this value is equal to
     *            {@link TRegexDFAExecutorLocals#getMaxIndex()}.
     */
    static void afterIndexOf(byte[] records, int recordRef, TRegexDFAExecutorLocals locals, int postLoopIndex) {
        locals.setIndex(postLoopIndex);
        checkFinalState(records, recordRef, locals);
    }

    /**
     * Save the current result iff we are in a final state.
     */
    private static boolean checkFinalState(byte[] records, int recordRef, TRegexDFAExecutorLocals locals) {
        if (isFinalState(records, recordRef)) {
            storeResult(locals);
        }
        return false;
    }

    /**
     * Gets called if {@link TRegexExecutorNode#getMaxIndex(TRegexExecutorLocals)} is reached.
     */
    static void atEnd(byte[] records, int recordRef, TRegexDFAExecutorLocals locals, boolean inputAtEnd) {
        boolean anchored = isAnchoredFinalState(records, recordRef) && inputAtEnd;
        if (isFinalState(records, recordRef) || anchored) {
            storeResult(locals);
        }
    }

    /**
     * Saves the current result (single index or all capture group boundaries).
     */
    static void storeResult(TRegexDFAExecutorLocals locals) {
        locals.setResultInt(locals.getIndex());
    }

    @TruffleBoundary
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        DebugUtil.appendNodeId(sb, getId()).append(": ");
        if (!treeTransitionMatching()) {
            sb.append(getNumberOfMatcherTransitions()).append(" successors");
        }
        if (isAnchoredFinalState()) {
            sb.append(", AFS");
        }
        if (isFinalState()) {
            sb.append(", FS");
        }
        sb.append(":\n");
        if (treeTransitionMatching()) {
            sb.append("      treeMatcher@").append(getTreeMatcherIndex()).append("\n      successors: ").append(Arrays.toString(successors)).append("\n");
        } else {
            for (int i = 0; i < getNumberOfMatcherTransitions(); i++) {
                sb.append("      transition ").append(i).append(" (matcherRecord@").append(getSequentialMatchersRef()).append(") -> ");
                DebugUtil.appendNodeId(sb, getSuccessors()[i]).append("\n");
            }
        }
        return sb.toString();
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        JsonArray transitions = Json.array();
        if (!treeTransitionMatching()) {
            for (int i = 0; i < getNumberOfMatcherTransitions(); i++) {
                transitions.append(Json.obj(Json.prop("matcher", "matcherRecord@" + getSequentialMatchersRef() + " transition " + i), Json.prop("target", successors[i])));
            }
        }
        return Json.obj(Json.prop("id", getId()),
                        Json.prop("anchoredFinalState", isAnchoredFinalState() || isGuardedAnchoredFinalState()),
                        Json.prop("finalState", isFinalState() || isGuardedFinalState()),
                        Json.prop("loopToSelf", hasLoopToSelf()),
                        Json.prop("transitions", transitions));
    }

    private int getNumberOfMatcherTransitions() {
        return successors.length - (hasBackwardPrefixState() ? 1 : 0);
    }
}
