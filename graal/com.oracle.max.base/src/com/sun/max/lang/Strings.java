/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.sun.max.lang;

import java.io.*;
import java.util.*;

/**
 * Additional String-related operations.
 */
public final class Strings {

    private Strings() {
    }

    /**
     * @param stream
     *            the input stream to be read in its entirety and then closed
     * @return the contents of the input stream as a String, with line breaks
     * @throws IOException
     *             as usual
     */
    public static String fromInputStream(InputStream stream) throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        final StringBuffer result = new StringBuffer();
        final String lineSeparator = System.getProperty("line.separator");
        while (true) {
            final String line = reader.readLine();
            if (line == null) {
                stream.close();
                return result.toString();
            }
            result.append(line);
            result.append(lineSeparator);
        }
    }

    public static String firstCharToLowerCase(String s) {
        if (s == null || s.length() == 0) {
            return s;
        }
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }

    public static String firstCharToUpperCase(String s) {
        if (s == null || s.length() == 0) {
            return s;
        }
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public static String times(char ch, int nTimes) {
        if (nTimes <= 0) {
            return "";
        }
        final char[] chars = new char[nTimes];
        for (int i = 0; i < nTimes; i++) {
            chars[i] = ch;
        }
        return new String(chars);
    }

    private static final char[] SPACES;
    static {
        SPACES = new char[200];
        java.util.Arrays.fill(SPACES, ' ');
    }

    public static String spaces(int nSpaces) {
        if (nSpaces <= 0) {
            return "";
        }
        if (nSpaces <= SPACES.length) {
            return new String(SPACES, 0, nSpaces);
        }
        return times(' ', nSpaces);
    }

    /**
     * @return The String {@code s} padded out to {@code length}, if needed, by appending space characters
     */
    public static String padLengthWithSpaces(String s, int length) {
        if (s.length() >= length) {
            return s;
        }
        return s + spaces(length - s.length());
    }

    /**
     * @return The string {@code s} padded out to {@code length}, if needed, by prepending space characters
     */
    public static String padLengthWithSpaces(int length, String s) {
        if (s.length() >= length) {
            return s;
        }
        return spaces(length - s.length()) + s;
    }

    private static final char[] ZEROES;
    static {
        ZEROES = new char[200];
        java.util.Arrays.fill(ZEROES, '0');
    }

    public static String zeroes(int nZeroes) {
        if (nZeroes <= 0) {
            return "";
        }
        if (nZeroes <= ZEROES.length) {
            return new String(ZEROES, 0, nZeroes);
        }
        return times(' ', nZeroes);
    }

    /**
     * @return The String {@code s} padded out to {@code length}, if needed, by appending zero characters
     */
    public static String padLengthWithZeroes(String s, int length) {
        if (s.length() >= length) {
            return s;
        }
        return s + zeroes(length - s.length());
    }

    /**
     * @return The string {@code s} padded out to {@code length}, if needed, by prepending zero characters
     */
    public static String padLengthWithZeroes(int length, String s) {
        if (s.length() >= length) {
            return s;
        }
        return zeroes(length - s.length()) + s;
    }

    /**
     * Finds the index of the first non-escaped instance of {@code c} in {@code s} starting at {@code fromIndex}.
     * The search takes into account that the escape char (i.e. {@code '\'}) may itself be escaped.
     *
     * @return -1 if the char could not be found
     */
    public static int indexOfNonEscapedChar(char c, String s, int fromIndex) {
        int index = s.indexOf(c, fromIndex);
        while (index != -1) {
            if (index > 0 && (s.charAt(index - 1) != '\\') || (index > 1 && s.charAt(index - 2) == '\\')) {
                return index;
            }
            index = s.indexOf(c, index + 1);
        }
        return -1;
    }

    /**
     * Parses a command line into a string array appropriate for calling {@link Runtime#exec(String[])}.
     * The given command line is tokenized around {@link Character#isWhitespace(char) whitespaces}
     * except for sequences of characters enclosed in non-escaped double quotes (after the double
     * quotes are removed).
     */
    public static String[] splitCommand(String command) {
        final List<String> parts = new ArrayList<String>();

        boolean escapedChar = false;
        boolean insideQuotes = false;

        final char[] buffer = new char[command.length()];
        int pos = 0;

        for (int index = 0; index < command.length(); ++index) {
            final char ch = command.charAt(index);
            if (escapedChar) {
                escapedChar = false;
            } else {
                if (ch == '\\') {
                    escapedChar = true;
                } else {
                    if (insideQuotes) {
                        if (ch == '"') {
                            insideQuotes = false;
                            continue;
                        }
                    } else {
                        if (ch == '"') {
                            insideQuotes = true;
                            continue;
                        } else if (Character.isWhitespace(ch)) {
                            if (pos != 0) {
                                parts.add(new String(buffer, 0, pos));
                                pos = 0;
                            }
                            continue;

                        }
                    }
                }
            }
            buffer[pos++] = ch;
        }

        if (insideQuotes) {
            throw new IllegalArgumentException("unclosed quotes");
        }
        if (escapedChar) {
            throw new IllegalArgumentException("command line cannot end with escape char '\\'");
        }
        if (pos != 0) {
            parts.add(new String(buffer, 0, pos));
        }
        return parts.toArray(new String[parts.size()]);
    }

    public static String truncate(String s, int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException();
        }
        if (s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength) + "...";
    }

    /**
     * Capitalizes the first character in a given string.
     *
     * @param string the string to process
     * @param lowercaseTail if true, the remaining characters in {@code string} are converted to lower case
     */
    public static String capitalizeFirst(String string, boolean lowercaseTail) {
        final String tail = string.substring(1);
        return Character.toUpperCase(string.charAt(0)) + (lowercaseTail ? tail.toLowerCase() : tail);
    }

    /**
     * Chops the last {@code count} from a given string.
     *
     * @param s      the string to chop
     * @param count  the number of characters to chop from the end of {@code s}
     * @return the chopped string
     * @throws  IndexOutOfBoundsException  if {@code count < 0} or {@code count > s.length()}
     */
    public static String chopSuffix(String s, int count) {
        return s.substring(0, s.length() - count);
    }

    /**
     * Chops the last {@code suffix.length()} from a given string. Calling this method is
     * equivalent to {@link #chopSuffix(String, int) chop(s, suffix.length())}.
     */
    public static String chopSuffix(String s, String suffix) {
        return chopSuffix(s, suffix.length());
    }

    /**
     * Prepends {@code n} space characters to every line in String {@code lines},
     * including a possibly non-empty line following the final newline.
     * Returns {@code lines} if {@code spaces <= 0}
     */
    public static String indent(String lines, int spaces) {
        return indent(lines, spaces(spaces));
    }

    /**
     * Prepends the String {@code indentation} to every line in String {@code lines},
     * including a possibly non-empty line following the final newline.
     */
    public static String indent(String lines, String indentation) {
        if (lines.length() == 0) {
            return lines;
        }
        final String newLine = "\n";
        if (lines.endsWith(newLine)) {
            return indentation + (lines.substring(0, lines.length() - 1)).replace(newLine, newLine + indentation) + newLine;
        }
        return indentation + lines.replace(newLine, newLine + indentation);
    }

    public static String formatParagraphs(String s, int leftJust, int pindent, int width) {
        final int len = s.length();
        int indent = pindent;
        indent += leftJust;
        int consumed = indent + leftJust;
        final String indstr = space(indent);
        final String ljstr = space(leftJust);
        final StringBuffer buf = new StringBuffer(s.length() + 50);
        buf.append(indstr);
        int lastSp = -1;
        for (int cntr = 0; cntr < len; cntr++) {
            final char c = s.charAt(cntr);
            if (c == '\n') {
                buf.append('\n');
                consumed = indent;
                buf.append(indstr);
                continue;
            } else if (Character.isWhitespace(c)) {
                lastSp = buf.length();
            }
            buf.append(c);
            consumed++;

            if (consumed > width) {
                if (lastSp >= 0) {
                    buf.setCharAt(lastSp, '\n');
                    buf.insert(lastSp + 1, ljstr);
                    consumed = buf.length() - lastSp + leftJust - 1;
                }
            }
        }
        return buf.toString();
    }

    protected static final String[] spacers = {
        "",            // 0
        " ",           // 1
        "  ",          // 2
        "   ",         // 3
        "    ",        // 4
        "     ",       // 5
        "      ",      // 6
        "       ",     // 7
        "        ",    // 8
        "         ",   // 9
        "          ",  // 10
    };

    public static void appendFract(StringBuffer buf, double val, int digits) {
        int cntr = 0;
        for (int radix = 10; cntr < digits; radix = radix * 10, cntr++) {
            if (cntr == 0) {
                buf.append('.');
            }
            final int digit = (int) (val * radix) % 10;
            buf.append((char) (digit + '0'));
        }
    }

    public static String fixedDouble(double fval, int places) {
        if (Double.isInfinite(fval)) {
            return "(inf)";
        }
        if (Double.isNaN(fval)) {
            return "(NaN)";
        }

        final StringBuffer buf = new StringBuffer(places + 5);
        // append the whole part
        final long val = (long) fval;
        buf.append(val);
        // append the fractional part
        final double fract = fval >= 0 ? fval - val : val - fval;
        appendFract(buf, fract, places);

        return buf.toString();
    }

    public static String space(int len) {
        if (len <= 0) {
            return "";
        }
        if (len < spacers.length) {
            return spacers[len];
        }
        return times(' ', len);
    }

    public static void space(StringBuffer buf, int len) {
        int i = 0;
        while (i++ < len) {
            buf.append(' ');
        }
    }

    public static String concat(String first, String second, String separator) {
        if (!first.isEmpty()) {
            return first + separator + second;
        }
        return second;
    }

}
