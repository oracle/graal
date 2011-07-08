/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.examples.inlining;


public class InliningExample {

    public static void run() {
        System.out.println();
        System.out.println();
        System.out.println("Running Inlining Example");
        long start = System.currentTimeMillis();
        System.out.println("result1=" + test());
        System.out.println("result2=" + testFib());
        System.out.println("time=" + (System.currentTimeMillis() - start) + "ms");
    }

    private static int test() {
        return alwaysInline(30);
    }

    public static int testFib() {
        int sum = 0;
        for (int i = 0; i < 100000000; ++i) {
            sum += fib(5);
        }
        return sum;
    }

    public static int alwaysInline(int value) {
        if (value == 0) {
            return neverInline(value);
        }
        return alwaysInline(value - 1);
    }

    public static int neverInline(int value) {
        if (value == 0) {
            return 0;
        }
        return neverInline(value - 1);
    }

    public static int fib(int val) {
        if (val == 0 || val == 1) {
            return 1;
        }
        return fib(val - 1) + fib(val - 2);
    }
}
