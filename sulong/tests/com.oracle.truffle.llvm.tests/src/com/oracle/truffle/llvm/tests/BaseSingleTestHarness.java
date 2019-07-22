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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.llvm.tests.pipe.CaptureNativeOutput;
import com.oracle.truffle.llvm.tests.util.ProcessUtil;
import com.oracle.truffle.llvm.tests.util.ProcessUtil.ProcessResult;

public abstract class BaseSingleTestHarness extends BaseTestHarness {

    @Override
    @Test
    public void test() throws IOException {
        Path referenceFile;
        try (Stream<Path> files = Files.walk(getTestDirectory())) {
            List<Path> executables = files.filter(isExecutable).collect(Collectors.toList());
            assert executables.size() == 1;
            referenceFile = executables.get(0);
        }
        try (Stream<Path> files = Files.walk(getTestDirectory())) {
            List<Path> testCandidates = files.filter(isFile).filter(isSulong).collect(Collectors.toList());
            String command = referenceFile.toAbsolutePath().toString() + " " + Arrays.stream(getArguments(referenceFile.getParent())).map(String::valueOf).collect(Collectors.joining(" "));
            ProcessResult processResult = ProcessUtil.executeNativeCommand(command);
            String referenceStdOut = processResult.getStdOutput();
            final int referenceReturnValue = processResult.getReturnValue();

            for (Path candidate : testCandidates) {
                if (!filterFileName().test(candidate.getFileName().toString())) {
                    continue;
                }

                if (!candidate.toAbsolutePath().toFile().exists()) {
                    throw new AssertionError("File " + candidate.toAbsolutePath().toFile() + " does not exist.");
                }

                ProcessResult out = ProcessUtil.executeSulongTestMain(candidate.toAbsolutePath().toFile(), getArguments(candidate.getParent()), getContextOptions(), c -> new CaptureNativeOutput());
                int sulongResult = out.getReturnValue();
                String sulongStdOut = out.getStdOutput();

                if (sulongResult != (sulongResult & 0xFF)) {
                    Assert.fail("Broken unittest " + getTestDirectory() + ". Test exits with invalid value.");
                }
                String testName = candidate.getFileName().toString() + " in " + getTestDirectory().toAbsolutePath().toString();
                Assert.assertEquals(testName + " failed. Posix return value missmatch.", referenceReturnValue,
                                sulongResult);
                Assert.assertEquals(testName + " failed. Output (stdout) missmatch.", referenceStdOut,
                                sulongStdOut);
            }
        }
    }

    protected abstract String[] getArguments(Path testCandidate);

}
