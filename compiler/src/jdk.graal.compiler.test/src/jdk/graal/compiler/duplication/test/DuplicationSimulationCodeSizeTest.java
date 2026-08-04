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
package jdk.graal.compiler.duplication.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationPhase;
import jdk.graal.compiler.duplication.phases.simulation.FixedDuplicationSimulationConfig;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;

public class DuplicationSimulationCodeSizeTest extends GraalCompilerTest {

    public static int intSideEffect;

    public static int ceIfSnippet(int a, int b) {
        if (a > 0) {
            int phi = 0;
            if (b == 0) {
                intSideEffect = a;
                phi = a;
            } else {
                intSideEffect = b;
                phi = 0;
            }// should be duplicated, ce opportunity, no cost
            if (phi == a) {
                intSideEffect = a;
            } else {
                intSideEffect = b;
            }
        }
        GraalDirectives.controlFlowAnchor();
        return a * b;
    }

    @Test
    public void test01() {
        // must not need any budget for this duplication
        OptionValues options = new OptionValues(getInitialOptions(), DuplicationOptions.DuplicationBudgetFactor, 0D, DuplicationOptions.DuplicationMinBranchFrequency, 0.01D,
                        DuplicationOptions.DuplicationCostReductionFactor, 0);
        StructuredGraph g = parseEager("ceIfSnippet", AllowAssumptions.NO, options);
        duplicate(g);

        // check that the if is gone as it did not need any budget
        Assert.assertEquals(2, g.getNodes(IfNode.TYPE).count());
    }

    private void duplicate(StructuredGraph graph) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope _ = debug.scope("SimualteDCE", graph)) {
            new DisableOverflownCountedLoopsPhase().apply(graph);
            CoreProviders context = getProviders();
            CanonicalizerPhase canonicalizer = this.createCanonicalizerPhase();
            canonicalizer.apply(graph, context);
            new DuplicationPhase(FixedDuplicationSimulationConfig.defaultForDepth(16), true, true, DuplicationPhase.FACTORS_INCLUDING_PEA, canonicalizer,
                            graph.getOptions()).apply(graph, context);
            new ConditionalEliminationPhase(canonicalizer, false).apply(graph, context);
        } catch (Throwable t) {
            debug.handle(t);
        }
    }
}
