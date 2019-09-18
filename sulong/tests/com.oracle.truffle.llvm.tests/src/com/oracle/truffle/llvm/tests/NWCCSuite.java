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
package com.oracle.truffle.llvm.tests;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;

import org.junit.AfterClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.llvm.tests.options.TestOptions;

@RunWith(Parameterized.class)
public final class NWCCSuite extends BaseSuiteHarness {

    private static final Path NWCC_SUITE_DIR = new File(TestOptions.EXTERNAL_TEST_SUITE_PATH).toPath();
    private static final Path NWCC_SOURCE_DIR = new File(TestOptions.TEST_SOURCE_PATH).toPath();
    private static final Path NWCC_CONFIG_DIR = new File(TestOptions.TEST_CONFIG_PATH).toPath();

    @Parameter(value = 0) public Path path;
    @Parameter(value = 1) public String testName;

    @Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        return collectTestCases(NWCC_CONFIG_DIR, NWCC_SUITE_DIR, NWCC_SOURCE_DIR);
    }

    @Override
    protected Path getTestDirectory() {
        return path;
    }

    @AfterClass
    public static void printStatistics() {
        printStatistics("NWCC", NWCC_SOURCE_DIR, NWCC_CONFIG_DIR, f -> true);
    }

    @Override
    protected String getTestName() {
        return testName;
    }
}
