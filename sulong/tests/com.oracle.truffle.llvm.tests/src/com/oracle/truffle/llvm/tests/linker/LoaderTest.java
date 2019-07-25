/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.linker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.llvm.tests.options.TestOptions;
import com.oracle.truffle.llvm.tests.pipe.CaptureNativeOutput;
import com.oracle.truffle.llvm.tests.pipe.CaptureOutput;

public class LoaderTest {
    protected static Function<Context.Builder, CaptureOutput> getCaptureOutput() {
        return c -> new CaptureNativeOutput();
    }

    private static final String SO_EXT = NFIContextExtension.getNativeLibrarySuffix();

    @Test
    public void test() throws IOException {
        try (Runner runner = new Runner()) {
            runner.load("reload/libA." + SO_EXT);
            runner.load("reload/libB." + SO_EXT);
            Assert.assertEquals("ctor a\nctor c\nctor b\n", runner.out.getStdOut());
        }
    }

    private static final Path TEST_DIR = new File(TestOptions.TEST_SUITE_PATH).toPath();

    protected static Map<String, String> getSulongTestLibContextOptions() {
        Map<String, String> map = new HashMap<>();
        String lib = System.getProperty("test.sulongtest.lib.path");
        map.put("llvm.libraryPath", lib);
        return map;
    }

    private static final class Runner implements AutoCloseable {
        private final Context context;
        private final CaptureOutput out;

        Runner() {
            Context.Builder builder = Context.newBuilder();
            this.out = getCaptureOutput().apply(builder);
            this.context = builder.options(getSulongTestLibContextOptions()).allowAllAccess(true).build();
        }

        @Override
        public void close() {
            context.close();
            try {
                out.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Value load(String testName) {
            Value library;
            try {
                File file = new File(TEST_DIR.toFile(), testName);
                Source source = Source.newBuilder("llvm", file).build();
                library = context.eval(source);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return library;
        }
    }
}
