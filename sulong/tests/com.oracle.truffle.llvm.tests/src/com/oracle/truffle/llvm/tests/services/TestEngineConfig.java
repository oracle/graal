/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.services;

import com.oracle.truffle.llvm.tests.pipe.CaptureOutput;
import com.oracle.truffle.llvm.tests.util.ProcessUtil;
import org.graalvm.polyglot.Context;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Configuration for an engine used for running unit tests.
 */
public interface TestEngineConfig extends Comparable<TestEngineConfig> {

    String TEST_ENGINE_CONFIG_PROPERTY_NAME = "sulongtest.config";

    /**
     * Gets the single global {@link TestEngineConfig} instance. Available configurations are
     * discovered using {@link java.util.ServiceLoader#load}. The
     * {@link #TEST_ENGINE_CONFIG_PROPERTY_NAME} property can be used to select a configuration
     * based on its {@link #getName() name}. If the property is not set, the config with the
     * <em>lowest</em> {@link #getPriority() priority} is selected.
     */
    static TestEngineConfig getInstance() {
        return TestEngineConfigBase.getInstance();
    }

    /**
     * Name of the current test engine configuration.
     */
    String getName();

    /**
     * If no config has explicitly selected, the one with with lowest priority is used.
     */
    int getPriority();

    /**
     * Suffix for mx distributions that can be used with this configuration.
     *
     * @see com.oracle.truffle.llvm.tests.options.TestOptions#getTestDistribution
     */
    String getDistributionSuffix();

    /**
     * Context options required by this test engine configuration.
     */
    default Map<String, String> getContextOptions() {
        return getContextOptions(null);
    }

    default Map<String, String> getEngineOptions() {
        return new HashMap<>();
    }

    Map<String, String> getContextOptions(String testName);

    /**
     * A filter to decide whether this configuration can execute the given test case.
     */
    boolean canExecute(Path path);

    Function<Context.Builder, CaptureOutput> getCaptureOutput();

    /**
     * Used for storing AOT auxiliary images of tests.
     */
    default boolean evaluateSourceOnly() {
        return false;
    }

    default boolean runReference() {
        return true;
    }

    default boolean runCandidate() {
        return true;
    }

    default ProcessUtil.ProcessResult filterCandidateProcessResult(ProcessUtil.ProcessResult candidateResult) {
        return candidateResult;
    }

    default String getConfigFolderName() {
        return getName();
    }
}
