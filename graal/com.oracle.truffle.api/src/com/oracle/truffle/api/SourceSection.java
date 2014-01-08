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
 * Description of contiguous text section within the source code of a guest language program.
 */
public interface SourceSection {

    /**
     * Returns the object representing the source program that contains this section.
     * 
     * @return the source object
     */
    Source getSource();

    /**
     * Returns 1-based line number of the first character in this source section (inclusive).
     * 
     * @return the starting line number
     */
    int getStartLine();

    /**
     * Returns the 1-based column number of the first character in this source section (inclusive).
     * 
     * @return the starting column number
     */
    int getStartColumn();

    /**
     * Returns the 0-based index of the first character in this source section.
     * <p>
     * The complete text of the source that contains this section can be retrieved via
     * {@link Source#getCode()}.
     * 
     * @return the starting character index
     */
    int getCharIndex();

    /**
     * Returns the length of this source section in characters.
     * <p>
     * The complete text of the source that contains this section can be retrieved via
     * {@link Source#getCode()}.
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
     * Returns the identifier of this source section that is used for printing the section.
     * 
     * @return the identifier of the section
     */
    String getIdentifier();

    /**
     * Returns text of the code represented by this source section.
     * 
     * @return the code as a String object
     */
    String getCode();

    /**
     * Singleton instance with no content.
     */
    SourceSection NULL = new NullSourceSection() {

        public Source getSource() {
            return null;
        }

        public int getStartLine() {
            return 0;
        }

        public int getStartColumn() {
            return 0;
        }

        public int getCharIndex() {
            return 0;
        }

        @Override
        public int getCharLength() {
            return 0;
        }

        public int getCharEndIndex() {
            return 0;
        }

        public String getIdentifier() {
            return null;
        }

        public String getCode() {
            return null;
        }

    };

}
