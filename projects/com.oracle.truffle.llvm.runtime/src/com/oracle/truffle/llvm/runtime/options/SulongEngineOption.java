/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.
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

import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionKey;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLanguage;

public final class SulongEngineOption {

    public static final String OPTION_ARRAY_SEPARATOR = ":";

    public static final OptionKey<String> CONFIGURATION = new OptionKey<>("basic");
    public static final String CONFIGURATION_NAME = "llvm.configuration";
    public static final String CONFIGURATION_INFO = "Sulongs configuration (default=basic).";

    public static final OptionKey<Integer> STACK_SIZE_KB = new OptionKey<>(81920);
    public static final String STACK_SIZE_KB_NAME = "llvm.stackSizeKB";
    public static final String STACK_SIZE_KB_INFO = "The stack size in KB.";

    public static final OptionKey<String> LIBRARY_PATH = new OptionKey<>("");
    public static final String LIBRARY_PATH_NAME = "llvm.libraryPath";
    public static final String LIBRARY_PATH_INFO = "A list of paths where Sulong will search for relative libraries. Paths are delimited by " +
                    OPTION_ARRAY_SEPARATOR + " .";

    public static final OptionKey<String> LIBRARIES = new OptionKey<>("");
    public static final String LIBRARIES_NAME = "llvm.libraries";
    public static final String LIBRARIES_INFO = "List of libraries (precompiled libraires *.dylib/*.so as well as bitcode libraries *.bc). Files with a relative path will be looked up relative to llvm.libraryPath. Libraries are delimited by " +
                    OPTION_ARRAY_SEPARATOR + " .";

    public static final OptionKey<Boolean> ENABLE_NFI = new OptionKey<>(true);
    public static final String ENABLE_NFI_NAME = "llvm.enableExternalNativeAccess";
    public static final String ENABLE_NFI_INFO = "Enable Sulongs native interface.";

    public static final OptionKey<String> DEBUG = new OptionKey<>(String.valueOf(false));
    public static final String DEBUG_NAME = "llvm.debug";
    public static final String DEBUG_INFO = "Turns debugging on/off. Can be \'true\', \'false\', \'stdout\', \'stderr\' or a filepath.";

    public static final OptionKey<String> NATIVE_CALL_STATS = new OptionKey<>(String.valueOf(false));
    public static final String NATIVE_CALL_STATS_NAME = "llvm.printNativeCallStats";
    public static final String NATIVE_CALL_STATS_INFO = "Outputs stats about native call site frequencies. Can be \'true\', \'false\', \'stdout\', \'stderr\' or a filepath.";

    public static final OptionKey<String> PRINT_LIFE_TIME_ANALYSIS_STATS = new OptionKey<>(String.valueOf(false));
    public static final String PRINT_LIFE_TIME_ANALYSIS_STATS_NAME = "llvm.printLifetimeAnalysisStats";
    public static final String PRINT_LIFE_TIME_ANALYSIS_STATS_INFO = "Prints the results of the lifetime analysis. Can be \'true\', \'false\', \'stdout\', \'stderr\' or a filepath.";

    public static final OptionKey<Boolean> PARSE_ONLY = new OptionKey<>(false);
    public static final String PARSE_ONLY_NAME = "llvm.parseOnly";
    public static final String PARSE_ONLY_INFO = "Only parses a bc file; execution is not possible.";

    public static final OptionKey<Boolean> ENABLE_LVI = new OptionKey<>(false);
    public static final String ENABLE_LVI_NAME = "llvm.enableLVI";
    public static final String ENABLE_LVI_INFO = "Enable source-level inspection of local variables.";

    public static final OptionKey<Boolean> STACKTRACE_ON_ABORT = new OptionKey<>(false);
    public static final String STACKTRACE_ON_ABORT_NAME = "llvm.printStackTraceOnAbort";
    public static final String STACKTRACE_ON_ABORT_INFO = "Prints a C stack trace when abort() is called.";

    public static List<OptionDescriptor> describeOptions() {
        ArrayList<OptionDescriptor> options = new ArrayList<>();
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.CONFIGURATION, SulongEngineOption.CONFIGURATION_NAME).help(SulongEngineOption.CONFIGURATION_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.STACK_SIZE_KB, SulongEngineOption.STACK_SIZE_KB_NAME).help(SulongEngineOption.STACK_SIZE_KB_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.LIBRARIES, SulongEngineOption.LIBRARIES_NAME).help(SulongEngineOption.LIBRARIES_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.LIBRARY_PATH, SulongEngineOption.LIBRARY_PATH_NAME).help(SulongEngineOption.LIBRARY_PATH_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.ENABLE_NFI, SulongEngineOption.ENABLE_NFI_NAME).help(SulongEngineOption.ENABLE_NFI_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.DEBUG, SulongEngineOption.DEBUG_NAME).help(SulongEngineOption.DEBUG_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.NATIVE_CALL_STATS, SulongEngineOption.NATIVE_CALL_STATS_NAME).help(SulongEngineOption.NATIVE_CALL_STATS_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.PRINT_LIFE_TIME_ANALYSIS_STATS, SulongEngineOption.PRINT_LIFE_TIME_ANALYSIS_STATS_NAME).help(
                        SulongEngineOption.PRINT_LIFE_TIME_ANALYSIS_STATS_INFO).category(
                                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.PARSE_ONLY, SulongEngineOption.PARSE_ONLY_NAME).help(
                        SulongEngineOption.PARSE_ONLY_INFO).category(
                                        OptionCategory.EXPERT).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.ENABLE_LVI, SulongEngineOption.ENABLE_LVI_NAME).help(SulongEngineOption.ENABLE_LVI_INFO).category(OptionCategory.DEBUG).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.STACKTRACE_ON_ABORT, SulongEngineOption.STACKTRACE_ON_ABORT_NAME).help(SulongEngineOption.STACKTRACE_ON_ABORT_INFO).category(
                        OptionCategory.DEBUG).build());
        return options;
    }

    public static PrintStream getStream(String name) {
        if ("stderr".equals(name)) {
            return System.err;
        } else {
            return System.out;
        }
    }

    public static boolean isTrue(String option) {
        return "true".equals(option.toLowerCase()) || "stdout".equals(option.toLowerCase()) || "stderr".equals(option.toLowerCase());
    }

    public static List<String> getPolyglotOptionSearchPaths(TruffleLanguage.Env env) {
        String graalHome = System.getProperty("graalvm.home");
        String libraryPathOption = env.getOptions().get(LIBRARY_PATH);
        String[] libraryPath = libraryPathOption.equals("") ? new String[0] : libraryPathOption.split(OPTION_ARRAY_SEPARATOR);

        List<String> path = new ArrayList<>();
        path.addAll(Arrays.asList(libraryPath));

        if (graalHome != null && !graalHome.equals("")) {
            String[] graalHomePath = new String[]{Paths.get(graalHome).toString(), Paths.get(graalHome, "jre", "languages", "llvm").toString()};
            path.addAll(Arrays.asList(graalHomePath));
        }
        return path;
    }

    public static List<String> getPolyglotOptionExternalLibraries(TruffleLanguage.Env env) {
        CompilerAsserts.neverPartOfCompilation();
        String librariesOption = env.getOptions().get(LIBRARIES);
        String[] userLibraries = librariesOption.equals("") ? new String[0] : librariesOption.split(OPTION_ARRAY_SEPARATOR);
        return Arrays.asList(userLibraries);
    }

}
