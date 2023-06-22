/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.optimize;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 * Tests value numbering of integer operations.
 */
public class VN_Int03 extends JTTTest {

    private static boolean cond = true;

    public static int test(int arg) {
        if (arg == 0) {
            return add(arg);
        }
        if (arg == 1) {
            return sub(arg);
        }
        if (arg == 2) {
            return mul(arg);
        }
        if (arg == 3) {
            return div(arg);
        }
        if (arg == 4) {
            return mod(arg);
        }
        if (arg == 5) {
            return and(arg);
        }
        if (arg == 6) {
            return or(arg);
        }
        if (arg == 7) {
            return xor(arg);
        }
        return 0;
    }

    public static int add(int x) {
        int c = 3;
        int t = x + c;
        if (cond) {
            int u = x + c;
            return t + u;
        }
        return 0;
    }

    public static int sub(int x) {
        int c = 3;
        int t = x - c;
        if (cond) {
            int u = x - c;
            return t - u;
        }
        return 3;
    }

    public static int mul(int x) {
        int i = 3;
        int t = x * i;
        if (cond) {
            int u = x * i;
            return t * u;
        }
        return 3;
    }

    public static int div(int x) {
        int i = 9;
        int t = i / x;
        if (cond) {
            int u = i / x;
            return t / u;
        }
        return 9;
    }

    public static int mod(int x) {
        int i = 7;
        int t = i % x;
        if (cond) {
            int u = i % x;
            return t % u;
        }
        return 7;
    }

    public static int and(int x) {
        int i = 7;
        int t = i & x;
        if (cond) {
            int u = i & x;
            return t & u;
        }
        return 7;
    }

    public static int or(int x) {
        int i = 7;
        int t = i | x;
        if (cond) {
            int u = i | x;
            return t | u;
        }
        return 7;
    }

    public static int xor(int x) {
        int i = 7;
        int t = i ^ x;
        if (cond) {
            int u = i ^ x;
            return t ^ u;
        }
        return 7;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 2);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 3);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", 4);
    }

    @Test
    public void run5() throws Throwable {
        runTest("test", 5);
    }

    @Test
    public void run6() throws Throwable {
        runTest("test", 6);
    }

    @Test
    public void run7() throws Throwable {
        runTest("test", 7);
    }

}
