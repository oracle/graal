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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.llvm.tests.options.TestOptions;

public final class FixtureUtils {

    public static final String COMMENT_LINE_PREFIX = "#";

    public static Collection<Object[]> getFixtureObjects(Class<?> testSuiteClass, Path suitesPath, Predicate<? super Path> predicate) {
        try {
            Map<String, String> excludedTests = getExcludedTests(testSuiteClass);
            Stream<Path> destDirs = Files.walk(suitesPath).filter(predicate).map(Path::getParent);
            return destDirs.map(testPath -> {
                String testCaseName = getTestCaseName(suitesPath, testPath);
                return new Object[]{testPath, testCaseName, excludedTests.get(testCaseName)};
            }).collect(Collectors.toList());
        } catch (IOException e) {
            throw new AssertionError("Test cases not found", e);
        }
    }

    private static String getTestCaseName(Path suitesPath, Path testPath) {
        return suitesPath.relativize(testPath).toString();
    }

    /**
     * Returns a map from excluded test to the exclude file that caused the exclusion.
     */
    private static Map<String, String> getExcludedTests(Class<?> testSuiteClass) {
        try {
            Path excludeDirectory = Paths.get(TestOptions.CONFIG_ROOT, testSuiteClass.getSimpleName());
            Map<String, String> excludeTestToFile = new HashMap<>();
            // walk all exclude files in the exclude directory
            Files.walk(excludeDirectory).filter(path -> path.toString().endsWith(".exclude")).forEach(path -> {
                // add all entries in the exclude file the the map
                String excludeFile = Paths.get(TestOptions.CONFIG_ROOT).relativize(path).toString();
                readAllLines(path).forEach(excludeTest -> {
                    String oldReason = excludeTestToFile.get(excludeTest);
                    excludeTestToFile.put(excludeTest, oldReason == null ? excludeFile : oldReason + ", " + excludeFile);
                });
            });
            return excludeTestToFile;
        } catch (IOException e) {
            return Collections.emptyMap();
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
