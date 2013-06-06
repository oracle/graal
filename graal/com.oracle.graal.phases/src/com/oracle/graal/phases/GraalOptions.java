/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases;

import static com.oracle.graal.options.OptionValue.*;

import com.oracle.graal.options.*;

/**
 * This class encapsulates options that control the behavior of the Graal compiler.
 */
// @formatter:off
public final class GraalOptions {

    @Option(help = "Enable use of compiler intrinsics")
    public static final OptionValue<Boolean> Intrinsify = newOption(true);
    @Option(help = "Enable inlining of monomorphic calls")
    static final OptionValue<Boolean> InlineMonomorphicCalls = newOption(true);
    @Option(help = "Enable inlining of polymorphic calls")
    static final OptionValue<Boolean> InlinePolymorphicCalls = newOption(true);
    @Option(help = "Enable inlining of megamorphic calls")
    static final OptionValue<Boolean> InlineMegamorphicCalls = newOption(true);
    @Option(help = "")
    public static final OptionValue<Double> MegamorphicInliningMinMethodProbability = newOption(0.33D);
    @Option(help = "")
    public static final OptionValue<Integer> MaximumDesiredSize = newOption(5000);
    @Option(help = "")
    public static final OptionValue<Integer> MaximumRecursiveInlining = newOption(1);

    // inlining settings
    @Option(help = "")
    public static final OptionValue<Float> BoostInliningForEscapeAnalysis = newOption(2f);
    @Option(help = "")
    public static final OptionValue<Float> RelevanceCapForInlining = newOption(1f);
    @Option(help = "")
    public static final OptionValue<Float> CapInheritedRelevance = newOption(1f);
    @Option(help = "")
    public static final OptionValue<Boolean> IterativeInlining = newOption(false);

    @Option(help = "")
    public static final OptionValue<Integer> TrivialInliningSize = newOption(10);
    @Option(help = "")
    public static final OptionValue<Integer> MaximumInliningSize = newOption(300);
    @Option(help = "")
    public static final OptionValue<Integer> SmallCompiledLowLevelGraphSize = newOption(300);
    @Option(help = "")
    public static final OptionValue<Double> LimitInlinedInvokes = newOption(5.0);

    // escape analysis settings
    @Option(help = "")
    public static final OptionValue<Boolean> PartialEscapeAnalysis = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> EscapeAnalysisHistogram = newOption(false);
    @Option(help = "")
    public static final OptionValue<Integer> EscapeAnalysisIterations = newOption(2);
    @Option(help = "")
    public static final OptionValue<String> EscapeAnalyzeOnly = OptionValue.newOption(null);
    @Option(help = "")
    public static final OptionValue<Integer> MaximumEscapeAnalysisArrayLength = newOption(32);
    @Option(help = "")
    public static final OptionValue<Boolean> PEAInliningHints = newOption(false);

    @Option(help = "")
    public static final OptionValue<Double> TailDuplicationProbability = newOption(0.5);
    @Option(help = "")
    public static final OptionValue<Integer> TailDuplicationTrivialSize = newOption(1);

    // profiling information
    @Option(help = "")
    public static final OptionValue<Integer> DeoptsToDisableOptimisticOptimization = newOption(40);
    @Option(help = "")
    public static final OptionValue<Integer> MatureExecutionsBranch = newOption(1);
    @Option(help = "")
    public static final OptionValue<Integer> MatureExecutionsPerSwitchCase = newOption(1);
    @Option(help = "")
    public static final OptionValue<Integer> MatureExecutionsTypeProfile = newOption(1);

    // comilation queue
    @Option(help = "")
    public static final OptionValue<Boolean> DynamicCompilePriority = newOption(false);
    @Option(help = "")
    public static final OptionValue<String> CompileTheWorld = OptionValue.newOption(null);
    @Option(help = "")
    public static final OptionValue<Integer> CompileTheWorldStartAt = newOption(1);
    @Option(help = "")
    public static final OptionValue<Integer> CompileTheWorldStopAt = newOption(Integer.MAX_VALUE);

    // graph caching
    @Option(help = "")
    public static final OptionValue<Boolean> CacheGraphs = newOption(true);
    @Option(help = "")
    public static final OptionValue<Integer> GraphCacheSize = newOption(1000);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintGraphCache = newOption(false);

    //loop transform settings TODO (gd) tune
    @Option(help = "")
    public static final OptionValue<Boolean> LoopPeeling = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> ReassociateInvariants = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> FullUnroll = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> LoopUnswitch = newOption(true);
    @Option(help = "")
    public static final OptionValue<Integer> FullUnrollMaxNodes = newOption(300);
    @Option(help = "")
    public static final OptionValue<Integer> ExactFullUnrollMaxNodes = newOption(1200);
    @Option(help = "")
    public static final OptionValue<Float> MinimumPeelProbability = newOption(0.35f);
    @Option(help = "")
    public static final OptionValue<Integer> LoopMaxUnswitch = newOption(3);
    @Option(help = "")
    public static final OptionValue<Integer> LoopUnswitchMaxIncrease = newOption(50);
    @Option(help = "")
    public static final OptionValue<Integer> LoopUnswitchUncertaintyBoost = newOption(5);
    @Option(help = "")
    public static final OptionValue<Boolean> UseLoopLimitChecks = newOption(true);

    // debugging settings
    @Option(help = "")
    public static final OptionValue<Boolean> ZapStackOnMethodEntry = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> DeoptALot = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> VerifyPhases = newOption(false);

    @Option(help = "")
    public static final OptionValue<String> PrintFilter = OptionValue.newOption(null);

    // Debug settings:
    @Option(help = "")
    public static final OptionValue<Boolean> BootstrapReplacements = newOption(false);

