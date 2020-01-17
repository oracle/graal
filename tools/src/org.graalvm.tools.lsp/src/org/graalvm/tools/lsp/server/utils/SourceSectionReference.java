/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.utils;

import java.util.Objects;

import org.graalvm.tools.lsp.server.types.Position;
import org.graalvm.tools.lsp.server.types.Range;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A light-weight source section reference whose equality is depending only on line and column and
 * not also on the content of a {@link Source} object. Used to create a mapping from source section
 * to {@link CoverageData} and to migrate the coverage data when the user modifies code.
 *
 */
public final class SourceSectionReference {
    private int startLine; // 1-based
    private int endLine; // 1-based
    private int startColumn;
    private int endColumn;

    private SourceSectionReference() {
    }

    public SourceSectionReference(SourceSectionReference section) {
        this.startLine = section.startLine;
        this.endLine = section.endLine;
        this.startColumn = section.startColumn;
        this.endColumn = section.endColumn;
    }

    public static SourceSectionReference from(SourceSection section) {
        SourceSectionReference mutableSection = new SourceSectionReference();
        mutableSection.startLine = section.getStartLine();
        mutableSection.endLine = section.getEndLine();
        mutableSection.startColumn = section.getStartColumn();
        mutableSection.endColumn = section.getEndColumn();

        return mutableSection;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    public void setStartColumn(int startColumn) {
        this.startColumn = startColumn;
    }

    public int getEndColumn() {
        return endColumn;
    }

    public void setEndColumn(int endColumn) {
        this.endColumn = endColumn;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SourceSectionReference)) {
            return false;
        }

        SourceSectionReference other = (SourceSectionReference) obj;
        return startLine == other.startLine && endLine == other.endLine && startColumn == other.startColumn && endColumn == other.endColumn;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startLine, startColumn, endLine, endColumn);
    }

    @Override
    public String toString() {
        return String.format("Location[%d:%d - %d:%d]", startLine, startColumn, endLine, endColumn);
    }

    public boolean includes(Range range) {
        Position start = range.getStart();
        Position end = range.getEnd();
        int otherStartLine = start.getLine() + 1;
        int otherEndLine = end.getLine() + 1;
        if (this.startLine < otherStartLine && otherEndLine < this.endLine) {
            // range is fully included and we do not have to check the columns
            return true;
        }
        int otherStartColumn = start.getCharacter() + 1;
        int otherEndColumn = end.getCharacter() + 1;
        return (startLine < otherStartLine || startLine == otherStartLine && startColumn <= otherStartColumn) &&
                        (otherEndLine < endLine || otherEndLine == endLine && otherEndColumn <= endColumn);
    }

    public boolean before(Range range) {
        Position start = range.getStart();
        int otherStartLine = start.getLine() + 1;
        int otherStartColumn = start.getCharacter() + 1;
        if (this.endLine < otherStartLine || this.endLine == otherStartLine && this.endColumn < otherStartColumn) {
            // range is fully behind us in the text
            return true;
        }
        return false;
    }

    public boolean behind(Range range) {
        Position end = range.getEnd();
        int otherEndLine = end.getLine() + 1;
        int otherEndColumn = end.getCharacter() + 1;
        if (otherEndLine < this.startLine || otherEndLine == this.startLine && otherEndColumn < this.startColumn) {
            // range is fully before us in the text
            return true;
        }
        return false;
    }

}
