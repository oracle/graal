/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.test;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;

public class OpaqueNodeTest extends GraalCompilerTest {

    public static byte[] arr = new byte[10];

    public static void writeConstSnippet() {
        final int i = 300;
        int j = GraalDirectives.opaque(i);
        arr[0] = (byte) j;
    }

    @Test
    public void testWriteConst() {
        test("writeConstSnippet");
        assertTrue(arr[0] == (byte) 300);
    }

    public static void writeNegConstSnippet() {
        final int i = -300;
        int j = GraalDirectives.opaque(i);
        arr[0] = (byte) j;
    }

    @Test
    public void testNegConstWrite() {
        test("writeNegConstSnippet");
        assertTrue(arr[0] == (byte) -300);
    }

    public static int fld = 200;

    public static void writeSnippet() {
        int i = fld + 100;
        int j = GraalDirectives.opaque(i);
        arr[0] = (byte) j;
    }

    @Test
    public void testWrite() {
        test("writeSnippet");
        assertTrue(arr[0] == (byte) 300);
    }

    public static void writeNegSnippet() {
        int i = fld - 500;
        int j = GraalDirectives.opaque(i);
        arr[0] = (byte) j;
    }

    @Test
    public void testNegWrite() {
        test("writeNegSnippet");
        assertTrue(arr[0] == (byte) -300);
    }

    public static void opaqueClassSnippet() {
        /*
         * GR-51558: This would cause an assertion failure in LIR constant load optimization if the
         * opaque is not removed.
         */
        Class<?> c = GraalDirectives.opaque(Object.class);
        if (c.getResource("resource.txt") == null) {
            GraalDirectives.deoptimize();
        }
    }

    @Test
    public void testOpaqueClass() {
        test("opaqueClassSnippet");
    }
}
