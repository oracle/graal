/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.api;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleInliningData;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.BackgroundCompileQueue;
import org.graalvm.compiler.truffle.runtime.CompilationTask;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.stack.SubstrateStackIntrospection;
import com.oracle.svm.truffle.TruffleSupport;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.impl.AbstractFastThreadLocal;
import com.oracle.truffle.api.impl.ThreadLocalHandshake;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.utilities.TriState;

import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

class SubstrateTruffleOptions {

    @Option(help = "Enable support for Truffle background compilation")//
    static final HostedOptionKey<Boolean> TruffleMultiThreaded = new HostedOptionKey<>(true);

    @Option(help = "Propagate Truffle compilation errors")//
    static final HostedOptionKey<Boolean> TrufflePropagateCompilationErrors = new HostedOptionKey<>(false);

    @Fold
    static boolean isMultiThreaded() {
        /*
         * Multi-threading (= Truffle background compilation) can be disabled either by disabling
         * thread support of Substrate VM completely, or by disabling only the Truffle-specific
         * background compile queue. The latter is useful when background compilation is not needed,
         * but recurring callbacks (which depend on the safepoint mechanism) are required.
         */
        return SubstrateOptions.MultiThreaded.getValue() && SubstrateTruffleOptions.TruffleMultiThreaded.getValue();
    }
}

public final class SubstrateTruffleRuntime extends GraalTruffleRuntime {

    private static final int DEBUG_TEAR_DOWN_TIMEOUT = 2_000;
    private static final int PRODUCTION_TEAR_DOWN_TIMEOUT = 10_000;

    private CallMethods hostedCallMethods;
    private volatile BackgroundCompileQueue compileQueue;
    private volatile boolean initialized;
    private volatile Boolean profilingEnabled;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTruffleRuntime() {
        super(Collections.emptyList());
        /* Ensure the factory class gets initialized. */
        super.getLoopNodeFactory();
    }

