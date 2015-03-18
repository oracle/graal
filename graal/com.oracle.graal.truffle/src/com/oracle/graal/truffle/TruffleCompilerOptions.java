/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle;

import com.oracle.graal.options.*;

/**
 * Options for the Truffle compiler.
 */
public class TruffleCompilerOptions {

    // @formatter:off
    // configuration
    /**
     * Instructs the Truffle Compiler to compile call targets only if their name contains at least one element of a comma-separated list of includes.
     * Excludes are prefixed with a tilde (~).
     *
     * The format in EBNF:
     * <pre>
     * CompileOnly = Element, { ',', Element } ;
     * Element = Include | '~' Exclude ;
     * </pre>
     */
    @Option(help = "Restrict compilation to comma-separated list of includes (or excludes prefixed with tilde)", type = OptionType.Debug)
    public static final OptionValue<String> TruffleCompileOnly = new OptionValue<>(null);

    @Option(help = "Exclude assertion code from Truffle compilations", type = OptionType.Debug)
    public static final StableOptionValue<Boolean> TruffleExcludeAssertions = new StableOptionValue<>(true);

    @Option(help = "Compile call target when call count exceeds this threshold", type = OptionType.User)
    public static final OptionValue<Integer> TruffleCompilationThreshold = new OptionValue<>(1000);

    @Option(help = "Defines the maximum timespan in milliseconds that is required for a call target to be queued for compilation.", type = OptionType.User)
    public static final OptionValue<Integer> TruffleTimeThreshold = new OptionValue<>(25000);

    @Option(help = "Minimum number of calls before a call target is compiled", type = OptionType.Expert)
    public static final OptionValue<Integer> TruffleMinInvokeThreshold = new OptionValue<>(3);

    @Option(help = "Delay compilation after an invalidation to allow for reprofiling", type = OptionType.Expert)
    public static final OptionValue<Integer> TruffleInvalidationReprofileCount = new OptionValue<>(3);

    @Option(help = "Delay compilation after a node replacement", type = OptionType.Expert)
    public static final OptionValue<Integer> TruffleReplaceReprofileCount = new OptionValue<>(10);

