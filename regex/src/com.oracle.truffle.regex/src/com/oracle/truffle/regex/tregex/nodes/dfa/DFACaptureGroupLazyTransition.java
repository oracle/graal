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
import java.util.HashMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.buffer.ByteArrayBuffer;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;

/**
 * Operations on lazy capture-group transitions encoded in a single {@code byte[]} owned by a
 * {@link TRegexDFAExecutorNode}. All records are concatenated without alignment or padding. Every
 * transition is addressed by its absolute byte offset in that array. References to partial
 * transitions are absolute offsets into the executor's separately encoded partial-transition
 * array. All multi-byte values use little-endian byte order.
 * <p>
 * A single transition has the following fixed-size layout:
 *
 * <pre>
 * single transition record (5 bytes)
 *
 * +-------------+------+----------------------------------------------+
 * | byte offset | size | field                                        |
 * +-------------+------+----------------------------------------------+
 * | 0           | 1    | kind                                         |
 * | 1           | 4    | partial-transition reference (int32)         |
 * +-------------+------+----------------------------------------------+
 * </pre>
 *
 * A branching transition starts with a nine-byte header:
 *
 * <pre>
 * branching transition header (9 bytes)
 *
 * +-------------+------+------------------------------------------------+
 * | byte offset | size | field                                          |
 * +-------------+------+------------------------------------------------+
 * | 0           | 1    | kind                                           |
 * | 1           | 2    | branch count (uint16)                          |
 * | 3           | 2    | selector byte count (uint16)                   |
 * | 5           | 4    | common partial-transition reference (int32)    |
 * +-------------+------+------------------------------------------------+
 * </pre>
 *
 * Let {@code branchCount} be the number of branches and {@code selectorByteCount} the size of the
 * selector payload. The complete branching record is laid out as follows:
 *
 * <pre>
 * recordSize = 9 + 4 * branchCount + selectorByteCount
 *
 * +--------+---------------------------------------+----------------+
 * | header | branch partial-transition references | selector bytes |
 * +--------+---------------------------------------+----------------+
 *
 * section start offsets
 *
 * header:                                0
 * branch partial-transition references:  9
 * selector bytes:                        9 + 4 * branchCount
 * </pre>
 *
 * The common partial transition is applied before the partial transition selected from the
 * reference table. The selector payload depends on {@link Kind}:
 * <ul>
 * <li>{@link Kind#branchesDirect}: {@code selectorByteCount == 0}; the predecessor transition
 * index is the branch index.</li>
 * <li>{@link Kind#branchesIndirect}: {@code selectorByteCount == 2 * (branchCount - 1)}; the
 * payload contains one unsigned 16-bit predecessor transition index for each branch except the
 * last, which is the fallback branch.</li>
 * <li>{@link Kind#branchesLookupTable}: the selector payload contains one byte per predecessor
 * transition ({@code selectorByteCount == predecessorCount}). The predecessor transition index
 * addresses the payload directly, and the selected byte is the unsigned branch index.</li>
 * </ul>
 * {@link #NO_TRANSITION} denotes an absent record. Reference {@link #EMPTY_TRANSITION} is reserved
 * for the canonical empty single transition.
 */
public final class DFACaptureGroupLazyTransition {

    public static final int NO_TRANSITION = -1;
    public static final int EMPTY_TRANSITION = 0;

    /** The ordinals are part of the encoded format; do not reorder these values. */
    private enum Kind {
        single,
        branchesDirect,
        branchesIndirect,
        branchesLookupTable
    }

    private static final int FIELD_KIND = 0;

    private static final int FIELD_SINGLE_PARTIAL_TRANSITION_REF = 1;
    private static final int SINGLE_SIZE = 5;

    private static final int FIELD_BRANCHES_BRANCH_COUNT = 1;
    private static final int FIELD_BRANCHES_SELECTOR_LENGTH = 3;
    private static final int FIELD_BRANCHES_COMMON_PARTIAL_TRANSITION_REF = 5;
    private static final int FIELD_BRANCHES_PARTIAL_TRANSITION_REFS = 9;

    private static final ByteArraySupport BYTE_ARRAY_SUPPORT = ByteArraySupport.littleEndian();

    private DFACaptureGroupLazyTransition() {
    }

    public static boolean isEmpty(int transitionRef) {
        // same empty value for both DFACaptureGroupLazyTransition and DFACaptureGroupPartialTransition
        return transitionRef == EMPTY_TRANSITION;
    }

