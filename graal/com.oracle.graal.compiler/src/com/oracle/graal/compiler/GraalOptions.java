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
package com.oracle.graal.compiler;


/**
 * This class encapsulates options that control the behavior of the Graal compiler.
 *
 * (thomaswue) WARNING: Fields of this class are treated as final by Graal.
 */
public final class GraalOptions {

    // Checkstyle: stop
    private static final boolean ____ = false;
    // Checkstyle: resume

    public static int     Threads                            = 4;

    // inlining settings
    public static boolean Inline                             = true;
    public static boolean Intrinsify                         = true;
           static boolean InlineMonomorphicCalls             = true;
           static boolean InlinePolymorphicCalls             = true;
           static boolean InlineMegamorphicCalls             = ____;
    public static int     InliningPolicy                     = 4;
    public static int     WeightComputationPolicy            = 2;
    public static int     MaximumTrivialSize                 = 10;
    public static int     MaximumInlineLevel                 = 30;
    public static int     MaximumDesiredSize                 = 3000;
    public static int     MaximumRecursiveInlining           = 1;
    public static int     SmallCompiledCodeSize              = 2000;
    public static boolean LimitInlinedProbability            = ____;
    // WeightBasedInliningPolicy (0)
    public static boolean ParseBeforeInlining                = ____;
    public static float   InliningSizePenaltyExp             = 20;
    public static float   MaximumInlineWeight                = 1.25f;
    public static float   InliningSizePenalty                = 1;
    // StaticSizeBasedInliningPolicy (1), MinimumCodeSizeBasedInlining (2),
    // DynamicSizeBasedInliningPolicy (3)
    public static int     MaximumInlineSize                  = 35;
    // GreedySizeBasedInlining (4)
    public static int     MaximumGreedyInlineSize            = 100;
    public static int     InliningBonusPerTransferredValue   = 10;
    // Common options for inlining policies 1 to 4
    public static float   NestedInliningSizeRatio            = 1f;
    public static float   BoostInliningForEscapeAnalysis     = 2f;
    public static float   ProbabilityCapForInlining          = 1f;

    // escape analysis settings
    public static boolean EscapeAnalysis                     = true;
    public static int     ForcedInlineEscapeWeight           = 10;
    public static boolean PrintEscapeAnalysis                = ____;

    // absolute probability analysis
    public static boolean ProbabilityAnalysis                = true;
    public static int     LoopFrequencyPropagationPolicy     = -2;

    // profiling information
    public static int     DeoptsToDisableOptimisticOptimization = 40;
    public static boolean PrintDisabledOptimisticOptimizations = true;
    public static int     MatureExecutionsBranch             = 1;
    public static int     MatureExecutionsPerSwitchCase      = 1;
    public static int     MatureExecutionsTypeProfile        = 1;

    // comilation queue
    public static int     TimedBootstrap                     = -1;
    public static boolean PriorityCompileQueue               = true;
    public static int     SlowQueueCutoff                    = 100000;
    public static boolean SlowCompileThreads                 = ____;
    public static boolean DynamicCompilePriority             = ____;

    // graph caching
    public static boolean CacheGraphs                        = true;
    public static int     GraphCacheSize                     = 1000;
    public static boolean PrintGraphCache                    = ____;

    //rematerialize settings
    public static float   MinimumUsageProbability            = 0.95f;

    //loop transform settings TODO (gd) tune
    public static boolean LoopPeeling                        = true;
    public static boolean ReassociateInvariants              = true;
    public static boolean FullUnroll                         = true;
    public static boolean LoopUnswitch                       = ____;
    public static int     FullUnrollMaxNodes                 = 150;
    public static float   MinimumPeelProbability             = 0.35f;
    public static int     LoopUnswitchMaxIncrease            = 50;
    public static int     LoopUnswitchUncertaintyBoost       = 5;

    // debugging settings
    public static int     MethodEndBreakpointGuards          = 0;
    public static boolean ZapStackOnMethodEntry              = ____;
    public static boolean StressLinearScan                   = ____;
    public static boolean DeoptALot                          = ____;
    public static boolean VerifyPhases                       = true;
    public static boolean CreateDeoptInfo                    = ____;

    public static String  PrintFilter                        = null;

    // printing settings
    public static boolean PrintLIR                           = ____;
    public static boolean PrintCFGToFile                     = ____;

    // Debug settings:
    public static boolean Debug                              = true;
    public static boolean PerThreadDebugValues               = ____;
    public static boolean SummarizeDebugValues               = ____;
    public static boolean SummarizePerPhase                  = ____;
    public static String Dump                                = null;
    public static String Meter                               = null;
    public static String Time                                = null;
    public static String Log                                 = null;
    public static String LogFile                             = null;
    public static String MethodFilter                        = null;
    public static boolean DumpOnError                        = ____;

    // Ideal graph visualizer output settings
    public static int     PlotLevel                          = 3;
    public static int     PrintIdealGraphLevel               = 0;
    public static boolean PrintIdealGraphFile                = ____;
    public static String  PrintIdealGraphAddress             = "127.0.0.1";
    public static int     PrintIdealGraphPort                = 4444;

