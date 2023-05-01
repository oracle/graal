/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.graalvm.compiler.jtt.micro;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class BigMixedParams03 extends JTTTest {

    @SuppressWarnings("unused")
    public static double test(int choice, int i0, int i1, int i2, int i3, double p0, double p1, double p2, double p3, int i4, int i5, double p4, double p5, double p6, double p7, double p8) {
        switch (choice) {
            case 0:
                return p0;
            case 1:
                return p1;
            case 2:
                return p2;
            case 3:
                return p3;
            case 4:
                return p4;
            case 5:
                return p5;
            case 6:
                return p6;
            case 7:
                return p7;
            case 8:
                return p8;
        }
        return 42;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0, -1, -1, -1, -1, 1d, 2d, 3d, 4d, -1, -1, 5d, 6d, 7d, 8d, 9d);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1, -1, -1, -1, -1, 1d, 2d, 3d, 4d, -1, -1, 5d, 6d, 7d, 8d, 9d);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 2, -1, -1, -1, -1, 1d, 2d, 3d, 4d, -1, -1, 5d, 6d, 7d, 8d, 9d);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 3, -1, -1, -1, -1, 1d, 2d, 3d, 4d, -1, -1, 5d, 6d, 7d, 8d, 9d);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", 4, -1, -1, -1, -1, 1d, 2d, 3d, 4d, -1, -1, 5d, 6d, 7d, 8d, 9d);
    }

    @Test
    public void run5() throws Throwable {
        runTest("test", 5, -1, -1, -1, -1, 1d, 2d, 3d, 4d, -1, -1, 5d, 6d, 7d, 8d, 9d);
    }

    @Test
    public void run6() throws Throwable {
        runTest("test", 6, -1, -1, -1, -1, 1d, 2d, 3d, 4d, -1, -1, 5d, 6d, 7d, 8d, 9d);
    }

    @Test
    public void run7() throws Throwable {
        runTest("test", 7, -1, -1, -1, -1, 1d, 2d, 3d, 4d, -1, -1, 5d, 6d, 7d, 8d, 9d);
    }

    @Test
    public void run8() throws Throwable {
        runTest("test", 8, -1, -1, -1, -1, 1d, 2d, 3d, 4d, -1, -1, 5d, 6d, 7d, 8d, 9d);
    }

}