    // Ideal graph visualizer output settings
    @Option(help = "")
    public static final OptionValue<Boolean> PrintBinaryGraphs = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintCFG = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintIdealGraphFile = newOption(false);
    @Option(help = "")
    public static final OptionValue<String> PrintIdealGraphAddress = OptionValue.newOption("127.0.0.1");
    @Option(help = "")
    public static final OptionValue<Integer> PrintIdealGraphPort = newOption(4444);
    @Option(help = "")
    public static final OptionValue<Integer> PrintBinaryGraphPort = newOption(4445);

    // Other printing settings
    @Option(help = "")
    public static final OptionValue<Boolean> PrintCompilation = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintProfilingInformation = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintIRWithLIR = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintCodeBytes = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintBailout = newOption(false);
    @Option(help = "")
    public static final OptionValue<Integer> TraceLinearScanLevel = newOption(0);
    @Option(help = "")
    public static final OptionValue<Integer> TraceLIRGeneratorLevel = newOption(0);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceEscapeAnalysis = newOption(false);
    @Option(help = "")
    public static final OptionValue<Integer> TraceBytecodeParserLevel = newOption(0);
    @Option(help = "")
    public static final OptionValue<Boolean> ExitVMOnBailout = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> ExitVMOnException = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintStackTraceOnException = newOption(false);

    // HotSpot command line options
    @Option(help = "")
    public static final OptionValue<Boolean> HotSpotPrintCompilation = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> HotSpotPrintInlining = newOption(false);

    // Register allocator debugging
    @Option(help = "")
    public static final OptionValue<String> RegisterPressure = OptionValue.newOption(null);

    // Code generator settings
    @Option(help = "")
    public static final OptionValue<Boolean> ConditionalElimination = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> CullFrameStates = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> UseProfilingInformation = newOption(true);
    @Option(help = "")
           static final OptionValue<Boolean> RemoveNeverExecutedCode = newOption(true);
           @Option(help = "")
           static final OptionValue<Boolean> UseExceptionProbability = newOption(true);
           @Option(help = "")
           static final OptionValue<Boolean> UseExceptionProbabilityForOperations = newOption(true);
           @Option(help = "")
    public static final OptionValue<Boolean> OmitHotExceptionStacktrace = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> GenSafepoints = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> GenLoopSafepoints = newOption(true);
    @Option(help = "")
           static final OptionValue<Boolean> UseTypeCheckHints = newOption(true);
           @Option(help = "")
    public static final OptionValue<Boolean> InlineVTableStubs = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> AlwaysInlineVTableStubs = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> GenAssertionCode = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> AlignCallsForPatching = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> ResolveClassBeforeStaticInvoke = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> CanOmitFrame = newOption(true);
    @Option(help = "")
    public static final OptionValue<Integer> SafepointPollOffset = newOption(256);

    @Option(help = "")
    public static final OptionValue<Boolean> MemoryAwareScheduling = newOption(true);

    // Translating tableswitch instructions
    @Option(help = "")
    public static final OptionValue<Integer> MinimumJumpTableSize = newOption(5);
    @Option(help = "")
    public static final OptionValue<Integer> RangeTestsSwitchDensity = newOption(5);
    @Option(help = "")
    public static final OptionValue<Double> MinTableSwitchDensity = newOption(0.5);

    // Runtime settings
    @Option(help = "")
    public static final OptionValue<Integer> StackShadowPages = newOption(2);

    @Option(help = "")
    public static final OptionValue<Boolean> SupportJsrBytecodes = newOption(true);

    @Option(help = "")
    public static final OptionValue<Boolean> OptAssumptions = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptConvertDeoptsToGuards = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptReadElimination = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptEarlyReadElimination = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptCanonicalizer = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptCanonicalizeReads = newOption(true);
    @Option(help = "")
     public static final OptionValue<Boolean> OptScheduleOutOfLoops = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptEliminateGuards = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptEliminateSafepoints = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptImplicitNullChecks = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptLivenessAnalysis = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptLoopTransform = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptFloatingReads = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptTailDuplication = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptEliminatePartiallyRedundantGuards = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptFilterProfiledTypes = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptDevirtualizeInvokesOptimistically = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptPushThroughPi = newOption(true);

    // Intrinsification settings
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyObjectClone = newOption(false);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyArrayCopy = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyObjectMethods = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifySystemMethods = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyClassMethods = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyThreadMethods = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyUnsafeMethods = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyMathMethods = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyAESMethods = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyReflectionMethods = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyInstalledCodeMethods = newOption(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyCallSiteTarget = newOption(true);
    /**
     * Counts the various paths taken through snippets.
     */
    @Option(help = "")
    public static final OptionValue<Boolean> SnippetCounters = newOption(false);

    /**
     * If the probability that a checkcast will hit one the profiled types (up to {@link #CheckcastMaxHints})
     * is below this value, the checkcast will be compiled without hints.
     */
    @Option(help = "")
    public static final OptionValue<Double> CheckcastMinHintHitProbability = newOption(0.5);

    /**
     * The maximum number of hint types that will be used when compiling a checkcast for which
     * profiling information is available. Note that {@link #CheckcastMinHintHitProbability}
     * also influences whether hints are used.
     */
    @Option(help = "")
    public static final OptionValue<Integer> CheckcastMaxHints = newOption(2);

    /**
     * @see #CheckcastMinHintHitProbability
     */
    @Option(help = "")
    public static final OptionValue<Double> InstanceOfMinHintHitProbability = newOption(0.5);

    /**
     * @see #CheckcastMaxHints
     */
    @Option(help = "")
    public static final OptionValue<Integer> InstanceOfMaxHints = newOption(2);
}
