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
package com.oracle.truffle.api.source;

/**
 * Description of contiguous section of text within a {@link Source} of program code; supports
 * multiple modes of access to the text and its location. A special {@linkplain NullSourceSection
 * null subtype} should be used for code that is not available from source, e.g language builtins.
 *
 * @see Source#createSection(String, int, int, int, int)
 * @see Source#createSection(String, int, int, int)
 * @see Source#createSection(String, int, int)
 * @see Source#createSection(String, int)
 * @see NullSourceSection
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
     * Gets a representation of the first line of the section, suitable for a hash key.
     */
    LineLocation getLineLocation();

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

}
