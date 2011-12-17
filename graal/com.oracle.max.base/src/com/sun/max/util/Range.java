/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.sun.max.util;

/**
 * A {@code Range} denotes all the integer values between a {@linkplain #start() start} (inclusive) and
 * {@linkplain #end() end} (exclusive) value.
 */
public class Range {
    private final int start;
    private final int end;

    public Range(int start) {
        this(start, start + 1);
    }

    /**
     * Creates an object representing values in the range {@code [start .. end)}.
     *
     * @param start
     *                the lowest value in the range
     * @param end
     *                the value one greater than the highest value in the range
     */
    public Range(int start, int end) {
        assert end >= start;
        this.start = start;
        this.end = end;
        assert length() >= 0;
    }

    /**
     * Gets the lowest value in this range.
     * @return
     */
    public int start() {
        return start;
    }

    /**
     * Gets the number of values covered by this range.
     */
    public long length() {
        // This cast and the long return type prevents integer overflow
        return ((long) end) - start;
    }

    /**
     * Gets the value one greater than the highest value in this range.
     */
    public int end() {
        return end;
    }

    public boolean contains(long value) {
        return value >= start && value < end;
    }

    @Override
    public String toString() {
        return "[" + start() + '-' + (end() - 1) + ']';
    }
}
