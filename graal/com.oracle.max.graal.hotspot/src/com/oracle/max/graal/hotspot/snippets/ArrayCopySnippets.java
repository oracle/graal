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
package com.oracle.max.graal.hotspot.snippets;

import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.hotspot.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.oracle.max.graal.snippets.*;
import com.oracle.max.graal.snippets.nodes.*;


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
        if (src == dest && srcPos < destPos) { // bad aliased case
            copyObjectsDown(src, srcPos * 8L, dest, destPos * 8L, length);
        } else {
            copyObjectsUp(src, srcPos * 8L, dest, destPos * 8L, length);
        }
        if (length > 0) {
            long header = ArrayHeaderSizeNode.sizeFor(CiKind.Object);
            int cardShift = CardTableShiftNode.get();
            long cardStart = CardTableStartNode.get();
            long dstAddr = GetObjectAddressNode.get(dest);
            long start = (dstAddr + header + destPos * 8L) >>> cardShift;
            long end = (dstAddr + header + (destPos + length - 1) * 8L) >>> cardShift;
            long count = end - start;
            while (count-- >= 0) {
                DirectStoreNode.store((start + cardStart) + count, false);
            }
        }
    }

    @Snippet
    public static void copyBytesDown(Object src, int srcPos, Object dest, int destPos, int length)  {
        long header = ArrayHeaderSizeNode.sizeFor(CiKind.Byte);
        for (long i = length - 1; i >= 0; i--) {
            Byte a = UnsafeLoadNode.load(src, i + (srcPos + header), CiKind.Byte);
            UnsafeStoreNode.store(dest, i + (destPos + header), a.byteValue(), CiKind.Byte);
        }
    }

    @Snippet
    public static void copyShortsDown(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        long header = ArrayHeaderSizeNode.sizeFor(CiKind.Short);
        for (long i = (length - 1) * 2; i >= 0; i -= 2) {
            Character a = UnsafeLoadNode.load(src, i + (srcOffset + header), CiKind.Short);
            UnsafeStoreNode.store(dest, i + (destOffset + header), a.charValue(), CiKind.Short);
        }
    }

    @Snippet
    public static void copyIntsDown(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        long header = ArrayHeaderSizeNode.sizeFor(CiKind.Short);
        for (long i = (length - 1) * 4; i >= 0; i -= 4) {
            Integer a = UnsafeLoadNode.load(src, i + (srcOffset + header), CiKind.Int);
            UnsafeStoreNode.store(dest, i + (destOffset + header), a.intValue(), CiKind.Int);
        }
    }

    @Snippet
    public static void copyLongsDown(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        long header = ArrayHeaderSizeNode.sizeFor(CiKind.Short);
        for (long i = (length - 1) * 8; i >= 0; i -= 8) {
            Long a = UnsafeLoadNode.load(src, i + (srcOffset + header), CiKind.Long);
            UnsafeStoreNode.store(dest, i + (destOffset + header), a.longValue(), CiKind.Long);
        }
    }

    // Does NOT perform store checks
    @Snippet
    public static void copyObjectsDown(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        long header = ArrayHeaderSizeNode.sizeFor(CiKind.Object);
        for (long i = (length - 1) * 8; i >= 0; i -= 8) {
            Object a = UnsafeLoadNode.load(src, i + (srcOffset + header), CiKind.Object);
            UnsafeStoreNode.store(dest, i + (destOffset + header), a, CiKind.Object);
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
        long header = ArrayHeaderSizeNode.sizeFor(CiKind.Byte);
        for (long i = 0; i < length; i++) {
            Byte a = UnsafeLoadNode.load(src, i + (srcPos + header), CiKind.Byte);
            UnsafeStoreNode.store(dest, i + (destPos + header), a.byteValue(), CiKind.Byte);
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
        long header = ArrayHeaderSizeNode.sizeFor(CiKind.Short);
        for (long i = 0; i < length * 2L; i += 2) {
            Character a = UnsafeLoadNode.load(src, i + (srcOffset + header), CiKind.Short);
            UnsafeStoreNode.store(dest, i + (destOffset + header), a.charValue(), CiKind.Short);
        }
    }

    @Snippet
    public static void copyIntsUp(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        long header = ArrayHeaderSizeNode.sizeFor(CiKind.Int);
        for (long i = 0; i < length * 4L; i += 4) {
            Integer a = UnsafeLoadNode.load(src, i + (srcOffset + header), CiKind.Int);
            UnsafeStoreNode.store(dest, i + (destOffset + header), a.intValue(), CiKind.Int);
        }
    }

    @Snippet
    public static void copyLongsUp(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        long header = ArrayHeaderSizeNode.sizeFor(CiKind.Long);
        for (long i = 0; i < length * 8L; i += 8) {
            Long a = UnsafeLoadNode.load(src, i + (srcOffset + header), CiKind.Long);
            UnsafeStoreNode.store(dest, i + (destOffset + header), a.longValue(), CiKind.Long);
        }
    }

    // Does NOT perform store checks
    @Snippet
    public static void copyObjectsUp(Object src, long srcOffset, Object dest, long destOffset, int length)  {
        long header = ArrayHeaderSizeNode.sizeFor(CiKind.Object);
        for (long i = 0; i < length * 8L; i += 8) {
            Object a = UnsafeLoadNode.load(src, i + (srcOffset + header), CiKind.Object);
            UnsafeStoreNode.store(dest, i + (destOffset + header), a, CiKind.Object);
        }
    }

    private static class GetObjectAddressNode extends FixedWithNextNode implements LIRLowerable {
        @Input private ValueNode object;

        public GetObjectAddressNode(ValueNode obj) {
            super(StampFactory.forKind(CiKind.Long));
            this.object = obj;
        }

        @SuppressWarnings("unused")
        @NodeIntrinsic
        public static long get(Object array) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void generate(LIRGeneratorTool gen) {
            CiValue obj = gen.newVariable(gen.target().wordKind);
            gen.emitMove(gen.operand(object), obj);
            gen.setResult(this, obj);
        }
    }

    private static class DirectStoreNode extends FixedWithNextNode implements LIRLowerable {
        @Input private ValueNode address;
        @Input private ValueNode value;

        public DirectStoreNode(ValueNode address, ValueNode value) {
            super(StampFactory.illegal());
            this.address = address;
            this.value = value;
        }

        @SuppressWarnings("unused")
        @NodeIntrinsic
        public static void store(long address, long value) {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unused")
        @NodeIntrinsic
        public static void store(long address, boolean value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void generate(LIRGeneratorTool gen) {
            CiValue v = gen.operand(value);
            gen.emitStore(new CiAddress(v.kind, gen.operand(address)), v, false);
        }
    }

    private static class CardTableShiftNode extends ConstantNode {
        public CardTableShiftNode() {
            super(CiConstant.forInt(CompilerImpl.getInstance().getConfig().cardtableShift));
        }

        @NodeIntrinsic
        public static int get() {
            throw new UnsupportedOperationException();
        }
    }

    private static class CardTableStartNode extends ConstantNode {
        public CardTableStartNode() {
            super(CiConstant.forLong(CompilerImpl.getInstance().getConfig().cardtableStartAddress));
        }

        @NodeIntrinsic
        public static long get() {
            throw new UnsupportedOperationException();
        }
    }
}
