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
package com.sun.max.io;

import java.io.*;

import com.sun.max.program.*;

/**
 * A line oriented character writer that indents line output on the left.
 */
public class IndentWriter {

    private final PrintWriter writer;
    private int lineCount;

    /**
     * Gets an IndentWriter that wraps the {@linkplain Trace#stream() trace stream}.
     * @return
     */
    public static IndentWriter traceStreamWriter() {
        return new IndentWriter(new OutputStreamWriter(Trace.stream()));
    }

    public IndentWriter(Writer writer) {
        this.writer = (writer instanceof PrintWriter) ? (PrintWriter) writer : new PrintWriter(writer);
    }

    public void close() {
        writer.close();
    }

    public void flush() {
        writer.flush();
    }

    private int indentation = 4;

    public int indentation() {
        return indentation;
    }

    public void setIndentation(int indentation) {
        this.indentation = indentation;
    }

    private int prefix;

    public void indent() {
        prefix += indentation;
    }

    public void outdent() {
        prefix -= indentation;
        assert prefix >= 0;
    }

    private boolean isCurrentLineIndented;

    private void writeIndentation() {
        if (!isCurrentLineIndented) {
            for (int i = 0; i < prefix; i++) {
                writer.print(" ");
            }
            isCurrentLineIndented = true;
        }
    }

    public void printSpaces(int width) {
        for (int i = 0; i < width; i++) {
            writer.print(" ");
        }
    }

    public void printFixedWidth(String s, int width) {
        assert width > 0 : "width must be positive";
        String text = s;
        if (text.length() + 1 > width) {
            if (width - 4 > 0) {
                text = s.substring(0, width - 4) + "...";
            } else {
                text = s.substring(0, width);
            }
        }
        writer.print(text);
        printSpaces(width - text.length());
    }

    public void print(String s) {
        writeIndentation();
        writer.print(s);
    }

    public void println() {
        writer.println();
        isCurrentLineIndented = false;
        ++lineCount;
    }

    public void println(String s) {
        writeIndentation();
        writer.println(s);
        isCurrentLineIndented = false;
        ++lineCount;
    }

    public void printLines(InputStream inputStream) {
        printLines(new InputStreamReader(inputStream));
    }

    public void printLines(Reader reader) {
        final BufferedReader bufferedReader = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        String line;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                println(line);
            }
        } catch (IOException e) {
            ProgramWarning.message(e.toString());
        }
    }

    public int lineCount() {
        return lineCount;
    }
}
