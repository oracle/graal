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
 * Information about a Breakpoint created in setBreakpoints or setFunctionBreakpoints.
 */
public class Breakpoint extends JSONBase {

    Breakpoint(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * An optional identifier for the breakpoint. It is needed if breakpoint events are used to
     * update or remove breakpoints.
     */
    public Integer getId() {
        return jsonData.has("id") ? jsonData.getInt("id") : null;
    }

    public Breakpoint setId(Integer id) {
        jsonData.putOpt("id", id);
        return this;
    }

    /**
     * If true breakpoint could be set (but not necessarily at the desired location).
     */
    public boolean isVerified() {
        return jsonData.getBoolean("verified");
    }

    public Breakpoint setVerified(boolean verified) {
        jsonData.put("verified", verified);
        return this;
    }

    /**
     * An optional message about the state of the breakpoint. This is shown to the user and can be
     * used to explain why a breakpoint could not be verified.
     */
    public String getMessage() {
        return jsonData.optString("message", null);
    }

    public Breakpoint setMessage(String message) {
        jsonData.putOpt("message", message);
        return this;
    }

    /**
     * The source where the breakpoint is located.
     */
    public Source getSource() {
        return jsonData.has("source") ? new Source(jsonData.optJSONObject("source")) : null;
    }

    public Breakpoint setSource(Source source) {
        jsonData.putOpt("source", source != null ? source.jsonData : null);
        return this;
    }

    /**
     * The start line of the actual range covered by the breakpoint.
     */
    public Integer getLine() {
        return jsonData.has("line") ? jsonData.getInt("line") : null;
    }

    public Breakpoint setLine(Integer line) {
        jsonData.putOpt("line", line);
        return this;
    }

    /**
     * An optional start column of the actual range covered by the breakpoint.
     */
    public Integer getColumn() {
        return jsonData.has("column") ? jsonData.getInt("column") : null;
    }

    public Breakpoint setColumn(Integer column) {
        jsonData.putOpt("column", column);
        return this;
    }

    /**
     * An optional end line of the actual range covered by the breakpoint.
     */
    public Integer getEndLine() {
        return jsonData.has("endLine") ? jsonData.getInt("endLine") : null;
    }

    public Breakpoint setEndLine(Integer endLine) {
        jsonData.putOpt("endLine", endLine);
        return this;
    }

    /**
     * An optional end column of the actual range covered by the breakpoint. If no end line is
     * given, then the end column is assumed to be in the start line.
     */
    public Integer getEndColumn() {
        return jsonData.has("endColumn") ? jsonData.getInt("endColumn") : null;
    }

    public Breakpoint setEndColumn(Integer endColumn) {
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
        Breakpoint other = (Breakpoint) obj;
        if (!Objects.equals(this.getId(), other.getId())) {
            return false;
        }
        if (this.isVerified() != other.isVerified()) {
            return false;
        }
        if (!Objects.equals(this.getMessage(), other.getMessage())) {
            return false;
        }
        if (!Objects.equals(this.getSource(), other.getSource())) {
            return false;
        }
        if (!Objects.equals(this.getLine(), other.getLine())) {
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
        if (this.getId() != null) {
            hash = 29 * hash + Integer.hashCode(this.getId());
        }
        hash = 29 * hash + Boolean.hashCode(this.isVerified());
        if (this.getMessage() != null) {
            hash = 29 * hash + Objects.hashCode(this.getMessage());
        }
        if (this.getSource() != null) {
            hash = 29 * hash + Objects.hashCode(this.getSource());
        }
        if (this.getLine() != null) {
            hash = 29 * hash + Integer.hashCode(this.getLine());
        }
        if (this.getColumn() != null) {
            hash = 29 * hash + Integer.hashCode(this.getColumn());
        }
        if (this.getEndLine() != null) {
            hash = 29 * hash + Integer.hashCode(this.getEndLine());
        }
        if (this.getEndColumn() != null) {
            hash = 29 * hash + Integer.hashCode(this.getEndColumn());
        }
        return hash;
    }

    public static Breakpoint create(Boolean verified) {
        final JSONObject json = new JSONObject();
        json.put("verified", verified);
        return new Breakpoint(json);
    }
}
