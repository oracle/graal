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
package com.sun.c1x;

import com.sun.c1x.debug.TTY.*;

/**
 * This class encapsulates options that control the behavior of the C1X compiler.
 * The help message for each option is specified by a {@linkplain #helpMap help map}.
 *
 * (tw) WARNING: Fields of this class are treated as final by Graal.
 *
 * @author Ben L. Titzer
 */
public final class C1XOptions {

    // Checkstyle: stop
    private static final boolean ____ = false;
    // Checkstyle: resume

    // inlining settings
    public static boolean OptInline                          = ____;
    public static boolean OptInlineExcept                    = ____;
    public static boolean OptInlineSynchronized              = ____;

    public static int     MaximumInstructionCount            = 37000;
    public static float   MaximumInlineRatio                 = 0.90f;
    public static int     MaximumInlineSize                  = 35;
    public static int     MaximumTrivialSize                 = 6;
    public static int     MaximumInlineLevel                 = 9;
    public static int     MaximumRecursiveInlineLevel        = 1;
    public static int     MaximumDesiredSize                 = 8000;
    public static int     MaximumShortLoopSize               = 5;

    // intrinsification settings
    public static boolean OptIntrinsify                      = ____;

    // debugging settings
    public static boolean VerifyPointerMaps                  = ____;
    public static boolean PinAllInstructions                 = ____;
    public static boolean TestPatching                       = ____;
    public static boolean FatalUnimplemented                 = ____;
    public static int     MethodEndBreakpointGuards          = 0;
    public static boolean ZapStackOnMethodEntry              = ____;
    public static boolean StressLinearScan                   = ____;

    /**
     * See {@link Filter#Filter(String, Object)}.
     */
    public static String  PrintFilter                        = null;

    // printing settings
    public static boolean PrintHIR                           = ____;
    public static boolean PrintInliningFailures              = ____;
    public static boolean PrintLIR                           = ____;
    public static boolean PrintCFGToFile                     = ____;
    public static boolean PrintMetrics                       = ____;
    public static boolean PrintTimers                        = ____;
    public static boolean PrintCompilation                   = ____;
    public static boolean PrintXirTemplates                  = ____;
    public static boolean PrintIRWithLIR                     = ____;
    public static boolean PrintAssembly                      = ____;
    public static boolean PrintCodeBytes                     = ____;
    public static int     PrintAssemblyBytesPerLine          = 16;
    public static int     TraceLinearScanLevel               = 0;
    public static boolean TraceRelocation                    = ____;
    public static boolean TraceLIRVisit                      = ____;
    public static boolean TraceAssembler                     = ____;
    public static int     TraceBytecodeParserLevel           = 0;
    public static boolean PrintAssumptions                   = ____;
    public static boolean PrintInlinedIntrinsics             = ____;

    // IR checking
    public static boolean InterpretInvokedMethods            = ____;
    public static boolean PrintStateInInterpreter            = ____;

    // canonicalizer settings
    public static boolean CanonicalizeIntrinsics             = true;
    public static boolean CanonicalizeFloatingPoint          = true;
    public static boolean CanonicalizeNarrowingInStores      = true;
    public static boolean CanonicalizeConstantFields         = true;
    public static boolean CanonicalizeUnsafes                = true;
    public static boolean CanonicalizeMultipliesToShifts     = true;
    public static boolean CanonicalizeObjectCheckCast        = true;
    public static boolean CanonicalizeObjectInstanceOf       = true;
    public static boolean CanonicalizeFoldableMethods        = true;
    public static boolean CanonicalizeArrayStoreChecks       = true;

    // all optimization settings
    public static boolean OptCanonicalize;
    public static boolean OptLocalValueNumbering;
    public static boolean OptLocalLoadElimination;
    public static boolean OptCSEArrayLength;
    public static boolean OptGlobalValueNumbering;
    public static boolean OptCEElimination;
    public static boolean OptBlockMerging;
    public static boolean OptBlockSkipping;
    public static boolean OptNullCheckElimination;
    public static boolean OptIterativeNCE;
    public static boolean OptFlowSensitiveNCE;
    public static boolean OptDeadCodeElimination1;
    public static boolean OptDeadCodeElimination2;
    public static boolean OptControlFlow;
    public static boolean OptMoveElimination;

    // optimistic optimization settings
    public static boolean UseAssumptions                = true;

    // state merging settings
    public static boolean AssumeVerifiedBytecode        = ____;
    public static boolean PhiSimplify                   = true;
    public static boolean PhiLoopStores                 = true;

    // miscellaneous settings
    public static boolean SupportObjectConstants        = true;

    // Linear scan settings
    public static boolean CopyPointerStackArguments     = true;

    // Code generator settings
    public static boolean GenLIR                        = true;
    public static boolean GenCode                       = true;

    public static boolean UseConstDirectCall            = false;

    public static boolean GenSpecialDivChecks           = ____;
    public static boolean GenAssertionCode              = ____;
    public static boolean AlignCallsForPatching         = true;
    public static boolean NullCheckUniquePc             = ____;
    public static boolean InvokeSnippetAfterArguments   = ____;
    public static boolean ResolveClassBeforeStaticInvoke = true;

    // Translating tableswitch instructions
    public static int     SequentialSwitchLimit         = 4;
    public static int     RangeTestsSwitchDensity       = 5;

    public static int     InitialCodeBufferSize         = 232;
    public static boolean DetailedAsserts               = true;

    // Runtime settings
    public static int     ReadPrefetchInstr             = 0;
    public static boolean UseStackMapTableLiveness      = ____;
    public static int     StackShadowPages              = 2;

    // Assembler settings
    public static boolean CommentedAssembly             = ____;
    public static boolean PrintLIRWithAssembly          = ____;
    public static int     Atomics                       = 0;
    public static boolean UseNormalNop                  = true;
    public static boolean UseAddressNop                 = true;
    public static boolean UseIncDec                     = ____;
    public static boolean UseXmmLoadAndClearUpper       = ____;
    public static boolean UseXmmRegToRegMoveAll         = ____;

    static {
        setOptimizationLevel(1);
    }

    public static void setOptimizationLevel(int level) {
        final boolean l = (level >= 1);
        final boolean ll = (level >= 2);
        final boolean lll = (level >= 3);

        // Level 1 optimizations
        OptCanonicalize                 = l;
        OptLocalValueNumbering          = l;
        OptLocalLoadElimination         = l;
        PhiLoopStores                   = l;
        OptControlFlow                  = l;
        OptMoveElimination              = l;
        OptNullCheckElimination         = l;

        // Level 2 optimizations
        OptInline                       = ll;
        OptBlockMerging                 = ll;

        // Level 3 optimizations
        OptCSEArrayLength               = lll;
        OptIntrinsify                   = lll;
        OptInlineExcept                 = lll;
        OptInlineSynchronized           = lll;
        UseStackMapTableLiveness        = lll;
        UseAssumptions                  = lll;
        OptIterativeNCE                 = lll;
        OptFlowSensitiveNCE             = lll;
        OptDeadCodeElimination1         = lll;
        OptDeadCodeElimination2         = lll;
        OptGlobalValueNumbering         = lll;
        OptCEElimination                = lll;
        OptBlockSkipping                = lll;
    }
}
