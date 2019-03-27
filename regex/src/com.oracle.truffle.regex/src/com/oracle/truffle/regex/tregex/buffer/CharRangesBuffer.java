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
    public void addRange(int rLo, int rHi) {
        add((char) rLo);
        add((char) rHi);
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
}
