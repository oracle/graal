/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.nodes.GuardingPiNode.*;
import static com.oracle.graal.nodes.calc.IsNullNode.*;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.Fold;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

@SuppressWarnings("unused")
public class ArrayCopySnippets implements Snippets {

    private static final EnumMap<Kind, Method> arraycopyMethods = new EnumMap<>(Kind.class);
    private static final EnumMap[][] arraycopyCalls = new EnumMap[2][2];
    private static final EnumMap[][] arraycopyDescriptors = new EnumMap[2][2];

    public static final Method genericArraycopySnippet;

    @SuppressWarnings("unchecked")
    private static void findArraycopyCall(Kind kind, Class<?> arrayClass, boolean aligned, boolean disjoint) throws NoSuchMethodException {
        String name = kind + (aligned ? "Aligned" : "") + (disjoint ? "Disjoint" : "") + "Arraycopy";
        arraycopyCalls[aligned ? 1 : 0][disjoint ? 1 : 0].put(kind, ArrayCopySnippets.class.getDeclaredMethod(name, arrayClass, int.class, arrayClass, int.class, int.class));
        arraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0].put(kind, new ForeignCallDescriptor(name, void.class, Word.class, Word.class, Word.class));
    }

    private static Method lookupArraycopyCall(Kind kind, boolean aligned, boolean disjoint) {
        return (Method) arraycopyCalls[aligned ? 1 : 0][disjoint ? 1 : 0].get(kind);
    }

    @Fold
    public static ForeignCallDescriptor lookupArraycopyDescriptor(Kind kind, boolean aligned, boolean disjoint) {
        return (ForeignCallDescriptor) arraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0].get(kind);
    }

    private static void addArraycopySnippetMethod(Kind kind, Class<?> arrayClass) throws NoSuchMethodException {
        arraycopyMethods.put(kind, ArrayCopySnippets.class.getDeclaredMethod("arraycopy", arrayClass, int.class, arrayClass, int.class, int.class));
        if (CallArrayCopy.getValue()) {
            if (kind != Kind.Object) {
                // Only primitive types are currently supported
                findArraycopyCall(kind, arrayClass, false, false);
                findArraycopyCall(kind, arrayClass, false, true);
                findArraycopyCall(kind, arrayClass, true, false);
                findArraycopyCall(kind, arrayClass, true, true);
            }
        }
    }

    static {
        for (int i = 0; i < arraycopyCalls.length; i++) {
            for (int j = 0; j < arraycopyCalls[i].length; j++) {
                arraycopyCalls[i][j] = new EnumMap<Kind, Method>(Kind.class);
                arraycopyDescriptors[i][j] = new EnumMap<Kind, ForeignCallDescriptor>(Kind.class);
            }
        }
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

    public static Method getSnippetForKind(Kind kind, boolean aligned, boolean disjoint) {
        Method m = lookupArraycopyCall(kind, aligned, disjoint);
        if (m != null) {
            return m;
        }
        return arraycopyMethods.get(kind);
    }

    private static void checkedCopy(Object src, int srcPos, Object dest, int destPos, int length, Kind baseKind) {
        Object nonNullSrc = guardingNonNull(src);
        Object nonNullDest = guardingNonNull(dest);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length);
        UnsafeArrayCopyNode.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, baseKind);
    }

    private static int checkArrayType(Word hub) {
        int layoutHelper = readLayoutHelper(hub);
        if (probability(SLOW_PATH_PROBABILITY, layoutHelper >= 0)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        return layoutHelper;
    }

    private static void checkLimits(Object src, int srcPos, Object dest, int destPos, int length) {
        if (probability(SLOW_PATH_PROBABILITY, srcPos < 0)) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (probability(SLOW_PATH_PROBABILITY, destPos < 0)) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (probability(SLOW_PATH_PROBABILITY, length < 0)) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (probability(SLOW_PATH_PROBABILITY, srcPos + length > ArrayLengthNode.arrayLength(src))) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (probability(SLOW_PATH_PROBABILITY, destPos + length > ArrayLengthNode.arrayLength(dest))) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        checkSuccessCounter.inc();
    }

    @Snippet
    public static void arraycopy(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        byteCounter.inc();
        checkedCopy(src, srcPos, dest, destPos, length, Kind.Byte);
    }

    @Snippet
    public static void arraycopy(boolean[] src, int srcPos, boolean[] dest, int destPos, int length) {
        booleanCounter.inc();
        checkedCopy(src, srcPos, dest, destPos, length, Kind.Boolean);
    }

    @Snippet
    public static void arraycopy(char[] src, int srcPos, char[] dest, int destPos, int length) {
        charCounter.inc();
        checkedCopy(src, srcPos, dest, destPos, length, Kind.Char);
    }

    @Snippet
    public static void arraycopy(short[] src, int srcPos, short[] dest, int destPos, int length) {
        shortCounter.inc();
        checkedCopy(src, srcPos, dest, destPos, length, Kind.Short);
    }

    @Snippet
    public static void arraycopy(int[] src, int srcPos, int[] dest, int destPos, int length) {
        intCounter.inc();
        checkedCopy(src, srcPos, dest, destPos, length, Kind.Int);
    }

    @Snippet
    public static void arraycopy(float[] src, int srcPos, float[] dest, int destPos, int length) {
        floatCounter.inc();
        checkedCopy(src, srcPos, dest, destPos, length, Kind.Float);
    }

    @Snippet
    public static void arraycopy(long[] src, int srcPos, long[] dest, int destPos, int length) {
        longCounter.inc();
        checkedCopy(src, srcPos, dest, destPos, length, Kind.Long);
    }

    @Snippet
    public static void arraycopy(double[] src, int srcPos, double[] dest, int destPos, int length) {
        doubleCounter.inc();
        checkedCopy(src, srcPos, dest, destPos, length, Kind.Double);
    }

    @Snippet
    public static void arraycopy(Object[] src, int srcPos, Object[] dest, int destPos, int length) {
        objectCounter.inc();
        checkedCopy(src, srcPos, dest, destPos, length, Kind.Object);
    }

    @Snippet
    public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length) {
        Object nonNullSrc = guardingNonNull(src);
        Object nonNullDest = guardingNonNull(dest);
        Word srcHub = loadHub(nonNullSrc);
        Word destHub = loadHub(nonNullDest);
        if (probability(FAST_PATH_PROBABILITY, srcHub.equal(destHub)) && probability(FAST_PATH_PROBABILITY, nonNullSrc != nonNullDest)) {
            int layoutHelper = checkArrayType(srcHub);
            final boolean isObjectArray = ((layoutHelper & layoutHelperElementTypePrimitiveInPlace()) == 0);

            checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length);
            if (probability(FAST_PATH_PROBABILITY, isObjectArray)) {
                genericObjectExactCallCounter.inc();
                UnsafeArrayCopyNode.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, Kind.Object);
            } else {
                genericPrimitiveCallCounter.inc();
                UnsafeArrayCopyNode.arraycopyPrimitive(nonNullSrc, srcPos, nonNullDest, destPos, length, layoutHelper);
            }
        } else {
            genericObjectCallCounter.inc();
            System.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length);
        }
    }

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void callArraycopy(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word src, Word dst, Word len);

    private static Word computeBase(Object object, Kind kind, int pos) {
        // In this code pos must be cast to long before being multiplied since shifting the value
        // could be outside the range of int. arrayIndexScale should probably either return long or
        // Word to force the right thing to happen.
        return Word.unsigned(GetObjectAddressNode.get(object) + arrayBaseOffset(kind) + (long) pos * arrayIndexScale(kind));
    }

    private static void callArraycopyTemplate(SnippetCounter counter, Kind kind, boolean aligned, boolean disjoint, Object src, int srcPos, Object dest, int destPos, int length) {
        counter.inc();
        Object nonNullSrc = guardingNonNull(src);
        Object nonNullDest = guardingNonNull(dest);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length);
        Word srcAddr = computeBase(src, kind, srcPos);
        Word destAddr = computeBase(dest, kind, destPos);
        callArraycopy(lookupArraycopyDescriptor(kind, aligned, disjoint), srcAddr, destAddr, Word.signed(length));
    }

    @Snippet
    public static void byteArraycopy(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        callArraycopyTemplate(byteCallCounter, Kind.Byte, false, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void byteDisjointArraycopy(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        callArraycopyTemplate(byteDisjointCallCounter, Kind.Byte, false, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void byteAlignedArraycopy(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        callArraycopyTemplate(byteAlignedCallCounter, Kind.Byte, true, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void byteAlignedDisjointArraycopy(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        callArraycopyTemplate(byteAlignedDisjointCallCounter, Kind.Byte, true, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void booleanArraycopy(boolean[] src, int srcPos, boolean[] dest, int destPos, int length) {
        callArraycopyTemplate(booleanCallCounter, Kind.Boolean, false, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void booleanDisjointArraycopy(boolean[] src, int srcPos, boolean[] dest, int destPos, int length) {
        callArraycopyTemplate(booleanDisjointCallCounter, Kind.Boolean, false, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void booleanAlignedArraycopy(boolean[] src, int srcPos, boolean[] dest, int destPos, int length) {
        callArraycopyTemplate(booleanAlignedCallCounter, Kind.Boolean, true, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void booleanAlignedDisjointArraycopy(boolean[] src, int srcPos, boolean[] dest, int destPos, int length) {
        callArraycopyTemplate(booleanAlignedDisjointCallCounter, Kind.Boolean, true, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void charArraycopy(char[] src, int srcPos, char[] dest, int destPos, int length) {
        callArraycopyTemplate(charCallCounter, Kind.Char, false, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void charDisjointArraycopy(char[] src, int srcPos, char[] dest, int destPos, int length) {
        callArraycopyTemplate(charDisjointCallCounter, Kind.Char, false, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void charAlignedArraycopy(char[] src, int srcPos, char[] dest, int destPos, int length) {
        callArraycopyTemplate(charAlignedCallCounter, Kind.Char, true, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void charAlignedDisjointArraycopy(char[] src, int srcPos, char[] dest, int destPos, int length) {
        callArraycopyTemplate(charAlignedDisjointCallCounter, Kind.Char, true, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void shortArraycopy(short[] src, int srcPos, short[] dest, int destPos, int length) {
        callArraycopyTemplate(shortCallCounter, Kind.Short, false, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void shortDisjointArraycopy(short[] src, int srcPos, short[] dest, int destPos, int length) {
        callArraycopyTemplate(shortDisjointCallCounter, Kind.Short, false, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void shortAlignedArraycopy(short[] src, int srcPos, short[] dest, int destPos, int length) {
        callArraycopyTemplate(shortAlignedCallCounter, Kind.Short, true, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void shortAlignedDisjointArraycopy(short[] src, int srcPos, short[] dest, int destPos, int length) {
        callArraycopyTemplate(shortAlignedDisjointCallCounter, Kind.Short, true, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void intArraycopy(int[] src, int srcPos, int[] dest, int destPos, int length) {
        callArraycopyTemplate(intCallCounter, Kind.Int, false, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void intDisjointArraycopy(int[] src, int srcPos, int[] dest, int destPos, int length) {
        callArraycopyTemplate(intDisjointCallCounter, Kind.Int, false, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void intAlignedArraycopy(int[] src, int srcPos, int[] dest, int destPos, int length) {
        callArraycopyTemplate(intAlignedCallCounter, Kind.Int, true, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void intAlignedDisjointArraycopy(int[] src, int srcPos, int[] dest, int destPos, int length) {
        callArraycopyTemplate(intAlignedDisjointCallCounter, Kind.Int, true, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void floatArraycopy(float[] src, int srcPos, float[] dest, int destPos, int length) {
        callArraycopyTemplate(floatCallCounter, Kind.Float, false, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void floatDisjointArraycopy(float[] src, int srcPos, float[] dest, int destPos, int length) {
        callArraycopyTemplate(floatDisjointCallCounter, Kind.Float, false, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void floatAlignedArraycopy(float[] src, int srcPos, float[] dest, int destPos, int length) {
        callArraycopyTemplate(floatAlignedCallCounter, Kind.Float, true, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void floatAlignedDisjointArraycopy(float[] src, int srcPos, float[] dest, int destPos, int length) {
        callArraycopyTemplate(floatAlignedDisjointCallCounter, Kind.Float, true, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void longArraycopy(long[] src, int srcPos, long[] dest, int destPos, int length) {
        callArraycopyTemplate(longCallCounter, Kind.Long, false, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void longDisjointArraycopy(long[] src, int srcPos, long[] dest, int destPos, int length) {
        callArraycopyTemplate(longDisjointCallCounter, Kind.Long, false, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void longAlignedArraycopy(long[] src, int srcPos, long[] dest, int destPos, int length) {
        callArraycopyTemplate(longAlignedCallCounter, Kind.Long, true, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void longAlignedDisjointArraycopy(long[] src, int srcPos, long[] dest, int destPos, int length) {
        callArraycopyTemplate(longAlignedDisjointCallCounter, Kind.Long, true, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void doubleArraycopy(double[] src, int srcPos, double[] dest, int destPos, int length) {
        callArraycopyTemplate(doubleCallCounter, Kind.Double, false, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void doubleDisjointArraycopy(double[] src, int srcPos, double[] dest, int destPos, int length) {
        callArraycopyTemplate(doubleDisjointCallCounter, Kind.Double, false, true, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void doubleAlignedArraycopy(double[] src, int srcPos, double[] dest, int destPos, int length) {
        callArraycopyTemplate(doubleAlignedCallCounter, Kind.Double, true, false, src, srcPos, dest, destPos, length);
    }

    @Snippet
    public static void doubleAlignedDisjointArraycopy(double[] src, int srcPos, double[] dest, int destPos, int length) {
        callArraycopyTemplate(doubleAlignedDisjointCallCounter, Kind.Double, true, true, src, srcPos, dest, destPos, length);
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

    private static final SnippetCounter booleanAlignedCallCounter = new SnippetCounter(counters, "boolean[]", "aligned arraycopy call for boolean[] arrays");
    private static final SnippetCounter booleanAlignedDisjointCallCounter = new SnippetCounter(counters, "boolean[]", "aligned disjoint arraycopy call for boolean[] arrays");
    private static final SnippetCounter booleanCallCounter = new SnippetCounter(counters, "boolean[]", "arraycopy call for boolean[] arrays");
    private static final SnippetCounter booleanDisjointCallCounter = new SnippetCounter(counters, "boolean[]", "disjoint arraycopy call for boolean[] arrays");
    private static final SnippetCounter byteAlignedCallCounter = new SnippetCounter(counters, "byte[]", "aligned arraycopy call for byte[] arrays");
    private static final SnippetCounter byteAlignedDisjointCallCounter = new SnippetCounter(counters, "byte[]", "aligned disjoint arraycopy call for byte[] arrays");
    private static final SnippetCounter byteCallCounter = new SnippetCounter(counters, "byte[]", "arraycopy call for byte[] arrays");
    private static final SnippetCounter byteDisjointCallCounter = new SnippetCounter(counters, "byte[]", "disjoint arraycopy call for byte[] arrays");
    private static final SnippetCounter charAlignedCallCounter = new SnippetCounter(counters, "char[]", "aligned arraycopy call for char[] arrays");
    private static final SnippetCounter charAlignedDisjointCallCounter = new SnippetCounter(counters, "char[]", "aligned disjoint arraycopy call for char[] arrays");
    private static final SnippetCounter charCallCounter = new SnippetCounter(counters, "char[]", "arraycopy call for char[] arrays");
    private static final SnippetCounter charDisjointCallCounter = new SnippetCounter(counters, "char[]", "disjoint arraycopy call for char[] arrays");
    private static final SnippetCounter doubleAlignedCallCounter = new SnippetCounter(counters, "double[]", "aligned arraycopy call for double[] arrays");
    private static final SnippetCounter doubleAlignedDisjointCallCounter = new SnippetCounter(counters, "double[]", "aligned disjoint arraycopy call for double[] arrays");
    private static final SnippetCounter doubleCallCounter = new SnippetCounter(counters, "double[]", "arraycopy call for double[] arrays");
    private static final SnippetCounter doubleDisjointCallCounter = new SnippetCounter(counters, "double[]", "disjoint arraycopy call for double[] arrays");
    private static final SnippetCounter floatAlignedCallCounter = new SnippetCounter(counters, "float[]", "aligned arraycopy call for float[] arrays");
    private static final SnippetCounter floatAlignedDisjointCallCounter = new SnippetCounter(counters, "float[]", "aligned disjoint arraycopy call for float[] arrays");
    private static final SnippetCounter floatCallCounter = new SnippetCounter(counters, "float[]", "arraycopy call for float[] arrays");
    private static final SnippetCounter floatDisjointCallCounter = new SnippetCounter(counters, "float[]", "disjoint arraycopy call for float[] arrays");
    private static final SnippetCounter intAlignedCallCounter = new SnippetCounter(counters, "int[]", "aligned arraycopy call for int[] arrays");
    private static final SnippetCounter intAlignedDisjointCallCounter = new SnippetCounter(counters, "int[]", "aligned disjoint arraycopy call for int[] arrays");
    private static final SnippetCounter intCallCounter = new SnippetCounter(counters, "int[]", "arraycopy call for int[] arrays");
    private static final SnippetCounter intDisjointCallCounter = new SnippetCounter(counters, "int[]", "disjoint arraycopy call for int[] arrays");
    private static final SnippetCounter longAlignedCallCounter = new SnippetCounter(counters, "long[]", "aligned arraycopy call for long[] arrays");
    private static final SnippetCounter longAlignedDisjointCallCounter = new SnippetCounter(counters, "long[]", "aligned disjoint arraycopy call for long[] arrays");
    private static final SnippetCounter longCallCounter = new SnippetCounter(counters, "long[]", "arraycopy call for long[] arrays");
    private static final SnippetCounter longDisjointCallCounter = new SnippetCounter(counters, "long[]", "disjoint arraycopy call for long[] arrays");
    private static final SnippetCounter shortAlignedCallCounter = new SnippetCounter(counters, "short[]", "aligned arraycopy call for short[] arrays");
    private static final SnippetCounter shortAlignedDisjointCallCounter = new SnippetCounter(counters, "short[]", "aligned disjoint arraycopy call for short[] arrays");
    private static final SnippetCounter shortCallCounter = new SnippetCounter(counters, "short[]", "arraycopy call for short[] arrays");
    private static final SnippetCounter shortDisjointCallCounter = new SnippetCounter(counters, "short[]", "disjoint arraycopy call for short[] arrays");

    private static final SnippetCounter genericPrimitiveCallCounter = new SnippetCounter(counters, "genericPrimitive", "generic arraycopy snippet for primitive arrays");
    private static final SnippetCounter genericObjectExactCallCounter = new SnippetCounter(counters, "genericObjectExact", "generic arraycopy snippet for special object arrays");
    private static final SnippetCounter genericObjectCallCounter = new SnippetCounter(counters, "genericObject", "call to the generic, native arraycopy method");

}
