/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;

public class TestNewInstanceWithExceptionNoKillAny extends GraalCompilerTest {

    static Object o;

    public static int snippet01() {
        try {
            o = new Object();
            return 1;
        } catch (OutOfMemoryError e) {
            // do nothing
            return 0;
        }
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        super.checkHighTierGraph(graph);
        Assert.assertEquals(1, graph.getNodes().filter(WithExceptionNode.class).count());
        Assert.assertEquals(1, graph.getNodes().filter(ExceptionObjectNode.class).count());
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        super.checkMidTierGraph(graph);
        Assert.assertEquals(1, graph.getNodes().filter(WithExceptionNode.class).count());
        Assert.assertEquals(1, graph.getNodes().filter(ExceptionObjectNode.class).count());
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        super.checkLowTierGraph(graph);
        Assert.assertEquals(1, graph.getNodes().filter(WithExceptionNode.class).count());
        Assert.assertEquals(0, graph.getNodes().filter(ExceptionObjectNode.class).count());
    }

    @Test
    public void test01() {
        test("snippet01");
    }

    public static void snippet02() {
        GraalDirectives.sideEffect(); // kill any here
        try {
            o = new Object();
        } catch (OutOfMemoryError e) {
            // do nothing
        }
        GraalDirectives.sideEffect(); // kill any here
    }

    @Test
    public void test02() {
        test("snippet02");
    }

    static int field;

    public static void snippet03() {
        try {
            o = new Object();
        } catch (OutOfMemoryError e) {
            // do nothing
            field = 123;
        }
        int val = field;
        GraalDirectives.sideEffect(val); // kill any here
    }

    @Test
    public void test03() {
        test("snippet03");
    }

    public static void snippet04() {
        field = 123;
        try {
            o = new Object();
        } catch (OutOfMemoryError e) {
            // do nothing
            GraalDirectives.sideEffect(field); // kill any here
        }
        GraalDirectives.sideEffect(field); // kill any here
    }

    @Test
    public void test04() {
        test("snippet04");
    }
}
