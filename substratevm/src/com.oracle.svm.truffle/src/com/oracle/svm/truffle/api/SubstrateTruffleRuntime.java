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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.options.OptionDescriptors;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.stack.SubstrateStackIntrospection;
import com.oracle.svm.truffle.TruffleSupport;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.impl.AbstractFastThreadLocal;
import com.oracle.truffle.api.impl.ThreadLocalHandshake;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.compiler.ConstantFieldInfo;
import com.oracle.truffle.compiler.HostMethodInfo;
import com.oracle.truffle.compiler.OptimizedAssumptionDependency;
import com.oracle.truffle.compiler.PartialEvaluationMethodInfo;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompiler;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.BackgroundCompileQueue;
import com.oracle.truffle.runtime.CompilationTask;
import com.oracle.truffle.runtime.EngineCacheSupport;
import com.oracle.truffle.runtime.EngineData;
import com.oracle.truffle.runtime.ModulesSupport;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedRuntimeOptions.ExceptionAction;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
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
        return SubstrateTruffleOptions.TruffleMultiThreaded.getValue();
    }
}

public final class SubstrateTruffleRuntime extends OptimizedTruffleRuntime {

    static {
        ModuleLayer layer = SubstrateTruffleRuntime.class.getModule().getLayer();
        if (layer != null) {
            Module enterpriseModule = layer.findModule("com.oracle.truffle.enterprise.svm").orElse(null);
            if (enterpriseModule != null) {
                ModulesSupport.exportJVMCI(enterpriseModule);
            }
        }
    }

    private static final int DEBUG_TEAR_DOWN_TIMEOUT = 2_000;
    private static final int PRODUCTION_TEAR_DOWN_TIMEOUT = 10_000;

    private KnownMethods hostedCallMethods;
    private volatile BackgroundCompileQueue compileQueue;
    private volatile boolean initialized;
    private volatile Boolean profilingEnabled;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTruffleRuntime() {
        super(new SubstrateTruffleCompilationSupport(), List.of());
    }

