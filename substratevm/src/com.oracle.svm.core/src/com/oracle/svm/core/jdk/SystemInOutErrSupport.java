/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Objects;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

/**
 * This class provides replacement values for the {@link System#in}, {@link System#out}, and
 * {@link System#err} streams at run time. We want a fresh set of objects, so that any buffers
 * filled during image generation, as well as any redirection of the streams to new values, do not
 * change the behavior at run time.
 *
 * By default, the streams are replaced to new streams that write to the standard file descriptors.
 * This can be customized by calling {@link #setIn}, {@link #setOut}, and {@link #setErr} before the
 * static analysis starts, i.e., in a {@link Feature#beforeAnalysis} method.
 */
public final class SystemInOutErrSupport {
    private InputStream in = new BufferedInputStream(new FileInputStream(FileDescriptor.in));
    private PrintStream out = newPrintStream(new FileOutputStream(FileDescriptor.out), System.getProperty("sun.stdout.encoding"));
    private PrintStream err = newPrintStream(new FileOutputStream(FileDescriptor.err), System.getProperty("sun.stderr.encoding"));

    /* Create `PrintStream` in the same way as `System.newPrintStream`. */
    private static PrintStream newPrintStream(FileOutputStream fos, String enc) {
        if (enc != null) {
            try {
                return new PrintStream(new BufferedOutputStream(fos, 128), true, enc);
            } catch (UnsupportedEncodingException ignored) {
            }
        }
        return new PrintStream(new BufferedOutputStream(fos, 128), true);
    }

    public InputStream in() {
        return in;
    }

    public static void setIn(InputStream in) {
        ImageSingletons.lookup(SystemInOutErrSupport.class).in = Objects.requireNonNull(in);
    }

    public PrintStream out() {
        return out;
    }

    public static void setOut(PrintStream out) {
        ImageSingletons.lookup(SystemInOutErrSupport.class).out = Objects.requireNonNull(out);
    }

    public PrintStream err() {
        return err;
    }

    public static void setErr(PrintStream err) {
        ImageSingletons.lookup(SystemInOutErrSupport.class).err = Objects.requireNonNull(err);
    }
}

@SuppressWarnings("unused")
class SystemInOutErrFeature implements Feature {
    /* Dummy for backward compatibility. */
}
