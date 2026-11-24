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
import jdk.graal.compiler.loop.phases.LoopUnswitchingPhase;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;

public class LoopUnswitchBudgetTest extends GraalCompilerTest {

    public static int invariantBranch(int n, int value) {
        // With a frequency boost of 40, this count overflows the int size budget.
        int result = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(6e7, i < n); i++) {
            if (GraalDirectives.injectBranchProbability(0.5, value > 0)) {
                result += i;
            } else {
                result -= i;
            }
            GraalDirectives.sideEffect(i);
        }
        return result;
    }

    @Test
    public void testUnswitchingRespectsMaximumIncreaseOnOverflow() {
        for (int maximumIncrease : new int[]{1, 1000}) {
            OptionValues options = new OptionValues(getInitialOptions(), DefaultLoopPolicies.Options.LoopUnswitchMaxIncrease, maximumIncrease,
                            DefaultLoopPolicies.Options.LoopUnswitchFrequencyBoost, 40.0, GraalOptions.MaximumDesiredSize, 20_000);
            StructuredGraph graph = parseEager("invariantBranch", AllowAssumptions.NO, options);
            new DisableOverflownCountedLoopsPhase().apply(graph);
            CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
            canonicalizer.apply(graph, getDefaultHighTierContext());
            Assert.assertEquals(1, graph.getNodes(LoopBeginNode.TYPE).count());
            new LoopUnswitchingPhase(new DefaultLoopPolicies(), canonicalizer).apply(graph, getDefaultHighTierContext());
            Assert.assertEquals("Loop count with maximum increase " + maximumIncrease,
                            maximumIncrease == 1 ? 1 : 2, graph.getNodes(LoopBeginNode.TYPE).count());
        }
    }
}
