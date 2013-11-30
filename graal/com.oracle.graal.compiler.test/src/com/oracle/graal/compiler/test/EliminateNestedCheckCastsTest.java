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
package com.oracle.graal.compiler.test;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

public class EliminateNestedCheckCastsTest extends GraalCompilerTest {

    public static long test1Snippet(A1 a1) {
        A2 a2 = (A2) a1;
        A3 a3 = (A3) a2;
        A4 a4 = (A4) a3;
        A5 a5 = (A5) a4;
        A6 a6 = (A6) a5;
        return a6.x6;
    }

    @Test
    public void test1() {
        compileSnippet("test1Snippet", 5, 1);
    }

    public static long test2Snippet(A1 a1) {
        A2 a2 = (A2) a1;
        A3 a3 = (A3) a2;
        A4 a4 = (A4) a3;
        A5 a5 = (A5) a4;
        A6 a6 = (A6) a5;
        // as a3 has an usage, thus don't remove the A3 checkcast.
        return a3.x2 + a6.x6;
    }

    @Test
    public void test2() {
        compileSnippet("test2Snippet", 5, 1);
    }

    public static long test3Snippet(A1 a1) {
        long result = a1.x1;
        A2 a2 = (A2) a1;
        if (a1.x1 == 42) {
            A3 a3 = (A3) a2;
            result = a3.x3;
        }
        return result;
    }

    @Test
    public void test3() {
        compileSnippet("test3Snippet", 2, 2);
    }

    public static long test4Snippet(A1 a1, A1 b1) {
        A2 a2 = (A2) a1;
        A3 b3 = (A3) b1;
        return a2.x2 + b3.x3;
    }

    @Test
    public void test4() {
        compileSnippet("test4Snippet", 2, 2);
    }

    public static long test5Snippet(A1 a1) {
        long sum = 0;
        A2 a2 = (A2) a1;
        A3 a3 = (A3) a2;
        sum += a2.x2;
        return sum + a3.x3;
    }

    @Test
    public void test5() {
        StructuredGraph graph = compileSnippet("test5Snippet", 2, 1);
        for (LoadFieldNode lfn : graph.getNodes().filter(LoadFieldNode.class)) {
            Assert.assertTrue(lfn.object() instanceof CheckCastNode);
        }
    }

    private StructuredGraph compileSnippet(final String snippet, final int checkcasts, final int afterCanon) {
        final StructuredGraph graph = parse(snippet);
        try (Scope s = Debug.scope("NestedCheckCastsTest", graph)) {
            Debug.dump(graph, "After parsing: " + snippet);
            Assert.assertEquals(checkcasts, graph.getNodes().filter(CheckCastNode.class).count());
            new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), new Assumptions(false)));
            Assert.assertEquals(afterCanon, graph.getNodes().filter(CheckCastNode.class).count());
            return graph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

    }

    public static class A1 {

        public long x1 = 1;
    }

    public static class A2 extends A1 {

        public long x2 = 2;
    }

    public static class A3 extends A2 {

        public long x3 = 3;
    }

    public static class A4 extends A3 {

        public long x4 = 4;
    }

    public static class A5 extends A4 {

        public long x5 = 5;
    }

    public static class A6 extends A5 {

        public long x6 = 6;
    }
}
