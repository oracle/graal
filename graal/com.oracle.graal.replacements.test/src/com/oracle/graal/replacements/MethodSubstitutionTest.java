/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import static org.junit.Assert.*;

import java.util.concurrent.*;

import org.junit.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.replacements.nodes.*;

/**
 * Tests if {@link MethodSubstitution}s are inlined correctly. Most test cases only assert that
 * there are no remaining invocations in the graph. This is sufficient if the method that is being
 * substituted is a native method. For Java methods, additional checks are necessary.
 */
public class MethodSubstitutionTest extends GraalCompilerTest {

    @Test
    public void testUnsafeSubstitutions() {
        test("unsafeCompareAndSwapInt");
        test("unsafeCompareAndSwapLong");
        test("unsafeCompareAndSwapObject");

        test("unsafeGetBoolean");
        test("unsafeGetByte");
        test("unsafeGetShort");
        test("unsafeGetChar");
        test("unsafeGetInt");
        test("unsafeGetFloat");
        test("unsafeGetDouble");
        test("unsafeGetObject");

        test("unsafePutBoolean");
        test("unsafePutByte");
        test("unsafePutShort");
        test("unsafePutChar");
        test("unsafePutInt");
        test("unsafePutFloat");
        test("unsafePutDouble");
        test("unsafePutObject");

        test("unsafeDirectMemoryRead");
        test("unsafeDirectMemoryWrite");
    }

    @SuppressWarnings("all")
    public static boolean unsafeCompareAndSwapInt(Unsafe unsafe, Object obj, long offset) {
        return unsafe.compareAndSwapInt(obj, offset, 0, 1);
    }

    @SuppressWarnings("all")
    public static boolean unsafeCompareAndSwapLong(Unsafe unsafe, Object obj, long offset) {
        return unsafe.compareAndSwapLong(obj, offset, 0, 1);
    }

    @SuppressWarnings("all")
    public static boolean unsafeCompareAndSwapObject(Unsafe unsafe, Object obj, long offset) {
        return unsafe.compareAndSwapObject(obj, offset, null, new Object());
    }

