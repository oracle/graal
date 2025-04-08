/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage;

import static java.util.stream.Collectors.joining;

import java.io.PrintWriter;
import java.util.LinkedHashMap;

import jdk.graal.compiler.core.common.NumUtil;

/**
 * Generates a source map according to the <a href="https://sourcemaps.info/spec.html">Source Map
 * Revision 3 Proposal</a>. This builder supports and always emits the following fields:
 * {@code version}, {@code sourceRoot}, {@code sources}, {@code names}, {@code mappings}. No other
 * fields are supported (e.g. {@code file}, {@code sourcesContent}, {@code x_google_linecount}).
 *
 * Field {@code version} is always {@code 3}. Field {@code sourceRoot} is the value passed to the
 * constructor, unchanged. Fields {@code sources}, {@code names}, {@code mappings} contain
 * information collected from calls to {@link #endLine()},
 * {@link #markSourceLocation(int, String, int, int)}, {@link #endMark(int)}, and
 * {@link #markSymbol(int, String)}.
 *
 * While the source map format uses relative line and column numbers, the methods of this class
 * expect absolute numbers. It translates them internally to be relative to the previous call.
 */
public final class SourceMapBuilder {
    // The value of the "version" JSON field.
    private static final int VERSION = 3;

    // The value of the "sourceRoot" JSON field.
    private final String sourceRoot;
    // Builder for the "sources" JSON field. Maps the elements of the array to their indices.
    private final LinkedHashMap<String, Integer> sources = new LinkedHashMap<>();
    // Builder for the "names" JSON field. Maps the elements of the array to their indices.
    private final LinkedHashMap<String, Integer> names = new LinkedHashMap<>();
    // Builder for the "mappings" JSON field.
    private final Base64VlqListBuilder mappings = new Base64VlqListBuilder();

    // Used to convert absolute indices to relative.
    private int lastColumn = 0;
    private int lastSrcFileIndex = 0;
    private int lastSrcLine = 0;
    private int lastSrcColumn = 0;
    private int lastSrcNameIndex = 0;
    // True if a comma should be added before the next element of "mappings".
    private boolean shouldAddComma = false;
    // True if the currently last element of "mappings" can be extended by a symbol name.
    private boolean canExtendItem = false;

