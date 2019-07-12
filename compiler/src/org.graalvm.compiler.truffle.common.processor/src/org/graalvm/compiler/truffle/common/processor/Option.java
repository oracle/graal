/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
            .def("null")
            .help("Restrict compilation to comma-separated list of includes (or excludes prefixed with tilde).",
                  "EBNF format of argument value:  CompileOnly = Element, { ',', Element } ;")
            .deprecatedBy("CompileOnly"),

        option("TruffleCompilation")
            .type("Boolean")
            .category("INTERNAL")
            .def("true")
            .help("Enable or disable truffle compilation.")
            .deprecatedBy("Compilation"),

        option("TruffleCompileImmediately")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Compile immediately to test truffle compiler")
            .deprecatedBy("CompileImmediately"),

        option("TruffleCompilationThreshold")
            .type("Integer")
            .category("USER")
            .def("1000")
            .help("Compile call target when call count exceeds this threshold.")
            .deprecatedBy("CompilationThreshold"),

        option("TruffleFirstTierCompilationThreshold")
            .type("Integer")
            .category("EXPERT")
            .def("100")
            .help("Compile call target in the first tier when call count exceeds this threshold.")
            .deprecatedBy("FirstTierCompilationThreshold"),

        option("TruffleFirstTierMinInvokeThreshold")
            .type("Integer")
            .category("EXPERT")
            .def("1")
            .help("Minimum number of calls before a call target is compiled in the first tier.")
            .deprecatedBy("FirstTierMinInvokeThreshold"),

        option("TruffleMinInvokeThreshold")
            .type("Integer")
            .category("EXPERT")
            .def("3")
            .help("Minimum number of calls before a call target is compiled")
            .deprecatedBy("MinInvokeThreshold"),

        option("TruffleInvalidationReprofileCount")
            .type("Integer")
            .category("EXPERT")
            .def("3")
            .help("Delay compilation after an invalidation to allow for reprofiling")
            .deprecatedBy("InvalidationReprofileCount"),

        option("TruffleReplaceReprofileCount")
            .type("Integer")
            .category("EXPERT")
            .def("3")
            .help("Delay compilation after a node replacement")
            .deprecatedBy("ReplaceReprofileCount"),

        option("TruffleFunctionInlining")
            .type("Boolean")
            .category("INTERNAL")
            .def("true")
            .help("Enable automatic inlining of call targets")
            .deprecatedBy("Inlining"), // COMPILER

        option("TruffleInliningMaxCallerSize")
            .type("Integer")
            .category("EXPERT")
            .def("2250")
            .help("Stop inlining if caller's cumulative tree size would exceed this limit")
            .deprecatedBy("InliningNodeBudget"),

        option("TruffleMaximumRecursiveInlining")
            .type("Integer")
            .category("EXPERT")
            .def("2")
            .help("Maximum level of recursive inlining")
            .deprecatedBy("InliningRecursionDepth"),

        option("TruffleSplitting")
            .type("Boolean")
            .category("EXPERT")
            .def("true")
            .help("Enable call target splitting")
            .deprecatedBy("Splitting"),

        option("TruffleOSR")
            .type("Boolean")
            .category("INTERNAL")
            .def("true")
            .help("Enable on stack replacement for Truffle loops.")
            .deprecatedBy("OSR"),

        option("TruffleOSRCompilationThreshold")
            .type("Integer")
            .category("INTERNAL")
            .def("100000")
            .help("Number of loop iterations until on-stack-replacement compilation is triggered.")
            .deprecatedBy("OSRCompilationThreshold"),

        option("TruffleSplittingMaxCalleeSize")
            .type("Integer")
            .category("INTERNAL")
            .def("100")
            .help("Disable call target splitting if tree size exceeds this limit")
            .deprecatedBy("SplittingMaxCalleeSize"),

        option("TruffleSplittingGrowthLimit")
            .type("Double")
            .category("INTERNAL")
            .def("1.5")
            .help("Disable call target splitting if the number of nodes created by splitting exceeds this factor times node count")
            .deprecatedBy("SplittingGrowthLimit"),

        option("TruffleSplittingMaxNumberOfSplitNodes")
            .type("Integer")
            .category("INTERNAL")
            .def("500_000")
            .help("Disable call target splitting if number of nodes created by splitting exceeds this limit")
            .deprecatedBy("SplittingMaxNumberOfSplitNodes"),

        option("TruffleSplittingMaxPropagationDepth")
            .type("Integer")
            .category("INTERNAL")
            .def("5")
            .help("Propagate info about a polymorphic specialize through maximum this many call targets")
            .deprecatedBy("SplittingMaxPropagationDepth"),

        option("TruffleLegacySplitting")
            .type("Boolean")
            .category("EXPERT")
            .def("false")
            .help("Use legacy splitting heuristic. This option will be removed.")
            .deprecatedBy("LegacySplitting"),

        option("TruffleTraceSplittingSummary")
            .type("Boolean")
            .category("EXPERT")
            .def("false")
            .help("Used for debugging the splitting implementation. Prints splitting summary directly to stdout on shutdown")
            .deprecatedBy("TraceSplittingSummary"),

        option("TruffleSplittingTraceEvents")
            .type("Boolean")
            .category("EXPERT")
            .def("false")
            .help("Trace details of splitting events and decisions.")
            .deprecatedBy("SplittingTraceEvents"),

        option("TruffleSplittingDumpDecisions")
            .type("Boolean")
            .category("EXPERT")
            .def("false")
            .help("Dumps to IGV information on polymorphic events")
            .deprecatedBy("SplittingDumpDecisions"),

        option("TruffleSplittingAllowForcedSplits")
            .type("Boolean")
            .category("EXPERT")
            .def("true")
            .help("Should forced splits be allowed.")
            .deprecatedBy("SplittingAllowForcedSplits"),

        option("TruffleBackgroundCompilation")
            .type("Boolean")
            .category("EXPERT")
            .def("true")
            .help("Enable asynchronous truffle compilation in background thread")
            .deprecatedBy("BackgroundCompilation"),

        option("TruffleCompilerThreads")
            .type("Integer")
            .category("EXPERT")
            .def("0")
            .help("Manually set the number of compiler threads")
            .deprecatedBy("CompilerThreads"),

        option("TruffleReturnTypeSpeculation")
            .type("Boolean")
            .category("INTERNAL")
            .def("true")
            .deprecatedBy("ReturnTypeSpeculation"),

        option("TruffleArgumentTypeSpeculation")
            .type("Boolean")
            .category("INTERNAL")
            .def("true")
            .deprecatedBy("ArgumentTypeSpeculation"),

        option("TraceTruffleCompilation")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print information for compilation results")
            .deprecatedBy("TraceCompilation"),

        option("TraceTruffleCompilationDetails")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print information for compilation queuing")
            .deprecatedBy("TraceCompilationDetails"),

        option("TraceTruffleCompilationPolymorphism")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print all polymorphic and generic nodes after each compilation"),

        option("TraceTruffleCompilationAST")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print the entire AST after each compilation"),

        option("TraceTruffleCompilationCallTree")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print the inlined call tree for each compiled method"),

        option("TruffleCompilationExceptionsAreFatal")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Treat compilation exceptions as fatal exceptions that will exit the application")
            .deprecatedBy("PerformanceWarningsAreFatal"), // COMPILER

        option("TrufflePerformanceWarningsAreFatal")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Treat performance warnings as fatal occurrences that will exit the applications")
            .deprecatedBy("CompilationExceptionsAreFatal"), // COMPILER

        option("TruffleCompilationExceptionsArePrinted")
            .type("Boolean")
            .category("INTERNAL")
            .def("true")
            .help("Prints the exception stack trace for compilation exceptions")
            .deprecatedBy("CompilationExceptionsArePrinted"),

        option("TruffleCompilationExceptionsAreThrown")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Treat compilation exceptions as thrown runtime exceptions")
            .deprecatedBy("CompilationExceptionsAreThrown"),

        option("TraceTruffleInlining")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print information for inlining for each compilation.")
            .deprecatedBy("TraceInlining"),

        option("TraceTruffleSplitting")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print information for each splitted call site.")
            .deprecatedBy("TraceSplitting"),

        option("TraceTruffleAssumptions")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print stack trace on assumption invalidation"),

        option("TraceTruffleStackTraceLimit")
            .type("Integer")
            .category("INTERNAL")
            .def("20")
            .help("Number of stack trace elements printed by TraceTruffleTransferToInterpreter and TraceTruffleAssumptions"), // COMPILER

        option("TruffleCompilationStatistics")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print Truffle compilation statistics at the end of a run."),

        option("TruffleCompilationStatisticDetails")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print additional more verbose Truffle compilation statistics at the end of a run."),

        option("TruffleProfilingEnabled")
            .type("Boolean")
            .category("INTERNAL")
            .def("true")
            .help("Enable/disable builtin profiles in com.oracle.truffle.api.profiles."),

        option("TraceTruffleTransferToInterpreter")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print stack trace on transfer to interpreter."),

        option("TruffleMultiTier")
            .type("Boolean")
            .category("EXPERT")
            .def("false")
            .help("Whether to use multiple Truffle compilation tiers by default.")
            .deprecatedBy("MultiTier"),
    };
    // @formatter:on

    String name;
    String category;
    String type;
    String defaultValue;
    String[] help = {""};
    String deprecatedBy;

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
        deprecatedBy = replacement;
        return this;
    }

    static Option option(String name) {
        return new Option().name(name);
    }
}
