/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

final class TStringOpsNodes {

    /**
     * Maximum possible combinations of two different stride values; possible stride values are
     * {@code [0, 1, 2]}. We can use this large limit here because all nodes in
     * {@link TStringOpsNodes} just exist to dispatch to various intrinsic stubs that require
     * constant stride values.
     */
    static final String LIMIT_STRIDE = "9";

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class RawReadValueNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int i);

        @Specialization(guards = {"stride(a) == cachedStrideA"}, limit = LIMIT_STRIDE)
        static int cached(AbstractTruffleString a, Object arrayA, int i,
                        @Cached(value = "stride(a)", allowUncached = true) int cachedStrideA) {
            return TStringOps.readValue(a, arrayA, cachedStrideA, i);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class IndexOfAnyCharNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int fromIndex, int maxIndex, char[] values);

        @Specialization(guards = {"isStride0(a)", "values.length == 1"})
        int stride0(AbstractTruffleString a, Object arrayA, int fromIndex, int maxIndex, char[] values) {
            return TStringOps.indexOfAnyChar(this, a, arrayA, 0, fromIndex, maxIndex, values);
        }

        @Specialization(guards = {"isStride0(a)", "values.length > 1"})
        int stride0MultiValue(AbstractTruffleString a, Object arrayA, int fromIndex, int maxIndex, char[] values) {
            int n = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] <= 0xff) {
                    n++;
                }
                TStringConstants.truffleSafePointPoll(this, i + 1);
            }
            final char[] stride0Values;
            if (n != values.length) {
                stride0Values = new char[n];
                n = 0;
                for (int i = 0; i < values.length; i++) {
                    if (values[i] <= 0xff) {
                        stride0Values[n++] = values[i];
                    }
                    TStringConstants.truffleSafePointPoll(this, i + 1);
                }
            } else {
                stride0Values = values;
            }
            return TStringOps.indexOfAnyChar(this, a, arrayA, 0, fromIndex, maxIndex, stride0Values);
        }

        @Specialization(guards = "isStride1(a)")
        int stride1(AbstractTruffleString a, Object arrayA, int fromIndex, int maxIndex, char[] values) {
            return TStringOps.indexOfAnyChar(this, a, arrayA, 1, fromIndex, maxIndex, values);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class IndexOfAnyIntNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int fromIndex, int maxIndex, int[] values);

        @Specialization(guards = {"isStride0(a)", "values.length == 1"})
        int stride0(AbstractTruffleString a, Object arrayA, int fromIndex, int maxIndex, int[] values) {
            return TStringOps.indexOfAnyInt(this, a, arrayA, 0, fromIndex, maxIndex, values);
        }

        @Specialization(guards = {"isStride0(a)", "values.length > 1"})
        int stride0MultiValue(AbstractTruffleString a, Object arrayA, int fromIndex, int maxIndex, int[] values) {
            int n = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] <= 0xff) {
                    n++;
                }
                TStringConstants.truffleSafePointPoll(this, i + 1);
            }
            final int[] stride0Values;
            if (n != values.length) {
                stride0Values = new int[n];
                n = 0;
                for (int i = 0; i < values.length; i++) {
                    if (values[i] <= 0xff) {
                        stride0Values[n++] = values[i];
                    }
                    TStringConstants.truffleSafePointPoll(this, i + 1);
                }
            } else {
                stride0Values = values;
            }
            return TStringOps.indexOfAnyInt(this, a, arrayA, 0, fromIndex, maxIndex, stride0Values);
        }

        @Specialization(guards = {"isStride1(a)", "values.length == 1"})
        int stride1(AbstractTruffleString a, Object arrayA, int fromIndex, int maxIndex, int[] values) {
            return TStringOps.indexOfAnyInt(this, a, arrayA, 1, fromIndex, maxIndex, values);
        }

        @Specialization(guards = {"isStride1(a)", "values.length > 1"})
        int stride1MultiValue(AbstractTruffleString a, Object arrayA, int fromIndex, int maxIndex, int[] values) {
            int n = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] <= 0xffff) {
                    n++;
                }
                TStringConstants.truffleSafePointPoll(this, i + 1);
            }
            final int[] stride1Values;
            if (n != values.length) {
                stride1Values = new int[n];
                n = 0;
                for (int i = 0; i < values.length; i++) {
                    if (values[i] <= 0xffff) {
                        stride1Values[n++] = values[i];
                    }
                    TStringConstants.truffleSafePointPoll(this, i + 1);
                }
            } else {
                stride1Values = values;
            }
            return TStringOps.indexOfAnyInt(this, a, arrayA, 1, fromIndex, maxIndex, stride1Values);
        }

        @Specialization(guards = "isStride2(a)")
        int stride2(AbstractTruffleString a, Object arrayA, int fromIndex, int maxIndex, int[] values) {
            return TStringOps.indexOfAnyInt(this, a, arrayA, 2, fromIndex, maxIndex, values);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class RawIndexOfCodePointNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int codepoint, int fromIndex, int toIndex);

        @Specialization(guards = {"stride(a) == cachedStrideA"}, limit = LIMIT_STRIDE)
        int cached(AbstractTruffleString a, Object arrayA, int codepoint, int fromIndex, int toIndex,
                        @Cached(value = "stride(a)", allowUncached = true) int cachedStrideA) {
            return TStringOps.indexOfCodePointWithStride(this, a, arrayA, cachedStrideA, fromIndex, toIndex, codepoint);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class RawLastIndexOfCodePointNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int codepoint, int fromIndex, int toIndex);

        @Specialization(guards = {"stride(a) == cachedStrideA"}, limit = LIMIT_STRIDE)
        int cached(AbstractTruffleString a, Object arrayA, int codepoint, int fromIndex, int toIndex,
                        @Cached(value = "stride(a)", allowUncached = true) int cachedStrideA) {
            return TStringOps.lastIndexOfCodePointWithOrMaskWithStride(this, a, arrayA, cachedStrideA, fromIndex, toIndex, codepoint, 0);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class RawIndexOfStringNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB, int fromIndex, int toIndex, byte[] mask);

        @Specialization(guards = {"length(b) == 1", "stride(a) == cachedStrideA", "stride(b) == cachedStrideB"}, limit = LIMIT_STRIDE)
        int cachedLen1(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB, int fromIndex, int toIndex, byte[] mask,
                        @Cached(value = "stride(a)", allowUncached = true) int cachedStrideA,
                        @Cached(value = "stride(b)", allowUncached = true) int cachedStrideB) {
            final int b0 = TStringOps.readValue(b, arrayB, cachedStrideB, 0);
            final int mask0 = mask == null ? 0 : TStringOps.readFromByteArray(mask, cachedStrideB, 0);
            return TStringOps.indexOfCodePointWithOrMaskWithStride(this, a, arrayA, cachedStrideA, fromIndex, toIndex, b0, mask0);
        }

        @Specialization(guards = {"length(b) > 1", "stride(a) == cachedStrideA", "stride(b) == cachedStrideB"}, limit = LIMIT_STRIDE)
        int cached(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB, int fromIndex, int toIndex, byte[] mask,
                        @Cached(value = "stride(a)", allowUncached = true) int cachedStrideA,
                        @Cached(value = "stride(b)", allowUncached = true) int cachedStrideB) {
            return TStringOps.indexOfStringWithOrMaskWithStride(this, a, arrayA, cachedStrideA, b, arrayB, cachedStrideB, fromIndex, toIndex, mask);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class RawLastIndexOfStringNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB, int fromIndex, int toIndex, byte[] mask);

        @Specialization(guards = {"length(b) == 1", "stride(a) == cachedStrideA", "stride(b) == cachedStrideB"}, limit = LIMIT_STRIDE)
        int cachedLen1(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB, int fromIndex, int toIndex, byte[] mask,
                        @Cached(value = "stride(a)", allowUncached = true) int cachedStrideA,
                        @Cached(value = "stride(b)", allowUncached = true) int cachedStrideB) {
            final int b0 = TStringOps.readValue(b, arrayB, cachedStrideB, 0);
            final int mask0 = mask == null ? 0 : TStringOps.readFromByteArray(mask, cachedStrideB, 0);
            return TStringOps.lastIndexOfCodePointWithOrMaskWithStride(this, a, arrayA, cachedStrideA, fromIndex, toIndex, b0, mask0);
        }

        @Specialization(guards = {"length(b) > 1", "stride(a) == cachedStrideA", "stride(b) == cachedStrideB"}, limit = LIMIT_STRIDE)
        int cached(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB, int fromIndex, int toIndex, byte[] mask,
                        @Cached(value = "stride(a)", allowUncached = true) int cachedStrideA,
                        @Cached(value = "stride(b)", allowUncached = true) int cachedStrideB) {
            return TStringOps.lastIndexOfStringWithOrMaskWithStride(this, a, arrayA, cachedStrideA, b, arrayB, cachedStrideB, fromIndex, toIndex, mask);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class RawRegionEqualsNode extends Node {

        abstract boolean execute(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB, int fromIndexA, int fromIndexB, int length, byte[] mask);

        @Specialization(guards = {"stride(a) == cachedStrideA", "stride(b) == cachedStrideB"}, limit = LIMIT_STRIDE)
        boolean cached(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB, int fromIndexA, int fromIndexB, int length, byte[] mask,
                        @Cached(value = "stride(a)", allowUncached = true) int cachedStrideA,
                        @Cached(value = "stride(b)", allowUncached = true) int cachedStrideB) {
            return TStringOps.regionEqualsWithOrMaskWithStride(this, a, arrayA, cachedStrideA, fromIndexA, b, arrayB, cachedStrideB, fromIndexB, mask, length);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class RawEqualsNode extends Node {

        abstract boolean execute(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB);

        @Specialization(guards = {"stride(a) == cachedStrideA", "stride(b) == cachedStrideB"}, limit = LIMIT_STRIDE)
        boolean cached(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB,
                        @Cached(value = "stride(a)", allowUncached = true) int cachedStrideA,
                        @Cached(value = "stride(b)", allowUncached = true) int cachedStrideB) {
            return a.length() == b.length() && TStringOps.regionEqualsWithOrMaskWithStride(
                            this, a, arrayA, cachedStrideA, 0,
                            b, arrayB, cachedStrideB, 0, null, a.length());
        }

        static RawEqualsNode getUncached() {
            return TStringOpsNodesFactory.RawEqualsNodeGen.getUncached();
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class RawMemCmpNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB);

        @Specialization(guards = {"stride(a) == cachedStrideA", "stride(b) == cachedStrideB"}, limit = LIMIT_STRIDE)
        int cached(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB,
                        @Cached(value = "stride(a)", allowUncached = true) int cachedStrideA,
                        @Cached(value = "stride(b)", allowUncached = true) int cachedStrideB) {
            int cmp = TStringOps.memcmpWithStride(this, a, arrayA, cachedStrideA, b, arrayB, cachedStrideB, Math.min(a.length(), b.length()));
            return memCmpTail(cmp, a.length(), b.length());
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class RawMemCmpBytesNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB);

        @Specialization(guards = {"stride(a) == cachedStrideA", "stride(b) == cachedStrideB"}, limit = LIMIT_STRIDE)
        int cached(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB,
                        @Cached(value = "stride(a)", allowUncached = true) int cachedStrideA,
                        @Cached(value = "stride(b)", allowUncached = true) int cachedStrideB) {
            int cmp = TStringOps.memcmpBytesWithStride(this, a, arrayA, cachedStrideA, b, arrayB, cachedStrideB, Math.min(a.length(), b.length()));
            return memCmpTail(cmp, a.length(), b.length());
        }
    }

    private static int memCmpTail(int cmp, int lengthA, int lengthB) {
        if (cmp == 0) {
            if (lengthA == lengthB) {
                return 0;
            } else {
                return lengthA < lengthB ? -1 : 1;
            }
        } else {
            return cmp > 0 ? 1 : -1;
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class RawArrayCopyNode extends Node {

        abstract void execute(AbstractTruffleString a, Object arrayA, int srcPos, byte[] dst, int dstPos, int dstStride, int length);

        @Specialization(guards = {"stride(a) == cachedStrideA", "dstStride == cachedStrideDst"}, limit = LIMIT_STRIDE)
        void cached(AbstractTruffleString a, Object arrayA, int srcPos, byte[] dst, int dstPos, @SuppressWarnings("unused") int dstStride, int length,
                        @Cached(value = "stride(a)", allowUncached = true) int cachedStrideA,
                        @Cached(value = "dstStride", allowUncached = true) int cachedStrideDst) {
            TStringOps.arraycopyWithStride(
                            this, arrayA, a.offset(), cachedStrideA, srcPos,
                            dst, 0, cachedStrideDst, dstPos, length);
        }

        static RawArrayCopyNode getUncached() {
            return TStringOpsNodesFactory.RawArrayCopyNodeGen.getUncached();
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class RawArrayCopyBytesNode extends Node {

        abstract void execute(
                        Object src, int offsetSrc, int strideSrc,
                        byte[] dst, int offsetDst, int strideDst, int length);

        @Specialization(guards = {"strideSrc == cachedStrideSrc", "strideDst == cachedStrideDst"}, limit = LIMIT_STRIDE)
        void cached(
                        Object src, int offsetSrc, @SuppressWarnings("unused") int strideSrc,
                        byte[] dst, int offsetDst, @SuppressWarnings("unused") int strideDst, int length,
                        @Cached(value = "strideSrc", allowUncached = true) int cachedStrideSrc,
                        @Cached(value = "strideDst", allowUncached = true) int cachedStrideDst) {
            TStringOps.arraycopyWithStride(this,
                            src, offsetSrc, cachedStrideSrc, 0,
                            dst, offsetDst, cachedStrideDst, 0, length);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class CalculateHashCodeNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA);

        @Specialization(guards = {"stride(a) == cachedStrideA"}, limit = LIMIT_STRIDE)
        int cached(AbstractTruffleString a, Object arrayA,
                        @Cached(value = "stride(a)", allowUncached = true) int cachedStrideA) {
            return TStringOps.hashCodeWithStride(this, a, arrayA, cachedStrideA);
        }
    }

}
