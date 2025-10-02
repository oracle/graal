/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.core.test;

import org.junit.Test;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class MinMaxCanonicalizationTest extends GraalCompilerTest {

    record ByteHolder(byte b) {

    }

    public static boolean byteMinSnippet(ByteHolder holder) {
        byte b = holder.b;
        /* This tests a case in IntegerEqualsOp#canonical where we need constants in the graph. */
        return (b < 8 ? b : 8) == 7;
    }

    public static boolean byteMinReference(ByteHolder holder) {
        return holder.b == 7;
    }

    @Test
    public void testByteMin() {
        testByteHolderMethod("byteMinSnippet", "byteMinReference");
    }

    private void testByteHolderMethod(String testSnippetName, String referenceSnippetName) {
        StructuredGraph graph = parseEager(testSnippetName, StructuredGraph.AllowAssumptions.YES);
        createInliningPhase().apply(graph, getDefaultHighTierContext());
        createCanonicalizerPhase().apply(graph, getProviders());

        StructuredGraph referenceGraph = parseEager(referenceSnippetName, StructuredGraph.AllowAssumptions.YES);
        assertEquals(referenceGraph, graph);

        ResolvedJavaMethod referenceMethod = referenceGraph.method();
        for (byte b = 7; b <= 9; b++) {
            testAgainstExpected(graph.method(), executeExpected(referenceMethod, null, new ByteHolder(b)), null, new ByteHolder(b));
        }
    }

    public static boolean byteMaxEqualsToLessThanSnippet(ByteHolder holder) {
        return Math.max(7, holder.b) == 7;
    }

    public static boolean byteMaxEqualsToLessThanReference(ByteHolder holder) {
        return holder.b <= 7;
    }

    @Test
    public void testByteMaxEqualsToLessThan() {
        testByteHolderMethod("byteMaxEqualsToLessThanSnippet", "byteMaxEqualsToLessThanReference");
    }

    private static volatile byte symbolic = 7;

    public static boolean byteMaxEqualsToLessThanSymbolicSnippet(ByteHolder holder) {
        byte other = symbolic;
        /*
         * Many of these tests test a case where we don't need constants in the graph. The tests
         * look cleaner with constants, but here is a demonstration that fully symbolic reasoning
         * works too.
         */
        return Math.max(other, holder.b) == other;
    }

    public static boolean byteMaxEqualsToLessThanSymbolicReference(ByteHolder holder) {
        byte other = symbolic;
        return holder.b <= other;
    }

    @Test
    public void testByteMaxEqualsToLessThanSymbolic() {
        testByteHolderMethod("byteMaxEqualsToLessThanSymbolicSnippet", "byteMaxEqualsToLessThanSymbolicReference");
    }

    public static boolean byteUMaxEqualsToLessThanSnippet(ByteHolder holder) {
        return NumUtil.maxUnsigned(holder.b & 0xff, 7) == 7;
    }

    public static boolean byteUMaxEqualsToLessThanReference(ByteHolder holder) {
        return Byte.compareUnsigned(holder.b, (byte) 7) <= 0;
    }

    @Test
    public void testByteUMaxEqualsToLessThan() {
        testByteHolderMethod("byteUMaxEqualsToLessThanSnippet", "byteUMaxEqualsToLessThanReference");
    }

    public static boolean byteMinEqualsToLessThanSnippet(ByteHolder holder) {
        return Math.min(7, holder.b) == 7;
    }

    public static boolean byteMinEqualsToLessThanReference(ByteHolder holder) {
        return holder.b >= 7;
    }

    @Test
    public void testByteMinEqualsToLessThan() {
        testByteHolderMethod("byteMinEqualsToLessThanSnippet", "byteMinEqualsToLessThanReference");
    }

    public static boolean byteUMinEqualsToLessThanSnippet(ByteHolder holder) {
        return NumUtil.minUnsigned(holder.b & 0xff, 7) == 7;
    }

    public static boolean byteUMinEqualsToLessThanReference(ByteHolder holder) {
        return Byte.compareUnsigned(holder.b, (byte) 7) >= 0;
    }

    @Test
    public void testByteUMinEqualsToLessThan() {
        testByteHolderMethod("byteUMinEqualsToLessThanSnippet", "byteUMinEqualsToLessThanReference");
    }
}
