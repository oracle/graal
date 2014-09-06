/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.jtt.bytecode;

import org.junit.*;

import com.oracle.graal.jtt.*;

/*
 */
public class BC_irem extends JTTTest {

    public static int test(int a, int b) {
        return a % b;
    }

    // Left as constant
    public static int test2(int b) {
        return 13 % b;
    }

    // Right as constant
    public static int test3(int a) {
        return a % 13;
    }

    // Tests if the zero extension works fine with 64 bit registers behind
    public static long test4(int a, int b) {
        int ra = Math.abs(a % b);
        int rb = Math.abs(a) % b;
        return ra << 32 | rb;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 1, 2);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 2, -1);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 256, 4);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 135, 7);
    }

    @Test
    public void run20() throws Throwable {
        runTest("test2", 2);
    }

    @Test
    public void run21() throws Throwable {
        runTest("test2", 20000000);
    }

    @Test
    public void run22() throws Throwable {
        runTest("test2", -20000000);
    }

    @Test
    public void run30() throws Throwable {
        runTest("test3", 2);
    }

    @Test
    public void run31() throws Throwable {
        runTest("test3", 200000000);
    }

    @Test
    public void run32() throws Throwable {
        runTest("test3", -200000000);
    }

    @Test
    public void run41() throws Throwable {
        runTest("test4", -100000, 3000000);
    }

    @Test
    public void run42() throws Throwable {
        runTest("test4", -100000, 30);
    }

    @Test
    public void run43() throws Throwable {
        runTest("test4", -1000000, -30);
    }
}
