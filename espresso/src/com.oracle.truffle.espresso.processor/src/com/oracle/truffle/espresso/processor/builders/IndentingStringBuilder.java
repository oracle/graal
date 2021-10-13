package com.oracle.truffle.espresso.processor.builders;

import java.util.Collection;

public final class IndentingStringBuilder {
    private static final char NEWLINE = '\n';
    private static final char SPACE = ' ';
    private static final String TAB = "    ";

    private final StringBuilder sb;
    private int indentLevel;
    private boolean lineStart;

    public IndentingStringBuilder(int indentLevel) {
        this.sb = new StringBuilder();
        this.indentLevel = Math.max(indentLevel, 0);
        this.lineStart = true;
    }

    public void raiseIndentLevel() {
        indentLevel++;
    }

    public void lowerIndentLevel() {
        indentLevel--;
        indentLevel = Math.max(indentLevel, 0);
    }

    public void setIndentLevel(int lvl) {
        indentLevel = Math.max(lvl, 0);
    }

    public IndentingStringBuilder append(char c) {
        handleLineStart();
        sb.append(c);
        return this;
    }

    public IndentingStringBuilder append(String str) {
        handleLineStart();
        sb.append(str);
        return this;
    }

    public IndentingStringBuilder appendSpace() {
        handleLineStart();
        sb.append(SPACE);
        return this;
    }

    public IndentingStringBuilder appendSpace(char c) {
        handleLineStart();
        sb.append(c).append(SPACE);
        return this;
    }

    public IndentingStringBuilder appendSpace(String str) {
        handleLineStart();
        sb.append(str).append(SPACE);
        return this;
    }

    public IndentingStringBuilder appendLine() {
        sb.append(NEWLINE);
        lineStart = true;
        return this;
    }

    public IndentingStringBuilder appendLine(char c) {
        handleLineStart();
        return append(c).appendLine();
    }

    public IndentingStringBuilder appendLine(String str) {
        handleLineStart();
        return append(str).appendLine();
    }

    public IndentingStringBuilder appendIndent(int level) {
        for (int i = 0; i < level; i++) {
            sb.append(TAB);
        }
        return this;
    }

    public IndentingStringBuilder join(String delimiter, Collection<String> parts) {
        if (!delimiter.isEmpty() && delimiter.charAt(0) == NEWLINE) {
            return joinLines(parts);
        }

        handleLineStart();

        int i = 0;
        for (String part : parts) {
            sb.append(part);
            if (i != parts.size() - 1) {
                sb.append(delimiter);
            }
            i++;
        }
        return this;
    }

    public IndentingStringBuilder joinLines(Collection<String> parts) {
        for (String part : parts) {
            appendLine(part);
        }
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    private IndentingStringBuilder handleLineStart() {
        if (lineStart) {
            appendIndent(indentLevel);
            lineStart = false;
        }
        return this;
    }
}
