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
import java.io.File;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.oracle.truffle.llvm.LLVM;

/**
 * This class is a compilation and execution server that can be executed as a remote process.
 * Communication works by writing commands to the standard input of the process.
 */
public class RemoteLLVMTester {

    private static final int SLEEP_TIME_MS = 100;
    private static final int TIMEOUT_SECONDS = 8;
    private static final int NR_TRIES = 300;
    private static int result;
    private static File file;
    private static Object[] programArgs;

    private static final String PROGRAM_ARGS_DELIMITER = " ";
    private static final String PROGRAM_COMMAND_DELIMITER = ";";

    public static class RemoteProgramArgsBuilder {

        private File file;
        private Object[] args;

        public RemoteProgramArgsBuilder(File file) {
            this.file = file;
        }

        public RemoteProgramArgsBuilder args(Object... commandArgs) {
            this.args = commandArgs;
            return this;
        }

        public String getCommand() {
            StringBuilder sb = new StringBuilder();
            if (file == null) {
                throw new AssertionError();
            }
            sb.append(file.getAbsolutePath());
            if (args != null && args.length != 0) {
                sb.append(PROGRAM_COMMAND_DELIMITER);
            }
            for (int i = 0; i < args.length; i++) {
                if (i != 0) {
                    sb.append(PROGRAM_ARGS_DELIMITER);
                }
                sb.append(args[i].toString());
            }
            sb.append("\n");
            return sb.toString();
        }

    }

    public static class ProgramArgsBuilder {

        private File file;
        private Object[] args;

        public ProgramArgsBuilder(String file) {
            this.file = new File(file);
        }

        public ProgramArgsBuilder args(Object... commandArgs) {
            this.args = commandArgs;
            return this;
        }

        public String getCommand() {
            StringBuilder sb = new StringBuilder();
            if (file == null) {
                throw new AssertionError();
            }
            sb.append(file.getAbsolutePath());
            if (args != null && args.length != 0) {
                sb.append(PROGRAM_COMMAND_DELIMITER);
            }
            for (int i = 0; i < args.length; i++) {
                if (i != 0) {
                    sb.append(PROGRAM_ARGS_DELIMITER);
                }
                sb.append(args[i].toString());
            }
            return sb.toString();
        }

    }

    // does not work since System.out prints immediately and printf is not
    /**
     * Expects one of the following commands: <code>exit</code>: Exits the remote tester
     * <code><file></code>: The LLVM IR file to be executed
     * <code><file>;<arg1> <arg2> ... <arg n></code>: The LLVM IR file with arguments that should be
     * passed
     */
    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            try {
                int tries = 0;
                while (tries < NR_TRIES && !br.ready()) {
                    Thread.sleep(SLEEP_TIME_MS);
                    tries++;
                }
                if (tries >= NR_TRIES) {
                    printError("timeout on wait exit");
                    System.exit(1);
                }
                String command = br.readLine();
                programArgs = null;
                file = null;
                if (command.equals("exit")) {
                    System.exit(0);
                } else if (command.contains(";")) {
                    String[] tokens = command.split(";");
                    file = new File(tokens[0]);
                    if (tokens.length != 2) {
                        throw new IllegalStateException("expected two tokens (file and args) but got " + Arrays.toString(tokens));
                    }
                    String argsString = tokens[1];
                    programArgs = argsString.split(" ");
                } else {
                    file = new File(command);
                    programArgs = null;
                }
                Runnable executeTruffleTask = new Runnable() {

                    @Override
                    public void run() {
                        try {
                            if (programArgs == null) {
                                result = LLVM.executeMain(file);
                            } else {
                                result = LLVM.executeMain(file, programArgs);
                            }
                        } catch (Throwable e) {
                            result = -1;
                            printError(e);
                        }
                    }

                };
                executeWithTimeout(executeTruffleTask);
                int exitCode = result;
                if (exitCode != -1) {
                    try {
                        LLVM.executeMain(LLVMPaths.FLUSH_BITCODE_FILE);
                    } catch (Exception e) {
                        printError(e);
                    }
                    System.out.println("\nexit " + exitCode);
                }
            } catch (Throwable e) {
                printError(e);
            }
        }
    }

    private static void printError(Throwable e) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        StringBuilder sb = new StringBuilder("\n");
        sb.append(e);
        for (StackTraceElement trace : stackTrace) {
            sb.append(trace.toString());
            sb.append("\n");
        }
        printError(sb.toString());
    }

    private static void printError(String error) {
        System.out.println("\n<error>");
        System.err.println(error);
        System.err.println("</error>");
    }

    private static void executeWithTimeout(Runnable task) throws InterruptedException, ExecutionException {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<?> future = executor.submit(task);
        executor.shutdown();

        try {
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            result = -1;
            printError("timeout");
        }
        if (!executor.isTerminated()) {
            executor.shutdownNow();
        }
    }

}