    public static void apply(int lazyTransitionRef, TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(lazyTransitionRef);
        apply(lazyTransitionRef, locals, executor, false);
    }

    public static void applyPreFinal(int lazyTransitionRef, TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor) {
        CompilerAsserts.partialEvaluationConstant(lazyTransitionRef);
        apply(lazyTransitionRef, locals, executor, true);
    }

    @ExplodeLoop
    public static void apply(int lazyTransitionRef, TRegexDFAExecutorLocals locals, TRegexDFAExecutorNode executor, boolean preFinal) {
        CompilerAsserts.partialEvaluationConstant(lazyTransitionRef);
        byte[] lazyTransitionRecords = executor.getCGLazyTransitionRecords();
        int kind = Byte.toUnsignedInt(lazyTransitionRecords[lazyTransitionRef + FIELD_KIND]);
        CompilerAsserts.partialEvaluationConstant(kind);
        if (kind == Kind.single.ordinal()) {
            int partialTransitionRef = getInt(lazyTransitionRecords, lazyTransitionRef + FIELD_SINGLE_PARTIAL_TRANSITION_REF);
            DFACaptureGroupPartialTransition.apply(executor.getCGPartialTransitionRecords(), partialTransitionRef, executor, locals.getCGData(), locals.getLastIndex(), preFinal, true);
            return;
        }
        int branchCount = getUnsignedShort(lazyTransitionRecords, lazyTransitionRef + FIELD_BRANCHES_BRANCH_COUNT);
        int commonPartialTransitionRef = getInt(lazyTransitionRecords, lazyTransitionRef + FIELD_BRANCHES_COMMON_PARTIAL_TRANSITION_REF);
        CompilerAsserts.partialEvaluationConstant(branchCount);
        CompilerAsserts.partialEvaluationConstant(commonPartialTransitionRef);
        byte[] partialTransitionRecords = executor.getCGPartialTransitionRecords();
        DFACaptureGroupTrackingData d = locals.getCGData();
        int lastIndex = locals.getLastIndex();
        DFACaptureGroupPartialTransition.apply(partialTransitionRecords, commonPartialTransitionRef, executor, d, lastIndex, preFinal, true);
        if (kind == Kind.branchesIndirect.ordinal()) {
            int selectorOffset = lazyTransitionRef + FIELD_BRANCHES_PARTIAL_TRANSITION_REFS + Integer.BYTES * branchCount;
            int lastTransition = locals.getLastTransition();
            for (int i = 0; i < branchCount; i++) {
                // i == branchCount - 1 transforms the last exploded iteration into an else-branch.
                if (i == branchCount - 1 || getUnsignedShort(lazyTransitionRecords, selectorOffset + Short.BYTES * i) == lastTransition) {
                    int partialTransitionRef = getInt(lazyTransitionRecords, lazyTransitionRef + FIELD_BRANCHES_PARTIAL_TRANSITION_REFS + Integer.BYTES * i);
                    CompilerAsserts.partialEvaluationConstant(partialTransitionRef);
                    DFACaptureGroupPartialTransition.apply(partialTransitionRecords, partialTransitionRef, executor, d, lastIndex, preFinal, isEmpty(commonPartialTransitionRef));
                    return;
                }
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
        int selectedBranch;
        if (kind == Kind.branchesDirect.ordinal()) {
            selectedBranch = locals.getLastTransition();
        } else {
            assert kind == Kind.branchesLookupTable.ordinal();
            int selectorOffset = lazyTransitionRef + FIELD_BRANCHES_PARTIAL_TRANSITION_REFS + Integer.BYTES * branchCount;
            assert locals.getLastTransition() < getUnsignedShort(lazyTransitionRecords, lazyTransitionRef + FIELD_BRANCHES_SELECTOR_LENGTH);
            selectedBranch = Byte.toUnsignedInt(lazyTransitionRecords[selectorOffset + locals.getLastTransition()]);
        }
        for (int i = 0; i < branchCount; i++) {
            // i == branchCount - 1 transforms the last exploded iteration into an else-branch.
            if (i == branchCount - 1 || i == selectedBranch) {
                assert i == selectedBranch;
                int partialTransitionRef = getInt(lazyTransitionRecords, lazyTransitionRef + FIELD_BRANCHES_PARTIAL_TRANSITION_REFS + Integer.BYTES * i);
                CompilerAsserts.partialEvaluationConstant(partialTransitionRef);
                DFACaptureGroupPartialTransition.apply(partialTransitionRecords, partialTransitionRef, executor, d, lastIndex, preFinal, isEmpty(commonPartialTransitionRef));
                return;
            }
        }
        throw CompilerDirectives.shouldNotReachHere();
    }

    public static int getCost(int lazyTransitionRef, TRegexDFAExecutorNode executor) {
        byte[] lazyTransitionRecords = executor.getCGLazyTransitionRecords();
        if (Byte.toUnsignedInt(lazyTransitionRecords[lazyTransitionRef + FIELD_KIND]) == Kind.single.ordinal()) {
            return DFACaptureGroupPartialTransition.getCost(executor.getCGPartialTransitionRecords(),
                            getInt(lazyTransitionRecords, lazyTransitionRef + FIELD_SINGLE_PARTIAL_TRANSITION_REF));
        }
        int branchCount = getUnsignedShort(lazyTransitionRecords, lazyTransitionRef + FIELD_BRANCHES_BRANCH_COUNT);
        int cost = DFACaptureGroupPartialTransition.getCost(executor.getCGPartialTransitionRecords(),
                        getInt(lazyTransitionRecords, lazyTransitionRef + FIELD_BRANCHES_COMMON_PARTIAL_TRANSITION_REF));
        for (int i = 0; i < branchCount; i++) {
            cost += DFACaptureGroupPartialTransition.getCost(executor.getCGPartialTransitionRecords(),
                            getInt(lazyTransitionRecords, lazyTransitionRef + FIELD_BRANCHES_PARTIAL_TRANSITION_REFS + Integer.BYTES * i));
        }
        return cost;
    }

    private static int getUnsignedShort(byte[] records, int offset) {
        return Short.toUnsignedInt(BYTE_ARRAY_SUPPORT.getShortUnaligned(records, offset));
    }

    private static int getInt(byte[] records, int offset) {
        return BYTE_ARRAY_SUPPORT.getIntUnaligned(records, offset);
    }

    /** Builds and content-deduplicates the two executor-owned capture-group transition arrays. */
    public static final class FlatRecordBuilder {

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

        private final ByteArrayBuffer partialTransitionRecords = new ByteArrayBuffer();
        private final ByteArrayBuffer lazyTransitionRecords = new ByteArrayBuffer();
        private final EconomicMap<byte[], Integer> partialTransitionRefs = EconomicMap.create(Equivalence.IDENTITY);
        private final EconomicMap<byte[], Integer> lazyTransitionRefs = EconomicMap.create(BYTE_ARRAY_EQUIVALENCE);
        private final HashMap<Integer, byte[]> partialTransitionRecordsByRef;

        public FlatRecordBuilder(boolean debugMode) {
            partialTransitionRecordsByRef = debugMode ? new HashMap<>() : null;
            int emptyPartialTransitionRef = getOrCreatePartialTransitionRef(DFACaptureGroupPartialTransition.getEmptyRecord());
            int emptyLazyTransitionRef = createSingle(DFACaptureGroupPartialTransition.getEmptyRecord());
            assert isEmpty(emptyPartialTransitionRef);
            assert isEmpty(emptyLazyTransitionRef);
        }

        public int createSingle(byte[] partialTransitionRecord) {
            int partialTransitionRef = getOrCreatePartialTransitionRef(partialTransitionRecord);
            byte[] lazyTransitionRecord = new byte[SINGLE_SIZE];
            lazyTransitionRecord[FIELD_KIND] = (byte) Kind.single.ordinal();
            BYTE_ARRAY_SUPPORT.putInt(lazyTransitionRecord, FIELD_SINGLE_PARTIAL_TRANSITION_REF, partialTransitionRef);
            return getOrCreateLazyTransitionRef(lazyTransitionRecord);
        }

        public int createBranchesDirect(byte[][] branchPartialTransitionRecords, DFAGenerator dfaGen) {
            return createBranches(Kind.branchesDirect, branchPartialTransitionRecords, null, dfaGen);
        }

        public int createBranchesIndirect(byte[][] branchPartialTransitionRecords, short[] possibleValues, DFAGenerator dfaGen) {
            byte[] selector = new byte[Short.BYTES * possibleValues.length];
            for (int i = 0; i < possibleValues.length; i++) {
                BYTE_ARRAY_SUPPORT.putShort(selector, Short.BYTES * i, possibleValues[i]);
            }
            return createBranches(Kind.branchesIndirect, branchPartialTransitionRecords, selector, dfaGen);
        }

        public int createBranchesWithLookupTable(byte[][] branchPartialTransitionRecords, byte[] lookupTable, DFAGenerator dfaGen) {
            return createBranches(Kind.branchesLookupTable, branchPartialTransitionRecords, lookupTable, dfaGen);
        }

        private int createBranches(Kind kind, byte[][] branchPartialTransitionRecords, byte[] selector, DFAGenerator dfaGen) {
            assert branchPartialTransitionRecords.length > 1;
            byte[] commonPartialTransitionRecord = dfaGen.internCGPartialTransition(DFACaptureGroupPartialTransition.intersect(branchPartialTransitionRecords, dfaGen.getCompilationBuffer()));
            if (!DFACaptureGroupPartialTransition.isEmpty(commonPartialTransitionRecord)) {
                for (int i = 0; i < branchPartialTransitionRecords.length; i++) {
                    branchPartialTransitionRecords[i] = dfaGen.internCGPartialTransition(
                                    DFACaptureGroupPartialTransition.subtract(branchPartialTransitionRecords[i], commonPartialTransitionRecord, dfaGen.getCompilationBuffer()));
                }
            }
            int selectorLength = selector == null ? 0 : selector.length;
            if (branchPartialTransitionRecords.length > 0xffff || selectorLength > 0xffff) {
                throw new UnsupportedRegexException("capture group lazy transition is too large");
            }
            byte[] lazyTransitionRecord = new byte[FIELD_BRANCHES_PARTIAL_TRANSITION_REFS + Integer.BYTES * branchPartialTransitionRecords.length + selectorLength];
            lazyTransitionRecord[FIELD_KIND] = (byte) kind.ordinal();
            BYTE_ARRAY_SUPPORT.putShort(lazyTransitionRecord, FIELD_BRANCHES_BRANCH_COUNT, (short) branchPartialTransitionRecords.length);
            BYTE_ARRAY_SUPPORT.putShort(lazyTransitionRecord, FIELD_BRANCHES_SELECTOR_LENGTH, (short) selectorLength);
            BYTE_ARRAY_SUPPORT.putInt(lazyTransitionRecord, FIELD_BRANCHES_COMMON_PARTIAL_TRANSITION_REF, getOrCreatePartialTransitionRef(commonPartialTransitionRecord));
            for (int i = 0; i < branchPartialTransitionRecords.length; i++) {
                BYTE_ARRAY_SUPPORT.putInt(lazyTransitionRecord, FIELD_BRANCHES_PARTIAL_TRANSITION_REFS + Integer.BYTES * i,
                                getOrCreatePartialTransitionRef(branchPartialTransitionRecords[i]));
            }
            if (selectorLength > 0) {
                System.arraycopy(selector, 0, lazyTransitionRecord, FIELD_BRANCHES_PARTIAL_TRANSITION_REFS + Integer.BYTES * branchPartialTransitionRecords.length, selectorLength);
            }
            return getOrCreateLazyTransitionRef(lazyTransitionRecord);
        }

        public int getOrCreatePartialTransitionRef(byte[] partialTransitionRecord) {
            Integer existingRef = partialTransitionRefs.get(partialTransitionRecord);
            if (existingRef != null) {
                return existingRef;
            }
            int partialTransitionRef = partialTransitionRecords.length();
            partialTransitionRecords.addAll(partialTransitionRecord, partialTransitionRecord.length);
            partialTransitionRefs.put(partialTransitionRecord, partialTransitionRef);
            if (partialTransitionRecordsByRef != null) {
                partialTransitionRecordsByRef.put(partialTransitionRef, partialTransitionRecord);
            }
            return partialTransitionRef;
        }

        private int getOrCreateLazyTransitionRef(byte[] lazyTransitionRecord) {
            Integer existingRef = lazyTransitionRefs.get(lazyTransitionRecord);
            if (existingRef != null) {
                return existingRef;
            }
            int lazyTransitionRef = lazyTransitionRecords.length();
            lazyTransitionRecords.addAll(lazyTransitionRecord, lazyTransitionRecord.length);
            lazyTransitionRefs.put(lazyTransitionRecord, lazyTransitionRef);
            return lazyTransitionRef;
        }

        public byte[] getPartialTransitionRecord(int partialTransitionRef) {
            assert partialTransitionRecordsByRef != null;
            return partialTransitionRecordsByRef.get(partialTransitionRef);
        }

        public byte[] getPartialTransitionRecords() {
            return partialTransitionRecords.toArray();
        }

        public byte[] getLazyTransitionRecords() {
            return lazyTransitionRecords.toArray();
        }
    }
}
