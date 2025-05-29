/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.webimage.jtt.bytecode;

import com.oracle.svm.core.NeverInline;

/*
 */
public class BC_dcmp10 {

    @NeverInline(value = "Test")
    public static boolean test(int x) {
        double a = 0;
        double b = 0;
        switch (x) {
            case 0:
                a = Double.POSITIVE_INFINITY;
                b = 1;
                break;
            case 1:
                a = 1;
                b = Double.POSITIVE_INFINITY;
                break;
            case 2:
                a = Double.NEGATIVE_INFINITY;
                b = 1;
                break;
            case 3:
                a = 1;
                b = Double.NEGATIVE_INFINITY;
                break;
            case 4:
                a = Double.NEGATIVE_INFINITY;
                b = Double.NEGATIVE_INFINITY;
                break;
            case 5:
                a = Double.NEGATIVE_INFINITY;
                b = Double.POSITIVE_INFINITY;
                break;
            case 6:
                a = Double.NaN;
                b = Double.POSITIVE_INFINITY;
                break;
            case 7:
                a = 1;
                b = Double.NaN;
                break;
            case 8:
                a = 1;
                b = -0.0d / 0.0d;
                break;
        }
        return a <= b;
    }

    @NeverInline(value = "Test")
    public void run0() throws Throwable {
        System.out.println(test(0));
    }

    @NeverInline(value = "Test")
    public void run1() throws Throwable {
        System.out.println(test(1));
    }

    @NeverInline(value = "Test")
    public void run2() throws Throwable {
        System.out.println(test(2));
    }

    @NeverInline(value = "Test")
    public void run3() throws Throwable {
        System.out.println(test(3));
    }

    @NeverInline(value = "Test")
    public void run4() throws Throwable {
        System.out.println(test(4));
    }

    @NeverInline(value = "Test")
    public void run5() throws Throwable {
        System.out.println(test(5));
    }

    @NeverInline(value = "Test")
    public void run6() throws Throwable {
        System.out.println(test(6));
    }

    @NeverInline(value = "Test")
    public void run7() throws Throwable {
        System.out.println(test(7));
    }

    @NeverInline(value = "Test")
    public void run8() throws Throwable {
        System.out.println(test(8));
    }

    @NeverInline(value = "Test")
    public static void s0() {
        try {
            new BC_dcmp10().run0();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s1() {
        try {
            new BC_dcmp10().run1();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s2() {
        try {
            new BC_dcmp10().run2();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s3() {
        try {
            new BC_dcmp10().run3();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s4() {
        try {
            new BC_dcmp10().run4();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s5() {
        try {
            new BC_dcmp10().run5();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s6() {
        try {
            new BC_dcmp10().run6();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s7() {
        try {
            new BC_dcmp10().run7();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s8() {
        try {
            new BC_dcmp10().run8();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    public static void main(String[] args) {
        s0();
        s1();
        s2();
        s3();
        s4();
        s5();
        s6();
        s7();
        s8();
    }

}
