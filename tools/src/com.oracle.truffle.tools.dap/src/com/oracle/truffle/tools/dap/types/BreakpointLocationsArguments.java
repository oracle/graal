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
package com.oracle.truffle.tools.dap.types;

import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.Objects;

/**
 * Arguments for 'breakpointLocations' request.
 */
public class BreakpointLocationsArguments extends JSONBase {

    BreakpointLocationsArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The source location of the breakpoints; either 'source.path' or 'source.reference' must be
     * specified.
     */
    public Source getSource() {
        return new Source(jsonData.getJSONObject("source"));
    }

    public BreakpointLocationsArguments setSource(Source source) {
        jsonData.put("source", source.jsonData);
        return this;
    }

    /**
     * Start line of range to search possible breakpoint locations in. If only the line is
     * specified, the request returns all possible locations in that line.
     */
    public int getLine() {
        return jsonData.getInt("line");
    }

    public BreakpointLocationsArguments setLine(int line) {
        jsonData.put("line", line);
        return this;
    }

    /**
     * Optional start column of range to search possible breakpoint locations in. If no start column
     * is given, the first column in the start line is assumed.
     */
    public Integer getColumn() {
        return jsonData.has("column") ? jsonData.getInt("column") : null;
    }

    public BreakpointLocationsArguments setColumn(Integer column) {
        jsonData.putOpt("column", column);
        return this;
    }

    /**
     * Optional end line of range to search possible breakpoint locations in. If no end line is
     * given, then the end line is assumed to be the start line.
     */
    public Integer getEndLine() {
        return jsonData.has("endLine") ? jsonData.getInt("endLine") : null;
    }

    public BreakpointLocationsArguments setEndLine(Integer endLine) {
        jsonData.putOpt("endLine", endLine);
        return this;
    }

    /**
     * Optional end column of range to search possible breakpoint locations in. If no end column is
     * given, then it is assumed to be in the last column of the end line.
     */
    public Integer getEndColumn() {
        return jsonData.has("endColumn") ? jsonData.getInt("endColumn") : null;
    }

    public BreakpointLocationsArguments setEndColumn(Integer endColumn) {
        jsonData.putOpt("endColumn", endColumn);
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
        BreakpointLocationsArguments other = (BreakpointLocationsArguments) obj;
        if (!Objects.equals(this.getSource(), other.getSource())) {
            return false;
        }
        if (this.getLine() != other.getLine()) {
            return false;
        }
        if (!Objects.equals(this.getColumn(), other.getColumn())) {
            return false;
        }
        if (!Objects.equals(this.getEndLine(), other.getEndLine())) {
            return false;
        }
        if (!Objects.equals(this.getEndColumn(), other.getEndColumn())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        hash = 23 * hash + Objects.hashCode(this.getSource());
        hash = 23 * hash + Integer.hashCode(this.getLine());
        if (this.getColumn() != null) {
            hash = 23 * hash + Integer.hashCode(this.getColumn());
        }
        if (this.getEndLine() != null) {
            hash = 23 * hash + Integer.hashCode(this.getEndLine());
        }
        if (this.getEndColumn() != null) {
            hash = 23 * hash + Integer.hashCode(this.getEndColumn());
        }
        return hash;
    }

    public static BreakpointLocationsArguments create(Source source, Integer line) {
        final JSONObject json = new JSONObject();
        json.put("source", source.jsonData);
        json.put("line", line);
        return new BreakpointLocationsArguments(json);
    }
}
