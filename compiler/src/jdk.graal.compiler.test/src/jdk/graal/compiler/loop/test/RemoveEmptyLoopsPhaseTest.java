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
package jdk.graal.compiler.loop.test;

import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.TransferToInterpreter;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.graal.compiler.vector.phases.RemoveEmptyLoopsPhase;
import jdk.vm.ci.meta.SpeculationLog;

public class RemoveEmptyLoopsPhaseTest extends GraalCompilerTest {

    public static int emptyLoopSnippet(int limit, int stop) {
        int i = 0;
        while (i < limit) {
            i++;
        }
        return i + stop;
    }

    private StructuredGraph emptyLoop() {
        StructuredGraph graph = parseEager("emptyLoopSnippet", AllowAssumptions.NO);
        createCanonicalizerPhase().apply(graph, getProviders());
        Assert.assertEquals(1, graph.getNodes(LoopBeginNode.TYPE).count());
        return graph;
    }

    private GuardNode addGuard(StructuredGraph graph, ValueNode value, AnchoringNode anchor) {
        return graph.addOrUniqueWithInputs(new GuardNode(IntegerEqualsNode.create(value, graph.getParameter(1), NodeView.DEFAULT),
                        anchor, TransferToInterpreter, InvalidateReprofile, true, SpeculationLog.NO_SPECULATION, null));
    }

    private void removeEmptyLoops(StructuredGraph graph) {
        new RemoveEmptyLoopsPhase(createCanonicalizerPhase()).apply(graph, getProviders());
        Assert.assertTrue(graph.verify());
        Assert.assertTrue(GraphOrder.assertSchedulableGraph(graph));
    }

    private void checkLoopVariantGuard(boolean anchorBeforeLoop) {
        StructuredGraph graph = emptyLoop();
        LoopBeginNode loopBegin = graph.getNodes(LoopBeginNode.TYPE).first();
        ValuePhiNode index = loopBegin.phis().filter(ValuePhiNode.class).first();
        FrameState state = loopBegin.stateAfter();
        GuardNode guard = addGuard(graph, index, anchorBeforeLoop ? graph.start() : loopBegin);
        Assert.assertTrue(guard.hasNoUsages());
        removeEmptyLoops(graph);
        Assert.assertTrue("A loop-variant floating guard prevents removal", loopBegin.isAlive());
        Assert.assertTrue("The guard must retain the iteration value", index.isAlive());
        Assert.assertSame(state, loopBegin.stateAfter());
        Assert.assertTrue(guard.isAlive());
    }

    @Test
    public void testLoopHeaderGuard() {
        checkLoopVariantGuard(false);
    }

    @Test
    public void testPreheaderGuard() {
        checkLoopVariantGuard(true);
    }

    @Test
    public void testEmptyLoop() {
        StructuredGraph graph = emptyLoop();
        removeEmptyLoops(graph);
        Assert.assertFalse("Unguarded empty loops must still be removed", graph.hasLoops());
    }

    @Test
    public void testInvariantPreheaderGuard() {
        StructuredGraph graph = emptyLoop();
        GuardNode guard = addGuard(graph, graph.getParameter(0), graph.start());
        removeEmptyLoops(graph);
        Assert.assertFalse("An invariant guard outside the loop does not prevent removal", graph.hasLoops());
        Assert.assertTrue(guard.isAlive());
    }

    public static int scanSnippet(byte[] buffer) {
        for (int i = 0; i < buffer.length; i++) {
            if (buffer[i] == ':') {
                GraalDirectives.deoptimizeAndInvalidate();
                return i;
            }
        }
        return -1;
    }

    @Test
    public void testFloatingReadGuard() {
        StructuredGraph graph = parseEager("scanSnippet", AllowAssumptions.NO);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        new ConvertDeoptimizeToGuardPhase(canonicalizer).apply(graph, getDefaultHighTierContext());
        new HighTierLoweringPhase(canonicalizer).apply(graph, getProviders());
        new FloatingReadPhase(canonicalizer).apply(graph, getProviders());
        Assert.assertEquals(1, graph.getNodes(LoopBeginNode.TYPE).count());
        LoopBeginNode loopBegin = graph.getNodes(LoopBeginNode.TYPE).first();
        Assert.assertTrue(graph.getNodes().filter(FloatingReadNode.class).isNotEmpty());
        Assert.assertTrue(graph.getNodes(GuardNode.TYPE).filter(guard -> ((GuardNode) guard).getReason() == TransferToInterpreter).isNotEmpty());
        // Model a preheader anchor with a loop-variant read address.
        for (GuardNode guard : graph.getNodes(GuardNode.TYPE)) {
            guard.setAnchor(graph.start());
        }
        removeEmptyLoops(graph);
        Assert.assertTrue("The read and guard must not collapse to the last index", loopBegin.isAlive());
    }

    @Override
    protected Suites createSuites(OptionValues options) {
        Suites suites = super.createSuites(options);
        // Exercise the phase before guard lowering, as in the failing pipeline.
        suites.getMidTier().findPhase(FloatingReadPhase.class, true).add(new RemoveEmptyLoopsPhase(createCanonicalizerPhase()));
        return suites;
    }

    @Test
    public void testScanExecution() {
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.FullUnroll, false, GraalOptions.LoopPeeling, false, GraalOptions.PartialUnroll, false);
        for (String input : new String[]{"Manifest-Version: 1.0", ":first", "last:", "no colon", ""}) {
            test(options, "scanSnippet", input.getBytes(StandardCharsets.UTF_8));
        }
    }
}
