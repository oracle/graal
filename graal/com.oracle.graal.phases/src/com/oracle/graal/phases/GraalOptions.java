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

import com.oracle.graal.options.*;

/**
 * This class encapsulates options that control the behavior of the Graal compiler.
 */
// @formatter:off
public final class GraalOptions {

    @Option(help = "Enable use of compiler intrinsics")
    public static final OptionValue<Boolean> Intrinsify = new OptionValue<>(true);
    @Option(help = "Enable inlining of monomorphic calls")
    static final OptionValue<Boolean> InlineMonomorphicCalls = new OptionValue<>(true);
    @Option(help = "Enable inlining of polymorphic calls")
    static final OptionValue<Boolean> InlinePolymorphicCalls = new OptionValue<>(true);
    @Option(help = "Enable inlining of megamorphic calls")
    static final OptionValue<Boolean> InlineMegamorphicCalls = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Double> MegamorphicInliningMinMethodProbability = new OptionValue<>(0.33D);
    @Option(help = "")
    public static final OptionValue<Integer> MaximumDesiredSize = new OptionValue<>(5000);
    @Option(help = "")
    public static final OptionValue<Integer> MaximumRecursiveInlining = new OptionValue<>(1);

    // inlining settings
    @Option(help = "")
    public static final OptionValue<Float> BoostInliningForEscapeAnalysis = new OptionValue<>(2f);
    @Option(help = "")
    public static final OptionValue<Float> RelevanceCapForInlining = new OptionValue<>(1f);
    @Option(help = "")
    public static final OptionValue<Float> CapInheritedRelevance = new OptionValue<>(1f);
    @Option(help = "")
    public static final OptionValue<Boolean> IterativeInlining = new OptionValue<>(false);

    @Option(help = "")
    public static final OptionValue<Integer> TrivialInliningSize = new OptionValue<>(10);
    @Option(help = "")
    public static final OptionValue<Integer> MaximumInliningSize = new OptionValue<>(300);
    @Option(help = "")
    public static final OptionValue<Integer> SmallCompiledLowLevelGraphSize = new OptionValue<>(300);
    @Option(help = "")
    public static final OptionValue<Double> LimitInlinedInvokes = new OptionValue<>(5.0);

    // escape analysis settings
    @Option(help = "")
    public static final OptionValue<Boolean> PartialEscapeAnalysis = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> EscapeAnalysisHistogram = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Integer> EscapeAnalysisIterations = new OptionValue<>(2);
    @Option(help = "")
    public static final OptionValue<String> EscapeAnalyzeOnly = new OptionValue<>(null);
    @Option(help = "")
    public static final OptionValue<Integer> MaximumEscapeAnalysisArrayLength = new OptionValue<>(32);
    @Option(help = "")
    public static final OptionValue<Boolean> PEAInliningHints = new OptionValue<>(false);

    @Option(help = "")
    public static final OptionValue<Double> TailDuplicationProbability = new OptionValue<>(0.5);
    @Option(help = "")
    public static final OptionValue<Integer> TailDuplicationTrivialSize = new OptionValue<>(1);

    // profiling information
    @Option(help = "")
    public static final OptionValue<Integer> DeoptsToDisableOptimisticOptimization = new OptionValue<>(40);

    // comilation queue
    @Option(help = "")
    public static final OptionValue<Boolean> DynamicCompilePriority = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<String> CompileTheWorld = new OptionValue<>(null);
    @Option(help = "")
    public static final OptionValue<Integer> CompileTheWorldStartAt = new OptionValue<>(1);
    @Option(help = "")
    public static final OptionValue<Integer> CompileTheWorldStopAt = new OptionValue<>(Integer.MAX_VALUE);

    // graph caching
    @Option(help = "")
    public static final OptionValue<Boolean> CacheGraphs = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Integer> GraphCacheSize = new OptionValue<>(1000);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintGraphCache = new OptionValue<>(false);

    //loop transform settings TODO (gd) tune
    @Option(help = "")
    public static final OptionValue<Boolean> LoopPeeling = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> ReassociateInvariants = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> FullUnroll = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> LoopUnswitch = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Integer> FullUnrollMaxNodes = new OptionValue<>(300);
    @Option(help = "")
    public static final OptionValue<Integer> ExactFullUnrollMaxNodes = new OptionValue<>(1200);
    @Option(help = "")
    public static final OptionValue<Float> MinimumPeelProbability = new OptionValue<>(0.35f);
    @Option(help = "")
    public static final OptionValue<Integer> LoopMaxUnswitch = new OptionValue<>(3);
    @Option(help = "")
    public static final OptionValue<Integer> LoopUnswitchMaxIncrease = new OptionValue<>(50);
    @Option(help = "")
    public static final OptionValue<Integer> LoopUnswitchUncertaintyBoost = new OptionValue<>(5);
    @Option(help = "")
    public static final OptionValue<Boolean> UseLoopLimitChecks = new OptionValue<>(true);

    // debugging settings
    @Option(help = "")
    public static final OptionValue<Boolean> ZapStackOnMethodEntry = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> DeoptALot = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> VerifyPhases = new OptionValue<>(false);

    @Option(help = "")
    public static final OptionValue<String> PrintFilter = new OptionValue<>(null);

    // Debug settings:
    @Option(help = "")
    public static final OptionValue<Boolean> BootstrapReplacements = new OptionValue<>(false);

    // Ideal graph visualizer output settings
    @Option(help = "")
    public static final OptionValue<Boolean> PrintBinaryGraphs = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintCFG = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintIdealGraphFile = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<String> PrintIdealGraphAddress = new OptionValue<>("127.0.0.1");
    @Option(help = "")
    public static final OptionValue<Integer> PrintIdealGraphPort = new OptionValue<>(4444);
    @Option(help = "")
    public static final OptionValue<Integer> PrintBinaryGraphPort = new OptionValue<>(4445);

