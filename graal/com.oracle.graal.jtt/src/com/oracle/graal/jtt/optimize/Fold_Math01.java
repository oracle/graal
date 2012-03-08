/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.jtt.optimize;

import org.junit.*;

/*
 */
public class Fold_Math01 {

    public static double test(int arg) {
        switch (arg) {
            case 0:
                return abs();
            case 1:
                return sin();
            case 2:
                return cos();
            case 3:
                return tan();
            case 4:
                return atan2();
            case 5:
                return sqrt();
            case 6:
                return log();
            case 7:
                return log10();
            case 8:
                return pow();
            case 9:
                return exp();
            case 10:
                return min();
            case 11:
                return max();
        }
        return 42;
    }

    private static double abs() {
        return Math.abs(-10.0d);
    }

    private static double sin() {
        return Math.sin(0.15d);
    }

    private static double cos() {
        return Math.cos(0.15d);
    }

    private static double tan() {
        return Math.tan(0.15d);
    }

    private static double atan2() {
        return Math.atan2(0.15d, 3.1d);
    }

    private static double sqrt() {
        return Math.sqrt(144d);
    }

    private static double log() {
        return Math.log(3.15d);
    }

    private static double log10() {
        return Math.log10(0.15d);
    }

    private static double pow() {
        return Math.pow(2.15d, 6.1d);
    }

    private static double exp() {
        return Math.log(3.15d);
    }

    private static int min() {
        return Math.min(2, -1);
    }

    private static int max() {
        return Math.max(2, -1);
    }

    @Test
    public void run0() throws Throwable {
        Assert.assertEquals(10d, test(0), 0);
    }

    @Test
    public void run1() throws Throwable {
        Assert.assertEquals(0.14943813247359922d, test(1), 0);
    }

    @Test
    public void run2() throws Throwable {
        Assert.assertEquals(0.9887710779360422d, test(2), 0);
    }

    @Test
    public void run3() throws Throwable {
        Assert.assertEquals(0.15113521805829508d, test(3), 0);
    }

    @Test
    public void run4() throws Throwable {
        Assert.assertEquals(0.04834938665190287d, test(4), 0);
    }

    @Test
    public void run5() throws Throwable {
        Assert.assertEquals(12.0d, test(5), 0);
    }

    @Test
    public void run6() throws Throwable {
        Assert.assertEquals(1.1474024528375417d, test(6), 0);
    }

    @Test
    public void run7() throws Throwable {
        Assert.assertEquals(-0.8239087409443188d, test(7), 0);
    }

    @Test
    public void run8() throws Throwable {
        Assert.assertEquals(106.62882057436371d, test(8), 0);
    }

    @Test
    public void run9() throws Throwable {
        Assert.assertEquals(1.1474024528375417d, test(9), 0);
    }

    @Test
    public void run10() throws Throwable {
        Assert.assertEquals(-1.0d, test(10), 0);
    }

    @Test
    public void run11() throws Throwable {
        Assert.assertEquals(2.0d, test(11), 0);
    }

    @Test
    public void run12() throws Throwable {
        Assert.assertEquals(42d, test(12), 0);
    }

}
