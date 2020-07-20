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

package com.oracle.truffle.regex.tregex.buffer;

import java.util.Arrays;
import java.util.PrimitiveIterator;

/**
 * This class is designed as a "scratchpad" for generating many char arrays of unknown size. It will
 * never shrink its internal buffer, so it should be disposed as soon as it is no longer needed.
 * <p>
 * Usage Example:
 * </p>
 *
 * <pre>
 * IntArrayBuffer buf = new IntArrayBuffer();
 * List<int[]> results = new ArrayList<>();
 * for (Object obj : listOfThingsToProcess) {
 *     for (Object x : obj.thingsThatShouldBecomeInts()) {
 *         buf.add(someCalculation(x));
 *     }
 *     results.add(buf.toArray());
 *     buf.clear();
 * }
 * </pre>
 */
public class IntArrayBuffer extends AbstractArrayBuffer implements Iterable<Integer> {

    private static final int[] EMPTY = {};
    protected int[] buf;

    public IntArrayBuffer() {
        this(8);
    }

    public IntArrayBuffer(int initialSize) {
        buf = new int[initialSize];
    }

    @Override
    int getBufferLength() {
        return buf.length;
    }

    @Override
    void grow(int newSize) {
        buf = Arrays.copyOf(buf, newSize);
    }

    public int[] getBuffer() {
        return buf;
    }

    public void add(int c) {
        if (length == buf.length) {
            grow(length * 2);
        }
        buf[length++] = c;
    }

    public IntArrayBuffer asFixedSizeArray(int size, int initialValue) {
        ensureCapacity(size);
        Arrays.fill(buf, 0, size, initialValue);
        length = size;
        return this;
    }

    public int get(int index) {
        assert index < length;
        return buf[index];
    }

    public void inc(int index) {
        assert index < length;
        buf[index]++;
    }

    public void set(int index, int value) {
        assert index < length;
        buf[index] = value;
    }

    public void addAll(IntArrayBuffer o) {
        ensureCapacity(length + o.length);
        System.arraycopy(o.buf, 0, buf, length, o.length);
    }

    public int[] toArray() {
        return isEmpty() ? EMPTY : Arrays.copyOf(buf, length);
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        return new IntArrayBufferIterator(buf, length);
    }

    private static final class IntArrayBufferIterator implements PrimitiveIterator.OfInt {

        private final int[] buf;
        private final int size;
        private int i = 0;

        private IntArrayBufferIterator(int[] buf, int size) {
            this.buf = buf;
            this.size = size;
        }

        @Override
        public boolean hasNext() {
            return i < size;
        }

        @Override
        public int nextInt() {
            return buf[i++];
        }
    }
}
