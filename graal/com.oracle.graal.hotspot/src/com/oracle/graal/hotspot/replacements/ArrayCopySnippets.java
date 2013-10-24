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
