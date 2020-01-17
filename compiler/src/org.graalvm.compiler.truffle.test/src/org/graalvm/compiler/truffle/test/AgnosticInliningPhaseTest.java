/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.nodes.InlineDecisionInjectNode;
import org.graalvm.compiler.truffle.compiler.nodes.InlineDecisionNode;
import org.graalvm.compiler.truffle.compiler.phases.inlining.AgnosticInliningPhase;
import org.graalvm.compiler.truffle.runtime.NoInliningPolicy;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.ReflectionUtils;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

public class AgnosticInliningPhaseTest extends PartialEvaluationTest {

    protected final TruffleRuntime runtime = Truffle.getRuntime();

    @Before
    public void before() {
        setupContext(Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).option("engine.LanguageAgnosticInlining", Boolean.TRUE.toString()).option("engine.InliningInliningBudget",
                        "1").build());
    }

    @Test
    public void testInInlinedNode() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        final OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(new CallsInnerNodeTwice(createDummyNode()));
        callTarget.call();
        final StructuredGraph graph = runLanguageAgnosticInliningPhase(callTarget);
        // Language agnostic inlining expects this particular pattern to be present in the graph
        Assert.assertEquals(2, graph.getNodes(InlineDecisionNode.TYPE).count());
        for (InlineDecisionNode decisionNode : graph.getNodes(InlineDecisionNode.TYPE)) {
            Assert.assertEquals(2, decisionNode.usages().count());
            for (Node usage : decisionNode.usages()) {
                if (usage instanceof IntegerEqualsNode) {
                    final Node ifNode = usage.usages().first();
                    Assert.assertTrue(ifNode instanceof IfNode);
                    final FixedNode invoke = ((IfNode) ifNode).falseSuccessor().next();
                    Assert.assertTrue(invoke instanceof Invoke);
                    final ValueNode injectNode = ((Invoke) invoke).callTarget().arguments().get(1);
                    Assert.assertTrue(injectNode instanceof InlineDecisionInjectNode);
                    Assert.assertEquals(decisionNode, ((InlineDecisionInjectNode) injectNode).getDecision());
                } else if (usage instanceof InlineDecisionInjectNode) {
                    // expected, checked in true branch
                } else {
                    Assert.fail("Unknown usage");
                }
            }
        }
    }

    protected StructuredGraph runLanguageAgnosticInliningPhase(OptimizedCallTarget callTarget) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final TruffleInlining callNodeProvider = new TruffleInlining(callTarget, new NoInliningPolicy());
        final PartialEvaluator partialEvaluator = truffleCompiler.getPartialEvaluator();
        final Class<?> partialEvaluatorClass = partialEvaluator.getClass().getSuperclass();
        final Method createGraphForPE = partialEvaluatorClass.getDeclaredMethod("createGraphForPE",
                        DebugContext.class,
                        String.class,
                        ResolvedJavaMethod.class,
                        StructuredGraph.AllowAssumptions.class,
                        CompilationIdentifier.class,
                        SpeculationLog.class,
                        Cancellable.class);
        ReflectionUtils.setAccessible(createGraphForPE, true);
        final StructuredGraph graph = (StructuredGraph) createGraphForPE.invoke(partialEvaluator,
                        getDebugContext(),
                        "",
                        rootCallForTarget(callTarget),
                        StructuredGraph.AllowAssumptions.YES,
                        new CompilationIdentifier() {
                            @Override
                            public String toString(Verbosity verbosity) {
                                return "";
                            }
                        },
                        getSpeculationLog(),
                        null);
        final AgnosticInliningPhase agnosticInliningPhase = new AgnosticInliningPhase(callTarget.getOptionValues(), partialEvaluator, callNodeProvider, callTarget);
        agnosticInliningPhase.apply(graph, truffleCompiler.getPartialEvaluator().getProviders());
        return graph;
    }

    protected final OptimizedCallTarget createDummyNode() {
        return (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        });
    }

    private ResolvedJavaMethod rootCallForTarget(OptimizedCallTarget target) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        final PartialEvaluator partialEvaluator = truffleCompiler.getPartialEvaluator();
        final Method rootForCallTarget = partialEvaluator.getClass().getSuperclass().getDeclaredMethod("rootForCallTarget", CompilableTruffleAST.class);
        return (ResolvedJavaMethod) rootForCallTarget.invoke(partialEvaluator, target);
    }

    protected class CallsInnerNodeTwice extends RootNode {

        @Child private OptimizedDirectCallNode callNode1;
        @Child private OptimizedDirectCallNode callNode2;

        public CallsInnerNodeTwice(RootCallTarget toCall) {
            super(null);
            this.callNode1 = (OptimizedDirectCallNode) runtime.createDirectCallNode(toCall);
            this.callNode2 = (OptimizedDirectCallNode) runtime.createDirectCallNode(toCall);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            callNode1.call(frame.getArguments());
            return callNode2.call(12345);
        }
    }
}
