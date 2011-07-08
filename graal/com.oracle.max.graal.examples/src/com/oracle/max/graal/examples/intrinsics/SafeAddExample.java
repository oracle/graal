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
package com.oracle.max.graal.examples.intrinsics;


public class SafeAddExample {

    public static final int N = 100000000;

    public static void run() {
        System.out.println();
        System.out.println();
        System.out.println("Running SafeAdd Example");
        long start = System.currentTimeMillis();
        System.out.println("result=" + testSafeAdd());
        System.out.println("time=" + (System.currentTimeMillis() - start) + "ms");
    }

    private static int testSafeAdd() {
        int sum = 0;
        int j = N;
        for (int i = -N; i < N; ++i) {
            sum = safeAdd(sum, i);
            sum = safeAdd(sum, j);
            --j;
        }
        return sum;
    }

    public static int safeAdd(int a, int b) {
        int result = a + b;
        if (b < 0 && result > a) {
            throw new IllegalStateException("underflow when adding " + a + " and " + b);
        } else if (b > 0 && result < a) {
            throw new IllegalStateException("overflow when adding " + a + " and " + b);
        }
        return result;
    }
}
