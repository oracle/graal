/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.hotspot;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

//@formatter:off

/**
 * int / long arithmetic seems to be broken in 1.6.0_14 HotSpot Server VM (Win XP).
 *
 * @test
 * @bug 6850611
 * @run main Test6850611
 */

public class Test6850611 extends JTTTest {

    public static int test() {
        // for (int j = 0; j < 5; ++j) {
        long x = 0;
        for (int i = Integer.MIN_VALUE; i < Integer.MAX_VALUE; ++i) {
            x += i;
        }
        if (x != -4294967295L) {
            return 97;
        }
        // }
        return 95;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }

}
