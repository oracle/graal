/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.common.processor;

/**
 * Specification of the options that are shared between the Truffle runtime and compiler.
 */
public class Option {

    /**
     * Specification of options.
     */
    // @formatter:off
    static final Option[] options = {
        option("TruffleCompileOnly")
            .type("String")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.CompileOnly.getDefaultValue()")
            .help("Restrict compilation to comma-separated list of includes (or excludes prefixed with tilde).",
                  "EBNF format of argument value:  CompileOnly = Element, { ',', Element } ;")
            .deprecatedBy("CompileOnly"),

        option("TruffleCompilation")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.Compilation.getDefaultValue()")
            .help("Enable or disable truffle compilation.")
            .deprecatedBy("Compilation"),

        option("TruffleCompileImmediately")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.CompileImmediately.getDefaultValue()")
            .help("Compile immediately to test truffle compiler")
            .deprecatedBy("CompileImmediately"),

        option("TruffleCompilationThreshold")
            .type("Integer")
            .category("USER")
            .def("PolyglotCompilerOptions.CompilationThreshold.getDefaultValue()")
            .help("Compile call target when call count exceeds this threshold.")
            .deprecatedBy("CompilationThreshold"),

        option("TruffleFirstTierCompilationThreshold")
            .type("Integer")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.FirstTierCompilationThreshold.getDefaultValue()")
            .help("Compile call target in the first tier when call count exceeds this threshold.")
            .deprecatedBy("FirstTierCompilationThreshold"),

        option("TruffleFirstTierMinInvokeThreshold")
            .type("Integer")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.FirstTierMinInvokeThreshold.getDefaultValue()")
            .help("Minimum number of calls before a call target is compiled in the first tier.")
            .deprecatedBy("FirstTierMinInvokeThreshold"),

        option("TruffleMinInvokeThreshold")
            .type("Integer")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.MinInvokeThreshold.getDefaultValue()")
            .help("Minimum number of calls before a call target is compiled")
            .deprecatedBy("MinInvokeThreshold"),

        option("TruffleInvalidationReprofileCount")
            .type("Integer")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.InvalidationReprofileCount.getDefaultValue()")
            .help("Delay compilation after an invalidation to allow for reprofiling")
            .deprecatedBy("InvalidationReprofileCount"),

        option("TruffleReplaceReprofileCount")
            .type("Integer")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.ReplaceReprofileCount.getDefaultValue()")
            .help("Delay compilation after a node replacement")
            .deprecatedBy("ReplaceReprofileCount"),

        option("TruffleFunctionInlining")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.Inlining.getDefaultValue()")
            .help("Enable automatic inlining of call targets")
            .deprecatedBy("Inlining"), // COMPILER

        option("TruffleLanguageAgnosticInlining")
            .type("Boolean")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.LanguageAgnosticInlining.getDefaultValue()")
            .help("Use language-agnostic inlining (overrides the TruffleFunctionInlining setting, option is experimental).")
            .deprecatedBy("LanguageAgnosticInlining"),

        option("TruffleInliningMaxCallerSize")
            .type("Integer")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.InliningNodeBudget.getDefaultValue()")
            .help("Stop inlining if caller's cumulative tree size would exceed this limit")
            .deprecatedBy("InliningNodeBudget"),

        option("TruffleMaximumRecursiveInlining")
            .type("Integer")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.InliningRecursionDepth.getDefaultValue()")
            .help("Maximum level of recursive inlining")
            .deprecatedBy("InliningRecursionDepth"),

        option("TruffleSplitting")
            .type("Boolean")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.Splitting.getDefaultValue()")
            .help("Enable call target splitting")
            .deprecatedBy("Splitting"),

        option("TruffleOSR")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.OSR.getDefaultValue()")
            .help("Enable on stack replacement for Truffle loops.")
            .deprecatedBy("OSR"),

        option("TruffleOSRCompilationThreshold")
            .type("Integer")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.OSRCompilationThreshold.getDefaultValue()")
            .help("Number of loop iterations until on-stack-replacement compilation is triggered.")
            .deprecatedBy("OSRCompilationThreshold"),

        option("TruffleSplittingMaxCalleeSize")
            .type("Integer")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.SplittingMaxCalleeSize.getDefaultValue()")
            .help("Disable call target splitting if tree size exceeds this limit")
            .deprecatedBy("SplittingMaxCalleeSize"),

        option("TruffleSplittingGrowthLimit")
            .type("Double")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.SplittingGrowthLimit.getDefaultValue()")
            .help("Disable call target splitting if the number of nodes created by splitting exceeds this factor times node count")
            .deprecatedBy("SplittingGrowthLimit"),

        option("TruffleSplittingMaxNumberOfSplitNodes")
            .type("Integer")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.SplittingMaxNumberOfSplitNodes.getDefaultValue()")
            .help("Disable call target splitting if number of nodes created by splitting exceeds this limit")
            .deprecatedBy("SplittingMaxNumberOfSplitNodes"),

        option("TruffleSplittingMaxPropagationDepth")
            .type("Integer")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.SplittingMaxPropagationDepth.getDefaultValue()")
            .help("Propagate info about a polymorphic specialize through maximum this many call targets")
            .deprecatedBy("SplittingMaxPropagationDepth"),

        option("TruffleTraceSplittingSummary")
            .type("Boolean")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.TraceSplittingSummary.getDefaultValue()")
            .help("Used for debugging the splitting implementation. Prints splitting summary directly to stdout on shutdown")
            .deprecatedBy("TraceSplittingSummary"),

        option("TruffleSplittingTraceEvents")
            .type("Boolean")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.SplittingTraceEvents.getDefaultValue()")
            .help("Trace details of splitting events and decisions.")
            .deprecatedBy("SplittingTraceEvents"),

        option("TruffleSplittingDumpDecisions")
            .type("Boolean")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.SplittingDumpDecisions.getDefaultValue()")
            .help("Dumps to IGV information on polymorphic events")
            .deprecatedBy("SplittingDumpDecisions"),

        option("TruffleSplittingAllowForcedSplits")
            .type("Boolean")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.SplittingAllowForcedSplits.getDefaultValue()")
            .help("Should forced splits be allowed.")
            .deprecatedBy("SplittingAllowForcedSplits"),

        option("TruffleBackgroundCompilation")
            .type("Boolean")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.BackgroundCompilation.getDefaultValue()")
            .help("Enable asynchronous truffle compilation in background thread")
            .deprecatedBy("BackgroundCompilation"),

        option("TruffleCompilerThreads")
            .type("Integer")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.CompilerThreads.getDefaultValue()")
            .help("Manually set the number of compiler threads")
            .deprecatedBy("CompilerThreads"),

        option("TruffleReturnTypeSpeculation")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.ReturnTypeSpeculation.getDefaultValue()")
            .deprecatedBy("ReturnTypeSpeculation"),

        option("TruffleArgumentTypeSpeculation")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.ArgumentTypeSpeculation.getDefaultValue()")
            .deprecatedBy("ArgumentTypeSpeculation"),

        option("TraceTruffleCompilation")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.TraceCompilation.getDefaultValue()")
            .help("Print information for compilation results")
            .deprecatedBy("TraceCompilation"),

        option("TraceTruffleCompilationDetails")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.TraceCompilationDetails.getDefaultValue()")
            .help("Print information for compilation queuing")
            .deprecatedBy("TraceCompilationDetails"),

        option("TraceTruffleCompilationPolymorphism")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.TraceCompilationPolymorphism.getDefaultValue()")
            .help("Print all polymorphic and generic nodes after each compilation")
            .deprecatedBy("TraceCompilationPolymorphism"),

        option("TraceTruffleCompilationAST")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.TraceCompilationAST.getDefaultValue()")
            .help("Print the entire AST after each compilation")
            .deprecatedBy("TraceCompilationAST"),

        option("TraceTruffleCompilationCallTree")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.TraceCompilationCallTree.getDefaultValue()")
            .help("Print the inlined call tree for each compiled method")
            .deprecatedBy("TraceCompilationCallTree"),

        option("TruffleCompilationExceptionsAreFatal")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.CompilationExceptionsAreFatal.getDefaultValue()")
            .help("Treat compilation exceptions as fatal exceptions that will exit the application")
            .deprecatedBy("CompilationExceptionsAreFatal"), // COMPILER

        option("TrufflePerformanceWarningsAreFatal")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Treat performance warnings as fatal occurrences that will exit the applications")
            .deprecatedBy("PerformanceWarningsAreFatal"), // COMPILER

        option("TruffleCompilationExceptionsArePrinted")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.CompilationExceptionsArePrinted.getDefaultValue()")
            .help("Prints the exception stack trace for compilation exceptions")
            .deprecatedBy("CompilationExceptionsArePrinted"),

        option("TruffleCompilationExceptionsAreThrown")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.CompilationExceptionsAreThrown.getDefaultValue()")
            .help("Treat compilation exceptions as thrown runtime exceptions")
            .deprecatedBy("CompilationExceptionsAreThrown"),

        option("TraceTruffleInlining")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.TraceInlining.getDefaultValue()")
            .help("Print information for inlining for each compilation.")
            .deprecatedBy("TraceInlining"),

        option("TraceTruffleSplitting")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.TraceSplitting.getDefaultValue()")
            .help("Print information for each splitted call site.")
            .deprecatedBy("TraceSplitting"),

        option("TraceTruffleAssumptions")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.TraceAssumptions.getDefaultValue()")
            .help("Print stack trace on assumption invalidation")
            .deprecatedBy("TraceAssumptions"),

        option("TraceTruffleStackTraceLimit")
            .type("Integer")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.TraceStackTraceLimit.getDefaultValue()")
            .help("Number of stack trace elements printed by TraceTruffleTransferToInterpreter and TraceTruffleAssumptions")
            .deprecatedBy("TraceStackTraceLimit"), // COMPILER

        option("TruffleCompilationStatistics")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.CompilationStatistics.getDefaultValue()")
            .help("Print Truffle compilation statistics at the end of a run.")
            .deprecatedBy("CompilationStatistics"),

        option("TruffleCompilationStatisticDetails")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.CompilationStatisticDetails.getDefaultValue()")
            .help("Print additional more verbose Truffle compilation statistics at the end of a run.")
            .deprecatedBy("CompilationStatisticDetails"),

        option("TruffleProfilingEnabled")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.Profiling.getDefaultValue()")
            .help("Enable/disable builtin profiles in com.oracle.truffle.api.profiles.")
            .deprecatedBy("Profiling"),

        option("TraceTruffleTransferToInterpreter")
            .type("Boolean")
            .category("INTERNAL")
            .def("PolyglotCompilerOptions.TraceTransferToInterpreter.getDefaultValue()")
            .help("Print stack trace on transfer to interpreter.")
            .deprecatedBy("TraceTransferToInterpreter"),

        option("TruffleMultiTier")
            .type("Boolean")
            .category("EXPERT")
            .def("PolyglotCompilerOptions.MultiTier.getDefaultValue()")
            .help("Whether to use multiple Truffle compilation tiers by default.")
            .deprecatedBy("MultiTier"),

        option("PrintTruffleTrees")
            .type("Boolean")
            .category("INTERNAL")
            .def("true")
            .help("Enable dumping Truffle ASTs to the IdealGraphVisualizer.")
            .deprecated("Deprecated with no replacement."),
    };
    // @formatter:on

    String name;
    String category;
    String type;
    String defaultValue;
    String[] help = {""};
    String deprecationMessage;

    Option name(String value) {
        name = value;
        return this;
    }

    Option type(String value) {
        type = value;
        return this;
    }

    Option category(String value) {
        category = value;
        return this;
    }

    Option def(String value) {
        defaultValue = value;
        return this;
    }

    Option help(String... lines) {
        help = lines;
        return this;
    }

    Option deprecatedBy(String replacement) {
        deprecationMessage = "Deprecated by {@link PolyglotCompilerOptions#" + replacement + "}.";
        return this;
    }

    Option deprecated(String message) {
        deprecationMessage = message;
        return this;
    }

    static Option option(String name) {
        return new Option().name(name);
    }
}
