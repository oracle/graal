/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.buffer.ByteArrayBuffer;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.util.EmptyArrays;

/**
 * A DFA transition node referencing a packed simple capture-group transition record. Simple and
 * generic capture-group tracking are mutually exclusive, so simple records use the executor's
 * capture-group transition record array when generic partial-transition records are absent.
 *
 * <pre>
 * simple capture-group transition record
 *
 * +-------------+------+-------------------------------------+
 * | byte offset | size | field                               |
 * +-------------+------+-------------------------------------+
 * | 0           | 1    | flags                               |
 * | 1           | 1    | last group (signed, -1 means none)  |
 * | 2           | 1    | index-update count                  |
 * | 3           | 1    | index-clear count                   |
 * +-------------+------+-------------------------------------+
 *
 * payload: index-update result indices, followed by index-clear result indices
 * </pre>
 */
public final class DFASimpleCGTransition extends DFAAbstractTransitionNode {

    public static final int NO_TRANSITION = -1;

    private static final int FLAG_FULL_CLEAR = 1;
    private static final int FLAG_FINAL_TRANSITION = 1 << 1;

    private static final int FIELD_FLAGS = 0;
    private static final int FIELD_LAST_GROUP = 1;
    private static final int FIELD_INDEX_UPDATES_COUNT = 2;
    private static final int FIELD_INDEX_CLEARS_COUNT = 3;
    private static final int RECORD_HEADER_SIZE = 4;

    /** Absolute offset into {@link TRegexDFAExecutorNode#getSimpleCGTransitionRecords()}. */
    private final int transitionRef;

    private DFASimpleCGTransition(short id, short successor, int transitionRef) {
        super(id, successor);
        assert transitionRef != NO_TRANSITION;
        this.transitionRef = transitionRef;
    }

    public static DFASimpleCGTransition create(short id, short successor, int transitionRef) {
        return transitionRef == NO_TRANSITION ? null : new DFASimpleCGTransition(id, successor, transitionRef);
    }

    @Override
    void apply(TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(this);
        apply(transitionRef, locals, executor);
    }

    static void apply(int transitionRef, TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        byte[] transitionRecords = executor.getSimpleCGTransitionRecords();
        CompilerAsserts.partialEvaluationConstant(transitionRecords);
        CompilerAsserts.partialEvaluationConstant(transitionRef);
        CompilerAsserts.partialEvaluationConstant(executor);
        int index = executor.isForward() ? locals.getIndex() : locals.getNextIndex();
        int[] result = isFinalTransition(transitionRecords, transitionRef) && executor.isSimpleCGMustCopy() ? locals.getCGData().currentResult : locals.getCGData().results;
        apply(transitionRecords, transitionRef, result, index, executor.tracksLastGroup(), executor.isForward());
    }

    private static void apply(byte[] transitionRecords, int transitionRef, int[] result, int currentIndex, boolean trackLastGroup, boolean forward) {
        int numberOfIndexUpdates = getIndexUpdatesCount(transitionRecords, transitionRef);
        int numberOfIndexClears = getIndexClearsCount(transitionRecords, transitionRef);
        if (isFullClear(transitionRecords, transitionRef)) {
            assert numberOfIndexClears == 0;
            Arrays.fill(result, -1);
        } else {
            applyIndexClear(transitionRecords, transitionRef + RECORD_HEADER_SIZE + numberOfIndexUpdates, numberOfIndexClears, result);
        }
        applyIndexUpdate(transitionRecords, transitionRef + RECORD_HEADER_SIZE, numberOfIndexUpdates, result, currentIndex);
        int lastGroup = transitionRecords[transitionRef + FIELD_LAST_GROUP];
        if (trackLastGroup && lastGroup != -1 && (forward || result[result.length - 1] == -1)) {
            result[result.length - 1] = lastGroup;
        }
    }

    private static boolean isFullClear(byte[] transitionRecords, int transitionRef) {
        return isFlagSet(transitionRecords, transitionRef, FLAG_FULL_CLEAR);
    }

    private static boolean isFinalTransition(byte[] transitionRecords, int transitionRef) {
        return isFlagSet(transitionRecords, transitionRef, FLAG_FINAL_TRANSITION);
    }

    private static boolean isFlagSet(byte[] transitionRecords, int transitionRef, int flag) {
        return (transitionRecords[transitionRef + FIELD_FLAGS] & flag) != 0;
    }

