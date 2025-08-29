/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import static jdk.graal.compiler.core.common.CompilationRequestIdentifier.asCompilationRequest;
import static jdk.graal.compiler.debug.DebugOptions.DumpOnError;

import java.util.Set;
import java.util.function.Supplier;

import org.junit.Assert;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DynamicDeoptimizeNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.truffle.PartialEvaluator;
import jdk.graal.compiler.truffle.PerformanceInformationHandler;
import jdk.graal.compiler.truffle.TruffleCompilation;
import jdk.graal.compiler.truffle.TruffleCompilerImpl;
import jdk.graal.compiler.truffle.TruffleDebugJavaMethod;
import jdk.graal.compiler.truffle.TruffleTierContext;
import jdk.graal.compiler.truffle.phases.TruffleTier;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

public abstract class PartialEvaluationTest extends TruffleCompilerImplTest {

    private static final Set<String> WRAPPER_CLASSES = CollectionsUtil.setOf(Boolean.class.getName(), Byte.class.getName(), Character.class.getName(), Float.class.getName(),
                    Integer.class.getName(), Long.class.getName(), Short.class.getName(), Double.class.getName());

    protected CompilationResult lastCompilationResult;
    DebugContext lastDebug;
    private volatile PhaseSuite<HighTierContext> suite;
    private boolean preventDumping = false;
    protected boolean preventProfileCalls = false;

    public PartialEvaluationTest() {
        super();
    }

