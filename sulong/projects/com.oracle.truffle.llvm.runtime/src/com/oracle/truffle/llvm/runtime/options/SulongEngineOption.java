/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.options;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;

public final class SulongEngineOption {

    public static final String OPTION_ARRAY_SEPARATOR = ":";

    // @formatter:off
    @Option(name = "llvm.stackSize",
            category = OptionCategory.USER,
            stability = OptionStability.STABLE,
            help = "The stack size, please end the input with one of: k, m, g, or t. " +
                   "Note that the stack size will be in bytes if no appropriate suffix is given.")
            public static final OptionKey<String> STACK_SIZE = new OptionKey<>("81920k");

    public static final String LIBRARY_PATH_NAME = "llvm.libraryPath";
    @Option(name = LIBRARY_PATH_NAME,
            category = OptionCategory.USER,
            stability = OptionStability.STABLE,
            help = "A list of paths where Sulong will search for relative libraries. " +
                   "Paths are delimited by a colon \'" + OPTION_ARRAY_SEPARATOR + "\'.")
    public static final OptionKey<String> LIBRARY_PATH = new OptionKey<>("");

    public static final String LOAD_CXX_LIBRARIES_NAME = "llvm.loadC++Libraries";
    @Option(name = LOAD_CXX_LIBRARIES_NAME,
            category = OptionCategory.EXPERT,
            help = "Specifies whether the standard C++ libraries (libc++ and libc++abi) " +
                   "should be loaded by default. This should only be needed for running " +
                   "plain bitcode files, since executables (ELF, Mach-O) usually have a " +
                   "dependency on both of them. Thus, the option is off by default.")
    public static final OptionKey<Boolean> LOAD_CXX_LIBRARIES = new OptionKey<>(false);

    public static final String CXX_INTEROP_NAME = "llvm.C++Interop";
    @Option(name = CXX_INTEROP_NAME,
            category = OptionCategory.EXPERT,
            help = "Enables using C++ code and features via interop.")
    public static final OptionKey<Boolean> CXX_INTEROP = new OptionKey<>(false);

    @Option(name = "llvm.enableExternalNativeAccess",
            category = OptionCategory.USER,
            help = "Enable Sulongs native interface.")
    public static final OptionKey<Boolean> ENABLE_NFI = new OptionKey<>(true);

    @Option(name = "llvm.debugSysCalls",
            category = OptionCategory.INTERNAL,
            help = "Turns syscall debugging on/off. " +
                   "Set value to \'stdout\', \'stderr\' or \'file://<path to writable file>\' to enable.")
    public static final OptionKey<String> DEBUG_SYSCALLS = new OptionKey<>(String.valueOf(false));

    @Option(name = "llvm.printNativeCallStats",
            category = OptionCategory.INTERNAL,
            help = "Outputs stats about native call site frequencies." +
                   "Set value to \'stdout\', \'stderr\' or \'file://<path to writable file>\' to enable.")
    public static final OptionKey<String> NATIVE_CALL_STATS = new OptionKey<>(String.valueOf(false));

    @Option(name = "llvm.printLifetimeAnalysisStats",
            category = OptionCategory.INTERNAL,
            help = "Prints the results of the lifetime analysis." +
                   "Set value to \'stdout\', \'stderr\' or \'file://<path to writable file>\' to enable.")
    public static final OptionKey<String> PRINT_LIFE_TIME_ANALYSIS_STATS = new OptionKey<>(String.valueOf(false));

    @Option(name = "llvm.debugLoader",
            category = OptionCategory.EXPERT,
            help = "Turns dynamic loader debugging on/off. " +
                   "Set value to \'stdout\', \'stderr\' or \'file://<path to writable file>\' to enable.")
    public static final OptionKey<String> LD_DEBUG = new OptionKey<>(String.valueOf(false));

    @Option(name = "llvm.optimizeFrameSlots",
            category = OptionCategory.INTERNAL,
            help = "Enable fusing of instructions producing values with instructions consuming values.")
    public static final OptionKey<Boolean> OPTIMIZE_FRAME_SLOTS = new OptionKey<>(true);

    @Option(name = "llvm.printAST",
            category = OptionCategory.INTERNAL,
            help = "Prints the Truffle AST of functions when it is created. " +
                   "A comma-separated list of regular expressions that will be matched against function names.")
    public static final OptionKey<String> PRINT_AST = new OptionKey<>("");

    @Option(name = "llvm.parseOnly",
            category = OptionCategory.EXPERT,
            help = "Only parses a bc file; execution is not possible.")
    public static final OptionKey<Boolean> PARSE_ONLY = new OptionKey<>(false);

