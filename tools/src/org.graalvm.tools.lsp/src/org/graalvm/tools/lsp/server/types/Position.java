/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.types;

import com.oracle.truffle.tools.utils.json.JSONObject;

/**
 * Position in a text document expressed as zero-based line and character offset. The offsets are
 * based on a UTF-16 string representation.
 *
 * Positions are line end character agnostic. So you can not specify a position that denotes `\r|\n`
 * or `\n|` where `|` represents the character offset.
 */
public class Position extends JSONBase {

    Position(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Line position in a document (zero-based). If a line number is greater than the number of
     * lines in a document, it defaults back to the number of lines in the document. If a line
     * number is negative, it defaults to 0.
     */
    public int getLine() {
        return jsonData.getInt("line");
    }

    public Position setLine(int line) {
        jsonData.put("line", line);
        return this;
    }

    /**
     * Character offset on a line in a document (zero-based). Assuming that the line is represented
     * as a string, the `character` value represents the gap between the `character` and `character
     * + 1`.
     *
     * If the character value is greater than the line length it defaults back to the line length.
     * If a line number is negative, it defaults to 0.
     */
    public int getCharacter() {
        return jsonData.getInt("character");
    }

    public Position setCharacter(int character) {
        jsonData.put("character", character);
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        Position other = (Position) obj;
        if (this.getLine() != other.getLine()) {
            return false;
        }
        if (this.getCharacter() != other.getCharacter()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + Integer.hashCode(this.getLine());
        hash = 89 * hash + Integer.hashCode(this.getCharacter());
        return hash;
    }

    /**
     * Creates a new Position literal from the given line and character.
     *
     * @param line The position's line.
     * @param character The position's character.
     */
    public static Position create(int line, int character) {
        final JSONObject json = new JSONObject();
        json.put("line", line);
        json.put("character", character);
        return new Position(json);
    }
}
