/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Truffle compilation options that can be configured per {@link Engine engine} instance.
 * These options are accessed by the Truffle runtime and not the Truffle compiler,
 * unlike {@link org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions}.
 * <p>
 * They replace the deprecated {@code -Dgraal.} Truffle-related options declared in
 * {@link org.graalvm.compiler.truffle.common.processor.Option}.
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

    @Option(help = "Enable or disable Truffle compilation.", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> Compilation = new OptionKey<>(true);

    @Option(help = "Restrict compilation to ','-separated list of includes (or excludes prefixed with '~').", category = OptionCategory.INTERNAL)
    public static final OptionKey<String> CompileOnly = new OptionKey<>(null, OptionType.defaultType(String.class));

    @Option(help = "Compile immediately to test Truffle compilation", category = OptionCategory.INTERNAL)
    public static final OptionKey<Boolean> CompileImmediately = new OptionKey<>(false);

    @Option(help = "Enable asynchronous truffle compilation in background threads", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> BackgroundCompilation = new OptionKey<>(true);

    @Option(help = "Manually set the number of compiler threads", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> CompilerThreads = new OptionKey<>(SharedTruffleRuntimeOptions.TruffleCompilerThreads.getDefaultValue());

    @Option(help = "Minimum number of invocations or loop iterations needed to compile a guest language root.",
                    category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> CompilationThreshold = new OptionKey<>(SharedTruffleRuntimeOptions.TruffleCompilationThreshold.getDefaultValue());

    @Option(help = "Minimum number of calls before a call target is compiled", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> MinInvokeThreshold = new OptionKey<>(SharedTruffleRuntimeOptions.TruffleMinInvokeThreshold.getDefaultValue());

    @Option(help = "Delay compilation after an invalidation to allow for reprofiling", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> InvalidationReprofileCount = new OptionKey<>(SharedTruffleRuntimeOptions.TruffleInvalidationReprofileCount.getDefaultValue());

    @Option(help = "Delay compilation after a node replacement", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> ReplaceReprofileCount = new OptionKey<>(SharedTruffleRuntimeOptions.TruffleReplaceReprofileCount.getDefaultValue());

    @Option(help = "Whether to use multiple Truffle compilation tiers by default.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> MultiTier = new OptionKey<>(false);

    @Option(help = "Minimum number of invocations or loop iterations needed to compile a guest language root in low tier mode.",
            category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> FirstTierCompilationThreshold = new OptionKey<>(SharedTruffleRuntimeOptions.TruffleFirstTierCompilationThreshold.getDefaultValue());

    @Option(help = "Minimum number of calls before a call target is compiled in the first tier.", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> FirstTierMinInvokeThreshold = new OptionKey<>(SharedTruffleRuntimeOptions.TruffleFirstTierMinInvokeThreshold.getDefaultValue());

    @Option(help = "Configures the execution mode of the engine. Available modes are 'latency' and 'throughput'. The default value balances between the two.",
                    category = OptionCategory.EXPERT)
    public static final OptionKey<EngineModeEnum> Mode = new OptionKey<>(EngineModeEnum.DEFAULT, ENGINE_MODE_TYPE);

    // Tracing

    @Option(help = "Print information for compilation results.", category = OptionCategory.EXPERT, stability = OptionStability.STABLE)
    public static final OptionKey<Boolean> TraceCompilation = new OptionKey<>(false);

    @Option(help = "Print information for compilation queuing.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> TraceCompilationDetails = new OptionKey<>(false);

    @Option(help = "Print information for inlining decisions.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> TraceInlining = new OptionKey<>(false);

    @Option(help = "Print information for splitting decisions.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> TraceSplitting = new OptionKey<>(false);

    // Inlining

    @Option(help = "Enable automatic inlining of guest language call targets.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> Inlining = new OptionKey<>(true);

    @Option(help = "Maximum number of inlined non-trivial AST nodes per compilation unit.", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> InliningNodeBudget = new OptionKey<>(SharedTruffleRuntimeOptions.TruffleInliningMaxCallerSize.getDefaultValue());

    @Option(help = "Maximum depth for recursive inlining.", category = OptionCategory.EXPERT)
    public static final OptionKey<Integer> InliningRecursionDepth = new OptionKey<>(SharedTruffleRuntimeOptions.TruffleMaximumRecursiveInlining.getDefaultValue());

    // Splitting

    @Option(help = "Enable automatic duplication of compilation profiles (splitting).",
                    category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> Splitting = new OptionKey<>(true);

    @Option(help = "Disable call target splitting if tree size exceeds this limit", category = OptionCategory.INTERNAL)
    public static final OptionKey<Integer> SplittingMaxCalleeSize = new OptionKey<>(SharedTruffleRuntimeOptions.TruffleSplittingMaxCalleeSize.getDefaultValue());

    @Option(help = "Disable call target splitting if the number of nodes created by splitting exceeds this factor times node count", category = OptionCategory.INTERNAL)
    public static final OptionKey<Double> SplittingGrowthLimit = new OptionKey<>(SharedTruffleRuntimeOptions.TruffleSplittingGrowthLimit.getDefaultValue());

    @Option(help = "Disable call target splitting if number of nodes created by splitting exceeds this limit", category = OptionCategory.INTERNAL)
    public static final OptionKey<Integer> SplittingMaxNumberOfSplitNodes = new OptionKey<>(SharedTruffleRuntimeOptions.TruffleSplittingMaxNumberOfSplitNodes.getDefaultValue());

    @Option(help = "Propagate info about a polymorphic specialize through maximum this many call targets", category = OptionCategory.INTERNAL)
    public static final OptionKey<Integer> SplittingMaxPropagationDepth = new OptionKey<>(SharedTruffleRuntimeOptions.TruffleSplittingMaxPropagationDepth.getDefaultValue());

    @Option(help = "Use legacy splitting heuristic. This option will be removed.", category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> LegacySplitting = new OptionKey<>(false);

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
    public static final OptionKey<Integer> OSRCompilationThreshold = new OptionKey<>(SharedTruffleRuntimeOptions.TruffleOSRCompilationThreshold.getDefaultValue());

    /*
     * TODO planned options (GR-13444):
     *
    @Option(help = "Trace deoptimization of compilation units.",
                    category = OptionCategory.EXPERT)
    public static final OptionKey<Boolean> TraceDeoptimization = new OptionKey<>(false);
    */

    // @formatter:on

    private static final EconomicMap<OptionKey<?>, OptionKey<?>> POLYGLOT_TO_TRUFFLE = EconomicMap.create(Equivalence.IDENTITY);
    static {
        initializePolyglotToGraalMapping();
    }

    private static void initializePolyglotToGraalMapping() {
        POLYGLOT_TO_TRUFFLE.put(Compilation, SharedTruffleRuntimeOptions.TruffleCompilation);
        POLYGLOT_TO_TRUFFLE.put(CompileOnly, SharedTruffleRuntimeOptions.TruffleCompileOnly);
        POLYGLOT_TO_TRUFFLE.put(CompileImmediately, SharedTruffleRuntimeOptions.TruffleCompileImmediately);
        POLYGLOT_TO_TRUFFLE.put(BackgroundCompilation, SharedTruffleRuntimeOptions.TruffleBackgroundCompilation);
        POLYGLOT_TO_TRUFFLE.put(CompilerThreads, SharedTruffleRuntimeOptions.TruffleCompilerThreads);
        POLYGLOT_TO_TRUFFLE.put(CompilationThreshold, SharedTruffleRuntimeOptions.TruffleCompilationThreshold);
        POLYGLOT_TO_TRUFFLE.put(MinInvokeThreshold, SharedTruffleRuntimeOptions.TruffleMinInvokeThreshold);
        POLYGLOT_TO_TRUFFLE.put(InvalidationReprofileCount, SharedTruffleRuntimeOptions.TruffleInvalidationReprofileCount);
        POLYGLOT_TO_TRUFFLE.put(ReplaceReprofileCount, SharedTruffleRuntimeOptions.TruffleReplaceReprofileCount);

        POLYGLOT_TO_TRUFFLE.put(MultiTier, SharedTruffleRuntimeOptions.TruffleMultiTier);
        POLYGLOT_TO_TRUFFLE.put(FirstTierCompilationThreshold, SharedTruffleRuntimeOptions.TruffleFirstTierCompilationThreshold);
        POLYGLOT_TO_TRUFFLE.put(FirstTierMinInvokeThreshold, SharedTruffleRuntimeOptions.TruffleFirstTierMinInvokeThreshold);

        POLYGLOT_TO_TRUFFLE.put(TraceCompilation, SharedTruffleRuntimeOptions.TraceTruffleCompilation);
        POLYGLOT_TO_TRUFFLE.put(TraceCompilationDetails, SharedTruffleRuntimeOptions.TraceTruffleCompilationDetails);
        POLYGLOT_TO_TRUFFLE.put(TraceInlining, SharedTruffleRuntimeOptions.TraceTruffleInlining);
        POLYGLOT_TO_TRUFFLE.put(TraceSplitting, SharedTruffleRuntimeOptions.TraceTruffleSplitting);

        POLYGLOT_TO_TRUFFLE.put(Inlining, SharedTruffleRuntimeOptions.TruffleFunctionInlining);
        POLYGLOT_TO_TRUFFLE.put(InliningNodeBudget, SharedTruffleRuntimeOptions.TruffleInliningMaxCallerSize);
        POLYGLOT_TO_TRUFFLE.put(InliningRecursionDepth, SharedTruffleRuntimeOptions.TruffleMaximumRecursiveInlining);

        POLYGLOT_TO_TRUFFLE.put(Splitting, SharedTruffleRuntimeOptions.TruffleSplitting);
        POLYGLOT_TO_TRUFFLE.put(SplittingMaxCalleeSize, SharedTruffleRuntimeOptions.TruffleSplittingMaxCalleeSize);
        POLYGLOT_TO_TRUFFLE.put(SplittingGrowthLimit, SharedTruffleRuntimeOptions.TruffleSplittingGrowthLimit);
        POLYGLOT_TO_TRUFFLE.put(SplittingMaxNumberOfSplitNodes, SharedTruffleRuntimeOptions.TruffleSplittingMaxNumberOfSplitNodes);
        POLYGLOT_TO_TRUFFLE.put(SplittingMaxPropagationDepth, SharedTruffleRuntimeOptions.TruffleSplittingMaxPropagationDepth);
        POLYGLOT_TO_TRUFFLE.put(LegacySplitting, SharedTruffleRuntimeOptions.TruffleLegacySplitting);
        POLYGLOT_TO_TRUFFLE.put(TraceSplittingSummary, SharedTruffleRuntimeOptions.TruffleTraceSplittingSummary);
        POLYGLOT_TO_TRUFFLE.put(SplittingTraceEvents, SharedTruffleRuntimeOptions.TruffleSplittingTraceEvents);
        POLYGLOT_TO_TRUFFLE.put(SplittingDumpDecisions, SharedTruffleRuntimeOptions.TruffleSplittingDumpDecisions);
        POLYGLOT_TO_TRUFFLE.put(SplittingAllowForcedSplits, SharedTruffleRuntimeOptions.TruffleSplittingAllowForcedSplits);

        POLYGLOT_TO_TRUFFLE.put(OSR, SharedTruffleRuntimeOptions.TruffleOSR);
        POLYGLOT_TO_TRUFFLE.put(OSRCompilationThreshold, SharedTruffleRuntimeOptions.TruffleOSRCompilationThreshold);
    }

    /**
     * Uses the --engine option if set, otherwise fallback on the -Dgraal option.
     */
    @SuppressWarnings("unchecked")
    static <T> T getValue(OptionValues polyglotValues, OptionKey<T> key) {
        if (polyglotValues != null && polyglotValues.hasBeenSet(key)) {
            return polyglotValues.get(key);
        } else {
            OptionKey<?> truffleKey = POLYGLOT_TO_TRUFFLE.get(key);
            if (truffleKey != null) {
                return (T) TruffleRuntimeOptions.getValue(truffleKey);
            }
        }
        return key.getDefaultValue();
    }

    static OptionDescriptors getDescriptors() {
        return new PolyglotCompilerOptionsOptionDescriptors();
    }

}
