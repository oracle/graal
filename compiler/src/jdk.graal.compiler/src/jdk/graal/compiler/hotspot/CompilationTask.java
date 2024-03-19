/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import static jdk.graal.compiler.core.CompilationWrapper.ExceptionAction.Diagnose;
import static jdk.graal.compiler.core.CompilationWrapper.ExceptionAction.ExitVM;
import static jdk.graal.compiler.core.GraalCompilerOptions.CompilationBailoutAsFailure;
import static jdk.graal.compiler.core.GraalCompilerOptions.CompilationFailureAction;
import static jdk.graal.compiler.core.phases.HighTier.Options.Inline;
import static jdk.graal.compiler.java.BytecodeParserOptions.InlineDuringParsing;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;

import java.io.PrintStream;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.CompilationPrinter;
import jdk.graal.compiler.core.CompilationWatchDog;
import jdk.graal.compiler.core.CompilationWrapper;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.DebugContext.Description;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.debug.DebugHandlersFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.StableProfileProvider;
import jdk.graal.compiler.nodes.spi.StableProfileProvider.TypeFilter;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotInstalledCode;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotNmethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCICompiler;

public class CompilationTask implements CompilationWatchDog.EventHandler {

    static class Options {
        @Option(help = "Perform a full GC of the libgraal heap after every compile to reduce idle heap and reclaim " +
                        "references to the HotSpot heap.  This flag has no effect in the context of jargraal.", type = OptionType.Debug)//
        public static final OptionKey<Boolean> FullGCAfterCompile = new OptionKey<>(false);
    }

    @Override
    public void onStuckCompilation(CompilationWatchDog watchDog, Thread watched, CompilationIdentifier compilation, StackTraceElement[] stackTrace, int stuckTime) {
        CompilationWatchDog.EventHandler.super.onStuckCompilation(watchDog, watched, compilation, stackTrace, stuckTime);
        TTY.println("Compilation %s on %s appears stuck - exiting VM", compilation, watched);
        HotSpotGraalServices.exit(STUCK_COMPILATION_EXIT_CODE, jvmciRuntime);
    }

    private final HotSpotJVMCIRuntime jvmciRuntime;

    protected final HotSpotGraalCompiler compiler;
    private final HotSpotCompilationIdentifier compilationId;

    private HotSpotInstalledCode installedCode;

    /**
     * Specifies whether the compilation result is installed as the
     * {@linkplain HotSpotNmethod#isDefault() default} nmethod for the compiled method.
     */
    private final boolean installAsDefault;

    private final StableProfileProvider profileProvider;

    private final boolean shouldRetainLocalVariables;
    private final boolean shouldUsePreciseUnresolvedDeopts;

    private final boolean eagerResolving;

    /**
     * Filter describing which types in {@link JavaTypeProfile} should be considered for profile
     * writing. This allows programmatically changing which types are saved.
     */
    private TypeFilter profileSaveFilter;

    protected class HotSpotCompilationWrapper extends CompilationWrapper<HotSpotCompilationRequestResult> {
        CompilationResult result;
        StructuredGraph graph;

        protected HotSpotCompilationWrapper() {
            super(compiler.getGraalRuntime().getOutputDirectory(), compiler.getGraalRuntime().getCompilationProblemsPerAction());
        }

        @Override
        protected DebugContext createRetryDebugContext(DebugContext initialDebug, OptionValues retryOptions, PrintStream logStream) {
            SnippetReflectionProvider snippetReflection = compiler.getGraalRuntime().getHostProviders().getSnippetReflection();
            Description description = initialDebug.getDescription();
            DebugHandlersFactory factory = new GraalDebugHandlersFactory(snippetReflection);
            return new Builder(retryOptions, factory).globalMetrics(initialDebug.getGlobalMetrics()).description(description).logStream(logStream).build();
        }

        @Override
        protected void exitHostVM(int status) {
            HotSpotGraalServices.exit(status, jvmciRuntime);
        }

        @Override
        public String toString() {
            return getMethod().format("%H.%n(%p) @ " + getEntryBCI());
        }

        @Override
        protected HotSpotCompilationRequestResult handleException(Throwable t) {
            if (t instanceof BailoutException) {
                BailoutException bailout = (BailoutException) t;
                /*
                 * Handling of permanent bailouts: Permanent bailouts that can happen for example
                 * due to unsupported unstructured control flow in the bytecodes of a method must
                 * not be retried. Hotspot compile broker will ensure that no recompilation at the
                 * given tier will happen if retry is false.
                 */
                return HotSpotCompilationRequestResult.failure(bailout.getMessage(), !bailout.isPermanent());
            }

            /*
             * Treat random exceptions from the compiler as indicating a problem compiling this
             * method. Report the result of toString instead of getMessage to ensure that the
             * exception type is included in the output in case there's no detail mesage.
             */
            return HotSpotCompilationRequestResult.failure(t.toString(), false);
        }

