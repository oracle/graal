/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.graal.compiler.core.GraalCompilerOptions.PrintCompilation;
import static jdk.graal.compiler.core.phases.HighTier.Options.Inline;
import static jdk.graal.compiler.hotspot.CompilationTask.Options.MethodRecompilationLimit;
import static jdk.graal.compiler.java.BytecodeParserOptions.InlineDuringParsing;

import java.io.PrintStream;
import java.util.List;
import java.util.ListIterator;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.CompilationPrinter;
import jdk.graal.compiler.core.CompilationWatchDog;
import jdk.graal.compiler.core.CompilationWrapper;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.DebugContext.Description;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.debug.DebugDumpHandlersFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.hotspot.replaycomp.ReplayCompilationSupport;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.ProfileProvider;
import jdk.graal.compiler.nodes.spi.StableProfileProvider;
import jdk.graal.compiler.nodes.spi.StableProfileProvider.TypeFilter;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.DeoptimizationGroupingPhase;
import jdk.graal.compiler.phases.common.ForceDeoptSpeculationPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotInstalledCode;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotNmethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCICompiler;

public class CompilationTask implements CompilationWatchDog.EventHandler {

    public static class Options {
        @Option(help = """
                        Options which are enabled based on the method being compiled.
                        The basic syntax is a MethodFilter option specification followed by a list of options to be set for that compilation.
                        "MethodFilter:" is used to distinguish this from normal usage of MethodFilter as option.
                        This can be repeated multiple times with each MethodFilter option separating the groups.
                        For example:
                        "    -D""" + HotSpotGraalOptionValues.GRAAL_OPTION_PROPERTY_PREFIX + """
                        PerMethodOptions=MethodFilter:String.indexOf SpeculativeGuardMovement=false MethodFilter:Integer.* SpeculativeGuardMovement=false
                        disables SpeculativeGuardMovement for compiles of String.indexOf and all methods in Integer.
                        If the value starts with a non-letter character, that
                        character is used as the separator between options instead of a space.""")//
        public static final OptionKey<String> PerMethodOptions = new OptionKey<>(null);
        @Option(help = "Hard limit on the number of recompilations to avoid deopt loops. Exceeding the limit results in a permanent bailout. " + //
                        "Negative value means the limit is disabled. The default is -1 (disabled).", type = OptionType.Debug)//
        public static final OptionKey<Integer> MethodRecompilationLimit = new OptionKey<>(-1);
        @Option(help = "When the number of recompilations exceeds the limit, enable the detection of repeated identical deopts and report the source of the deopt loop when detected. " + // +
                        "Negative value means the limit is disabled. The default is -1 (disabled).", type = OptionType.Debug)//
        public static final OptionKey<Integer> DetectRecompilationLimit = new OptionKey<>(-1);
    }

    @Override
    public void onStuckCompilation(CompilationWatchDog watchDog, Thread watched, CompilationIdentifier compilation, StackTraceElement[] stackTrace, long stuckTime) {
        CompilationWatchDog.EventHandler.super.onStuckCompilation(watchDog, watched, compilation, stackTrace, stuckTime);
        TTY.println("Compilation %s on %s appears stuck - exiting VM", compilation, watched);
        HotSpotGraalServices.exit(STUCK_COMPILATION_EXIT_CODE, jvmciRuntime);
    }

    private final HotSpotJVMCIRuntime jvmciRuntime;

    protected final HotSpotGraalCompiler compiler;
    protected final HotSpotCompilationIdentifier compilationId;

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

    protected boolean checkRecompileCycle;

    protected final int decompileCount;

    /**
     * Filter describing which types in {@link JavaTypeProfile} should be considered for profile
     * writing. This allows programmatically changing which types are saved.
     */
    private TypeFilter profileSaveFilter;

    protected class HotSpotCompilationWrapper extends CompilationWrapper<HotSpotCompilationRequestResult> {
        protected CompilationResult result;
        protected StructuredGraph graph;

