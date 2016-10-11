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
package com.oracle.truffle.llvm.test.spec;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.test.TestCaseFlag;
import com.oracle.truffle.llvm.test.TestHelper;

public class SpecificationFileReader {

    private static final String RECURSIVE_INCLUDE_FROM_FOLDER = "*";
    private static final String ONE_LINE_COMMENT = "#";
    private static final String INCLUDE = ".include";
    private static final String EXCLUDE = ".exclude";

    private static final FilenameFilter FILE_NAME_INCLUDE_FILTER = new FilenameFilter() {

        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(INCLUDE);
        }
    };

    private static final FilenameFilter FILE_NAME_EXCLUDE_FILTER = new FilenameFilter() {

        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(EXCLUDE);
        }
    };

    public static TestSpecification readSpecificationFolder(File directory, File testRoot) throws IOException {
        LLVMLogger.info("\tread specification files in " + directory);
        List<SpecificationEntry> includeFiles = getSpecificationFiles(directory, testRoot, FILE_NAME_INCLUDE_FILTER);
        List<SpecificationEntry> excludeFiles = getSpecificationFiles(directory, testRoot, FILE_NAME_EXCLUDE_FILTER);
        return new TestSpecification(includeFiles, excludeFiles);
    }

    private static List<SpecificationEntry> getSpecificationFiles(File directory, File testRoot, FilenameFilter filter) throws AssertionError, IOException {
        return getFiles(getSpecificationFileNames(directory, filter), testRoot);
    }

    private static List<String> getSpecificationFileNames(File directory, FilenameFilter filter) throws AssertionError, IOException {
        List<String> fileLines = new ArrayList<>();
        File[] listFiles = directory.listFiles();
        if (listFiles != null) {
            for (File file : listFiles) {
                if (file.isDirectory()) {
                    fileLines = Stream.concat(fileLines.stream(), getSpecificationFileNames(file, filter).stream()).collect(Collectors.toList());
                } else if (filter.accept(directory, file.getName())) {
                    List<String> readAllLines = Files.readAllLines(Paths.get(file.getAbsolutePath()));
                    fileLines.addAll(readAllLines);
                }
            }
        }
        return fileLines;
    }

    public static List<SpecificationEntry> getFiles(List<String> fileLines, File testRoot) {
        List<SpecificationEntry> testCases = new ArrayList<>();
        List<String> allLines = fileLines.stream().filter(l -> !l.startsWith(ONE_LINE_COMMENT)).filter(l -> !l.equals("")).collect(Collectors.toList());
        for (String line : allLines) {
            String[] parts = line.split(" ");
            String fileName = parts[0].trim();
            Set<TestCaseFlag> flags = Stream.of(parts).skip(1).map(p -> TestCaseFlag.valueOf(p)).collect(Collectors.toSet());
            if (fileName.endsWith(RECURSIVE_INCLUDE_FROM_FOLDER)) {
                File directory = new File(testRoot, fileName.substring(0, fileName.length() - 2));
                if (!directory.isDirectory()) {
                    throw new AssertionError(directory + " is not a directory!");
                }
                testCases.addAll(getEntries(TestHelper.getFiles(directory, true), flags));
            } else {
                File currentFile = new File(testRoot, fileName);
                if (currentFile.isDirectory()) {
                    testCases.addAll(getEntries(TestHelper.getFiles(currentFile, false), flags));
                } else if (currentFile.exists()) {
                    testCases.add(new SpecificationEntry(currentFile, flags));
                } else {
                    throw new AssertionError(currentFile.getAbsolutePath() + " does not exist!");
                }
            }
        }
        return testCases;
    }

    private static List<SpecificationEntry> getEntries(List<File> files, Set<TestCaseFlag> flags) {
        return files.stream().map(f -> new SpecificationEntry(f, flags)).collect(Collectors.toList());
    }

}
