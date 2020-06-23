/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.debug.DebugOptions.DumpOnError;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.truffle.common.TruffleDebugJavaMethod;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.runtime.CancellableCompileTask;
import org.graalvm.compiler.truffle.runtime.DefaultInliningPolicy;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.junit.Assert;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.SpeculationLog;

import java.lang.ref.WeakReference;

public abstract class PartialEvaluationTest extends TruffleCompilerImplTest {

    private volatile PhaseSuite<HighTierContext> suite;

    public PartialEvaluationTest() {
    }

    protected OptimizedCallTarget assertPartialEvalEquals(String methodName, RootNode root) {
        return assertPartialEvalEquals(methodName, root, new Object[0]);
    }

    private CompilationIdentifier getCompilationId(final OptimizedCallTarget compilable) {
        return this.getTruffleCompiler(compilable).createCompilationIdentifier(compilable);
    }

    protected OptimizedCallTarget compileHelper(String methodName, RootNode root, Object[] arguments) {
        return compileHelper(methodName, root, arguments, true);
    }

    protected CompilationResult lastCompilationResult;

    protected OptimizedCallTarget compileHelper(String methodName, RootNode root, Object[] arguments, boolean lastTierCompilation) {
        final OptimizedCallTarget compilable = (OptimizedCallTarget) (Truffle.getRuntime()).createCallTarget(root);
        CompilationIdentifier compilationId = getCompilationId(compilable);
        StructuredGraph graph = partialEval(compilable, arguments, compilationId);
        this.lastCompilationResult = getTruffleCompiler(compilable).compilePEGraph(graph, methodName, null, compilable, asCompilationRequest(compilationId), null,
                        new CancellableCompileTask(new WeakReference<>(compilable), lastTierCompilation));
        this.lastCompiledGraph = graph;
        return compilable;
    }

    protected void assertPartialEvalEquals(RootNode expected, RootNode actual, Object[] arguments) {
        final OptimizedCallTarget expectedTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(expected);
        final OptimizedCallTarget actualTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(actual);

        BailoutException lastBailout = null;
        for (int i = 0; i < 10; i++) {
            try {
                CompilationIdentifier expectedId = getCompilationId(expectedTarget);
                StructuredGraph expectedGraph = partialEval(expectedTarget, arguments, expectedId);
                getTruffleCompiler(expectedTarget).compilePEGraph(expectedGraph, "expectedTest", getSuite(expectedTarget), expectedTarget, asCompilationRequest(expectedId), null,
                                new CancellableCompileTask(new WeakReference<>(expectedTarget), true));
                removeFrameStates(expectedGraph);

                CompilationIdentifier actualId = getCompilationId(actualTarget);
                StructuredGraph actualGraph = partialEval(actualTarget, arguments, actualId);
                getTruffleCompiler(actualTarget).compilePEGraph(actualGraph, "actualTest", getSuite(actualTarget), actualTarget, asCompilationRequest(actualId), null,
                                new CancellableCompileTask(new WeakReference<>(actualTarget), true));
                removeFrameStates(actualGraph);
                assertEquals(expectedGraph, actualGraph, true, true);
                return;
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
    }

    protected OptimizedCallTarget assertPartialEvalEquals(String methodName, RootNode root, Object[] arguments) {
        final OptimizedCallTarget compilable = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(root);

        BailoutException lastBailout = null;
        for (int i = 0; i < 10; i++) {
            try {
                CompilationIdentifier compilationId = getCompilationId(compilable);
                StructuredGraph actual = partialEval(compilable, arguments, compilationId);
                getTruffleCompiler(compilable).compilePEGraph(actual, methodName, getSuite(compilable), compilable, asCompilationRequest(compilationId), null,
                                new CancellableCompileTask(new WeakReference<>(compilable), true));
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
        StructuredGraph actual = partialEval((OptimizedCallTarget) callTarget, arguments, INVALID_COMPILATION_ID);
        for (MethodCallTargetNode node : actual.getNodes(MethodCallTargetNode.TYPE)) {
            Assert.fail("Found invalid method call target node: " + node + " (" + node.targetMethod() + ")");
        }
    }

    protected StructuredGraph partialEval(OptimizedCallTarget compilable, Object[] arguments) {
        return partialEval(compilable, arguments, INVALID_COMPILATION_ID);
    }

    protected void compile(OptimizedCallTarget compilable, StructuredGraph graph) {
        String methodName = "test";
        CompilationIdentifier compilationId = getCompilationId(compilable);
        getTruffleCompiler(compilable).compilePEGraph(graph, methodName, getSuite(compilable), compilable, asCompilationRequest(compilationId), null,
                        new CancellableCompileTask(new WeakReference<>(compilable), true));
    }

    DebugContext lastDebug;

    @SuppressWarnings("try")
    protected StructuredGraph partialEval(OptimizedCallTarget compilable, Object[] arguments, CompilationIdentifier compilationId) {
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
        OptionValues options = getOptions();
        DebugContext debug = getDebugContext(options);
        lastDebug = debug;
        try (DebugContext.Scope s = debug.scope("TruffleCompilation", new TruffleDebugJavaMethod(compilable))) {
            TruffleInlining inliningDecision = new TruffleInlining(compilable, new DefaultInliningPolicy());
            SpeculationLog speculationLog = compilable.getCompilationSpeculationLog();
            if (speculationLog != null) {
                speculationLog.collectFailedSpeculations();
            }
            final PartialEvaluator partialEvaluator = getTruffleCompiler(compilable).getPartialEvaluator();
            final PartialEvaluator.Request request = partialEvaluator.new Request(compilable.getOptionValues(), debug, compilable, partialEvaluator.rootForCallTarget(compilable), inliningDecision,
                            compilationId, speculationLog, null);
            return partialEvaluator.evaluate(request);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected OptionValues getOptions() {
        OptionValues options = TruffleCompilerOptions.getOptions();
        if (preventDumping) {
            options = new OptionValues(options, DumpOnError, false);
        }
        return options;
    }

    private boolean preventDumping = false;

    protected class PreventDumping implements AutoCloseable {
        private final boolean previous;

        protected PreventDumping() {
            previous = preventDumping;
            preventDumping = true;
        }

        @Override
        public void close() {
            preventDumping = previous;
        }
    }

    protected void removeFrameStates(StructuredGraph graph) {
        for (FrameState frameState : graph.getNodes(FrameState.TYPE)) {
            frameState.replaceAtUsages(null);
            frameState.safeDelete();
        }
        createCanonicalizerPhase().apply(graph, getProviders());
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

    private PhaseSuite<HighTierContext> getSuite(OptimizedCallTarget callTarget) {
        PhaseSuite<HighTierContext> result = suite;
        if (result == null) {
            synchronized (this) {
                result = suite;
                if (result == null) {
                    result = getTruffleCompiler(callTarget).createGraphBuilderSuite();
                    suite = result;
                }
            }
        }
        return result;
    }

    /**
     * Error ignored when running before partially evaluating a root node.
     */
    @SuppressWarnings("serial")
    protected static final class IgnoreError extends ControlFlowException {

    }
}
