/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

/**
 * Represents a section in the source code of a guest language program.
 */
public class SourceSection {

    private final Source source;
    private final String identifier;
    private final int startLine;
    private final int startColumn;
    private final int charIndex;
    private final int charLength;

    /**
     * Creates a new object representing a section in the source code of a guest language program.
     * 
     * @param source object representing the source program this is should be a section of
     * @param identifier an identifier used when printing the section
     * @param startLine the index of the start line of the section
     * @param startColumn the index of the start column of the section
     * @param charIndex the index of the first character of the section
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
     * Returns the source object representing the source program this is a section of.
     * 
     * @return the source object
     */
    public final Source getSource() {
        return source;
    }

    /**
     * Returns the index of the start line of this source section (inclusive).
     * 
     * @return the start line
     */
    public final int getStartLine() {
        return startLine;
    }

    /**
     * Returns the index of the start column of this source section (inclusive).
     * 
     * @return the start column
     */
    public final int getStartColumn() {
        return startColumn;
    }

    /**
     * Returns the index of the first character of this section. All characters of the source can be
     * retrieved via the {@link Source#getCode()} method.
     * 
     * @return the character index
     */
    public final int getCharIndex() {
        return charIndex;
    }

    /**
     * Returns the length of this section in characters. All characters of the source can be
     * retrieved via the {@link Source#getCode()} method.
     * 
     * @return the character length
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
     * Returns the code represented by this code section.
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

}