    @Override
    public BackgroundCompileQueue getCompileQueue() {
        return compileQueue;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void resetHosted() {
        truffleCompiler = null;
        engineOptions = null;
        initializeEngineCacheSupport(new EngineCacheSupport.Disabled());
    }

    @Override
    public void onCodeInstallation(TruffleCompilable compilable, InstalledCode installedCode) {
        throw CompilerDirectives.shouldNotReachHere("onCodeInstallation is not implemented by " + getClass().getName());
    }

    @Override
    public ThreadLocalHandshake getThreadLocalHandshake() {
        return SubstrateThreadLocalHandshake.SINGLETON;
    }

    @Override
    protected AbstractFastThreadLocal getFastThreadLocalImpl() {
        return SubstrateFastThreadLocal.SINGLETON;
    }

    private void initializeAtRuntime(OptimizedCallTarget callTarget) {
        truffleCompiler.initialize(callTarget, true);
        if (SubstrateTruffleOptions.isMultiThreaded()) {
            compileQueue = TruffleSupport.singleton().createBackgroundCompileQueue(this);
        }
        if (callTarget.engine.traceTransferToInterpreter) {
            Deoptimizer.Options.TraceDeoptimization.update(true);
        }
        installDefaultListeners();
        RuntimeSupport.getRuntimeSupport().addTearDownHook(isFirstIsolate -> teardown());
    }

    @Override
    protected EngineCacheSupport loadEngineCacheSupport(List<OptionDescriptors> options) {
        /*
         * On SVM we initialize engine caching support when the TruffleFeature is initialized. We
         * cannot do it reliably here as the SubstrateTruffleRuntime might already be initialized
         * when the TruffleBaseFeature is initialized, this is when the TruffleSupport is not yet
         * installed.
         */
        return null;
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public PartialEvaluationMethodInfo getPartialEvaluationMethodInfo(ResolvedJavaMethod method) {
        return super.getPartialEvaluationMethodInfo(method);
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public HostMethodInfo getHostMethodInfo(ResolvedJavaMethod method) {
        return super.getHostMethodInfo(method);
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public ConstantFieldInfo getConstantFieldInfo(ResolvedJavaField field) {
        return super.getConstantFieldInfo(field);
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
    public SubstrateTruffleCompiler preinitializeTruffleCompiler() {
        assert truffleCompiler == null : "Cannot re-initialize Substrate TruffleCompiler";
        ((SubstrateTruffleCompilationSupport) compilationSupport).preinitialize();
        SubstrateTruffleCompiler compiler = (SubstrateTruffleCompiler) newTruffleCompiler();
        truffleCompiler = compiler;
        return compiler;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTruffleCompiler getPreinitializedTruffleCompiler() {
        assert truffleCompiler != null;
        return (SubstrateTruffleCompiler) truffleCompiler;
    }

    public ResolvedJavaMethod[] getAnyFrameMethod() {
        return knownMethods.anyFrameMethod;
    }

    @Override
    public SubstrateTruffleCompiler getTruffleCompiler(TruffleCompilable compilable) {
        Objects.requireNonNull(compilable, "Compilable must be non null.");
        ensureInitializedAtRuntime((OptimizedCallTarget) compilable);
        return (SubstrateTruffleCompiler) truffleCompiler;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void initializeHostedKnownMethods(MetaAccessProvider hostedMetaAccess) {
        hostedCallMethods = new KnownMethods(hostedMetaAccess);
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    protected void clearState() {
        super.clearState();
        hostedCallMethods = null;
    }

    @Override
    public KnownMethods getKnownMethods() {
        if (SubstrateUtil.HOSTED) {
            return hostedCallMethods;
        } else {
            return knownMethods;
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

    @Override
    protected OptimizedCallTarget createInitializationCallTarget(EngineData engine) {
        return TruffleSupport.singleton().createOptimizedCallTarget(engine);
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
        /*
         * Nothing to do here. We print the stack trace in the Deoptimizer when the actual
         * deoptimization happened.
         */
    }

    @Override
    public boolean isProfilingEnabled() {
        if (profilingEnabled == null) {
            /*
             * Inlined profiles are initialized in static initializers when the runtime is not yet
             * initialized. We need to assume that profiling is enabled, if it is not yet set in the
             * runtime.
             */
            return Boolean.TRUE;
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
            if (optimizedCallTarget.engine.compilationFailureAction == ExceptionAction.Throw) {
                throw e;
            } else if (SubstrateTruffleOptions.TrufflePropagateCompilationErrors.getValue()) {
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
    public TruffleCompilable asCompilableTruffleAST(JavaConstant constant) {
        return TruffleSupport.singleton().asCompilableTruffleAST(constant);
    }

    @Override
    public void log(String loggerId, TruffleCompilable compilable, String message) {
        if (!TruffleSupport.singleton().tryLog(this, loggerId, compilable, message)) {
            super.log(loggerId, compilable, message);
        }
    }

    @Override
    public boolean isSuppressedFailure(TruffleCompilable compilable, Supplier<String> serializedException) {
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

    @Override
    public long getStackOverflowLimit() {
        StackOverflowCheck stackOverflowCheck = ImageSingletons.lookup(StackOverflowCheck.class);
        return stackOverflowCheck.getStackOverflowBoundary().rawValue();
    }

    @Override
    protected int getObjectAlignment() {
        return ConfigurationValues.getObjectLayout().getAlignment();
    }

    @Override
    protected int getArrayBaseOffset(Class<?> componentType) {
        return ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.fromJavaClass(componentType));
    }

    @Override
    protected int getArrayIndexScale(Class<?> componentType) {
        return ConfigurationValues.getObjectLayout().getArrayIndexScale(JavaKind.fromJavaClass(componentType));
    }

    @Override
    protected int getBaseInstanceSize(Class<?> type) {
        int le = DynamicHub.fromClass(type).getLayoutEncoding();
        return (int) LayoutEncoding.getPureInstanceAllocationSize(le).rawValue();
    }

    @Override
    protected int[] getFieldOffsets(Class<?> type, boolean includePrimitive, boolean includeSuperclasses) {
        if (type.isArray() || type.isPrimitive()) {
            throw new IllegalArgumentException("Class " + type.getName() + " is a primitive type or an array class!");
        }
        if (includePrimitive) {
            throw new IllegalArgumentException("Retrieval of primitive field offsets is not supported!");
        }
        if (!includeSuperclasses) {
            throw new IllegalArgumentException("Exclusion of field offsets from superclasses is not supported!");
        }

        List<Integer> fieldOffsets = new ArrayList<>();

        DynamicHub dh = DynamicHub.fromClass(type);
        boolean referenceInstanceClass = dh.isReferenceInstanceClass();
        int monitorOffset = dh.getMonitorOffset();
        InteriorObjRefWalker.walkInstanceReferenceOffsets(dh, (offset) -> {
            if (monitorOffset != 0 && offset == monitorOffset) {
                // Object monitor is not a proper field.
            } else if (referenceInstanceClass && ReferenceInternals.isAnyReferenceFieldOffset(offset)) {
                // Reference class field offsets must not be exposed.
            } else {
                fieldOffsets.add(offset);
            }
        });
        return fieldOffsets.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Compilation task used when Truffle runtime is run in single threaded mode.
     */
    private static class SingleThreadedCompilationTask extends AbstractCompilationTask {
        private final boolean lastTierCompilation;
        private final boolean hasNextTier;

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
        public boolean hasNextTier() {
            return hasNextTier;
        }

    }

}