    protected OptimizedCallTarget assertPartialEvalEquals(Supplier<Object> method, RootNode root) {
        return assertPartialEvalEquals(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return method.get();
            }
        }, root, new Object[0]);
    }

    protected OptimizedCallTarget compileHelper(String methodName, RootNode root, Object[] arguments) {
        final OptimizedCallTarget compilable = (OptimizedCallTarget) root.getCallTarget();
        TruffleCompilationTask task = newTask();
        TruffleCompilerImpl compiler = getTruffleCompiler(compilable);
        try (TruffleCompilation compilation = compiler.openCompilation(task, compilable)) {
            CompilationIdentifier compilationId = compilation.getCompilationId();
            StructuredGraph graph = partialEval(compilable, arguments);
            this.lastCompilationResult = getTruffleCompiler(compilable).compilePEGraph(graph,
                            methodName,
                            null,
                            compilable,
                            asCompilationRequest(compilationId),
                            null,
                            task,
                            null,
                            null);
            this.lastCompiledGraph = graph;
        }

        // Ensure the invoke stub is installed
        OptimizedTruffleRuntime runtime = (OptimizedTruffleRuntime) Truffle.getRuntime();
        runtime.bypassedInstalledCode(compilable);

        return compilable;
    }

    @FunctionalInterface
    protected interface FrameFunction {
        Object execute(VirtualFrame frame);
    }

    protected RootNode toRootNode(FrameFunction f) {
        return new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return f.execute(frame);
            }
        };
    }

    protected OptimizedCallTarget assertPartialEvalEquals(RootNode expected, RootNode actual, Object... arguments) {
        return assertPartialEvalEquals(expected, actual, arguments, true);
    }

    protected OptimizedCallTarget assertPartialEvalEquals(OptimizedCallTarget expectedTarget, OptimizedCallTarget actualTarget, Object[] arguments, boolean checkConstants) {
        BailoutException lastBailout = null;
        for (int i = 0; i < 10; i++) {
            try {
                TruffleCompilerImpl compiler = getTruffleCompiler(expectedTarget);

                TruffleCompilationTask task = newTask();
                StructuredGraph expectedGraph;
                try (TruffleCompilation compilation = compiler.openCompilation(task, expectedTarget)) {
                    CompilationIdentifier expectedId = compilation.getCompilationId();
                    expectedGraph = partialEval(expectedTarget, arguments);
                    compiler.compilePEGraph(expectedGraph,
                                    "expectedTest",
                                    getSuite(expectedTarget),
                                    expectedTarget,
                                    asCompilationRequest(expectedId),
                                    null,
                                    task,
                                    null,
                                    null);
                    removeFrameStates(expectedGraph);
                }

                task = newTask();
                StructuredGraph actualGraph;
                try (TruffleCompilation compilation = compiler.openCompilation(task, actualTarget)) {
                    CompilationIdentifier actualId = compilation.getCompilationId();
                    actualGraph = partialEval(actualTarget, arguments);
                    getTruffleCompiler(actualTarget).compilePEGraph(actualGraph,
                                    "actualTest",
                                    getSuite(actualTarget),
                                    actualTarget,
                                    asCompilationRequest(actualId),
                                    null,
                                    task,
                                    null,
                                    null);
                    removeFrameStates(actualGraph);
                }
                assertEquals(expectedGraph, actualGraph, false, checkConstants, true);

                return actualTarget;
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
        return actualTarget;
    }

    protected OptimizedCallTarget assertPartialEvalEquals(RootNode expected, RootNode actual, Object[] arguments, boolean checkConstants) {
        final OptimizedCallTarget expectedTarget = (OptimizedCallTarget) expected.getCallTarget();
        final OptimizedCallTarget actualTarget = (OptimizedCallTarget) actual.getCallTarget();
        return assertPartialEvalEquals(expectedTarget, actualTarget, arguments, checkConstants);
    }

    protected static TruffleCompilationTask newTask() {
        return new TruffleCompilationTask() {
            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isLastTier() {
                return true;
            }

            @Override
            public boolean hasNextTier() {
                return false;
            }
        };
    }

    protected void assertPartialEvalNoInvokes(RootNode root) {
        assertPartialEvalNoInvokes(root, new Object[0]);
    }

    protected void assertPartialEvalNoInvokes(RootNode root, Object[] arguments) {
        assertPartialEvalNoInvokes(root.getCallTarget(), arguments);
    }

    protected void assertPartialEvalNoInvokes(CallTarget callTarget, Object[] arguments) {
        StructuredGraph actual = partialEval((OptimizedCallTarget) callTarget, arguments);
        for (MethodCallTargetNode node : actual.getNodes(MethodCallTargetNode.TYPE)) {
            Assert.fail("Found invalid method call target node: " + node + " (" + node.targetMethod() + ")");
        }
    }

    protected StructuredGraph partialEval(RootNode root, Object... arguments) {
        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        return partialEval(target, arguments);
    }

    protected StructuredGraph partialEval(OptimizedCallTarget compilable, Object[] arguments) {
        return partialEval(compilable, arguments, null);

    }

    protected void compile(OptimizedCallTarget compilable, StructuredGraph graph) {
        String methodName = "test";
        TruffleCompilationTask task = newTask();
        TruffleCompilerImpl compiler = getTruffleCompiler(compilable);
        try (TruffleCompilation compilation = compiler.openCompilation(task, compilable)) {
            compiler.compilePEGraph(graph,
                            methodName,
                            getSuite(compilable),
                            compilable,
                            asCompilationRequest(compilation.getCompilationId()),
                            null,
                            task,
                            null,
                            null);
        }
    }

    protected StructuredGraph partialEvalWithNodeEventListener(OptimizedCallTarget compilable, Object[] arguments, Graph.NodeEventListener listener) {
        return partialEval(compilable, arguments, listener);
    }

    @SuppressWarnings("try")
    private StructuredGraph partialEval(OptimizedCallTarget compilable, Object[] arguments, Graph.NodeEventListener nodeEventListener) {
        // Executed AST so that all classes are loaded and initialized.
        if (!preventProfileCalls) {
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
        }
        OptionValues options = getGraalOptions();
        DebugContext debug = getDebugContext(options);
        lastDebug = debug;
        TruffleCompilationTask task = newTask();
        TruffleCompilerImpl compiler = getTruffleCompiler(compilable);
        try (TruffleCompilation compilation = compiler.openCompilation(task, compilable)) {
            try (DebugContext.Scope _ = debug.scope("TruffleCompilation", new TruffleDebugJavaMethod(task, compilable))) {
                SpeculationLog speculationLog = compilable.getCompilationSpeculationLog();
                if (speculationLog != null) {
                    speculationLog.collectFailedSpeculations();
                }
                if (!compilable.wasExecuted()) {
                    compilable.prepareForAOT();
                }
                TruffleTier truffleTier = compiler.getTruffleTier();
                final PartialEvaluator partialEvaluator = compiler.getPartialEvaluator();
                try (PerformanceInformationHandler handler = PerformanceInformationHandler.install(
                                compiler.getConfig().runtime(), compiler.getOrCreateCompilerOptions(compilable));
                                CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod(debug.getOptions())) {
                    final TruffleTierContext context = TruffleTierContext.createInitialContext(partialEvaluator,
                                    compiler.getOrCreateCompilerOptions(compilable),
                                    debug, compilable,
                                    compilation.getCompilationId(), speculationLog,
                                    task,
                                    handler);
                    try (Graph.NodeEventScope _ = nodeEventListener == null ? null : context.graph.trackNodeEvents(nodeEventListener)) {
                        truffleTier.apply(context.graph, context);
                        lastCompiledGraph = context.graph;
                        return context.graph;
                    }
                }
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        }

    }

    protected OptionValues getGraalOptions() {
        OptionValues options = getTruffleCompiler().getOrCreateCompilerOptions(getInitCallTarget());
        if (preventDumping) {
            options = new OptionValues(options, DumpOnError, false);
        }
        return options;
    }

    protected void removeFrameStates(StructuredGraph graph) {
        for (FrameState frameState : graph.getNodes(FrameState.TYPE)) {
            frameState.replaceAtUsages(null);
            frameState.safeDelete();
        }

        /*
         * Deoptimize nodes typically contain information about frame states encoded in the action.
         * However this is not relevant when comparing graphs without frame states so we remove the
         * action and reason and replace it with zero.
         */
        for (Node deopt : graph.getNodes()) {
            if (deopt instanceof DynamicDeoptimizeNode) {
                deopt.replaceFirstInput(((DynamicDeoptimizeNode) deopt).getActionAndReason(),
                                graph.unique(ConstantNode.defaultForKind(((DynamicDeoptimizeNode) deopt).getActionAndReason().getStackKind())));
            }
        }

        new DeadCodeEliminationPhase().apply(graph);

        // we are not interested in comparing object ids of the graphs e.g. for root nodes
        // so we null out all the object constants
        for (Node node : graph.getNodes()) {
            if (node instanceof ConstantNode) {
                Constant constant = ((ConstantNode) node).getValue();
                if (constant instanceof JavaConstant) {
                    ResolvedJavaType type = getMetaAccess().lookupJavaType((JavaConstant) constant);
                    if (((JavaConstant) constant).getJavaKind() == JavaKind.Object && type != null && !WRAPPER_CLASSES.contains(type.toJavaName())) {
                        node.replaceAtUsages(graph.unique(ConstantNode.defaultForKind(JavaKind.Object)));
                        node.safeDelete();
                    }
                }
            }
        }
    }

    private PhaseSuite<HighTierContext> getSuite(OptimizedCallTarget callTarget) {
        PhaseSuite<HighTierContext> result = suite;
        if (result == null) {
            synchronized (this) {
                result = suite;
                if (result == null) {
                    TruffleCompilerImpl compiler = getTruffleCompiler(callTarget);
                    result = compiler.createGraphBuilderSuite(compiler.getConfig().lastTier());
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
}
