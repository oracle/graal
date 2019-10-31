/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
