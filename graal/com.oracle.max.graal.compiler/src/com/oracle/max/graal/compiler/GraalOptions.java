/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.max.graal.compiler.debug.TTY.*;

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

    public static boolean Lower                              = true;

    // inlining settings
    public static boolean Inline                             = true;
    public static boolean Intrinsify                         = true;
    public static boolean CacheGraphs                        = ____;
    public static boolean InlineWithTypeCheck                = ____;
    public static int     MaximumInstructionCount            = 3000;
    public static float   MaximumInlineRatio                 = 0.90f;
    public static int     MaximumInlineSize                  = 35;
    public static int     MaximumFreqInlineSize              = 200;
    public static int     FreqInlineRatio                    = 20;
    public static int     MaximumTrivialSize                 = 6;
    public static int     MaximumInlineLevel                 = 9;
    public static int     MaximumRecursiveInlineLevel        = 2;
    public static int     MaximumDesiredSize                 = 8000;
    public static int     MaximumShortLoopSize               = 5;

    // escape analysis settings
    public static boolean EscapeAnalysis                     = ____;
    public static int     ForcedInlineEscapeWeight           = 100;
    public static int     MaximumEscapeAnalysisArrayLength   = 32;
    public static boolean PrintEscapeAnalysis                = ____;

    // absolute probability analysis
    public static boolean ProbabilityAnalysis                = true;

    //rematerialize settings
    public static double  MinimumUsageProbability            = 0.95;

    // debugging settings
    public static boolean VerifyPointerMaps                  = ____;
    public static int     MethodEndBreakpointGuards          = 0;
    public static boolean ZapStackOnMethodEntry              = ____;
    public static boolean StressLinearScan                   = ____;
    public static boolean BailoutOnException                 = ____;
    public static boolean DeoptALot                          = ____;
    public static boolean Verify                             = true;
    public static boolean TestGraphDuplication               = ____;

    /**
     * See {@link Filter#Filter(String, Object)}.
     */
    public static String  PrintFilter                        = null;

    // printing settings
    public static boolean PrintLIR                           = ____;
    public static boolean PrintCFGToFile                     = ____;

    // DOT output settings
    public static boolean PrintDOTGraphToFile                = ____;
    public static boolean PrintDOTGraphToPdf                 = ____;
    public static boolean OmitDOTFrameStates                 = ____;

    public static boolean Extend                             = ____;

    // Ideal graph visualizer output settings
    public static boolean Plot                               = ____;
    public static boolean PlotVerbose                        = ____;
    public static boolean PlotOnError                        = ____;
    public static int     PrintIdealGraphLevel               = 0;
    public static boolean PrintIdealGraphFile                = ____;
    public static String  PrintIdealGraphAddress             = "127.0.0.1";
    public static int     PrintIdealGraphPort                = 4444;

    // Other printing settings
    public static boolean Meter                              = ____;
    public static boolean Time                               = ____;
    public static boolean PrintCompilation                   = ____;
    public static boolean PrintXirTemplates                  = ____;
    public static boolean PrintIRWithLIR                     = ____;
    public static boolean PrintAssembly                      = ____;
    public static boolean PrintCodeBytes                     = ____;
    public static int     PrintAssemblyBytesPerLine          = 16;
    public static int     TraceLinearScanLevel               = 0;
    public static int     TraceLIRGeneratorLevel             = 0;
    public static boolean TraceRelocation                    = ____;
    public static boolean TraceLIRVisit                      = ____;
    public static boolean TraceAssembler                     = ____;
    public static boolean TraceInlining                      = ____;
    public static boolean TraceDeadCodeElimination           = ____;
    public static boolean TraceEscapeAnalysis                = ____;
    public static boolean TraceCanonicalizer                 = ____;
    public static boolean TraceMemoryMaps                    = ____;
    public static boolean TraceProbability                 = ____;
    public static boolean TraceReadElimination               = ____;
    public static boolean TraceGVN                           = ____;
    public static int     TraceBytecodeParserLevel           = 0;
    public static boolean QuietBailout                       = ____;

    // state merging settings
    public static boolean AssumeVerifiedBytecode             = ____;

    // Linear scan settings
    public static boolean CopyPointerStackArguments          = true;

    // Code generator settings
    public static boolean GenLIR                             = true;
    public static boolean GenCode                            = true;
    public static boolean UseBranchPrediction                = true;
    public static boolean UseExceptionProbability            = ____;
    public static int     MatureInvocationCount              = 100;
    public static boolean GenSafepoints                      = true;

    public static boolean UseConstDirectCall                 = ____;

    public static boolean GenSpecialDivChecks                = ____;
    public static boolean GenAssertionCode                   = ____;
    public static boolean AlignCallsForPatching              = true;
    public static boolean NullCheckUniquePc                  = ____;
    public static boolean InvokeSnippetAfterArguments        = ____;
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

    public static boolean OptReadElimination                 = ____;
    public static boolean OptGVN                             = ____;
    public static boolean Rematerialize                      = ____;
    public static boolean SplitMaterialization               = ____;
    public static boolean OptCanonicalizer                   = true;
    public static boolean OptLoops                           = ____;
    public static boolean ScheduleOutOfLoops                 = true;
    public static boolean OptReorderLoops                    = ____;
    public static boolean LoopPeeling                        = ____;
    public static boolean LoopInversion                      = ____;
}