        @SuppressWarnings("try")
        @Override
        protected void dumpOnError(DebugContext errorContext, Throwable cause) {
            if (graph != null) {
                try (DebugContext.Scope s = errorContext.scope("DumpOnError", graph, new DebugDumpScope(getIdString(), true), new DebugDumpScope("Original failure"))) {
                    errorContext.forceDump(graph, "Exception: %s", cause);
                } catch (Throwable t) {
                    throw errorContext.handle(t);
                }
            }
        }

        @Override
        protected ExceptionAction lookupAction(OptionValues values, Throwable cause) {
            if (cause instanceof BailoutException) {
                BailoutException bailout = (BailoutException) cause;
                if (bailout.isPermanent()) {
                    // Respect current action if it has been explicitly set.
                    if (!CompilationBailoutAsFailure.hasBeenSet(values)) {
                        // Get more info for permanent bailouts during bootstrap.
                        if (compiler.getGraalRuntime().isBootstrapping()) {
                            return Diagnose;
                        }

                    }
                }
                if (!CompilationBailoutAsFailure.getValue(values)) {
                    return super.lookupAction(values, cause);
                }
            }

            // Respect current action if it has been explicitly set.
            if (!CompilationFailureAction.hasBeenSet(values)) {
                // Automatically exit on failure during bootstrap.
                if (compiler.getGraalRuntime().isBootstrapping()) {
                    TTY.println("Treating CompilationFailureAction as ExitVM due to exception throw during bootstrap: " + cause);
                    return ExitVM;
                }
                // Automatically exit on failure when assertions are enabled in libgraal
                if (IS_IN_NATIVE_IMAGE && (cause instanceof AssertionError || cause instanceof GraalError) && Assertions.assertionsEnabled()) {
                    TTY.println("Treating CompilationFailureAction as ExitVM due to assertion failure in libgraal: " + cause);
                    return ExitVM;
                }
            }
            return super.lookupAction(values, cause);
        }

        @SuppressWarnings("try")
        @Override
        protected HotSpotCompilationRequestResult performCompilation(DebugContext debug) {
            HotSpotResolvedJavaMethod method = getMethod();
            int entryBCI = getEntryBCI();
            final boolean isOSR = entryBCI != JVMCICompiler.INVOCATION_ENTRY_BCI;
            CompilationStatistics stats = CompilationStatistics.create(debug.getOptions(), method, isOSR);

            final CompilationPrinter printer = CompilationPrinter.begin(debug.getOptions(), compilationId, method, entryBCI);

            try (DebugContext.Scope s = debug.scope("Compiling", new DebugDumpScope(getIdString(), true))) {
                graph = compiler.createGraph(method, entryBCI, profileProvider, compilationId, debug.getOptions(), debug);
                result = compiler.compile(graph, shouldRetainLocalVariables, shouldUsePreciseUnresolvedDeopts, eagerResolving, compilationId, debug);
            } catch (Throwable e) {
                throw debug.handle(e);
            }

            try (DebugCloseable b = CodeInstallationTime.start(debug)) {
                installMethod(debug, graph, result);
            }
            // Installation is included in compilation time and memory usage reported by printer
            printer.finish(result, installedCode);

            stats.finish(method, installedCode);

            // For compilation of substitutions the method in the compilation request might be
            // different than the actual method parsed. The root of the compilation will always
            // be the first method in the methods list, so use that instead.
            ResolvedJavaMethod rootMethod = result.getMethods()[0];
            int inlinedBytecodes = result.getBytecodeSize() - rootMethod.getCodeSize();
            assert inlinedBytecodes >= 0 : rootMethod + " " + method;
            return HotSpotCompilationRequestResult.success(inlinedBytecodes);
        }

    }

    public CompilationTask(HotSpotJVMCIRuntime jvmciRuntime,
                    HotSpotGraalCompiler compiler,
                    HotSpotCompilationRequest request,
                    boolean useProfilingInfo,
                    boolean installAsDefault) {
        this(jvmciRuntime, compiler, request, useProfilingInfo, false, false, false, installAsDefault);
    }

