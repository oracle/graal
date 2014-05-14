/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

/**
 * Description of contiguous section of text within a {@link Source} of program code.
 *
 * The starting location of the section can be described using two different coordinates:
 * <ul>
 * <li>{@code (startLine, startColumn)}: rows and columns are 1-based, so the first character in a
 * source file is at position {@code (1,1)}. {@code Tab} characters are counted as occupying one
 * column.</li>
 * <li><b>character index</b>: 0-based offset of the character from the beginning of the source, so
 * the first character in a file is at index {@code 0}.</li>
 * </ul>
 * The {@code Newline} that terminates each line counts as a single character for the purpose of a
 * character index and when counting the length of text. The {@code (line,column)} coordinates of a
 * {@code Newline} should never appear in a text section.
 * <p>
 * If the final character of source is not a {@code Newline}, the final characters of the text are
 * still considered to be a line ("unterminated").
 * <p>
 *
 * @see Source#createSection(String, int, int, int, int)
 * @see Source#createSection(String, int, int, int)
 * @see Source#createSection(String, int, int)
 */
public interface SourceSection {

    // TODO support alternate text representations/encodings

    /**
     * Representation of the source program that contains this section.
     *
     * @return the source object
     */
    Source getSource();

    /**
     * Returns 1-based line number of the first character in this section (inclusive).
     *
     * @return the starting line number
     */
    int getStartLine();

    /**
     * Returns the 1-based column number of the first character in this section (inclusive).
     *
     * @return the starting column number
     */
    int getStartColumn();

    /**
     * Returns the 0-based index of the first character in this section.
     *
     * @return the starting character index
     */
    int getCharIndex();

    /**
     * Returns the length of this section in characters.
     *
     * @return the number of characters in the section
     */
    int getCharLength();

    /**
     * Returns the index of the text position immediately following the last character in the
     * section.
     *
     * @return the end position of the section
     */
    int getCharEndIndex();

    /**
     * Returns terse text describing this source section, typically used for printing the section.
     *
     * @return the identifier of the section
     */
    String getIdentifier();

    /**
     * Returns text described by this section.
     *
     * @return the code as a String object
     */
    String getCode();

    /**
     * Returns a short description of the source section, using just the file name, rather than its
     * full path.
     *
     * @return a short description of the source section
     */
    String getShortDescription();

    /**
     * Singleton instance with no content.
     */
    SourceSection NULL = new NullSourceSection() {

        @Override
        public Source getSource() {
            return null;
        }

        @Override
        public int getStartLine() {
            return 0;
        }

        @Override
        public int getStartColumn() {
            return 0;
        }

        @Override
        public int getCharIndex() {
            return 0;
        }

        @Override
        public int getCharLength() {
            return 0;
        }

        @Override
        public int getCharEndIndex() {
            return 0;
        }

        @Override
        public String getIdentifier() {
            return null;
        }

        @Override
        public String getCode() {
            return null;
        }

        @Override
        public String getShortDescription() {
            return "short";
        }

    };

}
