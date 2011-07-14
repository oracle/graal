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
package com.oracle.max.graal.examples.deopt;

import com.oracle.max.graal.examples.intrinsics.*;


public class DeoptExample {

    public static void run() {
        System.out.println();
        System.out.println();
        System.out.println("Running Deopt Example");
        long start = System.currentTimeMillis();
        System.out.println("result1=" + new DeoptExample().test());
        System.out.println("time=" + (System.currentTimeMillis() - start) + "ms");
    }

    private int test() {
        try {
            return testDeopt(90000);
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
            return 0;
        }
    }

    private int testDeopt(int n) {
        int sum = 0;
        for (int i = 0; i < n; i = SafeAddExample.safeAdd(i, 1)) {
            sum = SafeAddExample.safeAdd(sum, i);
        }
        return sum;
    }
}
