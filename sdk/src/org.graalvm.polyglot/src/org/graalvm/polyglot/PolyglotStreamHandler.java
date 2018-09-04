/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
