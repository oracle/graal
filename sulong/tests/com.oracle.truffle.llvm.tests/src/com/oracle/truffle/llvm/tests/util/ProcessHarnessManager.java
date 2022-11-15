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
package com.oracle.truffle.llvm.tests.util;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import com.oracle.truffle.llvm.tests.util.ProcessUtil.ProcessResult;

public class ProcessHarnessManager {
    BlockingQueue<Task> tasks = new LinkedBlockingQueue<>();
    ExecutorService executor = Executors.newCachedThreadPool();

    public Task startTask(String program) {
        Task task = new Task(this, program);
        addTask(task);
        return task;
    }

    protected void addTask(Task task) {
        tasks.offer(task);
    }

    protected Task getTask() throws InterruptedException {
        return tasks.take();
    }

    public static class Task {
        private final String task;
        private BlockingQueue<ProcessResult> resultQueue = new LinkedBlockingQueue<>();
        private Future<ProcessResult> result;

        protected Task(ProcessHarnessManager manager, String task) {
            this.task = task;
            result = manager.executor.submit(resultQueue::take);
        }

        protected void done(ProcessResult doneResult) {
            resultQueue.add(doneResult);
        }

        public ProcessResult get() {
            try {
                return result.get();
            } catch (InterruptedException ex) {
                return new ProcessResult(task, -1701, "The test task was interrupted.", "");
            } catch (ExecutionException ex) {
                return new ProcessResult(task, -1702, "An execution exception occurred during test evaluation:" + System.lineSeparator() + ex.toString(), "");
            }
        }
    }

    protected class ProcessHarnessInstance {
        static final String EXIT_CODE = "Exit code: ";

        Process process;
        BufferedReader output;
        BufferedReader error;
        Writer writer;
        Thread thread;

        public ProcessHarnessInstance(Process process, BufferedReader output, BufferedReader error, Writer writer) {
            this.process = process;
            this.output = output;
            this.error = error;
            this.writer = writer;
        }

        private String readOutputString(BufferedReader reader) throws IOException {
            StringBuilder buffer = new StringBuilder();
            assertEquals(">>>START_TEST", reader.readLine());
            while (true) {
                String line = reader.readLine();
                if (line.endsWith("<<<STOP_TEST")) {
                    buffer.append(line.substring(0, line.length() - "<<<STOP_TEST".length()));
                    break;
                }
                buffer.append(line);
                buffer.append(System.lineSeparator());
            }
            // return string without final newline
            return buffer.substring(0, buffer.length() - System.lineSeparator().length());
        }

        private ProcessResult processOutput(String command) throws InterruptedException, IOException, ExecutionException {
            // Get the stdout and stderr output in parallel, because printing to
            // stderr may be blocking for the client process if the buffer is
            // not cleared.
            Future<String> stdoutFuture = executor.submit(() -> readOutputString(output));
            Future<String> stderrFuture = executor.submit(() -> readOutputString(error));
            String stdout = stdoutFuture.get();
            String stderr = stderrFuture.get();
            String exitCodeLine = output.readLine();
            assert exitCodeLine.startsWith(EXIT_CODE);
            int exitCode = Integer.parseInt(exitCodeLine.substring(EXIT_CODE.length()));
            return new ProcessResult(command, exitCode, stderr, stdout);
        }

        private void waitReady() throws IOException {
            while (true) {
                String line = output.readLine();
                if (line.equals("READY")) {
                    break;
                } else {
                    System.out.println(line);
                }
            }
        }

        private void runTask(Task task) throws IOException, InterruptedException, ExecutionException {
            writer.write(String.format("RUN %s%n", task.task));
            writer.flush();
            ProcessResult result = processOutput(task.task);
            task.done(result);
        }

        private void shutdownProcess() {
            try {
                writer.write("EXIT" + System.lineSeparator());
                writer.flush();
                process.wait(1000);
            } catch (Exception ex) {
                process.destroyForcibly();
            }
        }

        private void runThread() {
            Task task;
            try {
                waitReady();

                while (true) {
                    task = getTask();
                    runTask(task);
                }
            } catch (InterruptedException ex) {
                // The executor service has been asked to shut down
            } catch (ExecutionException ex) {
                System.err.println("A test process manager failed with a fatal execution exception:");
                ex.printStackTrace();
                shutdown();
            } catch (IOException ex) {
                System.err.println("A test process manager failed with a fatal IO exception:");
                ex.printStackTrace();
                shutdown();
            } catch (Exception ex) {
                System.err.println("An unknown exception occurred:");
                ex.printStackTrace();
                shutdown();
            }

            shutdownProcess();
        }

    }

    private static String copyProperty(String property) {
        return String.format("-D%s=%s", property, System.getProperty(property));
    }

    private ProcessHarnessInstance startInstance() throws IOException {
        String java = ProcessHandle.current().info().command().get();
        String testHarness = System.getProperty("test.sulongtest.harness");
        String classpath = System.getProperty("java.class.path") + ":" + testHarness;

        // Set to true if a debugger should be attached to the process
        boolean debug = false;

        List<String> commandArgs = new ArrayList<>();
        commandArgs.add(java);
        if (debug) {
            commandArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=y");
        }
        Collections.addAll(commandArgs, "-esa", "-ea", "-Djava.awt.headless=true", "-cp", classpath,
                        copyProperty("polyglot.engine.WarnInterpreterOnly"),
                        copyProperty("truffle.nfi.library"),
                        copyProperty("org.graalvm.language.llvm.home"),
                        copyProperty("test.pipe.lib"),
                        "com.oracle.truffle.llvm.tests.harness.TestHarness");

        ProcessBuilder builder = new ProcessBuilder().command(commandArgs);
        builder.redirectInput();
        builder.redirectError();
        builder.redirectOutput();

        Process process = builder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader error = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        Writer writer = new OutputStreamWriter(process.getOutputStream());

        ProcessHarnessInstance instance = new ProcessHarnessInstance(process, reader, error, writer);
        instance.thread = new Thread(instance::runThread);
        instance.thread.start();
        return instance;
    }

    public static ProcessHarnessManager create(int count) throws IOException {
        ProcessHarnessManager manager = new ProcessHarnessManager();

        for (int i = 0; i < count; i++) {
            manager.startInstance();
        }

        return manager;
    }

    public void shutdown() {
        executor.shutdown();
    }
}
