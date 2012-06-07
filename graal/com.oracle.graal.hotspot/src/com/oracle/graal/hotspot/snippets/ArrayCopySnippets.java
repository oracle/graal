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
import com.oracle.graal.graph.Node.Fold;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.snippets.*;


@SuppressWarnings("unused")
public class ArrayCopySnippets implements SnippetsInterface{

    @Snippet
    public static void arraycopy(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > src.length || destPos + length > dest.length) {
            throw new IndexOutOfBoundsException();
        }

        if (src == dest && srcPos < destPos) { // bad aliased case
            if ((length & 0x01) == 0) {
                if ((length & 0x02) == 0) {
                    if ((length & 0x04) == 0) {
                        copyLongsDown(src, srcPos, dest, destPos, length >> 3);
                    } else {
                        copyIntsDown(src, srcPos, dest, destPos, length >> 2);
                    }
                } else {
                    copyShortsDown(src, srcPos, dest, destPos, length >> 1);
                }
            } else {
                copyBytesDown(src, srcPos, dest, destPos, length);
            }
        } else {
            if ((length & 0x01) == 0) {
                if ((length & 0x02) == 0) {
                    if ((length & 0x04) == 0) {
                        copyLongsUp(src, srcPos, dest, destPos, length >> 3);
                    } else {
                        copyIntsUp(src, srcPos, dest, destPos, length >> 2);
                    }
                } else {
                    copyShortsUp(src, srcPos, dest, destPos, length >> 1);
                }
            } else {
                copyBytesUp(src, srcPos, dest, destPos, length);
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
        if (src == dest && srcPos < destPos) { // bad aliased case
            if ((length & 0x01) == 0) {
                if ((length & 0x02) == 0) {
                    copyLongsDown(src, srcPos * 2L, dest, destPos * 2L, length >> 2);
                } else {
                    copyIntsDown(src, srcPos * 2L, dest, destPos * 2L, length >> 1);
                }
            } else {
                copyShortsDown(src, srcPos * 2L, dest, destPos * 2L, length);
            }
        } else {
            if ((length & 0x01) == 0) {
                if ((length & 0x02) == 0) {
                    copyLongsUp(src, srcPos * 2L, dest, destPos * 2L, length >> 2);
                } else {
                    copyIntsUp(src, srcPos * 2L, dest, destPos * 2L, length >> 1);
                }
            } else {
                copyShortsUp(src, srcPos * 2L, dest, destPos * 2L, length);
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
        if (src == dest && srcPos < destPos) { // bad aliased case
            if ((length & 0x01) == 0) {
                if ((length & 0x02) == 0) {
                    copyLongsDown(src, srcPos * 2L, dest, destPos * 2L, length >> 2);
                } else {
                    copyIntsDown(src, srcPos * 2L, dest, destPos * 2L, length >> 1);
                }
            } else {
                copyShortsDown(src, srcPos * 2L, dest, destPos * 2L, length);
            }
        } else {
            if ((length & 0x01) == 0) {
                if ((length & 0x02) == 0) {
                    copyLongsUp(src, srcPos * 2L, dest, destPos * 2L, length >> 2);
                } else {
                    copyIntsUp(src, srcPos * 2L, dest, destPos * 2L, length >> 1);
                }
            } else {
                copyShortsUp(src, srcPos * 2L, dest, destPos * 2L, length);
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
        if (src == dest && srcPos < destPos) { // bad aliased case
            if ((length & 0x01) == 0) {
                copyLongsDown(src, srcPos * 4L, dest, destPos * 4L, length >> 1);
            } else {
                copyIntsDown(src, srcPos * 4L, dest, destPos * 4L, length);
            }
        } else {
            if ((length & 0x01) == 0) {
                copyLongsUp(src, srcPos * 4L, dest, destPos * 4L, length >> 1);
            } else {
                copyIntsUp(src, srcPos * 4L, dest, destPos * 4L, length);
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
        if (src == dest && srcPos < destPos) { // bad aliased case
            if ((length & 0x01) == 0) {
                copyLongsDown(src, srcPos * 4L, dest, destPos * 4L, length >> 1);
            } else {
                copyIntsDown(src, srcPos * 4L, dest, destPos * 4L, length);
            }
        } else {
            if ((length & 0x01) == 0) {
                copyLongsUp(src, srcPos * 4L, dest, destPos * 4L, length >> 1);
            } else {
                copyIntsUp(src, srcPos * 4L, dest, destPos * 4L, length);
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
        if (src == dest && srcPos < destPos) { // bad aliased case
            copyLongsDown(src, srcPos * 8L, dest, destPos * 8L, length);
        } else {
            copyLongsUp(src, srcPos * 8L, dest, destPos * 8L, length);
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
        if (src == dest && srcPos < destPos) { // bad aliased case
            copyLongsDown(src, srcPos * 8L, dest, destPos * 8L, length);
        } else {
            copyLongsUp(src, srcPos * 8L, dest, destPos * 8L, length);
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
        final int scale = arrayIndexScale(RiKind.Object);
        if (src == dest && srcPos < destPos) { // bad aliased case
            copyObjectsDown(src, srcPos * scale, dest, destPos * scale, length);
        } else {
            copyObjectsUp(src, srcPos * scale, dest, destPos * scale, length);
        }
        if (length > 0) {
            int header = arrayBaseOffset(RiKind.Object);
            int cardShift = cardTableShift();
            long cardStart = cardTableStart();
            long dstAddr = GetObjectAddressNode.get(dest);
            long start = (dstAddr + header + destPos * scale) >>> cardShift;
            long end = (dstAddr + header + (destPos + length - 1) * scale) >>> cardShift;
            long count = end - start + 1;
            while (count-- > 0) {
                DirectStoreNode.store((start + cardStart) + count, false);
            }
        }
    }

    @Snippet
    public static void copyBytesDown(Object src, int srcPos, Object dest, int destPos, int length)  {
        int header = arrayBaseOffset(RiKind.Byte);
        for (long i = length - 1; i >= 0; i--) {
            Byte a = UnsafeLoadNode.load(src, header, i + srcPos, RiKind.Byte);
            UnsafeStoreNode.store(dest, header, i + destPos, a.byteValue(), RiKind.Byte);
        }
    }

    @Snippet
    public static void copyShortsDown(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        int header = arrayBaseOffset(RiKind.Short);
        for (long i = (length - 1) * 2; i >= 0; i -= 2) {
            Character a = UnsafeLoadNode.load(src, header, i + srcOffset, RiKind.Short);
            UnsafeStoreNode.store(dest, header, i + destOffset, a.charValue(), RiKind.Short);
        }
    }

    @Snippet
    public static void copyIntsDown(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        int header = arrayBaseOffset(RiKind.Int);
        for (long i = (length - 1) * 4; i >= 0; i -= 4) {
            Integer a = UnsafeLoadNode.load(src, header, i + srcOffset, RiKind.Int);
            UnsafeStoreNode.store(dest, header, i + destOffset, a.intValue(), RiKind.Int);
        }
    }

    @Snippet
    public static void copyLongsDown(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        int header = arrayBaseOffset(RiKind.Long);
        for (long i = (length - 1) * 8; i >= 0; i -= 8) {
            Long a = UnsafeLoadNode.load(src, header, i + srcOffset, RiKind.Long);
            UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), RiKind.Long);
        }
    }

    // Does NOT perform store checks
    @Snippet
    public static void copyObjectsDown(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        int header = arrayBaseOffset(RiKind.Object);
        final int scale = arrayIndexScale(RiKind.Object);
        for (long i = (length - 1) * scale; i >= 0; i -= scale) {
            Object a = UnsafeLoadNode.load(src, header, i + srcOffset, RiKind.Object);
            DirectObjectStoreNode.store(dest, header, i + destOffset, a);
        }
    }
    /**
     * Copies {@code length} bytes from {@code src} starting at {@code srcPos} to {@code dest} starting at {@code destPos}.
     * @param src source object
     * @param srcPos source offset
     * @param dest destination object
     * @param destPos destination offset
     * @param length number of bytes to copy
     */
    @Snippet
    public static void copyBytesUp(Object src, int srcPos, Object dest, int destPos, int length)  {
        int header = arrayBaseOffset(RiKind.Byte);
        for (long i = 0; i < length; i++) {
            Byte a = UnsafeLoadNode.load(src, header, i + srcPos, RiKind.Byte);
            UnsafeStoreNode.store(dest, header, i + destPos, a.byteValue(), RiKind.Byte);
        }
    }

    /**
     * Copies {@code length} shorts from {@code src} starting at offset {@code srcOffset} (in bytes) to {@code dest} starting at offset {@code destOffset} (in bytes).
     * @param src
     * @param srcOffset (in bytes)
     * @param dest
     * @param destOffset (in bytes)
     * @param length  (in shorts)
     */
    @Snippet
    public static void copyShortsUp(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        int header = arrayBaseOffset(RiKind.Short);
        for (long i = 0; i < length * 2L; i += 2) {
            Character a = UnsafeLoadNode.load(src, header, i + srcOffset, RiKind.Short);
            UnsafeStoreNode.store(dest, header, i + destOffset, a.charValue(), RiKind.Short);
        }
    }

    @Snippet
    public static void copyIntsUp(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        int header = arrayBaseOffset(RiKind.Int);
        for (long i = 0; i < length * 4L; i += 4) {
            Integer a = UnsafeLoadNode.load(src, header, i + srcOffset, RiKind.Int);
            UnsafeStoreNode.store(dest, header, i + destOffset, a.intValue(), RiKind.Int);
        }
    }

    @Snippet
    public static void copyLongsUp(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        int header = arrayBaseOffset(RiKind.Long);
        for (long i = 0; i < length * 8L; i += 8) {
            Long a = UnsafeLoadNode.load(src, header, i + srcOffset, RiKind.Long);
            UnsafeStoreNode.store(dest, header, i + destOffset, a.longValue(), RiKind.Long);
        }
    }

    // Does NOT perform store checks
    @Snippet
    public static void copyObjectsUp(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        int header = arrayBaseOffset(RiKind.Object);
        final int scale = arrayIndexScale(RiKind.Object);
        for (long i = 0; i < length * scale; i += scale) {
            Object a = UnsafeLoadNode.load(src, header, i + srcOffset, RiKind.Object);
            DirectObjectStoreNode.store(dest, header, i + destOffset, a);
        }
    }

    @Fold
    static int arrayBaseOffset(RiKind elementKind) {
        return elementKind.arrayBaseOffset();
    }

    @Fold
    static int arrayIndexScale(RiKind elementKind) {
        return elementKind.arrayIndexScale();
    }

    static {
        assert arrayIndexScale(RiKind.Byte) == 1;
        assert arrayIndexScale(RiKind.Boolean) == 1;
        assert arrayIndexScale(RiKind.Char) == 2;
        assert arrayIndexScale(RiKind.Short) == 2;
        assert arrayIndexScale(RiKind.Int) == 4;
        assert arrayIndexScale(RiKind.Long) == 8;
        assert arrayIndexScale(RiKind.Float) == 4;
        assert arrayIndexScale(RiKind.Double) == 8;
    }

    @Fold
    private static int cardTableShift() {
        return CompilerImpl.getInstance().getConfig().cardtableShift;
    }

    @Fold
    private static long cardTableStart() {
        return CompilerImpl.getInstance().getConfig().cardtableStartAddress;
    }
}
