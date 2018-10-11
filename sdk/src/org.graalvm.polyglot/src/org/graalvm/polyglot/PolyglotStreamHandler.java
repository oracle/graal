/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

final class PolyglotStreamHandler extends StreamHandler {

    private final boolean closeStream;
    private final boolean flushOnPublish;

    /**
     * Creates a {@link Handler} printing log messages into given {@link OutputStream}.
     *
     * @param out the {@link OutputStream} to print log messages into
     * @param closeStream if true the {@link Handler#close() handler's close} method closes given
     *            stream
     * @param flushOnPublish if true the {@link Handler#flush() flush} method is called after
     *            {@link Handler#publish(java.util.logging.LogRecord) publish}
     * @return the {@link Handler}
     */
    PolyglotStreamHandler(final OutputStream out, final boolean closeStream, final boolean flushOnPublish) {
        super(out, FormatterImpl.INSTANCE);
        setLevel(Level.ALL);
        this.closeStream = closeStream;
        this.flushOnPublish = flushOnPublish;
    }

    @Override
    public synchronized void publish(LogRecord record) {
        super.publish(record);
        if (flushOnPublish) {
            flush();
        }
    }

    @SuppressWarnings("sync-override")
    @Override
    public void close() {
        if (closeStream) {
            super.close();
        } else {
            flush();
        }
    }

    private static final class FormatterImpl extends Formatter {

        private static final String FORMAT = "[%1$s] %2$s: %3$s%4$s%n";
        static final Formatter INSTANCE = new FormatterImpl();

        private FormatterImpl() {
        }

        @Override
        public String format(LogRecord record) {
            String loggerName = formatLoggerName(record.getLoggerName());
            final String message = formatMessage(record);
            String stackTrace = "";
            final Throwable exception = record.getThrown();
            if (exception != null) {
                final StringWriter str = new StringWriter();
                try (PrintWriter out = new PrintWriter(str)) {
                    out.println();
                    exception.printStackTrace(out);
                }
                stackTrace = str.toString();
            }
            return String.format(
                            FORMAT,
                            loggerName,
                            record.getLevel().getName(),
                            message,
                            stackTrace);
        }

        private static String formatLoggerName(final String loggerName) {
            final String id;
            String name;
            int index = loggerName.indexOf('.');
            if (index < 0) {
                id = loggerName;
                name = "";
            } else {
                id = loggerName.substring(0, index);
                name = loggerName.substring(index + 1);
            }
            if (name.isEmpty()) {
                return id;
            }
            final StringBuilder sb = new StringBuilder(id);
            sb.append("::");
            sb.append(possibleSimpleName(name));
            return sb.toString();
        }

        private static String possibleSimpleName(final String loggerName) {
            int index = -1;
            for (int i = 0; i >= 0; i = loggerName.indexOf('.', i + 1)) {
                if (i + 1 < loggerName.length() && Character.isUpperCase(loggerName.charAt(i + 1))) {
                    index = i + 1;
                    break;
                }
            }
            return index < 0 ? loggerName : loggerName.substring(index);
        }
    }
}
