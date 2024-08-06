/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.core.common;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionType;

/**
 * This class encapsulates options that control the behavior of the GraalVM compiler.
 */
// @formatter:off
public final class GraalOptions {

    @Option(help = "Uses compiler intrinsifications.", type = OptionType.Expert)
    public static final OptionKey<Boolean> Intrinsify = new OptionKey<>(true);


    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> ReduceCodeSize = new OptionKey<>(false);

    @Option(help = "Rewrite signed comparisons to unsigned ones if the result is equal.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PreferUnsignedComparison = new OptionKey<>(true);

    @Option(help = "Performs early global value numbering on statements and expressions directly after parsing. " +
                   "This can clean up the intermediate representation and simplify later optimizations. ", type = OptionType.Expert)
    public static final OptionKey<Boolean> EarlyGVN = new OptionKey<>(true);

    @Option(help = "Performs early loop-invariant code motion.", type = OptionType.Expert)
    public static final OptionKey<Boolean> EarlyLICM = new OptionKey<>(true);

    @Option(help = "Inline calls with monomorphic type profile.", type = OptionType.Debug)
    public static final OptionKey<Boolean> InlineMonomorphicCalls = new OptionKey<>(true);

    @Option(help = "Inline calls with polymorphic type profile.", type = OptionType.Debug)
    public static final OptionKey<Boolean> InlinePolymorphicCalls = new OptionKey<>(true);

    @Option(help = "Inline calls with megamorphic type profile (i.e., not all types could be recorded).", type = OptionType.Debug)
    public static final OptionKey<Boolean> InlineMegamorphicCalls = new OptionKey<>(true);

    @Option(help = "Maximum desired size of the compiler graph in nodes.", type = OptionType.Debug)
    public static final OptionKey<Integer> MaximumDesiredSize = new OptionKey<>(20000);

    @Option(help = "Minimum probability for methods to be inlined for megamorphic type profiles.", type = OptionType.Debug)
    public static final OptionKey<Double> MegamorphicInliningMinMethodProbability = new OptionKey<>(0.33D);

    @Option(help = "Specifies the maximum level of recursive inlining.", type = OptionType.Expert)
    public static final OptionKey<Integer> MaximumRecursiveInlining = new OptionKey<>(5);

    @Option(help = "Specifies the size of a graph (counted in nodes) that is considered trivial. Graphs with fewer than this number of nodes are therefore always inlined.", type = OptionType.Expert)
    public static final OptionKey<Integer> TrivialInliningSize = new OptionKey<>(10);

    @Option(help = "Specifies the maximum graph size (measured in nodes) for which inlining is explored for each call site.", type = OptionType.Expert)
    public static final OptionKey<Integer> MaximumInliningSize = new OptionKey<>(300);

    @Option(help = "If the previous low-level graph size of the method exceeds the threshold, it is not inlined.", type = OptionType.Debug)
    public static final OptionKey<Integer> SmallCompiledLowLevelGraphSize = new OptionKey<>(330);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Double> LimitInlinedInvokes = new OptionKey<>(5.0);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> InlineEverything = new OptionKey<>(false);

    @Option(help = "Performs partial escape analysis and scalar replacement optimization.", type = OptionType.Expert)
    public static final OptionKey<Boolean> PartialEscapeAnalysis = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Integer> EscapeAnalysisIterations = new OptionKey<>(2);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Integer> EscapeAnalysisLoopCutoff = new OptionKey<>(20);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<String> EscapeAnalyzeOnly = new OptionKey<>(null);

    @Option(help = "Try to float non-constant division operations to expose global value numbering of divisions.", type = OptionType.Debug)
    public static final OptionKey<Boolean> FloatingDivNodes = new OptionKey<>(true);

    @Option(help = "Specifies the maximum length of an array that will be escape analyzed.", type = OptionType.Expert)
    public static final OptionKey<Integer> MaximumEscapeAnalysisArrayLength = new OptionKey<>(128);

    @Option(help = "Specifies the number of deoptimizations allowed per compilation unit until optimistic optimizations are disabled.", type = OptionType.Expert)
    public static final OptionKey<Integer> DeoptsToDisableOptimisticOptimization = new OptionKey<>(40);

    @Option(help = "Performs loop peeling optimization.", type = OptionType.Expert)
    public static final OptionKey<Boolean> LoopPeeling = new OptionKey<>(true);

    @Option(help = "Reassociates loop invariants and constants.", type = OptionType.Expert)
    public static final OptionKey<Boolean> ReassociateExpressions = new OptionKey<>(true);

    @Option(help = "Performs loop unrolling optimization. ", type = OptionType.Expert)
    public static final OptionKey<Boolean> FullUnroll = new OptionKey<>(true);

    @Option(help = "Performs loop unswitching optimization.", type = OptionType.Expert)
    public static final OptionKey<Boolean> LoopUnswitch = new OptionKey<>(true);

    @Option(help = "Performs partial loop unrolling optimizations. This is a special form of loop unrolling " +
                   "that splits a loop into a main and a post loop. The main loop can then be unrolled by a " +
                   "fixed amount of iterations. The post-loop performs any necessary fixup iterations. This " +
                   "can improve performance because the loop control overhead is reduced in the unrolled version.", type = OptionType.Expert)
    public static final OptionKey<Boolean> PartialUnroll = new OptionKey<>(true);

    @Option(help = "Minimum frequency a loop must have to be considered for loop peeling.", type = OptionType.Debug)
    public static final OptionKey<Float> MinimumPeelFrequency = new OptionKey<>(0.35f);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Integer> LoopMaxUnswitch = new OptionKey<>(3);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> UseLoopLimitChecks = new OptionKey<>(true);

    @Option(help = "Hoists array bounds checks out of simple loops. This is ignored if " +
                   "SpeculativeGuardMovement is enabled.", type = OptionType.Expert)
    public static final OptionKey<Boolean> LoopPredication = new OptionKey<>(true);

    @Option(help = "Restricts LoopPredication to only focus on array bounds checks that " +
                   "dominate the back edge of a loop.", type = OptionType.Debug)
    public static final OptionKey<Boolean> LoopPredicationMainPath = new OptionKey<>(true);

    // debugging settings
    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> ZapStackOnMethodEntry = new OptionKey<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> DeoptALot = new OptionKey<>(false);

    @Option(help = "Stress the code emitting explicit exception throwing code.", type = OptionType.Debug)
    public static final OptionKey<Boolean> StressExplicitExceptionCode = new OptionKey<>(false);

    @Option(help = "Stress the code emitting invokes with explicit exception edges.", type = OptionType.Debug)
    public static final OptionKey<Boolean> StressInvokeWithExceptionNode = new OptionKey<>(false);

    @Option(help = "Stress the code by emitting reads at earliest instead of latest point.", type = OptionType.Debug)
    public static final OptionKey<Boolean> StressTestEarlyReads = new OptionKey<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> VerifyPhases = new OptionKey<>(false);

    @Option(help = "Use generated assembly for GC barriers if supported by the platform", type = OptionType.Expert)
    public static final OptionKey<Boolean> AssemblyGCBarriers = new OptionKey<>(true);

    @Option(help = "Force use of slow path for assembly GC barriers. Intended for debugging only.", type = OptionType.Debug)
    public static final OptionKey<Boolean> AssemblyGCBarriersSlowPathOnly = new OptionKey<>(false);

    @Option(help = "Verify oops processed by GC barriers", type = OptionType.Debug)
    public static final OptionKey<Boolean> VerifyAssemblyGCBarriers = new OptionKey<>(false);

    // Debug settings:
    @Option(help = "Start tracing compiled GC barriers after N garbage collections (disabled if N <= 0).", type = OptionType.Debug)
    public static final OptionKey<Integer> GCDebugStartCycle = new OptionKey<>(-1);

    @Option(help = "Perform platform dependent validation of the Java heap at returns", type = OptionType.Debug)
    public static final OptionKey<Boolean> VerifyHeapAtReturn = new OptionKey<>(false);

    // Other printing settings
    @Option(help = "Print profiling information when parsing a method's bytecode", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintProfilingInformation = new OptionKey<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceEscapeAnalysis = new OptionKey<>(false);

    // HotSpot command line options
    @Option(help = "Print inlining optimizations", type = OptionType.Debug)
    public static final OptionKey<Boolean> HotSpotPrintInlining = new OptionKey<>(false);

    // Register allocator debugging
    @Option(help = "Comma separated list of registers that register allocation is limited to.", type = OptionType.Debug)
    public static final OptionKey<String> RegisterPressure = new OptionKey<>(null);

    @Option(help = "Eliminates redundant conditional expressions and statements where possible. " +
                   "This can improve performance because fewer logic instructions have to be executed.", type = OptionType.Expert)
    public static final OptionKey<Boolean> ConditionalElimination = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Integer> ConditionalEliminationMaxIterations = new OptionKey<>(4);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> RawConditionalElimination = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> ReplaceInputsWithConstantsBasedOnStamps = new OptionKey<>(true);

    @Option(help = "Uses deoptimization to prune branches of code in the generated code that have never " +
                   "been executed by the interpreter.", type = OptionType.Expert)
    public static final OptionKey<Boolean> RemoveNeverExecutedCode = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> UseExceptionProbability = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OmitHotExceptionStacktrace = new OptionKey<>(false);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> GenLoopSafepoints = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> UseTypeCheckHints = new OptionKey<>(true);

    @Option(help = "Inlines the vtable stub for method dispatch during inlining.", type = OptionType.Expert)
    public static final OptionKey<Boolean> InlineVTableStubs = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> AlwaysInlineVTableStubs = new OptionKey<>(false);

    // Runtime settings
    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> SupportJsrBytecodes = new OptionKey<>(true);

    @Option(help = "Uses assumptions during compilation that may later be invalidated and cause code to be deoptimized.", type = OptionType.Expert)
    public static final OptionKey<Boolean> OptAssumptions = new OptionKey<>(true);

    @Option(help = "Replaces deoptimization points with movable guards where possible. " +
                   "This can help the optimizer to apply better code movement optimizations.", type = OptionType.Expert)
    public static final OptionKey<Boolean> OptConvertDeoptsToGuards = new OptionKey<>(true);

    @Option(help = "Tries to remove redundant memory accesses (for example, successive reads of a non-volatile Java field).", type = OptionType.Expert)
    public static final OptionKey<Boolean> OptReadElimination = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Integer> ReadEliminationMaxLoopVisits = new OptionKey<>(5);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptDeoptimizationGrouping = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptScheduleOutOfLoops = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> GuardPriorities = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptEliminateGuards = new OptionKey<>(true);

    @Option(help = "Uses trapping null checks where possible to reduce explicit control flow for null checks. " +
                   "This can improve performance because explicit null checks do not have to be performed.", type = OptionType.Expert)
    public static final OptionKey<Boolean> OptImplicitNullChecks = new OptionKey<>(true);

    @Option(help = "Performs floating-read optimization. " +
                   "This enables memory read operations to freely move in control-flow while respecting memory (anti)-dependencies. " +
                   "This helps to reduce memory accesses and can improve performance. ", type = OptionType.Expert)
    public static final OptionKey<Boolean> OptFloatingReads = new OptionKey<>(true);

    @Option(help = "", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptDevirtualizeInvokesOptimistically = new OptionKey<>(true);

    @Option(help = "Moves loop invariant guards (for example, array bounds checks) out of loops.", type = OptionType.Expert)
    public static final OptionKey<Boolean> SpeculativeGuardMovement = new OptionKey<>(true);

    @Option(help = "Track the NodeSourcePosition.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TrackNodeSourcePosition = new OptionKey<>(false);

    @Option(help = "Track source stack trace where a node was inserted into the graph.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TrackNodeInsertion = new OptionKey<>(false);

    @Option(help = "Allow backend to match complex expressions.", type = OptionType.Debug)
    public static final OptionKey<Boolean> MatchExpressions = new OptionKey<>(true);

    @Option(help = "Enable counters for various paths in snippets.", type = OptionType.Debug)
    public static final OptionKey<Boolean> SnippetCounters = new OptionKey<>(false);

    @Option(help = "Eagerly construct extra snippet info.", type = OptionType.Debug)
    public static final OptionKey<Boolean> EagerSnippets = new OptionKey<>(false);

    @Option(help = "Use a cache for snippet graphs.", type = OptionType.Debug)
    public static final OptionKey<Boolean> UseSnippetGraphCache = new OptionKey<>(true);

    @Option(help = "file:doc-files/TraceInliningHelp.txt", type = OptionType.Debug, stability = OptionStability.STABLE)
    public static final OptionKey<Boolean> TraceInlining = new OptionKey<>(false);

    @Option(help = "Enable inlining decision tracing in stubs and snippets.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceInliningForStubsAndSnippets = new OptionKey<>(false);

    @Option(help = "Embeds all the emitted code for Graal-generated stubs.", type = OptionType.Expert)
    public static final OptionKey<Boolean> InlineGraalStubs = new OptionKey<>(false);

    @Option(help = "If applicable, uses bulk zeroing instructions when the zeroing size in bytes exceeds this threshold.", type = OptionType.Expert)
    public static final OptionKey<Integer> MinimalBulkZeroingSize = new OptionKey<>(2048);

    @Option(help = "Specifies the alignment in bytes for loop header blocks.", type = OptionType.Expert)
    public static final OptionKey<Integer> LoopHeaderAlignment = new OptionKey<>(16);

    @Option(help = "Alignment in bytes for loop header blocks that have no fall through paths.", type = OptionType.Debug)
    public static final OptionKey<Integer> IsolatedLoopHeaderAlignment = new OptionKey<>(32);

    @Option(help = "Evaluates array region equality checks at compile time if the receiver is a constant and the length of the array is less than this value.", type = OptionType.Expert)
    public static final OptionKey<Integer> ArrayRegionEqualsConstantLimit = new OptionKey<>(4096);

    @Option(help = "Invocations of String.indexOf are evaluated at compile time if the receiver is a constant and its length is less than this value.", type = OptionType.Expert)
    public static final OptionKey<Integer> StringIndexOfConstantLimit = new OptionKey<>(4096);

    @Option(help = "Emits substitutions for String methods. " +
                   "This can improve performance because the compiler can use optimized intrinsics for certain string operations.", type = OptionType.Expert)
    public static final OptionKey<Boolean> EmitStringSubstitutions = new OptionKey<>(true);

    @Option(help = "Perform checks that guards and deopts aren't introduced in graphs that should handle exceptions explicitly", type = OptionType.Debug)
    public static final OptionKey<Boolean> StrictDeoptInsertionChecks = new OptionKey<>(false);

    @Option(help = "AMD64 only: Replace forward jumps (jmp, jcc) with equivalent but smaller instructions if the actual jump displacement fits in one byte.", type = OptionType.Expert)
    public static final OptionKey<Boolean> OptimizeLongJumps = new OptionKey<>(false);

    @Option(help = "Optimize integer division operation by using various mathematical foundations to "
                    + " express it in faster, equivalent, arithmetic.", type = OptionType.Debug)
    public static final OptionKey<Boolean> OptimizeDiv = new OptionKey<>(true);
}
