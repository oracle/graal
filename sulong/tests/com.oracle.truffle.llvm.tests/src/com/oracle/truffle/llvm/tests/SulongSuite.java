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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.llvm.tests.options.TestOptions;

@RunWith(Parameterized.class)
public class SulongSuite extends BaseSuiteHarness {

    public static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().indexOf("mac") >= 0;
    @Parameter(value = 0) public Path path;
    @Parameter(value = 1) public String testName;

    @Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        Path suitesPath = new File(TestOptions.TEST_SUITE_PATH).toPath();
        return getData(suitesPath);
    }

    protected static Collection<Object[]> getData(Path suitesPath) {
        try (Stream<Path> files = Files.walk(suitesPath)) {
            Stream<Path> destDirs = files.filter(SulongSuite::isReference).map(Path::getParent);
            return destDirs.map(testPath -> new Object[]{testPath, suitesPath.relativize(testPath).toString()}).collect(Collectors.toList());
        } catch (IOException e) {
            throw new AssertionError("Test cases not found", e);
        }
    }

    private static boolean isReference(Path path) {
        return path.endsWith("ref.out") && (!IS_MAC || pathStream(path).noneMatch(p -> p.endsWith("ref.out.dSYM")));
    }

    private static Stream<Path> pathStream(Path path) {
        return StreamSupport.stream(path.spliterator(), false);
    }

    @Override
    protected Predicate<? super Path> getIsSulongFilter() {
        return f -> {
            boolean isBC = f.getFileName().toString().endsWith(".bc");
            boolean isOut = f.getFileName().toString().endsWith(".out");
            return isBC || (isOut && !IS_MAC);
        };
    }

    @Override
    protected Path getTestDirectory() {
        return path;
    }

    @Override
    protected String getTestName() {
        return testName;
    }
}
