/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.graalvm.polyglot.Context;

import com.oracle.truffle.llvm.tests.pipe.CaptureNativeOutput;
import com.oracle.truffle.llvm.tests.pipe.CaptureOutput;

public abstract class TestEngineConfigBase implements TestEngineConfig {

    private static class Instance {
        private static final TestEngineConfig INSTANCE = getInstance();

        private static TestEngineConfig getInstance() {
            final TestEngineConfig config;
            TestEngineConfig[] configs = StreamSupport.stream(ServiceLoader.load(TestEngineConfig.class).spliterator(), false).toArray(TestEngineConfig[]::new);
            String configName = System.getProperty(TestEngineConfig.TEST_ENGINE_CONFIG_PROPERTY_NAME);
            if (configName != null) {
                try {
                    config = Arrays.stream(configs).filter(c -> configName.equals(c.getName())).findFirst().get();
                } catch (NoSuchElementException e) {
                    throw new IllegalArgumentException("No " + TestEngineConfig.class.getSimpleName() + " with name " + configName + " found! (Known configs are " +
                                    Arrays.stream(configs).sorted().map(TestEngineConfig::getName).collect(Collectors.joining(", ")) + ")");
                }
            } else {
                try {
                    config = Arrays.stream(configs).sorted().findFirst().get();
                } catch (NoSuchElementException e) {
                    throw new IllegalArgumentException("No " + TestEngineConfig.class.getSimpleName() + " service found!");
                }
            }
            System.err.println("Using " + TestEngineConfig.class.getSimpleName() + " service " + config);
            return config;
        }

    }

    static TestEngineConfig getInstance() {
        return Instance.INSTANCE;
    }

    private final String canonicalName;

    public TestEngineConfigBase() {
        String simpleName = getClass().getSimpleName();
        String suffix = TestEngineConfig.class.getSimpleName();
        if (!simpleName.endsWith(suffix)) {
            throw new IllegalStateException(suffix + " implementation should have " + suffix + " as suffix (" + simpleName + ")");
        }
        canonicalName = simpleName.substring(0, simpleName.length() - suffix.length());
    }

    @Override
    public final int compareTo(TestEngineConfig o) {
        return getPriority() - o.getPriority();
    }

    @Override
    public String getName() {
        return canonicalName;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean canExecute(Path path) {
        return true;
    }

    @Override
    public Function<Context.Builder, CaptureOutput> getCaptureOutput() {
        return c -> new CaptureNativeOutput();
    }
}
