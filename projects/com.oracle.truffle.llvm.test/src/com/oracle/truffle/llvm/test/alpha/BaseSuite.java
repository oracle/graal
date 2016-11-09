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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.llvm.LLVM;
import com.oracle.truffle.llvm.pipe.CaptureOutput;
import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;
import com.oracle.truffle.llvm.tools.util.ProcessUtil;
import com.oracle.truffle.llvm.tools.util.ProcessUtil.ProcessResult;

public abstract class BaseSuite {

    protected static final Set<String> supportedFiles = new HashSet<>(Arrays.asList("f90", "f", "f03", "c", "cpp", "cc", "C", "m"));

    protected static final Predicate<? super Path> isExecutable = f -> f.getFileName().toString().endsWith(".out");
    protected static final Predicate<? super Path> isIncludeFile = f -> f.getFileName().toString().endsWith(".include");
    protected static final Predicate<? super Path> isSulong = f -> f.getFileName().toString().endsWith(".ll") || f.getFileName().toString().endsWith(".bc");
    protected static final Predicate<? super Path> isFile = f -> f.toFile().isFile();

    protected abstract Path getSuiteDirectory();

    protected abstract Path getTestDirectory();

    @Test
    public void test() throws Exception {
        assert Files.walk(getTestDirectory()).filter(isExecutable).count() == 1;

        Path referenceFile = Files.walk(getTestDirectory()).filter(isExecutable).findFirst().get();
        List<Path> testCandidates = Files.walk(getTestDirectory()).filter(isFile).filter(isSulong).collect(Collectors.toList());
        ProcessResult processResult = ProcessUtil.executeNativeCommand(referenceFile.toAbsolutePath().toString());
        String referenceStdOut = processResult.getStdOutput();
        final int referenceReturnValue = processResult.getReturnValue();

        for (Path candidate : testCandidates) {
            CaptureOutput.startCapturing();
            int sulongResult = -1;
            try {
                sulongResult = LLVM.executeMain(candidate.toAbsolutePath().toFile());
            } finally {
                CaptureOutput.stopCapturing();
            }

            if (sulongResult != (sulongResult & 0xFF)) {
                Assert.fail("Broken unittest " + getTestDirectory() + ". Test exits with invalid value.");
            }
            String sulongStdOut = CaptureOutput.getCapture();
            String testName = candidate.getFileName().toString() + " in " + getTestDirectory().toAbsolutePath().toString();
            Assert.assertEquals(testName + " failed. Posix return value missmatch.", referenceReturnValue,
                            sulongResult);
            Assert.assertEquals(testName + " failed. Output (stdout) missmatch.", referenceStdOut,
                            sulongStdOut);
        }
    }

    protected static final Collection<Object[]> collectTestCases(Path configPath, Path suiteDir) throws AssertionError {
        Set<Path> whiteList = getWhiteListTestFolders(configPath, suiteDir);
        Predicate<? super Path> whiteListFilter;
        String testDiscoveryPath = LLVMBaseOptionFacade.getTestDiscoveryPath();
        if (testDiscoveryPath == null) {
            whiteListFilter = whiteList::contains;
        } else {
            System.err.println(testDiscoveryPath);
            whiteListFilter = p -> !whiteList.contains(p) && p.startsWith(new File(suiteDir.toString(), testDiscoveryPath).toPath());
        }
        return collectTestCases(suiteDir, whiteListFilter);
    }

    private static Collection<Object[]> collectTestCases(Path suiteDir, Predicate<? super Path> whiteListFilter) throws AssertionError {
        try {
            return Files.walk(suiteDir).filter(isExecutable).map(f -> f.getParent()).filter(whiteListFilter).map(f -> new Object[]{f, f.toString()}).collect(Collectors.toList());
        } catch (IOException e) {
            throw new AssertionError("Test cases not found", e);
        }
    }

    protected static final Set<Path> getWhiteListTestFolders(Path configDir, Path suiteDirectory) {
        try {
            return Files.walk(configDir).filter(isIncludeFile).flatMap(f -> {
                try {
                    return Files.lines(f);
                } catch (IOException e) {
                    throw new AssertionError("Error creating whitelist.", e);
                }
            }).map(s -> new File(suiteDirectory.toString(), removeFileEnding(s)).toPath()).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new AssertionError("Error creating whitelist.", e);
        }
    }

    private static String removeFileEnding(String s) {
        return s.substring(0, s.lastIndexOf('.'));
    }
}
