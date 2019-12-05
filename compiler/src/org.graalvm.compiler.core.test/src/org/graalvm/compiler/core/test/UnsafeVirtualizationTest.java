/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.core.test;

import java.lang.reflect.Field;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.ea.EATestBase.TestClassInt;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

public class UnsafeVirtualizationTest extends GraalCompilerTest {

    private static boolean[] FT = new boolean[]{false, true};

    public static class Base {
        /*
         * This padding ensure that the size of the Base class ends up as a multiple of 8, which
         * makes the first field of the subclass 8-byte aligned.
         */
        double padding;
    }

    public static class A extends Base {
        int f1;
        int f2;
    }

    private static final long AF1Offset;
    private static final long AF2Offset;
    static {
        long o1 = -1;
        long o2 = -1;
        try {
            Field f1 = A.class.getDeclaredField("f1");
            Field f2 = A.class.getDeclaredField("f2");
            o1 = UNSAFE.objectFieldOffset(f1);
            o2 = UNSAFE.objectFieldOffset(f2);
        } catch (NoSuchFieldException | SecurityException e) {
            throw new AssertionError(e);
        }
        AF1Offset = o1;
        AF2Offset = o2;
    }

    // Side effect to create a deopt point, after possible virtualization.
    static int sideEffectField;

    private static void sideEffect() {
        sideEffectField = 5;
    }

