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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.BackgroundCompileQueue;
import org.graalvm.compiler.truffle.runtime.CancellableCompileTask;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.LoopNodeFactory;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionParser;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.SubstrateStackIntrospection;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.truffle.TruffleFeature;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

class SubstateTruffleOptions {

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
        return SubstrateOptions.MultiThreaded.getValue() && SubstateTruffleOptions.TruffleMultiThreaded.getValue();
    }
}

public final class SubstrateTruffleRuntime extends GraalTruffleRuntime {

    private static final int DEBUG_TEAR_DOWN_TIMEOUT = 2_000;
    private static final int PRODUCTION_TEAR_DOWN_TIMEOUT = 10_000;

    private CallMethods hostedCallMethods;
    private volatile BackgroundCompileQueue compileQueue;
    private volatile boolean initialized;
    private volatile Boolean profilingEnabled;

    @Override
    protected BackgroundCompileQueue getCompileQueue() {
        assert compileQueue != null;
        return compileQueue;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTruffleRuntime() {
        super(Collections.emptyList());
        /* Ensure the factory class gets initialized. */
        super.getLoopNodeFactory();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void resetHosted() {
        truffleCompiler = null;
    }

    private void initializeAtRuntime(OptimizedCallTarget callTarget) {
        truffleCompiler.initialize(TruffleRuntimeOptions.getOptionsForCompiler(callTarget), callTarget, true);
        if (SubstateTruffleOptions.isMultiThreaded()) {
            compileQueue = TruffleFeature.getSupport().createBackgroundCompileQueue(this);
            RuntimeSupport.getRuntimeSupport().addTearDownHook(this::tearDown);
        }
        if (callTarget.engine.traceTransferToInterpreter) {
            if (!SubstrateOptions.IncludeNodeSourcePositions.getValue()) {
                Log.log().string("Warning: TraceTruffleTransferToInterpreter cannot print stack traces. Build image with -H:+IncludeNodeSourcePositions to enable stack traces.").newline();
            }
            RuntimeOptionValues.singleton().update(Deoptimizer.Options.TraceDeoptimization, true);
        }
        installDefaultListeners();
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
        return TruffleFeature.getSupport().createTruffleCompiler(this);
    }

    private void tearDown() {
        /*
         * Runaway compilations should fail during testing, but should not cause crashes in
         * production.
         */
        long timeout = SubstrateUtil.assertionsEnabled() ? DEBUG_TEAR_DOWN_TIMEOUT : PRODUCTION_TEAR_DOWN_TIMEOUT;
        getCompileQueue().shutdownAndAwaitTermination(timeout);
    }

    @Override
    public SubstrateTruffleCompiler getTruffleCompiler(CompilableTruffleAST compilable) {
        Objects.requireNonNull(compilable, "Compilable must be non null.");
        ensureInitializedAtRuntime((OptimizedCallTarget) compilable);
        return (SubstrateTruffleCompiler) truffleCompiler;
    }

    @Override
    protected LoopNodeFactory getLoopNodeFactory() {
        if (loopNodeFactory == null) {
            throw shouldNotReachHere("loopNodeFactory not initialized");
        }
        return loopNodeFactory;
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
        OptimizedCallTarget callTarget = TruffleFeature.getSupport().createOptimizedCallTarget(source, rootNode);
        ensureInitializedAtRuntime(callTarget);
        return callTarget;
    }

    private void ensureInitializedAtRuntime(OptimizedCallTarget callTarget) {
        if (!SubstrateUtil.HOSTED && !initialized) {
            // Checkstyle: stop
            synchronized (this) {
                if (!initialized) {
                    initializeAtRuntime(callTarget);
                    initialized = true;
                }
            }
            // Checkstyle: resume
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
    public CancellableCompileTask submitForCompilation(OptimizedCallTarget optimizedCallTarget, boolean lastTierCompilation) {
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
         * already created in the boot image and they are directly compiled then the compile queue
         * might not yet be initialized.
         */
        ensureInitializedAtRuntime(optimizedCallTarget);

        if (SubstateTruffleOptions.isMultiThreaded()) {
            return super.submitForCompilation(optimizedCallTarget, lastTierCompilation);
        }

        try {
            // Single threaded compilation does not require cancellation.
            doCompile(optimizedCallTarget, null);
        } catch (com.oracle.truffle.api.OptimizationFailedException e) {
            if (TruffleRuntimeOptions.getPolyglotOptionValue(optimizedCallTarget.getOptionValues(), PolyglotCompilerOptions.CompilationExceptionsArePrinted)) {
                Log.log().string(printStackTraceToString(e));
            }
            if (SubstateTruffleOptions.TrufflePropagateCompilationErrors.getValue()) {
                throw e;
            }
        }

        return null;
    }

    @Override
    public void finishCompilation(OptimizedCallTarget optimizedCallTarget, CancellableCompileTask task, boolean mayBeAsynchronous) {
        if (SubstateTruffleOptions.isMultiThreaded()) {
            super.finishCompilation(optimizedCallTarget, task, mayBeAsynchronous);
        }
    }

    @Override
    public void waitForCompilation(OptimizedCallTarget optimizedCallTarget, long timeout) throws ExecutionException, TimeoutException {
        if (SubstateTruffleOptions.isMultiThreaded()) {
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
    public <T> T getOptions(Class<T> type) {
        if (type == OptionValues.class) {
            return type.cast(RuntimeOptionValues.singleton());
        }
        return super.getOptions(type);
    }

    @Override
    public <T> T convertOptions(Class<T> type, Map<String, Object> map) {
        if (type == OptionValues.class) {
            final EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                final String optionName = e.getKey();
                final Object optionValue = e.getValue();
                Optional<OptionDescriptor> descriptor = RuntimeOptionParser.singleton().getDescriptor(optionName);
                if (descriptor.isPresent()) {
                    OptionDescriptor desc = descriptor.get();
                    Class<?> valueType = optionValue.getClass();
                    if (desc.getOptionValueType().isAssignableFrom(valueType)) {
                        values.put(desc.getOptionKey(), optionValue);
                    } else {
                        throw new IllegalArgumentException("Invalid type of option '" + optionName + "': required " + desc.getOptionValueType().getSimpleName() + ", got " + valueType);
                    }
                }
            }
            return type.cast(new OptionValues(values));

        }
        return super.convertOptions(type, map);
    }

    @Override
    protected Map<String, Object> createInitialOptions() {
        Map<String, Object> res = new HashMap<>();
        UnmodifiableMapCursor<OptionKey<?>, Object> optionValues = RuntimeOptionValues.singleton().getMap().getEntries();
        while (optionValues.advance()) {
            final OptionKey<?> key = optionValues.getKey();
            Object value = optionValues.getValue();
            res.put(key.getName(), value);
        }
        return res;
    }

    @Platforms(HOSTED_ONLY.class)
    public void resetNativeImageState() {
        clearState();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T asObject(Class<T> type, JavaConstant constant) {
        return (T) KnownIntrinsics.convertUnknownValue(SubstrateObjectConstant.asObject(type, constant), Object.class);
    }

    @Override
    protected JavaConstant forObject(Object object) {
        return SubstrateObjectConstant.forObject(object);
    }

    @Override
    public Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(JavaConstant optimizedAssumptionConstant) {
        return TruffleFeature.getSupport().registerOptimizedAssumptionDependency(optimizedAssumptionConstant);
    }

    @Override
    public JavaConstant getCallTargetForCallNode(JavaConstant callNodeConstant) {
        return TruffleFeature.getSupport().getCallTargetForCallNode(callNodeConstant);
    }

    @Override
    public CompilableTruffleAST asCompilableTruffleAST(JavaConstant constant) {
        return TruffleFeature.getSupport().asCompilableTruffleAST(constant);
    }

    @Override
    public void log(CompilableTruffleAST compilable, String message) {
        if (!TruffleFeature.getSupport().tryLog(this, compilable, message)) {
            super.log(compilable, message);
        }
    }

    @Override
    protected OutputStream getDefaultLogStream() {
        return TTY.out;
    }
}
