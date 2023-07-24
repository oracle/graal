/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.core;

/**
 * An output writer that manages indentation and optional string prefixes that are prepended to each
 * written line. The writer holds the current {@link OptionValues} for convenience.
 */
public abstract class Writer {
    /**
     * The current level of indentation.
     */
    private int indentLevel;

    /**
     * Whether the indentation string has already been written for the current line.
     */
    private boolean indentWritten;

    /**
     * The prefix to be written in front of each line after the indentation.
     */
    private String prefix;

    /**
     * The current option values.
     */
    private final OptionValues optionValues;

    private Writer(OptionValues optionValues) {
        this.indentLevel = 0;
        this.indentWritten = false;
        this.prefix = null;
        this.optionValues = optionValues;
    }

    /**
     * Writes the given string to the output.
     *
     * @param output the string to be written
     */
    protected abstract void writeImpl(String output);

    /**
     * Writes a line separator to the output.
     */
    protected abstract void writelnImpl();

    /**
     * Creates and returns a writer that writes to the standard output. Uses the system-dependent
     * line separator.
     *
     * @param optionValues the current option values
     * @return a writer to the standard output
     */
    public static Writer standardOutput(OptionValues optionValues) {
        return new Writer(optionValues) {
            @Override
            protected void writeImpl(String output) {
                System.out.print(output);
            }

            @Override
            protected void writelnImpl() {
                System.out.println();
            }
        };
    }

    /**
     * A writer that appends all written output to a string builder. Uses {@code '\n'} as the line
     * separator to match the behavior of Java text blocks.
     */
    public static final class StringBuilderWriter extends Writer {

        private final StringBuilder stringBuilder;

        public StringBuilderWriter(OptionValues optionValues) {
            super(optionValues);
            this.stringBuilder = new StringBuilder();
        }

        @Override
        protected void writeImpl(String output) {
            stringBuilder.append(output);
        }

        @Override
        protected void writelnImpl() {
            stringBuilder.append('\n');
        }

        /**
         * Returns all written output as a string.
         */
        public String getOutput() {
            return stringBuilder.toString();
        }
    }

    /**
     * Creates and returns a writer that appends all output to a string builder.
     *
     * @param optionValues the current option values
     * @return a writer that writes to a string builder
     */
    public static StringBuilderWriter stringBuilder(OptionValues optionValues) {
        return new StringBuilderWriter(optionValues);
    }

    /**
     * Write a string to the output with the current indentation level without a line separator at
     * the end.
     *
     * @param output the string to be written
     */
    public void write(String output) {
        printIndentIfNeeded();
        writeImpl(output);
    }

    /**
     * Writes a string to the output with the current indentation level with a line separator at the
     * end.
     *
     * @param output the string to be written
     */
    public void writeln(String output) {
        printIndentIfNeeded();
        writeImpl(output);
        writeln();
    }

    /**
     * Writes a line separator to the output.
     */
    public void writeln() {
        writelnImpl();
        indentWritten = false;
    }

    /**
     * Gets the current indentation level.
     */
    public int getIndentLevel() {
        return indentLevel;
    }

    /**
     * Sets the current indentation level to the provided value.
     *
     * @param newIndentLevel the new indentation level
     */
    public void setIndentLevel(int newIndentLevel) {
        assert newIndentLevel >= 0 : "the indent level must be non-negative";
        indentLevel = newIndentLevel;
    }

    /**
     * Increases the current indentation level by one.
     */
    public void increaseIndent() {
        ++indentLevel;
    }

    /**
     * Increases the indentation level by a non-negative delta.
     *
     * @param delta the values that is added to the current indentation level
     */
    public void increaseIndent(int delta) {
        assert delta >= 0;
        indentLevel += delta;
    }

    /**
     * Decreases the current indentation level by one.
     */
    public void decreaseIndent() {
        assert indentLevel > 0;
        --indentLevel;
    }

    /**
     * Decreases the indentation level by a non-negative delta.
     *
     * @param delta the value that is subtracted from the current indentation level
     */
    public void decreaseIndent(int delta) {
        assert delta >= 0 && indentLevel - delta >= 0;
        indentLevel -= delta;
    }

    /**
     * Sets a string prefix that is prepended to each written line after the indentation.
     *
     * @param prefix the string prefix that will be prepended to each written line
     */
    public void setPrefixAfterIndent(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Clears the set prefix that is prepended to each written line after the indentation.
     */
    public void clearPrefixAfterIndent() {
        prefix = null;
    }

    /**
     * Gets the current option values.
     */
    public OptionValues getOptionValues() {
        return optionValues;
    }

    private void printIndentIfNeeded() {
        if (indentWritten) {
            return;
        }
        for (int i = 0; i < indentLevel; ++i) {
            writeImpl("    ");
        }
        if (prefix != null) {
            writeImpl(prefix);
        }
        indentWritten = true;
    }
}
