/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * Character matcher that uses a sorted list of bit sets (like {@link BitSetMatcher}) in conjunction
 * with another {@link CharMatcher} to cover all characters not covered by the bit sets.
 */
public abstract class HybridBitSetMatcher extends InvertibleCharMatcher {

    @CompilationFinal(dimensions = 1) private final byte[] highBytes;
    @CompilationFinal(dimensions = 1) private final CompilationFinalBitSet[] bitSets;
    private final CharMatcher restMatcher;

    /**
     * Constructs a new {@link HybridBitSetMatcher}.
     *
     * @param invert see {@link InvertibleCharMatcher}.
     * @param highBytes the respective high bytes of the bit sets.
     * @param bitSets the bit sets that match the low bytes if the character under inspection has
     *            the corresponding high byte.
     * @param restMatcher any {@link CharMatcher}, to cover the characters not covered by the bit
     *            sets.
     */
    HybridBitSetMatcher(boolean invert, byte[] highBytes, CompilationFinalBitSet[] bitSets, CharMatcher restMatcher) {
        super(invert);
        this.highBytes = highBytes;
        this.bitSets = bitSets;
        this.restMatcher = restMatcher;
        assert isSortedUnsigned(highBytes);
    }

    public static HybridBitSetMatcher create(boolean invert, byte[] highBytes, CompilationFinalBitSet[] bitSets, CharMatcher restMatcher) {
        return HybridBitSetMatcherNodeGen.create(invert, highBytes, bitSets, restMatcher);
    }

    @Specialization
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    protected boolean match(char c, boolean compactString) {
        assert !compactString : "this matcher should be avoided via ProfilingCharMatcher on compact strings";
        final int highByte = highByte(c);
        for (int i = 0; i < highBytes.length; i++) {
            int bitSetHighByte = Byte.toUnsignedInt(highBytes[i]);
            if (highByte == bitSetHighByte) {
                return result(bitSets[i].get(lowByte(c)));
            }
            if (highByte < bitSetHighByte) {
                break;
            }
        }
        return result(restMatcher.execute(c, compactString));
    }

    @Override
    public int estimatedCost() {
        return (bitSets.length * 2) + restMatcher.estimatedCost();
    }

    @Override
    @TruffleBoundary
    public String toString() {
        StringBuilder sb = new StringBuilder(modifiersToString()).append("hybrid [\n");
        for (int i = 0; i < highBytes.length; i++) {
            sb.append(String.format("  %02x: ", Byte.toUnsignedInt(highBytes[i]))).append(bitSets[i]).append("\n");
        }
        return sb.append("  rest: ").append(restMatcher).append("\n]").toString();
    }

    private static boolean isSortedUnsigned(byte[] array) {
        int prev = Integer.MIN_VALUE;
        for (byte b : array) {
            int i = Byte.toUnsignedInt(b);
            if (prev > i) {
                return false;
            }
            prev = i;
        }
        return true;
    }
}
