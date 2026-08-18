/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.ea;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.ea.ReadEliminationTest.TestObject;
import jdk.graal.compiler.hotspot.replacements.ObjectCloneNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.inlining.InliningPhase;
import jdk.graal.compiler.phases.common.inlining.policy.InlineEverythingPolicy;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.virtual.phases.ea.ObjectCloneRemovalPhase;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ReadEliminationArrayTest extends GraalCompilerTest {

    public static Object staticField;

    ReturnNode getReturn(String snippet, boolean doLowering) {
        StructuredGraph graph = processMethod(snippet, doLowering);
        assertDeepEquals(1, graph.getNodes(ReturnNode.TYPE).count());
        return graph.getNodes(ReturnNode.TYPE).first();
    }

    // Non constant indexed elimination test cases
    public static int testLoadIndexedSnippet(int[] arr) {
        int sum = 0;
        for (int i = 0; i < arr.length; i++) {
            sum += arr[i];
            sum += arr[i];
            sum += arr[i];
        }
        return sum;
    }

    @Test
    public void testLoadIndexed() {
        StructuredGraph graph = processMethod("testLoadIndexedSnippet", false);
        assertDeepEquals(1, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testStoreLoadIndexedSnippet(int[] arr) {
        int sum = 0;
        for (int i = 0; i < arr.length; i++) {
            arr[i] = i;
            // no need to load, should be i
            sum += arr[i];
        }
        return sum;
    }

    @Test
    public void testStoreLoadIndexed() {
        StructuredGraph graph = processMethod("testStoreLoadIndexedSnippet", false);
        assertDeepEquals(1, graph.getNodes().filter(StoreIndexedNode.class).count());
    }

    public static int testLoadStoreSnippet(int[] arr) {
        int sum = 0;
        for (int i = 0; i < arr.length; i++) {
            sum += arr[i];
            arr[i] = i;
        }
        return sum;
    }

    @Test
    public void testLoadStoreIndexed() {
        StructuredGraph graph = processMethod("testLoadStoreSnippet", false);
        assertDeepEquals(1, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testLoadStoreLoadIndexedSnippet(int[] arr) {
        int sum = 0;
        for (int i = 0; i < arr.length; i++) {
            // load needs to be done
            sum += arr[i];
            // store needs to be done
            arr[i] = i;
            // load can be removed
            sum += arr[i];
        }
        return sum;
    }

    @Test
    public void testLoadStoreLoadIndexed() {
        StructuredGraph graph = processMethod("testLoadStoreLoadIndexedSnippet", false);
        assertDeepEquals(1, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testPotentiallyAliasingParameterArrays1(int[] a1, int[] a2) {
        /*
         * the arrays potentially alias with each other, thus a store to a1 must also kill a2 and
         * wise versa
         */
        int sum = 0;
        for (int i = 0; i < a1.length; i++) {
            // load needs to be done
            sum += a1[i];
            // load needs to be done
            sum += a2[i];
            // store needs to be done (must also kill the access to a2, therefore, second load of a2
            // needs to be done)
            a1[i] /* must kill a2[i] */ = i;
            // load of a1[i] can be removed
            sum += a1[i];
            // load of a2[i] must be done as the array of the store to a1 above may alias with a2
            sum += a2[i];
        }
        return sum;
    }

    @Test
    public void testParameterBasedAliasing1() {
        StructuredGraph graph = processMethod("testPotentiallyAliasingParameterArrays1", false);
        assertDeepEquals(1, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(3, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testPotentiallyAliasingParameterArrays2(int[] a1, int[] a2) {
        /*
         * the arrays potentially alias with each other, thus a store to a1 must also kill a2 and
         * wise versa
         */
        int sum = 0;
        for (int i = 0; i < a1.length; i++) {
            // load needs to be done
            sum += a1[i];
            // load needs to be done
            sum += a2[i];
            // store needs to be done (must also kill the access to a1, therefore, second load of a1
            // needs to be done)
            a2[i] /* must kill a1[i] */ = i;
            // load of a1[i] must be performed as a1 and a2 potentially alias
            sum += a1[i];
            // load of a2[i] can be removed
            sum += a2[i];
        }
        return sum;
    }

    @Test
    public void testParameterBasedAliasing2() {
        StructuredGraph graph = processMethod("testPotentiallyAliasingParameterArrays2", false);
        assertDeepEquals(1, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(3, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testPotentiallyAliasingIndices(int[] a1, int i1, int i2) {
        int sum = 0;
        // read must be done
        sum += a1[i1];
        // write must be done
        a1[i2] = sum;
        // we know nothing about i1 and i2 thus we need to perform this read
        sum += a1[i1];
        return sum;
    }

    @Test
    public void testAliasingIndices() {
        StructuredGraph graph = processMethod("testPotentiallyAliasingIndices", false);
        assertDeepEquals(1, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(2, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testConstantIndicesSnippet(int[] a1) {
        int sum = 0;
        // read must be done
        sum += a1[0];
        // write must be done
        a1[1] = sum;
        // we can remove this load
        sum += a1[0];
        return sum;
    }

    @Test
    public void testConstantIndices() {
        StructuredGraph graph = processMethod("testConstantIndicesSnippet", false);
        assertDeepEquals(1, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testArrayAndFieldWriteSnippet(int[] a1, TestObject t) {
        // read must be done
        t.x = a1[0];
        // write must be done
        a1[1] = 0;
        // we can remove this load, but we do the write
        t.y = a1[0];
        // can both be eliminated
        return t.x + t.y;
    }

    @Test
    public void testArrayAndFieldWrite() {
        StructuredGraph graph = processMethod("testArrayAndFieldWriteSnippet", false);
        assertDeepEquals(1, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(2, graph.getNodes().filter(StoreFieldNode.class).count());
    }

    public static int testArrayAndUnsafeKownSnippet(int[] a1, int index, TestObject t) {
        // read must be done
        t.x = a1[0];
        // write must be done
        a1[1] = 0;
        // this unsafe write does NOT kill the cache for the int array as the location is OFF_HEAP
        UNSAFE.putLong(index, 12);
        // we can remove this load, but we do the write
        t.y = a1[0];
        // can both be eliminated
        return t.x + t.y;
    }

    @Test
    public void testArrayAndUnsafeKnown() {
        StructuredGraph graph = processMethod("testArrayAndUnsafeKownSnippet", false);
        assertDeepEquals(1, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(2, graph.getNodes().filter(StoreFieldNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(LoadFieldNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(RawStoreNode.class).count());
    }

    public static int testArrayAndUnsafeUnkownSnippet(int[] a1, double[] d1, long index, TestObject t) {
        // read must be done
        t.x = a1[0];
        // write must be done
        a1[1] = 0;
        // this unsafe write kills everything because of the location. we could be clever and track
        // that d1 is a double array and index is different than location of the mark word, and
        // length field but that probably does not pay off.
        UNSAFE.putDouble(d1, index, 12D);
        // we need to -redo this load
        t.y = a1[0];
        // we need to re-load x
        return t.x + t.y;
    }

    @Test
    public void testArrayAndUnsafeUnknown() {
        StructuredGraph graph = processMethod("testArrayAndUnsafeUnkownSnippet", false);
        assertDeepEquals(1, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(2, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(2, graph.getNodes().filter(StoreFieldNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(LoadFieldNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(RawStoreNode.class).count());
    }

    public static int testArrayUnsafeKillSnippet(int[] a1, Object o1, long index, TestObject t) {
        // read must be done
        t.x = a1[0];
        // write must be done
        a1[1] = 0;
        // this unsafe write MUST kill everything
        UNSAFE.putLong(o1, index, 12);
        // we need to -redo this load
        t.y = a1[0];
        // we need to re-load x
        return t.x + t.y;
    }

    @Test
    public void testArrayUnsafeKill() {
        StructuredGraph graph = processMethod("testArrayUnsafeKillSnippet", false);
        assertDeepEquals(1, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(2, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(2, graph.getNodes().filter(StoreFieldNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(LoadFieldNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(RawStoreNode.class).count());
    }

    static int[] array = new int[10000];

    public static int testRepetitiveReadsSnippet() {
        int result = 0;
        for (int i = 0; i < array.length; i++) {
            if (array[i] >= '0' && array[i] <= '9') {
                return i;
            }
        }
        return result;
    }

    @Test
    public void testRepetitiveReads() {
        StructuredGraph graph = processMethod("testRepetitiveReadsSnippet", false);
        assertDeepEquals(0, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(StoreFieldNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(LoadFieldNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(RawStoreNode.class).count());
    }

    public static int SideEffect;

    static class A {
        int a;
        int b;
        int c;
        int d;
        int e;
        int f;
        int g;
        int h;
    }

    public static int testRepetitiveReadsPhiSnippet(A a) {
        /*
         * Hashing of the optimization states in the read elimination uses an economic map. Economic
         * map starts out as a list before migrating to a real hash map if enough elements are
         * added, thus force economic map to opt out to be a hash map instead of a list to test the
         * hashing of nodes that are not yet constructed during read elimination.
         */
        int res = a.a + a.b + a.c + a.d + a.e + a.f + a.g + a.h;

        // read array
        if (array.length > 21) {
            // write
            array = new int[1234];
            // phi
        }
        for (int i = 0; i < array.length; i++) {
            if (array[i] >= '0' && array[i] <= '9') {
                return i;
            }
        }
        return 12 * res;
    }

    @Test
    public void testRepetitiveReadPhi() {
        StructuredGraph graph = processMethod("testRepetitiveReadsPhiSnippet", false);
        assertDeepEquals(0, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(StoreFieldNode.class).count());
        assertDeepEquals(9, graph.getNodes().filter(LoadFieldNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(RawStoreNode.class).count());
    }

    public static int testNeutralElementAliasingSnippet(int[] arr, boolean cond) {
        /*
         * test that neutral elements for binary operations are correctly handled. in all of the
         * following, the second write must correctly kill the first since the indices can
         * potentially be identical.
         */
        int sum = 0;
        for (int i = 1; i < arr.length; i++) {
            arr[i] = 0;
            arr[i + (cond ? 0 : 1)] = 1;
            sum += arr[i];

            arr[i] = 0;
            arr[(cond ? 0 : 1) + i] = 1;
            sum += arr[i];

            arr[i] = 0;
            arr[i - (cond ? 0 : 1)] = 1;
            sum += arr[i];

            arr[i] = 0;
            arr[(cond ? 0 : 1) - i] = 1;
            sum += arr[i];

            // special case, non-commutative operation
            arr[i] = 0;
            arr[(cond ? 1 : 2) - i] = 1;
            sum += arr[i];

            arr[i] = 0;
            arr[i * (cond ? 1 : 2)] = 1;
            sum += arr[i];

            arr[i] = 0;
            arr[(cond ? 1 : 2) * i] = 1;
            sum += arr[i];

            arr[i] = 0;
            arr[i / (cond ? 1 : 2)] = 1;
            sum += arr[i];

            arr[i] = 0;
            arr[(cond ? 1 : 2) / i] = 1;
            sum += arr[i];
        }
        return sum;
    }

    @Test
    public void testNeutralElementAliasing() {
        StructuredGraph graph = processMethod("testNeutralElementAliasingSnippet", false);
        assertDeepEquals(18, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(9, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testBasePlusOffsetWritesSnippet(int[] arr, int base) {
        arr[base] = 1;
        arr[base + 1] = 2;
        arr[2 + base] = 3;

        // all of these should get read-eliminated
        return arr[base] + arr[base + 1] + arr[2 + base];
    }

    @Test
    public void testBasePlusOffsetWrites() {
        StructuredGraph graph = processMethod("testBasePlusOffsetWritesSnippet", false);
        assertDeepEquals(3, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testBaseMinusOffsetWritesSnippet(int[] arr, int base, boolean cond) {
        // the conditional is needed so that the (x - const) does not canonicalise to
        // (x + (-const)), but we still have a known value stamp.
        int i1 = (cond ? 1 : 2);
        int i2 = (cond ? 3 : 4);

        arr[base] = 1;
        arr[base - i1] = 2;
        arr[base - i2] = 3;

        // all of these should get read-eliminated
        return arr[base] + arr[base - i1] + arr[base - i2];
    }

    @Test
    public void testBaseMinusOffsetWrites() {
        StructuredGraph graph = processMethod("testBaseMinusOffsetWritesSnippet", false);
        assertDeepEquals(3, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testBasePlusMinusOffsetWritesSnippet(int[] arr, int base, boolean cond) {
        int i1 = (cond ? 1 : 2);

        arr[base] = 1;
        arr[base - i1] = 2;
        arr[base + 1] = 3;

        return arr[base] + arr[base - i1] + arr[base + 1];
    }

    @Test
    public void testBasePlusMinusOffsetWrites() {
        StructuredGraph graph = processMethod("testBasePlusMinusOffsetWritesSnippet", false);
        assertDeepEquals(3, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testBasePlusMinusOffsetNoAliasSnippet(int[] arr, int base, boolean cond) {
        int off = (cond ? 1 : 2);

        arr[base] = 0;
        arr[off + base] = 1;
        arr[base - off] = -1;

        return arr[base] + arr[base - off] + arr[off + base];
    }

    @Test
    public void testBasePlusMinusOffsetNoAlias() {
        StructuredGraph graph = processMethod("testBasePlusMinusOffsetNoAliasSnippet", false);
        assertDeepEquals(3, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testBasePlusMinusOffsetMayAliasSnippet(int[] arr, int base, boolean cond) {
        int offset = (cond ? -1 : 0);

        arr[base] = 1;
        arr[base - offset] = 2;
        arr[base + 1] = 3;

        return arr[base] + arr[base - offset] + arr[base + 1];
    }

    @Test
    public void testBasePlusMinusOffsetMayAlias() {
        StructuredGraph graph = processMethod("testBasePlusMinusOffsetMayAliasSnippet", false);
        assertDeepEquals(3, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(2, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testAddSubOverflowMayAliasSnippet(int[] arr, int base, boolean cond) {
        int offset = (cond ? Integer.MIN_VALUE : 1);

        // offset may be == -offset
        arr[base - offset] = 1;
        arr[base + offset] = 2;

        return arr[base - offset] + arr[base + offset];
    }

    @Test
    public void testAddSubOverflowMayAlias() {
        StructuredGraph graph = processMethod("testAddSubOverflowMayAliasSnippet", false);
        assertDeepEquals(2, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int testIndexedReadForArrayPhiSnippet(boolean condition, int[] a, int[] b) {
        int[] selected;
        int value;
        if (condition) {
            selected = a;
            value = a[0];
        } else {
            selected = b;
            value = b[0];
        }
        // selected[0] == value, should get read eliminated
        return value + selected[0];
    }

    @Test
    public void testIndexedReadForArrayPhi() {
        StructuredGraph graph = processMethod("testIndexedReadForArrayPhiSnippet", false);
        assertDeepEquals(2, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static int newArraySnippet(int length, int index) {
        int[] zeroArray = new int[length];
        return zeroArray[index];
    }

    @Test
    public void newArray() {
        StructuredGraph graph = processMethod("newArraySnippet", false);
        assertDeepEquals(0, graph.getNodes().filter(LoadIndexedNode.class).count());
        // negative array length check + bounds check:
        assertDeepEquals(2, graph.getNodes().filter(FixedGuardNode.class).count());
        // the allocation can go away:
        assertDeepEquals(0, graph.getNodes().filter(NewArrayNode.class).count());
    }

    public static byte newArrayByteSnippet(int length, int index) {
        byte[] zeroArray = new byte[length];
        return zeroArray[index];
    }

    @Test
    public void newArrayByte() {
        StructuredGraph graph = processMethod("newArrayByteSnippet", false);
        assertDeepEquals(0, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(2, graph.getNodes().filter(FixedGuardNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(NewArrayNode.class).count());
    }

    public static char newArrayCharSnippet(int length, int index) {
        char[] zeroArray = new char[length];
        return zeroArray[index];
    }

    @Test
    public void newArrayChar() {
        StructuredGraph graph = processMethod("newArrayCharSnippet", false);
        assertDeepEquals(0, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(2, graph.getNodes().filter(FixedGuardNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(NewArrayNode.class).count());
    }

    public static boolean newArrayBooleanSnippet(int length, int index) {
        boolean[] zeroArray = new boolean[length];
        return zeroArray[index];
    }

    @Test
    public void newArrayBoolean() {
        StructuredGraph graph = processMethod("newArrayBooleanSnippet", false);
        assertDeepEquals(0, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(2, graph.getNodes().filter(FixedGuardNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(NewArrayNode.class).count());
    }

    public static String newArrayObjectSnippet(int length, int index) {
        String[] zeroArray = new String[length];
        return zeroArray[index];
    }

    @Test
    public void newArrayObject() {
        StructuredGraph graph = processMethod("newArrayObjectSnippet", false);
        assertDeepEquals(0, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(2, graph.getNodes().filter(FixedGuardNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(NewArrayNode.class).count());
    }

    public static int newArrayFillSnippet(int length, int index) {
        int[] filledArray = new int[length];
        Arrays.fill(filledArray, 42);
        /*
         * We inline the fill loop and aren't sophisticated enough to recognize that it overwrites
         * every element. Ideally we would want to rewrite this load to 42.
         */
        return filledArray[index];
    }

    @Test
    public void newArrayFill() {
        StructuredGraph graph = processMethod("newArrayFillSnippet", false);
        assertDeepEquals(1, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(NewArrayNode.class).count());
    }

    public static int newArrayWriteSnippet(int length, int index, int otherIndex) {
        int[] zeroArray = new int[length];
        /* Kill the "new initialized array" cache entry. */
        zeroArray[otherIndex] = 42;
        return zeroArray[index];
    }

    @Test
    public void newArrayWrite() {
        StructuredGraph graph = processMethod("newArrayWriteSnippet", false);
        assertDeepEquals(1, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(StoreIndexedNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(NewArrayNode.class).count());
    }

    public static int newArrayPhiSnippet(boolean condition, int length1, int length2, int index) {
        int[] zeroArray;
        if (condition) {
            zeroArray = new int[length1];
        } else {
            zeroArray = new int[length2];
        }
        return zeroArray[index];
    }

    @Test
    public void newArrayPhi() {
        StructuredGraph graph = processMethod("newArrayPhiSnippet", false);
        assertDeepEquals(0, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(FixedGuardNode.class).count());
        /*
         * We can't remove the allocations (at least not in the read elimination phase itself)
         * because the array length computation for the bounds check keeps them alive.
         */
        assertDeepEquals(2, graph.getNodes().filter(NewArrayNode.class).count());
    }

    public static int newArrayPhiWriteSnippet(boolean condition, int length1, int length2, int index) {
        int[] zeroArray;
        if (condition) {
            zeroArray = new int[length1];
        } else {
            zeroArray = new int[length2];
            /* Kill the "new initialized array" cache entry. */
            zeroArray[42] = 42;
        }
        return zeroArray[index];
    }

    @Test
    public void newArrayPhiWrite() {
        StructuredGraph graph = processMethod("newArrayPhiWriteSnippet", false);
        assertDeepEquals(1, graph.getNodes().filter(LoadIndexedNode.class).count());
    }

    public static float newArrayInBoundsSnippet() {
        float[] zeroArray = new float[7];
        return zeroArray[5];
    }

    @Test
    public void newArrayInBounds() {
        StructuredGraph graph = processMethod("newArrayInBoundsSnippet", false);
        assertDeepEquals(0, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(FixedGuardNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(NewArrayNode.class).count());
    }

    public static double newArrayOutOfBoundsSnippet() {
        double[] zeroArray = new double[7];
        return zeroArray[8];
    }

    @Test
    public void newArrayOutOfBounds() {
        StructuredGraph graph = processMethod("newArrayOutOfBoundsSnippet", false);
        /* Leave the read alone if it's provably out of bounds. */
        assertDeepEquals(1, graph.getNodes().filter(LoadIndexedNode.class).count());
        assertDeepEquals(0, graph.getNodes().filter(FixedGuardNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(NewArrayNode.class).count());
    }

    @Test
    public void noNewArrayDeoptLoop() {
        ResolvedJavaMethod newArrayByte = getResolvedJavaMethod("newArrayByteSnippet");
        newArrayByte.reprofile();
        int length = 10;

        /* Normal test: interpret, compile, compare. */
        test(newArrayByte, null, length, 5);
        Assert.assertEquals("deopts on in-bounds access", 0, newArrayByte.getProfilingInfo().getDeoptimizationCount(DeoptimizationReason.BoundsCheckException));
        InstalledCode code = getCode(newArrayByte);
        code.invalidate();

        /* Recompile and cause an out-of-bounds deopt in the compiled code. */
        code = getCode(newArrayByte);
        boolean caughtExpectedException = false;
        try {
            executeVarargsSafe(code, length, 10);
        } catch (ArrayIndexOutOfBoundsException e) {
            caughtExpectedException = true;
        }
        Assert.assertTrue("out-of-bounds access should have thrown", caughtExpectedException);
        Assert.assertFalse("implicit bounds check exception should have invalidated the code", code.isValid());
        Assert.assertEquals("foreign calls for explicit exception", 0, lastCompiledGraph.getNodes().filter(ForeignCallNode.class).count());

        /*
         * Recompile and check that another out-of-bounds access will not deopt but throw an
         * explicit exception. This means that we won't get into a deopt loop.
         */
        code = getCode(newArrayByte);
        caughtExpectedException = false;
        try {
            executeVarargsSafe(code, length, 10);
        } catch (ArrayIndexOutOfBoundsException e) {
            caughtExpectedException = true;
        }
        Assert.assertTrue("out-of-bounds access should have thrown", caughtExpectedException);
        Assert.assertTrue("explicit bounds check exception should not have invalidated the code", code.isValid());
        Assert.assertEquals("foreign calls for explicit exception", 1, lastCompiledGraph.getNodes().filter(ForeignCallNode.class).count());
    }

    protected StructuredGraph processMethod(String snippet, boolean doLowering) {
        StructuredGraph graph = parseEager(getResolvedJavaMethod(snippet), AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        createInliningPhase().apply(graph, context);
        if (doLowering) {
            new HighTierLoweringPhase(CanonicalizerPhase.create()).apply(graph, context);
        }
        new ReadEliminationPhase(CanonicalizerPhase.create()).apply(graph, context);
        return graph;
    }

    @Test
    public void testClone() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("snippet"));
        InliningPhase inlining = new InliningPhase(new InlineEverythingPolicy(), createCanonicalizerPhase());
        inlining.apply(graph, getDefaultHighTierContext());
        for (LoadFieldNode load : graph.getNodes().filter(LoadFieldNode.class)) {
            load.setStamp(ObjectStamp.pointerNonNull(load.stamp(NodeView.DEFAULT)));
        }
        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        new ReadEliminationPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());
        new ObjectCloneRemovalPhase().apply(graph);

        graph.verify(true);
        assertDeepEquals(0, graph.getNodes().filter(ObjectCloneNode.class).count());
    }

    public static class Holder {
        int[] arr = new int[10];
    }

    public static int snippet(Holder h) {
        var clone0 = h.arr;
        var clone1 = h.arr.clone();

        return clone0[0] + clone1[0];
    }
}
