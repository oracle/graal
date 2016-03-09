/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Random;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;

@Ignore("Convert into a microbenchmark!")
public class JavaInteropSpeedTest {
    private static final int REPEAT = 10000;
    private static int[] arr;
    private static long javaTime;
    private static long interopTime;

    @BeforeClass
    public static void beforeTesting() {
        InstrumentationTestMode.set(true);
        arr = initArray(REPEAT);
        for (int i = 0; i < 1000; i++) {
            JavaInteropSpeedTest t = new JavaInteropSpeedTest();
            t.doMinMaxInJava();
            t.doMinMaxWithInterOp();
            t.assertSame();
        }
    }

    @AfterClass
    public static void after() {
        InstrumentationTestMode.set(false);
    }

    private int mmInOp;
    private int mmInJava;

    @Test
    public void doMinMaxInJava() {
        int max = 0;
        long now = System.currentTimeMillis();
        for (int i = 0; i < arr.length; i++) {
            max = Math.max(arr[i], max);
        }
        javaTime = System.currentTimeMillis() - now;
        mmInJava = max;
    }

    private void assertSame() {
        assertEquals(mmInJava, mmInOp);
    }

    private static final TruffleObject TRUFFLE_MAX = JavaInterop.asTruffleFunction(IntBinaryOperation.class, new IntBinaryOperation() {
        @Override
        public int compute(int a, int b) {
            return Math.max(a, b);
        }
    });
    private static final IntBinaryOperation MAX = JavaInterop.asJavaFunction(IntBinaryOperation.class, TRUFFLE_MAX);

    @Test
    public void doMinMaxWithInterOp() {
        int max = 0;
        long now = System.currentTimeMillis();
        for (int i = 0; i < arr.length; i++) {
            max = MAX.compute(arr[i], max);
        }
        interopTime = System.currentTimeMillis() - now;
        mmInOp = max;
    }

    @AfterClass
    public static void nonSignificanDifference() {
        if (javaTime < 1) {
            javaTime = 1;
        }
        if (interopTime > 10 * javaTime) {
            fail("Interop took too long: " + interopTime + " ms, while java only " + javaTime + " ms");
        }
    }

    private static int[] initArray(int size) {
        Random r = new Random();
        int[] tmp = new int[size];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = r.nextInt(100000);
        }
        return tmp;
    }
}
