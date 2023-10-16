/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.options;

import com.oracle.truffle.llvm.tests.services.TestEngineConfig;
import org.junit.Assume;

public final class TestOptions {
    public static final String TEST_DISCOVERY_PATH = System.getProperty("sulongtest.testDiscoveryPath");
    public static final String TEST_AOT_IMAGE = System.getProperty("sulongtest.testAOTImage");
    public static final String TEST_AOT_ARGS = System.getProperty("sulongtest.testAOTArgs");
    public static final String TEST_FILTER = System.getProperty("sulongtest.testFilter");
    public static final String TEST_NAME_FILTER = System.getProperty("sulongtest.testNameFilter");
    public static final String PROJECT_ROOT = System.getProperty("sulongtest.projectRoot");
    public static final String CONFIG_ROOT = System.getProperty("sulongtest.configRoot");

    /**
     * Gets the path of an mx test distribution. The
     * {@link TestEngineConfig#getDistributionSuffix()} is added to the provided
     * {@code distribution} name.
     *
     * The properties are set in {@code mx_sulong} via (@code mx_unittest.add_config_participant}.
     */
    public static String getTestDistribution(String distribution) {
        TestEngineConfig config = TestEngineConfig.getInstance();
        String property = System.getProperty("sulongtest.path." + distribution + config.getDistributionSuffix());
        if (property == null) {
            throw new RuntimeException("Test distribution " + distribution + " does not exist for configuration " + config);
        }
        return property;
    }

    /**
     * Gets the path of a test source.
     *
     * The properties are manually set in {@code suite.py}.
     */
    public static String getSourcePath(String source) {
        String property = System.getProperty("sulongtest.source." + source);
        if (property == null) {
            throw new RuntimeException("Test sources does not exist: " + source);
        }
        return property;
    }

    /**
     * Assumption that the tests have been compiled with the bundled LLVM version.
     */
    public static void assumeBundledLLVM() {
        Assume.assumeTrue("Environment variable 'CLANG_CC' is set but project specifies 'bundledLLVMOnly'", System.getenv("CLANG_CC") == null);
    }
}
