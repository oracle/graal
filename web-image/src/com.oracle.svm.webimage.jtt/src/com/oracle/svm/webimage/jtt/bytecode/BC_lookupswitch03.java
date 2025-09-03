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
public class BC_lookupswitch03 {

    @NeverInline(value = "Test")
    public static int test(int a) {
        final int b = a + 10;
        switch (b) {
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
        System.out.println(test(66));
    }

    @NeverInline(value = "Test")
    public void run3() throws Throwable {
        System.out.println(test(67));
    }

    @NeverInline(value = "Test")
    public void run4() throws Throwable {
        System.out.println(test(68));
    }

    @NeverInline(value = "Test")
    public void run5() throws Throwable {
        System.out.println(test(96));
    }

    @NeverInline(value = "Test")
    public void run6() throws Throwable {
        System.out.println(test(97));
    }

    @NeverInline(value = "Test")
    public void run7() throws Throwable {
        System.out.println(test(98));
    }

    @NeverInline(value = "Test")
    public void run8() throws Throwable {
        System.out.println(test(106));
    }

    @NeverInline(value = "Test")
    public void run9() throws Throwable {
        System.out.println(test(107));
    }

    @NeverInline(value = "Test")
    public void run10() throws Throwable {
        System.out.println(test(108));
    }

    @NeverInline(value = "Test")
    public void run11() throws Throwable {
        System.out.println(test(132));
    }

    @NeverInline(value = "Test")
    public void run12() throws Throwable {
        System.out.println(test(133));
    }

    @NeverInline(value = "Test")
    public void run13() throws Throwable {
        System.out.println(test(134));
    }

    @NeverInline(value = "Test")
    public void run14() throws Throwable {
        System.out.println(test(211));
    }

    @NeverInline(value = "Test")
    public void run15() throws Throwable {
        System.out.println(test(212));
    }

    @NeverInline(value = "Test")
    public void run16() throws Throwable {
        System.out.println(test(213));
    }

    @NeverInline(value = "Test")
    public void run17() throws Throwable {
        System.out.println(test(-121));
    }

    @NeverInline(value = "Test")
    public void run18() throws Throwable {
        System.out.println(test(-122));
    }

    @NeverInline(value = "Test")
    public void run19() throws Throwable {
        System.out.println(test(-123));
    }

    @NeverInline(value = "Test")
    public static void s0() {
        try {
            new BC_lookupswitch03().run0();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s1() {
        try {
            new BC_lookupswitch03().run1();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s2() {
        try {
            new BC_lookupswitch03().run2();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s3() {
        try {
            new BC_lookupswitch03().run3();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s4() {
        try {
            new BC_lookupswitch03().run4();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s5() {
        try {
            new BC_lookupswitch03().run5();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s6() {
        try {
            new BC_lookupswitch03().run6();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s7() {
        try {
            new BC_lookupswitch03().run7();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s8() {
        try {
            new BC_lookupswitch03().run8();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s9() {
        try {
            new BC_lookupswitch03().run9();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s10() {
        try {
            new BC_lookupswitch03().run10();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s11() {
        try {
            new BC_lookupswitch03().run11();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s12() {
        try {
            new BC_lookupswitch03().run12();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s13() {
        try {
            new BC_lookupswitch03().run13();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s14() {
        try {
            new BC_lookupswitch03().run14();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s15() {
        try {
            new BC_lookupswitch03().run15();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s16() {
        try {
            new BC_lookupswitch03().run16();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s17() {
        try {
            new BC_lookupswitch03().run17();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s18() {
        try {
            new BC_lookupswitch03().run18();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s19() {
        try {
            new BC_lookupswitch03().run19();
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
        s9();
        s10();
        s11();
        s12();
        s13();
        s14();
        s15();
        s16();
        s17();
        s18();
        s19();
    }
}
