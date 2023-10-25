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

import org.graalvm.shadowed.org.json.JSONObject;

import java.util.Objects;

/**
 * Properties of a breakpoint location returned from the 'breakpointLocations' request.
 */
public class BreakpointLocation extends JSONBase {

    BreakpointLocation(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Start line of breakpoint location.
     */
    public int getLine() {
        return jsonData.getInt("line");
    }

    public BreakpointLocation setLine(int line) {
        jsonData.put("line", line);
        return this;
    }

    /**
     * Optional start column of breakpoint location.
     */
    public Integer getColumn() {
        return jsonData.has("column") ? jsonData.getInt("column") : null;
    }

    public BreakpointLocation setColumn(Integer column) {
        jsonData.putOpt("column", column);
        return this;
    }

    /**
     * Optional end line of breakpoint location if the location covers a range.
     */
    public Integer getEndLine() {
        return jsonData.has("endLine") ? jsonData.getInt("endLine") : null;
    }

    public BreakpointLocation setEndLine(Integer endLine) {
        jsonData.putOpt("endLine", endLine);
        return this;
    }

    /**
     * Optional end column of breakpoint location if the location covers a range.
     */
    public Integer getEndColumn() {
        return jsonData.has("endColumn") ? jsonData.getInt("endColumn") : null;
    }

    public BreakpointLocation setEndColumn(Integer endColumn) {
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
        BreakpointLocation other = (BreakpointLocation) obj;
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
        int hash = 5;
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

    public static BreakpointLocation create(Integer line) {
        final JSONObject json = new JSONObject();
        json.put("line", line);
        return new BreakpointLocation(json);
    }
}
