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
package com.oracle.truffle.llvm.tests.harness;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.tests.pipe.CaptureNativeOutput;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.Context.Builder;

public class TestHarness {
    private static Engine testEngine = Engine.newBuilder().allowExperimentalOptions(true).build();

    private static int runBitcode(File bitcodeFile, String[] args, Map<String, String> options) throws IOException {
        return runBitcode(bitcodeFile, args, options, testEngine, false);
    }

    private static int runBitcode(File bitcodeFile, String[] args, Map<String, String> options, Engine engine, boolean evalSourceOnly) throws IOException {
        Source source = Source.newBuilder(LLVMLanguage.ID, bitcodeFile).build();
        Builder builder = Context.newBuilder();
        try (Context context = builder.engine(engine).arguments(LLVMLanguage.ID, args).options(options).allowAllAccess(true).build()) {
            Value main = context.eval(source);
            if (!main.canExecute()) {
                throw new LLVMLinkerException("No main function found.");
            }
            if (!evalSourceOnly) {
                return main.execute().asInt();
            } else {
                return 0;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return -99123;
        }
    }

    private static int runBitcode(String[] runargs) throws IOException {
        File bitcodeFile = Path.of(runargs[0]).toFile();
        String[] args = Arrays.copyOfRange(runargs, 1, runargs.length);
        return runBitcode(bitcodeFile, args, new TreeMap<String, String>());
    }

    private static void run(String[] runargs) throws IOException {
        System.out.println(">>>START_TEST");
        System.err.println(">>>START_TEST");
        int result = runBitcode(runargs);
        CaptureNativeOutput.flushStdFiles();
        System.out.println("");
        System.out.println("<<<STOP_TEST");
        System.err.println("");
        System.err.println("<<<STOP_TEST");
        System.out.println("Exit code: " + result);
        System.out.flush();
        System.err.flush();
    }

    public static void main(@SuppressWarnings("unused") String[] args) {
        System.out.println("READY");
        System.out.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        try {
            while (true) {
                String[] command = reader.readLine().split(" ");

                switch (command[0]) {
                    case "RUN":
                        run(Arrays.copyOfRange(command, 1, command.length));
                        break;
                    case "EXIT":
                        testEngine.close();
                        return;
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }
}
