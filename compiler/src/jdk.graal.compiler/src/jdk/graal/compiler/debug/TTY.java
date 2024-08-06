/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug;

import java.io.PrintStream;

import jdk.graal.compiler.serviceprovider.GraalServices;

/**
 * A collection of static methods for printing debug and informational output to a global
 * {@link LogStream}. The output can be (temporarily) suppressed per thread through use of a
 * {@linkplain Filter filter}.
 */
public class TTY {

    /**
     * Support for thread-local suppression of {@link TTY}.
     */
    public static class Filter implements AutoCloseable {

        private LogStream previous;
        private final Thread thread = Thread.currentThread();

        /**
         * Creates an object that will suppress {@link TTY} for the current thread. To revert the
         * suppression state to how it was before this call, the {@link #remove()} method must be
         * called on this filter object.
         */
        public Filter() {
            previous = out();
            log.set(LogStream.SINK);
        }

        /**
         * Creates an object that will overwrite {@link TTY} for the current thread with a custom
         * log stream. To revert the overwritten state to how it was before this call, the
         * {@link #remove()} method must be called on this filter object.
         */
        public Filter(LogStream newStream) {
            previous = out();
            log.set(newStream);
        }

        /**
         * Reverts the suppression state of {@link TTY} to how it was before this object was
         * constructed.
         */
        public void remove() {
            Thread currentThread = Thread.currentThread();
            assert thread == currentThread : Assertions.errorMessage(thread, currentThread);
            if (previous != null) {
                log.set(previous);
            }
        }

        @Override
        public void close() {
            remove();
        }
    }

    /**
     * The {@link PrintStream} to which all non-suppressed output from {@link TTY} is written.
     * Substituted by {@code com.oracle.svm.graal.Target_jdk_graal_compiler_debug_TTY}.
     */
    public static final PrintStream out;
    static {
        TTYStreamProvider p = GraalServices.loadSingle(TTYStreamProvider.class, false);
        out = p == null ? System.out : p.getStream();
    }

    private static final ThreadLocal<LogStream> log = new ThreadLocal<>() {

        @Override
        protected LogStream initialValue() {
            return new LogStream(out);
        }
    };

    public static boolean isSuppressed() {
        return log.get() == LogStream.SINK;
    }

    /**
     * Gets the thread-local log stream to which the static methods of this class send their output.
     * This will either be a global log stream or the global {@linkplain LogStream#SINK sink}
     * depending on whether any suppression {@linkplain Filter filters} are in effect for the
     * current thread.
     */
    public static LogStream out() {
        return log.get();
    }

    /**
     * @see LogStream#print(String)
     */
    public static void print(String s) {
        out().print(s);
    }

    /**
     * @see LogStream#print(int)
     */
    public static void print(int i) {
        out().print(i);
    }

    /**
     * @see LogStream#print(long)
     */
    public static void print(long i) {
        out().print(i);
    }

    /**
     * @see LogStream#print(char)
     */
    public static void print(char c) {
        out().print(c);
    }

    /**
     * @see LogStream#print(boolean)
     */
    public static void print(boolean b) {
        out().print(b);
    }

    /**
     * @see LogStream#print(double)
     */
    public static void print(double d) {
        out().print(d);
    }

    /**
     * @see LogStream#print(float)
     */
    public static void print(float f) {
        out().print(f);
    }

    /**
     * @see LogStream#println(String)
     */
    public static void println(String s) {
        out().println(s);
    }

    /**
     * @see LogStream#println()
     */
    public static void println() {
        out().println();
    }

    /**
     * @see LogStream#println(int)
     */
    public static void println(int i) {
        out().println(i);
    }

    /**
     * @see LogStream#println(long)
     */
    public static void println(long l) {
        out().println(l);
    }

    /**
     * @see LogStream#println(char)
     */
    public static void println(char c) {
        out().println(c);
    }

    /**
     * @see LogStream#println(boolean)
     */
    public static void println(boolean b) {
        out().println(b);
    }

    /**
     * @see LogStream#println(double)
     */
    public static void println(double d) {
        out().println(d);
    }

    /**
     * @see LogStream#println(float)
     */
    public static void println(float f) {
        out().println(f);
    }

    public static void printf(String format, Object... args) {
        out().printf(format, args);
    }

    public static void println(String format, Object... args) {
        out().printf(format + "%n", args);
    }

    public static void fillTo(int i) {
        out().fillTo(i, ' ');
    }

    public static void flush() {
        out().flush();
    }
}
