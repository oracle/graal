/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.CallTarget;
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
        LATENCY;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    static final OptionType<EngineModeEnum> ENGINE_MODE_TYPE = new OptionType<>("EngineMode", new Function<String, EngineModeEnum>() {
        @Override
        public EngineModeEnum apply(String s) {
            try {
                return EngineModeEnum.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Mode can be: 'default', 'latency' or 'throughput'.");
            }
        }
    });

    public enum PerformanceWarningKind {
        VIRTUAL_RUNTIME_CALL("call", "Enables virtual call warnings"),
        VIRTUAL_INSTANCEOF("instanceof", "Enables virtual instanceof warnings"),
        VIRTUAL_STORE("store", "Enables virtual store warnings"),
        FRAME_INCOMPATIBLE_MERGE("frame_merge", "Enables warnings about deopts inserted for incompatible frame slot merges"),
        TRIVIAL_FAIL("trivial", "Enables trivial fail warnings"),
        // keep optional until all warnings in downstream are resolved
        MISSING_LOOP_FREQUENCY_INFO("loop", "Enables missing loop frequency warnings", true);

        private static final EconomicMap<String, PerformanceWarningKind> kindByName;
        static {
            kindByName = EconomicMap.create();
            for (PerformanceWarningKind kind : PerformanceWarningKind.values()) {
                kindByName.put(kind.name, kind);
            }
        }

        final String name;
        final String help;
        final boolean isOptional;

        PerformanceWarningKind(String name, String help) {
            this(name, help, false);
        }

        PerformanceWarningKind(String name, String help, boolean isOptional) {
            this.name = name;
            this.help = help;
            this.isOptional = isOptional;
        }

        boolean isOptional() {
            return isOptional;
        }

        public static PerformanceWarningKind forName(String name) {
            PerformanceWarningKind kind = kindByName.get(name);
            if (kind == null) {
                throw new IllegalArgumentException("Unknown PerformanceWarningKind name " + name);
            }
            return kind;
        }
    }

    public enum CompilationTier {
        peTier,
        truffleTier,
        lowTier;

        static CompilationTier parse(String name) {
            try {
                return CompilationTier.valueOf(name);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format("Unknown tier option value '%s'. %s", name, EXPANSION_VALUES));
            }
        }
    }

    static final OptionType<Set<CompilationTier>> COMPILATION_TIERS_TYPE = new OptionType<>("Tier", new Function<String, Set<CompilationTier>>() {
        @Override
        public Set<CompilationTier> apply(String s) {
            if (s.equals("true")) {
                return Collections.singleton(CompilationTier.truffleTier);
            } else if (s.equals("false")) {
                return Collections.emptySet();
            }
            String[] strings = s.split(",");
            EnumSet<CompilationTier> tiers = EnumSet.noneOf(CompilationTier.class);
            for (int i = 0; i < strings.length; i++) {
                tiers.add(CompilationTier.parse(strings[i]));
            }
            return Collections.unmodifiableSet(tiers);
        }
    });

    /**
     * Actions to take upon an exception being raised during Truffle compilation. The actions are
     * with respect to what the user sees on the console. The enum constants and order are the same
     * as defined in {@code org.graalvm.compiler.core.CompilationWrapper.ExceptionAction}.
     *
     * The actions are in ascending order of verbosity.
     */
    public enum ExceptionAction {
        /**
         * Print nothing to the console.
         */
        Silent,

        /**
         * Print a stack trace to the console.
         */
        Print,

        /**
         * Throw the exception to {@link CallTarget} caller.
         */
        Throw,

        /**
         * Retry compilation with extra diagnostics enabled.
         */
        Diagnose,

        /**
         * Exit the VM process.
         */
        ExitVM;

        private static final String HELP = "Specifies the action to take when Truffle compilation fails.%n" +
                        "The accepted values are:%n" +
                        "    Silent - Print nothing to the console.%n" +
                        "     Print - Print the exception to the console.%n" +
                        "     Throw - Throw the exception to caller.%n" +
                        "  Diagnose - Retry compilation with extra diagnostics enabled.%n" +
                        "    ExitVM - Exit the VM process.";
    }

    static final OptionType<ExceptionAction> EXCEPTION_ACTION_TYPE = new OptionType<>("ExceptionAction", new Function<String, ExceptionAction>() {
        @Override
        public ExceptionAction apply(String s) {
            try {
                return ExceptionAction.valueOf(s);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format(ExceptionAction.HELP));
            }
        }
    });

    static final OptionType<Set<PerformanceWarningKind>> PERFORMANCE_WARNING_TYPE = new OptionType<>("PerformanceWarningKind", new Function<String, Set<PerformanceWarningKind>>() {
        @Override
        public Set<PerformanceWarningKind> apply(String value) {
            if ("none".equals(value)) {
                return EnumSet.noneOf(PerformanceWarningKind.class);
            } else if ("all".equals(value)) {
                Set<PerformanceWarningKind> result = EnumSet.allOf(PerformanceWarningKind.class);
                result.removeIf(PerformanceWarningKind::isOptional);
                return result;
            } else {
                Set<PerformanceWarningKind> result = EnumSet.noneOf(PerformanceWarningKind.class);
                for (String name : value.split(",")) {
                    if ("bailout".equals(name)) {
                        /*
                         * The PerformanceWarningKind.BAILOUT was removed but 'bailout' can still
                         * appear in option value due to backward compatibility. We need to ignore
                         * the 'bailout' option value.
                         */
                        continue;
                    }
                    try {
                        result.add(PerformanceWarningKind.forName(name));
                    } catch (IllegalArgumentException e) {
                        String message = String.format("The \"%s\" is not a valid performance warning kind. Valid values are%n", name);
                        for (PerformanceWarningKind kind : PerformanceWarningKind.values()) {
                            message = message + String.format("%s%s%s%n", kind.name, indent(kind.name.length()), kind.help);
                        }
                        message = message + String.format("all%sEnables all performance warnings%n", indent(3));
                        message = message + String.format("none%sDisables performance warnings%n", indent(4));
                        throw new IllegalArgumentException(message);
                    }
                }
                return result;
            }
        }

        private String indent(int nameLength) {
            int len = Math.max(1, 16 - nameLength);
            return new String(new char[len]).replace('\0', ' ');
        }
    });

    private static final String PERFORMANCE_WARNING_SYNTAX = "none|all|<perfWarning>,<perfWarning>,...";
    private static final String PERFORMANCE_WARNING_LIST = "Performance warnings are: call, instanceof, store, frame_merge, trivial.";

    // Compilation

    @Option(help = "Configures the execution mode of the engine. Available modes are 'latency' and 'throughput'. The default value balances between the two.", //
                    usageSyntax = "latency|throughput", category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<EngineModeEnum> Mode = new OptionKey<>(EngineModeEnum.DEFAULT, ENGINE_MODE_TYPE);

    @Option(help = "Enable or disable Truffle compilation.", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> Compilation = new OptionKey<>(true);

    @Option(help = "Restrict compilation to ','-separated list of includes (or excludes prefixed with '~'). No restriction by default.", usageSyntax = "<name>,<name>,...", category = OptionCategory.INTERNAL) //
    public static final OptionKey<String> CompileOnly = new OptionKey<>(null, OptionType.defaultType(String.class));

    @Option(help = "Compile immediately to test Truffle compilation", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> CompileImmediately = new OptionKey<>(false);

    @Option(help = "Compiles created call targets immediately with last tier. Disables background compilation if enabled.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> CompileAOTOnCreate = new OptionKey<>(false);

    @Option(help = "Enable asynchronous truffle compilation in background threads (default: true)", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> BackgroundCompilation = new OptionKey<>(true);

    @Option(help = "Manually set the number of compiler threads. By default, the number of compiler threads is scaled with the number of available cores on the CPU.", usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> CompilerThreads = new OptionKey<>(-1);

    @Option(help = "Set the time in milliseconds an idle Truffle compiler thread will wait for new tasks before terminating. " +
                    "New compiler threads will be started once new compilation tasks are submitted. " +
                    "Select '0' to never terminate the Truffle compiler thread. " +
                    "The option is not supported by all Truffle runtimes. On the runtime which doesn't support it the option has no effect. default: 10000", usageSyntax = "<ms>", category = OptionCategory.EXPERT) //
    // TODO: GR-29949
    public static final OptionKey<Long> CompilerIdleDelay = new OptionKey<>(10000L);

    @Option(help = "Minimum number of invocations or loop iterations needed to compile a guest language root when not using multi tier (default: 1000).", usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> SingleTierCompilationThreshold = new OptionKey<>(1000);

    @Option(help = "Minimum number of calls before a call target is compiled (default: 3).", usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> MinInvokeThreshold = new OptionKey<>(3);

    @Option(help = "Delay compilation after an invalidation to allow for reprofiling. Deprecated: no longer has any effect.", category = OptionCategory.EXPERT, deprecated = true) //
    public static final OptionKey<Integer> InvalidationReprofileCount = new OptionKey<>(3);

    @Option(help = "Delay compilation after a node replacement. Deprecated: no longer has any effect.", category = OptionCategory.EXPERT, deprecated = true) //
    public static final OptionKey<Integer> ReplaceReprofileCount = new OptionKey<>(3);

    @Option(help = "Speculate on arguments types at call sites (default: true)", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> ArgumentTypeSpeculation = new OptionKey<>(true);

    @Option(help = "Speculate on return types at call sites (default: true)", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> ReturnTypeSpeculation = new OptionKey<>(true);

    @Option(help = "Enable/disable builtin profiles in com.oracle.truffle.api.profiles. (default: true)", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> Profiling = new OptionKey<>(true);

    // MultiTier

    @Option(help = "Whether to use multiple Truffle compilation tiers by default. (default: true)", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> MultiTier = new OptionKey<>(true);

    @Option(help = "Explicitly pick a first tier inlining policy by name (None, TrivialOnly). If empty (default) the lowest priority policy (TrivialOnly) is chosen.", usageSyntax = "<policy>", category = OptionCategory.INTERNAL) //
    public static final OptionKey<String> FirstTierInliningPolicy = new OptionKey<>("");

    @Option(help = "Number of invocations or loop iterations needed to compile a guest language root in first tier under normal compilation load." + //
                    "Might be reduced/increased when compilation load is low/high if DynamicCompilationThresholds is enabled. (default: 400).", //
                    usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> FirstTierCompilationThreshold = new OptionKey<>(400);

    @Option(help = "Number of invocations or loop iterations needed to compile a guest language root in first tier under normal compilation load." + //
                    "Might be reduced/increased when compilation load is low/high if DynamicCompilationThresholds is enabled. (default: 10000).", //
                    usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> LastTierCompilationThreshold = new OptionKey<>(10000);

    @Option(help = "Minimum number of calls before a call target is compiled in the first tier (default: 1)", usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> FirstTierMinInvokeThreshold = new OptionKey<>(1);

    @Option(help = "Whether to use the economy configuration in the first-tier compilations. (default: true)", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> FirstTierUseEconomy = new OptionKey<>(true);

    @Option(help = "Whether to emit look-back-edge counters in the first-tier compilations. (default: true)", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> FirstTierBackedgeCounts = new OptionKey<>(true);

    // Failed compilation behavior

    @Option(help = "Prints the exception stack trace for compilation exceptions", category = OptionCategory.INTERNAL, deprecated = true, deprecationMessage = "Use 'engine.CompilationFailureAction=Print'") //
    public static final OptionKey<Boolean> CompilationExceptionsArePrinted = new OptionKey<>(false);

    @Option(help = "Treat compilation exceptions as thrown runtime exceptions", category = OptionCategory.INTERNAL, deprecated = true, deprecationMessage = "Use 'engine.CompilationFailureAction=Throw'") //
    public static final OptionKey<Boolean> CompilationExceptionsAreThrown = new OptionKey<>(false);

    @Option(help = "Treat compilation exceptions as fatal exceptions that will exit the application", category = OptionCategory.INTERNAL, deprecated = true, deprecationMessage = "Use 'engine.CompilationFailureAction=ExitVM'") //
    public static final OptionKey<Boolean> CompilationExceptionsAreFatal = new OptionKey<>(false);

    @Option(help = "Treat performance warnings as fatal occurrences that will exit the applications", category = OptionCategory.INTERNAL, deprecated = true, //
                    deprecationMessage = "Use 'engine.CompilationFailureAction=ExitVM' 'engine.TreatPerformanceWarningsAsErrors=<PerformanceWarningKinds>'") //
    public static final OptionKey<Set<PerformanceWarningKind>> PerformanceWarningsAreFatal = new OptionKey<>(Collections.emptySet(), PERFORMANCE_WARNING_TYPE);

    @Option(help = ExceptionAction.HELP, usageSyntax = "Silent|Print|Throw|Diagnose|ExitVM", category = OptionCategory.INTERNAL) //
    public static final OptionKey<ExceptionAction> CompilationFailureAction = new OptionKey<>(ExceptionAction.Silent, EXCEPTION_ACTION_TYPE);

    @Option(help = "Treat performance warnings as error. Handling of the error depends on the CompilationFailureAction option value. " +
                    PERFORMANCE_WARNING_LIST, usageSyntax = PERFORMANCE_WARNING_SYNTAX, category = OptionCategory.INTERNAL) //
    public static final OptionKey<Set<PerformanceWarningKind>> TreatPerformanceWarningsAsErrors = new OptionKey<>(Collections.emptySet(), PERFORMANCE_WARNING_TYPE);

    // Tracing

    @Option(help = "Print information for compilation results.", category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> TraceCompilation = new OptionKey<>(false);

    @Option(help = "Print information for compilation queuing.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceCompilationDetails = new OptionKey<>(false);

    @Option(help = "Print all polymorphic and generic nodes after each compilation", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceCompilationPolymorphism = new OptionKey<>(false);

    @Option(help = "Print the entire AST after each compilation", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceCompilationAST = new OptionKey<>(false);

    @Option(help = "Print information for inlining decisions.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceInlining = new OptionKey<>(false);

    @Option(help = "Print information for splitting decisions.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceSplitting = new OptionKey<>(false);

    @Option(help = "Print Truffle compilation statistics at the end of a run.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> CompilationStatistics = new OptionKey<>(false);

    @Option(help = "Print additional more verbose Truffle compilation statistics at the end of a run.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> CompilationStatisticDetails = new OptionKey<>(false);

    @Option(help = "Number of stack trace elements printed by TraceTruffleTransferToInterpreter, TraceTruffleAssumptions and TraceDeoptimizeFrame (default: 20).", usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> TraceStackTraceLimit = new OptionKey<>(20);

    @Option(help = "Print stack trace on transfer to interpreter.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceTransferToInterpreter = new OptionKey<>(false);

    @Option(help = "Print stack trace on assumption invalidation", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceAssumptions = new OptionKey<>(false);

    @Option(help = "Print stack trace when deoptimizing a frame from the stack with `FrameInstance#getFrame(READ_WRITE|MATERIALIZE)`.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceDeoptimizeFrame = new OptionKey<>(false);

    private static final String EXPANSION_VALUES = "Accepted values are:%n" +
                    "    true - Collect data for the default tier 'truffleTier'.%n" +
                    "    false - No data will be collected.%n" +
                    "Or one or multiple tiers separated by comma (e.g. truffleTier,lowTier):%n" +
                    "    peTier - After partial evaluation without additional phases applied.%n" +
                    "    truffleTier - After partial evaluation with additional phases applied.%n" +
                    "    lowTier - After low tier phases were applied.";

    private static final String EXPANSION_SYNTAX = "true|false|peTier|truffleTier|lowTier|<tier>,<tier>,...";

    @Option(help = "Print a tree of all expanded Java methods with statistics after each compilation. " + EXPANSION_VALUES, usageSyntax = EXPANSION_SYNTAX, category = OptionCategory.INTERNAL) //
    public static final OptionKey<Set<CompilationTier>> TraceMethodExpansion = new OptionKey<>(Collections.emptySet(), COMPILATION_TIERS_TYPE);

    @Option(help = "Print a tree of all expanded Truffle nodes with statistics after each compilation. " + EXPANSION_VALUES, usageSyntax = EXPANSION_SYNTAX, category = OptionCategory.INTERNAL) //
    public static final OptionKey<Set<CompilationTier>> TraceNodeExpansion = new OptionKey<>(Collections.emptySet(), COMPILATION_TIERS_TYPE);

    @Option(help = "Print statistics on expanded Java methods during partial evaluation at the end of a run." + EXPANSION_VALUES, usageSyntax = EXPANSION_SYNTAX, category = OptionCategory.INTERNAL) //
    public static final OptionKey<Set<CompilationTier>> MethodExpansionStatistics = new OptionKey<>(Collections.emptySet(), COMPILATION_TIERS_TYPE);

    @Option(help = "Print statistics on expanded Truffle nodes during partial evaluation at the end of a run." + EXPANSION_VALUES, usageSyntax = EXPANSION_SYNTAX, category = OptionCategory.INTERNAL) //
    public static final OptionKey<Set<CompilationTier>> NodeExpansionStatistics = new OptionKey<>(Collections.emptySet(), COMPILATION_TIERS_TYPE);

    @Option(help = "Prints a histogram of all expanded Java methods.", category = OptionCategory.INTERNAL, deprecated = true,  //
                    deprecationMessage = "The expansion histogram was superseeded by engine.TraceMethodExpansion, engine.TraceNodeExpansion, engine.MethodExpansionHistogram and engine.NodeExpansionHistogram.") //
    public static final OptionKey<Boolean> PrintExpansionHistogram = new OptionKey<>(false);

    // Inlining

    @Option(help = "Restrict inlined methods to ','-separated list of includes (or excludes prefixed with '~'). No restriction by default.", usageSyntax = "<name>,<name>,...", category = OptionCategory.INTERNAL) //
    public static final OptionKey<String> InlineOnly = new OptionKey<>(null, OptionType.defaultType(String.class));

    @Option(help = "Enable automatic inlining of guest language call targets (default: true).", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> Inlining = new OptionKey<>(true);

    @Option(help = "Maximum depth for recursive inlining (default: 2).", usageSyntax = "[0, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> InliningRecursionDepth = new OptionKey<>(2);

    // Splitting

    @Option(help = "Enable automatic duplication of compilation profiles (splitting) (default: true).", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> Splitting = new OptionKey<>(true);

    @Option(help = "Disable call target splitting if tree size exceeds this limit (default: 100)", usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> SplittingMaxCalleeSize = new OptionKey<>(100);

    @Option(help = "Disable call target splitting if the number of nodes created by splitting exceeds this factor times node count (default: 1.5).", usageSyntax = "[0.0, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Double> SplittingGrowthLimit = new OptionKey<>(1.5);

    @Option(help = "Propagate info about a polymorphic specialize through maximum this many call targets (default: 5)", usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> SplittingMaxPropagationDepth = new OptionKey<>(5);

    @Option(help = "Used for debugging the splitting implementation. Prints splitting summary directly to stdout on shutdown", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceSplittingSummary = new OptionKey<>(false);

    @Option(help = "Trace details of splitting events and decisions.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> SplittingTraceEvents = new OptionKey<>(false);

    @Option(help = "Dumps to IGV information on polymorphic events", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> SplittingDumpDecisions = new OptionKey<>(false);

    @Option(help = "Should forced splits be allowed (default: true)", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> SplittingAllowForcedSplits = new OptionKey<>(true);

    // OSR

    @Option(help = "Enable automatic on-stack-replacement of loops (default: true).", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> OSR = new OptionKey<>(true);

    @Option(help = "Number of loop iterations until on-stack-replacement compilation is triggered (default 100352).", usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    // Note: default value is a multiple of the bytecode OSR polling interval.
    public static final OptionKey<Integer> OSRCompilationThreshold = new OptionKey<>(100352);

    @Option(help = "Number of compilation re-attempts before bailing out of OSR compilation for a given method (default 30). This number is an approximation of the acceptable number of deopts.", //
                    usageSyntax = "[0, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> OSRMaxCompilationReAttempts = new OptionKey<>(30);

    @Option(help = "Whether an AssertionError is thrown when the maximum number of OSR compilation attempts is reached for a given method (default 'false'). This should only be set to 'true' in testing environments.", //
                    usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> ThrowOnMaxOSRCompilationReAttemptsReached = new OptionKey<>(false);

    @Option(help = "Enable partial compilation for BlockNode (default: true).", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> PartialBlockCompilation = new OptionKey<>(true);

    @Option(help = "Sets the target non-trivial Truffle node size for partial compilation of BlockNode nodes (default: 3000).", usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> PartialBlockCompilationSize = new OptionKey<>(3000);

    @Option(help = "Sets the maximum non-trivial Truffle node size for partial compilation of BlockNode nodes (default: 10000).", usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> PartialBlockMaximumSize = new OptionKey<>(10000);

    /*
     * TODO planned options (GR-13444):
     *
     * @Option(help = "Trace deoptimization of compilation units.", category =
     * OptionCategory.EXPERT) public static final OptionKey<Boolean> TraceDeoptimization = new
     * OptionKey<>(false);
     */

    // Compiler options
    @Option(help = "Enable inlining across Truffle boundary", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> InlineAcrossTruffleBoundary = new OptionKey<>(false);

    @Option(help = "Print potential performance problems, " + PERFORMANCE_WARNING_LIST, usageSyntax = PERFORMANCE_WARNING_SYNTAX, category = OptionCategory.INTERNAL) //
    public static final OptionKey<Set<PerformanceWarningKind>> TracePerformanceWarnings = new OptionKey<>(Collections.emptySet(), PERFORMANCE_WARNING_TYPE);

    @Option(help = "Run the partial escape analysis iteratively in Truffle compilation.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> IterativePartialEscape = new OptionKey<>(false);

    @Option(help = "Method filter for host methods in which to add instrumentation.", usageSyntax = "<method>,<method>,...", category = OptionCategory.INTERNAL) //
    public static final OptionKey<String> InstrumentFilter = new OptionKey<>("*.*.*");

    @Option(help = "Maximum number of instrumentation counters available (default: 10000).", usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> InstrumentationTableSize = new OptionKey<>(10000);

    @Option(help = "Stop partial evaluation when the graph exceeded this many nodes (default: 40000).", usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL, deprecated = true, deprecationMessage = "Use MaximumGraalGraphSize.") //
    public static final OptionKey<Integer> MaximumGraalNodeCount = new OptionKey<>(400000);

    @Option(help = "Stop partial evaluation when the graph exceeded this size (default: 150000).", usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> MaximumGraalGraphSize = new OptionKey<>(150_000);

    @Option(help = "Ignore further truffle inlining decisions when the graph exceeded this many nodes (default: 150000).", usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> MaximumInlineNodeCount = new OptionKey<>(150000);

    @Option(help = "Exclude assertion code from Truffle compilations (default: true)", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> ExcludeAssertions = new OptionKey<>(true);

    @Option(help = "Enable node source positions in truffle partial evaluations.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> NodeSourcePositions = new OptionKey<>(false);

    @Option(help = "Instrument Truffle boundaries and output profiling information to the standard output.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> InstrumentBoundaries = new OptionKey<>(false);

    @Option(help = "Instrument Truffle boundaries by considering different inlining sites as different branches.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> InstrumentBoundariesPerInlineSite = new OptionKey<>(false);

    @Option(help = "Instrument branches and output profiling information to the standard output.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> InstrumentBranches = new OptionKey<>(false);

    @Option(help = "Instrument branches by considering different inlining sites as different branches.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> InstrumentBranchesPerInlineSite = new OptionKey<>(false);

    @Option(help = "Cache encoded graphs across Truffle compilations to speed up partial evaluation. (default: true).", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> EncodedGraphCache = new OptionKey<>(true);

    @Option(help = "Allow assumptions during parsing of seed graphs for partial evaluation. Disables the persistent encoded graph cache 'engine.EncodedGraphCache'. (default: false).", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> ParsePEGraphsWithAssumptions = new OptionKey<>(false);

    @Option(help = "Delay, in milliseconds, after which the encoded graph cache is dropped when a Truffle compiler thread becomes idle (default: 10000).", //
                    usageSyntax = "<ms>", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> EncodedGraphCachePurgeDelay = new OptionKey<>(10_000);

    @Option(help = "Forces the frame clearing mechanism to be executed, even if Frame.clear() is not used.", //
                    category = OptionCategory.EXPERT, deprecated = true, deprecationMessage = "The analysis is now always executed, irrespective of this option") //
    public static final OptionKey<Boolean> ForceFrameLivenessAnalysis = new OptionKey<>(false);

    // Compilation queue
    @Option(help = "Use the priority of compilation jobs in the compilation queue (default: true).", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> PriorityQueue = new OptionKey<>(true);

    @Option(help = "Use a traversing compilation queue. (default: true)", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraversingCompilationQueue = new OptionKey<>(true);

    @Option(help = "Traversing queue uses rate as priority for both tier. (default: true)", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraversingQueueWeightingBothTiers = new OptionKey<>(true);

    @Option(help = "Traversing queue gives first tier compilations priority.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraversingQueueFirstTierPriority = new OptionKey<>(false);

    @Option(help = "Controls how much of a priority should be given to first tier compilations (default 15.0).", usageSyntax = "[0.0, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Double> TraversingQueueFirstTierBonus = new OptionKey<>(15.0);

    @Option(help = "Reduce or increase the compilation threshold depending on the size of the compilation queue (default: true).", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> DynamicCompilationThresholds = new OptionKey<>(true);

    @Option(help = "Enables hotness propagation to lexical parent to lexically parent single callers.", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> PropagateLoopCountToLexicalSingleCaller = new OptionKey<>(true);

    @Option(help = "How high to propagate call and loop count (hotness proxy) up a single caller chain to lexical scope parent.", usageSyntax = "[0, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> PropagateLoopCountToLexicalSingleCallerMaxDepth = new OptionKey<>(10);

    @Option(help = "The minimal scale the compilation thresholds can be reduced to (default: 0.1).", usageSyntax = "[0.0, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Double> DynamicCompilationThresholdsMinScale = new OptionKey<>(0.1);

    @Option(help = "The desired minimum compilation queue load. When the load falls bellow this value, the compilation thresholds are decreased. The load is scaled by the number of compiler threads (default: 10).", //
                    usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> DynamicCompilationThresholdsMinNormalLoad = new OptionKey<>(10);

    @Option(help = "The desired maximum compilation queue load. When the load rises above this value, the compilation thresholds are increased. The load is scaled by the number of compiler threads.  (default: 10)", //
                    usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> DynamicCompilationThresholdsMaxNormalLoad = new OptionKey<>(90);

    // Language agnostic inlining

    @Option(help = "Print detailed information for inlining (i.e. the entire explored call tree).", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceInliningDetails = new OptionKey<>(false);

    @Option(help = "Explicitly pick a inlining policy by name. If empty (default) the highest priority chosen by default.", usageSyntax = "<policy>", category = OptionCategory.INTERNAL) //
    public static final OptionKey<String> InliningPolicy = new OptionKey<>("");

    @Option(help = "The base expansion budget for language-agnostic inlining (default: 12000).", usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> InliningExpansionBudget = new OptionKey<>(12_000);

    @Option(help = "The base inlining budget for language-agnostic inlining (default: 12000)", usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> InliningInliningBudget = new OptionKey<>(12_000);

    @Option(help = "Use the graph size as a cost model during inlining (default: false).", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> InliningUseSize = new OptionKey<>(false);

    public static OptionDescriptors getDescriptors() {
        return new PolyglotCompilerOptionsOptionDescriptors();
    }

}
