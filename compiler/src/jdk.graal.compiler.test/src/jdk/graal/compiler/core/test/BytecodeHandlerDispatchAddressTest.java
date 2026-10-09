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

import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.extended.BytecodeHandlerDispatchAddressNode;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

public class BytecodeHandlerDispatchAddressTest extends GraalCompilerTest {

    public static long loop(int count) {
        while (count > 0) {
            count--;
        }
        return 0;
    }

    public static long dynamic(int opcode, int state, int secondState) {
        return opcode + state + secondState;
    }

    private StructuredGraph dispatchGraph(boolean constantState) {
        StructuredGraph graph = parseEager("dynamic", AllowAssumptions.NO);
        long[][] tables = {{11, 12}, {21, 22}, {31, 32}, {41, 42}, {51, 52}, {61, 62}};
        ValueNode state = constantState ? ConstantNode.forInt(2, graph) : graph.getParameter(1);
        ValueNode secondState = constantState ? ConstantNode.forInt(1, graph) : graph.getParameter(2);
        BytecodeHandlerDispatchAddressNode dispatch = graph.add(new BytecodeHandlerDispatchAddressNode(
                        graph.getParameter(0), new ValueNode[]{state, secondState}, new int[]{3, 2}, index -> tables[index]));
        ReturnNode result = graph.getNodes(ReturnNode.TYPE).first();
        graph.addBeforeFixed(result, dispatch);
        result.replaceFirstInput(result.result(), dispatch);
        return graph;
    }

    private void lowerDispatchBeforeCompilation(StructuredGraph graph) {
        new HighTierLoweringPhase(createCanonicalizerPhase()) {
            @Override
            public void updateGraphState(GraphState graphState) {
                // Exercise dispatch lowering now, then compile the resulting graph normally.
            }
        }.apply(graph, getDefaultHighTierContext());
    }

    @Test
    public void testDynamicSelectionWithoutEscapeAnalysis() throws InvalidInstalledCodeException {
        StructuredGraph graph = dispatchGraph(false);
        Assert.assertFalse(graph.isAfterStage(StageFlag.FINAL_PARTIAL_ESCAPE));
        checkDynamicSelection(graph);
    }

    @Test
    public void testDynamicSelectionAfterEarlyEscapeAnalysis() throws InvalidInstalledCodeException {
        StructuredGraph graph = dispatchGraph(false);
        graph.getGraphState().setAfterStage(StageFlag.PARTIAL_ESCAPE);
        checkDynamicSelection(graph);
    }

    private void checkDynamicSelection(StructuredGraph graph) throws InvalidInstalledCodeException {
        lowerDispatchBeforeCompilation(graph);
        InstalledCode code = getCode(graph.method(), graph, true);
        for (int state = 0; state < 3; state++) {
            for (int secondState = 0; secondState < 2; secondState++) {
                for (int opcode = 0; opcode < 2; opcode++) {
                    Assert.assertEquals((long) (state + secondState * 3 + 1) * 10 + opcode + 1, code.executeVarargs(opcode, state, secondState));
                }
            }
        }
    }

    @Test
    public void testConstantSelectionAfterEscapeAnalysis() throws InvalidInstalledCodeException {
        StructuredGraph graph = dispatchGraph(true);
        graph.getGraphState().setAfterStage(StageFlag.FINAL_PARTIAL_ESCAPE);
        lowerDispatchBeforeCompilation(graph);
        Assert.assertEquals(0, graph.getNodes().filter(ConditionalNode.class).count());
        // Clear the simulated stage before running the normal compilation pipeline.
        graph.getGraphState().getStageFlags().remove(StageFlag.FINAL_PARTIAL_ESCAPE);
        InstalledCode code = getCode(graph.method(), graph, true);
        Assert.assertEquals(61L, code.executeVarargs(0, 0, 0));
        Assert.assertEquals(62L, code.executeVarargs(1, 0, 0));
    }

    @Test
    public void testDynamicSelectionAfterEscapeAnalysisIsRejected() {
        StructuredGraph graph = dispatchGraph(false);
        graph.getGraphState().setAfterStage(StageFlag.FINAL_PARTIAL_ESCAPE);
        AssertionError failure = Assert.assertThrows(AssertionError.class,
                        () -> new HighTierLoweringPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext()));
        Assert.assertTrue(failure.getMessage().contains("Template dispatch remained dynamic after final escape analysis"));
    }

    @Test
    public void testTableLoadBeforeLoopExit() {
        StructuredGraph graph = parseEager("loop", AllowAssumptions.NO);
        LoopExitNode exit = graph.getNodes(LoopExitNode.TYPE).first();
        Assert.assertNotNull(exit);
        Assert.assertTrue(exit.predecessor() instanceof ControlSplitNode);
        ControlSplitNode split = (ControlSplitNode) exit.predecessor();
        // Keep a constant proxy until lowering to exercise table selection at the loop exit.
        ValueProxyNode template = graph.addWithoutUnique(new ValueProxyNode(ConstantNode.forInt(1, graph), exit));
        long[][] tables = {new long[]{11}, new long[]{22}};
        BytecodeHandlerDispatchAddressNode dispatch = graph.add(new BytecodeHandlerDispatchAddressNode(
                        ConstantNode.forInt(0, graph), new ValueNode[]{template}, new int[]{2}, index -> tables[index]));
        ReturnNode result = graph.getNodes(ReturnNode.TYPE).first();
        graph.addBeforeFixed(result, dispatch);
        result.replaceFirstInput(result.result(), dispatch);

        new HighTierLoweringPhase(createCanonicalizerPhase()).apply(graph, getDefaultHighTierContext());

        Assert.assertFalse(dispatch.isAlive());
        Assert.assertTrue(exit.predecessor() instanceof FixedWithNextNode);
        FixedWithNextNode load = (FixedWithNextNode) exit.predecessor();
        Assert.assertTrue(load.predecessor() instanceof BeginNode);
        Assert.assertSame(split, load.predecessor().predecessor());
        ValueProxyNode tableProxy = exit.proxies().filter(ValueProxyNode.class).first();
        Assert.assertNotNull(tableProxy);
        Assert.assertSame(load, tableProxy.value());
        Assert.assertTrue(graph.verify());
    }
}
