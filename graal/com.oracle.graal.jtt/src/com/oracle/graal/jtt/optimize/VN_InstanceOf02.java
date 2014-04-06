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

import com.oracle.graal.jtt.*;
import com.oracle.graal.test.*;

/*
 * Tests value numbering of instanceof operations.
 */
public class VN_InstanceOf02 extends JTTTest {

    private static boolean cond = true;

    static final Object object = new VN_InstanceOf02();

    public static boolean test(int arg) {
        if (arg == 0) {
            return foo1();
        }
        if (arg == 1) {
            return foo2();
        }
        if (arg == 2) {
            return foo3();
        }
        // do nothing
        return false;
    }

    private static boolean foo1() {
        boolean a = object instanceof VN_InstanceOf02;
        if (cond) {
            boolean b = object instanceof VN_InstanceOf02;
            return a | b;
        }
        return false;
    }

    private static boolean foo2() {
        Object obj = new VN_InstanceOf02();
        boolean a = obj instanceof VN_InstanceOf02;
        if (cond) {
            boolean b = obj instanceof VN_InstanceOf02;
            return a | b;
        }
        return false;
    }

    private static boolean foo3() {
        boolean a = null instanceof VN_InstanceOf02;
        if (cond) {
            boolean b = null instanceof VN_InstanceOf02;
            return a | b;
        }
        return false;
    }

    @LongTest
    public void run0() throws Throwable {
        runTest("test", 0);
    }

    @LongTest
    public void run1() throws Throwable {
        runTest("test", 1);
    }

    @LongTest
    public void run2() throws Throwable {
        runTest("test", 2);
    }

}