    private static int getIndexUpdatesCount(byte[] transitionRecords, int transitionRef) {
        return Byte.toUnsignedInt(transitionRecords[transitionRef + FIELD_INDEX_UPDATES_COUNT]);
    }

    private static int getIndexClearsCount(byte[] transitionRecords, int transitionRef) {
        return Byte.toUnsignedInt(transitionRecords[transitionRef + FIELD_INDEX_CLEARS_COUNT]);
    }

    @ExplodeLoop
    private static void applyIndexUpdate(byte[] transitionRecords, int offset, int length, int[] result, int currentIndex) {
        for (int i = 0; i < length; i++) {
            int groupBoundaryIndex = Byte.toUnsignedInt(transitionRecords[offset + i]);
            CompilerAsserts.partialEvaluationConstant(groupBoundaryIndex);
            result[groupBoundaryIndex] = currentIndex;
        }
    }

    @ExplodeLoop
    private static void applyIndexClear(byte[] transitionRecords, int offset, int length, int[] result) {
        for (int i = 0; i < length; i++) {
            int groupBoundaryIndex = Byte.toUnsignedInt(transitionRecords[offset + i]);
            CompilerAsserts.partialEvaluationConstant(groupBoundaryIndex);
            result[groupBoundaryIndex] = -1;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DFASimpleCGTransition o)) {
            return false;
        }
        return getSuccessor() == o.getSuccessor() && transitionRef == o.transitionRef;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSuccessor(), transitionRef);
    }

    /** Builds and content-deduplicates packed simple capture-group transition records. */
    public static final class Builder {

        @SuppressWarnings("rawtypes") private static final Equivalence BYTE_ARRAY_EQUIVALENCE = new Equivalence() {
            @Override
            public boolean equals(Object a, Object b) {
                return Arrays.equals((byte[]) a, (byte[]) b);
            }

            @Override
            public int hashCode(Object o) {
                return Arrays.hashCode((byte[]) o);
            }
        };

        private final ByteArrayBuffer transitionRecords = new ByteArrayBuffer();
        private final EconomicMap<byte[], Integer> transitionRefs = EconomicMap.create(BYTE_ARRAY_EQUIVALENCE);

        public int create(NFAStateTransition transition, boolean fullClear, boolean finalTransition) {
            if (transition == null || (!fullClear && transition.getGroupBoundaries().isEmpty())) {
                return NO_TRANSITION;
            }
            transition.getGroupBoundaries().materializeArrays();
            byte[] indexUpdates = transition.getGroupBoundaries().isEmpty() ? EmptyArrays.BYTE : transition.getGroupBoundaries().updatesToByteArray();
            byte[] indexClears = fullClear ? EmptyArrays.BYTE : transition.getGroupBoundaries().clearsToByteArray();
            int lastGroup = transition.getGroupBoundaries().getLastGroup();
            if (indexUpdates.length > 0xff || indexClears.length > 0xff || lastGroup < -1 || lastGroup > Byte.MAX_VALUE) {
                throw new UnsupportedRegexException("simple capture group transition is too large");
            }
            byte[] transitionRecord = new byte[RECORD_HEADER_SIZE + indexUpdates.length + indexClears.length];
            transitionRecord[FIELD_FLAGS] = (byte) ((fullClear ? FLAG_FULL_CLEAR : 0) | (finalTransition ? FLAG_FINAL_TRANSITION : 0));
            transitionRecord[FIELD_LAST_GROUP] = (byte) lastGroup;
            transitionRecord[FIELD_INDEX_UPDATES_COUNT] = (byte) indexUpdates.length;
            transitionRecord[FIELD_INDEX_CLEARS_COUNT] = (byte) indexClears.length;
            System.arraycopy(indexUpdates, 0, transitionRecord, RECORD_HEADER_SIZE, indexUpdates.length);
            System.arraycopy(indexClears, 0, transitionRecord, RECORD_HEADER_SIZE + indexUpdates.length, indexClears.length);
            return getOrCreateTransitionRef(transitionRecord);
        }

        private int getOrCreateTransitionRef(byte[] transitionRecord) {
            Integer existingRef = transitionRefs.get(transitionRecord);
            if (existingRef != null) {
                return existingRef;
            }
            int transitionRef = transitionRecords.length();
            transitionRecords.addAll(transitionRecord, transitionRecord.length);
            transitionRefs.put(transitionRecord, transitionRef);
            return transitionRef;
        }

        public byte[] getTransitionRecords() {
            return transitionRecords.isEmpty() ? null : transitionRecords.toArray();
        }
    }
}
