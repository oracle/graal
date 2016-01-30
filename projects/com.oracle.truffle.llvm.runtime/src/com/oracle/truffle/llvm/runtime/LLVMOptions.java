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

    public enum Property {

        DEBUG("llvm-debug", "Turns debugging on/off", "false"),
        PRINT_FUNCTION_ASTS("llvm-print-asts", "Prints the Truffle ASTs for the parsed functions", "false"),
        /*
         * The boot classpath that should be used to execute the remote JVM when executing the LLVM
         * test suite (and other tests). These rely on comparing output sent to stdout that cannot
         * becaptured inside Java, since, e.g., a printf is executed by native code. To determine
         * the right value just copy the boot class path that you use to launch the main LLVM class"
         */
        REMOTE_TEST_BOOT_CLASSPATH_KEY("llvm-test-boot", "The boot classpath for the remote JVM used to capture native printf and other output ", null),
        GCC_TEST_DISCOVERY_PATH_KEY("llvm-test-gcc-discovery", "Looks for newly supported GCC test cases in the specified path.", null),
        LLVM_TEST_DISCOVER_PATH("llvm-test-llvm-discovery", "Looks for newly supported LLVM test cases in the specified path.", null),
        NWCC_TEST_DISCOVER_PATH("llvm-test-nwcc-discovery", "Looks for newly supported NWCC test cases in the specified path.", null),
        DYN_LIBRARY_PATHS("llvm-dyn-libs", "The native library search paths delimited by " + PATH_DELIMITER, null),
        PROJECT_ROOT_KEY("llvm-root", "Overrides the root of the LLVM project. This option exists to set the project root from mx", "."),
        IS_GATE("llvm-gate", "Tell Sulong that the gate is executing", "false"),
        PRINT_RET_VAL("llvm-print-retval", "Prints the return value of an execution", "true");

        Property(String key, String description, String defaultValue) {
            this.key = key;
            this.description = description;
            this.defaultValue = defaultValue;
        }

        private final String key;
        private final String description;
        private final String defaultValue;

        public String getKey() {
            return key;
        }

        public String getDescription() {
            return description;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        private static final String FORMAT_STRING = "%25s (default = %5s) %s";

        @Override
        public String toString() {
            return String.format(FORMAT_STRING, getKey(), getDefaultValue(), getDescription());
        }

    }

    private static final boolean LLVM_DEBUG;
    private static final boolean LLVM_PRINT_FUNCTION_ASTS;
    private static final String LLVM_REMOTE_TEST_BOOT_CLASSPATH;
    private static final String LLVM_GCC_TEST_DISCOVERY_PATH;
    private static final String NWCC_TEST_DISCOVER_PATH;
    private static final String LLVM_TEST_DISCOVERY_PATH;
    private static final String[] DYN_LIBRARY_PATHS;
    private static final String LLVM_PROJECT_ROOT;
    private static final boolean LLVM_IS_GATE;
    private static final boolean IS_PRINT_RET_VAL;

    static boolean parseBoolean(Property prop) {
        return Boolean.parseBoolean(System.getProperty(prop.getKey(), prop.getDefaultValue()));
    }

    static String parseString(Property prop) {
        return System.getProperty(prop.getKey(), prop.getDefaultValue());
    }

    static {
        LLVM_DEBUG = parseBoolean(Property.DEBUG);
        LLVM_PRINT_FUNCTION_ASTS = parseBoolean(Property.PRINT_FUNCTION_ASTS);
        LLVM_REMOTE_TEST_BOOT_CLASSPATH = parseString(Property.REMOTE_TEST_BOOT_CLASSPATH_KEY);
        LLVM_GCC_TEST_DISCOVERY_PATH = parseString(Property.GCC_TEST_DISCOVERY_PATH_KEY);
        NWCC_TEST_DISCOVER_PATH = parseString(Property.NWCC_TEST_DISCOVER_PATH);
        LLVM_TEST_DISCOVERY_PATH = parseString(Property.LLVM_TEST_DISCOVER_PATH);
        String dynamicLibraries = parseString(Property.DYN_LIBRARY_PATHS);
        DYN_LIBRARY_PATHS = dynamicLibraries == null ? null : dynamicLibraries.split(PATH_DELIMITER);
        LLVM_PROJECT_ROOT = parseString(Property.PROJECT_ROOT_KEY);
        LLVM_IS_GATE = parseBoolean(Property.IS_GATE);
        IS_PRINT_RET_VAL = parseBoolean(Property.PRINT_RET_VAL);
    }

    public static boolean isDebug() {
        return LLVM_DEBUG;
    }

    public static boolean isPrintFunctionAsts() {
        return LLVM_PRINT_FUNCTION_ASTS;
    }

    public static String getRemoteTestBootClassPath() {
        if (LLVM_REMOTE_TEST_BOOT_CLASSPATH == null) {
            throw new AssertionError();
        }
        return LLVM_REMOTE_TEST_BOOT_CLASSPATH;
    }

    public static String getGCCTestDiscoveryPath() {
        return LLVM_GCC_TEST_DISCOVERY_PATH;
    }

    public static boolean isDiscoveryTestMode() {
        return getGCCTestDiscoveryPath() != null || getLLVMTestDiscoveryPath() != null || getNWCCDiscoveryPath() != null;
    }

    public static String getLLVMTestDiscoveryPath() {
        return LLVM_TEST_DISCOVERY_PATH;
    }

    public static String getNWCCDiscoveryPath() {
        return NWCC_TEST_DISCOVER_PATH;
    }

    public static String[] getDynamicLibraryPaths() {
        return DYN_LIBRARY_PATHS;
    }

    public static String getProjectRoot() {
        return LLVM_PROJECT_ROOT;
    }

    public static boolean isGate() {
        return LLVM_IS_GATE;
    }

    public static boolean isPrintRetVal() {
        return IS_PRINT_RET_VAL;
    }

}
