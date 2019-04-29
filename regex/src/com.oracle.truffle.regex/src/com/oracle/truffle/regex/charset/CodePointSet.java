/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.charset;

import static com.oracle.truffle.regex.charset.Constants.MAX_CODEPOINT;
import static com.oracle.truffle.regex.charset.Constants.MIN_CODEPOINT;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntRangesBuffer;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public final class CodePointSet extends SortedListOfRanges implements JsonConvertible {

    private static final CodePointSet CONSTANT_EMPTY = new CodePointSet(new int[0]);
    private static final CodePointSet CONSTANT_FULL = new CodePointSet(new int[]{MIN_CODEPOINT, MAX_CODEPOINT});

    private static final CodePointSet[] CONSTANT_ASCII = new CodePointSet[128];
    private static final CodePointSet[] CONSTANT_INVERSE_ASCII = new CodePointSet[128];
    private static final CodePointSet[] CONSTANT_CASE_FOLD_ASCII = new CodePointSet[26];

    static {
        CONSTANT_ASCII[0] = new CodePointSet(new int[]{0, 0});
        CONSTANT_INVERSE_ASCII[0] = new CodePointSet(new int[]{1, MAX_CODEPOINT});
        for (int i = 1; i < 128; i++) {
            CONSTANT_ASCII[i] = new CodePointSet(new int[]{i, i});
            CONSTANT_INVERSE_ASCII[i] = new CodePointSet(new int[]{0, i - 1, i + 1, MAX_CODEPOINT});
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            CONSTANT_CASE_FOLD_ASCII[i - 'A'] = new CodePointSet(new int[]{i, i, Character.toLowerCase(i), Character.toLowerCase(i)});
        }
    }

    private final int[] ranges;

    private CodePointSet(int[] ranges) {
        this.ranges = ranges;
        assert (ranges.length & 1) == 0 : "ranges array must have an even length!";
        assert rangesAreSortedAndDisjoint();
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

    public static CodePointSet create(int... ranges) {
        CodePointSet constant = checkConstants(ranges, ranges.length);
        if (constant == null) {
            return new CodePointSet(ranges);
        }
        return constant;
    }

    public static CodePointSet createNoDedup(int... ranges) {
        return new CodePointSet(ranges);
    }

    private static CodePointSet checkConstants(int[] ranges, int length) {
        if (length == 0) {
            return CONSTANT_EMPTY;
        }
        if (length == 1) {
            if (ranges[0] < 128) {
                return CONSTANT_ASCII[ranges[0]];
            }
            return new CodePointSet(new int[]{ranges[0], ranges[0]});
        }
        if (length == 2) {
            if (ranges[0] == ranges[1] && ranges[0] < 128) {
                return CONSTANT_ASCII[ranges[0]];
            }
            if (ranges[0] == MIN_CODEPOINT && ranges[1] == MAX_CODEPOINT) {
                return CONSTANT_FULL;
            }
        }
        if (length == 4) {
            CodePointSet ret = checkInverseAndCaseFoldAscii(ranges[0], ranges[1], ranges[2], ranges[3]);
            if (ret != null) {
                return ret;
            }
        }
        for (CodePointSet predefCC : Constants.CONSTANT_CODE_POINT_SETS) {
            if (predefCC.ranges.length == length && rangesEqual(predefCC.ranges, ranges, length)) {
                return predefCC;
            }
        }
        return null;
    }

    private static boolean rangesEqual(int[] a, int[] b, int length) {
        for (int i = 0; i < length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static CodePointSet checkInverseAndCaseFoldAscii(int lo0, int hi0, int lo1, int hi1) {
        if (lo0 == MIN_CODEPOINT && hi1 == MAX_CODEPOINT && lo1 <= 128 && hi0 + 2 == lo1) {
            return CONSTANT_INVERSE_ASCII[hi0 + 1];
        }
        if (lo0 == hi0 && lo0 >= 'A' && lo0 <= 'Z' && lo1 == hi1 && lo1 == Character.toLowerCase(lo0)) {
            return CONSTANT_CASE_FOLD_ASCII[lo0 - 'A'];
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected CodePointSet createEmpty() {
        return getEmpty();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected CodePointSet createFull() {
        return getFull();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected CodePointSet create(RangesBuffer buffer) {
        assert buffer instanceof IntRangesBuffer;
        IntRangesBuffer buf = (IntRangesBuffer) buffer;
        CodePointSet constant = checkConstants(buf.getBuffer(), buf.length());
        if (constant == null) {
            return new CodePointSet(buf.toArray());
        }
        return constant;
    }

    @Override
    protected int getMinValue() {
        return MIN_CODEPOINT;
    }

    @Override
    protected int getMaxValue() {
        return MAX_CODEPOINT;
    }

    @Override
    public int getLo(int i) {
        return ranges[i * 2];
    }

    @Override
    public int getHi(int i) {
        return ranges[(i * 2) + 1];
    }

    @Override
    public int size() {
        return ranges.length / 2;
    }

    @Override
    protected IntRangesBuffer getBuffer1(CompilationBuffer compilationBuffer) {
        return compilationBuffer.getIntRangesBuffer1();
    }

    @Override
    protected IntRangesBuffer getBuffer2(CompilationBuffer compilationBuffer) {
        return compilationBuffer.getIntRangesBuffer2();
    }

    @Override
    protected IntRangesBuffer getBuffer3(CompilationBuffer compilationBuffer) {
        return compilationBuffer.getIntRangesBuffer3();
    }

    @Override
    protected IntRangesBuffer createTempBuffer() {
        return new IntRangesBuffer();
    }

    @Override
    protected void addRangeBulkTo(RangesBuffer buffer, int startIndex, int endIndex) {
        int bulkLength = (endIndex - startIndex) * 2;
        if (bulkLength == 0) {
            return;
        }
        assert buffer instanceof IntRangesBuffer;
        IntRangesBuffer buf = (IntRangesBuffer) buffer;
        int newSize = buf.length() + bulkLength;
        buf.ensureCapacity(newSize);
        System.arraycopy(ranges, startIndex * 2, buf.getBuffer(), buf.length(), bulkLength);
        buf.setLength(newSize);
    }

    @Override
    protected boolean equalsBuffer(RangesBuffer buffer) {
        assert buffer instanceof IntRangesBuffer;
        IntRangesBuffer buf = (IntRangesBuffer) buffer;
        return ranges.length == buf.length() && rangesEqual(ranges, buf.getBuffer(), ranges.length);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CodePointSet) {
            return Arrays.equals(ranges, ((CodePointSet) obj).ranges);
        }
        return super.equals(obj);
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
    public String dumpRaw() {
        StringBuilder sb = new StringBuilder(size() * 20);
        for (int i = 0; i < size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format("0x%06x, 0x%06x", getLo(i), getHi(i)));
        }
        return sb.toString();
    }
}
