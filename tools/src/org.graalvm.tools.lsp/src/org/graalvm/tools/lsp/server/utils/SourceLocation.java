package org.graalvm.tools.lsp.server.utils;

import java.util.Objects;

import org.eclipse.lsp4j.Range;

import com.oracle.truffle.api.source.SourceSection;

public class SourceLocation {
    private int startLine; // 1-based
    private int endLine; // 1-based
    private int startColumn;
    private int endColumn;

    private SourceLocation() {
    }

    public SourceLocation(SourceLocation location) {
        this.startLine = location.startLine;
        this.endLine = location.endLine;
        this.startColumn = location.startColumn;
        this.endColumn = location.endColumn;
    }

    public static SourceLocation from(SourceSection section) {
        SourceLocation location = new SourceLocation();
        location.startLine = section.getStartLine();
        location.endLine = section.getEndLine();
        location.startColumn = section.getStartColumn();
        location.endColumn = section.getEndColumn();

        return location;
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
        if (!(obj instanceof SourceLocation)) {
            return false;
        }

        SourceLocation other = (SourceLocation) obj;
        return startLine == other.startLine && endLine == other.endLine && startColumn == other.startColumn && endColumn == other.endColumn;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startLine, startColumn, endLine, endColumn);
    }

    @Override
    public String toString() {
        return String.format("Location[line=%d-%d, column=%d-%d]", startLine, endLine, startColumn, endColumn);
    }

    public boolean includes(Range range) {
        int otherStartLine = range.getStart().getLine() + 1;
        int otherEndLine = range.getStart().getLine() + 1;
        if (this.startLine < otherStartLine && otherEndLine < this.endLine) {
            // range is fully included and we do not have to check the columns
            return true;
        }
        // TODO(ds) edge cases this.startLine == startLine etc.
        return false;
    }

    public boolean before(Range range) {
        int otherStartLine = range.getStart().getLine() + 1;
        if (this.endLine < otherStartLine) {
            // range is fully behind us in the text
            return true;
        }
        return false;
    }

    public boolean behind(Range range) {
        int otherEndLine = range.getStart().getLine() + 1;
        if (otherEndLine < this.startLine) {
            // range is fully before us in the text
            return true;
        }
        return false;
    }

}
