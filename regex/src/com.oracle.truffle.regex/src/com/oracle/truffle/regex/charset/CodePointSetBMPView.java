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

import com.oracle.truffle.regex.tregex.buffer.IntRangesBuffer;

public final class CodePointSetBMPView extends ImmutableSortedListOfIntRanges {

    public static final int MIN_VALUE = Character.MIN_VALUE;
    public static final int MAX_VALUE = Character.MAX_VALUE;

    private static final CodePointSetBMPView EMPTY = new CodePointSetBMPView(new int[]{});
    private static final CodePointSetBMPView FULL = new CodePointSetBMPView(new int[]{MIN_VALUE, MAX_VALUE});

    private final int size;

    private CodePointSetBMPView(int[] ranges) {
        super(ranges);
        this.size = viewSize(ranges);
    }

    private static int viewSize(int[] ranges) {
        if (ranges.length == 0) {
            return 0;
        }
        if (ranges[ranges.length - 2] > MAX_VALUE) {
            assert ranges[ranges.length - 2] == Character.MAX_VALUE + 1 && ranges[ranges.length - 1] == Character.MAX_CODE_POINT;
            return (ranges.length / 2) - 1;
        } else {
            return ranges.length / 2;
        }
    }

    public static CodePointSetBMPView create(IntRangesBuffer buf) {
        return new CodePointSetBMPView(buf.toArray());
    }

    public static CodePointSetBMPView create(CodePointSet cps) {
        return new CodePointSetBMPView(cps.getRanges());
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int getLo(int i) {
        int lo = super.getLo(i);
        assert lo <= MAX_VALUE;
        return lo;
    }

    @Override
    public int getHi(int i) {
        int hi = super.getHi(i);
        assert hi <= MAX_VALUE || hi == Character.MAX_CODE_POINT;
        return (char) hi;
    }

    @Override
    public int[] toArray() {
        return size * 2 == ranges.length ? ranges : Arrays.copyOf(ranges, size * 2);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CodePointSetBMPView createEmpty() {
        return EMPTY;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CodePointSetBMPView createFull() {
        return FULL;
    }

    @SuppressWarnings("unchecked")
    @Override
    public CodePointSetBMPView create(RangesBuffer buffer) {
        assert buffer instanceof IntRangesBuffer;
        return create((IntRangesBuffer) buffer);
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
    public CodePointSetBMPView createInverse() {
        if (isEmpty()) {
            return createFull();
        }
        return new CodePointSetBMPView(createInverseArray(this));
    }

    @Override
    public boolean equalsBuffer(RangesBuffer buffer) {
        assert buffer instanceof IntRangesBuffer;
        IntRangesBuffer buf = (IntRangesBuffer) buffer;
        if (isEmpty()) {
            return buf.isEmpty();
        }
        return size() == buf.length() * 2 && rangesEqual(ranges, buf.getBuffer(), (size() * 2) - 1) && getMax() == (char) buf.getMax();
    }
}
