/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Value;

import com.oracle.truffle.llvm.tests.pipe.CaptureOutput;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.tests.options.TestOptions;

public class ProcessUtil {

    private static final int BUFFER_SIZE = 1024;
    private static final int PROCESS_WAIT_TIMEOUT = 60 * 1000; // 1 min timeout
    private static final int JOIN_TIMEOUT = 5 * 1000; // 5 sec timeout

    /**
     * This class represents the result of a native command executed by the operating system.
     */
    public static final class ProcessResult {

        private final String originalCommand;
        private final String stdErr;
        private final String stdOutput;
        private final int returnValue;

        private ProcessResult(String originalCommand, int returnValue, String stdErr, String stdOutput) {
            this.originalCommand = originalCommand;
            this.returnValue = returnValue;
            this.stdErr = stdErr;
            this.stdOutput = stdOutput;
        }

        /**
         * Gets the Standard error stream of the executed native command.
         *
         * @return <code>stderr</code> as a string
         */
        public String getStdErr() {
            return stdErr;
        }

        /**
         * Gets the Standard output stream of the executed native command.
         *
         * @return <code>stdout</code> as a string
         */
        public String getStdOutput() {
            return stdOutput;
        }

        /**
         * Gets the return value of the executed native command.
         *
         * @return <code>stderr</code> as a string
         */
        public int getReturnValue() {
            return returnValue;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("command : " + originalCommand + "\n");
            sb.append("stderr: " + stdErr + "\n");
            sb.append("stdout: " + stdOutput + "\n");
            sb.append("return value: " + returnValue + "\n");
            return sb.toString();
        }

        @Override
        public boolean equals(Object obj) {
            // ignore originalCommand, two different commands can still produce the same output
            if (!(obj instanceof ProcessResult)) {
                return false;
            }

            ProcessResult other = (ProcessResult) obj;
            return this.returnValue == other.returnValue &&
                            Objects.equals(this.stdErr, other.stdErr) &&
                            Objects.equals(this.stdOutput, other.stdOutput);
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 97 * hash + Objects.hashCode(this.stdErr);
            hash = 97 * hash + Objects.hashCode(this.stdOutput);
            hash = 97 * hash + this.returnValue;
            return hash;
        }
    }

    public static final class TimeoutError extends AssertionError {

        private static final long serialVersionUID = 1L;

        TimeoutError(String command) {
            super("timeout running command: " + command);
        }
    }

    public static ProcessResult executeSulongTestMain(File bitcodeFile, String[] args, Map<String, String> options, Function<Context.Builder, CaptureOutput> captureOutput) throws IOException {
        if (TestOptions.TEST_AOT_IMAGE == null) {
            org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.newBuilder(LLVMLanguage.ID, bitcodeFile).build();
            Builder builder = Context.newBuilder();
            try (CaptureOutput out = captureOutput.apply(builder)) {
                int result;
                try (Context context = builder.arguments(LLVMLanguage.ID, args).options(options).allowAllAccess(true).build()) {
                    Value main = context.eval(source);
                    if (!main.canExecute()) {
                        throw new LLVMLinkerException("No main function found.");
                    }
                    result = main.execute().asInt();
                }
                return new ProcessResult(bitcodeFile.getName(), result, out.getStdErr(), out.getStdOut());
            }
        } else {
            String aotArgs = TestOptions.TEST_AOT_ARGS == null ? "" : TestOptions.TEST_AOT_ARGS + " ";
            String cmdline = TestOptions.TEST_AOT_IMAGE + " " + aotArgs + concatOptions(options) + bitcodeFile.getAbsolutePath() + " " + concatCommand(args);
            return executeNativeCommand(cmdline);
        }
    }

    private static String concatOptions(Map<String, String> options) {
        StringBuilder str = new StringBuilder();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            String encoded = entry.getKey() + '=' + entry.getValue();
            str.append("'--").append(encoded.replace("'", "''")).append("' ");
        }
        return str.toString();
    }

    public static ProcessResult executeNativeCommandZeroReturn(String command) {
        ProcessResult result = executeNativeCommand(command);
        checkNoError(result);
        return result;
    }

    /**
     * Executes a native command and checks that the return value of the process is 0.
     */
    public static ProcessResult executeNativeCommandZeroReturn(String... command) {
        ProcessResult result;
        if (command.length == 1) {
            result = executeNativeCommand(command[0]);
        } else {
            result = executeNativeCommand(concatCommand(command));
        }
        checkNoError(result);
        return result;
    }

    /**
     * Concats a command by introducing whitespaces between the array elements.
     */
    static String concatCommand(Object[] command) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < command.length; i++) {
            if (i != 0) {
                sb.append(" ");
            }
            sb.append(command[i]);
        }
        return sb.toString();
    }

    public static ProcessResult executeNativeCommand(String command) {
        if (command == null) {
            throw new IllegalArgumentException("command is null!");
        }
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(command);
            StreamReader readError = StreamReader.read(process.getErrorStream());
            StreamReader readOutput = StreamReader.read(process.getInputStream());
            boolean success = process.waitFor(PROCESS_WAIT_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!success) {
                throw new TimeoutError(command);
            }
            int llvmResult = process.exitValue();
            return new ProcessResult(command, llvmResult, readError.getResult(), readOutput.getResult());
        } catch (Exception e) {
            throw new RuntimeException(command + " ", e);
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    public static void checkNoError(ProcessResult processResult) {
        if (processResult.getReturnValue() != 0) {
            throw new IllegalStateException(processResult.originalCommand + " exited with value " + processResult.getReturnValue() + " " + processResult.getStdErr());
        }
    }

    private static class StreamReader {

        private final Thread thread;
        private final ByteArrayOutputStream result;

        private IOException exception;

        static StreamReader read(InputStream inputStream) {
            StreamReader ret = new StreamReader(inputStream);
            ret.thread.start();
            return ret;
        }

        StreamReader(InputStream inputStream) {
            this.result = new ByteArrayOutputStream();
            this.thread = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        final byte[] buffer = new byte[BUFFER_SIZE];
                        int length;
                        while ((length = inputStream.read(buffer)) != -1) {
                            result.write(buffer, 0, length);
                        }
                    } catch (IOException ex) {
                        // re-throw in the other thread
                        exception = ex;
                    }
                }
            });
        }

        String getResult() throws IOException {
            try {
                thread.join(JOIN_TIMEOUT);
                result.close();
            } catch (InterruptedException ex) {
                // ignore
            }
            if (exception != null) {
                throw exception;
            }
            return result.toString();
        }
    }
}
