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

public class LLVMBaseOptionFacade {

    public static boolean debugEnabled() {
        return LLVMOptions.getParsedProperty(Property.DEBUG);
    }

    public static boolean printFunctionASTs() {
        return LLVMOptions.getParsedProperty(Property.PRINT_FUNCTION_ASTS);
    }

    public static String getRemoteTestBootClassPath() {
        if (LLVMOptions.getParsedProperty(Property.REMOTE_TEST_BOOT_CLASSPATH) == null) {
            throw new AssertionError();
        }
        return LLVMOptions.getParsedProperty(Property.REMOTE_TEST_BOOT_CLASSPATH);
    }

    public static String getTestDiscoveryPath() {
        return LLVMOptions.getParsedProperty(Property.TEST_DISCOVERY_PATH);
    }

    public static boolean discoveryTestModeEnabled() {
        return getTestDiscoveryPath() != null;
    }

    public static String[] getDynamicLibraryPaths() {
        return LLVMOptions.getParsedProperty(Property.DYN_LIBRARY_PATHS);
    }

    public static String getProjectRoot() {
        return LLVMOptions.getParsedProperty(Property.PROJECT_ROOT);
    }

    public static boolean specializeForExpectIntrinsic() {
        return !disableSpeculativeOptimizations() && (boolean) LLVMOptions.getParsedProperty(Property.OPTIMIZATION_SPECIALIZE_EXPECT_INTRINSIC);
    }

    public static boolean valueProfileMemoryReads() {
        return !disableSpeculativeOptimizations() && (boolean) LLVMOptions.getParsedProperty(Property.OPTIMIZATION_VALUE_PROFILE_MEMORY_READS);
    }

    public static boolean intrinsifyCLibraryFunctions() {
        return LLVMOptions.getParsedProperty(Property.OPTIMIZATION_INTRINSIFY_C_FUNCTIONS);
    }

    public static boolean injectBranchProbabilities() {
        return !disableSpeculativeOptimizations() && (boolean) LLVMOptions.getParsedProperty(Property.OPTIMIZATION_BRANCH_PROBABILITIES);
    }

    public static boolean printNativeCallStats() {
        return LLVMOptions.getParsedProperty(Property.NATIVE_CALL_STATS);
    }

    public static boolean disableSpeculativeOptimizations() {
        return LLVMOptions.getParsedProperty(Property.OPTIMIZATIONS_DISABLE_SPECULATIVE);
    }

    public static boolean lifeTimeAnalysisEnabled() {
        return LLVMOptions.getParsedProperty(Property.OPTIMIZATION_LIFE_TIME_ANALYSIS);
    }

    public static boolean printLifeTimeAnalysis() {
        return lifeTimeAnalysisEnabled() && (boolean) LLVMOptions.getParsedProperty(Property.LIFE_TIME_ANALYSIS_STATS);
    }

    public static boolean launchRemoteTestCasesAsLocal() {
        return LLVMOptions.getParsedProperty(Property.REMOTE_TEST_CASES_AS_LOCAL);
    }

    public static boolean printPerformanceWarnings() {
        return LLVMOptions.getParsedProperty(Property.PRINT_PERFORMANCE_WARNINGS);
    }

    public static int getInlineCacheSize() {
        return LLVMOptions.getParsedProperty(Property.OPTIMIZATION_INLINE_CACHE_SIZE);
    }

    public static int getExecutionCount() {
        return LLVMOptions.getParsedProperty(Property.EXECUTION_COUNT);
    }

    public static boolean valueProfileFunctionArgs() {
        return LLVMOptions.getParsedProperty(Property.OPTIMIZATION_VALUE_PROFILE_FUNCTION_ARGS);
    }

    public static boolean performanceWarningsAreFatal() {
        return LLVMOptions.getParsedProperty(Property.PERFORMANCE_WARNING_ARE_FATAL);
    }

    public static String[] getDynamicBitcodeLibraries() {
        return LLVMOptions.getParsedProperty(Property.DYN_BITCODE_LIBRARIES);
    }

}
