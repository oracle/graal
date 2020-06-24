/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.log;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.annotate.RestrictHeapAccess;

/**
 * Utility class that provides low-level output methods for basic Java data types (strings and
 * numbers). The methods do not allocate any Java objects or arrays, so that they can be used, e.g.,
 * during garbage collection. <br>
 * The string output methods do not perform platform- or charset-depending conversions. Therefore,
 * the printed strings must not contain any Unicode-characters. <br>
 * All methods return the {@link Log} object they are invoked on, so that output of multiple values
 * can be chained. A typical usage looks like the following:
 *
 * <pre>
 * import static com.oracle.svm.core.log.Log.*;
 *
 * void foo(int i, String s) {
 *   log().string("i: ").signed(i).string(" s: ").string(s).newline();
 * }
 * </pre>
 *
 * This class implements AutoCloseable, not because the log ever gets "closed", but so it can be
 * used to bracket several lines of logging with a try-with-resources statement, e.g.,
 *
 * <pre>
 * try (final Log trace = Log.log()) {
 *     trace.string("[in myMethod(arg: ".signed(arg).string(")").newline();
 *     trace.string("  i: ").signed(i);
 *     trace.string("  j: ").signed(j);
 *     trace.string("]").newline();
 * }
 * </pre>
 */
public abstract class Log implements AutoCloseable {

    /**
     * If {@link ImageSingletons#contains} returns {@code false} for {@code LogHandler.class}, then
     * this method installs a {@link FunctionPointerLogHandler} that delegates to {@code handler}.
     */
    public static void finalizeDefaultLogHandler(LogHandler handler) {
        if (!ImageSingletons.contains(LogHandler.class)) {
            ImageSingletons.add(LogHandler.class, new FunctionPointerLogHandler(handler));
        }
    }

    public static final int NO_ALIGN = 0;
    public static final int LEFT_ALIGN = 1;
    public static final int RIGHT_ALIGN = 2;

    /**
     * Logs come in (at least) two subclasses: RealLog which does real logging, and NoopLog which
     * doesn't do any logging. These are both final subclasses to make things easier to inline.
     */
    private static RealLog log = new RealLog();
    private static final NoopLog noopLog = new NoopLog();
    private static final PrintStream logStream = new PrintStream(new LogOutputStream());

    /**
     * Set the singleton RealLog instance (only possible during native image generation).
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setLog(RealLog realLog) {
        log = realLog;
    }

    /**
     * Return the singleton RealLog instance.
     */
    @Fold
    public static Log log() {
        return log;
    }

    /**
     * Returns the {@link #log()} wrapped as a {@link PrintStream}.
     */
    public static PrintStream logStream() {
        return logStream;
    }

    /**
     * Return the singleton NoopLog instance.
     */
    @Fold
    public static Log noopLog() {
        return noopLog;
    }

    /* Prevent creation from outside. */
    protected Log() {
    }

    /** Is this log enabled? */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract boolean isEnabled();

    /**
     * Prints all characters in the string, without any platform- or charset-depending conversions.
     */
    public abstract Log string(String value);

    /**
     * Prints all characters in the string, filling with spaces before or after.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log string(String str, int fill, int align);

    /**
     * Prints all characters in the array, without any platform- or charset-depending conversions.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log string(char[] value);

    /**
     * Prints all bytes in the array, without any conversion.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public final Log string(byte[] value) {
        return string(value, 0, value.length);
    }

    /**
     * Prints the provided range of bytes in the array, without any conversion.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log string(byte[] value, int offset, int length);

    /**
     * Prints the C string.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log string(CCharPointer value);

    /**
     * Prints the provided character.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log character(char value);

    /**
     * Prints the newline character.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log newline();

    /**
     * Turn auto-flushing of the log at every newline on or off. Default is off.
     *
     * @param onOrOff true if auto-flush must be turned on, false otherwise.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log autoflush(boolean onOrOff);

    /**
     * Prints the value according according to the given format specification. The digits '0' to '9'
     * followed by the letters 'a' to 'z' are used to represent the digits.
     *
     * @param value The value to print.
     * @param radix The base of the value, between 2 and 36.
     * @param signed true if the value should be treated as a signed value (and the digits are
     *            preceded by '-' for negative values).
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log number(long value, int radix, boolean signed);

    /**
     * Prints the value, treated as a signed value, in decimal format.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log signed(WordBase value);

    /**
     * Prints the value, treated as a signed value, in decimal format.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log signed(int value);

    /**
     * Prints the value, treated as a signed value, in decimal format.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log signed(long value);

    /**
     * Prints the value, treated as an unsigned value, in decimal format.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log unsigned(WordBase value);

    /**
     * Prints the value, treated as an unsigned value, filing spaces before or after.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log unsigned(WordBase value, int fill, int align);

    /**
     * Prints the value, treated as an unsigned value, in decimal format.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log unsigned(int value);

    /**
     * Prints the value, treated as an unsigned value, in decimal format.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log unsigned(long value);

    /**
     * Prints the value, treated as an unsigned value, filing spaces before or after.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log unsigned(long value, int fill, int align);

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log rational(long numerator, long denominator, long decimals);

    /**
     * Prints the value, treated as an unsigned value, in hexadecimal format.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log hex(WordBase value);

    /**
     * Prints the value, treated as an unsigned value, in hexadecimal format.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log hex(int value);

    /**
     * Prints the value, treated as an unsigned value, in hexadecimal format.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log hex(long value);

    /**
     * Prints the value, treated as an unsigned value, in hexadecimal format zero filled to
     * 16-digits.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log zhex(long value);

    /**
     * Prints the value, treated as an unsigned value, in hexadecimal format zero filled to
     * 8-digits.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log zhex(int value);

    /**
     * Prints the value, treated as an unsigned value, in hexadecimal format zero filled to
     * 4-digits.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log zhex(short value);

    /**
     * Prints the value, treated as an unsigned value, in hexadecimal format zero filled to
     * 2-digits.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log zhex(byte value);

    /**
     * Prints a hexdump.
     *
     * @param from pointer to memory where dumping should start from
     * @param wordSize size in bytes that a single word should have
     * @param numWords number of words to dump
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log hexdump(PointerBase from, int wordSize, int numWords);

    /**
     * Change current amount of indentation. Indentation determines the amount of spaces emitted
     * after each newline.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log redent(boolean addOrRemove);

    /**
     * Change current amount of indentation, and then print a newline.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public final Log indent(boolean addOrRemove) {
        return redent(addOrRemove).newline();
    }

    /**
     * Prints the strings "true" or "false" depending on the value.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log bool(boolean value);

    /**
     * Simulates java.lang.String.valueOf(Object obj), but without the call to hashCode().
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log object(Object value);

    /**
     * Prints the requested number of spaces, e.g., for indentation.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log spaces(int value);

    /**
     * Prints the provided exception, including a stack trace if available, followed by a newline.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public Log exception(Throwable t) {
        return exception(t, Integer.MAX_VALUE);
    }

    /**
     * Prints the provided exception, including a stack trace if available, with at most the
     * specified number of frames, followed by a newline.
     */
    public abstract Log exception(Throwable t, int maxFrames);

