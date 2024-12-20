/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.bridge;

import java.io.PrintStream;
import java.util.function.Supplier;

public final class Logger {
    private final boolean enabled;
    private final String prefix;
    private final PrintStream sink;

    public Logger(boolean enabled, String prefix, PrintStream sink) {
        this.enabled = enabled;
        this.prefix = prefix;
        this.sink = sink;
    }

    public void log(Supplier<String> s) {
        if (enabled) {
            log(s.get());
        }
    }

    public void log(String message) {
        if (enabled) {
            sink.println(prefix + " " + message);
        }
    }

    public void log(Throwable throwable) {
        if (enabled) {
            throwable.printStackTrace(new PrintStream(sink) {
                @Override
                public void println(Object x) {
                    super.println(prefix + " " + x);
                }
            });
        }
    }

    public void log(Throwable throwable, String message) {
        if (enabled) {
            log(message);
            log(throwable);
        }
    }

    public boolean isLoggable() {
        assert prefix != null;
        return enabled;
    }

    public void log(String messageSimpleFormat, Object... args) {
        if (enabled) {
            log(fmt(messageSimpleFormat, args));
        }
    }

    /**
     * Cheap alternative to {@link String#format(String, Object...)} that only provides simple
     * modifiers.
     */
    private static String fmt(String simpleFormat, Object... args) throws IllegalArgumentException {
        StringBuilder sb = new StringBuilder();
        int index = 0;
        int argIndex = 0;
        while (index < simpleFormat.length()) {
            char ch = simpleFormat.charAt(index++);
            if (ch == '%') {
                if (index >= simpleFormat.length()) {
                    throw new IllegalArgumentException("An unquoted '%' character cannot terminate a format specification");
                }
                char specifier = simpleFormat.charAt(index++);
                switch (specifier) {
                    case 's' -> {
                        if (argIndex >= args.length) {
                            throw new IllegalArgumentException("Too many format specifiers or not enough arguments");
                        }
                        sb.append(args[argIndex++]);
                    }
                    case '%' -> sb.append('%');
                    case 'n' -> sb.append(System.lineSeparator());
                    default -> throw new IllegalArgumentException("Illegal format specifier: " + specifier);
                }
            } else {
                sb.append(ch);
            }
        }
        if (argIndex < args.length) {
            throw new IllegalArgumentException("Not enough format specifiers or too many arguments");
        }
        return sb.toString();
    }
}
