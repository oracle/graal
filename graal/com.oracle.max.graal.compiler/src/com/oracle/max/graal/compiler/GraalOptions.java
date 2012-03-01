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
package com.oracle.max.graal.compiler;

/**
 * This class encapsulates options that control the behavior of the Graal compiler.
 * The help message for each option is specified by a {@linkplain #helpMap help map}.
 *
 * (tw) WARNING: Fields of this class are treated as final by Graal.
 */
public final class GraalOptions {

    // Checkstyle: stop
    private static final boolean ____ = false;
    // Checkstyle: resume




    public static int     Threads                            = 4;
    public static boolean Lower                              = true;

    // inlining settings
    public static boolean Inline                             = true;
    public static boolean Intrinsify                         = true;
    public static boolean CacheGraphs                        = ____;
    public static boolean InlineMonomorphicCalls             = true;
    public static boolean InlinePolymorphicCalls             = true;
    public static boolean InlineMegamorphicCalls             = ____;
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
    public static int     MaximumGreedyInlineSize            = 200;
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
    public static int     MatureExecutionsBranch             = 1;
    public static int     MatureExecutionsPerSwitchCase      = 1;
    public static int     MatureExecutionsTypeProfile        = 1;

    //rematerialize settings
    public static float   MinimumUsageProbability            = 0.95f;

    // debugging settings
    public static int     MethodEndBreakpointGuards          = 0;
    public static boolean ZapStackOnMethodEntry              = ____;
    public static boolean StressLinearScan                   = ____;
    public static boolean DeoptALot                          = ____;
    public static boolean VerifyPhases                       = true;
    public static boolean CreateDeoptInfo                    = ____;

    /**
     * See {@link Filter#Filter(String, Object)}.
     */
    public static String  PrintFilter                        = null;

    // printing settings
    public static boolean PrintLIR                           = ____;
    public static boolean PrintCFGToFile                     = ____;

    // statistics independent from debug mode
    public static boolean PrintCompilationStatistics         = ____;

    // Debug settings:
    public static boolean Debug                              = true;
    public static boolean SummarizeDebugValues               = ____;
    public static String Dump                                = null;
    public static String Meter                               = null;
    public static String Time                                = null;
    public static String Log                                 = null;
    public static String MethodFilter                        = null;

    // Ideal graph visualizer output settings
    public static boolean PlotOnError                        = ____;
    public static int     PlotLevel                          = 3;
    public static boolean PlotSnippets                       = ____;
    public static int     PrintIdealGraphLevel               = 0;
    public static boolean PrintIdealGraphFile                = ____;
    public static String  PrintIdealGraphAddress             = "127.0.0.1";
    public static int     PrintIdealGraphPort                = 4444;

    // Other printing settings
    public static boolean PrintQueue                         = ____;
    public static boolean PrintCompilation                   = ____;
    public static boolean PrintXirTemplates                  = ____;
    public static boolean PrintIRWithLIR                     = ____;
    public static boolean PrintAssembly                      = ____;
    public static boolean PrintCodeBytes                     = ____;
    public static int     PrintAssemblyBytesPerLine          = 16;
    public static int     TraceLinearScanLevel               = 0;
    public static boolean TraceRegisterAllocation            = false;
    public static int     TraceLIRGeneratorLevel             = 0;
    public static boolean TraceEscapeAnalysis                = ____;
    public static int     TraceBytecodeParserLevel           = 0;
    public static boolean ExitVMOnBailout                    = ____;
    public static boolean ExitVMOnException                  = true;

    // state merging settings
    public static boolean AssumeVerifiedBytecode             = true;

    // Code generator settings
    public static boolean PropagateTypes                     = ____;
    public static boolean UseBranchPrediction                = true;
    public static boolean UseExceptionProbability            = true;
    public static boolean AllowExplicitExceptionChecks       = true;
    public static boolean OmitHotExceptionStacktrace         = ____;
    public static boolean GenSafepoints                      = true;
    public static boolean GenLoopSafepoints                  = true;
    public static boolean UseTypeCheckHints                  = true;
    public static boolean InlineVTableStubs                  = ____;
    public static boolean AlwaysInlineVTableStubs            = ____;

    public static boolean GenAssertionCode                   = ____;
    public static boolean AlignCallsForPatching              = true;
    public static boolean ResolveClassBeforeStaticInvoke     = true;

    // Translating tableswitch instructions
    public static int     SequentialSwitchLimit              = 4;
    public static int     RangeTestsSwitchDensity            = 5;

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
    public static boolean OptLoops                           = ____;
    public static boolean ScheduleOutOfLoops                 = true;
    public static boolean OptReorderLoops                    = true;
    public static boolean OptEliminateGuards                 = true;
    public static boolean OptImplicitNullChecks              = true;

    /**
     * Flag to turn on SSA-based register allocation, which is currently under development.
     */
    public static boolean AllocSSA                           = false;

    static {
        // turn detailed assertions on when the general assertions are on (misusing the assert keyword for this)
        assert (DetailedAsserts = true) == true;
        assert (CommentedAssembly = true) == true;
    }
}
