/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.io.ProcessHandler;

final class ProcessHandlers {

    private ProcessHandlers() {
        throw new IllegalStateException("Instance is not allowed");
    }

    static ProcessHandler newDefaultProcessHandler() {
        return new DefaultProcessHandler();
    }

    static boolean isDefault(ProcessHandler handler) {
        return handler instanceof DefaultProcessHandler;
    }

    private static final class DefaultProcessHandler implements ProcessHandler {

        @Override
        public Process start(ProcessCommand command) throws IOException {
            ProcessBuilder builder = new ProcessBuilder(command.getCommand()).redirectErrorStream(command.isRedirectErrorStream()).redirectInput(
                            translateRedirect(command.getInputRedirect())).redirectOutput(translateRedirect(command.getOutputRedirect())).redirectError(translateRedirect(command.getErrorRedirect()));
            Map<String, String> env = builder.environment();
            env.clear();
            env.putAll(command.getEnvironment());
            String cwd = command.getDirectory();
            if (cwd != null) {
                builder.directory(Paths.get(cwd).toFile());
            }
            Process process = builder.start();
            boolean outputRedirectedToStream = command.getOutputRedirect().getType() == ProcessHandler.Redirect.Type.STREAM;
            boolean errorOutputRedirectedToStream = command.getErrorRedirect().getType() == ProcessHandler.Redirect.Type.STREAM;
            if (outputRedirectedToStream || errorOutputRedirectedToStream) {
                process = new ProcessDecorator(
                                command.getCommand().get(0),
                                process,
                                command.getOutputRedirect().getOutputStream(),
                                command.getErrorRedirect().getOutputStream());
            }
            return process;
        }

        private static java.lang.ProcessBuilder.Redirect translateRedirect(ProcessHandler.Redirect redirect) {
            switch (redirect.getType()) {
                case PIPE:
                    return java.lang.ProcessBuilder.Redirect.PIPE;
                case INHERIT:
                    return java.lang.ProcessBuilder.Redirect.INHERIT;
                case STREAM:
                    return java.lang.ProcessBuilder.Redirect.PIPE;
                default:
                    throw new IllegalStateException("Unsupported redirect: " + redirect);
            }
        }

        private static final class ProcessDecorator extends Process {

            private final Process delegate;
            private final Thread outCopier;
            private final Thread errCopier;

            ProcessDecorator(
                            String name,
                            Process delegate,
                            OutputStream out,
                            OutputStream err) {
                Objects.requireNonNull(delegate, "Delegate must be non null.");
                this.delegate = delegate;
                this.outCopier = out == null ? null : new CopierThread(name + " [stdout]", delegate.getInputStream(), out);
                this.errCopier = err == null ? null : new CopierThread(name + " [stderr]", delegate.getErrorStream(), err);
                if (outCopier != null) {
                    outCopier.start();
                }
                if (errCopier != null) {
                    errCopier.start();
                }
            }

            @Override
            public OutputStream getOutputStream() {
                return delegate.getOutputStream();
            }

            @Override
            public InputStream getInputStream() {
                if (outCopier == null) {
                    return delegate.getInputStream();
                }
                return null;
            }

            @Override
            public InputStream getErrorStream() {
                if (errCopier == null) {
                    return delegate.getErrorStream();
                }
                return null;
            }

            @Override
            public int waitFor() throws InterruptedException {
                int res = delegate.waitFor();
                waitForCopiers();
                return res;
            }

            @Override
            public int exitValue() {
                return delegate.exitValue();
            }

            @Override
            public void destroy() {
                delegate.destroy();
            }

            @Override
            public Process destroyForcibly() {
                delegate.destroyForcibly();
                return this;
            }

            @Override
            public boolean isAlive() {
                return delegate.isAlive();
            }

            @Override
            public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
                boolean res = delegate.waitFor(timeout, unit);
                if (res) {
                    waitForCopiers();
                }
                return res;
            }

            private void waitForCopiers() throws InterruptedException {
                if (outCopier != null) {
                    outCopier.join();
                }
                if (errCopier != null) {
                    errCopier.join();
                }
            }
        }

        private static final class CopierThread extends Thread {

            private static final int BUFSIZE = 8192;

            private final InputStream in;
            private final OutputStream out;
            private final byte[] buffer;

            CopierThread(String name, InputStream in, OutputStream out) {
                Objects.requireNonNull(name, "Name must be non null.");
                Objects.requireNonNull(in, "In must be non null.");
                Objects.requireNonNull(out, "Out must be non null.");
                setName(name);
                this.in = in;
                this.out = out;
                this.buffer = new byte[BUFSIZE];
            }

            @Override
            public void run() {
                try {
                    while (true) {
                        if (isInterrupted()) {
                            return;
                        }
                        int read = in.read(buffer, 0, buffer.length);
                        if (read == -1) {
                            return;
                        }
                        out.write(buffer, 0, read);
                    }
                } catch (IOException e) {
                }
            }
        }

    }
}
