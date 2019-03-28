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

import java.util.Arrays;

/**
 * This class is designed as a "scratchpad" for generating many short arrays of unknown size. It
 * will never shrink its internal buffer, so it should be disposed as soon as it is no longer
 * needed.
 * <p>
 * Usage Example:
 * </p>
 * 
 * <pre>
 * ShortArrayBuffer buf = new ShortArrayBuffer();
 * List<short[]> results = new ArrayList<>();
 * for (Object obj : listOfThingsToProcess) {
 *     for (Object x : obj.thingsThatShouldBecomeShorts()) {
 *         buf.add(someCalculation(x));
 *     }
 *     results.add(buf.toArray());
 *     buf.clear();
 * }
 * </pre>
 */
public class ShortArrayBuffer extends AbstractArrayBuffer {

    private short[] buf;

    public ShortArrayBuffer() {
        this(16);
    }

    public ShortArrayBuffer(int initialSize) {
        buf = new short[initialSize];
    }

    @Override
    int getBufferLength() {
        return buf.length;
    }

    @Override
    void grow(int newSize) {
        buf = Arrays.copyOf(buf, newSize);
    }

    public short get(int i) {
        return buf[i];
    }

    public void add(short s) {
        if (length == buf.length) {
            grow(length * 2);
        }
        buf[length] = s;
        length++;
    }

    public void addAll(short[] values, int valuesLength) {
        ensureCapacity(length + valuesLength);
        System.arraycopy(values, 0, buf, length, valuesLength);
        length += valuesLength;
    }

    public short[] toArray() {
        return Arrays.copyOf(buf, length);
    }
}
