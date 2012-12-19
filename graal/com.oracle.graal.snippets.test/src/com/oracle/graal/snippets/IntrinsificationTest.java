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
package com.oracle.graal.snippets;

import static org.junit.Assert.*;

import java.util.concurrent.*;

import org.junit.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.snippets.nodes.*;

/**
 * Tests if compiler intrinsics are inlined correctly. Most test cases only assert that there are no remaining
 * invocations in the graph. This is sufficient if the method that is being intrinsified is a native method.
 * For Java methods, additional checks are necessary.
 */
public class IntrinsificationTest extends GraalCompilerTest {
    @Test
    public void testObjectIntrinsics() {
        test("getClassSnippet");
        test("objectHashCodeSnippet");
    }

    @SuppressWarnings("all")
    public static boolean getClassSnippet(Object obj, Class<?> clazz) {
        return obj.getClass() == clazz;
    }
    @SuppressWarnings("all")
    public static int objectHashCodeSnippet(TestClassA obj) {
        return obj.hashCode();
    }


    @Test
    public void testClassIntrinsics() {
        test("getModifiersSnippet");
        test("isInstanceSnippet");
        test("isInterfaceSnippet");
        test("isArraySnippet");
        test("isPrimitiveSnippet");
        test("getSuperClassSnippet");
        test("getComponentTypeSnippet");
    }

    @SuppressWarnings("all")
    public static int getModifiersSnippet(Class<?> clazz) {
        return clazz.getModifiers();
    }
    @SuppressWarnings("all")
    public static boolean isInstanceSnippet(Class<?> clazz) {
        return clazz.isInstance(Number.class);
    }
    @SuppressWarnings("all")
    public static boolean isInterfaceSnippet(Class<?> clazz) {
        return clazz.isInterface();
    }
    @SuppressWarnings("all")
    public static boolean isArraySnippet(Class<?> clazz) {
        return clazz.isArray();
    }
    @SuppressWarnings("all")
    public static boolean isPrimitiveSnippet(Class<?> clazz) {
        return clazz.isPrimitive();
    }
    @SuppressWarnings("all")
    public static Class<?> getSuperClassSnippet(Class<?> clazz) {
        return clazz.getSuperclass();
    }
    @SuppressWarnings("all")
    public static Class<?> getComponentTypeSnippet(Class<?> clazz) {
        return clazz.getComponentType();
    }


    @Test
    public void testThreadIntrinsics() {
        test("currentThreadSnippet");
        test("threadIsInterruptedSnippet");
        test("threadInterruptedSnippet");
    }

    @SuppressWarnings("all")
    public static Thread currentThreadSnippet() {
        return Thread.currentThread();
    }
    @SuppressWarnings("all")
    public static boolean threadIsInterruptedSnippet(Thread thread) {
        return thread.isInterrupted();
    }
    @SuppressWarnings("all")
    public static boolean threadInterruptedSnippet() {
        return Thread.interrupted();
    }


    @Test
    public void testSystemIntrinsics() {
        test("systemTimeSnippet");
        test("systemIdentityHashCode");
    }

    @SuppressWarnings("all")
    public static long systemTimeSnippet() {
        return System.currentTimeMillis() +
               System.nanoTime();
    }
    @SuppressWarnings("all")
    public static int systemIdentityHashCode(Object obj) {
        return System.identityHashCode(obj);
    }


    @Test
    public void testUnsafeIntrinsics() {
        test("unsafeCompareAndSwapIntSnippet");
        test("unsafeCompareAndSwapLongSnippet");
        test("unsafeCompareAndSwapObjectSnippet");

        test("unsafeGetBooleanSnippet");
        test("unsafeGetByteSnippet");
        test("unsafeGetShortSnippet");
        test("unsafeGetCharSnippet");
        test("unsafeGetIntSnippet");
        test("unsafeGetFloatSnippet");
        test("unsafeGetDoubleSnippet");
        test("unsafeGetObjectSnippet");

        test("unsafePutBooleanSnippet");
        test("unsafePutByteSnippet");
        test("unsafePutShortSnippet");
        test("unsafePutCharSnippet");
        test("unsafePutIntSnippet");
        test("unsafePutFloatSnippet");
        test("unsafePutDoubleSnippet");
        test("unsafePutObjectSnippet");

        test("unsafeDirectMemoryReadSnippet");
        test("unsafeDirectMemoryWriteSnippet");
    }