    @SuppressWarnings("all")
    public static boolean unsafeGetBoolean(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getBoolean(obj, offset) && unsafe.getBooleanVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static int unsafeGetByte(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getByte(obj, offset) + unsafe.getByteVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static int unsafeGetShort(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getShort(obj, offset) + unsafe.getShortVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static int unsafeGetChar(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getChar(obj, offset) + unsafe.getCharVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static int unsafeGetInt(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getInt(obj, offset) + unsafe.getIntVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static long unsafeGetLong(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getLong(obj, offset) + unsafe.getLongVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static float unsafeGetFloat(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getFloat(obj, offset) + unsafe.getFloatVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static double unsafeGetDouble(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getDouble(obj, offset) + unsafe.getDoubleVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static boolean unsafeGetObject(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getObject(obj, offset) == unsafe.getObjectVolatile(obj, offset);
    }

    @SuppressWarnings("all")
    public static void unsafePutBoolean(Unsafe unsafe, Object obj, long offset, boolean value) {
        unsafe.putBoolean(obj, offset, value);
        unsafe.putBooleanVolatile(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutByte(Unsafe unsafe, Object obj, long offset, byte value) {
        unsafe.putByte(obj, offset, value);
        unsafe.putByteVolatile(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutShort(Unsafe unsafe, Object obj, long offset, short value) {
        unsafe.putShort(obj, offset, value);
        unsafe.putShortVolatile(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutChar(Unsafe unsafe, Object obj, long offset, char value) {
        unsafe.putChar(obj, offset, value);
        unsafe.putCharVolatile(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutInt(Unsafe unsafe, Object obj, long offset, int value) {
        unsafe.putInt(obj, offset, value);
        unsafe.putIntVolatile(obj, offset, value);
        unsafe.putOrderedInt(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutLong(Unsafe unsafe, Object obj, long offset, long value) {
        unsafe.putLong(obj, offset, value);
        unsafe.putLongVolatile(obj, offset, value);
        unsafe.putOrderedLong(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutFloat(Unsafe unsafe, Object obj, long offset, float value) {
        unsafe.putFloat(obj, offset, value);
        unsafe.putFloatVolatile(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutDouble(Unsafe unsafe, Object obj, long offset, double value) {
        unsafe.putDouble(obj, offset, value);
        unsafe.putDoubleVolatile(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static void unsafePutObject(Unsafe unsafe, Object obj, long offset, Object value) {
        unsafe.putObject(obj, offset, value);
        unsafe.putObjectVolatile(obj, offset, value);
        unsafe.putOrderedObject(obj, offset, value);
    }

    @SuppressWarnings("all")
    public static double unsafeDirectMemoryRead(Unsafe unsafe, long address) {
        // Unsafe.getBoolean(long) and Unsafe.getObject(long) do not exist
        return unsafe.getByte(address) + unsafe.getShort(address) + unsafe.getChar(address) + unsafe.getInt(address) + unsafe.getLong(address) + unsafe.getFloat(address) + unsafe.getDouble(address);
    }

    @SuppressWarnings("all")
    public static void unsafeDirectMemoryWrite(Unsafe unsafe, long address, byte value) {
        // Unsafe.putBoolean(long) and Unsafe.putObject(long) do not exist
        unsafe.putByte(address, value);
        unsafe.putShort(address, value);
        unsafe.putChar(address, (char) value);
        unsafe.putInt(address, value);
        unsafe.putLong(address, value);
        unsafe.putFloat(address, value);
        unsafe.putDouble(address, value);
    }

    @Test
    public void testMathSubstitutions() {
        assertInGraph(assertNotInGraph(test("mathAbs"), IfNode.class), MathIntrinsicNode.class);     // Java
        test("math");
    }

    @SuppressWarnings("all")
    public static double mathAbs(double value) {
        return Math.abs(value);
    }

    @SuppressWarnings("all")
    public static double math(double value) {
        return Math.sqrt(value) + Math.log(value) + Math.log10(value) + Math.sin(value) + Math.cos(value) + Math.tan(value);
        // Math.exp(value) +
        // Math.pow(value, 13);
    }

    @Test
    public void testIntegerSubstitutions() {
        assertInGraph(test("integerReverseBytes"), ReverseBytesNode.class);              // Java
        assertInGraph(test("integerNumberOfLeadingZeros"), BitScanReverseNode.class);    // Java
        assertInGraph(test("integerNumberOfTrailingZeros"), BitScanForwardNode.class);   // Java
        assertInGraph(test("integerBitCount"), BitCountNode.class);                      // Java
    }

    @SuppressWarnings("all")
    public static int integerReverseBytes(int value) {
        return Integer.reverseBytes(value);
    }

    @SuppressWarnings("all")
    public static int integerNumberOfLeadingZeros(int value) {
        return Integer.numberOfLeadingZeros(value);
    }

    @SuppressWarnings("all")
    public static int integerNumberOfTrailingZeros(int value) {
        return Integer.numberOfTrailingZeros(value);
    }

    @SuppressWarnings("all")
    public static int integerBitCount(int value) {
        return Integer.bitCount(value);
    }

    @Test
    public void testLongSubstitutions() {
        assertInGraph(test("longReverseBytes"), ReverseBytesNode.class);              // Java
        assertInGraph(test("longNumberOfLeadingZeros"), BitScanReverseNode.class);    // Java
        assertInGraph(test("longNumberOfTrailingZeros"), BitScanForwardNode.class);   // Java
        assertInGraph(test("longBitCount"), BitCountNode.class);                      // Java
    }

    @SuppressWarnings("all")
    public static long longReverseBytes(long value) {
        return Long.reverseBytes(value);
    }

    @SuppressWarnings("all")
    public static long longNumberOfLeadingZeros(long value) {
        return Long.numberOfLeadingZeros(value);
    }

    @SuppressWarnings("all")
    public static long longNumberOfTrailingZeros(long value) {
        return Long.numberOfTrailingZeros(value);
    }

    @SuppressWarnings("all")
    public static int longBitCount(long value) {
        return Long.bitCount(value);
    }

    @Test
    public void testFloatSubstitutions() {
        assertInGraph(test("floatToIntBits"), ConvertNode.class); // Java
        test("intBitsToFloat");
    }

    @SuppressWarnings("all")
    public static int floatToIntBits(float value) {
        return Float.floatToIntBits(value);
    }

    @SuppressWarnings("all")
    public static float intBitsToFloat(int value) {
        return Float.intBitsToFloat(value);
    }

    @Test
    public void testDoubleSubstitutions() {
        assertInGraph(test("doubleToLongBits"), ConvertNode.class); // Java
        test("longBitsToDouble");
    }

    @SuppressWarnings("all")
    public static long doubleToLongBits(double value) {
        return Double.doubleToLongBits(value);
    }

    @SuppressWarnings("all")
    public static double longBitsToDouble(long value) {
        return Double.longBitsToDouble(value);
    }

    protected StructuredGraph test(final String snippet) {
        return Debug.scope("MethodSubstitutionTest", runtime.lookupJavaMethod(getMethod(snippet)), new Callable<StructuredGraph>() {

            @Override
            public StructuredGraph call() {
                StructuredGraph graph = parse(snippet);
                PhasePlan phasePlan = getDefaultPhasePlan();
                Assumptions assumptions = new Assumptions(true);
                new ComputeProbabilityPhase().apply(graph);
                Debug.dump(graph, "Graph");
                new InliningPhase(runtime(), null, assumptions, null, phasePlan, OptimisticOptimizations.ALL).apply(graph);
                Debug.dump(graph, "Graph");
                new CanonicalizerPhase(runtime(), assumptions).apply(graph);
                new DeadCodeEliminationPhase().apply(graph);

                assertNotInGraph(graph, Invoke.class);
                return graph;
            }
        });
    }

    protected static StructuredGraph assertNotInGraph(StructuredGraph graph, Class<?> clazz) {
        for (Node node : graph.getNodes()) {
            if (clazz.isInstance(node)) {
                fail(node.toString());
            }
        }
        return graph;
    }

    protected static StructuredGraph assertInGraph(StructuredGraph graph, Class<?> clazz) {
        for (Node node : graph.getNodes()) {
            if (clazz.isInstance(node)) {
                return graph;
            }
        }
        fail("Graph does not contain a node of class " + clazz.getName());
        return graph;
    }
}
