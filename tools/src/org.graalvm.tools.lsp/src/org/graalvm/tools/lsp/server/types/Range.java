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
import java.util.Objects;

/**
 * A range in a text document expressed as (zero-based) start and end positions.
 *
 * If you want to specify a range that contains a line including the line ending character(s) then
 * use an end position denoting the start of the next line. For example: ```ts { start: { line: 5,
 * character: 23 } end : { line 6, character : 0 } } ```
 */
public class Range extends JSONBase {

    Range(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The range's start position.
     */
    public Position getStart() {
        return new Position(jsonData.getJSONObject("start"));
    }

    public Range setStart(Position start) {
        jsonData.put("start", start.jsonData);
        return this;
    }

    /**
     * The range's end position.
     */
    public Position getEnd() {
        return new Position(jsonData.getJSONObject("end"));
    }

    public Range setEnd(Position end) {
        jsonData.put("end", end.jsonData);
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
        Range other = (Range) obj;
        if (!Objects.equals(this.getStart(), other.getStart())) {
            return false;
        }
        if (!Objects.equals(this.getEnd(), other.getEnd())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.getStart());
        hash = 23 * hash + Objects.hashCode(this.getEnd());
        return hash;
    }

    /**
     * Create a new Range liternal.
     *
     * @param start The range's start position.
     * @param end The range's end position.
     */
    public static Range create(Position start, Position end) {
        final JSONObject json = new JSONObject();
        json.put("start", start.jsonData);
        json.put("end", end.jsonData);
        return new Range(json);
    }

    /**
     * Create a new Range liternal.
     *
     * @param startLine The start line number.
     * @param startCharacter The start character.
     * @param endLine The end line number.
     * @param endCharacter The end character.
     */
    public static Range create(Integer startLine, Integer startCharacter, Integer endLine, Integer endCharacter) {
        return create(Position.create(startLine, startCharacter), Position.create(endLine, endCharacter));
    }

    public static Range create(Object one, Object two, Integer three, Integer four) {
        if (one instanceof Integer && two instanceof Integer && three != null && four != null) {
            return create((Integer) one, (Integer) two, three, four);
        }
        if (one instanceof Position && two instanceof Position) {
            return create((Position) one, (Position) two);
        }
        throw new IllegalArgumentException("Range.create called with invalid arguments");
    }
}
