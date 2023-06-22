/*
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.micro;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class BigVirtualParams01 extends JTTTest {

    public static String test(boolean b, String p0, String p1, String p2, String p3, String p4, String p5, String p6, String p7, String p8, String p9) {
        I i = b ? new A() : new B();
        return i.test(p0, p1, p2, p3, p4, p5, p6, p7, p8, p9);
    }

    abstract static class I {

        abstract String test(String p0, String p1, String p2, String p3, String p4, String p5, String p6, String p7, String p8, String p9);
    }

    static class A extends I {

        @Override
        public String test(String p0, String p1, String p2, String p3, String p4, String p5, String p6, String p7, String p8, String p9) {
            return "A" + p0 + p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9;
        }
    }

    static class B extends I {

        @Override
        public String test(String p0, String p1, String p2, String p3, String p4, String p5, String p6, String p7, String p8, String p9) {
            return "B" + p0 + p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9;
        }
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", true, "0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", false, "0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
    }

}
