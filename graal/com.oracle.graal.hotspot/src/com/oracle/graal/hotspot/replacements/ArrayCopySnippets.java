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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.phases.GraalOptions.*;
import static com.oracle.graal.replacements.nodes.BranchProbabilityNode.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

@SuppressWarnings("unused")
public class ArrayCopySnippets implements Snippets {

    private static final EnumMap<Kind, Method> arraycopyMethods = new EnumMap<>(Kind.class);
    public static final Method genericArraycopySnippet;

    private static void addArraycopySnippetMethod(Kind kind, Class<?> arrayClass) throws NoSuchMethodException {
        arraycopyMethods.put(kind, ArrayCopySnippets.class.getDeclaredMethod("arraycopy", arrayClass, int.class, arrayClass, int.class, int.class));
    }

    static {
        try {
            addArraycopySnippetMethod(Kind.Byte, byte[].class);
            addArraycopySnippetMethod(Kind.Boolean, boolean[].class);
            addArraycopySnippetMethod(Kind.Char, char[].class);
            addArraycopySnippetMethod(Kind.Short, short[].class);
            addArraycopySnippetMethod(Kind.Int, int[].class);
            addArraycopySnippetMethod(Kind.Long, long[].class);
            addArraycopySnippetMethod(Kind.Float, float[].class);
            addArraycopySnippetMethod(Kind.Double, double[].class);
            addArraycopySnippetMethod(Kind.Object, Object[].class);
            genericArraycopySnippet = ArrayCopySnippets.class.getDeclaredMethod("arraycopy", Object.class, int.class, Object.class, int.class, int.class);
        } catch (SecurityException | NoSuchMethodException e) {
            throw new GraalInternalError(e);
        }
    }

    public static Method getSnippetForKind(Kind kind) {
        return arraycopyMethods.get(kind);
    }

    private static final Kind VECTOR_KIND = Kind.Long;
    private static final long VECTOR_SIZE = arrayIndexScale(Kind.Long);

    private static void vectorizedCopy(Object src, int srcPos, Object dest, int destPos, int length, Kind baseKind) {
        checkNonNull(src);
        checkNonNull(dest);
        checkLimits(src, srcPos, dest, destPos, length);
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

    public static void checkNonNull(Object obj) {
        if (obj == null) {
            checkNPECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
    }

    public static int checkArrayType(Word hub) {
        int layoutHelper = readLayoutHelper(hub);
        if (layoutHelper >= 0) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        return layoutHelper;
    }

    public static void checkLimits(Object src, int srcPos, Object dest, int destPos, int length) {
        if (srcPos < 0) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (destPos < 0) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (length < 0) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (srcPos + length > ArrayLengthNode.arrayLength(src)) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (destPos + length > ArrayLengthNode.arrayLength(dest)) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        checkSuccessCounter.inc();
    }

    @Snippet
    public static void arraycopy(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        byteCounter.inc();
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Byte);
    }

    @Snippet
    public static void arraycopy(boolean[] src, int srcPos, boolean[] dest, int destPos, int length) {
        booleanCounter.inc();
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Byte);
    }