    @SuppressWarnings("all")
    public static boolean unsafeCompareAndSwapIntSnippet(Unsafe unsafe, Object obj, long offset) {
        return unsafe.compareAndSwapInt(obj, offset, 0, 1);
    }
    @SuppressWarnings("all")
    public static boolean unsafeCompareAndSwapLongSnippet(Unsafe unsafe, Object obj, long offset) {
        return unsafe.compareAndSwapLong(obj, offset, 0, 1);
    }
    @SuppressWarnings("all")
    public static boolean unsafeCompareAndSwapObjectSnippet(Unsafe unsafe, Object obj, long offset) {
        return unsafe.compareAndSwapObject(obj, offset, null, new Object());
    }
    @SuppressWarnings("all")
    public static boolean unsafeGetBooleanSnippet(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getBoolean(obj, offset) &&
               unsafe.getBooleanVolatile(obj, offset);
    }
    @SuppressWarnings("all")
    public static int unsafeGetByteSnippet(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getByte(obj, offset) +
               unsafe.getByteVolatile(obj, offset);
    }
    @SuppressWarnings("all")
    public static int unsafeGetShortSnippet(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getShort(obj, offset) +
               unsafe.getShortVolatile(obj, offset);
    }
    @SuppressWarnings("all")
    public static int unsafeGetCharSnippet(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getChar(obj, offset) +
               unsafe.getCharVolatile(obj, offset);
    }
    @SuppressWarnings("all")
    public static int unsafeGetIntSnippet(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getInt(obj, offset) +
               unsafe.getIntVolatile(obj, offset);
    }
    @SuppressWarnings("all")
    public static long unsafeGetLongSnippet(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getLong(obj, offset) +
               unsafe.getLongVolatile(obj, offset);
    }
    @SuppressWarnings("all")
    public static float unsafeGetFloatSnippet(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getFloat(obj, offset) +
               unsafe.getFloatVolatile(obj, offset);
    }
    @SuppressWarnings("all")
    public static double unsafeGetDoubleSnippet(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getDouble(obj, offset) +
               unsafe.getDoubleVolatile(obj, offset);
    }
    @SuppressWarnings("all")
    public static boolean unsafeGetObjectSnippet(Unsafe unsafe, Object obj, long offset) {
        return unsafe.getObject(obj, offset) == unsafe.getObjectVolatile(obj, offset);
    }
    @SuppressWarnings("all")
    public static void unsafePutBooleanSnippet(Unsafe unsafe, Object obj, long offset, boolean value) {
        unsafe.putBoolean(obj, offset, value);
        unsafe.putBooleanVolatile(obj, offset, value);
    }
    @SuppressWarnings("all")
    public static void unsafePutByteSnippet(Unsafe unsafe, Object obj, long offset, byte value) {
        unsafe.putByte(obj, offset, value);
        unsafe.putByteVolatile(obj, offset, value);
    }
    @SuppressWarnings("all")
    public static void unsafePutShortSnippet(Unsafe unsafe, Object obj, long offset, short value) {
        unsafe.putShort(obj, offset, value);
        unsafe.putShortVolatile(obj, offset, value);
    }
    @SuppressWarnings("all")
    public static void unsafePutCharSnippet(Unsafe unsafe, Object obj, long offset, char value) {
        unsafe.putChar(obj, offset, value);
        unsafe.putCharVolatile(obj, offset, value);
    }
    @SuppressWarnings("all")
    public static void unsafePutIntSnippet(Unsafe unsafe, Object obj, long offset, int value) {
        unsafe.putInt(obj, offset, value);
        unsafe.putIntVolatile(obj, offset, value);
        unsafe.putOrderedInt(obj, offset, value);
    }
    @SuppressWarnings("all")
    public static void unsafePutLongSnippet(Unsafe unsafe, Object obj, long offset, long value) {
        unsafe.putLong(obj, offset, value);
        unsafe.putLongVolatile(obj, offset, value);
        unsafe.putOrderedLong(obj, offset, value);
    }
    @SuppressWarnings("all")
    public static void unsafePutFloatSnippet(Unsafe unsafe, Object obj, long offset, float value) {
        unsafe.putFloat(obj, offset, value);
        unsafe.putFloatVolatile(obj, offset, value);
    }
    @SuppressWarnings("all")
    public static void unsafePutDoubleSnippet(Unsafe unsafe, Object obj, long offset, double value) {
        unsafe.putDouble(obj, offset, value);
        unsafe.putDoubleVolatile(obj, offset, value);
    }
    @SuppressWarnings("all")
    public static void unsafePutObjectSnippet(Unsafe unsafe, Object obj, long offset, Object value) {
        unsafe.putObject(obj, offset, value);
        unsafe.putObjectVolatile(obj, offset, value);
        unsafe.putOrderedObject(obj, offset, value);
    }
    @SuppressWarnings("all")
    public static double unsafeDirectMemoryReadSnippet(Unsafe unsafe, long address) {
        // Unsafe.getBoolean(long) and Unsafe.getObject(long) do not exist
        return unsafe.getByte(address) +
               unsafe.getShort(address) +
               unsafe.getChar(address) +
               unsafe.getInt(address) +
               unsafe.getLong(address) +
               unsafe.getFloat(address) +
               unsafe.getDouble(address);
    }
    @SuppressWarnings("all")
    public static void unsafeDirectMemoryWriteSnippet(Unsafe unsafe, long address, byte value) {
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
    public void testMathIntrinsics() {
        assertInGraph(assertNotInGraph(test("mathAbsSnippet"), IfNode.class), MathIntrinsicNode.class);     // Java
        test("mathSnippet");
    }

    @SuppressWarnings("all")
    public static double mathAbsSnippet(double value) {
        return Math.abs(value);
    }
    @SuppressWarnings("all")
    public static double mathSnippet(double value) {
        return Math.sqrt(value) +
               Math.log(value) +
               Math.log10(value) +
               Math.sin(value) +
               Math.cos(value) +
               Math.tan(value);
//               Math.exp(value) +
//               Math.pow(value, 13);
    }


    @Test
    public void testIntegerIntrinsics() {
        assertInGraph(test("integerReverseBytesSnippet"), ReverseBytesNode.class);              // Java
        assertInGraph(test("integerNumberOfLeadingZerosSnippet"), BitScanReverseNode.class);    // Java
        assertInGraph(test("integerNumberOfTrailingZerosSnippet"), BitScanForwardNode.class);   // Java
        assertInGraph(test("integerBitCountSnippet"), BitCountNode.class);                      // Java
    }

    @SuppressWarnings("all")
    public static int integerReverseBytesSnippet(int value) {
        return Integer.reverseBytes(value);
    }
    @SuppressWarnings("all")
    public static int integerNumberOfLeadingZerosSnippet(int value) {
        return Integer.numberOfLeadingZeros(value);
    }
    @SuppressWarnings("all")
    public static int integerNumberOfTrailingZerosSnippet(int value) {
        return Integer.numberOfTrailingZeros(value);
    }
    @SuppressWarnings("all")
    public static int integerBitCountSnippet(int value) {
        return Integer.bitCount(value);
    }


    @Test
    public void testLongIntrinsics() {
        assertInGraph(test("longReverseBytesSnippet"), ReverseBytesNode.class);              // Java
        assertInGraph(test("longNumberOfLeadingZerosSnippet"), BitScanReverseNode.class);    // Java
        assertInGraph(test("longNumberOfTrailingZerosSnippet"), BitScanForwardNode.class);   // Java
        assertInGraph(test("longBitCountSnippet"), BitCountNode.class);                      // Java
    }

    @SuppressWarnings("all")
    public static long longReverseBytesSnippet(long value) {
        return Long.reverseBytes(value);
    }
    @SuppressWarnings("all")
    public static long longNumberOfLeadingZerosSnippet(long value) {
        return Long.numberOfLeadingZeros(value);
    }
    @SuppressWarnings("all")
    public static long longNumberOfTrailingZerosSnippet(long value) {
        return Long.numberOfTrailingZeros(value);
    }
    @SuppressWarnings("all")
    public static int longBitCountSnippet(long value) {
        return Long.bitCount(value);
    }


    @Test
    public void testFloatIntrinsics() {
        assertInGraph(test("floatToIntBitsSnippet"), ConvertNode.class); // Java
        test("intBitsToFloatSnippet");
    }

    @SuppressWarnings("all")
    public static int floatToIntBitsSnippet(float value) {
        return Float.floatToIntBits(value);
    }
    @SuppressWarnings("all")
    public static float intBitsToFloatSnippet(int value) {
        return Float.intBitsToFloat(value);
    }


    @Test
    public void testDoubleIntrinsics() {
        assertInGraph(test("doubleToLongBitsSnippet"), ConvertNode.class); // Java
        test("longBitsToDoubleSnippet");
    }

    @SuppressWarnings("all")
    public static long doubleToLongBitsSnippet(double value) {
        return Double.doubleToLongBits(value);
    }
    @SuppressWarnings("all")
    public static double longBitsToDoubleSnippet(long value) {
        return Double.longBitsToDouble(value);
    }


    private StructuredGraph test(final String snippet) {
        return Debug.scope("IntrinsificationTest", runtime.lookupJavaMethod(getMethod(snippet)), new Callable<StructuredGraph>() {
            @Override
            public StructuredGraph call() {
                StructuredGraph graph = parse(snippet);
                PhasePlan phasePlan = getDefaultPhasePlan();
                Assumptions assumptions = new Assumptions(true);
                new ComputeProbabilityPhase().apply(graph);
                Debug.dump(graph, "Graph");
                new InliningPhase(null, runtime(), null, assumptions, null, phasePlan, OptimisticOptimizations.ALL).apply(graph);
                Debug.dump(graph, "Graph");
                new CanonicalizerPhase(null, runtime(), assumptions).apply(graph);
                new DeadCodeEliminationPhase().apply(graph);

                assertNotInGraph(graph, Invoke.class);
                return graph;
            }
        });
    }

    private static StructuredGraph assertNotInGraph(StructuredGraph graph, Class<?> clazz) {
        for (Node node: graph.getNodes()) {
            if (clazz.isInstance(node)) {
                fail(node.toString());
            }
        }
        return graph;
    }

    private static StructuredGraph assertInGraph(StructuredGraph graph, Class<?> clazz) {
        for (Node node: graph.getNodes()) {
            if (clazz.isInstance(node)) {
                return graph;
            }
        }
        fail("Graph does not contain a node of class " + clazz.getName());
        return graph;
    }

    private static class TestClassA {
    }
}
