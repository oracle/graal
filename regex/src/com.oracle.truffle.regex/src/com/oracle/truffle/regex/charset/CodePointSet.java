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
package com.oracle.truffle.regex.charset;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.buffer.IntRangesBuffer;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public final class CodePointSet extends ImmutableSortedListOfIntRanges implements Comparable<CodePointSet>, JsonConvertible {

    public static final int MIN_VALUE = Character.MIN_CODE_POINT;
    public static final int MAX_VALUE = Character.MAX_CODE_POINT;
    private static final CodePointSet CONSTANT_EMPTY = new CodePointSet(new int[0]);
    private static final CodePointSet CONSTANT_FULL = new CodePointSet(new int[]{MIN_VALUE, MAX_VALUE});

    private static final CodePointSet[] CONSTANT_ASCII = new CodePointSet[128];
    private static final CodePointSet[] CONSTANT_INVERSE_ASCII = new CodePointSet[128];
    private static final CodePointSet[] CONSTANT_CASE_FOLD_ASCII = new CodePointSet[26];

    static {
        CONSTANT_ASCII[0] = new CodePointSet(new int[]{0, 0});
        CONSTANT_INVERSE_ASCII[0] = new CodePointSet(new int[]{1, Character.MAX_CODE_POINT});
        for (int i = 1; i < 128; i++) {
            CONSTANT_ASCII[i] = new CodePointSet(new int[]{i, i});
            CONSTANT_INVERSE_ASCII[i] = new CodePointSet(new int[]{0, i - 1, i + 1, Character.MAX_CODE_POINT});
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            CONSTANT_CASE_FOLD_ASCII[i - 'A'] = new CodePointSet(new int[]{i, i, Character.toLowerCase(i), Character.toLowerCase(i)});
        }
    }

    private CodePointSet(int[] ranges) {
        super(ranges);
        assert ranges.length == 0 || ranges[0] >= MIN_VALUE && ranges[ranges.length - 1] <= MAX_VALUE;
    }

    public int[] getRanges() {
        return ranges;
    }

    public static CodePointSet getEmpty() {
        return CONSTANT_EMPTY;
    }

    public static CodePointSet getFull() {
        return CONSTANT_FULL;
    }

    public static CodePointSet createNoDedup(int... ranges) {
        return new CodePointSet(ranges);
    }

    public static CodePointSet create(int single) {
        if (single < 128) {
            return CONSTANT_ASCII[single];
        }
        return new CodePointSet(new int[]{single, single});
    }

    public static CodePointSet create(int... ranges) {
        CodePointSet constant = checkConstants(ranges, ranges.length);
        if (constant == null) {
            return new CodePointSet(ranges);
        }
        return constant;
    }

    public static CodePointSet create(IntRangesBuffer buf) {
        CodePointSet constant = checkConstants(buf.getBuffer(), buf.length());
        if (constant == null) {
            return new CodePointSet(buf.toArray());
        }
        return constant;
    }

    private static CodePointSet checkConstants(int[] ranges, int length) {
        if (length == 0) {
            return CONSTANT_EMPTY;
        }
        if (length == 2) {
            if (ranges[0] == ranges[1] && ranges[0] < 128) {
                return CONSTANT_ASCII[ranges[0]];
            }
            if (ranges[0] == Character.MIN_CODE_POINT && ranges[1] == Character.MAX_CODE_POINT) {
                return CONSTANT_FULL;
            }
        }
        if (length == 4) {
            if (ranges[0] == Character.MIN_CODE_POINT && ranges[3] == Character.MAX_CODE_POINT && ranges[2] <= 128 && ranges[1] + 2 == ranges[2]) {
                return CONSTANT_INVERSE_ASCII[ranges[1] + 1];
            }
            if (ranges[0] == ranges[1] && ranges[0] >= 'A' && ranges[0] <= 'Z' && ranges[2] == ranges[3] && ranges[2] == (ranges[0] | 0x20)) {
                return CONSTANT_CASE_FOLD_ASCII[ranges[0] - 'A'];
            }
        }
        for (CodePointSet predefCC : Constants.CONSTANT_CODE_POINT_SETS) {
            if (predefCC.ranges.length == length && rangesEqual(predefCC.ranges, ranges, length)) {
                return predefCC;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CodePointSet createEmpty() {
        return getEmpty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public CodePointSet createFull() {
        return getFull();
    }

    @SuppressWarnings("unchecked")
    @Override
    public CodePointSet create(RangesBuffer buffer) {
        assert buffer instanceof IntRangesBuffer;
        return create((IntRangesBuffer) buffer);
    }

    @Override
    public boolean equalsBuffer(RangesBuffer buffer) {
        assert buffer instanceof IntRangesBuffer;
        IntRangesBuffer buf = (IntRangesBuffer) buffer;
        return ranges.length == buf.length() && rangesEqual(ranges, buf.getBuffer(), ranges.length);
    }

    @Override
    public int getMinValue() {
        return MIN_VALUE;
    }

    @Override
    public int getMaxValue() {
        return MAX_VALUE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CodePointSet createInverse() {
        return createInverse(this);
    }

    public static CodePointSet createInverse(SortedListOfRanges src) {
        assert src.getMinValue() == Character.MIN_CODE_POINT;
        assert src.getMaxValue() == Character.MAX_CODE_POINT;
        if (src.matchesNothing()) {
            return getFull();
        }
        if (src.matchesSingleAscii()) {
            return CONSTANT_INVERSE_ASCII[src.getMin()];
        }
        return new CodePointSet(createInverseArray(src));
    }

    @Override
    public int compareTo(CodePointSet o) {
        if (this == o) {
            return 0;
        }
        if (matchesEverything()) {
            if (o.matchesEverything()) {
                return 0;
            }
            return 1;
        }
        if (matchesNothing()) {
            if (o.matchesNothing()) {
                return 0;
            }
            return -1;
        }
        if (o.matchesEverything()) {
            return -1;
        }
        if (o.matchesNothing()) {
            return 1;
        }
        int cmp = size() - o.size();
        if (cmp != 0) {
            return cmp;
        }
        for (int i = 0; i < size(); i++) {
            cmp = getLo(i) - o.getLo(i);
            if (cmp != 0) {
                return cmp;
            }
        }
        return cmp;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof CodePointSet) {
            return Arrays.equals(ranges, ((CodePointSet) obj).ranges);
        }
        if (obj instanceof SortedListOfRanges) {
            return equalsListOfRanges((SortedListOfRanges) obj);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ranges);
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.array(ranges);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return defaultToString();
    }

    @TruffleBoundary
    public String dumpRaw() {
        StringBuilder sb = new StringBuilder(size() * 20);
        for (int i = 0; i < size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(String.format("0x%06x, 0x%06x", getLo(i), getHi(i)));
        }
        return sb.toString();
    }

    @Override
    public int[] toArray() {
        return getRanges();
    }

    public char[] inverseToCharArray() {
        char[] array = new char[inverseValueCount()];
        int index = 0;
        int lastHi = -1;
        for (int i = 0; i < size(); i++) {
            for (int j = lastHi + 1; j < getLo(i); j++) {
                array[index++] = (char) j;
            }
            lastHi = getHi(i);
        }
        for (int j = lastHi + 1; j <= getMaxValue(); j++) {
            array[index++] = (char) j;
        }
        return array;
    }
}
