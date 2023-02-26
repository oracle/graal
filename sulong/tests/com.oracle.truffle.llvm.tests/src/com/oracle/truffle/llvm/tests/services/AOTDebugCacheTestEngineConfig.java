/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.services;

import com.oracle.truffle.llvm.tests.util.ProcessUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * An example of using <code>AOTDebugCacheTestEngineConfig</code>.
 * 
 * <pre>
 * mx -d --dy /graal-enterprise unittest --sulong-config=AOTDebugCache -Dsulongtest.testAOTImage="mx" \
 * -Dsulongtest.testAOTArgs="--dy /graal-enterprise --jvm lli-mul --multi-context-runs=2 --use-debug-cache --engine.TraceTransferToInterpreter" \
 * SulongSuite
 * </pre>
 */
public class AOTDebugCacheTestEngineConfig extends NativeTestEngineConfig {

    @Override
    public String getConfigFolderName() {
        return "AOTCache";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public ProcessUtil.ProcessResult filterCandidateProcessResult(ProcessUtil.ProcessResult candidateResult) {
        String stdErr = candidateResult.getStdErr();
        stdErr = removeTruffleLogRedirectNotif(stdErr);
        stdErr = removeLastDeoptTrace(stdErr); // remove the deopt caused by throwing
                                               // LLVMExitException
        return new ProcessUtil.ProcessResult(candidateResult.getOriginalCommand(), candidateResult.getReturnValue(), stdErr, candidateResult.getStdOutput());
    }

    private static String removeTruffleLogRedirectNotif(String stdErr) {
        try {
            if (!stdErr.startsWith("[To redirect Truffle")) {
                return stdErr;
            }
            StringWriter stringWriter = new StringWriter();
            try (BufferedReader lineReader = new BufferedReader(new StringReader(stdErr));
                            BufferedWriter lineWriter = new BufferedWriter(stringWriter);) {

                @SuppressWarnings("UnusedAssignment")
                String line = lineReader.readLine();
                boolean acceptLine = false;
                do {
                    line = lineReader.readLine();
                    acceptLine = acceptLine || (line != null && !line.startsWith("* "));
                    if (acceptLine && line != null) {
                        lineWriter.append(line);
                        lineWriter.newLine();
                    }
                } while (line != null);
            }
            return stringWriter.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String removeLastDeoptTrace(String stdErr) {
        try {
            List<String> lines = new ArrayList<>();
            List<Integer> transferToInterpreterStarts = new ArrayList<>();
            try (BufferedReader lineReader = new BufferedReader(new StringReader(stdErr))) {
                String line = lineReader.readLine();
                while (line != null) {
                    if (line.startsWith("[engine] transferToInterpreter at")) {
                        transferToInterpreterStarts.add(lines.size());
                    }
                    lines.add(line);
                    line = lineReader.readLine();
                }
            }

            if (transferToInterpreterStarts.isEmpty()) {
                return stdErr;
            }

            int lastTTIStart = transferToInterpreterStarts.get(transferToInterpreterStarts.size() - 1);
            StringWriter stringWriter = new StringWriter();
            try (BufferedWriter lineWriter = new BufferedWriter(stringWriter)) {
                for (int i = 0; i < lastTTIStart; i++) {
                    lineWriter.append(lines.get(i));
                    lineWriter.newLine();
                }
            }
            return stringWriter.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
