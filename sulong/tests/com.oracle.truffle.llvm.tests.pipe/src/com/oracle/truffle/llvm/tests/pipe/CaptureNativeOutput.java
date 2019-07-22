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
package com.oracle.truffle.llvm.tests.pipe;

import java.io.File;

import java.io.IOException;
import java.nio.file.Files;

public final class CaptureNativeOutput implements CaptureOutput {

    static {
        String pipeLib = System.getProperty("test.pipe.lib");
        if (pipeLib == null) {
            System.loadLibrary("pipe");
        } else {
            System.load(pipeLib);
        }
    }

    private static final int STDOUT = 1;
    private static final int STDERR = 2;

    private final File stdoutFile;
    private final int oldStdout;

    private final File stderrFile;
    private final int oldStderr;

    private String stdout;
    private String stderr;

    public CaptureNativeOutput() {
        try {
            stdoutFile = File.createTempFile("stdout", ".log");
            stdoutFile.deleteOnExit();

            stderrFile = File.createTempFile("stderr", ".log");
            stderrFile.deleteOnExit();

            oldStdout = startCapturing(STDOUT, stdoutFile.getAbsolutePath());
            oldStderr = startCapturing(STDERR, stderrFile.getAbsolutePath());
        } catch (IOException e) {
            System.out.println("Error while initializing output capture:");
            e.printStackTrace();
            System.exit(-99);
            throw new IllegalStateException();
        }

        stdout = null;
        stderr = null;
    }

    @Override
    public void close() throws IOException {
        System.out.flush();
        System.err.flush();
        if (stdout == null) {
            stopCapturing(oldStdout, oldStderr);

            stdout = new String(Files.readAllBytes(stdoutFile.toPath()));
            stdoutFile.delete();

            stderr = new String(Files.readAllBytes(stderrFile.toPath()));
            stderrFile.delete();
        }
    }

    @Override
    public String getStdOut() throws IOException {
        close();
        return stdout;
    }

    @Override
    public String getStdErr() throws IOException {
        close();
        return stderr;
    }

    private static native int startCapturing(int fd, String tempFilename) throws IOException;

    private static native void stopCapturing(int oldStdout, int oldStderr) throws IOException;
}
