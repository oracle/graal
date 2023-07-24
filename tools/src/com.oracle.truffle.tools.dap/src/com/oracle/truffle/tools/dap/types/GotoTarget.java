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
 * A GotoTarget describes a code location that can be used as a target in the 'goto' request. The
 * possible goto targets can be determined via the 'gotoTargets' request.
 */
public class GotoTarget extends JSONBase {

    GotoTarget(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Unique identifier for a goto target. This is used in the goto request.
     */
    public int getId() {
        return jsonData.getInt("id");
    }

    public GotoTarget setId(int id) {
        jsonData.put("id", id);
        return this;
    }

    /**
     * The name of the goto target (shown in the UI).
     */
    public String getLabel() {
        return jsonData.getString("label");
    }

    public GotoTarget setLabel(String label) {
        jsonData.put("label", label);
        return this;
    }

    /**
     * The line of the goto target.
     */
    public int getLine() {
        return jsonData.getInt("line");
    }

    public GotoTarget setLine(int line) {
        jsonData.put("line", line);
        return this;
    }

    /**
     * An optional column of the goto target.
     */
    public Integer getColumn() {
        return jsonData.has("column") ? jsonData.getInt("column") : null;
    }

    public GotoTarget setColumn(Integer column) {
        jsonData.putOpt("column", column);
        return this;
    }

    /**
     * An optional end line of the range covered by the goto target.
     */
    public Integer getEndLine() {
        return jsonData.has("endLine") ? jsonData.getInt("endLine") : null;
    }

    public GotoTarget setEndLine(Integer endLine) {
        jsonData.putOpt("endLine", endLine);
        return this;
    }

    /**
     * An optional end column of the range covered by the goto target.
     */
    public Integer getEndColumn() {
        return jsonData.has("endColumn") ? jsonData.getInt("endColumn") : null;
    }

    public GotoTarget setEndColumn(Integer endColumn) {
        jsonData.putOpt("endColumn", endColumn);
        return this;
    }

    /**
     * Optional memory reference for the instruction pointer value represented by this target.
     */
    public String getInstructionPointerReference() {
        return jsonData.optString("instructionPointerReference", null);
    }

    public GotoTarget setInstructionPointerReference(String instructionPointerReference) {
        jsonData.putOpt("instructionPointerReference", instructionPointerReference);
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
        GotoTarget other = (GotoTarget) obj;
        if (this.getId() != other.getId()) {
            return false;
        }
        if (!Objects.equals(this.getLabel(), other.getLabel())) {
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
        if (!Objects.equals(this.getInstructionPointerReference(), other.getInstructionPointerReference())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + Integer.hashCode(this.getId());
        hash = 59 * hash + Objects.hashCode(this.getLabel());
        hash = 59 * hash + Integer.hashCode(this.getLine());
        if (this.getColumn() != null) {
            hash = 59 * hash + Integer.hashCode(this.getColumn());
        }
        if (this.getEndLine() != null) {
            hash = 59 * hash + Integer.hashCode(this.getEndLine());
        }
        if (this.getEndColumn() != null) {
            hash = 59 * hash + Integer.hashCode(this.getEndColumn());
        }
        if (this.getInstructionPointerReference() != null) {
            hash = 59 * hash + Objects.hashCode(this.getInstructionPointerReference());
        }
        return hash;
    }

    public static GotoTarget create(Integer id, String label, Integer line) {
        final JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("label", label);
        json.put("line", line);
        return new GotoTarget(json);
    }
}
