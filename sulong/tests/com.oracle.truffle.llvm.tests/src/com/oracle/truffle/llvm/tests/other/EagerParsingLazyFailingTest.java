/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.other;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.StringContains;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.llvm.tests.CommonTestUtils;
import com.oracle.truffle.llvm.tests.Platform;
import com.oracle.truffle.llvm.tests.options.TestOptions;

public class EagerParsingLazyFailingTest {

    @Before
    public void bundledLLVMOnly() {
        TestOptions.assumeBundledLLVM();
    }

    private static final Path TEST_DIR = new File(TestOptions.getTestDistribution("SULONG_EMBEDDED_TEST_SUITES"), "other").toPath();
    private static final String FILENAME = "bitcode-O0.bc";

    private static final class Runner implements AutoCloseable {
        private final String testName;
        private final Context context;

        private Value library;

        Runner(String testName) {
            this(testName, Collections.emptyMap());
        }

        Runner(String testName, Map<String, String> options) {
            this.testName = testName;
            Builder builder = Context.newBuilder();
            if (!Platform.isLinux() || !Platform.isAMD64()) {
                // ignore target triple
                CommonTestUtils.disableBitcodeVerification(builder);
            }
            this.context = builder.options(options).allowAllAccess(true).build();
            this.library = null;
        }

        @Override
        public void close() {
            context.close();
        }

        Value load() {
            if (library == null) {
                try {
                    File file = TEST_DIR.resolve(testName + CommonTestUtils.TEST_DIR_EXT).resolve(FILENAME).toFile();
                    Source source = Source.newBuilder("llvm", file).build();
                    library = context.eval(source);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return library;
        }
    }

    private static HashMap<String, String> eagerParsingOptions() {
        HashMap<String, String> options = new HashMap<>();
        options.put("llvm.lazyParsing", "false");
        return options;
    }

    @Test
    public void unsupportedInlineAsmNotExecuted() {
        try (Runner runner = new Runner("unsupported_inline_asm.ll")) {
            Assert.assertEquals(2, runner.load().invokeMember("run", 0).asInt());
        }
    }

    @Test
    public void unsupportedInlineAsmExecuted() {
        try (Runner runner = new Runner("unsupported_inline_asm.ll")) {
            PolyglotException exception = Assert.assertThrows(PolyglotException.class, () -> runner.load().invokeMember("run", 4).asInt());
            MatcherAssert.assertThat(exception.getMessage(), StringContains.containsString("Unsupported operation"));
        }
    }

    @Test
    public void unsupportedInlineAsmEagerParsingNotExecuted() {
        try (Runner runner = new Runner("unsupported_inline_asm.ll", eagerParsingOptions())) {
            Assert.assertEquals(2, runner.load().invokeMember("run", 0).asInt());
        }
    }

    @Test
    public void unsupportedInlineAsmEagerParsingExecuted() {
        try (Runner runner = new Runner("unsupported_inline_asm.ll", eagerParsingOptions())) {
            PolyglotException exception = Assert.assertThrows(PolyglotException.class, () -> runner.load().invokeMember("run", 4).asInt());
            MatcherAssert.assertThat(exception.getMessage(), StringContains.containsString("Unsupported operation"));
        }
    }
}
