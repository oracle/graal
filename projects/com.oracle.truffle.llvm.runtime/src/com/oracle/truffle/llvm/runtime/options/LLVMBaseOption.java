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

import com.oracle.truffle.llvm.runtime.options.LLVMOptions.OptionParser;

public enum LLVMBaseOption implements LLVMOption {

    DEBUG("Debug", "Turns debugging on/off", false, LLVMOptions::parseBoolean, PropertyCategory.DEBUG),
    PRINT_PERFORMANCE_WARNINGS("PrintPerformanceWarnings", "Prints performance warnings", false, LLVMOptions::parseBoolean, PropertyCategory.DEBUG),
    PERFORMANCE_WARNING_ARE_FATAL("PerformanceWarningsAreFatal", "Terminates the program after a performance issue is encountered", false, LLVMOptions::parseBoolean, PropertyCategory.DEBUG),
    PRINT_FUNCTION_ASTS("PrintASTs", "Prints the Truffle ASTs for the parsed functions", false, LLVMOptions::parseBoolean, PropertyCategory.DEBUG),
    EXECUTION_COUNT("ExecutionCount", "Execute each program for as many times as specified by this option", 1, LLVMOptions::parseInteger, PropertyCategory.DEBUG),
    /*
     * The boot classpath that should be used to execute the remote JVM when executing the LLVM test
     * suite (and other tests). These rely on comparing output sent to stdout that cannot becaptured
     * inside Java, since, e.g., a printf is executed by native code. To determine the right value
     * just copy the boot class path that you use to launch the main LLVM class"
     */
    REMOTE_TEST_BOOT_CLASSPATH(
                    "TestRemoteBootPath",
                    "The boot classpath for the remote JVM used to capture native printf and other output.",
                    null,
                    LLVMOptions::parseString,
                    PropertyCategory.TESTS),
    REMOTE_TEST_CASES_AS_LOCAL(
                    "LaunchRemoteTestCasesLocally",
                    "Launches the test cases which are usually launched in a separate JVM in the currently running one.",
                    false,
                    LLVMOptions::parseBoolean,
                    PropertyCategory.TESTS),
    TEST_DISCOVERY_PATH(
                    "TestDiscoveryPath",
                    "Looks for newly supported test cases in the specified path. E.g., when executing the GCC test cases you can use /gcc.c-torture/execute to discover newly working torture test cases.",
                    null,
                    LLVMOptions::parseString,
                    PropertyCategory.TESTS),
    DYN_LIBRARY_PATHS(
                    "DynamicNativeLibraryPath",
                    "The native library search paths delimited by " + LLVMOptions.getPathDelimiter(),
                    null,
                    LLVMOptions::parseDynamicLibraryPath,
                    PropertyCategory.GENERAL),
    DYN_BITCODE_LIBRARIES(
                    "DynamicBitcodeLibraries",
                    "The paths to shared bitcode libraries delimited by " + LLVMOptions.getPathDelimiter(),
                    null,
                    LLVMOptions::parseDynamicLibraryPath,
                    PropertyCategory.GENERAL),
    PROJECT_ROOT("ProjectRoot", "Overrides the root of the project. This option exists to set the project root from mx", ".", LLVMOptions::parseString, PropertyCategory.MX),
    OPTIMIZATIONS_DISABLE_SPECULATIVE(
                    "DisableSpeculativeOptimizations",
                    "Disables all speculative optimizations regardless if they would be enabled otherwise",
                    false,
                    LLVMOptions::parseBoolean,
                    PropertyCategory.PERFORMANCE),
    OPTIMIZATION_SPECIALIZE_EXPECT_INTRINSIC("SpecializeExpectIntrinsic", "Specialize the llvm.expect intrinsic", true, LLVMOptions::parseBoolean, PropertyCategory.PERFORMANCE),
    OPTIMIZATION_VALUE_PROFILE_MEMORY_READS("ValueProfileMemoryReads", "Enable value profiling for memory reads", true, LLVMOptions::parseBoolean, PropertyCategory.PERFORMANCE),
    OPTIMIZATION_VALUE_PROFILE_FUNCTION_ARGS("ValueProfileFunctionArgs", "Enable value profiling for function arguments", true, LLVMOptions::parseBoolean, PropertyCategory.PERFORMANCE),
    OPTIMIZATION_BRANCH_PROBABILITIES("InjectBranchProbabilities", "Injects branch probabilities for the basic block successors", true, LLVMOptions::parseBoolean, PropertyCategory.PERFORMANCE),
    OPTIMIZATION_INTRINSIFY_C_FUNCTIONS("IntrinsifyCFunctions", "Substitute C functions by Java equivalents where possible", true, LLVMOptions::parseBoolean, PropertyCategory.PERFORMANCE),
    OPTIMIZATION_INLINE_CACHE_SIZE("InlineCacheSize", "Specifies the size of the polymorphic inline cache", 5, LLVMOptions::parseInteger, PropertyCategory.PERFORMANCE),
    OPTIMIZATION_LIFE_TIME_ANALYSIS(
                    "EnableLifetimeAnalysis",
                    "Performs a lifetime analysis to set dead frame slots to null to assist the PE",
                    true,
                    LLVMOptions::parseBoolean,
                    PropertyCategory.PERFORMANCE),
    NATIVE_CALL_STATS("PrintNativeCallStats", "Outputs stats about native call site frequencies", false, LLVMOptions::parseBoolean, PropertyCategory.DEBUG),
    LIFE_TIME_ANALYSIS_STATS("PrintNativeAnalysisStats", "Outputs the results of the lifetime analysis (if enabled)", false, LLVMOptions::parseBoolean, PropertyCategory.DEBUG);

    LLVMBaseOption(String key, String description, Object defaultValue, OptionParser parser, PropertyCategory category) {
        this.key = LLVMOptions.getOptionPrefix() + key;
        this.description = description;
        this.defaultValue = defaultValue;
        this.parser = parser;
        this.category = category;
    }

    private final String key;
    private final String description;
    private final Object defaultValue;
    private final OptionParser parser;
    private final PropertyCategory category;

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Object getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String getCategoryLabel() {
        return category.toString();
    }

    @Override
    public Object getValue() {
        return parser.parse(this);
    }

    public enum PropertyCategory {
        GENERAL,
        DEBUG,
        PERFORMANCE,
        TESTS,
        MX;
    }

}
