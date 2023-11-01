/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.tests.Platform;
import com.oracle.truffle.llvm.tests.options.TestOptions;
import com.oracle.truffle.llvm.tests.pipe.CaptureOutput;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.Assert;

public class ProcessUtil {

    private static final int BUFFER_SIZE = 1024;
    private static final int PROCESS_WAIT_TIMEOUT = 5 * 60 * 1000; // 5 min timeout
    private static final int JOIN_TIMEOUT = 5 * 1000; // 5 sec timeout

    /**
     * This class represents the result of a native command executed by the operating system.
     */
    public static final class ProcessResult {

        private final String originalCommand;
        private final String stdErr;
        private final String stdOutput;
        private final int returnValue;

        public ProcessResult(String originalCommand, int returnValue, String stdErr, String stdOutput) {
            this.originalCommand = originalCommand;
            this.returnValue = returnValue;
            this.stdErr = stdErr;
            this.stdOutput = stdOutput;
        }

        public String getOriginalCommand() {
            return originalCommand;
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

        private static String sanitizeCRLF(String str) {
            if (Platform.isWindows()) {
                // on windows, the JVM sometimes messes with the binary/text mode of stdout
                // so we have to ignore CRLF vs LF differences in the test output
                return str.replaceAll("\r\n", "\n");
            } else {
                return str;
            }
        }

        @Override
        public boolean equals(Object obj) {
            // ignore originalCommand, two different commands can still produce the same output
            if (!(obj instanceof ProcessResult)) {
                return false;
            }

            ProcessResult other = (ProcessResult) obj;
            return this.returnValue == other.returnValue &&
                            Objects.equals(sanitizeCRLF(this.stdErr), sanitizeCRLF(other.stdErr)) &&
                            Objects.equals(sanitizeCRLF(this.stdOutput), sanitizeCRLF(other.stdOutput));
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

    public abstract static class TestEngineMode implements Closeable {
        public abstract ProcessResult run(File bitcodeFile, String[] args, Map<String, String> options, boolean evalSourceOnly) throws IOException;

        @Override
        public void close() {
        }
    }

    public static class CachedEngineMode extends TestEngineMode {
        private Engine engine;
        private Function<Builder, CaptureOutput> captureOutput;

        public CachedEngineMode(Engine engine, Function<Context.Builder, CaptureOutput> captureOutput) {
            this.engine = engine;
            this.captureOutput = captureOutput;
        }

        @Override
        public ProcessResult run(File bitcodeFile, String[] args, Map<String, String> options, boolean evalSourceOnly) throws IOException {
            org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.newBuilder("llvm", bitcodeFile).build();
            Builder builder = Context.newBuilder();
            try (CaptureOutput out = captureOutput.apply(builder)) {
                int result = 0;
                try (Context context = builder.engine(engine).arguments("llvm", args).options(options).allowAllAccess(true).build()) {
                    Value main = context.eval(source);
                    if (!main.canExecute()) {
                        Assert.fail("No main function found.");
                    }
                    if (!evalSourceOnly) {
                        result = main.execute().asInt();
                    }
                }
                return new ProcessResult(bitcodeFile.getName(), result, out.getStdErr(), out.getStdOut());
            }
        }

        @Override
        public void close() {
            engine.close();
        }
    }

    public static class SeparateProcessEngineMode extends TestEngineMode {
        private ProcessHarnessManager manager;

        public SeparateProcessEngineMode() throws IOException {
            this.manager = ProcessHarnessManager.create(1);
        }

        @Override
        public ProcessResult run(File bitcodeFile, String[] args, Map<String, String> options, boolean evalSourceOnly) throws IOException {
            return manager.startTask(bitcodeFile.getAbsolutePath()).get();
        }

        @Override
        public void close() {
            manager.shutdown();
        }
    }

    public static class AOTEngineMode extends TestEngineMode {
        @Override
        public ProcessResult run(File bitcodeFile, String[] args, Map<String, String> options, boolean evalSourceOnly) throws IOException {

            ArrayList<String> command = new ArrayList<>();
            command.add(TestOptions.TEST_AOT_IMAGE);
            if (evalSourceOnly) {
                command.add("--eval-source-only");
            }
            if (TestOptions.TEST_AOT_ARGS != null) {
                command.addAll(Arrays.asList(TestOptions.TEST_AOT_ARGS.split(" ")));
            }
            command.add("--experimental-options");
            command.addAll(concatOptions(options));
            command.add(bitcodeFile.getAbsolutePath());
            command.addAll(Arrays.asList(args));
            return executeNativeCommand(command);
        }
    }

    public static ProcessResult executeSulongTestMain(File bitcodeFile, String[] args, Map<String, String> options, Function<Context.Builder, CaptureOutput> captureOutput) throws IOException {
        TestEngineMode mode = new CachedEngineMode(Engine.newBuilder().allowExperimentalOptions(true).build(), captureOutput);
        ProcessResult result = mode.run(bitcodeFile, args, options, false);
        mode.close();
        return result;
    }

    private static List<String> concatOptions(Map<String, String> options) {
        List<String> optList = new ArrayList<>();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            String encoded = "--" + entry.getKey() + '=' + entry.getValue();
            optList.add(encoded);
        }
        return optList;
    }

    public static ProcessResult executeNativeCommand(List<String> command) {
        if (command == null) {
            throw new IllegalArgumentException("command is null!");
        }
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = null;
        try {
            process = processBuilder.start();
            StreamReader readError = StreamReader.read(process.getErrorStream());
            StreamReader readOutput = StreamReader.read(process.getInputStream());
            boolean success = process.waitFor(PROCESS_WAIT_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!success) {
                throw new TimeoutError(command.toString());
            }
            int llvmResult = process.exitValue();
            return new ProcessResult(command.toString(), llvmResult, readError.getResult(), readOutput.getResult());
        } catch (Exception e) {
            throw new RuntimeException(command + " ", e);
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
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
