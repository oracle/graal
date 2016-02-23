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
package com.oracle.truffle.llvm.runtime;

import java.util.HashMap;
import java.util.Map;

public class LLVMOptions {

    public static void main(String[] args) {
        Property[] properties = Property.values();
        for (Property prop : properties) {
            // Checkstyle: stop
            System.out.println(prop);
            // Checkstyle: resume
        }
    }

    private static final String PATH_DELIMITER = ":";

    @FunctionalInterface
    interface OptionParser {
        Object parse(Property property);
    }

    static boolean parseBoolean(Property prop) {
        return Boolean.parseBoolean(System.getProperty(prop.getKey(), prop.getDefaultValue()));
    }

    static String parseString(Property prop) {
        return System.getProperty(prop.getKey(), prop.getDefaultValue());
    }

    static String[] parseDynamicLibraryPath(Property prop) {
        String property = System.getProperty(prop.getKey(), prop.getDefaultValue());
        if (property == null) {
            return new String[0];
        } else {
            return property.split(PATH_DELIMITER);
        }
    }

    public enum Property {

        DEBUG("llvm-debug", "Turns debugging on/off", "false", LLVMOptions::parseBoolean),
        PRINT_FUNCTION_ASTS("llvm-print-asts", "Prints the Truffle ASTs for the parsed functions", "false", LLVMOptions::parseBoolean),
        /*
         * The boot classpath that should be used to execute the remote JVM when executing the LLVM
         * test suite (and other tests). These rely on comparing output sent to stdout that cannot
         * becaptured inside Java, since, e.g., a printf is executed by native code. To determine
         * the right value just copy the boot class path that you use to launch the main LLVM class"
         */
        REMOTE_TEST_BOOT_CLASSPATH("llvm-test-boot", "The boot classpath for the remote JVM used to capture native printf and other output.", null, LLVMOptions::parseString),
        TEST_DISCOVERY_PATH(
                        "llvm-test-discovery",
                        "Looks for newly supported test cases in the specified path. E.g., when executing the GCC test cases you can use /gcc.c-torture/execute to discover newly working torture test cases.",
                        null,
                        LLVMOptions::parseString),
        DYN_LIBRARY_PATHS("llvm-dyn-libs", "The native library search paths delimited by " + PATH_DELIMITER, null, LLVMOptions::parseDynamicLibraryPath),
        PROJECT_ROOT("llvm-root", "Overrides the root of the LLVM project. This option exists to set the project root from mx", ".", LLVMOptions::parseString),
        OPTIMIZATION_SPECIALIZE_EXPECT_INTRINSIC("llvm-opt-expect", "Specialize the llvm.expect intrinsic", "true", LLVMOptions::parseBoolean),
        OPTIMIZATION_VALUE_PROFILE_MEMORY_READS("llvm-opt-valueprofiling", "Enable value profiling for memory reads", "true", LLVMOptions::parseBoolean),
        OPTIMIZATION_INJECT_PROBS_SELECT("llvm-opt-select", "Inject branch probabilities for select", "true", LLVMOptions::parseBoolean),
        OPTIMIZATION_INTRINSIFY_C_FUNCTIONS("llvm-opt-cintrinsics", "Substitute C functions by Java equivalents where possible", "true", LLVMOptions::parseBoolean),
        OPTIMIZATION_INJECT_PROBS_COND_BRANCH("llvm-opt-br", "Inject branch probabilities for conditional branches", "true", LLVMOptions::parseBoolean),
        NATIVE_CALL_STATS("llvm-native-call-stats", "Outputs stats about native call site frequencies", "false", LLVMOptions::parseBoolean),
        LIFE_TIME_ANALYSIS_STATS("llvm-lifetime-analysis-stats", "Outputs the results of the lifetime analysis", "false", LLVMOptions::parseBoolean);

        Property(String key, String description, String defaultValue, OptionParser parser) {
            this.key = key;
            this.description = description;
            this.defaultValue = defaultValue;
            this.parser = parser;
        }

        private final String key;
        private final String description;
        private final String defaultValue;
        private final OptionParser parser;

        public String getKey() {
            return key;
        }

        public String getDescription() {
            return description;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public Object parse() {
            return parser.parse(this);
        }

        private static final String FORMAT_STRING = "%25s (default = %5s) %s";

        @Override
        public String toString() {
            return String.format(FORMAT_STRING, getKey(), getDefaultValue(), getDescription());
        }
    }

    private static Map<Property, Object> parsedProperties = new HashMap<>();

    static {
        Property[] properties = Property.values();
        for (Property prop : properties) {
            parsedProperties.put(prop, prop.parse());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getParsedProperty(Property property) {
        return (T) parsedProperties.get(property);
    }

    public static boolean isDebug() {
        return getParsedProperty(Property.DEBUG);
    }

    public static boolean isPrintFunctionAsts() {
        return getParsedProperty(Property.PRINT_FUNCTION_ASTS);
    }

    public static String getRemoteTestBootClassPath() {
        if (getParsedProperty(Property.REMOTE_TEST_BOOT_CLASSPATH) == null) {
            throw new AssertionError();
        }
        return getParsedProperty(Property.REMOTE_TEST_BOOT_CLASSPATH);
    }

    public static String getTestDiscoveryPath() {
        return getParsedProperty(Property.TEST_DISCOVERY_PATH);
    }

    public static boolean isDiscoveryTestMode() {
        return getTestDiscoveryPath() != null;
    }

    public static String[] getDynamicLibraryPaths() {
        return getParsedProperty(Property.DYN_LIBRARY_PATHS);
    }

    public static String getProjectRoot() {
        return getParsedProperty(Property.PROJECT_ROOT);
    }

    public static boolean specializeForExpectIntrinsic() {
        return getParsedProperty(Property.OPTIMIZATION_SPECIALIZE_EXPECT_INTRINSIC);
    }

    public static boolean valueProfileMemoryReads() {
        return getParsedProperty(Property.OPTIMIZATION_VALUE_PROFILE_MEMORY_READS);
    }

    public static boolean injectBranchProbabilitiesForSelect() {
        return getParsedProperty(Property.OPTIMIZATION_INJECT_PROBS_SELECT);
    }

    public static boolean intrinsifyCLibraryFunctions() {
        return getParsedProperty(Property.OPTIMIZATION_INTRINSIFY_C_FUNCTIONS);
    }

    public static boolean injectBranchProbabilitiesForConditionalBranch() {
        return getParsedProperty(Property.OPTIMIZATION_INJECT_PROBS_COND_BRANCH);
    }

    public static boolean isNativeCallStats() {
        return getParsedProperty(Property.NATIVE_CALL_STATS);
    }

    public static boolean isNativeAnalysisStats() {
        return getParsedProperty(Property.LIFE_TIME_ANALYSIS_STATS);
    }

}
