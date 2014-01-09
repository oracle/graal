/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.jtt.jdk;

import java.lang.reflect.*;
import java.util.*;

import org.junit.*;

import sun.misc.*;

import com.oracle.graal.jtt.*;

/*
 */
public class UnsafeAllocateInstance01 extends JTTTest {

    private static abstract class AbstractClass {

    }

    int field01 = 42;

    public static int testInstance() throws SecurityException, InstantiationException {
        final Unsafe unsafe = getUnsafe();
        UnsafeAllocateInstance01 newObject = (UnsafeAllocateInstance01) unsafe.allocateInstance(UnsafeAllocateInstance01.class);
        return newObject.field01;
    }

    public static void testArray() throws SecurityException, InstantiationException {
        final Unsafe unsafe = getUnsafe();
        unsafe.allocateInstance(UnsafeAllocateInstance01[].class);
    }

    public static void testAbstract() throws SecurityException, InstantiationException {
        final Unsafe unsafe = getUnsafe();
        unsafe.allocateInstance(AbstractClass.class);
    }

    public static void testInterface() throws SecurityException, InstantiationException {
        final Unsafe unsafe = getUnsafe();
        unsafe.allocateInstance(List.class);
    }

    public static void testClass() throws SecurityException, InstantiationException {
        final Unsafe unsafe = getUnsafe();
        unsafe.allocateInstance(Class.class);
    }

    public static void testVoid() throws SecurityException, InstantiationException {
        final Unsafe unsafe = getUnsafe();
        unsafe.allocateInstance(void.class);
    }

    public static void testInt() throws SecurityException, InstantiationException {
        final Unsafe unsafe = getUnsafe();
        unsafe.allocateInstance(int.class);
    }

    static Unsafe getUnsafe() {
        try {
            final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return (Unsafe) unsafeField.get(null);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    @Test
    public void run0() throws Throwable {
        runTest("testInstance");
    }

    @Test
    public void run1() throws Throwable {
        runTest("testArray");
    }

    @Test
    public void run2() throws Throwable {
        runTest("testAbstract");
    }

    @Test
    public void run3() throws Throwable {
        runTest("testInterface");
    }

    @Test
    public void run4() throws Throwable {
        runTest("testClass");
    }

    @Test
    public void run5() throws Throwable {
        runTest("testVoid");
    }

    @Test
    public void run6() throws Throwable {
        runTest("testInt");
    }
}
