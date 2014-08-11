/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.test;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.compiler.test.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.common.*;

/**
 * Tests compilation of a hot exception handler.
 */
public class CompiledExceptionHandlerTest extends GraalCompilerTest {

    public CompiledExceptionHandlerTest() {
        getSuites().getHighTier().findPhase(AbstractInliningPhase.class).remove();
    }

    @Override
    protected StructuredGraph parseEager(Method m) {
        StructuredGraph graph = super.parseEager(m);
        int handlers = graph.getNodes().filter(ExceptionObjectNode.class).count();
        Assert.assertEquals(1, handlers);
        return graph;
    }

    private static void raiseException(String s) {
        throw new RuntimeException(s);
    }

    @Test
    public void test1() {
        // Ensure the profile shows a hot exception
        for (int i = 0; i < 10000; i++) {
            test1Snippet("");
            test1Snippet(null);
        }

        test("test1Snippet", "a string");
        test("test1Snippet", (String) null);
    }

    public static String test1Snippet(String message) {
        if (message != null) {
            try {
                raiseException(message);
            } catch (Exception e) {
                return message + e.getMessage();
            }
        }
        return null;
    }

    private static void raiseException(String m1, String m2, String m3, String m4, String m5) {
        throw new RuntimeException(m1 + m2 + m3 + m4 + m5);
    }

    @Test
    public void test2() {
        // Ensure the profile shows a hot exception
        for (int i = 0; i < 10000; i++) {
            test2Snippet("m1", "m2", "m3", "m4", "m5");
            test2Snippet(null, "m2", "m3", "m4", "m5");
        }

        test("test2Snippet", "m1", "m2", "m3", "m4", "m5");
        test("test2Snippet", null, "m2", "m3", "m4", "m5");
    }

    public static String test2Snippet(String m1, String m2, String m3, String m4, String m5) {
        if (m1 != null) {
            try {
                raiseException(m1, m2, m3, m4, m5);
            } catch (Exception e) {
                return m5 + m4 + m3 + m2 + m1;
            }
        }
        return m4 + m3;
    }

    @Test
    public void test3() {
        // Ensure the profile shows a hot exception
        for (int i = 0; i < 10000; i++) {
            test3Snippet("object1", "object2");
            test3Snippet(null, "object2");
        }

        test("test3Snippet", (Object) null, "object2");
        test("test3Snippet", "object1", "object2");
    }

    public static String test3Snippet(Object o, Object o2) {
        try {
            return o.toString();
        } catch (NullPointerException e) {
            return String.valueOf(o2);
        }
    }
}
