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
                  "EBNF format of argument value:  CompileOnly = Element, { ',', Element } ;"),

        option("TruffleCompilation")
            .type("Boolean")
            .category("INTERNAL")
            .def("true")
            .help("Enable or disable truffle compilation."),

        option("TruffleCompileImmediately")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Compile immediately to test truffle compiler"),

        option("TruffleCompilationThreshold")
            .type("Integer")
            .category("USER")
            .def("1000")
            .help("Compile call target when call count exceeds this threshold.")
            .javadocExtra("Deprecated: Use {@code PolyglotCompilerOptions.CompilationThreshold} instead."),

        option("TruffleFirstTierCompilationThreshold")
            .type("Integer")
            .category("EXPERT")
            .def("100")
            .help("Compile call target in the first tier when call count exceeds this threshold.")
            .javadocExtra("Deprecated: Use {@code PolyglotCompilerOptions.CompilationThreshold} instead."),

        option("TruffleFirstTierMinInvokeThreshold")
            .type("Integer")
            .category("EXPERT")
            .def("1")
            .help("Minimum number of calls before a call target is compiled in the first tier."),

        option("TruffleTimeThreshold")
            .type("Integer")
            .category("USER")
            .def("50000")
            .help("Defines the maximum timespan in milliseconds that is required for a call target to be queued for compilation.")
            .javadocExtra("Deprecated: Use {@code PolyglotCompilerOptions.QueueTimeThreshold} instead."),

        option("TruffleMinInvokeThreshold")
            .type("Integer")
            .category("EXPERT")
            .def("3")
            .help("Minimum number of calls before a call target is compiled"),

        option("TruffleInvalidationReprofileCount")
            .type("Integer")
            .category("EXPERT")
            .def("3")
            .help("Delay compilation after an invalidation to allow for reprofiling"),

        option("TruffleReplaceReprofileCount")
            .type("Integer")
            .category("EXPERT")
            .def("3")
            .help("Delay compilation after a node replacement"),

        option("TruffleFunctionInlining")
            .type("Boolean")
            .category("INTERNAL")
            .def("true")
            .help("Enable automatic inlining of call targets"), // COMPILER

        option("TruffleInliningMaxCallerSize")
            .type("Integer")
            .category("EXPERT")
            .def("2250")
            .help("Stop inlining if caller's cumulative tree size would exceed this limit"),

        option("TruffleMaximumRecursiveInlining")
            .type("Integer")
            .category("EXPERT")
            .def("2")
            .help("Maximum level of recursive inlining"),

        option("TruffleSplitting")
            .type("Boolean")
            .category("EXPERT")
            .def("true")
            .help("Enable call target splitting"),

        option("TruffleOSR")
            .type("Boolean")
            .category("INTERNAL")
            .def("true")
            .help("Enable on stack replacement for Truffle loops."),

        option("TruffleOSRCompilationThreshold")
            .type("Integer")
            .category("INTERNAL")
            .def("100000")
            .help("Number of loop iterations until on-stack-replacement compilation is triggered."),

        option("TruffleSplittingMaxCalleeSize")
            .type("Integer")
            .category("INTERNAL")
            .def("100")
            .help("Disable call target splitting if tree size exceeds this limit"),

        option("TruffleSplittingGrowthLimit")
            .type("Double")
            .category("INTERNAL")
            .def("1.5")
            .help("Disable call target splitting if the number of nodes created by splitting exceeds this factor times node count"),

        option("TruffleSplittingMaxNumberOfSplitNodes")
            .type("Integer")
            .category("INTERNAL")
            .def("500_000")
            .help("Disable call target splitting if number of nodes created by splitting exceeds this limit"),

        option("TruffleSplittingMaxPropagationDepth")
            .type("Integer")
            .category("INTERNAL")
            .def("5")
            .help("Propagate info about a polymorphic specialize through maximum this many call targets"),

        option("TruffleLegacySplitting")
            .type("Boolean")
            .category("EXPERT")
            .def("false")
            .help("Use legacy splitting heuristic. This option will be removed."),

        option("TruffleTraceSplittingSummary")
            .type("Boolean")
            .category("EXPERT")
            .def("false")
            .help("Used for debugging the splitting implementation. Prints splitting summary directly to stdout on shutdown"),

        option("TruffleSplittingTraceEvents")
            .type("Boolean")
            .category("EXPERT")
            .def("false")
            .help("Trace details of splitting events and decisions."),

        option("TruffleSplittingDumpDecisions")
            .type("Boolean")
            .category("EXPERT")
            .def("false")
            .help("Dumps to IGV information on polymorphic events"),

        option("TruffleSplittingAllowForcedSplits")
            .type("Boolean")
            .category("EXPERT")
            .def("true")
            .help("Should forced splits be allowed."),

        option("TruffleBackgroundCompilation")
            .type("Boolean")
            .category("EXPERT")
            .def("true")
            .help("Enable asynchronous truffle compilation in background thread"),

        option("TruffleCompilerThreads")
            .type("Integer")
            .category("EXPERT")
            .def("0")
            .help("Manually set the number of compiler threads"),

        option("TruffleReturnTypeSpeculation")
            .type("Boolean")
            .category("INTERNAL")
            .def("true"),

        option("TruffleArgumentTypeSpeculation")
            .type("Boolean")
            .category("INTERNAL")
            .def("true"),

        option("TraceTruffleCompilation")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print information for compilation results"),

        option("TraceTruffleCompilationDetails")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print information for compilation queuing"),

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

        option("TraceTruffleExpansionSource")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print source sections for printed expansion trees"),

        option("TruffleCompilationExceptionsAreFatal")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Treat compilation exceptions as fatal exceptions that will exit the application"), // COMPILER

        option("TrufflePerformanceWarningsAreFatal")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Treat performance warnings as fatal occurrences that will exit the applications"), // COMPILER

        option("TruffleCompilationExceptionsArePrinted")
            .type("Boolean")
            .category("INTERNAL")
            .def("true")
            .help("Prints the exception stack trace for compilation exceptions"),

        option("TruffleCompilationExceptionsAreThrown")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Treat compilation exceptions as thrown runtime exceptions"),

        option("TraceTruffleInlining")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print information for inlining for each compilation."),

        option("TraceTruffleSplitting")
            .type("Boolean")
            .category("INTERNAL")
            .def("false")
            .help("Print information for each splitted call site."),

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
            .category("USER")
            .def("false")
            .help("Whether to use multiple Truffle compilation tiers by default.")
    };
    // @formatter:on

    String name;
    String category;
    String type;
    String defaultValue;
    String[] help = {""};
    String[] javadocExtra;

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

    Option javadocExtra(String... lines) {
        javadocExtra = lines;
        return this;
    }

    static Option option(String name) {
        return new Option().name(name);
    }
}
