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

public class BC_aastore {

    static Object[] param = {new Object(), null, "h"};
    static Object[] array1 = {null, null, null};
    static String[] array2 = {null, null, null};

    @NeverInline(value = "Test")
    public static int test(boolean a, int indx) {
        Object[] array = a ? array1 : array2;
        Object val;
        val = param[indx];
        array[indx] = val;
        return indx;
    }

    @NeverInline(value = "Test")
    public void run0() throws Throwable {
        System.out.println(test(true, 0));
    }

    @NeverInline(value = "Test")
    public void run1() throws Throwable {
        System.out.println(test(true, 1));
    }

    @NeverInline(value = "Test")
    public void run2() throws Throwable {
        System.out.println(test(true, 2));
    }

    @NeverInline(value = "Test")
    public void run3() throws Throwable {
        System.out.println(test(false, 1));
    }

    @NeverInline(value = "Test")
    public void run4() throws Throwable {
        System.out.println(test(false, 2));
    }

    @NeverInline(value = "Test")
    public static void s0() {
        try {
            new BC_aastore().run0();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s1() {
        try {
            new BC_aastore().run1();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s2() {
        try {
            new BC_aastore().run2();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s3() {
        try {
            new BC_aastore().run3();
        } catch (Throwable t) {
            System.out.println("Error");
        }
    }

    @NeverInline(value = "Test")
    public static void s4() {
        try {
            new BC_aastore().run4();
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
    }

}
