/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.ptx.test;

import static com.oracle.graal.lir.ptx.ThreadDimension.*;

import org.junit.*;

import com.oracle.graal.lir.ptx.*;

public class ArrayPTXTest extends PTXTest {

    @Ignore("PTXHotSpotForeignCallsProvider.lookupForeignCall() is unimplemented")
    @Test
    public void test1() {
        test("testStoreArray1I", new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, 2);
    }

    @Ignore("PTXHotSpotForeignCallsProvider.lookupForeignCall() is unimplemented")
    @Test
    public void test2() {
        test("testStoreArrayWarp0", new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, 2);
    }

    @Ignore("PTXHotSpotForeignCallsProvider.lookupForeignCall() is unimplemented")
    @Test
    public void test3() {
        test("testStoreArrayWarp1I", new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, 2);
    }

    public static void testStoreArray1I(int[] array, int i) {
        array[i] = 42;
    }

    public static void testStoreArrayWarp0(int[] array, @Warp(dimension = X) int i) {
        array[i] = 42;
    }

    public static void testStoreArrayWarp1I(@ParallelOver(dimension = X) int[] array, @Warp(dimension = X) int i) {
        array[i] = 42;
    }

    public static void main(String[] args) {
        compileAndPrintCode(new ArrayPTXTest());
    }
}