    @Override
    public BackgroundCompileQueue getCompileQueue() {
        return compileQueue;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void resetHosted() {
        truffleCompiler = null;
    }

    @Override
    public ThreadLocalHandshake getThreadLocalHandshake() {
        return SubstrateThreadLocalHandshake.SINGLETON;
    }

    @Override
    protected AbstractFastThreadLocal getFastThreadLocalImpl() {
        return SubstrateFastThreadLocal.SINGLETON;
    }

    @Override
    protected AutoCloseable openCompilerThreadScope() {
        return new CompilerThreadScope();
    }

    private void initializeAtRuntime(OptimizedCallTarget callTarget) {
        truffleCompiler.initialize(getOptionsForCompiler(callTarget), callTarget, true);
        if (SubstrateTruffleOptions.isMultiThreaded()) {
            compileQueue = TruffleSupport.singleton().createBackgroundCompileQueue(this);
        }
        if (callTarget.engine.traceTransferToInterpreter) {
            Deoptimizer.Options.TraceDeoptimization.update(true);
        }
        installDefaultListeners();
        RuntimeSupport.getRuntimeSupport().addTearDownHook(this::teardown);
    }

    private void teardown() {
        long timeout = SubstrateUtil.assertionsEnabled() ? DEBUG_TEAR_DOWN_TIMEOUT : PRODUCTION_TEAR_DOWN_TIMEOUT;
        BackgroundCompileQueue queue = getCompileQueue();
        if (queue != null) {
            queue.shutdownAndAwaitTermination(timeout);
        }

        TruffleCompiler tcp = truffleCompiler;
        if (tcp != null) {
            ((SubstrateTruffleCompiler) tcp).teardown();
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTruffleCompiler initTruffleCompiler() {
        assert truffleCompiler == null : "Cannot re-initialize Substrate TruffleCompiler";
        SubstrateTruffleCompiler compiler = newTruffleCompiler();
        truffleCompiler = compiler;
        return compiler;
    }

    public ResolvedJavaMethod[] getAnyFrameMethod() {
        return callMethods.anyFrameMethod;
    }

    @Override
    protected String getCompilerConfigurationName() {
        TruffleCompiler compiler = truffleCompiler;
        if (compiler != null) {
            return compiler.getCompilerConfigurationName();
        }
        return null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public SubstrateTruffleCompiler newTruffleCompiler() {
        return TruffleSupport.singleton().createTruffleCompiler(this);
    }

    @Override
    public SubstrateTruffleCompiler getTruffleCompiler(CompilableTruffleAST compilable) {
        Objects.requireNonNull(compilable, "Compilable must be non null.");
        ensureInitializedAtRuntime((OptimizedCallTarget) compilable);
        return (SubstrateTruffleCompiler) truffleCompiler;
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void lookupCallMethods(MetaAccessProvider metaAccess) {
        super.lookupCallMethods(metaAccess);
        hostedCallMethods = CallMethods.lookup(GraalAccess.getOriginalProviders().getMetaAccess());
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    protected void clearState() {
        super.clearState();
        hostedCallMethods = null;
    }

    @Override
    protected CallMethods getCallMethods() {
        if (SubstrateUtil.HOSTED) {
            return hostedCallMethods;
        } else {
            return callMethods;
        }
    }

    @Override
    public OptimizedCallTarget createOptimizedCallTarget(OptimizedCallTarget source, RootNode rootNode) {
        CompilerAsserts.neverPartOfCompilation();
        if (profilingEnabled == null) {
            profilingEnabled = getEngineData(rootNode).profilingEnabled;
        }
        OptimizedCallTarget callTarget = TruffleSupport.singleton().createOptimizedCallTarget(source, rootNode);
        ensureInitializedAtRuntime(callTarget);
        return callTarget;
    }

    private void ensureInitializedAtRuntime(OptimizedCallTarget callTarget) {
        if (!SubstrateUtil.HOSTED && !initialized) {
            synchronized (this) {
                if (!initialized) {
                    initializeAtRuntime(callTarget);
                    initialized = true;
                }
            }
        }
    }

    @Override
    public SpeculationLog createSpeculationLog() {
        return new SubstrateSpeculationLog();
    }

    @Override
    public void notifyTransferToInterpreter() {
        CompilerAsserts.neverPartOfCompilation();
        /*
         * Nothing to do here. We print the stack trace in the Deoptimizer when the actual
         * deoptimization happened.
         */
    }

    @Override
    public boolean isProfilingEnabled() {
        if (profilingEnabled == null) {
            profilingEnabled = getEngineData(null).profilingEnabled;
        }
        return profilingEnabled;
    }

    @Override
    public CompilationTask submitForCompilation(OptimizedCallTarget optimizedCallTarget, boolean lastTierCompilation) {
        if (SubstrateUtil.HOSTED) {
            /*
             * Truffle code can run during image generation. But for now it is the easiest to not
             * JIT compile during image generation. Support would be difficult and require major
             * refactorings in the Truffle runtime: we already run with the SubstrateTruffleRuntime
             * and not with the HotSpotTruffleRuntime, so we do not have the correct configuration
             * for Graal, we do not have the correct subclasses for the OptimizedCallTarget, ...
             */
            return null;
        }
        /*
         * Normally creating call targets schedules the initialization. However if call targets were
         * already created in the image and they are directly compiled then the compile queue might
         * not yet be initialized.
         */
        ensureInitializedAtRuntime(optimizedCallTarget);

        if (SubstrateTruffleOptions.isMultiThreaded()) {
            return super.submitForCompilation(optimizedCallTarget, lastTierCompilation);
        }

        try {
            doCompile(optimizedCallTarget, new SingleThreadedCompilationTask(optimizedCallTarget, lastTierCompilation));
        } catch (com.oracle.truffle.api.OptimizationFailedException e) {
            if (optimizedCallTarget.getOptionValue(PolyglotCompilerOptions.CompilationExceptionsArePrinted)) {
                Log.log().string(printStackTraceToString(e));
            }
            if (SubstrateTruffleOptions.TrufflePropagateCompilationErrors.getValue()) {
                throw e;
            }
        }

        return null;
    }

    @Override
    public void finishCompilation(OptimizedCallTarget optimizedCallTarget, CompilationTask task, boolean mayBeAsynchronous) {
        if (SubstrateTruffleOptions.isMultiThreaded()) {
            super.finishCompilation(optimizedCallTarget, task, mayBeAsynchronous);
        }
    }

    @Override
    public void waitForCompilation(OptimizedCallTarget optimizedCallTarget, long timeout) throws ExecutionException, TimeoutException {
        if (SubstrateTruffleOptions.isMultiThreaded()) {
            super.waitForCompilation(optimizedCallTarget, timeout);
            return;
        }

        /* We have no background compilation, so nothing to do. */
    }

    @Override
    protected StackIntrospection getStackIntrospection() {
        return SubstrateStackIntrospection.SINGLETON;
    }

    @Override
    public <T> T getGraalOptions(Class<T> type) {
        if (type == OptionValues.class) {
            return type.cast(RuntimeOptionValues.singleton());
        }
        return super.getGraalOptions(type);
    }

    @Override
    protected boolean isPrintGraphEnabled() {
        return DebugOptions.PrintGraph.getValue(getGraalOptions(OptionValues.class)) != DebugOptions.PrintGraphTarget.Disable;
    }

    @Platforms(HOSTED_ONLY.class)
    public void resetNativeImageState() {
        clearState();
    }

    @Override
    protected <T> T asObject(Class<T> type, JavaConstant constant) {
        return SubstrateObjectConstant.asObject(type, constant);
    }

    @Override
    protected JavaConstant forObject(Object object) {
        return SubstrateObjectConstant.forObject(object);
    }

    @Override
    public Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(JavaConstant optimizedAssumptionConstant) {
        return TruffleSupport.singleton().registerOptimizedAssumptionDependency(optimizedAssumptionConstant);
    }

    @Override
    public JavaConstant getCallTargetForCallNode(JavaConstant callNodeConstant) {
        return TruffleSupport.singleton().getCallTargetForCallNode(callNodeConstant);
    }

    @Override
    public CompilableTruffleAST asCompilableTruffleAST(JavaConstant constant) {
        return TruffleSupport.singleton().asCompilableTruffleAST(constant);
    }

    @Override
    public void log(String loggerId, CompilableTruffleAST compilable, String message) {
        if (!TruffleSupport.singleton().tryLog(this, loggerId, compilable, message)) {
            super.log(loggerId, compilable, message);
        }
    }

    @Override
    public boolean isSuppressedFailure(CompilableTruffleAST compilable, Supplier<String> serializedException) {
        TriState res = TruffleSupport.singleton().tryIsSuppressedFailure(compilable, serializedException);
        switch (res) {
            case TRUE:
                return true;
            case FALSE:
                return false;
            case UNDEFINED:
                return super.isSuppressedFailure(compilable, serializedException);
            default:
                throw new IllegalStateException("Unsupported value " + res);
        }
    }

    /**
     * Compilation task used when truffle runtime is run in single threaded mode.
     */
    private static class SingleThreadedCompilationTask implements TruffleCompilationTask {
        private final boolean lastTierCompilation;
        private final boolean hasNextTier;
        TruffleInlining inlining = new TruffleInlining();

        SingleThreadedCompilationTask(OptimizedCallTarget optimizedCallTarget, boolean lastTierCompilation) {
            this.hasNextTier = !optimizedCallTarget.engine.firstTierOnly && !lastTierCompilation;
            this.lastTierCompilation = lastTierCompilation;
        }

        @Override
        public boolean isCancelled() {
            // Single threaded compilation does not require cancellation.
            return false;
        }

        @Override
        public boolean isLastTier() {
            return lastTierCompilation;
        }

        @Override
        public TruffleInliningData inliningData() {
            return inlining;
        }

        @Override
        public boolean hasNextTier() {
            return hasNextTier;
        }

    }

    private static final class CompilerThreadScope implements AutoCloseable {

        CompilerThreadScope() {
            open();
        }

        // Substituted by EnterpriseTruffleFeature
        private void open() {
        }

        // Substituted by EnterpriseTruffleFeature
        @Override
        public void close() {
        }
    }

}
