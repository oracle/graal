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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class LLVMOptions {

    public static void main(String[] args) {
        for (PropertyCategory category : PropertyCategory.values()) {
            List<Property> properties = category.getProperties();
            if (!properties.isEmpty()) {
                // Checkstyle: stop
                System.out.println(category + ":");
                for (Property prop : properties) {
                    System.out.println(prop);
                }
                System.out.println();
                // Checkstyle: resume
            }

        }
    }

    private static final String PATH_DELIMITER = ":";
    private static final String OPTION_PREFIX = "sulong.";
    private static final String OBSOLETE_OPTION_PREFIX = "llvm.";

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

    static int parseInteger(Property prop) {
        return Integer.parseInt(System.getProperty(prop.getKey(), prop.getDefaultValue()));
    }

    static String[] parseDynamicLibraryPath(Property prop) {
        String property = System.getProperty(prop.getKey(), prop.getDefaultValue());
        if (property == null) {
            return new String[0];
        } else {
            return property.split(PATH_DELIMITER);
        }
    }

    public enum PropertyCategory {
        GENERAL,
        DEBUG,
        PERFORMANCE,
        TESTS,
        MX;

        public List<Property> getProperties() {
            List<Property> properties = new ArrayList<>();
            for (Property p : Property.values()) {
                if (this == p.getCategory()) {
                    properties.add(p);
                }
            }
            return properties;
        }
    }

    public enum Property {

        DEBUG("Debug", "Turns debugging on/off", "false", LLVMOptions::parseBoolean, PropertyCategory.DEBUG),
        PRINT_FUNCTION_ASTS("PrintASTs", "Prints the Truffle ASTs for the parsed functions", "false", LLVMOptions::parseBoolean, PropertyCategory.DEBUG),
        EXECUTION_COUNT("ExecutionCount", "Execute each program for as many times as specified by this option", "1", LLVMOptions::parseInteger, PropertyCategory.DEBUG),
        /*
         * The boot classpath that should be used to execute the remote JVM when executing the LLVM
         * test suite (and other tests). These rely on comparing output sent to stdout that cannot
         * becaptured inside Java, since, e.g., a printf is executed by native code. To determine
         * the right value just copy the boot class path that you use to launch the main LLVM class"
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
                        "false",
                        LLVMOptions::parseBoolean,
                        PropertyCategory.TESTS),
        TEST_DISCOVERY_PATH(
                        "TestDiscoveryPath",
                        "Looks for newly supported test cases in the specified path. E.g., when executing the GCC test cases you can use /gcc.c-torture/execute to discover newly working torture test cases.",
                        null,
                        LLVMOptions::parseString,
                        PropertyCategory.TESTS),
        DYN_LIBRARY_PATHS("DynamicNativeLibraryPath", "The native library search paths delimited by " + PATH_DELIMITER, null, LLVMOptions::parseDynamicLibraryPath, PropertyCategory.GENERAL),
        PROJECT_ROOT("ProjectRoot", "Overrides the root of the project. This option exists to set the project root from mx", ".", LLVMOptions::parseString, PropertyCategory.MX),
        OPTIMIZATIONS_DISABLE_SPECULATIVE(
                        "DisableSpeculativeOptimizations",
                        "Disables all speculative optimizations regardless if they would be enabled otherwise",
                        "false",
                        LLVMOptions::parseBoolean,
                        PropertyCategory.PERFORMANCE),
        OPTIMIZATION_SPECIALIZE_EXPECT_INTRINSIC("SpecializeExpectIntrinsic", "Specialize the llvm.expect intrinsic", "true", LLVMOptions::parseBoolean, PropertyCategory.PERFORMANCE),
        OPTIMIZATION_VALUE_PROFILE_MEMORY_READS("ValueProfileMemoryReads", "Enable value profiling for memory reads", "true", LLVMOptions::parseBoolean, PropertyCategory.PERFORMANCE),
        OPTIMIZATION_INJECT_PROBS_SELECT("InjectProbabilitySelect", "Inject branch probabilities for select", "true", LLVMOptions::parseBoolean, PropertyCategory.PERFORMANCE),
        OPTIMIZATION_INTRINSIFY_C_FUNCTIONS("IntrinsifyCFunctions", "Substitute C functions by Java equivalents where possible", "true", LLVMOptions::parseBoolean, PropertyCategory.PERFORMANCE),
        OPTIMIZATION_INJECT_PROBS_COND_BRANCH("InjectProbabilityBr", "Inject branch probabilities for conditional branches", "true", LLVMOptions::parseBoolean, PropertyCategory.PERFORMANCE),
        OPTIMIZATION_LIFE_TIME_ANALYSIS(
                        "EnableLifetimeAnalysis",
                        "Performs a lifetime analysis to set dead frame slots to null to assist the PE",
                        "true",
                        LLVMOptions::parseBoolean,
                        PropertyCategory.PERFORMANCE),
        NATIVE_CALL_STATS("PrintNativeCallStats", "Outputs stats about native call site frequencies", "false", LLVMOptions::parseBoolean, PropertyCategory.DEBUG),
        LIFE_TIME_ANALYSIS_STATS("PrintNativeAnalysisStats", "Outputs the results of the lifetime analysis (if enabled)", "false", LLVMOptions::parseBoolean, PropertyCategory.DEBUG);

        Property(String key, String description, String defaultValue, OptionParser parser, PropertyCategory category) {
            this.key = OPTION_PREFIX + key;
            this.description = description;
            this.defaultValue = defaultValue;
            this.parser = parser;
            this.category = category;
        }

        private final String key;
        private final String description;
        private final String defaultValue;
        private final OptionParser parser;
        private final PropertyCategory category;

        public String getKey() {
            return key;
        }

        public String getDescription() {
            return description;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public PropertyCategory getCategory() {
            return category;
        }

        public Object parse() {
            return parser.parse(this);
        }

        private static final String FORMAT_STRING = "%40s (default = %5s) %s";

        @Override
        public String toString() {
            return String.format(FORMAT_STRING, getKey(), getDefaultValue(), getDescription());
        }

        public static Property fromKey(String key) {
            for (Property p : values()) {
                if (p.getKey().equals(key)) {
                    return p;
                }
            }
            return null;
        }

    }

    private static Map<Property, Object> parsedProperties = new HashMap<>();

    static {
        parseOptions();
        checkForInvalidOptionNames();
        checkForObsoleteOptionPrefix();
    }

    private static void checkForInvalidOptionNames() {
        boolean wrongOptionName = false;
        Properties allProperties = System.getProperties();
        for (String key : allProperties.stringPropertyNames()) {
            if (key.startsWith(OPTION_PREFIX)) {
                if (Property.fromKey(key) == null) {
                    wrongOptionName = true;
                    // Checkstyle: stop
                    System.err.println(key + " is an invalid option!");
                    // Checkstyle: resume
                }
            }
        }
        if (wrongOptionName) {
            // Checkstyle: stop
            System.err.println("\nvalid options:");
            // Checkstyle: resume
            printOptions();
            System.exit(-1);
        }
    }

    private static void printOptions() {
        LLVMOptions.main(new String[0]);
    }

    private static void checkForObsoleteOptionPrefix() {
        Properties allProperties = System.getProperties();
        for (String key : allProperties.stringPropertyNames()) {
            if (key.startsWith(OBSOLETE_OPTION_PREFIX)) {
                // Checkstyle: stop
                System.err.println("The prefix '" + OBSOLETE_OPTION_PREFIX + "' in option '" + key + "' is an obsolete option prefix and has been replaced by the prefix '" + OPTION_PREFIX + "':");
                // Checkstyle: resume
                printOptions();
                System.exit(-1);
            }
        }
    }

    private static void parseOptions() {
        Property[] properties = Property.values();
        for (Property prop : properties) {
            parsedProperties.put(prop, prop.parse());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getParsedProperty(Property property) {
        return (T) parsedProperties.get(property);
    }

    public static boolean debugEnabled() {
        return getParsedProperty(Property.DEBUG);
    }

    public static boolean printFunctionASTs() {
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

    public static boolean discoveryTestModeEnabled() {
        return getTestDiscoveryPath() != null;
    }

    public static String[] getDynamicLibraryPaths() {
        return getParsedProperty(Property.DYN_LIBRARY_PATHS);
    }

    public static String getProjectRoot() {
        return getParsedProperty(Property.PROJECT_ROOT);
    }

    public static boolean specializeForExpectIntrinsic() {
        return !disableSpeculativeOptimizations() && (boolean) getParsedProperty(Property.OPTIMIZATION_SPECIALIZE_EXPECT_INTRINSIC);
    }

    public static boolean valueProfileMemoryReads() {
        return !disableSpeculativeOptimizations() && (boolean) getParsedProperty(Property.OPTIMIZATION_VALUE_PROFILE_MEMORY_READS);
    }

    public static boolean injectBranchProbabilitiesForSelect() {
        return !disableSpeculativeOptimizations() && (boolean) getParsedProperty(Property.OPTIMIZATION_INJECT_PROBS_SELECT);
    }

    public static boolean intrinsifyCLibraryFunctions() {
        return getParsedProperty(Property.OPTIMIZATION_INTRINSIFY_C_FUNCTIONS);
    }

    public static boolean injectBranchProbabilitiesForConditionalBranch() {
        return !disableSpeculativeOptimizations() && (boolean) getParsedProperty(Property.OPTIMIZATION_INJECT_PROBS_COND_BRANCH);
    }

    public static boolean printNativeCallStats() {
        return getParsedProperty(Property.NATIVE_CALL_STATS);
    }

    public static boolean disableSpeculativeOptimizations() {
        return getParsedProperty(Property.OPTIMIZATIONS_DISABLE_SPECULATIVE);
    }

    public static boolean lifeTimeAnalysisEnabled() {
        return getParsedProperty(Property.OPTIMIZATION_LIFE_TIME_ANALYSIS);
    }

    public static boolean printLifeTimeAnalysis() {
        return lifeTimeAnalysisEnabled() && (boolean) getParsedProperty(Property.LIFE_TIME_ANALYSIS_STATS);
    }

    public static boolean launchRemoteTestCasesAsLocal() {
        return getParsedProperty(Property.REMOTE_TEST_CASES_AS_LOCAL);
    }

    public static int getExecutionCount() {
        return getParsedProperty(Property.EXECUTION_COUNT);
    }

}
