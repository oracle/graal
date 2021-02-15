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
package com.oracle.truffle.llvm.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.llvm.tests.options.TestOptions;

public final class FixtureUtils {

    public static final String COMMENT_LINE_PREFIX = "#";

    public static Collection<Object[]> getFixtureObjects(Class<?> testSuiteClass, Path suitesPath, Predicate<? super Path> predicate) {
        try {
            Set<String> excludedTests = getExcludedTests(testSuiteClass);
            Stream<Path> destDirs = Files.walk(suitesPath).filter(predicate).map(Path::getParent);
            return destDirs.filter(testPath -> !excludedTests.contains(
                            getTestCaseName(suitesPath, testPath))).map(testPath -> new Object[]{testPath, getTestCaseName(suitesPath, testPath)}).collect(Collectors.toList());
        } catch (IOException e) {
            throw new AssertionError("Test cases not found", e);
        }
    }

    private static String getTestCaseName(Path suitesPath, Path testPath) {
        return suitesPath.relativize(testPath).toString();
    }

    private static Set<String> getExcludedTests(Class<?> testSuiteClass) {
        try {
            Path excludeDirectory = Paths.get(TestOptions.CONFIG_ROOT, testSuiteClass.getSimpleName());
            return Files.walk(excludeDirectory).filter(path -> path.toString().endsWith(".exclude")).flatMap(path -> readAllLines(path)).collect(Collectors.toSet());
        } catch (IOException e) {
            return Collections.emptySet();
        }
    }

    private static Stream<String> readAllLines(Path path) {
        try {
            return Files.readAllLines(path).stream().filter(line -> !line.trim().isEmpty() && !line.startsWith(COMMENT_LINE_PREFIX));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
