/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.strings;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;

/**
 * Utility nodes providing some functionality of {@link TruffleString} also for {@link String}.
 *
 * @since 23.0
 */
public final class JavaStringUtils {

    private JavaStringUtils() {
    }

    /**
     * Returns {@code true} if the given string's content is LATIN-1 only (see
     * {@link com.oracle.truffle.api.strings.TruffleString.CodeRange#LATIN_1}).
     *
     * @since 23.0
     */
    public static boolean isLatin1(String string) {
        return TStringUnsafe.getJavaStringStride(string) == 0;
    }

    /**
     * Counterpart of {@link TruffleString.ByteIndexOfCodePointSetNode}.
     *
     * @see TruffleString.ByteIndexOfCodePointSetNode
     * @since 23.0
     */
    public abstract static class CharIndexOfCodePointSetNode extends AbstractPublicNode {

        CharIndexOfCodePointSetNode() {
        }

        /**
         * Returns the char index of the first codepoint present in the given
         * {@link IndexOfCodePointSet codepoint set}.
         *
         * @see TruffleString.ByteIndexOfCodePointSetNode
         * @since 23.0
         */
        public abstract int execute(String a, int fromCharIndex, int maxCharIndex, int[] ranges);

        @Specialization(guards = "ranges == cachedRanges", limit = "1")
        int indexOfCached(String a, int fromIndex, int maxIndex, @SuppressWarnings("unused") int[] ranges,
                        @Cached(value = "ranges", dimensions = 0) @SuppressWarnings("unused") int[] cachedRanges,
                        @Cached("create(cachedRanges, UTF_16)") TStringInternalNodes.IndexOfCodePointSetNode internalNode) {
            if (a.isEmpty()) {
                return -1;
            }
            AbstractTruffleString.boundsCheckI(fromIndex, maxIndex, a.length());
            if (fromIndex == maxIndex) {
                return -1;
            }
            int stride = TStringUnsafe.getJavaStringStride(a);
            int codeRange = stride == 0 ? TSCodeRange.get8Bit() : TSCodeRange.getValidMultiByte();
            return internalNode.execute(TStringUnsafe.getJavaStringArray(a), 0, a.length(), stride, codeRange, fromIndex, maxIndex);
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = "indexOfCached")
        int indexOfUncached(String a, int fromIndex, int maxIndex, int[] ranges) {
            IndexOfCodePointSet.checkRangesArray(ranges, TruffleString.Encoding.UTF_16);
            if (a.isEmpty()) {
                return -1;
            }
            AbstractTruffleString.boundsCheckI(fromIndex, maxIndex, a.length());
            if (fromIndex == maxIndex) {
                return -1;
            }
            int i = fromIndex;
            while (i < maxIndex) {
                int codepoint = a.codePointAt(i);
                if (IndexOfCodePointSet.IndexOfRangesNode.rangesContain(ranges, codepoint)) {
                    return i;
                }
                i += codepoint > 0xffff ? 2 : 1;
            }
            return -1;
        }

        /**
         * Create a new {@link CharIndexOfCodePointSetNode}.
         *
         * @since 23.0
         */
        public static CharIndexOfCodePointSetNode create() {
            return JavaStringUtilsFactory.CharIndexOfCodePointSetNodeGen.create();
        }

        /**
         * Get the uncached version of {@link CharIndexOfCodePointSetNode}.
         *
         * @since 23.0
         */
        public static CharIndexOfCodePointSetNode getUncached() {
            return JavaStringUtilsFactory.CharIndexOfCodePointSetNodeGen.getUncached();
        }
    }
}