    @Option(help = "Enable automatic inlining of call targets", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleFunctionInlining = new OptionValue<>(true);

    @Option(help = "Enable an expansion cache per CallTarget. Only functionable with TruffleContextSensitiveInlining enabled.", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleFunctionInliningCache = new OptionValue<>(true);

    @Option(help = "Maximum number of Graal IR nodes during partial evaluation", type = OptionType.Expert)
    public static final OptionValue<Integer> TruffleGraphMaxNodes = new OptionValue<>(200000);

    @Option(help = "Stop inlining if caller's cumulative tree size would exceed this limit", type = OptionType.Expert)
    public static final OptionValue<Integer> TruffleInliningMaxCallerSize = new OptionValue<>(2250);

    @Option(help = "Maximum level of recursive inlining", type = OptionType.Expert)
    public static final OptionValue<Integer> TruffleMaximumRecursiveInlining = new OptionValue<>(4);

    @Option(help = "Defines the number of graal nodes that triggers a performance warning.", type = OptionType.Debug)
    public static final OptionValue<Integer> TrufflePerformanceWarningGraalNodeCount = new OptionValue<>(1000);

    @Option(help = "Enable call target splitting", type = OptionType.Expert)
    public static final OptionValue<Boolean> TruffleSplitting = new OptionValue<>(true);

    @Option(help = "Experimental: Enable the new version of truffle splitting.", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleSplittingNew = new OptionValue<>(false);

    @Option(help = "Experimental. New splitting only: Whether or not splitting should be based instance comparisons of non TypedObjects", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleSplittingClassInstanceStamps = new OptionValue<>(false);

    @Option(help = "Experimental. New splitting only: Whether or not splitting should be based instance comparisons of TypedObjects", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleSplittingTypeInstanceStamps = new OptionValue<>(true);

    @Option(help = "Experimental. New splitting only: The number of calls until splitting is performed. ", type = OptionType.Debug)
    public static final OptionValue<Integer> TruffleSplittingStartCallCount = new OptionValue<>(3);

    @Option(help = "Experimental. New splitting only: Split everything aggressively. ", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleSplittingAggressive = new OptionValue<>(false);

    @Option(help = "Disable call target splitting if tree size exceeds this limit", type = OptionType.Debug)
    public static final OptionValue<Integer> TruffleSplittingMaxCalleeSize = new OptionValue<>(100);

    @Option(help = "Number of most recently used methods in truffle cache", type = OptionType.Debug)
    public static final OptionValue<Integer> TruffleMaxCompilationCacheSize = new OptionValue<>(512);

    @Option(help = "Enable asynchronous truffle compilation in background thread", type = OptionType.Expert)
    public static final OptionValue<Boolean> TruffleBackgroundCompilation = new OptionValue<>(true);

    @Option(help = "Manually set the number of compiler threads", type = OptionType.Expert)
    public static final StableOptionValue<Integer> TruffleCompilerThreads = new StableOptionValue<>(0);

    @Option(help = "Enable inlining across Truffle boundary", type = OptionType.Expert)
    public static final OptionValue<Boolean> TruffleInlineAcrossTruffleBoundary = new OptionValue<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Integer> TruffleCompilationDecisionTime = new OptionValue<>(100);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleCompilationDecisionTimePrintFail = new OptionValue<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleReturnTypeSpeculation = new StableOptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleArgumentTypeSpeculation = new StableOptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final StableOptionValue<Boolean> TruffleUseFrameWithoutBoxing = new StableOptionValue<>(true);

    // tracing
    @Option(help = "Print potential performance problems", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTrufflePerformanceWarnings = new OptionValue<>(false);

    @Option(help = "Print information for compilation results", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleCompilation = new OptionValue<>(false);

    @Option(help = "Print information for compilation queuing", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleCompilationDetails = new OptionValue<>(false);

    @Option(help = "Print a node count histogram after each compilation", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleCompilationHistogram = new OptionValue<>(false);

    @Option(help = "Print all polymorphic and generic nodes after each compilation", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleCompilationPolymorphism = new OptionValue<>(false);

    @Option(help = "Print all polymorphic and generic nodes after each compilation", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleCompilationAST = new OptionValue<>(false);

    @Option(help = "Print the inlined call tree for each compiled method", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleCompilationCallTree = new OptionValue<>(false);

    @Option(help = "Print the expansion trees for each compilation", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleExpansion = new OptionValue<>(false);

    @Option(help = "Print source secions for printed expansion trees", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleExpansionSource = new OptionValue<>(false);

    @Option(help = "Prints a histogram of all expanded Java methods.", type = OptionType.Debug)
    public static final OptionValue<Boolean> PrintTruffleExpansionHistogram = new OptionValue<>(false);

    @Option(help = "Print detailed information for the Truffle compilation cache", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleCacheDetails = new OptionValue<>(false);

    @Option(help = "Treat compilation exceptions as fatal exceptions that will exit the application", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleCompilationExceptionsAreFatal = new OptionValue<>(false);

    @Option(help = "Treat compilation exceptions as thrown runtime exceptions", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleCompilationExceptionsAreThrown = new OptionValue<>(false);

    @Option(help = "Print information for inlining for each compilation.", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleInlining = new OptionValue<>(false);

    @Option(help = "Print information for each splitted call site.", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleSplitting = new OptionValue<>(false);

    @Option(help = "Print stack trace on transfer to interpreter", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleTransferToInterpreter = new StableOptionValue<>(false);

    @Option(help = "Print stack trace on assumption invalidation", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleAssumptions = new StableOptionValue<>(false);

    @Option(help = "Number of stack trace elements printed by TraceTruffleTransferToInterpreter and TraceTruffleAssumptions", type = OptionType.Debug)
    public static final OptionValue<Integer> TraceTruffleStackTraceLimit = new OptionValue<>(20);

    @Option(help = "Print a summary of execution counts for all executed CallTargets. Introduces counter overhead for each call.", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleCallTargetProfiling = new StableOptionValue<>(false);

    @Option(help = "Print Truffle compilation statistics at the end of a run.", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleCompilationStatistics = new OptionValue<>(false);

    @Option(help = "Print additional more verbose Truffle compilation statistics at the end of a run.", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleCompilationStatisticDetails = new OptionValue<>(false);

    @Option(help = "Experimental new version of the partial evaluator.", type = OptionType.Debug)
    public static final OptionValue<Boolean> FastPE = new OptionValue<>(true);
    // @formatter:on
}
