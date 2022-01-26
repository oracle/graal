/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Objects;

import com.oracle.svm.core.util.UserError;

public class NativeImageSystemIOWrappers {

    final CapturingStdioWrapper outWrapper;
    final CapturingStdioWrapper errWrapper;

    boolean useCapturing;

    NativeImageSystemIOWrappers() {
        outWrapper = new CapturingStdioWrapper(System.out, new ByteArrayOutputStream(128));
        errWrapper = new CapturingStdioWrapper(System.err, new ByteArrayOutputStream(128));
        useCapturing = false;
    }

    void verifySystemOutErrReplacement() {
        String format = "%s was changed during image building. This is not allowed.";
        UserError.guarantee(System.out == outWrapper, format, "System.out");
        UserError.guarantee(System.err == errWrapper, format, "System.err");
    }

    void flushCapturedContent() {
        outWrapper.flushCapturedContent();
        errWrapper.flushCapturedContent();
    }

    void replaceSystemOutErr() {
        System.setOut(outWrapper);
        System.setErr(errWrapper);
    }

    public static NativeImageSystemIOWrappers singleton() {
        return NativeImageSystemClassLoader.singleton().systemIOWrappers;
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
     * Wrapper with the ability to temporarily capture output to stdout and stderr.
     */
    private final class CapturingStdioWrapper extends PrintStream {
        private final ByteArrayOutputStream buffer;
        private PrintStream delegate;

        private CapturingStdioWrapper(PrintStream delegate, ByteArrayOutputStream buffer) {
            super(buffer);
            this.buffer = buffer;
            this.delegate = delegate;
        }

        @Override
        public void write(int b) {
            if (useCapturing) {
                super.write(b);
            } else {
                delegate.write(b);
            }
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            if (useCapturing) {
                super.write(buf, off, len);
            } else {
                delegate.write(buf, off, len);
            }
        }

        private void flushCapturedContent() {
            byte[] byteArray = buffer.toByteArray();
            delegate.write(byteArray, 0, byteArray.length);
            buffer.reset();
        }
    }
}
