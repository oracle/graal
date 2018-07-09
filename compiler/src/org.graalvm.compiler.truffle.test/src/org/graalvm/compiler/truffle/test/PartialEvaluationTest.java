/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.CompilationIdentifier.INVALID_COMPILATION_ID;
import static org.graalvm.compiler.core.common.CompilationRequestIdentifier.asCompilationRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleDebugJavaMethod;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.runtime.DefaultInliningPolicy;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.runtime.TruffleTreeDebugHandlersFactory;
import org.junit.Assert;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.SpeculationLog;

public abstract class PartialEvaluationTest extends GraalCompilerTest {
    protected final TruffleCompilerImpl truffleCompiler;
    private final PhaseSuite<HighTierContext> suite;

    public PartialEvaluationTest() {
        beforeInitialization();
        this.truffleCompiler = (TruffleCompilerImpl) TruffleCompilerRuntime.getRuntime().newTruffleCompiler();
        this.suite = truffleCompiler.createGraphBuilderSuite();
    }

    /**
     * Executed before initialization. This hook can be used to override specific flags.
     */
    protected void beforeInitialization() {
    }

    @Override
    protected Collection<DebugHandlersFactory> getDebugHandlersFactories() {
        List<DebugHandlersFactory> c = new ArrayList<>(super.getDebugHandlersFactories());
        c.add(new TruffleTreeDebugHandlersFactory());
        return c;
    }

    protected OptimizedCallTarget assertPartialEvalEquals(String methodName, RootNode root) {
        return assertPartialEvalEquals(methodName, root, new Object[0]);
    }

    private CompilationIdentifier getCompilationId(final OptimizedCallTarget compilable) {
        return this.truffleCompiler.getCompilationIdentifier(compilable);
    }

    protected OptimizedCallTarget compileHelper(String methodName, RootNode root, Object[] arguments) {
        final OptimizedCallTarget compilable = (OptimizedCallTarget) (Truffle.getRuntime()).createCallTarget(root);
        CompilationIdentifier compilationId = getCompilationId(compilable);
        StructuredGraph actual = partialEval(compilable, arguments, AllowAssumptions.YES, compilationId);
        truffleCompiler.compilePEGraph(actual, methodName, null, compilable, asCompilationRequest(compilationId), null);
        return compilable;
    }

    protected OptimizedCallTarget assertPartialEvalEquals(String methodName, RootNode root, Object[] arguments) {
        final OptimizedCallTarget compilable = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(root);

        BailoutException lastBailout = null;
        for (int i = 0; i < 10; i++) {
            try {
                CompilationIdentifier compilationId = getCompilationId(compilable);
                StructuredGraph actual = partialEval(compilable, arguments, AllowAssumptions.YES, compilationId);
                truffleCompiler.compilePEGraph(actual, methodName, suite, compilable, asCompilationRequest(compilationId), null);
                removeFrameStates(actual);
                StructuredGraph expected = parseForComparison(methodName, actual.getDebug());
                removeFrameStates(expected);

                assertEquals(expected, actual, true, true);
                return compilable;
            } catch (BailoutException e) {
                if (e.isPermanent()) {
                    throw e;
                }
                lastBailout = e;
                continue;
            }
        }
        if (lastBailout != null) {
            throw lastBailout;
        }
        return compilable;
    }

    protected void assertPartialEvalNoInvokes(RootNode root) {
        assertPartialEvalNoInvokes(root, new Object[0]);
    }

    protected void assertPartialEvalNoInvokes(RootNode root, Object[] arguments) {
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(root);
        assertPartialEvalNoInvokes(callTarget, arguments);
    }

    protected void assertPartialEvalNoInvokes(CallTarget callTarget, Object[] arguments) {
        StructuredGraph actual = partialEval((OptimizedCallTarget) callTarget, arguments, AllowAssumptions.YES, INVALID_COMPILATION_ID);
        for (MethodCallTargetNode node : actual.getNodes(MethodCallTargetNode.TYPE)) {
            Assert.fail("Found invalid method call target node: " + node + " (" + node.targetMethod() + ")");
        }
    }

    @SuppressWarnings("try")
    protected StructuredGraph partialEval(OptimizedCallTarget compilable, Object[] arguments, AllowAssumptions allowAssumptions, CompilationIdentifier compilationId) {
        // Executed AST so that all classes are loaded and initialized.
        try {
            compilable.call(arguments);
        } catch (IgnoreError e) {
        }
        try {
            compilable.call(arguments);
        } catch (IgnoreError e) {
        }
        try {
            compilable.call(arguments);
        } catch (IgnoreError e) {
        }

        OptionValues options = TruffleCompilerOptions.getOptions();
        DebugContext debug = getDebugContext(options);
        try (DebugContext.Scope s = debug.scope("TruffleCompilation", new TruffleDebugJavaMethod(compilable))) {
            TruffleInlining inliningDecision = new TruffleInlining(compilable, new DefaultInliningPolicy());
            SpeculationLog speculationLog = compilable.getSpeculationLog();
            return truffleCompiler.getPartialEvaluator().createGraph(debug, compilable, inliningDecision, allowAssumptions, compilationId, speculationLog, null);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected void removeFrameStates(StructuredGraph graph) {
        for (FrameState frameState : graph.getNodes(FrameState.TYPE)) {
            frameState.replaceAtUsages(null);
            frameState.safeDelete();
        }
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        new DeadCodeEliminationPhase().apply(graph);
    }

    @SuppressWarnings("try")
    protected StructuredGraph parseForComparison(final String methodName, DebugContext debug) {
        try (DebugContext.Scope s = debug.scope("Truffle", new DebugDumpScope("Comparison: " + methodName))) {
            StructuredGraph graph = parseEager(methodName, AllowAssumptions.YES);
            compile(graph.method(), graph);
            return graph;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    /**
     * Error ignored when running before partially evaluating a root node.
     */
    @SuppressWarnings("serial")
    protected static final class IgnoreError extends ControlFlowException {

    }
}
