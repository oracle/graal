/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.ptx.test;

import static com.oracle.graal.lir.ptx.ThreadDimension.*;

import com.oracle.graal.lir.ptx.ParallelOver;
import com.oracle.graal.lir.ptx.Warp;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.Test;

public class ArrayPTXTest extends PTXTestBase {

    @Test
    public void testArray() {
        int[] array1 = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] array2 = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] array3 = {1, 2, 3, 4, 5, 6, 7, 8, 9};

        invoke(compile("testStoreArray1I"), array1, 2);
        if (array1[2] == 42) {
            printReport("testStoreArray1I: " + Arrays.toString(array1) + " PASSED");
        } else {
            printReport("testStoreArray1I: " + Arrays.toString(array1) + " FAILED");
        }

        invoke(compile("testStoreArrayWarp0"), array2, 2);
        printReport("testStoreArrayWarp0: " + Arrays.toString(array2));

        invoke(compile("testStoreArrayWarp1I"), array3, 2);
        printReport("testStoreArrayWarp1I: " + Arrays.toString(array3));

    }

    public static void testStoreArray1I(int[] array, int i) {
        array[i] = 42;
    }

    public static void testStoreArrayWarp0(int[] array, @Warp(dimension = X) int i) {
        array[i] = 42;
    }

    public static void testStoreArrayWarp1I(@ParallelOver(dimension = X) int[] array, @Warp(dimension = X) int i) {
        array[i] = 42;
    }

    public static void main(String[] args) {
        ArrayPTXTest test = new ArrayPTXTest();
        for (Method m : ArrayPTXTest.class.getMethods()) {
            String name = m.getName();
            if (m.getAnnotation(Test.class) == null && name.startsWith("test")) {
                // CheckStyle: stop system..print check
                System.out.println(name + ": \n" + new String(test.compile(name).getTargetCode()));
                // CheckStyle: resume system..print check
            }
        }
    }
}
