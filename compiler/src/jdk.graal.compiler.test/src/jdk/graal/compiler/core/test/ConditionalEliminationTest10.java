/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.LoweringPhase;

/**
 * This test checks the combined action of {@link ConditionalEliminationPhase} and
 * {@link LoweringPhase}. The lowering phase needs to introduce the null checks at the correct
 * places for the dominator conditional elimination phase to pick them up.
 */
public class ConditionalEliminationTest10 extends ConditionalEliminationTestBase {

    private static boolean condition1;
    private static boolean condition2;

    private static class TestClass {
        int x;
    }

    @SuppressWarnings("all")
    public static int testSnippet1(TestClass t) {
        int result = 0;
        if (condition1) {
            GraalDirectives.controlFlowAnchor();
            result = t.x;
        }
        GraalDirectives.controlFlowAnchor();
        return result + t.x;
    }

    @Test
    public void test1() {
        test("testSnippet1", 1);
    }

    @SuppressWarnings("all")
    public static int testSnippet2(TestClass t) {
        int result = 0;
        if (condition1) {
            GraalDirectives.controlFlowAnchor();
            result = t.x;
        } else {
            GraalDirectives.controlFlowAnchor();
            result = t.x;
        }

        if (condition2) {
            result = t.x;
            GraalDirectives.controlFlowAnchor();
        }

        return result;
    }

    @Test
    public void test2() {
        test("testSnippet2", 1);
    }

    private void test(String snippet, int guardCount) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        CoreProviders context = getProviders();
        CanonicalizerPhase c = createCanonicalizerPhase();
        new HighTierLoweringPhase(c).apply(graph, context);
        new ConditionalEliminationPhase(c, true).apply(graph, context);
        Assert.assertEquals(guardCount, graph.getNodes().filter(GuardNode.class).count());
    }
}
