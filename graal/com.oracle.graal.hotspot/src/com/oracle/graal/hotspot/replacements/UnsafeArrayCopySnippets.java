/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;
import static com.oracle.graal.replacements.nodes.BranchProbabilityNode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

public class UnsafeArrayCopySnippets implements Snippets {

    private static final Kind VECTOR_KIND = Kind.Long;
    private static final long VECTOR_SIZE = arrayIndexScale(Kind.Long);

    private static void vectorizedCopy(Object src, int srcPos, Object dest, int destPos, int length, Kind baseKind) {
        int header = arrayBaseOffset(baseKind);
        int elementSize = arrayIndexScale(baseKind);
        long byteLength = (long) length * elementSize;
        long nonVectorBytes = byteLength % VECTOR_SIZE;
        long srcOffset = (long) srcPos * elementSize;
        long destOffset = (long) destPos * elementSize;
        if (probability(NOT_FREQUENT_PROBABILITY, src == dest) && probability(NOT_FREQUENT_PROBABILITY, srcPos < destPos)) {
            // bad aliased case
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
    public static void arraycopyByte(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Byte);
    }

    @Snippet
    public static void arraycopyBoolean(boolean[] src, int srcPos, boolean[] dest, int destPos, int length) {
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Byte);
    }

    @Snippet
    public static void arraycopyChar(char[] src, int srcPos, char[] dest, int destPos, int length) {
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Char);
    }

    @Snippet
    public static void arraycopyShort(short[] src, int srcPos, short[] dest, int destPos, int length) {
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Short);
    }

    @Snippet
    public static void arraycopyInt(int[] src, int srcPos, int[] dest, int destPos, int length) {
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Int);
    }

    @Snippet
    public static void arraycopyFloat(float[] src, int srcPos, float[] dest, int destPos, int length) {
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Float);
    }

    @Snippet
    public static void arraycopyLong(long[] src, int srcPos, long[] dest, int destPos, int length) {
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
    public static void arraycopyDouble(double[] src, int srcPos, double[] dest, int destPos, int length) {
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

    /**
     * Does NOT perform store checks.
     */
    @Snippet
    public static void arraycopyObject(Object[] src, int srcPos, Object[] dest, int destPos, int length) {
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
    }

    @Snippet
    public static void arraycopyPrimitive(Object src, int srcPos, Object dest, int destPos, int length, int layoutHelper) {
        int log2ElementSize = (layoutHelper >> layoutHelperLog2ElementSizeShift()) & layoutHelperLog2ElementSizeMask();
        int headerSize = (layoutHelper >> layoutHelperHeaderSizeShift()) & layoutHelperHeaderSizeMask();

        Word srcOffset = (Word) Word.fromObject(src).add(headerSize).add(srcPos << log2ElementSize);
        Word destOffset = (Word) Word.fromObject(dest).add(headerSize).add(destPos << log2ElementSize);
        Word destStart = destOffset;
        long sizeInBytes = ((long) length) << log2ElementSize;
        Word destEnd = destOffset.add(Word.unsigned(length).shiftLeft(log2ElementSize));

        int nonVectorBytes = (int) (sizeInBytes % VECTOR_SIZE);
        Word destNonVectorEnd = destStart.add(nonVectorBytes);

        while (destOffset.belowThan(destNonVectorEnd)) {
            destOffset.writeByte(0, srcOffset.readByte(0, ANY_LOCATION), ANY_LOCATION);
            destOffset = destOffset.add(1);
            srcOffset = srcOffset.add(1);
        }
        while (destOffset.belowThan(destEnd)) {
            destOffset.writeWord(0, srcOffset.readWord(0, ANY_LOCATION), ANY_LOCATION);
            destOffset = destOffset.add(wordSize());
            srcOffset = srcOffset.add(wordSize());
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo[] arraycopySnippets;
        private final SnippetInfo genericPrimitiveSnippet;

        public Templates(MetaAccessProvider runtime, Replacements replacements, TargetDescription target) {
            super(runtime, replacements, target);

            arraycopySnippets = new SnippetInfo[Kind.values().length];
            arraycopySnippets[Kind.Boolean.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyBoolean");
            arraycopySnippets[Kind.Byte.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyByte");
            arraycopySnippets[Kind.Short.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyShort");
            arraycopySnippets[Kind.Char.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyChar");
            arraycopySnippets[Kind.Int.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyInt");
            arraycopySnippets[Kind.Long.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyLong");
            arraycopySnippets[Kind.Float.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyFloat");
            arraycopySnippets[Kind.Double.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyDouble");
            arraycopySnippets[Kind.Object.ordinal()] = snippet(UnsafeArrayCopySnippets.class, "arraycopyObject");

            genericPrimitiveSnippet = snippet(UnsafeArrayCopySnippets.class, "arraycopyPrimitive");
        }

        public void lower(UnsafeArrayCopyNode node) {
            Kind elementKind = node.getElementKind();
            SnippetInfo snippet;
            if (elementKind == null) {
                // primitive array of unknown kind
                snippet = genericPrimitiveSnippet;
            } else {
                snippet = arraycopySnippets[elementKind.ordinal()];
                assert snippet != null : "arraycopy snippet for " + elementKind.name() + " not found";
            }

            Arguments args = new Arguments(snippet);
            node.addSnippetArguments(args);

            SnippetTemplate template = template(args);
            template.instantiate(runtime, node, DEFAULT_REPLACER, args);
        }
    }
}
