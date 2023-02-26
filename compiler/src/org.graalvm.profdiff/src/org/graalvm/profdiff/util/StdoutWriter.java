/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.util;

import org.graalvm.profdiff.core.VerbosityLevel;

/**
 * An output writer that manages indentation and writes to the standard output. The writer holds the
 * current {@link VerbosityLevel} for convenience.
 */
public class StdoutWriter implements Writer {
    /**
     * The current level of indentation.
     */
    private int indentLevel = 0;

    /**
     * Whether the indentation string has already been written for the current line.
     */
    private boolean indentWritten = false;

    /**
     * The prefix to be written in front of each line after the indentation.
     */
    private String prefix;

    /**
     * The current verbosity level.
     */
    private final VerbosityLevel verbosityLevel;

    /**
     * Constructs a writer to the standard output.
     *
     * @param verbosityLevel the current verbosity level
     */
    public StdoutWriter(VerbosityLevel verbosityLevel) {
        this.verbosityLevel = verbosityLevel;
    }

    @Override
    public void write(String output) {
        printIndentIfNeeded();
        System.out.print(output);
    }

    @Override
    public void writeln(String output) {
        printIndentIfNeeded();
        System.out.print(output);
        writeln();
    }

    @Override
    public void writeln() {
        System.out.println();
        indentWritten = false;
    }

    @Override
    public int getIndentLevel() {
        return indentLevel;
    }

    @Override
    public void setIndentLevel(int newIndentLevel) {
        assert newIndentLevel >= 0;
        indentLevel = newIndentLevel;
    }

    @Override
    public void increaseIndent() {
        ++indentLevel;
    }

    @Override
    public void increaseIndent(int delta) {
        assert delta >= 0;
        indentLevel += delta;
    }

    @Override
    public void decreaseIndent() {
        assert indentLevel > 0;
        --indentLevel;
    }

    @Override
    public void decreaseIndent(int delta) {
        assert delta >= 0 && indentLevel - delta >= 0;
        indentLevel -= delta;
    }

    @Override
    public void setPrefixAfterIndent(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void clearPrefixAfterIndent() {
        prefix = null;
    }

    @Override
    public VerbosityLevel getVerbosityLevel() {
        return verbosityLevel;
    }

    private void printIndentIfNeeded() {
        if (indentWritten) {
            return;
        }
        for (int i = 0; i < indentLevel; ++i) {
            System.out.print("    ");
        }
        if (prefix != null) {
            System.out.print(prefix);
        }
        indentWritten = true;
    }
}
