/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime;

import java.util.function.Function;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.SandboxPolicy;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Option;

/**
 * Truffle compilation options that can be configured per {@link Engine engine} instance. These
 * options are accessed by the Truffle runtime and not the Truffle compiler, unlike
 * jdk.graal.compiler.truffle.TruffleCompilerOptions
 */
@Option.Group("engine")
public final class OptimizedRuntimeOptions {
    public enum EngineModeEnum {
        DEFAULT,
        LATENCY,
        THROUGHPUT;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * Actions to take upon an exception being raised during Truffle compilation. The actions are
     * with respect to what the user sees on the console. The enum constants and order are the same
     * as defined in {@code jdk.graal.compiler.core.CompilationWrapper.ExceptionAction}.
     *
     * The actions are in ascending order of verbosity. Ordinal order is semantically significant!
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

    @Option(help = "Speculate on arguments types at call sites (default: true)", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> ArgumentTypeSpeculation = new OptionKey<>(true);

    @Option(help = "Enable asynchronous truffle compilation in background threads (default: true)", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> BackgroundCompilation = new OptionKey<>(true);

    @Option(help = "Enable or disable Truffle compilation.", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> Compilation = new OptionKey<>(true);

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

    @Option(help = ExceptionAction.HELP, usageSyntax = "Silent|Print|Throw|Diagnose|ExitVM", category = OptionCategory.EXPERT, stability = OptionStability.STABLE, sandbox = SandboxPolicy.UNTRUSTED) //
    public static final OptionKey<ExceptionAction> CompilationFailureAction = new OptionKey<>(ExceptionAction.Silent, EXCEPTION_ACTION_TYPE);

    @Option(help = "Print additional more verbose Truffle compilation statistics at the end of a run.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> CompilationStatisticDetails = new OptionKey<>(false);

    @Option(help = "Print Truffle compilation statistics at the end of a run.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> CompilationStatistics = new OptionKey<>(false);

    @Option(help = "Compiles created call targets immediately with last tier. Disables background compilation if enabled.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> CompileAOTOnCreate = new OptionKey<>(false);

    @Option(help = "Compile immediately to test Truffle compilation", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> CompileImmediately = new OptionKey<>(false);

    @Option(help = "Restrict compilation to ','-separated list of includes (or excludes prefixed with '~'). No restriction by default.", usageSyntax = "<name>,<name>,...", category = OptionCategory.INTERNAL) //
    public static final OptionKey<String> CompileOnly = new OptionKey<>(null, OptionType.defaultType(String.class));

    @Option(help = "Set the time in milliseconds an idle Truffle compiler thread will wait for new tasks before terminating. " +
                    "New compiler threads will be started once new compilation tasks are submitted. " +
                    "Select '0' to never terminate the Truffle compiler thread. " +
                    "The option is not supported by all Truffle runtimes. On the runtime which doesn't support it the option has no effect. default: 10000", usageSyntax = "<ms>", category = OptionCategory.EXPERT) //
    // TODO: GR-29949
    public static final OptionKey<Long> CompilerIdleDelay = new OptionKey<>(10000L);

    @Option(help = "Manually set the number of compiler threads. By default, the number of compiler threads is scaled with the number of available cores on the CPU.", usageSyntax = "[1, inf)", category = OptionCategory.EXPERT, //
                    stability = OptionStability.STABLE, sandbox = SandboxPolicy.UNTRUSTED) //
    public static final OptionKey<Integer> CompilerThreads = new OptionKey<>(-1);

    @Option(help = "Reduce or increase the compilation threshold depending on the size of the compilation queue (default: true).", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> DynamicCompilationThresholds = new OptionKey<>(true);

    @Option(help = "The desired maximum compilation queue load. When the load rises above this value, the compilation thresholds are increased. The load is scaled by the number of compiler threads.  (default: 10)", //
                    usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> DynamicCompilationThresholdsMaxNormalLoad = new OptionKey<>(90);

    @Option(help = "The desired minimum compilation queue load. When the load falls bellow this value, the compilation thresholds are decreased. The load is scaled by the number of compiler threads (default: 10).", //
                    usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> DynamicCompilationThresholdsMinNormalLoad = new OptionKey<>(10);

    @Option(help = "The minimal scale the compilation thresholds can be reduced to (default: 0.1).", usageSyntax = "[0.0, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Double> DynamicCompilationThresholdsMinScale = new OptionKey<>(0.1);

    @Option(help = "Delay, in milliseconds, after which the encoded graph cache is dropped when a Truffle compiler thread becomes idle (default: 10000).", //
                    usageSyntax = "<ms>", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> EncodedGraphCachePurgeDelay = new OptionKey<>(10_000);

    @Option(help = "Whether to emit look-back-edge counters in the first-tier compilations. (default: true)", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> FirstTierBackedgeCounts = new OptionKey<>(true);

    @Option(help = "Number of invocations or loop iterations needed to compile a guest language root in first tier under normal compilation load." + //
                    "Might be reduced/increased when compilation load is low/high if DynamicCompilationThresholds is enabled. (default: 400).", //
                    usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> FirstTierCompilationThreshold = new OptionKey<>(400);

    @Option(help = "Minimum number of calls before a call target is compiled in the first tier (default: 1)", usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> FirstTierMinInvokeThreshold = new OptionKey<>(1);

    @Option(help = "Number of invocations or loop iterations needed to compile a guest language root in first tier under normal compilation load." + //
                    "Might be reduced/increased when compilation load is low/high if DynamicCompilationThresholds is enabled. (default: 10000).", //
                    usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> LastTierCompilationThreshold = new OptionKey<>(10000);

    @Option(help = "Minimum number of calls before a call target is compiled (default: 3).", usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> MinInvokeThreshold = new OptionKey<>(3);

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

    @Option(help = "Configures the execution mode of the engine. Available modes are 'latency' and 'throughput'. The default value balances between the two.", //
                    usageSyntax = "latency|throughput", category = OptionCategory.EXPERT, stability = OptionStability.STABLE, sandbox = SandboxPolicy.UNTRUSTED) //
    public static final OptionKey<EngineModeEnum> Mode = new OptionKey<>(EngineModeEnum.DEFAULT, ENGINE_MODE_TYPE);

    @Option(help = "Whether to use multiple Truffle compilation tiers by default. (default: true)", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> MultiTier = new OptionKey<>(true);

    @Option(help = "Enable automatic on-stack-replacement of loops (default: true).", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> OSR = new OptionKey<>(true);

    @Option(help = "Number of loop iterations until on-stack-replacement compilation is triggered (default 100352).", usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    // Note: default value is a multiple of the bytecode OSR polling interval.
    public static final OptionKey<Integer> OSRCompilationThreshold = new OptionKey<>(100352);

    @Option(help = "Number of compilation re-attempts before bailing out of OSR compilation for a given method (default 30). This number is an approximation of the acceptable number of deopts.", //
                    usageSyntax = "[0, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> OSRMaxCompilationReAttempts = new OptionKey<>(30);

    @Option(help = "Enable partial compilation for BlockNode (default: true).", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> PartialBlockCompilation = new OptionKey<>(true);

    @Option(help = "Sets the target non-trivial Truffle node size for partial compilation of BlockNode nodes (default: 3000).", usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> PartialBlockCompilationSize = new OptionKey<>(3000);

    @Option(help = "Sets the maximum non-trivial Truffle node size for partial compilation of BlockNode nodes (default: 10000).", usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> PartialBlockMaximumSize = new OptionKey<>(10000);

    @Option(help = "Use the priority of compilation jobs in the compilation queue (default: true).", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> PriorityQueue = new OptionKey<>(true);

    @Option(help = "Enable/disable builtin profiles in com.oracle.truffle.api.profiles. (default: true)", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> Profiling = new OptionKey<>(true);

    @Option(help = "Enables hotness propagation to lexical parent to lexically parent single callers.", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> PropagateLoopCountToLexicalSingleCaller = new OptionKey<>(true);

    @Option(help = "How high to propagate call and loop count (hotness proxy) up a single caller chain to lexical scope parent.", usageSyntax = "[0, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> PropagateLoopCountToLexicalSingleCallerMaxDepth = new OptionKey<>(10);

    @Option(help = "Speculate on return types at call sites (default: true)", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> ReturnTypeSpeculation = new OptionKey<>(true);

    @Option(help = "Minimum number of invocations or loop iterations needed to compile a guest language root when not using multi tier (default: 1000).", usageSyntax = "[1, inf)", category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> SingleTierCompilationThreshold = new OptionKey<>(1000);

    @Option(help = "Enable automatic duplication of compilation profiles (splitting) (default: true).", usageSyntax = "true|false", category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> Splitting = new OptionKey<>(true);

    @Option(help = "Should forced splits be allowed (default: true)", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> SplittingAllowForcedSplits = new OptionKey<>(true);

    @Option(help = "Dumps to IGV information on polymorphic events", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> SplittingDumpDecisions = new OptionKey<>(false);

    @Option(help = "Disable call target splitting if the number of nodes created by splitting exceeds this factor times node count (default: 1.5).", usageSyntax = "[0.0, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Double> SplittingGrowthLimit = new OptionKey<>(1.5);

    @Option(help = "Disable call target splitting if tree size exceeds this limit (default: 100)", usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> SplittingMaxCalleeSize = new OptionKey<>(100);

    @Option(help = "Propagate info about a polymorphic specialize through maximum this many call targets (default: 5)", usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> SplittingMaxPropagationDepth = new OptionKey<>(5);

    @Option(help = "Trace details of splitting events and decisions.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> SplittingTraceEvents = new OptionKey<>(false);

    @Option(help = "Whether an AssertionError is thrown when the maximum number of OSR compilation attempts is reached for a given method (default 'false'). This should only be set to 'true' in testing environments.", //
                    usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> ThrowOnMaxOSRCompilationReAttemptsReached = new OptionKey<>(false);

    @Option(help = "Print stack trace on assumption invalidation", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceAssumptions = new OptionKey<>(false);

    @Option(help = "Print information for compilation results.", category = OptionCategory.EXPERT, stability = OptionStability.STABLE) //
    public static final OptionKey<Boolean> TraceCompilation = new OptionKey<>(false);

    @Option(help = "Print the entire AST after each compilation", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceCompilationAST = new OptionKey<>(false);

    @Option(help = "Print information for compilation queuing.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceCompilationDetails = new OptionKey<>(false);

    @Option(help = "Print all polymorphic and generic nodes after each compilation", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceCompilationPolymorphism = new OptionKey<>(false);

    @Option(help = "Print stack trace when deoptimizing a frame from the stack with `FrameInstance#getFrame(READ_WRITE|MATERIALIZE)`.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceDeoptimizeFrame = new OptionKey<>(false);

    @Option(help = "Print information for splitting decisions.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceSplitting = new OptionKey<>(false);

    @Option(help = "Used for debugging the splitting implementation. Prints splitting summary directly to stdout on shutdown", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceSplittingSummary = new OptionKey<>(false);

    @Option(help = "Number of stack trace elements printed by TraceTruffleTransferToInterpreter, TraceTruffleAssumptions and TraceDeoptimizeFrame (default: 20).", usageSyntax = "[1, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Integer> TraceStackTraceLimit = new OptionKey<>(20);

    @Option(help = "Print stack trace on transfer to interpreter.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraceTransferToInterpreter = new OptionKey<>(false);

    @Option(help = "Use a traversing compilation queue. (default: true)", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraversingCompilationQueue = new OptionKey<>(true);

    @Option(help = "Controls how much of a priority should be given to first tier compilations (default 15.0).", usageSyntax = "[0.0, inf)", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Double> TraversingQueueFirstTierBonus = new OptionKey<>(15.0);

    @Option(help = "Traversing queue gives first tier compilations priority.", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraversingQueueFirstTierPriority = new OptionKey<>(false);

    @Option(help = "Traversing queue uses rate as priority for both tier. (default: true)", usageSyntax = "true|false", category = OptionCategory.INTERNAL) //
    public static final OptionKey<Boolean> TraversingQueueWeightingBothTiers = new OptionKey<>(true);

    public static OptionDescriptors getDescriptors() {
        return new OptimizedRuntimeOptionsOptionDescriptors();
    }

}
