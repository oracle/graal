/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.CompilationRequestIdentifier.asCompilationRequest;
import static org.graalvm.compiler.debug.DebugOptions.DumpOnError;

import java.util.function.Supplier;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DynamicDeoptimizeNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleDebugJavaMethod;
import org.graalvm.compiler.truffle.common.TruffleInliningData;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.junit.Assert;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

public abstract class PartialEvaluationTest extends TruffleCompilerImplTest {

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

    protected final CompilationIdentifier getCompilationId(final RootCallTarget compilable) {
        return this.getTruffleCompiler((OptimizedCallTarget) compilable).createCompilationIdentifier((OptimizedCallTarget) compilable);
    }

    protected OptimizedCallTarget compileHelper(String methodName, RootNode root, Object[] arguments) {
        final OptimizedCallTarget compilable = (OptimizedCallTarget) root.getCallTarget();
        CompilationIdentifier compilationId = getCompilationId(compilable);
        StructuredGraph graph = partialEval(compilable, arguments, compilationId);
        this.lastCompilationResult = getTruffleCompiler(compilable).compilePEGraph(graph, methodName, null, compilable, asCompilationRequest(compilationId), null,
                        newTask());
        this.lastCompiledGraph = graph;
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

    protected OptimizedCallTarget assertPartialEvalEquals(RootNode expected, RootNode actual, Object[] arguments) {
        return assertPartialEvalEquals(expected, actual, arguments, true);
    }

    protected OptimizedCallTarget assertPartialEvalEquals(RootNode expected, RootNode actual, Object[] arguments, boolean checkConstants) {
        final OptimizedCallTarget expectedTarget = (OptimizedCallTarget) expected.getCallTarget();
        final OptimizedCallTarget actualTarget = (OptimizedCallTarget) actual.getCallTarget();

        BailoutException lastBailout = null;
        for (int i = 0; i < 10; i++) {
            try {
                CompilationIdentifier expectedId = getCompilationId(expectedTarget);
                StructuredGraph expectedGraph = partialEval(expectedTarget, arguments, expectedId);
                getTruffleCompiler(expectedTarget).compilePEGraph(expectedGraph, "expectedTest", getSuite(expectedTarget), expectedTarget, asCompilationRequest(expectedId), null,
                                newTask());
                removeFrameStates(expectedGraph);

                CompilationIdentifier actualId = getCompilationId(actualTarget);
                StructuredGraph actualGraph = partialEval(actualTarget, arguments, actualId);
                getTruffleCompiler(actualTarget).compilePEGraph(actualGraph, "actualTest", getSuite(actualTarget), actualTarget, asCompilationRequest(actualId), null,
                                newTask());
                removeFrameStates(actualGraph);
                assertEquals(expectedGraph, actualGraph, false, checkConstants);
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

    private static TruffleCompilationTask newTask() {
        return new TruffleCompilationTask() {
            final TruffleInlining inlining = new TruffleInlining();

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isLastTier() {
                return true;
            }

            @Override
            public TruffleInliningData inliningData() {
                return inlining;
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
        return partialEval(compilable, arguments, getCompilationId(compilable));
    }

    protected void compile(OptimizedCallTarget compilable, StructuredGraph graph) {
        String methodName = "test";
        CompilationIdentifier compilationId = getCompilationId(compilable);
        getTruffleCompiler(compilable).compilePEGraph(graph, methodName, getSuite(compilable), compilable, asCompilationRequest(compilationId), null, newTask());
    }

    @SuppressWarnings("try")
    protected StructuredGraph partialEval(OptimizedCallTarget compilable, Object[] arguments, CompilationIdentifier compilationId) {
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
        try (DebugContext.Scope s = debug.scope("TruffleCompilation", new TruffleDebugJavaMethod(compilable))) {
            SpeculationLog speculationLog = compilable.getCompilationSpeculationLog();
            if (speculationLog != null) {
                speculationLog.collectFailedSpeculations();
            }
            if (!compilable.wasExecuted()) {
                compilable.prepareForAOT();
            }
            final PartialEvaluator partialEvaluator = getTruffleCompiler(compilable).getPartialEvaluator();
            final PartialEvaluator.Request request = partialEvaluator.new Request(compilable.getOptionValues(), debug, compilable, partialEvaluator.rootForCallTarget(compilable),
                            compilationId, speculationLog,
                            new TruffleCompilerImpl.CancellableTruffleCompilationTask(newTask()));
            return partialEvaluator.evaluate(request);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected OptionValues getGraalOptions() {
        OptionValues options = TruffleCompilerRuntime.getRuntime().getGraalOptions(OptionValues.class);
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
                    if (((JavaConstant) constant).getJavaKind() == JavaKind.Object && type != null) {
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
