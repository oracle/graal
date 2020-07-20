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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntRangesBuffer;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;
import com.oracle.truffle.regex.util.BitSets;

public abstract class ImmutableSortedListOfIntRanges implements ImmutableSortedListOfRanges {

    protected final int[] ranges;

    protected ImmutableSortedListOfIntRanges(int[] ranges) {
        this.ranges = ranges;
        assert (ranges.length & 1) == 0 : "ranges array must have an even length!";
        assert rangesAreSortedNonAdjacentAndDisjoint();
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
    public IntRangesBuffer getBuffer1(CompilationBuffer compilationBuffer) {
        return compilationBuffer.getIntRangesBuffer1();
    }

    @Override
    public IntRangesBuffer getBuffer2(CompilationBuffer compilationBuffer) {
        return compilationBuffer.getIntRangesBuffer2();
    }

    @Override
    public IntRangesBuffer getBuffer3(CompilationBuffer compilationBuffer) {
        return compilationBuffer.getIntRangesBuffer3();
    }

    @Override
    public IntRangesBuffer createTempBuffer() {
        return new IntRangesBuffer();
    }

    @Override
    public void appendRangesTo(RangesBuffer buffer, int startIndex, int endIndex) {
        int bulkLength = (endIndex - startIndex) * 2;
        if (bulkLength == 0) {
            return;
        }
        assert buffer instanceof IntRangesBuffer;
        IntRangesBuffer buf = (IntRangesBuffer) buffer;
        int newSize = buf.length() + bulkLength;
        buf.ensureCapacity(newSize);
        assert buf.isEmpty() || rightOf(startIndex, buf, buf.size() - 1);
        System.arraycopy(ranges, startIndex * 2, buf.getBuffer(), buf.length(), bulkLength);
        buf.setLength(newSize);
    }

    public abstract int[] toArray();

    /**
     * Returns {@code true} iff not all values of this range set have the same high byte, but that
     * would be the case in the inverse of this range set.
     */
    public boolean inverseIsSameHighByte(Encoding encoding) {
        if (isEmpty()) {
            return false;
        }
        if (BitSets.highByte(getMin()) == BitSets.highByte(getMax())) {
            return false;
        }
        return matchesMinAndMax(encoding) && BitSets.highByte(getHi(0) + 1) == BitSets.highByte(getLo(size() - 1) - 1);
    }

    protected static int[] createInverseArray(SortedListOfRanges src, Encoding encoding) {
        int[] invRanges = new int[src.sizeOfInverse(encoding) * 2];
        int i = 0;
        if (src.getMin() > encoding.getMinValue()) {
            setRange(invRanges, i++, encoding.getMinValue(), src.getMin() - 1);
        }
        for (int ia = 1; ia < src.size(); ia++) {
            setRange(invRanges, i++, src.getHi(ia - 1) + 1, src.getLo(ia) - 1);
        }
        if (src.getMax() < encoding.getMaxValue()) {
            setRange(invRanges, i++, src.getMax() + 1, encoding.getMaxValue());
        }
        return invRanges;
    }

    private static void setRange(int[] arr, int i, int lo, int hi) {
        arr[i * 2] = lo;
        arr[i * 2 + 1] = hi;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return defaultToString();
    }

    protected static boolean rangesEqual(int[] a, int[] b, int length) {
        for (int i = 0; i < length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }
}