    // Other printing settings
    @Option(help = "")
    public static final OptionValue<Boolean> PrintCompilation = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintProfilingInformation = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintIRWithLIR = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintCodeBytes = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintBailout = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Integer> TraceLinearScanLevel = new OptionValue<>(0);
    @Option(help = "")
    public static final OptionValue<Integer> TraceLIRGeneratorLevel = new OptionValue<>(0);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceEscapeAnalysis = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Integer> TraceBytecodeParserLevel = new OptionValue<>(0);
    @Option(help = "")
    public static final OptionValue<Boolean> ExitVMOnBailout = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> ExitVMOnException = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> PrintStackTraceOnException = new OptionValue<>(false);

    // HotSpot command line options
    @Option(help = "")
    public static final OptionValue<Boolean> HotSpotPrintCompilation = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> HotSpotPrintInlining = new OptionValue<>(false);

    // Register allocator debugging
    @Option(help = "")
    public static final OptionValue<String> RegisterPressure = new OptionValue<>(null);

    // Code generator settings
    @Option(help = "")
    public static final OptionValue<Boolean> ConditionalElimination = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> UseProfilingInformation = new OptionValue<>(true);
    @Option(help = "")
           static final OptionValue<Boolean> RemoveNeverExecutedCode = new OptionValue<>(true);
           @Option(help = "")
           static final OptionValue<Boolean> UseExceptionProbability = new OptionValue<>(true);
           @Option(help = "")
           static final OptionValue<Boolean> UseExceptionProbabilityForOperations = new OptionValue<>(true);
           @Option(help = "")
    public static final OptionValue<Boolean> OmitHotExceptionStacktrace = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> GenSafepoints = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> GenLoopSafepoints = new OptionValue<>(true);
    @Option(help = "")
           static final OptionValue<Boolean> UseTypeCheckHints = new OptionValue<>(true);
           @Option(help = "")
    public static final OptionValue<Boolean> InlineVTableStubs = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> AlwaysInlineVTableStubs = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> GenAssertionCode = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> AlignCallsForPatching = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> ResolveClassBeforeStaticInvoke = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> CanOmitFrame = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Integer> SafepointPollOffset = new OptionValue<>(256);

    @Option(help = "")
    public static final OptionValue<Boolean> MemoryAwareScheduling = new OptionValue<>(true);

    // Translating tableswitch instructions
    @Option(help = "")
    public static final OptionValue<Integer> MinimumJumpTableSize = new OptionValue<>(5);
    @Option(help = "")
    public static final OptionValue<Integer> RangeTestsSwitchDensity = new OptionValue<>(5);
    @Option(help = "")
    public static final OptionValue<Double> MinTableSwitchDensity = new OptionValue<>(0.5);

    // Ahead of time compilation
    @Option(help = "configure compiler to emit code compatible with AOT requirements for HotSpot")
    public static final OptionValue<Boolean> AOTCompilation = new OptionValue<>(false);

    // Runtime settings
    @Option(help = "")
    public static final OptionValue<Integer> StackShadowPages = new OptionValue<>(2);

    @Option(help = "")
    public static final OptionValue<Boolean> SupportJsrBytecodes = new OptionValue<>(true);

    @Option(help = "")
    public static final OptionValue<Boolean> OptAssumptions = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptConvertDeoptsToGuards = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptReadElimination = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptCanonicalizer = new OptionValue<>(true);
    @Option(help = "")
     public static final OptionValue<Boolean> OptScheduleOutOfLoops = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptEliminateGuards = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptEliminateSafepoints = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptImplicitNullChecks = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptLivenessAnalysis = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptLoopTransform = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptFloatingReads = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptTailDuplication = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptEliminatePartiallyRedundantGuards = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptFilterProfiledTypes = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptDevirtualizeInvokesOptimistically = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> OptPushThroughPi = new OptionValue<>(true);

    // Intrinsification settings
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyObjectClone = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyArrayCopy = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyObjectMethods = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifySystemMethods = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyClassMethods = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyThreadMethods = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyUnsafeMethods = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyMathMethods = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyAESMethods = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyReflectionMethods = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyInstalledCodeMethods = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> IntrinsifyCallSiteTarget = new OptionValue<>(true);
    /**
     * Counts the various paths taken through snippets.
     */
    @Option(help = "")
    public static final OptionValue<Boolean> SnippetCounters = new OptionValue<>(false);

    /**
     * If the probability that a checkcast will hit one the profiled types (up to {@link #CheckcastMaxHints})
     * is below this value, the checkcast will be compiled without hints.
     */
    @Option(help = "")
    public static final OptionValue<Double> CheckcastMinHintHitProbability = new OptionValue<>(0.5);

    /**
     * The maximum number of hint types that will be used when compiling a checkcast for which
     * profiling information is available. Note that {@link #CheckcastMinHintHitProbability}
     * also influences whether hints are used.
     */
    @Option(help = "")
    public static final OptionValue<Integer> CheckcastMaxHints = new OptionValue<>(2);

    /**
     * If the probability that an instanceof will hit one the profiled types (up to {@link #InstanceOfMaxHints})
     * is below this value, the instanceof will be compiled without hints.
     */
    @Option(help = "")
    public static final OptionValue<Double> InstanceOfMinHintHitProbability = new OptionValue<>(0.5);

    /**
     * The maximum number of hint types that will be used when compiling an instanceof for which
     * profiling information is available. Note that {@link #InstanceOfMinHintHitProbability}
     * also influences whether hints are used.
     */
    @Option(help = "")
    public static final OptionValue<Integer> InstanceOfMaxHints = new OptionValue<>(2);
}
