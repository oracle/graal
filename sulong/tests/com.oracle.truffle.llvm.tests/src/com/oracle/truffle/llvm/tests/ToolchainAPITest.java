/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.llvm.tests.pipe.CaptureNativeOutput;
import com.oracle.truffle.llvm.tests.pipe.CaptureOutput;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.llvm.api.Toolchain;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.tck.TruffleRunner;

public class ToolchainAPITest {
    @Rule public TruffleRunner.RunWithPolyglotRule runWithPolyglot = new TruffleRunner.RunWithPolyglotRule(getContextBuilder());

    private static final String TOOLCHAIN_PATH_PATTERN_NAME = "sulongtest.toolchainPathPattern";
    private static final String TOOLCHAIN_PATH_PATTERN = System.getProperty(TOOLCHAIN_PATH_PATTERN_NAME);

    protected Context.Builder getContextBuilder() {
        return Context.newBuilder().allowAllAccess(true);
    }

    protected Value load(File file) {
        try {
            Source source = Source.newBuilder(LLVMLanguage.ID, file).build();
            return runWithPolyglot.getPolyglotContext().eval(source);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Toolchain getToolchain() {
        TruffleLanguage.Env env = runWithPolyglot.getTruffleTestEnv();
        LanguageInfo llvmInfo = env.getInternalLanguages().get("llvm");
        return env.lookup(llvmInfo, Toolchain.class);
    }

    protected int compile(String tool, File src, File dst, String... args) throws IOException, InterruptedException {
        Toolchain toolchain = getToolchain();
        TruffleFile clang = toolchain.getToolPath(tool);
        Assume.assumeTrue("Tool '" + tool + "' is not supported by " + toolchain, clang != null);
        if (TOOLCHAIN_PATH_PATTERN != null) {
            Assert.assertTrue(String.format("Toolchain path ('%s') does not contain '%s'='%s'", clang, TOOLCHAIN_PATH_PATTERN_NAME, TOOLCHAIN_PATH_PATTERN),
                            clang.toString().contains(TOOLCHAIN_PATH_PATTERN));
        }
        List<String> allArgs = new ArrayList<>(Arrays.asList(clang.toString(), src.getAbsolutePath(), "-o", dst.getAbsolutePath()));
        allArgs.addAll(Arrays.asList(args));
        Process p = runWithPolyglot.getTruffleTestEnv().newProcessBuilder(allArgs.toArray(new String[0])).inheritIO(true).start();
        p.waitFor();
        return p.exitValue();
    }

    protected static void write(File src, String text) throws IOException {
        FileWriter fw = new FileWriter(src);
        fw.write(text);
        fw.close();
    }

    protected static final String HELLO_WORLD_C = "#include <stdio.h>\nint main() {\n  printf(\"Hello World!\");\n  return 0;\n}";

    @Test
    public void testCC() throws IOException, InterruptedException {
        File src = File.createTempFile(ToolchainAPITest.class.getSimpleName(), ".c");
        src.deleteOnExit();
        File dst = File.createTempFile(ToolchainAPITest.class.getSimpleName(), ".out");
        dst.deleteOnExit();
        write(src, HELLO_WORLD_C);
        int compileResult = compile("CC", src, dst);
        Assert.assertEquals("compiler result", 0, compileResult);
        try (CaptureOutput out = new CaptureNativeOutput()) {
            int runResult = load(dst).execute().asInt();
            Assert.assertEquals("run result", 0, runResult);
            Assert.assertEquals("Hello World!", out.getStdOut());
        }
    }

    protected static final String HELLO_WORLD_CXX = "#include <iostream>\nint main() {\n  std::cout << \"Hello World!\";\n  return 0;\n}";

    @Test
    public void testCXX() throws IOException, InterruptedException {
        File src = File.createTempFile(ToolchainAPITest.class.getSimpleName(), ".cpp");
        src.deleteOnExit();
        File dst = File.createTempFile(ToolchainAPITest.class.getSimpleName(), ".out");
        dst.deleteOnExit();
        write(src, HELLO_WORLD_CXX);
        int compileResult = compile("CXX", src, dst);
        Assert.assertEquals("compiler result", 0, compileResult);
        try (CaptureOutput out = new CaptureNativeOutput()) {
            int runResult = load(dst).execute().asInt();
            Assert.assertEquals("run result", 0, runResult);
            Assert.assertEquals("Hello World!", out.getStdOut());
        }
    }
}
