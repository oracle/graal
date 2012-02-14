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
package com.oracle.max.graal.jtt.bytecode;

import org.junit.*;

/*
 */
public class BC_lookupswitch04 {

    public static int test(int a) {
        final int b = a + 8;
        final int c = b + 2;
        switch (c) {
            case 77:
                return 0;
            case 107:
                return 1;
            case 117:
                return 2;
            case 143:
                return 3;
            case 222:
                return 4;
            case -112:
                return 5;
        }
        return 42;
    }

    @Test
    public void run0() throws Throwable {
        Assert.assertEquals(42, test(0));
    }

    @Test
    public void run1() throws Throwable {
        Assert.assertEquals(42, test(1));
    }

    @Test
    public void run2() throws Throwable {
        Assert.assertEquals(42, test(66));
    }

    @Test
    public void run3() throws Throwable {
        Assert.assertEquals(0, test(67));
    }

    @Test
    public void run4() throws Throwable {
        Assert.assertEquals(42, test(68));
    }

    @Test
    public void run5() throws Throwable {
        Assert.assertEquals(42, test(96));
    }

    @Test
    public void run6() throws Throwable {
        Assert.assertEquals(1, test(97));
    }

    @Test
    public void run7() throws Throwable {
        Assert.assertEquals(42, test(98));
    }

    @Test
    public void run8() throws Throwable {
        Assert.assertEquals(42, test(106));
    }

    @Test
    public void run9() throws Throwable {
        Assert.assertEquals(2, test(107));
    }

    @Test
    public void run10() throws Throwable {
        Assert.assertEquals(42, test(108));
    }

    @Test
    public void run11() throws Throwable {
        Assert.assertEquals(42, test(132));
    }

    @Test
    public void run12() throws Throwable {
        Assert.assertEquals(3, test(133));
    }

    @Test
    public void run13() throws Throwable {
        Assert.assertEquals(42, test(134));
    }

    @Test
    public void run14() throws Throwable {
        Assert.assertEquals(42, test(211));
    }

    @Test
    public void run15() throws Throwable {
        Assert.assertEquals(4, test(212));
    }

    @Test
    public void run16() throws Throwable {
        Assert.assertEquals(42, test(213));
    }

    @Test
    public void run17() throws Throwable {
        Assert.assertEquals(42, test(-121));
    }

    @Test
    public void run18() throws Throwable {
        Assert.assertEquals(5, test(-122));
    }

    @Test
    public void run19() throws Throwable {
        Assert.assertEquals(42, test(-123));
    }

}