    @Option(name = "llvm.enableLVI",
            category = OptionCategory.EXPERT,
            help = "This option is deprecated, local variable inspection is always enabled.",
            deprecated = true)
    public static final OptionKey<Boolean> ENABLE_LVI = new OptionKey<>(false);

    @Option(name = "llvm.OSR",
            category = OptionCategory.EXPERT,
            help = "Enable on-stack-replacement of loops.")
    public static final OptionKey<Boolean> ENABLE_OSR = new OptionKey<>(true);

    public static final String LAZY_PARSING_NAME = "llvm.lazyParsing";
    @Option(name = LAZY_PARSING_NAME,
            category = OptionCategory.EXPERT,
            help = "Enable lazy parsing of LLVM bitcode files.")
    public static final OptionKey<Boolean> LAZY_PARSING = new OptionKey<>(true);

    @Option(name = "llvm.llDebug",
            category = OptionCategory.EXPERT,
            help = "Enable IR-level debugging of LLVM bitcode files.")
    public static final OptionKey<Boolean> LL_DEBUG = new OptionKey<>(false);

    @Option(name = "llvm.llDebug.verbose",
            category = OptionCategory.EXPERT,
            help = "Enables diagnostics for IR-level debugging (e.g., report missing .ll files). Requires \'--llvm.llDebug=true\'. " +
                   "Set value to \'stdout\', \'stderr\' or \'file://<path to writable file>\' to enable.")
    public static final OptionKey<String> LL_DEBUG_VERBOSE = new OptionKey<>("");

    @Option(name = "llvm.llDebug.sources",
            category = OptionCategory.EXPERT,
            help = "Provide the locations of *.ll files for debugging. " +
                   "The expected format is <bc-path>=<ll-path>{:<bc-path>=<ll-path>}.")
    public static final OptionKey<String> LL_DEBUG_SOURCES = new OptionKey<>("");

    @Option(name = "llvm.printStackTraceOnAbort",
            category = OptionCategory.INTERNAL,
            help = "Prints a C stack trace when abort() is called.")
    public static final OptionKey<Boolean> STACKTRACE_ON_ABORT = new OptionKey<>(false);

    @Option(name = "llvm.traceIR",
            category = OptionCategory.EXPERT,
            help = "Prints a trace of the executed bitcode. Requires \'--llvm.llDebug=true\'. " +
                   "Set value to \'stdout\', \'stderr\' or \'file://<path to writable file>\' to enable.")
    public static final OptionKey<String> TRACE_IR = new OptionKey<>("");

    @Option(name = "llvm.libraries",
            category = OptionCategory.USER,
            stability = OptionStability.STABLE,
            help = "List of libraries (precompiled libraries *.dylib/*.so as well as bitcode libraries *.bc). " +
                   "Files with a relative path will be looked up relative to llvm.libraryPath. " +
                   "Libraries are delimited by a colon \'" + OPTION_ARRAY_SEPARATOR + "\'.")
    public static final OptionKey<String> LIBRARIES = new OptionKey<>("");
    // @formatter:on

    public static List<OptionDescriptor> describeOptions() {
        ArrayList<OptionDescriptor> options = new ArrayList<>();
        Iterator<OptionDescriptor> iterator = SulongEngineOption.createDescriptors().iterator();
        while (iterator.hasNext()) {
            options.add(iterator.next());
        }
        return options;
    }

    public static OptionDescriptors createDescriptors() {
        return new SulongEngineOptionOptionDescriptors();
    }

    public static boolean optionEnabled(String option) {
        return !"".equalsIgnoreCase(option) && !"false".equalsIgnoreCase(option);
    }

    public static List<String> getPolyglotOptionSearchPaths(TruffleLanguage.Env env) {
        String libraryPathOption = env.getOptions().get(LIBRARY_PATH);
        String[] libraryPath = "".equals(libraryPathOption) ? new String[0] : libraryPathOption.split(OPTION_ARRAY_SEPARATOR);
        return Arrays.asList(libraryPath);
    }

    public static List<String> getPolyglotOptionExternalLibraries(TruffleLanguage.Env env) {
        CompilerAsserts.neverPartOfCompilation();
        String librariesOption = env.getOptions().get(LIBRARIES);
        String[] userLibraries = "".equals(librariesOption) ? new String[0] : librariesOption.split(OPTION_ARRAY_SEPARATOR);
        return Arrays.asList(userLibraries);
    }
}
