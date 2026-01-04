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

import java.io.PrintStream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.Uninterruptible;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Provides low-level output methods for basic Java data types (strings and numbers). The methods do
 * not allocate any Java objects or arrays, so that they can be used from allocation-free code,
 * e.g., during garbage collection.
 *
 * <p>
 * On most platforms, this logs to {@code stderr} by default. However, platform-specific
 * implementations can decide to redirect the output elsewhere, so this implementation cannot be
 * {@link Uninterruptible}. If logging needs to be called for debugging purposes from
 * uninterruptible code, please use {@link Debug#stderr()} instead.
 *
 * <p>
 * The string output methods do not perform platform- or charset-depending conversions. Therefore,
 * the printed strings must not contain any Unicode-characters. <br>
 * All methods return the {@link Log} object they are invoked on, so that output of multiple values
 * can be chained. A typical usage looks like the following:
 *
 * <pre>
 * import static com.oracle.svm.core.log.Log;
 *
 * void foo(int i, String s) {
 *   Log.log().string("i: ").signed(i).string(" s: ").string(s).newline();
 * }
 * </pre>
 */
public interface Log {
    int NO_ALIGN = 0;
    int LEFT_ALIGN = 1;
    int RIGHT_ALIGN = 2;

    /**
     * If {@link ImageSingletons#contains} returns {@code false} for {@code LogHandler.class}, then
     * this method installs a {@link FunctionPointerLogHandler} that delegates to {@code handler}.
     */
    @Platforms(HOSTED_ONLY.class)
    static void finalizeDefaultLogHandler(LogHandler handler) {
        if (!ImageSingletons.contains(LogHandler.class)) {
            ImageSingletons.add(LogHandler.class, new FunctionPointerLogHandler(handler));
        }
    }

    /**
     * Enters a fatal logging context which may redirect or suppress further log output if
     * {@code logHandler} is a {@link LogHandlerExtension}.
     *
     * @return {@code null} if fatal error logging is to be suppressed, otherwise the {@link Log}
     *         object to be used for fatal error logging
     */
    static Log enterFatalContext(LogHandler logHandler, CodePointer callerIP, String msg, Throwable ex) {
        if (logHandler instanceof LogHandlerExtension ext) {
            return ext.enterFatalContext(callerIP, msg, ex);
        }
        return Log.log();
    }

    @Fold
    static Log log() {
        return Loggers.realLog;
    }

    /** Returns {@link #log()} wrapped in a {@link PrintStream}. */
    @Fold
    static PrintStream logStream() {
        return Loggers.logStream;
    }

    @Fold
    static Log noopLog() {
        return Loggers.noopLog;
    }

    /** Returns true if logging is enabled. */
    boolean isEnabled();

    /**
     * Prints all characters in the string, without any platform- or charset-depending conversions.
     */
    Log string(String value);

    /**
     * Prints all characters in the string, filling with spaces before or after. Does not do any
     * platform- or charset-depending conversions.
     */
    Log string(String str, int fill, int align);

    /**
     * Prints the string characters, up to the given maximum length. Does not do any platform- or
     * charset-depending conversions.
     */
    Log string(String value, int maxLen);

    /**
     * Prints all characters in the array, without any platform- or charset-depending conversions.
     */
    Log string(char[] value);

    /**
     * Prints all bytes in the array, without any platform- or charset-depending conversions.
     */
    Log string(byte[] value);

    /**
     * Prints the provided range of bytes in the array, without any platform- or charset-depending
     * conversions.
     */
    Log string(byte[] value, int offset, int length);

    /** Prints the null-terminated C string. */
    Log string(CCharPointer value);

    /** Prints {@code length} characters of the C string. */
    Log string(CCharPointer value, int length);

    /** Prints the provided character. */
    Log character(char value);

    /** Prints the newline character. */
    Log newline();

    /**
     * Prints the value according to the given format specification. The digits '0' to '9' followed
     * by the letters 'a' to 'z' are used to represent the digits.
     *
     * @param value The value to print.
     * @param radix The base of the value, between 2 and 36.
     * @param signed true if the value should be treated as a signed value (and the digits are
     *            preceded by '-' for negative values).
     */
    Log number(long value, int radix, boolean signed);

    /** Prints the value, treated as a signed value, in decimal format. */
    Log signed(WordBase value);

    /** Prints the value, treated as a signed value, in decimal format. */
    Log signed(int value);

    /** Prints the value, treated as a signed value, in decimal format. */
    Log signed(long value);

    /** Prints the value, treated as a signed value, filling spaces before or after. */
    Log signed(long value, int fill, int align);

    /** Prints the value, treated as an unsigned value, in decimal format. */
    Log unsigned(WordBase value);

    /** Prints the value, treated as an unsigned value, filling spaces before or after. */
    Log unsigned(WordBase value, int fill, int align);

    /** Prints the value, treated as an unsigned value, in decimal format. */
    Log unsigned(int value);

    /** Prints the value, treated as an unsigned value, in decimal format. */
    Log unsigned(long value);

    /** Prints the value, treated as an unsigned value, filling spaces before or after. */
    Log unsigned(long value, int fill, int align);

    /**
     * Prints a rational number.
     * <p>
     * Note: this method will not perform rounding and it will print all trailing zeros, i.e.,
     * {@code rational(1, 2, 4)} prints {@code 0.5000}
     *
     * @param numerator Numerator in division
     * @param denominator or divisor
     * @param decimals number of decimals after the . to be printed.
     */
    Log rational(long numerator, long denominator, long decimals);

    /**
     * Prints a rational number.
     * <p>
     * Note: this method will not perform rounding and it will print all trailing zeros, i.e.,
     * {@code rational(1, 2, 4)} prints {@code 0.5000}
     *
     * @param numerator Numerator in division
     * @param denominator or divisor
     * @param decimals number of decimals after the . to be printed.
     */
    Log rational(UnsignedWord numerator, long denominator, long decimals);

    /** Prints the value, treated as an unsigned value, in hexadecimal format. */
    Log hex(WordBase value);

    /** Prints the value, treated as an unsigned value, in hexadecimal format. */
    Log hex(int value);

    /** Prints the value, treated as an unsigned value, in hexadecimal format. */
    Log hex(long value);

    /**
     * Prints the value, treated as an unsigned value, in hexadecimal format zero filled to
     * 16-digits.
     */
    Log zhex(WordBase value);

    /**
     * Prints the value, treated as an unsigned value, in hexadecimal format zero filled to
     * 16-digits.
     */
    Log zhex(long value);

    /**
     * Prints the value, treated as an unsigned value, in hexadecimal format zero filled to
     * 8-digits.
     */
    Log zhex(int value);

    /**
     * Prints the value, treated as an unsigned value, in hexadecimal format zero filled to
     * 4-digits.
     */
    Log zhex(short value);

    /**
     * Prints the value, treated as an unsigned value, in hexadecimal format zero filled to
     * 2-digits.
     */
    Log zhex(byte value);

    /**
     * Prints a hexdump.
     *
     * @param from pointer to memory where dumping should start from
     * @param wordSize size in bytes that a single word should have
     * @param numWords number of words to dump
     */
    Log hexdump(PointerBase from, int wordSize, int numWords);

    /**
     * Prints a hexdump.
     *
     * @param from pointer to memory where dumping should start from
     * @param wordSize size in bytes that a single word should have
     * @param numWords number of words to dump
     * @param bytesPerLine number of bytes that should be printed on one line
     */
    Log hexdump(PointerBase from, int wordSize, int numWords, int bytesPerLine);

    /**
     * Change current amount of indentation. Indentation determines the amount of spaces emitted
     * after each newline.
     */
    Log redent(boolean addOrRemove);

    /** Change current amount of indentation, and then print a newline. */
    Log indent(boolean addOrRemove);

    /** Reset the indentation to 0. */
    Log resetIndentation();

    /** Returns the current indentation. */
    int getIndentation();

    /** Prints the strings "true" or "false" depending on the value. */
    Log bool(boolean value);

    /** Simulates {@link String#valueOf(Object)}, but without the call to hashCode(). */
    Log object(Object value);

    /** Prints the requested number of spaces, e.g., for indentation. */
    Log spaces(int value);

    /**
     * Prints the provided exception, including a stack trace if available, followed by a newline.
     */
    Log exception(Throwable t);

    /**
     * Prints the provided exception, including a stack trace if available, with at most the
     * specified number of frames, followed by a newline.
     */
    Log exception(Throwable t, int maxFrames);

    /** Forces the log to flush to its destination. */
    Log flush();
}