    // Other printing settings
    public static boolean PrintQueue                         = ____;
    public static boolean PrintCompilation                   = ____;
    public static boolean PrintProfilingInformation          = ____;
    public static boolean PrintXirTemplates                  = ____;
    public static boolean PrintIRWithLIR                     = ____;
    public static boolean PrintAssembly                      = ____;
    public static boolean PrintCodeBytes                     = ____;
    public static int     PrintAssemblyBytesPerLine          = 16;
    public static boolean PrintBailout                       = ____;
    public static int     TraceLinearScanLevel               = 0;
    public static boolean TraceRegisterAllocation            = false;
    public static int     TraceLIRGeneratorLevel             = 0;
    public static boolean TraceEscapeAnalysis                = ____;
    public static int     TraceBytecodeParserLevel           = 0;
    public static boolean PrintBailouts                      = true;
    public static boolean ExitVMOnBailout                    = ____;
    public static boolean ExitVMOnException                  = true;

    // state merging settings
    public static boolean AssumeVerifiedBytecode             = true;

    // Code generator settings
    public static boolean PropagateTypes                     = ____;
    public static boolean CheckCastElimination               = true;
    public static boolean CullFrameStates                    = ____;
    public static boolean UseProfilingInformation            = true;
           static boolean RemoveNeverExecutedCode            = true;
           static boolean UseExceptionProbability            = true;
    public static boolean AllowExplicitExceptionChecks       = true;
    public static boolean OmitHotExceptionStacktrace         = ____;
    public static boolean GenSafepoints                      = true;
    public static boolean GenLoopSafepoints                  = true;
           static boolean UseTypeCheckHints                  = true;
    public static boolean InlineVTableStubs                  = ____;
    public static boolean AlwaysInlineVTableStubs            = ____;

    public static boolean GenAssertionCode                   = ____;
    public static boolean AlignCallsForPatching              = true;
    public static boolean ResolveClassBeforeStaticInvoke     = true;

    // Translating tableswitch instructions
    public static int     SequentialSwitchLimit              = 4;
    public static int     RangeTestsSwitchDensity            = 5;
    public static double  MinTableSwitchDensity              = 0.5;

    public static boolean DetailedAsserts                    = ____;

    // Runtime settings
    public static int     ReadPrefetchInstr                  = 0;
    public static int     StackShadowPages                   = 2;

    // Assembler settings
    public static boolean CommentedAssembly                  = ____;
    public static boolean PrintLIRWithAssembly               = ____;

    public static boolean SupportJsrBytecodes                = true;

    public static boolean OptAssumptions                     = true;
    public static boolean OptReadElimination                 = true;
    public static boolean OptGVN                             = true;
    public static boolean OptCanonicalizer                   = true;
    public static boolean ScheduleOutOfLoops                 = true;
    public static boolean OptReorderLoops                    = true;
    public static boolean OptEliminateGuards                 = true;
    public static boolean OptImplicitNullChecks              = true;
    public static boolean OptLivenessAnalysis                = true;
    public static boolean OptLoopTransform                   = true;
    public static boolean OptSafepointElimination            = true;

    /**
     * Insert a counter in the method prologue to track the most frequently called methods that were compiled by Graal.
     */
    public static boolean MethodEntryCounters               = false;
    /**
     * Number of caller program counters to distinguish when counting methods.
     */
    public static int     MethodEntryCountersCallers        = 20;

    /**
     * Flag to turn on SSA-based register allocation, which is currently under development.
     */
    public static boolean AllocSSA                           = false;

    /**
     * Prints all the available GraalOptions.
     */
    public static boolean PrintFlags                           = false;

    /**
     * Counts the various paths taken through a compiled checkcast.
     */
    public static boolean CheckcastCounters = false;

    /**
     * If the probability that a checkcast will hit one the profiled types (up to {@link #CheckcastMaxHints})
     * is below this value, the checkcast will be compiled without hints.
     */
    public static double CheckcastMinHintHitProbability = 0.5;

    /**
     * The maximum number of hint types that will be used when compiling a checkcast for which
     * profiling information is available. Note that {@link #CheckcastMinHintHitProbability}
     * also influences whether hints are used.
     */
    public static int CheckcastMaxHints = 2;

    /**
     * @see #CheckcastMinHintHitProbability
     */
    public static double InstanceOfMinHintHitProbability = 0.5;

    /**
     * @see #CheckcastMaxHints
     */
    public static int InstanceOfMaxHints = 1;

    /**
     * Use HIR lowering instead of LIR lowering for certain instructions.
     * Only instructions in methods whose fully qualified name contains this option will be HIR lowered.
     */
    public static String HIRLowerCheckcast = "";
    public static String HIRLowerNewInstance = "";

    static {
        // turn detailed assertions on when the general assertions are on (misusing the assert keyword for this)
        assert (DetailedAsserts = true) == true;
        assert (CommentedAssembly = true) == true;
    }
}
