/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

/**
 * Description of contiguous section of text within a {@link Source} of program code; supports
 * multiple modes of access to the text and its location. A special
 * {@link #createUnavailable(java.lang.String, java.lang.String) null value} should be used for code
 * that is not available from source, e.g language builtins.
 * <p>
 * Equality of instances is defined in terms of equivalent locations: the same start and length in
 * equal source code instances.
 *
 *
 * @see Source#createSection(String, int, int, int, int)
 * @see Source#createSection(String, int, int, int)
 * @see Source#createSection(String, int, int)
 * @see Source#createSection(String, int)
 * @see #createUnavailable
 * @since 0.8 or earlier
 */
public final class SourceSection {

    private static final String UNKNOWN = "<unknown>";
    private final Source source;
    private final String identifier; // deprecated
    private final int startLine;
    private final int startColumn;
    private final int charIndex;
    private final int charLength;
    private final String kind;

    /**
     * Creates a new object representing a contiguous text section within the source code of a guest
     * language program's text.
     * <p>
     * The starting location of the section is specified using two different coordinate:
     * <ul>
     * <li><b>(row, column)</b>: rows and columns are 1-based, so the first character in a source
     * file is at position {@code (1,1)}.</li>
     * <li><b>character index</b>: 0-based offset of the character from the beginning of the source,
     * so the first character in a file is at index {@code 0}.</li>
     * </ul>
     * The <b>newline</b> that terminates each line counts as a single character for the purpose of
     * a character index. The (row,column) coordinates of a newline character should never appear in
     * a text section.
     * <p>
     * Equality of instances is defined in terms of equivalent locations: the same start and length
     * in equal source code instances.
     *
     * @param source object representing the complete source program that contains this section
     * @param identifier an identifier used when printing the section
     * @param startLine the 1-based number of the start line of the section
     * @param startColumn the 1-based number of the start column of the section
     * @param charIndex the 0-based index of the first character of the section
     * @param charLength the length of the section in number of characters
     */
    SourceSection(Source source, String identifier, int startLine, int startColumn, int charIndex, int charLength) {
        this.kind = null;
        this.source = source;
        this.identifier = identifier;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.charIndex = charIndex;
        this.charLength = charLength;
    }

    /**
     * Creates a new object representing a contiguous text section within the source code of a guest
     * language program's text.
     * <p>
     * The starting location of the section is specified using two different coordinate:
     * <ul>
     * <li><b>(row, column)</b>: rows and columns are 1-based, so the first character in a source
     * file is at position {@code (1,1)}.</li>
     * <li><b>character index</b>: 0-based offset of the character from the beginning of the source,
     * so the first character in a file is at index {@code 0}.</li>
     * </ul>
     * The <b>newline</b> that terminates each line counts as a single character for the purpose of
     * a character index. The (row,column) coordinates of a newline character should never appear in
     * a text section.
     * <p>
     * Equality of instances is defined in terms of equivalent locations: the same start and length
     * in equal source code instances.
     *
     * @param source object representing the complete source program that contains this section
     * @param startLine the 1-based number of the start line of the section
     * @param startColumn the 1-based number of the start column of the section
     * @param charIndex the 0-based index of the first character of the section
     * @param charLength the length of the section in number of characters
     */
    SourceSection(Source source, int startLine, int startColumn, int charIndex, int charLength) {
        this.kind = null;
        this.source = source;
        this.identifier = null;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.charIndex = charIndex;
        this.charLength = charLength;
    }

    /**
     * Representation of the source program that contains this section.
     *
     * @return the source object
     * @since 0.8 or earlier
     */
    public Source getSource() {
        return source;
    }

    /**
     * Returns 1-based line number of the first character in this section (inclusive).
     *
     * @return the starting line number
     * @since 0.8 or earlier
     */
    public int getStartLine() {
        return startLine;
    }

    /**
     * Gets a representation of the first line of the section, suitable for a hash key.
     *
     * @return first line of the section
     * @since 0.8 or earlier
     */
    public LineLocation getLineLocation() {
        return source.createLineLocation(startLine);
    }

    /**
     * Returns the 1-based column number of the first character in this section (inclusive).
     *
     * @return the starting column number
     * @since 0.8 or earlier
     */
    public int getStartColumn() {
        return startColumn;
    }

