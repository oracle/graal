/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.core;

import java.io.*;
import java.util.*;

import com.oracle.truffle.ruby.runtime.core.array.*;

public class StringFormatter {

    public static String format(String format, List<Object> values) {
        final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArray);

        format(printStream, format, values);

        return byteArray.toString();
    }

    public static void format(PrintStream stream, String format, List<Object> values) {
        /*
         * See http://www.ruby-doc.org/core-1.9.3/Kernel.html#method-i-sprintf.
         * 
         * At the moment we just do the basics that we need. We will need a proper lexer later on.
         * Or better than that we could compile to Truffle nodes if the format string is constant! I
         * don't think we can easily translate to Java's format syntax, otherwise JRuby would do
         * that and they don't.
         */

        // I'm not using a for loop, because Checkstyle won't let me modify the control variable

        int n = 0;
        int v = 0;

        while (n < format.length()) {
            final char c = format.charAt(n);
            n++;

            if (c == '%') {
                // %[flags][width][.precision]type

                final String flagChars = "0";

                boolean zeroPad = false;

                while (n < format.length() && flagChars.indexOf(format.charAt(n)) != -1) {
                    switch (format.charAt(n)) {
                        case '0':
                            zeroPad = true;
                            break;
                    }

                    n++;
                }

                int width;

                if (n < format.length() && Character.isDigit(format.charAt(n))) {
                    final int widthStart = n;

                    while (Character.isDigit(format.charAt(n))) {
                        n++;
                    }

                    width = Integer.parseInt(format.substring(widthStart, n));
                } else {
                    width = 0;
                }

                int precision;

                if (format.charAt(n) == '.') {
                    n++;

                    final int precisionStart = n;

                    while (Character.isDigit(format.charAt(n))) {
                        n++;
                    }

                    precision = Integer.parseInt(format.substring(precisionStart, n));
                } else {
                    precision = 5;
                }

                final char type = format.charAt(n);
                n++;

                final StringBuilder formatBuilder = new StringBuilder();

                formatBuilder.append("%");

                if (width > 0) {
                    if (zeroPad) {
                        formatBuilder.append("0");
                    }

                    formatBuilder.append(width);
                }

                switch (type) {
                    case 'd': {
                        formatBuilder.append("d");
                        final int value = GeneralConversions.toFixnum(values.get(v));
                        stream.printf(formatBuilder.toString(), value);
                        break;
                    }

                    case 'f': {
                        formatBuilder.append(".");
                        formatBuilder.append(precision);
                        formatBuilder.append("f");
                        final double value = GeneralConversions.toFloat(values.get(v));
                        stream.printf(formatBuilder.toString(), value);
                        break;
                    }

                    default:
                        throw new RuntimeException("Kernel#sprintf error");
                }

                v++;
            } else {
                stream.print(c);
            }
        }
    }

    public static void formatPuts(PrintStream stream, List<Object> args) {
        if (args.size() > 0) {
            formatPutsInner(stream, args);
        } else {
            stream.println();
        }
    }

    public static void formatPutsInner(PrintStream stream, List<Object> args) {
        if (args.size() > 0) {
            for (Object arg : args) {
                if (arg instanceof RubyArray) {
                    final RubyArray array = (RubyArray) arg;
                    formatPutsInner(stream, array.asList());
                } else {
                    stream.println(arg);
                }
            }
        }
    }
}
