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
package jdk.graal.compiler.core.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.extended.OpaqueNode;
import jdk.graal.compiler.nodes.extended.OpaqueValueNode;

public class IntegerTestCanonicalizationTest extends GraalCompilerTest {

    @BytecodeParserNeverInline
    static int integerTest(int a, int b) {
        return (a & b);
    }

    /*
     * In order to avoid canonicalization of other parts of the expression we wrap the x in an
     * opaque node and later remove it during checkHighTier graph.
     */
    @BytecodeParserNeverInline
    static int hideBehindCall(Object p0, Object p1) {
        return GraalDirectives.opaque(p0 == p1 ? 1 : 0);
    }

    static int snippet(Object p0, Object p1) {
        if (integerTest(hideBehindCall(p0, p1), 1) == 0) {
            GraalDirectives.sideEffect(123);
            return 12;
        } else {
            GraalDirectives.sideEffect(111);
            return 13;
        }
    }

    /*
     * In order to avoid canonicalization of other parts of the expression we wrap the x in an
     * opaque node and later remove it during checkHighTier graph.
     */
    @BytecodeParserNeverInline
    static int hideBehindCall2(Object p0, Object p1) {
        return GraalDirectives.opaque(p0 == p1 ? 0 : 1);
    }

    static int snippet2(Object p0, Object p1) {
        if (integerTest(hideBehindCall2(p0, p1), 1) == 0) {
            GraalDirectives.sideEffect(123);
            return 12;
        } else {
            GraalDirectives.sideEffect(111);
            return 13;
        }
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        super.checkHighTierGraph(graph);
        Assert.assertEquals(1, graph.getNodes().filter(IntegerTestNode.class).count());
        for (OpaqueNode opaque : graph.getNodes().filter(OpaqueValueNode.class).snapshot()) {
            opaque.remove();
        }
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        super.checkMidTierGraph(graph);
        Assert.assertEquals(0, graph.getNodes().filter(IntegerTestNode.class).count());
    }

    @Test
    public void test01() {
        test("snippet", "abc", 123);
        test("snippet", "abc", null);
        test("snippet", null, 123);
        Object o = new Object();
        test("snippet", o, o);
        test("snippet", o, null);
        test("snippet", null, o);
        test("snippet", null, null);
    }

    @Test
    public void test02() {
        test("snippet2", "abc", 123);
        test("snippet2", "abc", null);
        test("snippet2", null, 123);
        Object o = new Object();
        test("snippet2", o, o);
        test("snippet2", o, null);
        test("snippet2", null, o);
        test("snippet2", null, null);
    }

}
