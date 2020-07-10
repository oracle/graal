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
package org.graalvm.compiler.truffle.compiler;

import java.util.Formatter;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsAreFatal;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.NodeSourcePositions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ExcludeAssertions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InlineAcrossTruffleBoundary;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Inlining;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningExpansionBudget;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningInliningBudget;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningPolicy;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningRecursionDepth;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InstrumentBoundaries;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InstrumentBoundariesPerInlineSite;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InstrumentBranches;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InstrumentBranchesPerInlineSite;
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
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationFailureAction;
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

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.Pair;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.SharedTruffleOptions;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.options.OptionValuesImpl;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;

import jdk.vm.ci.common.NativeImageReinitialize;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;

/**
 * Options for the Truffle compiler. Options shared with the Truffle runtime are declared in
 * {@link SharedTruffleCompilerOptions}.
 */
@SharedTruffleOptions(name = "SharedTruffleCompilerOptions", runtime = false)
public final class TruffleCompilerOptions {

    // @formatter:off
    // configuration

    /**
     * Deprecated by {@link PolyglotCompilerOptions#ExcludeAssertions}.
     */
    @Option(help = "Exclude assertion code from Truffle compilations", type = OptionType.Debug, deprecated = true)
    static final OptionKey<Boolean> TruffleExcludeAssertions = new OptionKey<>(ExcludeAssertions.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#InlineAcrossTruffleBoundary}.
     */
    @Option(help = "Enable inlining across Truffle boundary", type = OptionType.Expert, deprecated = true)
    static final OptionKey<Boolean> TruffleInlineAcrossTruffleBoundary = new OptionKey<>(InlineAcrossTruffleBoundary.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#TracePerformanceWarnings}.
     */
    @Option(help = "Print potential performance problems", type = OptionType.Debug, deprecated = true)
    static final OptionKey<Boolean> TraceTrufflePerformanceWarnings = new OptionKey<>(false);

    /**
     * Deprecated by {@link PolyglotCompilerOptions#PrintExpansionHistogram}.
     */
    @Option(help = "Prints a histogram of all expanded Java methods.", type = OptionType.Debug, deprecated = true)
    static final OptionKey<Boolean> PrintTruffleExpansionHistogram = new OptionKey<>(PrintExpansionHistogram.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#NodeSourcePositions}.
     */
    @Option(help = "Enable support for simple infopoints in truffle partial evaluations.", type = OptionType.Expert, deprecated = true)
    static final OptionKey<Boolean> TruffleEnableInfopoints = new OptionKey<>(NodeSourcePositions.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#IterativePartialEscape}.
     */
    @Option(help = "Run the partial escape analysis iteratively in Truffle compilation.", type = OptionType.Debug, deprecated = true)
    static final OptionKey<Boolean> TruffleIterativePartialEscape = new OptionKey<>(IterativePartialEscape.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#InstrumentBranches}.
     */
    @Option(help = "Instrument branches and output profiling information to the standard output.", deprecated = true)
    static final OptionKey<Boolean> TruffleInstrumentBranches = new OptionKey<>(InstrumentBranches.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#InstrumentBranchesPerInlineSite}.
     */
    @Option(help = "Instrument branches by considering different inlining sites as different branches.", deprecated = true)
    static final OptionKey<Boolean> TruffleInstrumentBranchesPerInlineSite = new OptionKey<>(InstrumentBranchesPerInlineSite.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#InstrumentBoundaries}.
     */
    @Option(help = "Instrument Truffle boundaries and output profiling information to the standard output.", deprecated = true)
    static final OptionKey<Boolean> TruffleInstrumentBoundaries = new OptionKey<>(InstrumentBoundaries.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#InstrumentBoundariesPerInlineSite}.
     */
    @Option(help = "Instrument Truffle boundaries by considering different inlining sites as different branches.", deprecated = true)
    static final OptionKey<Boolean> TruffleInstrumentBoundariesPerInlineSite = new OptionKey<>(InstrumentBoundariesPerInlineSite.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#InstrumentFilter}.
     */
    @Option(help = "Method filter for host methods in which to add instrumentation.", deprecated = true)
    static final OptionKey<String> TruffleInstrumentFilter = new OptionKey<>(InstrumentFilter.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#InstrumentationTableSize}.
     */
    @Option(help = "Maximum number of instrumentation counters available.", deprecated = true)
    static final OptionKey<Integer> TruffleInstrumentationTableSize = new OptionKey<>(InstrumentationTableSize.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#MaximumGraalNodeCount}.
     */
    @Option(help = "Stop partial evaluation when the graph exceeded this many nodes.", deprecated = true)
    static final OptionKey<Integer> TruffleMaximumGraalNodeCount = new OptionKey<>(MaximumGraalNodeCount.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#MaximumInlineNodeCount}.
     */
    @Option(help = "Ignore further truffle inlining decisions when the graph exceeded this many nodes.", deprecated = true)
    static final OptionKey<Integer> TruffleMaximumInlineNodeCount = new OptionKey<>(MaximumInlineNodeCount.getDefaultValue());

    /**
     * Deprecated with no replacement.
     */
    @Option(help = "Intrinsify get/set/is methods of FrameWithoutBoxing to improve Truffle compilation time", type = OptionType.Debug, deprecated = true)
    static final OptionKey<Boolean> TruffleIntrinsifyFrameAccess = new OptionKey<>(true);

    // Language agnostic inlining

    /**
     * Deprecated by {@link PolyglotCompilerOptions#TraceInliningDetails}.
     */
    @Option(help = "Print detailed information for inlining (i.e. the entire explored call tree).", type = OptionType.Expert, deprecated = true)
    static final OptionKey<Boolean> TraceTruffleInliningDetails = new OptionKey<>(TraceInliningDetails.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#InliningPolicy}.
     */
    @Option(help = "Explicitly pick a inlining policy by name. Highest priority chosen by default.", type = OptionType.Expert, deprecated = true)
    static final OptionKey<String> TruffleInliningPolicy = new OptionKey<>(InliningPolicy.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#InliningExpansionBudget}.
     */
    @Option(help = "The base expansion budget for language-agnostic inlining.", type = OptionType.Expert, deprecated = true)
    static final OptionKey<Integer> TruffleInliningExpansionBudget = new OptionKey<>(InliningExpansionBudget.getDefaultValue());

    /**
     * Deprecated by {@link PolyglotCompilerOptions#InliningInliningBudget}.
     */
    @Option(help = "The base inlining budget for language-agnostic inlining", type = OptionType.Expert, deprecated = true)
    static final OptionKey<Integer> TruffleInliningInliningBudget = new OptionKey<>(InliningInliningBudget.getDefaultValue());
    // @formatter:on

    private TruffleCompilerOptions() {
        throw new IllegalStateException("No instance allowed.");
    }

    @NativeImageReinitialize private static volatile OptionValues optionValues;

    /**
     * Uses the --engine option if set, otherwise falls back on the -Dgraal option.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getPolyglotOptionValue(org.graalvm.options.OptionValues options, org.graalvm.options.OptionKey<T> optionKey) {
        if (options != null && options.hasBeenSet(optionKey)) {
            return options.get(optionKey);
        }
        Pair<? extends OptionKey<?>, Function<Object, ?>> compilerOptionKeyPair = Lazy.POLYGLOT_TO_COMPILER.get(optionKey);
        if (compilerOptionKeyPair != null) {
            return (T) compilerOptionKeyPair.getRight().apply(compilerOptionKeyPair.getLeft().getValue(getOptions()));
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
        boolean performanceWarningsAreFatal = !getPolyglotOptionValue(options, PerformanceWarningsAreFatal).isEmpty();
        boolean exitVM = getPolyglotOptionValue(options, CompilationFailureAction) == PolyglotCompilerOptions.ExceptionAction.ExitVM;
        return compilationExceptionsAreFatal || performanceWarningsAreFatal || exitVM;
    }

    /**
     * Gets the object holding the values of Truffle options.
     */
    public static OptionValues getOptions() {
        OptionValues result = optionValues;
        if (result == null) {
            result = TruffleCompilerRuntime.getRuntime().getOptions(OptionValues.class);
            optionValues = result;
        }
        return result;
    }

    /**
     * Converts the values of {@link PolyglotCompilerOptions} passed to the
     * {@link TruffleCompiler#doCompile} as a {@link Map} into
     * {@link org.graalvm.options.OptionValues}.
     */
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

    static String[] checkDeprecation(CompilableTruffleAST compilable) {
        EconomicMap<OptionKey<?>, org.graalvm.options.OptionKey<?>> deprecatedToReplacement = EconomicMap.create(Equivalence.IDENTITY);
        OptionValues options = getOptions();
        MapCursor<org.graalvm.options.OptionKey<?>, Pair<? extends OptionKey<?>, Function<Object, ?>>> cursor = Lazy.POLYGLOT_TO_COMPILER.getEntries();
        while (cursor.advance()) {
            OptionKey<?> deprecatedKey = cursor.getValue().getLeft();
            if (deprecatedKey.hasBeenSet(options)) {
                deprecatedToReplacement.put(deprecatedKey, cursor.getKey());
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
                if (value instanceof Boolean && ((boolean) value) == true) {
                    formatter.format("* '--%s' if the option is passed using a guest language launcher.%n", polyglotOptionName);
                } else {
                    formatter.format("* '--%s=%s' if the option is passed using a guest language launcher.%n", polyglotOptionName, strValue);
                }
                formatter.format("* '-Dpolyglot.%s=%s' if the option is passed using the host Java launcher.%n", polyglotOptionName, strValue);
                String quot = value instanceof String ? "\"" : "";
                formatter.format("* Using polyglot API: 'org.graalvm.polyglot.Context.newBuilder().option(\"%s\", " + quot + "%s" + quot + ")'", polyglotOptionName, strValue);
            }
            TruffleCompilerRuntime.getRuntime().log(compilable, warning.toString());
        }
        return new String[0];
    }

    private static final class Lazy {

        // Support for mapping PolyglotCompilerOptions to legacy TruffleCompilerOptions.
        private static final EconomicMap<org.graalvm.options.OptionKey<?>, Pair<? extends OptionKey<?>, Function<Object, ?>>> POLYGLOT_TO_COMPILER = initializePolyglotToGraalMapping();

        private static EconomicMap<org.graalvm.options.OptionKey<?>, Pair<? extends OptionKey<?>, Function<Object, ?>>> initializePolyglotToGraalMapping() {
            EconomicMap<org.graalvm.options.OptionKey<?>, Pair<? extends OptionKey<?>, Function<Object, ?>>> result = EconomicMap.create(Equivalence.IDENTITY);
            result.put(InlineAcrossTruffleBoundary, identity(TruffleInlineAcrossTruffleBoundary));
            result.put(TracePerformanceWarnings, booleanToPerformanceWarningKind(TraceTrufflePerformanceWarnings));
            result.put(PrintExpansionHistogram, identity(PrintTruffleExpansionHistogram));
            result.put(IterativePartialEscape, identity(TruffleIterativePartialEscape));
            result.put(InstrumentFilter, identity(TruffleInstrumentFilter));
            result.put(InstrumentationTableSize, identity(TruffleInstrumentationTableSize));
            result.put(MaximumGraalNodeCount, identity(TruffleMaximumGraalNodeCount));
            result.put(MaximumInlineNodeCount, identity(TruffleMaximumInlineNodeCount));
            result.put(TraceInliningDetails, identity(TraceTruffleInliningDetails));
            result.put(InliningPolicy, identity(TruffleInliningPolicy));
            result.put(InliningExpansionBudget, identity(TruffleInliningExpansionBudget));
            result.put(InliningInliningBudget, identity(TruffleInliningInliningBudget));
            result.put(CompilationExceptionsAreFatal, identity(SharedTruffleCompilerOptions.TruffleCompilationExceptionsAreFatal));
            result.put(PerformanceWarningsAreFatal, booleanToPerformanceWarningKind(SharedTruffleCompilerOptions.TrufflePerformanceWarningsAreFatal));
            result.put(TraceInlining, identity(SharedTruffleCompilerOptions.TraceTruffleInlining));
            result.put(TraceStackTraceLimit, identity(SharedTruffleCompilerOptions.TraceTruffleStackTraceLimit));
            result.put(Inlining, identity(SharedTruffleCompilerOptions.TruffleFunctionInlining));
            result.put(InliningRecursionDepth, identity(SharedTruffleCompilerOptions.TruffleMaximumRecursiveInlining));
            result.put(LanguageAgnosticInlining, identity(SharedTruffleCompilerOptions.TruffleLanguageAgnosticInlining));
            result.put(ExcludeAssertions, identity(TruffleExcludeAssertions));
            result.put(NodeSourcePositions, identity(TruffleEnableInfopoints));
            result.put(InstrumentBoundaries, identity(TruffleInstrumentBoundaries));
            result.put(InstrumentBoundariesPerInlineSite, identity(TruffleInstrumentBoundariesPerInlineSite));
            result.put(InstrumentBranches, identity(TruffleInstrumentBranches));
            result.put(InstrumentBranchesPerInlineSite, identity(TruffleInstrumentBranchesPerInlineSite));

            result.put(Compilation, identity(SharedTruffleCompilerOptions.TruffleCompilation));
            result.put(CompileOnly, identity(SharedTruffleCompilerOptions.TruffleCompileOnly));
            result.put(CompileImmediately, identity(SharedTruffleCompilerOptions.TruffleCompileImmediately));
            result.put(BackgroundCompilation, identity(SharedTruffleCompilerOptions.TruffleBackgroundCompilation));
            result.put(CompilerThreads, identity(SharedTruffleCompilerOptions.TruffleCompilerThreads));
            result.put(CompilationThreshold, identity(SharedTruffleCompilerOptions.TruffleCompilationThreshold));
            result.put(MinInvokeThreshold, identity(SharedTruffleCompilerOptions.TruffleMinInvokeThreshold));
            result.put(InvalidationReprofileCount, identity(SharedTruffleCompilerOptions.TruffleInvalidationReprofileCount));
            result.put(ReplaceReprofileCount, identity(SharedTruffleCompilerOptions.TruffleReplaceReprofileCount));
            result.put(ArgumentTypeSpeculation, identity(SharedTruffleCompilerOptions.TruffleArgumentTypeSpeculation));
            result.put(ReturnTypeSpeculation, identity(SharedTruffleCompilerOptions.TruffleReturnTypeSpeculation));
            result.put(Profiling, identity(SharedTruffleCompilerOptions.TruffleProfilingEnabled));
            result.put(MultiTier, identity(SharedTruffleCompilerOptions.TruffleMultiTier));
            result.put(FirstTierCompilationThreshold, identity(SharedTruffleCompilerOptions.TruffleFirstTierCompilationThreshold));
            result.put(FirstTierMinInvokeThreshold, identity(SharedTruffleCompilerOptions.TruffleFirstTierMinInvokeThreshold));
            result.put(CompilationExceptionsArePrinted, identity(SharedTruffleCompilerOptions.TruffleCompilationExceptionsArePrinted));
            result.put(CompilationExceptionsAreThrown, identity(SharedTruffleCompilerOptions.TruffleCompilationExceptionsAreThrown));
            result.put(TraceCompilation, identity(SharedTruffleCompilerOptions.TraceTruffleCompilation));
            result.put(TraceCompilationDetails, identity(SharedTruffleCompilerOptions.TraceTruffleCompilationDetails));
            result.put(TraceCompilationPolymorphism, identity(SharedTruffleCompilerOptions.TraceTruffleCompilationPolymorphism));
            result.put(TraceCompilationAST, identity(SharedTruffleCompilerOptions.TraceTruffleCompilationAST));
            result.put(TraceCompilationCallTree, identity(SharedTruffleCompilerOptions.TraceTruffleCompilationCallTree));
            result.put(TraceAssumptions, identity(SharedTruffleCompilerOptions.TraceTruffleAssumptions));
            result.put(CompilationStatistics, identity(SharedTruffleCompilerOptions.TruffleCompilationStatistics));
            result.put(CompilationStatisticDetails, identity(SharedTruffleCompilerOptions.TruffleCompilationStatisticDetails));
            result.put(TraceTransferToInterpreter, identity(SharedTruffleCompilerOptions.TraceTruffleTransferToInterpreter));
            result.put(TraceSplitting, identity(SharedTruffleCompilerOptions.TraceTruffleSplitting));
            result.put(InliningNodeBudget, identity(SharedTruffleCompilerOptions.TruffleInliningMaxCallerSize));
            result.put(Splitting, identity(SharedTruffleCompilerOptions.TruffleSplitting));
            result.put(SplittingMaxCalleeSize, identity(SharedTruffleCompilerOptions.TruffleSplittingMaxCalleeSize));
            result.put(SplittingGrowthLimit, identity(SharedTruffleCompilerOptions.TruffleSplittingGrowthLimit));
            result.put(SplittingMaxNumberOfSplitNodes, identity(SharedTruffleCompilerOptions.TruffleSplittingMaxNumberOfSplitNodes));
            result.put(SplittingMaxPropagationDepth, identity(SharedTruffleCompilerOptions.TruffleSplittingMaxPropagationDepth));
            result.put(TraceSplittingSummary, identity(SharedTruffleCompilerOptions.TruffleTraceSplittingSummary));
            result.put(SplittingTraceEvents, identity(SharedTruffleCompilerOptions.TruffleSplittingTraceEvents));
            result.put(SplittingDumpDecisions, identity(SharedTruffleCompilerOptions.TruffleSplittingDumpDecisions));
            result.put(SplittingAllowForcedSplits, identity(SharedTruffleCompilerOptions.TruffleSplittingAllowForcedSplits));
            result.put(OSR, identity(SharedTruffleCompilerOptions.TruffleOSR));
            result.put(OSRCompilationThreshold, identity(SharedTruffleCompilerOptions.TruffleOSRCompilationThreshold));
            return result;
        }
    }

    private static Pair<OptionKey<?>, Function<Object, ?>> identity(OptionKey<?> key) {
        return Pair.create(key, Function.identity());
    }

    private static Pair<OptionKey<Boolean>, Function<Object, ?>> booleanToPerformanceWarningKind(OptionKey<Boolean> key) {
        return Pair.create(key, new Function<Object, Set<PolyglotCompilerOptions.PerformanceWarningKind>>() {
            @Override
            public Set<PolyglotCompilerOptions.PerformanceWarningKind> apply(Object t) {
                return ((Boolean) t) ? EnumSet.allOf(PolyglotCompilerOptions.PerformanceWarningKind.class) : EnumSet.noneOf(PolyglotCompilerOptions.PerformanceWarningKind.class);
            }
        });
    }
}
