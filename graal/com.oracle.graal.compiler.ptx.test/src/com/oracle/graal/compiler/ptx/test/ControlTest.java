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

import org.junit.*;

import java.lang.reflect.Method;

public class ControlTest extends PTXTestBase {

    @Ignore
    @Test
    public void testControl() {
        compile("testSwitch1I");
        compile("testStatic");
        compile("testCall");
        compile("testLookupSwitch1I");
    }

    public static int testSwitch1I(int a) {
        switch (a) {
            case 1:
                return 2;
            case 2:
                return 3;
            default:
                return 4;
        }
    }

    public static int testLookupSwitch1I(int a) {
        switch (a) {
            case 0:
                return 1;
            case 1:
                return 2;
            case 2:
                return 3;
            case 3:
                return 1;
            case 4:
                return 2;
            case 5:
                return 3;
            case 6:
                return 1;
            case 7:
                return 2;
            case 8:
                return 3;
            case 9:
                return 1;
            case 10:
                return 2;
            case 11:
                return 3;
            default:
                return -1;
        }
    }

    @SuppressWarnings("unused") private static Object foo = null;

    public static boolean testStatic(Object o) {
        foo = o;
        return true;
    }

    private static int method(int a, int b) {
        return a + b;
    }

    public static int testCall(@SuppressWarnings("unused") Object o, int a, int b) {
        return method(a, b);
    }

    public static void main(String[] args) {
        ControlTest test = new ControlTest();
        for (Method m : ControlTest.class.getMethods()) {
            String name = m.getName();
            if (m.getAnnotation(Test.class) == null && name.startsWith("test")) {
                // CheckStyle: stop system..print check
                System.out.println(name + ": \n" + new String(test.compile(name).getTargetCode()));
                // CheckStyle: resume system..print check
            }
        }
    }
}