    /**
     * Forces the log to flush to its destination.
     */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, mayBeInlined = true, reason = "Must not allocate when logging.")
    public abstract Log flush();

    /** An implementation of AutoCloseable.close(). */
    @Override
    public void close() {
        return;
    }

    /**
     * A class that overrides most of the public methods of Log with noop implementations.
     *
     * The usage is somewhere to have a
     *
     * <pre>
     * public static final int verbosity = ....;
     * </pre>
     *
     * and then in methods that want to conditionally log output use
     *
     * <pre>
     * final Log myLog = (verbosity > 17 ? log() : noopLog());
     * myLog.string("Some opening message").newline();
     * ....
     * myLog.string("Some closing message").newline();
     * </pre>
     *
     * and expect the runtime compiler to evaluate the predicate and inline the effectively-empty
     * bodies of the methods from NoopLog into noops. It can do that except if the evaluation of the
     * arguments to the methods have side-effects, including possibly causing exceptions, e.g.,
     * NullPointerException. So be careful with the arguments.
     */
    private static final class NoopLog extends Log {

        protected NoopLog() {
            super();
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public Log string(String value) {
            return this;
        }

        @Override
        public Log string(String str, int fill, int align) {
            return this;
        }

        @Override
        public Log string(char[] value) {
            return this;
        }

        @Override
        public Log string(byte[] value, int offset, int length) {
            return this;
        }

        @Override
        public Log string(CCharPointer value) {
            return this;
        }

        @Override
        public Log character(char value) {
            return this;
        }

        @Override
        public Log newline() {
            return this;
        }

        @Override
        public Log number(long value, int radix, boolean signed) {
            return this;
        }

        @Override
        public Log signed(WordBase value) {
            return this;
        }

        @Override
        public Log signed(int value) {
            return this;
        }

        @Override
        public Log signed(long value) {
            return this;
        }

        @Override
        public Log unsigned(WordBase value) {
            return this;
        }

        @Override
        public Log unsigned(WordBase value, int fill, int align) {
            return this;
        }

        @Override
        public Log unsigned(int value) {
            return this;
        }

        @Override
        public Log unsigned(long value) {
            return this;
        }

        @Override
        public Log unsigned(long value, int fill, int align) {
            return this;
        }

        @Override
        public Log rational(long numerator, long denominator, long decimals) {
            return this;
        }

        @Override
        public Log hex(WordBase value) {
            return this;
        }

        @Override
        public Log hex(int value) {
            return this;
        }

        @Override
        public Log hex(long value) {
            return this;
        }

        @Override
        public Log bool(boolean value) {
            return this;
        }

        @Override
        public Log object(Object value) {
            return this;
        }

        @Override
        public Log spaces(int value) {
            return this;
        }

        @Override
        public Log flush() {
            return this;
        }

        @Override
        public Log autoflush(boolean onOrOff) {
            return this;
        }

        @Override
        public Log zhex(long value) {
            return this;
        }

        @Override
        public Log zhex(int value) {
            return this;
        }

        @Override
        public Log zhex(short value) {
            return this;
        }

        @Override
        public Log zhex(byte value) {
            return this;
        }

        @Override
        public Log hexdump(PointerBase from, int wordSize, int numWords) {
            return null;
        }

        @Override
        public Log exception(Throwable t, int maxFrames) {
            return this;
        }

        @Override
        public Log redent(boolean addOrRemove) {
            return this;
        }
    }

    static class LogOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            log().character((char) b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            log().string(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            log().flush();
        }
    }
}
