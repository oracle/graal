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
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactSplitNode;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;
import jdk.vm.ci.meta.DeoptimizationReason;

public class OptimizeExactMathTest extends GraalCompilerTest {

    /*
     * Loop where both counter and value are exact operations. Only the result counter may overflow,
     * there we need to keep the exact semantics
     */
    public static int snippet01(int a) {
        int result = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(1000D, i < a); i = Math.addExact(i, 1)) {
            result = Math.addExact(result, i);
        }
        return result;
    }

    /*
     * Loop where both counter and value are exact operations. Both can overflow, however, we can
     * get rid of the exact index increment if we add a loop limit deopt before the loop.
     */
    public static int snippet02(int start, int end) {
        int result = 0;
        for (int i = start; GraalDirectives.injectIterationCount(1000D, i < end); i = Math.addExact(i, 4)) {
            result = Math.addExact(result, i);
        }
        return result;
    }

    /**
     * Loop where the result of the exact math call is not used meaning we only need to keep the
     * logic node in the graph and deopt with a loop limit check or create the split exact node.
     */
    public static int snippet03(int start, int end) {
        int result = 0;
        for (int i = start; GraalDirectives.injectIterationCount(1000D, i < end); i += 4) {
            Math.addExact(i, 4);
            result++;
        }
        return result;
    }

    private StructuredGraph getAfterMidTier(String snippet) {
        return getAfterMidTier(snippet, false);
    }

    private StructuredGraph getAfterMidTier(String snippet, boolean partialUnroll) {
        // we want to disable the removal of empty loops in order ot properly check the number of
        // add operations in a graph
        OptionValues options = new OptionValues(getInitialOptions(), VectorIntrinsics.Options.Vectorization, false);
        options = new OptionValues(options, GraalOptions.LoopPeeling, false);
        options = new OptionValues(options, GraalOptions.PartialUnroll, partialUnroll);
        Suites s = super.createSuites(options);

        StructuredGraph g = parseEager(getResolvedJavaMethod(snippet), AllowAssumptions.NO, options);
        s.getHighTier().apply(g, getDefaultHighTierContext());
        s.getMidTier().apply(g, getDefaultMidTierContext());
        return g;
    }

    @Test
    public void test1() {
        StructuredGraph g = getAfterMidTier("snippet01");
        Assert.assertEquals(1, g.getNodes().filter(IntegerAddExactSplitNode.class).count());
        Assert.assertEquals(3, g.getNodes().filter(AddNode.class).count());
        test("snippet01", 0);
        test("snippet01", 1);
        test("snippet01", -1);
        test("snippet01", 10);
        test("snippet01", Integer.MAX_VALUE);
        test("snippet01", Integer.MIN_VALUE);
    }

    @Test
    public void test2() {
        StructuredGraph g = getAfterMidTier("snippet02");
        Assert.assertEquals(1, g.getNodes().filter(IntegerAddExactSplitNode.class).count());
        Assert.assertEquals(1, g.getNodes().filter(AddNode.class).count());
        Assert.assertEquals(1, g.getNodes().filter(x -> {
            if (x instanceof DeoptimizeNode) {
                DeoptimizeNode deopt = (DeoptimizeNode) x;
                return deopt.getReason() == DeoptimizationReason.LoopLimitCheck;
            }
            return false;
        }).count());
        test("snippet02", 0, 0);
        test("snippet02", 0, 1);
        test("snippet02", 1, 0);
        test("snippet02", -1, 1);
        test("snippet02", 0, 10);
        test("snippet02", 0, -10);
        test("snippet02", 0, Integer.MAX_VALUE);
        test("snippet02", Integer.MAX_VALUE, 0);
        test("snippet02", Integer.MIN_VALUE, Integer.MIN_VALUE);
        test("snippet02", Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
    }

    @Test
    public void test3() {
        StructuredGraph g = getAfterMidTier("snippet03");
        Assert.assertEquals(0, g.getNodes().filter(IntegerAddExactSplitNode.class).count());
        Assert.assertEquals(5, g.getNodes().filter(AddNode.class).count());
        test("snippet02", 0, 0);
        test("snippet02", 0, 1);
        test("snippet02", 1, 0);
        test("snippet02", -1, 1);
        test("snippet02", 0, 10);
        test("snippet02", 0, -10);
        test("snippet02", 0, Integer.MAX_VALUE);
        test("snippet02", Integer.MAX_VALUE, 0);
        test("snippet02", Integer.MIN_VALUE, Integer.MIN_VALUE);
        test("snippet02", Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
    }

    public static int snippet04(int a) {
        int result = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(100000D, i < a); i++) {
            result = Math.addExact(result, 1);
        }
        return result;
    }

    @Test
    public void test4() {
        StructuredGraph g1 = getAfterMidTier("snippet04", true);
        Assert.assertEquals(0, g1.getNodes().filter(IntegerAddExactSplitNode.class).count());
        test("snippet04", 0);
        test("snippet04", 1);
        test("snippet04", -1);
        test("snippet04", 10);
        test("snippet04", -10);
        test("snippet04", Integer.MAX_VALUE);
        test("snippet04", Integer.MIN_VALUE);
        test("snippet04", Integer.MAX_VALUE - 1);
    }
}
