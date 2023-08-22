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
 * A Stackframe contains the source location.
 */
public class StackFrame extends JSONBase {

    StackFrame(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * An identifier for the stack frame. It must be unique across all threads. This id can be used
     * to retrieve the scopes of the frame with the 'scopesRequest' or to restart the execution of a
     * stackframe.
     */
    public int getId() {
        return jsonData.getInt("id");
    }

    public StackFrame setId(int id) {
        jsonData.put("id", id);
        return this;
    }

    /**
     * The name of the stack frame, typically a method name.
     */
    public String getName() {
        return jsonData.getString("name");
    }

    public StackFrame setName(String name) {
        jsonData.put("name", name);
        return this;
    }

    /**
     * The optional source of the frame.
     */
    public Source getSource() {
        return jsonData.has("source") ? new Source(jsonData.optJSONObject("source")) : null;
    }

    public StackFrame setSource(Source source) {
        jsonData.putOpt("source", source != null ? source.jsonData : null);
        return this;
    }

    /**
     * The line within the file of the frame. If source is null or doesn't exist, line is 0 and must
     * be ignored.
     */
    public int getLine() {
        return jsonData.getInt("line");
    }

    public StackFrame setLine(int line) {
        jsonData.put("line", line);
        return this;
    }

    /**
     * The column within the line. If source is null or doesn't exist, column is 0 and must be
     * ignored.
     */
    public int getColumn() {
        return jsonData.getInt("column");
    }

    public StackFrame setColumn(int column) {
        jsonData.put("column", column);
        return this;
    }

    /**
     * An optional end line of the range covered by the stack frame.
     */
    public Integer getEndLine() {
        return jsonData.has("endLine") ? jsonData.getInt("endLine") : null;
    }

    public StackFrame setEndLine(Integer endLine) {
        jsonData.putOpt("endLine", endLine);
        return this;
    }

    /**
     * An optional end column of the range covered by the stack frame.
     */
    public Integer getEndColumn() {
        return jsonData.has("endColumn") ? jsonData.getInt("endColumn") : null;
    }

    public StackFrame setEndColumn(Integer endColumn) {
        jsonData.putOpt("endColumn", endColumn);
        return this;
    }

    /**
     * Optional memory reference for the current instruction pointer in this frame.
     */
    public String getInstructionPointerReference() {
        return jsonData.optString("instructionPointerReference", null);
    }

    public StackFrame setInstructionPointerReference(String instructionPointerReference) {
        jsonData.putOpt("instructionPointerReference", instructionPointerReference);
        return this;
    }

    /**
     * The module associated with this frame, if any.
     */
    public Object getModuleId() {
        return jsonData.opt("moduleId");
    }

    public StackFrame setModuleId(Object moduleId) {
        jsonData.putOpt("moduleId", moduleId);
        return this;
    }

    /**
     * An optional hint for how to present this frame in the UI. A value of 'label' can be used to
     * indicate that the frame is an artificial frame that is used as a visual label or separator. A
     * value of 'subtle' can be used to change the appearance of a frame in a 'subtle' way.
     */
    public String getPresentationHint() {
        return jsonData.optString("presentationHint", null);
    }

    public StackFrame setPresentationHint(String presentationHint) {
        jsonData.putOpt("presentationHint", presentationHint);
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
        StackFrame other = (StackFrame) obj;
        if (this.getId() != other.getId()) {
            return false;
        }
        if (!Objects.equals(this.getName(), other.getName())) {
            return false;
        }
        if (!Objects.equals(this.getSource(), other.getSource())) {
            return false;
        }
        if (this.getLine() != other.getLine()) {
            return false;
        }
        if (this.getColumn() != other.getColumn()) {
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
        if (!Objects.equals(this.getModuleId(), other.getModuleId())) {
            return false;
        }
        if (!Objects.equals(this.getPresentationHint(), other.getPresentationHint())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        hash = 53 * hash + Integer.hashCode(this.getId());
        hash = 53 * hash + Objects.hashCode(this.getName());
        if (this.getSource() != null) {
            hash = 53 * hash + Objects.hashCode(this.getSource());
        }
        hash = 53 * hash + Integer.hashCode(this.getLine());
        hash = 53 * hash + Integer.hashCode(this.getColumn());
        if (this.getEndLine() != null) {
            hash = 53 * hash + Integer.hashCode(this.getEndLine());
        }
        if (this.getEndColumn() != null) {
            hash = 53 * hash + Integer.hashCode(this.getEndColumn());
        }
        if (this.getInstructionPointerReference() != null) {
            hash = 53 * hash + Objects.hashCode(this.getInstructionPointerReference());
        }
        if (this.getModuleId() != null) {
            hash = 53 * hash + Objects.hashCode(this.getModuleId());
        }
        if (this.getPresentationHint() != null) {
            hash = 53 * hash + Objects.hashCode(this.getPresentationHint());
        }
        return hash;
    }

    public static StackFrame create(Integer id, String name, Integer line, Integer column) {
        final JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("line", line);
        json.put("column", column);
        return new StackFrame(json);
    }
}
