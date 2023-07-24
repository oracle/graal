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
 * Properties of a breakpoint or logpoint passed to the setBreakpoints request.
 */
public class SourceBreakpoint extends JSONBase {

    SourceBreakpoint(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The source line of the breakpoint or logpoint.
     */
    public int getLine() {
        return jsonData.getInt("line");
    }

    public SourceBreakpoint setLine(int line) {
        jsonData.put("line", line);
        return this;
    }

    /**
     * An optional source column of the breakpoint.
     */
    public Integer getColumn() {
        return jsonData.has("column") ? jsonData.getInt("column") : null;
    }

    public SourceBreakpoint setColumn(Integer column) {
        jsonData.putOpt("column", column);
        return this;
    }

    /**
     * An optional expression for conditional breakpoints. It is only honored by a debug adapter if
     * the capability 'supportsConditionalBreakpoints' is true.
     */
    public String getCondition() {
        return jsonData.optString("condition", null);
    }

    public SourceBreakpoint setCondition(String condition) {
        jsonData.putOpt("condition", condition);
        return this;
    }

    /**
     * An optional expression that controls how many hits of the breakpoint are ignored. The backend
     * is expected to interpret the expression as needed. The attribute is only honored by a debug
     * adapter if the capability 'supportsHitConditionalBreakpoints' is true.
     */
    public String getHitCondition() {
        return jsonData.optString("hitCondition", null);
    }

    public SourceBreakpoint setHitCondition(String hitCondition) {
        jsonData.putOpt("hitCondition", hitCondition);
        return this;
    }

    /**
     * If this attribute exists and is non-empty, the backend must not 'break' (stop) but log the
     * message instead. Expressions within {} are interpolated. The attribute is only honored by a
     * debug adapter if the capability 'supportsLogPoints' is true.
     */
    public String getLogMessage() {
        return jsonData.optString("logMessage", null);
    }

    public SourceBreakpoint setLogMessage(String logMessage) {
        jsonData.putOpt("logMessage", logMessage);
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
        SourceBreakpoint other = (SourceBreakpoint) obj;
        if (this.getLine() != other.getLine()) {
            return false;
        }
        if (!Objects.equals(this.getColumn(), other.getColumn())) {
            return false;
        }
        if (!Objects.equals(this.getCondition(), other.getCondition())) {
            return false;
        }
        if (!Objects.equals(this.getHitCondition(), other.getHitCondition())) {
            return false;
        }
        if (!Objects.equals(this.getLogMessage(), other.getLogMessage())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Integer.hashCode(this.getLine());
        if (this.getColumn() != null) {
            hash = 37 * hash + Integer.hashCode(this.getColumn());
        }
        if (this.getCondition() != null) {
            hash = 37 * hash + Objects.hashCode(this.getCondition());
        }
        if (this.getHitCondition() != null) {
            hash = 37 * hash + Objects.hashCode(this.getHitCondition());
        }
        if (this.getLogMessage() != null) {
            hash = 37 * hash + Objects.hashCode(this.getLogMessage());
        }
        return hash;
    }

    public static SourceBreakpoint create(Integer line) {
        final JSONObject json = new JSONObject();
        json.put("line", line);
        return new SourceBreakpoint(json);
    }
}
