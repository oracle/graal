package de.hpi.swa.trufflelsp;

import java.util.Objects;

import com.oracle.truffle.api.source.SourceSection;

public class SourceLocation {
    private int startLine;
    private int endLine;
    private int startColumn;
    private int endColumn;

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

}
