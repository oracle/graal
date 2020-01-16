/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.options;

import java.util.function.Function;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.Option;

/**
 * Truffle compilation options that can be configured per {@link Engine engine} instance. These
 * options are accessed by the Truffle runtime and not the Truffle compiler, unlike
 * org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions
 * <p>
 * They replace the deprecated {@code -Dgraal.} Truffle-related options declared in
 * org.graalvm.compiler.truffle.common.processor.Option
 */
@Option.Group("engine")
public final class PolyglotCompilerOptions {
    public enum EngineModeEnum {
        DEFAULT,
        THROUGHPUT,
        LATENCY
    }

    static final OptionType<EngineModeEnum> ENGINE_MODE_TYPE = new OptionType<>("EngineMode",
                    new Function<String, EngineModeEnum>() {
                        @Override
                        public EngineModeEnum apply(String s) {
                            try {
                                return EngineModeEnum.valueOf(s.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException("Mode can be: 'default', 'latency' or 'throughput'.");
                            }
                        }
                    });

    // @formatter:off

    // Compilation

    @Option(help = "Configures the execution mode of the engine. Available modes are 'latency' and 'throughput'. The default value balances between the two.",
            category = OptionCategory.EXPERT)
    public static final OptionKey<EngineModeEnum> Mode = new OptionKey<>(EngineModeEnum.DEFAULT, ENGINE_MODE_TYPE);

    @Option(help = "Enable or disable Truffle compilation.", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> Compilation = new OptionKey<>(true);

    @Option(help = "Restrict compilation to ','-separated list of includes (or excludes prefixed with '~').", category = OptionCategory.INTERNAL)
    public static final OptionKey<String> CompileOnly = new OptionKey<>(null, OptionType.defaultType(String.class));

    @Option(help = "Compile immediately to test Truffle compilation", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> CompileImmediately = new OptionKey<>(false);

    @Option(help = "Enable asynchronous truffle compilation in background threads", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> BackgroundCompilation = new OptionKey<>(true);

    @Option(help = "Manually set the number of compiler threads", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> CompilerThreads = new OptionKey<>(0);

    @Option(help = "Minimum number of invocations or loop iterations needed to compile a guest language root.",
                    category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> CompilationThreshold = new OptionKey<>(1000);

    @Option(help = "Minimum number of calls before a call target is compiled", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> MinInvokeThreshold = new OptionKey<>(3);

    @Option(help = "Delay compilation after an invalidation to allow for reprofiling. Deprecated: no longer has any effect.", category = OptionCategory.EXPERT, deprecated =  true)
    public static final OptionKey<Integer> InvalidationReprofileCount = new OptionKey<>(3);

    @Option(help = "Delay compilation after a node replacement. Deprecated: no longer has any effect.", category = OptionCategory.EXPERT, deprecated =  true)
    public static final OptionKey<Integer> ReplaceReprofileCount = new OptionKey<>(3);

    @Option(help = "Speculate on arguments types at call sites", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> ArgumentTypeSpeculation = new OptionKey<>(true);

    @Option(help = "Speculate on return types at call sites", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> ReturnTypeSpeculation = new OptionKey<>(true);

    @Option(help = "Enable/disable builtin profiles in com.oracle.truffle.api.profiles.", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> Profiling = new OptionKey<>(true);

    // MultiTier

    @Option(help = "Whether to use multiple Truffle compilation tiers by default.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> MultiTier = new OptionKey<>(false);

    @Option(help = "Minimum number of invocations or loop iterations needed to compile a guest language root in low tier mode.",
            category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> FirstTierCompilationThreshold = new OptionKey<>(100);

    @Option(help = "Minimum number of calls before a call target is compiled in the first tier.", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> FirstTierMinInvokeThreshold = new OptionKey<>(1);

    // Failed compilation behavior

    @Option(help = "Prints the exception stack trace for compilation exceptions", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> CompilationExceptionsArePrinted = new OptionKey<>(true);

    @Option(help = "Treat compilation exceptions as thrown runtime exceptions", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> CompilationExceptionsAreThrown = new OptionKey<>(false);

    @Option(help = "Treat compilation exceptions as fatal exceptions that will exit the application", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> CompilationExceptionsAreFatal = new OptionKey<>(false);

    @Option(help = "Treat performance warnings as fatal occurrences that will exit the applications", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> PerformanceWarningsAreFatal = new OptionKey<>(false);

    // Tracing

    @Option(help = "Print information for compilation results.", category = OptionCategory.EXPERT, stability = OptionStability.STABLE)
    public static final OptionKey<Boolean> TraceCompilation = new OptionKey<>(false);

    @Option(help = "Print information for compilation queuing.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> TraceCompilationDetails = new OptionKey<>(false);

    @Option(help = "Print all polymorphic and generic nodes after each compilation", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> TraceCompilationPolymorphism = new OptionKey<>(false);

    @Option(help = "Print the entire AST after each compilation", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> TraceCompilationAST = new OptionKey<>(false);

    @Option(help = "Print the inlined call tree for each compiled method", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> TraceCompilationCallTree = new OptionKey<>(false);

    @Option(help = "Print information for inlining decisions.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> TraceInlining = new OptionKey<>(false);

    @Option(help = "Print information for splitting decisions.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> TraceSplitting = new OptionKey<>(false);

    @Option(help = "Print stack trace on assumption invalidation", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> TraceAssumptions = new OptionKey<>(false);

    @Option(help = "Number of stack trace elements printed by TraceTruffleTransferToInterpreter and TraceTruffleAssumptions", category = OptionCategory.INTERNAL)
    public static final OptionKey<Integer> TraceStackTraceLimit = new OptionKey<>(20);

    @Option(help = "Print Truffle compilation statistics at the end of a run.", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> CompilationStatistics = new OptionKey<>(false);

    @Option(help = "Print additional more verbose Truffle compilation statistics at the end of a run.", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> CompilationStatisticDetails = new OptionKey<>(false);

    @Option(help = "Print stack trace on transfer to interpreter.", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> TraceTransferToInterpreter = new OptionKey<>(false);

    // Inlining

    @Option(help = "Enable automatic inlining of guest language call targets.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> Inlining = new OptionKey<>(true);

    @Option(help = "Maximum number of inlined non-trivial AST nodes per compilation unit.", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> InliningNodeBudget = new OptionKey<>(2250);

    @Option(help = "Maximum depth for recursive inlining.", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> InliningRecursionDepth = new OptionKey<>(2);

    @Option(help = "Use language-agnostic inlining (overrides the TruffleFunctionInlining setting, option is experimental).", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> LanguageAgnosticInlining = new OptionKey<>(false);

    @Option(help = "Controls how impactful many cutoff nodes is on exploration decision in language-agnostic inlining.", category = OptionCategory.EXPERT)
    public static final OptionKey<Double> InliningCutoffCountPenalty = new OptionKey<>(0.1);

    @Option(help = "Controls how impactful the size of the subtree is on exploration decision in language-agnostic inlining.", category = OptionCategory.EXPERT)
    public static final OptionKey<Double> InliningNodeCountPenalty = new OptionKey<>(0.1);

    @Option(help = "Controls how impactful few cutoff nodes are on exploration decisions in language-agnostic inlining.", category = OptionCategory.EXPERT)
    public static final OptionKey<Double> InliningExpandAllProximityFactor = new OptionKey<>(0.5);

    @Option(help = "Controls at what point few cutoff nodes are impactful on exploration decisions in language-agnostic inlining.", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> InliningExpandAllProximityBonus = new OptionKey<>(10);

    @Option(help = "Controls how steep the exploration limit curve grows in language-agnostic inlining.", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> InliningExpansionCounterPressure = new OptionKey<>(2000);

    @Option(help = "Controls how steep the inlining limit curve grows in language-agnostic inlining", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> InliningInliningCounterPressure = new OptionKey<>(2000);

    // Splitting

    @Option(help = "Enable automatic duplication of compilation profiles (splitting).",
                    category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> Splitting = new OptionKey<>(true);

    @Option(help = "Disable call target splitting if tree size exceeds this limit", category = OptionCategory.INTERNAL)
    public static final OptionKey<Integer> SplittingMaxCalleeSize = new OptionKey<>(100);

    @Option(help = "Disable call target splitting if the number of nodes created by splitting exceeds this factor times node count", category = OptionCategory.INTERNAL)
    public static final OptionKey<Double> SplittingGrowthLimit = new OptionKey<>(1.5);

    @Option(help = "Disable call target splitting if number of nodes created by splitting exceeds this limit", category = OptionCategory.INTERNAL)
    public static final OptionKey<Integer> SplittingMaxNumberOfSplitNodes = new OptionKey<>(500_000);

    @Option(help = "Propagate info about a polymorphic specialize through maximum this many call targets", category = OptionCategory.INTERNAL)
    public static final OptionKey<Integer> SplittingMaxPropagationDepth = new OptionKey<>(5);

    @Option(help = "Used for debugging the splitting implementation. Prints splitting summary directly to stdout on shutdown", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> TraceSplittingSummary = new OptionKey<>(false);

    @Option(help = "Trace details of splitting events and decisions.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> SplittingTraceEvents = new OptionKey<>(false);

    @Option(help = "Dumps to IGV information on polymorphic events", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> SplittingDumpDecisions = new OptionKey<>(false);

    @Option(help = "Should forced splits be allowed.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> SplittingAllowForcedSplits = new OptionKey<>(true);

    // OSR

    @Option(help = "Enable automatic on-stack-replacement of loops.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> OSR = new OptionKey<>(true);

    @Option(help = "Number of loop iterations until on-stack-replacement compilation is triggered.", category = OptionCategory.INTERNAL)
    public static final OptionKey<Integer> OSRCompilationThreshold = new OptionKey<>(100000);

    @Option(help = "Enable partial compilation for BlockNode.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> PartialBlockCompilation = new OptionKey<>(true);

    @Option(help = "Sets the target non-trivial Truffle node size for partial compilation of BlockNode nodes.", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> PartialBlockCompilationSize = new OptionKey<>(3000);

    /*
     * TODO planned options (GR-13444):
     *
    @Option(help = "Trace deoptimization of compilation units.",
                    category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> TraceDeoptimization = new OptionKey<>(false);
    */

    //Compiler options
    @Option(help = "Enable inlining across Truffle boundary", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> InlineAcrossTruffleBoundary = new OptionKey<>(false);

    @Option(help = "Print potential performance problems", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> TracePerformanceWarnings = new OptionKey<>(false);

    @Option(help = "Prints a histogram of all expanded Java methods.", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> PrintExpansionHistogram = new OptionKey<>(false);

    @Option(help = "Run the partial escape analysis iteratively in Truffle compilation.", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> IterativePartialEscape = new OptionKey<>(false);

    @Option(help = "Method filter for host methods in which to add instrumentation.", category = OptionCategory.EXPERT)
    public static final OptionKey<String> InstrumentFilter = new OptionKey<>("*.*.*");

    @Option(help = "Maximum number of instrumentation counters available.", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> InstrumentationTableSize = new OptionKey<>(10000);

    @Option(help = "Stop partial evaluation when the graph exceeded this many nodes.", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> MaximumGraalNodeCount = new OptionKey<>(400000);

    @Option(help = "Ignore further truffle inlining decisions when the graph exceeded this many nodes.", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> MaximumInlineNodeCount = new OptionKey<>(150000);

    // Language agnostic inlining

    @Option(help = "Print detailed information for inlining (i.e. the entire explored call tree).", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> TraceInliningDetails = new OptionKey<>(false);

    @Option(help = "Explicitly pick a inlining policy by name. Highest priority chosen by default.", category = OptionCategory.EXPERT)
    public static final OptionKey<String> InliningPolicy = new OptionKey<>("");

    @Option(help = "The base expansion budget for language-agnostic inlining.", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> InliningExpansionBudget = new OptionKey<>(50_000);

    @Option(help = "The base inlining budget for language-agnostic inlining", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> InliningInliningBudget = new OptionKey<>(50_000);

    // @formatter:on

    public static OptionDescriptors getDescriptors() {
        return new PolyglotCompilerOptionsOptionDescriptors();
    }

}