    @Snippet
    public static void arraycopy(char[] src, int srcPos, char[] dest, int destPos, int length) {
        charCounter.inc();
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Char);
    }

    @Snippet
    public static void arraycopy(short[] src, int srcPos, short[] dest, int destPos, int length) {
        shortCounter.inc();
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Short);
    }

    @Snippet
    public static void arraycopy(int[] src, int srcPos, int[] dest, int destPos, int length) {
        intCounter.inc();
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Int);
    }

    @Snippet
    public static void arraycopy(float[] src, int srcPos, float[] dest, int destPos, int length) {
        floatCounter.inc();
        vectorizedCopy(src, srcPos, dest, destPos, length, Kind.Float);
    }

    @Snippet
    public static void arraycopy(long[] src, int srcPos, long[] dest, int destPos, int length) {
        longCounter.inc();
        checkNonNull(src);
        checkNonNull(dest);
        checkLimits(src, srcPos, dest, destPos, length);
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
        doubleCounter.inc();
        checkNonNull(src);
        checkNonNull(dest);
        checkLimits(src, srcPos, dest, destPos, length);
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

    @Snippet
    public static void arraycopy(Object[] src, int srcPos, Object[] dest, int destPos, int length) {
        arrayObjectCopy(src, srcPos, dest, destPos, length);
    }

    // Does NOT perform store checks
    @Snippet
    public static void arrayObjectCopy(Object src, int srcPos, Object dest, int destPos, int length) {
        objectCounter.inc();
        checkNonNull(src);
        checkNonNull(dest);
        checkLimits(src, srcPos, dest, destPos, length);
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
            GenericArrayRangeWriteBarrier.insertWriteBarrier(dest, destPos, length);
        }
    }

    @Snippet
    public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length) {

        // loading the hubs also checks for nullness
        Word srcHub = loadHub(src);
        Word destHub = loadHub(dest);
        int layoutHelper = checkArrayType(srcHub);
        int log2ElementSize = (layoutHelper >> layoutHelperLog2ElementSizeShift()) & layoutHelperLog2ElementSizeMask();
        final boolean isObjectArray = ((layoutHelper & layoutHelperElementTypePrimitiveInPlace()) == 0);

        if (probability(FAST_PATH_PROBABILITY, srcHub.equal(destHub)) && probability(FAST_PATH_PROBABILITY, src != dest)) {
            checkLimits(src, srcPos, dest, destPos, length);
            if (probability(FAST_PATH_PROBABILITY, isObjectArray)) {
                genericObjectExactCallCounter.inc();
                arrayObjectCopy(src, srcPos, dest, destPos, length);
            } else {
                genericPrimitiveCallCounter.inc();
                arraycopyInnerloop(src, srcPos, dest, destPos, length, layoutHelper);
            }
        } else {
            genericObjectCallCounter.inc();
            System.arraycopy(src, srcPos, dest, destPos, length);
        }
    }

    public static void arraycopyInnerloop(Object src, int srcPos, Object dest, int destPos, int length, int layoutHelper) {
        int log2ElementSize = (layoutHelper >> layoutHelperLog2ElementSizeShift()) & layoutHelperLog2ElementSizeMask();
        int headerSize = (layoutHelper >> layoutHelperHeaderSizeShift()) & layoutHelperHeaderSizeMask();

        Word memory = (Word) Word.fromObject(src);

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

    private static final SnippetCounter.Group checkCounters = SnippetCounters.getValue() ? new SnippetCounter.Group("System.arraycopy checkInputs") : null;
    private static final SnippetCounter checkSuccessCounter = new SnippetCounter(checkCounters, "checkSuccess", "checkSuccess");
    private static final SnippetCounter checkNPECounter = new SnippetCounter(checkCounters, "checkNPE", "checkNPE");
    private static final SnippetCounter checkAIOOBECounter = new SnippetCounter(checkCounters, "checkAIOOBE", "checkAIOOBE");

    private static final SnippetCounter.Group counters = SnippetCounters.getValue() ? new SnippetCounter.Group("System.arraycopy") : null;
    private static final SnippetCounter byteCounter = new SnippetCounter(counters, "byte[]", "arraycopy for byte[] arrays");
    private static final SnippetCounter charCounter = new SnippetCounter(counters, "char[]", "arraycopy for char[] arrays");
    private static final SnippetCounter shortCounter = new SnippetCounter(counters, "short[]", "arraycopy for short[] arrays");
    private static final SnippetCounter intCounter = new SnippetCounter(counters, "int[]", "arraycopy for int[] arrays");
    private static final SnippetCounter booleanCounter = new SnippetCounter(counters, "boolean[]", "arraycopy for boolean[] arrays");
    private static final SnippetCounter longCounter = new SnippetCounter(counters, "long[]", "arraycopy for long[] arrays");
    private static final SnippetCounter objectCounter = new SnippetCounter(counters, "Object[]", "arraycopy for Object[] arrays");
    private static final SnippetCounter floatCounter = new SnippetCounter(counters, "float[]", "arraycopy for float[] arrays");
    private static final SnippetCounter doubleCounter = new SnippetCounter(counters, "double[]", "arraycopy for double[] arrays");
    private static final SnippetCounter genericPrimitiveCallCounter = new SnippetCounter(counters, "genericPrimitive", "generic arraycopy snippet for primitive arrays");
    private static final SnippetCounter genericObjectExactCallCounter = new SnippetCounter(counters, "genericObjectExact", "generic arraycopy snippet for special object arrays");
    private static final SnippetCounter genericObjectCallCounter = new SnippetCounter(counters, "genericObject", "call to the generic, native arraycopy method");

}
