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
package com.oracle.truffle.llvm.test.alpha;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.test.options.TestOptions;

@RunWith(Parameterized.class)
public final class ParserTortureSuite {

    private static final Path GCC_SUITE_DIR = new File(TestOptions.PROJECT_ROOT + "/../cache/tests/gcc.c-torture").toPath();
    private static final Path GCC_CONFIG_DIR = new File(TestOptions.PROJECT_ROOT + "/../tests/gcc/compileConfigs").toPath();

    @Parameter(value = 0) public Path path;
    @Parameter(value = 1) public String testName;

    @Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        return BaseTestHarness.collectRegularRun(GCC_CONFIG_DIR, GCC_SUITE_DIR);
    }

    @Test
    public void test() throws Exception {
        List<Path> testCandidates = Files.walk(path).filter(BaseTestHarness.isFile).filter(BaseTestHarness.isSulong).collect(Collectors.toList());
        for (Path candidate : testCandidates) {

            if (!candidate.toAbsolutePath().toFile().exists()) {
                throw new AssertionError("File " + candidate.toAbsolutePath().toFile() + " does not exist.");
            }

            try {
                Engine engine = Engine.create();
                PolyglotContext context = engine.createPolyglotContext();
                context.eval(LLVMLanguage.NAME, org.graalvm.polyglot.Source.create(candidate.toFile()));
            } catch (Throwable e) {
                throw e;
            }
        }

    }

}
