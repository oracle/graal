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
import static com.oracle.graal.api.code.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.hotspot.snippets.HotSpotSnippetUtils.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Fold;
import com.oracle.graal.snippets.nodes.*;


@SuppressWarnings("unused")
public class ArrayCopySnippets implements SnippetsInterface{

    private static final EnumMap<Kind, Method> arraycopyMethods = new EnumMap<>(Kind.class);
    public static final Method increaseGenericCallCounterMethod;

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
            increaseGenericCallCounterMethod = ArrayCopySnippets.class.getDeclaredMethod("increaseGenericCallCounter", Object.class, int.class, Object.class, int.class, int.class);
        } catch (SecurityException | NoSuchMethodException e) {
            throw new GraalInternalError(e);
        }
    }

    public static Method getSnippetForKind(Kind kind) {
        return arraycopyMethods.get(kind);
    }

    private static final Kind VECTOR_KIND = Kind.Long;
    private static final long VECTOR_SIZE = arrayIndexScale(Kind.Long);

    public static void vectorizedCopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter("baseKind") Kind baseKind) {
        checkInputs(src, srcPos, dest, destPos, length);
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

    public static void checkInputs(Object src, int srcPos, Object dest, int destPos, int length) {
        if (src == null || dest == null) {
            checkNPECounter.inc();
            throw new NullPointerException();
        }
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos + length > ArrayLengthNode.arrayLength(src) || destPos + length > ArrayLengthNode.arrayLength(dest)) {
            checkAIOOBECounter.inc();
            throw new ArrayIndexOutOfBoundsException();
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
        checkInputs(src, srcPos, dest, destPos, length);
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
        checkInputs(src, srcPos, dest, destPos, length);
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
        objectCounter.inc();
        checkInputs(src, srcPos, dest, destPos, length);
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

    @Snippet
    public static void increaseGenericCallCounter(Object src, int srcPos, Object dest, int destPos, int length) {
        if (GraalOptions.SnippetCounters) {
            if (src.getClass().getComponentType().isPrimitive()) {
                genericPrimitiveCallCounter.inc();
            } else {
                genericObjectCallCounter.inc();
            }
        }
    }

    private static final SnippetCounter.Group checkCounters = GraalOptions.SnippetCounters ? new SnippetCounter.Group("System.arraycopy checkInputs") : null;
    private static final SnippetCounter checkSuccessCounter = new SnippetCounter(checkCounters, "checkSuccess", "checkSuccess");
    private static final SnippetCounter checkNPECounter = new SnippetCounter(checkCounters, "checkNPE", "checkNPE");
    private static final SnippetCounter checkAIOOBECounter = new SnippetCounter(checkCounters, "checkAIOOBE", "checkAIOOBE");

    private static final SnippetCounter.Group counters = GraalOptions.SnippetCounters ? new SnippetCounter.Group("System.arraycopy") : null;
    private static final SnippetCounter byteCounter = new SnippetCounter(counters, "byte[]", "arraycopy for byte[] arrays");
    private static final SnippetCounter charCounter = new SnippetCounter(counters, "char[]", "arraycopy for char[] arrays");
    private static final SnippetCounter shortCounter = new SnippetCounter(counters, "short[]", "arraycopy for short[] arrays");
    private static final SnippetCounter intCounter = new SnippetCounter(counters, "int[]", "arraycopy for int[] arrays");
    private static final SnippetCounter booleanCounter = new SnippetCounter(counters, "boolean[]", "arraycopy for boolean[] arrays");
    private static final SnippetCounter longCounter = new SnippetCounter(counters, "long[]", "arraycopy for long[] arrays");
    private static final SnippetCounter objectCounter = new SnippetCounter(counters, "Object[]", "arraycopy for Object[] arrays");
    private static final SnippetCounter floatCounter = new SnippetCounter(counters, "float[]", "arraycopy for float[] arrays");
    private static final SnippetCounter doubleCounter = new SnippetCounter(counters, "double[]", "arraycopy for double[] arrays");
    private static final SnippetCounter genericPrimitiveCallCounter = new SnippetCounter(counters, "genericPrimitive", "call to the generic, native arraycopy method");
    private static final SnippetCounter genericObjectCallCounter = new SnippetCounter(counters, "genericObject", "call to the generic, native arraycopy method");


}
