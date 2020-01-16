/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import java.util.Formatter;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsAreFatal;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InlineAcrossTruffleBoundary;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Inlining;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningCutoffCountPenalty;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningExpandAllProximityBonus;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningExpandAllProximityFactor;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningExpansionBudget;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningExpansionCounterPressure;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningInliningBudget;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningInliningCounterPressure;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningNodeCountPenalty;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningPolicy;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningRecursionDepth;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InstrumentFilter;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InstrumentationTableSize;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.IterativePartialEscape;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.LanguageAgnosticInlining;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MaximumInlineNodeCount;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MaximumGraalNodeCount;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PerformanceWarningsAreFatal;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PrintExpansionHistogram;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceInlining;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceInliningDetails;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TracePerformanceWarnings;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceStackTraceLimit;

import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ArgumentTypeSpeculation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.BackgroundCompilation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Compilation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsArePrinted;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsAreThrown;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationStatistics;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationStatisticDetails;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompileImmediately;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompileOnly;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilerThreads;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.FirstTierCompilationThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.FirstTierMinInvokeThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningNodeBudget;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InvalidationReprofileCount;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MinInvokeThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MultiTier;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.OSR;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.OSRCompilationThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Profiling;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ReplaceReprofileCount;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ReturnTypeSpeculation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Splitting;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingAllowForcedSplits;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingDumpDecisions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingGrowthLimit;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingMaxCalleeSize;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingMaxNumberOfSplitNodes;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingMaxPropagationDepth;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingTraceEvents;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceAssumptions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceCompilation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceCompilationAST;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceCompilationCallTree;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceCompilationDetails;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceCompilationPolymorphism;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceSplitting;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceSplittingSummary;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceTransferToInterpreter;

import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionStability;
import org.graalvm.compiler.truffle.common.SharedTruffleOptions;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.options.OptionValuesImpl;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;

import jdk.vm.ci.common.NativeImageReinitialize;

/**
 * Options for the Truffle compiler. Options shared with the Truffle runtime are declared in
 * {@link SharedTruffleCompilerOptions}.
 */
@SharedTruffleOptions(name = "SharedTruffleCompilerOptions", runtime = false)
public final class TruffleCompilerOptions {

    // @formatter:off
    // configuration