    /**
     * Creates a new empty source map builder.
     *
     * @param sourceRoot The value of the {@code sourceRoot} field in the source map.
     */
    public SourceMapBuilder(String sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    /**
     * Prints the source map in JSON format. Does not change the state of this builder.
     *
     * @param out The print writer to print the JSON to.
     */
    public void printTo(PrintWriter out) {
        out.println("{");
        out.printf("\t\"version\": %d,", VERSION).println();
        out.printf("\t\"sourceRoot\": \"%s\",", sourceRoot).println();
        out.printf("\t\"sources\": %s,", makeJsonStringList(sources)).println();
        out.printf("\t\"names\": %s,", makeJsonStringList(names)).println();
        out.printf("\t\"mappings\": \"%s\"", mappings).println();
        out.println("}");
    }

    private static String makeJsonStringList(LinkedHashMap<String, ?> map) {
        // Example outputs: [], ["foo"], ["foo", "bar"]
        return map.isEmpty() ? "[]" : map.keySet().stream().collect(joining("\", \"", "[\"", "\"]"));
    }

    /**
     * Ends the list of mappings associated with the current line of output and starts a new one.
     */
    public void endLine() {
        mappings.append(';');
        lastColumn = 0;
        shouldAddComma = false;
        canExtendItem = false;
    }

    /**
     * Marks the output starting at the current position as mapping to the specified source
     * location. Current position is determined by the parameter {@code column} and number of
     * previous {@link #endLine()} calls.
     *
     * @param column Zero-based column index within the current line of output.
     * @param srcFile Input file name.
     * @param srcLine Zero-based line number in {@code srcFile}.
     * @param srcColumn Zero-based column index in {@code srcFile:srcLine}.
     */
    public void markSourceLocation(int column, String srcFile, int srcLine, int srcColumn) {
        int srcFileIndex = addAndGetIndex(sources, srcFile);

        if (shouldAddComma) {
            mappings.append(',');
        } else {
            shouldAddComma = true;
        }

        mappings.append(column - lastColumn);
        mappings.append(srcFileIndex - lastSrcFileIndex);
        mappings.append(srcLine - lastSrcLine);
        mappings.append(srcColumn - lastSrcColumn);

        lastColumn = column;
        lastSrcFileIndex = srcFileIndex;
        lastSrcLine = srcLine;
        lastSrcColumn = srcColumn;

        canExtendItem = true;
    }

    /**
     * Marks the output starting at the current position as not mapping to any source file. Current
     * position is determined by the parameter {@code column} and number of previous
     * {@link #endLine()} calls.
     *
     * Source positions after this mark (and before any following ones) are usually not translated
     * by the consumers (e.g. stack trace elements point to the generated JavaScript file). Use this
     * to terminate the effect of any previous marks.
     *
     * @param column Zero-based column index within the current line of output.
     */
    public void endMark(int column) {
        if (shouldAddComma) {
            mappings.append(',');
        } else {
            shouldAddComma = true;
        }

        mappings.append(column - lastColumn);

        lastColumn = column;

        canExtendItem = false;
    }

    /**
     * Marks the output starting at the current position as mapping to specified symbol (variable or
     * method). Current position is determined by the parameter {@code column} and number of
     * previous {@link #endLine()} calls.
     *
     * @param column Zero-based column index within the current line of output.
     * @param srcName The name of the symbol.
     */
    public void markSymbol(int column, String srcName) {
        int srcNameIndex = addAndGetIndex(names, srcName);

        if (canExtendItem && column == lastColumn) {
            mappings.append(srcNameIndex - lastSrcNameIndex);
            lastSrcNameIndex = srcNameIndex;
            canExtendItem = false;
            return;
        }

        if (shouldAddComma) {
            mappings.append(',');
        } else {
            shouldAddComma = true;
        }

        mappings.append(column - lastColumn);
        mappings.append(/* srcFileIndex */ 0);
        mappings.append(/* srcLine */ 0);
        mappings.append(/* srcColumn */ 0);
        mappings.append(srcNameIndex - lastSrcNameIndex);

        lastColumn = column;
        lastSrcNameIndex = srcNameIndex;

        canExtendItem = false;
    }

    private static int addAndGetIndex(LinkedHashMap<String, Integer> map, String element) {
        int newIndex = map.size();
        Integer oldIndex = map.putIfAbsent(element, newIndex);
        return oldIndex != null ? oldIndex : newIndex;
    }

    // Builds a string containing Base64 VLQs (variable-length quantities), as described in the
    // source maps specification.
    public static final class Base64VlqListBuilder {
        private static final String BASE64_DIGITS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        private static final int BITS_PER_DIGIT = 5;
        private static final int DIGIT_MASK = (1 << BITS_PER_DIGIT) - 1;
        private static final int BITS_IN_FIRST_DIGIT = 4;
        private static final int FIRST_DIGIT_MASK = (1 << BITS_IN_FIRST_DIGIT) - 1;
        private static final int CONTINUATION = 1 << BITS_PER_DIGIT;

        private final StringBuilder content = new StringBuilder();

        public void append(int i) {
            assert i != Integer.MIN_VALUE : "Integer.MIN_VALUE cannot be appended";

            int signBit = -(i >> 31);
            // we don't care about overflow for the digit count
            int r = NumUtil.unsafeAbs(i);

            int digit = (r & FIRST_DIGIT_MASK) << 1 | signBit;
            r >>= BITS_IN_FIRST_DIGIT;

            while (r != 0) {
                appendDigit(digit | CONTINUATION);
                digit = r & DIGIT_MASK;
                r >>= BITS_PER_DIGIT;
            }

            appendDigit(digit);
        }

        public void append(char c) {
            content.append(c);
        }

        private void appendDigit(int digitValue) {
            content.append(BASE64_DIGITS.charAt(digitValue));
        }

        @Override
        public String toString() {
            return content.toString();
        }
    }
}
