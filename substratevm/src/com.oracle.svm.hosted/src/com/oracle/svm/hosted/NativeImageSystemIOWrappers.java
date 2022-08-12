/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.hosted;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Objects;

import com.oracle.svm.core.util.UserError;

public class NativeImageSystemIOWrappers {

    final StdioWrapper outWrapper;
    final StdioWrapper errWrapper;

    ProgressReporter progressReporter = null;

    NativeImageSystemIOWrappers() {
        outWrapper = new StdioWrapper(System.out);
        errWrapper = new StdioWrapper(System.err);
    }

    void verifySystemOutErrReplacement() {
        String format = "%s was changed during image building. This is not allowed.";
        UserError.guarantee(System.out == outWrapper, format, "System.out");
        UserError.guarantee(System.err == errWrapper, format, "System.err");
    }

    void replaceSystemOutErr() {
        System.setOut(outWrapper);
        System.setErr(errWrapper);
    }

    public static NativeImageSystemIOWrappers singleton() {
        return NativeImageSystemClassLoader.singleton().systemIOWrappers;
    }

    public static NativeImageSystemIOWrappers disabled() {
        return new NativeImageSystemIOWrappersDisabled();
    }

    public PrintStream getOut() {
        return outWrapper.delegate;
    }

    public void setOut(PrintStream customOut) {
        outWrapper.delegate = Objects.requireNonNull(customOut);
    }

    public PrintStream getErr() {
        return errWrapper.delegate;
    }

    public void setErr(PrintStream customErr) {
        errWrapper.delegate = Objects.requireNonNull(customErr);
    }

    /**
     * Wrapper with the ability to inform {@link ProgressReporter} before a write.
     */
    private final class StdioWrapper extends PrintStream {
        private PrintStream delegate;

        private StdioWrapper(PrintStream delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public void write(int b) {
            maybeInformProgressReporterOnce();
            delegate.write(b);
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            maybeInformProgressReporterOnce();
            delegate.write(buf, off, len);
        }

        private void maybeInformProgressReporterOnce() {
            if (progressReporter != null) {
                progressReporter.beforeNextStdioWrite();
                progressReporter = null;
            }
        }
    }

    private static class NativeImageSystemIOWrappersDisabled extends NativeImageSystemIOWrappers {
        private static final PrintStream NULL_PRINT_STREAM = new PrintStream(OutputStream.nullOutputStream());

        @Override
        public PrintStream getOut() {
            return NULL_PRINT_STREAM;
        }

        @Override
        public PrintStream getErr() {
            return NULL_PRINT_STREAM;
        }
    }
}
