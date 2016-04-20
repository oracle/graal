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
package com.oracle.truffle.llvm.test;

import java.io.File;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.llvm.tools.util.PathUtil;

public final class TestCaseFiles {

    private final File originalFile;
    private final File bitCodeFile;
    private final File expectedResult;
    private final Set<TestCaseFlag> flags;

    private TestCaseFiles(File bitCodeFile, Set<TestCaseFlag> flags) {
        this(bitCodeFile, bitCodeFile, bitCodeFile, flags);
    }

    private TestCaseFiles(File originalFile, File byteCodeFile, Set<TestCaseFlag> flags) {
        this(originalFile, byteCodeFile, null, flags);
    }

    private TestCaseFiles(File originalFile, File bitCodeFile, File expectedResult, Set<TestCaseFlag> flags) {
        checkBitCodeFile(bitCodeFile);
        this.originalFile = originalFile;
        this.bitCodeFile = bitCodeFile;
        this.expectedResult = expectedResult;
        this.flags = flags;
    }

    public File getOriginalFile() {
        return originalFile;
    }

    public File getBitCodeFile() {
        return bitCodeFile;
    }

    public File getExpectedResult() {
        return expectedResult;
    }

    public boolean hasFlag(TestCaseFlag flag) {
        return flags.contains(flag);
    }

    public Set<TestCaseFlag> getFlags() {
        return Collections.unmodifiableSet(flags);
    }

    public static TestCaseFiles createFromCompiledFile(File originalFile, File bitCodeFile, File expectedResult, Set<TestCaseFlag> flags) {
        return new TestCaseFiles(originalFile, bitCodeFile, expectedResult, flags);
    }

    public static TestCaseFiles createFromCompiledFile(File originalFile, File bitCodeFile, Set<TestCaseFlag> flags) {
        return new TestCaseFiles(originalFile, bitCodeFile, flags);
    }

    public static TestCaseFiles createFromBitCodeFile(File bitCodeFile, Set<TestCaseFlag> flags) {
        return new TestCaseFiles(bitCodeFile, flags);
    }

    public static TestCaseFiles createFromBitCodeFile(File bitCodeFile, File expectedResult, Set<TestCaseFlag> flags) {
        return new TestCaseFiles(bitCodeFile, bitCodeFile, expectedResult, flags);
    }

    private static void checkBitCodeFile(File bitCodeFile) {
        assert bitCodeFile != null;
        String extension = PathUtil.getExtension(bitCodeFile.getName());
        if (!Constants.LLVM_BITFILE_EXTENSION.equals(extension)) {
            throw new IllegalArgumentException(bitCodeFile + " is not a bitcode file!");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TestCaseFiles)) {
            return false;
        } else {
            TestCaseFiles other = (TestCaseFiles) obj;
            return bitCodeFile.equals(other.bitCodeFile) && originalFile.equals(other.originalFile) &&
                            (expectedResult == null && other.expectedResult == null || expectedResult.equals(other.expectedResult));
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalFile, bitCodeFile, expectedResult);
    }

    @Override
    public String toString() {
        return "original file: " + originalFile + " bitcode file: " + bitCodeFile + " expected result: " + expectedResult + " flags: " + flags;
    }

}
