/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.snippets;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Fold;


@SuppressWarnings("unused")
public class ArrayCopySnippets implements SnippetsInterface{
    private static final Kind VECTOR_KIND = Kind.Long;
    private static final long VECTOR_SIZE = arrayIndexScale(Kind.Long);

    //@Snippet
    public static void vectorizedCopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter("baseKind") Kind baseKind) {
        int header = arrayBaseOffset(baseKind);
        long byteLength = length * arrayIndexScale(baseKind);
        long nonVectorBytes = byteLength % VECTOR_SIZE;
        long srcOffset = srcPos * arrayIndexScale(baseKind);
        long destOffset = destPos * arrayIndexScale(baseKind);
        if (src == dest && srcPos < destPos) { // bad aliased case
            for (long i = byteLength - 1; i > byteLength - 1 - nonVectorBytes; i--) {
                Byte a = UnsafeLoadNode.load(src, header, i + srcOffset, Kind.Byte);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.byteValue(), Kind.Byte);
            }
            long vectorLength = byteLength - nonVectorBytes;
            for (long i = vectorLength - VECTOR_SIZE; i >= 0; i -= VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        } else {
            for (long i = 0; i < nonVectorBytes; i++) {
                Byte a = UnsafeLoadNode.load(src, header, i + srcOffset, Kind.Byte);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.byteValue(), Kind.Byte);
            }
            for (long i = nonVectorBytes; i < byteLength; i += VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        }
    }

    @Snippet
    public static void arraycopy(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        Kind baseKind = Kind.Byte;
        int header = arrayBaseOffset(baseKind);
        long byteLength = length * arrayIndexScale(baseKind);
        long nonVectorBytes = byteLength % VECTOR_SIZE;
        long srcOffset = srcPos * arrayIndexScale(baseKind);
        long destOffset = destPos * arrayIndexScale(baseKind);
        if (src == dest && srcPos < destPos) { // bad aliased case
            for (long i = byteLength - arrayIndexScale(Kind.Byte); i >= byteLength - nonVectorBytes; i -= arrayIndexScale(Kind.Byte)) {
                Byte a = UnsafeLoadNode.load(src, header, i + srcOffset, Kind.Byte);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.byteValue(), Kind.Byte);
            }
            long vectorLength = byteLength - nonVectorBytes;
            for (long i = vectorLength - VECTOR_SIZE; i >= 0; i -= VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        } else {
            for (long i = 0; i < nonVectorBytes; i += arrayIndexScale(Kind.Byte)) {
                Byte a = UnsafeLoadNode.load(src, header, i + srcOffset, Kind.Byte);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.byteValue(), Kind.Byte);
            }
            for (long i = nonVectorBytes; i < byteLength; i += VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        }
    }

    @Snippet
    public static void arraycopy(char[] src, int srcPos, char[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        Kind baseKind = Kind.Char;
        int header = arrayBaseOffset(baseKind);
        long byteLength = length * arrayIndexScale(baseKind);
        long nonVectorBytes = byteLength % VECTOR_SIZE;
        long srcOffset = srcPos * arrayIndexScale(baseKind);
        long destOffset = destPos * arrayIndexScale(baseKind);
        if (src == dest && srcPos < destPos) { // bad aliased case
            for (long i = byteLength - arrayIndexScale(Kind.Char); i >= byteLength - nonVectorBytes; i -= arrayIndexScale(Kind.Char)) {
                Character a = UnsafeLoadNode.load(src, header, i + srcOffset, Kind.Char);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.charValue(), Kind.Char);
            }
            long vectorLength = byteLength - nonVectorBytes;
            for (long i = vectorLength - VECTOR_SIZE; i >= 0; i -= VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        } else {
            for (long i = 0; i < nonVectorBytes; i += arrayIndexScale(Kind.Char)) {
                Character a = UnsafeLoadNode.load(src, header, i + srcOffset, Kind.Char);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.charValue(), Kind.Char);
            }
            for (long i = nonVectorBytes; i < byteLength; i += VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        }
    }

    @Snippet
    public static void arraycopy(short[] src, int srcPos, short[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        Kind baseKind = Kind.Short;
        int header = arrayBaseOffset(baseKind);
        long byteLength = length * arrayIndexScale(baseKind);
        long nonVectorBytes = byteLength % VECTOR_SIZE;
        long srcOffset = srcPos * arrayIndexScale(baseKind);
        long destOffset = destPos * arrayIndexScale(baseKind);
        if (src == dest && srcPos < destPos) { // bad aliased case
            for (long i = byteLength - arrayIndexScale(Kind.Short); i >= byteLength - nonVectorBytes; i -= arrayIndexScale(Kind.Short)) {
                Short a = UnsafeLoadNode.load(src, header, i + srcOffset, Kind.Short);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.shortValue(), Kind.Short);
            }
            long vectorLength = byteLength - nonVectorBytes;
            for (long i = vectorLength - VECTOR_SIZE; i >= 0; i -= VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        } else {
            for (long i = 0; i < nonVectorBytes; i += arrayIndexScale(Kind.Short)) {
                Short a = UnsafeLoadNode.load(src, header, i + srcOffset, Kind.Short);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.shortValue(), Kind.Short);
            }
            for (long i = nonVectorBytes; i < byteLength; i += VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        }
    }

    @Snippet
    public static void arraycopy(int[] src, int srcPos, int[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        Kind baseKind = Kind.Int;
        int header = arrayBaseOffset(baseKind);
        long byteLength = length * arrayIndexScale(baseKind);
        long nonVectorBytes = byteLength % VECTOR_SIZE;
        long srcOffset = srcPos * arrayIndexScale(baseKind);
        long destOffset = destPos * arrayIndexScale(baseKind);
        if (src == dest && srcPos < destPos) { // bad aliased case
            for (long i = byteLength - arrayIndexScale(Kind.Int); i >= byteLength - nonVectorBytes; i -= arrayIndexScale(Kind.Int)) {
                Integer a = UnsafeLoadNode.load(src, header, i + srcOffset, Kind.Int);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.intValue(), Kind.Int);
            }
            long vectorLength = byteLength - nonVectorBytes;
            for (long i = vectorLength - VECTOR_SIZE; i >= 0; i -= VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        } else {
            for (long i = 0; i < nonVectorBytes; i += arrayIndexScale(Kind.Int)) {
                Integer a = UnsafeLoadNode.load(src, header, i + srcOffset, Kind.Int);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.intValue(), Kind.Int);
            }
            for (long i = nonVectorBytes; i < byteLength; i += VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        }
    }

    @Snippet
    public static void arraycopy(float[] src, int srcPos, float[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        Kind baseKind = Kind.Float;
        int header = arrayBaseOffset(baseKind);
        long byteLength = length * arrayIndexScale(baseKind);
        long nonVectorBytes = byteLength % VECTOR_SIZE;
        long srcOffset = srcPos * arrayIndexScale(baseKind);
        long destOffset = destPos * arrayIndexScale(baseKind);
        if (src == dest && srcPos < destPos) { // bad aliased case
            for (long i = byteLength - arrayIndexScale(Kind.Float); i >= byteLength - nonVectorBytes; i -= arrayIndexScale(Kind.Float)) {
                Float a = UnsafeLoadNode.load(src, header, i + srcOffset, Kind.Float);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.floatValue(), Kind.Float);
            }
            long vectorLength = byteLength - nonVectorBytes;
            for (long i = vectorLength - VECTOR_SIZE; i >= 0; i -= VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        } else {
            for (long i = 0; i < nonVectorBytes; i += arrayIndexScale(Kind.Float)) {
                Float a = UnsafeLoadNode.load(src, header, i + srcOffset, Kind.Float);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.floatValue(), Kind.Float);
            }
            for (long i = nonVectorBytes; i < byteLength; i += VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        }
    }

    @Snippet
    public static void arraycopy(long[] src, int srcPos, long[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        Kind baseKind = Kind.Long;
        int header = arrayBaseOffset(baseKind);
        long byteLength = length * arrayIndexScale(baseKind);
        long srcOffset = srcPos * arrayIndexScale(baseKind);
        long destOffset = destPos * arrayIndexScale(baseKind);
        if (src == dest && srcPos < destPos) { // bad aliased case
            for (long i = byteLength - VECTOR_SIZE; i >= 0; i -= VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        } else {
            for (long i = 0; i < byteLength; i += VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        }
    }

    @Snippet
    public static void arraycopy(double[] src, int srcPos, double[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        Kind baseKind = Kind.Double;
        int header = arrayBaseOffset(baseKind);
        long byteLength = length * arrayIndexScale(baseKind);
        long srcOffset = srcPos * arrayIndexScale(baseKind);
        long destOffset = destPos * arrayIndexScale(baseKind);
        if (src == dest && srcPos < destPos) { // bad aliased case
            for (long i = byteLength - VECTOR_SIZE; i >= 0; i -= VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        } else {
            for (long i = 0; i < byteLength; i += VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        }
    }

    // Does NOT perform store checks
    @Snippet
    public static void arraycopy(Object[] src, int srcPos, Object[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        final int scale = arrayIndexScale(Kind.Object);
        int header = arrayBaseOffset(Kind.Object);
        if (src == dest && srcPos < destPos) { // bad aliased case
            for (long i = (length - 1) * scale; i >= 0; i -= scale) {
                Object a = UnsafeLoadNode.load(src, header, i + (long) srcPos * scale, Kind.Object);
                DirectObjectStoreNode.store(dest, header, i + (long) destPos * scale, a);
            }
        } else {
            for (long i = 0; i < length * scale; i += scale) {
                Object a = UnsafeLoadNode.load(src, header, i + (long) srcPos * scale, Kind.Object);
                DirectObjectStoreNode.store(dest, header, i + (long) destPos * scale, a);
            }
        }
        if (length > 0) {
            int cardShift = cardTableShift();
            long cardStart = cardTableStart();
            long dstAddr = GetObjectAddressNode.get(dest);
            long start = (dstAddr + header + (long) destPos * scale) >>> cardShift;
            long end = (dstAddr + header + ((long) destPos + length - 1) * scale) >>> cardShift;
            long count = end - start + 1;
            while (count-- > 0) {
                DirectStoreNode.store((start + cardStart) + count, false);
            }
        }
    }

    @Fold
    static int arrayBaseOffset(Kind elementKind) {
        return elementKind.arrayBaseOffset();
    }

    @Fold
    static int arrayIndexScale(Kind elementKind) {
        return elementKind.arrayIndexScale();
    }

    static {
        assert arrayIndexScale(Kind.Byte) == 1;
        assert arrayIndexScale(Kind.Boolean) == 1;
        assert arrayIndexScale(Kind.Char) == 2;
        assert arrayIndexScale(Kind.Short) == 2;
        assert arrayIndexScale(Kind.Int) == 4;
        assert arrayIndexScale(Kind.Long) == 8;
        assert arrayIndexScale(Kind.Float) == 4;
        assert arrayIndexScale(Kind.Double) == 8;
    }

    @Fold
    private static int cardTableShift() {
        return HotSpotGraalRuntime.getInstance().getConfig().cardtableShift;
    }

    @Fold
    private static long cardTableStart() {
        return HotSpotGraalRuntime.getInstance().getConfig().cardtableStartAddress;
    }
}
