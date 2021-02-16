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
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.llvm.tests.options.TestOptions;

public final class FixtureUtils {

    public static final String COMMENT_LINE_PREFIX = "#";

    public static final String OS = Platform.getOS().getDirectory();
    public static final String ARCH = Platform.getArchitecture().getDirectory();
    public static final String OTHERS = "others";

    /**
     * Collect test cases and exclusion reasons.
     *
     * This method has two responsibilities. First, it walks a directory and identifies <em>test
     * cases</em>. A <em>test case</em> is a <em>directory</em> which contains one or multiple
     * bitcode files and optionally a reference executable. The {@code predicate} identifies a test
     * case (the directory) by looking at the files in the directory. For example, if a test case
     * {@code myTest} contains the files {@code ref.out, O0.bc} and {@code O1.bc}, the
     * {@code predicate} might look for a file called {@code ref.out} to identify {@code myTest} as
     * a test case. Unless otherwise noted, the <em>test case</em> is relative to {@code suitePath}
     * and does not have a trailing directory separator ({@code /}).
     *
     * Second, the method collects excluded test cases based on a predefined exclude file structure.
     * Exclude files are files with a {@code .exclude} extension. Every line specifies a <em>test
     * case</em> (a directory) that should be excluded. Lines that start with {@code #} are ignored.
     * Exclude files are recursively search in the {@code testSuiteClass#getSimpleName()}
     * subdirectory of {@link TestOptions#CONFIG_ROOT}. The exclude file name (relative to
     * {@link TestOptions#CONFIG_ROOT}) is used as the exclude reason.
     *
     * The {@code os_arch} subdirectory is handled specially. It can be used to implement platform
     * specific excludes. This method recursivly looks for exclude files in
     * {@code os_arch/<os>/<arch>} directory ({@link Platform#getOS()},
     * {@link Platform#getArchitecture()}). If the {@code <os>} or {@code <arch>} directory does not
     * exist, the method looks for an {@code others} directory, which serves as an {@code else}
     * case.
     *
     * @param testSuiteClass the class for which the tests should be collected
     * @param suitesPath the path where the <em>compiled</em> test cases live
     * @param predicate a {@link Predicate} for identifying a test case
     *
     * @return a collection of {@link Object} arrays, each with three {@link String} elements: the
     *         absolute path to the test, the test name, and an exclude reason or {@code null} if
     *         there is none.
     */
    public static Collection<Object[]> getFixtureObjects(Class<?> testSuiteClass, Path suitesPath, Predicate<? super Path> predicate) {
        try {
            // collect excludes
            Map<String, String> excludedTests = getExcludedTests(testSuiteClass);
            // walk test cases
            return Files.walk(suitesPath).filter(predicate).map(Path::getParent).map(testPath -> {
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
            Path osArchDirectory = excludeDirectory.resolve("os_arch");
            FileVisitors visitors = new FileVisitors(osArchDirectory);

            // walk all exclude files in the base exclude directory (skipping special directories)
            Files.walkFileTree(excludeDirectory, visitors.skippingVisitor());

            // walk os/arch dirs. if "os" or "arch" does not exists, try a directory called "others"
            if (osArchDirectory.toFile().exists()) {
                // try <os>, others
                for (String osSubDir : new String[]{OS, OTHERS}) {
                    Path osDirectory = osArchDirectory.resolve(osSubDir);
                    if (osDirectory.toFile().exists()) {
                        // try <arch>, others
                        for (String archSubDir : new String[]{ARCH, OTHERS}) {
                            Path archDirectory = osDirectory.resolve(archSubDir);
                            if (archDirectory.toFile().exists()) {
                                // visit os/arch subdir
                                Files.walkFileTree(archDirectory, visitors.visitor());
                                // do not look for other
                                break;
                            }
                        }
                        // do not look for other
                        break;
                    }
                }
            }
            return visitors.getExcludeMap();
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }

    private static class FileVisitors {
        private final Path skip;
        private final Map<String, String> excludeTestToFile;

        private FileVisitors(Path skip) {
            this.excludeTestToFile = new HashMap<>();
            this.skip = skip;
        }

        public Map<String, String> getExcludeMap() {
            return excludeTestToFile;
        }

        public ExcludeFileVisitor visitor() {
            return new ExcludeFileVisitor();
        }

        public SkippingFileVisitor skippingVisitor() {
            return new SkippingFileVisitor();
        }

        /**
         * A {@link FileVisitor} that looks for ".exclude" files and adds their content to the
         * {@link #excludeTestToFile exclude map}.
         */
        private class ExcludeFileVisitor extends SimpleFileVisitor<Path> {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                if (path.toString().endsWith(".exclude")) {
                    // add all entries in the exclude file the the map
                    String excludeFile = Paths.get(TestOptions.CONFIG_ROOT).relativize(path).toString();
                    readAllLines(path).forEach(excludeTest -> {
                        String oldReason = excludeTestToFile.get(excludeTest);
                        excludeTestToFile.put(excludeTest, oldReason == null ? excludeFile : oldReason + ", " + excludeFile);
                    });
                }
                return FileVisitResult.CONTINUE;
            }
        }

        /**
         * A {@link ExcludeFileVisitor} that skips special directories.
         */
        private class SkippingFileVisitor extends ExcludeFileVisitor {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.startsWith(skip)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
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
}