    @Option(help = "Exclude assertion code from Truffle compilations", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleExcludeAssertions = new OptionKey<>(true);

    @Option(help = "Enable inlining across Truffle boundary", type = OptionType.Expert)
    public static final OptionKey<Boolean> TruffleInlineAcrossTruffleBoundary = new OptionKey<>(false);

    @Option(help = "Print potential performance problems", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceTrufflePerformanceWarnings = new OptionKey<>(false);

    @Option(help = "Prints a histogram of all expanded Java methods.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintTruffleExpansionHistogram = new OptionKey<>(false);

    @Option(help = "Enable support for simple infopoints in truffle partial evaluations.", type = OptionType.Expert)
    public static final OptionKey<Boolean> TruffleEnableInfopoints = new OptionKey<>(false);

    @Option(help = "Run the partial escape analysis iteratively in Truffle compilation.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleIterativePartialEscape = new OptionKey<>(false);

    @Option(help = "Instrument branches and output profiling information to the standard output.")
    public static final OptionKey<Boolean> TruffleInstrumentBranches = new OptionKey<>(false);

    @Option(help = "Instrument branches by considering different inlining sites as different branches.")
    public static final OptionKey<Boolean> TruffleInstrumentBranchesPerInlineSite = new OptionKey<>(false);

    @Option(help = "Instrument Truffle boundaries and output profiling information to the standard output.")
    public static final OptionKey<Boolean> TruffleInstrumentBoundaries = new OptionKey<>(false);

    @Option(help = "Instrument Truffle boundaries by considering different inlining sites as different branches.")
    public static final OptionKey<Boolean> TruffleInstrumentBoundariesPerInlineSite = new OptionKey<>(false);

    @Option(help = "Method filter for host methods in which to add instrumentation.")
    public static final OptionKey<String> TruffleInstrumentFilter = new OptionKey<>("*.*.*");

    @Option(help = "Maximum number of instrumentation counters available.")
    public static final OptionKey<Integer> TruffleInstrumentationTableSize = new OptionKey<>(10000);

    @Option(help = "Stop partial evaluation when the graph exceeded this many nodes.")
    public static final OptionKey<Integer> TruffleMaximumGraalNodeCount = new OptionKey<>(400000);

    @Option(help = "Ignore further truffle inlining decisions when the graph exceeded this many nodes.")
    public static final OptionKey<Integer> TruffleMaximumInlineNodeCount = new OptionKey<>(150000);

    @Option(help = "Intrinsify get/set/is methods of FrameWithoutBoxing to improve Truffle compilation time", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleIntrinsifyFrameAccess = new OptionKey<>(true);

    // Language agnostic inlining

    @Option(help = "Print detailed information for inlining (i.e. the entire explored call tree).", type = OptionType.Expert)
    public static final OptionKey<Boolean> TraceTruffleInliningDetails = new OptionKey<>(false);

    @Option(help = "Explicitly pick a inlining policy by name. Highest priority chosen by default.", type = OptionType.Expert)
    public static final OptionKey<String> TruffleInliningPolicy = new OptionKey<>("");

    @Option(help = "The base expansion budget for language-agnostic inlining.", type = OptionType.Expert)
    public static final OptionKey<Integer> TruffleInliningExpansionBudget = new OptionKey<>(50_000);

    @Option(help = "The base inlining budget for language-agnostic inlining", type = OptionType.Expert)
    public static final OptionKey<Integer> TruffleInliningInliningBudget = new OptionKey<>(50_000);

    @Option(help = "Controls how impactful many cutoff nodes is on exploration decision in language-agnostic inlining.", type = OptionType.Expert, stability = OptionStability.EXPERIMENTAL)
    public static final OptionKey<Double> TruffleInliningCutoffCountPenalty = new OptionKey<>(0.1);

    @Option(help = "Controls how impactful the size of the subtree is on exploration decision in language-agnostic inlining.", type = OptionType.Expert, stability = OptionStability.EXPERIMENTAL)
    public static final OptionKey<Double> TruffleInliningNodeCountPenalty = new OptionKey<>(0.1);

    @Option(help = "Controls how impactful few cutoff nodes are on exploration decisions in language-agnostic inlining.", type = OptionType.Expert, stability = OptionStability.EXPERIMENTAL)
    public static final OptionKey<Double> TruffleInliningExpandAllProximityFactor = new OptionKey<>(0.5);

    @Option(help = "Controls at what point few cutoff nodes are impactful on exploration decisions in language-agnostic inlining.", type = OptionType.Expert, stability = OptionStability.EXPERIMENTAL)
    public static final OptionKey<Integer> TruffleInliningExpandAllProximityBonus = new OptionKey<>(10);

    @Option(help = "Controls how steep the exploration limit curve grows in language-agnostic inlining.", type = OptionType.Expert, stability = OptionStability.EXPERIMENTAL)
    public static final OptionKey<Integer> TruffleInliningExpansionCounterPressure = new OptionKey<>(2000);

    @Option(help = "Controls how steep the inlining limit curve grows in language-agnostic inlining", type = OptionType.Expert, stability = OptionStability.EXPERIMENTAL)
    public static final OptionKey<Integer> TruffleInliningInliningCounterPressure = new OptionKey<>(2000);
    // @formatter:on

    private TruffleCompilerOptions() {
        throw new IllegalStateException("No instance allowed.");
    }

    @NativeImageReinitialize private static volatile OptionValues optionValues;

    private static OptionValues getInitialOptions() {
        OptionValues result = optionValues;
        if (result == null) {
            result = TruffleCompilerRuntime.getRuntime().getOptions(OptionValues.class);
            optionValues = result;
        }
        return result;
    }

    /**
     * Uses the --engine option if set, otherwise falls back on the -Dgraal option.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getPolyglotOptionValue(org.graalvm.options.OptionValues options, org.graalvm.options.OptionKey<T> optionKey) {
        if (options != null && options.hasBeenSet(optionKey)) {
            return options.get(optionKey);
        }
        OptionKey<T> compilerOptionKey = (OptionKey<T>) Lazy.POLYGLOT_TO_COMPILER.get(optionKey);
        if (compilerOptionKey != null) {
            return getValue(compilerOptionKey);
        }
        return optionKey.getDefaultValue();
    }

    /**
     * Determines whether an exception during a Truffle compilation should result in calling
     * {@link System#exit(int)}.
     */
    public static boolean areTruffleCompilationExceptionsFatal(org.graalvm.options.OptionValues options) {
        /*
         * This is duplicated in TruffleRuntimeOptions#areTruffleCompilationExceptionsFatal.
         */
        boolean compilationExceptionsAreFatal = getPolyglotOptionValue(options, CompilationExceptionsAreFatal);
        boolean performanceWarningsAreFatal = getPolyglotOptionValue(options, PerformanceWarningsAreFatal);
        return compilationExceptionsAreFatal || performanceWarningsAreFatal;
    }

    /**
     * Gets the object holding the values of Truffle options, taking into account any active
     * {@linkplain #overrideOptions(OptionKey, Object, Object...) overrides}.
     */
    public static OptionValues getOptions() {
        TruffleOptionsOverrideScope scope = Lazy.overrideScope.get();
        return scope != null ? scope.options : getInitialOptions();
    }

    /**
     * Gets the options defined in the current option
     * {@linkplain #overrideOptions(OptionKey, Object, Object...) override} scope or {@code null} if
     * there is no override scope active for the current thread.
     */
    public static OptionValues getCurrentOptionOverrides() {
        TruffleOptionsOverrideScope scope = Lazy.overrideScope.get();
        return scope != null ? scope.options : null;
    }

    public static final class TruffleOptionsOverrideScope implements AutoCloseable {
        private final TruffleOptionsOverrideScope outer;
        private final OptionValues options;

        private TruffleOptionsOverrideScope(UnmodifiableEconomicMap<OptionKey<?>, Object> overrides) {
            outer = Lazy.overrideScope.get();
            options = new OptionValues(outer == null ? getInitialOptions() : outer.options, overrides);
            Lazy.overrideScope.set(this);
        }

        public OptionValues getOptions() {
            return options;
        }

        @Override
        public void close() {
            Lazy.overrideScope.set(outer);
        }
    }

    /**
     * Forces specified values in the object returned by {@link #getOptions()} until
     * {@link TruffleOptionsOverrideScope#close()} is called on the object returned by this method.
     * The values forced while the override is active are taken from the key/value pairs in
     * {@code overrides}. The override is thread local.
     * <p>
     * The returned object should be used with the try-with-resource construct:
     *
     * <pre>
     * try (TruffleOptionsOverrideScope s = overrideOptions(option1, value1, option2, value2)) {
     *     ...
     * }
     * </pre>
     *
     * NOTE: This feature is only intended for testing. The caller must be aware whether or not the
     * options being overridden are accessed inside the new override scope.
     *
     * @param extraOverrides overrides in the form {@code [key1, value2, key3, value3, ...]}
     */
    public static TruffleOptionsOverrideScope overrideOptions(OptionKey<?> key1, Object value1, Object... extraOverrides) {
        return new TruffleOptionsOverrideScope(OptionValues.asMap(key1, value1, extraOverrides));
    }

    public static TruffleOptionsOverrideScope overrideOptions(UnmodifiableEconomicMap<OptionKey<?>, Object> overrides) {
        return new TruffleOptionsOverrideScope(overrides);
    }

    public static TruffleOptionsOverrideScope overrideOptions(Map<String, Object> overrides) {
        TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
        UnmodifiableEconomicMap<OptionKey<?>, Object> values = runtime.convertOptions(OptionValues.class, overrides).getMap();
        return new TruffleOptionsOverrideScope(values);
    }

    /**
     * Gets the value of a given Truffle option key taking into account any active
     * {@linkplain #overrideOptions overrides}.
     */
    public static <T> T getValue(OptionKey<T> key) {
        return key.getValue(getOptions());
    }

    public static org.graalvm.options.OptionValues getOptionsForCompiler(Map<String, Object> options) {
        EconomicMap<org.graalvm.options.OptionKey<?>, Object> parsedOptions = EconomicMap.create(Equivalence.IDENTITY);
        OptionDescriptors descriptors = PolyglotCompilerOptions.getDescriptors();
        for (Map.Entry<String, Object> e : options.entrySet()) {
            final OptionDescriptor descriptor = descriptors.get(e.getKey());
            final org.graalvm.options.OptionKey<?> k = descriptor != null ? descriptor.getKey() : null;
            if (k != null) {
                Object value = e.getValue();
                if (value.getClass() == String.class) {
                    value = descriptor.getKey().getType().convert((String) e.getValue());
                }
                parsedOptions.put(k, value);
            }
        }
        return new OptionValuesImpl(descriptors, parsedOptions);
    }

    static void checkDeprecation() {
        EconomicMap<OptionKey<?>, org.graalvm.options.OptionKey<?>> deprecatedToReplacement = EconomicMap.create(Equivalence.IDENTITY);
        OptionValues options = getOptions();
        MapCursor<org.graalvm.options.OptionKey<?>, OptionKey<?>> cursor = Lazy.POLYGLOT_TO_COMPILER.getEntries();
        while (cursor.advance()) {
            if (cursor.getValue().hasBeenSet(options)) {
                deprecatedToReplacement.put(cursor.getValue(), cursor.getKey());
            }
        }
        if (!deprecatedToReplacement.isEmpty()) {
            EconomicMap<org.graalvm.options.OptionKey<?>, String> polyglotOptionKeyToName = EconomicMap.create(Equivalence.IDENTITY);
            for (org.graalvm.options.OptionDescriptor descriptor : PolyglotCompilerOptions.getDescriptors()) {
                polyglotOptionKeyToName.put(descriptor.getKey(), descriptor.getName());
            }
            MapCursor<OptionKey<?>, org.graalvm.options.OptionKey<?>> deprecatedCursor = deprecatedToReplacement.getEntries();
            StringBuilder warning = new StringBuilder();
            Formatter formatter = new Formatter(warning);
            while (deprecatedCursor.advance()) {
                if (warning.length() > 0) {
                    formatter.format("%n");
                }
                OptionKey<?> deprecatedOptionKey = deprecatedCursor.getKey();
                Object value = deprecatedOptionKey.getValue(options);
                String strValue = String.valueOf(value);
                String polyglotOptionName = polyglotOptionKeyToName.get(deprecatedCursor.getValue());
                formatter.format("WARNING: The option '%s' was deprecated. Truffle runtime options are no longer specified as Graal options (-Dgraal.*).%n", deprecatedOptionKey.getName());
                formatter.format("Replace the Graal option usage with one of the following replacements:%n");
                if (value instanceof Boolean) {
                    formatter.format("* '--%s' if the option is passed using a guest language launcher.%n", polyglotOptionName);
                } else {
                    formatter.format("* '--%s=%s' if the option is passed using a guest language launcher.%n", polyglotOptionName, strValue);
                }
                formatter.format("* '-Dpolyglot.%s=%s' if the option is passed using the host Java launcher.%n", polyglotOptionName, strValue);
                String quot = value instanceof String ? "\"" : "";
                formatter.format("* Using polyglot API: 'org.graalvm.polyglot.Context.newBuilder().option(\"%s\", " + quot + "%s" + quot + ")'", polyglotOptionName, strValue);
            }
            throw new Error(warning.toString());
        }
    }

    private static final class Lazy {

        static final ThreadLocal<TruffleOptionsOverrideScope> overrideScope = new ThreadLocal<>();

        // Support for mapping PolyglotCompilerOptions to legacy TruffleCompilerOptions.
        private static final EconomicMap<org.graalvm.options.OptionKey<?>, OptionKey<?>> POLYGLOT_TO_COMPILER = initializePolyglotToGraalMapping();

        private static EconomicMap<org.graalvm.options.OptionKey<?>, OptionKey<?>> initializePolyglotToGraalMapping() {
            EconomicMap<org.graalvm.options.OptionKey<?>, OptionKey<?>> result = EconomicMap.create(Equivalence.IDENTITY);
            result.put(InlineAcrossTruffleBoundary, TruffleInlineAcrossTruffleBoundary);
            result.put(TracePerformanceWarnings, TraceTrufflePerformanceWarnings);
            result.put(PrintExpansionHistogram, PrintTruffleExpansionHistogram);
            result.put(IterativePartialEscape, TruffleIterativePartialEscape);
            result.put(InstrumentFilter, TruffleInstrumentFilter);
            result.put(InstrumentationTableSize, TruffleInstrumentationTableSize);
            result.put(MaximumGraalNodeCount, TruffleMaximumGraalNodeCount);
            result.put(MaximumInlineNodeCount, TruffleMaximumInlineNodeCount);
            result.put(TraceInliningDetails, TraceTruffleInliningDetails);
            result.put(InliningPolicy, TruffleInliningPolicy);
            result.put(InliningExpansionBudget, TruffleInliningExpansionBudget);
            result.put(InliningInliningBudget, TruffleInliningInliningBudget);
            result.put(InliningCutoffCountPenalty, TruffleInliningCutoffCountPenalty);
            result.put(InliningNodeCountPenalty, TruffleInliningNodeCountPenalty);
            result.put(InliningExpandAllProximityFactor, TruffleInliningExpandAllProximityFactor);
            result.put(InliningExpandAllProximityBonus, TruffleInliningExpandAllProximityBonus);
            result.put(InliningExpansionCounterPressure, TruffleInliningExpansionCounterPressure);
            result.put(InliningInliningCounterPressure, TruffleInliningInliningCounterPressure);
            result.put(CompilationExceptionsAreFatal, SharedTruffleCompilerOptions.TruffleCompilationExceptionsAreFatal);
            result.put(PerformanceWarningsAreFatal, SharedTruffleCompilerOptions.TrufflePerformanceWarningsAreFatal);
            result.put(TraceInlining, SharedTruffleCompilerOptions.TraceTruffleInlining);
            result.put(TraceStackTraceLimit, SharedTruffleCompilerOptions.TraceTruffleStackTraceLimit);
            result.put(Inlining, SharedTruffleCompilerOptions.TruffleFunctionInlining);
            result.put(InliningRecursionDepth, SharedTruffleCompilerOptions.TruffleMaximumRecursiveInlining);
            result.put(LanguageAgnosticInlining, SharedTruffleCompilerOptions.TruffleLanguageAgnosticInlining);

            result.put(Compilation, SharedTruffleCompilerOptions.TruffleCompilation);
            result.put(CompileOnly, SharedTruffleCompilerOptions.TruffleCompileOnly);
            result.put(CompileImmediately, SharedTruffleCompilerOptions.TruffleCompileImmediately);
            result.put(BackgroundCompilation, SharedTruffleCompilerOptions.TruffleBackgroundCompilation);
            result.put(CompilerThreads, SharedTruffleCompilerOptions.TruffleCompilerThreads);
            result.put(CompilationThreshold, SharedTruffleCompilerOptions.TruffleCompilationThreshold);
            result.put(MinInvokeThreshold, SharedTruffleCompilerOptions.TruffleMinInvokeThreshold);
            result.put(InvalidationReprofileCount, SharedTruffleCompilerOptions.TruffleInvalidationReprofileCount);
            result.put(ReplaceReprofileCount, SharedTruffleCompilerOptions.TruffleReplaceReprofileCount);
            result.put(ArgumentTypeSpeculation, SharedTruffleCompilerOptions.TruffleArgumentTypeSpeculation);
            result.put(ReturnTypeSpeculation, SharedTruffleCompilerOptions.TruffleReturnTypeSpeculation);
            result.put(Profiling, SharedTruffleCompilerOptions.TruffleProfilingEnabled);
            result.put(MultiTier, SharedTruffleCompilerOptions.TruffleMultiTier);
            result.put(FirstTierCompilationThreshold, SharedTruffleCompilerOptions.TruffleFirstTierCompilationThreshold);
            result.put(FirstTierMinInvokeThreshold, SharedTruffleCompilerOptions.TruffleFirstTierMinInvokeThreshold);
            result.put(CompilationExceptionsArePrinted, SharedTruffleCompilerOptions.TruffleCompilationExceptionsArePrinted);
            result.put(CompilationExceptionsAreThrown, SharedTruffleCompilerOptions.TruffleCompilationExceptionsAreThrown);
            result.put(TraceCompilation, SharedTruffleCompilerOptions.TraceTruffleCompilation);
            result.put(TraceCompilationDetails, SharedTruffleCompilerOptions.TraceTruffleCompilationDetails);
            result.put(TraceCompilationPolymorphism, SharedTruffleCompilerOptions.TraceTruffleCompilationPolymorphism);
            result.put(TraceCompilationAST, SharedTruffleCompilerOptions.TraceTruffleCompilationAST);
            result.put(TraceCompilationCallTree, SharedTruffleCompilerOptions.TraceTruffleCompilationCallTree);
            result.put(TraceAssumptions, SharedTruffleCompilerOptions.TraceTruffleAssumptions);
            result.put(CompilationStatistics, SharedTruffleCompilerOptions.TruffleCompilationStatistics);
            result.put(CompilationStatisticDetails, SharedTruffleCompilerOptions.TruffleCompilationStatisticDetails);
            result.put(TraceTransferToInterpreter, SharedTruffleCompilerOptions.TraceTruffleTransferToInterpreter);
            result.put(TraceSplitting, SharedTruffleCompilerOptions.TraceTruffleSplitting);
            result.put(InliningNodeBudget, SharedTruffleCompilerOptions.TruffleInliningMaxCallerSize);
            result.put(Splitting, SharedTruffleCompilerOptions.TruffleSplitting);
            result.put(SplittingMaxCalleeSize, SharedTruffleCompilerOptions.TruffleSplittingMaxCalleeSize);
            result.put(SplittingGrowthLimit, SharedTruffleCompilerOptions.TruffleSplittingGrowthLimit);
            result.put(SplittingMaxNumberOfSplitNodes, SharedTruffleCompilerOptions.TruffleSplittingMaxNumberOfSplitNodes);
            result.put(SplittingMaxPropagationDepth, SharedTruffleCompilerOptions.TruffleSplittingMaxPropagationDepth);
            result.put(TraceSplittingSummary, SharedTruffleCompilerOptions.TruffleTraceSplittingSummary);
            result.put(SplittingTraceEvents, SharedTruffleCompilerOptions.TruffleSplittingTraceEvents);
            result.put(SplittingDumpDecisions, SharedTruffleCompilerOptions.TruffleSplittingDumpDecisions);
            result.put(SplittingAllowForcedSplits, SharedTruffleCompilerOptions.TruffleSplittingAllowForcedSplits);
            result.put(OSR, SharedTruffleCompilerOptions.TruffleOSR);
            result.put(OSRCompilationThreshold, SharedTruffleCompilerOptions.TruffleOSRCompilationThreshold);
            return result;
        }
    }
}
