/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test.strings;

import java.util.ArrayList;
import java.util.List;

public final class TStringOpsIndexOfConsecutiveTablesTestData {

    static final int OFFSET = 20;
    static final int PADDING = 20;
    static final int CONTENT_LENGTH = 256;

    static final int[] FROM_INDEXES = {0, 1, 15};
    static final int[] LENGTHS = {16, 17, 31, 32, 33, 63, 64, 65, 255, 256};
    static final int[] EXHAUSTIVE_TAIL_LENGTHS = {47, 63};

    private static final byte NON_MATCH_BYTE = 'x';
    private static final byte[][] BASE_ARRAYS = {
                    createTestArray(0),
                    createTestArray(1),
                    createTestArray(2),
    };

    /**
     * Match a realistic mix of two zero bytes, common ASCII bigrams, and one UTF-8 byte pair with
     * the sign bit set in both bytes.
     */
    static final TableCase[] TABLE_CASES_2 = {
                    new TableCase(
                                    "zero_zero/th/he/in/utf8_e_acute_c3_a9",
                                    new String[]{"zero_zero", "th", "he", "in", "utf8_e_acute_c3_a9"},
                                    new byte[][]{seq(0x00, 0x00), ascii("th"), ascii("he"), ascii("in"), seq(0xC3, 0xA9)}),
                    new TableCase(
                                    "zero_zero/er/re/an/utf8_beta_ce_b2",
                                    new String[]{"zero_zero", "er", "re", "an", "utf8_beta_ce_b2"},
                                    new byte[][]{seq(0x00, 0x00), ascii("er"), ascii("re"), ascii("an"), seq(0xCE, 0xB2)}),
                    new TableCase(
                                    "zero_zero/on/ed/ng/utf8_ya_d1_8f",
                                    new String[]{"zero_zero", "on", "ed", "ng", "utf8_ya_d1_8f"},
                                    new byte[][]{seq(0x00, 0x00), ascii("on"), ascii("ed"), ascii("ng"), seq(0xD1, 0x8F)}),
                    new TableCase(
                                    "ff_h/ff_e/ff_n/utf8_ff_80",
                                    new String[]{"ff_h", "ff_e", "ff_n", "utf8_ff_80"},
                                    new byte[][]{seq(0xFF, 'h'), seq(0xFF, 'e'), seq(0xFF, 'n'), seq(0xFF, 0x80)}),
    };

    static final TableCase[] TABLE_CASES_3 = {
                    new TableCase(
                                    "zero_zero_zero/the/ing/and/utf8_euro_e2_82_ac",
                                    new String[]{"zero_zero_zero", "the", "ing", "and", "utf8_euro_e2_82_ac"},
                                    new byte[][]{seq(0x00, 0x00, 0x00), ascii("the"), ascii("ing"), ascii("and"), seq(0xE2, 0x82, 0xAC)}),
                    new TableCase(
                                    "zero_zero_zero/ent/ion/for/utf8_deva_ha_e0_a4_b9",
                                    new String[]{"zero_zero_zero", "ent", "ion", "for", "utf8_deva_ha_e0_a4_b9"},
                                    new byte[][]{seq(0x00, 0x00, 0x00), ascii("ent"), ascii("ion"), ascii("for"), seq(0xE0, 0xA4, 0xB9)}),
    };

    static final TableCase[] TABLE_CASES_4 = {
                    new TableCase(
                                    "zero_zero_zero_zero/that/tion/here/utf8_grinning_f0_9f_98_80",
                                    new String[]{"zero_zero_zero_zero", "that", "tion", "here", "utf8_grinning_f0_9f_98_80"},
                                    new byte[][]{seq(0x00, 0x00, 0x00, 0x00), ascii("that"), ascii("tion"), ascii("here"), seq(0xF0, 0x9F, 0x98, 0x80)}),
                    new TableCase(
                                    "zero_zero_zero_zero/with/ther/ment/utf8_poo_f0_9f_92_a9",
                                    new String[]{"zero_zero_zero_zero", "with", "ther", "ment", "utf8_poo_f0_9f_92_a9"},
                                    new byte[][]{seq(0x00, 0x00, 0x00, 0x00), ascii("with"), ascii("ther"), ascii("ment"), seq(0xF0, 0x9F, 0x92, 0xA9)}),
    };

