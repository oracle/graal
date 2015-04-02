/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test.ea;

import org.junit.*;

import sun.misc.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.virtual.phases.ea.*;

public class PEAReadEliminationTest extends EarlyReadEliminationTest {

    public static int testIndexed1Snippet(int[] array) {
        array[1] = 1;
        array[2] = 2;
        array[3] = 3;
        array[4] = 4;
        array[5] = 5;
        array[6] = 6;
        return array[1] + array[2] + array[3] + array[4] + array[5] + array[6];
    }

    @Test
    public void testIndexed1() {
        processMethod("testIndexed1Snippet");
        assertDeepEquals(0, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testIndexed2Snippet(int v, int[] array) {
        array[1] = 1;
        array[2] = 2;
        array[3] = 3;
        array[v] = 0;
        array[4] = 4;
        array[5] = 5;
        array[6] = 6;
        array[4] = 4;
        array[5] = 5;
        array[6] = 6;
        return array[1] + array[2] + array[3] + array[4] + array[5] + array[6];
    }

    @Test
    public void testIndexed2() {
        processMethod("testIndexed2Snippet");
        assertDeepEquals(3, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(7, graph.getNodes().filter(StoreIndexedNode.class).count());
    }

    public static int testIndexed3Snippet(int v, int[] array, short[] array2) {
        array[1] = 1;
        array2[1] = 1;
        array[2] = 2;
        array2[2] = 2;
        array[3] = 3;
        array2[3] = 3;
        array[v] = 0;
        array[4] = 4;
        array2[4] = 4;
        array[5] = 5;
        array2[5] = 5;
        array[6] = 6;
        array2[6] = 6;
        return array[1] + array[2] + array[3] + array[4] + array[5] + array[6] + array2[1] + array2[2] + array2[3] + array2[4] + array2[5] + array2[6];
    }

    @Test
    public void testIndexed3() {
        processMethod("testIndexed3Snippet");
        assertDeepEquals(3, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    private static native void nonInlineable();

    public static int testIndexed4Snippet(int[] array) {
        array[1] = 1;
        array[2] = 2;
        array[3] = 3;
        nonInlineable();
        array[4] = 4;
        array[5] = 5;
        array[6] = 6;
        return array[1] + array[2] + array[3] + array[4] + array[5] + array[6];
    }

    @Test
    public void testIndexed4() {
        processMethod("testIndexed4Snippet");
        assertDeepEquals(3, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    private static final long offsetInt1 = Unsafe.ARRAY_INT_BASE_OFFSET + Unsafe.ARRAY_INT_INDEX_SCALE * 1;
    private static final long offsetInt2 = Unsafe.ARRAY_INT_BASE_OFFSET + Unsafe.ARRAY_INT_INDEX_SCALE * 2;

    public static int testUnsafe1Snippet(int v, int[] array) {
        int s = UnsafeAccess.unsafe.getInt(array, offsetInt1);
        UnsafeAccess.unsafe.putInt(array, offsetInt1, v);
        UnsafeAccess.unsafe.putInt(array, offsetInt2, v);
        return s + UnsafeAccess.unsafe.getInt(array, offsetInt1) + UnsafeAccess.unsafe.getInt(array, offsetInt2);
    }

    @Test
    public void testUnsafe1() {
        processMethod("testUnsafe1Snippet");
        assertDeepEquals(1, graph.getNodes().filter(UnsafeLoadNode.class).count());
    }

    public static int testUnsafe2Snippet(int v, Object array) {
        int s = UnsafeAccess.unsafe.getInt(array, offsetInt1);
        UnsafeAccess.unsafe.putInt(array, offsetInt1, v);
        UnsafeAccess.unsafe.putInt(array, offsetInt2, v);
        return s + UnsafeAccess.unsafe.getInt(array, offsetInt1) + UnsafeAccess.unsafe.getInt(array, offsetInt2);
    }

    @Test
    public void testUnsafe2() {
        processMethod("testUnsafe2Snippet");
        assertDeepEquals(3, graph.getNodes().filter(UnsafeLoadNode.class).count());
    }

    private static final long offsetObject1 = Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * 1;
    private static final long offsetObject2 = Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * 2;

    public static int testUnsafe3Snippet(int v, Object[] array) {
        int s = (Integer) UnsafeAccess.unsafe.getObject(array, offsetObject1);
        UnsafeAccess.unsafe.putObject(array, offsetObject1, v);
        UnsafeAccess.unsafe.putObject(array, offsetObject2, v);
        return s + (Integer) UnsafeAccess.unsafe.getObject(array, offsetObject1) + (Integer) UnsafeAccess.unsafe.getObject(array, offsetObject2);
    }

    @Test
    public void testUnsafe3() {
        processMethod("testUnsafe3Snippet");
        assertDeepEquals(1, graph.getNodes().filter(UnsafeLoadNode.class).count());
    }

    public static int testUnsafe4Snippet(int v, Object[] array) {
        int s = (Integer) UnsafeAccess.unsafe.getObject(array, offsetObject1);
        UnsafeAccess.unsafe.putObject(array, offsetObject1, v);
        UnsafeAccess.unsafe.putObject(array, offsetObject2, v);
        array[v] = null;
        return s + (Integer) UnsafeAccess.unsafe.getObject(array, offsetObject1) + (Integer) UnsafeAccess.unsafe.getObject(array, offsetObject2);
    }

    @Test
    public void testUnsafe4() {
        processMethod("testUnsafe4Snippet");
        assertDeepEquals(3, graph.getNodes().filter(UnsafeLoadNode.class).count());
    }

    private static final long offsetLong1 = Unsafe.ARRAY_LONG_BASE_OFFSET + Unsafe.ARRAY_LONG_INDEX_SCALE * 1;
    private static final long offsetLong2 = Unsafe.ARRAY_LONG_BASE_OFFSET + Unsafe.ARRAY_LONG_INDEX_SCALE * 2;

    public static int testUnsafe5Snippet(int v, long[] array) {
        int s = UnsafeAccess.unsafe.getInt(array, offsetLong1);
        UnsafeAccess.unsafe.putInt(array, offsetLong1, v);
        UnsafeAccess.unsafe.putInt(array, offsetLong2, v);
        return s + UnsafeAccess.unsafe.getInt(array, offsetLong1) + UnsafeAccess.unsafe.getInt(array, offsetLong2);
    }

    @Test
    public void testUnsafe5() {
        processMethod("testUnsafe5Snippet");
        assertDeepEquals(1, graph.getNodes().filter(UnsafeLoadNode.class).count());
    }

    @Override
    protected void processMethod(final String snippet) {
        graph = parseEager(snippet, AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        new InliningPhase(new CanonicalizerPhase()).apply(graph, context);
        new PartialEscapePhase(false, true, new CanonicalizerPhase(), null).apply(graph, context);
    }
}
