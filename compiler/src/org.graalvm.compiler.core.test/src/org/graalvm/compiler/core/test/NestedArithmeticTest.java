/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import org.junit.Test;

public class LoopArithmeticTest extends GraalCompilerTest {
    public static final int N = 400;

    public static int runNestedLoopTry() {
        int i3 = 240, i4, i5 = 13485;
        for (i4 = 303; i4 > 15; i4 -= 2) {
            int f = 1;
            do {
                try {
                    i3 = (38726 / i5);
                    i3 = (i4 % -21500);
                    i5 = (i3 % 787);
                } catch (ArithmeticException a_e) {
                    System.out.println("f=" + f + ", ERR i3 = " + i3 + ", i5 = " + i5);
                    return 0;
                }
                i3 <<= i4;
                i5 <<= i5;
                i3 += (8 + (f * f));
                i5 >>= i5;
            } while (++f < 11);
        }
        return 0;
    }

    @Test
    public void nestedLoopTryTest() {
        test("runNestedLoopTry");
    }
}