        protected HotSpotCompilationWrapper() {
            super(compiler.getGraalRuntime().getOutputDirectory(), compiler.getGraalRuntime().getCompilationProblemsPerAction());
        }

        @Override
        protected DebugContext createRetryDebugContext(DebugContext initialDebug, OptionValues retryOptions, PrintStream logStream) {
            SnippetReflectionProvider snippetReflection = compiler.getGraalRuntime().getHostProviders().getSnippetReflection();
            Description description = initialDebug.getDescription();
            DebugDumpHandlersFactory factory = new GraalDebugHandlersFactory(snippetReflection);
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
        protected void parseRetryOptions(String[] options, EconomicMap<OptionKey<?>, Object> values) {
            OptionsParser.parseOptions(options, values, OptionsParser.getOptionsLoader());
        }

        @Override
        protected HotSpotCompilationRequestResult handleException(Throwable t) {
            if (t instanceof BailoutException bailout) {
                /*
                 * Handling of permanent bailouts: Permanent bailouts that can happen for example
                 * due to unsupported unstructured control flow in the bytecodes of a method must
                 * not be retried. Hotspot compile broker will ensure that no recompilation at the
                 * given tier will happen if retry is false.
                 */
                return HotSpotCompilationRequestResult.failure(bailout.getMessage(), !bailout.isPermanent());
            }
            if (t instanceof ForceDeoptSpeculationPhase.TooManyDeoptimizationsError) {
                // Handle this as a permanent bailout
                return HotSpotCompilationRequestResult.failure(t.getMessage(), false);
            }

            /*
             * Treat random exceptions from the compiler as indicating a problem compiling this
             * method. Report the result of toString instead of getMessage to ensure that the
             * exception type is included in the output in case there's no detail message.
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
            if (cause instanceof BailoutException bailout) {
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
                if (shouldExitVM(cause)) {
                    TTY.println("Treating CompilationFailureAction as ExitVM due to assertion failure in libgraal: " + cause);
                    return ExitVM;
                }
            }
            return super.lookupAction(values, cause);
        }

        /**
         * Determines if {@code throwable} should result in a VM exit.
         */
        private static boolean shouldExitVM(Throwable throwable) {
            // If not in libgraal, don't exit
            if (LibGraalSupport.INSTANCE == null) {
                return false;
            }
            // If assertions are not enabled, don't exit.
            if (!Assertions.assertionsEnabled()) {
                return false;
            }
            // A normal assertion error => exit.
            if (throwable instanceof AssertionError) {
                return true;
            }
            // A GraalError not caused by an OOME => exit.
            if (throwable instanceof GraalError && isNotCausedByOOME(throwable)) {
                return true;
            }
            return false;
        }

        /**
         * Determines if {@code throwable} has a causality chain denoting an OutOfMemoryError. This
         * can happen in GC stress tests and exiting the VM would cause the test to fail.
         */
        private static boolean isNotCausedByOOME(Throwable throwable) {
            Throwable t = throwable;
            while (t != null) {
                if (t instanceof OutOfMemoryError) {
                    return false;
                }
                t = t.getCause();
            }
            return true;
        }

        @SuppressWarnings("try")
        @Override
        protected HotSpotCompilationRequestResult performCompilation(DebugContext debug) {
            HotSpotResolvedJavaMethod method = getMethod();
            if (ReplayCompilationSupport.matchesRecordCompilationFilter(debug.getOptions(), method) || compiler.getGraalRuntime().getReplayCompilationSupport() != null) {
                return performCompilationWithReplaySupport(debug);
            }

            int entryBCI = getEntryBCI();
            final boolean isOSR = entryBCI != JVMCICompiler.INVOCATION_ENTRY_BCI;
            CompilationStatistics stats = CompilationStatistics.create(debug.getOptions(), method, isOSR);

            final CompilationPrinter printer = CompilationPrinter.begin(debug.getOptions(), compilationId, method, entryBCI);

            try (DebugContext.Scope s = debug.scope("Compiling", new DebugDumpScope(getIdString(), true))) {
                graph = compiler.createGraph(method, entryBCI, profileProvider, compilationId, debug.getOptions(), debug);
                Suites suites = compiler.getSuites(compiler.getGraalRuntime().getHostProviders(), debug.getOptions());
                adjustSuitesForRecompilation(debug.getOptions(), suites);
                result = compiler.compile(graph, shouldRetainLocalVariables, shouldUsePreciseUnresolvedDeopts, eagerResolving, compilationId, debug, suites);
                performRecompilationCheck(debug.getOptions(), method);
            } catch (Throwable e) {
                throw debug.handle(e);
            }

            try (DebugCloseable b = CodeInstallationTime.start(debug)) {
                installMethod(compiler.getGraalRuntime().getHostBackend(), debug, graph, result);
            }
            // Installation is included in compilation time and memory usage reported by printer
            printer.finish(result, installedCode);

            stats.finish(method, installedCode);

            return buildCompilationRequestResult(method);
        }

        /**
         * Modifies the provided suites to prevent excessive recompilation if necessary.
         *
         * @param options the option values
         * @param suites the suites to modify
         */
        private void adjustSuitesForRecompilation(OptionValues options, Suites suites) {
            if (checkRecompileCycle && (MethodRecompilationLimit.getValue(options) < 0 || decompileCount < MethodRecompilationLimit.getValue(options))) {
                /*
                 * Disable DeoptimizationGroupingPhase to simplify the creation of the speculations
                 * for each deopt.
                 */
                ListIterator<BasePhase<? super MidTierContext>> phase = suites.getMidTier().findPhase(DeoptimizationGroupingPhase.class);
                if (phase != null) {
                    phase.remove();
                }
                ListIterator<BasePhase<? super LowTierContext>> lowTierPhasesIterator = suites.getLowTier().findPhase(SchedulePhase.FinalSchedulePhase.class);
                if (lowTierPhasesIterator != null) {
                    lowTierPhasesIterator.previous();
                    lowTierPhasesIterator.add(new ForceDeoptSpeculationPhase(decompileCount));
                }
            }
        }

        /**
         * Checks whether the recompilation limit is exceeded, and if so, throws an exception.
         *
         * @param options the option values
         * @param method the compiled method
         */
        private void performRecompilationCheck(OptionValues options, HotSpotResolvedJavaMethod method) {
            if (checkRecompileCycle && (MethodRecompilationLimit.getValue(options) >= 0 && decompileCount >= MethodRecompilationLimit.getValue(options))) {
                ProfilingInfo info = profileProvider.getProfilingInfo(method);
                throw new ForceDeoptSpeculationPhase.TooManyDeoptimizationsError("too many decompiles: " + decompileCount + " " + ForceDeoptSpeculationPhase.getDeoptSummary(info));
            }
        }

        private static final TimerKey CompilationReplayTime = DebugContext.timer("CompilationReplayTime").doc("The time spent in recorded/replayed compilations.");

        private static final CounterKey CompilationReplayBytecodes = DebugContext.counter("CompilationReplayBytecodes").doc("The size of bytecodes compiled in recorded/replayed compilations.");

        /**
         * Performs a recorded or replayed compilation.
         *
         * @param initialDebug the initial debug context
         * @return the compilation result
         */
        @SuppressWarnings("try")
        private HotSpotCompilationRequestResult performCompilationWithReplaySupport(DebugContext initialDebug) {
            OptionValues options = initialDebug.getOptions();
            HotSpotGraalCompiler selectedCompiler;
            if (compiler.getGraalRuntime().getReplayCompilationSupport() != null) {
                selectedCompiler = compiler;
            } else {
                CompilerConfigurationFactory configFactory = CompilerConfigurationFactory.selectFactory(compiler.getGraalRuntime().getCompilerConfigurationName(), options, jvmciRuntime);
                selectedCompiler = HotSpotGraalCompilerFactory.createCompiler("VM-record", jvmciRuntime, options, configFactory, ReplayCompilationSupport.createRecording(configFactory.getName()));
            }
            ReplayCompilationSupport replaySupport = selectedCompiler.getGraalRuntime().getReplayCompilationSupport();
            HotSpotCompilationRequest request = getRequest();
            try (DebugCloseable closeable = replaySupport.enterCompilationContext(request, options)) {
                request = replaySupport.decorateCompilationRequest(request);
                HotSpotResolvedJavaMethod method = request.getMethod();
                /*
                 * Passing a snippet reflection instance to the debug handlers would cause replay
                 * failures.
                 */
                List<DebugDumpHandlersFactory> debugHandlersFactories = List.of(new GraalDebugHandlersFactory(null));
                PrintStream selectedPrintStream = initialDebug.getConfig() == null ? DebugContext.getDefaultLogStream() : initialDebug.getConfig().output();
                try (DebugContext debug = selectedCompiler.getGraalRuntime().openDebugContext(options, compilationId, method, debugHandlersFactories, selectedPrintStream);
                                DebugContext.Activation a = debug.activate();
                                DebugCloseable d = replaySupport.withDebugContext(debug);
                                DebugCloseable c = initialDebug.inRetryCompilation() ? debug.openRetryCompilation() : null;
                                DebugCloseable t = CompilationReplayTime.start(debug)) {
                    int entryBCI = getEntryBCI();
                    boolean isOSR = entryBCI != JVMCICompiler.INVOCATION_ENTRY_BCI;
                    CompilationStatistics stats = CompilationStatistics.create(options, method, isOSR);
                    CompilationPrinter printer = CompilationPrinter.begin(options, compilationId, method, entryBCI);
                    if (initialDebug.inRetryCompilation()) {
                        profileProvider.forQueriedProfiles((profileKey, profilingInfo) -> {
                            replaySupport.injectProfiles(profileKey.method(), profileKey.includeNormal(), profileKey.includeOSR(), profilingInfo);
                        });
                    }
                    ProfileProvider selectedProfileProvider = new StableProfileProvider();
                    try (DebugContext.Scope s = debug.scope("Compiling with replay support", new DebugDumpScope(getIdString(), true))) {
                        graph = selectedCompiler.createGraph(method, entryBCI, selectedProfileProvider, compilationId, options, debug);
                        Suites suites = compiler.getSuites(compiler.getGraalRuntime().getHostProviders(), debug.getOptions());
                        adjustSuitesForRecompilation(options, suites);
                        result = selectedCompiler.compile(graph, shouldRetainLocalVariables, shouldUsePreciseUnresolvedDeopts, eagerResolving, compilationId, debug, suites);
                        performRecompilationCheck(options, method);
                        CompilationReplayBytecodes.add(debug, result.getBytecodeSize());
                    } catch (Throwable e) {
                        throw debug.handle(e);
                    }
                    try (DebugCloseable b = CodeInstallationTime.start(debug)) {
                        installMethod(selectedCompiler.getGraalRuntime().getHostBackend(), debug, graph, result);
                    }
                    printer.finish(result, installedCode);
                    stats.finish(method, installedCode);
                    replaySupport.recordCompilationArtifacts(graph, result);
                    return buildCompilationRequestResult(method);
                }
            }
        }

        protected HotSpotCompilationRequestResult buildCompilationRequestResult(HotSpotResolvedJavaMethod method) {
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
        this.decompileCount = HotSpotGraalServices.getDecompileCount(request.getMethod());
    }

    public void setTypeFilter(TypeFilter typeFilter) {
        this.profileSaveFilter = typeFilter;
    }

    public OptionValues filterOptions(OptionValues originalOptions) {
        HotSpotGraalRuntimeProvider graalRuntime = compiler.getGraalRuntime();
        GraalHotSpotVMConfig config = graalRuntime.getVMConfig();
        OptionValues newOptions = originalOptions;

        // Set any options for this compile.
        String perMethodOptions = Options.PerMethodOptions.getValue(originalOptions);
        if (perMethodOptions != null) {
            EconomicMap<OptionKey<?>, Object> values = null;
            try {
                EconomicMap<String, String> optionSettings = null;
                for (String option : OptionsParser.splitOptions(perMethodOptions)) {
                    String prefix = "MethodFilter:";
                    if (option.startsWith(prefix)) {
                        MethodFilter filter = MethodFilter.parse(option.substring(prefix.length()));
                        if (filter.matches(getMethod())) {
                            // Begin accumulating options
                            optionSettings = EconomicMap.create();
                        } else if (optionSettings != null) {
                            // This is a new MethodFilter: so stop collecting options
                            break;
                        }
                    } else if (optionSettings != null) {
                        OptionsParser.parseOptionSettingTo(option, optionSettings);
                    }
                }
                if (optionSettings != null) {
                    if (optionSettings.isEmpty()) {
                        throw new IllegalArgumentException("No options specified for MethodFilter:");
                    }
                    values = EconomicMap.create();
                    OptionsParser.parseOptions(optionSettings, values, OptionsParser.getOptionsLoader());
                }
            } catch (Exception e) {
                values = null;
                TTY.println(e.toString());
                TTY.println("Errors encountered during " + Options.PerMethodOptions.getName() + " parsing.  Exiting...");
                HotSpotGraalServices.exit(-1, jvmciRuntime);
            }

            if (values != null) {
                newOptions = new OptionValues(newOptions, values);
                if (PrintCompilation.getValue(newOptions)) {
                    TTY.println("Compiling " + getMethod() + " with extra options: " + new OptionValues(values));
                }
            }
        }
        /*
         * Disable inlining if HotSpot has it disabled unless it's been explicitly set in Graal.
         */
        if (!config.inline) {
            EconomicMap<OptionKey<?>, Object> m = OptionValues.newOptionMap();
            if (Inline.getValue(newOptions) && !Inline.hasBeenSet(newOptions)) {
                m.put(Inline, false);
            }
            if (InlineDuringParsing.getValue(newOptions) && !InlineDuringParsing.hasBeenSet(newOptions)) {
                m.put(InlineDuringParsing, false);
            }
            if (!m.isEmpty()) {
                newOptions = new OptionValues(newOptions, m);
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

    public StableProfileProvider getProfileProvider() {
        return profileProvider;
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

    @SuppressWarnings({"try"})
    public HotSpotCompilationRequestResult runCompilation(DebugContext debug) {
        try (DebugCloseable a = CompilationTime.start(debug); DebugCloseable b = GraalServices.GCTimerScope.create(debug)) {
            HotSpotCompilationRequestResult result = runCompilation(debug, new HotSpotCompilationWrapper());
            LibGraalSupport libgraal = LibGraalSupport.INSTANCE;
            if (libgraal != null) {
                /*
                 * Notify the libgraal runtime that most objects allocated in the current
                 * compilation are dead and can be reclaimed.
                 */
                try (DebugCloseable timer = HintedFullGC.start(debug)) {
                    libgraal.notifyLowMemoryPoint(true);
                    libgraal.processReferences();
                }
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

        ProfileReplaySupport result = ProfileReplaySupport.profileReplayPrologue(debug, entryBCI, method, profileProvider, profileSaveFilter);
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
    protected void installMethod(HotSpotBackend backend, DebugContext debug, StructuredGraph graph, final CompilationResult compResult) {
        installedCode = null;
        Object[] context = {new DebugDumpScope(getIdString(), true), backend.getProviders().getCodeCache(), getMethod(), compResult};
        try (DebugContext.Scope s = debug.scope("CodeInstall", context, graph)) {
            HotSpotCompilationRequest request = getRequest();
            // By default, we only profile deoptimizations for compiled methods installed as
            // default.
            installedCode = (HotSpotInstalledCode) backend.createInstalledCode(debug,
                            request.getMethod(),
                            request,
                            compResult,
                            null,
                            installAsDefault,
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