    static {
        verifyTableCases();
    }

    public static List<Object[]> data(int fromStride, int tableCount) {
        ArrayList<Object[]> ret = new ArrayList<>();
        for (int strideA = fromStride; strideA < 3; strideA++) {
            for (int offsetOrdinal = 0; offsetOrdinal < 2; offsetOrdinal++) {
                int offsetA = offsetBytes(strideA, offsetOrdinal);
                for (int fromIndexA : FROM_INDEXES) {
                    for (int lengthA : LENGTHS) {
                        if (fromIndexA >= lengthA) {
                            continue;
                        }
                        for (TableCase tableCase : tableCases(tableCount)) {
                            int sequenceIndex = chooseSequenceIndex(tableCase, offsetOrdinal, strideA, fromIndexA, lengthA);
                            for (CaseSpec caseSpec : defaultCaseSpecs(tableCount, strideA, lengthA, fromIndexA)) {
                                ret.add(new Object[]{offsetA, lengthA, strideA, fromIndexA, tableCase, caseSpec, sequenceIndex, tableCase.sequenceLabel(sequenceIndex)});
                            }
                        }
                    }
                    for (int tailLength : EXHAUSTIVE_TAIL_LENGTHS) {
                        int lengthA = fromIndexA + tailLength;
                        if (lengthA <= CONTENT_LENGTH) {
                            for (TableCase tableCase : tableCases(tableCount)) {
                                int sequenceIndex = chooseSequenceIndex(tableCase, offsetOrdinal, strideA, fromIndexA, lengthA);
                                for (CaseSpec caseSpec : exhaustiveTailCaseSpecs(tableCount, strideA, lengthA, fromIndexA)) {
                                    ret.add(new Object[]{offsetA, lengthA, strideA, fromIndexA, tableCase, caseSpec, sequenceIndex, tableCase.sequenceLabel(sequenceIndex)});
                                }
                            }
                        }
                    }
                }
            }
        }
        return ret;
    }

    private TStringOpsIndexOfConsecutiveTablesTestData() {
    }

    static int offsetBytes(int stride, int offsetOrdinal) {
        return (OFFSET + (offsetOrdinal * (CONTENT_LENGTH / 2))) << stride;
    }

    static byte[] createArray(int stride, int offsetBytes, int length, int fromIndex, CaseSpec caseSpec, byte[] sequence) {
        if (!caseSpec.injectsSequence()) {
            return BASE_ARRAYS[stride];
        }
        byte[] array = BASE_ARRAYS[stride].clone();
        injectSequence(array, offsetBytes, stride, caseSpec.startPosition(sequence.length, length, fromIndex), caseSpec, sequence);
        return array;
    }

    static int chooseSequenceIndex(TableCase tableCase, int offsetOrdinal, int stride, int fromIndex, int length) {
        return (offsetOrdinal + stride + fromIndex + length) % tableCase.sequenceCount();
    }

    static List<CaseSpec> defaultCaseSpecs(int tableCount, int stride, int length, int fromIndex) {
        return tableCount == 2 ? defaultCaseSpecs2(stride, length, fromIndex) : defaultCaseSpecsN(tableCount, stride, length, fromIndex);
    }

    private static List<CaseSpec> defaultCaseSpecs2(int stride, int length, int fromIndex) {
        ArrayList<CaseSpec> ret = new ArrayList<>();
        for (Scenario scenario : new Scenario[]{
                        Scenario.BASELINE,
                        Scenario.MATCH_AT_FROM_INDEX,
                        Scenario.MATCH_BEFORE_FROM_INDEX,
                        Scenario.MATCH_IN_BETWEEN,
                        Scenario.MATCH_AT_WINDOW_END,
                        Scenario.MATCH_AFTER_WINDOW}) {
            if (scenario.applies(2, stride, length, fromIndex)) {
                ret.add(CaseSpec.ofScenario(scenario));
            }
        }
        if (Scenario.MATCH_AT_FROM_INDEX.applies(2, stride, length, fromIndex)) {
            ret.add(CaseSpec.nonMatchAtFromIndex(1, "first_only_at_fromIndex"));
            ret.add(CaseSpec.nonMatchAtFromIndex(0, "second_only_at_fromIndex"));
            if (stride > 0) {
                ret.add(CaseSpec.widenedLowBytesAtFromIndex());
                ret.add(CaseSpec.widenedByteAtFromIndex(0, 0x01, "widened_leading_byte_at_fromIndex"));
                ret.add(CaseSpec.widenedByteAtFromIndex(1, 0x80, "widened_trailing_byte_at_fromIndex"));
            }
        }
        return ret;
    }