    public static int unsafeSnippet1(double i1, boolean c) {
        A a = new A();
        UNSAFE.putDouble(a, AF1Offset, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getInt(a, AF1Offset) + UNSAFE.getInt(a, AF2Offset);
    }

    public static long unsafeSnippet2a(int i1, boolean c) {
        A a = new A();
        UNSAFE.putDouble(a, AF1Offset, i1);
        a.f1 = i1;
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(a, AF1Offset);
    }

    public static long unsafeSnippet2b(int i1, boolean c) {
        A a = new A();
        UNSAFE.putDouble(a, AF1Offset, i1);
        a.f2 = i1;
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(a, AF1Offset);
    }

    public static long unsafeSnippet3a(int i1, boolean c) {
        A a = new A();
        UNSAFE.putDouble(a, AF1Offset, i1);
        UNSAFE.putInt(a, AF1Offset, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(a, AF1Offset);
    }

    public static long unsafeSnippet3b(int i1, boolean c) {
        A a = new A();
        UNSAFE.putDouble(a, AF1Offset, i1);
        UNSAFE.putInt(a, AF2Offset, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(a, AF1Offset);
    }

    public static int unsafeSnippet4(double i1, boolean c) {
        A a = new A();
        UNSAFE.putDouble(a, AF1Offset, i1);
        UNSAFE.putDouble(a, AF1Offset, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getInt(a, AF1Offset) + UNSAFE.getInt(a, AF2Offset);
    }

    public static int unsafeSnippet5(long i1, boolean c) {
        int[] t = new int[2];
        UNSAFE.putLong(t, (long) Unsafe.ARRAY_INT_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(t, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 6 + Unsafe.ARRAY_INT_BASE_OFFSET);
    }

    public static int unsafeSnippet6(long i1, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putLong(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(b, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 6 + Unsafe.ARRAY_INT_BASE_OFFSET);
    }

    public static int unsafeSnippet7(int i1, boolean c) {
        byte[] b = new byte[4];
        UNSAFE.putInt(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(b, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 0 + Unsafe.ARRAY_INT_BASE_OFFSET);
    }

    public static int unsafeSnippet8(long i1, int i2, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putLong(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        UNSAFE.putInt(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + 4 * Unsafe.ARRAY_BYTE_INDEX_SCALE, i2);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(b, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 2 + Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static int unsafeSnippet9(long i1, short i2, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putLong(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        UNSAFE.putShort(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + 4 * Unsafe.ARRAY_BYTE_INDEX_SCALE, i2);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(b, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 6 + Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static int unsafeSnippet10(double i1, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putDouble(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(b, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 2 + Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static float unsafeSnippet11(double i1, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putDouble(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getFloat(b, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 4 + Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static long unsafeSnippet12(double i1, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putDouble(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static short unsafeSnippet13(short i1, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putShort(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static int unsafeSnippet14(long l, int i, boolean c) {
        int[] t = new int[2];
        if (i < l) {
            UNSAFE.putLong(t, (long) Unsafe.ARRAY_INT_BASE_OFFSET, l);
        } else {
            UNSAFE.putInt(t, (long) Unsafe.ARRAY_INT_BASE_OFFSET, i);
        }
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(t, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 6 + Unsafe.ARRAY_INT_BASE_OFFSET);
    }

    public static int unsafeSnippet15(long i1, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putLong(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(b, getUnsafeByteArrayOffset(0));
    }

    private static long getUnsafeByteArrayOffset(int i) {
        return (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * i + Unsafe.ARRAY_BYTE_BASE_OFFSET;
    }

    public static byte[] unsafeSnippet16(long l, int i, short s, double d, float f, boolean c) {
        byte[] b = new byte[128];
        UNSAFE.putLong(b, getUnsafeByteArrayOffset(8), l);
        UNSAFE.putInt(b, getUnsafeByteArrayOffset(20), i);
        UNSAFE.putShort(b, getUnsafeByteArrayOffset(26), s);
        UNSAFE.putDouble(b, getUnsafeByteArrayOffset(32), d);
        UNSAFE.putFloat(b, getUnsafeByteArrayOffset(44), f);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return b;
    }

    public static long unsafeSnippet17(long i1, boolean c) {
        byte[] t = new byte[8];
        UNSAFE.putLong(t, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(t, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static long unsafeSnippet18(int i1, boolean c) {
        byte[] t = new byte[8];
        UNSAFE.putInt(t, getUnsafeByteArrayOffset(3), i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(t, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    @Test
    public void testUnsafePEA01() {
        performTest("unsafeSnippet1", 1.0);
    }

    @Test
    public void testUnsafePEA02() {
        performTest("unsafeSnippet2a", 1);

        performTest("unsafeSnippet2b", 1);
    }

    @Test
    public void testUnsafePEA03() {
        performTest("unsafeSnippet3a", 1);

        performTest("unsafeSnippet3b", 1);
    }

    @Test
    public void testUnsafePEA04() {
        performTest("unsafeSnippet4", 1.0);
    }

    @Test
    public void testUnsafePEA05() {
        performTest("unsafeSnippet5", 0x0102030405060708L);
    }

    @Test
    public void testUnsafePEA06() {
        performTest("unsafeSnippet6", 0x0102030405060708L);
    }

    @Test
    public void testUnsafePEA07() {
        performTest("unsafeSnippet7", 0x01020304);
    }

    @Test
    public void testUnsafePEA08() {
        performTest("unsafeSnippet8", 0x0102030405060708L, 0x01020304);
    }

    @Test
    public void testUnsafePEA09() {
        performTest("unsafeSnippet9", 0x0102030405060708L, (short) 0x0102);
    }

    @Test
    public void testUnsafePEA10() {
        performTest("unsafeSnippet10", Double.longBitsToDouble(0x0102030405060708L));
    }

    @Test
    public void testUnsafePEA11() {
        performTest("unsafeSnippet11", Double.longBitsToDouble(0x0102030405060708L));
    }

    @Test
    public void testUnsafePEA12() {
        performTest("unsafeSnippet12", Double.longBitsToDouble(0x0102030405060708L));
    }

    @Test
    public void testUnsafePEA13() {
        performTest("unsafeSnippet13", (short) 0x0102);
    }

    @Test
    public void testUnsafePEA14() {
        performTest("unsafeSnippet14", 0x0102030405060708L, 0x01020304);
    }

    @Test
    public void testUnsafePEA15() {
        performTest("unsafeSnippet15", 0x0102030405060708L);
    }

    @Test
    public void testUnsafePEA16() {
        performTest("unsafeSnippet16", 0x0102030405060708L, 0x01020304, (short) 0x0102, Double.longBitsToDouble(0x0102030405060708L), Float.intBitsToFloat(0x01020304));
    }

    @Test
    public void testUnsafePEA17() {
        performTest("unsafeSnippet17", 0x0102030405060708L);
    }

    @Test
    public void testUnsafePEA18() {
        performTest("unsafeSnippet18", 0x01020304);
    }

    private void performTest(String snippet, int arg) {
        for (boolean b1 : FT) {
            for (boolean b2 : FT) {
                testPartialEscapeReadElimination(snippet, b1, arg, b2);
            }
        }
    }

    private void performTest(String snippet, long arg) {
        for (boolean b1 : FT) {
            for (boolean b2 : FT) {
                testPartialEscapeReadElimination(snippet, b1, arg, b2);
            }
        }
    }

    private void performTest(String snippet, double arg) {
        for (boolean b1 : FT) {
            for (boolean b2 : FT) {
                testPartialEscapeReadElimination(snippet, b1, arg, b2);
            }
        }
    }

    private void performTest(String snippet, long arg1, int arg2) {
        for (boolean b1 : FT) {
            for (boolean b2 : FT) {
                testPartialEscapeReadElimination(snippet, b1, arg1, arg2, b2);
            }
        }
    }

    private void performTest(String snippet, long arg1, short arg2) {
        for (boolean b1 : FT) {
            for (boolean b2 : FT) {
                testPartialEscapeReadElimination(snippet, b1, arg1, arg2, b2);
            }
        }
    }

    private void performTest(String snippet, short arg1) {
        for (boolean b1 : FT) {
            for (boolean b2 : FT) {
                testPartialEscapeReadElimination(snippet, b1, arg1, b2);
            }
        }
    }

    private void performTest(String snippet, long l, int i, short s, double d, float f) {
        for (boolean b1 : FT) {
            for (boolean b2 : FT) {
                testPartialEscapeReadElimination(snippet, b1, l, i, s, d, f, b2);
            }
        }
    }

    public void testPartialEscapeReadElimination(String snippet, boolean canonicalizeBefore, Object... args) {
        assert TestClassInt.fieldOffset1 % 8 == 0 : "First of the two int-fields must be 8-byte aligned";

        ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO);
        OptionValues options = graph.getOptions();
        CoreProviders context = getDefaultHighTierContext();
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        if (canonicalizeBefore) {
            canonicalizer.apply(graph, context);
        }
        Result r = executeExpected(method, null, args);
        new PartialEscapePhase(true, true, canonicalizer, null, options).apply(graph, context);
        try {
            InstalledCode code = getCode(method, graph);
            Object result = code.executeVarargs(args);
            assertEquals(r, new Result(result, null));
        } catch (Throwable e) {
            assertFalse(true, e.toString());
        }
    }
}
