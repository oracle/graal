/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Represents a contiguous text section within the source code of a guest language program.
 */
public class SourceSection {

    private final Source source;
    private final String identifier;
    private final int startLine;
    private final int startColumn;
    private final int charIndex;
    private final int charLength;

    /**
     * Creates a new object representing a contiguous text section within the source code of a guest
     * language program.
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
     * 
     * @param source object representing the complete source program that contains this section
     * @param identifier an identifier used when printing the section
     * @param startLine the 1-based number of the start line of the section
     * @param startColumn the 1-based number of the start column of the section
     * @param charIndex the 0-based index of the first character of the section
     * @param charLength the length of the section in number of characters
     */
    public SourceSection(Source source, String identifier, int startLine, int startColumn, int charIndex, int charLength) {
        this.source = source;
        this.identifier = identifier;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.charIndex = charIndex;
        this.charLength = charLength;
    }

    /**
     * Returns the object representing the source program that contains this section.
     * 
     * @return the source object
     */
    public final Source getSource() {
        return source;
    }

    /**
     * Returns 1-based line number of the first character in this source section (inclusive).
     * 
     * @return the starting line number
     */
    public final int getStartLine() {
        return startLine;
    }

    /**
     * Returns the 1-based column number of the first character in this source section (inclusive).
     * 
     * @return the starting column number
     */
    public final int getStartColumn() {
        return startColumn;
    }

    /**
     * Returns the 0-based index of the first character in this source section.
     * <p>
     * The complete text of the source that contains this section can be retrieved via
     * {@link Source#getCode()}.
     * 
     * @return the starting character index
     */
    public final int getCharIndex() {
        return charIndex;
    }

    /**
     * Returns the length of this source section in characters.
     * <p>
     * The complete text of the source that contains this section can be retrieved via
     * {@link Source#getCode()}.
     * 
     * @return the number of characters in the section
     */
    public final int getCharLength() {
        return charLength;
    }

    /**
     * Returns the identifier of this source section that is used for printing the section.
     * 
     * @return the identifier of the section
     */
    public final String getIdentifier() {
        return identifier;
    }

    /**
     * Returns text of the code represented by this source section.
     * 
     * @return the code as a String object
     */
    public final String getCode() {
        return getSource().getCode().substring(charIndex, charIndex + charLength);
    }

    @Override
    public String toString() {
        return String.format("%s:%d", source.getName(), startLine);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + charIndex;
        result = prime * result + charLength;
        result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        result = prime * result + startColumn;
        result = prime * result + startLine;
        return result;
    }

    @Override
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
        if (identifier == null) {
            if (other.identifier != null) {
                return false;
            }
        } else if (!identifier.equals(other.identifier)) {
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

}
