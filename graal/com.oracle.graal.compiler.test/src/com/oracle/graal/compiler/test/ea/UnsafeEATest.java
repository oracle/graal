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
package com.oracle.graal.compiler.test.ea;

import org.junit.*;

import sun.misc.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;

public class UnsafeEATest extends EATestBase {

    public static int zero = 0;

    private static final Unsafe unsafe;
    private static final long fieldOffsetX;
    private static final long fieldOffsetY;

    static {
        unsafe = UnsafeAccess.unsafe;
        try {
            fieldOffsetX = unsafe.objectFieldOffset(TestClassInt.class.getField("x"));
            fieldOffsetY = unsafe.objectFieldOffset(TestClassInt.class.getField("y"));
            assert fieldOffsetY == fieldOffsetX + 4;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testSimpleInt() {
        testEscapeAnalysis("testSimpleIntSnippet", Constant.forInt(101), false);
    }

    public static int testSimpleIntSnippet() {
        TestClassInt x = new TestClassInt();
        unsafe.putInt(x, fieldOffsetX, 101);
        return unsafe.getInt(x, fieldOffsetX);
    }

    @Test
    public void testMaterializedInt() {
        test("testMaterializedIntSnippet");
    }

    public static TestClassInt testMaterializedIntSnippet() {
        TestClassInt x = new TestClassInt();
        unsafe.putInt(x, fieldOffsetX, 101);
        return x;
    }

    @Test
    public void testSimpleDouble() {
        testEscapeAnalysis("testSimpleDoubleSnippet", Constant.forDouble(10.1), false);
    }

    public static double testSimpleDoubleSnippet() {
        TestClassInt x = new TestClassInt();
        unsafe.putDouble(x, fieldOffsetX, 10.1);
        return unsafe.getDouble(x, fieldOffsetX);
    }

    @Test
    public void testMergedDouble() {
        testEscapeAnalysis("testMergedDoubleSnippet", null, false);
        Assert.assertEquals(1, returnNodes.size());
        Assert.assertTrue(returnNodes.get(0).result() instanceof ValuePhiNode);
        PhiNode phi = (PhiNode) returnNodes.get(0).result();
        Assert.assertTrue(phi.valueAt(0) instanceof LoadFieldNode);
        Assert.assertTrue(phi.valueAt(1) instanceof LoadFieldNode);
    }

    public static double testMergedDoubleSnippet(boolean a) {
        TestClassInt x;
        if (a) {
            x = new TestClassInt(0, 0);
            unsafe.putDouble(x, fieldOffsetX, doubleField);
        } else {
            x = new TestClassInt();
            unsafe.putDouble(x, fieldOffsetX, doubleField2);
        }
        return unsafe.getDouble(x, fieldOffsetX);
    }

    @Test
    public void testMaterializedDouble() {
        test("testMaterializedDoubleSnippet");
    }

    public static TestClassInt testMaterializedDoubleSnippet() {
        TestClassInt x = new TestClassInt();
        unsafe.putDouble(x, fieldOffsetX, 10.1);
        return x;
    }

    @Test
    public void testDeoptDoubleVar() {
        test("testDeoptDoubleVarSnippet");
    }

    public static double doubleField = 10.1e99;
    public static double doubleField2;

    public static TestClassInt testDeoptDoubleVarSnippet() {
        TestClassInt x = new TestClassInt();
        unsafe.putDouble(x, fieldOffsetX, doubleField);
        doubleField2 = 123;
        try {
            doubleField = ((int) unsafe.getDouble(x, fieldOffsetX)) / zero;
        } catch (RuntimeException e) {
            return x;
        }
        return x;
    }

    @Test
    public void testDeoptDoubleConstant() {
        test("testDeoptDoubleConstantSnippet");
    }

    public static TestClassInt testDeoptDoubleConstantSnippet() {
        TestClassInt x = new TestClassInt();
        unsafe.putDouble(x, fieldOffsetX, 10.123);
        doubleField2 = 123;
        try {
            doubleField = ((int) unsafe.getDouble(x, fieldOffsetX)) / zero;
        } catch (RuntimeException e) {
            return x;
        }
        return x;
    }

    @Test
    public void testDeoptLongVar() {
        test("testDeoptLongVarSnippet");
    }

    public static long longField = 0x133443218aaaffffL;
    public static long longField2;

    public static TestClassInt testDeoptLongVarSnippet() {
        TestClassInt x = new TestClassInt();
        unsafe.putLong(x, fieldOffsetX, longField);
        longField2 = 123;
        try {
            longField = unsafe.getLong(x, fieldOffsetX) / zero;
        } catch (RuntimeException e) {
            return x;
        }
        return x;
    }

    @Test
    public void testDeoptLongConstant() {
        test("testDeoptLongConstantSnippet");
    }

    public static TestClassInt testDeoptLongConstantSnippet() {
        TestClassInt x = new TestClassInt();
        unsafe.putLong(x, fieldOffsetX, 0x2222222210123L);
        longField2 = 123;
        try {
            longField = unsafe.getLong(x, fieldOffsetX) / zero;
        } catch (RuntimeException e) {
            return x;
        }
        return x;
    }
}
