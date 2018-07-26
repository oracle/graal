/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.graal.SubstrateGraalUtils.updateGraalArchitectureWithHostCPUFeatures;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleCompilationExceptionsArePrinted;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.runtime.CancellableCompileTask;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.LoopNodeFactory;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.stack.SubstrateStackIntrospection;
import com.oracle.svm.graal.GraalSupport;
import com.oracle.svm.graal.hosted.GraalFeature;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.truffle.TruffleFeature;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

public final class SubstrateTruffleRuntime extends GraalTruffleRuntime {

    private static final int DEBUG_TEAR_DOWN_TIMEOUT = 2_000;
    private static final int PRODUCTION_TEAR_DOWN_TIMEOUT = 10_000;

    private BackgroundCompileQueue compileQueue;
    private CallMethods hostedCallMethods;
    private boolean initialized;

    @Override
    protected BackgroundCompileQueue getCompileQueue() {
        assert compileQueue != null;
        return compileQueue;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTruffleRuntime() {
        super(() -> ImageSingletons.lookup(GraalRuntime.class), Collections.emptyList());
        /* Ensure the factory class gets initialized. */
        super.getLoopNodeFactory();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void resetHosted() {
        truffleCompiler = null;
    }

    public void initializeAtRuntime() {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            compileQueue = new BackgroundCompileQueue();
            RuntimeSupport.getRuntimeSupport().addTearDownHook(this::tearDown);
        }
        if (TruffleCompilerOptions.TraceTruffleTransferToInterpreter.getValue(RuntimeOptionValues.singleton())) {
            if (!SubstrateOptions.IncludeNodeSourcePositions.getValue()) {
                System.out.println("Warning: " + TruffleCompilerOptions.TraceTruffleTransferToInterpreter.getName() +
                                " cannot print stack traces. Build image with -H:+IncludeNodeSourcePositions to enable stack traces.");
            }
            RuntimeOptionValues.singleton().update(Deoptimizer.Options.TraceDeoptimization, true);
        }

        updateGraalArchitectureWithHostCPUFeatures(getTruffleCompiler().getBackend());
        installDefaultListeners();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTruffleCompiler initTruffleCompiler() {
        assert truffleCompiler == null : "Cannot re-initialize Substrate TruffleCompiler";
        GraalFeature graalFeature = ImageSingletons.lookup(GraalFeature.class);
        SnippetReflectionProvider snippetReflection = graalFeature.getHostedProviders().getSnippetReflection();
        SubstrateTruffleCompiler compiler = new SubstrateTruffleCompiler(this, graalFeature.getHostedProviders().getGraphBuilderPlugins(), GraalSupport.getSuites(),
                        GraalSupport.getLIRSuites(),
                        GraalSupport.getRuntimeConfig().getBackendForNormalMethod(), snippetReflection);
        truffleCompiler = compiler;

        return compiler;
    }

    public ResolvedJavaMethod[] getAnyFrameMethod() {
        return callMethods.anyFrameMethod;
    }

    @Override
    protected String getCompilerConfigurationName() {
        TruffleCompiler compiler = getTruffleCompiler();
        if (compiler != null) {
            return compiler.getCompilerConfigurationName();
        }
        return null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public SubstrateTruffleCompiler newTruffleCompiler() {
        GraalFeature graalFeature = ImageSingletons.lookup(GraalFeature.class);
        SnippetReflectionProvider snippetReflectionProvider = graalFeature.getHostedProviders().getSnippetReflection();
        return new SubstrateTruffleCompiler(this, graalFeature.getHostedProviders().getGraphBuilderPlugins(), GraalSupport.getSuites(),
                        GraalSupport.getLIRSuites(),
                        GraalSupport.getRuntimeConfig().getBackendForNormalMethod(), snippetReflectionProvider);
    }

    private void tearDown() {
        ExecutorService executor = getCompileQueue().getCompilationExecutor();
        executor.shutdownNow();
        try {
            /*
             * Runaway compilations should fail during testing, but should not cause crashes in
             * production.
             */
            long timeout = SubstrateUtil.assertionsEnabled() ? DEBUG_TEAR_DOWN_TIMEOUT : PRODUCTION_TEAR_DOWN_TIMEOUT;
            executor.awaitTermination(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Could not terminate compiler threads. Check if there are runaway compilations that don't handle Thread#interrupt.", e);
        }
    }

    @Override
    public SubstrateTruffleCompiler getTruffleCompiler() {
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

        if (!SubstrateUtil.HOSTED && !initialized) {
            initializeAtRuntime();
            initialized = true;
        }

        return TruffleFeature.getSupport().createOptimizedCallTarget(source, rootNode);
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
    public CancellableCompileTask submitForCompilation(OptimizedCallTarget optimizedCallTarget) {
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

        if (SubstrateOptions.MultiThreaded.getValue()) {
            return super.submitForCompilation(optimizedCallTarget);
        }

        try {
            // Single threaded compilation does not require cancellation.
            doCompile(RuntimeOptionValues.singleton(), optimizedCallTarget, null);
        } catch (com.oracle.truffle.api.OptimizationFailedException e) {
            if (TruffleCompilationExceptionsArePrinted.getValue(RuntimeOptionValues.singleton())) {
                StringWriter string = new StringWriter();
                e.printStackTrace(new PrintWriter(string));
                Log.log().string(string.toString());
            }
        } finally {
            optimizedCallTarget.resetCompilationTask();
        }

        return null;
    }

    @Override
    public void finishCompilation(OptimizedCallTarget optimizedCallTarget, Future<?> future, boolean mayBeAsynchronous) {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            super.finishCompilation(optimizedCallTarget, future, mayBeAsynchronous);
        }
    }

    @Override
    public boolean cancelInstalledTask(OptimizedCallTarget optimizedCallTarget, Object source, CharSequence reason) {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            return super.cancelInstalledTask(optimizedCallTarget, source, reason);
        }

        return false;
    }

    @Override
    public void waitForCompilation(OptimizedCallTarget optimizedCallTarget, long timeout) throws ExecutionException, TimeoutException {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            super.waitForCompilation(optimizedCallTarget, timeout);
            return;
        }

        /* We have no background compilation, so nothing to do. */
    }

    @Override
    public boolean isCompiling(OptimizedCallTarget optimizedCallTarget) {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            return super.isCompiling(optimizedCallTarget);
        }

        return false;
    }

    @Override
    protected StackIntrospection getStackIntrospection() {
        return SubstrateStackIntrospection.SINGLETON;
    }

    @Override
    public OptionValues getInitialOptions() {
        return RuntimeOptionValues.singleton();
    }

    @Platforms(HOSTED_ONLY.class)
    public void resetNativeImageState() {
        clearState();
    }
}