    public CompilationTask(HotSpotJVMCIRuntime jvmciRuntime,
                    HotSpotGraalCompiler compiler,
                    HotSpotCompilationRequest request,
                    boolean useProfilingInfo,
                    boolean shouldRetainLocalVariables,
                    boolean shouldUsePreciseUnresolvedDeopts,
                    boolean installAsDefault) {
        this(jvmciRuntime, compiler, request, useProfilingInfo, shouldRetainLocalVariables, shouldUsePreciseUnresolvedDeopts, false, installAsDefault);
    }

    public CompilationTask(HotSpotJVMCIRuntime jvmciRuntime,
                    HotSpotGraalCompiler compiler,
                    HotSpotCompilationRequest request,
                    boolean useProfilingInfo,
                    boolean shouldRetainLocalVariables,
                    boolean shouldUsePreciseUnresolvedDeopts,
                    boolean eagerResolving,
                    boolean installAsDefault) {
        this.jvmciRuntime = jvmciRuntime;
        this.compiler = compiler;
        this.compilationId = new HotSpotCompilationIdentifier(request);
        this.profileProvider = useProfilingInfo ? new StableProfileProvider() : null;
        this.shouldRetainLocalVariables = shouldRetainLocalVariables;
        this.shouldUsePreciseUnresolvedDeopts = shouldUsePreciseUnresolvedDeopts;
        this.eagerResolving = eagerResolving;
        this.installAsDefault = installAsDefault;
    }

    public void setTypeFilter(TypeFilter typeFilter) {
        this.profileSaveFilter = typeFilter;
    }

    public OptionValues filterOptions(OptionValues options) {
        /*
         * Disable inlining if HotSpot has it disabled unless it's been explicitly set in Graal.
         */
        HotSpotGraalRuntimeProvider graalRuntime = compiler.getGraalRuntime();
        GraalHotSpotVMConfig config = graalRuntime.getVMConfig();
        OptionValues newOptions = options;
        if (!config.inline) {
            EconomicMap<OptionKey<?>, Object> m = OptionValues.newOptionMap();
            if (Inline.getValue(options) && !Inline.hasBeenSet(options)) {
                m.put(Inline, false);
            }
            if (InlineDuringParsing.getValue(options) && !InlineDuringParsing.hasBeenSet(options)) {
                m.put(InlineDuringParsing, false);
            }
            if (!m.isEmpty()) {
                newOptions = new OptionValues(options, m);
            }
        }
        return newOptions;
    }

    public HotSpotResolvedJavaMethod getMethod() {
        return getRequest().getMethod();
    }

    CompilationIdentifier getCompilationIdentifier() {
        return compilationId;
    }

    /**
     * Returns the HotSpot id of this compilation.
     *
     * @return HotSpot compile id
     */
    public int getId() {
        return getRequest().getId();
    }

    public int getEntryBCI() {
        return getRequest().getEntryBCI();
    }

    /**
     * @return the compilation id plus a trailing '%' if the compilation is an OSR to match
     *         PrintCompilation style output
     */
    public String getIdString() {
        if (getEntryBCI() != JVMCICompiler.INVOCATION_ENTRY_BCI) {
            return getId() + "%";
        } else {
            return Integer.toString(getId());
        }
    }

    public HotSpotInstalledCode getInstalledCode() {
        return installedCode;
    }

    /**
     * Time spent in compilation.
     */
    public static final TimerKey CompilationTime = DebugContext.timer("CompilationTime").doc("Time spent in compilation and code installation.");

    /**
     * Counts the number of compiled {@linkplain CompilationResult#getBytecodeSize() bytecodes}.
     */
    private static final CounterKey CompiledBytecodes = DebugContext.counter("CompiledBytecodes");

    /**
     * Counts the number of compiled {@linkplain CompilationResult#getBytecodeSize() bytecodes} for
     * which {@linkplain CompilationResult#getTargetCode()} code was installed.
     */
    public static final CounterKey CompiledAndInstalledBytecodes = DebugContext.counter("CompiledAndInstalledBytecodes");

    /**
     * Counts the number of installed {@linkplain CompilationResult#getTargetCodeSize()} bytes.
     */
    private static final CounterKey InstalledCodeSize = DebugContext.counter("InstalledCodeSize");

    /**
     * Time spent in code installation.
     */
    public static final TimerKey CodeInstallationTime = DebugContext.timer("CodeInstallation");

    /**
     * Time spent in hinted full GC.
     */
    public static final TimerKey HintedFullGC = DebugContext.timer("HintedFullGC").doc("Time spent in hinted GC performed at the end of compilations.");

