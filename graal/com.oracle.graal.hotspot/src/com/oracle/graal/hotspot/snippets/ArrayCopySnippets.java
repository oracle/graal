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
import static com.oracle.graal.hotspot.snippets.HotSpotSnippetUtils.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Fold;
import com.oracle.graal.snippets.nodes.*;


@SuppressWarnings("unused")
public class ArrayCopySnippets implements SnippetsInterface{
    private static final Kind VECTOR_KIND = Kind.Long;
    private static final long VECTOR_SIZE = arrayIndexScale(Kind.Long);

    @Snippet
    public static void vectorizedCopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter("baseKind") Kind baseKind) {
        int header = arrayBaseOffset(baseKind);
        int elementSize = arrayIndexScale(baseKind);
        long byteLength = (long) length * elementSize;
        long nonVectorBytes = byteLength % VECTOR_SIZE;
        long srcOffset = (long) srcPos * elementSize;
        long destOffset = (long) destPos * elementSize;
        if (src == dest && srcPos < destPos) { // bad aliased case
            for (long i = byteLength - elementSize; i >= byteLength - nonVectorBytes; i -= elementSize) {
                UnsafeStoreNode.store(dest, header, i + destOffset, UnsafeLoadNode.load(src, header, i + srcOffset, baseKind), baseKind);
            }
            long vectorLength = byteLength - nonVectorBytes;
            for (long i = vectorLength - VECTOR_SIZE; i >= 0; i -= VECTOR_SIZE) {
                Long a = UnsafeLoadNode.load(src, header, i + srcOffset, VECTOR_KIND);
                UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), VECTOR_KIND);
            }
        } else {
            for (long i = 0; i < nonVectorBytes; i += elementSize) {
                UnsafeStoreNode.store(dest, header, i + destOffset, UnsafeLoadNode.load(src, header, i + srcOffset, baseKind), baseKind);
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
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Byte);
    }

    @Snippet
    public static void arraycopy(char[] src, int srcPos, char[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Char);
    }

    @Snippet
    public static void arraycopy(short[] src, int srcPos, short[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Short);
    }

    @Snippet
    public static void arraycopy(int[] src, int srcPos, int[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Int);
    }

    @Snippet
    public static void arraycopy(float[] src, int srcPos, float[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Float);
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
        long byteLength = (long) length * arrayIndexScale(baseKind);
        long srcOffset = (long) srcPos * arrayIndexScale(baseKind);
        long destOffset = (long) destPos * arrayIndexScale(baseKind);
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
        long byteLength = (long) length * arrayIndexScale(baseKind);
        long srcOffset = (long) srcPos * arrayIndexScale(baseKind);
        long destOffset = (long) destPos * arrayIndexScale(baseKind);
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
            long start = (long) (length - 1) * scale;
            for (long i = start; i >= 0; i -= scale) {
                Object a = UnsafeLoadNode.load(src, header, i + (long) srcPos * scale, Kind.Object);
                DirectObjectStoreNode.storeObject(dest, header, i + (long) destPos * scale, a);
            }
        } else {
            long end = (long) length * scale;
            for (long i = 0; i < end; i += scale) {
                Object a = UnsafeLoadNode.load(src, header, i + (long) srcPos * scale, Kind.Object);
                DirectObjectStoreNode.storeObject(dest, header, i + (long) destPos * scale, a);
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
}
