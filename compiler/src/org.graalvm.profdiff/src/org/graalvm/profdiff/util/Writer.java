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
 * An output writer that manages indentation and optional string prefixes that are prepended to each
 * written line. The writer holds the current {@link VerbosityLevel} for convenience.
 */
public interface Writer {
    /**
     * Write a string to the output with the current indentation level without a linefeed at the
     * end.
     *
     * @param output the string to be written
     */
    void write(String output);

    /**
     * Writes a string to the output with the current indentation level with a linefeed at the end.
     *
     * @param output the string to be written
     */
    void writeln(String output);

    /**
     * Writes a linefeed to the output.
     */
    void writeln();

    /**
     * Gets the current indentation level.
     */
    int getIndentLevel();

    /**
     * Sets the current indentation level to the provided value.
     *
     * @param newIndentLevel the new indentation level
     */
    void setIndentLevel(int newIndentLevel);

    /**
     * Increases the current indentation level by one.
     */
    void increaseIndent();

    /**
     * Increases the indentation level by a non-negative delta.
     *
     * @param delta the values that is added to the current indentation level
     */
    void increaseIndent(int delta);

    /**
     * Decreases the current indentation level by one.
     */
    void decreaseIndent();

    /**
     * Decreases the indentation level by a non-negative delta.
     *
     * @param delta the value that is subtracted from the current indentation level
     */
    void decreaseIndent(int delta);

    /**
     * Sets a string prefix that is prepended to each written line after the indentation.
     *
     * @param prefix the string prefix that will be prepended to each written line
     */
    void setPrefixAfterIndent(String prefix);

    /**
     * Clears the set prefix that is prepended to each written line after the indentation.
     */
    void clearPrefixAfterIndent();

    /**
     * Gets the current verbosity level.
     */
    VerbosityLevel getVerbosityLevel();
}
