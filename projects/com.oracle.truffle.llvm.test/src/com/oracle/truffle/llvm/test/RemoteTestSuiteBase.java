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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

import com.oracle.truffle.llvm.LLVM;
import com.oracle.truffle.llvm.runtime.LLVMOptions;
import com.oracle.truffle.llvm.tools.util.ProcessUtil;

public class RemoteTestSuiteBase extends TestSuiteBase {

    private static Process remoteTruffleProcess;
    private static BufferedWriter outputStream;
    private static BufferedReader reader;
    private static BufferedReader errorReader;

    protected static final Pattern RETURN_VALUE_PATTERN = Pattern.compile("exit ([-]*[0-9]*)");

    public List<String> launchLocal(TestCaseFiles tuple) {
        List<String> result = new ArrayList<>();
        if (LLVMOptions.debugEnabled()) {
            System.out.println("current file: " + tuple.getOriginalFile().getAbsolutePath());
        }
        try {
            int retValue = LLVM.executeMain(tuple.getBitCodeFile());
            result.add("exit " + retValue);
        } catch (Throwable t) {
            recordError(tuple, t);
            result.add("exit -1");
        }
        return result;
    }

    // we have to launch a remote process to capture native prints
    public List<String> launchRemote(TestCaseFiles tuple) throws IOException, AssertionError {
        if (LLVMOptions.launchRemoteTestCasesAsLocal()) {
            return launchLocal(tuple);
        } else {
            String str = tuple.getBitCodeFile().getAbsolutePath() + "\n";
            outputStream.write(str);
            outputStream.flush();
            String line;

            List<String> lines = new ArrayList<>();
            while (true) {
                line = reader.readLine();
                lines.add(line);
                if (line == null) {
                    throw new IllegalStateException();
                }
                if (RETURN_VALUE_PATTERN.matcher(line).matches()) {
                    break;
                } else if (line.matches("<error>")) {
                    readErrorAndFail(tuple.getBitCodeFile());
                    break;
                }
            }
            while (reader.ready()) {
                lines.add(reader.readLine());
            }
            return lines;
        }
    }

    protected static int parseAndRemoveReturnValue(List<String> expectedLines) {
        String lastLine = expectedLines.remove(expectedLines.size() - 1);
        Matcher matcher = RETURN_VALUE_PATTERN.matcher(lastLine);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        }
        throw new AssertionError(lastLine);
    }

    @AfterClass
    public static void endRemoteProcess() {
        if (!LLVMOptions.launchRemoteTestCasesAsLocal()) {
            try {
                outputStream.write("exit\n");
                outputStream.flush();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

    }

    @BeforeClass
    public static void startRemoteProcess() throws IOException {
        if (!LLVMOptions.launchRemoteTestCasesAsLocal()) {
            remoteTruffleProcess = TestHelper.launchRemoteTruffle();
            outputStream = new BufferedWriter(new OutputStreamWriter(remoteTruffleProcess.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(remoteTruffleProcess.getInputStream()));
            errorReader = new BufferedReader(new InputStreamReader(remoteTruffleProcess.getErrorStream()));
            if (!remoteTruffleProcess.isAlive()) {
                throw new IllegalStateException(ProcessUtil.readStream(remoteTruffleProcess.getErrorStream()));
            }
            TestHelper.compileToLLVMIRWithClang(LLVMPaths.FLUSH_C_FILE, LLVMPaths.FLUSH_BITCODE_FILE);
        }
    }

    private static void readErrorAndFail(File bitCodeFile) throws IOException {
        StringBuilder errorMessage = new StringBuilder();
        while (true) {
            String errorLine = errorReader.readLine();
            if (errorLine.equals("</error>")) {
                break;
            } else {
                errorMessage.append(errorLine + "\n");
            }
        }
        Assert.fail(bitCodeFile + errorMessage.toString());
    }

}
