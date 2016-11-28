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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.llvm.context.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;

@RunWith(Parameterized.class)
public final class ParserTortureSuite {

    private static final Path GCC_SUITE_DIR = new File(LLVMOptions.ENGINE.projectRoot() + "/../tests/cache/tests/gcc").toPath();
    private static final Path GCC_CONFIG_DIR = new File(LLVMOptions.ENGINE.projectRoot() + "/../tests/gcc/compileConfigs").toPath();

    @Parameter(value = 0) public Path path;
    @Parameter(value = 1) public String testName;

    @Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        return collectTestCases(GCC_CONFIG_DIR, GCC_SUITE_DIR);
    }

    @Test
    public void test() throws Exception {
        List<Path> testCandidates = Files.walk(path).filter(isFile).filter(isSulong).collect(Collectors.toList());
        for (Path candidate : testCandidates) {

            if (!candidate.toAbsolutePath().toFile().exists()) {
                throw new AssertionError("File " + candidate.toAbsolutePath().toFile() + " does not exist.");
            }

            try {
                Builder engineBuilder = PolyglotEngine.newBuilder();
                engineBuilder.config(LLVMLanguage.LLVM_BITCODE_MIME_TYPE, LLVMLanguage.PARSE_ONLY_KEY, true);
                PolyglotEngine build = engineBuilder.build();
                build.eval(Source.newBuilder(candidate.toFile()).build());
            } catch (Throwable e) {
                throw e;
            }
        }

    }

    protected static final Predicate<? super Path> isIncludeFile = f -> f.getFileName().toString().endsWith(".include");
    protected static final Predicate<? super Path> isSulong = f -> f.getFileName().toString().endsWith(".bc");
    protected static final Predicate<? super Path> isFile = f -> f.toFile().isFile();

    protected static Collection<Object[]> collectTestCases(Path configPath, Path suiteDir) throws AssertionError {
        Set<Path> whiteList = BaseTestHarness.getWhiteListTestFolders(configPath, suiteDir);
        Collection<Object[]> testCases = collectTestCases(suiteDir, whiteList::contains);
        Set<Path> collectedFiles = testCases.stream().map(a -> (Path) a[0]).collect(Collectors.toSet());
        Set<Path> missingTestCases = whiteList.stream().filter(p -> !collectedFiles.contains(p)).collect(Collectors.toSet());
        if (!missingTestCases.isEmpty()) {
            throw new AssertionError("The following tests are on the white list but not found:\n" + missingTestCases.stream().map(p -> p.toString()).collect(Collectors.joining("\n")));
        } else {
            System.err.println(String.format("Collected %d test folders.", testCases.size()));
        }
        return testCases;
    }

    private static Collection<Object[]> collectTestCases(Path suiteDir, Predicate<? super Path> whiteListFilter) throws AssertionError {
        try {
            return Files.walk(suiteDir).filter(isSulong).map(f -> f.getParent()).filter(whiteListFilter).map(f -> new Object[]{f, f.toString()}).collect(Collectors.toList());
        } catch (IOException e) {
            throw new AssertionError("Test cases not found", e);
        }
    }
}
