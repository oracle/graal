/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

public final class TestOptions {
    public static final String[] FILE_EXTENSION_FILTER = getFileExtensions();
    public static final String TEST_DISCOVERY_PATH = System.getProperty("sulongtest.testDiscoveryPath");
    public static final String TEST_AOT_IMAGE = System.getProperty("sulongtest.testAOTImage");
    public static final String TEST_AOT_ARGS = System.getProperty("sulongtest.testAOTArgs");
    public static final String TEST_FILTER = System.getProperty("sulongtest.testFilter");
    public static final String PROJECT_ROOT = System.getProperty("sulongtest.projectRoot");
    public static final String TEST_SUITE_PATH = System.getProperty("sulongtest.testSuitePath");
    public static final String LL_TEST_SUITE_PATH = System.getProperty("sulongtest.llTestSuitePath");
    public static final String EXTERNAL_TEST_SUITE_PATH = System.getProperty("sulongtest.externalTestSuitePath");
    public static final String TEST_SOURCE_PATH = System.getProperty("sulongtest.testSourcePath");
    public static final String TEST_CONFIG_PATH = System.getProperty("sulongtest.testConfigPath");

    private static String[] getFileExtensions() {
        String property = System.getProperty("sulongtest.fileExtensionFilter");
        if (property != null && property.length() > 0) {
            return property.split(":");
        } else {
            return new String[0];
        }
    }
}
