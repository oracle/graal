/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.ea;

import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.PointerEqualsNode;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;

public class LowTierReadEliminationTest extends GraalCompilerTest {

    @Test
    public void testIsNullCanonicalization() {
        Suites suites = createSuites(getInitialOptions()).copy();
        StructuredGraph graph = parseEager("isNullCanonicalizationSnippet", AllowAssumptions.YES);

        PhaseSuite<HighTierContext> highTier = suites.getHighTier();
        highTier.removeSubTypePhases(ReadEliminationPhase.class);
        highTier.removeSubTypePhases(PartialEscapePhase.class);
        highTier.apply(graph, getDefaultHighTierContext());
        PhaseSuite<MidTierContext> midTier = suites.getMidTier();
        midTier.removeSubTypePhases(ReadEliminationPhase.class);
        midTier.removeSubTypePhases(FloatingReadPhase.class);
        midTier.apply(graph, getDefaultMidTierContext());

        GraalError.guarantee(graph.getNodes().filter(PointerEqualsNode.class).isNotEmpty(), "A PointerEqualsNode should be in the graph at this point");
        suites.getLowTier().apply(graph, getDefaultLowTierContext());

        assertTrue(graph.getNodes().filter(PointerEqualsNode.class).isEmpty());
        assertTrue(graph.getNodes().filter(IsNullNode.class).isNotEmpty());
    }

    public static Object field = Integer.valueOf(0);

    public static Object isNullCanonicalizationSnippet() {
        Object object = field;
        field = null;
        if (object == field) {
            return object;
        }
        return field;
    }
}