    private static List<CaseSpec> defaultCaseSpecsN(int tableCount, int stride, int length, int fromIndex) {
        ArrayList<CaseSpec> ret = new ArrayList<>();
        for (Scenario scenario : new Scenario[]{
                        Scenario.BASELINE,
                        Scenario.MATCH_AT_FROM_INDEX,
                        Scenario.MATCH_BEFORE_FROM_INDEX,
                        Scenario.MATCH_IN_BETWEEN,
                        Scenario.MATCH_AT_WINDOW_END,
                        Scenario.MATCH_AFTER_WINDOW}) {
            if (scenario.applies(tableCount, stride, length, fromIndex)) {
                ret.add(CaseSpec.ofScenario(scenario));
            }
        }
        if (Scenario.MATCH_AT_FROM_INDEX.applies(tableCount, stride, length, fromIndex)) {
            ret.add(CaseSpec.nonMatchAtFromIndex(0, "non_match_first_byte_at_fromIndex"));
            ret.add(CaseSpec.nonMatchAtFromIndex(tableCount - 1, "non_match_last_byte_at_fromIndex"));
            if (stride > 0) {
                ret.add(CaseSpec.widenedLowBytesAtFromIndex());
                for (int sequenceIndex = 0; sequenceIndex < tableCount; sequenceIndex++) {
                    ret.add(CaseSpec.widenedByteAtFromIndex(sequenceIndex, widenedSingleHighByteBase(sequenceIndex, tableCount), "widened_byte_" + sequenceIndex + "_at_fromIndex"));
                }
            }
        }
        return ret;
    }

    static List<CaseSpec> exhaustiveTailCaseSpecs(int tableCount, int stride, int length, int fromIndex) {
        int tailLength = length - fromIndex;
        if (tailLength != 47 && tailLength != 63) {
            throw new AssertionError("expected length - fromIndex to be 47 or 63");
        }
        ArrayList<CaseSpec> ret = new ArrayList<>();
        for (int startPosition = fromIndex; startPosition <= length; startPosition++) {
            ret.add(CaseSpec.matchAtPosition(fromIndex, startPosition));
            if (stride > 0 && startPosition + tableCount - 1 < length) {
                ret.add(CaseSpec.widenedLowBytesAtPosition(fromIndex, startPosition));
                if (tableCount == 2) {
                    ret.add(CaseSpec.widenedByteAtPosition(fromIndex, startPosition, 0, 0x01,
                                    "widened_leading_byte_at_position_" + startPosition + "_rel" + (startPosition - fromIndex)));
                    ret.add(CaseSpec.widenedByteAtPosition(fromIndex, startPosition, 1, 0x80,
                                    "widened_trailing_byte_at_position_" + startPosition + "_rel" + (startPosition - fromIndex)));
                } else {
                    for (int sequenceIndex = 0; sequenceIndex < tableCount; sequenceIndex++) {
                        ret.add(CaseSpec.widenedByteAtPosition(fromIndex, startPosition, sequenceIndex, widenedSingleHighByteBase(sequenceIndex, tableCount),
                                        "widened_byte_" + sequenceIndex + "_at_position_" + startPosition + "_rel" + (startPosition - fromIndex)));
                    }
                }
            }
        }
        return ret;
    }

