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

import static org.junit.Assert.*;

import org.junit.*;

/**
 * Test class for small Java methods compiled to PTX kernels.
 */
public class BasicPTXTest extends PTXTest {

    @Test
    public void test() {
        test("testConstI");
    }

    public static int testConstI() {
        return 42;
    }

    @Test
    public void testStaticIntKernel() {
        test("staticIntKernel", 'a', 42);
    }

    public static int staticIntKernel(char p0, int p1) {
        return p1 + p0;
    }

    @Test
    public void testVirtualIntKernel() {
        test("virtualIntKernel", 'a', 42);
    }

    public int virtualIntKernel(char p0, int p1) {
        return p1 + p0;
    }

    @Test
    public void testGetAvailableProcessors() {
        assertTrue(getPTXBackend().getAvailableProcessors() >= 0);
    }

    public static void main(String[] args) {
        compileAndPrintCode(new BasicPTXTest());
    }
}
