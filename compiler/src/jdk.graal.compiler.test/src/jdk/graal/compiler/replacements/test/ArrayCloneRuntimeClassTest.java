/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;

/**
 * The class of an array clone is the run time class of the original. When the compiler only knows
 * an upper bound of that class, it must not build the copy from that bound, even if it knows the
 * length.
 */
public class ArrayCloneRuntimeClassTest extends GraalCompilerTest {

    public static Object sink;

    public static String cloneClass(boolean c) {
        Object[] a = c ? new String[2] : new Integer[2];
        Object[] b = a.clone();
        return b.getClass().getName();
    }

    public static String cloneStore(boolean c) {
        Object[] a = c ? new String[2] : new Integer[2];
        Object[] b = a.clone();
        try {
            b[0] = Long.valueOf(1);
            return "stored";
        } catch (ArrayStoreException e) {
            return "ArrayStoreException";
        }
    }

    public static String clonePublished(boolean c) {
        Object[] a = c ? new String[3] : new Integer[3];
        sink = a;
        return a.clone().getClass().getName();
    }

    private static void warmUp() {
        for (int i = 0; i < 20000; i++) {
            cloneClass((i & 1) == 0);
            cloneStore((i & 1) == 0);
            clonePublished((i & 1) == 0);
        }
    }

    @Test
    public void testClass() {
        warmUp();
        test("cloneClass", true);
        test("cloneClass", false);
    }

    @Test
    public void testStore() {
        warmUp();
        test("cloneStore", true);
    }

    @Test
    public void testPublished() {
        warmUp();
        test("clonePublished", true);
    }
}
