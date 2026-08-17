/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.FloatingIntegerDivRemNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.OptimizeDivPhase;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class OptimizeDivTest extends GraalCompilerTest {

    private void checkFloatingDivRemByConstantOptimization(ResolvedJavaMethod method, int floatingDivRemsBeforeOptimization, int floatingDivRemsAfterOptimization) {
        StructuredGraph graph = parseEager(method, StructuredGraph.AllowAssumptions.YES);
        Suites suites = super.createSuites(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.EarlyGVN, false));
        suites.getHighTier().apply(graph, getDefaultHighTierContext());

        Assert.assertEquals(floatingDivRemsBeforeOptimization, graph.getNodes().filter(FloatingIntegerDivRemNode.class).count());
        new OptimizeDivPhase(createCanonicalizerPhase()).apply(graph, getProviders());
        Assert.assertEquals(floatingDivRemsAfterOptimization, graph.getNodes().filter(FloatingIntegerDivRemNode.class).count());
    }

    @Test
    public void testFloatingRemByConstant() {
        checkFloatingDivRemByConstantOptimization(getResolvedJavaMethod(FloatingDivTest.class, "snippet01"), 2, 0);
    }

    @Test
    public void testFloatingDivByConstant() {
        checkFloatingDivRemByConstantOptimization(getResolvedJavaMethod(FloatingDivTest.class, "snippet09"), 2, 0);
    }

    public static int unsignedDivByNonCanonical1Snippet() {
        int a = GraalDirectives.opaque(0);
        int b = ~Integer.numberOfTrailingZeros(a);  // = -33
        int c = Integer.divideUnsigned(b, -5160);  // = 1
        int d = Integer.divideUnsigned(56133, c);
        return d;
    }

    @Test
    public void unsignedDivByNonCanonical1() {
        test("unsignedDivByNonCanonical1Snippet");
    }

    public static int signedDivByNonCanonical1Snippet() {
        int a = GraalDirectives.opaque(0);
        int b = ~Integer.numberOfTrailingZeros(a);  // = -33
        int c = Integer.divideUnsigned(b, -5160);  // = 1
        int d = 56133 / c;
        return d;
    }

    @Test
    public void signedDivByNonCanonical1() {
        test(new OptionValues(getInitialOptions(), GraalOptions.FloatingDivNodes, false), "signedDivByNonCanonical1Snippet");
    }
}