    public HotSpotCompilationRequestResult runCompilation(OptionValues initialOptions) {
        OptionValues options = filterOptions(initialOptions);
        HotSpotGraalRuntimeProvider graalRuntime = compiler.getGraalRuntime();
        try (DebugContext debug = graalRuntime.openDebugContext(options, compilationId, getMethod(), compiler.getDebugHandlersFactories(), DebugContext.getDefaultLogStream())) {
            return runCompilation(debug);
        }
    }

    @SuppressWarnings({"try", "unchecked"})
    public HotSpotCompilationRequestResult runCompilation(DebugContext debug) {
        try (DebugCloseable a = CompilationTime.start(debug)) {
            HotSpotCompilationRequestResult result = runCompilation(debug, new HotSpotCompilationWrapper());

            // Notify the runtime that most objects allocated in the current compilation
            // are dead and can be reclaimed.
            try (DebugCloseable timer = HintedFullGC.start(debug)) {
                GraalServices.notifyLowMemoryPoint(Options.FullGCAfterCompile.getValue(debug.getOptions()));
            }
            return result;
        }
    }

    @SuppressWarnings({"try", "unchecked"})
    protected HotSpotCompilationRequestResult runCompilation(DebugContext debug, HotSpotCompilationWrapper compilation) {
        HotSpotGraalRuntimeProvider graalRuntime = compiler.getGraalRuntime();
        GraalHotSpotVMConfig config = graalRuntime.getVMConfig();
        int entryBCI = getEntryBCI();
        boolean isOSR = entryBCI != JVMCICompiler.INVOCATION_ENTRY_BCI;
        HotSpotResolvedJavaMethod method = getMethod();

        if (installAsDefault || isOSR) {
            // If there is already compiled code for this method on our level we simply return.
            // JVMCI compiles are always at the highest compile level, even in non-tiered mode so we
            // only need to check for that value.
            if (method.hasCodeAtLevel(entryBCI, config.compilationLevelFullOptimization)) {
                return HotSpotCompilationRequestResult.failure("Already compiled", false);
            }
            if (HotSpotGraalCompilerFactory.shouldExclude(method)) {
                return HotSpotCompilationRequestResult.failure("GraalCompileOnly excluded", false);
            }
        }

        ProfileReplaySupport result = ProfileReplaySupport.profileReplayPrologue(debug, graalRuntime.getHostProviders(), entryBCI, method, profileProvider, profileSaveFilter);
        try {
            return compilation.run(debug);
        } finally {
            try {
                if (compilation.result != null) {
                    int compiledBytecodes = compilation.result.getBytecodeSize();
                    CompiledBytecodes.add(debug, compiledBytecodes);
                    if (installedCode != null) {
                        int codeSize = installedCode.getSize();
                        CompiledAndInstalledBytecodes.add(debug, compiledBytecodes);
                        InstalledCodeSize.add(debug, codeSize);
                    }
                    if (result != null && result.getExpectedResult() != null && !result.getExpectedResult()) {
                        TTY.printf("Expected failure: %s %s%n", method.format("%H.%n(%P)%R"), entryBCI);
                    }
                }
                if (result != null) {
                    result.profileReplayEpilogue(debug, compilation.result, compilation.graph, profileProvider, compilationId, entryBCI, method);
                }
            } catch (Throwable t) {
                return compilation.handleException(t);
            }
        }
    }

    @SuppressWarnings("try")
    private void installMethod(DebugContext debug, StructuredGraph graph, final CompilationResult compResult) {
        final CodeCacheProvider codeCache = jvmciRuntime.getHostJVMCIBackend().getCodeCache();
        HotSpotBackend backend = compiler.getGraalRuntime().getHostBackend();
        installedCode = null;
        Object[] context = {new DebugDumpScope(getIdString(), true), codeCache, getMethod(), compResult};
        try (DebugContext.Scope s = debug.scope("CodeInstall", context, graph)) {
            HotSpotCompilationRequest request = getRequest();
            installedCode = (HotSpotInstalledCode) backend.createInstalledCode(debug,
                            request.getMethod(),
                            request,
                            compResult,
                            null,
                            installAsDefault,
                            context);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    @Override
    public String toString() {
        return "Compilation[id=" + getId() + ", " + getMethod().format("%H.%n(%p)") + (getEntryBCI() == JVMCICompiler.INVOCATION_ENTRY_BCI ? "" : "@" + getEntryBCI()) + "]";
    }

    private HotSpotCompilationRequest getRequest() {
        return compilationId.getRequest();
    }
}