    private static byte[] createTestArray(int stride) {
        byte[] array = new byte[(OFFSET + (CONTENT_LENGTH * 3) + PADDING) << stride];
        int[] valueOffset = {0, 0x1000, 0x10_0000};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < CONTENT_LENGTH; j++) {
                TStringTest.writeValue(array, stride, OFFSET + j + (CONTENT_LENGTH * i), j + valueOffset[i]);
            }
        }
        return array;
    }

    private static void injectSequence(byte[] array, int offsetBytes, int stride, int startPosition, CaseSpec caseSpec, byte[] sequence) {
        int baseIndex = offsetBytes >> stride;
        if (startPosition > 0) {
            TStringTest.writeValue(array, stride, baseIndex + startPosition - 1, Byte.toUnsignedInt(NON_MATCH_BYTE));
        }
        for (int i = 0; i < sequence.length; i++) {
            TStringTest.writeValue(array, stride, baseIndex + startPosition + i, caseSpec.valueAt(sequence, stride, i));
        }
        TStringTest.writeValue(array, stride, baseIndex + startPosition + sequence.length, Byte.toUnsignedInt(NON_MATCH_BYTE));
    }

    private static byte[] ascii(String sequence) {
        byte[] ret = new byte[sequence.length()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = (byte) sequence.charAt(i);
        }
        return ret;
    }

    private static byte[] seq(int... values) {
        byte[] ret = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            ret[i] = (byte) values[i];
        }
        return ret;
    }

    private static int widenValue(byte value, int stride, int highByteBase) {
        int low = Byte.toUnsignedInt(value);
        return switch (stride) {
            case 1 -> (highByteBase << 8) | low;
            case 2 -> (highByteBase << 24) | ((highByteBase + 1) << 16) | ((highByteBase + 2) << 8) | low;
            default -> throw new AssertionError(stride);
        };
    }

    private static int widenedLowBytesHighByteBase(int sequenceIndex) {
        return 0x01 + (sequenceIndex * 3);
    }

    private static int widenedSingleHighByteBase(int sequenceIndex, int sequenceLength) {
        return sequenceIndex == sequenceLength - 1 ? 0x80 : widenedLowBytesHighByteBase(sequenceIndex);
    }

    private static byte[] buildTable(byte[][] sequences, int byteIndex) {
        int[] table = new int[32];
        for (int bit = 0; bit < sequences.length; bit++) {
            int value = Byte.toUnsignedInt(sequences[bit][byteIndex]);
            int mask = 1 << bit;
            table[(value >>> 4) & 0xf] |= mask;
            table[16 + (value & 0xf)] |= mask;
        }
        byte[] ret = new byte[32];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = (byte) table[i];
        }
        return ret;
    }

    private static byte[] concat(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }
        byte[] ret = new byte[totalLength];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, ret, offset, array.length);
            offset += array.length;
        }
        return ret;
    }

    private static int tableBitSet(byte[] tables, int tableOffset, int value) {
        if (value > 0xff) {
            return 0;
        }
        return Byte.toUnsignedInt(tables[tableOffset + ((value >>> 4) & 0xf)]) & Byte.toUnsignedInt(tables[tableOffset + 16 + (value & 0xf)]);
    }

    private static int midPosition(int tableCount, int length, int fromIndex) {
        int lo = fromIndex + 1;
        int hi = length - (tableCount + 1);
        if (lo > hi) {
            throw new AssertionError();
        }
        return lo + ((hi - lo) / 2);
    }

    private static TableCase[] tableCases(int tableCount) {
        return switch (tableCount) {
            case 2 -> TABLE_CASES_2;
            case 3 -> TABLE_CASES_3;
            case 4 -> TABLE_CASES_4;
            default -> throw new AssertionError(tableCount);
        };
    }

    private static void verifyTableCases() {
        for (TableCase[] tableCases : new TableCase[][]{TABLE_CASES_2, TABLE_CASES_3, TABLE_CASES_4}) {
            for (TableCase tableCase : tableCases) {
                verifyTableCase(tableCase);
            }
        }
    }

    private static void verifyTableCase(TableCase tableCase) {
        if (tableCase.sequenceCount() > Byte.SIZE) {
            throw new AssertionError(tableCase);
        }
        for (int tableIndex = 0; tableIndex < tableCase.sequenceLength(); tableIndex++) {
            if (tableBitSet(tableCase.tables(), tableIndex * 32, Byte.toUnsignedInt(NON_MATCH_BYTE)) != 0) {
                throw new AssertionError(tableCase);
            }
        }
        boolean hasSignBitSequence = false;
        for (int i = 0; i < tableCase.sequenceCount(); i++) {
            byte[] sequence = tableCase.sequence(i);
            if (sequence.length != tableCase.sequenceLength()) {
                throw new AssertionError(tableCase);
            }
            for (byte value : sequence) {
                hasSignBitSequence |= (value & 0x80) != 0;
            }
        }
        if (!hasSignBitSequence) {
            throw new AssertionError(tableCase);
        }
    }

    private enum MutationKind {
        NONE,
        WIDEN_LOW_BYTES_ALL,
        WIDEN_SINGLE_ELEMENT,
        NON_MATCH_SINGLE_ELEMENT,
    }

    public enum Scenario {
        BASELINE("baseline") {
            @Override
            boolean applies(int tableCount, int stride, int length, int fromIndex) {
                return true;
            }

            @Override
            boolean injectsSequence() {
                return false;
            }

            @Override
            int startPosition(int tableCount, int length, int fromIndex) {
                throw new UnsupportedOperationException();
            }
        },
        MATCH_AT_EXPLICIT_POSITION("match_at_explicit_position") {
            @Override
            boolean applies(int tableCount, int stride, int length, int fromIndex) {
                return false;
            }

            @Override
            int startPosition(int tableCount, int length, int fromIndex) {
                throw new UnsupportedOperationException();
            }
        },
        MATCH_AT_FROM_INDEX("match_at_fromIndex") {
            @Override
            boolean applies(int tableCount, int stride, int length, int fromIndex) {
                return fromIndex + tableCount - 1 < length;
            }

            @Override
            int startPosition(int tableCount, int length, int fromIndex) {
                return fromIndex;
            }
        },
        MATCH_BEFORE_FROM_INDEX("match_before_fromIndex") {
            @Override
            boolean applies(int tableCount, int stride, int length, int fromIndex) {
                return fromIndex > 0;
            }

            @Override
            int startPosition(int tableCount, int length, int fromIndex) {
                return fromIndex - 1;
            }
        },
        MATCH_IN_BETWEEN("match_in_between") {
            @Override
            boolean applies(int tableCount, int stride, int length, int fromIndex) {
                return fromIndex + tableCount + 1 < length;
            }

            @Override
            int startPosition(int tableCount, int length, int fromIndex) {
                return midPosition(tableCount, length, fromIndex);
            }
        },
        MATCH_AT_WINDOW_END("match_at_window_end") {
            @Override
            boolean applies(int tableCount, int stride, int length, int fromIndex) {
                return fromIndex + tableCount < length;
            }

            @Override
            int startPosition(int tableCount, int length, int fromIndex) {
                return length - tableCount;
            }
        },
        MATCH_AFTER_WINDOW("match_after_window") {
            @Override
            boolean applies(int tableCount, int stride, int length, int fromIndex) {
                return true;
            }

            @Override
            int startPosition(int tableCount, int length, int fromIndex) {
                return length;
            }
        };

        private final String displayName;

        Scenario(String displayName) {
            this.displayName = displayName;
        }

        abstract boolean applies(int tableCount, int stride, int length, int fromIndex);

        boolean injectsSequence() {
            return true;
        }

        abstract int startPosition(int tableCount, int length, int fromIndex);

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static final class CaseSpec {
        private final Scenario scenario;
        private final int explicitStartPosition;
        private final String displayName;
        private final MutationKind mutationKind;
        private final int mutationSequenceIndex;
        private final int mutationHighByteBase;

        private CaseSpec(Scenario scenario, int explicitStartPosition, String displayName, MutationKind mutationKind, int mutationSequenceIndex, int mutationHighByteBase) {
            this.scenario = scenario;
            this.explicitStartPosition = explicitStartPosition;
            this.displayName = displayName;
            this.mutationKind = mutationKind;
            this.mutationSequenceIndex = mutationSequenceIndex;
            this.mutationHighByteBase = mutationHighByteBase;
        }

        static CaseSpec ofScenario(Scenario scenario) {
            return new CaseSpec(scenario, -1, scenario.toString(), MutationKind.NONE, -1, -1);
        }

        static CaseSpec matchAtPosition(int fromIndex, int startPosition) {
            return new CaseSpec(Scenario.MATCH_AT_EXPLICIT_POSITION, startPosition, "match_at_position_" + startPosition + "_rel" + (startPosition - fromIndex), MutationKind.NONE, -1, -1);
        }

        static CaseSpec widenedLowBytesAtPosition(int fromIndex, int startPosition) {
            return new CaseSpec(Scenario.MATCH_AT_EXPLICIT_POSITION, startPosition, "widened_low_bytes_at_position_" + startPosition + "_rel" + (startPosition - fromIndex),
                            MutationKind.WIDEN_LOW_BYTES_ALL, -1, -1);
        }

        static CaseSpec widenedByteAtPosition(int fromIndex, int startPosition, int sequenceIndex, int highByteBase, String displayName) {
            return new CaseSpec(Scenario.MATCH_AT_EXPLICIT_POSITION, startPosition, displayName, MutationKind.WIDEN_SINGLE_ELEMENT, sequenceIndex, highByteBase);
        }

        static CaseSpec widenedLowBytesAtFromIndex() {
            return new CaseSpec(Scenario.MATCH_AT_FROM_INDEX, -1, "widened_low_bytes_at_fromIndex", MutationKind.WIDEN_LOW_BYTES_ALL, -1, -1);
        }

        static CaseSpec widenedByteAtFromIndex(int sequenceIndex, int highByteBase, String displayName) {
            return new CaseSpec(Scenario.MATCH_AT_FROM_INDEX, -1, displayName, MutationKind.WIDEN_SINGLE_ELEMENT, sequenceIndex, highByteBase);
        }

        static CaseSpec nonMatchAtFromIndex(int sequenceIndex, String displayName) {
            return new CaseSpec(Scenario.MATCH_AT_FROM_INDEX, -1, displayName, MutationKind.NON_MATCH_SINGLE_ELEMENT, sequenceIndex, -1);
        }

        boolean injectsSequence() {
            return scenario.injectsSequence();
        }

        int startPosition(int tableCount, int length, int fromIndex) {
            return explicitStartPosition >= 0 ? explicitStartPosition : scenario.startPosition(tableCount, length, fromIndex);
        }

        int valueAt(byte[] sequence, int stride, int sequenceIndex) {
            return switch (mutationKind) {
                case NONE -> Byte.toUnsignedInt(sequence[sequenceIndex]);
                case WIDEN_LOW_BYTES_ALL -> widenValue(sequence[sequenceIndex], stride, widenedLowBytesHighByteBase(sequenceIndex));
                case WIDEN_SINGLE_ELEMENT -> sequenceIndex == mutationSequenceIndex
                                ? widenValue(sequence[sequenceIndex], stride, mutationHighByteBase)
                                : Byte.toUnsignedInt(sequence[sequenceIndex]);
                case NON_MATCH_SINGLE_ELEMENT -> sequenceIndex == mutationSequenceIndex
                                ? Byte.toUnsignedInt(NON_MATCH_BYTE)
                                : Byte.toUnsignedInt(sequence[sequenceIndex]);
            };
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static final class TableCase {
        private final String name;
        private final String[] sequenceLabels;
        private final byte[][] sequences;
        private final byte[] tables;

        private TableCase(String name, String[] sequenceLabels, byte[][] sequences) {
            this.name = name;
            this.sequenceLabels = sequenceLabels;
            this.sequences = sequences;
            byte[][] tableSlices = new byte[sequenceLength()][];
            for (int i = 0; i < tableSlices.length; i++) {
                tableSlices[i] = buildTable(sequences, i);
            }
            this.tables = concat(tableSlices);
        }

        byte[] tables() {
            return tables;
        }

        byte[] sequence(int index) {
            return sequences[index];
        }

        String sequenceLabel(int index) {
            return sequenceLabels[index];
        }

        int sequenceCount() {
            return sequences.length;
        }

        int sequenceLength() {
            return sequences[0].length;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
