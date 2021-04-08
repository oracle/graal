/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A utility for printing compiler debug and informational output to an output stream.
 *
 * A {@link LogStream} instance maintains an internal buffer that is flushed to the underlying
 * output stream every time one of the {@code println} methods is invoked, or a newline character (
 * {@code '\n'}) is written.
 *
 * All of the {@code print} and {@code println} methods return the {code LogStream} instance on
 * which they were invoked. This allows chaining of these calls to mitigate use of String
 * concatenation by the caller.
 *
 * A {@code LogStream} maintains a current {@linkplain #indentationLevel() indentation} level. Each
 * line of output written to this stream has {@code n} spaces prefixed to it where {@code n} is the
 * value that would be returned by {@link #indentationLevel()} when the first character of a new
 * line is written.
 *
 * A {@code LogStream} maintains a current {@linkplain #position() position} for the current line
 * being written. This position can be advanced to a specified position by
 * {@linkplain #fillTo(int, char) filling} this stream with a given character.
 */
public class LogStream {

    /**
     * Null output stream that simply swallows any output sent to it.
     */
    public static final LogStream SINK = new LogStream();

    private static final PrintStream SINK_PS = new PrintStream(new OutputStream() {

        @Override
        public void write(int b) throws IOException {
        }
    });

    private LogStream() {
        this.consumer = null;
        this.lineBuffer = null;
    }

    /**
     * The output stream to which this log stream writes.
     */
    private final Consumer<? super String> consumer;

    private final StringBuilder lineBuffer;
    private int indentationLevel;
    private char indentation = ' ';
    private boolean indentationDisabled;

    public final PrintStream out() {
        if (consumer == null) {
            return SINK_PS;
        } else if (consumer instanceof PrintStreamConsumer) {
            return ((PrintStreamConsumer) consumer).printStream;
        } else {
            return ((ForwardingConsumer) consumer).getPrintStream();
        }
    }

    /**
     * The system dependent line separator.
     */
    public static final String LINE_SEPARATOR = System.lineSeparator();

    /**
     * Creates a new log stream.
     *
     * @param os the underlying output stream to which prints are sent
     */
    public LogStream(OutputStream os) {
        consumer = new PrintStreamConsumer(os instanceof PrintStream ? (PrintStream) os : new PrintStream(os));
        lineBuffer = new StringBuilder(100);
    }

    /**
     * Creates a new log stream.
     *
     * @param sink the {@link Consumer} to which prints are sent
     */
    public LogStream(Consumer<? super String> sink) {
        this.consumer = new ForwardingConsumer(this, sink);
        lineBuffer = new StringBuilder(100);
    }

    /**
     * Creates a new log stream that shares the same {@linkplain #consumer output stream} as a given
     * {@link LogStream}.
     *
     * @param log a LogStream whose output stream is shared with this one
     */
    public LogStream(LogStream log) {
        consumer = log.consumer;
        lineBuffer = new StringBuilder(100);
    }

    /**
     * Prepends {@link #indentation} to the current output line until its write position is equal to
     * the current {@linkplain #indentationLevel()} level.
     */
    private void indent() {
        if (consumer != null) {
            if (!indentationDisabled && indentationLevel != 0) {
                while (lineBuffer.length() < indentationLevel) {
                    lineBuffer.append(indentation);
                }
            }
        }
    }

    private LogStream flushLine(boolean withNewline) {
        if (consumer != null) {
            if (withNewline) {
                lineBuffer.append(LINE_SEPARATOR);
            }
            consumer.accept(lineBuffer.toString());
            lineBuffer.setLength(0);
        }
        return this;
    }

    /**
     * Flushes the stream. This is done by terminating the current line if it is not at position 0
     * and then flushing the underlying output stream.
     */
    public void flush() {
        if (consumer != null) {
            flushLine(false);
        }
    }

    /**
     * Gets the current column position of this log stream.
     *
     * @return the current column position of this log stream
     */
    public int position() {
        return lineBuffer == null ? 0 : lineBuffer.length();

    }

    /**
     * Gets the current indentation level for this log stream.
     *
     * @return the current indentation level for this log stream.
     */
    public int indentationLevel() {
        return indentationLevel;
    }

    /**
     * Adjusts the current indentation level of this log stream.
     *
     * @param delta
     */
    public void adjustIndentation(int delta) {
        if (delta < 0) {
            indentationLevel = Math.max(0, indentationLevel + delta);
        } else {
            indentationLevel += delta;
        }
    }

    /**
     * Gets the current indentation character of this log stream.
     */
    public char indentation() {
        return indentation;
    }

    public void disableIndentation() {
        indentationDisabled = true;
    }

    public void enableIndentation() {
        indentationDisabled = false;
    }

    /**
     * Sets the character used for indentation.
     */
    public void setIndentation(char c) {
        indentation = c;
    }

    /**
     * Advances this stream's {@linkplain #position() position} to a given position by repeatedly
     * appending a given character as necessary.
     *
     * @param position the position to which this stream's position will be advanced
     * @param filler the character used to pad the stream
     */
    public LogStream fillTo(int position, char filler) {
        if (consumer != null) {
            indent();
            while (lineBuffer.length() < position) {
                lineBuffer.append(filler);
            }
        }
        return this;
    }

    /**
     * Writes a boolean value to this stream as {@code "true"} or {@code "false"}.
     *
     * @param b the value to be printed
     * @return this {@link LogStream} instance
     */
    public LogStream print(boolean b) {
        if (consumer != null) {
            indent();
            lineBuffer.append(b);
        }
        return this;
    }

    /**
     * Writes a boolean value to this stream followed by a {@linkplain #LINE_SEPARATOR line
     * separator}.
     *
     * @param b the value to be printed
     * @return this {@link LogStream} instance
     */
    public LogStream println(boolean b) {
        if (consumer != null) {
            indent();
            lineBuffer.append(b);
            return flushLine(true);
        }
        return this;
    }

    /**
     * Writes a character value to this stream.
     *
     * @param c the value to be printed
     * @return this {@link LogStream} instance
     */
    public LogStream print(char c) {
        if (consumer != null) {
            indent();
            lineBuffer.append(c);
            if (c == '\n') {
                if (lineBuffer.indexOf(LINE_SEPARATOR, lineBuffer.length() - LINE_SEPARATOR.length()) != -1) {
                    flushLine(false);
                }
            }
        }
        return this;
    }

    /**
     * Writes a character value to this stream followed by a {@linkplain #LINE_SEPARATOR line
     * separator}.
     *
     * @param c the value to be printed
     * @return this {@link LogStream} instance
     */
    public LogStream println(char c) {
        if (consumer != null) {
            indent();
            lineBuffer.append(c);
            flushLine(true);
        }
        return this;
    }

    /**
     * Prints an int value.
     *
     * @param i the value to be printed
     * @return this {@link LogStream} instance
     */
    public LogStream print(int i) {
        if (consumer != null) {
            indent();
            lineBuffer.append(i);
        }
        return this;
    }

    /**
     * Writes an int value to this stream followed by a {@linkplain #LINE_SEPARATOR line separator}.
     *
     * @param i the value to be printed
     * @return this {@link LogStream} instance
     */
    public LogStream println(int i) {
        if (consumer != null) {
            indent();
            lineBuffer.append(i);
            return flushLine(true);
        }
        return this;
    }

    /**
     * Writes a float value to this stream.
     *
     * @param f the value to be printed
     * @return this {@link LogStream} instance
     */
    public LogStream print(float f) {
        if (consumer != null) {
            indent();
            lineBuffer.append(f);
        }
        return this;
    }

    /**
     * Writes a float value to this stream followed by a {@linkplain #LINE_SEPARATOR line separator}
     * .
     *
     * @param f the value to be printed
     * @return this {@link LogStream} instance
     */
    public LogStream println(float f) {
        if (consumer != null) {
            indent();
            lineBuffer.append(f);
            return flushLine(true);
        }
        return this;
    }

    /**
     * Writes a long value to this stream.
     *
     * @param l the value to be printed
     * @return this {@link LogStream} instance
     */
    public LogStream print(long l) {
        if (consumer != null) {
            indent();
            lineBuffer.append(l);
        }
        return this;
    }

    /**
     * Writes a long value to this stream followed by a {@linkplain #LINE_SEPARATOR line separator}.
     *
     * @param l the value to be printed
     * @return this {@link LogStream} instance
     */
    public LogStream println(long l) {
        if (consumer != null) {
            indent();
            lineBuffer.append(l);
            return flushLine(true);
        }
        return this;
    }

    /**
     * Writes a double value to this stream.
     *
     * @param d the value to be printed
     * @return this {@link LogStream} instance
     */
    public LogStream print(double d) {
        if (consumer != null) {
            indent();
            lineBuffer.append(d);
        }
        return this;
    }

    /**
     * Writes a double value to this stream followed by a {@linkplain #LINE_SEPARATOR line
     * separator}.
     *
     * @param d the value to be printed
     * @return this {@link LogStream} instance
     */
    public LogStream println(double d) {
        if (consumer != null) {
            indent();
            lineBuffer.append(d);
            return flushLine(true);
        }
        return this;
    }

    /**
     * Writes a {@code String} value to this stream. This method ensures that the
     * {@linkplain #position() position} of this stream is updated correctly with respect to any
     * {@linkplain #LINE_SEPARATOR line separators} present in {@code s}.
     *
     * @param s the value to be printed
     * @return this {@link LogStream} instance
     */
    public LogStream print(String s) {
        if (consumer != null) {
            if (s == null) {
                indent();
                lineBuffer.append(s);
                return this;
            }

            int index = 0;
            int next = s.indexOf(LINE_SEPARATOR, index);
            while (index < s.length()) {
                indent();
                if (next > index || next == 0) {
                    lineBuffer.append(s.substring(index, next));
                    flushLine(true);
                    index = next + LINE_SEPARATOR.length();
                    next = s.indexOf(LINE_SEPARATOR, index);
                } else {
                    lineBuffer.append(s.substring(index));
                    break;
                }
            }
        }
        return this;
    }

    /**
     * Writes a {@code String} value to this stream followed by a {@linkplain #LINE_SEPARATOR line
     * separator}.
     *
     * @param s the value to be printed
     * @return this {@link LogStream} instance
     */
    public LogStream println(String s) {
        if (consumer != null) {
            print(s);
            flushLine(true);
        }
        return this;
    }

    /**
     * Writes a formatted string to this stream.
     *
     * @param format a format string as described in {@link String#format(String, Object...)}
     * @param args the arguments to be formatted
     * @return this {@link LogStream} instance
     */
    public LogStream printf(String format, Object... args) {
        if (consumer != null) {
            print(String.format(format, args));
        }
        return this;
    }

    /**
     * Writes a {@linkplain #LINE_SEPARATOR line separator} to this stream.
     *
     * @return this {@code LogStream} instance
     */
    public LogStream println() {
        if (consumer != null) {
            indent();
            flushLine(true);
        }
        return this;
    }

    private static final class PrintStreamConsumer implements Consumer<String> {

        private final PrintStream printStream;

        PrintStreamConsumer(PrintStream printStream) {
            this.printStream = Objects.requireNonNull(printStream, "PrintStream must be non null.");
        }

        @Override
        public void accept(String t) {
            printStream.print(t);
            printStream.flush();
        }
    }

    private static final class ForwardingConsumer implements Consumer<String> {

        private final LogStream owner;
        private final Consumer<? super String> delegate;
        private final AtomicReference<PrintStream> printStreamRef;

        ForwardingConsumer(LogStream owner, Consumer<? super String> delegate) {
            this.owner = Objects.requireNonNull(owner, "Owner must be non null.");
            this.delegate = Objects.requireNonNull(delegate, "Delegate must be non null.");
            this.printStreamRef = new AtomicReference<>();
        }

        @Override
        public void accept(String t) {
            delegate.accept(t);
        }

        PrintStream getPrintStream() {
            PrintStream res = printStreamRef.get();
            if (res == null) {
                res = new DelegatingPrintStream(owner);
                if (!printStreamRef.compareAndSet(null, res)) {
                    res = printStreamRef.get();
                }
            }
            assert res != null : "PrintStream must exist";
            return res;
        }

        private static final class DelegatingPrintStream extends PrintStream {

            private final LogStream owner;

            DelegatingPrintStream(LogStream owner) {
                super(new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        throw new IllegalStateException("Should not reach here.");
                    }
                });
                this.owner = owner;
            }

            @Override
            public PrintStream append(CharSequence csq, int start, int end) {
                owner.print(csq == null ? "null" : csq.subSequence(start, end).toString());
                return this;
            }

            @Override
            public void print(Object obj) {
                print(String.valueOf(obj));
            }

            @Override
            public void print(String s) {
                owner.print(s);
            }

            @Override
            public void print(char[] s) {
                for (int i = 0; i < s.length; i++) {
                    owner.print(s[i]);
                }
            }

            @Override
            public void print(double d) {
                owner.print(d);
            }

            @Override
            public void print(float f) {
                owner.print(f);
            }

            @Override
            public void print(long l) {
                owner.print(l);
            }

            @Override
            public void print(int i) {
                owner.print(i);
            }

            @Override
            public void print(char c) {
                owner.print(c);
            }

            @Override
            public void print(boolean b) {
                owner.print(b);
            }

            @Override
            public void println(char[] s) {
                print(s);
                owner.println();
            }

            @Override
            public void println(char c) {
                print(c);
                owner.println();
            }

            @Override
            public void println(Object o) {
                print(o);
                owner.println();
            }

            @Override
            public void println(String s) {
                print(s);
                owner.println();
            }

            @Override
            public void println(double s) {
                print(s);
                owner.println();
            }

            @Override
            public void println(float f) {
                print(f);
                owner.println();
            }

            @Override
            public void println(long l) {
                print(l);
                owner.println();
            }

            @Override
            public void println(int i) {
                print(i);
                owner.println();
            }

            @Override
            public void println(boolean b) {
                print(b);
                owner.println();
            }

            @Override
            public void println() {
                owner.println();
            }

            @Override
            public void write(byte[] buf, int off, int len) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void write(int b) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void flush() {
                owner.flush();
            }
        }
    }
}
