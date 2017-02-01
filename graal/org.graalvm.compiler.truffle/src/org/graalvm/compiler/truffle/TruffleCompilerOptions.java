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
package org.graalvm.compiler.truffle;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.StableOptionValue;

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

    @Option(help = "Compile immediately to test truffle compiler", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleCompileImmediately = new OptionValue<>(false);

    @Option(help = "Exclude assertion code from Truffle compilations", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleExcludeAssertions = new StableOptionValue<>(true);

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

    @Option(help = "Stop inlining if caller's cumulative tree size would exceed this limit", type = OptionType.Expert)
    public static final OptionValue<Integer> TruffleInliningMaxCallerSize = new OptionValue<>(2250);

    @Option(help = "Maximum level of recursive inlining", type = OptionType.Expert)
    public static final OptionValue<Integer> TruffleMaximumRecursiveInlining = new OptionValue<>(4);

    @Option(help = "Enable call target splitting", type = OptionType.Expert)
    public static final OptionValue<Boolean> TruffleSplitting = new OptionValue<>(true);

    @Option(help = "Enable on stack replacement for Truffle loops.", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleOSR = new OptionValue<>(true);

    @Option(help = "Number of loop iterations until on-stack-replacement compilation is triggered.", type = OptionType.Debug)
    public static final OptionValue<Integer> TruffleOSRCompilationThreshold = new OptionValue<>(100000);

    @Option(help = "Disable call target splitting if tree size exceeds this limit", type = OptionType.Debug)
    public static final OptionValue<Integer> TruffleSplittingMaxCalleeSize = new OptionValue<>(100);

    @Option(help = "Enable asynchronous truffle compilation in background thread", type = OptionType.Expert)
    public static final OptionValue<Boolean> TruffleBackgroundCompilation = new OptionValue<>(true);

    @Option(help = "Manually set the number of compiler threads", type = OptionType.Expert)
    public static final OptionValue<Integer> TruffleCompilerThreads = new OptionValue<>(0);

    @Option(help = "Enable inlining across Truffle boundary", type = OptionType.Expert)
    public static final OptionValue<Boolean> TruffleInlineAcrossTruffleBoundary = new OptionValue<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleReturnTypeSpeculation = new StableOptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleArgumentTypeSpeculation = new StableOptionValue<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleUseFrameWithoutBoxing = new StableOptionValue<>(true);

    // tracing
    @Option(help = "Print potential performance problems", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTrufflePerformanceWarnings = new OptionValue<>(false);

    @Option(help = "Print information for compilation results", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleCompilation = new OptionValue<>(false);

    @Option(help = "Compile time benchmarking: repeat Truffle compilation n times and then exit the VM", type = OptionType.Debug)
    public static final OptionValue<Integer> TruffleCompilationRepeats = new OptionValue<>(0);

    @Option(help = "Print information for compilation queuing", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleCompilationDetails = new OptionValue<>(false);

    @Option(help = "Print all polymorphic and generic nodes after each compilation", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleCompilationPolymorphism = new OptionValue<>(false);

    @Option(help = "Print all polymorphic and generic nodes after each compilation", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleCompilationAST = new OptionValue<>(false);

    @Option(help = "Print the inlined call tree for each compiled method", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleCompilationCallTree = new OptionValue<>(false);

    @Option(help = "Print source secions for printed expansion trees", type = OptionType.Debug)
    public static final OptionValue<Boolean> TraceTruffleExpansionSource = new OptionValue<>(false);

    @Option(help = "Prints a histogram of all expanded Java methods.", type = OptionType.Debug)
    public static final OptionValue<Boolean> PrintTruffleExpansionHistogram = new OptionValue<>(false);

    @Option(help = "Treat compilation exceptions as fatal exceptions that will exit the application", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleCompilationExceptionsAreFatal = new OptionValue<>(false);

    @Option(help = "Prints the exception stack trace for compilation exceptions", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleCompilationExceptionsArePrinted = new OptionValue<>(true);

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

    @Option(help = "Enable support for simple infopoints in truffle partial evaluations.", type = OptionType.Expert)
    public static final OptionValue<Boolean> TruffleEnableInfopoints = new OptionValue<>(false);

    @Option(help = "Run the partial escape analysis iteratively in Truffle compilation.", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleIterativePartialEscape = new OptionValue<>(false);

    @Option(help = "Enable/disable builtin profiles in com.oracle.truffle.api.profiles.", type = OptionType.Debug)
    public static final OptionValue<Boolean> TruffleProfilingEnabled = new OptionValue<>(true);

    @Option(help = "Instrument branches and output profiling information to the standard output.")
    public static final OptionValue<Boolean> TruffleInstrumentBranches = new OptionValue<>(false);

    @Option(help = "Instrument branches by considering different inlining sites as different branches.")
    public static final OptionValue<Boolean> TruffleInstrumentBranchesPerInlineSite = new OptionValue<>(false);

    @Option(help = "Instrument Truffle boundaries and output profiling information to the standard output.")
    public static final OptionValue<Boolean> TruffleInstrumentBoundaries = new OptionValue<>(false);

    @Option(help = "Instrument Truffle boundaries by considering different inlining sites as different branches.")
    public static final OptionValue<Boolean> TruffleInstrumentBoundariesPerInlineSite = new OptionValue<>(false);

    @Option(help = "Method filter for host methods in which to add instrumentation.")
    public static final OptionValue<String> TruffleInstrumentFilter = new OptionValue<>("*.*.*");

    @Option(help = "Maximum number of instrumentation counters available.")
    public static final OptionValue<Integer> TruffleInstrumentationTableSize = new OptionValue<>(10000);

    // @formatter:on
}
