/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

import org.graalvm.shadowed.com.ibm.icu.lang.UCharacter;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Range;
import com.oracle.truffle.regex.charset.RangesBuffer;
import com.oracle.truffle.regex.charset.SortedListOfRanges;
import com.oracle.truffle.regex.tregex.string.Encodings;

public class CaseFoldData {

    private static final int INTEGER_OFFSET = 1;
    private static final int DIRECT_MAPPING = 2;
    private static final int ALTERNATING_UL = 3;
    private static final int ALTERNATING_AL = 4;
    private static final int DIRECT_SINGLE = 5;

    public enum CaseFoldUnfoldAlgorithm {
        Ascii,
        ECMAScriptNonUnicode,
        ECMAScriptUnicode,
        JavaUnicode,
        OracleDBSimple,
        PythonUnicode;

        public BiPredicate<Integer, Integer> getEqualsPredicate() {
            return (codePointA, codePointB) -> getTable(this).equalsIgnoreCase(codePointA, codePointB);
        }
    }

    public enum CaseFoldAlgorithm {
        Ruby,
        OracleDB,
        OracleDBSimple,
        OracleDBAI
    }

    private static CaseFoldEquivalenceTable getTable(CaseFoldUnfoldAlgorithm algorithm) {
        switch (algorithm) {
            case ECMAScriptNonUnicode:
                return UNICODE_15_1_0_JS;
            case ECMAScriptUnicode:
                return UNICODE_15_1_0_SIMPLE;
            case Ascii:
                return ASCII;
            case JavaUnicode:
                // Currently supported JDK versions for the Java flavor are 21, 22 and 23, where 21
                // uses Unicode version 15.0.0 and the other versions use Unicode 15.1.0. There are
                // no differences in the case folding table between those two Unicode versions, so
                // we can use the same table on all supported JDK versions for now.
                return UNICODE_15_0_0_JAVA;
            case OracleDBSimple:
                return UNICODE_15_0_0_ODB_SIMPLE_EQ;
            case PythonUnicode:
                return UNICODE_15_1_0_PY;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public static CaseFoldTable getTable(CaseFoldAlgorithm algorithm) {
        switch (algorithm) {
            case Ruby:
                return UNICODE_15_1_0_FULL;
            case OracleDB:
                return UNICODE_15_0_0_ODB_FULL;
            case OracleDBSimple:
                return UNICODE_15_0_0_ODB_SIMPLE;
            case OracleDBAI:
                return UNICODE_15_0_0_ODB_AI;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public static CaseUnfoldingTrie getUnfoldingTrie(CaseFoldAlgorithm algorithm) {
        switch (algorithm) {
            case Ruby:
                return UNFOLDING_TRIE_RUBY;
            case OracleDB:
                return UNFOLDING_TRIE_ORACLE_DB;
            case OracleDBAI:
                return UNFOLDING_TRIE_ORACLE_DB_AI;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public static String icuSimpleCaseFold(String string) {
        int[] folded = string.codePoints().map(CaseFoldData::icuSimpleCaseFold).toArray();
        return new String(folded, 0, folded.length);
    }

    public static int icuSimpleCaseFold(int codePoint) {
        return UCharacter.foldCase(codePoint, UCharacter.FOLD_CASE_DEFAULT);
    }

    static CodePointSet rangeSet(int... ranges) {
        return CodePointSet.createNoDedup(ranges);
    }

    public static void applyCaseFoldUnfold(CodePointSetAccumulator codePointSet, CodePointSetAccumulator tmp, CaseFoldUnfoldAlgorithm algorithm) {
        codePointSet.copyTo(tmp);
        getTable(algorithm).applyCaseFold(codePointSet, tmp);
    }

    public static CodePointSet simpleCaseFold(CodePointSet codePointSet, CodePointSetAccumulator tmp) {
        tmp.addSet(codePointSet);
        UNICODE_15_1_0_SIMPLE.applyCaseFold(tmp, codePointSet);
        tmp.intersectWith(FOLDED_CHARACTERS);
        return tmp.toCodePointSet();
    }

    /**
     * Maps characters to their respective set of equivalent characters in case-insensitive context,
     * e.g. {@code A -> [Aa]}.
     */
    public static final class CaseFoldEquivalenceTable implements SortedListOfRanges {

        private final CaseFoldEquivalenceTable parent;
        private final CodePointSet[] directMappings;
        private final int[] ranges;

        CaseFoldEquivalenceTable(CaseFoldEquivalenceTable parent, CodePointSet[] directMappings, int[] ranges) {
            this.parent = parent;
            this.directMappings = directMappings;
            this.ranges = ranges;
        }

        void applyCaseFold(CodePointSetAccumulator dst, Iterable<Range> src) {
            for (Range r : src) {
                applyCaseFold(dst, r);
            }
        }

        private void applyCaseFold(CodePointSetAccumulator dst, Range r) {
            int search = binarySearch(r.lo);
            if (binarySearchExactMatch(search, r.lo, r.hi)) {
                apply(dst, search, r.lo, r.hi);
                return;
            }
            int firstIntersection = binarySearchGetFirstIntersecting(search, r.lo, r.hi);
            if (binarySearchNoIntersectingFound(firstIntersection)) {
                if (parent != null) {
                    parent.applyCaseFold(dst, r);
                }
                return;
            }
            int lastIntersectionHi = r.lo - 1;
            for (int j = firstIntersection; j < size(); j++) {
                if (rightOf(j, r.lo, r.hi)) {
                    break;
                }
                assert intersects(j, r.lo, r.hi);
                int intersectionLo = Math.max(getLo(j), r.lo);
                int intersectionHi = Math.min(getHi(j), r.hi);
                apply(dst, j, intersectionLo, intersectionHi);
                if (parent != null && intersectionLo > lastIntersectionHi + 1) {
                    parent.applyCaseFold(dst, new Range(lastIntersectionHi + 1, intersectionLo - 1));
                }
                lastIntersectionHi = intersectionHi;
            }
            if (parent != null && r.hi > lastIntersectionHi) {
                parent.applyCaseFold(dst, new Range(lastIntersectionHi + 1, r.hi));
            }
        }

        private void apply(CodePointSetAccumulator codePointSet, int tblEntryIndex, int intersectionLo, int intersectionHi) {
            switch (ranges[tblEntryIndex * 4 + 2]) {
                case INTEGER_OFFSET:
                    int delta = ranges[tblEntryIndex * 4 + 3];
                    addRange(codePointSet, intersectionLo + delta, intersectionHi + delta);
                    break;
                case DIRECT_MAPPING:
                    CodePointSet set = directMappings[ranges[tblEntryIndex * 4 + 3]];
                    assert set.getMax() <= Character.MAX_CODE_POINT : "CaseFoldEquivalenceTable is currently used for single-character mappings only";
                    codePointSet.addSet(set);
                    break;
                case ALTERNATING_UL:
                    int loUL = Math.min(((intersectionLo - 1) ^ 1) + 1, ((intersectionHi - 1) ^ 1) + 1);
                    int hiUL = Math.max(((intersectionLo - 1) ^ 1) + 1, ((intersectionHi - 1) ^ 1) + 1);
                    if (!SortedListOfRanges.contains(intersectionLo, intersectionHi, loUL, hiUL)) {
                        addRange(codePointSet, loUL, hiUL);
                    }
                    break;
                case ALTERNATING_AL:
                    int loAL = Math.min(intersectionLo ^ 1, intersectionHi ^ 1);
                    int hiAL = Math.max(intersectionLo ^ 1, intersectionHi ^ 1);
                    if (!SortedListOfRanges.contains(intersectionLo, intersectionHi, loAL, hiAL)) {
                        addRange(codePointSet, loAL, hiAL);
                    }
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static void addRange(CodePointSetAccumulator codePointSet, int lo, int hi) {
            assert lo <= Character.MAX_CODE_POINT : "CaseFoldEquivalenceTable is currently used for single-character mappings only";
            codePointSet.addRange(lo, hi);
        }

        boolean equalsIgnoreCase(int codePointA, int codePointB) {
            if (codePointA == codePointB) {
                return true;
            }
            int search = binarySearch(codePointA);
            if (binarySearchExactMatch(search, codePointA, codePointA)) {
                return equalsIgnoreCase(search, codePointA, codePointB);
            }
            int firstIntersection = binarySearchGetFirstIntersecting(search, codePointA, codePointA);
            if (binarySearchNoIntersectingFound(firstIntersection) || rightOf(firstIntersection, codePointA, codePointA)) {
                return parent != null && parent.equalsIgnoreCase(codePointA, codePointB);
            }
            assert intersects(firstIntersection, codePointA, codePointA);
            return equalsIgnoreCase(firstIntersection, codePointA, codePointB);
        }

        private boolean equalsIgnoreCase(int tblEntryIndex, int codePointA, int codePointB) {
            switch (ranges[tblEntryIndex * 4 + 2]) {
                case INTEGER_OFFSET:
                    int delta = ranges[tblEntryIndex * 4 + 3];
                    return codePointA + delta == codePointB;
                case DIRECT_MAPPING:
                    CodePointSet set = directMappings[ranges[tblEntryIndex * 4 + 3]];
                    return set.contains(codePointB);
                case ALTERNATING_UL:
                    return ((codePointA - 1) ^ 1) + 1 == codePointB;
                case ALTERNATING_AL:
                    return (codePointA ^ 1) == codePointB;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Override
        public int getLo(int i) {
            return ranges[i * 4];
        }

        @Override
        public int getHi(int i) {
            return ranges[i * 4 + 1];
        }

        @Override
        public int size() {
            return ranges.length / 4;
        }

        @Override
        public void appendRangesTo(RangesBuffer buffer, int startIndex, int endIndex) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public static final class CaseFoldTable implements SortedListOfRanges {

        private final CaseFoldTable parent;
        private final int[] ranges;

        CaseFoldTable(CaseFoldTable parent, int[] ranges) {
            this.parent = parent;
            this.ranges = ranges;
        }

        public int[] caseFold(int codepoint) {
            final int[][] ret = new int[1][];
            caseFold(new Range(codepoint, codepoint), (cp, caseFolded) -> ret[0] = caseFolded);
            return ret[0];
        }

        public void caseFold(CodePointSetAccumulator cps, BiConsumer<Integer, int[]> caseFoldItem) {
            for (Range r : cps) {
                caseFold(r, caseFoldItem);
            }
        }

        public void caseFold(Range r, BiConsumer<Integer, int[]> caseFoldItem) {
            int search = binarySearch(r.lo);
            if (binarySearchExactMatch(search, r.lo, r.hi)) {
                apply(search, r.lo, r.hi, caseFoldItem);
                return;
            }
            int firstIntersection = binarySearchGetFirstIntersecting(search, r.lo, r.hi);
            if (binarySearchNoIntersectingFound(firstIntersection)) {
                if (parent != null) {
                    parent.caseFold(r, caseFoldItem);
                }
                return;
            }
            int lastIntersectionHi = r.lo - 1;
            for (int j = firstIntersection; j < size(); j++) {
                if (rightOf(j, r.lo, r.hi)) {
                    break;
                }
                assert intersects(j, r.lo, r.hi);
                int intersectionLo = Math.max(getLo(j), r.lo);
                int intersectionHi = Math.min(getHi(j), r.hi);
                apply(j, intersectionLo, intersectionHi, caseFoldItem);
                if (parent != null && intersectionLo > lastIntersectionHi + 1) {
                    parent.caseFold(new Range(lastIntersectionHi + 1, intersectionLo - 1), caseFoldItem);
                }
                lastIntersectionHi = intersectionHi;
            }
            if (parent != null && r.hi > lastIntersectionHi) {
                parent.caseFold(new Range(lastIntersectionHi + 1, r.hi), caseFoldItem);
            }
        }

        private void apply(int tblEntryIndex, int intersectionLo, int intersectionHi, BiConsumer<Integer, int[]> caseFoldItem) {
            int kind = ranges[tblEntryIndex * 4 + 2];
            switch (kind) {
                case INTEGER_OFFSET:
                    int delta = ranges[tblEntryIndex * 4 + 3];
                    if (delta != 0) {
                        for (int i = intersectionLo; i <= intersectionHi; i++) {
                            applyMapping(i, i + delta, caseFoldItem);
                        }
                    }
                    break;
                case ALTERNATING_AL, ALTERNATING_UL:
                    int loUL = kind == ALTERNATING_UL ? intersectionLo | 1 : intersectionLo + (intersectionLo & 1);
                    for (int i = loUL; i <= intersectionHi; i += 2) {
                        applyMapping(i, i + 1, caseFoldItem);
                    }
                    break;
                case DIRECT_SINGLE:
                    int dst = ranges[tblEntryIndex * 4 + 3];
                    for (int i = intersectionLo; i <= intersectionHi; i++) {
                        applyMapping(i, dst, caseFoldItem);
                    }
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static void applyMapping(int from, int to, BiConsumer<Integer, int[]> caseFoldItem) {
            assert from <= 0x10_ffff;
            caseFoldItem.accept(from, mappingToCodepoints(to));
        }

        private static int[] mappingToCodepoints(int mapping) {
            if (mapping > 0x10_ffff) {
                return MULTI_CHAR_SEQUENCES[mapping - 0x11_0000].codePoints().toArray();
            } else {
                return new int[]{mapping};
            }
        }

        private CaseUnfoldingTrie createCaseUnfoldTrie() {
            CaseUnfoldingTrie trie = new CaseUnfoldingTrie(0);
            if (parent == null) {
                for (int i = 0; i < ranges.length; i += 4) {
                    switch (ranges[i + 2]) {
                        case INTEGER_OFFSET -> {
                            for (int j = ranges[i]; j <= ranges[i + 1]; j++) {
                                trie.add(j, mappingToCodepoints(j + ranges[i + 3]), 0);
                            }
                        }
                        case ALTERNATING_UL, ALTERNATING_AL -> {
                            for (int j = ranges[i]; j <= ranges[i + 1]; j += 2) {
                                trie.add(j, mappingToCodepoints(j + 1), 0);
                            }
                        }
                        case DIRECT_SINGLE -> {
                            for (int j = ranges[i]; j <= ranges[i + 1]; j++) {
                                trie.add(j, mappingToCodepoints(ranges[i + 3]), 0);
                            }
                        }
                        default -> throw CompilerDirectives.shouldNotReachHere();
                    }
                }
            } else {
                caseFold(new Range(0, 0x10_ffff), (from, to) -> trie.add(from, to, 0));
            }
            return trie;
        }

        @Override
        public int getLo(int i) {
            return ranges[i * 4];
        }

        @Override
        public int getHi(int i) {
            return ranges[i * 4 + 1];
        }

        @Override
        public int size() {
            return ranges.length / 4;
        }

        @Override
        public void appendRangesTo(RangesBuffer buffer, int startIndex, int endIndex) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static final CaseFoldEquivalenceTable ASCII = new CaseFoldEquivalenceTable(null, new CodePointSet[0], new int[]{
                    0x000041, 0x00005a, INTEGER_OFFSET, 32,
                    0x000061, 0x00007a, INTEGER_OFFSET, -32
    });

    /* GENERATED CODE BEGIN - KEEP THIS MARKER FOR AUTOMATIC UPDATES */

    private static final String[] MULTI_CHAR_SEQUENCES = {
                    "i\u0307",
                    "SS",
                    "FF",
                    "FI",
                    "FL",
                    "FFI",
                    "FFL",
                    "ST",
                    "\u0535\u0552",
                    "\u0544\u0546",
                    "\u0544\u0535",
                    "\u0544\u053b",
                    "\u054e\u0546",
                    "\u0544\u053d",
                    "\u02bcN",
                    "\u0399\u0308\u0301",
                    "\u03a5\u0308\u0301",
                    "J\u030c",
                    "H\u0331",
                    "T\u0308",
                    "W\u030a",
                    "Y\u030a",
                    "A\u02be",
                    "\u03a5\u0313",
                    "\u03a5\u0313\u0300",
                    "\u03a5\u0313\u0301",
                    "\u03a5\u0313\u0342",
                    "\u0391\u0342",
                    "\u0397\u0342",
                    "\u0399\u0308\u0300",
                    "\u0399\u0342",
                    "\u0399\u0308\u0342",
                    "\u03a5\u0308\u0300",
                    "\u03a1\u0313",
                    "\u03a5\u0342",
                    "\u03a5\u0308\u0342",
                    "\u03a9\u0342",
                    "\u1f08\u0399",
                    "\u1f09\u0399",
                    "\u1f0a\u0399",
                    "\u1f0b\u0399",
                    "\u1f0c\u0399",
                    "\u1f0d\u0399",
                    "\u1f0e\u0399",
                    "\u1f0f\u0399",
                    "\u1f28\u0399",
                    "\u1f29\u0399",
                    "\u1f2a\u0399",
                    "\u1f2b\u0399",
                    "\u1f2c\u0399",
                    "\u1f2d\u0399",
                    "\u1f2e\u0399",
                    "\u1f2f\u0399",
                    "\u1f68\u0399",
                    "\u1f69\u0399",
                    "\u1f6a\u0399",
                    "\u1f6b\u0399",
                    "\u1f6c\u0399",
                    "\u1f6d\u0399",
                    "\u1f6e\u0399",
                    "\u1f6f\u0399",
                    "\u0391\u0399",
                    "\u0397\u0399",
                    "\u03a9\u0399",
                    "\u1fba\u0399",
                    "\u0386\u0399",
                    "\u1fca\u0399",
                    "\u0389\u0399",
                    "\u1ffa\u0399",
                    "\u038f\u0399",
                    "\u0391\u0342\u0399",
                    "\u0397\u0342\u0399",
                    "\u03a9\u0342\u0399",
                    "ss",
                    "\u02bcn",
                    "j\u030c",
                    "\u03b9\u0308\u0301",
                    "\u03c5\u0308\u0301",
                    "\u0565\u0582",
                    "h\u0331",
                    "t\u0308",
                    "w\u030a",
                    "y\u030a",
                    "a\u02be",
                    "\u03c5\u0313",
                    "\u03c5\u0313\u0300",
                    "\u03c5\u0313\u0301",
                    "\u03c5\u0313\u0342",
                    "\u1f00\u03b9",
                    "\u1f01\u03b9",
                    "\u1f02\u03b9",
                    "\u1f03\u03b9",
                    "\u1f04\u03b9",
                    "\u1f05\u03b9",
                    "\u1f06\u03b9",
                    "\u1f07\u03b9",
                    "\u1f20\u03b9",
                    "\u1f21\u03b9",
                    "\u1f22\u03b9",
                    "\u1f23\u03b9",
                    "\u1f24\u03b9",
                    "\u1f25\u03b9",
                    "\u1f26\u03b9",
                    "\u1f27\u03b9",
                    "\u1f60\u03b9",
                    "\u1f61\u03b9",
                    "\u1f62\u03b9",
                    "\u1f63\u03b9",
                    "\u1f64\u03b9",
                    "\u1f65\u03b9",
                    "\u1f66\u03b9",
                    "\u1f67\u03b9",
                    "\u1f70\u03b9",
                    "\u03b1\u03b9",
                    "\u03ac\u03b9",
                    "\u03b1\u0342",
                    "\u03b1\u0342\u03b9",
                    "\u1f74\u03b9",
                    "\u03b7\u03b9",
                    "\u03ae\u03b9",
                    "\u03b7\u0342",
                    "\u03b7\u0342\u03b9",
                    "\u03b9\u0308\u0300",
                    "\u03b9\u0342",
                    "\u03b9\u0308\u0342",
                    "\u03c5\u0308\u0300",
                    "\u03c1\u0313",
                    "\u03c5\u0342",
                    "\u03c5\u0308\u0342",
                    "\u1f7c\u03b9",
                    "\u03c9\u03b9",
                    "\u03ce\u03b9",
                    "\u03c9\u0342",
                    "\u03c9\u0342\u03b9",
                    "ff",
                    "fi",
                    "fl",
                    "ffi",
                    "ffl",
                    "st",
                    "\u0574\u0576",
                    "\u0574\u0565",
                    "\u0574\u056b",
                    "\u057e\u0576",
                    "\u0574\u056d",
                    "ij",
                    "oe",
                    "lj",
                    "nj",
                    "dz",
                    "d\u0292",
                    "d\u0291",
                    "ts",
                    "t\u0283",
                    "t\u0255",
                    "co",
                    "no",
                    "sm",
                    "del",
                    "tm",
                    "ii",
                    "iii",
                    "iv",
                    "vi",
                    "vii",
                    "viii",
                    "ix",
                    "xi",
                    "xii",
                    "fo",
    };
    public static final int MAX_MULTI_CHAR_SEQUENCE_LENGTH = 4;
    private static final CaseFoldEquivalenceTable UNICODE_15_1_0_SIMPLE = new CaseFoldEquivalenceTable(null, new CodePointSet[]{
                    rangeSet(0x00004b, 0x00004b, 0x00006b, 0x00006b, 0x00212a, 0x00212a),
                    rangeSet(0x000053, 0x000053, 0x000073, 0x000073, 0x00017f, 0x00017f),
                    rangeSet(0x0000b5, 0x0000b5, 0x00039c, 0x00039c, 0x0003bc, 0x0003bc),
                    rangeSet(0x0000c5, 0x0000c5, 0x0000e5, 0x0000e5, 0x00212b, 0x00212b),
                    rangeSet(0x0001c4, 0x0001c6),
                    rangeSet(0x0001c7, 0x0001c9),
                    rangeSet(0x0001ca, 0x0001cc),
                    rangeSet(0x0001f1, 0x0001f3),
                    rangeSet(0x000345, 0x000345, 0x000399, 0x000399, 0x0003b9, 0x0003b9, 0x001fbe, 0x001fbe),
                    rangeSet(0x000392, 0x000392, 0x0003b2, 0x0003b2, 0x0003d0, 0x0003d0),
                    rangeSet(0x000395, 0x000395, 0x0003b5, 0x0003b5, 0x0003f5, 0x0003f5),
                    rangeSet(0x000398, 0x000398, 0x0003b8, 0x0003b8, 0x0003d1, 0x0003d1, 0x0003f4, 0x0003f4),
                    rangeSet(0x00039a, 0x00039a, 0x0003ba, 0x0003ba, 0x0003f0, 0x0003f0),
                    rangeSet(0x0003a0, 0x0003a0, 0x0003c0, 0x0003c0, 0x0003d6, 0x0003d6),
                    rangeSet(0x0003a1, 0x0003a1, 0x0003c1, 0x0003c1, 0x0003f1, 0x0003f1),
                    rangeSet(0x0003a3, 0x0003a3, 0x0003c2, 0x0003c3),
                    rangeSet(0x0003a6, 0x0003a6, 0x0003c6, 0x0003c6, 0x0003d5, 0x0003d5),
                    rangeSet(0x0003a9, 0x0003a9, 0x0003c9, 0x0003c9, 0x002126, 0x002126),
                    rangeSet(0x000412, 0x000412, 0x000432, 0x000432, 0x001c80, 0x001c80),
                    rangeSet(0x000414, 0x000414, 0x000434, 0x000434, 0x001c81, 0x001c81),
                    rangeSet(0x00041e, 0x00041e, 0x00043e, 0x00043e, 0x001c82, 0x001c82),
                    rangeSet(0x000421, 0x000421, 0x000441, 0x000441, 0x001c83, 0x001c83),
                    rangeSet(0x000422, 0x000422, 0x000442, 0x000442, 0x001c84, 0x001c85),
                    rangeSet(0x00042a, 0x00042a, 0x00044a, 0x00044a, 0x001c86, 0x001c86),
                    rangeSet(0x000462, 0x000463, 0x001c87, 0x001c87),
                    rangeSet(0x001c88, 0x001c88, 0x00a64a, 0x00a64b),
                    rangeSet(0x001e60, 0x001e61, 0x001e9b, 0x001e9b),
    }, new int[]{
                    0x000041, 0x00004a, INTEGER_OFFSET, 32,
                    0x00004b, 0x00004b, DIRECT_MAPPING, 0,
                    0x00004c, 0x000052, INTEGER_OFFSET, 32,
                    0x000053, 0x000053, DIRECT_MAPPING, 1,
                    0x000054, 0x00005a, INTEGER_OFFSET, 32,
                    0x000061, 0x00006a, INTEGER_OFFSET, -32,
                    0x00006b, 0x00006b, DIRECT_MAPPING, 0,
                    0x00006c, 0x000072, INTEGER_OFFSET, -32,
                    0x000073, 0x000073, DIRECT_MAPPING, 1,
                    0x000074, 0x00007a, INTEGER_OFFSET, -32,
                    0x0000b5, 0x0000b5, DIRECT_MAPPING, 2,
                    0x0000c0, 0x0000c4, INTEGER_OFFSET, 32,
                    0x0000c5, 0x0000c5, DIRECT_MAPPING, 3,
                    0x0000c6, 0x0000d6, INTEGER_OFFSET, 32,
                    0x0000d8, 0x0000de, INTEGER_OFFSET, 32,
                    0x0000df, 0x0000df, INTEGER_OFFSET, 7615,
                    0x0000e0, 0x0000e4, INTEGER_OFFSET, -32,
                    0x0000e5, 0x0000e5, DIRECT_MAPPING, 3,
                    0x0000e6, 0x0000f6, INTEGER_OFFSET, -32,
                    0x0000f8, 0x0000fe, INTEGER_OFFSET, -32,
                    0x0000ff, 0x0000ff, INTEGER_OFFSET, 121,
                    0x000100, 0x00012f, ALTERNATING_AL, 0,
                    0x000132, 0x000137, ALTERNATING_AL, 0,
                    0x000139, 0x000148, ALTERNATING_UL, 0,
                    0x00014a, 0x000177, ALTERNATING_AL, 0,
                    0x000178, 0x000178, INTEGER_OFFSET, -121,
                    0x000179, 0x00017e, ALTERNATING_UL, 0,
                    0x00017f, 0x00017f, DIRECT_MAPPING, 1,
                    0x000180, 0x000180, INTEGER_OFFSET, 195,
                    0x000181, 0x000181, INTEGER_OFFSET, 210,
                    0x000182, 0x000185, ALTERNATING_AL, 0,
                    0x000186, 0x000186, INTEGER_OFFSET, 206,
                    0x000187, 0x000188, ALTERNATING_UL, 0,
                    0x000189, 0x00018a, INTEGER_OFFSET, 205,
                    0x00018b, 0x00018c, ALTERNATING_UL, 0,
                    0x00018e, 0x00018e, INTEGER_OFFSET, 79,
                    0x00018f, 0x00018f, INTEGER_OFFSET, 202,
                    0x000190, 0x000190, INTEGER_OFFSET, 203,
                    0x000191, 0x000192, ALTERNATING_UL, 0,
                    0x000193, 0x000193, INTEGER_OFFSET, 205,
                    0x000194, 0x000194, INTEGER_OFFSET, 207,
                    0x000195, 0x000195, INTEGER_OFFSET, 97,
                    0x000196, 0x000196, INTEGER_OFFSET, 211,
                    0x000197, 0x000197, INTEGER_OFFSET, 209,
                    0x000198, 0x000199, ALTERNATING_AL, 0,
                    0x00019a, 0x00019a, INTEGER_OFFSET, 163,
                    0x00019c, 0x00019c, INTEGER_OFFSET, 211,
                    0x00019d, 0x00019d, INTEGER_OFFSET, 213,
                    0x00019e, 0x00019e, INTEGER_OFFSET, 130,
                    0x00019f, 0x00019f, INTEGER_OFFSET, 214,
                    0x0001a0, 0x0001a5, ALTERNATING_AL, 0,
                    0x0001a6, 0x0001a6, INTEGER_OFFSET, 218,
                    0x0001a7, 0x0001a8, ALTERNATING_UL, 0,
                    0x0001a9, 0x0001a9, INTEGER_OFFSET, 218,
                    0x0001ac, 0x0001ad, ALTERNATING_AL, 0,
                    0x0001ae, 0x0001ae, INTEGER_OFFSET, 218,
                    0x0001af, 0x0001b0, ALTERNATING_UL, 0,
                    0x0001b1, 0x0001b2, INTEGER_OFFSET, 217,
                    0x0001b3, 0x0001b6, ALTERNATING_UL, 0,
                    0x0001b7, 0x0001b7, INTEGER_OFFSET, 219,
                    0x0001b8, 0x0001b9, ALTERNATING_AL, 0,
                    0x0001bc, 0x0001bd, ALTERNATING_AL, 0,
                    0x0001bf, 0x0001bf, INTEGER_OFFSET, 56,
                    0x0001c4, 0x0001c6, DIRECT_MAPPING, 4,
                    0x0001c7, 0x0001c9, DIRECT_MAPPING, 5,
                    0x0001ca, 0x0001cc, DIRECT_MAPPING, 6,
                    0x0001cd, 0x0001dc, ALTERNATING_UL, 0,
                    0x0001dd, 0x0001dd, INTEGER_OFFSET, -79,
                    0x0001de, 0x0001ef, ALTERNATING_AL, 0,
                    0x0001f1, 0x0001f3, DIRECT_MAPPING, 7,
                    0x0001f4, 0x0001f5, ALTERNATING_AL, 0,
                    0x0001f6, 0x0001f6, INTEGER_OFFSET, -97,
                    0x0001f7, 0x0001f7, INTEGER_OFFSET, -56,
                    0x0001f8, 0x00021f, ALTERNATING_AL, 0,
                    0x000220, 0x000220, INTEGER_OFFSET, -130,
                    0x000222, 0x000233, ALTERNATING_AL, 0,
                    0x00023a, 0x00023a, INTEGER_OFFSET, 10795,
                    0x00023b, 0x00023c, ALTERNATING_UL, 0,
                    0x00023d, 0x00023d, INTEGER_OFFSET, -163,
                    0x00023e, 0x00023e, INTEGER_OFFSET, 10792,
                    0x00023f, 0x000240, INTEGER_OFFSET, 10815,
                    0x000241, 0x000242, ALTERNATING_UL, 0,
                    0x000243, 0x000243, INTEGER_OFFSET, -195,
                    0x000244, 0x000244, INTEGER_OFFSET, 69,
                    0x000245, 0x000245, INTEGER_OFFSET, 71,
                    0x000246, 0x00024f, ALTERNATING_AL, 0,
                    0x000250, 0x000250, INTEGER_OFFSET, 10783,
                    0x000251, 0x000251, INTEGER_OFFSET, 10780,
                    0x000252, 0x000252, INTEGER_OFFSET, 10782,
                    0x000253, 0x000253, INTEGER_OFFSET, -210,
                    0x000254, 0x000254, INTEGER_OFFSET, -206,
                    0x000256, 0x000257, INTEGER_OFFSET, -205,
                    0x000259, 0x000259, INTEGER_OFFSET, -202,
                    0x00025b, 0x00025b, INTEGER_OFFSET, -203,
                    0x00025c, 0x00025c, INTEGER_OFFSET, 42319,
                    0x000260, 0x000260, INTEGER_OFFSET, -205,
                    0x000261, 0x000261, INTEGER_OFFSET, 42315,
                    0x000263, 0x000263, INTEGER_OFFSET, -207,
                    0x000265, 0x000265, INTEGER_OFFSET, 42280,
                    0x000266, 0x000266, INTEGER_OFFSET, 42308,
                    0x000268, 0x000268, INTEGER_OFFSET, -209,
                    0x000269, 0x000269, INTEGER_OFFSET, -211,
                    0x00026a, 0x00026a, INTEGER_OFFSET, 42308,
                    0x00026b, 0x00026b, INTEGER_OFFSET, 10743,
                    0x00026c, 0x00026c, INTEGER_OFFSET, 42305,
                    0x00026f, 0x00026f, INTEGER_OFFSET, -211,
                    0x000271, 0x000271, INTEGER_OFFSET, 10749,
                    0x000272, 0x000272, INTEGER_OFFSET, -213,
                    0x000275, 0x000275, INTEGER_OFFSET, -214,
                    0x00027d, 0x00027d, INTEGER_OFFSET, 10727,
                    0x000280, 0x000280, INTEGER_OFFSET, -218,
                    0x000282, 0x000282, INTEGER_OFFSET, 42307,
                    0x000283, 0x000283, INTEGER_OFFSET, -218,
                    0x000287, 0x000287, INTEGER_OFFSET, 42282,
                    0x000288, 0x000288, INTEGER_OFFSET, -218,
                    0x000289, 0x000289, INTEGER_OFFSET, -69,
                    0x00028a, 0x00028b, INTEGER_OFFSET, -217,
                    0x00028c, 0x00028c, INTEGER_OFFSET, -71,
                    0x000292, 0x000292, INTEGER_OFFSET, -219,
                    0x00029d, 0x00029d, INTEGER_OFFSET, 42261,
                    0x00029e, 0x00029e, INTEGER_OFFSET, 42258,
                    0x000345, 0x000345, DIRECT_MAPPING, 8,
                    0x000370, 0x000373, ALTERNATING_AL, 0,
                    0x000376, 0x000377, ALTERNATING_AL, 0,
                    0x00037b, 0x00037d, INTEGER_OFFSET, 130,
                    0x00037f, 0x00037f, INTEGER_OFFSET, 116,
                    0x000386, 0x000386, INTEGER_OFFSET, 38,
                    0x000388, 0x00038a, INTEGER_OFFSET, 37,
                    0x00038c, 0x00038c, INTEGER_OFFSET, 64,
                    0x00038e, 0x00038f, INTEGER_OFFSET, 63,
                    0x000390, 0x000390, INTEGER_OFFSET, 7235,
                    0x000391, 0x000391, INTEGER_OFFSET, 32,
                    0x000392, 0x000392, DIRECT_MAPPING, 9,
                    0x000393, 0x000394, INTEGER_OFFSET, 32,
                    0x000395, 0x000395, DIRECT_MAPPING, 10,
                    0x000396, 0x000397, INTEGER_OFFSET, 32,
                    0x000398, 0x000398, DIRECT_MAPPING, 11,
                    0x000399, 0x000399, DIRECT_MAPPING, 8,
                    0x00039a, 0x00039a, DIRECT_MAPPING, 12,
                    0x00039b, 0x00039b, INTEGER_OFFSET, 32,
                    0x00039c, 0x00039c, DIRECT_MAPPING, 2,
                    0x00039d, 0x00039f, INTEGER_OFFSET, 32,
                    0x0003a0, 0x0003a0, DIRECT_MAPPING, 13,
                    0x0003a1, 0x0003a1, DIRECT_MAPPING, 14,
                    0x0003a3, 0x0003a3, DIRECT_MAPPING, 15,
                    0x0003a4, 0x0003a5, INTEGER_OFFSET, 32,
                    0x0003a6, 0x0003a6, DIRECT_MAPPING, 16,
                    0x0003a7, 0x0003a8, INTEGER_OFFSET, 32,
                    0x0003a9, 0x0003a9, DIRECT_MAPPING, 17,
                    0x0003aa, 0x0003ab, INTEGER_OFFSET, 32,
                    0x0003ac, 0x0003ac, INTEGER_OFFSET, -38,
                    0x0003ad, 0x0003af, INTEGER_OFFSET, -37,
                    0x0003b0, 0x0003b0, INTEGER_OFFSET, 7219,
                    0x0003b1, 0x0003b1, INTEGER_OFFSET, -32,
                    0x0003b2, 0x0003b2, DIRECT_MAPPING, 9,
                    0x0003b3, 0x0003b4, INTEGER_OFFSET, -32,
                    0x0003b5, 0x0003b5, DIRECT_MAPPING, 10,
                    0x0003b6, 0x0003b7, INTEGER_OFFSET, -32,
                    0x0003b8, 0x0003b8, DIRECT_MAPPING, 11,
                    0x0003b9, 0x0003b9, DIRECT_MAPPING, 8,
                    0x0003ba, 0x0003ba, DIRECT_MAPPING, 12,
                    0x0003bb, 0x0003bb, INTEGER_OFFSET, -32,
                    0x0003bc, 0x0003bc, DIRECT_MAPPING, 2,
                    0x0003bd, 0x0003bf, INTEGER_OFFSET, -32,
                    0x0003c0, 0x0003c0, DIRECT_MAPPING, 13,
                    0x0003c1, 0x0003c1, DIRECT_MAPPING, 14,
                    0x0003c2, 0x0003c3, DIRECT_MAPPING, 15,
                    0x0003c4, 0x0003c5, INTEGER_OFFSET, -32,
                    0x0003c6, 0x0003c6, DIRECT_MAPPING, 16,
                    0x0003c7, 0x0003c8, INTEGER_OFFSET, -32,
                    0x0003c9, 0x0003c9, DIRECT_MAPPING, 17,
                    0x0003ca, 0x0003cb, INTEGER_OFFSET, -32,
                    0x0003cc, 0x0003cc, INTEGER_OFFSET, -64,
                    0x0003cd, 0x0003ce, INTEGER_OFFSET, -63,
                    0x0003cf, 0x0003cf, INTEGER_OFFSET, 8,
                    0x0003d0, 0x0003d0, DIRECT_MAPPING, 9,
                    0x0003d1, 0x0003d1, DIRECT_MAPPING, 11,
                    0x0003d5, 0x0003d5, DIRECT_MAPPING, 16,
                    0x0003d6, 0x0003d6, DIRECT_MAPPING, 13,
                    0x0003d7, 0x0003d7, INTEGER_OFFSET, -8,
                    0x0003d8, 0x0003ef, ALTERNATING_AL, 0,
                    0x0003f0, 0x0003f0, DIRECT_MAPPING, 12,
                    0x0003f1, 0x0003f1, DIRECT_MAPPING, 14,
                    0x0003f2, 0x0003f2, INTEGER_OFFSET, 7,
                    0x0003f3, 0x0003f3, INTEGER_OFFSET, -116,
                    0x0003f4, 0x0003f4, DIRECT_MAPPING, 11,
                    0x0003f5, 0x0003f5, DIRECT_MAPPING, 10,
                    0x0003f7, 0x0003f8, ALTERNATING_UL, 0,
                    0x0003f9, 0x0003f9, INTEGER_OFFSET, -7,
                    0x0003fa, 0x0003fb, ALTERNATING_AL, 0,
                    0x0003fd, 0x0003ff, INTEGER_OFFSET, -130,
                    0x000400, 0x00040f, INTEGER_OFFSET, 80,
                    0x000410, 0x000411, INTEGER_OFFSET, 32,
                    0x000412, 0x000412, DIRECT_MAPPING, 18,
                    0x000413, 0x000413, INTEGER_OFFSET, 32,
                    0x000414, 0x000414, DIRECT_MAPPING, 19,
                    0x000415, 0x00041d, INTEGER_OFFSET, 32,
                    0x00041e, 0x00041e, DIRECT_MAPPING, 20,
                    0x00041f, 0x000420, INTEGER_OFFSET, 32,
                    0x000421, 0x000421, DIRECT_MAPPING, 21,
                    0x000422, 0x000422, DIRECT_MAPPING, 22,
                    0x000423, 0x000429, INTEGER_OFFSET, 32,
                    0x00042a, 0x00042a, DIRECT_MAPPING, 23,
                    0x00042b, 0x00042f, INTEGER_OFFSET, 32,
                    0x000430, 0x000431, INTEGER_OFFSET, -32,
                    0x000432, 0x000432, DIRECT_MAPPING, 18,
                    0x000433, 0x000433, INTEGER_OFFSET, -32,
                    0x000434, 0x000434, DIRECT_MAPPING, 19,
                    0x000435, 0x00043d, INTEGER_OFFSET, -32,
                    0x00043e, 0x00043e, DIRECT_MAPPING, 20,
                    0x00043f, 0x000440, INTEGER_OFFSET, -32,
                    0x000441, 0x000441, DIRECT_MAPPING, 21,
                    0x000442, 0x000442, DIRECT_MAPPING, 22,
                    0x000443, 0x000449, INTEGER_OFFSET, -32,
                    0x00044a, 0x00044a, DIRECT_MAPPING, 23,
                    0x00044b, 0x00044f, INTEGER_OFFSET, -32,
                    0x000450, 0x00045f, INTEGER_OFFSET, -80,
                    0x000460, 0x000461, ALTERNATING_AL, 0,
                    0x000462, 0x000463, DIRECT_MAPPING, 24,
                    0x000464, 0x000481, ALTERNATING_AL, 0,
                    0x00048a, 0x0004bf, ALTERNATING_AL, 0,
                    0x0004c0, 0x0004c0, INTEGER_OFFSET, 15,
                    0x0004c1, 0x0004ce, ALTERNATING_UL, 0,
                    0x0004cf, 0x0004cf, INTEGER_OFFSET, -15,
                    0x0004d0, 0x00052f, ALTERNATING_AL, 0,
                    0x000531, 0x000556, INTEGER_OFFSET, 48,
                    0x000561, 0x000586, INTEGER_OFFSET, -48,
                    0x0010a0, 0x0010c5, INTEGER_OFFSET, 7264,
                    0x0010c7, 0x0010c7, INTEGER_OFFSET, 7264,
                    0x0010cd, 0x0010cd, INTEGER_OFFSET, 7264,
                    0x0010d0, 0x0010fa, INTEGER_OFFSET, 3008,
                    0x0010fd, 0x0010ff, INTEGER_OFFSET, 3008,
                    0x0013a0, 0x0013ef, INTEGER_OFFSET, 38864,
                    0x0013f0, 0x0013f5, INTEGER_OFFSET, 8,
                    0x0013f8, 0x0013fd, INTEGER_OFFSET, -8,
                    0x001c80, 0x001c80, DIRECT_MAPPING, 18,
                    0x001c81, 0x001c81, DIRECT_MAPPING, 19,
                    0x001c82, 0x001c82, DIRECT_MAPPING, 20,
                    0x001c83, 0x001c83, DIRECT_MAPPING, 21,
                    0x001c84, 0x001c85, DIRECT_MAPPING, 22,
                    0x001c86, 0x001c86, DIRECT_MAPPING, 23,
                    0x001c87, 0x001c87, DIRECT_MAPPING, 24,
                    0x001c88, 0x001c88, DIRECT_MAPPING, 25,
                    0x001c90, 0x001cba, INTEGER_OFFSET, -3008,
                    0x001cbd, 0x001cbf, INTEGER_OFFSET, -3008,
                    0x001d79, 0x001d79, INTEGER_OFFSET, 35332,
                    0x001d7d, 0x001d7d, INTEGER_OFFSET, 3814,
                    0x001d8e, 0x001d8e, INTEGER_OFFSET, 35384,
                    0x001e00, 0x001e5f, ALTERNATING_AL, 0,
                    0x001e60, 0x001e61, DIRECT_MAPPING, 26,
                    0x001e62, 0x001e95, ALTERNATING_AL, 0,
                    0x001e9b, 0x001e9b, DIRECT_MAPPING, 26,
                    0x001e9e, 0x001e9e, INTEGER_OFFSET, -7615,
                    0x001ea0, 0x001eff, ALTERNATING_AL, 0,
                    0x001f00, 0x001f07, INTEGER_OFFSET, 8,
                    0x001f08, 0x001f0f, INTEGER_OFFSET, -8,
                    0x001f10, 0x001f15, INTEGER_OFFSET, 8,
                    0x001f18, 0x001f1d, INTEGER_OFFSET, -8,
                    0x001f20, 0x001f27, INTEGER_OFFSET, 8,
                    0x001f28, 0x001f2f, INTEGER_OFFSET, -8,
                    0x001f30, 0x001f37, INTEGER_OFFSET, 8,
                    0x001f38, 0x001f3f, INTEGER_OFFSET, -8,
                    0x001f40, 0x001f45, INTEGER_OFFSET, 8,
                    0x001f48, 0x001f4d, INTEGER_OFFSET, -8,
                    0x001f51, 0x001f51, INTEGER_OFFSET, 8,
                    0x001f53, 0x001f53, INTEGER_OFFSET, 8,
                    0x001f55, 0x001f55, INTEGER_OFFSET, 8,
                    0x001f57, 0x001f57, INTEGER_OFFSET, 8,
                    0x001f59, 0x001f59, INTEGER_OFFSET, -8,
                    0x001f5b, 0x001f5b, INTEGER_OFFSET, -8,
                    0x001f5d, 0x001f5d, INTEGER_OFFSET, -8,
                    0x001f5f, 0x001f5f, INTEGER_OFFSET, -8,
                    0x001f60, 0x001f67, INTEGER_OFFSET, 8,
                    0x001f68, 0x001f6f, INTEGER_OFFSET, -8,
                    0x001f70, 0x001f71, INTEGER_OFFSET, 74,
                    0x001f72, 0x001f75, INTEGER_OFFSET, 86,
                    0x001f76, 0x001f77, INTEGER_OFFSET, 100,
                    0x001f78, 0x001f79, INTEGER_OFFSET, 128,
                    0x001f7a, 0x001f7b, INTEGER_OFFSET, 112,
                    0x001f7c, 0x001f7d, INTEGER_OFFSET, 126,
                    0x001f80, 0x001f87, INTEGER_OFFSET, 8,
                    0x001f88, 0x001f8f, INTEGER_OFFSET, -8,
                    0x001f90, 0x001f97, INTEGER_OFFSET, 8,
                    0x001f98, 0x001f9f, INTEGER_OFFSET, -8,
                    0x001fa0, 0x001fa7, INTEGER_OFFSET, 8,
                    0x001fa8, 0x001faf, INTEGER_OFFSET, -8,
                    0x001fb0, 0x001fb1, INTEGER_OFFSET, 8,
                    0x001fb3, 0x001fb3, INTEGER_OFFSET, 9,
                    0x001fb8, 0x001fb9, INTEGER_OFFSET, -8,
                    0x001fba, 0x001fbb, INTEGER_OFFSET, -74,
                    0x001fbc, 0x001fbc, INTEGER_OFFSET, -9,
                    0x001fbe, 0x001fbe, DIRECT_MAPPING, 8,
                    0x001fc3, 0x001fc3, INTEGER_OFFSET, 9,
                    0x001fc8, 0x001fcb, INTEGER_OFFSET, -86,
                    0x001fcc, 0x001fcc, INTEGER_OFFSET, -9,
                    0x001fd0, 0x001fd1, INTEGER_OFFSET, 8,
                    0x001fd3, 0x001fd3, INTEGER_OFFSET, -7235,
                    0x001fd8, 0x001fd9, INTEGER_OFFSET, -8,
                    0x001fda, 0x001fdb, INTEGER_OFFSET, -100,
                    0x001fe0, 0x001fe1, INTEGER_OFFSET, 8,
                    0x001fe3, 0x001fe3, INTEGER_OFFSET, -7219,
                    0x001fe5, 0x001fe5, INTEGER_OFFSET, 7,
                    0x001fe8, 0x001fe9, INTEGER_OFFSET, -8,
                    0x001fea, 0x001feb, INTEGER_OFFSET, -112,
                    0x001fec, 0x001fec, INTEGER_OFFSET, -7,
                    0x001ff3, 0x001ff3, INTEGER_OFFSET, 9,
                    0x001ff8, 0x001ff9, INTEGER_OFFSET, -128,
                    0x001ffa, 0x001ffb, INTEGER_OFFSET, -126,
                    0x001ffc, 0x001ffc, INTEGER_OFFSET, -9,
                    0x002126, 0x002126, DIRECT_MAPPING, 17,
                    0x00212a, 0x00212a, DIRECT_MAPPING, 0,
                    0x00212b, 0x00212b, DIRECT_MAPPING, 3,
                    0x002132, 0x002132, INTEGER_OFFSET, 28,
                    0x00214e, 0x00214e, INTEGER_OFFSET, -28,
                    0x002160, 0x00216f, INTEGER_OFFSET, 16,
                    0x002170, 0x00217f, INTEGER_OFFSET, -16,
                    0x002183, 0x002184, ALTERNATING_UL, 0,
                    0x0024b6, 0x0024cf, INTEGER_OFFSET, 26,
                    0x0024d0, 0x0024e9, INTEGER_OFFSET, -26,
                    0x002c00, 0x002c2f, INTEGER_OFFSET, 48,
                    0x002c30, 0x002c5f, INTEGER_OFFSET, -48,
                    0x002c60, 0x002c61, ALTERNATING_AL, 0,
                    0x002c62, 0x002c62, INTEGER_OFFSET, -10743,
                    0x002c63, 0x002c63, INTEGER_OFFSET, -3814,
                    0x002c64, 0x002c64, INTEGER_OFFSET, -10727,
                    0x002c65, 0x002c65, INTEGER_OFFSET, -10795,
                    0x002c66, 0x002c66, INTEGER_OFFSET, -10792,
                    0x002c67, 0x002c6c, ALTERNATING_UL, 0,
                    0x002c6d, 0x002c6d, INTEGER_OFFSET, -10780,
                    0x002c6e, 0x002c6e, INTEGER_OFFSET, -10749,
                    0x002c6f, 0x002c6f, INTEGER_OFFSET, -10783,
                    0x002c70, 0x002c70, INTEGER_OFFSET, -10782,
                    0x002c72, 0x002c73, ALTERNATING_AL, 0,
                    0x002c75, 0x002c76, ALTERNATING_UL, 0,
                    0x002c7e, 0x002c7f, INTEGER_OFFSET, -10815,
                    0x002c80, 0x002ce3, ALTERNATING_AL, 0,
                    0x002ceb, 0x002cee, ALTERNATING_UL, 0,
                    0x002cf2, 0x002cf3, ALTERNATING_AL, 0,
                    0x002d00, 0x002d25, INTEGER_OFFSET, -7264,
                    0x002d27, 0x002d27, INTEGER_OFFSET, -7264,
                    0x002d2d, 0x002d2d, INTEGER_OFFSET, -7264,
                    0x00a640, 0x00a649, ALTERNATING_AL, 0,
                    0x00a64a, 0x00a64b, DIRECT_MAPPING, 25,
                    0x00a64c, 0x00a66d, ALTERNATING_AL, 0,
                    0x00a680, 0x00a69b, ALTERNATING_AL, 0,
                    0x00a722, 0x00a72f, ALTERNATING_AL, 0,
                    0x00a732, 0x00a76f, ALTERNATING_AL, 0,
                    0x00a779, 0x00a77c, ALTERNATING_UL, 0,
                    0x00a77d, 0x00a77d, INTEGER_OFFSET, -35332,
                    0x00a77e, 0x00a787, ALTERNATING_AL, 0,
                    0x00a78b, 0x00a78c, ALTERNATING_UL, 0,
                    0x00a78d, 0x00a78d, INTEGER_OFFSET, -42280,
                    0x00a790, 0x00a793, ALTERNATING_AL, 0,
                    0x00a794, 0x00a794, INTEGER_OFFSET, 48,
                    0x00a796, 0x00a7a9, ALTERNATING_AL, 0,
                    0x00a7aa, 0x00a7aa, INTEGER_OFFSET, -42308,
                    0x00a7ab, 0x00a7ab, INTEGER_OFFSET, -42319,
                    0x00a7ac, 0x00a7ac, INTEGER_OFFSET, -42315,
                    0x00a7ad, 0x00a7ad, INTEGER_OFFSET, -42305,
                    0x00a7ae, 0x00a7ae, INTEGER_OFFSET, -42308,
                    0x00a7b0, 0x00a7b0, INTEGER_OFFSET, -42258,
                    0x00a7b1, 0x00a7b1, INTEGER_OFFSET, -42282,
                    0x00a7b2, 0x00a7b2, INTEGER_OFFSET, -42261,
                    0x00a7b3, 0x00a7b3, INTEGER_OFFSET, 928,
                    0x00a7b4, 0x00a7c3, ALTERNATING_AL, 0,
                    0x00a7c4, 0x00a7c4, INTEGER_OFFSET, -48,
                    0x00a7c5, 0x00a7c5, INTEGER_OFFSET, -42307,
                    0x00a7c6, 0x00a7c6, INTEGER_OFFSET, -35384,
                    0x00a7c7, 0x00a7ca, ALTERNATING_UL, 0,
                    0x00a7d0, 0x00a7d1, ALTERNATING_AL, 0,
                    0x00a7d6, 0x00a7d9, ALTERNATING_AL, 0,
                    0x00a7f5, 0x00a7f6, ALTERNATING_UL, 0,
                    0x00ab53, 0x00ab53, INTEGER_OFFSET, -928,
                    0x00ab70, 0x00abbf, INTEGER_OFFSET, -38864,
                    0x00fb05, 0x00fb06, ALTERNATING_UL, 0,
                    0x00ff21, 0x00ff3a, INTEGER_OFFSET, 32,
                    0x00ff41, 0x00ff5a, INTEGER_OFFSET, -32,
                    0x010400, 0x010427, INTEGER_OFFSET, 40,
                    0x010428, 0x01044f, INTEGER_OFFSET, -40,
                    0x0104b0, 0x0104d3, INTEGER_OFFSET, 40,
                    0x0104d8, 0x0104fb, INTEGER_OFFSET, -40,
                    0x010570, 0x01057a, INTEGER_OFFSET, 39,
                    0x01057c, 0x01058a, INTEGER_OFFSET, 39,
                    0x01058c, 0x010592, INTEGER_OFFSET, 39,
                    0x010594, 0x010595, INTEGER_OFFSET, 39,
                    0x010597, 0x0105a1, INTEGER_OFFSET, -39,
                    0x0105a3, 0x0105b1, INTEGER_OFFSET, -39,
                    0x0105b3, 0x0105b9, INTEGER_OFFSET, -39,
                    0x0105bb, 0x0105bc, INTEGER_OFFSET, -39,
                    0x010c80, 0x010cb2, INTEGER_OFFSET, 64,
                    0x010cc0, 0x010cf2, INTEGER_OFFSET, -64,
                    0x0118a0, 0x0118bf, INTEGER_OFFSET, 32,
                    0x0118c0, 0x0118df, INTEGER_OFFSET, -32,
                    0x016e40, 0x016e5f, INTEGER_OFFSET, 32,
                    0x016e60, 0x016e7f, INTEGER_OFFSET, -32,
                    0x01e900, 0x01e921, INTEGER_OFFSET, 34,
                    0x01e922, 0x01e943, INTEGER_OFFSET, -34,
    });
    private static final CaseFoldEquivalenceTable UNICODE_15_1_0_JS = new CaseFoldEquivalenceTable(UNICODE_15_1_0_SIMPLE, new CodePointSet[]{
                    rangeSet(0x000398, 0x000398, 0x0003b8, 0x0003b8, 0x0003d1, 0x0003d1),
    }, new int[]{
                    0x00004b, 0x00005a, INTEGER_OFFSET, 32,
                    0x00006b, 0x00007a, INTEGER_OFFSET, -32,
                    0x0000c5, 0x0000d6, INTEGER_OFFSET, 32,
                    0x0000df, 0x0000df, INTEGER_OFFSET, 0,
                    0x0000e5, 0x0000f6, INTEGER_OFFSET, -32,
                    0x00017f, 0x00017f, INTEGER_OFFSET, 0,
                    0x000390, 0x000390, INTEGER_OFFSET, 0,
                    0x000398, 0x000398, DIRECT_MAPPING, 0,
                    0x0003a9, 0x0003ab, INTEGER_OFFSET, 32,
                    0x0003b0, 0x0003b0, INTEGER_OFFSET, 0,
                    0x0003b8, 0x0003b8, DIRECT_MAPPING, 0,
                    0x0003c9, 0x0003cb, INTEGER_OFFSET, -32,
                    0x0003d1, 0x0003d1, DIRECT_MAPPING, 0,
                    0x0003f4, 0x0003f4, INTEGER_OFFSET, 0,
                    0x001e9e, 0x001e9e, INTEGER_OFFSET, 0,
                    0x001f80, 0x001f87, INTEGER_OFFSET, 0,
                    0x001f88, 0x001f8f, INTEGER_OFFSET, 0,
                    0x001f90, 0x001f97, INTEGER_OFFSET, 0,
                    0x001f98, 0x001f9f, INTEGER_OFFSET, 0,
                    0x001fa0, 0x001fa7, INTEGER_OFFSET, 0,
                    0x001fa8, 0x001faf, INTEGER_OFFSET, 0,
                    0x001fb3, 0x001fb3, INTEGER_OFFSET, 0,
                    0x001fbc, 0x001fbc, INTEGER_OFFSET, 0,
                    0x001fc3, 0x001fc3, INTEGER_OFFSET, 0,
                    0x001fcc, 0x001fcc, INTEGER_OFFSET, 0,
                    0x001fd3, 0x001fd3, INTEGER_OFFSET, 0,
                    0x001fe3, 0x001fe3, INTEGER_OFFSET, 0,
                    0x001ff3, 0x001ff3, INTEGER_OFFSET, 0,
                    0x001ffc, 0x001ffc, INTEGER_OFFSET, 0,
                    0x002126, 0x002126, INTEGER_OFFSET, 0,
                    0x00212a, 0x00212a, INTEGER_OFFSET, 0,
                    0x00212b, 0x00212b, INTEGER_OFFSET, 0,
                    0x00fb05, 0x00fb06, INTEGER_OFFSET, 0,
                    0x010400, 0x010427, INTEGER_OFFSET, 0,
                    0x010428, 0x01044f, INTEGER_OFFSET, 0,
                    0x0104b0, 0x0104d3, INTEGER_OFFSET, 0,
                    0x0104d8, 0x0104fb, INTEGER_OFFSET, 0,
                    0x010570, 0x01057a, INTEGER_OFFSET, 0,
                    0x01057c, 0x01058a, INTEGER_OFFSET, 0,
                    0x01058c, 0x010592, INTEGER_OFFSET, 0,
                    0x010594, 0x010595, INTEGER_OFFSET, 0,
                    0x010597, 0x0105a1, INTEGER_OFFSET, 0,
                    0x0105a3, 0x0105b1, INTEGER_OFFSET, 0,
                    0x0105b3, 0x0105b9, INTEGER_OFFSET, 0,
                    0x0105bb, 0x0105bc, INTEGER_OFFSET, 0,
                    0x010c80, 0x010cb2, INTEGER_OFFSET, 0,
                    0x010cc0, 0x010cf2, INTEGER_OFFSET, 0,
                    0x0118a0, 0x0118bf, INTEGER_OFFSET, 0,
                    0x0118c0, 0x0118df, INTEGER_OFFSET, 0,
                    0x016e40, 0x016e5f, INTEGER_OFFSET, 0,
                    0x016e60, 0x016e7f, INTEGER_OFFSET, 0,
                    0x01e900, 0x01e921, INTEGER_OFFSET, 0,
                    0x01e922, 0x01e943, INTEGER_OFFSET, 0,
    });
    private static final CaseFoldEquivalenceTable UNICODE_15_1_0_PY = new CaseFoldEquivalenceTable(UNICODE_15_1_0_SIMPLE, new CodePointSet[]{
                    rangeSet(0x000049, 0x000049, 0x000069, 0x000069, 0x000130, 0x000131),
    }, new int[]{
                    0x000049, 0x000049, DIRECT_MAPPING, 0,
                    0x000069, 0x000069, DIRECT_MAPPING, 0,
                    0x000130, 0x000131, DIRECT_MAPPING, 0,
    });
    private static final CaseFoldEquivalenceTable UNICODE_15_0_0_JAVA = new CaseFoldEquivalenceTable(UNICODE_15_1_0_PY, new CodePointSet[]{
    }, new int[]{
    });
    private static final CaseFoldTable UNICODE_15_1_0_FULL = new CaseFoldTable(null, new int[]{
                    0x000041, 0x00005a, INTEGER_OFFSET, 32,
                    0x0000b5, 0x0000b5, INTEGER_OFFSET, 775,
                    0x0000c0, 0x0000d6, INTEGER_OFFSET, 32,
                    0x0000d8, 0x0000de, INTEGER_OFFSET, 32,
                    0x0000df, 0x0000df, INTEGER_OFFSET, 1113962,
                    0x000100, 0x00012e, ALTERNATING_AL, 0,
                    0x000130, 0x000130, INTEGER_OFFSET, 1113808,
                    0x000132, 0x000136, ALTERNATING_AL, 0,
                    0x000139, 0x000147, ALTERNATING_UL, 0,
                    0x000149, 0x000149, INTEGER_OFFSET, 1113857,
                    0x00014a, 0x000176, ALTERNATING_AL, 0,
                    0x000178, 0x000178, INTEGER_OFFSET, -121,
                    0x000179, 0x00017d, ALTERNATING_UL, 0,
                    0x00017f, 0x00017f, INTEGER_OFFSET, -268,
                    0x000181, 0x000181, INTEGER_OFFSET, 210,
                    0x000182, 0x000184, ALTERNATING_AL, 0,
                    0x000186, 0x000186, INTEGER_OFFSET, 206,
                    0x000187, 0x000187, ALTERNATING_UL, 0,
                    0x000189, 0x00018a, INTEGER_OFFSET, 205,
                    0x00018b, 0x00018b, ALTERNATING_UL, 0,
                    0x00018e, 0x00018e, INTEGER_OFFSET, 79,
                    0x00018f, 0x00018f, INTEGER_OFFSET, 202,
                    0x000190, 0x000190, INTEGER_OFFSET, 203,
                    0x000191, 0x000191, ALTERNATING_UL, 0,
                    0x000193, 0x000193, INTEGER_OFFSET, 205,
                    0x000194, 0x000194, INTEGER_OFFSET, 207,
                    0x000196, 0x000196, INTEGER_OFFSET, 211,
                    0x000197, 0x000197, INTEGER_OFFSET, 209,
                    0x000198, 0x000198, ALTERNATING_AL, 0,
                    0x00019c, 0x00019c, INTEGER_OFFSET, 211,
                    0x00019d, 0x00019d, INTEGER_OFFSET, 213,
                    0x00019f, 0x00019f, INTEGER_OFFSET, 214,
                    0x0001a0, 0x0001a4, ALTERNATING_AL, 0,
                    0x0001a6, 0x0001a6, INTEGER_OFFSET, 218,
                    0x0001a7, 0x0001a7, ALTERNATING_UL, 0,
                    0x0001a9, 0x0001a9, INTEGER_OFFSET, 218,
                    0x0001ac, 0x0001ac, ALTERNATING_AL, 0,
                    0x0001ae, 0x0001ae, INTEGER_OFFSET, 218,
                    0x0001af, 0x0001af, ALTERNATING_UL, 0,
                    0x0001b1, 0x0001b2, INTEGER_OFFSET, 217,
                    0x0001b3, 0x0001b5, ALTERNATING_UL, 0,
                    0x0001b7, 0x0001b7, INTEGER_OFFSET, 219,
                    0x0001b8, 0x0001b8, ALTERNATING_AL, 0,
                    0x0001bc, 0x0001bc, ALTERNATING_AL, 0,
                    0x0001c4, 0x0001c5, DIRECT_SINGLE, 454,
                    0x0001c7, 0x0001c8, DIRECT_SINGLE, 457,
                    0x0001ca, 0x0001cb, DIRECT_SINGLE, 460,
                    0x0001cd, 0x0001db, ALTERNATING_UL, 0,
                    0x0001de, 0x0001ee, ALTERNATING_AL, 0,
                    0x0001f0, 0x0001f0, INTEGER_OFFSET, 1113691,
                    0x0001f1, 0x0001f2, DIRECT_SINGLE, 499,
                    0x0001f4, 0x0001f4, ALTERNATING_AL, 0,
                    0x0001f6, 0x0001f6, INTEGER_OFFSET, -97,
                    0x0001f7, 0x0001f7, INTEGER_OFFSET, -56,
                    0x0001f8, 0x00021e, ALTERNATING_AL, 0,
                    0x000220, 0x000220, INTEGER_OFFSET, -130,
                    0x000222, 0x000232, ALTERNATING_AL, 0,
                    0x00023a, 0x00023a, INTEGER_OFFSET, 10795,
                    0x00023b, 0x00023b, ALTERNATING_UL, 0,
                    0x00023d, 0x00023d, INTEGER_OFFSET, -163,
                    0x00023e, 0x00023e, INTEGER_OFFSET, 10792,
                    0x000241, 0x000241, ALTERNATING_UL, 0,
                    0x000243, 0x000243, INTEGER_OFFSET, -195,
                    0x000244, 0x000244, INTEGER_OFFSET, 69,
                    0x000245, 0x000245, INTEGER_OFFSET, 71,
                    0x000246, 0x00024e, ALTERNATING_AL, 0,
                    0x000345, 0x000345, INTEGER_OFFSET, 116,
                    0x000370, 0x000372, ALTERNATING_AL, 0,
                    0x000376, 0x000376, ALTERNATING_AL, 0,
                    0x00037f, 0x00037f, INTEGER_OFFSET, 116,
                    0x000386, 0x000386, INTEGER_OFFSET, 38,
                    0x000388, 0x00038a, INTEGER_OFFSET, 37,
                    0x00038c, 0x00038c, INTEGER_OFFSET, 64,
                    0x00038e, 0x00038f, INTEGER_OFFSET, 63,
                    0x000390, 0x000390, INTEGER_OFFSET, 1113276,
                    0x000391, 0x0003a1, INTEGER_OFFSET, 32,
                    0x0003a3, 0x0003ab, INTEGER_OFFSET, 32,
                    0x0003b0, 0x0003b0, INTEGER_OFFSET, 1113245,
                    0x0003c2, 0x0003c2, ALTERNATING_AL, 0,
                    0x0003cf, 0x0003cf, INTEGER_OFFSET, 8,
                    0x0003d0, 0x0003d0, INTEGER_OFFSET, -30,
                    0x0003d1, 0x0003d1, INTEGER_OFFSET, -25,
                    0x0003d5, 0x0003d5, INTEGER_OFFSET, -15,
                    0x0003d6, 0x0003d6, INTEGER_OFFSET, -22,
                    0x0003d8, 0x0003ee, ALTERNATING_AL, 0,
                    0x0003f0, 0x0003f0, INTEGER_OFFSET, -54,
                    0x0003f1, 0x0003f1, INTEGER_OFFSET, -48,
                    0x0003f4, 0x0003f4, INTEGER_OFFSET, -60,
                    0x0003f5, 0x0003f5, INTEGER_OFFSET, -64,
                    0x0003f7, 0x0003f7, ALTERNATING_UL, 0,
                    0x0003f9, 0x0003f9, INTEGER_OFFSET, -7,
                    0x0003fa, 0x0003fa, ALTERNATING_AL, 0,
                    0x0003fd, 0x0003ff, INTEGER_OFFSET, -130,
                    0x000400, 0x00040f, INTEGER_OFFSET, 80,
                    0x000410, 0x00042f, INTEGER_OFFSET, 32,
                    0x000460, 0x000480, ALTERNATING_AL, 0,
                    0x00048a, 0x0004be, ALTERNATING_AL, 0,
                    0x0004c0, 0x0004c0, INTEGER_OFFSET, 15,
                    0x0004c1, 0x0004cd, ALTERNATING_UL, 0,
                    0x0004d0, 0x00052e, ALTERNATING_AL, 0,
                    0x000531, 0x000556, INTEGER_OFFSET, 48,
                    0x000587, 0x000587, INTEGER_OFFSET, 1112775,
                    0x0010a0, 0x0010c5, INTEGER_OFFSET, 7264,
                    0x0010c7, 0x0010c7, INTEGER_OFFSET, 7264,
                    0x0010cd, 0x0010cd, INTEGER_OFFSET, 7264,
                    0x0013f8, 0x0013fd, INTEGER_OFFSET, -8,
                    0x001c80, 0x001c80, INTEGER_OFFSET, -6222,
                    0x001c81, 0x001c81, INTEGER_OFFSET, -6221,
                    0x001c82, 0x001c82, INTEGER_OFFSET, -6212,
                    0x001c83, 0x001c84, INTEGER_OFFSET, -6210,
                    0x001c85, 0x001c85, INTEGER_OFFSET, -6211,
                    0x001c86, 0x001c86, INTEGER_OFFSET, -6204,
                    0x001c87, 0x001c87, INTEGER_OFFSET, -6180,
                    0x001c88, 0x001c88, INTEGER_OFFSET, 35267,
                    0x001c90, 0x001cba, INTEGER_OFFSET, -3008,
                    0x001cbd, 0x001cbf, INTEGER_OFFSET, -3008,
                    0x001e00, 0x001e94, ALTERNATING_AL, 0,
                    0x001e96, 0x001e9a, INTEGER_OFFSET, 1106361,
                    0x001e9b, 0x001e9b, INTEGER_OFFSET, -58,
                    0x001e9e, 0x001e9e, INTEGER_OFFSET, 1106347,
                    0x001ea0, 0x001efe, ALTERNATING_AL, 0,
                    0x001f08, 0x001f0f, INTEGER_OFFSET, -8,
                    0x001f18, 0x001f1d, INTEGER_OFFSET, -8,
                    0x001f28, 0x001f2f, INTEGER_OFFSET, -8,
                    0x001f38, 0x001f3f, INTEGER_OFFSET, -8,
                    0x001f48, 0x001f4d, INTEGER_OFFSET, -8,
                    0x001f50, 0x001f50, INTEGER_OFFSET, 1106180,
                    0x001f52, 0x001f52, INTEGER_OFFSET, 1106179,
                    0x001f54, 0x001f54, INTEGER_OFFSET, 1106178,
                    0x001f56, 0x001f56, INTEGER_OFFSET, 1106177,
                    0x001f59, 0x001f59, INTEGER_OFFSET, -8,
                    0x001f5b, 0x001f5b, INTEGER_OFFSET, -8,
                    0x001f5d, 0x001f5d, INTEGER_OFFSET, -8,
                    0x001f5f, 0x001f5f, INTEGER_OFFSET, -8,
                    0x001f68, 0x001f6f, INTEGER_OFFSET, -8,
                    0x001f80, 0x001f87, INTEGER_OFFSET, 1106136,
                    0x001f88, 0x001f97, INTEGER_OFFSET, 1106128,
                    0x001f98, 0x001fa7, INTEGER_OFFSET, 1106120,
                    0x001fa8, 0x001faf, INTEGER_OFFSET, 1106112,
                    0x001fb2, 0x001fb4, INTEGER_OFFSET, 1106110,
                    0x001fb6, 0x001fb7, INTEGER_OFFSET, 1106109,
                    0x001fb8, 0x001fb9, INTEGER_OFFSET, -8,
                    0x001fba, 0x001fbb, INTEGER_OFFSET, -74,
                    0x001fbc, 0x001fbc, INTEGER_OFFSET, 1106101,
                    0x001fbe, 0x001fbe, INTEGER_OFFSET, -7173,
                    0x001fc2, 0x001fc4, INTEGER_OFFSET, 1106099,
                    0x001fc6, 0x001fc7, INTEGER_OFFSET, 1106098,
                    0x001fc8, 0x001fcb, INTEGER_OFFSET, -86,
                    0x001fcc, 0x001fcc, INTEGER_OFFSET, 1106090,
                    0x001fd2, 0x001fd2, INTEGER_OFFSET, 1106088,
                    0x001fd3, 0x001fd3, INTEGER_OFFSET, 1106041,
                    0x001fd6, 0x001fd7, INTEGER_OFFSET, 1106085,
                    0x001fd8, 0x001fd9, INTEGER_OFFSET, -8,
                    0x001fda, 0x001fdb, INTEGER_OFFSET, -100,
                    0x001fe2, 0x001fe2, INTEGER_OFFSET, 1106075,
                    0x001fe3, 0x001fe3, INTEGER_OFFSET, 1106026,
                    0x001fe4, 0x001fe4, INTEGER_OFFSET, 1106074,
                    0x001fe6, 0x001fe7, INTEGER_OFFSET, 1106073,
                    0x001fe8, 0x001fe9, INTEGER_OFFSET, -8,
                    0x001fea, 0x001feb, INTEGER_OFFSET, -112,
                    0x001fec, 0x001fec, INTEGER_OFFSET, -7,
                    0x001ff2, 0x001ff4, INTEGER_OFFSET, 1106063,
                    0x001ff6, 0x001ff7, INTEGER_OFFSET, 1106062,
                    0x001ff8, 0x001ff9, INTEGER_OFFSET, -128,
                    0x001ffa, 0x001ffb, INTEGER_OFFSET, -126,
                    0x001ffc, 0x001ffc, INTEGER_OFFSET, 1106054,
                    0x002126, 0x002126, INTEGER_OFFSET, -7517,
                    0x00212a, 0x00212a, INTEGER_OFFSET, -8383,
                    0x00212b, 0x00212b, INTEGER_OFFSET, -8262,
                    0x002132, 0x002132, INTEGER_OFFSET, 28,
                    0x002160, 0x00216f, INTEGER_OFFSET, 16,
                    0x002183, 0x002183, ALTERNATING_UL, 0,
                    0x0024b6, 0x0024cf, INTEGER_OFFSET, 26,
                    0x002c00, 0x002c2f, INTEGER_OFFSET, 48,
                    0x002c60, 0x002c60, ALTERNATING_AL, 0,
                    0x002c62, 0x002c62, INTEGER_OFFSET, -10743,
                    0x002c63, 0x002c63, INTEGER_OFFSET, -3814,
                    0x002c64, 0x002c64, INTEGER_OFFSET, -10727,
                    0x002c67, 0x002c6b, ALTERNATING_UL, 0,
                    0x002c6d, 0x002c6d, INTEGER_OFFSET, -10780,
                    0x002c6e, 0x002c6e, INTEGER_OFFSET, -10749,
                    0x002c6f, 0x002c6f, INTEGER_OFFSET, -10783,
                    0x002c70, 0x002c70, INTEGER_OFFSET, -10782,
                    0x002c72, 0x002c72, ALTERNATING_AL, 0,
                    0x002c75, 0x002c75, ALTERNATING_UL, 0,
                    0x002c7e, 0x002c7f, INTEGER_OFFSET, -10815,
                    0x002c80, 0x002ce2, ALTERNATING_AL, 0,
                    0x002ceb, 0x002ced, ALTERNATING_UL, 0,
                    0x002cf2, 0x002cf2, ALTERNATING_AL, 0,
                    0x00a640, 0x00a66c, ALTERNATING_AL, 0,
                    0x00a680, 0x00a69a, ALTERNATING_AL, 0,
                    0x00a722, 0x00a72e, ALTERNATING_AL, 0,
                    0x00a732, 0x00a76e, ALTERNATING_AL, 0,
                    0x00a779, 0x00a77b, ALTERNATING_UL, 0,
                    0x00a77d, 0x00a77d, INTEGER_OFFSET, -35332,
                    0x00a77e, 0x00a786, ALTERNATING_AL, 0,
                    0x00a78b, 0x00a78b, ALTERNATING_UL, 0,
                    0x00a78d, 0x00a78d, INTEGER_OFFSET, -42280,
                    0x00a790, 0x00a792, ALTERNATING_AL, 0,
                    0x00a796, 0x00a7a8, ALTERNATING_AL, 0,
                    0x00a7aa, 0x00a7aa, INTEGER_OFFSET, -42308,
                    0x00a7ab, 0x00a7ab, INTEGER_OFFSET, -42319,
                    0x00a7ac, 0x00a7ac, INTEGER_OFFSET, -42315,
                    0x00a7ad, 0x00a7ad, INTEGER_OFFSET, -42305,
                    0x00a7ae, 0x00a7ae, INTEGER_OFFSET, -42308,
                    0x00a7b0, 0x00a7b0, INTEGER_OFFSET, -42258,
                    0x00a7b1, 0x00a7b1, INTEGER_OFFSET, -42282,
                    0x00a7b2, 0x00a7b2, INTEGER_OFFSET, -42261,
                    0x00a7b3, 0x00a7b3, INTEGER_OFFSET, 928,
                    0x00a7b4, 0x00a7c2, ALTERNATING_AL, 0,
                    0x00a7c4, 0x00a7c4, INTEGER_OFFSET, -48,
                    0x00a7c5, 0x00a7c5, INTEGER_OFFSET, -42307,
                    0x00a7c6, 0x00a7c6, INTEGER_OFFSET, -35384,
                    0x00a7c7, 0x00a7c9, ALTERNATING_UL, 0,
                    0x00a7d0, 0x00a7d0, ALTERNATING_AL, 0,
                    0x00a7d6, 0x00a7d8, ALTERNATING_AL, 0,
                    0x00a7f5, 0x00a7f5, ALTERNATING_UL, 0,
                    0x00ab70, 0x00abbf, INTEGER_OFFSET, -38864,
                    0x00fb00, 0x00fb05, INTEGER_OFFSET, 1049990,
                    0x00fb06, 0x00fb06, INTEGER_OFFSET, 1049989,
                    0x00fb13, 0x00fb17, INTEGER_OFFSET, 1049977,
                    0x00ff21, 0x00ff3a, INTEGER_OFFSET, 32,
                    0x010400, 0x010427, INTEGER_OFFSET, 40,
                    0x0104b0, 0x0104d3, INTEGER_OFFSET, 40,
                    0x010570, 0x01057a, INTEGER_OFFSET, 39,
                    0x01057c, 0x01058a, INTEGER_OFFSET, 39,
                    0x01058c, 0x010592, INTEGER_OFFSET, 39,
                    0x010594, 0x010595, INTEGER_OFFSET, 39,
                    0x010c80, 0x010cb2, INTEGER_OFFSET, 64,
                    0x0118a0, 0x0118bf, INTEGER_OFFSET, 32,
                    0x016e40, 0x016e5f, INTEGER_OFFSET, 32,
                    0x01e900, 0x01e921, INTEGER_OFFSET, 34,
    });
    private static final CaseFoldTable UNICODE_15_0_0_ODB_FULL = new CaseFoldTable(UNICODE_15_1_0_FULL, new int[]{
                    0x001f88, 0x001f8f, INTEGER_OFFSET, -8,
                    0x001f98, 0x001f9f, INTEGER_OFFSET, -8,
                    0x001fa8, 0x001faf, INTEGER_OFFSET, -8,
                    0x001fbc, 0x001fbc, INTEGER_OFFSET, -9,
                    0x001fcc, 0x001fcc, INTEGER_OFFSET, -9,
                    0x001ffc, 0x001ffc, INTEGER_OFFSET, -9,
    });
    private static final CaseFoldTable UNICODE_15_0_0_ODB_SIMPLE = new CaseFoldTable(UNICODE_15_1_0_FULL, new int[]{
                    0x0000b5, 0x0000b5, INTEGER_OFFSET, 0,
                    0x0000df, 0x0000df, INTEGER_OFFSET, 0,
                    0x000130, 0x000130, INTEGER_OFFSET, -199,
                    0x000149, 0x000149, INTEGER_OFFSET, 0,
                    0x00017f, 0x00017f, INTEGER_OFFSET, 0,
                    0x0001f0, 0x0001f0, INTEGER_OFFSET, 0,
                    0x000345, 0x000345, INTEGER_OFFSET, 0,
                    0x000390, 0x000390, INTEGER_OFFSET, 0,
                    0x0003b0, 0x0003b0, INTEGER_OFFSET, 0,
                    0x0003c2, 0x0003c2, INTEGER_OFFSET, 0,
                    0x0003d0, 0x0003d0, INTEGER_OFFSET, 0,
                    0x0003d1, 0x0003d1, INTEGER_OFFSET, 0,
                    0x0003d5, 0x0003d5, INTEGER_OFFSET, 0,
                    0x0003d6, 0x0003d6, INTEGER_OFFSET, 0,
                    0x0003f0, 0x0003f0, INTEGER_OFFSET, 0,
                    0x0003f1, 0x0003f1, INTEGER_OFFSET, 0,
                    0x0003f5, 0x0003f5, INTEGER_OFFSET, 0,
                    0x000587, 0x000587, INTEGER_OFFSET, 0,
                    0x0013a0, 0x0013ef, INTEGER_OFFSET, 38864,
                    0x0013f0, 0x0013f5, INTEGER_OFFSET, 8,
                    0x0013f8, 0x0013fd, INTEGER_OFFSET, 0,
                    0x001c80, 0x001c80, INTEGER_OFFSET, 0,
                    0x001c81, 0x001c81, INTEGER_OFFSET, 0,
                    0x001c82, 0x001c82, INTEGER_OFFSET, 0,
                    0x001c83, 0x001c84, INTEGER_OFFSET, 0,
                    0x001c85, 0x001c85, INTEGER_OFFSET, 0,
                    0x001c86, 0x001c86, INTEGER_OFFSET, 0,
                    0x001c87, 0x001c87, INTEGER_OFFSET, 0,
                    0x001c88, 0x001c88, INTEGER_OFFSET, 0,
                    0x001e96, 0x001e9a, INTEGER_OFFSET, 0,
                    0x001e9b, 0x001e9b, INTEGER_OFFSET, 0,
                    0x001e9e, 0x001e9e, INTEGER_OFFSET, -7615,
                    0x001f50, 0x001f50, INTEGER_OFFSET, 0,
                    0x001f52, 0x001f52, INTEGER_OFFSET, 0,
                    0x001f54, 0x001f54, INTEGER_OFFSET, 0,
                    0x001f56, 0x001f56, INTEGER_OFFSET, 0,
                    0x001f80, 0x001f87, INTEGER_OFFSET, 0,
                    0x001f88, 0x001f8f, INTEGER_OFFSET, -8,
                    0x001f90, 0x001f97, INTEGER_OFFSET, 0,
                    0x001f98, 0x001f9f, INTEGER_OFFSET, -8,
                    0x001fa0, 0x001fa7, INTEGER_OFFSET, 0,
                    0x001fa8, 0x001faf, INTEGER_OFFSET, -8,
                    0x001fb2, 0x001fb4, INTEGER_OFFSET, 0,
                    0x001fb6, 0x001fb7, INTEGER_OFFSET, 0,
                    0x001fbc, 0x001fbc, INTEGER_OFFSET, -9,
                    0x001fbe, 0x001fbe, INTEGER_OFFSET, 0,
                    0x001fc2, 0x001fc4, INTEGER_OFFSET, 0,
                    0x001fc6, 0x001fc7, INTEGER_OFFSET, 0,
                    0x001fcc, 0x001fcc, INTEGER_OFFSET, -9,
                    0x001fd2, 0x001fd2, INTEGER_OFFSET, 0,
                    0x001fd3, 0x001fd3, INTEGER_OFFSET, 0,
                    0x001fd6, 0x001fd7, INTEGER_OFFSET, 0,
                    0x001fe2, 0x001fe2, INTEGER_OFFSET, 0,
                    0x001fe3, 0x001fe3, INTEGER_OFFSET, 0,
                    0x001fe4, 0x001fe4, INTEGER_OFFSET, 0,
                    0x001fe6, 0x001fe7, INTEGER_OFFSET, 0,
                    0x001ff2, 0x001ff4, INTEGER_OFFSET, 0,
                    0x001ff6, 0x001ff7, INTEGER_OFFSET, 0,
                    0x001ffc, 0x001ffc, INTEGER_OFFSET, -9,
                    0x00ab70, 0x00abbf, INTEGER_OFFSET, 0,
                    0x00fb00, 0x00fb05, INTEGER_OFFSET, 0,
                    0x00fb06, 0x00fb06, INTEGER_OFFSET, 0,
                    0x00fb13, 0x00fb17, INTEGER_OFFSET, 0,
    });
    private static final CaseFoldEquivalenceTable UNICODE_15_0_0_ODB_SIMPLE_EQ = new CaseFoldEquivalenceTable(UNICODE_15_1_0_PY, new CodePointSet[]{
                    rangeSet(0x000049, 0x000049, 0x000069, 0x000069, 0x000130, 0x000130),
                    rangeSet(0x000398, 0x000398, 0x0003b8, 0x0003b8, 0x0003f4, 0x0003f4),
    }, new int[]{
                    0x000049, 0x000049, DIRECT_MAPPING, 0,
                    0x000053, 0x00005a, INTEGER_OFFSET, 32,
                    0x000069, 0x000069, DIRECT_MAPPING, 0,
                    0x000073, 0x00007a, INTEGER_OFFSET, -32,
                    0x0000b5, 0x0000b5, INTEGER_OFFSET, 0,
                    0x000130, 0x000130, DIRECT_MAPPING, 0,
                    0x000131, 0x000131, INTEGER_OFFSET, 0,
                    0x00017f, 0x00017f, INTEGER_OFFSET, 0,
                    0x000345, 0x000345, INTEGER_OFFSET, 0,
                    0x000390, 0x000390, INTEGER_OFFSET, 0,
                    0x000392, 0x000397, INTEGER_OFFSET, 32,
                    0x000398, 0x000398, DIRECT_MAPPING, 1,
                    0x000399, 0x0003a1, INTEGER_OFFSET, 32,
                    0x0003a3, 0x0003a8, INTEGER_OFFSET, 32,
                    0x0003b0, 0x0003b0, INTEGER_OFFSET, 0,
                    0x0003b2, 0x0003b7, INTEGER_OFFSET, -32,
                    0x0003b8, 0x0003b8, DIRECT_MAPPING, 1,
                    0x0003b9, 0x0003c1, INTEGER_OFFSET, -32,
                    0x0003c2, 0x0003c2, INTEGER_OFFSET, 0,
                    0x0003c3, 0x0003c8, INTEGER_OFFSET, -32,
                    0x0003d0, 0x0003d0, INTEGER_OFFSET, 0,
                    0x0003d1, 0x0003d1, INTEGER_OFFSET, 0,
                    0x0003d5, 0x0003d5, INTEGER_OFFSET, 0,
                    0x0003d6, 0x0003d6, INTEGER_OFFSET, 0,
                    0x0003f0, 0x0003f0, INTEGER_OFFSET, 0,
                    0x0003f1, 0x0003f1, INTEGER_OFFSET, 0,
                    0x0003f4, 0x0003f4, DIRECT_MAPPING, 1,
                    0x0003f5, 0x0003f5, INTEGER_OFFSET, 0,
                    0x000412, 0x00042f, INTEGER_OFFSET, 32,
                    0x000432, 0x00044f, INTEGER_OFFSET, -32,
                    0x000462, 0x000481, ALTERNATING_AL, 0,
                    0x001c80, 0x001c80, INTEGER_OFFSET, 0,
                    0x001c81, 0x001c81, INTEGER_OFFSET, 0,
                    0x001c82, 0x001c82, INTEGER_OFFSET, 0,
                    0x001c83, 0x001c83, INTEGER_OFFSET, 0,
                    0x001c84, 0x001c85, INTEGER_OFFSET, 0,
                    0x001c86, 0x001c86, INTEGER_OFFSET, 0,
                    0x001c87, 0x001c87, INTEGER_OFFSET, 0,
                    0x001c88, 0x001c88, INTEGER_OFFSET, 0,
                    0x001e60, 0x001e95, ALTERNATING_AL, 0,
                    0x001e9b, 0x001e9b, INTEGER_OFFSET, 0,
                    0x001fbe, 0x001fbe, INTEGER_OFFSET, 0,
                    0x001fd3, 0x001fd3, INTEGER_OFFSET, 0,
                    0x001fe3, 0x001fe3, INTEGER_OFFSET, 0,
                    0x00a64a, 0x00a66d, ALTERNATING_AL, 0,
                    0x00fb05, 0x00fb06, INTEGER_OFFSET, 0,
    });
    private static final CaseFoldTable UNICODE_15_0_0_ODB_AI = new CaseFoldTable(null, new int[]{
                    0x000041, 0x00005a, INTEGER_OFFSET, 32,
                    0x000084, 0x000084, ALTERNATING_AL, 0,
                    0x0000a9, 0x0000a9, INTEGER_OFFSET, -70,
                    0x0000aa, 0x0000aa, INTEGER_OFFSET, -73,
                    0x0000ae, 0x0000ae, INTEGER_OFFSET, -60,
                    0x0000b2, 0x0000b3, INTEGER_OFFSET, -128,
                    0x0000b5, 0x0000b5, INTEGER_OFFSET, 775,
                    0x0000b9, 0x0000b9, INTEGER_OFFSET, -136,
                    0x0000ba, 0x0000ba, INTEGER_OFFSET, -75,
                    0x0000c0, 0x0000c5, DIRECT_SINGLE, 97,
                    0x0000c6, 0x0000c6, INTEGER_OFFSET, 32,
                    0x0000c7, 0x0000c7, INTEGER_OFFSET, -100,
                    0x0000c8, 0x0000cb, DIRECT_SINGLE, 101,
                    0x0000cc, 0x0000cf, DIRECT_SINGLE, 105,
                    0x0000d0, 0x0000d0, INTEGER_OFFSET, 32,
                    0x0000d1, 0x0000d2, INTEGER_OFFSET, -99,
                    0x0000d3, 0x0000d6, DIRECT_SINGLE, 111,
                    0x0000d8, 0x0000d8, INTEGER_OFFSET, -105,
                    0x0000d9, 0x0000dc, DIRECT_SINGLE, 117,
                    0x0000dd, 0x0000dd, INTEGER_OFFSET, -100,
                    0x0000de, 0x0000de, INTEGER_OFFSET, 32,
                    0x0000df, 0x0000df, INTEGER_OFFSET, 1113962,
                    0x0000e0, 0x0000e5, DIRECT_SINGLE, 97,
                    0x0000e7, 0x0000e7, INTEGER_OFFSET, -132,
                    0x0000e8, 0x0000eb, DIRECT_SINGLE, 101,
                    0x0000ec, 0x0000ef, DIRECT_SINGLE, 105,
                    0x0000f1, 0x0000f2, INTEGER_OFFSET, -131,
                    0x0000f3, 0x0000f6, DIRECT_SINGLE, 111,
                    0x0000f8, 0x0000f8, INTEGER_OFFSET, -137,
                    0x0000f9, 0x0000fc, DIRECT_SINGLE, 117,
                    0x0000fd, 0x0000fd, INTEGER_OFFSET, -132,
                    0x0000ff, 0x0000ff, INTEGER_OFFSET, -134,
                    0x000100, 0x000105, DIRECT_SINGLE, 97,
                    0x000106, 0x00010d, DIRECT_SINGLE, 99,
                    0x00010e, 0x000111, DIRECT_SINGLE, 100,
                    0x000112, 0x00011b, DIRECT_SINGLE, 101,
                    0x00011c, 0x000123, DIRECT_SINGLE, 103,
                    0x000124, 0x000127, DIRECT_SINGLE, 104,
                    0x000128, 0x000131, DIRECT_SINGLE, 105,
                    0x000132, 0x000133, DIRECT_SINGLE, 1114257,
                    0x000134, 0x000135, DIRECT_SINGLE, 106,
                    0x000136, 0x000138, DIRECT_SINGLE, 107,
                    0x000139, 0x000140, DIRECT_SINGLE, 108,
                    0x000141, 0x000141, ALTERNATING_UL, 0,
                    0x000142, 0x000142, INTEGER_OFFSET, -214,
                    0x000143, 0x000148, DIRECT_SINGLE, 110,
                    0x00014a, 0x00014a, ALTERNATING_AL, 0,
                    0x00014c, 0x000151, DIRECT_SINGLE, 111,
                    0x000152, 0x000153, DIRECT_SINGLE, 1114258,
                    0x000154, 0x000159, DIRECT_SINGLE, 114,
                    0x00015a, 0x000161, DIRECT_SINGLE, 115,
                    0x000162, 0x000165, DIRECT_SINGLE, 116,
                    0x000166, 0x000166, ALTERNATING_AL, 0,
                    0x000167, 0x000168, INTEGER_OFFSET, -243,
                    0x000169, 0x000173, DIRECT_SINGLE, 117,
                    0x000174, 0x000175, DIRECT_SINGLE, 119,
                    0x000176, 0x000178, DIRECT_SINGLE, 121,
                    0x000179, 0x00017e, DIRECT_SINGLE, 122,
                    0x00017f, 0x00017f, INTEGER_OFFSET, -268,
                    0x000181, 0x000181, INTEGER_OFFSET, 210,
                    0x000182, 0x000184, ALTERNATING_AL, 0,
                    0x000186, 0x000186, INTEGER_OFFSET, 206,
                    0x000187, 0x000187, ALTERNATING_UL, 0,
                    0x000189, 0x00018a, INTEGER_OFFSET, 205,
                    0x00018b, 0x00018b, ALTERNATING_UL, 0,
                    0x00018e, 0x00018e, INTEGER_OFFSET, 79,
                    0x00018f, 0x00018f, INTEGER_OFFSET, 202,
                    0x000190, 0x000190, INTEGER_OFFSET, 203,
                    0x000191, 0x000191, ALTERNATING_UL, 0,
                    0x000193, 0x000193, INTEGER_OFFSET, 205,
                    0x000194, 0x000194, INTEGER_OFFSET, 207,
                    0x000196, 0x000196, INTEGER_OFFSET, 211,
                    0x000197, 0x000197, INTEGER_OFFSET, 209,
                    0x000198, 0x000198, ALTERNATING_AL, 0,
                    0x00019c, 0x00019c, INTEGER_OFFSET, 211,
                    0x00019d, 0x00019d, INTEGER_OFFSET, 213,
                    0x00019f, 0x00019f, INTEGER_OFFSET, 214,
                    0x0001a0, 0x0001a1, DIRECT_SINGLE, 111,
                    0x0001a2, 0x0001a4, ALTERNATING_AL, 0,
                    0x0001a6, 0x0001a6, INTEGER_OFFSET, 218,
                    0x0001a7, 0x0001a7, ALTERNATING_UL, 0,
                    0x0001a9, 0x0001a9, INTEGER_OFFSET, 218,
                    0x0001ac, 0x0001ac, ALTERNATING_AL, 0,
                    0x0001ae, 0x0001ae, INTEGER_OFFSET, 218,
                    0x0001af, 0x0001b0, DIRECT_SINGLE, 117,
                    0x0001b1, 0x0001b2, INTEGER_OFFSET, 217,
                    0x0001b3, 0x0001b5, ALTERNATING_UL, 0,
                    0x0001b7, 0x0001b7, INTEGER_OFFSET, 219,
                    0x0001b8, 0x0001b8, ALTERNATING_AL, 0,
                    0x0001bc, 0x0001bc, ALTERNATING_AL, 0,
                    0x0001c4, 0x0001c6, DIRECT_SINGLE, 499,
                    0x0001c7, 0x0001c9, DIRECT_SINGLE, 1114259,
                    0x0001ca, 0x0001cc, DIRECT_SINGLE, 1114260,
                    0x0001cd, 0x0001ce, DIRECT_SINGLE, 97,
                    0x0001cf, 0x0001d0, DIRECT_SINGLE, 105,
                    0x0001d1, 0x0001d2, DIRECT_SINGLE, 111,
                    0x0001d3, 0x0001dc, DIRECT_SINGLE, 117,
                    0x0001de, 0x0001e1, DIRECT_SINGLE, 97,
                    0x0001e2, 0x0001e3, DIRECT_SINGLE, 230,
                    0x0001e4, 0x0001e4, ALTERNATING_AL, 0,
                    0x0001e6, 0x0001e7, DIRECT_SINGLE, 103,
                    0x0001e8, 0x0001e9, DIRECT_SINGLE, 107,
                    0x0001ea, 0x0001ed, DIRECT_SINGLE, 111,
                    0x0001ee, 0x0001ee, INTEGER_OFFSET, -55,
                    0x0001ef, 0x0001ef, INTEGER_OFFSET, 163,
                    0x0001f0, 0x0001f0, INTEGER_OFFSET, -390,
                    0x0001f1, 0x0001f3, DIRECT_SINGLE, 1114261,
                    0x0001f4, 0x0001f5, DIRECT_SINGLE, 103,
                    0x0001f6, 0x0001f6, INTEGER_OFFSET, -97,
                    0x0001f7, 0x0001f7, INTEGER_OFFSET, -56,
                    0x0001f8, 0x0001f9, DIRECT_SINGLE, 110,
                    0x0001fa, 0x0001fb, DIRECT_SINGLE, 97,
                    0x0001fc, 0x0001fd, DIRECT_SINGLE, 230,
                    0x0001fe, 0x0001ff, DIRECT_SINGLE, 111,
                    0x000200, 0x000203, DIRECT_SINGLE, 97,
                    0x000204, 0x000207, DIRECT_SINGLE, 101,
                    0x000208, 0x00020b, DIRECT_SINGLE, 105,
                    0x00020c, 0x00020f, DIRECT_SINGLE, 111,
                    0x000210, 0x000213, DIRECT_SINGLE, 114,
                    0x000214, 0x000217, DIRECT_SINGLE, 117,
                    0x000218, 0x000219, DIRECT_SINGLE, 115,
                    0x00021a, 0x00021b, DIRECT_SINGLE, 116,
                    0x00021c, 0x00021c, ALTERNATING_AL, 0,
                    0x00021e, 0x00021f, DIRECT_SINGLE, 104,
                    0x000222, 0x000224, ALTERNATING_AL, 0,
                    0x000226, 0x000227, DIRECT_SINGLE, 97,
                    0x000228, 0x000229, DIRECT_SINGLE, 101,
                    0x00022a, 0x000231, DIRECT_SINGLE, 111,
                    0x000232, 0x000233, DIRECT_SINGLE, 121,
                    0x0002a3, 0x0002a8, INTEGER_OFFSET, 1113586,
                    0x0002b0, 0x0002b0, INTEGER_OFFSET, -584,
                    0x0002b1, 0x0002b1, INTEGER_OFFSET, -75,
                    0x0002b2, 0x0002b2, INTEGER_OFFSET, -584,
                    0x0002b3, 0x0002b3, INTEGER_OFFSET, -577,
                    0x0002b4, 0x0002b4, INTEGER_OFFSET, -59,
                    0x0002b5, 0x0002b5, INTEGER_OFFSET, -58,
                    0x0002b6, 0x0002b6, INTEGER_OFFSET, -53,
                    0x0002b7, 0x0002b7, INTEGER_OFFSET, -576,
                    0x0002b8, 0x0002b8, INTEGER_OFFSET, -575,
                    0x0002e0, 0x0002e0, INTEGER_OFFSET, -125,
                    0x0002e1, 0x0002e1, INTEGER_OFFSET, -629,
                    0x0002e2, 0x0002e2, INTEGER_OFFSET, -623,
                    0x0002e4, 0x0002e4, INTEGER_OFFSET, -79,
                    0x000344, 0x000344, INTEGER_OFFSET, -60,
                    0x000385, 0x000385, INTEGER_OFFSET, -733,
                    0x000386, 0x000386, INTEGER_OFFSET, 43,
                    0x000388, 0x000388, INTEGER_OFFSET, 45,
                    0x000389, 0x000389, INTEGER_OFFSET, 46,
                    0x00038a, 0x00038a, INTEGER_OFFSET, 47,
                    0x00038c, 0x00038c, INTEGER_OFFSET, 51,
                    0x00038e, 0x00038e, INTEGER_OFFSET, 55,
                    0x00038f, 0x00038f, INTEGER_OFFSET, 58,
                    0x000390, 0x000390, INTEGER_OFFSET, 41,
                    0x000391, 0x0003a1, INTEGER_OFFSET, 32,
                    0x0003a3, 0x0003a9, INTEGER_OFFSET, 32,
                    0x0003aa, 0x0003aa, INTEGER_OFFSET, 15,
                    0x0003ab, 0x0003ab, INTEGER_OFFSET, 26,
                    0x0003ac, 0x0003ac, INTEGER_OFFSET, 5,
                    0x0003ad, 0x0003ad, INTEGER_OFFSET, 8,
                    0x0003ae, 0x0003ae, INTEGER_OFFSET, 9,
                    0x0003af, 0x0003af, INTEGER_OFFSET, 10,
                    0x0003b0, 0x0003b0, INTEGER_OFFSET, 21,
                    0x0003c2, 0x0003c2, ALTERNATING_AL, 0,
                    0x0003ca, 0x0003ca, INTEGER_OFFSET, -17,
                    0x0003cb, 0x0003cb, INTEGER_OFFSET, -6,
                    0x0003cc, 0x0003cc, INTEGER_OFFSET, -13,
                    0x0003cd, 0x0003cd, INTEGER_OFFSET, -8,
                    0x0003ce, 0x0003ce, INTEGER_OFFSET, -5,
                    0x0003d0, 0x0003d0, INTEGER_OFFSET, -30,
                    0x0003d1, 0x0003d1, INTEGER_OFFSET, -25,
                    0x0003d2, 0x0003d4, DIRECT_SINGLE, 965,
                    0x0003d5, 0x0003d5, INTEGER_OFFSET, -15,
                    0x0003d6, 0x0003d6, INTEGER_OFFSET, -22,
                    0x0003da, 0x0003ee, ALTERNATING_AL, 0,
                    0x0003f0, 0x0003f0, INTEGER_OFFSET, -54,
                    0x0003f1, 0x0003f1, INTEGER_OFFSET, -48,
                    0x0003f2, 0x0003f2, INTEGER_OFFSET, -47,
                    0x0003f4, 0x0003f4, INTEGER_OFFSET, -60,
                    0x000400, 0x000401, DIRECT_SINGLE, 1077,
                    0x000402, 0x000402, INTEGER_OFFSET, 80,
                    0x000403, 0x000403, INTEGER_OFFSET, 48,
                    0x000404, 0x000406, INTEGER_OFFSET, 80,
                    0x000407, 0x000407, INTEGER_OFFSET, 79,
                    0x000408, 0x00040b, INTEGER_OFFSET, 80,
                    0x00040c, 0x00040c, INTEGER_OFFSET, 46,
                    0x00040d, 0x00040d, INTEGER_OFFSET, 43,
                    0x00040e, 0x00040e, INTEGER_OFFSET, 53,
                    0x00040f, 0x00040f, INTEGER_OFFSET, 80,
                    0x000410, 0x000418, INTEGER_OFFSET, 32,
                    0x000419, 0x000419, INTEGER_OFFSET, 31,
                    0x00041a, 0x00042f, INTEGER_OFFSET, 32,
                    0x000439, 0x000439, INTEGER_OFFSET, -1,
                    0x000450, 0x000451, DIRECT_SINGLE, 1077,
                    0x000453, 0x000453, INTEGER_OFFSET, -32,
                    0x000457, 0x000457, INTEGER_OFFSET, -1,
                    0x00045c, 0x00045c, INTEGER_OFFSET, -34,
                    0x00045d, 0x00045d, INTEGER_OFFSET, -37,
                    0x00045e, 0x00045e, INTEGER_OFFSET, -27,
                    0x000460, 0x000474, ALTERNATING_AL, 0,
                    0x000476, 0x000477, DIRECT_SINGLE, 1141,
                    0x000478, 0x000480, ALTERNATING_AL, 0,
                    0x00048c, 0x00048e, ALTERNATING_AL, 0,
                    0x000490, 0x000491, DIRECT_SINGLE, 1075,
                    0x000492, 0x0004be, ALTERNATING_AL, 0,
                    0x0004c1, 0x0004c2, DIRECT_SINGLE, 1078,
                    0x0004c3, 0x0004c3, ALTERNATING_UL, 0,
                    0x0004c7, 0x0004c7, ALTERNATING_UL, 0,
                    0x0004cb, 0x0004cb, ALTERNATING_UL, 0,
                    0x0004d0, 0x0004d3, DIRECT_SINGLE, 1072,
                    0x0004d4, 0x0004d4, ALTERNATING_AL, 0,
                    0x0004d6, 0x0004d7, DIRECT_SINGLE, 1077,
                    0x0004d8, 0x0004d8, ALTERNATING_AL, 0,
                    0x0004da, 0x0004db, DIRECT_SINGLE, 1241,
                    0x0004dc, 0x0004dd, DIRECT_SINGLE, 1078,
                    0x0004de, 0x0004df, DIRECT_SINGLE, 1079,
                    0x0004e0, 0x0004e0, ALTERNATING_AL, 0,
                    0x0004e2, 0x0004e5, DIRECT_SINGLE, 1080,
                    0x0004e6, 0x0004e7, DIRECT_SINGLE, 1086,
                    0x0004e8, 0x0004e8, ALTERNATING_AL, 0,
                    0x0004ea, 0x0004eb, DIRECT_SINGLE, 1257,
                    0x0004ec, 0x0004ed, DIRECT_SINGLE, 1101,
                    0x0004ee, 0x0004f3, DIRECT_SINGLE, 1091,
                    0x0004f4, 0x0004f5, DIRECT_SINGLE, 1095,
                    0x0004f8, 0x0004f9, DIRECT_SINGLE, 1099,
                    0x000531, 0x000556, INTEGER_OFFSET, 48,
                    0x0005da, 0x0005da, ALTERNATING_AL, 0,
                    0x0005dd, 0x0005df, ALTERNATING_UL, 0,
                    0x0005e3, 0x0005e5, ALTERNATING_UL, 0,
                    0x000622, 0x000623, DIRECT_SINGLE, 1575,
                    0x000624, 0x000624, INTEGER_OFFSET, 36,
                    0x000625, 0x000625, INTEGER_OFFSET, 2,
                    0x000626, 0x000626, INTEGER_OFFSET, 36,
                    0x000660, 0x000669, INTEGER_OFFSET, -1584,
                    0x0006c0, 0x0006c0, INTEGER_OFFSET, 21,
                    0x0006c2, 0x0006c2, INTEGER_OFFSET, -1,
                    0x0006d3, 0x0006d3, INTEGER_OFFSET, -1,
                    0x0006f0, 0x0006f9, INTEGER_OFFSET, -1728,
                    0x000929, 0x000929, INTEGER_OFFSET, -1,
                    0x000931, 0x000931, INTEGER_OFFSET, -1,
                    0x000934, 0x000934, INTEGER_OFFSET, -1,
                    0x0009cb, 0x0009cc, DIRECT_SINGLE, 2503,
                    0x000b48, 0x000b48, INTEGER_OFFSET, -1,
                    0x000b4b, 0x000b4c, DIRECT_SINGLE, 2887,
                    0x000b94, 0x000b94, INTEGER_OFFSET, -2,
                    0x000bca, 0x000bcb, INTEGER_OFFSET, -4,
                    0x000bcc, 0x000bcc, INTEGER_OFFSET, -6,
                    0x000c48, 0x000c48, INTEGER_OFFSET, -2,
                    0x000cc0, 0x000cc0, INTEGER_OFFSET, -1,
                    0x000cc7, 0x000cc8, DIRECT_SINGLE, 3270,
                    0x000cca, 0x000ccb, DIRECT_SINGLE, 3270,
                    0x000d4a, 0x000d4b, INTEGER_OFFSET, -4,
                    0x000d4c, 0x000d4c, INTEGER_OFFSET, -6,
                    0x000dda, 0x000dda, INTEGER_OFFSET, -1,
                    0x000ddc, 0x000dde, DIRECT_SINGLE, 3545,
                    0x000f73, 0x000f73, INTEGER_OFFSET, -2,
                    0x000f75, 0x000f75, INTEGER_OFFSET, -4,
                    0x000f81, 0x000f81, INTEGER_OFFSET, -16,
                    0x001026, 0x001026, INTEGER_OFFSET, -1,
                    0x0010a0, 0x0010c5, INTEGER_OFFSET, 48,
                    0x001e00, 0x001e01, DIRECT_SINGLE, 97,
                    0x001e02, 0x001e07, DIRECT_SINGLE, 98,
                    0x001e08, 0x001e09, DIRECT_SINGLE, 99,
                    0x001e0a, 0x001e13, DIRECT_SINGLE, 100,
                    0x001e14, 0x001e1d, DIRECT_SINGLE, 101,
                    0x001e1e, 0x001e1f, DIRECT_SINGLE, 102,
                    0x001e20, 0x001e21, DIRECT_SINGLE, 103,
                    0x001e22, 0x001e2b, DIRECT_SINGLE, 104,
                    0x001e2c, 0x001e2f, DIRECT_SINGLE, 105,
                    0x001e30, 0x001e35, DIRECT_SINGLE, 107,
                    0x001e36, 0x001e3d, DIRECT_SINGLE, 108,
                    0x001e3e, 0x001e43, DIRECT_SINGLE, 109,
                    0x001e44, 0x001e4b, DIRECT_SINGLE, 110,
                    0x001e4c, 0x001e53, DIRECT_SINGLE, 111,
                    0x001e54, 0x001e57, DIRECT_SINGLE, 112,
                    0x001e58, 0x001e5f, DIRECT_SINGLE, 114,
                    0x001e60, 0x001e69, DIRECT_SINGLE, 115,
                    0x001e6a, 0x001e71, DIRECT_SINGLE, 116,
                    0x001e72, 0x001e7b, DIRECT_SINGLE, 117,
                    0x001e7c, 0x001e7f, DIRECT_SINGLE, 118,
                    0x001e80, 0x001e89, DIRECT_SINGLE, 119,
                    0x001e8a, 0x001e8d, DIRECT_SINGLE, 120,
                    0x001e8e, 0x001e8f, DIRECT_SINGLE, 121,
                    0x001e90, 0x001e95, DIRECT_SINGLE, 122,
                    0x001e96, 0x001e96, INTEGER_OFFSET, -7726,
                    0x001e97, 0x001e97, INTEGER_OFFSET, -7715,
                    0x001e98, 0x001e98, INTEGER_OFFSET, -7713,
                    0x001e99, 0x001e99, INTEGER_OFFSET, -7712,
                    0x001e9a, 0x001e9a, INTEGER_OFFSET, 1106361,
                    0x001e9b, 0x001e9b, INTEGER_OFFSET, -7720,
                    0x001ea0, 0x001eb7, DIRECT_SINGLE, 97,
                    0x001eb8, 0x001ec7, DIRECT_SINGLE, 101,
                    0x001ec8, 0x001ecb, DIRECT_SINGLE, 105,
                    0x001ecc, 0x001ee3, DIRECT_SINGLE, 111,
                    0x001ee4, 0x001ef1, DIRECT_SINGLE, 117,
                    0x001ef2, 0x001ef9, DIRECT_SINGLE, 121,
                    0x001f00, 0x001f0f, DIRECT_SINGLE, 945,
                    0x001f10, 0x001f15, DIRECT_SINGLE, 949,
                    0x001f18, 0x001f1d, DIRECT_SINGLE, 949,
                    0x001f20, 0x001f2f, DIRECT_SINGLE, 951,
                    0x001f30, 0x001f3f, DIRECT_SINGLE, 953,
                    0x001f40, 0x001f45, DIRECT_SINGLE, 959,
                    0x001f48, 0x001f4d, DIRECT_SINGLE, 959,
                    0x001f50, 0x001f57, DIRECT_SINGLE, 965,
                    0x001f59, 0x001f59, INTEGER_OFFSET, -7060,
                    0x001f5b, 0x001f5b, INTEGER_OFFSET, -7062,
                    0x001f5d, 0x001f5d, INTEGER_OFFSET, -7064,
                    0x001f5f, 0x001f5f, INTEGER_OFFSET, -7066,
                    0x001f60, 0x001f6f, DIRECT_SINGLE, 969,
                    0x001f70, 0x001f70, INTEGER_OFFSET, -7103,
                    0x001f72, 0x001f72, INTEGER_OFFSET, -7101,
                    0x001f74, 0x001f74, INTEGER_OFFSET, -7101,
                    0x001f76, 0x001f76, INTEGER_OFFSET, -7101,
                    0x001f78, 0x001f78, INTEGER_OFFSET, -7097,
                    0x001f7a, 0x001f7a, INTEGER_OFFSET, -7093,
                    0x001f7c, 0x001f7c, INTEGER_OFFSET, -7091,
                    0x001f80, 0x001f8f, DIRECT_SINGLE, 945,
                    0x001f90, 0x001f9f, DIRECT_SINGLE, 951,
                    0x001fa0, 0x001faf, DIRECT_SINGLE, 969,
                    0x001fb0, 0x001fb4, DIRECT_SINGLE, 945,
                    0x001fb6, 0x001fba, DIRECT_SINGLE, 945,
                    0x001fbb, 0x001fbb, INTEGER_OFFSET, -74,
                    0x001fbc, 0x001fbc, INTEGER_OFFSET, -7179,
                    0x001fbe, 0x001fbe, INTEGER_OFFSET, -7173,
                    0x001fc1, 0x001fc1, INTEGER_OFFSET, -7961,
                    0x001fc2, 0x001fc4, DIRECT_SINGLE, 951,
                    0x001fc6, 0x001fc7, DIRECT_SINGLE, 951,
                    0x001fc8, 0x001fc8, INTEGER_OFFSET, -7187,
                    0x001fc9, 0x001fc9, INTEGER_OFFSET, -86,
                    0x001fca, 0x001fca, INTEGER_OFFSET, -7187,
                    0x001fcb, 0x001fcb, INTEGER_OFFSET, -86,
                    0x001fcc, 0x001fcc, INTEGER_OFFSET, -7189,
                    0x001fcd, 0x001fcf, DIRECT_SINGLE, 8127,
                    0x001fd0, 0x001fd2, DIRECT_SINGLE, 953,
                    0x001fd6, 0x001fda, DIRECT_SINGLE, 953,
                    0x001fdb, 0x001fdb, INTEGER_OFFSET, -100,
                    0x001fdd, 0x001fdf, DIRECT_SINGLE, 8190,
                    0x001fe0, 0x001fe2, DIRECT_SINGLE, 965,
                    0x001fe4, 0x001fe5, DIRECT_SINGLE, 961,
                    0x001fe6, 0x001fea, DIRECT_SINGLE, 965,
                    0x001feb, 0x001feb, INTEGER_OFFSET, -112,
                    0x001fec, 0x001fec, INTEGER_OFFSET, -7211,
                    0x001fed, 0x001fed, INTEGER_OFFSET, -8005,
                    0x001ff2, 0x001ff4, DIRECT_SINGLE, 969,
                    0x001ff6, 0x001ff7, DIRECT_SINGLE, 969,
                    0x001ff8, 0x001ff8, INTEGER_OFFSET, -7225,
                    0x001ff9, 0x001ff9, INTEGER_OFFSET, -128,
                    0x001ffa, 0x001ffa, INTEGER_OFFSET, -7217,
                    0x001ffb, 0x001ffb, INTEGER_OFFSET, -126,
                    0x001ffc, 0x001ffc, INTEGER_OFFSET, -7219,
                    0x002070, 0x002070, INTEGER_OFFSET, -8256,
                    0x002074, 0x002079, INTEGER_OFFSET, -8256,
                    0x00207f, 0x00207f, INTEGER_OFFSET, -8209,
                    0x002080, 0x002089, INTEGER_OFFSET, -8272,
                    0x002102, 0x002103, DIRECT_SINGLE, 99,
                    0x002105, 0x002105, INTEGER_OFFSET, 1105814,
                    0x002109, 0x00210b, INTEGER_OFFSET, -8355,
                    0x00210c, 0x00210f, DIRECT_SINGLE, 104,
                    0x002110, 0x002111, DIRECT_SINGLE, 105,
                    0x002112, 0x002113, DIRECT_SINGLE, 108,
                    0x002115, 0x002115, INTEGER_OFFSET, -8359,
                    0x002116, 0x002116, INTEGER_OFFSET, 1105798,
                    0x002119, 0x00211b, INTEGER_OFFSET, -8361,
                    0x00211c, 0x00211d, DIRECT_SINGLE, 114,
                    0x002120, 0x002122, INTEGER_OFFSET, 1105789,
                    0x002124, 0x002124, INTEGER_OFFSET, -8362,
                    0x002126, 0x002126, INTEGER_OFFSET, -7517,
                    0x002128, 0x002128, INTEGER_OFFSET, -8366,
                    0x00212a, 0x00212a, INTEGER_OFFSET, -8383,
                    0x00212b, 0x00212c, INTEGER_OFFSET, -8394,
                    0x00212f, 0x002130, DIRECT_SINGLE, 101,
                    0x002131, 0x002131, INTEGER_OFFSET, -8395,
                    0x002133, 0x002133, INTEGER_OFFSET, -8390,
                    0x002134, 0x002134, INTEGER_OFFSET, -8389,
                    0x00215f, 0x00215f, INTEGER_OFFSET, -8494,
                    0x002160, 0x002160, INTEGER_OFFSET, -8439,
                    0x002161, 0x002163, INTEGER_OFFSET, 1105727,
                    0x002164, 0x002164, INTEGER_OFFSET, -8430,
                    0x002165, 0x002168, INTEGER_OFFSET, 1105726,
                    0x002169, 0x002169, INTEGER_OFFSET, -8433,
                    0x00216a, 0x00216b, INTEGER_OFFSET, 1105725,
                    0x00216c, 0x00216c, INTEGER_OFFSET, -8448,
                    0x00216d, 0x00216e, INTEGER_OFFSET, -8458,
                    0x00216f, 0x00216f, INTEGER_OFFSET, -8450,
                    0x002170, 0x002170, INTEGER_OFFSET, -8455,
                    0x002171, 0x002173, INTEGER_OFFSET, 1105711,
                    0x002174, 0x002174, INTEGER_OFFSET, -8446,
                    0x002175, 0x002178, INTEGER_OFFSET, 1105710,
                    0x002179, 0x002179, INTEGER_OFFSET, -8449,
                    0x00217a, 0x00217b, INTEGER_OFFSET, 1105709,
                    0x00217c, 0x00217c, INTEGER_OFFSET, -8464,
                    0x00217d, 0x00217e, INTEGER_OFFSET, -8474,
                    0x00217f, 0x00217f, INTEGER_OFFSET, -8466,
                    0x00219a, 0x00219a, INTEGER_OFFSET, -10,
                    0x00219b, 0x00219b, INTEGER_OFFSET, -9,
                    0x0021ae, 0x0021ae, INTEGER_OFFSET, -26,
                    0x0021cd, 0x0021cd, INTEGER_OFFSET, 3,
                    0x0021ce, 0x0021ce, INTEGER_OFFSET, 6,
                    0x0021cf, 0x0021cf, INTEGER_OFFSET, 3,
                    0x002204, 0x002204, INTEGER_OFFSET, -1,
                    0x002209, 0x002209, INTEGER_OFFSET, -1,
                    0x00220c, 0x00220c, INTEGER_OFFSET, -1,
                    0x002222, 0x002222, ALTERNATING_AL, 0,
                    0x002224, 0x002224, INTEGER_OFFSET, -1,
                    0x002226, 0x002226, INTEGER_OFFSET, -1,
                    0x002241, 0x002241, INTEGER_OFFSET, -5,
                    0x002244, 0x002244, INTEGER_OFFSET, -1,
                    0x002247, 0x002247, INTEGER_OFFSET, -2,
                    0x002249, 0x002249, INTEGER_OFFSET, -1,
                    0x002260, 0x002260, INTEGER_OFFSET, -8739,
                    0x002262, 0x002262, INTEGER_OFFSET, -1,
                    0x00226d, 0x00226d, INTEGER_OFFSET, -32,
                    0x00226e, 0x00226e, INTEGER_OFFSET, -8754,
                    0x00226f, 0x00226f, INTEGER_OFFSET, -8753,
                    0x002270, 0x002271, INTEGER_OFFSET, -12,
                    0x002274, 0x002275, INTEGER_OFFSET, -2,
                    0x002278, 0x002279, INTEGER_OFFSET, -2,
                    0x002280, 0x002281, INTEGER_OFFSET, -6,
                    0x002284, 0x002285, INTEGER_OFFSET, -2,
                    0x002288, 0x002289, INTEGER_OFFSET, -2,
                    0x0022ac, 0x0022ac, INTEGER_OFFSET, -10,
                    0x0022ad, 0x0022ae, INTEGER_OFFSET, -5,
                    0x0022af, 0x0022af, INTEGER_OFFSET, -4,
                    0x0022e0, 0x0022e1, INTEGER_OFFSET, -100,
                    0x0022e2, 0x0022e3, INTEGER_OFFSET, -81,
                    0x0022ea, 0x0022ed, INTEGER_OFFSET, -56,
                    0x002460, 0x002468, INTEGER_OFFSET, -9263,
                    0x002474, 0x00247c, INTEGER_OFFSET, -9283,
                    0x002488, 0x002490, INTEGER_OFFSET, -9303,
                    0x00249c, 0x0024b5, INTEGER_OFFSET, -9275,
                    0x0024b6, 0x0024cf, INTEGER_OFFSET, -9301,
                    0x0024d0, 0x0024e9, INTEGER_OFFSET, -9327,
                    0x0024ea, 0x0024ea, INTEGER_OFFSET, -9402,
                    0x00277d, 0x00277e, INTEGER_OFFSET, -10053,
                    0x002787, 0x002788, INTEGER_OFFSET, -10063,
                    0x002791, 0x002792, INTEGER_OFFSET, -10073,
                    0x003007, 0x003007, INTEGER_OFFSET, -12247,
                    0x003021, 0x003029, INTEGER_OFFSET, -12272,
                    0x00304c, 0x00304c, INTEGER_OFFSET, -1,
                    0x00304e, 0x00304e, INTEGER_OFFSET, -1,
                    0x003050, 0x003050, INTEGER_OFFSET, -1,
                    0x003052, 0x003052, INTEGER_OFFSET, -1,
                    0x003054, 0x003054, INTEGER_OFFSET, -1,
                    0x003056, 0x003056, INTEGER_OFFSET, -1,
                    0x003058, 0x003058, INTEGER_OFFSET, -1,
                    0x00305a, 0x00305a, INTEGER_OFFSET, -1,
                    0x00305c, 0x00305c, INTEGER_OFFSET, -1,
                    0x00305e, 0x00305e, INTEGER_OFFSET, -1,
                    0x003060, 0x003060, INTEGER_OFFSET, -1,
                    0x003062, 0x003062, INTEGER_OFFSET, -1,
                    0x003065, 0x003065, INTEGER_OFFSET, -1,
                    0x003067, 0x003067, INTEGER_OFFSET, -1,
                    0x003069, 0x003069, INTEGER_OFFSET, -1,
                    0x003070, 0x003071, DIRECT_SINGLE, 12399,
                    0x003073, 0x003074, DIRECT_SINGLE, 12402,
                    0x003076, 0x003077, DIRECT_SINGLE, 12405,
                    0x003079, 0x00307a, DIRECT_SINGLE, 12408,
                    0x00307c, 0x00307d, DIRECT_SINGLE, 12411,
                    0x003094, 0x003094, INTEGER_OFFSET, -78,
                    0x00309e, 0x00309e, INTEGER_OFFSET, -1,
                    0x0030ac, 0x0030ac, INTEGER_OFFSET, -1,
                    0x0030ae, 0x0030ae, INTEGER_OFFSET, -1,
                    0x0030b0, 0x0030b0, INTEGER_OFFSET, -1,
                    0x0030b2, 0x0030b2, INTEGER_OFFSET, -1,
                    0x0030b4, 0x0030b4, INTEGER_OFFSET, -1,
                    0x0030b6, 0x0030b6, INTEGER_OFFSET, -1,
                    0x0030b8, 0x0030b8, INTEGER_OFFSET, -1,
                    0x0030ba, 0x0030ba, INTEGER_OFFSET, -1,
                    0x0030bc, 0x0030bc, INTEGER_OFFSET, -1,
                    0x0030be, 0x0030be, INTEGER_OFFSET, -1,
                    0x0030c0, 0x0030c0, INTEGER_OFFSET, -1,
                    0x0030c2, 0x0030c2, INTEGER_OFFSET, -1,
                    0x0030c5, 0x0030c5, INTEGER_OFFSET, -1,
                    0x0030c7, 0x0030c7, INTEGER_OFFSET, -1,
                    0x0030c9, 0x0030c9, INTEGER_OFFSET, -1,
                    0x0030d0, 0x0030d1, DIRECT_SINGLE, 12495,
                    0x0030d3, 0x0030d4, DIRECT_SINGLE, 12498,
                    0x0030d6, 0x0030d7, DIRECT_SINGLE, 12501,
                    0x0030d9, 0x0030da, DIRECT_SINGLE, 12504,
                    0x0030dc, 0x0030dd, DIRECT_SINGLE, 12507,
                    0x0030f4, 0x0030f4, INTEGER_OFFSET, -78,
                    0x0030f7, 0x0030fa, INTEGER_OFFSET, -8,
                    0x0030fe, 0x0030fe, INTEGER_OFFSET, -1,
                    0x00f8e2, 0x00f8e3, DIRECT_SINGLE, 1102,
                    0x00f8e4, 0x00f8e5, DIRECT_SINGLE, 1099,
                    0x00f8e6, 0x00f8e7, DIRECT_SINGLE, 1098,
                    0x00f8e8, 0x00f8e9, DIRECT_SINGLE, 1091,
                    0x00f8ea, 0x00f8eb, DIRECT_SINGLE, 1086,
                    0x00f8ec, 0x00f8ed, DIRECT_SINGLE, 1080,
                    0x00f8ee, 0x00f8ef, DIRECT_SINGLE, 1101,
                    0x00f8f0, 0x00f8f1, DIRECT_SINGLE, 1072,
                    0x00f8f6, 0x00f8f6, INTEGER_OFFSET, -63615,
                    0x00f8f7, 0x00f8f7, INTEGER_OFFSET, -63625,
                    0x00f8f8, 0x00f8f8, INTEGER_OFFSET, -63631,
                    0x00f8f9, 0x00f8f9, INTEGER_OFFSET, -63618,
                    0x00f8fa, 0x00f8fa, INTEGER_OFFSET, -63633,
                    0x00fb00, 0x00fb00, INTEGER_OFFSET, 1050025,
                    0x00fb01, 0x00fb05, INTEGER_OFFSET, 1049990,
                    0x00fb06, 0x00fb06, INTEGER_OFFSET, 1049989,
                    0x00ff10, 0x00ff19, INTEGER_OFFSET, -65248,
                    0x00ff21, 0x00ff3a, INTEGER_OFFSET, -65216,
                    0x00ff41, 0x00ff5a, INTEGER_OFFSET, -65248,
    });
    public static final CodePointSet FOLDABLE_CHARACTERS = rangeSet(0x000041, 0x00005a, 0x0000b5, 0x0000b5, 0x0000c0, 0x0000d6, 0x0000d8, 0x0000de, 0x000100, 0x000100, 0x000102, 0x000102, 0x000104,
                    0x000104, 0x000106, 0x000106, 0x000108, 0x000108, 0x00010a, 0x00010a, 0x00010c, 0x00010c, 0x00010e, 0x00010e, 0x000110, 0x000110, 0x000112, 0x000112, 0x000114, 0x000114, 0x000116,
                    0x000116, 0x000118, 0x000118, 0x00011a, 0x00011a, 0x00011c, 0x00011c, 0x00011e, 0x00011e, 0x000120, 0x000120, 0x000122, 0x000122, 0x000124, 0x000124, 0x000126, 0x000126, 0x000128,
                    0x000128, 0x00012a, 0x00012a, 0x00012c, 0x00012c, 0x00012e, 0x00012e, 0x000132, 0x000132, 0x000134, 0x000134, 0x000136, 0x000136, 0x000139, 0x000139, 0x00013b, 0x00013b, 0x00013d,
                    0x00013d, 0x00013f, 0x00013f, 0x000141, 0x000141, 0x000143, 0x000143, 0x000145, 0x000145, 0x000147, 0x000147, 0x00014a, 0x00014a, 0x00014c, 0x00014c, 0x00014e, 0x00014e, 0x000150,
                    0x000150, 0x000152, 0x000152, 0x000154, 0x000154, 0x000156, 0x000156, 0x000158, 0x000158, 0x00015a, 0x00015a, 0x00015c, 0x00015c, 0x00015e, 0x00015e, 0x000160, 0x000160, 0x000162,
                    0x000162, 0x000164, 0x000164, 0x000166, 0x000166, 0x000168, 0x000168, 0x00016a, 0x00016a, 0x00016c, 0x00016c, 0x00016e, 0x00016e, 0x000170, 0x000170, 0x000172, 0x000172, 0x000174,
                    0x000174, 0x000176, 0x000176, 0x000178, 0x000179, 0x00017b, 0x00017b, 0x00017d, 0x00017d, 0x00017f, 0x00017f, 0x000181, 0x000182, 0x000184, 0x000184, 0x000186, 0x000187, 0x000189,
                    0x00018b, 0x00018e, 0x000191, 0x000193, 0x000194, 0x000196, 0x000198, 0x00019c, 0x00019d, 0x00019f, 0x0001a0, 0x0001a2, 0x0001a2, 0x0001a4, 0x0001a4, 0x0001a6, 0x0001a7, 0x0001a9,
                    0x0001a9, 0x0001ac, 0x0001ac, 0x0001ae, 0x0001af, 0x0001b1, 0x0001b3, 0x0001b5, 0x0001b5, 0x0001b7, 0x0001b8, 0x0001bc, 0x0001bc, 0x0001c4, 0x0001c5, 0x0001c7, 0x0001c8, 0x0001ca,
                    0x0001cb, 0x0001cd, 0x0001cd, 0x0001cf, 0x0001cf, 0x0001d1, 0x0001d1, 0x0001d3, 0x0001d3, 0x0001d5, 0x0001d5, 0x0001d7, 0x0001d7, 0x0001d9, 0x0001d9, 0x0001db, 0x0001db, 0x0001de,
                    0x0001de, 0x0001e0, 0x0001e0, 0x0001e2, 0x0001e2, 0x0001e4, 0x0001e4, 0x0001e6, 0x0001e6, 0x0001e8, 0x0001e8, 0x0001ea, 0x0001ea, 0x0001ec, 0x0001ec, 0x0001ee, 0x0001ee, 0x0001f1,
                    0x0001f2, 0x0001f4, 0x0001f4, 0x0001f6, 0x0001f8, 0x0001fa, 0x0001fa, 0x0001fc, 0x0001fc, 0x0001fe, 0x0001fe, 0x000200, 0x000200, 0x000202, 0x000202, 0x000204, 0x000204, 0x000206,
                    0x000206, 0x000208, 0x000208, 0x00020a, 0x00020a, 0x00020c, 0x00020c, 0x00020e, 0x00020e, 0x000210, 0x000210, 0x000212, 0x000212, 0x000214, 0x000214, 0x000216, 0x000216, 0x000218,
                    0x000218, 0x00021a, 0x00021a, 0x00021c, 0x00021c, 0x00021e, 0x00021e, 0x000220, 0x000220, 0x000222, 0x000222, 0x000224, 0x000224, 0x000226, 0x000226, 0x000228, 0x000228, 0x00022a,
                    0x00022a, 0x00022c, 0x00022c, 0x00022e, 0x00022e, 0x000230, 0x000230, 0x000232, 0x000232, 0x00023a, 0x00023b, 0x00023d, 0x00023e, 0x000241, 0x000241, 0x000243, 0x000246, 0x000248,
                    0x000248, 0x00024a, 0x00024a, 0x00024c, 0x00024c, 0x00024e, 0x00024e, 0x000345, 0x000345, 0x000370, 0x000370, 0x000372, 0x000372, 0x000376, 0x000376, 0x00037f, 0x00037f, 0x000386,
                    0x000386, 0x000388, 0x00038a, 0x00038c, 0x00038c, 0x00038e, 0x00038f, 0x000391, 0x0003a1, 0x0003a3, 0x0003ab, 0x0003c2, 0x0003c2, 0x0003cf, 0x0003d1, 0x0003d5, 0x0003d6, 0x0003d8,
                    0x0003d8, 0x0003da, 0x0003da, 0x0003dc, 0x0003dc, 0x0003de, 0x0003de, 0x0003e0, 0x0003e0, 0x0003e2, 0x0003e2, 0x0003e4, 0x0003e4, 0x0003e6, 0x0003e6, 0x0003e8, 0x0003e8, 0x0003ea,
                    0x0003ea, 0x0003ec, 0x0003ec, 0x0003ee, 0x0003ee, 0x0003f0, 0x0003f1, 0x0003f4, 0x0003f5, 0x0003f7, 0x0003f7, 0x0003f9, 0x0003fa, 0x0003fd, 0x00042f, 0x000460, 0x000460, 0x000462,
                    0x000462, 0x000464, 0x000464, 0x000466, 0x000466, 0x000468, 0x000468, 0x00046a, 0x00046a, 0x00046c, 0x00046c, 0x00046e, 0x00046e, 0x000470, 0x000470, 0x000472, 0x000472, 0x000474,
                    0x000474, 0x000476, 0x000476, 0x000478, 0x000478, 0x00047a, 0x00047a, 0x00047c, 0x00047c, 0x00047e, 0x00047e, 0x000480, 0x000480, 0x00048a, 0x00048a, 0x00048c, 0x00048c, 0x00048e,
                    0x00048e, 0x000490, 0x000490, 0x000492, 0x000492, 0x000494, 0x000494, 0x000496, 0x000496, 0x000498, 0x000498, 0x00049a, 0x00049a, 0x00049c, 0x00049c, 0x00049e, 0x00049e, 0x0004a0,
                    0x0004a0, 0x0004a2, 0x0004a2, 0x0004a4, 0x0004a4, 0x0004a6, 0x0004a6, 0x0004a8, 0x0004a8, 0x0004aa, 0x0004aa, 0x0004ac, 0x0004ac, 0x0004ae, 0x0004ae, 0x0004b0, 0x0004b0, 0x0004b2,
                    0x0004b2, 0x0004b4, 0x0004b4, 0x0004b6, 0x0004b6, 0x0004b8, 0x0004b8, 0x0004ba, 0x0004ba, 0x0004bc, 0x0004bc, 0x0004be, 0x0004be, 0x0004c0, 0x0004c1, 0x0004c3, 0x0004c3, 0x0004c5,
                    0x0004c5, 0x0004c7, 0x0004c7, 0x0004c9, 0x0004c9, 0x0004cb, 0x0004cb, 0x0004cd, 0x0004cd, 0x0004d0, 0x0004d0, 0x0004d2, 0x0004d2, 0x0004d4, 0x0004d4, 0x0004d6, 0x0004d6, 0x0004d8,
                    0x0004d8, 0x0004da, 0x0004da, 0x0004dc, 0x0004dc, 0x0004de, 0x0004de, 0x0004e0, 0x0004e0, 0x0004e2, 0x0004e2, 0x0004e4, 0x0004e4, 0x0004e6, 0x0004e6, 0x0004e8, 0x0004e8, 0x0004ea,
                    0x0004ea, 0x0004ec, 0x0004ec, 0x0004ee, 0x0004ee, 0x0004f0, 0x0004f0, 0x0004f2, 0x0004f2, 0x0004f4, 0x0004f4, 0x0004f6, 0x0004f6, 0x0004f8, 0x0004f8, 0x0004fa, 0x0004fa, 0x0004fc,
                    0x0004fc, 0x0004fe, 0x0004fe, 0x000500, 0x000500, 0x000502, 0x000502, 0x000504, 0x000504, 0x000506, 0x000506, 0x000508, 0x000508, 0x00050a, 0x00050a, 0x00050c, 0x00050c, 0x00050e,
                    0x00050e, 0x000510, 0x000510, 0x000512, 0x000512, 0x000514, 0x000514, 0x000516, 0x000516, 0x000518, 0x000518, 0x00051a, 0x00051a, 0x00051c, 0x00051c, 0x00051e, 0x00051e, 0x000520,
                    0x000520, 0x000522, 0x000522, 0x000524, 0x000524, 0x000526, 0x000526, 0x000528, 0x000528, 0x00052a, 0x00052a, 0x00052c, 0x00052c, 0x00052e, 0x00052e, 0x000531, 0x000556, 0x0010a0,
                    0x0010c5, 0x0010c7, 0x0010c7, 0x0010cd, 0x0010cd, 0x0013f8, 0x0013fd, 0x001c80, 0x001c88, 0x001c90, 0x001cba, 0x001cbd, 0x001cbf, 0x001e00, 0x001e00, 0x001e02, 0x001e02, 0x001e04,
                    0x001e04, 0x001e06, 0x001e06, 0x001e08, 0x001e08, 0x001e0a, 0x001e0a, 0x001e0c, 0x001e0c, 0x001e0e, 0x001e0e, 0x001e10, 0x001e10, 0x001e12, 0x001e12, 0x001e14, 0x001e14, 0x001e16,
                    0x001e16, 0x001e18, 0x001e18, 0x001e1a, 0x001e1a, 0x001e1c, 0x001e1c, 0x001e1e, 0x001e1e, 0x001e20, 0x001e20, 0x001e22, 0x001e22, 0x001e24, 0x001e24, 0x001e26, 0x001e26, 0x001e28,
                    0x001e28, 0x001e2a, 0x001e2a, 0x001e2c, 0x001e2c, 0x001e2e, 0x001e2e, 0x001e30, 0x001e30, 0x001e32, 0x001e32, 0x001e34, 0x001e34, 0x001e36, 0x001e36, 0x001e38, 0x001e38, 0x001e3a,
                    0x001e3a, 0x001e3c, 0x001e3c, 0x001e3e, 0x001e3e, 0x001e40, 0x001e40, 0x001e42, 0x001e42, 0x001e44, 0x001e44, 0x001e46, 0x001e46, 0x001e48, 0x001e48, 0x001e4a, 0x001e4a, 0x001e4c,
                    0x001e4c, 0x001e4e, 0x001e4e, 0x001e50, 0x001e50, 0x001e52, 0x001e52, 0x001e54, 0x001e54, 0x001e56, 0x001e56, 0x001e58, 0x001e58, 0x001e5a, 0x001e5a, 0x001e5c, 0x001e5c, 0x001e5e,
                    0x001e5e, 0x001e60, 0x001e60, 0x001e62, 0x001e62, 0x001e64, 0x001e64, 0x001e66, 0x001e66, 0x001e68, 0x001e68, 0x001e6a, 0x001e6a, 0x001e6c, 0x001e6c, 0x001e6e, 0x001e6e, 0x001e70,
                    0x001e70, 0x001e72, 0x001e72, 0x001e74, 0x001e74, 0x001e76, 0x001e76, 0x001e78, 0x001e78, 0x001e7a, 0x001e7a, 0x001e7c, 0x001e7c, 0x001e7e, 0x001e7e, 0x001e80, 0x001e80, 0x001e82,
                    0x001e82, 0x001e84, 0x001e84, 0x001e86, 0x001e86, 0x001e88, 0x001e88, 0x001e8a, 0x001e8a, 0x001e8c, 0x001e8c, 0x001e8e, 0x001e8e, 0x001e90, 0x001e90, 0x001e92, 0x001e92, 0x001e94,
                    0x001e94, 0x001e9b, 0x001e9b, 0x001e9e, 0x001e9e, 0x001ea0, 0x001ea0, 0x001ea2, 0x001ea2, 0x001ea4, 0x001ea4, 0x001ea6, 0x001ea6, 0x001ea8, 0x001ea8, 0x001eaa, 0x001eaa, 0x001eac,
                    0x001eac, 0x001eae, 0x001eae, 0x001eb0, 0x001eb0, 0x001eb2, 0x001eb2, 0x001eb4, 0x001eb4, 0x001eb6, 0x001eb6, 0x001eb8, 0x001eb8, 0x001eba, 0x001eba, 0x001ebc, 0x001ebc, 0x001ebe,
                    0x001ebe, 0x001ec0, 0x001ec0, 0x001ec2, 0x001ec2, 0x001ec4, 0x001ec4, 0x001ec6, 0x001ec6, 0x001ec8, 0x001ec8, 0x001eca, 0x001eca, 0x001ecc, 0x001ecc, 0x001ece, 0x001ece, 0x001ed0,
                    0x001ed0, 0x001ed2, 0x001ed2, 0x001ed4, 0x001ed4, 0x001ed6, 0x001ed6, 0x001ed8, 0x001ed8, 0x001eda, 0x001eda, 0x001edc, 0x001edc, 0x001ede, 0x001ede, 0x001ee0, 0x001ee0, 0x001ee2,
                    0x001ee2, 0x001ee4, 0x001ee4, 0x001ee6, 0x001ee6, 0x001ee8, 0x001ee8, 0x001eea, 0x001eea, 0x001eec, 0x001eec, 0x001eee, 0x001eee, 0x001ef0, 0x001ef0, 0x001ef2, 0x001ef2, 0x001ef4,
                    0x001ef4, 0x001ef6, 0x001ef6, 0x001ef8, 0x001ef8, 0x001efa, 0x001efa, 0x001efc, 0x001efc, 0x001efe, 0x001efe, 0x001f08, 0x001f0f, 0x001f18, 0x001f1d, 0x001f28, 0x001f2f, 0x001f38,
                    0x001f3f, 0x001f48, 0x001f4d, 0x001f59, 0x001f59, 0x001f5b, 0x001f5b, 0x001f5d, 0x001f5d, 0x001f5f, 0x001f5f, 0x001f68, 0x001f6f, 0x001f88, 0x001f8f, 0x001f98, 0x001f9f, 0x001fa8,
                    0x001faf, 0x001fb8, 0x001fbc, 0x001fbe, 0x001fbe, 0x001fc8, 0x001fcc, 0x001fd3, 0x001fd3, 0x001fd8, 0x001fdb, 0x001fe3, 0x001fe3, 0x001fe8, 0x001fec, 0x001ff8, 0x001ffc, 0x002126,
                    0x002126, 0x00212a, 0x00212b, 0x002132, 0x002132, 0x002160, 0x00216f, 0x002183, 0x002183, 0x0024b6, 0x0024cf, 0x002c00, 0x002c2f, 0x002c60, 0x002c60, 0x002c62, 0x002c64, 0x002c67,
                    0x002c67, 0x002c69, 0x002c69, 0x002c6b, 0x002c6b, 0x002c6d, 0x002c70, 0x002c72, 0x002c72, 0x002c75, 0x002c75, 0x002c7e, 0x002c80, 0x002c82, 0x002c82, 0x002c84, 0x002c84, 0x002c86,
                    0x002c86, 0x002c88, 0x002c88, 0x002c8a, 0x002c8a, 0x002c8c, 0x002c8c, 0x002c8e, 0x002c8e, 0x002c90, 0x002c90, 0x002c92, 0x002c92, 0x002c94, 0x002c94, 0x002c96, 0x002c96, 0x002c98,
                    0x002c98, 0x002c9a, 0x002c9a, 0x002c9c, 0x002c9c, 0x002c9e, 0x002c9e, 0x002ca0, 0x002ca0, 0x002ca2, 0x002ca2, 0x002ca4, 0x002ca4, 0x002ca6, 0x002ca6, 0x002ca8, 0x002ca8, 0x002caa,
                    0x002caa, 0x002cac, 0x002cac, 0x002cae, 0x002cae, 0x002cb0, 0x002cb0, 0x002cb2, 0x002cb2, 0x002cb4, 0x002cb4, 0x002cb6, 0x002cb6, 0x002cb8, 0x002cb8, 0x002cba, 0x002cba, 0x002cbc,
                    0x002cbc, 0x002cbe, 0x002cbe, 0x002cc0, 0x002cc0, 0x002cc2, 0x002cc2, 0x002cc4, 0x002cc4, 0x002cc6, 0x002cc6, 0x002cc8, 0x002cc8, 0x002cca, 0x002cca, 0x002ccc, 0x002ccc, 0x002cce,
                    0x002cce, 0x002cd0, 0x002cd0, 0x002cd2, 0x002cd2, 0x002cd4, 0x002cd4, 0x002cd6, 0x002cd6, 0x002cd8, 0x002cd8, 0x002cda, 0x002cda, 0x002cdc, 0x002cdc, 0x002cde, 0x002cde, 0x002ce0,
                    0x002ce0, 0x002ce2, 0x002ce2, 0x002ceb, 0x002ceb, 0x002ced, 0x002ced, 0x002cf2, 0x002cf2, 0x00a640, 0x00a640, 0x00a642, 0x00a642, 0x00a644, 0x00a644, 0x00a646, 0x00a646, 0x00a648,
                    0x00a648, 0x00a64a, 0x00a64a, 0x00a64c, 0x00a64c, 0x00a64e, 0x00a64e, 0x00a650, 0x00a650, 0x00a652, 0x00a652, 0x00a654, 0x00a654, 0x00a656, 0x00a656, 0x00a658, 0x00a658, 0x00a65a,
                    0x00a65a, 0x00a65c, 0x00a65c, 0x00a65e, 0x00a65e, 0x00a660, 0x00a660, 0x00a662, 0x00a662, 0x00a664, 0x00a664, 0x00a666, 0x00a666, 0x00a668, 0x00a668, 0x00a66a, 0x00a66a, 0x00a66c,
                    0x00a66c, 0x00a680, 0x00a680, 0x00a682, 0x00a682, 0x00a684, 0x00a684, 0x00a686, 0x00a686, 0x00a688, 0x00a688, 0x00a68a, 0x00a68a, 0x00a68c, 0x00a68c, 0x00a68e, 0x00a68e, 0x00a690,
                    0x00a690, 0x00a692, 0x00a692, 0x00a694, 0x00a694, 0x00a696, 0x00a696, 0x00a698, 0x00a698, 0x00a69a, 0x00a69a, 0x00a722, 0x00a722, 0x00a724, 0x00a724, 0x00a726, 0x00a726, 0x00a728,
                    0x00a728, 0x00a72a, 0x00a72a, 0x00a72c, 0x00a72c, 0x00a72e, 0x00a72e, 0x00a732, 0x00a732, 0x00a734, 0x00a734, 0x00a736, 0x00a736, 0x00a738, 0x00a738, 0x00a73a, 0x00a73a, 0x00a73c,
                    0x00a73c, 0x00a73e, 0x00a73e, 0x00a740, 0x00a740, 0x00a742, 0x00a742, 0x00a744, 0x00a744, 0x00a746, 0x00a746, 0x00a748, 0x00a748, 0x00a74a, 0x00a74a, 0x00a74c, 0x00a74c, 0x00a74e,
                    0x00a74e, 0x00a750, 0x00a750, 0x00a752, 0x00a752, 0x00a754, 0x00a754, 0x00a756, 0x00a756, 0x00a758, 0x00a758, 0x00a75a, 0x00a75a, 0x00a75c, 0x00a75c, 0x00a75e, 0x00a75e, 0x00a760,
                    0x00a760, 0x00a762, 0x00a762, 0x00a764, 0x00a764, 0x00a766, 0x00a766, 0x00a768, 0x00a768, 0x00a76a, 0x00a76a, 0x00a76c, 0x00a76c, 0x00a76e, 0x00a76e, 0x00a779, 0x00a779, 0x00a77b,
                    0x00a77b, 0x00a77d, 0x00a77e, 0x00a780, 0x00a780, 0x00a782, 0x00a782, 0x00a784, 0x00a784, 0x00a786, 0x00a786, 0x00a78b, 0x00a78b, 0x00a78d, 0x00a78d, 0x00a790, 0x00a790, 0x00a792,
                    0x00a792, 0x00a796, 0x00a796, 0x00a798, 0x00a798, 0x00a79a, 0x00a79a, 0x00a79c, 0x00a79c, 0x00a79e, 0x00a79e, 0x00a7a0, 0x00a7a0, 0x00a7a2, 0x00a7a2, 0x00a7a4, 0x00a7a4, 0x00a7a6,
                    0x00a7a6, 0x00a7a8, 0x00a7a8, 0x00a7aa, 0x00a7ae, 0x00a7b0, 0x00a7b4, 0x00a7b6, 0x00a7b6, 0x00a7b8, 0x00a7b8, 0x00a7ba, 0x00a7ba, 0x00a7bc, 0x00a7bc, 0x00a7be, 0x00a7be, 0x00a7c0,
                    0x00a7c0, 0x00a7c2, 0x00a7c2, 0x00a7c4, 0x00a7c7, 0x00a7c9, 0x00a7c9, 0x00a7d0, 0x00a7d0, 0x00a7d6, 0x00a7d6, 0x00a7d8, 0x00a7d8, 0x00a7f5, 0x00a7f5, 0x00ab70, 0x00abbf, 0x00fb05,
                    0x00fb05, 0x00ff21, 0x00ff3a, 0x010400, 0x010427, 0x0104b0, 0x0104d3, 0x010570, 0x01057a, 0x01057c, 0x01058a, 0x01058c, 0x010592, 0x010594, 0x010595, 0x010c80, 0x010cb2, 0x0118a0,
                    0x0118bf, 0x016e40, 0x016e5f, 0x01e900, 0x01e921);

    /* GENERATED CODE END - KEEP THIS MARKER FOR AUTOMATIC UPDATES */

    private static final CaseUnfoldingTrie UNFOLDING_TRIE_RUBY = getTable(CaseFoldAlgorithm.Ruby).createCaseUnfoldTrie();
    private static final CaseUnfoldingTrie UNFOLDING_TRIE_ORACLE_DB = getTable(CaseFoldAlgorithm.OracleDB).createCaseUnfoldTrie();
    private static final CaseUnfoldingTrie UNFOLDING_TRIE_ORACLE_DB_AI = getTable(CaseFoldAlgorithm.OracleDBAI).createCaseUnfoldTrie();

    public static final CodePointSet FOLDED_CHARACTERS = FOLDABLE_CHARACTERS.createInverse(Encodings.UTF_32);

}
