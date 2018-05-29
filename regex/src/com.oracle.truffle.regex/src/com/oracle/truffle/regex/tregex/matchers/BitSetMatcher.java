/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.matchers;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * Matcher that matches multiple characters with a common high byte using a bit set.<br>
 * Example: characters {@code \u1010, \u1020, \u1030} have a common high byte {@code 0x10}, so they
 * are matched by this high byte and a bit set that matches {@code 0x10}, {@code 0x20} and
 * {@code 0x30}.
 */
public final class BitSetMatcher extends ProfiledCharMatcher {

    private final int highByte;
    private final CompilationFinalBitSet bitSet;

    private BitSetMatcher(boolean invert, int highByte, CompilationFinalBitSet bitSet) {
        super(invert);
        this.highByte = highByte;
        this.bitSet = bitSet;
    }

    /**
     * Constructs a new bit-set-based character matcher.
     * 
     * @param invert see {@link ProfiledCharMatcher}.
     * @param highByte the high byte common to all characters to match.
     * @param bitSet the bit set to match the low byte of the characters to match.
     * @return a new {@link BitSetMatcher} or a {@link NullHighByteBitSetMatcher}.
     */
    public static ProfiledCharMatcher create(boolean invert, int highByte, CompilationFinalBitSet bitSet) {
        if (highByte == 0) {
            return new NullHighByteBitSetMatcher(invert, bitSet);
        }
        return new BitSetMatcher(invert, highByte, bitSet);
    }

    public CompilationFinalBitSet getBitSet() {
        return bitSet;
    }

    @Override
    public boolean matchChar(char c) {
        return highByte(c) == highByte && bitSet.get(lowByte(c));
    }

    @Override
    public int estimatedCost() {
        // 4 for the bit set + 1 for the high byte check
        return 5;
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public String toString() {
        return modifiersToString() + "{hi " + DebugUtil.charToString(highByte) + " lo " + bitSet + "}";
    }
}
