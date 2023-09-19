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
 * Arguments for 'completions' request.
 */
public class CompletionsArguments extends JSONBase {

    CompletionsArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Returns completions in the scope of this stack frame. If not specified, the completions are
     * returned for the global scope.
     */
    public Integer getFrameId() {
        return jsonData.has("frameId") ? jsonData.getInt("frameId") : null;
    }

    public CompletionsArguments setFrameId(Integer frameId) {
        jsonData.putOpt("frameId", frameId);
        return this;
    }

    /**
     * One or more source lines. Typically this is the text a user has typed into the debug console
     * before he asked for completion.
     */
    public String getText() {
        return jsonData.getString("text");
    }

    public CompletionsArguments setText(String text) {
        jsonData.put("text", text);
        return this;
    }

    /**
     * The character position for which to determine the completion proposals.
     */
    public int getColumn() {
        return jsonData.getInt("column");
    }

    public CompletionsArguments setColumn(int column) {
        jsonData.put("column", column);
        return this;
    }

    /**
     * An optional line for which to determine the completion proposals. If missing the first line
     * of the text is assumed.
     */
    public Integer getLine() {
        return jsonData.has("line") ? jsonData.getInt("line") : null;
    }

    public CompletionsArguments setLine(Integer line) {
        jsonData.putOpt("line", line);
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
        CompletionsArguments other = (CompletionsArguments) obj;
        if (!Objects.equals(this.getFrameId(), other.getFrameId())) {
            return false;
        }
        if (!Objects.equals(this.getText(), other.getText())) {
            return false;
        }
        if (this.getColumn() != other.getColumn()) {
            return false;
        }
        if (!Objects.equals(this.getLine(), other.getLine())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        if (this.getFrameId() != null) {
            hash = 37 * hash + Integer.hashCode(this.getFrameId());
        }
        hash = 37 * hash + Objects.hashCode(this.getText());
        hash = 37 * hash + Integer.hashCode(this.getColumn());
        if (this.getLine() != null) {
            hash = 37 * hash + Integer.hashCode(this.getLine());
        }
        return hash;
    }

    public static CompletionsArguments create(String text, Integer column) {
        final JSONObject json = new JSONObject();
        json.put("text", text);
        json.put("column", column);
        return new CompletionsArguments(json);
    }
}
