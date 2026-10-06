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

import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig;
import jdk.graal.compiler.api.directives.BytecodeInterpreterDirectives.BytecodeInterpreterHandlerConfig.Argument;
import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationPhase;
import jdk.graal.compiler.duplication.phases.simulation.FixedDuplicationSimulationConfig;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.MultiReturnNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchorNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig;
import jdk.graal.compiler.phases.util.BytecodeHandlerStubHelper;
import jdk.graal.compiler.phases.util.BytecodeInterpreterAnnotations;
import jdk.graal.compiler.replacements.GraphKit;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class TailCallDuplicationOpportunityTest extends GraalCompilerTest {

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 1, arguments = {@Argument})
    public static int handler(int value) {
        return value;
    }

    @BytecodeInterpreterHandlerConfig(maximumOperationCode = 1, enableTailDuplication = true, arguments = {@Argument})
    public static int splittingHandler(int value) {
        return value;
    }

    public static int fetchOpcode(int value) {
        return value & 1;
    }

    public static int profiledSnippet(int value) {
        int result;
        if (GraalDirectives.injectBranchProbability(0.5, value == 0)) {
            GraalDirectives.sideEffect(1);
            result = 1;
        } else {
            GraalDirectives.sideEffect(2);
            result = 2;
        }
        return result;
    }

    public static int snippet(int value) {
        int result;
        if (value == 0) {
            result = 1;
        } else {
            result = 2;
        }
        if (value == 42) {
            GraalDirectives.sideEffect(1);
        } else {
            GraalDirectives.sideEffect(2);
        }
        return result;
    }

    public static int integerSwitchSnippet(int value) {
        int result;
        switch (value) {
            case 0:
                result = 1;
                break;
            case 1:
                result = 2;
                break;
            case 2:
                result = 3;
                break;
            default:
                result = 4;
        }
        return result;
    }

    @Test
    public void testTailCallEncouragesDuplication() {
        StructuredGraph graph = buildGraph(true);
        applyDuplication(graph);

        Assert.assertTrue(graph.verify(true));
        Assert.assertTrue(graph.getNodes(MergeNode.TYPE).isEmpty());
        Assert.assertTrue(graph.getNodes(ReturnNode.TYPE).count() > 1);
    }

    @Test
    public void testOrdinaryMultiReturnDoesNotEncourageDuplication() {
        StructuredGraph graph = buildGraph(false);
        applyDuplication(graph);

        Assert.assertTrue(graph.verify(true));
        Assert.assertEquals(1, graph.getNodes(MergeNode.TYPE).count());
        Assert.assertEquals(1, graph.getNodes(ReturnNode.TYPE).count());
    }

    @Test
    public void testUnmarkedConstantTailCallDoesNotEncourageDuplication() {
        checkUnmarkedTailCall(false);
    }

    @Test
    public void testUnmarkedDynamicTailCallDoesNotEncourageDuplication() {
        checkUnmarkedTailCall(true);
    }

    @Test
    public void testMarkedDynamicTailCallEncouragesDuplication() {
        StructuredGraph graph = buildGraph("snippet", true, true, true);
        applyDuplication(graph);

        Assert.assertTrue(graph.verify(true));
        Assert.assertTrue(graph.getNodes(MergeNode.TYPE).isEmpty());
        Assert.assertTrue(graph.getNodes(ReturnNode.TYPE).count() > 1);
    }

    @Test
    public void testStubConstruction() {
        BytecodeInterpreterAnnotations.registerCompilerDirectives(getMetaAccess());
        checkStubConstruction("handler", false);
        checkStubConstruction("splittingHandler", true);
        BytecodeHandlerConfig defaultConfig = BytecodeHandlerConfig.getHandlerConfig(getResolvedJavaMethod("handler"), getResolvedJavaMethod("handler"), false);
        BytecodeHandlerConfig splittingConfig = BytecodeHandlerConfig.getHandlerConfig(getResolvedJavaMethod("splittingHandler"), getResolvedJavaMethod("handler"), false);
        Assert.assertNotEquals(defaultConfig, splittingConfig);
    }

    private void checkStubConstruction(String methodName, boolean enableTailDuplication) {
        ResolvedJavaMethod handler = getResolvedJavaMethod(methodName);
        BytecodeHandlerConfig config = BytecodeHandlerConfig.getHandlerConfig(handler, handler, false);
        Assert.assertEquals(enableTailDuplication, config.isTailDuplicationEnabled());
        for (boolean threading : new boolean[]{false, true}) {
            GraphKit kit = new GraphKit(getDebugContext(), handler, getProviders(), getDefaultGraphBuilderPlugins(), CompilationIdentifier.INVALID_COMPILATION_ID, "handler", false, false) {
            };
            StructuredGraph graph = BytecodeHandlerStubHelper.createStub(kit, handler, 0, threading, getResolvedJavaMethod("fetchOpcode"), index -> new long[2], config, handler, 0, null);
            Assert.assertTrue(graph.verify(true));
            Assert.assertEquals(threading && enableTailDuplication ? 0 : 1, graph.getNodes().filter(ControlFlowAnchorNode.class).count());
            Assert.assertEquals(1, graph.getNodes(ReturnNode.TYPE).count());
            MultiReturnNode result = (MultiReturnNode) graph.getNodes(ReturnNode.TYPE).first().result();
            Assert.assertEquals(threading && enableTailDuplication, result.shouldEncourageTailDuplication());
            Assert.assertEquals(threading, result.getTailCallTarget() != null);
        }
    }

    @Test
    public void testFrequencyPruningBypass() {
        for (boolean marked : new boolean[]{false, true}) {
            // Both predecessors have frequency 0.5, below the pruning threshold of 0.75.
            StructuredGraph graph = buildGraph("profiledSnippet", true, marked, false, 0.75D);
            applyDuplication(graph);
            Assert.assertTrue(graph.verify(true));
            Assert.assertEquals(marked ? 0 : 1, graph.getNodes(MergeNode.TYPE).count());
            Assert.assertEquals(marked ? 2 : 1, graph.getNodes(ReturnNode.TYPE).count());
        }
    }

    private void checkUnmarkedTailCall(boolean dynamicTarget) {
        StructuredGraph graph = buildGraph("snippet", true, false, dynamicTarget);
        applyDuplication(graph);

        Assert.assertTrue(graph.verify(true));
        Assert.assertEquals(1, graph.getNodes(MergeNode.TYPE).count());
        Assert.assertEquals(1, graph.getNodes(ReturnNode.TYPE).count());
    }

    @Test
    public void testIntegerSwitchTailCallEncouragesDuplication() {
        StructuredGraph graph = buildGraph("integerSwitchSnippet", true);
        Assert.assertEquals(1, graph.getNodes().filter(IntegerSwitchNode.class).count());

        applyDuplication(graph);

        Assert.assertTrue(graph.verify(true));
        Assert.assertTrue(graph.getNodes(MergeNode.TYPE).isEmpty());
        Assert.assertEquals(4, graph.getNodes(ReturnNode.TYPE).count());
    }

    private StructuredGraph buildGraph(boolean tailCall) {
        return buildGraph("snippet", tailCall);
    }

    private StructuredGraph buildGraph(String methodName, boolean tailCall) {
        return buildGraph(methodName, tailCall, tailCall, false);
    }

    private StructuredGraph buildGraph(String methodName, boolean tailCall, boolean encourageTailDuplication, boolean dynamicTarget) {
        return buildGraph(methodName, tailCall, encourageTailDuplication, dynamicTarget, 0.01D);
    }

    private StructuredGraph buildGraph(String methodName, boolean tailCall, boolean encourageTailDuplication, boolean dynamicTarget, double minFrequency) {
        OptionValues options = new OptionValues(getInitialOptions(),
                        DuplicationOptions.SmallGraphDuplicationBudgetFactor, 10D,
                        DuplicationOptions.DuplicationCostReductionFactor, 2,
                        DuplicationOptions.SimulationPruneUnlikelyBranches, true,
                        DuplicationOptions.DuplicationMinBranchFrequency, minFrequency);
        StructuredGraph graph = parseEager(methodName, AllowAssumptions.YES, options);
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        ValueNode returnResult = returnNode.result();
        ValueNode tailCallTarget = tailCall ? ConstantNode.forLong(1, graph) : null;
        if (dynamicTarget) {
            tailCallTarget = graph.addOrUnique(new SignExtendNode(graph.getParameter(0), 64));
        }
        MultiReturnNode multiReturn = graph.addWithoutUnique(new MultiReturnNode(returnResult, tailCallTarget, encourageTailDuplication));
        returnNode.replaceFirstInput(returnResult, multiReturn);
        return graph;
    }

    private void applyDuplication(StructuredGraph graph) {
        new DisableOverflownCountedLoopsPhase().apply(graph);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        new DuplicationPhase(FixedDuplicationSimulationConfig.defaultForDepth(DuplicationOptions.EarlySimulationDepth), true, true,
                        DuplicationPhase.FACTORS_INCLUDING_PEA, canonicalizer, graph.getOptions()).apply(graph, getProviders());
    }
}
