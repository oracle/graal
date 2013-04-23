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
    private final int endLine;
    private final int endColumn;

    /**
     * Creates a new object representing a section in the source code of a guest language program.
     * 
     * @param source object representing the source program this is should be a section of
     * @param identifier an identifier used when printing the section
     * @param startLine the index of the start line of the section (inclusive)
     * @param startColumn the index of the start column of the section (inclusive)
     * @param endLine the index of the end line of the section (inclusive)
     * @param endColumn the index of the end column of the section (inclusive)
     */
    public SourceSection(Source source, String identifier, int startLine, int startColumn, int endLine, int endColumn) {
        this.source = source;
        this.identifier = identifier;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
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
     * Returns the index of the end line of this source section (inclusive).
     * 
     * @return the end line
     */
    public final int getEndLine() {
        return endLine;
    }

    /**
     * Returns the index of the end column of this source section (inclusive).
     * 
     * @return the end column
     */
    public final int getEndColumn() {
        return endColumn;
    }

    /**
     * Returns the identifier of this source section that is used for printing the section.
     * 
     * @return the identifier of the section
     */
    public final String getIdentifier() {
        return identifier;
    }
}
