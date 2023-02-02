/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Test;

public class SubCanonicalizationTest extends GraalCompilerTest {

    @Test
    public void testSnippet0() {
        test("snippet0", 0, 0);
        test("snippet0", 0, 0xFFFFFFFF);
        test("snippet0", 0xFFFF0000, 0xFFFFFFFF);
        test("snippet0", 0xFFFF0000, 0x0000FFFF);
    }

    static int snippet0(int a, int b) {
        return (a | b) - (a ^ b);
    }

    @Test
    public void testSnippet1() {
        test("snippet1", 0L, 0L);
        test("snippet1", 0L, 0xFFFFFFFFFFFFFFFFL);
        test("snippet1", 0xFFFFFFFF00000000L, 0xFFFFFFFFFFFFFFFFL);
        test("snippet1", 0xFFFFFFFF00000000L, 0x00000000FFFFFFFFFL);
    }

    static long snippet1(long a, long b) {
        return (a | b) - (a ^ b);
    }
}
