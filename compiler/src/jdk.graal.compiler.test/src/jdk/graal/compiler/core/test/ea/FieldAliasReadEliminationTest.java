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
package jdk.graal.compiler.core.test.ea;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.virtual.FieldAliasNode;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;
import jdk.vm.ci.meta.ResolvedJavaField;

public class FieldAliasReadEliminationTest extends GraalCompilerTest {

    static final class State {
        final Object[] references;

        State(Object[] references) {
            this.references = references;
        }
    }

    private static native void memoryKill();

    public static Object[] readAfterCall(State state, Object[] alias) {
        memoryKill();
        return state.references;
    }

    @Test
    public void testImmutableAliasSurvivesCallInReadElimination() throws NoSuchFieldException {
        StructuredGraph graph = graphWithFieldAlias(true);
        HighTierContext context = getDefaultHighTierContext();
        new ReadEliminationPhase(createCanonicalizerPhase()).apply(graph, context);
        Assert.assertSame(graph.getParameter(1), graph.getNodes().filter(ReturnNode.class).first().result());
    }

    @Test
    public void testImmutableAliasSurvivesCallInPartialEscapeAnalysis() throws NoSuchFieldException {
        StructuredGraph graph = graphWithFieldAlias(true);
        HighTierContext context = getDefaultHighTierContext();
        new PartialEscapePhase(false, true, createCanonicalizerPhase(), null, graph.getOptions()).apply(graph, context);
        Assert.assertSame(graph.getParameter(1), graph.getNodes().filter(ReturnNode.class).first().result());
    }

    @Test
    public void testMutableAliasIsKilledByCall() throws NoSuchFieldException {
        StructuredGraph graph = graphWithFieldAlias(false);
        HighTierContext context = getDefaultHighTierContext();
        new PartialEscapePhase(false, true, createCanonicalizerPhase(), null, graph.getOptions()).apply(graph, context);
        Assert.assertTrue(graph.getNodes().filter(ReturnNode.class).first().result() instanceof LoadFieldNode);
    }

    private StructuredGraph graphWithFieldAlias(boolean immutable) throws NoSuchFieldException {
        StructuredGraph graph = parseEager(getResolvedJavaMethod("readAfterCall"), AllowAssumptions.NO);
        ResolvedJavaField referencesField = getMetaAccess().lookupJavaField(State.class.getDeclaredField("references"));
        LoadFieldNode load = graph.getNodes().filter(LoadFieldNode.class).first();
        ParameterNode aliasParameter = graph.addWithoutUnique(new ParameterNode(1, StampPair.createSingle(load.stamp(NodeView.DEFAULT))));
        FieldAliasNode alias = graph.add(new FieldAliasNode(graph.getParameter(0), referencesField, aliasParameter, immutable));
        graph.addAfterFixed(graph.start(), alias);
        return graph;
    }
}
