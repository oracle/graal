/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
import java.util.List;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionKey;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLanguage;

public final class SulongEngineOption {

    public static final String OPTION_ARRAY_SEPARATOR = ":";

    public static final OptionKey<Integer> STACK_SIZE_KB = new OptionKey<>(81920);
    public static final String STACK_SIZE_KB_NAME = "sulong.sackSizeKB";
    public static final String STACK_SIZE_KB_INFO = "The stack size in KB.";

    public static final OptionKey<String> DYN_LIBRARY_PATHS = new OptionKey<>("");
    public static final String DYN_LIBRARY_PATHS_NAME = "sulong.dynamicNativeLibraryPath";
    public static final String DYN_LIBRARY_PATHS_INFO = "The native library search paths delimited by " +
                    OPTION_ARRAY_SEPARATOR + " .";

    public static final OptionKey<String> DYN_BITCODE_LIBRARIES = new OptionKey<>("");
    public static final String DYN_BITCODE_LIBRARIES_NAME = "sulong.dynamicBitcodeLibraries";
    public static final String DYN_BITCODE_LIBRARIES_INFO = "The paths to shared bitcode libraries delimited by " +
                    OPTION_ARRAY_SEPARATOR + " .";

    public static final OptionKey<Boolean> DISABLE_NFI = new OptionKey<>(false);
    public static final String DISABLE_NFI_NAME = "sulong.disableNativeInterface";
    public static final String DISABLE_NFI_INFO = "Disables Sulongs native interface.";

    public static final OptionKey<Boolean> LAZY_PARSING = new OptionKey<>(true);
    public static final String LAZY_PARSING_NAME = "sulong.lazyParsing";
    public static final String LAZY_PARSING_INFO = "Transforms LLVM IR functions to Sulong ASTs lazily.";

    public static final OptionKey<String> DEBUG = new OptionKey<>(String.valueOf(false));
    public static final String DEBUG_NAME = "sulong.debug";
    public static final String DEBUG_INFO = "Turns debugging on/off. Can be \'true\', \'false\', \'stdout\', \'stderr\' or a filepath.";

    public static final OptionKey<String> PRINT_FUNCTION_ASTS = new OptionKey<>(String.valueOf(false));
    public static final String PRINT_FUNCTION_ASTS_NAME = "sulong.printASTs";
    public static final String PRINT_FUNCTION_ASTS_INFO = "Prints the Truffle ASTs for the parsed functions. Can be \'true\', \'false\', \'stdout\', \'stderr\' or a filepath.";

    public static final OptionKey<String> NATIVE_CALL_STATS = new OptionKey<>(String.valueOf(false));
    public static final String NATIVE_CALL_STATS_NAME = "sulong.printNativeCallStats";
    public static final String NATIVE_CALL_STATS_INFO = "Outputs stats about native call site frequencies. Can be \'true\', \'false\', \'stdout\', \'stderr\' or a filepath.";

    public static final OptionKey<String> PRINT_LIFE_TIME_ANALYSIS_STATS = new OptionKey<>(String.valueOf(false));
    public static final String PRINT_LIFE_TIME_ANALYSIS_STATS_NAME = "sulong.printLifetimeAnalysisStats";
    public static final String PRINT_LIFE_TIME_ANALYSIS_STATS_INFO = "Prints the results of the lifetime analysis. Can be \'true\', \'false\', \'stdout\', \'stderr\' or a filepath.";

    public static List<OptionDescriptor> describeOptions() {
        ArrayList<OptionDescriptor> options = new ArrayList<>();
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.STACK_SIZE_KB, SulongEngineOption.STACK_SIZE_KB_NAME).help(SulongEngineOption.STACK_SIZE_KB_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.DYN_LIBRARY_PATHS, SulongEngineOption.DYN_LIBRARY_PATHS_NAME).help(SulongEngineOption.DYN_LIBRARY_PATHS_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.DYN_BITCODE_LIBRARIES, SulongEngineOption.DYN_BITCODE_LIBRARIES_NAME).help(SulongEngineOption.DYN_BITCODE_LIBRARIES_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.DISABLE_NFI, SulongEngineOption.DISABLE_NFI_NAME).help(SulongEngineOption.DISABLE_NFI_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.LAZY_PARSING, SulongEngineOption.LAZY_PARSING_NAME).help(SulongEngineOption.LAZY_PARSING_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.DEBUG, SulongEngineOption.DEBUG_NAME).help(SulongEngineOption.DEBUG_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.PRINT_FUNCTION_ASTS, SulongEngineOption.PRINT_FUNCTION_ASTS_NAME).help(SulongEngineOption.PRINT_FUNCTION_ASTS_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.NATIVE_CALL_STATS, SulongEngineOption.NATIVE_CALL_STATS_NAME).help(SulongEngineOption.NATIVE_CALL_STATS_INFO).category(
                        OptionCategory.USER).build());
        options.add(OptionDescriptor.newBuilder(SulongEngineOption.PRINT_LIFE_TIME_ANALYSIS_STATS, SulongEngineOption.PRINT_LIFE_TIME_ANALYSIS_STATS_NAME).help(
                        SulongEngineOption.PRINT_LIFE_TIME_ANALYSIS_STATS_INFO).category(
                                        OptionCategory.USER).build());
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

    public static String[] getNativeLibraries(TruffleLanguage.Env env) {
        CompilerAsserts.neverPartOfCompilation();
        String graalHome = System.getProperty("graalvm.home");
        String libraryPathOption = env.getOptions().get(DYN_LIBRARY_PATHS);
        String[] userLibraries = libraryPathOption.equals("") ? new String[0] : libraryPathOption.split(OPTION_ARRAY_SEPARATOR);
        if (graalHome != null) {
            String defaultPath;
            if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                defaultPath = Paths.get(graalHome, "language", "llvm", "libsulong.dylib").toAbsolutePath().toString();
            } else {
                defaultPath = Paths.get(graalHome, "language", "llvm", "libsulong.so").toAbsolutePath().toString();
            }
            String[] prependedUserLibraries = new String[userLibraries.length + 1];
            System.arraycopy(userLibraries, 0, prependedUserLibraries, 1, userLibraries.length);
            prependedUserLibraries[0] = defaultPath;
            return prependedUserLibraries;
        } else {
            return userLibraries;
        }
    }

    public static String[] getBitcodeLibraries(TruffleLanguage.Env env) {
        CompilerAsserts.neverPartOfCompilation();
        String graalHome = System.getProperty("graalvm.home");
        String libraryPathOption = env.getOptions().get(DYN_BITCODE_LIBRARIES);
        String[] userLibraries = libraryPathOption.equals("") ? new String[0] : libraryPathOption.split(OPTION_ARRAY_SEPARATOR);
        if (graalHome != null) {
            String defaultPath = Paths.get(graalHome, "language", "llvm", "libsulong.bc").toAbsolutePath().toString();
            String[] prependedUserLibraries = new String[userLibraries.length + 1];
            System.arraycopy(userLibraries, 0, prependedUserLibraries, 1, userLibraries.length);
            prependedUserLibraries[0] = defaultPath;
            return prependedUserLibraries;
        } else {
            return userLibraries;
        }
    }

}
