/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.llvm.tests.options.TestOptions;
import com.oracle.truffle.llvm.tests.services.TestEngineConfig;

/**
 * Utils for collecting test cases and exclusion reasons.
 * 
 * @see #collectTestCases
 */
public final class TestCaseCollector {

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
     * Exclude files are recursively searched in the {@code testSuiteClass#getSimpleName()}
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
     * @return a collection of {@link Object} arrays, each with three elements: the absolute
     *         {@link Path path} to the test, the {@link String test name}, and an {@link String
     *         exclude reason} or {@code null} if there is none.
     * @see #TEST_PATH_IDX
     * @see #TEST_NAME_IDX
     * @see #EXCLUDE_REASON_IDX
     */
    public static Collection<Object[]> collectTestCases(Class<?> testSuiteClass, Path suitesPath, Predicate<? super Path> predicate) {
        try {
            // collect excludes
            ExcludeMap excludedTests = getExcludedTests(testSuiteClass);
            // walk test cases
            List<Object[]> list = Files.walk(suitesPath).filter(predicate).map(Path::getParent).map(testPath -> {
                String testCaseName = getTestCaseName(suitesPath, testPath).replace("\\", "/");
                return new Object[]{testPath, testCaseName, excludedTests.get(testCaseName)};
            }).collect(Collectors.toList());
            if (!list.isEmpty()) {
                return list;
            }
            throw new AssertionError("No test cases not found in: " + suitesPath);
        } catch (IOException e) {
            throw new AssertionError("Test cases not found", e);
        }
    }

    public static final int TEST_PATH_IDX = 0;
    public static final int TEST_NAME_IDX = 1;
    public static final int EXCLUDE_REASON_IDX = 2;

    /**
     * Gets the canonical configuration directory for a {@link Class}.
     */
    public static Path getConfigDirectory(Class<?> testSuiteClass) {
        return Paths.get(TestOptions.CONFIG_ROOT, testSuiteClass.getSimpleName());
    }

    private static String getTestCaseName(Path suitesPath, Path testPath) {
        return suitesPath.relativize(testPath).toString();
    }

    public abstract static class ExcludeMap {
        public abstract String get(String key);
    }

    private static final class EmptyExcludeMap extends ExcludeMap {
        private static final EmptyExcludeMap EMPTY = new EmptyExcludeMap();

        @Override
        public String get(String key) {
            return null;
        }
    }

    private static final class ExcludeAllMap extends ExcludeMap {

        private static final String EXCLUDE_ALL_PATTERN = "*";

        private final String reason;

        private ExcludeAllMap(String reason) {
            this.reason = reason;
        }

        @Override
        public String get(String key) {
            return reason;
        }
    }

    private static final class MapBasedExcludeMap extends ExcludeMap {

        private final Map<String, String> map;

        private MapBasedExcludeMap(Map<String, String> map) {
            this.map = map;
        }

        @Override
        public String get(String key) {
            return map.get(key);
        }
    }

    /**
     * Returns a map from excluded test to the exclude file that caused the exclusion.
     */
    public static ExcludeMap getExcludedTests(Class<?> testSuiteClass) {
        try {
            FileVisitors visitors = new FileVisitors();

            Path excludeDirectory = getConfigDirectory(testSuiteClass);
            Path osArchDirectory = excludeDirectory.resolve("os_arch");
            Path configDirectory = excludeDirectory.resolve("testEngineConfig");
            // walk <ROOT><testSuiteClass>/, skip "os_arch" and "runtimeConfig"
            FileVisitors.SkippingFileVisitor visitor = visitors.skippingVisitor(osArchDirectory, configDirectory);
            walkFileTreeIfExists(excludeDirectory, visitor);
            // walk <ROOT><testSuiteClass>/os_arch/
            walkOsArch(visitors, osArchDirectory);

            Path configExcludeDirectory = configDirectory.resolve(TestEngineConfig.getInstance().getConfigFolderName());
            Path configOsArchDirectory = configExcludeDirectory.resolve("os_arch");
            // walk <ROOT><testSuiteClass>/"runtimeConfig"/<LLVMRuntimeConfig>/, skip "os_arch"
            walkFileTreeIfExists(configExcludeDirectory, visitors.skippingVisitor(configOsArchDirectory));
            // walk <ROOT><testSuiteClass>/"runtimeConfig"/<LLVMRuntimeConfig>/os_arch/
            walkOsArch(visitors, configOsArchDirectory);

            Map<String, String> excludeMap = visitors.getExcludeMap();
            String excludeAllReason = excludeMap.get(ExcludeAllMap.EXCLUDE_ALL_PATTERN);
            if (excludeAllReason != null) {
                return new ExcludeAllMap(excludeAllReason);
            }
            return new MapBasedExcludeMap(excludeMap);
        } catch (IOException e) {
            return EmptyExcludeMap.EMPTY;
        }
    }

    private static void walkFileTreeIfExists(Path excludeDirectory, FileVisitors.ExcludeFileVisitor visitor) throws IOException {
        if (excludeDirectory.toFile().exists()) {
            Files.walkFileTree(excludeDirectory, visitor);
        }
    }

    /**
     * Walk os/arch dirs. If "os" or "arch" does not exists, try a directory called "others".
     */
    private static void walkOsArch(FileVisitors visitors, Path osArchDirectory) throws IOException {
        if (osArchDirectory.toFile().exists()) {
            try {
                Predicate<Path> exists = p -> p.toFile().exists();
                // try <os>, others
                Path osDirectory = Stream.of(OS, OTHERS).map(osArchDirectory::resolve).filter(exists).iterator().next();
                // try <arch>, others
                Path archDirectory = Stream.of(ARCH, OTHERS).map(osDirectory::resolve).filter(exists).iterator().next();
                // visit os/arch subdir
                walkFileTreeIfExists(archDirectory, visitors.visitor());
            } catch (NoSuchElementException e) {
                // either os or arch directory is missing
            }
        }
    }

    private static final class FileVisitors {
        private final Map<String, String> excludeTestToFile;

        private FileVisitors() {
            this.excludeTestToFile = new HashMap<>();
        }

        public Map<String, String> getExcludeMap() {
            return excludeTestToFile;
        }

        public ExcludeFileVisitor visitor() {
            return new ExcludeFileVisitor();
        }

        public SkippingFileVisitor skippingVisitor(Path... skip) {
            return new SkippingFileVisitor(skip);
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
        private final class SkippingFileVisitor extends ExcludeFileVisitor {
            private final Path[] skip;

            private SkippingFileVisitor(Path... skip) {
                this.skip = skip;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (Arrays.stream(skip).anyMatch(dir::startsWith)) {
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
