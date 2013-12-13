/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;

/**
 * Tests any optimization that commons loads of non-inlineable constants.
 */
public class CommonedConstantsTest extends GraalCompilerTest {

    public static final String[] array = {"1", "2", null};

    // A method where a constant is used on the normal and exception edge of a non-inlined call.
    // The dominating block of both usages is the block containing the call.
    public static Object testSnippet(String[] arr, int i) {
        Object result = null;
        try {
            result = Array.get(arr, i);
        } catch (ArrayIndexOutOfBoundsException e) {
            result = array[0];
        }
        if (result == null) {
            result = array[2];
        }
        return result;
    }

    @Test
    public void test0() {
        // Ensure the exception path is profiled
        ResolvedJavaMethod javaMethod = getMetaAccess().lookupJavaMethod(getMethod("testSnippet"));
        javaMethod.reprofile();
        testSnippet(array, array.length);

        test("testSnippet", array, 0);
        test("testSnippet", array, 2);
        test("testSnippet", array, 3);
        test("testSnippet", array, 1);
    }
}
