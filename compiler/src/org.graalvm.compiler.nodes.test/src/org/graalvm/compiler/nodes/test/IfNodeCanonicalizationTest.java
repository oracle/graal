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
package org.graalvm.compiler.nodes.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * A few tests of expected simplifications by {@link IfNode#simplify(SimplifierTool)}.
 */
public class IfNodeCanonicalizationTest extends GraalCompilerTest {

    static int value;

    static final byte[] testValues = {-128, -1, 0, 1, 127};

    @Test
    public void test1() {
        /*
         * exercise conversion of x - y < 0 into x < y, both by checking expected graph shape and
         * that the transformed code produces the right answer.
         */
        test("testSnippet1", SubNode.class, 0);
        byte[] values = new byte[4];
        for (byte a : testValues) {
            values[0] = a;
            for (byte b : testValues) {
                values[1] = b;
                for (byte c : testValues) {
                    values[2] = c;
                    for (byte d : testValues) {
                        values[3] = d;
                        value = 2;
                        super.test("testSnippet1", values);
                    }
                }
            }
        }
    }

    public int testSnippet1(byte[] values) {
        int v = values[0] - values[1];
        if (v < 0) {
            value = 2;
        }
        v = values[3] - values[2];
        if (v < 0) {
            value = 1;
        }
        return value;
    }

    @Test
    public void test2() {
        test("testSnippet2", 1);
    }

    public boolean testSnippet2(int a, int[] limit) {
        int l = limit.length;
        if (!(a >= 0 && a < l)) {
            value = a;
            return true;
        }
        return false;
    }

    @Ignore("currently not working because swapped case isn't handled")
    @Test
    public void test3() {
        test("testSnippet3", 1);
    }

    public boolean testSnippet3(int a, int[] limit) {
        int l = limit.length;
        if (a < l && a >= 0) {
            value = 9;
            return true;
        }
        return false;
    }

    @Test
    public void test4() {
        test("testSnippet4", 1);
    }

    public boolean testSnippet4(int a, int[] limit) {
        int l = limit.length;
        if (a < 0) {
            GraalDirectives.deoptimize();
        }
        if (a >= l) {
            GraalDirectives.deoptimize();
        }
        return true;
    }

    @Ignore("Reversed conditions aren't working with guards")
    @Test
    public void test5() {
        test("testSnippet5", 1);
    }

    public boolean testSnippet5(int a, int[] limit) {
        int l = limit.length;
        if (a >= l) {
            GraalDirectives.deoptimize();
        }
        if (a < 0) {
            GraalDirectives.deoptimize();
        }
        return true;
    }

    public void test(String name, int logicCount) {
        test(name, LogicNode.class, logicCount);
    }

    public void test(String name, Class<? extends Node> expectedClass, int expectedCount) {
        StructuredGraph graph = parseEager(name, AllowAssumptions.YES);

        CoreProviders context = getProviders();
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        new ConvertDeoptimizeToGuardPhase().apply(graph, context);
        graph.clearAllStateAfterForTestingOnly();
        graph.setGuardsStage(StructuredGraph.GuardsStage.AFTER_FSA);
        canonicalizer.apply(graph, context);

        new IterativeConditionalEliminationPhase(canonicalizer, true).apply(graph, context);
        canonicalizer.apply(graph, context);
        canonicalizer.apply(graph, context);

        Assert.assertEquals(expectedCount, graph.getNodes().filter(expectedClass).count());
    }
}