    /**
     * Returns 1-based line number of the last character in this section (inclusive).
     *
     * @return the starting line number
     * @since 0.8 or earlier
     */
    public int getEndLine() {
        if (source.getLength() == 0) {
            return 1;
        }
        return source.getLineNumber(charIndex + charLength - 1);
    }

    /**
     * Returns the 1-based column number of the last character in this section (inclusive).
     *
     * @return the starting column number
     * @since 0.8 or earlier
     */
    public int getEndColumn() {
        return source.getColumnNumber(charIndex + charLength - 1);
    }

    /**
     * Returns the 0-based index of the first character in this section.
     *
     * @return the starting character index
     * @since 0.8 or earlier
     */
    public int getCharIndex() {
        return charIndex;
    }

    /**
     * Returns the length of this section in characters.
     *
     * @return the number of characters in the section
     * @since 0.8 or earlier
     */
    public int getCharLength() {
        return charLength;
    }

    /**
     * Returns the index of the text position immediately following the last character in the
     * section.
     *
     * @return the end position of the section
     * @since 0.8 or earlier
     */
    public int getCharEndIndex() {
        return charIndex + charLength;
    }

    /**
     * Returns terse text describing this source section, typically used for printing the section.
     *
     * @return the identifier of the section
     * @since 0.8 or earlier
     * @deprecated "identifier" is no longer needed, will be removed to save space
     */
    @Deprecated
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Returns the source code fragment described by this section.
     *
     * @return the code as a string, or {@code "<unavailable>"} if the SourceSection was created
     *         using {@link #createUnavailable}.
     * @since 0.8 or earlier
     */
    public String getCode() {
        return source == null ? "<unavailable>" : source.getCode(charIndex, charLength);
    }

    /**
     * Returns a short description of the source section, using just the file name, rather than its
     * full path.
     *
     * @return a short description of the source section formatted as {@code <filename>:<line>}.
     * @since 0.8 or earlier
     */
    public String getShortDescription() {
        if (source == null) {
            return kind == null ? UNKNOWN : kind;
        }
        return String.format("%s:%d", source.getName(), startLine);
    }

    /**
     * Returns an implementation-defined string representation of this source section to be used for
     * debugging purposes only.
     *
     * @see #getCode()
     * @see #getShortDescription()
     * @since 0.8 or earlier
     */
    @Override
    public String toString() {
        if (source == null) {
            return kind == null ? UNKNOWN : kind;
        } else {

            return "source=" + source.getName() + " pos=" + charIndex + " len=" + charLength + " line=" + startLine + " col=" + startColumn +
                            " code=" + getCode().replaceAll("\\n", "\\\\n");
        }
    }

    /** @since 0.8 or earlier */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + charIndex;
        result = prime * result + charLength;
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        result = prime * result + startColumn;
        result = prime * result + startLine;
        return result;
    }

    /** @since 0.8 or earlier */
    @Override
    @SuppressFBWarnings("ES_COMPARING_STRINGS_WITH_EQ")
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof SourceSection)) {
            return false;
        }
        SourceSection other = (SourceSection) obj;
        if (charIndex != other.charIndex) {
            return false;
        }
        if (charLength != other.charLength) {
            return false;
        }
        if (source == null) {
            if (other.source != null) {
                return false;
            }
        } else if (!source.equals(other.source)) {
            return false;
        }
        if (startColumn != other.startColumn) {
            return false;
        }
        if (startLine != other.startLine) {
            return false;
        }

        return true;
    }

    /**
     * Placeholder for source that is unavailable, e.g. for language <em>builtins</em>. The
     * <code>SourceSection</code> created by this method returns <code>null</code> when queried for
     * a {@link #getSource()} - regular source sections created via one of
     * {@link Source#createSection(java.lang.String, int) Source.createSection} methods have a non-
     * <code>null</code> source.
     *
     * @param kind the general category, e.g. "JS builtin"
     * @param name specific name for this section
     * @return source section which is mostly <em>empty</em>
     * @since 0.8 or earlier
     */
    public static SourceSection createUnavailable(String kind, String name) {
        return new SourceSection(kind, name == null ? UNKNOWN : name);
    }

    /** Special representation for unknown source. */
    private SourceSection(String kind, String identifier) {
        this.source = null;
        this.kind = kind;
        this.identifier = identifier;
        this.startLine = -1;
        this.startColumn = -1;
        this.charIndex = -1;
        this.charLength = -1;
    }

}
