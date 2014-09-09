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
    @Option(help = "Restrict compilation to comma-separated list of includes (or excludes prefixed with tilde)")
    public static final OptionValue<String> TruffleCompileOnly = new OptionValue<>(null);
    @Option(help = "Compile call target when call count exceeds this threshold")
    public static final OptionValue<Integer> TruffleCompilationThreshold = new OptionValue<>(1000);
    @Option(help = "Minimum number of calls before a call target is compiled")
    public static final OptionValue<Integer> TruffleMinInvokeThreshold = new OptionValue<>(3);
    @Option(help = "Delay compilation after an invalidation to allow for reprofiling")
    public static final OptionValue<Integer> TruffleInvalidationReprofileCount = new OptionValue<>(3);
    @Option(help = "Delay compilation after a node replacement")
    public static final OptionValue<Integer> TruffleReplaceReprofileCount = new OptionValue<>(10);
    @Option(help = "Enable automatic inlining of call targets")
    public static final OptionValue<Boolean> TruffleFunctionInlining = new OptionValue<>(true);
    @Option(help = "Maximum number of Graal IR nodes during partial evaluation")
    public static final OptionValue<Integer> TruffleGraphMaxNodes = new OptionValue<>(200000);
    @Option(help = "Stop inlining if caller's cumulative tree size would exceed this limit")
    public static final OptionValue<Integer> TruffleInliningMaxCallerSize = new OptionValue<>(2250);
    @Option(help = "Skip inlining candidate if its tree size exceeds this limit")
    public static final OptionValue<Integer> TruffleInliningMaxCalleeSize = new OptionValue<>(500);
    @Option(help = "Call frequency relative to call target")
    public static final OptionValue<Double> TruffleInliningMinFrequency = new OptionValue<>(0.3);
    @Option(help = "Allow inlining of less hot candidates if tree size is small")
    public static final OptionValue<Integer> TruffleInliningTrivialSize = new OptionValue<>(10);

    @Option(help = "Enable call target splitting")
    public static final OptionValue<Boolean> TruffleSplitting = new OptionValue<>(true);
    @Option(help = "Experimental: Enable the new version of truffle splitting.")
    public static final OptionValue<Boolean> TruffleSplittingNew = new OptionValue<>(false);
    @Option(help = "Experimental. New splitting only: Whether or not splitting should be based instance comparisons of non TypedObjects")
    public static final OptionValue<Boolean> TruffleSplittingClassInstanceStamps = new OptionValue<>(false);
    @Option(help = "Experimental. New splitting only: Whether or not splitting should be based instance comparisons of TypedObjects")
    public static final OptionValue<Boolean> TruffleSplittingTypeInstanceStamps = new OptionValue<>(true);
    @Option(help = "Experimental. New splitting only: The number of calls until splitting is performed. ")
    public static final OptionValue<Integer> TruffleSplittingStartCallCount = new OptionValue<>(3);
    @Option(help = "Experimental. New splitting only: Split everything aggressively. ")
    public static final OptionValue<Boolean> TruffleSplittingAggressive = new OptionValue<>(false);



    @Option(help = "Disable call target splitting if tree size exceeds this limit")
    public static final OptionValue<Integer> TruffleSplittingMaxCalleeSize = new OptionValue<>(100);
    @Option(help = "Number of most recently used methods in truffle cache")
    public static final OptionValue<Integer> TruffleMaxCompilationCacheSize = new OptionValue<>(512);
    @Option(help = "Enable asynchronous truffle compilation in background thread")
    public static final OptionValue<Boolean> TruffleBackgroundCompilation = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Integer> TruffleCompilationDecisionTime = new OptionValue<>(100);
    @Option(help = "")
    public static final OptionValue<Boolean> TruffleCompilationDecisionTimePrintFail = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TruffleReturnTypeSpeculation = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> TruffleArgumentTypeSpeculation = new StableOptionValue<>(true);

    // tracing
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleCompilation = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleCompilationDetails = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleCompilationHistogram = new OptionValue<>(false);
    @Option(help = "Prints out all polymorphic and generic nodes after compilation.")
    public static final OptionValue<Boolean> TraceTruffleCompilationPolymorphism = new OptionValue<>(false);
    @Option(help = "Prints out all polymorphic and generic nodes after compilation.")
    public static final OptionValue<Boolean> TraceTruffleCompilationAST = new OptionValue<>(false);
    @Option(help = "Prints out all calls of a compiled method.")
    public static final OptionValue<Boolean> TraceTruffleCompilationCallTree = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleExpansion = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleExpansionSource = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleCacheDetails = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TruffleCompilationExceptionsAreFatal = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TruffleCompilationExceptionsAreThrown = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleInlining = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleSplitting = new OptionValue<>(false);
    @Option(help = "Print stack trace on transfer to interpreter")
    public static final OptionValue<Boolean> TraceTruffleTransferToInterpreter = new StableOptionValue<>(false);
    @Option(help = "Print stack trace on assumption invalidation")
    public static final OptionValue<Boolean> TraceTruffleAssumptions = new StableOptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TruffleCallTargetProfiling = new StableOptionValue<>(false);
    // @formatter:on
}
