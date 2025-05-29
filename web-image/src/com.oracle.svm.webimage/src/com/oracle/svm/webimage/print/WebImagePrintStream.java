/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.print;

import java.io.PrintStream;

/**
 * Copy of {@link PrintStream} that directly prints characters instead of encoding them first.
 * <p>
 * Provides a fast path for printing. Printing to stdout or stderr is normally done through
 * System.out and System.err. These will be set to an instance of {@link WebImagePrintStream} and
 * any regular print calls can avoid the overhead of encoding to UTF-8 and decoding again.
 */
public class WebImagePrintStream extends PrintStream {
    private final WebImagePrintingProvider.Descriptor fd;
    private boolean isClosed = false;

    private boolean hasError = false;

    private static final char[] NEW_LINE_CHARS = {'\n'};
    private static final char[] NULL_CHARS = {'n', 'u', 'l', 'l'};
    private static final char[] TRUE_CHARS = {'t', 'r', 'u', 'e'};
    private static final char[] FALSE_CHARS = {'f', 'a', 'l', 's', 'e'};

    public WebImagePrintStream(WebImageOutputStream out) {
        super(out, true);
        fd = out.fd;
    }

    @Override
    public void close() {
        super.close();
        isClosed = true;
    }

    @Override
    public boolean checkError() {
        return hasError || super.checkError();
    }

    @Override
    protected void clearError() {
        hasError = false;
        super.clearError();
    }

    protected void doWrite(char[] buf) {
        WebImagePrintingProvider.singleton().print(fd, buf);
    }

    @Override
    public void flush() {
        WebImagePrintingProvider.singleton().flush(fd);
    }

    private void write(char[] buf) {
        if (isClosed) {
            hasError = true;
            return;
        }

        doWrite(buf);
    }

    private void write(String s) {
        write(s.toCharArray());
    }

    private void newLine() {
        write(NEW_LINE_CHARS);
    }

    @Override
    public void print(boolean b) {
        write(b ? TRUE_CHARS : FALSE_CHARS);
    }

    @Override
    public void print(char c) {
        write(new char[]{c});
    }

    @Override
    public void print(int i) {
        write(String.valueOf(i));
    }

    @Override
    public void print(long l) {
        write(String.valueOf(l));
    }

    @Override
    public void print(float f) {
        write(String.valueOf(f));
    }

    @Override
    public void print(double d) {
        write(String.valueOf(d));
    }

    @Override
    public void print(char[] s) {
        write(s);
    }

    @Override
    public void print(String s) {
        if (s == null) {
            write(NULL_CHARS);
        } else {
            write(s);
        }
    }

    @Override
    public void print(Object obj) {
        write(String.valueOf(obj));
    }

    @Override
    public void println() {
        newLine();
    }

    @Override
    public void println(boolean x) {
        print(x);
        newLine();
    }

    @Override
    public void println(char x) {
        print(x);
        newLine();
    }

    @Override
    public void println(int x) {
        print(x);
        newLine();
    }

    @Override
    public void println(long x) {
        print(x);
        newLine();
    }

    @Override
    public void println(float x) {
        print(x);
        newLine();
    }

    @Override
    public void println(double x) {
        print(x);
        newLine();
    }

    @Override
    public void println(char[] x) {
        print(x);
        newLine();
    }

    @Override
    public void println(String x) {
        print(x);
        newLine();
    }

    @Override
    public void println(Object x) {
        print(String.valueOf(x));
        newLine();
    }

    @Override
    public PrintStream append(CharSequence csq, int start, int end) {
        if (csq == null) {
            write(NULL_CHARS);
        } else {
            write(csq.subSequence(start, end).toString());
        }
        return this;
    }
}
