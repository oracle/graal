/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.regex.tregex.buffer;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.charset.RangesBuffer;

/**
 * Extension of {@link CharArrayBuffer} that adds convenience functions for arrays of character
 * ranges in the form:
 *
 * <pre>
 * [
 *     inclusive lower bound of range 1, inclusive upper bound of range 1,
 *     inclusive lower bound of range 2, inclusive upper bound of range 2,
 *     inclusive lower bound of range 3, inclusive upper bound of range 3,
 *     ...
 * ]
 * </pre>
 */
public class CharRangesBuffer extends CharArrayBuffer implements RangesBuffer {

    public CharRangesBuffer() {
        this(16);
    }

    public CharRangesBuffer(int initialSize) {
        super(initialSize);
    }

    @Override
    public int getMinValue() {
        return Character.MIN_VALUE;
    }

    @Override
    public int getMaxValue() {
        return Character.MAX_VALUE;
    }

    @Override
    public int getLo(int i) {
        return buf[i * 2];
    }

    @Override
    public int getHi(int i) {
        return buf[i * 2 + 1];
    }

    @Override
    public int size() {
        return length() / 2;
    }

    @Override
    public void appendRange(int lo, int hi) {
        assert isEmpty() || leftOf(size() - 1, lo, hi) && !adjacent(size() - 1, lo, hi);
        add((char) lo);
        add((char) hi);
    }

    @Override
    public void insertRange(int index, int lo, int hi) {
        assert index >= 0 && index < size();
        assert index == 0 || leftOf(index - 1, lo, hi) && !adjacent(index - 1, lo, hi);
        assert rightOf(index, lo, hi) && !adjacent(index, lo, hi);
        ensureCapacity(length + 2);
        int i = index * 2;
        System.arraycopy(buf, i, buf, i + 2, length - i);
        buf[i] = (char) lo;
        buf[i + 1] = (char) hi;
        length += 2;
    }

    @Override
    public void replaceRanges(int fromIndex, int toIndex, int lo, int hi) {
        assert fromIndex >= 0 && fromIndex < toIndex && toIndex >= 0 && toIndex <= size();
        assert fromIndex == 0 || leftOf(fromIndex - 1, lo, hi) && !adjacent(fromIndex - 1, lo, hi);
        assert toIndex == size() || rightOf(toIndex, lo, hi) && !adjacent(toIndex, lo, hi);
        buf[fromIndex * 2] = (char) lo;
        buf[fromIndex * 2 + 1] = (char) hi;
        if (toIndex < size()) {
            System.arraycopy(buf, toIndex * 2, buf, fromIndex * 2 + 2, length - (toIndex * 2));
        }
        length -= (toIndex - (fromIndex + 1)) * 2;
    }

    @Override
    public void appendRangesTo(RangesBuffer buffer, int startIndex, int endIndex) {
        assert buffer instanceof CharRangesBuffer;
        int bulkLength = (endIndex - startIndex) * 2;
        if (bulkLength == 0) {
            return;
        }
        CharRangesBuffer o = (CharRangesBuffer) buffer;
        int newSize = o.length() + bulkLength;
        o.ensureCapacity(newSize);
        assert o.isEmpty() || rightOf(startIndex, o, o.size() - 1);
        System.arraycopy(buf, startIndex * 2, o.getBuffer(), o.length(), bulkLength);
        o.setLength(newSize);
    }

    @SuppressWarnings("unchecked")
    @Override
    public CharRangesBuffer create() {
        return new CharRangesBuffer(buf.length);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return defaultToString();
    }
}
