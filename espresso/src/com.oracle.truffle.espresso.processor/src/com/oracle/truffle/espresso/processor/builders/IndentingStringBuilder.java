/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.processor.builders;

import java.util.Collection;

public final class IndentingStringBuilder {
    private static final char NEWLINE = '\n';
    private static final char SPACE = ' ';
    private static final String TAB = "    ";

    private final StringBuilder sb;
    private int indentLevel;
    private boolean lineStart;

    public IndentingStringBuilder(int indentBy) {
        sb = new StringBuilder();
        indentLevel = Math.max(indentBy, 0);
        lineStart = true;
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
