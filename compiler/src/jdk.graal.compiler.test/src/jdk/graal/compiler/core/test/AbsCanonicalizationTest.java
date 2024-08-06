/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.AbsNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import org.junit.Assert;
import org.junit.Test;

public class AbsCanonicalizationTest extends GraalCompilerTest {
    public static double absReference(double x) {
        return Math.abs(x);
    }

    public static double absAbs(double x) {
        return Math.abs(Math.abs(x));
    }

    public static double absNegate(double x) {
        return Math.abs(-x);
    }

    @Test
    public void testAbsNegate() {
        StructuredGraph graph = parseEager("absNegate", StructuredGraph.AllowAssumptions.YES);
        createInliningPhase().apply(graph, getDefaultHighTierContext());
        createCanonicalizerPhase().apply(graph, getProviders());

        StructuredGraph referenceGraph = parseEager("absReference", StructuredGraph.AllowAssumptions.YES);
        assertEquals(referenceGraph, graph);
        Assert.assertEquals(0, graph.getNodes().filter(NegateNode.class).count());

        testAgainstExpected(graph.method(), new Result(absNegate(-Double.MAX_VALUE), null), (Object) null, Double.MAX_VALUE);
        testAgainstExpected(graph.method(), new Result(absNegate(0d), null), (Object) null, 0d);
        testAgainstExpected(graph.method(), new Result(absNegate(Double.MAX_VALUE), null), (Object) null, Double.MAX_VALUE);
    }

    @Test
    public void testAbsAbs() {
        StructuredGraph graph = parseEager("absAbs", StructuredGraph.AllowAssumptions.YES);
        createInliningPhase().apply(graph, getDefaultHighTierContext());
        createCanonicalizerPhase().apply(graph, getProviders());

        StructuredGraph referenceGraph = parseEager("absReference", StructuredGraph.AllowAssumptions.YES);
        assertEquals(referenceGraph, graph);
        Assert.assertEquals(1, graph.getNodes().filter(AbsNode.class).count());

        testAgainstExpected(graph.method(), new Result(absAbs(-Double.MAX_VALUE), null), (Object) null, Double.MAX_VALUE);
        testAgainstExpected(graph.method(), new Result(absAbs(0d), null), (Object) null, 0d);
        testAgainstExpected(graph.method(), new Result(absAbs(Double.MAX_VALUE), null), (Object) null, Double.MAX_VALUE);
    }
}
