/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
/*
 */
package com.oracle.graal.jtt.lang;

import com.oracle.graal.test.*;
import com.oracle.graal.jtt.*;

public final class Class_forName01 extends JTTTest {

    public static String test(int i) throws ClassNotFoundException {
        if (i == 0) {
            return Class.forName("java.lang.Object").toString();
        }
        if (i == 1) {
            return Class.forName("java.lang.String").toString();
        }
        if (i == 2) {
            return Class.forName("com.oracle.graal.jtt.lang.Class_forName01").toString();
        }
        if (i == 3) {
            return Class.forName("xyxzz.xyzyzyz.XXYYY").toString();
        }
        return null;
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

    @LongTest
    public void run3() throws Throwable {
        runTest("test", 3);
    }

    @LongTest
    public void run4() throws Throwable {
        runTest("test", 4);
    }

}
